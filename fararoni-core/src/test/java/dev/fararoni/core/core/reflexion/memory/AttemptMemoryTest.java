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

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("AttemptMemory - Memoria de Intentos")
class AttemptMemoryTest {
    private AttemptMemory memory;

    @BeforeEach
    void setUp() {
        memory = new AttemptMemory();
    }

    @Nested
    @DisplayName("Recording")
    class RecordingTests {
        @Test
        @DisplayName("recordAttempt almacena intento")
        void recordAttempt_storesAttempt() {
            RetryAttempt attempt = RetryAttempt.ofCode(1, "code");

            memory.recordAttempt("exercise-1", attempt);

            assertEquals(1, memory.getAttemptCount("exercise-1"));
        }

        @Test
        @DisplayName("recordAttempt almacena multiples intentos")
        void recordAttempt_storesMultiple() {
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(1, "code1"));
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(2, "code2"));
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(3, "code3"));

            assertEquals(3, memory.getAttemptCount("exercise-1"));
        }

        @Test
        @DisplayName("clearExercise elimina intentos de un ejercicio")
        void clearExercise_removesAttempts() {
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(1, "code"));

            memory.clearExercise("exercise-1");

            assertEquals(0, memory.getAttemptCount("exercise-1"));
        }

        @Test
        @DisplayName("clearAll elimina todos los intentos")
        void clearAll_removesAllAttempts() {
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(1, "code"));
            memory.recordAttempt("exercise-2", RetryAttempt.ofCode(1, "code"));

            memory.clearAll();

            assertEquals(0, memory.getAttemptCount("exercise-1"));
            assertEquals(0, memory.getAttemptCount("exercise-2"));
        }
    }

    @Nested
    @DisplayName("Queries")
    class QueryTests {
        @Test
        @DisplayName("getAttempts retorna lista de intentos")
        void getAttempts_returnsList() {
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(1, "code1"));
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(2, "code2"));

            List<RetryAttempt> attempts = memory.getAttempts("exercise-1");

            assertEquals(2, attempts.size());
        }

        @Test
        @DisplayName("getAttempts retorna lista vacia para ejercicio desconocido")
        void getAttempts_returnsEmptyForUnknown() {
            List<RetryAttempt> attempts = memory.getAttempts("unknown");

            assertTrue(attempts.isEmpty());
        }

        @Test
        @DisplayName("getLastAttempt retorna ultimo intento")
        void getLastAttempt_returnsLast() {
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(1, "code1"));
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(2, "code2"));

            Optional<RetryAttempt> last = memory.getLastAttempt("exercise-1");

            assertTrue(last.isPresent());
            assertEquals(2, last.get().attemptNumber());
        }

        @Test
        @DisplayName("hasAttempts retorna true si hay intentos")
        void hasAttempts_trueIfExists() {
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(1, "code"));

            assertTrue(memory.hasAttempts("exercise-1"));
            assertFalse(memory.hasAttempts("exercise-2"));
        }
    }

    @Nested
    @DisplayName("Repetition Detection")
    class RepetitionDetectionTests {
        @Test
        @DisplayName("isRepeatingCode detecta codigo identico")
        void isRepeatingCode_detectsIdentical() {
            String code = "def add(a, b): return a + b";
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(1, code));
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(2, code));

            assertTrue(memory.isRepeatingCode("exercise-1"));
        }

        @Test
        @DisplayName("isRepeatingCode retorna false para codigo diferente")
        void isRepeatingCode_falseForDifferent() {
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(1, "code1"));
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(2, "code2"));

            assertFalse(memory.isRepeatingCode("exercise-1"));
        }

        @Test
        @DisplayName("isRepeatingPattern detecta patron repetido")
        void isRepeatingPattern_detectsRepeated() {
            TestFailure failure1 = TestFailure.ofComparison("test_a", "AssertionError", "5", "4");
            TestFailure failure2 = TestFailure.ofComparison("test_b", "AssertionError", "10", "9");

            memory.recordAttempt("exercise-1", RetryAttempt.of(1, "code1", List.of(failure1)));
            memory.recordAttempt("exercise-1", RetryAttempt.of(2, "code2", List.of(failure2)));

            assertTrue(memory.isRepeatingPattern("exercise-1", FailurePattern.OFF_BY_ONE));
        }

        @Test
        @DisplayName("isRepeatingSameFailingTests detecta mismos tests fallando")
        void isRepeatingSameFailingTests_detectsSame() {
            TestFailure failure1 = TestFailure.ofName("test_add");
            TestFailure failure2 = TestFailure.ofName("test_add");

            memory.recordAttempt("exercise-1", RetryAttempt.of(1, "code1", List.of(failure1)));
            memory.recordAttempt("exercise-1", RetryAttempt.of(2, "code2", List.of(failure2)));

            assertTrue(memory.isRepeatingSameFailingTests("exercise-1"));
        }

        @Test
        @DisplayName("getRepeatedPatterns retorna patrones repetidos")
        void getRepeatedPatterns_returnsRepeated() {
            TestFailure failure1 = TestFailure.ofComparison("test_a", "AssertionError", "5", "4");
            TestFailure failure2 = TestFailure.ofComparison("test_b", "AssertionError", "10", "9");

            memory.recordAttempt("exercise-1", RetryAttempt.of(1, "code1", List.of(failure1)));
            memory.recordAttempt("exercise-1", RetryAttempt.of(2, "code2", List.of(failure2)));

            Set<FailurePattern> repeated = memory.getRepeatedPatterns("exercise-1", 3);

            assertTrue(repeated.contains(FailurePattern.OFF_BY_ONE));
        }
    }

    @Nested
    @DisplayName("Suggestions")
    class SuggestionTests {
        @Test
        @DisplayName("getSuggestionToAvoidRepeat genera sugerencia para codigo repetido")
        void getSuggestionToAvoidRepeat_generatesForRepeatedCode() {
            String code = "def add(a, b): return a + b";
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(1, code));
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(2, code));

            Optional<String> suggestion = memory.getSuggestionToAvoidRepeat("exercise-1");

            assertTrue(suggestion.isPresent());
            assertTrue(suggestion.get().contains("IDENTICO") || suggestion.get().contains("identico"));
        }

        @Test
        @DisplayName("getSuggestionToAvoidRepeat retorna empty sin repeticiones")
        void getSuggestionToAvoidRepeat_emptyWithoutRepetitions() {
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(1, "code1"));
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(2, "code2"));

            Optional<String> suggestion = memory.getSuggestionToAvoidRepeat("exercise-1");

            assertTrue(suggestion.isEmpty());
        }

        @Test
        @DisplayName("getHistorySummary genera resumen")
        void getHistorySummary_generatesSummary() {
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(1, "code1"));
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(2, "code2"));

            String summary = memory.getHistorySummary("exercise-1");

            assertTrue(summary.contains("exercise-1"));
            assertTrue(summary.contains("2 intentos"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {
        @Test
        @DisplayName("Retorna false para menos de 2 intentos")
        void repetitionChecks_falseForLessThan2() {
            memory.recordAttempt("exercise-1", RetryAttempt.ofCode(1, "code"));

            assertFalse(memory.isRepeatingCode("exercise-1"));
            assertFalse(memory.isRepeatingPattern("exercise-1", FailurePattern.OFF_BY_ONE));
            assertFalse(memory.isRepeatingSameFailingTests("exercise-1"));
        }

        @Test
        @DisplayName("Retorna empty para ejercicio sin intentos")
        void queries_emptyForNoAttempts() {
            Optional<RetryAttempt> last = memory.getLastAttempt("unknown");
            Optional<String> suggestion = memory.getSuggestionToAvoidRepeat("unknown");

            assertTrue(last.isEmpty());
            assertTrue(suggestion.isEmpty());
        }

        @Test
        @DisplayName("Constructor acepta TTL personalizado")
        void constructor_acceptsCustomTTL() {
            AttemptMemory customMemory = new AttemptMemory(Duration.ofMinutes(5));

            assertNotNull(customMemory);
        }
    }
}
