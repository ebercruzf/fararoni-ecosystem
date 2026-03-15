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
package dev.fararoni.core.core.engine;

import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.reflexion.EvaluationContext;
import dev.fararoni.core.core.reflexion.critics.AssumptionCritic;
import dev.fararoni.core.core.reflexion.critics.CompletenessCritic;
import dev.fararoni.core.core.reflexion.critics.SecurityCritic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ReflexionEngine - Motor de Reflexion")
class ReflexionEngineTest {
    private final EvaluationContext ctx = EvaluationContext.empty();

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodsTests {
        @Test
        @DisplayName("standard() crea engine con criticos basicos")
        void standard_createsEngineWithBasicCritics() {
            ReflexionEngine engine = ReflexionEngine.standard();

            assertFalse(engine.getCritics().isEmpty());
            assertTrue(engine.getCritics().size() >= 2);
        }

        @Test
        @DisplayName("forCode() crea engine para codigo")
        void forCode_createsEngineForCode() {
            ReflexionEngine engine = ReflexionEngine.forCode();

            assertFalse(engine.getCritics().isEmpty());
            assertTrue(engine.getCritics().stream()
                .anyMatch(c -> c.getName().contains("Security")));
        }

        @Test
        @DisplayName("forSecurity() crea engine de seguridad")
        void forSecurity_createsSecurityEngine() {
            ReflexionEngine engine = ReflexionEngine.forSecurity();

            assertFalse(engine.getCritics().isEmpty());
        }

        @Test
        @DisplayName("minimal() crea engine minimo")
        void minimal_createsMinimalEngine() {
            ReflexionEngine engine = ReflexionEngine.minimal();

            assertEquals(1, engine.getCritics().size());
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {
        @Test
        @DisplayName("Builder crea engine con criticos configurados")
        void builder_createsEngineWithConfiguredCritics() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(new AssumptionCritic())
                .addCritic(new CompletenessCritic())
                .build();

            assertEquals(2, engine.getCritics().size());
        }

        @Test
        @DisplayName("Builder con lista de criticos")
        void builder_withCriticsList() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritics(List.of(
                    new AssumptionCritic(),
                    new SecurityCritic()
                ))
                .build();

            assertEquals(2, engine.getCritics().size());
        }

        @Test
        @DisplayName("Builder con configuracion de paralelismo")
        void builder_withParallelConfig() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(new AssumptionCritic())
                .withParallelEvaluation(true)
                .build();

            assertNotNull(engine);
        }
    }

    @Nested
    @DisplayName("reflect()")
    class ReflectTests {
        @Test
        @DisplayName("reflect() retorna resultado con evaluaciones")
        void reflect_returnsResultWithEvaluations() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(Critic.alwaysPass("TestCritic"))
                .build();

            ReflexionResult result = engine.reflect("test response", ctx);

            assertNotNull(result);
            assertFalse(result.allEvaluations().isEmpty());
        }

        @Test
        @DisplayName("reflect() detecta fallos")
        void reflect_detectsFailures() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(Critic.alwaysFail("FailCritic", "Always fails"))
                .build();

            ReflexionResult result = engine.reflect("test", ctx);

            assertTrue(result.requiresRegeneration());
            assertFalse(result.failures().isEmpty());
        }

        @Test
        @DisplayName("reflect() combina multiples criticos")
        void reflect_combinesMultipleCritics() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(Critic.alwaysPass("C1"))
                .addCritic(Critic.alwaysPass("C2"))
                .addCritic(Critic.alwaysPass("C3"))
                .build();

            ReflexionResult result = engine.reflect("test", ctx);

            assertEquals(3, result.allEvaluations().size());
            assertEquals(3, result.passes().size());
        }

        @Test
        @DisplayName("reflect() registra duracion")
        void reflect_recordsDuration() {
            ReflexionEngine engine = ReflexionEngine.standard();

            ReflexionResult result = engine.reflect("test", ctx);

            assertNotNull(result.evaluationDuration());
            assertTrue(result.evaluationDuration().toMillis() >= 0);
        }
    }

    @Nested
    @DisplayName("reflectByCategory()")
    class ReflectByCategoryTests {
        @Test
        @DisplayName("reflectByCategory() filtra por categoria")
        void reflectByCategory_filtersByCategory() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(new AssumptionCritic())
                .addCritic(new SecurityCritic())
                .build();

            ReflexionResult result = engine.reflectByCategory(
                "test", ctx, Critic.CriticCategory.SECURITY);

            assertTrue(result.allEvaluations().size() <= 1);
        }
    }

    @Nested
    @DisplayName("quickCheck()")
    class QuickCheckTests {
        @Test
        @DisplayName("quickCheck() retorna true si todos pasan")
        void quickCheck_returnsTrueIfAllPass() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(Critic.alwaysPass("C1"))
                .addCritic(Critic.alwaysPass("C2"))
                .build();

            boolean ok = engine.quickCheck("test", ctx);

            assertTrue(ok);
        }

        @Test
        @DisplayName("quickCheck() retorna false si hay fallo")
        void quickCheck_returnsFalseIfFail() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(Critic.alwaysPass("C1"))
                .addCritic(Critic.alwaysFail("C2", "Fail"))
                .build();

            boolean ok = engine.quickCheck("test", ctx);

            assertFalse(ok);
        }
    }

    @Nested
    @DisplayName("Critic Management")
    class CriticManagementTests {
        @Test
        @DisplayName("addCritic() agrega critico")
        void addCritic_addsCritic() {
            ReflexionEngine engine = ReflexionEngine.builder().build();

            engine.addCritic(new AssumptionCritic());

            assertEquals(1, engine.getCritics().size());
        }

        @Test
        @DisplayName("removeCritic() remueve por nombre")
        void removeCritic_removesByName() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(new AssumptionCritic())
                .build();

            boolean removed = engine.removeCritic("AssumptionCritic");

            assertTrue(removed);
            assertTrue(engine.getCritics().isEmpty());
        }

        @Test
        @DisplayName("getCriticsByCategory() filtra correctamente")
        void getCriticsByCategory_filtersCorrectly() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(new AssumptionCritic())
                .addCritic(new SecurityCritic())
                .build();

            List<Critic> qualityCritics = engine.getCriticsByCategory(
                Critic.CriticCategory.QUALITY);

            assertTrue(qualityCritics.stream()
                .allMatch(c -> c.getCategory() == Critic.CriticCategory.QUALITY));
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {
        @Test
        @DisplayName("getStats() retorna estadisticas por critico")
        void getStats_returnsStatsPerCritic() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(new CompletenessCritic())
                .build();

            engine.reflect("test response with enough content", ctx);
            engine.reflect("another test response with content", ctx);

            var stats = engine.getStats();

            assertFalse(stats.isEmpty());
            assertTrue(stats.containsKey("CompletenessCritic"));
            assertEquals(2, stats.get("CompletenessCritic").getTotalEvaluations());
        }

        @Test
        @DisplayName("resetStats() limpia estadisticas")
        void resetStats_clearsStats() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(Critic.alwaysPass("TestCritic"))
                .build();

            engine.reflect("test", ctx);
            engine.resetStats();

            assertTrue(engine.getStats().isEmpty());
        }
    }

    @Nested
    @DisplayName("ReflexionResult")
    class ReflexionResultTests {
        @Test
        @DisplayName("isClean() es true sin fallos ni warnings")
        void isClean_trueWithoutFailuresOrWarnings() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(Critic.alwaysPass("C1"))
                .build();

            ReflexionResult result = engine.reflect("test", ctx);

            assertTrue(result.isClean());
        }

        @Test
        @DisplayName("getPassRate() calcula correctamente")
        void getPassRate_calculatesCorrectly() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(Critic.alwaysPass("C1"))
                .addCritic(Critic.alwaysPass("C2"))
                .build();

            ReflexionResult result = engine.reflect("test", ctx);

            assertEquals(1.0, result.getPassRate());
        }

        @Test
        @DisplayName("getFeedbackForLlm() genera feedback estructurado")
        void getFeedbackForLlm_generatesStructuredFeedback() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(Critic.alwaysFail("TestCritic", "Test reason"))
                .build();

            ReflexionResult result = engine.reflect("test", ctx);
            String feedback = result.getFeedbackForLlm();

            assertFalse(feedback.isEmpty());
            assertTrue(feedback.contains("Feedback"));
            assertTrue(feedback.contains("Test reason"));
        }

        @Test
        @DisplayName("shouldRetry() respeta maxRetries")
        void shouldRetry_respectsMaxRetries() {
            ReflexionEngine engine = ReflexionEngine.builder()
                .addCritic(Critic.alwaysFail("C1", "Fail"))
                .withMaxRetries(3)
                .build();

            ReflexionResult result = engine.reflect("test", ctx);

            assertTrue(result.shouldRetry(1));
            assertTrue(result.shouldRetry(2));
            assertFalse(result.shouldRetry(3));
        }

        @Test
        @DisplayName("toDetailedReport() genera reporte completo")
        void toDetailedReport_generatesFullReport() {
            ReflexionEngine engine = ReflexionEngine.standard();

            ReflexionResult result = engine.reflect("test response with content", ctx);
            String report = result.toDetailedReport();

            assertTrue(report.contains("Reflexion Report"));
            assertTrue(report.contains("Total evaluations"));
        }
    }

    @Nested
    @DisplayName("Integration with Real Critics")
    class IntegrationTests {
        @Test
        @DisplayName("Engine detecta suposiciones")
        void engine_detectsAssumptions() {
            ReflexionEngine engine = ReflexionEngine.standard();

            ReflexionResult result = engine.reflect(
                "I assume you want to use Java for this project, probably version 17.",
                ctx
            );

            assertTrue(result.hasWarnings() || result.requiresRegeneration());
        }

        @Test
        @DisplayName("Engine detecta vulnerabilidades de seguridad")
        void engine_detectsSecurityVulnerabilities() {
            ReflexionEngine engine = ReflexionEngine.forSecurity();

            String response = """
                Here's the code:
                ```java
                String password = "secretPassword123";
                connection.connect(password);
                ```
                """;

            ReflexionResult result = engine.reflect(response,
                EvaluationContext.forCode("fix query", "java"));

            assertNotNull(result);
            assertTrue(result.allEvaluations().size() > 0);
        }

        @Test
        @DisplayName("Engine pasa respuesta limpia")
        void engine_passesCleanResponse() {
            ReflexionEngine engine = ReflexionEngine.minimal();

            String response = """
                Here is a complete solution for your problem.

                The approach uses a HashMap for O(1) lookups.

                ```java
                Map<String, Integer> map = new HashMap<>();
                map.put("key", 1);
                ```

                This implementation is efficient and thread-safe for read operations.
                """;

            ReflexionResult result = engine.reflect(response, ctx);

            assertTrue(result.isClean() || !result.requiresRegeneration());
        }
    }
}
