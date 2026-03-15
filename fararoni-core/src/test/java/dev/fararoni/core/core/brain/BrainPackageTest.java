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
package dev.fararoni.core.core.brain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Brain Package - El Lobulo Frontal Tests")
class BrainPackageTest {
    @Nested
    @DisplayName("TokenMonitor Tests")
    class TokenMonitorTests {
        private TokenMonitor monitor;

        @BeforeEach
        void setUp() {
            monitor = new TokenMonitor();
        }

        @Test
        @DisplayName("Debe estimar tokens correctamente")
        void shouldEstimateTokens() {
            String text = "1234567890123456";

            int tokens = monitor.estimateTokens(text);

            assertEquals(4, tokens);
        }

        @Test
        @DisplayName("Debe retornar 0 tokens para texto vacio")
        void shouldReturnZeroForEmpty() {
            assertEquals(0, monitor.estimateTokens(null));
            assertEquals(0, monitor.estimateTokens(""));
        }

        @Test
        @DisplayName("Debe identificar prompts seguros")
        void shouldIdentifySafePrompts() {
            String smallPrompt = "x".repeat(1000);

            assertTrue(monitor.isSafe(smallPrompt));
        }

        @Test
        @DisplayName("Debe identificar prompts peligrosos")
        void shouldIdentifyUnsafePrompts() {
            String hugePrompt = "x".repeat(150000);

            assertFalse(monitor.isSafe(hugePrompt));
        }

        @Test
        @DisplayName("Debe podar texto preservando inicio y fin")
        void shouldTruncateSmartly() {
            String text = "INICIO" + "x".repeat(10000) + "FIN";

            String truncated = monitor.truncateSmart(text, 100);

            assertTrue(truncated.contains("INICIO"));
            assertTrue(truncated.contains("FIN"));
            assertTrue(truncated.contains("[SECCION PODADA"));
        }

        @Test
        @DisplayName("No debe podar texto corto")
        void shouldNotTruncateShortText() {
            String text = "Este es un texto corto";

            String result = monitor.truncateSmart(text, 1000);

            assertEquals(text, result);
        }

        @Test
        @DisplayName("Debe calcular presupuesto restante")
        void shouldCalculateRemainingBudget() {
            String prompt = "x".repeat(4000);

            int remaining = monitor.getRemainingBudget(prompt);

            assertTrue(remaining > 25000);
        }

        @Test
        @DisplayName("Debe rechazar limite muy bajo")
        void shouldRejectLowLimit() {
            assertThrows(IllegalArgumentException.class, () -> {
                new TokenMonitor(500);
            });
        }

        @Test
        @DisplayName("Debe generar reporte de presupuesto")
        void shouldGenerateBudgetReport() {
            String prompt = "Test prompt";

            String report = monitor.getBudgetReport(prompt);

            assertTrue(report.contains("TokenMonitor Budget Report"));
            assertTrue(report.contains("Tokens usados"));
            assertTrue(report.contains("OK"));
        }

        @Test
        @DisplayName("safeGuard debe ser mas estricto que isSafe")
        void safeGuardShouldBeMoreStrict() {
            String text = "x".repeat(10000);

            boolean normalSafe = monitor.isSafe(text);
            boolean conservativeSafe = monitor.safeGuard(3000, text);

            assertTrue(normalSafe);
            assertFalse(conservativeSafe);
        }

        @Test
        @DisplayName("Debe estimar tokens conservadoramente")
        void shouldEstimateConservatively() {
            String text = "x".repeat(1000);

            int normalEstimate = monitor.estimateTokens(text);
            int conservativeEstimate = monitor.estimateTokensConservative(text);

            assertTrue(conservativeEstimate > normalEstimate);
            assertEquals(250, normalEstimate);
            assertEquals(400, conservativeEstimate);
        }

        @Test
        @DisplayName("safeGuard debe aceptar texto vacio")
        void safeGuardShouldAcceptEmpty() {
            assertTrue(monitor.safeGuard(1000, null));
            assertTrue(monitor.safeGuard(1000, ""));
        }
    }

    @Nested
    @DisplayName("CognitiveFSM Tests")
    class CognitiveFSMTests {
        private CognitiveFSM fsm;

        @BeforeEach
        void setUp() {
            fsm = new CognitiveFSM(3);
        }

        @Test
        @DisplayName("Debe iniciar en estado IDLE")
        void shouldStartInIdle() {
            assertEquals(CognitiveFSM.State.IDLE, fsm.getCurrentState());
        }

        @Test
        @DisplayName("Debe transicionar IDLE -> GATHERING")
        void shouldTransitionToGathering() {
            fsm.transition(CognitiveFSM.State.GATHERING);

            assertEquals(CognitiveFSM.State.GATHERING, fsm.getCurrentState());
        }

        @Test
        @DisplayName("Debe seguir flujo completo hasta COMPLETED")
        void shouldCompleteFullFlow() {
            fsm.transition(CognitiveFSM.State.GATHERING);
            fsm.transition(CognitiveFSM.State.PLANNING);
            fsm.transition(CognitiveFSM.State.CODING);
            fsm.transition(CognitiveFSM.State.VERIFYING);
            fsm.transition(CognitiveFSM.State.COMPLETED);

            assertEquals(CognitiveFSM.State.COMPLETED, fsm.getCurrentState());
            assertFalse(fsm.canContinue());
        }

        @Test
        @DisplayName("Debe rechazar transiciones invalidas")
        void shouldRejectInvalidTransitions() {
            assertThrows(IllegalStateException.class, () -> {
                fsm.transition(CognitiveFSM.State.CODING);
            });
        }

        @Test
        @DisplayName("Debe contar intentos")
        void shouldCountAttempts() {
            fsm.recordAttempt();
            fsm.recordAttempt();

            assertEquals(2, fsm.getAttemptCount());
        }

        @Test
        @DisplayName("Debe fallar despues de max intentos")
        void shouldFailAfterMaxAttempts() {
            fsm.recordAttempt();
            fsm.recordAttempt();
            fsm.recordAttempt();

            assertEquals(CognitiveFSM.State.FAILED, fsm.getCurrentState());
            assertFalse(fsm.canContinue());
        }

        @Test
        @DisplayName("No debe transicionar desde estado terminal")
        void shouldNotTransitionFromTerminal() {
            fsm.transition(CognitiveFSM.State.GATHERING);
            fsm.transition(CognitiveFSM.State.PLANNING);
            fsm.transition(CognitiveFSM.State.CODING);
            fsm.transition(CognitiveFSM.State.VERIFYING);
            fsm.transition(CognitiveFSM.State.COMPLETED);

            fsm.transition(CognitiveFSM.State.IDLE);

            assertEquals(CognitiveFSM.State.COMPLETED, fsm.getCurrentState());
        }

        @Test
        @DisplayName("Debe registrar historial de estados")
        void shouldRecordHistory() {
            fsm.transition(CognitiveFSM.State.GATHERING);
            fsm.transition(CognitiveFSM.State.PLANNING);

            var history = fsm.getHistory();
            assertEquals(3, history.size());
            assertEquals(CognitiveFSM.State.IDLE, history.get(0));
            assertEquals(CognitiveFSM.State.GATHERING, history.get(1));
            assertEquals(CognitiveFSM.State.PLANNING, history.get(2));
        }

        @Test
        @DisplayName("Debe poder reiniciarse")
        void shouldReset() {
            fsm.transition(CognitiveFSM.State.GATHERING);
            fsm.recordAttempt();

            fsm.reset();

            assertEquals(CognitiveFSM.State.IDLE, fsm.getCurrentState());
            assertEquals(0, fsm.getAttemptCount());
            assertEquals(1, fsm.getHistory().size());
        }

        @Test
        @DisplayName("Debe generar reporte de estado")
        void shouldGenerateStatusReport() {
            fsm.transition(CognitiveFSM.State.GATHERING);

            String report = fsm.getStatusReport();

            assertTrue(report.contains("CognitiveFSM Status"));
            assertTrue(report.contains("GATHERING"));
            assertTrue(report.contains("Historial"));
        }
    }

    @Nested
    @DisplayName("DependencyTracker Tests")
    class DependencyTrackerTests {
        private DependencyTracker tracker;

        @TempDir
        Path tempDir;

        @BeforeEach
        void setUp() {
            tracker = new DependencyTracker();
        }

        @Test
        @DisplayName("Debe encontrar dependencias Python simples")
        void shouldFindPythonDependencies() throws IOException {
            Files.writeString(tempDir.resolve("main.py"),
                "from utils import helper\n" +
                "import models\n" +
                "print('hello')");
            Files.writeString(tempDir.resolve("utils.py"), "def helper(): pass");
            Files.writeString(tempDir.resolve("models.py"), "class User: pass");

            Set<String> impact = tracker.findImpactRadius(tempDir, "main.py", 1);

            assertTrue(impact.contains("main.py"));
        }

        @Test
        @DisplayName("Debe retornar solo archivo semilla si no tiene imports")
        void shouldReturnSeedOnlyIfNoImports() throws IOException {
            Files.writeString(tempDir.resolve("isolated.py"), "print('no imports')");

            Set<String> impact = tracker.findImpactRadius(tempDir, "isolated.py", 2);

            assertEquals(1, impact.size());
            assertTrue(impact.contains("isolated.py"));
        }

        @Test
        @DisplayName("Debe manejar archivo inexistente")
        void shouldHandleNonexistentFile() {
            Set<String> impact = tracker.findImpactRadius(tempDir, "nonexistent.py", 1);

            assertEquals(1, impact.size());
        }

        @Test
        @DisplayName("Debe limitar profundidad de busqueda")
        void shouldLimitSearchDepth() throws IOException {
            Files.writeString(tempDir.resolve("a.py"), "import b");
            Files.writeString(tempDir.resolve("b.py"), "import c");
            Files.writeString(tempDir.resolve("c.py"), "print('end')");

            Set<String> depth0 = tracker.findImpactRadius(tempDir, "a.py", 0);

            assertEquals(1, depth0.size());
        }

        @Test
        @DisplayName("Debe ignorar imports comentados")
        void shouldIgnoreCommentedImports() throws IOException {
            Files.writeString(tempDir.resolve("main.py"),
                "import real_module\n" +
                "# import commented_module\n" +
                "// import also_commented\n" +
                "print('hello')");
            Files.writeString(tempDir.resolve("real_module.py"), "x = 1");
            Files.writeString(tempDir.resolve("commented_module.py"), "y = 2");

            Set<String> impact = tracker.findImpactRadius(tempDir, "main.py", 1);

            assertTrue(impact.contains("main.py"));
        }
    }

    @Nested
    @DisplayName("Strategist Tests")
    class StrategistTests {
        private Strategist strategist;

        @BeforeEach
        void setUp() {
            strategist = new Strategist();
        }

        @Test
        @DisplayName("Debe clasificar intent EXPLAIN")
        void shouldClassifyExplainIntent() {
            var result = strategist.classifyIntent(
                "why is this function returning null?",
                "NullPointerException"
            );

            assertEquals(Strategist.StrategyType.EXPLAIN, result);
        }

        @Test
        @DisplayName("Debe clasificar intent REFACTOR")
        void shouldClassifyRefactorIntent() {
            var result = strategist.classifyIntent(
                "please refactor this messy code",
                ""
            );

            assertEquals(Strategist.StrategyType.REFACTOR, result);
        }

        @Test
        @DisplayName("Debe defaultear a HOTFIX")
        void shouldDefaultToHotfix() {
            var result = strategist.classifyIntent(
                "fix this error",
                "CompilationError at line 42"
            );

            assertEquals(Strategist.StrategyType.HOTFIX, result);
        }

        @Test
        @DisplayName("Debe seleccionar REFACTOR para archivos pequenos")
        void shouldSelectRefactorForSmallFiles() {
            String smallFile = "class User { }";

            var result = strategist.selectBestPath(smallFile);

            assertEquals(Strategist.StrategyType.REFACTOR, result);
        }

        @Test
        @DisplayName("Debe seleccionar HOTFIX para archivos grandes")
        void shouldSelectHotfixForLargeFiles() {
            String largeFile = "x".repeat(5000);

            var result = strategist.selectBestPath(largeFile);

            assertEquals(Strategist.StrategyType.HOTFIX, result);
        }

        @Test
        @DisplayName("Debe evaluar multiples archivos")
        void shouldEvaluateMultipleFiles() {
            Map<String, String> files = Map.of(
                "a.py", "content a",
                "b.py", "content b",
                "c.py", "content c",
                "d.py", "content d"
            );

            var result = strategist.evaluateMultipleFiles(files);

            assertEquals(Strategist.StrategyType.HOTFIX, result);
        }

        @Test
        @DisplayName("Debe generar justificacion")
        void shouldGenerateJustification() {
            String justification = strategist.justifyStrategy(
                Strategist.StrategyType.HOTFIX,
                5000
            );

            assertTrue(justification.contains("parche rapido"));
            assertTrue(justification.contains("5000"));
        }

        @Test
        @DisplayName("StrategyType debe tener etiquetas correctas")
        void strategyTypeShouldHaveLabels() {
            assertEquals("Parche Rapido", Strategist.StrategyType.HOTFIX.getLabel());
            assertEquals("Reingenieria", Strategist.StrategyType.REFACTOR.getLabel());
            assertEquals("Solo Explicar", Strategist.StrategyType.EXPLAIN.getLabel());
        }

        @Test
        @DisplayName("StrategyType debe tener probabilidades")
        void strategyTypeShouldHaveProbabilities() {
            assertEquals(0.8, Strategist.StrategyType.HOTFIX.getBaseSuccessProbability());
            assertEquals(0.4, Strategist.StrategyType.REFACTOR.getBaseSuccessProbability());
            assertEquals(1.0, Strategist.StrategyType.EXPLAIN.getBaseSuccessProbability());
        }

        @Test
        @DisplayName("Debe estimar complejidad ciclomatica basica")
        void shouldEstimateCyclomaticComplexity() {
            String simpleCode = "int x = 1;\nreturn x;";

            int complexity = strategist.estimateCyclomaticComplexity(simpleCode);

            assertEquals(1, complexity);
        }

        @Test
        @DisplayName("Debe aumentar complejidad con estructuras de control")
        void shouldIncreaseComplexityWithControlStructures() {
            String complexCode = """
                if (x > 0) {
                    for (int i = 0; i < 10; i++) {
                        if (i % 2 == 0) {
                            doSomething();
                        } else {
                            doOther();
                        }
                    }
                } else {
                    handleNegative();
                }
                """;

            int complexity = strategist.estimateCyclomaticComplexity(complexCode);

            assertTrue(complexity >= 5, "Expected complexity >= 5 but got: " + complexity);
        }

        @Test
        @DisplayName("Debe penalizar indentacion profunda")
        void shouldPenalizeDeepNesting() {
            String deeplyNested = """
                if (a) {
                    if (b) {
                        if (c) {
                            if (d) {
                                if (e) {
                                                    doDeep();
                                }
                            }
                        }
                    }
                }
                """;

            int complexity = strategist.estimateCyclomaticComplexity(deeplyNested);

            assertTrue(complexity > 5);
        }

        @Test
        @DisplayName("selectBestPathWithComplexity debe preferir HOTFIX para codigo complejo")
        void shouldPreferHotfixForComplexCode() {
            StringBuilder complexCode = new StringBuilder();
            for (int i = 0; i < 15; i++) {
                complexCode.append("if (condition").append(i).append(") { }\n");
            }

            var result = strategist.selectBestPathWithComplexity(complexCode.toString());

            assertEquals(Strategist.StrategyType.HOTFIX, result);
        }

        @Test
        @DisplayName("selectBestPathWithComplexity debe permitir REFACTOR para codigo simple")
        void shouldAllowRefactorForSimpleCode() {
            String simpleCode = "int x = 1;\nreturn x;";

            var result = strategist.selectBestPathWithComplexity(simpleCode);

            assertEquals(Strategist.StrategyType.REFACTOR, result);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        @Test
        @DisplayName("Flujo completo: FSM + TokenMonitor + Strategist")
        void fullCognitiveFlow() {
            CognitiveFSM fsm = new CognitiveFSM(3);
            TokenMonitor tokenizer = new TokenMonitor();
            Strategist strategist = new Strategist();

            String errorLog = "TypeError: 'NoneType' object is not subscriptable";
            String fileContent = "def get_user(id): return None";

            fsm.transition(CognitiveFSM.State.GATHERING);
            assertEquals(CognitiveFSM.State.GATHERING, fsm.getCurrentState());

            fsm.transition(CognitiveFSM.State.PLANNING);
            var strategy = strategist.selectBestPath(fileContent);
            assertEquals(Strategist.StrategyType.REFACTOR, strategy);

            fsm.transition(CognitiveFSM.State.CODING);
            String prompt = fileContent + "\n" + errorLog;
            assertTrue(tokenizer.isSafe(prompt));

            fsm.transition(CognitiveFSM.State.VERIFYING);
            fsm.transition(CognitiveFSM.State.COMPLETED);

            assertFalse(fsm.canContinue());
            assertEquals(CognitiveFSM.State.COMPLETED, fsm.getCurrentState());
        }
    }
}
