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

import com.amdelamar.jotp.OTP;
import com.amdelamar.jotp.type.Type;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class TOTPUtil {
    private static final System.Logger LOG = System.getLogger(TOTPUtil.class.getName());

    public static String calculateTOTP(String totpUrl) throws TOTPCalculationFailedException {
        try {
            // TODO implement variants with algorithm=SHA256 and algorithm=SHA512
            Map<String, String> parameters = extractSecretFromOtpUrl(totpUrl);
            String hexTime = OTP.timeInHex(
                    System.currentTimeMillis(),
                    Integer.parseInt(parameters.getOrDefault("period", "30"))
            );
            return OTP.create(
                    parameters.getOrDefault("secret", ""),
                    hexTime,
                    Integer.parseInt(parameters.getOrDefault("digits", "6")),
                    Type.TOTP);
        } catch (IOException | InvalidKeyException | NoSuchAlgorithmException | RuntimeException ex) {
            throw new TOTPCalculationFailedException(ex);
        }
    }

    private static Map<String,String> extractSecretFromOtpUrl(String input) {
        if(input == null || input.isBlank()) {
            return null;
        }
        URI uri = URI.create(input);
        return Arrays.stream(uri.getRawQuery().split("&"))
                .filter(queryPart -> queryPart.contains("="))
                .map(queryPart -> {
                    String[] queryParts = queryPart.split("=", 2);
                    String key = URLDecoder.decode(queryParts[0], StandardCharsets.UTF_8).toLowerCase();
                    String value = URLDecoder.decode(queryParts[1], StandardCharsets.UTF_8);
                    return new HashMap.SimpleEntry<>(key, value);
                })
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
    }

    public static class TOTPCalculationFailedException extends RuntimeException {

        public TOTPCalculationFailedException(Throwable cause) {
            super(cause);
        }

    }
}
