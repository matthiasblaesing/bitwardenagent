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
import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient;
import eu.doppelhelix.app.bitwardenagent.impl.http.ErrorResult;
import eu.doppelhelix.app.bitwardenagent.impl.http.FieldData;
import eu.doppelhelix.app.bitwardenagent.impl.http.SyncData;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * Client for Bitwarden Systems
 */
public class BitwardenMain {

    public static void main(String[] args) throws Exception {
        Logger log = Logger.getLogger(BitwardenClient.class.getName());
        log.setLevel(Level.FINEST);

        for(Handler h: Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }

        FlatLightLaf.setup();

        JTextField serverField = new JTextField("https://vault.bitwarden.eu/", 20);
        JTextField emailField = new JTextField("", 20);
        JPasswordField passwordField = new JPasswordField("", 20);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(new JLabel("Server URL:"), new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
        panel.add(serverField, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
        panel.add(new JLabel("E-Mail:"), new GridBagConstraints(0, 2, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
        panel.add(emailField, new GridBagConstraints(0, 3, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
        panel.add(new JLabel("Master Password:"), new GridBagConstraints(0, 4, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
        panel.add(passwordField, new GridBagConstraints(0, 5, 1, 1, 1, 0, GridBagConstraints.BASELINE_LEADING, 1, new Insets(5, 5, 5, 5), 0, 0));
        int result = JOptionPane.showConfirmDialog(null, panel, "Master Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        BitwardenClient bwClient = new BitwardenClient(
                UUID.fromString("7c446598-e200-4293-aabd-274874f78aad"),
                null
        );

        char[] password = passwordField.getPassword();
        try {
            try {
                bwClient.login(emailField.getText(), password);
            } catch (WebApplicationException ex) {
                if (ex.getResponse().getStatus() == 400) {
                    ErrorResult er = ex.getResponse().readEntity(ErrorResult.class);
                    if ("device_error".equals(er.error())) {
                        String otp = JOptionPane.showInputDialog(null, "Device OTP Code: ", "Device OTP", JOptionPane.QUESTION_MESSAGE);
                        bwClient.login(emailField.getText(), password, otp);
                    } else {
                        throw ex;
                    }
                }
            } finally {
                Arrays.fill(password, (char) 0);
            }
        } catch (WebApplicationException ex) {
            try {
                ErrorResult er = ex.getResponse().readEntity(ErrorResult.class);
                if(er.ErrorModel() != null && er.ErrorModel().Message() != null) {
                    System.err.println(er.ErrorModel().Message());
                } else {
                    System.err.println(er.error_description());
                }
            } catch (ProcessingException ex2) {
                // Ok, not an ErrorResult
                System.err.println(ex.getMessage());
            }
            System.exit(1);
        }

        SyncData sync = bwClient.sync();

        sync.ciphers()
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
