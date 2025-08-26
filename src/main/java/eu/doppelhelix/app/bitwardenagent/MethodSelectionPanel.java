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
import eu.doppelhelix.app.bitwardenagent.impl.UtilUI;
import java.awt.Desktop;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class MethodSelectionPanel extends javax.swing.JPanel {

    private static final Logger LOG = System.getLogger(MethodSelectionPanel.class.getName());
    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("eu/doppelhelix/app/bitwardenagent/Bundle");

    private final BitwardenAuthenticator authenticator;

    /**
     * Creates new form MethodSelectionPanel
     */
    public MethodSelectionPanel(BitwardenAuthenticator authenticator) {
        this.authenticator = authenticator;
        initComponents();
        if (authenticator.getBaseURI() != null) {
            String uriString = authenticator.getBaseURI().toString();
            boolean existingUriFound = false;
            for (int i = 0; i < uriInput.getItemCount(); i++) {
                if (uriString.equals(uriInput.getItemAt(i))) {
                    existingUriFound = true;
                }
            }
            if (!existingUriFound) {
                uriInput.insertItemAt(uriString, 0);
                uriInput.setSelectedIndex(0);
            }
        }
        cancelButton.addActionListener((ae) -> {
            setWarnings(null);
            enableInputs(false);
            UtilUI.runOffTheEdt(
                    () -> authenticator.cancel(),
                    () -> enableInputs(true),
                    (exception) -> {
                        enableInputs(true);
                        setWarnings(List.of(RESOURCE_BUNDLE.getString("failedCancel")));
                        LOG.log(Level.WARNING, "Failed to cancel", exception);
                    }
            );
        });
        ssoButton.addActionListener(ae -> {
            validateUriAndRunIfOk((uri) -> {
                UtilUI.runOffTheEdt(
                    () -> authenticator.startSso(uri),
                    (redirectUri) -> {
                        try {
                            Desktop.getDesktop().browse(redirectUri);
                        } catch (IOException ex) {
                            
                        }
                        enableInputs(true);},
                    (exception) -> {
                        enableInputs(true);
                        setWarnings(List.of(RESOURCE_BUNDLE.getString("failedStartSSO")));
                        LOG.log(Level.WARNING, "Failed to start SSO", exception);
                    }
                );
            });
        });
        continueButton.addActionListener(ae -> {
            validateUriAndRunIfOk((uri) -> {
                UtilUI.runOffTheEdt(
                        () -> authenticator.startLogin(uri),
                        () -> enableInputs(true),
                        (exception) -> {
                            enableInputs(true);
                            setWarnings(List.of(RESOURCE_BUNDLE.getString("failedStartLogin")));
                            LOG.log(Level.WARNING, "Failed to start Login", exception);
                        }
                );
            });
        });
        uriInput.addActionListener(ae -> {
            if (uriInput.isEnabled()) {
                enableProgressButtonsIfBaseCheckOk();
            }
        });
        setWarnings(null);
    }

    private void enableProgressButtonsIfBaseCheckOk() {
        String input = (String) uriInput.getSelectedItem();
        boolean valid = false;
        if (input != null && ! input.isBlank()) {
            try {
                URI.create(input);
                valid = true;
            } catch (IllegalArgumentException ex) {
            }
        }
        continueButton.setEnabled(valid);
        ssoButton.setEnabled(valid);
    }

    private void enableInputs(boolean enabled) {
        continueButton.setEnabled(enabled);
        ssoButton.setEnabled(enabled);
        cancelButton.setEnabled(enabled);
        uriInput.setEnabled(enabled);
        if(enabled) {
            enableProgressButtonsIfBaseCheckOk();
        }
    }

    public void setWarnings(Collection<String> texts) {
        StringBuilder message = new StringBuilder();
        if (texts != null && !texts.isEmpty()) {
            message.append("<html>");
            message.append("<ul style='color: #E50808; margin-left: 10px'>");
            for (String text : texts) {
                message.append("<li>");
                message.append(UtilUI.escapeXml(text));
                message.append("</li>");
            }
            message.append("</ul>");
        }
        warningTextPane.setText(message.toString());
    }

    private void validateUriAndRunIfOk(Consumer<URI> r) {
        setWarnings(null);
        enableInputs(false);
        String uriInputString = (String) uriInput.getSelectedItem();
        UtilUI.runOffTheEdt(
                () -> authenticator.validateBaseUri(uriInputString),
                (validationMessages) -> {
                    enableInputs(true);
                    setWarnings(validationMessages);
                    if (validationMessages.isEmpty()) {
                        r.accept(URI.create(uriInputString));
                    }
                },
                (exception) -> {
                    setWarnings(List.of(RESOURCE_BUNDLE.getString("uriValidationFailed")));
                    LOG.log(Level.WARNING, "Failed to validate uri", exception);
                }
        );
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        uriLabel = new javax.swing.JLabel();
        uriInput = new javax.swing.JComboBox<>();
        continueButton = new javax.swing.JButton();
        ssoButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        warningScrollPane = new javax.swing.JScrollPane();
        warningTextPane = new javax.swing.JTextPane();

        setMinimumSize(new java.awt.Dimension(450, 450));
        setPreferredSize(new java.awt.Dimension(450, 450));
        setRequestFocusEnabled(false);
        setLayout(new java.awt.GridBagLayout());

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("eu/doppelhelix/app/bitwardenagent/Bundle"); // NOI18N
        uriLabel.setText(bundle.getString("uriLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        add(uriLabel, gridBagConstraints);

        uriInput.setEditable(true);
        uriInput.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "https://vault.bitwarden.eu", "https://vault.bitwarden.com" }));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        add(uriInput, gridBagConstraints);

        continueButton.setText(bundle.getString("continueButton")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        add(continueButton, gridBagConstraints);

        ssoButton.setText(bundle.getString("ssoButton")); // NOI18N
        ssoButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ssoButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        add(ssoButton, gridBagConstraints);

        cancelButton.setText(bundle.getString("cancelButton")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        add(cancelButton, gridBagConstraints);

        warningTextPane.setEditable(false);
        warningTextPane.setContentType("text/html"); // NOI18N
        warningTextPane.setAlignmentX(0.0F);
        warningTextPane.setAlignmentY(0.0F);
        warningTextPane.setName(""); // NOI18N
        warningScrollPane.setViewportView(warningTextPane);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 150;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        add(warningScrollPane, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void ssoButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ssoButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_ssoButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton continueButton;
    private javax.swing.JButton ssoButton;
    private javax.swing.JComboBox<String> uriInput;
    private javax.swing.JLabel uriLabel;
    private javax.swing.JScrollPane warningScrollPane;
    private javax.swing.JTextPane warningTextPane;
    // End of variables declaration//GEN-END:variables
}
