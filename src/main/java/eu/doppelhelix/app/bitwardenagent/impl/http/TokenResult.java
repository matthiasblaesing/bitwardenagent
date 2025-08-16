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
package eu.doppelhelix.app.bitwardenagent.impl.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenResult(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("expires_in")
        int expiresIn,
        @JsonProperty("token_type")
        String tokenType,
        @JsonProperty("refresh_token")
        String refreshToken,
        String scope,
        @JsonProperty("privateKey")
        String privateKey,
        String key,
        @JsonProperty("ForcePasswordreset")
        boolean forcePasswordreset,
        @JsonProperty("resetMasterPassword")
        boolean ResetMasterPassword,
        @JsonProperty("Kdf")
        int kdf,
        @JsonProperty("KdfIterations")
        Integer kdfIterations,
        @JsonProperty("KdfMemory")
        Integer kdfMemory,
        @JsonProperty("KdfParallelism")
        Integer kdfParallelism,
        @JsonProperty("UserDecryptionOptions")
        UserDecryptionOptions userDecryptionOptions
        ) {

}
