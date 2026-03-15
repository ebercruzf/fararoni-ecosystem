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
package dev.fararoni.core.core.utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("TokenUtils Tests")
class TokenUtilsTest {
    @Nested
    @DisplayName("calculateTokens")
    class CalculateTokensTests {
        @ParameterizedTest(name = "calculateTokens({0}) = {1}")
        @CsvSource({
            "0, 0",
            "1, 1",
            "4, 1",
            "5, 2",
            "100, 25",
            "1000, 250",
            "4000, 1000",
            "4001, 1001"
        })
        @DisplayName("Debe calcular tokens correctamente")
        void calculateTokens_VariousInputs_CalculatesCorrectly(long chars, long expectedTokens) {
            assertEquals(expectedTokens, TokenUtils.calculateTokens(chars));
        }

        @Test
        @DisplayName("Valores negativos deben retornar 0")
        void calculateTokens_NegativeValue_ReturnsZero() {
            assertEquals(0, TokenUtils.calculateTokens(-1));
            assertEquals(0, TokenUtils.calculateTokens(-100));
            assertEquals(0, TokenUtils.calculateTokens(Long.MIN_VALUE));
        }

        @Test
        @DisplayName("Valores grandes deben calcularse sin overflow")
        void calculateTokens_LargeValue_NoOverflow() {
            assertEquals(250_000, TokenUtils.calculateTokens(1_000_000));
            assertEquals(250_000_000_000L, TokenUtils.calculateTokens(1_000_000_000_000L));
        }
    }

    @Nested
    @DisplayName("formatTokenCount")
    class FormatTokenCountTests {
        @ParameterizedTest(name = "formatTokenCount({0}) = \"{1}\"")
        @CsvSource({
            "0, 0",
            "1, 1",
            "999, 999",
            "1000, 1.0k",
            "1500, 1.5k",
            "12500, 12.5k",
            "100000, 100.0k",
            "1000000, 1000.0k"
        })
        @DisplayName("Debe formatear tokens correctamente")
        void formatTokenCount_VariousInputs_FormatsCorrectly(long tokens, String expected) {
            assertEquals(expected, TokenUtils.formatTokenCount(tokens));
        }

        @Test
        @DisplayName("Valores negativos deben retornar '0'")
        void formatTokenCount_NegativeValue_ReturnsZero() {
            assertEquals("0", TokenUtils.formatTokenCount(-1));
            assertEquals("0", TokenUtils.formatTokenCount(-1000));
        }
    }

    @Nested
    @DisplayName("formatMeta")
    class FormatMetaTests {
        @Test
        @DisplayName("Debe generar formato completo con input y output")
        void formatMeta_WithBothInputAndOutput_GeneratesCompleteFormat() {
            String result = TokenUtils.formatMeta(5000, 20000, 4000);

            assertTrue(result.contains("5s"), "Debe contener duracion");
            assertTrue(result.contains("↑"), "Debe contener flecha de input");
            assertTrue(result.contains("↓"), "Debe contener flecha de output");
            assertTrue(result.contains("5.0k"), "Debe contener tokens de input");
            assertTrue(result.contains("1.0k"), "Debe contener tokens de output");
        }

        @Test
        @DisplayName("Debe manejar valores cero correctamente")
        void formatMeta_ZeroValues_HandlesCorrectly() {
            String result = TokenUtils.formatMeta(0, 0, 0);

            assertTrue(result.contains("0s"), "Debe contener 0 segundos");
            assertTrue(result.contains("↑ 0"), "Debe contener 0 tokens input");
            assertTrue(result.contains("↓ 0"), "Debe contener 0 tokens output");
        }

        @Test
        @DisplayName("Debe usar separador correcto entre componentes")
        void formatMeta_SeparatorFormat_IsCorrect() {
            String result = TokenUtils.formatMeta(1000, 400, 400);

            assertTrue(result.contains(" · "), "Debe usar ' · ' como separador");
        }

        @Test
        @DisplayName("Debe manejar duraciones negativas como 0")
        void formatMeta_NegativeDuration_TreatedAsZero() {
            String result = TokenUtils.formatMeta(-1000, 4000, 2000);

            assertTrue(result.contains("0s"), "Duracion negativa debe ser 0s");
        }
    }

    @Nested
    @DisplayName("formatMetaInputOnly")
    class FormatMetaInputOnlyTests {
        @Test
        @DisplayName("Debe generar formato solo con input")
        void formatMetaInputOnly_GeneratesInputOnlyFormat() {
            String result = TokenUtils.formatMetaInputOnly(3000, 20000);

            assertTrue(result.contains("3s"), "Debe contener duracion");
            assertTrue(result.contains("↑"), "Debe contener flecha de input");
            assertTrue(result.contains("5.0k"), "Debe contener tokens de input");
            assertFalse(result.contains("↓"), "NO debe contener flecha de output");
        }

        @Test
        @DisplayName("Debe manejar valores cero correctamente")
        void formatMetaInputOnly_ZeroValues_HandlesCorrectly() {
            String result = TokenUtils.formatMetaInputOnly(0, 0);

            assertTrue(result.contains("0s"), "Debe contener 0 segundos");
            assertTrue(result.contains("↑ 0"), "Debe contener 0 tokens");
        }

        @Test
        @DisplayName("Debe ser mas corto que formatMeta completo")
        void formatMetaInputOnly_ShorterThanFull() {
            String full = TokenUtils.formatMeta(1000, 4000, 2000);
            String inputOnly = TokenUtils.formatMetaInputOnly(1000, 4000);

            assertTrue(inputOnly.length() < full.length(),
                    "formatMetaInputOnly debe ser mas corto que formatMeta completo");
        }
    }

    @Nested
    @DisplayName("estimateCost")
    class EstimateCostTests {
        @Test
        @DisplayName("Debe calcular costo correctamente para Claude 3.5 Sonnet")
        void estimateCost_Claude35Sonnet_CalculatesCorrectly() {
            double cost = TokenUtils.estimateCost(10000, 2000, 3.0, 15.0);

            assertEquals(0.06, cost, 0.001);
        }

        @Test
        @DisplayName("Debe calcular costo correctamente para GPT-4 Turbo")
        void estimateCost_GPT4Turbo_CalculatesCorrectly() {
            double cost = TokenUtils.estimateCost(100000, 10000, 10.0, 30.0);

            assertEquals(1.3, cost, 0.001);
        }

        @Test
        @DisplayName("Cero tokens debe dar costo cero")
        void estimateCost_ZeroTokens_ZeroCost() {
            assertEquals(0.0, TokenUtils.estimateCost(0, 0, 10.0, 30.0));
        }

        @Test
        @DisplayName("Solo input tokens debe calcular correctamente")
        void estimateCost_OnlyInput_CalculatesCorrectly() {
            double cost = TokenUtils.estimateCost(1_000_000, 0, 3.0, 15.0);
            assertEquals(3.0, cost, 0.001);
        }
    }

    @Nested
    @DisplayName("formatCost")
    class FormatCostTests {
        @ParameterizedTest(name = "formatCost({0}) = \"{1}\"")
        @CsvSource({
            "0.0, $0.00",
            "0.005, <$0.01",
            "0.01, $0.01",
            "0.05, $0.05",
            "0.99, $0.99",
            "1.0, $1.00",
            "1.23, $1.23",
            "10.5, $10.50"
        })
        @DisplayName("Debe formatear costos correctamente")
        void formatCost_VariousInputs_FormatsCorrectly(double cost, String expected) {
            assertEquals(expected, TokenUtils.formatCost(cost));
        }

        @Test
        @DisplayName("Valores negativos deben retornar $0.00")
        void formatCost_NegativeValue_ReturnsZero() {
            assertEquals("$0.00", TokenUtils.formatCost(-1.0));
            assertEquals("$0.00", TokenUtils.formatCost(-100.0));
        }

        @Test
        @DisplayName("Valores muy pequenos deben mostrar <$0.01")
        void formatCost_VerySmallValue_ShowsLessThan() {
            assertEquals("<$0.01", TokenUtils.formatCost(0.001));
            assertEquals("<$0.01", TokenUtils.formatCost(0.009));
        }
    }

    @Nested
    @DisplayName("Integracion")
    class IntegrationTests {
        @Test
        @DisplayName("Flujo completo: chars -> tokens -> meta -> cost")
        void fullWorkflow_CharsToTokensToCost() {
            long inputChars = 50_000;
            long outputChars = 10_000;

            long inputTokens = TokenUtils.calculateTokens(inputChars);
            long outputTokens = TokenUtils.calculateTokens(outputChars);

            assertEquals(12_500, inputTokens);
            assertEquals(2_500, outputTokens);

            String meta = TokenUtils.formatMeta(5000, inputChars, outputChars);
            assertTrue(meta.contains("12.5k"));
            assertTrue(meta.contains("2.5k"));

            double cost = TokenUtils.estimateCost(inputTokens, outputTokens, 3.0, 15.0);
            assertEquals(0.075, cost, 0.001);

            String formattedCost = TokenUtils.formatCost(cost);
            assertEquals("$0.08", formattedCost);
        }

        @Test
        @DisplayName("Simulacion de streaming con actualizaciones incrementales")
        void streamingSimulation_IncrementalUpdates() {
            long startTime = 0;
            long inputChars = 20_000;

            String meta1 = TokenUtils.formatMetaInputOnly(0, inputChars);
            assertTrue(meta1.contains("↑ 5.0k"));
            assertFalse(meta1.contains("↓"));

            String meta2 = TokenUtils.formatMeta(2000, inputChars, 2000);
            assertTrue(meta2.contains("↑ 5.0k"));
            assertTrue(meta2.contains("↓ 500"));

            String meta3 = TokenUtils.formatMeta(10000, inputChars, 8000);
            assertTrue(meta3.contains("↑ 5.0k"));
            assertTrue(meta3.contains("↓ 2.0k"));
        }
    }
}
