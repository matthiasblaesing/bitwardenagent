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

public class DecryptedUriData {
    private String uri;
    private String uriChecksum;
    private int match;

    public DecryptedUriData() {
    }

    public DecryptedUriData(String uri, String uriChecksum, int match) {
        this.uri = uri;
        this.uriChecksum = uriChecksum;
        this.match = match;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUriChecksum() {
        return uriChecksum;
    }

    public void setUriChecksum(String uriChecksum) {
        this.uriChecksum = uriChecksum;
    }

    public int getMatch() {
        return match;
    }

    public void setMatch(int match) {
        this.match = match;
    }

}
