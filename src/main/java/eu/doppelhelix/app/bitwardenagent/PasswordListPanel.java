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
import eu.doppelhelix.app.bitwardenagent.impl.DecryptedSyncData;
import eu.doppelhelix.app.bitwardenagent.impl.OUFolderTreeNode;
import eu.doppelhelix.app.bitwardenagent.impl.UtilUI;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import static eu.doppelhelix.app.bitwardenagent.impl.UtilUI.FOLDER_ICON;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilUI.OFFICE_BUILDING_ICON;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilUI.FOLDER_NETWORK_ICON;
import static java.util.Arrays.stream;
import static java.util.stream.Stream.ofNullable;
import static eu.doppelhelix.app.bitwardenagent.impl.UtilUI.emptyNullToSpace;

public class PasswordListPanel extends javax.swing.JPanel {

    private static final System.Logger LOG = System.getLogger(PasswordListPanel.class.getName());

    private final BitwardenClient client;
    private final DefaultListModel<DecryptedCipherData> passwordListModel = new DefaultListModel<>();
    private List<DecryptedCipherData> cipherList = List.of();
    private DefaultTreeModel passwordListGroupModel = new DefaultTreeModel(null);
    private Set<String> selectedOrganizations = new HashSet<>();
    private Set<String> selectedCollections = new HashSet<>();
    private Set<String> selectedFolders = new HashSet<>();
    private boolean selectedUnnamedFolder = false;

    public PasswordListPanel(BitwardenClient client) {
        this.client = client;
        initComponents();
        updateVisiblePanel();
        passwordListScrollPane.getHorizontalScrollBar().setUnitIncrement(getFont().getSize());
        passwordListScrollPane.getHorizontalScrollBar().setUnitIncrement(getFont().getSize());
        passwordList.addListSelectionListener(lse -> {
            passwordPanel.setDecryptedCipherData(passwordList.getSelectedValue());
            updateVisiblePanel();
        });
        passwordList.setCellRenderer(new DefaultListCellRenderer() {
            private final JLabel nameLabel = new JLabel();
            private final JLabel subtitle = new JLabel();
            private final JLabel officeIcon;
            private final JPanel listEntryPanel;
            private final JPanel namePanel;

            {
                officeIcon = new JLabel("", UtilUI.FOLDER_NETWORK_ICON, JLabel.LEADING);
                officeIcon.setBorder(new EmptyBorder(0, 5, 0, 0));
                namePanel = new JPanel();
                namePanel.setLayout(new FlowLayout(FlowLayout.LEADING, 0, 0));
                namePanel.add(nameLabel);
                namePanel.add(officeIcon);
                namePanel.setBorder(new EmptyBorder(2, 0, 0, 0));
                listEntryPanel = new JPanel();
                listEntryPanel.setLayout(new BoxLayout(listEntryPanel, BoxLayout.PAGE_AXIS));
                listEntryPanel.add(namePanel);
                listEntryPanel.add(subtitle);
                namePanel.setAlignmentX(0);
                subtitle.setAlignmentX(0);
                nameLabel.setOpaque(false);
                subtitle.setOpaque(false);
                subtitle.setBorder(new EmptyBorder(0, 0, 2, 0));
                namePanel.setOpaque(false);
                listEntryPanel.setOpaque(true);
            }

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JComponent superComponent = (JComponent) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                DecryptedCipherData dcd = (DecryptedCipherData) value;
                Color subtitleColor;
                HSLColor color = new HSLColor(superComponent.getForeground());
                if (!(isSelected || cellHasFocus)) {
                    if (color.getLuminance() > 50) {
                        subtitleColor = color.adjustLuminance(color.getLuminance() - 35);
                    } else {
                        subtitleColor = color.adjustLuminance(color.getLuminance() + 35);
                    }
                } else {
                    subtitleColor = superComponent.getForeground();
                }
                nameLabel.setText(emptyNullToSpace(dcd.getName()));
                if(dcd.getLogin() != null) {
                    subtitle.setText(emptyNullToSpace(dcd.getLogin().getUsername()));
                } else if(dcd.getSshKey() != null) {
                    subtitle.setText(emptyNullToSpace(dcd.getSshKey().getKeyFingerprint()));
                } else {
                    subtitle.setText(" ");
                }
                subtitle.setForeground(Color.lightGray);
                listEntryPanel.setComponentOrientation(superComponent.getComponentOrientation());
                nameLabel.setForeground(superComponent.getForeground());
                nameLabel.setFont(superComponent.getFont());
                subtitle.setForeground(subtitleColor);
                subtitle.setFont(superComponent.getFont().deriveFont(Font.ITALIC));
                listEntryPanel.setBorder(superComponent.getBorder());
                listEntryPanel.setBackground(superComponent.getBackground());
                officeIcon.setVisible(dcd.getOrganizationId() != null);
                return listEntryPanel;
            }
        });
        passwordList.setModel(passwordListModel);
        client.addStateObserver((oldState, newState) -> updatePasswordsFromClient());
        updatePasswordsFromClient();
        passwordListQuickFilter.getDocument().addDocumentListener(new DocumentListener() {
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
        passwordListGroupSelector.setRootVisible(true);
        passwordListGroupSelector.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                JLabel renderer = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                boolean plainFont = true;
                if(value instanceof OUFolderTreeNode ouftn) {
                    if(ouftn.getIcon() != null) {
                        renderer.setIcon(ouftn.getIcon());
                    }
                    renderer.setText(ouftn.getName());
                    if (ouftn.getOrganisationId() == null && ouftn.getFolderId() == null && ouftn.getCollectionId() == null && ! ouftn.isUnnamedFolder()) {
                        renderer.setForeground(Color.DARK_GRAY);
                        plainFont = false;
                    }
                }
                if(plainFont) {
                    renderer.setFont(renderer.getFont().deriveFont(Font.PLAIN));
                } else {
                    renderer.setFont(renderer.getFont().deriveFont(Font.ITALIC));
                }
                return renderer;
            }
        });
        passwordListGroupSelector.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent tse) {
                List<OUFolderTreeNode> selectedNodes = new LinkedList<>();
                selectedNodes.addAll(
                        ofNullable(passwordListGroupSelector.getSelectionPaths())
                        .flatMap(array -> stream(array))
                        .map(treePath -> (OUFolderTreeNode) treePath.getLastPathComponent())
                        .collect(Collectors.toList()));
                selectedCollections.clear();
                selectedFolders.clear();
                selectedOrganizations.clear();
                selectedUnnamedFolder = false;
                selectedNodes.forEach(ouftn -> {
                    if(ouftn.getOrganisationId() != null) {
                        selectedOrganizations.add(ouftn.getOrganisationId());
                    }
                    if(ouftn.getFolderId() != null) {
                        selectedFolders.add(ouftn.getFolderId());
                    }
                    if(ouftn.getCollectionId() != null) {
                        selectedCollections.add(ouftn.getCollectionId());
                    }
                    selectedUnnamedFolder |= ouftn.isUnnamedFolder();
                });
                updateFilteredList();
            }
        });
    }

    private void updateVisiblePanel() {
        int dividerLocation = passwordListWrapper.getDividerLocation();
        passwordPanelWrapper.setVisible(passwordPanel.getDecryptedCipherData() != null);
        selectEntryPanel.setVisible(passwordPanel.getDecryptedCipherData() == null);
        if(passwordPanel.getDecryptedCipherData() == null) {
            passwordListWrapper.setRightComponent(selectEntryPanel);
        } else {
            passwordListWrapper.setRightComponent(passwordPanelWrapper);
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
                    if (sd != null) {
                        cipherList = sd.getCiphers();
                        cipherList.sort(Comparator.nullsFirst(Comparator.comparing(c -> c.getName())));
                    } else {
                        cipherList = List.of();
                    }
                    TreeNode rootNode = buildSelectionNode(sd);
                    passwordListGroupModel.setRoot(rootNode);
                    Consumer<TreePath> pathExpander = new Consumer<TreePath>() {
                        @Override
                        public void accept(TreePath path) {
                            passwordListGroupSelector.expandPath(path);
                            ((TreeNode) path.getLastPathComponent())
                                    .children()
                                    .asIterator()
                                    .forEachRemaining(tn -> {
                                        TreePath childPath = path.pathByAddingChild(tn);
                                        accept(childPath);
                                    });
                        }
                    };
                    pathExpander.accept(new TreePath(rootNode));
                    updateFilteredList();
                },
                (ex) -> {
                    LOG.log(System.Logger.Level.WARNING, "Failed to parse sync data", ex);
                }
        );
    }

    private OUFolderTreeNode buildSelectionNode(DecryptedSyncData dsd) {
        OUFolderTreeNode rootNode = new OUFolderTreeNode(null, "Filter", null);
        if(dsd != null) {
            OUFolderTreeNode foldersNode = new OUFolderTreeNode(rootNode, "Folders", FOLDER_ICON);
            foldersNode.setUnnamedFolder(true);
            OUFolderTreeNode collectionsNode = new OUFolderTreeNode(rootNode, "Organisations", OFFICE_BUILDING_ICON);
            Map<String, OUFolderTreeNode> folderNodes = new HashMap<>();
            folderNodes.put("", foldersNode);
            dsd.getFolder().forEach(df -> {
                String[] path = df.getName().split("/");
                String pathCombined = "";
                for(int i = 0; i < path.length; i++) {
                    OUFolderTreeNode parentNode = folderNodes.get(pathCombined);
                    String folderName = path[i];
                    pathCombined = pathCombined + "/" + folderName;
                    if (!folderNodes.containsKey(pathCombined)) {
                        OUFolderTreeNode folderNode = new OUFolderTreeNode(parentNode, folderName, FOLDER_ICON);
                        folderNodes.put(pathCombined, folderNode);
                    }
                    if(i == path.length - 1) {
                        folderNodes.get(pathCombined).setFolderId(df.getId());
                    }
                }
            });
            Map<String, OUFolderTreeNode> collectionNodes = new HashMap<>();
            dsd.getOrganizationNames().entrySet().forEach(e -> {
                OUFolderTreeNode organizationNode = new OUFolderTreeNode(collectionsNode, e.getValue(), OFFICE_BUILDING_ICON);
                organizationNode.setOrganisationId(e.getKey());
                collectionNodes.put(e.getKey(), organizationNode);
            });
            dsd.getCollections().forEach(dc -> {
                String[] path = dc.getName().split("/");
                OUFolderTreeNode parentNode = collectionNodes.get(dc.getOrganizationId());
                String pathCombined = dc.getOrganizationId();
                for (String collectionName : path) {
                    pathCombined = pathCombined + "/" + collectionName;
                    if (!collectionNodes.containsKey(pathCombined)) {
                        OUFolderTreeNode collectionNode = new OUFolderTreeNode(parentNode, collectionName, FOLDER_NETWORK_ICON);
                        collectionNodes.put(pathCombined, collectionNode);
                        parentNode = collectionNode;
                    } else {
                        parentNode = collectionNodes.get(pathCombined);
                    }
                }
                parentNode.setCollectionId(dc.getId());
            });
        }
        return rootNode;
    }

    private void updateFilteredList() {
        DecryptedCipherData decryptedCipherData = passwordPanel.getDecryptedCipherData();
        String filterText = passwordListQuickFilter.getText().toLowerCase();
        String selectedId = decryptedCipherData != null ? decryptedCipherData.getId() : null;
        List<DecryptedCipherData> filteredList = cipherList.stream().filter(dcd -> {
            return dcd.getName().toLowerCase().contains(filterText) && (
                    (selectedCollections.isEmpty() && selectedFolders.isEmpty() && selectedOrganizations.isEmpty() && ! selectedUnnamedFolder)
                    || selectedOrganizations.contains(dcd.getOrganizationId())
                    || selectedFolders.contains(dcd.getFolderId())
                    || ( selectedUnnamedFolder && dcd.getOrganizationId() == null && dcd.getFolderId() == null)
                    || dcd.getCollectionIds().stream().anyMatch(c -> selectedCollections.contains(c))
            );
        }).toList();
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
        passwordPanel.setDecryptedCipherData(newSelected);
        updateVisiblePanel();
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
        passwordListPanel = new javax.swing.JSplitPane();
        passwordListFilterPanel = new javax.swing.JPanel();
        passwordListQuickFilter = new javax.swing.JTextField();
        passwordListGroupSelectorWrapper = new javax.swing.JScrollPane();
        passwordListGroupSelector = new javax.swing.JTree();
        passwordListWarpper2 = new javax.swing.JPanel();
        passwordListScrollPane = new javax.swing.JScrollPane();
        passwordList = new javax.swing.JList<>();
        selectEntryPanel = new javax.swing.JPanel();
        selectEntryLabel = new javax.swing.JLabel();
        passwordPanelWrapper = new javax.swing.JPanel();
        passwordPanel = new eu.doppelhelix.app.bitwardenagent.PasswordPanel();

        setLayout(new java.awt.BorderLayout());

        passwordListWrapper.setDividerLocation(500);

        passwordListPanel.setDividerLocation(250);
        passwordListPanel.setResizeWeight(0.5);

        passwordListFilterPanel.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        passwordListFilterPanel.add(passwordListQuickFilter, gridBagConstraints);

        passwordListGroupSelector.setModel(passwordListGroupModel);
        passwordListGroupSelectorWrapper.setViewportView(passwordListGroupSelector);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 3);
        passwordListFilterPanel.add(passwordListGroupSelectorWrapper, gridBagConstraints);

        passwordListPanel.setLeftComponent(passwordListFilterPanel);

        passwordListWarpper2.setLayout(new java.awt.GridBagLayout());

        passwordList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        passwordListScrollPane.setViewportView(passwordList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 3, 5, 3);
        passwordListWarpper2.add(passwordListScrollPane, gridBagConstraints);

        passwordListPanel.setRightComponent(passwordListWarpper2);

        passwordListWrapper.setLeftComponent(passwordListPanel);

        selectEntryPanel.setLayout(new java.awt.GridBagLayout());

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("eu/doppelhelix/app/bitwardenagent/Bundle"); // NOI18N
        selectEntryLabel.setText(bundle.getString("selectEntryLabel")); // NOI18N
        selectEntryPanel.add(selectEntryLabel, new java.awt.GridBagConstraints());

        passwordListWrapper.setRightComponent(selectEntryPanel);

        passwordPanelWrapper.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 3, 5, 5);
        passwordPanelWrapper.add(passwordPanel, gridBagConstraints);

        passwordListWrapper.setRightComponent(passwordPanelWrapper);

        add(passwordListWrapper, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList<DecryptedCipherData> passwordList;
    private javax.swing.JPanel passwordListFilterPanel;
    private javax.swing.JTree passwordListGroupSelector;
    private javax.swing.JScrollPane passwordListGroupSelectorWrapper;
    private javax.swing.JSplitPane passwordListPanel;
    private javax.swing.JTextField passwordListQuickFilter;
    private javax.swing.JScrollPane passwordListScrollPane;
    private javax.swing.JPanel passwordListWarpper2;
    private javax.swing.JSplitPane passwordListWrapper;
    private eu.doppelhelix.app.bitwardenagent.PasswordPanel passwordPanel;
    private javax.swing.JPanel passwordPanelWrapper;
    private javax.swing.JLabel selectEntryLabel;
    private javax.swing.JPanel selectEntryPanel;
    // End of variables declaration//GEN-END:variables
}
