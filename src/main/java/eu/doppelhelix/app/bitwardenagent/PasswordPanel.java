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
        copyCardHoldername.addActionListener(new CopyFieldAction(cardHoldernameField));
        copyCardBrand.addActionListener(new CopyFieldAction(cardBrandField));
        copyCardNumber.addActionListener(new CopyFieldAction(cardNumberField));
        copyCardExpirationMonth.addActionListener(new CopyFieldAction(cardExpirationMonthField));
        copyCardExpirationYear.addActionListener(new CopyFieldAction(cardExpirationYearField));
        copyCardCvv.addActionListener(new CopyFieldAction(cardCvvField));
        copyIdentityAddress1.addActionListener(new CopyFieldAction(identityAddress1Field));
        copyIdentityAddress2.addActionListener(new CopyFieldAction(identityAddress2Field));
        copyIdentityAddress3.addActionListener(new CopyFieldAction(identityAddress3Field));
        copyIdentityCity.addActionListener(new CopyFieldAction(identityCityField));
        copyIdentityCompany.addActionListener(new CopyFieldAction(identityCompanyField));
        copyIdentityCountry.addActionListener(new CopyFieldAction(identityCountryField));
        copyIdentityEmail.addActionListener(new CopyFieldAction(identityEmailField));
        copyIdentityFirstname.addActionListener(new CopyFieldAction(identityFirstnameField));
        copyIdentityLastname.addActionListener(new CopyFieldAction(identityLastnameField));
        copyIdentityLicenseNumber.addActionListener(new CopyFieldAction(identityLicenseNumberField));
        copyIdentityMiddlename.addActionListener(new CopyFieldAction(identityMiddlenameField));
        copyIdentityPassportNumber.addActionListener(new CopyFieldAction(identityPassportNumberField));
        copyIdentitySsn.addActionListener(new CopyFieldAction(identitySsnField));
        copyIdentityState.addActionListener(new CopyFieldAction(identityStateField));
        copyIdentityPhone.addActionListener(new CopyFieldAction(identityPhoneField));
        copyIdentityTitle.addActionListener(new CopyFieldAction(identityTitleField));
        copyIdentityUsername.addActionListener(new CopyFieldAction(identityUsernameField));
        copyIdentityZip.addActionListener(new CopyFieldAction(identityPostalcodeField));
        passwordMask = passwordField.getEchoChar();
        passwordVisible.addActionListener(ae -> {
            passwordField.setEchoChar(passwordVisible.isSelected() ? '\u0000' : passwordMask);
        });
        totpVisibleButton.addActionListener(ae -> {
            totpField.setEchoChar(totpVisibleButton.isSelected() ? '\u0000' : passwordMask);
        });
        cardNumberVisibleButton.addActionListener(ae -> {
            cardNumberField.setEchoChar(cardNumberVisibleButton.isSelected() ? '\u0000' : passwordMask);
        });
        cardCvvVisible.addActionListener(ae -> {
            cardCvvField.setEchoChar(cardCvvVisible.isSelected() ? '\u0000' : passwordMask);
        });
        identitySsnVisibile.addActionListener(ae -> {
            identitySsnField.setEchoChar(identitySsnVisibile.isSelected() ? '\u0000' : passwordMask);
        });
        identityPassportNumberVisible.addActionListener(ae -> {
            identityPassportNumberField.setEchoChar(identityPassportNumberVisible.isSelected() ? '\u0000' : passwordMask);
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
        showCardFields(false);
        showIdentityFields(false);
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
            } else if (decryptedCipherData.getCard() != null) {
                showCardFields(true);
                cardBrandField.setText(decryptedCipherData.getCard().getBrand());
                cardHoldernameField.setText(decryptedCipherData.getCard().getCardholderName());
                cardExpirationMonthField.setText(decryptedCipherData.getCard().getExpMonth());
                cardExpirationYearField.setText(decryptedCipherData.getCard().getExpYear());
                cardCvvField.setText(decryptedCipherData.getCard().getCode());
                cardNumberField.setText(decryptedCipherData.getCard().getNumber());
            } else if (decryptedCipherData.getIdentity() != null) {
                showIdentityFields(true);
                identityAddress1Field.setText(decryptedCipherData.getIdentity().getAddress1());
                identityAddress2Field.setText(decryptedCipherData.getIdentity().getAddress2());
                identityAddress3Field.setText(decryptedCipherData.getIdentity().getAddress3());
                identityCityField.setText(decryptedCipherData.getIdentity().getCity());
                identityCompanyField.setText(decryptedCipherData.getIdentity().getCompany());
                identityCountryField.setText(decryptedCipherData.getIdentity().getCountry());
                identityEmailField.setText(decryptedCipherData.getIdentity().getEmail());
                identityFirstnameField.setText(decryptedCipherData.getIdentity().getFirstName());
                identityLastnameField.setText(decryptedCipherData.getIdentity().getLastName());
                identityLicenseNumberField.setText(decryptedCipherData.getIdentity().getLicenseNumber());
                identityMiddlenameField.setText(decryptedCipherData.getIdentity().getMiddleName());
                identityPassportNumberField.setText(decryptedCipherData.getIdentity().getPassportNumber());
                identitySsnField.setText(decryptedCipherData.getIdentity().getSsn());
                identityStateField.setText(decryptedCipherData.getIdentity().getState());
                identityPhoneField.setText(decryptedCipherData.getIdentity().getPhone());
                identityTitleField.setText(decryptedCipherData.getIdentity().getTitle());
                identityUsernameField.setText(decryptedCipherData.getIdentity().getUsername());
                identityPostalcodeField.setText(decryptedCipherData.getIdentity().getPostalCode());
            }
        }

        updateTotpEvaluated();
        int componentRow = 35;
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

    public void showCardFields(boolean state) {
        cardHoldernameLabel.setVisible(state);
        cardHoldernameField.setVisible(state);
        copyCardHoldername.setVisible(state);
        cardBrandLabel.setVisible(state);
        cardBrandField.setVisible(state);
        copyCardBrand.setVisible(state);
        cardExpirationLabel.setVisible(state);
        cardExpirationPanel.setVisible(state);
        cardCvvLabel.setVisible(state);
        cardCvvField.setVisible(state);
        copyCardCvv.setVisible(state);
        cardCvvVisible.setVisible(state);
        cardNumberLabel.setVisible(state);
        cardNumberField.setVisible(state);
        copyCardNumber.setVisible(state);
        cardNumberVisibleButton.setVisible(state);
    }

    public void showIdentityFields(boolean state) {
        identityTitleLabel.setVisible(state);
        identityTitleField.setVisible(state);
        copyIdentityTitle.setVisible(state);
        identityFirstnameLabel.setVisible(state);
        identityFirstnameField.setVisible(state);
        copyIdentityFirstname.setVisible(state);
        identityMiddlenameLabel.setVisible(state);
        identityMiddlenameField.setVisible(state);
        copyIdentityMiddlename.setVisible(state);
        identityLastnameLabel.setVisible(state);
        identityLastnameField.setVisible(state);
        copyIdentityLastname.setVisible(state);
        identityUsername.setVisible(state);
        identityUsernameField.setVisible(state);
        copyIdentityUsername.setVisible(state);
        identityCompany.setVisible(state);
        identityCompanyField.setVisible(state);
        copyIdentityCompany.setVisible(state);
        identitySsnLabel.setVisible(state);
        identitySsnField.setVisible(state);
        copyIdentitySsn.setVisible(state);
        identitySsnVisibile.setVisible(state);
        identityPassportNumberLabel.setVisible(state);
        identityPassportNumberField.setVisible(state);
        copyIdentityPassportNumber.setVisible(state);
        identityPassportNumberVisible.setVisible(state);
        identityLicenseNumberLabel.setVisible(state);
        identityLicenseNumberField.setVisible(state);
        copyIdentityLicenseNumber.setVisible(state);
        identityEmailLabel.setVisible(state);
        identityEmailField.setVisible(state);
        copyIdentityEmail.setVisible(state);
        identityPhoneLabel.setVisible(state);
        identityPhoneField.setVisible(state);
        copyIdentityPhone.setVisible(state);
        identityAddress1Label.setVisible(state);
        identityAddress1Field.setVisible(state);
        copyIdentityAddress1.setVisible(state);
        identityAddress2Label.setVisible(state);
        identityAddress2Field.setVisible(state);
        copyIdentityAddress2.setVisible(state);
        identityAddress3Label.setVisible(state);
        identityAddress3Field.setVisible(state);
        copyIdentityAddress3.setVisible(state);
        identityCityLabel.setVisible(state);
        identityCityField.setVisible(state);
        copyIdentityCity.setVisible(state);
        identityPostalcodeLabel.setVisible(state);
        identityPostalcodeField.setVisible(state);
        copyIdentityZip.setVisible(state);
        identityStateLabel.setVisible(state);
        identityStateField.setVisible(state);
        copyIdentityState.setVisible(state);
        identityCountryLabel.setVisible(state);
        identityCountryField.setVisible(state);
        copyIdentityCountry.setVisible(state);
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
        cardHoldernameLabel = new javax.swing.JLabel();
        cardHoldernameField = new javax.swing.JTextField();
        copyCardHoldername = new javax.swing.JButton();
        cardBrandLabel = new javax.swing.JLabel();
        cardBrandField = new javax.swing.JTextField();
        copyCardBrand = new javax.swing.JButton();
        cardNumberLabel = new javax.swing.JLabel();
        cardNumberField = new javax.swing.JPasswordField();
        copyCardNumber = new javax.swing.JButton();
        cardExpirationLabel = new javax.swing.JLabel();
        cardExpirationPanel = new javax.swing.JPanel();
        cardExpirationMonthField = new javax.swing.JTextField();
        copyCardExpirationMonth = new javax.swing.JButton();
        expSplit = new javax.swing.JLabel();
        cardExpirationYearField = new javax.swing.JTextField();
        copyCardExpirationYear = new javax.swing.JButton();
        cardCvvLabel = new javax.swing.JLabel();
        cardCvvField = new javax.swing.JPasswordField();
        copyCardCvv = new javax.swing.JButton();
        cardNumberVisibleButton = new javax.swing.JToggleButton();
        cardCvvVisible = new javax.swing.JToggleButton();
        identityTitleLabel = new javax.swing.JLabel();
        identityTitleField = new javax.swing.JTextField();
        copyIdentityTitle = new javax.swing.JButton();
        identityFirstnameLabel = new javax.swing.JLabel();
        identityFirstnameField = new javax.swing.JTextField();
        copyIdentityFirstname = new javax.swing.JButton();
        identityMiddlenameLabel = new javax.swing.JLabel();
        identityMiddlenameField = new javax.swing.JTextField();
        copyIdentityMiddlename = new javax.swing.JButton();
        identityLastnameLabel = new javax.swing.JLabel();
        identityLastnameField = new javax.swing.JTextField();
        copyIdentityLastname = new javax.swing.JButton();
        identityUsername = new javax.swing.JLabel();
        identityUsernameField = new javax.swing.JTextField();
        copyIdentityUsername = new javax.swing.JButton();
        identityCompany = new javax.swing.JLabel();
        identityCompanyField = new javax.swing.JTextField();
        copyIdentityCompany = new javax.swing.JButton();
        identitySsnLabel = new javax.swing.JLabel();
        identitySsnField = new javax.swing.JPasswordField();
        copyIdentitySsn = new javax.swing.JButton();
        identitySsnVisibile = new javax.swing.JToggleButton();
        identityPassportNumberLabel = new javax.swing.JLabel();
        identityPassportNumberField = new javax.swing.JPasswordField();
        copyIdentityPassportNumber = new javax.swing.JButton();
        identityPassportNumberVisible = new javax.swing.JToggleButton();
        identityLicenseNumberLabel = new javax.swing.JLabel();
        identityLicenseNumberField = new javax.swing.JTextField();
        copyIdentityLicenseNumber = new javax.swing.JButton();
        identityEmailLabel = new javax.swing.JLabel();
        identityEmailField = new javax.swing.JTextField();
        copyIdentityEmail = new javax.swing.JButton();
        identityPhoneLabel = new javax.swing.JLabel();
        identityPhoneField = new javax.swing.JTextField();
        copyIdentityPhone = new javax.swing.JButton();
        identityAddress1Label = new javax.swing.JLabel();
        identityAddress1Field = new javax.swing.JTextField();
        copyIdentityAddress1 = new javax.swing.JButton();
        identityAddress2Label = new javax.swing.JLabel();
        identityAddress2Field = new javax.swing.JTextField();
        copyIdentityAddress2 = new javax.swing.JButton();
        identityAddress3Label = new javax.swing.JLabel();
        identityAddress3Field = new javax.swing.JTextField();
        copyIdentityAddress3 = new javax.swing.JButton();
        identityCityLabel = new javax.swing.JLabel();
        identityCityField = new javax.swing.JTextField();
        copyIdentityCity = new javax.swing.JButton();
        identityPostalcodeLabel = new javax.swing.JLabel();
        identityPostalcodeField = new javax.swing.JTextField();
        copyIdentityZip = new javax.swing.JButton();
        identityStateLabel = new javax.swing.JLabel();
        identityStateField = new javax.swing.JTextField();
        copyIdentityState = new javax.swing.JButton();
        identityCountryLabel = new javax.swing.JLabel();
        identityCountryField = new javax.swing.JTextField();
        copyIdentityCountry = new javax.swing.JButton();

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
        gridBagConstraints.gridy = 34;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(notesLabel, gridBagConstraints);

        copyNotes.setIcon(COPY_ICON);
        copyNotes.setMaximumSize(new java.awt.Dimension(24, 24));
        copyNotes.setMinimumSize(new java.awt.Dimension(24, 24));
        copyNotes.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 34;
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
        gridBagConstraints.gridy = 34;
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

        cardHoldernameLabel.setText(bundle.getString("cardHoldername")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(cardHoldernameLabel, gridBagConstraints);

        cardHoldernameField.setEditable(false);
        cardHoldernameField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(cardHoldernameField, gridBagConstraints);

        copyCardHoldername.setIcon(COPY_ICON);
        copyCardHoldername.setMaximumSize(new java.awt.Dimension(24, 24));
        copyCardHoldername.setMinimumSize(new java.awt.Dimension(24, 24));
        copyCardHoldername.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyCardHoldername, gridBagConstraints);

        cardBrandLabel.setText(bundle.getString("cardBrand")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(cardBrandLabel, gridBagConstraints);

        cardBrandField.setEditable(false);
        cardBrandField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(cardBrandField, gridBagConstraints);

        copyCardBrand.setIcon(COPY_ICON);
        copyCardBrand.setMaximumSize(new java.awt.Dimension(24, 24));
        copyCardBrand.setMinimumSize(new java.awt.Dimension(24, 24));
        copyCardBrand.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyCardBrand, gridBagConstraints);

        cardNumberLabel.setText(bundle.getString("cardNumber")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(cardNumberLabel, gridBagConstraints);

        cardNumberField.setEditable(false);
        cardNumberField.setColumns(25);
        cardNumberField.setToolTipText("");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(cardNumberField, gridBagConstraints);

        copyCardNumber.setIcon(COPY_ICON);
        copyCardNumber.setMaximumSize(new java.awt.Dimension(24, 24));
        copyCardNumber.setMinimumSize(new java.awt.Dimension(24, 24));
        copyCardNumber.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyCardNumber, gridBagConstraints);

        cardExpirationLabel.setText(bundle.getString("cardExpiration")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(cardExpirationLabel, gridBagConstraints);

        cardExpirationMonthField.setEditable(false);
        cardExpirationMonthField.setColumns(4);
        cardExpirationPanel.add(cardExpirationMonthField);

        copyCardExpirationMonth.setIcon(COPY_ICON);
        copyCardExpirationMonth.setMaximumSize(new java.awt.Dimension(24, 24));
        copyCardExpirationMonth.setMinimumSize(new java.awt.Dimension(24, 24));
        copyCardExpirationMonth.setPreferredSize(new java.awt.Dimension(24, 24));
        cardExpirationPanel.add(copyCardExpirationMonth);

        expSplit.setText("/");
        cardExpirationPanel.add(expSplit);

        cardExpirationYearField.setEditable(false);
        cardExpirationYearField.setColumns(6);
        cardExpirationPanel.add(cardExpirationYearField);

        copyCardExpirationYear.setIcon(COPY_ICON);
        copyCardExpirationYear.setMaximumSize(new java.awt.Dimension(24, 24));
        copyCardExpirationYear.setMinimumSize(new java.awt.Dimension(24, 24));
        copyCardExpirationYear.setPreferredSize(new java.awt.Dimension(24, 24));
        cardExpirationPanel.add(copyCardExpirationYear);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        passwordPanel.add(cardExpirationPanel, gridBagConstraints);

        cardCvvLabel.setText(bundle.getString("cardCVV")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(cardCvvLabel, gridBagConstraints);

        cardCvvField.setEditable(false);
        cardCvvField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(cardCvvField, gridBagConstraints);

        copyCardCvv.setIcon(COPY_ICON);
        copyCardCvv.setMaximumSize(new java.awt.Dimension(24, 24));
        copyCardCvv.setMinimumSize(new java.awt.Dimension(24, 24));
        copyCardCvv.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyCardCvv, gridBagConstraints);

        cardNumberVisibleButton.setIcon(CLOSED_EYE_ICON);
        cardNumberVisibleButton.setMaximumSize(new java.awt.Dimension(24, 24));
        cardNumberVisibleButton.setMinimumSize(new java.awt.Dimension(24, 24));
        cardNumberVisibleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        cardNumberVisibleButton.setSelectedIcon(OPEN_EYE_ICON);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(cardNumberVisibleButton, gridBagConstraints);

        cardCvvVisible.setIcon(CLOSED_EYE_ICON);
        cardCvvVisible.setMaximumSize(new java.awt.Dimension(24, 24));
        cardCvvVisible.setMinimumSize(new java.awt.Dimension(24, 24));
        cardCvvVisible.setPreferredSize(new java.awt.Dimension(24, 24));
        cardCvvVisible.setSelectedIcon(OPEN_EYE_ICON);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(cardCvvVisible, gridBagConstraints);

        identityTitleLabel.setText(bundle.getString("identityTitle")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityTitleLabel, gridBagConstraints);

        identityTitleField.setEditable(false);
        identityTitleField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityTitleField, gridBagConstraints);

        copyIdentityTitle.setIcon(COPY_ICON);
        copyIdentityTitle.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityTitle.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityTitle.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityTitle, gridBagConstraints);

        identityFirstnameLabel.setText(bundle.getString("identityFirstname")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 17;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityFirstnameLabel, gridBagConstraints);

        identityFirstnameField.setEditable(false);
        identityFirstnameField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 17;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityFirstnameField, gridBagConstraints);

        copyIdentityFirstname.setIcon(COPY_ICON);
        copyIdentityFirstname.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityFirstname.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityFirstname.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 17;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityFirstname, gridBagConstraints);

        identityMiddlenameLabel.setText(bundle.getString("identityMiddleName")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 18;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityMiddlenameLabel, gridBagConstraints);

        identityMiddlenameField.setEditable(false);
        identityMiddlenameField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 18;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityMiddlenameField, gridBagConstraints);

        copyIdentityMiddlename.setIcon(COPY_ICON);
        copyIdentityMiddlename.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityMiddlename.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityMiddlename.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 18;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityMiddlename, gridBagConstraints);

        identityLastnameLabel.setText(bundle.getString("identityLastname")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 19;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityLastnameLabel, gridBagConstraints);

        identityLastnameField.setEditable(false);
        identityLastnameField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 19;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityLastnameField, gridBagConstraints);

        copyIdentityLastname.setIcon(COPY_ICON);
        copyIdentityLastname.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityLastname.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityLastname.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 19;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityLastname, gridBagConstraints);

        identityUsername.setText(bundle.getString("identityUsername")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 20;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityUsername, gridBagConstraints);

        identityUsernameField.setEditable(false);
        identityUsernameField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 20;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityUsernameField, gridBagConstraints);

        copyIdentityUsername.setIcon(COPY_ICON);
        copyIdentityUsername.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityUsername.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityUsername.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 20;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityUsername, gridBagConstraints);

        identityCompany.setText(bundle.getString("identityCompany")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 21;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityCompany, gridBagConstraints);

        identityCompanyField.setEditable(false);
        identityCompanyField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 21;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityCompanyField, gridBagConstraints);

        copyIdentityCompany.setIcon(COPY_ICON);
        copyIdentityCompany.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityCompany.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityCompany.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 21;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityCompany, gridBagConstraints);

        identitySsnLabel.setText(bundle.getString("identitySsn")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 22;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identitySsnLabel, gridBagConstraints);

        identitySsnField.setEditable(false);
        identitySsnField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 22;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identitySsnField, gridBagConstraints);

        copyIdentitySsn.setIcon(COPY_ICON);
        copyIdentitySsn.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentitySsn.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentitySsn.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 22;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentitySsn, gridBagConstraints);

        identitySsnVisibile.setIcon(CLOSED_EYE_ICON);
        identitySsnVisibile.setMaximumSize(new java.awt.Dimension(24, 24));
        identitySsnVisibile.setMinimumSize(new java.awt.Dimension(24, 24));
        identitySsnVisibile.setPreferredSize(new java.awt.Dimension(24, 24));
        identitySsnVisibile.setSelectedIcon(OPEN_EYE_ICON);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 22;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identitySsnVisibile, gridBagConstraints);

        identityPassportNumberLabel.setText(bundle.getString("identityPassportNumber")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 23;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityPassportNumberLabel, gridBagConstraints);

        identityPassportNumberField.setEditable(false);
        identityPassportNumberField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 23;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityPassportNumberField, gridBagConstraints);

        copyIdentityPassportNumber.setIcon(COPY_ICON);
        copyIdentityPassportNumber.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityPassportNumber.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityPassportNumber.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 23;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityPassportNumber, gridBagConstraints);

        identityPassportNumberVisible.setIcon(CLOSED_EYE_ICON);
        identityPassportNumberVisible.setMaximumSize(new java.awt.Dimension(24, 24));
        identityPassportNumberVisible.setMinimumSize(new java.awt.Dimension(24, 24));
        identityPassportNumberVisible.setPreferredSize(new java.awt.Dimension(24, 24));
        identityPassportNumberVisible.setSelectedIcon(OPEN_EYE_ICON);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 23;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityPassportNumberVisible, gridBagConstraints);

        identityLicenseNumberLabel.setText(bundle.getString("identityLicenseNumber")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 24;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityLicenseNumberLabel, gridBagConstraints);

        identityLicenseNumberField.setEditable(false);
        identityLicenseNumberField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 24;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityLicenseNumberField, gridBagConstraints);

        copyIdentityLicenseNumber.setIcon(COPY_ICON);
        copyIdentityLicenseNumber.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityLicenseNumber.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityLicenseNumber.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 24;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityLicenseNumber, gridBagConstraints);

        identityEmailLabel.setText(bundle.getString("identityEmail")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 25;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityEmailLabel, gridBagConstraints);

        identityEmailField.setEditable(false);
        identityEmailField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 25;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityEmailField, gridBagConstraints);

        copyIdentityEmail.setIcon(COPY_ICON);
        copyIdentityEmail.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityEmail.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityEmail.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 25;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityEmail, gridBagConstraints);

        identityPhoneLabel.setText(bundle.getString("identityTelephone")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 26;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityPhoneLabel, gridBagConstraints);

        identityPhoneField.setEditable(false);
        identityPhoneField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 26;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityPhoneField, gridBagConstraints);

        copyIdentityPhone.setIcon(COPY_ICON);
        copyIdentityPhone.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityPhone.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityPhone.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 26;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityPhone, gridBagConstraints);

        identityAddress1Label.setText(bundle.getString("identityAdress1")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 27;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityAddress1Label, gridBagConstraints);

        identityAddress1Field.setEditable(false);
        identityAddress1Field.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 27;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityAddress1Field, gridBagConstraints);

        copyIdentityAddress1.setIcon(COPY_ICON);
        copyIdentityAddress1.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityAddress1.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityAddress1.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 27;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityAddress1, gridBagConstraints);

        identityAddress2Label.setText(bundle.getString("identityAddress2")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 28;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityAddress2Label, gridBagConstraints);

        identityAddress2Field.setEditable(false);
        identityAddress2Field.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 28;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityAddress2Field, gridBagConstraints);

        copyIdentityAddress2.setIcon(COPY_ICON);
        copyIdentityAddress2.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityAddress2.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityAddress2.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 28;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityAddress2, gridBagConstraints);

        identityAddress3Label.setText(bundle.getString("identityAddress3")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 29;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityAddress3Label, gridBagConstraints);

        identityAddress3Field.setEditable(false);
        identityAddress3Field.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 29;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityAddress3Field, gridBagConstraints);

        copyIdentityAddress3.setIcon(COPY_ICON);
        copyIdentityAddress3.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityAddress3.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityAddress3.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 29;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityAddress3, gridBagConstraints);

        identityCityLabel.setText(bundle.getString("identityCity")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 30;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityCityLabel, gridBagConstraints);

        identityCityField.setEditable(false);
        identityCityField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 30;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityCityField, gridBagConstraints);

        copyIdentityCity.setIcon(COPY_ICON);
        copyIdentityCity.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityCity.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityCity.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 30;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityCity, gridBagConstraints);

        identityPostalcodeLabel.setText(bundle.getString("identityZip")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 31;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityPostalcodeLabel, gridBagConstraints);

        identityPostalcodeField.setEditable(false);
        identityPostalcodeField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 31;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityPostalcodeField, gridBagConstraints);

        copyIdentityZip.setIcon(COPY_ICON);
        copyIdentityZip.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityZip.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityZip.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 31;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityZip, gridBagConstraints);

        identityStateLabel.setText(bundle.getString("identityState")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 32;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityStateLabel, gridBagConstraints);

        identityStateField.setEditable(false);
        identityStateField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 32;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityStateField, gridBagConstraints);

        copyIdentityState.setIcon(COPY_ICON);
        copyIdentityState.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityState.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityState.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 32;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityState, gridBagConstraints);

        identityCountryLabel.setText(bundle.getString("identityCountry")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 33;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityCountryLabel, gridBagConstraints);

        identityCountryField.setEditable(false);
        identityCountryField.setColumns(25);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 33;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(identityCountryField, gridBagConstraints);

        copyIdentityCountry.setIcon(COPY_ICON);
        copyIdentityCountry.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdentityCountry.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdentityCountry.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 33;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdentityCountry, gridBagConstraints);

        passwordPanelScrollPane.setViewportView(passwordPanel);

        add(passwordPanelScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox allowAccessCheckbox;
    private javax.swing.JTextField cardBrandField;
    private javax.swing.JLabel cardBrandLabel;
    private javax.swing.JPasswordField cardCvvField;
    private javax.swing.JLabel cardCvvLabel;
    private javax.swing.JToggleButton cardCvvVisible;
    private javax.swing.JLabel cardExpirationLabel;
    private javax.swing.JTextField cardExpirationMonthField;
    private javax.swing.JPanel cardExpirationPanel;
    private javax.swing.JTextField cardExpirationYearField;
    private javax.swing.JTextField cardHoldernameField;
    private javax.swing.JLabel cardHoldernameLabel;
    private javax.swing.JPasswordField cardNumberField;
    private javax.swing.JLabel cardNumberLabel;
    private javax.swing.JToggleButton cardNumberVisibleButton;
    private javax.swing.JButton copyCardBrand;
    private javax.swing.JButton copyCardCvv;
    private javax.swing.JButton copyCardExpirationMonth;
    private javax.swing.JButton copyCardExpirationYear;
    private javax.swing.JButton copyCardHoldername;
    private javax.swing.JButton copyCardNumber;
    private javax.swing.JButton copyIdButton;
    private javax.swing.JButton copyIdentityAddress1;
    private javax.swing.JButton copyIdentityAddress2;
    private javax.swing.JButton copyIdentityAddress3;
    private javax.swing.JButton copyIdentityCity;
    private javax.swing.JButton copyIdentityCompany;
    private javax.swing.JButton copyIdentityCountry;
    private javax.swing.JButton copyIdentityEmail;
    private javax.swing.JButton copyIdentityFirstname;
    private javax.swing.JButton copyIdentityLastname;
    private javax.swing.JButton copyIdentityLicenseNumber;
    private javax.swing.JButton copyIdentityMiddlename;
    private javax.swing.JButton copyIdentityPassportNumber;
    private javax.swing.JButton copyIdentityPhone;
    private javax.swing.JButton copyIdentitySsn;
    private javax.swing.JButton copyIdentityState;
    private javax.swing.JButton copyIdentityTitle;
    private javax.swing.JButton copyIdentityUsername;
    private javax.swing.JButton copyIdentityZip;
    private javax.swing.JButton copyNotes;
    private javax.swing.JButton copyPasswordButton;
    private javax.swing.JButton copySshFingerprint;
    private javax.swing.JButton copySshPrivateKey;
    private javax.swing.JButton copySshPublicKey;
    private javax.swing.JButton copyTotpButton;
    private javax.swing.JButton copyTotpEvaluatedButton;
    private javax.swing.JButton copyUsernameButton;
    private javax.swing.JLabel expSplit;
    private javax.swing.Box.Filler fillerMiddle;
    private javax.swing.JTextField idField;
    private javax.swing.JLabel idLabel;
    private javax.swing.JTextField identityAddress1Field;
    private javax.swing.JLabel identityAddress1Label;
    private javax.swing.JTextField identityAddress2Field;
    private javax.swing.JLabel identityAddress2Label;
    private javax.swing.JTextField identityAddress3Field;
    private javax.swing.JLabel identityAddress3Label;
    private javax.swing.JTextField identityCityField;
    private javax.swing.JLabel identityCityLabel;
    private javax.swing.JLabel identityCompany;
    private javax.swing.JTextField identityCompanyField;
    private javax.swing.JTextField identityCountryField;
    private javax.swing.JLabel identityCountryLabel;
    private javax.swing.JTextField identityEmailField;
    private javax.swing.JLabel identityEmailLabel;
    private javax.swing.JTextField identityFirstnameField;
    private javax.swing.JLabel identityFirstnameLabel;
    private javax.swing.JTextField identityLastnameField;
    private javax.swing.JLabel identityLastnameLabel;
    private javax.swing.JTextField identityLicenseNumberField;
    private javax.swing.JLabel identityLicenseNumberLabel;
    private javax.swing.JTextField identityMiddlenameField;
    private javax.swing.JLabel identityMiddlenameLabel;
    private javax.swing.JPasswordField identityPassportNumberField;
    private javax.swing.JLabel identityPassportNumberLabel;
    private javax.swing.JToggleButton identityPassportNumberVisible;
    private javax.swing.JTextField identityPhoneField;
    private javax.swing.JLabel identityPhoneLabel;
    private javax.swing.JTextField identityPostalcodeField;
    private javax.swing.JLabel identityPostalcodeLabel;
    private javax.swing.JPasswordField identitySsnField;
    private javax.swing.JLabel identitySsnLabel;
    private javax.swing.JToggleButton identitySsnVisibile;
    private javax.swing.JTextField identityStateField;
    private javax.swing.JLabel identityStateLabel;
    private javax.swing.JTextField identityTitleField;
    private javax.swing.JLabel identityTitleLabel;
    private javax.swing.JLabel identityUsername;
    private javax.swing.JTextField identityUsernameField;
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
