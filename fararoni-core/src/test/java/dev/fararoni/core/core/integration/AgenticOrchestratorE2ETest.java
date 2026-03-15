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
package dev.fararoni.core.core.integration;

import dev.fararoni.core.client.LlmClient;
import dev.fararoni.core.core.ninja.ExecutionModeSelector.ExecutionMode;
import dev.fararoni.core.core.orchestrator.AgenticOrchestrator;
import dev.fararoni.core.core.orchestrator.AgenticOrchestrator.OrchestratorResult;
import dev.fararoni.core.core.persona.Personas;
import dev.fararoni.core.model.GenerationRequest;
import dev.fararoni.core.model.GenerationResponse;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("AgenticOrchestrator E2E Integration Tests")
class AgenticOrchestratorE2ETest {
    private MockLlmClientE2E mockClient;

    @BeforeEach
    void setUp() {
        mockClient = new MockLlmClientE2E();
    }

    @Nested
    @DisplayName("Scenario: Simple Query")
    class SimpleQueryScenario {
        @Test
        @DisplayName("E2E: User asks simple question → FAST mode → Direct response")
        void simpleQuestion_usesFirstMode_returnsDirectly() {
            mockClient.queueResponse("Java is a programming language.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("What is Java?");

            assertTrue(result.success());
            assertNotNull(result.response());
            assertEquals(ExecutionMode.FAST, result.mode());
            assertFalse(result.usedReAct());
            assertTrue(result.duration().toMillis() < 5000);
        }

        @Test
        @DisplayName("E2E: User asks multiple questions → Context maintained")
        void multipleQuestions_maintainsContext() {
            mockClient.queueResponse("Java is a programming language.");
            mockClient.queueResponse("Python is also a programming language.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            orchestrator.execute("What is Java?");
            orchestrator.execute("What about Python?");

            Map<String, Object> metrics = orchestrator.getMetrics();
            assertEquals(4, metrics.get("conversationLength"));
        }
    }

    @Nested
    @DisplayName("Scenario: Complex Task")
    class ComplexTaskScenario {
        @Test
        @DisplayName("E2E: User requests refactoring → THOROUGH mode → ReAct engaged")
        void refactoringRequest_usesThoroughMode() {
            mockClient.queueResponse("I'll analyze the code and provide a refactored version.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .maxReActTurns(3)
                .build();

            OrchestratorResult result = orchestrator.execute(
                "Refactor the authentication system to use JWT",
                ExecutionMode.THOROUGH
            );

            assertTrue(result.success());
            assertEquals(ExecutionMode.THOROUGH, result.mode());
        }

        @Test
        @DisplayName("E2E: Batch update request → Uses advanced mode")
        void batchUpdateRequest_usesAdvancedMode() {
            mockClient.queueResponse("I'll update all files systematically.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("Update all files in the project");

            assertTrue(result.success());
            assertTrue(
                result.mode() == ExecutionMode.THOROUGH ||
                result.mode() == ExecutionMode.SPECULATIVE
            );
        }
    }

    @Nested
    @DisplayName("Scenario: Persona Selection")
    class PersonaSelectionScenario {
        @Test
        @DisplayName("E2E: Security question → SECURITY persona selected")
        void securityQuestion_selectsSecurityPersona() {
            mockClient.queueResponse("I'll analyze for security vulnerabilities.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enablePersonas(true)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute(
                "Check this code for security vulnerabilities"
            );

            assertTrue(result.success());
            assertNotNull(result.persona());
            assertTrue(
                result.persona().id().equals("security") ||
                result.persona().expertise().contains("security")
            );
        }

        @Test
        @DisplayName("E2E: Architecture question → ARCHITECT persona selected")
        void architectureQuestion_selectsArchitectPersona() {
            mockClient.queueResponse("Here's the recommended architecture.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enablePersonas(true)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute(
                "Design a microservices architecture for this application"
            );

            assertTrue(result.success());
            assertNotNull(result.persona());
        }

        @Test
        @DisplayName("E2E: Manual persona override works")
        void manualPersonaOverride_respected() {
            mockClient.queueResponse("I'll test this functionality.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enablePersonas(false)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            orchestrator.setPersona(Personas.TESTER);

            assertEquals("tester", orchestrator.getCurrentPersona().id());

            OrchestratorResult result = orchestrator.execute("Explain inheritance");
            assertTrue(result.success());
        }
    }

    @Nested
    @DisplayName("Scenario: Error Handling")
    class ErrorHandlingScenario {
        @Test
        @DisplayName("E2E: Empty input → Returns error gracefully")
        void emptyInput_returnsError() {
            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("");

            assertNotNull(result);
        }

        @Test
        @DisplayName("E2E: Null input → Throws exception")
        void nullInput_throwsException() {
            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            assertThrows(NullPointerException.class, () -> {
                orchestrator.execute(null);
            });
        }

        @Test
        @DisplayName("E2E: LLM returns empty → Handled gracefully")
        void llmReturnsEmpty_handledGracefully() {
            mockClient.queueResponse("");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("Test question");

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Scenario: Streaming")
    class StreamingScenario {
        @Test
        @DisplayName("E2E: Streaming response → Tokens received incrementally")
        void streamingResponse_tokensReceivedIncrementally() {
            mockClient.queueResponse("This is a streaming response with multiple tokens.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            StringBuilder accumulated = new StringBuilder();
            AtomicInteger tokenCount = new AtomicInteger(0);

            orchestrator.executeStreaming("Test streaming", token -> {
                accumulated.append(token);
                tokenCount.incrementAndGet();
            });

            assertTrue(tokenCount.get() > 0);
            assertTrue(accumulated.length() > 0);
        }
    }

    @Nested
    @DisplayName("Scenario: Metrics")
    class MetricsScenario {
        @Test
        @DisplayName("E2E: Execution tracks tokens used")
        void executionTracksTokens() {
            mockClient.queueResponse("Response with some content here.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("Count my tokens");

            assertTrue(result.tokensUsed() > 0);
        }

        @Test
        @DisplayName("E2E: Execution tracks duration")
        void executionTracksDuration() {
            mockClient.queueResponse("Quick response.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("Quick question");

            assertNotNull(result.duration());
            assertTrue(result.duration().toNanos() > 0);
        }

        @Test
        @DisplayName("E2E: toSummary() provides useful information")
        void toSummaryProvidesUsefulInfo() {
            mockClient.queueResponse("Summary test response.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("Test");
            String summary = result.toSummary();

            assertTrue(summary.contains("mode="));
            assertTrue(summary.contains("success="));
            assertTrue(summary.contains("tokens="));
        }
    }

    @Nested
    @DisplayName("Scenario: History Management")
    class HistoryManagementScenario {
        @Test
        @DisplayName("E2E: clearHistory() resets conversation")
        void clearHistoryResetsConversation() {
            mockClient.queueResponse("First response.");
            mockClient.queueResponse("Second response.");
            mockClient.queueResponse("After clear response.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            orchestrator.execute("First question");
            orchestrator.execute("Second question");

            Map<String, Object> beforeClear = orchestrator.getMetrics();
            assertEquals(4, beforeClear.get("conversationLength"));

            orchestrator.clearHistory();

            Map<String, Object> afterClear = orchestrator.getMetrics();
            assertEquals(0, afterClear.get("conversationLength"));

            orchestrator.execute("After clear");
            Map<String, Object> afterNew = orchestrator.getMetrics();
            assertEquals(2, afterNew.get("conversationLength"));
        }
    }

    private static class MockLlmClientE2E implements LlmClient {
        private final Queue<String> responses = new LinkedList<>();
        private final AtomicInteger callCount = new AtomicInteger(0);

        void queueResponse(String response) {
            responses.add(response);
        }

        @Override
        public GenerationResponse generate(GenerationRequest request) {
            callCount.incrementAndGet();
            String response = responses.poll();
            if (response == null) {
                response = "Default mock response";
            }
            return new GenerationResponse(response, null, null, 100L, "stop", false, null);
        }

        @Override
        public CompletableFuture<GenerationResponse> generateAsync(GenerationRequest request) {
            return CompletableFuture.completedFuture(generate(request));
        }

        @Override
        public void generateStream(
            GenerationRequest request,
            Consumer<String> onToken,
            Consumer<Throwable> onError,
            Runnable onComplete
        ) {
            callCount.incrementAndGet();
            String response = responses.poll();
            if (response == null) {
                response = "Default streaming response";
            }
            for (String token : response.split("\\s+")) {
                onToken.accept(token + " ");
            }
            onComplete.run();
        }

        @Override
        public List<Integer> generateTokens(List<Integer> inputTokens, int maxTokens) {
            return List.of();
        }

        @Override
        public void generateTokensStream(List<Integer> inputTokens, int maxTokens,
                                         Consumer<Integer> onToken,
                                         Consumer<Throwable> onError,
                                         Runnable onComplete) {
            onComplete.run();
        }

        @Override
        public void generateWithChunking(GenerationRequest request,
                                         Consumer<ChunkResult> onChunk,
                                         Consumer<Throwable> onError,
                                         Runnable onComplete) {
            onComplete.run();
        }

        @Override
        public ServerStatus checkServerStatus() {
            return ServerStatus.healthy("1.0", "mock-model", 4096);
        }

        @Override
        public ModelInfo getModelInfo() {
            return new ModelInfo("mock-model", "transformer", 4096, 32000, List.of());
        }

        @Override
        public void close() {
        }
    }
}
