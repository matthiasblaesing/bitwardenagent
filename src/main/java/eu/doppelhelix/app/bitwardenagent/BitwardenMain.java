/*
 * Copyright 2025 Matthias Bläsing.
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
package eu.doppelhelix.app.bitwardenagent;

import com.formdev.flatlaf.FlatLightLaf;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient;
import eu.doppelhelix.app.bitwardenagent.impl.http.ErrorResult;
import eu.doppelhelix.app.bitwardenagent.impl.http.FieldData;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

import javax.swing.JTextField;

import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.createCodeChallenge;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.generateRandomString;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Client for Bitwarden Systems
 */
public class BitwardenMain {

    public static void main(String[] args) throws Exception {
        Logger log = Logger.getLogger(BitwardenClient.class.getName());
        log.setLevel(Level.FINEST);

        for (Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }

        FlatLightLaf.setup();

        BitwardenClient bwClient = new BitwardenClient();

        if (bwClient.isNeedsUnlocking()) {
            JPasswordField passwordField = new JPasswordField("", 20);
            JPanel panel = new JPanel(new GridBagLayout());
            panel.add(new JLabel("Master Password:"), new GridBagConstraints(0, 4, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
            panel.add(passwordField, new GridBagConstraints(0, 5, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
            int result = JOptionPane.showConfirmDialog(null, panel, "Master Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (result != JOptionPane.OK_OPTION) {
                return;
            }

            char[] password = passwordField.getPassword();
            try {
                bwClient.unlock(passwordField.getPassword());
            } finally {
                Arrays.fill(password, (char) 0);
            }
        }

        if (!bwClient.isAuthenticated()) {
            JComboBox<String> loginSelection = new JComboBox<>(new String[] {"Normal", "SSO"});
            loginSelection.setSelectedIndex(0);

            JTextField serverField = new JTextField(bwClient.getBaseURI().toString(), 20);
            JTextField emailField = new JTextField(bwClient.getEmail(), 20);
            JPasswordField passwordField = new JPasswordField("", 20);
            JPanel panel = new JPanel(new GridBagLayout());
            panel.add(new JLabel("Server URL:"), new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
            panel.add(serverField, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
            panel.add(new JLabel("E-Mail:"), new GridBagConstraints(0, 2, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
            panel.add(emailField, new GridBagConstraints(0, 3, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
            panel.add(new JLabel("Master Password:"), new GridBagConstraints(0, 4, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
            panel.add(passwordField, new GridBagConstraints(0, 5, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
            panel.add(new JLabel("Login-Type:"), new GridBagConstraints(0, 6, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
            panel.add(loginSelection, new GridBagConstraints(0, 7, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
            loginSelection.addActionListener(ae -> {
                emailField.setEnabled(loginSelection.getSelectedIndex() == 0);
            });
            int result = JOptionPane.showConfirmDialog(null, panel, "Master Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);


            if (result != JOptionPane.OK_OPTION) {
                return;
            }

            URI baseUri = URI.create(serverField.getText());
            char[] password = passwordField.getPassword();
            try {
                if (loginSelection.getSelectedIndex() == 0) {
                    try {
                        bwClient.login(baseUri, emailField.getText(), password);
                    } catch (WebApplicationException ex) {
                        if (ex.getResponse().getStatus() == 400) {
                            ErrorResult er = ex.getResponse().readEntity(ErrorResult.class);
                            if ("device_error".equals(er.error())) {
                                String otp = JOptionPane.showInputDialog(null, "Device OTP Code: ", "Device OTP", JOptionPane.QUESTION_MESSAGE);
                                bwClient.login(baseUri, emailField.getText(), password, otp);
                            } else {
                                throw ex;
                            }
                        }
                    }
                } else if (loginSelection.getSelectedIndex() == 1) {
                    int port = 8066;
                    String state = generateRandomString(64);
                    String codeVerifier = generateRandomString(64);
                    String codeChallenge = createCodeChallenge(codeVerifier);
                    String redirectUri = "http://localhost:" + port;

                    URI uri = new URI(
                            baseUri.getScheme(),
                            null,
                            baseUri.getHost(),
                            baseUri.getPort(),
                            "/",
                            null,
                            String.format("/sso?clientId=cli&redirectUri=%s&state=%s&codeChallenge=%s",
                                    redirectUri, state, codeChallenge)
                    );

                    Desktop.getDesktop().browse(uri);

                    AtomicReference<String> codeInput = new AtomicReference<>(null);
                    AtomicReference<String> stateInput = new AtomicReference<>(null);
                    CountDownLatch cdl = new CountDownLatch(1);

                    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
                    HttpContext ctx = server.createContext("/");
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
                                switch(key) {
                                    case "code" -> codeInput.set(value);
                                    case "state" -> stateInput.set(value);
                                }
                            }
                            try (InputStream is = exchange.getRequestBody()) {
                                is.transferTo(System.out);
                            }
                            exchange.getResponseHeaders().add("Content-Type", "text/plain;charset=utf-8");
                            exchange.sendResponseHeaders(200, 0);
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write("You can close the connection now".getBytes(UTF_8));
                            }
                            if(codeInput.get() != null && stateInput.get() != null) {
                                cdl.countDown();
                            }
                        }
                    });
                    server.start();

                    cdl.await();

                    server.stop(0);

                    bwClient.loginSSO(baseUri, password, codeInput.get(), stateInput.get(), codeVerifier, redirectUri);

                }
            } catch (WebApplicationException ex) {
                try {
                    ErrorResult er = ex.getResponse().readEntity(ErrorResult.class);
                    if (er.errorModel() != null && er.errorModel().message() != null) {
                        System.err.println(er.errorModel().message());
                    } else {
                        System.err.println(er.errorDescription());
                    }
                } catch (ProcessingException ex2) {
                    // Ok, not an ErrorResultä-
                    System.err.println(ex.getMessage());
                }
                System.exit(1);
            } finally {
                Arrays.fill(password, (char) 0);
            }
        }

        bwClient.sync();

        bwClient.getSyncData().ciphers()
                .stream()
                .forEach(cd -> {
                    try {
                        System.out.printf("%s (ID: %s)%n", bwClient.decryptString(cd, cd.name()), cd.id());
                        System.out.printf("\tUsername: %s%n", bwClient.decryptString(cd, cd.login().username()));
                        System.out.printf("\tPassword: %s%n", bwClient.decryptString(cd, cd.login().password()));
                        System.out.printf("\tTOTP:     %s%n", bwClient.decryptString(cd, cd.login().totp()));
                        System.out.printf("\tFields:%n");
                        if (cd.data() != null && cd.data().fields() != null && !cd.data().fields().isEmpty()) {
                            for (FieldData fd : cd.data().fields()) {
                                System.out.printf("\t\t%s: %s%n",
                                        bwClient.decryptString(cd, fd.name()),
                                        bwClient.decryptString(cd, fd.value())
                                );
                            }
                        } else {
                            System.out.printf("\t\t-%n");
                        }
                    } catch (Exception ex) {
                        System.getLogger(BitwardenMain.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                    }
                });
    }

}
