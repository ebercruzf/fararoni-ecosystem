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
package dev.fararoni.core.core.reflexion.memory;

import dev.fararoni.core.core.reflexion.testoutput.FailurePattern;
import dev.fararoni.core.core.reflexion.testoutput.TestFailure;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("RetryAttempt - Registro de Intentos")
class RetryAttemptTest {
    @Nested
    @DisplayName("Creation")
    class CreationTests {
        @Test
        @DisplayName("of() crea intento con failures y detecta patrones")
        void of_createsWithFailuresAndPatterns() {
            TestFailure failure = TestFailure.ofComparison("test_add", "AssertionError", "5", "4");

            RetryAttempt attempt = RetryAttempt.of(1, "def add(a, b): return a", List.of(failure));

            assertEquals(1, attempt.attemptNumber());
            assertEquals(1, attempt.failureCount());
            assertTrue(attempt.hasPattern(FailurePattern.OFF_BY_ONE));
        }

        @Test
        @DisplayName("ofCode() crea intento solo con codigo")
        void ofCode_createsWithCodeOnly() {
            RetryAttempt attempt = RetryAttempt.ofCode(1, "def add(a, b): return a + b");

            assertEquals(1, attempt.attemptNumber());
            assertEquals(0, attempt.failureCount());
            assertTrue(attempt.isSuccessful());
        }

        @Test
        @DisplayName("Constructor valida attemptNumber >= 1")
        void constructor_validatesAttemptNumber() {
            assertThrows(IllegalArgumentException.class, () ->
                RetryAttempt.ofCode(0, "code"));

            assertThrows(IllegalArgumentException.class, () ->
                RetryAttempt.ofCode(-1, "code"));
        }

        @Test
        @DisplayName("Constructor maneja null gracefully")
        void constructor_handlesNulls() {
            RetryAttempt attempt = RetryAttempt.of(1, null, List.of());

            assertEquals("", attempt.code());
            assertTrue(attempt.failures().isEmpty());
            assertTrue(attempt.patterns().isEmpty());
        }
    }

    @Nested
    @DisplayName("Pattern Detection")
    class PatternTests {
        @Test
        @DisplayName("hasPattern retorna true para patron presente")
        void hasPattern_trueForPresent() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "5", "4");

            RetryAttempt attempt = RetryAttempt.of(1, "code", List.of(failure));

            assertTrue(attempt.hasPattern(FailurePattern.OFF_BY_ONE));
        }

        @Test
        @DisplayName("hasPattern retorna false para patron ausente")
        void hasPattern_falseForAbsent() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "5", "4");

            RetryAttempt attempt = RetryAttempt.of(1, "code", List.of(failure));

            assertFalse(attempt.hasPattern(FailurePattern.STRING_TYPO));
        }

        @Test
        @DisplayName("hasHighSeverityPattern detecta patrones HIGH")
        void hasHighSeverityPattern_detectsHigh() {
            TestFailure failure = TestFailure.of("test", "TypeError", "type error");

            RetryAttempt attempt = RetryAttempt.of(1, "code", List.of(failure));

            assertTrue(attempt.hasHighSeverityPattern());
        }
    }

    @Nested
    @DisplayName("Comparison")
    class ComparisonTests {
        @Test
        @DisplayName("hasSameCode detecta codigo identico")
        void hasSameCode_detectsIdentical() {
            String code = "def add(a, b): return a + b";

            RetryAttempt attempt1 = RetryAttempt.ofCode(1, code);
            RetryAttempt attempt2 = RetryAttempt.ofCode(2, code);

            assertTrue(attempt1.hasSameCode(attempt2));
        }

        @Test
        @DisplayName("hasSameCode retorna false para codigo diferente")
        void hasSameCode_falseForDifferent() {
            RetryAttempt attempt1 = RetryAttempt.ofCode(1, "def add(a, b): return a");
            RetryAttempt attempt2 = RetryAttempt.ofCode(2, "def add(a, b): return a + b");

            assertFalse(attempt1.hasSameCode(attempt2));
        }

        @Test
        @DisplayName("hasSamePatterns detecta patrones iguales")
        void hasSamePatterns_detectsSame() {
            TestFailure failure1 = TestFailure.ofComparison("test_a", "AssertionError", "5", "4");
            TestFailure failure2 = TestFailure.ofComparison("test_b", "AssertionError", "10", "9");

            RetryAttempt attempt1 = RetryAttempt.of(1, "code1", List.of(failure1));
            RetryAttempt attempt2 = RetryAttempt.of(2, "code2", List.of(failure2));

            assertTrue(attempt1.hasSamePatterns(attempt2));
        }

        @Test
        @DisplayName("sharesSameFailingTests detecta tests compartidos")
        void sharesSameFailingTests_detectsShared() {
            TestFailure failure1 = TestFailure.ofComparison("test_add", "AssertionError", "5", "4");
            TestFailure failure2 = TestFailure.ofComparison("test_add", "AssertionError", "10", "9");

            RetryAttempt attempt1 = RetryAttempt.of(1, "code1", List.of(failure1));
            RetryAttempt attempt2 = RetryAttempt.of(2, "code2", List.of(failure2));

            assertTrue(attempt1.sharesSameFailingTests(attempt2));
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityTests {
        @Test
        @DisplayName("failingTestNames retorna nombres de tests")
        void failingTestNames_returnsNames() {
            TestFailure failure1 = TestFailure.ofName("test_a");
            TestFailure failure2 = TestFailure.ofName("test_b");

            RetryAttempt attempt = RetryAttempt.of(1, "code", List.of(failure1, failure2));

            assertEquals(2, attempt.failingTestNames().size());
            assertTrue(attempt.failingTestNames().contains("test_a"));
            assertTrue(attempt.failingTestNames().contains("test_b"));
        }

        @Test
        @DisplayName("isSuccessful retorna true sin failures")
        void isSuccessful_trueWithoutFailures() {
            RetryAttempt attempt = RetryAttempt.ofCode(1, "code");

            assertTrue(attempt.isSuccessful());
        }

        @Test
        @DisplayName("isSuccessful retorna false con failures")
        void isSuccessful_falseWithFailures() {
            TestFailure failure = TestFailure.ofName("test");

            RetryAttempt attempt = RetryAttempt.of(1, "code", List.of(failure));

            assertFalse(attempt.isSuccessful());
        }

        @Test
        @DisplayName("toSummary genera resumen legible")
        void toSummary_generatesReadableSummary() {
            TestFailure failure = TestFailure.ofComparison("test", "AssertionError", "5", "4");

            RetryAttempt attempt = RetryAttempt.of(1, "code", List.of(failure));

            String summary = attempt.toSummary();
            assertTrue(summary.contains("Intento #1"));
            assertTrue(summary.contains("1 failures"));
        }

        @Test
        @DisplayName("toSummary indica EXITOSO sin failures")
        void toSummary_indicatesSuccess() {
            RetryAttempt attempt = RetryAttempt.ofCode(1, "code");

            String summary = attempt.toSummary();
            assertTrue(summary.contains("EXITOSO"));
        }
    }
}
