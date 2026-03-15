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
package dev.fararoni.core.core.reflexion;

import dev.fararoni.core.core.reflexion.critics.*;
import dev.fararoni.core.core.reflexion.critics.DiffStrategyCritic;
import dev.fararoni.core.core.reflexion.critics.FailurePatternCritic;
import dev.fararoni.core.core.reflexion.critics.RetryMemoryCritic;
import dev.fararoni.core.core.reflexion.critics.TestOutputCritic;
import dev.fararoni.core.core.reflexion.memory.AttemptMemory;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ReflexionEngine - Motor de Reflexion")
class ReflexionEngineTest {
    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {
        @Test
        @DisplayName("forTestCorrection crea engine con todos los critics")
        void forTestCorrection_createsEngineWithAllCritics() {
            ReflexionEngine engine = ReflexionEngine.forTestCorrection();

            List<Critic> critics = engine.getCritics();

            assertEquals(4, critics.size());
            assertTrue(critics.get(0) instanceof TestOutputCritic);
            assertTrue(critics.get(1) instanceof FailurePatternCritic);
            assertTrue(critics.get(2) instanceof DiffStrategyCritic);
            assertTrue(critics.get(3) instanceof RetryMemoryCritic);
        }

        @Test
        @DisplayName("minimal crea engine con solo TestOutputCritic")
        void minimal_createsMinimalEngine() {
            ReflexionEngine engine = ReflexionEngine.minimal();

            List<Critic> critics = engine.getCritics();

            assertEquals(1, critics.size());
            assertTrue(critics.get(0) instanceof TestOutputCritic);
        }

        @Test
        @DisplayName("forTestCorrection tiene memoria compartida")
        void forTestCorrection_hasSharedMemory() {
            ReflexionEngine engine = ReflexionEngine.forTestCorrection();

            assertNotNull(engine.getMemory());
        }
    }

    @Nested
    @DisplayName("Reflect Method")
    class ReflectMethodTests {
        private ReflexionEngine engine;

        @BeforeEach
        void setUp() {
            engine = ReflexionEngine.forTestCorrection();
        }

        @Test
        @DisplayName("reflect lanza NPE para null response")
        void reflect_throwsNPE_forNullResponse() {
            EvaluationContext ctx = EvaluationContext.empty();

            assertThrows(NullPointerException.class, () -> engine.reflect(null, ctx));
        }

        @Test
        @DisplayName("reflect lanza NPE para null context")
        void reflect_throwsNPE_forNullContext() {
            assertThrows(NullPointerException.class, () -> engine.reflect("code", null));
        }

        @Test
        @DisplayName("reflect retorna resultado con evaluaciones")
        void reflect_returnsResultWithEvaluations() {
            String testOutput = "FAILED test_solution.py::test_add - AssertionError: assert 5 == 4";
            EvaluationContext ctx = EvaluationContext.forTestRetry("implement add", testOutput, 1);

            ReflexionEngine.ReflexionResult result = engine.reflect("def add(a, b): return a", ctx);

            assertNotNull(result);
            assertFalse(result.getEvaluations().isEmpty());
        }

        @Test
        @DisplayName("reflect detecta failures")
        void reflect_detectsFailures() {
            String testOutput = "FAILED test_solution.py::test_add - AssertionError: assert 5 == 4";
            EvaluationContext ctx = EvaluationContext.forTestRetry("implement add", testOutput, 1);

            ReflexionEngine.ReflexionResult result = engine.reflect("def add(a, b): return a", ctx);

            assertTrue(result.needsCorrection());
        }

        @Test
        @DisplayName("reflect detecta tests pasando")
        void reflect_detectsPassingTests() {
            String testOutput = "============================= 1 passed in 0.12s ==============================";
            EvaluationContext ctx = EvaluationContext.forTestRetry("implement add", testOutput, 1);

            ReflexionEngine.ReflexionResult result = engine.reflect("def add(a, b): return a + b", ctx);

            assertFalse(result.needsCorrection());
        }
    }

    @Nested
    @DisplayName("ReflectWithTestOutput Method")
    class ReflectWithTestOutputTests {
        private ReflexionEngine engine;

        @BeforeEach
        void setUp() {
            engine = ReflexionEngine.forTestCorrection();
        }

        @Test
        @DisplayName("reflectWithTestOutput es convenience method")
        void reflectWithTestOutput_isConvenienceMethod() {
            String testOutput = "FAILED test_solution.py::test_add - AssertionError";

            ReflexionEngine.ReflexionResult result = engine.reflectWithTestOutput(
                "def add(a, b): return a",
                "implement add",
                testOutput,
                1
            );

            assertNotNull(result);
            assertTrue(result.needsCorrection());
        }
    }

    @Nested
    @DisplayName("ReflexionResult")
    class ReflexionResultTests {
        private ReflexionEngine engine;

        @BeforeEach
        void setUp() {
            engine = ReflexionEngine.minimal();
        }

        @Test
        @DisplayName("needsCorrection retorna true cuando hay Fail")
        void needsCorrection_returnsTrueOnFail() {
            String testOutput = "FAILED test_solution.py::test_add - AssertionError";
            EvaluationContext ctx = EvaluationContext.forTestRetry("prompt", testOutput, 1);

            ReflexionEngine.ReflexionResult result = engine.reflect("code", ctx);

            assertTrue(result.needsCorrection());
        }

        @Test
        @DisplayName("isSuccess retorna true cuando no hay fails ni warnings")
        void isSuccess_returnsTrueWithoutFailsOrWarnings() {
            String testOutput = "============================= 1 passed in 0.12s ==============================";
            EvaluationContext ctx = EvaluationContext.forTestRetry("prompt", testOutput, 1);

            ReflexionEngine.ReflexionResult result = engine.reflect("code", ctx);

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("getFailures retorna solo Fail evaluations")
        void getFailures_returnsOnlyFails() {
            String testOutput = "FAILED test_solution.py::test_add - AssertionError";
            EvaluationContext ctx = EvaluationContext.forTestRetry("prompt", testOutput, 1);

            ReflexionEngine.ReflexionResult result = engine.reflect("code", ctx);

            assertFalse(result.getFailures().isEmpty());
            assertTrue(result.getFailures().stream()
                .allMatch(f -> f instanceof Evaluation.Fail));
        }

        @Test
        @DisplayName("getFormattedFeedback retorna markdown (formato hibrido v2.1)")
        void getFormattedFeedback_returnsMarkdown() {
            String testOutput = "FAILED test_solution.py::test_add - AssertionError";
            EvaluationContext ctx = EvaluationContext.forTestRetry("prompt", testOutput, 1);

            ReflexionEngine.ReflexionResult result = engine.reflect("code", ctx);
            String feedback = result.getFormattedFeedback();

            assertTrue(feedback.contains("## CRITICAL FAILURE ANALYSIS:") ||
                       feedback.contains("## REQUIRED FIX:"));
        }

        @Test
        @DisplayName("getSummary retorna resumen corto")
        void getSummary_returnsShortSummary() {
            String testOutput = "FAILED test_solution.py::test_add - AssertionError";
            EvaluationContext ctx = EvaluationContext.forTestRetry("prompt", testOutput, 1);

            ReflexionEngine.ReflexionResult result = engine.reflect("code", ctx);
            String summary = result.getSummary();

            assertTrue(summary.contains("Reflexion:"));
            assertTrue(summary.contains("fails"));
            assertTrue(summary.contains("warnings"));
            assertTrue(summary.contains("passes"));
        }

        @Test
        @DisplayName("getWarnings retorna solo warnings")
        void getWarnings_returnsOnlyWarnings() {
            ReflexionEngine engineFull = ReflexionEngine.forTestCorrection();

            String testOutput = "FAILED test_solution.py::test_add - AssertionError: assert 5 == 4";
            EvaluationContext ctx1 = EvaluationContext.forTestRetry("implement add 123", testOutput, 1);
            engineFull.reflect("def add(a, b): return a", ctx1);

            EvaluationContext ctx2 = EvaluationContext.forTestRetry("implement add 123", testOutput, 2);
            ReflexionEngine.ReflexionResult result = engineFull.reflect("def add(a, b): return a", ctx2);

            List<Evaluation.Warning> warnings = result.getWarnings();
            assertTrue(warnings.stream().allMatch(w -> w instanceof Evaluation.Warning));
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {
        @Test
        @DisplayName("builder permite agregar critics custom")
        void builder_allowsCustomCritics() {
            Critic customCritic = new Critic() {
                @Override
                public Evaluation evaluate(String response, EvaluationContext context) {
                    return Evaluation.pass("CustomCritic", "OK");
                }
                @Override
                public String getName() { return "CustomCritic"; }
                @Override
                public String getDescription() { return "Test critic"; }
                @Override
                public CriticCategory getCategory() { return CriticCategory.CODE; }
            };

            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(customCritic)
                .build();

            assertEquals(1, engine.getCritics().size());
            assertEquals("CustomCritic", engine.getCritics().get(0).getName());
        }

        @Test
        @DisplayName("builder con withDefaultCritics agrega 4 critics")
        void builder_withDefaultCritics_adds4Critics() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .withDefaultCritics()
                .build();

            assertEquals(4, engine.getCritics().size());
        }

        @Test
        @DisplayName("builder lanza excepcion sin critics")
        void builder_throwsWithoutCritics() {
            ReflexionEngine.Builder builder = ReflexionEngine.builder();

            assertThrows(IllegalStateException.class, builder::build);
        }

        @Test
        @DisplayName("builder permite memoria custom")
        void builder_allowsCustomMemory() {
            AttemptMemory customMemory = new AttemptMemory();

            ReflexionEngine engine = ReflexionEngine.builder()
                .withMemory(customMemory)
                .withDefaultCritics()
                .build();

            assertSame(customMemory, engine.getMemory());
        }

        @Test
        @DisplayName("builder permite formatter custom")
        void builder_allowsCustomFormatter() {
            FeedbackFormatter customFormatter = new FeedbackFormatter(true, true, 500);

            ReflexionEngine engine = ReflexionEngine.builder()
                .withFormatter(customFormatter)
                .withDefaultCritics()
                .build();

            String testOutput = "============================= 1 passed in 0.12s ==============================";
            EvaluationContext ctx = EvaluationContext.forTestRetry("prompt", testOutput, 1);
            ReflexionEngine.ReflexionResult result = engine.reflect("code", ctx);

            String feedback = result.getFormattedFeedback();
            assertTrue(feedback.contains("Validaciones Exitosas") || !result.needsCorrection());
        }
    }

    @Nested
    @DisplayName("Memory Management")
    class MemoryManagementTests {
        @Test
        @DisplayName("clearMemory limpia la memoria")
        void clearMemory_clearsMemory() {
            ReflexionEngine engine = ReflexionEngine.forTestCorrection();

            String testOutput = "FAILED test_solution.py::test_add - AssertionError";
            EvaluationContext ctx = EvaluationContext.forTestRetry("test prompt", testOutput, 1);
            engine.reflect("code", ctx);

            engine.clearMemory();

            String exerciseId = "exercise-" + Math.abs("test prompt".hashCode());
            assertFalse(engine.getMemory().hasAttempts(exerciseId));
        }

        @Test
        @DisplayName("getMemory retorna memoria compartida")
        void getMemory_returnsSharedMemory() {
            ReflexionEngine engine = ReflexionEngine.forTestCorrection();

            AttemptMemory memory = engine.getMemory();

            assertNotNull(memory);
        }
    }

    @Nested
    @DisplayName("Critic Error Handling")
    class CriticErrorHandlingTests {
        @Test
        @DisplayName("captura errores de critics y continua")
        void capturesCriticErrors_andContinues() {
            Critic failingCritic = new Critic() {
                @Override
                public Evaluation evaluate(String response, EvaluationContext context) {
                    throw new RuntimeException("Critic exploto");
                }
                @Override
                public String getName() { return "FailingCritic"; }
                @Override
                public String getDescription() { return "Always fails"; }
                @Override
                public CriticCategory getCategory() { return CriticCategory.CODE; }
            };

            Critic workingCritic = new Critic() {
                @Override
                public Evaluation evaluate(String response, EvaluationContext context) {
                    return Evaluation.pass("WorkingCritic", "OK");
                }
                @Override
                public String getName() { return "WorkingCritic"; }
                @Override
                public String getDescription() { return "Always works"; }
                @Override
                public CriticCategory getCategory() { return CriticCategory.CODE; }
            };

            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(failingCritic)
                .addCritic(workingCritic)
                .build();

            EvaluationContext ctx = EvaluationContext.empty();
            ReflexionEngine.ReflexionResult result = engine.reflect("code", ctx);

            assertEquals(2, result.getEvaluations().size());

            boolean hasCriticErrorFail = result.getFailures().stream()
                .anyMatch(f -> f.reason().contains("Error ejecutando critic"));
            assertTrue(hasCriticErrorFail, "Debe haber un Fail por el error del critic");
        }
    }

    @Nested
    @DisplayName("Integration")
    class IntegrationTests {
        @Test
        @DisplayName("flujo completo de reflexion")
        void fullReflectionFlow() {
            ReflexionEngine engine = ReflexionEngine.forTestCorrection();

            String testOutput1 = """
                ============================= FAILURES =============================
                FAILED test_solution.py::test_add - AssertionError: assert 5 == 4
                =============================== 1 failed in 0.12s ===============================
                """;
            EvaluationContext ctx1 = EvaluationContext.forTestRetry(
                "implement add function",
                testOutput1,
                1
            );
            ReflexionEngine.ReflexionResult result1 = engine.reflect("def add(a, b): return a", ctx1);

            assertTrue(result1.needsCorrection());
            assertFalse(result1.getFailures().isEmpty());
            assertFalse(result1.getFormattedFeedback().isEmpty());

            String testOutput2 = "============================= 1 passed in 0.12s ==============================";
            EvaluationContext ctx2 = EvaluationContext.forTestRetry(
                "implement add function",
                testOutput2,
                2
            );
            ReflexionEngine.ReflexionResult result2 = engine.reflect("def add(a, b): return a + b", ctx2);

            assertFalse(result2.needsCorrection());
        }
    }
}
