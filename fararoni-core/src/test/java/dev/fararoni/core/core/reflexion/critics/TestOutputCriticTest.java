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

import java.util.List;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("TestOutputCritic - Critic para Output de Tests")
class TestOutputCriticTest {
    private TestOutputCritic critic;

    @BeforeEach
    void setUp() {
        critic = new TestOutputCritic();
    }

    @Nested
    @DisplayName("Skip Scenarios")
    class SkipTests {
        @Test
        @DisplayName("Skip cuando no hay testOutput en contexto")
        void evaluate_noTestOutput_skips() {
            EvaluationContext context = EvaluationContext.builder()
                .userPrompt("Implementa add")
                .build();

            Evaluation result = critic.evaluate("def add(a, b): return a + b", context);

            assertInstanceOf(Evaluation.Skip.class, result);
            assertEquals("TestOutputCritic", result.criticName());
        }

        @Test
        @DisplayName("Skip cuando testOutput esta vacio")
        void evaluate_emptyTestOutput_skips() {
            EvaluationContext context = EvaluationContext.builder()
                .userPrompt("Implementa add")
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, "")
                .build();

            Evaluation result = critic.evaluate("def add(a, b): return a + b", context);

            assertInstanceOf(Evaluation.Skip.class, result);
        }

        @Test
        @DisplayName("Skip cuando testOutput es solo espacios")
        void evaluate_blankTestOutput_skips() {
            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, "   \n   ")
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Skip.class, result);
        }
    }

    @Nested
    @DisplayName("Pass Scenarios")
    class PassTests {
        @Test
        @DisplayName("Pass cuando todos los tests pasan")
        void evaluate_allTestsPass_passes() {
            String testOutput = """
                PASSED test_add
                PASSED test_sub
                ===================== 2 passed =====================
                """;

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = critic.evaluate("def add(a, b): return a + b", context);

            assertInstanceOf(Evaluation.Pass.class, result);
            assertTrue(result.toSummary().contains("pasaron"));
        }

        @Test
        @DisplayName("Pass cuando output no contiene FAILED ni error")
        void evaluate_noFailedKeyword_passes() {
            String testOutput = "All tests completed successfully";

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Pass.class, result);
        }
    }

    @Nested
    @DisplayName("Fail Scenarios")
    class FailTests {
        @Test
        @DisplayName("Fail con un test fallido simple")
        void evaluate_singleFailure_fails() {
            String testOutput = "FAILED test_add - AssertionError: assert 5 == 4";

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = critic.evaluate("def add(a, b): return a - b", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            assertTrue(result.isBlocking());
            assertTrue(result.toSummary().contains("test_add"));
        }

        @Test
        @DisplayName("Fail con multiples tests fallidos")
        void evaluate_multipleFailures_fails() {
            String testOutput = """
                FAILED test_add - AssertionError: assert 5 == 4
                FAILED test_sub - AssertionError: assert 3 == 2
                FAILED test_mul - TypeError: invalid type
                """;

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;
            assertTrue(fail.reason().contains("3 tests fallaron"));
        }

        @Test
        @DisplayName("Fail incluye evidencia con detalles")
        void evaluate_failure_includesEvidence() {
            String testOutput = "FAILED test_add - AssertionError: assert 5 == 4";

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;
            assertTrue(fail.evidence().isPresent());
            assertTrue(fail.evidence().get().contains("test_add"));
        }

        @Test
        @DisplayName("Fail incluye sugerencia de correccion")
        void evaluate_failure_includesSuggestion() {
            String testOutput = "FAILED test_add - AssertionError: assert 5 == 4";

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;
            assertTrue(fail.suggestedFix().isPresent());
            assertTrue(fail.suggestedFix().get().contains("OFF_BY_ONE") ||
                       fail.suggestedFix().get().contains("Sugerencias"));
        }
    }

    @Nested
    @DisplayName("Pattern Detection")
    class PatternDetectionTests {
        @Test
        @DisplayName("Detecta patron OFF_BY_ONE y sugiere fix")
        void evaluate_offByOne_suggestsIndexCheck() {
            String testOutput = "FAILED test_count - AssertionError: assert 10 == 9";

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;
            assertTrue(fail.suggestedFix().orElse("").contains("OFF_BY_ONE"));
        }

        @Test
        @DisplayName("Detecta patron STRING_TYPO y sugiere fix")
        void evaluate_stringTypo_suggestsCharCheck() {
            String testOutput = "FAILED test_song - AssertionError: assert 'Take one down' == 'Take ane down'";

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;
            assertTrue(fail.suggestedFix().orElse("").contains("STRING_TYPO"));
        }

        @Test
        @DisplayName("Detecta patron EMPTY_RESULT")
        void evaluate_emptyResult_suggestsReturnCheck() {
            String testOutput = "FAILED test_list - AssertionError: assert [1, 2] == None";

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;
            assertTrue(fail.suggestedFix().orElse("").contains("EMPTY_RESULT"));
        }

        @Test
        @DisplayName("Detecta patron TYPE_ERROR")
        void evaluate_typeError_suggestsTypeCheck() {
            String testOutput = "FAILED test_add - TypeError: unsupported operand type";

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;
            assertTrue(fail.suggestedFix().orElse("").contains("TYPE_ERROR"));
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {
        @Test
        @DisplayName("withDetailedFeedback crea nueva instancia")
        void withDetailedFeedback_createsNewInstance() {
            TestOutputCritic detailed = critic.withDetailedFeedback(false);

            assertNotSame(critic, detailed);
        }

        @Test
        @DisplayName("withMaxFailures limita failures mostrados")
        void withMaxFailures_limitsOutput() {
            TestOutputCritic limited = critic.withMaxFailures(2);

            String testOutput = """
                FAILED test_1 - AssertionError: assert 1 == 0
                FAILED test_2 - AssertionError: assert 2 == 0
                FAILED test_3 - AssertionError: assert 3 == 0
                FAILED test_4 - AssertionError: assert 4 == 0
                FAILED test_5 - AssertionError: assert 5 == 0
                """;

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = limited.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;
            assertTrue(fail.evidence().orElse("").contains("... y 3 tests mas"));
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethodTests {
        @Test
        @DisplayName("parseFailures extrae TestFailure correctamente")
        void parseFailures_extractsFailures() {
            String testOutput = """
                FAILED test_add - AssertionError: assert 5 == 4
                FAILED test_sub - AssertionError: assert 3 == 2
                """;

            List<TestFailure> failures = critic.parseFailures(testOutput);

            assertEquals(2, failures.size());
            assertEquals("test_add", failures.get(0).testName());
            assertEquals("test_sub", failures.get(1).testName());
        }

        @Test
        @DisplayName("parseFailures retorna lista vacia para output sin fallos")
        void parseFailures_emptyForNoFailures() {
            List<TestFailure> failures = critic.parseFailures("All tests passed");

            assertTrue(failures.isEmpty());
        }

        @Test
        @DisplayName("parseFailures retorna lista vacia para null")
        void parseFailures_emptyForNull() {
            List<TestFailure> failures = critic.parseFailures(null);

            assertTrue(failures.isEmpty());
        }

        @Test
        @DisplayName("generateFeedback retorna feedback formateado")
        void generateFeedback_returnsFeedback() {
            String testOutput = "FAILED test_add - AssertionError: assert 5 == 4";

            Optional<String> feedback = critic.generateFeedback(testOutput);

            assertTrue(feedback.isPresent());
            assertTrue(feedback.get().contains("test_add"));
            assertTrue(feedback.get().contains("Sugerencias"));
        }

        @Test
        @DisplayName("generateFeedback retorna empty para output sin fallos")
        void generateFeedback_emptyForNoFailures() {
            Optional<String> feedback = critic.generateFeedback("All tests passed");

            assertTrue(feedback.isEmpty());
        }
    }

    @Nested
    @DisplayName("Critic Interface")
    class CriticInterfaceTests {
        @Test
        @DisplayName("getName retorna TestOutputCritic")
        void getName_returnsCorrectName() {
            assertEquals("TestOutputCritic", critic.getName());
        }

        @Test
        @DisplayName("getDescription retorna descripcion significativa")
        void getDescription_returnsMeaningfulDescription() {
            String desc = critic.getDescription();

            assertNotNull(desc);
            assertFalse(desc.isBlank());
            assertTrue(desc.contains("pytest") || desc.contains("feedback") || desc.contains("self-correction"));
        }

        @Test
        @DisplayName("getCategory retorna CODE")
        void getCategory_returnsCode() {
            assertEquals(Critic.CriticCategory.CODE, critic.getCategory());
        }

        @Test
        @DisplayName("evaluate lanza NullPointerException para response null")
        void evaluate_nullResponse_throwsNPE() {
            EvaluationContext context = EvaluationContext.empty();

            assertThrows(NullPointerException.class, () -> critic.evaluate(null, context));
        }

        @Test
        @DisplayName("evaluate lanza NullPointerException para context null")
        void evaluate_nullContext_throwsNPE() {
            assertThrows(NullPointerException.class, () -> critic.evaluate("code", null));
        }
    }

    @Nested
    @DisplayName("Real World Examples")
    class RealWorldExampleTests {
        @Test
        @DisplayName("Parse output real de beer-song benchmark")
        void evaluate_beerSongOutput_providesGoodFeedback() {
            String testOutput = """
                ../../../dev::BeerSongTest::test_all_verses FAILED [ 12%]
                E       AssertionError: assert '99 bottles of beer on the wall' == '99 bottles of beer'
                """;

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;
            assertTrue(fail.evidence().orElse("").contains("test_all_verses"));
        }

        @Test
        @DisplayName("Parse output con multiples formatos de assert")
        void evaluate_mixedAssertFormats_parsesAll() {
            String testOutput = """
                FAILED test_add - AssertionError: assert 5 == 4
                E       assert 10 == 9
                FAILED test_str - AssertionError: assert 'hello' == 'hallo'
                """;

            EvaluationContext context = EvaluationContext.builder()
                .metadata(TestOutputCritic.TEST_OUTPUT_KEY, testOutput)
                .build();

            Evaluation result = critic.evaluate("code", context);

            assertInstanceOf(Evaluation.Fail.class, result);
            Evaluation.Fail fail = (Evaluation.Fail) result;
            assertTrue(fail.reason().contains("2 tests fallaron"));
        }
    }
}
