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
package dev.fararoni.core.core.reflexion.critics;

import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.reflexion.Evaluation;
import dev.fararoni.core.core.reflexion.EvaluationContext;
import dev.fararoni.core.core.reflexion.testoutput.FailurePattern;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("FailurePatternCritic - Deteccion de Patrones")
class FailurePatternCriticTest {
    private FailurePatternCritic critic;

    @BeforeEach
    void setUp() {
        critic = new FailurePatternCritic();
    }

    @Nested
    @DisplayName("Skip Tests")
    class SkipTests {
        @Test
        @DisplayName("Skip si no hay testOutput en contexto")
        void evaluate_skips_whenNoTestOutput() {
            EvaluationContext context = EvaluationContext.empty();

            Evaluation result = critic.evaluate("def add(a, b): return a + b", context);

            assertInstanceOf(Evaluation.Skip.class, result);
        }

        @Test
        @DisplayName("Skip si testOutput esta vacio")
        void evaluate_skips_whenTestOutputEmpty() {
            EvaluationContext context = EvaluationContext.builder()
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, "")
                .build();

            Evaluation result = critic.evaluate("def add(a, b): return a + b", context);

            assertInstanceOf(Evaluation.Skip.class, result);
        }

        @Test
        @DisplayName("Pass si output no indica failures (texto aleatorio)")
        void evaluate_passes_whenOutputHasNoFailureIndicators() {
            EvaluationContext context = EvaluationContext.builder()
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, "random text without failure info")
                .build();

            Evaluation result = critic.evaluate("def add(a, b): return a + b", context);

            assertInstanceOf(Evaluation.Pass.class, result);
        }
    }

    @Nested
    @DisplayName("Pass Tests")
    class PassTests {
        @Test
        @DisplayName("Pass si todos los tests pasaron")
        void evaluate_passes_whenAllTestsPass() {
            String output = """
                ============================= test session starts ==============================
                collected 5 items
                test_solution.py .....                                                   [100%]
                ============================== 5 passed in 0.01s ===============================
                """;

            EvaluationContext context = EvaluationContext.builder()
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output)
                .build();

            Evaluation result = critic.evaluate("def add(a, b): return a + b", context);

            assertInstanceOf(Evaluation.Pass.class, result);
        }
    }

    @Nested
    @DisplayName("Pattern Detection")
    class PatternDetectionTests {
        @Test
        @DisplayName("Detecta OFF_BY_ONE en output")
        void evaluate_detectsOffByOne() {
            String output = """
                FAILED test_solution.py::test_count - AssertionError: assert 5 == 4
                """;

            EvaluationContext context = EvaluationContext.forTestRetry("Implementa count", output, 1);

            Evaluation result = critic.evaluate("def count(): return 4", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.reason().contains("OFF_BY_ONE") ||
                       fail.suggestedFix().map(s -> s.contains("OFF_BY_ONE")).orElse(false),
                       "Debe detectar patron OFF_BY_ONE");
        }

        @Test
        @DisplayName("Detecta EMPTY_RESULT en output")
        void evaluate_detectsEmptyResult() {
            String output = """
                FAILED test_solution.py::test_get_items - AssertionError: assert [1, 2, 3] == None
                """;

            EvaluationContext context = EvaluationContext.forTestRetry("Implementa get_items", output, 1);

            Evaluation result = critic.evaluate("def get_items(): pass", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.suggestedFix().map(s -> s.contains("EMPTY_RESULT")).orElse(false),
                       "Debe detectar patron EMPTY_RESULT");
        }

        @Test
        @DisplayName("Detecta STRING_TYPO en output")
        void evaluate_detectsStringTypo() {
            String output = """
                FAILED test_solution.py::test_greet - AssertionError: assert 'hello' == 'hallo'
                """;

            EvaluationContext context = EvaluationContext.forTestRetry("Implementa greet", output, 1);

            Evaluation result = critic.evaluate("def greet(): return 'hallo'", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.suggestedFix().map(s -> s.contains("STRING_TYPO")).orElse(false),
                       "Debe detectar patron STRING_TYPO");
        }

        @Test
        @DisplayName("Detecta TYPE_MISMATCH por TypeError")
        void evaluate_detectsTypeMismatch() {
            String output = """
                FAILED test_solution.py::test_add - TypeError: unsupported operand type(s)
                """;

            EvaluationContext context = EvaluationContext.forTestRetry("Implementa add", output, 1);

            Evaluation result = critic.evaluate("def add(a, b): return a + b", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.suggestedFix().map(s -> s.contains("TYPE_MISMATCH")).orElse(false),
                       "Debe detectar patron TYPE_MISMATCH");
        }

        @Test
        @DisplayName("Detecta LOGIC_INVERSION para true vs false")
        void evaluate_detectsLogicInversion() {
            String output = """
                FAILED test_solution.py::test_is_valid - AssertionError: assert True == False
                """;

            EvaluationContext context = EvaluationContext.forTestRetry("Implementa is_valid", output, 1);

            Evaluation result = critic.evaluate("def is_valid(): return False", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.suggestedFix().map(s -> s.contains("LOGIC_INVERSION")).orElse(false),
                       "Debe detectar patron LOGIC_INVERSION");
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethodTests {
        @Test
        @DisplayName("getPatternSummary retorna conteo correcto")
        void getPatternSummary_returnsCorrectCounts() {
            String output = """
                FAILED test_solution.py::test_a - AssertionError: assert 5 == 4
                FAILED test_solution.py::test_b - AssertionError: assert 10 == 9
                FAILED test_solution.py::test_c - TypeError: unsupported operand
                """;

            Map<FailurePattern, Integer> summary = critic.getPatternSummary(output);

            assertFalse(summary.isEmpty());
            assertTrue(summary.containsKey(FailurePattern.OFF_BY_ONE) ||
                       summary.containsKey(FailurePattern.TYPE_MISMATCH));
        }

        @Test
        @DisplayName("getPatternSummary retorna mapa vacio para output vacio")
        void getPatternSummary_returnsEmpty_forEmptyOutput() {
            Map<FailurePattern, Integer> summary = critic.getPatternSummary("");

            assertTrue(summary.isEmpty());
        }

        @Test
        @DisplayName("getDominantPattern retorna patron correcto")
        void getDominantPattern_returnsCorrectPattern() {
            String output = """
                FAILED test_solution.py::test_a - AssertionError: assert 5 == 4
                FAILED test_solution.py::test_b - AssertionError: assert 10 == 9
                """;

            FailurePattern dominant = critic.getDominantPattern(output);

            assertEquals(FailurePattern.OFF_BY_ONE, dominant);
        }

        @Test
        @DisplayName("getDominantPattern retorna UNKNOWN para output vacio")
        void getDominantPattern_returnsUnknown_forEmptyOutput() {
            FailurePattern dominant = critic.getDominantPattern("");

            assertEquals(FailurePattern.UNKNOWN, dominant);
        }

        @Test
        @DisplayName("generatePatternFeedback genera feedback")
        void generatePatternFeedback_generatesFeedback() {
            String output = """
                FAILED test_solution.py::test_add - AssertionError: assert 5 == 4
                """;

            Optional<String> feedback = critic.generatePatternFeedback(output);

            assertTrue(feedback.isPresent());
            assertTrue(feedback.get().contains("OFF_BY_ONE"));
        }

        @Test
        @DisplayName("generatePatternFeedback retorna empty para sin failures")
        void generatePatternFeedback_returnsEmpty_forNoFailures() {
            Optional<String> feedback = critic.generatePatternFeedback("all tests passed");

            assertTrue(feedback.isEmpty());
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {
        @Test
        @DisplayName("withAllPatterns crea nueva instancia")
        void withAllPatterns_createsNewInstance() {
            FailurePatternCritic configured = critic.withAllPatterns(false);

            assertNotSame(critic, configured);
        }

        @Test
        @DisplayName("withMaxFailures crea nueva instancia")
        void withMaxFailures_createsNewInstance() {
            FailurePatternCritic configured = critic.withMaxFailures(5);

            assertNotSame(critic, configured);
        }
    }

    @Nested
    @DisplayName("Critic Interface")
    class CriticInterfaceTests {
        @Test
        @DisplayName("getName retorna FailurePatternCritic")
        void getName_returnsCorrectName() {
            assertEquals("FailurePatternCritic", critic.getName());
        }

        @Test
        @DisplayName("getDescription no es vacio")
        void getDescription_notEmpty() {
            assertFalse(critic.getDescription().isEmpty());
        }

        @Test
        @DisplayName("getCategory es CODE")
        void getCategory_isCode() {
            assertEquals(Critic.CriticCategory.CODE, critic.getCategory());
        }

        @Test
        @DisplayName("evaluate lanza NPE para response null")
        void evaluate_throwsNPE_forNullResponse() {
            assertThrows(NullPointerException.class,
                () -> critic.evaluate(null, EvaluationContext.empty()));
        }

        @Test
        @DisplayName("evaluate lanza NPE para context null")
        void evaluate_throwsNPE_forNullContext() {
            assertThrows(NullPointerException.class,
                () -> critic.evaluate("code", null));
        }
    }

    @Nested
    @DisplayName("Real World Examples")
    class RealWorldExampleTests {
        @Test
        @DisplayName("Analiza output complejo de pytest con multiples patrones")
        void evaluate_handlesComplexOutput() {
            String output = """
                ============================= test session starts ==============================
                collected 5 items

                test_solution.py::test_add FAILED
                test_solution.py::test_subtract FAILED
                test_solution.py::test_multiply PASSED
                test_solution.py::test_divide FAILED
                test_solution.py::test_modulo PASSED

                =================================== FAILURES ===================================
                __________________________________ test_add ____________________________________

                    def test_add():
                >       assert add(2, 3) == 5
                E       AssertionError: assert 4 == 5

                ________________________________ test_subtract _________________________________

                    def test_subtract():
                >       assert subtract(10, 3) == 7
                E       AssertionError: assert 6 == 7

                __________________________________ test_divide _________________________________

                    def test_divide():
                >       assert divide(10, 0) == 0
                E       ZeroDivisionError: division by zero

                =========================== short test summary info ============================
                FAILED test_solution.py::test_add - AssertionError: assert 4 == 5
                FAILED test_solution.py::test_subtract - AssertionError: assert 6 == 7
                FAILED test_solution.py::test_divide - ZeroDivisionError: division by zero
                ========================= 3 failed, 2 passed in 0.03s =========================
                """;

            EvaluationContext context = EvaluationContext.forTestRetry(
                "Implementa operaciones aritmeticas", output, 1);

            Evaluation result = critic.evaluate("def add(a, b): return a", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertFalse(fail.reason().isBlank(), "Reason no debe estar vacia");

            assertTrue(fail.suggestedFix().isPresent(), "Debe tener suggestedFix");

            String suggestion = fail.suggestedFix().get();
            assertTrue(suggestion.contains("OFF_BY_ONE") || suggestion.contains("UNHANDLED_EXCEPTION") ||
                       suggestion.contains("UNKNOWN"), "Debe mencionar al menos un patron detectado");
        }

        @Test
        @DisplayName("Detecta patron dominante en multiples failures")
        void evaluate_detectsDominantPattern() {
            String output = """
                FAILED test_solution.py::test_a - AssertionError: assert 5 == 4
                FAILED test_solution.py::test_b - AssertionError: assert 10 == 9
                FAILED test_solution.py::test_c - AssertionError: assert 100 == 99
                FAILED test_solution.py::test_d - TypeError: unsupported operand
                """;

            EvaluationContext context = EvaluationContext.forTestRetry("Tests", output, 1);

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.reason().contains("OFF_BY_ONE"),
                "OFF_BY_ONE debe ser el patron dominante");
        }
    }
}
