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
import eu.doppelhelix.app.bitwardenagent.BitwardenMain;
import eu.doppelhelix.app.bitwardenagent.Configuration;
import eu.doppelhelix.app.bitwardenagent.http.CipherData;
import eu.doppelhelix.app.bitwardenagent.http.ConfigResponse;
import eu.doppelhelix.app.bitwardenagent.http.FieldData;
import eu.doppelhelix.app.bitwardenagent.http.OrganzationData;
import eu.doppelhelix.app.bitwardenagent.http.PreloginResult;
import eu.doppelhelix.app.bitwardenagent.http.SyncData;
import eu.doppelhelix.app.bitwardenagent.http.TokenResult;
import eu.doppelhelix.app.bitwardenagent.http.UriData;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.logging.LoggingFeature;

import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Initial;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.LocalStatePresent;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Offline;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Started;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Syncable;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Syncing;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.decryptKey;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.decryptPrivateKey;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.deriveMasterKey;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.encryptString;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.encryptionKeyFromMasterKey;

// https://bitwarden.com/help/kdf-algorithms/
// https://www.avangate.it/wp-content/uploads/2024/04/help-bitwarden-security-white-paper.pdf
// https://github.com/bitwarden/clients/blob/f55f315ca15df09772e957e0e8b089a2d45b04f7/libs/common/src/key-management/crypto/services/web-crypto-function.service.ts#L258
// https://github.com/bitwarden/clients/blob/main/libs/common/src/key-management/crypto/services/encrypt.service.implementation.ts#L307
// https://github.com/bitwarden/clients/blob/f55f315ca15df09772e957e0e8b089a2d45b04f7/libs/common/src/platform/models/domain/symmetric-crypto-key.ts#L53
public class BitwardenClient implements Closeable {

    public enum State {
        Started,
        LocalStatePresent,
        Initial,
        Offline,
        Syncable,
        Syncing
    }

    public interface StateObserver {
        public void stateChanged(State oldState, State newState);
    }

    private final static ObjectMapper objectMapper = new ObjectMapper();
    private final List<StateObserver> stateObserver = new CopyOnWriteArrayList<>();
    private final Client client;
    private final Path configPath;
    private UUID deviceId = UUID.randomUUID();
    private String deviceName = "BitwardenAgent";
    private String email;
    private String refreshToken;
    private PreloginResult preloginResult;
    private URI baseURI = URI.create("https://vault.bitwarden.eu/");
    private EncryptionKey stretchedMasterKey;
    private EncryptionKey userKey;
    private PrivateKey userPrivateKey;
    private Map<String, EncryptionKey> organizationKeys;
    private SyncData syncData;
    private State state = State.Started;

    public BitwardenClient() {
        client = JerseyClientBuilder.newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .register(new LoggingFeature(Logger.getLogger(BitwardenClient.class.getName()), Level.FINE, LoggingFeature.Verbosity.PAYLOAD_ANY, 65535))
                .build();
        configPath = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")
                ? Path.of(System.getenv("LOCALAPPDATA"), "BitwardenAgent", "config.json")
                : Path.of(System.getenv("HOME"), ".config/BitwardenAgent", "config.json");
        if (Files.exists(configPath)) {
            try {
                Configuration config = objectMapper.readValue(configPath.toFile(), Configuration.class);
                email = config.getEmail();
                baseURI = config.getBaseUri() != null ? config.getBaseUri() : this.baseURI;
                if(config.getClientId() != null) {
                    deviceId = config.getClientId();
                }
                refreshToken = config.getRefreshToken();
                syncData = config.getSyncData();
                preloginResult = config.getPreloginResult();
            } catch (IOException ex) {
                System.getLogger(BitwardenClient.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        }
        if(email != null && baseURI != null && preloginResult != null && syncData != null) {
            setState(State.LocalStatePresent);
        } else {
            setState(State.Initial);
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

    public void unlock(char[] password) throws GeneralSecurityException {
        byte[] masterKey = deriveMasterKey(password, email, this.preloginResult);
        stretchedMasterKey = encryptionKeyFromMasterKey(masterKey);

        if (syncData != null) {
            userKey = decryptKey(stretchedMasterKey, syncData.profile().key());
            userPrivateKey = decryptPrivateKey(userKey, syncData.profile().privateKey());
            Map<String, EncryptionKey> organizationKeysBuilder = new HashMap<>();
            for (OrganzationData od : syncData.profile().organizations()) {
                organizationKeysBuilder.put(od.id(), decryptKey(userPrivateKey, od.key()));
            }
            organizationKeys = organizationKeysBuilder;
        }

        setState(Offline);

        if(refreshToken != null) {
            setState(Syncable);
        }
    }

    void login(URI baseURI, String email, EncryptionKey stretchedMasterKey, String masterPasswordHash, String newRefreshToken, PreloginResult preloginResult) throws GeneralSecurityException {
        this.baseURI = baseURI;
        this.preloginResult = preloginResult;
        this.email = email;
        this.stretchedMasterKey = stretchedMasterKey;
        this.refreshToken = encryptString(stretchedMasterKey, newRefreshToken);

        store();

        setState(Syncable);

        sync();
    }


    TokenResult loginSSO(URI baseURI, String code, String codeVerifier, String redirectUri) throws GeneralSecurityException, IllegalStateException, IOException  {
        TokenResult loginResponse = client
                .target(baseURI)
                .path("identity/connect/token")
                .request()
                .header("Device-Type", 25)
                .post(Entity.form(tokenRequestSSO(code, codeVerifier, redirectUri)), TokenResult.class);

        return loginResponse;
    }

    public void sync() throws GeneralSecurityException {
        setState(Syncing);

        try {
            WebTarget baseTarget = client.target(baseURI);
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
                    .header("Bitwarden-Client-Version", "2025.08.0")
                    .get(SyncData.class);

            userKey = decryptKey(stretchedMasterKey, syncData.profile().key());
            userPrivateKey = decryptPrivateKey(userKey, syncData.profile().privateKey());
            Map<String, EncryptionKey> organizationKeysBuilder = new HashMap<>();
            for (OrganzationData od : syncData.profile().organizations()) {
                organizationKeysBuilder.put(od.id(), decryptKey(userPrivateKey, od.key()));
            }
            organizationKeys = organizationKeysBuilder;

            store();

            setState(Syncable);
        } catch (NotAuthorizedException | ForbiddenException ex) {
            setState(Offline);
        } catch (Exception ex) {
            setState(Syncable);
        }
    }

    public DecryptedSyncData getSyncData() {
        EncryptionKey localUserKey = userKey;
        Map<String, EncryptionKey> localOrganizationKeys = new HashMap<>(this.organizationKeys);
        SyncData localSyncData = syncData;
        if (localSyncData == null) {
            return null;
        }
        DecryptedSyncData result = new DecryptedSyncData();
        result.setId(localSyncData.profile().id());
        result.setEmail(localSyncData.profile().email());
        result.setName(localSyncData.profile().name());
        localSyncData.profile().organizations().forEach(od -> {
            result.getOrganizationNames().put(od.id(), od.name());
        });
        localSyncData.ciphers()
                .stream()
                .forEach(cd -> {
                    try {
                        DecryptedCipherData dcd = new DecryptedCipherData();
                        dcd.setName(decryptString(localUserKey, localOrganizationKeys, cd, cd.name()));
                        dcd.setId(cd.id());
                        dcd.setOrganizationId(cd.organizationId());
                        if(cd.organizationId() != null) {
                            dcd.setOrganization(result.getOrganizationNames().get(cd.organizationId()));
                        }
                        if (cd.login() != null) {
                            DecryptedLoginData dld = new DecryptedLoginData();
                            dld.setPassword(decryptString(localUserKey, localOrganizationKeys, cd, cd.login().password()));
                            dld.setTotp(decryptString(localUserKey, localOrganizationKeys, cd, cd.login().totp()));
                            dld.setUri(decryptString(localUserKey, localOrganizationKeys, cd, cd.login().uri()));
                            dld.setUsername(decryptString(localUserKey, localOrganizationKeys, cd, cd.login().username()));
                            if(cd.login().uris() != null) {
                                for(UriData ud: cd.login().uris()) {
                                    DecryptedUriData dud = new DecryptedUriData();
                                    dud.setMatch(ud.match());
                                    dud.setUri(decryptString(localUserKey, localOrganizationKeys, cd, ud.uri()));
                                    dud.setUriChecksum(decryptString(localUserKey, localOrganizationKeys, cd, ud.uriChecksum()));
                                };
                            }
                            dcd.setLogin(dld);
                        }
                        if (cd.sshKey() != null) {
                            DecryptedSshKey dsk = new DecryptedSshKey();
                            dsk.setKeyFingerprint(decryptString(localUserKey, localOrganizationKeys, cd, cd.sshKey().keyFingerprint()));
                            dsk.setPrivateKey(decryptString(localUserKey, localOrganizationKeys, cd, cd.sshKey().privateKey()));
                            dsk.setPublicKey(decryptString(localUserKey, localOrganizationKeys, cd, cd.sshKey().publicKey()));
                            dcd.setSshKey(dsk);
                        }
                        dcd.setNotes(decryptString(localUserKey, localOrganizationKeys, cd, cd.notes()));
                        if(cd.fields() != null) {
                            for (FieldData fd : cd.fields()) {
                                DecryptedFieldData dfd = new DecryptedFieldData();
                                dfd.setLinkedId(fd.linkedId());
                                dfd.setType(fd.type());
                                dfd.setName(decryptString(localUserKey, localOrganizationKeys, cd, fd.name()));
                                dfd.setValue(decryptString(localUserKey, localOrganizationKeys, cd, fd.value()));
                                dcd.getFields().add(dfd);
                            }
                        }
                        result.getCiphers().add(dcd);
                    } catch (Exception ex) {
                        System.getLogger(BitwardenMain.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                    }
                });
        return result;
    }

    public String decryptString(CipherData cd, String encryptedString) throws GeneralSecurityException {
        return decryptString(userKey, organizationKeys, cd, encryptedString);
    }

    public static String decryptString(EncryptionKey userKey, Map<String, EncryptionKey> organizationKeys, CipherData cd, String encryptedString) throws GeneralSecurityException {
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

    public void clear() {
        this.email = null;
        this.refreshToken = null;
        this.preloginResult = null;
        this.stretchedMasterKey = null;
        this.userKey = null;
        this.userPrivateKey = null;
        this.organizationKeys = null;
        this.syncData = null;
        store();
        setState(Initial);
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


    public State getState() {
        return state;
    }

    public void addStateObserver(StateObserver so) {
        Objects.requireNonNull(so);
        stateObserver.add(so);
    }

    public void removeStateObserver(StateObserver so) {
        stateObserver.remove(so);
    }

    private static final Map<State, Set<State>> ALLOWED_TRANSITIONS = Map.of(
            Started, EnumSet.of(Initial, LocalStatePresent),
            LocalStatePresent, EnumSet.of(Initial, Offline),
            Initial, EnumSet.of(Syncable),
            Offline, EnumSet.of(Syncable),
            Syncable, EnumSet.of(Syncing),
            Syncing, EnumSet.of(Syncable, Offline)
    );

    private void setState(State newState) {
        if(newState == state) {
            return;
        }
        boolean allowedTransition = ALLOWED_TRANSITIONS
                .getOrDefault(state, EnumSet.noneOf(State.class))
                .contains(newState);
        if (!allowedTransition) {
            throw new IllegalStateException(String.format("Transition not allowed: %s -> %s", state, newState));
        }

        State oldState = state;
        state = newState;
        stateObserver.forEach(so -> so.stateChanged(oldState, newState));
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
