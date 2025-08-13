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

import eu.doppelhelix.app.bitwardenagent.impl.http.CipherData;
import eu.doppelhelix.app.bitwardenagent.impl.http.OrganzationData;
import eu.doppelhelix.app.bitwardenagent.impl.http.PreloginRequest;
import eu.doppelhelix.app.bitwardenagent.impl.http.PreloginResult;
import eu.doppelhelix.app.bitwardenagent.impl.http.SyncData;
import eu.doppelhelix.app.bitwardenagent.impl.http.TokenResult;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
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
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.deriveHkdfSha256;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.deriveMasterKey;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.deriveMasterKeyHash;

// https://bitwarden.com/help/kdf-algorithms/
// https://www.avangate.it/wp-content/uploads/2024/04/help-bitwarden-security-white-paper.pdf
// https://github.com/bitwarden/clients/blob/f55f315ca15df09772e957e0e8b089a2d45b04f7/libs/common/src/key-management/crypto/services/web-crypto-function.service.ts#L258
// https://github.com/bitwarden/clients/blob/main/libs/common/src/key-management/crypto/services/encrypt.service.implementation.ts#L307
// https://github.com/bitwarden/clients/blob/f55f315ca15df09772e957e0e8b089a2d45b04f7/libs/common/src/platform/models/domain/symmetric-crypto-key.ts#L53
public class BitwardenClient implements Closeable {
    public enum State {
        Started,
        LoclaStatePresent,
        Initial,
        InitialSync,
        Offline,
        OfflineWithToken,
        Syncing,
        Synced
    }

    private final Client client;
    private final UUID clientId;
    private final String clientName;
    private URI baseURI;
    private WebTarget baseTarget;
    private WebTarget preloginTarget;
    private WebTarget tokenTarget;
    private WebTarget syncTarget;

    private String email;
    private EncryptionKey stretchedMasterKey;
    private String masterPasswordHash;
    private EncryptionKey userKey;
    private PrivateKey userPrivateKey;
    private Map<String, EncryptionKey> organizationKeys;
    private SyncData syncData;
    private State state;

    public BitwardenClient(
        UUID clientId,
        String clientName
    ) {
        client = JerseyClientBuilder.newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .register(new LoggingFeature(Logger.getLogger(BitwardenClient.class.getName()), Level.FINEST, LoggingFeature.Verbosity.PAYLOAD_ANY, 65535))
                .build();
        this.clientId = clientId == null ? UUID.randomUUID() : clientId;
        this.clientName = clientName == null ? "BitwardenClient" : clientName;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public URI getBaseURI() {
        return baseURI;
    }

    public void login(String email, char[] password) {
        login(email, password, null);
    }

    public void login(String email, char[] password, String otp) {
        this.baseURI = baseURI == null ? URI.create("https://vault.bitwarden.eu/") : baseURI;
        baseTarget = client.target(baseURI);
        preloginTarget = baseTarget.path("identity/accounts/prelogin");
        tokenTarget = baseTarget.path("identity/connect/token");
        syncTarget = baseTarget.path("api/sync").queryParam("excludeDomains", "true");

        PreloginResult preloginResult = preloginTarget
                .request()
                .post(Entity.json(new PreloginRequest(email)), PreloginResult.class);

        this.email = email;
        byte[] masterKey = deriveMasterKey(password, email, preloginResult);
        stretchedMasterKey = new EncryptionKey(deriveHkdfSha256(masterKey, "enc"), deriveHkdfSha256(masterKey, "mac"));
        masterPasswordHash = deriveMasterKeyHash(masterKey, password);

        tokenTarget
                .request()
                .header("Device-Type", 25)
                .header("Bitwarden-Client-Name", "cli")
                .header("Bitwarden-Client-Version", "2025.7.0")
                .post(Entity.form(tokenRequestPwd(otp)), TokenResult.class);
    }

    public SyncData sync() throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException, InvalidKeySpecException {
        TokenResult loginResponse = tokenTarget
                .request()
                .header("Device-Type", 25)
                .header("Bitwarden-Client-Name", "cli")
                .header("Bitwarden-Client-Version", "2025.7.0")
                .post(Entity.form(tokenRequestPwd(null)), TokenResult.class);

        syncData = syncTarget.request()
                .header("Authorization", "Bearer " + loginResponse.access_token())
                .get(SyncData.class);

        userKey = decryptKey(stretchedMasterKey, syncData.profile().key());
        userPrivateKey = decryptPrivateKey(userKey, syncData.profile().privateKey());
        Map<String, EncryptionKey> organizationKeysBuilder = new HashMap<>();
        for (OrganzationData od : syncData.profile().organizations()) {
            organizationKeysBuilder.put(od.id(), decryptKey(userPrivateKey, od.key()));
        }
        organizationKeys = organizationKeysBuilder;

        return syncData;
    }

    public String decryptString(CipherData cd, String encryptedString) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException {
        EncryptionKey ek;
        if (cd.organizationId() == null) {
            ek = userKey;
        } else {
            ek = organizationKeys.get(cd.organizationId());
        }
        return UtilCryto.decryptString(ek, cd.login().username());
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private Form tokenRequestPwd(String newDeviceOtp) {
        Form form = new Form();
        form.param("scope", "api offline_access");
        form.param("client_id", "cli");
        form.param("deviceType", "25");
        form.param("deviceIdentifier", clientId.toString());
        form.param("deviceName", clientName);
        form.param("grant_type", "password");
        form.param("username", email);
        form.param("password", masterPasswordHash);
        if (newDeviceOtp != null) {
            form.param("newDeviceOtp", newDeviceOtp);
        }
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
