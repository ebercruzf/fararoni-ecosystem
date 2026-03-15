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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
class HardwareTierTest {
    @Nested
    @DisplayName("Valores de Tier")
    class TierValuesTests {
        @Test
        @DisplayName("LOW_RESOURCE tiene limites correctos")
        void testLowResourceLimits() {
            HardwareTier tier = HardwareTier.LOW_RESOURCE;

            assertEquals(12_000, tier.getMaxInputChars());
            assertEquals(1_024, tier.getMaxOutputTokens());
            assertEquals("DIRECTIVO", tier.getPromptTone());
            assertEquals(3_000, tier.getMaxInputTokens());
        }

        @Test
        @DisplayName("MEDIUM tiene limites correctos")
        void testMediumLimits() {
            HardwareTier tier = HardwareTier.MEDIUM;

            assertEquals(24_000, tier.getMaxInputChars());
            assertEquals(2_048, tier.getMaxOutputTokens());
            assertEquals("CONTEXTUAL", tier.getPromptTone());
            assertEquals(6_000, tier.getMaxInputTokens());
        }

        @Test
        @DisplayName("HIGH_PERFORMANCE tiene limites correctos")
        void testHighPerformanceLimits() {
            HardwareTier tier = HardwareTier.HIGH_PERFORMANCE;

            assertEquals(64_000, tier.getMaxInputChars());
            assertEquals(4_096, tier.getMaxOutputTokens());
            assertEquals("ARQUITECTONICO", tier.getPromptTone());
            assertEquals(16_000, tier.getMaxInputTokens());
        }
    }

    @Nested
    @DisplayName("Deteccion desde Nombre de Modelo")
    class FromModelNameTests {
        @Test
        @DisplayName("Detecta 0.5B como LOW_RESOURCE")
        void testDetects05b() {
            assertEquals(HardwareTier.LOW_RESOURCE,
                HardwareTier.fromModelName("qwen2.5-coder:0.5b"));
        }

        @Test
        @DisplayName("Detecta 1.5B como LOW_RESOURCE")
        void testDetects15b() {
            assertEquals(HardwareTier.LOW_RESOURCE,
                HardwareTier.fromModelName("qwen2.5-coder:1.5b"));
        }

        @Test
        @DisplayName("Detecta 3B como LOW_RESOURCE")
        void testDetects3b() {
            assertEquals(HardwareTier.LOW_RESOURCE,
                HardwareTier.fromModelName("llama3.2:3b"));
        }

        @Test
        @DisplayName("Detecta 7B como MEDIUM")
        void testDetects7b() {
            assertEquals(HardwareTier.MEDIUM,
                HardwareTier.fromModelName("qwen2.5-coder:7b"));
        }

        @Test
        @DisplayName("Detecta 8B como MEDIUM")
        void testDetects8b() {
            assertEquals(HardwareTier.MEDIUM,
                HardwareTier.fromModelName("llama3.1:8b"));
        }

        @Test
        @DisplayName("Detecta 14B como MEDIUM")
        void testDetects14b() {
            assertEquals(HardwareTier.MEDIUM,
                HardwareTier.fromModelName("qwen2.5-coder:14b"));
        }

        @Test
        @DisplayName("Detecta 30B como HIGH_PERFORMANCE")
        void testDetects30b() {
            assertEquals(HardwareTier.HIGH_PERFORMANCE,
                HardwareTier.fromModelName("qwen3-coder:30b"));
        }

        @Test
        @DisplayName("Detecta 32B como HIGH_PERFORMANCE")
        void testDetects32b() {
            assertEquals(HardwareTier.HIGH_PERFORMANCE,
                HardwareTier.fromModelName("qwen2.5-coder:32b"));
        }

        @Test
        @DisplayName("Detecta 70B como HIGH_PERFORMANCE")
        void testDetects70b() {
            assertEquals(HardwareTier.HIGH_PERFORMANCE,
                HardwareTier.fromModelName("llama3.1:70b"));
        }

        @Test
        @DisplayName("Detecta GPT-4 como HIGH_PERFORMANCE")
        void testDetectsGpt4() {
            assertEquals(HardwareTier.HIGH_PERFORMANCE,
                HardwareTier.fromModelName("gpt-4o"));

            assertEquals(HardwareTier.HIGH_PERFORMANCE,
                HardwareTier.fromModelName("gpt-4-turbo"));
        }

        @Test
        @DisplayName("Detecta Claude-3 como HIGH_PERFORMANCE")
        void testDetectsClaude3() {
            assertEquals(HardwareTier.HIGH_PERFORMANCE,
                HardwareTier.fromModelName("claude-3-opus"));

            assertEquals(HardwareTier.HIGH_PERFORMANCE,
                HardwareTier.fromModelName("claude-3-sonnet"));
        }

        @Test
        @DisplayName("Null retorna MEDIUM (default seguro)")
        void testNullReturnsMedium() {
            assertEquals(HardwareTier.MEDIUM,
                HardwareTier.fromModelName(null));
        }

        @Test
        @DisplayName("String vacio retorna MEDIUM")
        void testEmptyReturnsMedium() {
            assertEquals(HardwareTier.MEDIUM,
                HardwareTier.fromModelName(""));

            assertEquals(HardwareTier.MEDIUM,
                HardwareTier.fromModelName("   "));
        }

        @Test
        @DisplayName("Modelo desconocido retorna MEDIUM")
        void testUnknownReturnsMedium() {
            assertEquals(HardwareTier.MEDIUM,
                HardwareTier.fromModelName("custom-model"));

            assertEquals(HardwareTier.MEDIUM,
                HardwareTier.fromModelName("mistral-latest"));
        }

        @Test
        @DisplayName("Case insensitive")
        void testCaseInsensitive() {
            assertEquals(HardwareTier.HIGH_PERFORMANCE,
                HardwareTier.fromModelName("QWEN2.5-CODER:32B"));

            assertEquals(HardwareTier.LOW_RESOURCE,
                HardwareTier.fromModelName("Qwen2.5-Coder:1.5B"));
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {
        @Test
        @DisplayName("toString incluye informacion relevante")
        void testToStringFormat() {
            String str = HardwareTier.LOW_RESOURCE.toString();

            assertTrue(str.contains("LOW_RESOURCE"));
            assertTrue(str.contains("12000"));
            assertTrue(str.contains("1024"));
            assertTrue(str.contains("DIRECTIVO"));
        }
    }
}
