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

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("AesHelper Tests")
class AesHelperTest {
    private static final String TEST_PASSWORD = "TestPassword123!@#$%";
    private static final String TEST_PLAINTEXT = "sk-proj-abc123xyz789-secret-api-key";

    @Nested
    @DisplayName("Encryption/Decryption")
    class EncryptionDecryptionTests {
        @Test
        @DisplayName("encrypt debe generar texto diferente al original")
        void encrypt_ShouldGenerateDifferentText() {
            String encrypted = AesHelper.encrypt(TEST_PLAINTEXT, TEST_PASSWORD);

            assertNotNull(encrypted);
            assertNotEquals(TEST_PLAINTEXT, encrypted);
            assertTrue(encrypted.length() > TEST_PLAINTEXT.length(),
                    "Texto encriptado debe ser más largo (incluye salt+iv+tag)");
        }

        @Test
        @DisplayName("decrypt debe recuperar texto original")
        void decrypt_ShouldRecoverOriginalText() {
            String encrypted = AesHelper.encrypt(TEST_PLAINTEXT, TEST_PASSWORD);
            String decrypted = AesHelper.decrypt(encrypted, TEST_PASSWORD);

            assertEquals(TEST_PLAINTEXT, decrypted);
        }

        @Test
        @DisplayName("encrypt debe generar resultado diferente cada vez (IV aleatorio)")
        void encrypt_ShouldGenerateDifferentResultEachTime() {
            String encrypted1 = AesHelper.encrypt(TEST_PLAINTEXT, TEST_PASSWORD);
            String encrypted2 = AesHelper.encrypt(TEST_PLAINTEXT, TEST_PASSWORD);

            assertNotEquals(encrypted1, encrypted2,
                    "Cada encriptación debe usar IV diferente");

            assertEquals(AesHelper.decrypt(encrypted1, TEST_PASSWORD),
                    AesHelper.decrypt(encrypted2, TEST_PASSWORD));
        }

        @Test
        @DisplayName("decrypt con contraseña incorrecta debe fallar")
        void decrypt_WithWrongPassword_ShouldFail() {
            String encrypted = AesHelper.encrypt(TEST_PLAINTEXT, TEST_PASSWORD);

            assertThrows(SecurityException.class, () ->
                    AesHelper.decrypt(encrypted, "WrongPassword"));
        }

        @Test
        @DisplayName("encrypt/decrypt con texto largo debe funcionar")
        void encryptDecrypt_WithLongText_ShouldWork() {
            String longText = "x".repeat(10000);

            String encrypted = AesHelper.encrypt(longText, TEST_PASSWORD);
            String decrypted = AesHelper.decrypt(encrypted, TEST_PASSWORD);

            assertEquals(longText, decrypted);
        }

        @Test
        @DisplayName("encrypt/decrypt con caracteres Unicode debe funcionar")
        void encryptDecrypt_WithUnicode_ShouldWork() {
            String unicodeText = "API Key: 日本語テスト 🔐 émojis ñ";

            String encrypted = AesHelper.encrypt(unicodeText, TEST_PASSWORD);
            String decrypted = AesHelper.decrypt(encrypted, TEST_PASSWORD);

            assertEquals(unicodeText, decrypted);
        }
    }

    @Nested
    @DisplayName("Input Validation")
    class ValidationTests {
        @Test
        @DisplayName("encrypt con plaintext null debe lanzar excepción")
        void encrypt_WithNullPlaintext_ShouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                    AesHelper.encrypt(null, TEST_PASSWORD));
        }

        @Test
        @DisplayName("encrypt con plaintext vacío debe lanzar excepción")
        void encrypt_WithEmptyPlaintext_ShouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                    AesHelper.encrypt("", TEST_PASSWORD));
        }

        @Test
        @DisplayName("encrypt con password null debe lanzar excepción")
        void encrypt_WithNullPassword_ShouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                    AesHelper.encrypt(TEST_PLAINTEXT, null));
        }

        @Test
        @DisplayName("encrypt con password vacío debe lanzar excepción")
        void encrypt_WithEmptyPassword_ShouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                    AesHelper.encrypt(TEST_PLAINTEXT, ""));
        }

        @Test
        @DisplayName("decrypt con texto inválido debe lanzar excepción")
        void decrypt_WithInvalidText_ShouldThrow() {
            assertThrows(SecurityException.class, () ->
                    AesHelper.decrypt("not-valid-base64!!!", TEST_PASSWORD));
        }

        @Test
        @DisplayName("decrypt con texto muy corto debe lanzar excepción")
        void decrypt_WithTooShortText_ShouldThrow() {
            assertThrows(SecurityException.class, () ->
                    AesHelper.decrypt("YWJj", TEST_PASSWORD));
        }
    }

    @Nested
    @DisplayName("isEncrypted Detection")
    class IsEncryptedTests {
        @Test
        @DisplayName("isEncrypted debe detectar texto encriptado")
        void isEncrypted_WithEncryptedText_ShouldReturnTrue() {
            String encrypted = AesHelper.encrypt(TEST_PLAINTEXT, TEST_PASSWORD);

            assertTrue(AesHelper.isEncrypted(encrypted));
        }

        @Test
        @DisplayName("isEncrypted debe rechazar texto plano")
        void isEncrypted_WithPlainText_ShouldReturnFalse() {
            assertFalse(AesHelper.isEncrypted(TEST_PLAINTEXT));
            assertFalse(AesHelper.isEncrypted("sk-proj-abc123"));
        }

        @Test
        @DisplayName("isEncrypted con null debe retornar false")
        void isEncrypted_WithNull_ShouldReturnFalse() {
            assertFalse(AesHelper.isEncrypted(null));
        }

        @Test
        @DisplayName("isEncrypted con texto vacío debe retornar false")
        void isEncrypted_WithEmpty_ShouldReturnFalse() {
            assertFalse(AesHelper.isEncrypted(""));
        }
    }

    @Nested
    @DisplayName("Password Generation")
    class PasswordGenerationTests {
        @Test
        @DisplayName("generateSecurePassword debe generar longitud correcta")
        void generateSecurePassword_ShouldHaveCorrectLength() {
            String password = AesHelper.generateSecurePassword(32);

            assertEquals(32, password.length());
        }

        @Test
        @DisplayName("generateSecurePassword debe generar valores únicos")
        void generateSecurePassword_ShouldBeUnique() {
            String password1 = AesHelper.generateSecurePassword(32);
            String password2 = AesHelper.generateSecurePassword(32);

            assertNotEquals(password1, password2);
        }

        @Test
        @DisplayName("generateSecurePassword con longitud < 16 debe lanzar excepción")
        void generateSecurePassword_WithShortLength_ShouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                    AesHelper.generateSecurePassword(8));
        }

        @Test
        @DisplayName("generateSecurePassword debe ser URL-safe")
        void generateSecurePassword_ShouldBeUrlSafe() {
            String password = AesHelper.generateSecurePassword(64);

            assertFalse(password.contains("+"));
            assertFalse(password.contains("/"));
        }
    }

    @Nested
    @DisplayName("Security Properties")
    class SecurityPropertiesTests {
        @Test
        @DisplayName("texto encriptado debe ser Base64 válido")
        void encryptedText_ShouldBeValidBase64() {
            String encrypted = AesHelper.encrypt(TEST_PLAINTEXT, TEST_PASSWORD);

            assertDoesNotThrow(() ->
                    java.util.Base64.getDecoder().decode(encrypted));
        }

        @Test
        @DisplayName("texto encriptado debe incluir salt, IV y tag")
        void encryptedText_ShouldIncludeSaltIvAndTag() {
            String encrypted = AesHelper.encrypt(TEST_PLAINTEXT, TEST_PASSWORD);
            byte[] decoded = java.util.Base64.getDecoder().decode(encrypted);

            assertTrue(decoded.length >= 45,
                    "Debe incluir salt(16) + iv(12) + tag(16) + datos");
        }
    }
}
