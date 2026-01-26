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
import eu.doppelhelix.app.bitwardenagent.http.CipherData;
import eu.doppelhelix.app.bitwardenagent.http.ConfigResponse;
import eu.doppelhelix.app.bitwardenagent.http.FieldData;
import eu.doppelhelix.app.bitwardenagent.http.LoginErrorData;
import eu.doppelhelix.app.bitwardenagent.http.OrganzationData;
import eu.doppelhelix.app.bitwardenagent.http.PasswordHistoryEntry;
import eu.doppelhelix.app.bitwardenagent.http.PreloginResult;
import eu.doppelhelix.app.bitwardenagent.http.SyncData;
import eu.doppelhelix.app.bitwardenagent.http.TokenResult;
import eu.doppelhelix.app.bitwardenagent.http.UriData;
import jakarta.ws.rs.BadRequestException;
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
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.jersey.logging.LoggingFeature;

import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Initial;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.LocalStatePresent;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Offline;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Started;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Syncable;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Syncing;
import static eu.doppelhelix.app.bitwardenagent.impl.Util.isWindows;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.decryptKey;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.decryptPrivateKey;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.deriveMasterKey;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.encryptString;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.encryptionKeyFromMasterKey;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.ERROR;

// https://bitwarden.com/help/kdf-algorithms/
// https://www.avangate.it/wp-content/uploads/2024/04/help-bitwarden-security-white-paper.pdf
// https://github.com/bitwarden/clients/blob/f55f315ca15df09772e957e0e8b089a2d45b04f7/libs/common/src/key-management/crypto/services/web-crypto-function.service.ts#L258
// https://github.com/bitwarden/clients/blob/main/libs/common/src/key-management/crypto/services/encrypt.service.implementation.ts#L307
// https://github.com/bitwarden/clients/blob/f55f315ca15df09772e957e0e8b089a2d45b04f7/libs/common/src/platform/models/domain/symmetric-crypto-key.ts#L53
public class BitwardenClient implements Closeable {

    private static final System.Logger LOG = System.getLogger(BitwardenClient.class.getName());

    public enum State {
        Started,
        LocalStatePresent,
        Initial,
        Offline,
        Syncable,
        Syncing;

        private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("eu/doppelhelix/app/bitwardenagent/Bundle");

        public String toLocaleString() {
            String resourceKey = "status." + name();
            if (RESOURCE_BUNDLE.keySet().contains(resourceKey)) {
                return RESOURCE_BUNDLE.getString(resourceKey);
            } else {
                return name();
            }
        }
    }

    public interface StateObserver {
        public void stateChanged(State oldState, State newState);
    }

    private final static ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
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
                .register(new JacksonJsonProvider(objectMapper))
                .build();
        configPath = isWindows()
                ? Path.of(System.getenv("APPDATA"), "BitwardenAgent", "state.json")
                : Path.of(System.getenv("HOME"), ".config/BitwardenAgent", "state.json");
        if (Files.exists(configPath)) {
            try {
                ClientState config = objectMapper.readValue(configPath.toFile(), ClientState.class);
                email = config.getEmail();
                baseURI = config.getBaseUri() != null ? config.getBaseUri() : this.baseURI;
                if(config.getClientId() != null) {
                    deviceId = config.getClientId();
                }
                refreshToken = config.getRefreshToken();
                syncData = config.getSyncData();
                preloginResult = config.getPreloginResult();
            } catch (IOException ex) {
                LOG.log(ERROR, (String) null, ex);
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
        LOG.log(INFO, "Starting sync");

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
                    .header("Bitwarden-Client-Version", "2026.1.0")
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
            LOG.log(ERROR, "Authentication failure received", ex);
        } catch (Exception ex) {
            boolean handled = false;
            try {
                // Why NotAuthorized is not used is beyond my understanding
                if(ex instanceof BadRequestException bre
                        && "invalid_grant".equals(bre.getResponse().readEntity(LoginErrorData.class).error())) {
                    handled = true;
                    setState(Offline);
                }
            } catch (Exception ex2) {}
            if(! handled) {
                setState(Syncable);
            }
            LOG.log(ERROR, "Sync failed with exception", ex);
        } finally {
            LOG.log(INFO, "Finished sync");
        }
    }

    public DecryptedSyncData getSyncData() {
        EncryptionKey localUserKey = userKey;
        if(userKey == null || this.organizationKeys == null) {
            return null;
        }
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
        localSyncData.collections().forEach(c -> {
            try {
                result.getCollectionNames().put(c.id(), decryptString(organizationKeys, c.organizationId(), c.name()));
            } catch (GeneralSecurityException ex) {
                LOG.log(ERROR, (String) null, ex);
            }
        });
        localSyncData.folders().forEach(f -> {
            try {
                result.getFolderNames().put(f.id(), UtilCryto.decryptString(userKey, f.name()));
            } catch (GeneralSecurityException ex) {
                LOG.log(ERROR, (String) null, ex);
            }
        });
        localSyncData.ciphers().forEach(cd -> {
            try {
                DecryptedCipherData dcd = new DecryptedCipherData();
                dcd.setName(decryptString(localUserKey, localOrganizationKeys, cd, cd.name()));
                dcd.setId(cd.id());
                dcd.setOrganizationId(cd.organizationId());
                dcd.setFolderId(cd.folderId());
                if(dcd.getFolderId() != null && result.getFolderNames().containsKey(dcd.getFolderId())) {
                    dcd.setFolder(result.getFolderNames().get(dcd.getFolderId()));
                }
                if (cd.collectionIds() != null) {
                    dcd.getCollectionIds().addAll(cd.collectionIds());
                    dcd.getCollections().addAll(
                            cd.collectionIds()
                                    .stream()
                                    .map(ci -> result.getCollectionNames().get(ci))
                                    .filter(cn -> cn != null)
                                    .collect(Collectors.toList())
                    );
                }
                if (cd.organizationId() != null) {
                    dcd.setOrganization(result.getOrganizationNames().get(cd.organizationId()));
                }
                if (cd.login() != null) {
                    DecryptedLoginData dld = new DecryptedLoginData();
                    dld.setPassword(decryptString(localUserKey, localOrganizationKeys, cd, cd.login().password()));
                    dld.setTotp(decryptString(localUserKey, localOrganizationKeys, cd, cd.login().totp()));
                    dld.setUri(decryptString(localUserKey, localOrganizationKeys, cd, cd.login().uri()));
                    dld.setUsername(decryptString(localUserKey, localOrganizationKeys, cd, cd.login().username()));
                    if (cd.login().uris() != null) {
                        for (UriData ud : cd.login().uris()) {
                            DecryptedUriData dud = new DecryptedUriData();
                            dud.setMatch(ud.match());
                            dud.setUri(decryptString(localUserKey, localOrganizationKeys, cd, ud.uri()));
                            dud.setUriChecksum(decryptString(localUserKey, localOrganizationKeys, cd, ud.uriChecksum()));
                            dld.getUriData().add(dud);
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
                if (cd.card() != null) {
                    DecryptedCardData decryptedCardData = new DecryptedCardData();
                    decryptedCardData.setBrand(decryptString(cd, cd.card().brand()));
                    decryptedCardData.setCardholderName(decryptString(cd, cd.card().cardholderName()));
                    decryptedCardData.setNumber(decryptString(cd, cd.card().number()));
                    decryptedCardData.setExpMonth(decryptString(cd, cd.card().expMonth()));
                    decryptedCardData.setExpYear(decryptString(cd, cd.card().expYear()));
                    decryptedCardData.setCode(decryptString(cd, cd.card().code()));
                    dcd.setCard(decryptedCardData);
                }
                if (cd.identity() != null) {
                    DecryptedIdentityData decryptedIdentityData = new DecryptedIdentityData();
                    decryptedIdentityData.setTitle(decryptString(cd, cd.identity().title()));
                    decryptedIdentityData.setFirstName(decryptString(cd, cd.identity().firstName()));
                    decryptedIdentityData.setMiddleName(decryptString(cd, cd.identity().middleName()));
                    decryptedIdentityData.setLastName(decryptString(cd, cd.identity().lastName()));
                    decryptedIdentityData.setAddress1(decryptString(cd, cd.identity().address1()));
                    decryptedIdentityData.setAddress2(decryptString(cd, cd.identity().address2()));
                    decryptedIdentityData.setAddress3(decryptString(cd, cd.identity().address3()));
                    decryptedIdentityData.setCity(decryptString(cd, cd.identity().city()));
                    decryptedIdentityData.setState(decryptString(cd, cd.identity().state()));
                    decryptedIdentityData.setPostalCode(decryptString(cd, cd.identity().postalCode()));
                    decryptedIdentityData.setCountry(decryptString(cd, cd.identity().country()));
                    decryptedIdentityData.setCompany(decryptString(cd, cd.identity().company()));
                    decryptedIdentityData.setEmail(decryptString(cd, cd.identity().email()));
                    decryptedIdentityData.setPhone(decryptString(cd, cd.identity().phone()));
                    decryptedIdentityData.setSsn(decryptString(cd, cd.identity().ssn()));
                    decryptedIdentityData.setUsername(decryptString(cd, cd.identity().username()));
                    decryptedIdentityData.setPassportNumber(decryptString(cd, cd.identity().passportNumber()));
                    decryptedIdentityData.setLicenseNumber(decryptString(cd, cd.identity().licenseNumber()));
                    dcd.setIdentity(decryptedIdentityData);
                }
                dcd.setNotes(decryptString(localUserKey, localOrganizationKeys, cd, cd.notes()));
                if (cd.fields() != null) {
                    for (FieldData fd : cd.fields()) {
                        DecryptedFieldData dfd = new DecryptedFieldData();
                        dfd.setLinkedId(fd.linkedId());
                        dfd.setType(fd.type());
                        dfd.setName(decryptString(localUserKey, localOrganizationKeys, cd, fd.name()));
                        dfd.setValue(decryptString(localUserKey, localOrganizationKeys, cd, fd.value()));
                        dcd.getFields().add(dfd);
                    }
                }
                if (cd.passwordHistory() != null) {
                    for(PasswordHistoryEntry phe: cd.passwordHistory()) {
                        DecryptedPasswordHistoryEntry dphe = new DecryptedPasswordHistoryEntry();
                        dphe.setLastUsedDate(phe.lastUsedDate());
                        dphe.setPassword(decryptString(localUserKey, localOrganizationKeys, cd, phe.password()));
                        dcd.getPasswordHistory().add(dphe);
                    }
                }
                dcd.setArchivedDate(cd.archivedDate());
                dcd.setCreationDate(cd.creationDate());
                dcd.setDeletedDate(cd.deletedDate());
                dcd.setRevisionDate(cd.revisionDate());
                result.getCiphers().add(dcd);
            } catch (Exception ex) {
                LOG.log(ERROR, (String) null, ex);
            }
        });
        localSyncData.folders().forEach(f -> {
            try {
                DecryptedFolder df = new DecryptedFolder();
                df.setId(f.id());
                df.setName(UtilCryto.decryptString(localUserKey, f.name()));
                df.setRevisionDate(f.revisionDate());
                result.getFolder().add(df);
            } catch (Exception ex) {
                LOG.log(ERROR, (String) null, ex);
            }
        });
        localSyncData.collections().forEach(c -> {
            try {
                DecryptedCollection dc = new DecryptedCollection();
                dc.setId(c.id());
                dc.setOrganizationId(c.organizationId());
                dc.setName(decryptString(localOrganizationKeys, c.organizationId(), c.name()));
                dc.setHidePasswords(c.hidePasswords());
                dc.setManage(c.manage());
                result.getCollections().add(dc);
            } catch (Exception ex) {
                LOG.log(ERROR, (String) null, ex);
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

    public static String decryptString(Map<String, EncryptionKey> organizationKeys, String organizationId, String encryptedString) throws GeneralSecurityException {
        EncryptionKey ek = organizationKeys.get(organizationId);
        return UtilCryto.decryptString(ek, encryptedString);
    }

    private void store() {
        ClientState config = new ClientState();
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
            LOG.log(ERROR, (String) null, ex);
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    /**
     * Logout user and remove saved state
     */
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
            Offline, EnumSet.of(Initial, Syncable),
            Syncable, EnumSet.of(Initial, Syncing),
            Syncing, EnumSet.of(Initial, Syncable, Offline)
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
