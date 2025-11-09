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
package eu.doppelhelix.app.bitwardenagent.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.swing.Icon;
import javax.swing.tree.TreeNode;


public class OUFolderTreeNode implements TreeNode {

    private final OUFolderTreeNode parent;
    private final List<OUFolderTreeNode> childNodes = new ArrayList<>();
    private final String name;
    private final Icon icon;
    private boolean unnamedFolder;
    private String folderId;
    private String collectionId;
    private String organisationId;

    @SuppressWarnings("LeakingThisInConstructor")
    public OUFolderTreeNode(OUFolderTreeNode parent, String name, Icon icon) {
        this.parent = parent;
        this.name = name;
        this.icon = icon;
        if(parent != null) {
            parent.childNodes.add(this);
        }
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
    }

    public boolean isUnnamedFolder() {
        return unnamedFolder;
    }

    public void setUnnamedFolder(boolean unnamedFolder) {
        this.unnamedFolder = unnamedFolder;
    }

    public String getName() {
        return name;
    }

    public Icon getIcon() {
        return icon;
    }

    @Override
    public TreeNode getChildAt(int childIndex) {
        return childNodes.get(childIndex);
    }

    @Override
    public int getChildCount() {
        return childNodes.size();
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public int getIndex(TreeNode node) {
        return childNodes.indexOf(node);
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return childNodes.isEmpty();
    }

    @Override
    public Enumeration<? extends TreeNode> children() {
        return Collections.enumeration(childNodes);
    }

    public List<OUFolderTreeNode> getChildren() {
        return Collections.unmodifiableList(childNodes);
    }

}
