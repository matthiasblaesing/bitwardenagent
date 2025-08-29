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
package eu.doppelhelix.app.bitwardenagent;

import eu.doppelhelix.app.bitwardenagent.http.FieldData;
import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient;
import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State;
import eu.doppelhelix.app.bitwardenagent.impl.UtilUI;
import java.awt.BorderLayout;
import java.util.EnumSet;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Offline;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Syncable;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Syncing;

public class BitwardenMainPanel extends JPanel {

    private static final EnumSet PASSWORD_PANEL_STATES = EnumSet.of(Offline, Syncable, Syncing);
    private final BitwardenClient client;

    public BitwardenMainPanel(BitwardenClient client) {
        this.client = client;
        this.setLayout(new BorderLayout());
        client.addStateObserver((oldState, newState) -> SwingUtilities.invokeLater(() -> updateVisiblePanel(newState)));
        updateVisiblePanel(client.getState());
    }

    private void updateVisiblePanel(State newState) {
        if (PASSWORD_PANEL_STATES.contains(newState)) {
            if (!(getComponent(0) instanceof PasswordListPanel)) {
                removeAll();
                add(new PasswordListPanel(client));
                UtilUI.runOffTheEdt(
                        () -> {
                            client.sync();
                            client.getSyncData().ciphers()
                                    .stream()
                                    .forEach(cd -> {
                                        try {
                                            System.out.printf("%s (ID: %s)%n", client.decryptString(cd, cd.name()), cd.id());
                                            if (cd.login() != null) {
                                                System.out.printf("\tUsername: %s%n", client.decryptString(cd, cd.login().username()));
                                                System.out.printf("\tPassword: %s%n", client.decryptString(cd, cd.login().password()));
                                                System.out.printf("\tTOTP:     %s%n", client.decryptString(cd, cd.login().totp()));
                                            }
                                            System.out.printf("\tFields:%n");
                                            if (cd.fields() != null && !cd.fields().isEmpty()) {
                                                for (FieldData fd : cd.fields()) {
                                                    System.out.printf("\t\t%2$s (%1$s - %4$s): %3$s%n",
                                                            fd.type(),
                                                            client.decryptString(cd, fd.name()),
                                                            client.decryptString(cd, fd.value()),
                                                            fd.linkedId()
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
                        (ex) -> {
                            ex.printStackTrace();
                            System.exit(1);
                        }
                );
            }
        } else {
            removeAll();
            switch(newState) {
                case Started -> add(new StartenPanel(client, false));
                case Initial -> add(new StartenPanel(client, true));
                case LocalStatePresent -> add(new UnlockPanel(client));
            }
        }

        SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
        });
    }
}