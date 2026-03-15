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
package dev.fararoni.bus.agent.api.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para LlmProvider enum (Track A - Fase A4).
 *
 * Verifica:
 * - Parsing de strings a proveedores
 * - Auto-detección por URL
 * - Propiedades de cada proveedor
 */
@DisplayName("LlmProvider - Selección de Proveedor LLM")
class LlmProviderTest {

    @Nested
    @DisplayName("fromString() - Parsing de identificador")
    class FromStringTests {

        @ParameterizedTest(name = "fromString(\"{0}\") debe retornar {1}")
        @CsvSource({
            "openai, OPENAI",
            "OPENAI, OPENAI",
            "OpenAi, OPENAI",
            "ollama, OLLAMA",
            "OLLAMA, OLLAMA",
            "Ollama, OLLAMA",
            "anthropic, ANTHROPIC",
            "ANTHROPIC, ANTHROPIC",
            "local, LOCAL",
            "LOCAL, LOCAL"
        })
        void fromString_withValidId_returnsProvider(String input, LlmProvider expected) {
            Optional<LlmProvider> result = LlmProvider.fromString(input);

            assertTrue(result.isPresent(), "Debería encontrar proveedor para: " + input);
            assertEquals(expected, result.get());
        }

        @ParameterizedTest(name = "fromString(\"{0}\") debe retornar empty")
        @ValueSource(strings = {"unknown", "gpt", "claude", "gemini", "invalid"})
        void fromString_withInvalidId_returnsEmpty(String input) {
            Optional<LlmProvider> result = LlmProvider.fromString(input);

            assertTrue(result.isEmpty(), "No debería encontrar proveedor para: " + input);
        }

        @ParameterizedTest(name = "fromString(\"{0}\") debe retornar empty")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        void fromString_withNullOrBlank_returnsEmpty(String input) {
            Optional<LlmProvider> result = LlmProvider.fromString(input);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("fromString() maneja espacios alrededor del ID")
        void fromString_withWhitespace_trimsAndMatches() {
            Optional<LlmProvider> result = LlmProvider.fromString("  openai  ");

            assertTrue(result.isPresent());
            assertEquals(LlmProvider.OPENAI, result.get());
        }
    }

    @Nested
    @DisplayName("detectFromUrl() - Auto-detección por URL")
    class DetectFromUrlTests {

        @ParameterizedTest(name = "URL \"{0}\" debe detectar {1}")
        @CsvSource({
            "http://localhost:11434, OLLAMA",
            "http://localhost:11434/api/generate, OLLAMA",
            "http://ollama.local:8080, OLLAMA",
            "http://my-ollama-server:11434, OLLAMA",
            "https://api.openai.com/v1, OPENAI",
            "https://api.openai.com/v1/chat/completions, OPENAI",
            "https://my-resource.openai.azure.com/v1, OPENAI",
            "https://api.anthropic.com/v1, ANTHROPIC",
            "https://api.anthropic.com/v1/messages, ANTHROPIC",
            "http://localhost:8000/v1, OPENAI",
            "http://localhost:1234/v1, OPENAI",
            "http://192.168.1.100:8000, OPENAI"
        })
        void detectFromUrl_withKnownPatterns_detectsCorrectly(String url, LlmProvider expected) {
            LlmProvider result = LlmProvider.detectFromUrl(url);

            assertEquals(expected, result, "URL: " + url);
        }

        @Test
        @DisplayName("detectFromUrl() con null retorna OPENAI como default")
        void detectFromUrl_withNull_returnsOpenAi() {
            assertEquals(LlmProvider.OPENAI, LlmProvider.detectFromUrl(null));
        }

        @Test
        @DisplayName("detectFromUrl() con string vacío retorna OPENAI como default")
        void detectFromUrl_withEmpty_returnsOpenAi() {
            assertEquals(LlmProvider.OPENAI, LlmProvider.detectFromUrl(""));
            assertEquals(LlmProvider.OPENAI, LlmProvider.detectFromUrl("   "));
        }

        @Test
        @DisplayName("detectFromUrl() es case-insensitive")
        void detectFromUrl_isCaseInsensitive() {
            assertEquals(LlmProvider.OLLAMA, LlmProvider.detectFromUrl("http://LOCALHOST:11434"));
            assertEquals(LlmProvider.OLLAMA, LlmProvider.detectFromUrl("http://OLLAMA.local"));
            assertEquals(LlmProvider.OPENAI, LlmProvider.detectFromUrl("https://API.OPENAI.COM/v1"));
        }
    }

    @Nested
    @DisplayName("Propiedades del proveedor")
    class ProviderPropertiesTests {

        @Test
        @DisplayName("OPENAI tiene propiedades correctas")
        void openai_hasCorrectProperties() {
            LlmProvider provider = LlmProvider.OPENAI;

            assertEquals("openai", provider.getId());
            assertNotNull(provider.getDescription());
            assertTrue(provider.usesSseStreaming());
            assertFalse(provider.isLocal());
        }

        @Test
        @DisplayName("OLLAMA tiene propiedades correctas")
        void ollama_hasCorrectProperties() {
            LlmProvider provider = LlmProvider.OLLAMA;

            assertEquals("ollama", provider.getId());
            assertNotNull(provider.getDescription());
            assertFalse(provider.usesSseStreaming());
            assertFalse(provider.isLocal());
        }

        @Test
        @DisplayName("ANTHROPIC tiene propiedades correctas")
        void anthropic_hasCorrectProperties() {
            LlmProvider provider = LlmProvider.ANTHROPIC;

            assertEquals("anthropic", provider.getId());
            assertNotNull(provider.getDescription());
            assertTrue(provider.usesSseStreaming());
            assertFalse(provider.isLocal());
        }

        @Test
        @DisplayName("LOCAL tiene propiedades correctas")
        void local_hasCorrectProperties() {
            LlmProvider provider = LlmProvider.LOCAL;

            assertEquals("local", provider.getId());
            assertNotNull(provider.getDescription());
            assertFalse(provider.usesSseStreaming());
            assertTrue(provider.isLocal());
        }

        @Test
        @DisplayName("toString() retorna el ID")
        void toString_returnsId() {
            assertEquals("openai", LlmProvider.OPENAI.toString());
            assertEquals("ollama", LlmProvider.OLLAMA.toString());
            assertEquals("anthropic", LlmProvider.ANTHROPIC.toString());
            assertEquals("local", LlmProvider.LOCAL.toString());
        }
    }

    @Nested
    @DisplayName("getDefault()")
    class GetDefaultTests {

        @Test
        @DisplayName("getDefault() retorna OPENAI")
        void getDefault_returnsOpenAi() {
            assertEquals(LlmProvider.OPENAI, LlmProvider.getDefault());
        }
    }

    @Nested
    @DisplayName("Enum completitud")
    class EnumCompletenessTests {

        @Test
        @DisplayName("Enum tiene exactamente 4 valores")
        void enum_hasFourValues() {
            assertEquals(4, LlmProvider.values().length);
        }

        @Test
        @DisplayName("Todos los proveedores tienen ID único")
        void allProviders_haveUniqueIds() {
            var ids = java.util.Arrays.stream(LlmProvider.values())
                .map(LlmProvider::getId)
                .distinct()
                .count();

            assertEquals(LlmProvider.values().length, ids,
                "Todos los proveedores deberían tener IDs únicos");
        }

        @Test
        @DisplayName("Todos los proveedores tienen descripción no vacía")
        void allProviders_haveNonEmptyDescription() {
            for (LlmProvider provider : LlmProvider.values()) {
                assertNotNull(provider.getDescription(),
                    provider.name() + " debería tener descripción");
                assertFalse(provider.getDescription().isBlank(),
                    provider.name() + " descripción no debería estar vacía");
            }
        }
    }
}
