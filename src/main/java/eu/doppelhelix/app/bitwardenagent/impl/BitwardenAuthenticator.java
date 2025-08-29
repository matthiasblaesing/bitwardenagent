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

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import eu.doppelhelix.app.bitwardenagent.MethodSelectionPanel;
import eu.doppelhelix.app.bitwardenagent.http.ConfigResponse;
import eu.doppelhelix.app.bitwardenagent.http.ErrorResult;
import eu.doppelhelix.app.bitwardenagent.http.PreloginRequest;
import eu.doppelhelix.app.bitwardenagent.http.PreloginResult;
import eu.doppelhelix.app.bitwardenagent.http.TokenResult;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger.Level;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenAuthenticator.State.*;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.createCodeChallenge;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.deriveMasterKey;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.deriveMasterKeyHash;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.encryptionKeyFromMasterKey;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.generateRandomString;
import static java.nio.charset.StandardCharsets.UTF_8;

public class BitwardenAuthenticator {

    public enum State {
        MethodSelection,
        WaitingForSsoReply,
        QueryMasterPassword,
        EmailMasterPass,
        Select2FA,
        EmailOTP,
        TOTPInput,
        QueryOTP,
        Finished,
        Canceled
    }

    public interface StateObserver {
        public void stateChanged(State oldState, State newState);
    }

    private static final System.Logger LOG = System.getLogger(BitwardenAuthenticator.class.getName());
    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("eu/doppelhelix/app/bitwardenagent/Bundle", Locale.getDefault(), MethodSelectionPanel.class.getClassLoader());
    private final List<StateObserver> stateObserver = new CopyOnWriteArrayList<>();
    private final BitwardenClient client;
    private URI baseURI;
    private State state;
    private HttpServer httpServer;
    private String email;
    private EncryptionKey stretchedMasterKey;
    private String masterPasswordHash;
    private PreloginResult preloginResult;
    private TokenResult loginResponse;

    public URI getBaseURI() {
        return baseURI;
    }

    BitwardenAuthenticator(BitwardenClient client) {
        this.client = client;
        this.state = State.MethodSelection;
        this.baseURI = client.getBaseURI();
    }

    public Set<String> validateBaseUri(String uriToCheckString) {
        Set<String> errors = new HashSet<>();
        try {
            URI uriToCheck = new URI(uriToCheckString);
            ConfigResponse response = client.getConfig(uriToCheck);
            if(response == null || response.version() == null) {
                errors.add(RESOURCE_BUNDLE.getString("uriConnectionError"));
            }
        } catch (WebApplicationException | ProcessingException ex) {
            LOG.log(Level.INFO, ex);
            errors.add(RESOURCE_BUNDLE.getString("uriConnectionError"));
        } catch (URISyntaxException ex) {
            LOG.log(Level.INFO, ex);
            errors.add(RESOURCE_BUNDLE.getString("uriValidationFailed"));
        }
        return errors;
    }

    public void startLogin(URI newBaseUri) throws IOException, URISyntaxException {
        baseURI = newBaseUri;
        setState(EmailMasterPass);
    }

    public URI startSso(URI newBaseUri) throws IOException, URISyntaxException {
        try {
            AtomicReference<String> codeInput = new AtomicReference<>(null);
            AtomicReference<String> stateInput = new AtomicReference<>(null);
            AtomicReference<String> redirectUri = new AtomicReference<>(null);
            String authState = generateRandomString(64);
            String codeVerifier = generateRandomString(64);

            HttpServer httpServerBuilder = HttpServer.create();
            HttpContext ctx = httpServerBuilder.createContext("/");
            ctx.setHandler(new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    System.out.println(exchange.getRequestMethod());
                    System.out.println(exchange.getRequestURI());
                    String[] queryElements = exchange.getRequestURI().getRawQuery().split("&");
                    for (String queryElement : queryElements) {
                        String[] parts = queryElement.split("=");
                        String key;
                        String value;
                        if (parts.length > 1) {
                            key = URLDecoder.decode(parts[0], UTF_8);
                            value = URLDecoder.decode(parts[1], UTF_8);
                        } else {
                            key = URLDecoder.decode(parts[0], UTF_8);
                            value = null;
                        }
                        switch (key) {
                            case "code" ->
                                codeInput.set(value);
                            case "state" ->
                                stateInput.set(value);
                        }
                    }
                    // State is modified by Bitwarden server
                    if (codeInput.get() != null && stateInput.get() != null && stateInput.get().startsWith(authState)) {
                        try {
                            loginResponse = client.loginSSO(baseURI, codeInput.get(), codeVerifier, redirectUri.get());
                        } catch (Exception ex) {
                            LOG.log(Level.ERROR, (String) null, ex);
                        }
                    }
                    try (InputStream is = exchange.getRequestBody()) {
                        is.transferTo(System.out);
                    }
                    exchange.getResponseHeaders().add("Content-Type", "text/plain;charset=utf-8");
                    exchange.sendResponseHeaders(200, 0);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write("You can close the windows now".getBytes(UTF_8));
                    }
                    if (codeInput.get() != null && stateInput.get() != null) {
                        httpServerBuilder.stop(0);
                    }
                    setState(QueryMasterPassword);
                }
            });

            URI returnUri = null;
            String redirectUriBuilder = null;

            // Server rejects requests from ports below 8065
            for (int port = 8065; port < 9000; port++) {
                try {
                    httpServerBuilder.bind(new InetSocketAddress("localhost", port), 0);

                    redirectUriBuilder = "http://localhost:" + port;

                    returnUri = new URI(
                            newBaseUri.getScheme(),
                            null,
                            newBaseUri.getHost(),
                            newBaseUri.getPort(),
                            "/",
                            null,
                            String.format("/sso?clientId=cli&redirectUri=%s&state=%s&codeChallenge=%s",
                                    redirectUriBuilder, authState, createCodeChallenge(codeVerifier))
                    );

                    break;
                } catch (BindException ex) {
                    int finalPort = port;
                    LOG.log(Level.DEBUG, () -> "Failed to bind to port " + finalPort, ex);
                    if (port == 9000 - 1) {
                        throw ex;
                    }
                }
            }

            httpServerBuilder.start();

            httpServer = httpServerBuilder;
            baseURI = newBaseUri;
            redirectUri.set(redirectUriBuilder);

            setState(WaitingForSsoReply);

            return returnUri;
        } catch (IOException | URISyntaxException | RuntimeException ex) {
            LOG.log(Level.WARNING, "Failed to setup sso", ex);
            throw ex;
        }
    }

    public void setEmailMasterPass(String emailInput, char[] password) throws GeneralSecurityException, IllegalStateException {
        WebTarget baseTarget = client.getClient().target(baseURI);

        PreloginResult preloginResultBuilder = baseTarget
                .path("identity/accounts/prelogin")
                .request()
                .post(Entity.json(new PreloginRequest(emailInput)), PreloginResult.class);

        byte[] masterKeyBuilder = deriveMasterKey(password, emailInput, preloginResultBuilder);
        EncryptionKey stretchedMasterKeyBuilder = encryptionKeyFromMasterKey(masterKeyBuilder);
        String masterPasswordHashBuilder = deriveMasterKeyHash(masterKeyBuilder, password);

        TokenResult loginResponse;
        try {
            loginResponse = baseTarget
                    .path("identity/connect/token")
                    .request()
                    .header("Device-Type", 25)
                    .post(Entity.form(client.tokenRequestPwd(emailInput, masterPasswordHashBuilder, null)), TokenResult.class);
        } catch (WebApplicationException ex) {
            if (ex.getResponse().getStatus() == 400) {
                ErrorResult er = ex.getResponse().readEntity(ErrorResult.class);
                if ("device_error".equals(er.error())) {
                    email = emailInput;
                    stretchedMasterKey = stretchedMasterKeyBuilder;
                    masterPasswordHash = masterPasswordHashBuilder;
                    preloginResult = preloginResultBuilder;
                    setState(QueryOTP);
                    return;
                } else {
                    throw ex;
                }
            }
            throw ex;
        }

        client.login(baseURI, emailInput, stretchedMasterKeyBuilder, masterPasswordHashBuilder, loginResponse.refreshToken(), preloginResultBuilder);

        setState(Finished);
    }

    public void setEmailMasterPassSSO(String email, char[] password) throws GeneralSecurityException, IllegalStateException {
        WebTarget baseTarget = client.getClient().target(baseURI);

        PreloginResult preloginResultBuilder = baseTarget
                .path("identity/accounts/prelogin")
                .request()
                .post(Entity.json(new PreloginRequest(email)), PreloginResult.class);

        byte[] masterKeyBuilder = deriveMasterKey(password, email, preloginResultBuilder);
        EncryptionKey stretchedMasterKeyBuilder = encryptionKeyFromMasterKey(masterKeyBuilder);
        String masterPasswordHashBuilder = deriveMasterKeyHash(masterKeyBuilder, password);

        client.login(baseURI, email, stretchedMasterKeyBuilder, masterPasswordHashBuilder, loginResponse.refreshToken(), preloginResultBuilder);

        setState(Finished);
    }

    public void setDeviceOTP(String otp) throws GeneralSecurityException {
        WebTarget baseTarget = client.getClient().target(baseURI);

        TokenResult loginResponse = baseTarget
                .path("identity/connect/token")
                .request()
                .header("Device-Type", 25)
                .post(Entity.form(client.tokenRequestPwd(email, masterPasswordHash, otp)), TokenResult.class);

        client.login(baseURI, email, stretchedMasterKey, masterPasswordHash, loginResponse.refreshToken(), preloginResult);

        setState(Finished);
    }

    public void cancel() {
        setState(Canceled);
    }

    public void reset() {
        this.email = null;
        this.stretchedMasterKey = null;
        this.masterPasswordHash = null;
        this.preloginResult = null;
        this.loginResponse = null;
        setState(MethodSelection);
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
            MethodSelection, EnumSet.of(WaitingForSsoReply, EmailMasterPass),
            WaitingForSsoReply, EnumSet.of(MethodSelection, QueryMasterPassword),
            EmailMasterPass, EnumSet.of(QueryOTP, Finished),
            QueryMasterPassword, EnumSet.of(Finished),
            QueryOTP, EnumSet.of(Finished),
            Canceled, EnumSet.of(MethodSelection)
    );

    private void setState(State newState) {
        if(newState == state) {
            return;
        }
        boolean allowedTransition = ALLOWED_TRANSITIONS
                .getOrDefault(state, EnumSet.noneOf(State.class))
                .contains(newState);
        if (newState != Canceled && !allowedTransition) {
            throw new IllegalStateException(String.format("Transition not allowed: %s -> %s", state, newState));
        }

        if (newState != WaitingForSsoReply && httpServer != null) {
            try {
                HttpServer localHttpServer = httpServer;
                httpServer = null;
                localHttpServer.stop(0);
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Failed to cancel", ex);
            }
        }

        if (newState == Finished) {

        }

        State oldState = state;
        state = newState;
        stateObserver.forEach(so -> so.stateChanged(oldState, newState));
    }
}
