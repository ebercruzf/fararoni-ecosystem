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
@DisplayName("LogMaskingFilter Tests")
class LogMaskingFilterTest {
    @Nested
    @DisplayName("API Key Masking")
    class ApiKeyMaskingTests {
        @Test
        @DisplayName("debe enmascarar API keys de OpenAI (sk-*)")
        void mask_OpenAiApiKey_ShouldMask() {
            String message = "Using API key: sk-proj-abc123xyz789-test-key-long-enough";

            String masked = LogMaskingFilter.mask(message);

            assertFalse(masked.contains("sk-proj-abc123xyz789"),
                    "API key debe estar enmascarada");
            assertTrue(masked.contains("***MASKED***"));
        }

        @Test
        @DisplayName("debe enmascarar API keys de Anthropic (sk-ant-*)")
        void mask_AnthropicApiKey_ShouldMask() {
            String message = "Anthropic key: sk-ant-api03-xyz123abc456def789";

            String masked = LogMaskingFilter.mask(message);

            assertFalse(masked.contains("sk-ant-api03-xyz123abc456def789"));
            assertTrue(masked.contains("***MASKED***"));
        }

        @Test
        @DisplayName("debe enmascarar múltiples API keys en el mismo mensaje")
        void mask_MultipleApiKeys_ShouldMaskAll() {
            String message = "Keys: sk-openai-key-123456789012345678901234 and sk-ant-another-key-12345678901234";

            String masked = LogMaskingFilter.mask(message);

            assertFalse(masked.contains("sk-openai-key"));
            assertFalse(masked.contains("sk-ant-another"));
        }
    }

    @Nested
    @DisplayName("Bearer Token Masking")
    class BearerTokenMaskingTests {
        @Test
        @DisplayName("debe enmascarar Bearer tokens")
        void mask_BearerToken_ShouldMask() {
            String message = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test";

            String masked = LogMaskingFilter.mask(message);

            assertFalse(masked.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
            assertTrue(masked.contains("***MASKED***"));
        }

        @Test
        @DisplayName("debe enmascarar Bearer con mayúsculas/minúsculas")
        void mask_BearerCaseInsensitive_ShouldMask() {
            String message1 = "BEARER my-secret-token-value";
            String message2 = "bearer my-secret-token-value";

            assertTrue(LogMaskingFilter.mask(message1).contains("***MASKED***"));
            assertTrue(LogMaskingFilter.mask(message2).contains("***MASKED***"));
        }
    }

    @Nested
    @DisplayName("JWT Token Masking")
    class JwtMaskingTests {
        @Test
        @DisplayName("debe enmascarar JWT tokens (eyJ...)")
        void mask_JwtToken_ShouldMask() {
            String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
            String message = "Token received: " + jwt;

            String masked = LogMaskingFilter.mask(message);

            assertFalse(masked.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
            assertTrue(masked.contains("***MASKED***"));
        }
    }

    @Nested
    @DisplayName("URL Credential Masking")
    class UrlCredentialMaskingTests {
        @Test
        @DisplayName("debe enmascarar credenciales en URLs")
        void mask_UrlCredentials_ShouldMask() {
            String message = "Connecting to: https://user:password123@api.example.com/endpoint";

            String masked = LogMaskingFilter.mask(message);

            assertFalse(masked.contains("password123"),
                    "Password no debe estar visible");
            assertTrue(masked.contains("://***:***@"),
                    "Debe usar máscara especial para URLs");
        }

        @Test
        @DisplayName("debe enmascarar credenciales en URLs con caracteres especiales")
        void mask_UrlCredentials_WithSpecialChars_ShouldMask() {
            String message = "URL: postgresql://admin:p@ssw0rd!@localhost:5432/db";

            String masked = LogMaskingFilter.mask(message);

            assertFalse(masked.contains("p@ssw0rd!"));
        }
    }

    @Nested
    @DisplayName("Generic Secret Masking")
    class GenericSecretMaskingTests {
        @Test
        @DisplayName("debe enmascarar api_key=xxx")
        void mask_ApiKeyParam_ShouldMask() {
            String message = "Request with api_key=abc123secret456";

            String masked = LogMaskingFilter.mask(message);

            assertFalse(masked.contains("abc123secret456"));
            assertTrue(masked.contains("***MASKED***"));
        }

        @Test
        @DisplayName("debe enmascarar secret=xxx")
        void mask_SecretParam_ShouldMask() {
            String message = "Config: secret=my-super-secret-value";

            String masked = LogMaskingFilter.mask(message);

            assertFalse(masked.contains("my-super-secret-value"));
        }

        @Test
        @DisplayName("debe enmascarar password=xxx")
        void mask_PasswordParam_ShouldMask() {
            String message = "Login with password=admin123";

            String masked = LogMaskingFilter.mask(message);

            assertFalse(masked.contains("admin123"));
        }

        @Test
        @DisplayName("debe enmascarar token=xxx")
        void mask_TokenParam_ShouldMask() {
            String message = "Auth token: token=refresh-token-value";

            String masked = LogMaskingFilter.mask(message);

            assertFalse(masked.contains("refresh-token-value"));
        }
    }

    @Nested
    @DisplayName("Encrypted Value Masking")
    class EncryptedValueMaskingTests {
        @Test
        @DisplayName("debe enmascarar valores ENC: de SecureConfigService")
        void mask_EncryptedValue_ShouldMask() {
            String message = "Loaded config: ENC:YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=";

            String masked = LogMaskingFilter.mask(message);

            assertFalse(masked.contains("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="));
        }
    }

    @Nested
    @DisplayName("Detection")
    class DetectionTests {
        @Test
        @DisplayName("containsSensitiveData debe detectar API keys")
        void containsSensitiveData_WithApiKey_ShouldReturnTrue() {
            assertTrue(LogMaskingFilter.containsSensitiveData(
                    "Key: sk-proj-abc123xyz789012345678901234567890"));
        }

        @Test
        @DisplayName("containsSensitiveData debe detectar Bearer tokens")
        void containsSensitiveData_WithBearer_ShouldReturnTrue() {
            assertTrue(LogMaskingFilter.containsSensitiveData(
                    "Bearer my-secret-token-value"));
        }

        @Test
        @DisplayName("containsSensitiveData con texto normal debe retornar false")
        void containsSensitiveData_WithNormalText_ShouldReturnFalse() {
            assertFalse(LogMaskingFilter.containsSensitiveData(
                    "Normal log message without secrets"));
        }

        @Test
        @DisplayName("containsSensitiveData con null debe retornar false")
        void containsSensitiveData_WithNull_ShouldReturnFalse() {
            assertFalse(LogMaskingFilter.containsSensitiveData(null));
        }

        @Test
        @DisplayName("containsSensitiveData con vacío debe retornar false")
        void containsSensitiveData_WithEmpty_ShouldReturnFalse() {
            assertFalse(LogMaskingFilter.containsSensitiveData(""));
        }
    }

    @Nested
    @DisplayName("API Key Partial Masking")
    class PartialMaskingTests {
        @Test
        @DisplayName("maskApiKey debe mostrar prefijo y sufijo")
        void maskApiKey_ShouldShowPrefixAndSuffix() {
            String apiKey = "sk-proj-abc123xyz789-very-long-key";

            String masked = LogMaskingFilter.maskApiKey(apiKey);

            assertTrue(masked.startsWith("sk-proj"),
                    "Debe mostrar los primeros 7 caracteres");
            assertTrue(masked.endsWith("-key"),
                    "Debe mostrar los últimos 4 caracteres");
            assertTrue(masked.contains("..."),
                    "Debe contener separador");
        }

        @Test
        @DisplayName("maskApiKey con key muy corta debe enmascarar completamente")
        void maskApiKey_WithShortKey_ShouldMaskCompletely() {
            String shortKey = "sk-short";

            String masked = LogMaskingFilter.maskApiKey(shortKey);

            assertEquals("***MASKED***", masked);
        }

        @Test
        @DisplayName("maskApiKey con null debe retornar MASKED")
        void maskApiKey_WithNull_ShouldReturnMasked() {
            assertEquals("***MASKED***", LogMaskingFilter.maskApiKey(null));
        }
    }

    @Nested
    @DisplayName("Bearer Token Partial Masking")
    class BearerPartialMaskingTests {
        @Test
        @DisplayName("maskBearerToken debe mostrar solo inicio")
        void maskBearerToken_ShouldShowOnlyStart() {
            String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";

            String masked = LogMaskingFilter.maskBearerToken(token);

            assertTrue(masked.startsWith("eyJhbGci"),
                    "Debe mostrar los primeros 8 caracteres");
            assertTrue(masked.contains("..."));
            assertTrue(masked.contains("***MASKED***"));
        }

        @Test
        @DisplayName("maskBearerToken con token corto debe enmascarar")
        void maskBearerToken_WithShortToken_ShouldMask() {
            assertEquals("***MASKED***", LogMaskingFilter.maskBearerToken("short"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {
        @Test
        @DisplayName("mask con null debe retornar null")
        void mask_WithNull_ShouldReturnNull() {
            assertNull(LogMaskingFilter.mask(null));
        }

        @Test
        @DisplayName("mask con vacío debe retornar vacío")
        void mask_WithEmpty_ShouldReturnEmpty() {
            assertEquals("", LogMaskingFilter.mask(""));
        }

        @Test
        @DisplayName("mask sin datos sensibles debe retornar igual")
        void mask_WithNoSensitiveData_ShouldReturnSame() {
            String message = "INFO: Application started successfully at port 8080";

            String masked = LogMaskingFilter.mask(message);

            assertEquals(message, masked);
        }

        @Test
        @DisplayName("mask debe preservar estructura del mensaje")
        void mask_ShouldPreserveMessageStructure() {
            String message = "[2026-01-07T10:00:00Z] API call with key sk-proj-abc123xyz789012345678901234567890 completed";

            String masked = LogMaskingFilter.mask(message);

            assertTrue(masked.contains("[2026-01-07T10:00:00Z]"));
            assertTrue(masked.contains("API call with key"));
            assertTrue(masked.contains("completed"));
        }
    }
}
