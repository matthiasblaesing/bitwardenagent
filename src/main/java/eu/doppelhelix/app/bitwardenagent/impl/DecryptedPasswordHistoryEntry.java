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

import java.time.OffsetDateTime;

public class DecryptedPasswordHistoryEntry {
    private String password;
    private OffsetDateTime lastUsedDate;

    public DecryptedPasswordHistoryEntry() {
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public OffsetDateTime getLastUsedDate() {
        return lastUsedDate;
    }

    public void setLastUsedDate(OffsetDateTime lastUsedDate) {
        this.lastUsedDate = lastUsedDate;
    }

}
