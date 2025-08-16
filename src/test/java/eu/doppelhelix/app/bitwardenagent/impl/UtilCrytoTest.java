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

import eu.doppelhelix.app.bitwardenagent.impl.http.PreloginResult;
import org.junit.jupiter.api.Test;

import static eu.doppelhelix.app.bitwardenagent.impl.UtilCryto.encryptionKeyFromMasterKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UtilCrytoTest {

    public UtilCrytoTest() {
    }

    @Test
    public void testEncryptionRoundTrip() throws Exception {
        PreloginResult preloginResult = new PreloginResult(
                PreloginResult.KDF.PBKDF2,
                600000,
                null,
                null
        );
        String email = "dummy@example.com";
        char[] password = "MasterKey".toCharArray();
        byte[] input = "TestString".getBytes(UTF_8);

        EncryptionKey ek = encryptionKeyFromMasterKey(UtilCryto.deriveMasterKey(password, email, preloginResult));

        String encryptedString = UtilCryto.encryptByteArray(ek, input);

        assertNotNull(encryptedString);
        assertTrue(encryptedString.matches("2\\.[^|]+\\|[^|]+\\|[^|]+"));

        byte[] decrypted = UtilCryto.decryptByteArray(ek, encryptedString);

        assertArrayEquals(input, decrypted);
    }

}
