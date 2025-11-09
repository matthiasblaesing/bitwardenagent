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

import eu.doppelhelix.app.bitwardenagent.http.FieldData;
import java.util.ArrayList;
import java.util.List;

public class DecryptedCipherData {

    private String id;
    private String name;
    private String organizationId;
    private String organization;
    private DecryptedLoginData login;
    private DecryptedSshKey sshKey;
    private String notes;
    private final List<DecryptedFieldData> fields = new ArrayList<> ();
    private String folderId;
    private final List<String> collectionIds = new ArrayList<>();

    public DecryptedCipherData() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public DecryptedLoginData getLogin() {
        return login;
    }

    public void setLogin(DecryptedLoginData login) {
        this.login = login;
    }

    public DecryptedSshKey getSshKey() {
        return sshKey;
    }

    public void setSshKey(DecryptedSshKey sshKey) {
        this.sshKey = sshKey;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<DecryptedFieldData> getFields() {
        return fields;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public List<String> getCollectionIds() {
        return collectionIds;
    }

}
