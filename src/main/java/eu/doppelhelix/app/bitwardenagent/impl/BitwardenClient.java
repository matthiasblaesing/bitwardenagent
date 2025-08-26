/*
 * Copyright 2025 matthias.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.doppelhelix.app.bitwardenagent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.doppelhelix.app.bitwardenagent.Configuration;
import eu.doppelhelix.app.bitwardenagent.http.CipherData;
import eu.doppelhelix.app.bitwardenagent.http.ConfigResponse;
import eu.doppelhelix.app.bitwardenagent.http.OrganzationData;
import eu.doppelhelix.app.bitwardenagent.http.PreloginResult;
import eu.doppelhelix.app.bitwardenagent.http.SyncData;
import eu.doppelhelix.app.bitwardenagent.http.TokenResult;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.logging.LoggingFeature;

import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.decryptKey;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.decryptPrivateKey;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.deriveMasterKey;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.deriveMasterKeyHash;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.encryptString;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.encryptionKeyFromMasterKey;

// https://bitwarden.com/help/kdf-algorithms/
// https://www.avangate.it/wp-content/uploads/2024/04/help-bitwarden-security-white-paper.pdf
// https://github.com/bitwarden/clients/blob/f55f315ca15df09772e957e0e8b089a2d45b04f7/libs/common/src/key-management/crypto/services/web-crypto-function.service.ts#L258
// https://github.com/bitwarden/clients/blob/main/libs/common/src/key-management/crypto/services/encrypt.service.implementation.ts#L307
// https://github.com/bitwarden/clients/blob/f55f315ca15df09772e957e0e8b089a2d45b04f7/libs/common/src/platform/models/domain/symmetric-crypto-key.ts#L53
public class BitwardenClient implements Closeable {

    private final static ObjectMapper objectMapper = new ObjectMapper();
    private final Client client;
    private final Path configPath;
    private UUID deviceId = UUID.randomUUID();
    private String deviceName = "BitwardenAgent";
    private String email;
    private String refreshToken;
    private PreloginResult preloginResult;
    private URI baseURI = URI.create("https://vault.bitwarden.eu/");
    private WebTarget baseTarget;
    private EncryptionKey stretchedMasterKey;
    private String masterPasswordHash;
    private EncryptionKey userKey;
    private PrivateKey userPrivateKey;
    private Map<String, EncryptionKey> organizationKeys;
    private SyncData syncData;

    public BitwardenClient() {
        client = JerseyClientBuilder.newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .register(new LoggingFeature(Logger.getLogger(BitwardenClient.class.getName()), Level.FINEST, LoggingFeature.Verbosity.PAYLOAD_ANY, 65535))
                .build();
        configPath = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")
                ? Path.of(System.getenv("LOCALAPPDATA"), "BitwardenAgent", "config.json")
                : Path.of(System.getenv("HOME"), ".config/BitwardenAgent", "config.json");
        if (Files.exists(configPath)) {
            try {
                Configuration config = objectMapper.readValue(configPath.toFile(), Configuration.class);
                email = config.getEmail();
                baseURI = config.getBaseUri() != null ? config.getBaseUri() : this.baseURI;
                deviceId = config.getClientId();
                refreshToken = config.getRefreshToken();
                syncData = config.getSyncData();
                preloginResult = config.getPreloginResult();
            } catch (IOException ex) {
                System.getLogger(BitwardenClient.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        }
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public URI getBaseURI() {
        return baseURI;
    }

    public String getEmail() {
        return email;
    }

    public boolean isNeedsUnlocking() {
        return this.refreshToken != null && this.preloginResult != null && this.stretchedMasterKey == null;
    }

    public boolean isAuthenticated() {
        return this.refreshToken != null && this.preloginResult != null && this.stretchedMasterKey != null;
    }

    public void unlock(char[] password) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException, InvalidKeySpecException {
        baseTarget = client.target(baseURI);

        byte[] masterKey = deriveMasterKey(password, email, this.preloginResult);
        stretchedMasterKey = encryptionKeyFromMasterKey(masterKey);
        masterPasswordHash = deriveMasterKeyHash(masterKey, password);

        if (syncData != null) {
            userKey = decryptKey(stretchedMasterKey, syncData.profile().key());
            userPrivateKey = decryptPrivateKey(userKey, syncData.profile().privateKey());
            Map<String, EncryptionKey> organizationKeysBuilder = new HashMap<>();
            for (OrganzationData od : syncData.profile().organizations()) {
                organizationKeysBuilder.put(od.id(), decryptKey(userPrivateKey, od.key()));
            }
            organizationKeys = organizationKeysBuilder;
        }
    }

    void login(URI baseURI, String email, EncryptionKey stretchedMasterKey, String masterPasswordHash, String newRefreshToken, PreloginResult preloginResult) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException, InvalidKeySpecException {
        this.baseURI = baseURI;
        this.baseTarget = client.target(baseURI);
        this.preloginResult = preloginResult;
        this.email = email;
        this.stretchedMasterKey = stretchedMasterKey;
        this.masterPasswordHash = masterPasswordHash;
        this.refreshToken = encryptString(stretchedMasterKey, newRefreshToken);

        store();

        sync();
    }


    TokenResult loginSSO(URI baseURI, String code, String codeVerifier, String redirectUri) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException, InvalidKeySpecException, IOException  {
        TokenResult loginResponse = client
                .target(baseURI)
                .path("identity/connect/token")
                .request()
                .header("Device-Type", 25)
                .post(Entity.form(tokenRequestSSO(code, codeVerifier, redirectUri)), TokenResult.class);

        return loginResponse;
    }

    public void sync() throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException, InvalidKeySpecException {
        TokenResult loginResponse = baseTarget
                .path("identity/connect/token")
                .request()
                .header("Device-Type", 25)
                .post(Entity.form(tokenRequestToken(UtilCryto.decryptString(stretchedMasterKey, refreshToken))), TokenResult.class);

        refreshToken = encryptString(stretchedMasterKey, loginResponse.refreshToken());

        syncData = baseTarget
                .path("api/sync")
                .queryParam("excludeDomains", "true")
                .request()
                .header("Authorization", "Bearer " + loginResponse.accessToken())
                .get(SyncData.class);

        userKey = decryptKey(stretchedMasterKey, syncData.profile().key());
        userPrivateKey = decryptPrivateKey(userKey, syncData.profile().privateKey());
        Map<String, EncryptionKey> organizationKeysBuilder = new HashMap<>();
        for (OrganzationData od : syncData.profile().organizations()) {
            organizationKeysBuilder.put(od.id(), decryptKey(userPrivateKey, od.key()));
        }
        organizationKeys = organizationKeysBuilder;

        store();
    }

    public SyncData getSyncData() {
        return syncData;
    }

    public String decryptString(CipherData cd, String encryptedString) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException {
        EncryptionKey ek;
        if (cd.organizationId() == null) {
            ek = userKey;
        } else {
            ek = organizationKeys.get(cd.organizationId());
        }
        return UtilCryto.decryptString(ek, encryptedString);
    }

    private void store() {
        Configuration config = new Configuration();
        config.setEmail(this.email);
        config.setBaseUri(baseURI);
        config.setClientId(deviceId);
        config.setRefreshToken(refreshToken);
        config.setSyncData(syncData);
        config.setPreloginResult(preloginResult);
        try {
            Files.createDirectories(configPath.getParent());
            objectMapper.writeValue(configPath.toFile(), config);
        } catch (IOException ex) {
            System.getLogger(BitwardenClient.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    public BitwardenAuthenticator createAuthenticator() {
        return new BitwardenAuthenticator(this);
    }

    Client getClient() {
        return client;
    }

    public ConfigResponse getConfig(URI baseUri) {
        return client
                .target(baseUri)
                .path("/api/config")
                .request()
                .get(ConfigResponse.class);
    }

    Form tokenRequestPwd(String newDeviceOtp) {
        return tokenRequestPwd(email, masterPasswordHash, newDeviceOtp);
    }

    Form tokenRequestPwd(String emailInput, String masterPasswordHashInput, String newDeviceOtp) {
        Form form = new Form();
        form.param("scope", "api offline_access");
        form.param("client_id", "cli");
        form.param("deviceType", "25");
        form.param("deviceIdentifier", deviceId.toString());
        form.param("deviceName", deviceName);
        form.param("grant_type", "password");
        form.param("username", emailInput);
        form.param("password", masterPasswordHashInput);
        if (newDeviceOtp != null) {
            form.param("newDeviceOtp", newDeviceOtp);
        }
        return form;
    }

    private Form tokenRequestSSO(String code, String codeVerifier, String redirectUri) {
        Form form = new Form();
        form.param("scope", "api offline_access");
        form.param("client_id", "cli");
        form.param("deviceType", "25");
        form.param("deviceIdentifier", deviceId.toString());
        form.param("deviceName", deviceName);
        form.param("grant_type", "authorization_code");
        form.param("code", code);
        form.param("code_verifier", codeVerifier);
        form.param("redirect_uri", redirectUri);
        return form;
    }

    private static Form tokenRequestToken(String refreshToken) {
        Form form = new Form();
        form.param("grant_type", "refresh_token");
        form.param("client_id", "cli");
        form.param("refresh_token", refreshToken);
        return form;
    }
}
