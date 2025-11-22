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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DecryptedSyncData {

    private String id;
    private String email;
    private String name;
    private final Map<String, String> organizationNames = new HashMap<>();
    private final Map<String, String> folderNames = new HashMap<>();
    private final Map<String, String> collectionNames = new HashMap<>();
    private final List<DecryptedCipherData> ciphers = new ArrayList<>();
    private final List<DecryptedCollection> collections = new ArrayList<>();
    private final List<DecryptedFolder> folder = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getOrganizationNames() {
        return organizationNames;
    }

    public Map<String, String> getFolderNames() {
        return folderNames;
    }

    public Map<String, String> getCollectionNames() {
        return collectionNames;
    }

    public List<DecryptedCipherData> getCiphers() {
        return ciphers;
    }

    public List<DecryptedCollection> getCollections() {
        return collections;
    }

    public List<DecryptedFolder> getFolder() {
        return folder;
    }

}
