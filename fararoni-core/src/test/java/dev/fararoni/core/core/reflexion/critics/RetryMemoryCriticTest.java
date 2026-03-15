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
import dev.fararoni.core.core.reflexion.memory.AttemptMemory;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("RetryMemoryCritic - Deteccion de Repeticiones")
class RetryMemoryCriticTest {
    private AttemptMemory memory;
    private RetryMemoryCritic critic;

    @BeforeEach
    void setUp() {
        memory = new AttemptMemory();
        critic = new RetryMemoryCritic(memory);
    }

    @Nested
    @DisplayName("First Attempt")
    class FirstAttemptTests {
        @Test
        @DisplayName("Pass en primer intento")
        void evaluate_passesOnFirstAttempt() {
            String output = "FAILED test_solution.py::test_add - AssertionError: assert 5 == 4";

            EvaluationContext context = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 1)
                .build();

            Evaluation result = critic.evaluate("def add(a, b): return a", context);

            assertInstanceOf(Evaluation.Pass.class, result);
        }

        @Test
        @DisplayName("Registra intento en memoria")
        void evaluate_recordsAttemptInMemory() {
            EvaluationContext context = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 1)
                .build();

            critic.evaluate("def add(a, b): return a", context);

            assertTrue(memory.hasAttempts("exercise-" + Math.abs("implement add".hashCode())));
        }
    }

    @Nested
    @DisplayName("Repetition Detection")
    class RepetitionDetectionTests {
        @Test
        @DisplayName("Warning cuando codigo es identico")
        void evaluate_warnsOnIdenticalCode() {
            String code = "def add(a, b): return a";
            String output = "FAILED test_solution.py::test_add - AssertionError: assert 5 == 4";

            EvaluationContext ctx1 = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 1)
                .build();
            critic.evaluate(code, ctx1);

            EvaluationContext ctx2 = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 2)
                .build();
            Evaluation result = critic.evaluate(code, ctx2);

            assertInstanceOf(Evaluation.Warning.class, result);
            Evaluation.Warning warning = (Evaluation.Warning) result;
            assertFalse(warning.issues().isEmpty());
            String allIssues = String.join(" ", warning.issues());
            assertTrue(allIssues.contains("IDENTICO") || allIssues.contains("identico"),
                "Debe mencionar codigo identico: " + allIssues);
        }

        @Test
        @DisplayName("Warning cuando patron se repite")
        void evaluate_warnsOnRepeatedPattern() {
            String output1 = "FAILED test_solution.py::test_a - AssertionError: assert 5 == 4";
            String output2 = "FAILED test_solution.py::test_b - AssertionError: assert 10 == 9";

            EvaluationContext ctx1 = EvaluationContext.builder()
                .userPrompt("implement count")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output1)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 1)
                .build();
            critic.evaluate("code1", ctx1);

            EvaluationContext ctx2 = EvaluationContext.builder()
                .userPrompt("implement count")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output2)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 2)
                .build();
            Evaluation result = critic.evaluate("code2", ctx2);

            assertInstanceOf(Evaluation.Warning.class, result);
            Evaluation.Warning warning = (Evaluation.Warning) result;
            assertFalse(warning.issues().isEmpty());
            String allIssues = String.join(" ", warning.issues());
            assertTrue(allIssues.contains("OFF_BY_ONE"),
                "Debe mencionar patron repetido: " + allIssues);
        }

        @Test
        @DisplayName("Pass cuando codigo es diferente y patron cambia")
        void evaluate_passesWhenCodeAndPatternChange() {
            String output1 = "FAILED test_solution.py::test_a - AssertionError: assert 5 == 4";
            String output2 = "FAILED test_solution.py::test_b - TypeError: unsupported operand";

            EvaluationContext ctx1 = EvaluationContext.builder()
                .userPrompt("implement func")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output1)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 1)
                .build();
            critic.evaluate("code1", ctx1);

            EvaluationContext ctx2 = EvaluationContext.builder()
                .userPrompt("implement func")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output2)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 2)
                .build();
            Evaluation result = critic.evaluate("code2", ctx2);

            assertInstanceOf(Evaluation.Pass.class, result);
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {
        @Test
        @DisplayName("withBlockOnIdenticalCode retorna Fail en lugar de Warning")
        void withBlockOnIdenticalCode_returnsFail() {
            RetryMemoryCritic strictCritic = critic.withBlockOnIdenticalCode(true);

            String code = "def add(a, b): return a";
            String output = "FAILED test_solution.py::test_add - AssertionError: assert 5 == 4";

            EvaluationContext ctx1 = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 1)
                .build();
            strictCritic.evaluate(code, ctx1);

            EvaluationContext ctx2 = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 2)
                .build();
            Evaluation result = strictCritic.evaluate(code, ctx2);

            assertInstanceOf(Evaluation.Fail.class, result);
        }

        @Test
        @DisplayName("withBlockOnIdenticalCode crea nueva instancia")
        void withBlockOnIdenticalCode_createsNewInstance() {
            RetryMemoryCritic configured = critic.withBlockOnIdenticalCode(true);

            assertNotSame(critic, configured);
        }
    }

    @Nested
    @DisplayName("Exercise ID")
    class ExerciseIdTests {
        @Test
        @DisplayName("Usa exerciseId de metadata si existe")
        void evaluate_usesExerciseIdFromMetadata() {
            EvaluationContext context = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(RetryMemoryCritic.KEY_EXERCISE_ID, "custom-exercise-123")
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 1)
                .build();

            critic.evaluate("code", context);

            assertTrue(memory.hasAttempts("custom-exercise-123"));
        }

        @Test
        @DisplayName("Usa hash de prompt si no hay exerciseId")
        void evaluate_usesPromptHashWithoutExerciseId() {
            EvaluationContext context = EvaluationContext.builder()
                .userPrompt("implement add function")
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 1)
                .build();

            critic.evaluate("code", context);

            String expectedId = "exercise-" + Math.abs("implement add function".hashCode());
            assertTrue(memory.hasAttempts(expectedId));
        }
    }

    @Nested
    @DisplayName("Critic Interface")
    class CriticInterfaceTests {
        @Test
        @DisplayName("getName retorna RetryMemoryCritic")
        void getName_returnsCorrectName() {
            assertEquals("RetryMemoryCritic", critic.getName());
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
        @DisplayName("getMemory retorna memoria compartida")
        void getMemory_returnsSharedMemory() {
            assertSame(memory, critic.getMemory());
        }

        @Test
        @DisplayName("hasRepetitions detecta repeticiones")
        void hasRepetitions_detectsRepetitions() {
            String code = "def add(a, b): return a";
            String output = "FAILED test_solution.py::test_add - AssertionError: assert 5 == 4";

            EvaluationContext ctx = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 1)
                .build();
            critic.evaluate(code, ctx);

            ctx = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, output)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 2)
                .build();
            critic.evaluate(code, ctx);

            String exerciseId = "exercise-" + Math.abs("implement add".hashCode());
            assertTrue(critic.hasRepetitions(exerciseId));
        }

        @Test
        @DisplayName("getSuggestion retorna sugerencia para repeticiones")
        void getSuggestion_returnsSuggestionForRepetitions() {
            String code = "def add(a, b): return a";

            EvaluationContext ctx = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 1)
                .build();
            critic.evaluate(code, ctx);

            ctx = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 2)
                .build();
            critic.evaluate(code, ctx);

            String exerciseId = "exercise-" + Math.abs("implement add".hashCode());
            Optional<String> suggestion = critic.getSuggestion(exerciseId);

            assertTrue(suggestion.isPresent());
        }
    }
}
