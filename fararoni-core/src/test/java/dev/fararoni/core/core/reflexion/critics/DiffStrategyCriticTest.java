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
import dev.fararoni.core.core.reflexion.testoutput.TestFailure;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("DiffStrategyCritic - Generacion de Estrategias")
class DiffStrategyCriticTest {
    private DiffStrategyCritic critic;

    @BeforeEach
    void setUp() {
        critic = new DiffStrategyCritic();
    }

    @Nested
    @DisplayName("Skip Tests")
    class SkipTests {
        @Test
        @DisplayName("Skip si no hay testOutput")
        void evaluate_skips_whenNoTestOutput() {
            EvaluationContext context = EvaluationContext.empty();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Skip.class, result);
        }

        @Test
        @DisplayName("Skip si testOutput esta vacio")
        void evaluate_skips_whenTestOutputEmpty() {
            EvaluationContext context = EvaluationContext.builder()
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, "")
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Skip.class, result);
        }

        @Test
        @DisplayName("Skip si no se pueden parsear failures")
        void evaluate_skips_whenCannotParseFailures() {
            EvaluationContext context = EvaluationContext.builder()
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, "FAILED but no parseable info")
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Skip.class, result);
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
                collected 3 items
                test_solution.py ...                                                     [100%]
                ============================== 3 passed in 0.01s ===============================
                """;

            EvaluationContext context = EvaluationContext.builder()
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output)
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Pass.class, result);
        }
    }

    @Nested
    @DisplayName("Strategy Generation")
    class StrategyGenerationTests {
        @Test
        @DisplayName("Genera estrategia para OFF_BY_ONE")
        void evaluate_generatesStrategy_forOffByOne() {
            String output = "FAILED test_solution.py::test_count - AssertionError: assert 5 == 4";

            EvaluationContext context = EvaluationContext.forTestRetry("count items", output, 1);

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.evidence().isPresent());
            String evidence = fail.evidence().get();

            assertTrue(evidence.contains("OFF_BY_ONE"), "Debe mencionar patron");
            assertTrue(evidence.contains("Expected") || evidence.contains("expected"),
                "Debe mostrar expected");
            assertTrue(evidence.contains("rango") || evidence.contains("indices"),
                "Debe dar sugerencia de rango/indices");
        }

        @Test
        @DisplayName("Genera estrategia para EMPTY_RESULT")
        void evaluate_generatesStrategy_forEmptyResult() {
            String output = "FAILED test_solution.py::test_get - AssertionError: assert [1, 2] == None";

            EvaluationContext context = EvaluationContext.forTestRetry("get items", output, 1);

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.evidence().isPresent());
            String evidence = fail.evidence().get();

            assertTrue(evidence.contains("EMPTY_RESULT"), "Debe mencionar patron");
            assertTrue(evidence.contains("return"), "Debe sugerir verificar return");
        }

        @Test
        @DisplayName("Genera estrategia para LOGIC_INVERSION")
        void evaluate_generatesStrategy_forLogicInversion() {
            String output = "FAILED test_solution.py::test_valid - AssertionError: assert True == False";

            EvaluationContext context = EvaluationContext.forTestRetry("validate", output, 1);

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.evidence().isPresent());
            String evidence = fail.evidence().get();

            assertTrue(evidence.contains("LOGIC_INVERSION"), "Debe mencionar patron");
            assertTrue(evidence.contains("and") || evidence.contains("or") || evidence.contains("condicion"),
                "Debe sugerir revisar condiciones");
        }

        @Test
        @DisplayName("Genera estrategia para INDEX_ERROR")
        void evaluate_generatesStrategy_forIndexError() {
            String output = "FAILED test_solution.py::test_get - IndexError: list index out of range";

            EvaluationContext context = EvaluationContext.forTestRetry("get item", output, 1);

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.evidence().isPresent());
            String evidence = fail.evidence().get();

            assertTrue(evidence.contains("INDEX_ERROR"), "Debe mencionar patron");
            assertTrue(evidence.contains("len") || evidence.contains("indice"),
                "Debe sugerir verificar indices");
        }

        @Test
        @DisplayName("Genera estrategia para TYPE_MISMATCH")
        void evaluate_generatesStrategy_forTypeMismatch() {
            String output = "FAILED test_solution.py::test_add - TypeError: unsupported operand";

            EvaluationContext context = EvaluationContext.forTestRetry("add", output, 1);

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.evidence().isPresent());
            String evidence = fail.evidence().get();

            assertTrue(evidence.contains("TYPE_MISMATCH"), "Debe mencionar patron");
            assertTrue(evidence.contains("tipo") || evidence.contains("conversion"),
                "Debe sugerir verificar tipos");
        }
    }

    @Nested
    @DisplayName("Code Examples")
    class CodeExamplesTests {
        @Test
        @DisplayName("Incluye ejemplos de codigo por defecto")
        void evaluate_includesCodeExamples_byDefault() {
            String output = "FAILED test_solution.py::test_add - AssertionError: assert 5 == 4";

            EvaluationContext context = EvaluationContext.forTestRetry("add", output, 1);

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.evidence().isPresent());
            String evidence = fail.evidence().get();

            assertTrue(evidence.contains("```python") || evidence.contains("```"),
                "Debe incluir bloques de codigo");
        }

        @Test
        @DisplayName("Sin ejemplos cuando se configura withCodeExamples(false)")
        void evaluate_noCodeExamples_whenDisabled() {
            DiffStrategyCritic noExamples = critic.withCodeExamples(false);

            String output = "FAILED test_solution.py::test_add - AssertionError: assert 5 == 4";

            EvaluationContext context = EvaluationContext.forTestRetry("add", output, 1);

            Evaluation result = noExamples.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.evidence().isPresent());
            String evidence = fail.evidence().get();

            assertNotNull(evidence);
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {
        @Test
        @DisplayName("withMaxStrategies limita estrategias")
        void withMaxStrategies_limitsOutput() {
            DiffStrategyCritic limited = critic.withMaxStrategies(1);

            String output = """
                FAILED test_solution.py::test_a - AssertionError: assert 5 == 4
                FAILED test_solution.py::test_b - AssertionError: assert 10 == 9
                FAILED test_solution.py::test_c - AssertionError: assert 100 == 99
                """;

            EvaluationContext context = EvaluationContext.forTestRetry("tests", output, 1);

            Evaluation result = limited.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.evidence().isPresent());
            String evidence = fail.evidence().get();

            assertTrue(evidence.contains("adicionales") || evidence.contains("..."),
                "Debe indicar que hay mas failures");
        }

        @Test
        @DisplayName("withCodeExamples crea nueva instancia")
        void withCodeExamples_createsNewInstance() {
            DiffStrategyCritic configured = critic.withCodeExamples(false);

            assertNotSame(critic, configured);
        }

        @Test
        @DisplayName("withMaxStrategies crea nueva instancia")
        void withMaxStrategies_createsNewInstance() {
            DiffStrategyCritic configured = critic.withMaxStrategies(5);

            assertNotSame(critic, configured);
        }
    }

    @Nested
    @DisplayName("Critic Interface")
    class CriticInterfaceTests {
        @Test
        @DisplayName("getName retorna DiffStrategyCritic")
        void getName_returnsCorrectName() {
            assertEquals("DiffStrategyCritic", critic.getName());
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
        @DisplayName("evaluate lanza NPE para null response")
        void evaluate_throwsNPE_forNullResponse() {
            assertThrows(NullPointerException.class,
                () -> critic.evaluate(null, EvaluationContext.empty()));
        }

        @Test
        @DisplayName("evaluate lanza NPE para null context")
        void evaluate_throwsNPE_forNullContext() {
            assertThrows(NullPointerException.class,
                () -> critic.evaluate("code", null));
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethodTests {
        @Test
        @DisplayName("generateStrategy genera estrategia para TestFailure")
        void generateStrategy_generatesForTestFailure() {
            TestFailure failure = TestFailure.ofComparison("test_add", "AssertionError", "5", "4");

            String strategy = critic.generateStrategy(failure);

            assertFalse(strategy.isBlank());
            assertTrue(strategy.contains("test_add"));
            assertTrue(strategy.contains("OFF_BY_ONE"));
        }

        @Test
        @DisplayName("generateStrategiesFromOutput genera para output valido")
        void generateStrategiesFromOutput_generatesForValidOutput() {
            String output = "FAILED test_solution.py::test_add - AssertionError: assert 5 == 4";

            Optional<String> strategies = critic.generateStrategiesFromOutput(output);

            assertTrue(strategies.isPresent());
            assertTrue(strategies.get().contains("Estrategia"));
        }

        @Test
        @DisplayName("generateStrategiesFromOutput retorna empty para output sin failures")
        void generateStrategiesFromOutput_returnsEmpty_forNoFailures() {
            Optional<String> strategies = critic.generateStrategiesFromOutput("all tests passed");

            assertTrue(strategies.isEmpty());
        }

        @Test
        @DisplayName("generateStrategiesFromOutput retorna empty para null")
        void generateStrategiesFromOutput_returnsEmpty_forNull() {
            Optional<String> strategies = critic.generateStrategiesFromOutput(null);

            assertTrue(strategies.isEmpty());
        }
    }

    @Nested
    @DisplayName("Real World Examples")
    class RealWorldExampleTests {
        @Test
        @DisplayName("Maneja output complejo de pytest")
        void evaluate_handlesComplexOutput() {
            String output = """
                ============================= test session starts ==============================
                collected 4 items

                test_solution.py::test_add FAILED
                test_solution.py::test_empty FAILED
                test_solution.py::test_valid PASSED
                test_solution.py::test_edge FAILED

                =================================== FAILURES ===================================
                FAILED test_solution.py::test_add - AssertionError: assert 4 == 5
                FAILED test_solution.py::test_empty - AssertionError: assert [1, 2] == None
                FAILED test_solution.py::test_edge - IndexError: list index out of range
                ========================= 3 failed, 1 passed in 0.02s =========================
                """;

            EvaluationContext context = EvaluationContext.forTestRetry("implement", output, 1);

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;

            assertTrue(fail.evidence().isPresent());
            String evidence = fail.evidence().get();

            assertTrue(evidence.contains("Estrategia") || evidence.contains("estrategia"),
                "Debe contener estrategias");
            boolean hasPattern = evidence.contains("OFF_BY_ONE") ||
                                 evidence.contains("EMPTY_RESULT") ||
                                 evidence.contains("INDEX_ERROR") ||
                                 evidence.contains("UNKNOWN");
            assertTrue(hasPattern, "Debe mencionar al menos un patron: " + evidence.substring(0, Math.min(500, evidence.length())));
        }
    }
}
