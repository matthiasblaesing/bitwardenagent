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

import eu.doppelhelix.app.bitwardenagent.impl.BitwardenAuthenticator;
import eu.doppelhelix.app.bitwardenagent.impl.BitwardenAuthenticator.State;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class AuthenticatorUI extends JPanel {

    private final BitwardenAuthenticator authenticator;

    public AuthenticatorUI(BitwardenAuthenticator authenticator) {
        this.authenticator = authenticator;
        this.setPreferredSize(new Dimension(450, 450));
        this.setLayout(new BorderLayout());
        authenticator.addStateObserver((oldState, newState) -> SwingUtilities.invokeLater(() -> updateVisiblePanel(newState)));
        updateVisiblePanel(authenticator.getState());
    }

    private void updateVisiblePanel(State newState) {
        removeAll();
        switch (newState) {
            case MethodSelection ->
                this.add(new MethodSelectionPanel(authenticator), BorderLayout.CENTER);
            case WaitingForSsoReply ->
                this.add(new WaitingForSsoReplyPanel(authenticator), BorderLayout.CENTER);
            case EmailMasterPass ->
                this.add(new EmailMasterPassPanel(authenticator, false), BorderLayout.CENTER);
            case QueryOTP ->
                this.add(new OTPPanel(authenticator), BorderLayout.CENTER);
            case QueryMasterPassword ->
                this.add(new EmailMasterPassPanel(authenticator, true), BorderLayout.CENTER);
        }
        SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
        });
    }
}
