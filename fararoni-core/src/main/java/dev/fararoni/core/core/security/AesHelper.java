/*
 * Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fararoni.core.core.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class AesHelper {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";

    private static final int KEY_SIZE_BITS = 256;
    private static final int IV_SIZE_BYTES = 12;
    private static final int SALT_SIZE_BYTES = 16;
    private static final int TAG_SIZE_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 310000;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesHelper() {
    }

    public static String encrypt(String plaintext, String password) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("Plaintext cannot be null or empty");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        try {
            byte[] salt = generateRandomBytes(SALT_SIZE_BYTES);
            byte[] iv = generateRandomBytes(IV_SIZE_BYTES);

            SecretKey secretKey = deriveKey(password, salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_SIZE_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length);
            buffer.put(salt);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new SecurityException("Encryption failed: " + e.getMessage(), e);
        }
    }

    public static String decrypt(String encryptedBase64, String password) {
        if (encryptedBase64 == null || encryptedBase64.isEmpty()) {
            throw new IllegalArgumentException("Encrypted text cannot be null or empty");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        try {
            byte[] encryptedData = Base64.getDecoder().decode(encryptedBase64);

            int minSize = SALT_SIZE_BYTES + IV_SIZE_BYTES + TAG_SIZE_BITS / 8;
            if (encryptedData.length < minSize) {
                throw new SecurityException("Invalid encrypted data: too short");
            }

            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);

            byte[] salt = new byte[SALT_SIZE_BYTES];
            buffer.get(salt);

            byte[] iv = new byte[IV_SIZE_BYTES];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            SecretKey secretKey = deriveKey(password, salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_SIZE_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Invalid Base64 encoding", e);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new SecurityException("Decryption failed: invalid password or corrupted data", e);
        } catch (Exception e) {
            throw new SecurityException("Decryption failed: " + e.getMessage(), e);
        }
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                PBKDF2_ITERATIONS,
                KEY_SIZE_BITS
        );

        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();

        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }

    private static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(text);
            int minSize = SALT_SIZE_BYTES + IV_SIZE_BYTES + TAG_SIZE_BITS / 8;
            return decoded.length >= minSize;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String generateSecurePassword(int length) {
        if (length < 16) {
            throw new IllegalArgumentException("Password length must be at least 16 characters");
        }

        byte[] randomBytes = generateRandomBytes(length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes).substring(0, length);
    }
}
