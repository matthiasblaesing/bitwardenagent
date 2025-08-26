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
import eu.doppelhelix.app.bitwardenagent.http.FieldData;
import eu.doppelhelix.app.bitwardenagent.impl.BitwardenAuthenticator;
import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient;
import eu.doppelhelix.app.bitwardenagent.impl.UtilUI;
import java.awt.BorderLayout;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;


/**
 * Client for Bitwarden Systems
 */
public class BitwardenMain {

    public static void main(String[] args) throws Exception {
        Logger log = Logger.getLogger(BitwardenClient.class.getName());
        log.setLevel(Level.FINEST);

//        for (Handler h : Logger.getLogger("").getHandlers()) {
//            h.setLevel(Level.ALL);
//        }

        FlatLightLaf.setup();

        BitwardenClient bwClient = new BitwardenClient();

        SwingUtilities.invokeLater(() -> {
            BitwardenAuthenticator authenticator = bwClient.createAuthenticator();
            AuthenticatorUI ui = new AuthenticatorUI(authenticator);
            JFrame frame = new JFrame("Bitwarden Login");
            frame.setLayout(new BorderLayout());
            frame.add(ui, BorderLayout.CENTER);
            frame.setSize(450, 450);
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
            authenticator.addStateObserver((oldState, newState) -> {
                if (newState == BitwardenAuthenticator.State.Finished) {
                    UtilUI.runOffTheEdt(
                            () -> {
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
                            },
                            () -> System.exit(0),
                            (ex) -> { ex.printStackTrace(); System.exit(1); }
                    );
                    frame.setVisible(false);
                } else if (newState == BitwardenAuthenticator.State.Canceled) {
                    authenticator.reset();
                }
            });
        });

    }

}
