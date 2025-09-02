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

import com.amdelamar.jotp.OTP;
import com.amdelamar.jotp.type.Type;
import com.formdev.flatlaf.util.HSLColor;
import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient;
import eu.doppelhelix.app.bitwardenagent.impl.CopyFieldAction;
import eu.doppelhelix.app.bitwardenagent.impl.DecryptedCipherData;
import eu.doppelhelix.app.bitwardenagent.impl.UtilUI;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignE;
import org.kordamp.ikonli.swing.FontIcon;

/**
 *
 * @author matthias
 */
public class PasswordListPanel extends javax.swing.JPanel {

    private static final System.Logger LOG = System.getLogger(PasswordListPanel.class.getName());

    private static final ImageIcon OPEN_EYE_ICON;
    private static final ImageIcon CLOSED_EYE_ICON;
    private static final ImageIcon COPY_ICON;

    static {
        FontIcon openEyeIcon = FontIcon.of(MaterialDesignE.EYE);
        FontIcon closedEyeIcon = FontIcon.of(MaterialDesignE.EYE_OFF);
        FontIcon copyIcon = FontIcon.of(MaterialDesignC.CONTENT_COPY);
        openEyeIcon.setIconSize(20);
        closedEyeIcon.setIconSize(20);
        copyIcon.setIconSize(20);
        OPEN_EYE_ICON = openEyeIcon.toImageIcon();
        CLOSED_EYE_ICON = closedEyeIcon.toImageIcon();
        COPY_ICON = copyIcon.toImageIcon();
    }

    private final BitwardenClient client;
    private final DefaultListModel<DecryptedCipherData> passwordListModel = new DefaultListModel<>();
    private List<DecryptedCipherData> cipherList = List.of();
    private DecryptedCipherData decryptedCipherData;
    private char passwordMask;
    private Timer totpTimer = new Timer(5000, ae -> updateTotpEvaluated());

    /**
     * Creates new form PasswordListPanel
     */
    public PasswordListPanel(BitwardenClient client) {
        this.client = client;
        initComponents();
        updateVisiblePanel();
        passwordPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                GridBagLayout gbl = (GridBagLayout) passwordPanel.getLayout();
                GridBagConstraints middleConstraints = gbl.getConstraints(fillerMiddle);
                GridBagConstraints trailingConstraints = gbl.getConstraints(fillerTrailing);
                if(passwordPanel.getPreferredSize().getWidth() > passwordPanel.getWidth()) {
                    middleConstraints.weightx = 1;
                    trailingConstraints.weightx = 0;
                } else {
                    middleConstraints.weightx = 0;
                    trailingConstraints.weightx = 1;
                }
                gbl.setConstraints(fillerMiddle, middleConstraints);
                gbl.setConstraints(fillerTrailing, trailingConstraints);
            }
        });

        copyIdButton.addActionListener(new CopyFieldAction(idField));
        copyUsernameButton.addActionListener(new CopyFieldAction(usernameField));
        copyPasswordButton.addActionListener(new CopyFieldAction(passwordField));
        copyTotpButton.addActionListener(new CopyFieldAction(totpField));
        copyTotpEvaluatedButton.addActionListener(new CopyFieldAction(totpEvaluatedField));
        copySshPrivateKey.addActionListener(new CopyFieldAction(sshPrivateKeyField));
        copySshPublicKey.addActionListener(new CopyFieldAction(sshPublicKeyField));
        copySshFingerprint.addActionListener(new CopyFieldAction(sshFingerprintField));
        copyNotes.addActionListener(new CopyFieldAction(notesField));

        passwordList.addListSelectionListener(lse -> {
            setDecryptedCipherData(passwordList.getSelectedValue());
        });
        passwordMask = passwordField.getEchoChar();
        passwordVisible.addActionListener(ae -> {
            passwordField.setEchoChar(passwordVisible.isSelected() ? '\u0000' : passwordMask);
        });
        totpVisibleButton.addActionListener(ae -> {
            totpField.setEchoChar(totpVisibleButton.isSelected() ? '\u0000' : passwordMask);
        });
        passwordList.setCellRenderer(new DefaultListCellRenderer() {
            private final JLabel nameLabel = new JLabel();
            private final JLabel organizationLabel = new JLabel();
            private final JPanel listEntryPanel;

            {
                listEntryPanel = new JPanel();
                listEntryPanel.setLayout(new BoxLayout(listEntryPanel, BoxLayout.PAGE_AXIS));
                listEntryPanel.add(nameLabel);
                listEntryPanel.add(organizationLabel);
                nameLabel.setOpaque(false);
                organizationLabel.setOpaque(false);
                listEntryPanel.setOpaque(true);
            }

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JComponent superComponent = (JComponent) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                DecryptedCipherData dcd = (DecryptedCipherData) value;
                Color backgroundColor = superComponent.getBackground();
                if ((index % 2 == 1) && (!(isSelected || cellHasFocus))) {
                    HSLColor color = new HSLColor(backgroundColor);
                    if (color.getLuminance() > 50) {
                        backgroundColor = color.adjustLuminance(color.getLuminance() - 5);
                    } else {
                        backgroundColor = color.adjustLuminance(color.getLuminance() + 5);
                    }
                }
                nameLabel.setText(emptyNullToSpace(dcd.getName()));
                organizationLabel.setText(emptyNullToSpace(dcd.getOrganization()));
                listEntryPanel.setComponentOrientation(superComponent.getComponentOrientation());
                nameLabel.setForeground(superComponent.getForeground());
                nameLabel.setFont(superComponent.getFont());
                organizationLabel.setForeground(superComponent.getForeground());
                organizationLabel.setFont(superComponent.getFont().deriveFont(Font.ITALIC));
                listEntryPanel.setBorder(superComponent.getBorder());
                listEntryPanel.setBackground(backgroundColor);
                return listEntryPanel;
            }
        });
        passwordList.setModel(passwordListModel);
        client.addStateObserver((oldState, newState) -> updatePasswordsFromClient());
        updatePasswordsFromClient();
        passwordListFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateFilteredList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateFilteredList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateFilteredList();
            }
        });
    }

    private void updateVisiblePanel() {
        int dividerLocation = passwordListWrapper.getDividerLocation();
        passwordPanel.setVisible(decryptedCipherData != null);
        selectEntryPanel.setVisible(decryptedCipherData == null);
        if(decryptedCipherData == null) {
            passwordListWrapper.setRightComponent(selectEntryPanel);
        } else {
            passwordListWrapper.setRightComponent(passwordPanel);
        }
        passwordListWrapper.setDividerLocation(dividerLocation);
        revalidate();
        repaint();
    }

    private void updatePasswordsFromClient() {
        UtilUI.runOffTheEdt(
                () -> {
                    return client.getSyncData();
                },
                (sd) -> {
                    cipherList = sd.getCiphers();
                    cipherList.sort(Comparator.nullsFirst(Comparator.comparing(c -> c.getName())));
                    updateFilteredList();
                },
                (ex) -> {
                }
        );
    }

    private void updateFilteredList() {
        String filterText = passwordListFilter.getText().toLowerCase();
        String selectedId = decryptedCipherData != null ? decryptedCipherData.getId() : null;
        List<DecryptedCipherData> filteredList = cipherList.stream().filter(dcd -> dcd.getName().toLowerCase().contains(filterText)).toList();
        passwordListModel.removeAllElements();
        passwordListModel.addAll(filteredList);
        DecryptedCipherData newSelected;
        if (selectedId == null) {
            newSelected = null;
        } else {
            newSelected = filteredList
                    .stream()
                    .filter(dcd -> selectedId.equals(dcd.getId()))
                    .findFirst()
                    .orElse(null);
        }
        setDecryptedCipherData(newSelected);
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
        organizationTitle.setText(emptyNullToSpace(null));
        passwordTitle.setText(emptyNullToSpace(null));
        if (this.decryptedCipherData != null) {
            idField.setText(decryptedCipherData.getId());
            passwordTitle.setText(emptyNullToSpace(decryptedCipherData.getName()));
            organizationTitle.setText(emptyNullToSpace(decryptedCipherData.getOrganization()));
            notesField.setText(decryptedCipherData.getNotes());
            if(decryptedCipherData.getLogin() != null) {
                showLoginFields(true);
                usernameField.setText(decryptedCipherData.getLogin().getUsername());
                passwordField.setText(decryptedCipherData.getLogin().getPassword());
                totpField.setText(decryptedCipherData.getLogin().getTotp());
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
                sshPublicKeyField.setText(decryptedCipherData.getSshKey().getPublicKey());
                sshFingerprintField.setText(decryptedCipherData.getSshKey().getKeyFingerprint());
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
        updateVisiblePanel();
        updateTotpEvaluated();
        revalidate();
        repaint();
        firePropertyChange("decryptedCipherData", old, this.decryptedCipherData);
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

    private String emptyNullToSpace(String input) {
        if(input == null || input.isEmpty()) {
            return " ";
        } else {
            return input;
        }
    }

    private boolean isNotNullNotEmpty(String input) {
        return input != null && ! input.isBlank();
    }

    private void updateTotpEvaluated() {
        String code = null;
        try {
            // TODO implement variants with algorithm=SHA256 and algorithm=SHA512
            Map<String,String> parameters = extractSecretFromOtpUrl(totpField.getText());
            String hexTime = OTP.timeInHex(
                    System.currentTimeMillis(),
                    Integer.parseInt(parameters.getOrDefault("period", "30"))
            );
            code = OTP.create(
                    parameters.getOrDefault("secret", ""),
                    hexTime,
                    Integer.parseInt(parameters.getOrDefault("digits", "6")),
                    Type.TOTP);
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

    private Map<String,String> extractSecretFromOtpUrl(String input) {
        if(input == null || input.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(totpField.getText());
            return Arrays.stream(uri.getRawQuery().split("&"))
                    .filter(queryPart -> queryPart.contains("="))
                    .map(queryPart -> {
                        String[] queryParts = queryPart.split("=", 2);
                        String key = URLDecoder.decode(queryParts[0], StandardCharsets.UTF_8).toLowerCase();
                        String value = URLDecoder.decode(queryParts[1], StandardCharsets.UTF_8);
                        return new HashMap.SimpleEntry<>(key, value);
                    })
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        } catch (Exception ex) {
            LOG.log(Level.DEBUG, "Failed to parse URL", ex);
        }
        return null;
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

        passwordListWrapper = new javax.swing.JSplitPane();
        passwordListPanel = new javax.swing.JPanel();
        passwordListFilter = new javax.swing.JTextField();
        passwordListScrollPane = new javax.swing.JScrollPane();
        passwordList = new javax.swing.JList<>();
        selectEntryPanel = new javax.swing.JPanel();
        selectEntryLabel = new javax.swing.JLabel();
        passwordPanel = new javax.swing.JPanel();
        passwordTitle = new javax.swing.JLabel();
        idLabel = new javax.swing.JLabel();
        idField = new javax.swing.JTextField();
        usernameLabel = new javax.swing.JLabel();
        usernameField = new javax.swing.JTextField();
        passwordLabel = new javax.swing.JLabel();
        passwordField = new javax.swing.JPasswordField();
        passwordVisible = new javax.swing.JToggleButton();
        organizationTitle = new javax.swing.JLabel();
        copyIdButton = new javax.swing.JButton();
        copyUsernameButton = new javax.swing.JButton();
        copyPasswordButton = new javax.swing.JButton();
        fillerMiddle = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 32767));
        fillerTrailing = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 32767));
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

        setLayout(new java.awt.BorderLayout());

        passwordListWrapper.setDividerLocation(250);

        passwordListPanel.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        passwordListPanel.add(passwordListFilter, gridBagConstraints);

        passwordList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        passwordListScrollPane.setViewportView(passwordList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordListPanel.add(passwordListScrollPane, gridBagConstraints);

        passwordListWrapper.setLeftComponent(passwordListPanel);

        selectEntryPanel.setLayout(new java.awt.GridBagLayout());

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("eu/doppelhelix/app/bitwardenagent/Bundle"); // NOI18N
        selectEntryLabel.setText(bundle.getString("selectEntryLabel")); // NOI18N
        selectEntryPanel.add(selectEntryLabel, new java.awt.GridBagConstraints());

        passwordListWrapper.setRightComponent(selectEntryPanel);

        passwordPanel.setLayout(new java.awt.GridBagLayout());

        passwordTitle.setFont(passwordTitle.getFont().deriveFont(passwordTitle.getFont().getStyle() | java.awt.Font.BOLD, passwordTitle.getFont().getSize()+5));
        passwordTitle.setText("<Title>");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(passwordTitle, gridBagConstraints);

        idLabel.setText("ID:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(idLabel, gridBagConstraints);

        idField.setEditable(false);
        idField.setColumns(25);
        idField.setText("4d3be0b0-1425-4bb8-8725-b32d01134b64");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(idField, gridBagConstraints);

        usernameLabel.setText(bundle.getString("usernameLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(usernameLabel, gridBagConstraints);

        usernameField.setEditable(false);
        usernameField.setColumns(25);
        usernameField.setText("demo@invalid");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(usernameField, gridBagConstraints);

        passwordLabel.setText(bundle.getString("passwordLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(passwordLabel, gridBagConstraints);

        passwordField.setEditable(false);
        passwordField.setColumns(25);
        passwordField.setText("DemoPassword");
        passwordField.setToolTipText("");
        passwordField.putClientProperty("JPasswordField.cutCopyAllowed", true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 4;
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
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(passwordVisible, gridBagConstraints);

        organizationTitle.setFont(organizationTitle.getFont().deriveFont((organizationTitle.getFont().getStyle() | java.awt.Font.ITALIC)));
        organizationTitle.setText("<organization>");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(organizationTitle, gridBagConstraints);

        copyIdButton.setIcon(COPY_ICON);
        copyIdButton.setMaximumSize(new java.awt.Dimension(24, 24));
        copyIdButton.setMinimumSize(new java.awt.Dimension(24, 24));
        copyIdButton.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyIdButton, gridBagConstraints);
        copyIdButton.getAccessibleContext().setAccessibleName("Copy Id");

        copyUsernameButton.setIcon(COPY_ICON);
        copyUsernameButton.setMaximumSize(new java.awt.Dimension(24, 24));
        copyUsernameButton.setMinimumSize(new java.awt.Dimension(24, 24));
        copyUsernameButton.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyUsernameButton, gridBagConstraints);
        copyUsernameButton.getAccessibleContext().setAccessibleName("Copy Username");

        copyPasswordButton.setIcon(COPY_ICON);
        copyPasswordButton.setMaximumSize(new java.awt.Dimension(24, 24));
        copyPasswordButton.setMinimumSize(new java.awt.Dimension(24, 24));
        copyPasswordButton.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyPasswordButton, gridBagConstraints);
        copyPasswordButton.getAccessibleContext().setAccessibleName("Copy Password");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 25;
        gridBagConstraints.weighty = 1.0;
        passwordPanel.add(fillerMiddle, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 25;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        passwordPanel.add(fillerTrailing, gridBagConstraints);

        totpLabel.setText(bundle.getString("totpLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(totpLabel, gridBagConstraints);

        totpField.setEditable(false);
        totpField.setColumns(25);
        totpField.setText("jPasswordField1");
        totpField.putClientProperty("JPasswordField.cutCopyAllowed", true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(totpField, gridBagConstraints);

        totpEvaluatedField.setEditable(false);
        totpEvaluatedField.setColumns(25);
        totpEvaluatedField.setText("123456");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 6;
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
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyTotpButton, gridBagConstraints);

        totpVisibleButton.setIcon(CLOSED_EYE_ICON);
        totpVisibleButton.setMaximumSize(new java.awt.Dimension(24, 24));
        totpVisibleButton.setMinimumSize(new java.awt.Dimension(24, 24));
        totpVisibleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        totpVisibleButton.setSelectedIcon(OPEN_EYE_ICON);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(totpVisibleButton, gridBagConstraints);

        copyTotpEvaluatedButton.setIcon(COPY_ICON);
        copyTotpEvaluatedButton.setMaximumSize(new java.awt.Dimension(24, 24));
        copyTotpEvaluatedButton.setMinimumSize(new java.awt.Dimension(24, 24));
        copyTotpEvaluatedButton.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyTotpEvaluatedButton, gridBagConstraints);

        sshPrivateKeyLabel.setText(bundle.getString("sshPrivateKeyLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(sshPrivateKeyLabel, gridBagConstraints);

        sshPublicKeyLabel.setText(bundle.getString("sshPublicKeyLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(sshPublicKeyLabel, gridBagConstraints);

        sshFingerprintLabel.setText(bundle.getString("sshFingerprintLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(sshFingerprintLabel, gridBagConstraints);

        sshPrivateKeyField.setEditable(false);
        sshPrivateKeyField.setColumns(25);
        sshPrivateKeyField.setRows(5);
        sshPrivateKeyScrollPane.setViewportView(sshPrivateKeyField);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(sshPrivateKeyScrollPane, gridBagConstraints);

        sshPublicKeyField.setEditable(false);
        sshPublicKeyField.setColumns(25);
        sshPublicKeyField.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(sshPublicKeyField, gridBagConstraints);

        sshFingerprintField.setEditable(false);
        sshFingerprintField.setColumns(25);
        sshFingerprintField.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 9;
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
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copySshPrivateKey, gridBagConstraints);

        copySshPublicKey.setIcon(COPY_ICON);
        copySshPublicKey.setMaximumSize(new java.awt.Dimension(24, 24));
        copySshPublicKey.setMinimumSize(new java.awt.Dimension(24, 24));
        copySshPublicKey.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copySshPublicKey, gridBagConstraints);

        copySshFingerprint.setIcon(COPY_ICON);
        copySshFingerprint.setMaximumSize(new java.awt.Dimension(24, 24));
        copySshFingerprint.setMinimumSize(new java.awt.Dimension(24, 24));
        copySshFingerprint.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copySshFingerprint, gridBagConstraints);

        notesLabel.setText(bundle.getString("notesLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(notesLabel, gridBagConstraints);

        copyNotes.setIcon(COPY_ICON);
        copyNotes.setMaximumSize(new java.awt.Dimension(24, 24));
        copyNotes.setMinimumSize(new java.awt.Dimension(24, 24));
        copyNotes.setPreferredSize(new java.awt.Dimension(24, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(copyNotes, gridBagConstraints);

        notesField.setEditable(false);
        notesField.setColumns(20);
        notesField.setRows(5);
        notesScrollPane.setViewportView(notesField);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(notesScrollPane, gridBagConstraints);

        passwordListWrapper.setRightComponent(passwordPanel);

        add(passwordListWrapper, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
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
    private javax.swing.Box.Filler fillerTrailing;
    private javax.swing.JTextField idField;
    private javax.swing.JLabel idLabel;
    private javax.swing.JTextArea notesField;
    private javax.swing.JLabel notesLabel;
    private javax.swing.JScrollPane notesScrollPane;
    private javax.swing.JLabel organizationTitle;
    private javax.swing.JPasswordField passwordField;
    private javax.swing.JLabel passwordLabel;
    private javax.swing.JList<DecryptedCipherData> passwordList;
    private javax.swing.JTextField passwordListFilter;
    private javax.swing.JPanel passwordListPanel;
    private javax.swing.JScrollPane passwordListScrollPane;
    private javax.swing.JSplitPane passwordListWrapper;
    private javax.swing.JPanel passwordPanel;
    private javax.swing.JLabel passwordTitle;
    private javax.swing.JToggleButton passwordVisible;
    private javax.swing.JLabel selectEntryLabel;
    private javax.swing.JPanel selectEntryPanel;
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
