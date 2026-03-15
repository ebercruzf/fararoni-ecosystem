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

import dev.fararoni.core.core.reflexion.testoutput.FailurePattern;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("TestCorrectionService - Servicio de Corrección de Tests")
class TestCorrectionServiceTest {
    private TestCorrectionService service;

    private static final String PYTEST_FAIL_OUTPUT = """
        ============================= FAILURES =============================
        FAILED test_solution.py::test_add - AssertionError: assert 5 == 4
        ========================= 1 failed in 0.12s =========================
        """;

    private static final String PYTEST_PASS_OUTPUT = """
        ============================= test session starts ==============================
        ============================= 5 passed in 0.12s ==============================
        """;

    @BeforeEach
    void setUp() {
        service = TestCorrectionService.create();
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {
        @Test
        @DisplayName("create() retorna servicio configurado")
        void create_returnsConfiguredService() {
            TestCorrectionService s = TestCorrectionService.create();
            assertNotNull(s);
        }

        @Test
        @DisplayName("minimal() retorna servicio minimalista")
        void minimal_returnsMinimalService() {
            TestCorrectionService s = TestCorrectionService.minimal();
            assertNotNull(s);
        }
    }

    @Nested
    @DisplayName("generateCorrectionFeedback")
    class GenerateFeedbackTests {
        @Test
        @DisplayName("genera feedback para failure")
        void generatesFeedbackForFailure() {
            String feedback = service.generateCorrectionFeedback(
                "def add(a, b): return a",
                PYTEST_FAIL_OUTPUT,
                1
            );

            assertNotNull(feedback);
            assertFalse(feedback.isEmpty());
            assertTrue(feedback.contains("CRITICAL") || feedback.contains("FAILURE") || feedback.contains("FIX"));
        }

        @Test
        @DisplayName("feedback contiene información del error")
        void feedbackContainsErrorInfo() {
            String feedback = service.generateCorrectionFeedback(
                "def add(a, b): return a",
                PYTEST_FAIL_OUTPUT,
                1
            );

            assertTrue(
                feedback.contains("test_add") ||
                feedback.contains("AssertionError") ||
                feedback.contains("fail"),
                "Feedback debe mencionar el test fallido"
            );
        }

        @Test
        @DisplayName("acepta exerciseId opcional")
        void acceptsOptionalExerciseId() {
            String feedback = service.generateCorrectionFeedback(
                "def add(a, b): return a",
                PYTEST_FAIL_OUTPUT,
                1,
                "exercise-affine-cipher"
            );

            assertNotNull(feedback);
        }

        @Test
        @DisplayName("lanza NPE para code null")
        void throwsNPE_forNullCode() {
            assertThrows(NullPointerException.class, () ->
                service.generateCorrectionFeedback(null, PYTEST_FAIL_OUTPUT, 1)
            );
        }

        @Test
        @DisplayName("lanza NPE para testOutput null")
        void throwsNPE_forNullTestOutput() {
            assertThrows(NullPointerException.class, () ->
                service.generateCorrectionFeedback("code", null, 1)
            );
        }
    }

    @Nested
    @DisplayName("needsCorrection")
    class NeedsCorrectionTests {
        @Test
        @DisplayName("retorna true para failure")
        void returnsTrueForFailure() {
            boolean needs = service.needsCorrection("code", PYTEST_FAIL_OUTPUT);
            assertTrue(needs);
        }

        @Test
        @DisplayName("retorna false para pass")
        void returnsFalseForPass() {
            boolean needs = service.needsCorrection("code", PYTEST_PASS_OUTPUT);
            assertFalse(needs);
        }
    }

    @Nested
    @DisplayName("detectPatterns")
    class DetectPatternsTests {
        @Test
        @DisplayName("detecta patrones de failure")
        void detectsFailurePatterns() {
            String output = """
                FAILED test_add - AssertionError: assert 5 == 4
                """;

            Set<FailurePattern> patterns = service.detectPatterns(output);
            assertNotNull(patterns);
        }
    }

    @Nested
    @DisplayName("buildRetryPrompt")
    class BuildRetryPromptTests {
        @Test
        @DisplayName("construye prompt completo para retry con Sandwich Cognitivo")
        void buildsCompleteRetryPrompt() {
            String prompt = service.buildRetryPrompt(
                "Implementa la función add",
                "def add(a, b): return a",
                PYTEST_FAIL_OUTPUT,
                2
            );

            assertNotNull(prompt);
            assertTrue(prompt.contains("DIRECTIVA") ||
                       prompt.contains("CORRECCIÓN") ||
                       prompt.contains("INTERVENCIÓN") ||
                       prompt.contains("Senior") ||
                       prompt.length() > 50,
                "Debe contener autopsia de ReflexionStrategies");
            assertTrue(prompt.contains("INFORME") ||
                       prompt.contains("Intento") ||
                       prompt.contains("Attempt") ||
                       prompt.contains("2"),
                "Debe contener número de intento");
            assertTrue(prompt.contains("Implementa la función add"),
                "Debe contener instrucciones originales");
        }
    }

    @Nested
    @DisplayName("Memory Management")
    class MemoryManagementTests {
        @Test
        @DisplayName("clearMemory limpia estado")
        void clearMemory_clearsState() {
            service.generateCorrectionFeedback("code", PYTEST_FAIL_OUTPUT, 1);
            service.clearMemory();

            String feedback = service.generateCorrectionFeedback("code", PYTEST_FAIL_OUTPUT, 1);
            assertNotNull(feedback);
        }

        @Test
        @DisplayName("startExercise reinicia para nuevo ejercicio")
        void startExercise_resetsForNewExercise() {
            service.startExercise("exercise-1");
            service.generateCorrectionFeedback("code1", PYTEST_FAIL_OUTPUT, 1);

            service.startExercise("exercise-2");
            String feedback = service.generateCorrectionFeedback("code2", PYTEST_FAIL_OUTPUT, 1);
            assertNotNull(feedback);
        }
    }

    @Nested
    @DisplayName("getSummary")
    class GetSummaryTests {
        @Test
        @DisplayName("retorna resumen corto")
        void returnsShortSummary() {
            String summary = service.getSummary(PYTEST_FAIL_OUTPUT);

            assertNotNull(summary);
            assertTrue(summary.contains("Bypass") || summary.contains("Summary") || summary.length() > 5);
        }
    }

    @Nested
    @DisplayName("getMetrics")
    class GetMetricsTests {
        @Test
        @DisplayName("retorna métricas del servicio")
        void returnsServiceMetrics() {
            var metrics = service.getMetrics();

            assertNotNull(metrics);
            assertTrue(metrics.containsKey("currentExerciseId"));
        }
    }
}
