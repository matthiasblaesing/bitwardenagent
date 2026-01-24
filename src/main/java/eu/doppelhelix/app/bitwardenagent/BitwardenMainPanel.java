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

import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient;
import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State;
import java.awt.BorderLayout;
import java.util.EnumSet;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Offline;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Syncable;
import static eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient.State.Syncing;

public class BitwardenMainPanel extends JPanel {

    private static final EnumSet PASSWORD_PANEL_STATES = EnumSet.of(Offline, Syncable, Syncing);
    private final BitwardenClient client;
    private final LoginAction authenticationAction;

    public BitwardenMainPanel(LoginAction authenticationAction, BitwardenClient client, JMenuBar menubar) {
        this.authenticationAction = authenticationAction;
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
            }
        } else {
            removeAll();
            switch(newState) {
                case Started -> add(new StartenPanel(authenticationAction, false));
                case Initial -> add(new StartenPanel(authenticationAction, true));
                case LocalStatePresent -> add(new UnlockPanel(client));
            }
        }

        revalidate();
        repaint();
    }
}