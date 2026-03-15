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
package dev.fararoni.core.config;

import dev.fararoni.core.core.constants.AppDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@DisplayName("CliConfig - Configuration Tests")
class CliConfigTest {
    @Test
    @DisplayName("Builder debe crear configuración válida con valores por defecto")
    void builderShouldCreateValidConfig() {
        CliConfig config = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test-model")
            .build();

        assertNotNull(config);
        assertEquals("http://localhost:8000", config.serverUrl());
        assertEquals("test-model", config.modelName());
        assertEquals(CliConfig.TokenizerMode.REMOTE, config.tokenizerMode());
        assertEquals(AppDefaults.DEFAULT_CONTEXT_WINDOW, config.contextWindow());
        assertEquals(AppDefaults.DEFAULT_MAX_TOKENS, config.maxTokens());
    }

    @Test
    @DisplayName("Debe normalizar URL removiendo slash final")
    void shouldNormalizeUrlRemovingTrailingSlash() {
        CliConfig config = CliConfig.builder()
            .serverUrl("http://localhost:8000/")
            .modelName("test")
            .build();

        assertEquals("http://localhost:8000", config.serverUrl(),
            "URL debe tener slash final removido");
    }

    @Test
    @DisplayName("Debe validar maxTokens en rango permitido")
    void shouldValidateMaxTokensRange() {
        var builder = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test");

        assertThrows(IllegalArgumentException.class, () ->
            builder.maxTokens(0).build(),
            "maxTokens < 1 debe lanzar excepción");

        assertThrows(IllegalArgumentException.class, () ->
            builder.maxTokens(100001).build(),
            "maxTokens > 100,000 debe lanzar excepción");
    }

    @Test
    @DisplayName("Debe validar contextWindow en rango permitido")
    void shouldValidateContextWindowRange() {
        var builder = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test");

        assertThrows(IllegalArgumentException.class, () ->
            builder.contextWindow(511).build(),
            "contextWindow < 512 debe lanzar excepción");

        assertThrows(IllegalArgumentException.class, () ->
            builder.contextWindow(200001).build(),
            "contextWindow > 200,000 debe lanzar excepción");
    }

    @Test
    @DisplayName("Debe validar que maxTokens no sea mayor que contextWindow")
    void shouldValidateMaxTokensNotGreaterThanContextWindow() {
        assertThrows(IllegalArgumentException.class, () ->
            CliConfig.builder()
                .serverUrl("http://localhost:8000")
                .modelName("test")
                .maxTokens(10000)
                .contextWindow(8192)
                .build(),
            "maxTokens > contextWindow debe lanzar excepción");
    }

    @Test
    @DisplayName("Debe validar temperature en rango permitido")
    void shouldValidateTemperatureRange() {
        var builder = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test");

        assertThrows(IllegalArgumentException.class, () ->
            builder.temperature(-0.1).build());

        assertThrows(IllegalArgumentException.class, () ->
            builder.temperature(2.1).build());
    }

    @Test
    @DisplayName("Debe crear copias defensivas de customCommands")
    void shouldCreateDefensiveCopiesOfCustomCommands() {
        Map<String, String> mutableMap = new java.util.HashMap<>();
        mutableMap.put("key1", "value1");

        CliConfig config = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test")
            .customCommands(mutableMap)
            .build();

        mutableMap.put("key2", "value2");
        assertEquals(1, config.customCommands().size(),
            "Mapa de comandos debe ser inmutable/copia defensiva");
    }

    @Test
    @DisplayName("Debe usar system prompt por defecto cuando no se especifica")
    void shouldUseDefaultSystemPromptWhenNotSpecified() {
        CliConfig config = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test")
            .build();

        assertNotNull(config.systemPrompt());
        assertTrue(config.systemPrompt().contains("Java"),
            "System prompt por defecto debe mencionar Java");
    }

    @Test
    @DisplayName("Debe usar system prompt con Chain of Thought cuando está habilitado")
    void shouldUseChainOfThoughtSystemPromptWhenEnabled() {
        CliConfig config = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test")
            .enableChainOfThought(true)
            .build();

        assertNotNull(config.systemPrompt());
        assertTrue(config.systemPrompt().contains("<thinking>"),
            "System prompt con CoT debe incluir instrucciones de thinking");
    }

    @Test
    @DisplayName("Métodos de conveniencia deben funcionar correctamente")
    void convenienceMethodsShouldWork() {
        CliConfig config = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test")
            .tokenizerMode(CliConfig.TokenizerMode.REMOTE)
            .contextStrategy(CliConfig.ContextStrategy.COMPRESSION)
            .build();

        assertTrue(config.isRemoteTokenizer());
        assertFalse(config.isLocalTokenizer());
        assertTrue(config.shouldUseCompression());
        assertFalse(config.shouldUseChunking());
    }

    @Test
    @DisplayName("getEffectiveContextSize debe reservar espacio para respuesta")
    void getEffectiveContextSizeShouldReserveSpaceForResponse() {
        CliConfig config = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test")
            .contextWindow(10000)
            .maxTokens(2000)
            .build();

        int effectiveSize = config.getEffectiveContextSize();

        assertTrue(effectiveSize < config.contextWindow(),
            "Effective context debe ser menor que context window");
        assertTrue(effectiveSize >= 512,
            "Effective context debe tener al menos 512 tokens");
    }

    @Test
    @DisplayName("withMaxTokens debe crear nueva instancia con valor actualizado")
    void withMaxTokensShouldCreateNewInstanceWithUpdatedValue() {
        CliConfig original = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test")
            .maxTokens(1000)
            .build();

        CliConfig updated = original.withMaxTokens(1500);

        assertNotSame(original, updated, "Debe crear nueva instancia");
        assertEquals(1000, original.maxTokens(), "Original no debe cambiar");
        assertEquals(1500, updated.maxTokens(), "Nuevo debe tener valor actualizado");
        assertEquals(original.serverUrl(), updated.serverUrl(),
            "Otros campos deben permanecer igual");
    }

    @Test
    @DisplayName("getConfigSummary debe retornar resumen legible")
    void getConfigSummaryShouldReturnReadableSummary() {
        CliConfig config = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test-model")
            .build();

        String summary = config.getConfigSummary();

        assertNotNull(summary);
        assertTrue(summary.contains("http://localhost:8000"));
        assertTrue(summary.contains("test-model"));
        assertTrue(summary.contains("REMOTE"));
    }

    @Test
    @DisplayName("Debe validar maxHistoryMessages en rango permitido")
    void shouldValidateMaxHistoryMessagesRange() {
        var builder = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test");

        assertThrows(IllegalArgumentException.class, () ->
            builder.maxHistoryMessages(0).build());

        assertThrows(IllegalArgumentException.class, () ->
            builder.maxHistoryMessages(1001).build());
    }

    @Test
    @DisplayName("Debe validar timeouts en rangos permitidos")
    void shouldValidateTimeoutsRange() {
        var builder = CliConfig.builder()
            .serverUrl("http://localhost:8000")
            .modelName("test");

        assertThrows(IllegalArgumentException.class, () ->
            builder.connectTimeout(999).build());

        assertThrows(IllegalArgumentException.class, () ->
            builder.readTimeout(4999).build());

        assertThrows(IllegalArgumentException.class, () ->
            builder.maxRetries(-1).build());

        assertThrows(IllegalArgumentException.class, () ->
            builder.maxRetries(11).build());
    }
}
