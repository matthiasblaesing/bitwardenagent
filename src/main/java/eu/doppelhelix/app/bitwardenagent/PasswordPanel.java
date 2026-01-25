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

import eu.doppelhelix.app.bitwardenagent.http.FieldType;
import eu.doppelhelix.app.bitwardenagent.http.LinkedId;
import eu.doppelhelix.app.bitwardenagent.http.UriMatchType;
import eu.doppelhelix.app.bitwardenagent.impl.CopyFieldAction;
import eu.doppelhelix.app.bitwardenagent.impl.DecryptedCipherData;
import eu.doppelhelix.app.bitwardenagent.impl.DecryptedFieldData;
import eu.doppelhelix.app.bitwardenagent.impl.DecryptedPasswordHistoryEntry;
import eu.doppelhelix.app.bitwardenagent.impl.DecryptedUriData;
import eu.doppelhelix.app.bitwardenagent.impl.LinkedIdListCellRenderer;
import eu.doppelhelix.app.bitwardenagent.impl.TOTPUtil;
import eu.doppelhelix.app.bitwardenagent.impl.UriMatchTypeListCellRenderer;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Comparator;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignE;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;

import static eu.doppelhelix.app.bitwardenagent.Configuration.PROP_ALLOW_ACCESS;
import static eu.doppelhelix.app.bitwardenagent.Configuration.PROP_ALLOW_ALL_ACCESS;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilUI.FOLDER_ICON;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilUI.OFFICE_BUILDING_ICON;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilUI.createIcon;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilUI.emptyNullToSpace;
import static java.awt.GridBagConstraints.BASELINE_LEADING;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilUI.FOLDER_NETWORK_ICON;


public class PasswordPanel extends javax.swing.JPanel {

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("eu/doppelhelix/app/bitwardenagent/Bundle");

    private static final ImageIcon OPEN_EYE_ICON = createIcon(MaterialDesignE.EYE, 20);
    private static final ImageIcon CLOSED_EYE_ICON = createIcon(MaterialDesignE.EYE_OFF, 20);
    private static final ImageIcon COPY_ICON = createIcon(MaterialDesignC.CONTENT_COPY, 20);
    private static final ImageIcon WRENCH_ICON = createIcon(MaterialDesignW.WRENCH_CHECK, 20);
    private static final ImageIcon WRENCH_CHECK_ICON = createIcon(MaterialDesignW.WRENCH_CHECK, 20);
    private static final ImageIcon LINK_ICON = createIcon(MaterialDesignL.LINK_VARIANT, 16);

    private static final System.Logger LOG = System.getLogger(PasswordPanel.class.getName());

    private final List<Component> additionalComponents = new ArrayList<>();
    private final Timer totpTimer = new Timer(5000, ae -> updateTotpEvaluated());
    private char passwordMask;
    private Set<String> allowAccess = Collections.emptySet();
    private DecryptedCipherData decryptedCipherData;

    public PasswordPanel() {
        initComponents();
        passwordPanelScrollPane.getHorizontalScrollBar().setUnitIncrement(getFont().getSize());
        passwordPanelScrollPane.getVerticalScrollBar().setUnitIncrement(getFont().getSize());

        copyIdButton.addActionListener(new CopyFieldAction(idField));
        copyUsernameButton.addActionListener(new CopyFieldAction(usernameField));
        copyPasswordButton.addActionListener(new CopyFieldAction(passwordField));
        copyTotpButton.addActionListener(new CopyFieldAction(totpField));
        copyTotpEvaluatedButton.addActionListener(new CopyFieldAction(totpEvaluatedField));
        copySshPrivateKey.addActionListener(new CopyFieldAction(sshPrivateKeyField));
        copySshPublicKey.addActionListener(new CopyFieldAction(sshPublicKeyField));
        copySshFingerprint.addActionListener(new CopyFieldAction(sshFingerprintField));
        copyNotes.addActionListener(new CopyFieldAction(notesField));
        passwordMask = passwordField.getEchoChar();
        passwordVisible.addActionListener(ae -> {
            passwordField.setEchoChar(passwordVisible.isSelected() ? '\u0000' : passwordMask);
        });
        totpVisibleButton.addActionListener(ae -> {
            totpField.setEchoChar(totpVisibleButton.isSelected() ? '\u0000' : passwordMask);
        });
        Configuration.getConfiguration().addObserver((name, value) -> {
            if(PROP_ALLOW_ACCESS.equals(name)) {
                allowAccess = new HashSet<>((Collection<String>) value);
                updateAllowAccessCheckbox();
            }
        });
        Configuration.getConfiguration().addObserver((name, value) -> {
            if(PROP_ALLOW_ALL_ACCESS.equals(name)) {
                updateAllowAccessCheckbox();
            }
        });
        allowAccess = new HashSet<>(Configuration.getConfiguration().getAllowAccess());
        allowAccessCheckbox.addActionListener(ae -> {
            if (allowAccessCheckbox.isSelected()) {
                Configuration.getConfiguration().addAllowAccess(decryptedCipherData.getId());
            } else {
                Configuration.getConfiguration().removeAllowAccess(decryptedCipherData.getId());
            }
        });
    }

    public DecryptedCipherData getDecryptedCipherData() {
        return this.decryptedCipherData;
    }

    public void setDecryptedCipherData(DecryptedCipherData decryptedCipherData) {
        DecryptedCipherData old = this.decryptedCipherData;
        this.decryptedCipherData = decryptedCipherData;
        copyUsernameButton.setEnabled(false);
        copyPasswordButton.setEnabled(false);
        copyTotpButton.setEnabled(false);
        copyTotpEvaluatedButton.setEnabled(false);
        idField.setText("");
        usernameField.setText("");
        passwordField.setText("");
        totpField.setText("");
        totpEvaluatedField.setText("");
        sshPrivateKeyField.setText("");
        sshPublicKeyField.setText("");
        sshFingerprintField.setText("");
        showLoginFields(false);
        showSshFields(false);
        for(Component c: locationInfoWrapper.getComponents()) {
            if(c != locationInfoPlaceholder) {
                locationInfoWrapper.remove(c);
            }
        }
        passwordTitle.setText(emptyNullToSpace(null));
        if (this.decryptedCipherData != null) {
            idField.setText(decryptedCipherData.getId());
            passwordTitle.setText(emptyNullToSpace(decryptedCipherData.getName()));
            if(decryptedCipherData.getFolder() != null && ! decryptedCipherData.getFolder().isBlank()) {
                JLabel label = new JLabel(decryptedCipherData.getFolder(), FOLDER_ICON, JLabel.LEADING);
                label.setFont(label.getFont().deriveFont(Font.ITALIC));
                locationInfoWrapper.add(label, locationInfoWrapper.getComponents().length - 1);
            }
            if(decryptedCipherData.getOrganization() != null && ! decryptedCipherData.getOrganization().isBlank()) {
                JLabel label = new JLabel(decryptedCipherData.getOrganization(), OFFICE_BUILDING_ICON, JLabel.LEADING);
                label.setFont(label.getFont().deriveFont(Font.ITALIC));
                locationInfoWrapper.add(label, locationInfoWrapper.getComponents().length - 1);
            }
            for (String collection : decryptedCipherData.getCollections()) {
                JLabel label = new JLabel(collection, FOLDER_NETWORK_ICON, JLabel.LEADING);
                label.setFont(label.getFont().deriveFont(Font.ITALIC));
                locationInfoWrapper.add(label);
            }
            notesField.setText(decryptedCipherData.getNotes());
            notesField.setCaretPosition(0);
            if(decryptedCipherData.getLogin() != null) {
                showLoginFields(true);
                usernameField.setText(decryptedCipherData.getLogin().getUsername());
                usernameField.setCaretPosition(0);
                passwordField.setText(decryptedCipherData.getLogin().getPassword());
                passwordField.setCaretPosition(0);
                totpField.setText(decryptedCipherData.getLogin().getTotp());
                totpField.setCaretPosition(0);
                if(isNotNullNotEmpty(decryptedCipherData.getLogin().getUsername())) {
                    copyUsernameButton.setEnabled(true);
                }
                if(isNotNullNotEmpty(decryptedCipherData.getLogin().getPassword())) {
                    copyPasswordButton.setEnabled(true);
                }
                if(isNotNullNotEmpty(decryptedCipherData.getLogin().getTotp())) {
                    copyTotpButton.setEnabled(true);
                }
            } else if (decryptedCipherData.getSshKey() != null) {
                showSshFields(true);
                sshPrivateKeyField.setText(decryptedCipherData.getSshKey().getPrivateKey());
                sshPrivateKeyField.setCaretPosition(0);
                sshPublicKeyField.setText(decryptedCipherData.getSshKey().getPublicKey());
                sshPublicKeyField.setCaretPosition(0);
                sshFingerprintField.setText(decryptedCipherData.getSshKey().getKeyFingerprint());
                sshFingerprintField.setCaretPosition(0);
                if(isNotNullNotEmpty(decryptedCipherData.getSshKey().getPrivateKey())) {
                    copySshPrivateKey.setEnabled(true);
                }
                if(isNotNullNotEmpty(decryptedCipherData.getSshKey().getPublicKey())) {
                    copySshPublicKey.setEnabled(true);
                }
                if(isNotNullNotEmpty(decryptedCipherData.getSshKey().getKeyFingerprint())) {
                    copySshFingerprint.setEnabled(true);
                }
            }
        }

        updateTotpEvaluated();
        int componentRow = 12;
        additionalComponents.forEach(c -> passwordPanel.remove(c));

        Insets defaultInsets = new Insets(5, 5, 5, 5);
        if (this.decryptedCipherData != null
                && this.decryptedCipherData.getLogin() != null
                && this.decryptedCipherData.getLogin().getUriData() != null) {
            for(DecryptedUriData ud: this.decryptedCipherData.getLogin().getUriData()) {
                JLabel label = new JLabel("Website:");
                additionalComponents.add(label);
                passwordPanel.add(label, new GridBagConstraints(0, componentRow, 2, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
                JTextField uriField = new JTextField();
                uriField.setColumns(25);
                uriField.setEditable(false);
                uriField.setText(ud.getUri());
                JComboBox<UriMatchType> combobox = new JComboBox<>(UriMatchType.values());
                combobox.setRenderer(new UriMatchTypeListCellRenderer());
                combobox.setEnabled(false);
                combobox.setSelectedItem(ud.getMatch());
                JButton copyButton = buildCopyButton(uriField);
                JToggleButton toggleVisibilityButton = new JToggleButton();
                toggleVisibilityButton.setIcon(WRENCH_ICON);
                toggleVisibilityButton.setMaximumSize(new java.awt.Dimension(24, 24));
                toggleVisibilityButton.setMinimumSize(new java.awt.Dimension(24, 24));
                toggleVisibilityButton.setPreferredSize(new java.awt.Dimension(24, 24));
                toggleVisibilityButton.setSelectedIcon(WRENCH_CHECK_ICON);
                toggleVisibilityButton.addActionListener(ae -> {
                    combobox.setVisible(toggleVisibilityButton.isSelected());
                    this.revalidate();
                });
                JButton openLink = new JButton();
                openLink.setIcon(LINK_ICON);
                openLink.setMinimumSize(new Dimension(24, 24));
                openLink.setPreferredSize(new Dimension(24, 24));
                openLink.setMaximumSize(new Dimension(24, 24));
                openLink.addActionListener(ae -> {
                    try {
                        Desktop.getDesktop().browse(URI.create(ud.getUri()));
                    } catch (IOException | IllegalArgumentException ex) {
                        LOG.log(System.Logger.Level.ERROR, (String) null, ex);
                    }
                });
                additionalComponents.add(uriField);
                additionalComponents.add(copyButton);
                additionalComponents.add(toggleVisibilityButton);
                additionalComponents.add(openLink);
                passwordPanel.add(copyButton, new GridBagConstraints(3, componentRow, 1, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
                passwordPanel.add(openLink, new GridBagConstraints(4, componentRow, 1, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
                passwordPanel.add(toggleVisibilityButton, new GridBagConstraints(5, componentRow, 1, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
                passwordPanel.add(uriField, new GridBagConstraints(1, componentRow, 2, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.HORIZONTAL, defaultInsets, 0, 0));
                componentRow++;
                additionalComponents.add(combobox);
                passwordPanel.add(combobox, new GridBagConstraints(1, componentRow, 2, 1, 1, 0, BASELINE_LEADING, GridBagConstraints.HORIZONTAL, defaultInsets, 0, 0));
                combobox.setVisible(false);
                componentRow++;
            }
        }

        if (this.decryptedCipherData != null && this.decryptedCipherData.getFields() != null) {
            for (int i = 0; i < this.decryptedCipherData.getFields().size(); i++) {
                DecryptedFieldData dfd = this.decryptedCipherData.getFields().get(i);
                JLabel label = new JLabel(dfd.getName());
                additionalComponents.add(label);
                passwordPanel.add(label, new GridBagConstraints(0, componentRow, 1, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
                if (dfd.getType() == FieldType.TEXT) {
                    JTextField textField = new JTextField();
                    textField.setColumns(25);
                    textField.setEditable(false);
                    textField.setText(dfd.getValue());
                    JButton copyButton = buildCopyButton(textField);
                    additionalComponents.add(textField);
                    additionalComponents.add(copyButton);
                    passwordPanel.add(copyButton, new GridBagConstraints(3, componentRow, 1, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
                    passwordPanel.add(textField, new GridBagConstraints(1, componentRow, 2, 1, 1, 0, BASELINE_LEADING, GridBagConstraints.HORIZONTAL, defaultInsets, 0, 0));
                } else if (dfd.getType() == FieldType.HIDDEN) {
                    JPasswordField passwordField = new JPasswordField();
                    passwordField.setColumns(25);
                    passwordField.setEditable(false);
                    passwordField.setText(dfd.getValue());
                    JButton copyButton = buildCopyButton(passwordField);
                    JToggleButton toggleVisibilityButton = new JToggleButton();
                    toggleVisibilityButton.setIcon(CLOSED_EYE_ICON);
                    toggleVisibilityButton.setMaximumSize(new java.awt.Dimension(24, 24));
                    toggleVisibilityButton.setMinimumSize(new java.awt.Dimension(24, 24));
                    toggleVisibilityButton.setPreferredSize(new java.awt.Dimension(24, 24));
                    toggleVisibilityButton.setSelectedIcon(OPEN_EYE_ICON);
                    toggleVisibilityButton.addActionListener(ae -> {
                        passwordField.setEchoChar(toggleVisibilityButton.isSelected() ? '\u0000' : passwordMask);
                    });
                    additionalComponents.add(passwordField);
                    additionalComponents.add(copyButton);
                    additionalComponents.add(toggleVisibilityButton);
                    passwordPanel.add(copyButton, new GridBagConstraints(3, componentRow, 1, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
                    passwordPanel.add(toggleVisibilityButton, new GridBagConstraints(4, componentRow, 1, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
                    passwordPanel.add(passwordField, new GridBagConstraints(1, componentRow, 2, 1, 1, 0, BASELINE_LEADING, GridBagConstraints.HORIZONTAL, defaultInsets, 0, 0));
                } else if (dfd.getType() == FieldType.CHECKBOX) {
                    JCheckBox checkbox = new JCheckBox();
                    checkbox.setEnabled(false);
                    checkbox.setSelected(Boolean.parseBoolean(dfd.getValue()));
                    additionalComponents.add(checkbox);
                    passwordPanel.add(checkbox, new GridBagConstraints(1, componentRow, 2, 1, 1, 0, BASELINE_LEADING, GridBagConstraints.HORIZONTAL, defaultInsets, 0, 0));
                } else if (dfd.getType() == FieldType.LINKED) {
                    JComboBox<LinkedId> combobox = new JComboBox<>(LinkedId.values());
                    combobox.setRenderer(new LinkedIdListCellRenderer());
                    combobox.setEnabled(false);
                    combobox.setSelectedItem(dfd.getLinkedId());
                    additionalComponents.add(combobox);
                    passwordPanel.add(combobox, new GridBagConstraints(1, componentRow, 2, 1, 1, 0, BASELINE_LEADING, GridBagConstraints.HORIZONTAL, defaultInsets, 0, 0));
                }
                componentRow++;
            }
        }

        if (decryptedCipherData != null) {
            JLabel entryHistoryLbl = new JLabel(RESOURCE_BUNDLE.getString("entryHistory"));
            entryHistoryLbl.setFont(entryHistoryLbl.getFont().deriveFont(Font.BOLD));
            passwordPanel.add(entryHistoryLbl, new GridBagConstraints(0, componentRow, 6, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.HORIZONTAL, defaultInsets, 0, 0));
            additionalComponents.add(entryHistoryLbl);
            componentRow++;

            JLabel revisionDateLbl = new JLabel(RESOURCE_BUNDLE.getString("entryHistory.revisionDate"));
            passwordPanel.add(revisionDateLbl, new GridBagConstraints(0, componentRow, 1, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
            additionalComponents.add(revisionDateLbl);
            JLabel revisionDateValue = new JLabel(formatLocalDate(decryptedCipherData.getRevisionDate()));
            passwordPanel.add(revisionDateValue, new GridBagConstraints(1, componentRow, 2, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.HORIZONTAL, defaultInsets, 0, 0));
            additionalComponents.add(revisionDateValue);
            componentRow++;

            JLabel creationDateLbl = new JLabel(RESOURCE_BUNDLE.getString("entryHistory.creationDate"));
            passwordPanel.add(creationDateLbl, new GridBagConstraints(0, componentRow, 1, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
            additionalComponents.add(creationDateLbl);
            JLabel creationDateValue = new JLabel(formatLocalDate(decryptedCipherData.getCreationDate()));
            passwordPanel.add(creationDateValue, new GridBagConstraints(1, componentRow, 2, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.HORIZONTAL, defaultInsets, 0, 0));
            additionalComponents.add(creationDateValue);
            componentRow++;

            OffsetDateTime passwordUpdated = decryptedCipherData
                    .getPasswordHistory()
                    .stream()
                    .map(entry -> entry.getLastUsedDate())
                    .max(Comparator.naturalOrder())
                    .orElse(decryptedCipherData.getCreationDate());

            JLabel passwordUpdatedLbl = new JLabel(RESOURCE_BUNDLE.getString("entryHistory.passwordUpdated"));
            passwordPanel.add(passwordUpdatedLbl, new GridBagConstraints(0, componentRow, 1, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
            additionalComponents.add(passwordUpdatedLbl);
            JLabel passwordUpdatedValue = new JLabel(formatLocalDate(passwordUpdated));
            passwordPanel.add(passwordUpdatedValue, new GridBagConstraints(1, componentRow, 2, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.HORIZONTAL, defaultInsets, 0, 0));
            additionalComponents.add(passwordUpdatedValue);
            componentRow++;

            for (DecryptedPasswordHistoryEntry dphe : decryptedCipherData.getPasswordHistory()) {
                JPasswordField passwordField = new JPasswordField();
                passwordField.setColumns(25);
                passwordField.setEditable(false);
                passwordField.setText(dphe.getPassword());
                JButton copyButton = buildCopyButton(passwordField);
                JToggleButton toggleVisibilityButton = new JToggleButton();
                toggleVisibilityButton.setIcon(CLOSED_EYE_ICON);
                toggleVisibilityButton.setMaximumSize(new java.awt.Dimension(24, 24));
                toggleVisibilityButton.setMinimumSize(new java.awt.Dimension(24, 24));
                toggleVisibilityButton.setPreferredSize(new java.awt.Dimension(24, 24));
                toggleVisibilityButton.setSelectedIcon(OPEN_EYE_ICON);
                toggleVisibilityButton.addActionListener(ae -> {
                    passwordField.setEchoChar(toggleVisibilityButton.isSelected() ? '\u0000' : passwordMask);
                });
                JLabel changeDate = new JLabel(formatLocalDate(dphe.getLastUsedDate()));
                additionalComponents.add(passwordField);
                additionalComponents.add(changeDate);
                additionalComponents.add(copyButton);
                additionalComponents.add(toggleVisibilityButton);
                passwordPanel.add(copyButton, new GridBagConstraints(3, componentRow, 1, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
                passwordPanel.add(toggleVisibilityButton, new GridBagConstraints(4, componentRow, 1, 1, 0, 0, BASELINE_LEADING, GridBagConstraints.NONE, defaultInsets, 0, 0));
                passwordPanel.add(passwordField, new GridBagConstraints(1, componentRow, 1, 1, 1, 0, BASELINE_LEADING, GridBagConstraints.HORIZONTAL, defaultInsets, 0, 0));
                passwordPanel.add(changeDate, new GridBagConstraints(2, componentRow, 1, 1, 1, 0, BASELINE_LEADING, GridBagConstraints.HORIZONTAL, defaultInsets, 0, 0));
                componentRow++;
            }
        }

        updateAllowAccessCheckbox();
        revalidate();
        repaint();
        firePropertyChange("decryptedCipherData", old, this.decryptedCipherData);
    }

    public static String formatLocalDate(OffsetDateTime passwordUpdated) {
        if(passwordUpdated == null) {
            return "-";
        } else {
            return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(passwordUpdated);
        }
    }

    public JButton buildCopyButton(JTextComponent textField) {
        JButton copyButton = new JButton();
        copyButton.setIcon(COPY_ICON);
        copyButton.setMinimumSize(new Dimension(24, 24));
        copyButton.setPreferredSize(new Dimension(24, 24));
        copyButton.setMaximumSize(new Dimension(24, 24));
        copyButton.addActionListener(new CopyFieldAction(textField));
        return copyButton;
    }

    public void showLoginFields(boolean state) {
        usernameLabel.setVisible(state);
        usernameField.setVisible(state);
        copyUsernameButton.setVisible(state);
        passwordLabel.setVisible(state);
        passwordField.setVisible(state);
        copyPasswordButton.setVisible(state);
        passwordVisible.setVisible(state);
        totpLabel.setVisible(state);
        totpField.setVisible(state);
        copyTotpButton.setVisible(state);
        totpVisibleButton.setVisible(state);
        totpEvaluatedField.setVisible(state);
        copyTotpEvaluatedButton.setVisible(state);
    }

    public void showSshFields(boolean state) {
        sshPrivateKeyLabel.setVisible(state);
        sshPrivateKeyScrollPane.setVisible(state);
        copySshPrivateKey.setVisible(state);
        sshPublicKeyLabel.setVisible(state);
        sshPublicKeyField.setVisible(state);
        copySshPublicKey.setVisible(state);
        sshFingerprintLabel.setVisible(state);
        sshFingerprintField.setVisible(state);
        copySshFingerprint.setVisible(state);
    }

    private void updateTotpEvaluated() {
        String code = null;
        try {
            code = TOTPUtil.calculateTOTP(totpField.getText());
        } catch (Exception ex) {
        }
        if(code != null) {
            totpTimer.start();
            totpEvaluatedField.setText(code);
            copyTotpEvaluatedButton.setEnabled(true);
        } else {
            totpTimer.stop();
            totpEvaluatedField.setText("");
            copyTotpEvaluatedButton.setEnabled(false);
        }
    }

    private void updateAllowAccessCheckbox() {
        if(Configuration.getConfiguration().isAllowAllAccess()) {
            allowAccessCheckbox.setEnabled(false);
            allowAccessCheckbox.setSelected(true);
        } else if(decryptedCipherData != null) {
            allowAccessCheckbox.setEnabled(true);
            allowAccessCheckbox.setSelected(allowAccess.contains(decryptedCipherData.getId()));
        } else {
            allowAccessCheckbox.setEnabled(false);
            allowAccessCheckbox.setSelected(false);
        }
    }

    private boolean isNotNullNotEmpty(String input) {
        return input != null && ! input.isBlank();
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

        passwordPanelScrollPane = new javax.swing.JScrollPane();
        passwordPanel = new javax.swing.JPanel();
        passwordTitle = new javax.swing.JLabel();
        locationInfoWrapper = new javax.swing.JPanel();
        locationInfoPlaceholder = new javax.swing.JLabel();
        idLabel = new javax.swing.JLabel();
        idField = new javax.swing.JTextField();
        usernameLabel = new javax.swing.JLabel();
        usernameField = new javax.swing.JTextField();
        passwordLabel = new javax.swing.JLabel();
        passwordField = new javax.swing.JPasswordField();
        passwordVisible = new javax.swing.JToggleButton();
        copyIdButton = new javax.swing.JButton();
        copyUsernameButton = new javax.swing.JButton();
        copyPasswordButton = new javax.swing.JButton();
        fillerMiddle = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 32767));
        totpLabel = new javax.swing.JLabel();
        totpField = new javax.swing.JPasswordField();
        totpEvaluatedField = new javax.swing.JTextField();
        copyTotpButton = new javax.swing.JButton();
        totpVisibleButton = new javax.swing.JToggleButton();
        copyTotpEvaluatedButton = new javax.swing.JButton();
        sshPrivateKeyLabel = new javax.swing.JLabel();
        sshPublicKeyLabel = new javax.swing.JLabel();
        sshFingerprintLabel = new javax.swing.JLabel();
        sshPrivateKeyScrollPane = new javax.swing.JScrollPane();
        sshPrivateKeyField = new javax.swing.JTextArea();
        sshPublicKeyField = new javax.swing.JTextField();
        sshFingerprintField = new javax.swing.JTextField();
        copySshPrivateKey = new javax.swing.JButton();
        copySshPublicKey = new javax.swing.JButton();
        copySshFingerprint = new javax.swing.JButton();
        notesLabel = new javax.swing.JLabel();
        copyNotes = new javax.swing.JButton();
        notesScrollPane = new javax.swing.JScrollPane();
        notesField = new javax.swing.JTextArea();
        allowAccessCheckbox = new javax.swing.JCheckBox();

        setLayout(new java.awt.BorderLayout());

        passwordPanel.setLayout(new java.awt.GridBagLayout());

        passwordTitle.setFont(passwordTitle.getFont().deriveFont(passwordTitle.getFont().getStyle() | java.awt.Font.BOLD, passwordTitle.getFont().getSize()+5));
        passwordTitle.setText("<Title>");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(passwordTitle, gridBagConstraints);

        java.awt.FlowLayout flowLayout1 = new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0);
        flowLayout1.setAlignOnBaseline(true);
        locationInfoWrapper.setLayout(flowLayout1);

        locationInfoPlaceholder.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        locationInfoPlaceholder.setText(" ");
        locationInfoPlaceholder.setToolTipText("");
        locationInfoWrapper.add(locationInfoPlaceholder);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 5);
        passwordPanel.add(locationInfoWrapper, gridBagConstraints);

        idLabel.setText("ID:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(idLabel, gridBagConstraints);

        idField.setEditable(false);
        idField.setColumns(25);
        idField.setText("4d3be0b0-1425-4bb8-8725-b32d01134b64");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(idField, gridBagConstraints);

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("eu/doppelhelix/app/bitwardenagent/Bundle"); // NOI18N
        usernameLabel.setText(bundle.getString("usernameLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(usernameLabel, gridBagConstraints);

        usernameField.setEditable(false);
        usernameField.setColumns(25);
        usernameField.setText("demo@invalid");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(usernameField, gridBagConstraints);

        passwordLabel.setText(bundle.getString("passwordLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(passwordLabel, gridBagConstraints);

        passwordField.setEditable(false);
        passwordField.setColumns(25);
        passwordField.setText("DemoPassword");
        passwordField.setToolTipText("");
        passwordField.putClientProperty("JPasswordField.cutCopyAllowed", true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(passwordField, gridBagConstraints);

        passwordVisible.setIcon(CLOSED_EYE_ICON);
        passwordVisible.setMaximumSize(new java.awt.Dimension(24, 24));
        passwordVisible.setMinimumSize(new java.awt.Dimension(24, 24));
        passwordVisible.setPreferredSize(new java.awt.Dimension(24, 24));
        passwordVisible.setSelectedIcon(OPEN_EYE_ICON);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(passwordVisible, gridBagConstraints);

        copyIdButton.setIcon(COPY_ICON);
        copyIdButton.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdButton.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdButton.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdButton, gridBagConstraints);

        copyUsernameButton.setIcon(COPY_ICON);
        copyUsernameButton.setMaximumSize(new java.awt.Dimension(24, 24));
        copyUsernameButton.setMinimumSize(new java.awt.Dimension(24, 24));
        copyUsernameButton.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyUsernameButton, gridBagConstraints);

        copyPasswordButton.setIcon(COPY_ICON);
        copyPasswordButton.setMaximumSize(new java.awt.Dimension(24, 24));
        copyPasswordButton.setMinimumSize(new java.awt.Dimension(24, 24));
        copyPasswordButton.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyPasswordButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 250;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        passwordPanel.add(fillerMiddle, gridBagConstraints);

        totpLabel.setText(bundle.getString("totpLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(totpLabel, gridBagConstraints);

        totpField.setEditable(false);
        totpField.setColumns(25);
        totpField.setText("jPasswordField1");
        totpField.putClientProperty("JPasswordField.cutCopyAllowed", true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(totpField, gridBagConstraints);

        totpEvaluatedField.setEditable(false);
        totpEvaluatedField.setColumns(25);
        totpEvaluatedField.setText("123456");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(totpEvaluatedField, gridBagConstraints);

        copyTotpButton.setIcon(COPY_ICON);
        copyTotpButton.setMaximumSize(new java.awt.Dimension(24, 24));
        copyTotpButton.setMinimumSize(new java.awt.Dimension(24, 24));
        copyTotpButton.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyTotpButton, gridBagConstraints);

        totpVisibleButton.setIcon(CLOSED_EYE_ICON);
        totpVisibleButton.setMaximumSize(new java.awt.Dimension(24, 24));
        totpVisibleButton.setMinimumSize(new java.awt.Dimension(24, 24));
        totpVisibleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        totpVisibleButton.setSelectedIcon(OPEN_EYE_ICON);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(totpVisibleButton, gridBagConstraints);

        copyTotpEvaluatedButton.setIcon(COPY_ICON);
        copyTotpEvaluatedButton.setMaximumSize(new java.awt.Dimension(24, 24));
        copyTotpEvaluatedButton.setMinimumSize(new java.awt.Dimension(24, 24));
        copyTotpEvaluatedButton.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyTotpEvaluatedButton, gridBagConstraints);

        sshPrivateKeyLabel.setText(bundle.getString("sshPrivateKeyLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(sshPrivateKeyLabel, gridBagConstraints);

        sshPublicKeyLabel.setText(bundle.getString("sshPublicKeyLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(sshPublicKeyLabel, gridBagConstraints);

        sshFingerprintLabel.setText(bundle.getString("sshFingerprintLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(sshFingerprintLabel, gridBagConstraints);

        sshPrivateKeyField.setEditable(false);
        sshPrivateKeyField.setColumns(25);
        sshPrivateKeyField.setRows(8);
        sshPrivateKeyScrollPane.setViewportView(sshPrivateKeyField);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(sshPrivateKeyScrollPane, gridBagConstraints);

        sshPublicKeyField.setEditable(false);
        sshPublicKeyField.setColumns(25);
        sshPublicKeyField.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(sshPublicKeyField, gridBagConstraints);

        sshFingerprintField.setEditable(false);
        sshFingerprintField.setColumns(25);
        sshFingerprintField.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(sshFingerprintField, gridBagConstraints);

        copySshPrivateKey.setIcon(COPY_ICON);
        copySshPrivateKey.setMaximumSize(new java.awt.Dimension(24, 24));
        copySshPrivateKey.setMinimumSize(new java.awt.Dimension(24, 24));
        copySshPrivateKey.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copySshPrivateKey, gridBagConstraints);

        copySshPublicKey.setIcon(COPY_ICON);
        copySshPublicKey.setMaximumSize(new java.awt.Dimension(24, 24));
        copySshPublicKey.setMinimumSize(new java.awt.Dimension(24, 24));
        copySshPublicKey.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copySshPublicKey, gridBagConstraints);

        copySshFingerprint.setIcon(COPY_ICON);
        copySshFingerprint.setMaximumSize(new java.awt.Dimension(24, 24));
        copySshFingerprint.setMinimumSize(new java.awt.Dimension(24, 24));
        copySshFingerprint.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copySshFingerprint, gridBagConstraints);

        notesLabel.setText(bundle.getString("notesLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(notesLabel, gridBagConstraints);

        copyNotes.setIcon(COPY_ICON);
        copyNotes.setMaximumSize(new java.awt.Dimension(24, 24));
        copyNotes.setMinimumSize(new java.awt.Dimension(24, 24));
        copyNotes.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyNotes, gridBagConstraints);

        notesField.setEditable(false);
        notesField.setColumns(20);
        notesField.setLineWrap(true);
        notesField.setRows(8);
        notesField.setWrapStyleWord(true);
        notesScrollPane.setViewportView(notesField);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(notesScrollPane, gridBagConstraints);

        allowAccessCheckbox.setText(bundle.getString("allowAccessCheckbox")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(allowAccessCheckbox, gridBagConstraints);

        passwordPanelScrollPane.setViewportView(passwordPanel);

        add(passwordPanelScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox allowAccessCheckbox;
    private javax.swing.JButton copyIdButton;
    private javax.swing.JButton copyNotes;
    private javax.swing.JButton copyPasswordButton;
    private javax.swing.JButton copySshFingerprint;
    private javax.swing.JButton copySshPrivateKey;
    private javax.swing.JButton copySshPublicKey;
    private javax.swing.JButton copyTotpButton;
    private javax.swing.JButton copyTotpEvaluatedButton;
    private javax.swing.JButton copyUsernameButton;
    private javax.swing.Box.Filler fillerMiddle;
    private javax.swing.JTextField idField;
    private javax.swing.JLabel idLabel;
    private javax.swing.JLabel locationInfoPlaceholder;
    private javax.swing.JPanel locationInfoWrapper;
    private javax.swing.JTextArea notesField;
    private javax.swing.JLabel notesLabel;
    private javax.swing.JScrollPane notesScrollPane;
    private javax.swing.JPasswordField passwordField;
    private javax.swing.JLabel passwordLabel;
    private javax.swing.JPanel passwordPanel;
    private javax.swing.JScrollPane passwordPanelScrollPane;
    private javax.swing.JLabel passwordTitle;
    private javax.swing.JToggleButton passwordVisible;
    private javax.swing.JTextField sshFingerprintField;
    private javax.swing.JLabel sshFingerprintLabel;
    private javax.swing.JTextArea sshPrivateKeyField;
    private javax.swing.JLabel sshPrivateKeyLabel;
    private javax.swing.JScrollPane sshPrivateKeyScrollPane;
    private javax.swing.JTextField sshPublicKeyField;
    private javax.swing.JLabel sshPublicKeyLabel;
    private javax.swing.JTextField totpEvaluatedField;
    private javax.swing.JPasswordField totpField;
    private javax.swing.JLabel totpLabel;
    private javax.swing.JToggleButton totpVisibleButton;
    private javax.swing.JTextField usernameField;
    private javax.swing.JLabel usernameLabel;
    // End of variables declaration//GEN-END:variables
}
