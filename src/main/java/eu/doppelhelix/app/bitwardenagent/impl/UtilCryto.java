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
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.util.DigestFactory;
import org.bouncycastle.util.encoders.Hex;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

public class UtilCryto {

    private static byte[] encodeUTF8(String input) {
        if (input == null) {
            return null;
        }
        return input.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] encodeUTF8(char[] input) {
        if (input == null) {
            return null;
        }
        ByteBuffer encodedBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(input));
        byte[] result = new byte[encodedBuffer.limit()];
        encodedBuffer.get(result);
        return result;
    }

    public static PrivateKey decryptPrivateKey(EncryptionKey encryptionKey, String payload) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException, InvalidKeySpecException {
        byte[] decryptedPayload = decryptByteArray(encryptionKey, payload);
        KeyFactory rsaFactory = KeyFactory.getInstance("RSA");
        PrivateKey rsaKey = rsaFactory.generatePrivate(new PKCS8EncodedKeySpec(decryptedPayload));
        return rsaKey;
    }

    public static EncryptionKey decryptKey(EncryptionKey encryptionKey, String payload) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException {
        byte[] decryptedPayload = decryptByteArray(encryptionKey, payload);
        return new EncryptionKey(
                Arrays.copyOfRange(decryptedPayload, 0, 32),
                Arrays.copyOfRange(decryptedPayload, 32, 64)
        );
    }

    public static EncryptionKey decryptKey(PrivateKey encryptionKey, String payload) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException {
        byte[] decryptedPayload = decryptByteArray(encryptionKey, payload);
        return new EncryptionKey(
                Arrays.copyOfRange(decryptedPayload, 0, 32),
                Arrays.copyOfRange(decryptedPayload, 32, 64)
        );
    }

    public static String decryptString(EncryptionKey encryptionKey, String payload) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException {
        byte[] data = decryptByteArray(encryptionKey, payload);
        if (data == null) {
            return null;
        }
        return new String(data, UTF_8);
    }

    public static byte[] decryptByteArray(EncryptionKey encryptionKey, String payload) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException {
        if (payload == null) {
            return null;
        }
        String[] encryptedParts = payload.split("\\.", 2);
        String encryptionScheme = encryptedParts[0];

        if (!"2".equals(encryptionScheme)) {
            throw new IllegalStateException("Unsupported Encryption scheme: " + encryptionScheme);
        }

        String[] payloadParts = encryptedParts[1].split("\\|");
        byte[] iv = Base64.getDecoder().decode(payloadParts[0]);
        byte[] data = Base64.getDecoder().decode(payloadParts[1]);
        byte[] mac = Base64.getDecoder().decode(payloadParts[2]);
        checkMac(encryptionKey.mac(), iv, data, mac);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey.enc(), "AES"), new IvParameterSpec(iv));
        byte[] userkeyDecrypted = cipher.doFinal(data);
        return userkeyDecrypted;
    }

    public static String encryptString(EncryptionKey encryptionKey, String payload) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException {
        if (payload == null) {
            return null;
        }
        return encryptByteArray(encryptionKey, payload.getBytes(UTF_8));
    }

    public static String encryptByteArray(EncryptionKey encryptionKey, byte[] payload) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException {
        if (payload == null) {
            return null;
        }

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] iv = new byte[cipher.getBlockSize()];
        SecureRandom.getInstanceStrong().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey.enc(), "AES"), new IvParameterSpec(iv));
        byte[] data = cipher.doFinal(payload);
        byte[] mac = createMac(encryptionKey.mac(), iv, data);

        return "2."
                + Base64.getEncoder().encodeToString(iv)
                + "|"
                + Base64.getEncoder().encodeToString(data)
                + "|"
                + Base64.getEncoder().encodeToString(mac);
    }

    public static byte[] decryptByteArray(PrivateKey encryptionKey, String payload) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalStateException {
        String[] encryptedParts = payload.split("\\.", 2);
        String encryptionScheme = encryptedParts[0];

        if (!"4".equals(encryptionScheme)) {
            throw new IllegalStateException("Unsupported Encryption scheme: " + encryptionScheme);
        }

        byte[] encryptedPayload = Base64.getDecoder().decode(encryptedParts[1]);

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec oaepParams = new OAEPParameterSpec("SHA-1", "MGF1", new MGF1ParameterSpec("SHA-1"), PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, oaepParams);
        byte[] payloadDecrypted = cipher.doFinal(encryptedPayload);
        return payloadDecrypted;
    }

    private static void checkMac(byte[] macKey, byte[] iv, byte[] data, byte[] targetMac) throws IllegalStateException, InvalidKeyException, NoSuchAlgorithmException {
        byte[] refMac = createMac(macKey, iv, data);

        if (!Arrays.equals(refMac, targetMac)) {
            System.out.println("Ref:        " + Hex.toHexString(refMac));
            System.out.println("Calculated: " + Hex.toHexString(targetMac));
            throw new IllegalStateException("Mac did not match!");
        }
    }

    private static byte[] createMac(byte[] macKey, byte[] iv, byte[] data) throws IllegalStateException, InvalidKeyException, NoSuchAlgorithmException {
        Mac hmac256 = Mac.getInstance("HmacSHA256");
        hmac256.init(new SecretKeySpec(macKey, "HmacSHA256"));
        hmac256.update(iv);
        byte[] refMac = hmac256.doFinal(data);
        return refMac;
    }

    public static byte[] deriveMasterKey(char[] password, String email, PreloginResult preloginResult) {
        // see: https://github.com/bitwarden/sdk-internal/blob/f75f62ed0d17cb99bfc837b230ea61943beaa9bb/crates/bitwarden-crypto/src/keys/kdf.rs#L38-L85
        return switch (preloginResult.kdf()) {
            case PBKDF2:
                PKCS5S2ParametersGenerator gen1 = new PKCS5S2ParametersGenerator(DigestFactory.createSHA256());
                gen1.init(encodeUTF8(password), encodeUTF8(email), preloginResult.kdfIterations());
                yield ((KeyParameter) gen1.generateDerivedParameters(256)).getKey();
            case Argon2Id:
                byte[] saltInput = encodeUTF8(email);
                Digest sha256 = DigestFactory.createSHA256();
                sha256.update(saltInput, 0, saltInput.length);
                byte[] salt = new byte[32];
                sha256.doFinal(salt, 0);
                Argon2BytesGenerator argon = new Argon2BytesGenerator();
                argon.init(new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withIterations(preloginResult.kdfIterations())
                        .withMemoryAsKB(preloginResult.kdfMemory() * 1024)
                        .withParallelism(preloginResult.kdfParallelism())
                        .withSalt(salt)
                        .withVersion(0x13)
                        .build()
                );
                byte[] result = new byte[32];
                argon.generateBytes(password, result);
                yield result;
        };
    }

    public static String deriveMasterKeyHash(byte[] masterKey, char[] password) {
        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(DigestFactory.createSHA256());
        gen.init(masterKey, encodeUTF8(password), 1);
        String masterPasswordHash = Base64.getEncoder().encodeToString(((KeyParameter) gen.generateDerivedParameters(256)).getKey());
        return masterPasswordHash;
    }

    public static EncryptionKey encryptionKeyFromMasterKey(byte[] masterKey) {
        return new EncryptionKey(deriveHkdfSha256(masterKey, "enc"), deriveHkdfSha256(masterKey, "mac"));
    }

    public static byte[] deriveHkdfSha256(byte[] sk, String info) throws IllegalArgumentException, DataLengthException {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(DigestFactory.createSHA256());
        hkdf.init(HKDFParameters.skipExtractParameters(sk, encodeUTF8(info)));
        byte[] encKey = new byte[32];
        hkdf.generateBytes(encKey, 0, encKey.length);
        return encKey;
    }

    public static String generateRandomString(int length) throws NoSuchAlgorithmException {
        StringBuilder result = new StringBuilder(length);
        String candidates = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom
                .getInstanceStrong()
                .ints(0, candidates.length())
                .takeWhile(i -> result.length() < length)
                .forEach(i -> result.append(candidates.charAt(i)));
        return result.toString();
    }

    public static String createCodeChallenge(String inputString) {
        byte[] input = inputString.getBytes(US_ASCII);
        Digest sha256 = DigestFactory.createSHA256();
        sha256.update(input, 0, input.length);
        byte[] hash = new byte[sha256.getDigestSize()];
        sha256.doFinal(hash, 0);
        String base64String = Base64.getUrlEncoder().encodeToString(hash);
        // String trailing padding
        return base64String.replaceFirst("=+$", "");
    }
}
