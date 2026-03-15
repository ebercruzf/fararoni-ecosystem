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
package dev.fararoni.core.core.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
class LocalLlmConfigSafetyCapsTest {
    @Test
    @DisplayName("HARD_LIMIT_CONTEXT debe ser 4096")
    void hardLimitContext() {
        assertEquals(4096, LocalLlmConfig.HARD_LIMIT_CONTEXT,
            "El limite de contexto debe ser 4096 tokens (seguro para Intel)");
    }

    @Test
    @DisplayName("HARD_LIMIT_MAX_TOKENS debe ser 2048")
    void hardLimitMaxTokens() {
        assertEquals(2048, LocalLlmConfig.HARD_LIMIT_MAX_TOKENS,
            "El limite de tokens de respuesta debe ser 2048");
    }

    @Test
    @DisplayName("HARD_LIMIT_MAX_TOKENS debe ser menor que HARD_LIMIT_CONTEXT")
    void maxTokensMenorQueContext() {
        assertTrue(LocalLlmConfig.HARD_LIMIT_MAX_TOKENS < LocalLlmConfig.HARD_LIMIT_CONTEXT,
            "maxTokens debe ser menor que contextLength para dejar espacio al prompt");
    }

    @Test
    @DisplayName("HARD_LIMIT_TEMPERATURE debe ser 2.0")
    void hardLimitTemperature() {
        assertEquals(2.0, LocalLlmConfig.HARD_LIMIT_TEMPERATURE,
            "El limite de temperatura debe ser 2.0");
    }

    @Test
    @DisplayName("HARD_LIMIT_TEMPERATURE_MIN debe ser 0.0")
    void hardLimitTemperatureMin() {
        assertEquals(0.0, LocalLlmConfig.HARD_LIMIT_TEMPERATURE_MIN,
            "El minimo de temperatura debe ser 0.0");
    }

    @Test
    @DisplayName("defaults() debe respetar los hard limits")
    void defaultsRespetaLimites() {
        LocalLlmConfig config = LocalLlmConfig.defaults();

        assertTrue(config.contextLength() <= LocalLlmConfig.HARD_LIMIT_CONTEXT,
            "Context length por defecto no debe exceder hard limit");
        assertTrue(config.maxTokens() <= LocalLlmConfig.HARD_LIMIT_MAX_TOKENS,
            "Max tokens por defecto no debe exceder hard limit");
        assertTrue(config.temperature() >= LocalLlmConfig.HARD_LIMIT_TEMPERATURE_MIN,
            "Temperature por defecto no debe ser menor que hard limit min");
        assertTrue(config.temperature() <= LocalLlmConfig.HARD_LIMIT_TEMPERATURE,
            "Temperature por defecto no debe exceder hard limit");
    }

    @Test
    @DisplayName("DEFAULT_CONTEXT_LENGTH es menor o igual que HARD_LIMIT")
    void defaultContextDentroDelLimite() {
        assertTrue(LocalLlmConfig.DEFAULT_CONTEXT_LENGTH <= LocalLlmConfig.HARD_LIMIT_CONTEXT,
            "El default debe estar dentro del hard limit");
    }

    @Test
    @DisplayName("DEFAULT_MAX_TOKENS es menor o igual que HARD_LIMIT")
    void defaultMaxTokensDentroDelLimite() {
        assertTrue(LocalLlmConfig.DEFAULT_MAX_TOKENS <= LocalLlmConfig.HARD_LIMIT_MAX_TOKENS,
            "El default debe estar dentro del hard limit");
    }

    @Test
    @DisplayName("Constantes de safety estan documentadas")
    void constantesDocumentadas() {
        assertNotNull(LocalLlmConfig.class);
        assertEquals(4096, LocalLlmConfig.HARD_LIMIT_CONTEXT);
        assertEquals(2048, LocalLlmConfig.HARD_LIMIT_MAX_TOKENS);
        assertEquals(2.0, LocalLlmConfig.HARD_LIMIT_TEMPERATURE);
        assertEquals(0.0, LocalLlmConfig.HARD_LIMIT_TEMPERATURE_MIN);
    }
}
