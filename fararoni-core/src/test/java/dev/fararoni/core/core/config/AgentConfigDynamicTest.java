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
package dev.fararoni.core.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
class AgentConfigDynamicTest {
    private AgentConfig config;

    @BeforeEach
    void setUp() {
        config = AgentConfig.defaults();
    }

    @Nested
    @DisplayName("detectHardwareTier()")
    class DetectHardwareTierTests {
        @Test
        @DisplayName("Detecta tier para modelo Rabbit (1.5B)")
        void testDetectRabbitTier() {
            HardwareTier tier = config.detectHardwareTier("qwen2.5-coder:1.5b");

            assertEquals(HardwareTier.LOW_RESOURCE, tier);
        }

        @Test
        @DisplayName("Detecta tier para modelo Turtle (32B)")
        void testDetectTurtleTier() {
            HardwareTier tier = config.detectHardwareTier("qwen2.5-coder:32b");

            assertEquals(HardwareTier.HIGH_PERFORMANCE, tier);
        }

        @Test
        @DisplayName("Detecta tier para Qwen3-coder:30b (MoE)")
        void testDetectQwen3Tier() {
            HardwareTier tier = config.detectHardwareTier("qwen3-coder:30b");

            assertEquals(HardwareTier.HIGH_PERFORMANCE, tier);
        }

        @Test
        @DisplayName("Detecta tier para modelo medio (7B)")
        void testDetectMediumTier() {
            HardwareTier tier = config.detectHardwareTier("qwen2.5-coder:7b");

            assertEquals(HardwareTier.MEDIUM, tier);
        }

        @Test
        @DisplayName("Modelo de nube detecta HIGH_PERFORMANCE")
        void testCloudModelTier() {
            assertEquals(HardwareTier.HIGH_PERFORMANCE,
                config.detectHardwareTier("gpt-4o"));
        }
    }

    @Nested
    @DisplayName("getMaxInputChars()")
    class MaxInputCharsTests {
        @Test
        @DisplayName("Modelo pequeño obtiene 12K chars")
        void testSmallModelChars() {
            int maxChars = config.getMaxInputChars("qwen2.5-coder:1.5b");

            assertEquals(12_000, maxChars);
        }

        @Test
        @DisplayName("Modelo grande obtiene 64K chars")
        void testLargeModelChars() {
            int maxChars = config.getMaxInputChars("qwen2.5-coder:32b");

            assertEquals(64_000, maxChars);
        }

        @Test
        @DisplayName("Modelo medio obtiene 24K chars")
        void testMediumModelChars() {
            int maxChars = config.getMaxInputChars("llama3.1:8b");

            assertEquals(24_000, maxChars);
        }

        @Test
        @DisplayName("rabbitMaxChars sigue siendo accesible (compatibilidad)")
        void testLegacyRabbitMaxChars() {
            assertEquals(12_000, config.rabbitMaxChars());
        }
    }

    @Nested
    @DisplayName("getMaxOutputTokens()")
    class MaxOutputTokensTests {
        @Test
        @DisplayName("Modelo pequeño obtiene 1K tokens")
        void testSmallModelTokens() {
            int maxTokens = config.getMaxOutputTokens("qwen2.5-coder:1.5b");

            assertEquals(1_024, maxTokens);
        }

        @Test
        @DisplayName("Modelo grande obtiene 4K tokens")
        void testLargeModelTokens() {
            int maxTokens = config.getMaxOutputTokens("qwen2.5-coder:32b");

            assertEquals(4_096, maxTokens);
        }

        @Test
        @DisplayName("Modelo medio obtiene 2K tokens")
        void testMediumModelTokens() {
            int maxTokens = config.getMaxOutputTokens("qwen2.5-coder:7b");

            assertEquals(2_048, maxTokens);
        }
    }

    @Nested
    @DisplayName("getPromptTone()")
    class PromptToneTests {
        @Test
        @DisplayName("Modelo pequeño usa tono DIRECTIVO")
        void testSmallModelTone() {
            String tone = config.getPromptTone("qwen2.5-coder:1.5b");

            assertEquals("DIRECTIVO", tone);
        }

        @Test
        @DisplayName("Modelo grande usa tono ARQUITECTONICO")
        void testLargeModelTone() {
            String tone = config.getPromptTone("qwen2.5-coder:32b");

            assertEquals("ARQUITECTONICO", tone);
        }

        @Test
        @DisplayName("Modelo medio usa tono CONTEXTUAL")
        void testMediumModelTone() {
            String tone = config.getPromptTone("qwen2.5-coder:7b");

            assertEquals("CONTEXTUAL", tone);
        }
    }

    @Nested
    @DisplayName("fitsInModel()")
    class FitsInModelTests {
        @Test
        @DisplayName("Contexto pequeño cabe en modelo pequeño")
        void testSmallContextFitsSmallModel() {
            assertTrue(config.fitsInModel(10_000, "qwen2.5-coder:1.5b"));
        }

        @Test
        @DisplayName("Contexto grande NO cabe en modelo pequeño")
        void testLargeContextDoesNotFitSmallModel() {
            assertFalse(config.fitsInModel(50_000, "qwen2.5-coder:1.5b"));
        }

        @Test
        @DisplayName("Contexto grande cabe en modelo grande")
        void testLargeContextFitsLargeModel() {
            assertTrue(config.fitsInModel(50_000, "qwen2.5-coder:32b"));
        }

        @Test
        @DisplayName("Contexto exacto al limite cabe")
        void testExactLimitFits() {
            assertTrue(config.fitsInModel(12_000, "qwen2.5-coder:1.5b"));
        }

        @Test
        @DisplayName("Contexto un char sobre limite NO cabe")
        void testOneOverLimitDoesNotFit() {
            assertFalse(config.fitsInModel(12_001, "qwen2.5-coder:1.5b"));
        }
    }

    @Nested
    @DisplayName("getRemainingBudget()")
    class RemainingBudgetTests {
        @Test
        @DisplayName("Budget positivo cuando hay espacio")
        void testPositiveBudget() {
            int remaining = config.getRemainingBudget(5_000, "qwen2.5-coder:1.5b");

            assertEquals(7_000, remaining);
        }

        @Test
        @DisplayName("Budget cero cuando esta al limite")
        void testZeroBudget() {
            int remaining = config.getRemainingBudget(12_000, "qwen2.5-coder:1.5b");

            assertEquals(0, remaining);
        }

        @Test
        @DisplayName("Budget negativo cuando excede")
        void testNegativeBudget() {
            int remaining = config.getRemainingBudget(15_000, "qwen2.5-coder:1.5b");

            assertEquals(-3_000, remaining);
        }

        @Test
        @DisplayName("Budget grande con modelo grande")
        void testLargeBudgetWithLargeModel() {
            int remaining = config.getRemainingBudget(10_000, "qwen2.5-coder:32b");

            assertEquals(54_000, remaining);
        }
    }

    @Nested
    @DisplayName("Escenarios Reales")
    class RealWorldScenariosTests {
        @Test
        @DisplayName("Escenario: Rabbit procesa archivo pequeño")
        void testRabbitSmallFile() {
            String modelName = "qwen2.5-coder:1.5b";
            int fileSize = 8_000;

            assertTrue(config.fitsInModel(fileSize, modelName));
            assertEquals(4_000, config.getRemainingBudget(fileSize, modelName));
            assertEquals("DIRECTIVO", config.getPromptTone(modelName));
        }

        @Test
        @DisplayName("Escenario: Turtle procesa archivo grande")
        void testTurtleLargeFile() {
            String modelName = "qwen2.5-coder:32b";
            int fileSize = 45_000;

            assertTrue(config.fitsInModel(fileSize, modelName));
            assertEquals(19_000, config.getRemainingBudget(fileSize, modelName));
            assertEquals("ARQUITECTONICO", config.getPromptTone(modelName));
        }

        @Test
        @DisplayName("Escenario: Archivo muy grande requiere Turtle")
        void testLargeFileRequiresTurtle() {
            int largeFileSize = 30_000;

            assertFalse(config.fitsInModel(largeFileSize, "qwen2.5-coder:1.5b"));

            assertTrue(config.fitsInModel(largeFileSize, "qwen2.5-coder:32b"));
        }

        @Test
        @DisplayName("Escenario: Modelo de nube tiene capacidad alta")
        void testCloudModelCapacity() {
            String modelName = "gpt-4o";

            assertEquals(64_000, config.getMaxInputChars(modelName));
            assertEquals(4_096, config.getMaxOutputTokens(modelName));
            assertEquals("ARQUITECTONICO", config.getPromptTone(modelName));
        }
    }
}
