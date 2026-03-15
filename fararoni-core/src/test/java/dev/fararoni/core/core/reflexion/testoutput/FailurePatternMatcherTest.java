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
package dev.fararoni.core.core.reflexion.testoutput;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("FailurePatternMatcher - Deteccion de Patrones")
class FailurePatternMatcherTest {
    @Nested
    @DisplayName("Patrones Numericos")
    class NumericPatternTests {
        @Test
        @DisplayName("Detecta OFF_BY_ONE para diferencia de 1")
        void match_offByOne_detectsDifferenceOfOne() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "5", "4");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.OFF_BY_ONE, pattern);
        }

        @Test
        @DisplayName("Detecta OFF_BY_ONE para diferencia de -1")
        void match_offByOne_detectsNegativeOne() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "4", "5");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.OFF_BY_ONE, pattern);
        }

        @Test
        @DisplayName("Detecta PRECISION_ERROR para floats cercanos")
        void match_precisionError_detectsSmallDifference() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "3.14159", "3.14160");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.PRECISION_ERROR, pattern);
        }

        @Test
        @DisplayName("No detecta OFF_BY_ONE para diferencia mayor")
        void match_notOffByOne_forLargerDifference() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "10", "5");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertNotEquals(FailurePattern.OFF_BY_ONE, pattern);
        }
    }

    @Nested
    @DisplayName("Patrones de String")
    class StringPatternTests {
        @Test
        @DisplayName("Detecta STRING_TYPO para distancia de 1")
        void match_stringTypo_detectsSingleCharDifference() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "hello", "hallo");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.STRING_TYPO, pattern);
        }

        @Test
        @DisplayName("Detecta STRING_MISMATCH para diferencias moderadas")
        void match_stringMismatch_detectsMultipleCharDifference() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "hello", "world");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.STRING_MISMATCH, pattern);
        }

        @Test
        @DisplayName("Detecta ORDER_MISMATCH para mismos caracteres diferente orden")
        void match_orderMismatch_detectsSameCharsDifferentOrder() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "abc", "cba");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.ORDER_MISMATCH, pattern);
        }
    }

    @Nested
    @DisplayName("Patrones de Vacio")
    class EmptyPatternTests {
        @Test
        @DisplayName("Detecta EMPTY_RESULT para None")
        void match_emptyResult_detectsNone() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "[1, 2]", "None");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.EMPTY_RESULT, pattern);
        }

        @Test
        @DisplayName("Detecta EMPTY_RESULT para lista vacia")
        void match_emptyResult_detectsEmptyList() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "[1, 2]", "[]");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.EMPTY_RESULT, pattern);
        }

        @Test
        @DisplayName("Detecta EXPECTED_EMPTY cuando se esperaba vacio")
        void match_expectedEmpty_detectsUnexpectedContent() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "None", "[1, 2]");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.EXPECTED_EMPTY, pattern);
        }
    }

    @Nested
    @DisplayName("Patrones por Tipo de Error")
    class ErrorTypePatternTests {
        @Test
        @DisplayName("Detecta TYPE_MISMATCH para TypeError")
        void match_typeMismatch_detectsTypeError() {
            TestFailure failure = TestFailure.of("test", "TypeError", "unsupported operand");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.TYPE_MISMATCH, pattern);
        }

        @Test
        @DisplayName("Detecta INDEX_ERROR para IndexError")
        void match_indexError_detectsIndexError() {
            TestFailure failure = TestFailure.of("test", "IndexError", "list index out of range");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.INDEX_ERROR, pattern);
        }

        @Test
        @DisplayName("Detecta KEY_ERROR para KeyError")
        void match_keyError_detectsKeyError() {
            TestFailure failure = TestFailure.of("test", "KeyError", "'missing_key'");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.KEY_ERROR, pattern);
        }

        @Test
        @DisplayName("Detecta ATTRIBUTE_ERROR para AttributeError")
        void match_attributeError_detectsAttributeError() {
            TestFailure failure = TestFailure.of("test", "AttributeError", "no attribute 'foo'");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.ATTRIBUTE_ERROR, pattern);
        }
    }

    @Nested
    @DisplayName("Patrones Logicos")
    class LogicPatternTests {
        @Test
        @DisplayName("Detecta LOGIC_INVERSION para true vs false")
        void match_logicInversion_detectsTrueFalse() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "true", "false");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.LOGIC_INVERSION, pattern);
        }

        @Test
        @DisplayName("Detecta LOGIC_INVERSION para 0 vs 1")
        void match_logicInversion_detectsZeroOne() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "0", "1");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.LOGIC_INVERSION, pattern);
        }
    }

    @Nested
    @DisplayName("matchAll()")
    class MatchAllTests {
        @Test
        @DisplayName("matchAll retorna lista no vacia")
        void matchAll_returnsNonEmptyList() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "5", "4");

            List<FailurePattern> patterns = FailurePatternMatcher.matchAll(failure);

            assertFalse(patterns.isEmpty());
            assertTrue(patterns.contains(FailurePattern.OFF_BY_ONE));
        }

        @Test
        @DisplayName("matchAll incluye UNKNOWN como fallback")
        void matchAll_includesUnknownAsFallback() {
            TestFailure failure = TestFailure.of("test", "UnknownError", "mysterious error");

            List<FailurePattern> patterns = FailurePatternMatcher.matchAll(failure);

            assertTrue(patterns.contains(FailurePattern.UNKNOWN));
        }
    }

    @Nested
    @DisplayName("matches()")
    class MatchesTests {
        @Test
        @DisplayName("matches retorna true para patron correcto")
        void matches_trueForCorrectPattern() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "5", "4");

            assertTrue(FailurePatternMatcher.matches(failure, FailurePattern.OFF_BY_ONE));
        }

        @Test
        @DisplayName("matches retorna false para patron incorrecto")
        void matches_falseForIncorrectPattern() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "5", "4");

            assertFalse(FailurePatternMatcher.matches(failure, FailurePattern.STRING_TYPO));
        }
    }

    @Nested
    @DisplayName("Casos Borde")
    class EdgeCaseTests {
        @Test
        @DisplayName("match lanza NPE para null")
        void match_throwsNPE_forNull() {
            assertThrows(NullPointerException.class, () -> FailurePatternMatcher.match(null));
        }

        @Test
        @DisplayName("match retorna UNKNOWN para failure sin comparacion ni tipo especifico")
        void match_returnsUnknown_forGenericFailure() {
            TestFailure failure = TestFailure.ofName("test_generic");

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.UNKNOWN, pattern);
        }

        @Test
        @DisplayName("match por mensaje detecta index out of range")
        void match_byMessage_detectsIndexOutOfRange() {
            TestFailure failure = TestFailure.builder()
                .testName("test")
                .errorType("AssertionError")
                .fullMessage("list index out of range at position 5")
                .build();

            FailurePattern pattern = FailurePatternMatcher.match(failure);

            assertEquals(FailurePattern.INDEX_ERROR, pattern);
        }
    }

    @Nested
    @DisplayName("FailurePattern Enum")
    class FailurePatternEnumTests {
        @Test
        @DisplayName("Todos los patrones tienen descripcion")
        void allPatterns_haveDescription() {
            for (FailurePattern pattern : FailurePattern.values()) {
                assertNotNull(pattern.getDescription());
                assertFalse(pattern.getDescription().isEmpty());
            }
        }

        @Test
        @DisplayName("Todos los patrones tienen sugerencia")
        void allPatterns_haveSuggestion() {
            for (FailurePattern pattern : FailurePattern.values()) {
                assertNotNull(pattern.getSuggestion());
                assertFalse(pattern.getSuggestion().isEmpty());
            }
        }

        @Test
        @DisplayName("Todos los patrones tienen severidad")
        void allPatterns_haveSeverity() {
            for (FailurePattern pattern : FailurePattern.values()) {
                assertNotNull(pattern.getSeverity());
            }
        }

        @Test
        @DisplayName("isHighSeverity es true para OFF_BY_ONE")
        void isHighSeverity_trueForOffByOne() {
            assertTrue(FailurePattern.OFF_BY_ONE.isHighSeverity());
        }

        @Test
        @DisplayName("toSummary genera resumen formateado")
        void toSummary_generatesFormattedSummary() {
            String summary = FailurePattern.OFF_BY_ONE.toSummary();

            assertTrue(summary.contains("OFF_BY_ONE"));
            assertTrue(summary.contains("Diferencia"));
        }

        @Test
        @DisplayName("toFeedback genera feedback para LLM")
        void toFeedback_generatesFeedbackForLLM() {
            String feedback = FailurePattern.STRING_TYPO.toFeedback();

            assertTrue(feedback.contains("STRING_TYPO"));
            assertTrue(feedback.contains("Sugerencia"));
        }
    }
}
