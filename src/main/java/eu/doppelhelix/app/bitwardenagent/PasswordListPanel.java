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

import com.formdev.flatlaf.util.HSLColor;
import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient;
import eu.doppelhelix.app.bitwardenagent.impl.DecryptedCipherData;
import eu.doppelhelix.app.bitwardenagent.impl.UtilUI;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Comparator;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
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

    private final Clipboard systemClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    private final BitwardenClient client;
    private final DefaultListModel<DecryptedCipherData> passwordListModel = new DefaultListModel<>();
    private List<DecryptedCipherData> cipherList = List.of();
    private DecryptedCipherData decryptedCipherData;
    private char passwordMask;

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
        copyIdButton.addActionListener(ae -> {
            StringSelection ss = new StringSelection(decryptedCipherData.getId());
            systemClipboard.setContents(ss, ss);
        });
        copyUsernameButton.addActionListener(ae -> {
            StringSelection ss = new StringSelection(decryptedCipherData.getLogin().getUsername());
            systemClipboard.setContents(ss, ss);
        });
        copyPasswordButton.addActionListener(ae -> {
            StringSelection ss = new StringSelection(decryptedCipherData.getLogin().getPassword());
            systemClipboard.setContents(ss, ss);
        });
        passwordList.addListSelectionListener(lse -> {
            setDecryptedCipherData(passwordList.getSelectedValue());
        });
        passwordMask = passwordField.getEchoChar();
        passwordVisible.addActionListener(ae -> {
            passwordField.setEchoChar(passwordVisible.isSelected() ? '\u0000' : passwordMask);
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
        if (this.decryptedCipherData == null) {
            idField.setText(" ");
            usernameField.setText(" ");
            passwordField.setText(" ");
            organizationTitle.setText(" ");
            passwordTitle.setText(" ");
        } else {
            idField.setText(decryptedCipherData.getId());
            passwordTitle.setText(emptyNullToSpace(decryptedCipherData.getName()));
            organizationTitle.setText(emptyNullToSpace(decryptedCipherData.getOrganization()));
            if(decryptedCipherData.getLogin() == null) {
                usernameField.setText("");
                passwordField.setText("");
            } else {
                usernameField.setText(decryptedCipherData.getLogin().getUsername());
                passwordField.setText(decryptedCipherData.getLogin().getPassword());
                copyUsernameButton.setEnabled(true);
                copyPasswordButton.setEnabled(true);
            }
        }
        updateVisiblePanel();
        revalidate();
        repaint();
        firePropertyChange("decryptedCipherData", old, this.decryptedCipherData);
    }

    private String emptyNullToSpace(String input) {
        if(input == null || input.isEmpty()) {
            return " ";
        } else {
            return input;
        }
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
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
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
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
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
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.BASELINE_LEADING;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        passwordPanel.add(passwordField, gridBagConstraints);

        passwordVisible.setIcon(CLOSED_EYE_ICON);
        passwordVisible.setMaximumSize(new java.awt.Dimension(24, 24));
        passwordVisible.setMinimumSize(new java.awt.Dimension(24, 24));
        passwordVisible.setPreferredSize(new java.awt.Dimension(24, 24));
        passwordVisible.setSelectedIcon(OPEN_EYE_ICON);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
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
        gridBagConstraints.gridx = 2;
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
        gridBagConstraints.gridx = 2;
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
        gridBagConstraints.gridx = 2;
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
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 25;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        passwordPanel.add(fillerTrailing, gridBagConstraints);

        passwordListWrapper.setRightComponent(passwordPanel);

        add(passwordListWrapper, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton copyIdButton;
    private javax.swing.JButton copyPasswordButton;
    private javax.swing.JButton copyUsernameButton;
    private javax.swing.Box.Filler fillerMiddle;
    private javax.swing.Box.Filler fillerTrailing;
    private javax.swing.JTextField idField;
    private javax.swing.JLabel idLabel;
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
    private javax.swing.JTextField usernameField;
    private javax.swing.JLabel usernameLabel;
    // End of variables declaration//GEN-END:variables
}
