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
package dev.fararoni.core.core.react;

import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.bus.agent.api.ToolResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ReAct System - Motor Reasoning and Acting")
class ReActTest {
    @Nested
    @DisplayName("ReActStep")
    class ReActStepTests {
        @Test
        @DisplayName("thinking() crea paso de pensamiento")
        void thinking_createsThinkingStep() {
            ReActStep step = ReActStep.thinking(1, "Analyzing the problem", "raw output");

            assertEquals(1, step.stepNumber());
            assertEquals(ReActStep.StepType.THINKING, step.type());
            assertEquals("Analyzing the problem", step.thought());
            assertFalse(step.hasAction());
            assertFalse(step.hasObservation());
            assertFalse(step.isTerminal());
        }

        @Test
        @DisplayName("toolCall() crea paso de llamada a herramienta")
        void toolCall_createsToolCallStep() {
            ToolRequest action = ToolRequest.of("FILE", "write_file",
                Map.of("path", "test.txt", "content", "hello"));
            ToolResponse observation = ToolResponse.success("File created");

            ReActStep step = ReActStep.toolCall(2, "Need to write file",
                action, observation, "raw output", Duration.ofMillis(100));

            assertEquals(2, step.stepNumber());
            assertEquals(ReActStep.StepType.TOOL_CALL, step.type());
            assertTrue(step.hasAction());
            assertTrue(step.hasObservation());
            assertTrue(step.isObservationSuccessful());
            assertEquals("FILE", step.getToolName().orElse(""));
            assertEquals("write_file", step.getActionName().orElse(""));
            assertFalse(step.isTerminal());
        }

        @Test
        @DisplayName("finalAnswer() crea paso de respuesta final")
        void finalAnswer_createsFinalAnswerStep() {
            ReActStep step = ReActStep.finalAnswer(3, "The answer is 42", "raw output");

            assertEquals(3, step.stepNumber());
            assertEquals(ReActStep.StepType.FINAL_ANSWER, step.type());
            assertTrue(step.isFinalAnswer());
            assertTrue(step.isTerminal());
            assertEquals("The answer is 42", step.getObservationMessage().orElse(""));
        }

        @Test
        @DisplayName("error() crea paso de error")
        void error_createsErrorStep() {
            ReActStep step = ReActStep.error(1, "Connection failed", null);

            assertEquals(1, step.stepNumber());
            assertEquals(ReActStep.StepType.ERROR, step.type());
            assertTrue(step.isError());
            assertTrue(step.isTerminal());
            assertEquals("Connection failed", step.getObservationMessage().orElse(""));
        }

        @Test
        @DisplayName("toSummary() genera resumen correcto")
        void toSummary_generatesCorrectSummary() {
            ReActStep thinking = ReActStep.thinking(1, "Analyzing", "raw");
            assertTrue(thinking.toSummary().contains("THINKING"));
            assertTrue(thinking.toSummary().contains("Step 1"));

            ReActStep answer = ReActStep.finalAnswer(2, "Done", "raw");
            assertTrue(answer.toSummary().contains("FINAL_ANSWER"));
        }

        @Test
        @DisplayName("toPromptFormat() genera formato para prompt")
        void toPromptFormat_generatesPromptFormat() {
            ToolRequest action = ToolRequest.of("FILE", "read_file", Map.of("path", "test.txt"));
            ToolResponse observation = ToolResponse.success("file content");

            ReActStep step = ReActStep.toolCall(1, "Need to read the file",
                action, observation, "raw", Duration.ZERO);

            String format = step.toPromptFormat();
            assertTrue(format.contains("Thought:"));
            assertTrue(format.contains("Action:"));
            assertTrue(format.contains("Observation:"));
        }

        @Test
        @DisplayName("Constructor valida stepNumber >= 1")
        void constructor_validatesStepNumber() {
            assertThrows(IllegalArgumentException.class, () ->
                ReActStep.thinking(0, "test", "raw"));

            assertThrows(IllegalArgumentException.class, () ->
                ReActStep.thinking(-1, "test", "raw"));
        }

        @Test
        @DisplayName("Constructor valida type no null")
        void constructor_validatesTypeNotNull() {
            assertThrows(NullPointerException.class, () ->
                new ReActStep(1, null, "thought", null, null, "raw", null, null));
        }
    }

    @Nested
    @DisplayName("ReActResult")
    class ReActResultTests {
        @Test
        @DisplayName("success() crea resultado exitoso")
        void success_createsSuccessResult() {
            List<ReActStep> steps = List.of(
                ReActStep.thinking(1, "Thinking", "raw"),
                ReActStep.finalAnswer(2, "Done", "raw")
            );

            ReActResult result = ReActResult.success(steps, "The answer", Duration.ofMillis(500));

            assertTrue(result.isSuccess());
            assertFalse(result.hasError());
            assertFalse(result.isTimeout());
            assertEquals(2, result.stepCount());
            assertEquals("The answer", result.finalAnswer());
        }

        @Test
        @DisplayName("error() crea resultado con error")
        void error_createsErrorResult() {
            List<ReActStep> steps = List.of(
                ReActStep.error(1, "Failed", "raw")
            );

            ReActResult result = ReActResult.error(steps, "Error message", Duration.ofMillis(100));

            assertFalse(result.isSuccess());
            assertTrue(result.hasError());
            assertEquals(1, result.stepCount());
            assertNull(result.finalAnswer());
        }

        @Test
        @DisplayName("timeout() crea resultado de timeout")
        void timeout_createsTimeoutResult() {
            ReActResult result = ReActResult.timeout(List.of(), Duration.ofSeconds(30));

            assertFalse(result.isSuccess());
            assertFalse(result.hasError());
            assertTrue(result.isTimeout());
            assertFalse(result.completed());
        }

        @Test
        @DisplayName("toolCallCount() cuenta llamadas a herramientas")
        void toolCallCount_countsToolCalls() {
            List<ReActStep> steps = List.of(
                ReActStep.thinking(1, "Think 1", "raw"),
                ReActStep.toolCall(2, "Call 1", ToolRequest.of("A", "a"),
                    ToolResponse.success("ok"), "raw", Duration.ZERO),
                ReActStep.toolCall(3, "Call 2", ToolRequest.of("B", "b"),
                    ToolResponse.success("ok"), "raw", Duration.ZERO),
                ReActStep.finalAnswer(4, "Done", "raw")
            );

            ReActResult result = new ReActResult(steps, "Done", null, Duration.ZERO, true);

            assertEquals(2, result.toolCallCount());
        }

        @Test
        @DisplayName("getToolsUsed() retorna herramientas usadas")
        void getToolsUsed_returnsToolsUsed() {
            List<ReActStep> steps = List.of(
                ReActStep.toolCall(1, "Call 1", ToolRequest.of("FILE", "read"),
                    ToolResponse.success("ok"), "raw", Duration.ZERO),
                ReActStep.toolCall(2, "Call 2", ToolRequest.of("GIT", "status"),
                    ToolResponse.success("ok"), "raw", Duration.ZERO),
                ReActStep.toolCall(3, "Call 3", ToolRequest.of("FILE", "write"),
                    ToolResponse.success("ok"), "raw", Duration.ZERO)
            );

            ReActResult result = new ReActResult(steps, null, null, Duration.ZERO, true);

            List<String> tools = result.getToolsUsed();
            assertEquals(2, tools.size());
            assertTrue(tools.contains("FILE"));
            assertTrue(tools.contains("GIT"));
        }

        @Test
        @DisplayName("getLastStep() retorna ultimo paso")
        void getLastStep_returnsLastStep() {
            List<ReActStep> steps = List.of(
                ReActStep.thinking(1, "First", "raw"),
                ReActStep.finalAnswer(2, "Last", "raw")
            );

            ReActResult result = new ReActResult(steps, "Last", null, Duration.ZERO, true);

            assertTrue(result.getLastStep().isPresent());
            assertEquals(2, result.getLastStep().get().stepNumber());
            assertTrue(result.getLastStep().get().isFinalAnswer());
        }

        @Test
        @DisplayName("getFirstError() retorna primer error")
        void getFirstError_returnsFirstError() {
            List<ReActStep> steps = List.of(
                ReActStep.thinking(1, "Think", "raw"),
                ReActStep.error(2, "Error 1", "raw"),
                ReActStep.error(3, "Error 2", "raw")
            );

            ReActResult result = new ReActResult(steps, null, null, Duration.ZERO, false);

            assertTrue(result.getFirstError().isPresent());
            assertEquals(2, result.getFirstError().get().stepNumber());
        }

        @Test
        @DisplayName("getFinalAnswerOrError() retorna respuesta o error")
        void getFinalAnswerOrError_returnsAnswerOrError() {
            ReActResult success = ReActResult.success(List.of(), "Answer", Duration.ZERO);
            assertEquals("Answer", success.getFinalAnswerOrError());

            List<ReActStep> errorSteps = List.of(ReActStep.error(1, "Failed", "raw"));
            ReActResult error = new ReActResult(errorSteps, null, null, Duration.ZERO, false);
            assertEquals("Failed", error.getFinalAnswerOrError());
        }

        @Test
        @DisplayName("toSummary() genera resumen correcto")
        void toSummary_generatesCorrectSummary() {
            ReActResult result = ReActResult.success(
                List.of(ReActStep.finalAnswer(1, "Done", "raw")),
                "Done",
                Duration.ofMillis(100)
            );

            String summary = result.toSummary();
            assertTrue(summary.contains("SUCCESS"));
            assertTrue(summary.contains("steps: 1"));
            assertTrue(summary.contains("100ms"));
        }

        @Test
        @DisplayName("toDetailedLog() genera log detallado")
        void toDetailedLog_generatesDetailedLog() {
            List<ReActStep> steps = List.of(
                ReActStep.thinking(1, "Analyzing", "raw1"),
                ReActStep.toolCall(2, "Calling", ToolRequest.of("FILE", "read"),
                    ToolResponse.success("content"), "raw2", Duration.ofMillis(50)),
                ReActStep.finalAnswer(3, "Done", "raw3")
            );

            ReActResult result = new ReActResult(steps, "Done", null, Duration.ofMillis(200), true);

            String log = result.toDetailedLog();
            assertTrue(log.contains("ReAct Execution Log"));
            assertTrue(log.contains("THINKING"));
            assertTrue(log.contains("TOOL_CALL"));
            assertTrue(log.contains("FINAL_ANSWER"));
            assertTrue(log.contains("Summary"));
        }
    }

    @Nested
    @DisplayName("ReActLoop Configuration")
    class ReActLoopConfigTests {
        @Test
        @DisplayName("Builder crea loop con configuracion correcta")
        void builder_createsLoopWithCorrectConfig() {
            assertDoesNotThrow(() -> {
                var builder = ReActLoop.builder()
                    .maxTurns(10)
                    .timeout(Duration.ofMinutes(5))
                    .maxRetries(3)
                    .retryDelay(Duration.ofSeconds(2))
                    .validateFinalAnswer(true)
                    .retryCondition(r -> !r.isSuccess())
                    .stopCondition(r -> r.isSuccess())
                    .onStep(step -> {})
                    .onRetry(event -> {});
            });
        }

        @Test
        @DisplayName("RetryEvent contiene informacion correcta")
        void retryEvent_containsCorrectInfo() {
            ReActResult previousResult = ReActResult.timeout(List.of(), Duration.ZERO);
            Duration delay = Duration.ofSeconds(5);

            ReActLoop.RetryEvent event = new ReActLoop.RetryEvent(2, previousResult, delay);

            assertEquals(2, event.attemptNumber());
            assertSame(previousResult, event.previousResult());
            assertEquals(delay, event.delayBeforeRetry());
        }

        @Test
        @DisplayName("TimeoutException tiene mensaje correcto")
        void timeoutException_hasCorrectMessage() {
            ReActLoop.TimeoutException ex = new ReActLoop.TimeoutException("Test timeout");
            assertEquals("Test timeout", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("ReActEngine Parsing")
    class ReActEngineParsingTests {
        @Test
        @DisplayName("ToolRequest normaliza nombre de herramienta a mayusculas")
        void toolRequest_normalizesToolNameToUppercase() {
            ToolRequest request = ToolRequest.of("file", "write");
            assertEquals("FILE", request.toolName());

            ToolRequest request2 = ToolRequest.of("Git", "status");
            assertEquals("GIT", request2.toolName());
        }

        @Test
        @DisplayName("ToolRequest genera ID si no se proporciona")
        void toolRequest_generatesIdIfNotProvided() {
            ToolRequest request = ToolRequest.of("FILE", "read");
            assertNotNull(request.requestId());
            assertFalse(request.requestId().isBlank());
        }

        @Test
        @DisplayName("ToolRequest permite acceso a parametros")
        void toolRequest_allowsParamAccess() {
            ToolRequest request = ToolRequest.of("FILE", "write",
                Map.of("path", "test.txt", "content", "hello"));

            assertTrue(request.hasParam("path"));
            assertTrue(request.hasParam("content"));
            assertFalse(request.hasParam("nonexistent"));
            assertEquals("test.txt", request.getParamAsString("path"));
        }

        @Test
        @DisplayName("ToolResponse success crea respuesta exitosa")
        void toolResponse_success_createsSuccessResponse() {
            ToolResponse response = ToolResponse.success("File created");

            assertTrue(response.success());
            assertFalse(response.isError());
            assertEquals("File created", response.result());
            assertEquals("File created", response.getMessage());
        }

        @Test
        @DisplayName("ToolResponse error crea respuesta de error")
        void toolResponse_error_createsErrorResponse() {
            ToolResponse response = ToolResponse.error("Permission denied");

            assertFalse(response.success());
            assertTrue(response.isError());
            assertEquals("Permission denied", response.errorMessage());
            assertEquals("Permission denied", response.getMessage());
        }

        @Test
        @DisplayName("ToolResponse fromException crea respuesta de excepcion")
        void toolResponse_fromException_createsExceptionResponse() {
            RuntimeException ex = new RuntimeException("Something went wrong");
            ToolResponse response = ToolResponse.fromException(ex);

            assertFalse(response.success());
            assertEquals("Something went wrong", response.errorMessage());
        }
    }
}
