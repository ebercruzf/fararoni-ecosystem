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

import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("TestFailure - Record de Test Fallido")
class TestFailureTest {
    @Nested
    @DisplayName("Creacion")
    class CreationTests {
        @Test
        @DisplayName("Builder crea TestFailure valido")
        void builder_createsValidTestFailure() {
            TestFailure failure = TestFailure.builder()
                .testName("test_add")
                .errorType("AssertionError")
                .expected("5")
                .actual("4")
                .lineNumber(42)
                .fullMessage("assert 5 == 4")
                .fileName("test_math.py")
                .build();

            assertEquals("test_add", failure.testName());
            assertEquals("AssertionError", failure.errorType());
            assertEquals("5", failure.expected());
            assertEquals("4", failure.actual());
            assertEquals(42, failure.lineNumber());
            assertEquals("assert 5 == 4", failure.fullMessage());
            assertEquals("test_math.py", failure.fileName());
        }

        @Test
        @DisplayName("ofName crea TestFailure minimo")
        void ofName_createsMinimalTestFailure() {
            TestFailure failure = TestFailure.ofName("test_simple");

            assertEquals("test_simple", failure.testName());
            assertEquals("UnknownError", failure.errorType());
            assertEquals("", failure.expected());
            assertEquals("", failure.actual());
        }

        @Test
        @DisplayName("of crea TestFailure con mensaje")
        void of_createsTestFailureWithMessage() {
            TestFailure failure = TestFailure.of("test_x", "TypeError", "invalid type");

            assertEquals("test_x", failure.testName());
            assertEquals("TypeError", failure.errorType());
            assertEquals("invalid type", failure.fullMessage());
        }

        @Test
        @DisplayName("ofComparison crea TestFailure con expected/actual")
        void ofComparison_createsTestFailureWithComparison() {
            TestFailure failure = TestFailure.ofComparison(
                "test_sum", "AssertionError", "10", "9");

            assertEquals("test_sum", failure.testName());
            assertEquals("10", failure.expected());
            assertEquals("9", failure.actual());
            assertTrue(failure.hasComparison());
        }

        @Test
        @DisplayName("testName null lanza NullPointerException")
        void nullTestName_throwsNPE() {
            assertThrows(NullPointerException.class, () ->
                TestFailure.builder().testName(null).build());
        }

        @Test
        @DisplayName("testName vacio lanza IllegalArgumentException")
        void emptyTestName_throwsIAE() {
            assertThrows(IllegalArgumentException.class, () ->
                TestFailure.builder().testName("").build());
        }

        @Test
        @DisplayName("testName con solo espacios lanza IllegalArgumentException")
        void blankTestName_throwsIAE() {
            assertThrows(IllegalArgumentException.class, () ->
                TestFailure.builder().testName("   ").build());
        }
    }

    @Nested
    @DisplayName("Analisis Numerico")
    class NumericAnalysisTests {
        @Test
        @DisplayName("isNumericComparison detecta numeros")
        void isNumericComparison_detectsNumbers() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "5", "4");

            assertTrue(failure.isNumericComparison());
        }

        @Test
        @DisplayName("isNumericComparison rechaza strings")
        void isNumericComparison_rejectsStrings() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "hello", "world");

            assertFalse(failure.isNumericComparison());
        }

        @Test
        @DisplayName("numericDifference calcula diferencia correcta")
        void numericDifference_calculatesCorrectly() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "10", "7");

            Optional<Double> diff = failure.numericDifference();

            assertTrue(diff.isPresent());
            assertEquals(3.0, diff.get(), 0.001);
        }

        @Test
        @DisplayName("isOffByOne detecta diferencia de 1")
        void isOffByOne_detectsDifferenceOfOne() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "5", "4");

            assertTrue(failure.isOffByOne());
        }

        @Test
        @DisplayName("isOffByOne detecta diferencia de -1")
        void isOffByOne_detectsNegativeOne() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "4", "5");

            assertTrue(failure.isOffByOne());
        }

        @Test
        @DisplayName("isOffByOne rechaza diferencia mayor")
        void isOffByOne_rejectsLargerDifference() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "10", "5");

            assertFalse(failure.isOffByOne());
        }

        @Test
        @DisplayName("numericDifference con decimales")
        void numericDifference_handlesDecimals() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "3.14", "3.15");

            Optional<Double> diff = failure.numericDifference();

            assertTrue(diff.isPresent());
            assertEquals(-0.01, diff.get(), 0.001);
        }
    }

    @Nested
    @DisplayName("Analisis de Strings")
    class StringAnalysisTests {
        @Test
        @DisplayName("isStringComparison detecta strings")
        void isStringComparison_detectsStrings() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "hello", "hallo");

            assertTrue(failure.isStringComparison());
        }

        @Test
        @DisplayName("stringEditDistance calcula distancia correcta")
        void stringEditDistance_calculatesCorrectly() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "kitten", "sitting");

            assertEquals(3, failure.stringEditDistance());
        }

        @Test
        @DisplayName("isSingleCharDifference detecta un caracter")
        void isSingleCharDifference_detectsSingleChar() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "Take one down", "Take ane down");

            assertTrue(failure.isSingleCharDifference());
        }

        @Test
        @DisplayName("isSingleCharDifference rechaza multiples diferencias")
        void isSingleCharDifference_rejectsMultiple() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "hello", "world");

            assertFalse(failure.isSingleCharDifference());
        }
    }

    @Nested
    @DisplayName("Deteccion de Vacios")
    class EmptyDetectionTests {
        @Test
        @DisplayName("isEmptyActual detecta string vacio")
        void isEmptyActual_detectsEmptyString() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "[1, 2, 3]", "");

            assertTrue(failure.isEmptyActual());
        }

        @Test
        @DisplayName("isEmptyActual detecta None")
        void isEmptyActual_detectsNone() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "[1, 2, 3]", "None");

            assertTrue(failure.isEmptyActual());
        }

        @Test
        @DisplayName("isEmptyActual detecta null string")
        void isEmptyActual_detectsNullString() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "value", "null");

            assertTrue(failure.isEmptyActual());
        }

        @Test
        @DisplayName("isEmptyActual detecta lista vacia")
        void isEmptyActual_detectsEmptyList() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "[1, 2]", "[]");

            assertTrue(failure.isEmptyActual());
        }

        @Test
        @DisplayName("isEmptyActual detecta dict vacio")
        void isEmptyActual_detectsEmptyDict() {
            TestFailure failure = TestFailure.ofComparison(
                "test", "AssertionError", "{'a': 1}", "{}");

            assertTrue(failure.isEmptyActual());
        }
    }

    @Nested
    @DisplayName("Tipos de Error")
    class ErrorTypeTests {
        @Test
        @DisplayName("isTypeError detecta TypeError")
        void isTypeError_detectsTypeError() {
            TestFailure failure = TestFailure.of("test", "TypeError", "msg");

            assertTrue(failure.isTypeError());
        }

        @Test
        @DisplayName("isAssertionError detecta AssertionError")
        void isAssertionError_detectsAssertionError() {
            TestFailure failure = TestFailure.of("test", "AssertionError", "msg");

            assertTrue(failure.isAssertionError());
        }

        @Test
        @DisplayName("hasComparison es false sin expected/actual")
        void hasComparison_falseWithoutValues() {
            TestFailure failure = TestFailure.ofName("test");

            assertFalse(failure.hasComparison());
        }
    }

    @Nested
    @DisplayName("Formateo")
    class FormattingTests {
        @Test
        @DisplayName("toShortSummary genera resumen corto")
        void toShortSummary_generatesShortSummary() {
            TestFailure failure = TestFailure.ofComparison(
                "test_add", "AssertionError", "5", "4");

            String summary = failure.toShortSummary();

            assertTrue(summary.contains("test_add"));
            assertTrue(summary.contains("AssertionError"));
            assertTrue(summary.contains("5"));
            assertTrue(summary.contains("4"));
        }

        @Test
        @DisplayName("toDetailedSummary genera markdown")
        void toDetailedSummary_generatesMarkdown() {
            TestFailure failure = TestFailure.builder()
                .testName("test_add")
                .errorType("AssertionError")
                .expected("5")
                .actual("4")
                .lineNumber(42)
                .build();

            String detailed = failure.toDetailedSummary();

            assertTrue(detailed.contains("### test_add"));
            assertTrue(detailed.contains("**Error:**"));
            assertTrue(detailed.contains("**Esperado:**"));
            assertTrue(detailed.contains("**Recibido:**"));
            assertTrue(detailed.contains("OFF_BY_ONE"));
            assertTrue(detailed.contains("**Linea:**"));
        }

        @Test
        @DisplayName("toDetailedSummary sin comparacion")
        void toDetailedSummary_withoutComparison() {
            TestFailure failure = TestFailure.of("test_x", "RuntimeError", "msg");

            String detailed = failure.toDetailedSummary();

            assertTrue(detailed.contains("### test_x"));
            assertTrue(detailed.contains("RuntimeError"));
            assertFalse(detailed.contains("**Esperado:**"));
        }
    }

    @Nested
    @DisplayName("Campos Opcionales")
    class OptionalFieldsTests {
        @Test
        @DisplayName("getLineNumber retorna Optional")
        void getLineNumber_returnsOptional() {
            TestFailure withLine = TestFailure.builder()
                .testName("test")
                .lineNumber(42)
                .build();

            TestFailure withoutLine = TestFailure.ofName("test");

            assertTrue(withLine.getLineNumber().isPresent());
            assertEquals(42, withLine.getLineNumber().get());
            assertTrue(withoutLine.getLineNumber().isEmpty());
        }

        @Test
        @DisplayName("getFileName retorna Optional")
        void getFileName_returnsOptional() {
            TestFailure withFile = TestFailure.builder()
                .testName("test")
                .fileName("test.py")
                .build();

            TestFailure withoutFile = TestFailure.ofName("test");

            assertTrue(withFile.getFileName().isPresent());
            assertEquals("test.py", withFile.getFileName().get());
            assertTrue(withoutFile.getFileName().isEmpty());
        }
    }
}
