/*
 * Copyright 2026 matthias.
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
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class StatusBar extends JPanel {

    public StatusBar(BitwardenClient client) {
        JLabel statusLabel = new JLabel(client.getState().toLocaleString());
        statusLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        client.addStateObserver((oldState, newState) -> {
            SwingUtilities.invokeLater(() -> statusLabel.setText(newState.toLocaleString()));
        });
        setPreferredSize(new Dimension(16, 26));
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        add(statusLabel);
    }

}
