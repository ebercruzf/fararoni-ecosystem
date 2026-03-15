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
package dev.fararoni.core.core.orchestrator;

import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.bus.agent.api.ToolResponse;
import dev.fararoni.core.client.LlmClient;
import dev.fararoni.core.core.ninja.ExecutionModeSelector.ExecutionMode;
import dev.fararoni.core.core.orchestrator.AgenticOrchestrator.OrchestratorResult;
import dev.fararoni.core.core.persona.Personas;
import dev.fararoni.core.model.GenerationRequest;
import dev.fararoni.core.model.GenerationResponse;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("AgenticOrchestrator Tests")
class AgenticOrchestratorTest {
    private MockLlmClient mockClient;

    @BeforeEach
    void setUp() {
        mockClient = new MockLlmClient();
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {
        @Test
        @DisplayName("builder() requiere llmClient")
        void builder_requiresLlmClient() {
            assertThrows(NullPointerException.class, () ->
                AgenticOrchestrator.builder().build()
            );
        }

        @Test
        @DisplayName("builder() crea orchestrator con defaults")
        void builder_createsWithDefaults() {
            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .build();

            assertNotNull(orchestrator);
            assertNotNull(orchestrator.getCurrentPersona());
        }

        @Test
        @DisplayName("builder() permite configurar todas las opciones")
        void builder_allowsFullConfiguration() {
            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enablePersonas(false)
                .enableReflexion(false)
                .enableSpeculativeCache(false)
                .maxRagItems(5)
                .maxContextTokens(2048)
                .maxTokens(1024)
                .maxReActTurns(3)
                .temperature(0.5)
                .systemPrompt("Custom system prompt")
                .build();

            assertNotNull(orchestrator);
        }

        @Test
        @DisplayName("minimal() crea orchestrator minimo")
        void minimal_createsMinimalOrchestrator() {
            AgenticOrchestrator orchestrator = AgenticOrchestrator.minimal(mockClient);

            assertNotNull(orchestrator);
        }
    }

    @Nested
    @DisplayName("Execution Tests")
    class ExecutionTests {
        @Test
        @DisplayName("execute() retorna resultado exitoso")
        void execute_returnsSuccessfulResult() {
            mockClient.setResponse("This is the response.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("Simple question");

            assertTrue(result.success());
            assertNotNull(result.response());
            assertEquals("This is the response.", result.response());
        }

        @Test
        @DisplayName("execute() con modo FAST no usa ReAct")
        void execute_fastModeNoReAct() {
            mockClient.setResponse("Fast response.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("What is Java?", ExecutionMode.FAST);

            assertTrue(result.success());
            assertFalse(result.usedReAct());
            assertEquals(0, result.stepCount());
        }

        @Test
        @DisplayName("execute() calcula tokens estimados")
        void execute_estimatesTokens() {
            mockClient.setResponse("Response with some content.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("Test");

            assertTrue(result.tokensUsed() > 0);
        }

        @Test
        @DisplayName("execute() registra duracion")
        void execute_recordsDuration() {
            mockClient.setResponse("Response.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("Test");

            assertNotNull(result.duration());
            assertTrue(result.duration().toMillis() >= 0);
        }

        @Test
        @DisplayName("execute() asigna modo de ejecucion")
        void execute_assignsExecutionMode() {
            mockClient.setResponse("Response.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("Simple question", ExecutionMode.FAST);

            assertEquals(ExecutionMode.FAST, result.mode());
        }
    }

    @Nested
    @DisplayName("Persona Tests")
    class PersonaTests {
        @Test
        @DisplayName("getCurrentPersona() retorna persona por defecto")
        void getCurrentPersona_returnsDefault() {
            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enablePersonas(true)
                .build();

            assertNotNull(orchestrator.getCurrentPersona());
        }

        @Test
        @DisplayName("setPersona() cambia la persona actual")
        void setPersona_changesCurrentPersona() {
            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enablePersonas(true)
                .build();

            orchestrator.setPersona(Personas.SECURITY);

            assertEquals("security", orchestrator.getCurrentPersona().id());
        }

        @Test
        @DisplayName("execute() selecciona persona automaticamente si habilitado")
        void execute_selectsPersonaAutomatically() {
            mockClient.setResponse("Security analysis.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enablePersonas(true)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            OrchestratorResult result = orchestrator.execute("Review this code for security vulnerabilities");

            assertNotNull(result.persona());
        }
    }

    @Nested
    @DisplayName("History Tests")
    class HistoryTests {
        @Test
        @DisplayName("execute() agrega mensajes al historial")
        void execute_addsToHistory() {
            mockClient.setResponse("First response.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            orchestrator.execute("First question");
            Map<String, Object> metrics = orchestrator.getMetrics();

            assertEquals(2, metrics.get("conversationLength"));
        }

        @Test
        @DisplayName("clearHistory() limpia el historial")
        void clearHistory_clearsConversation() {
            mockClient.setResponse("Response.");

            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(false)
                .enableReflexion(false)
                .build();

            orchestrator.execute("Question 1");
            orchestrator.execute("Question 2");
            orchestrator.clearHistory();

            Map<String, Object> metrics = orchestrator.getMetrics();
            assertEquals(0, metrics.get("conversationLength"));
        }
    }

    @Nested
    @DisplayName("Tool Execution Tests")
    class ToolExecutionTests {
        @Test
        @DisplayName("executeTool() sin registry retorna error")
        void executeTool_withoutRegistry_returnsError() {
            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .build();

            ToolRequest request = ToolRequest.of("FILE", "read", Map.of("path", "test.txt"));
            ToolResponse response = orchestrator.executeTool(request);

            assertFalse(response.success());
        }

        @Test
        @DisplayName("executeToolsBatch() ejecuta en secuencia sin ninja")
        void executeToolsBatch_executesSequentially() {
            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .build();

            List<ToolRequest> requests = List.of(
                ToolRequest.of("FILE", "read", Map.of("path", "a.txt")),
                ToolRequest.of("FILE", "read", Map.of("path", "b.txt"))
            );

            List<ToolResponse> responses = orchestrator.executeToolsBatch(requests);

            assertEquals(2, responses.size());
            assertFalse(responses.get(0).success());
            assertFalse(responses.get(1).success());
        }
    }

    @Nested
    @DisplayName("Metrics Tests")
    class MetricsTests {
        @Test
        @DisplayName("getMetrics() retorna metricas basicas")
        void getMetrics_returnsBasicMetrics() {
            AgenticOrchestrator orchestrator = AgenticOrchestrator.builder()
                .llmClient(mockClient)
                .enableRAG(true)
                .enableReflexion(true)
                .build();

            Map<String, Object> metrics = orchestrator.getMetrics();

            assertTrue(metrics.containsKey("conversationLength"));
            assertTrue(metrics.containsKey("currentPersona"));
            assertTrue(metrics.containsKey("ragEnabled"));
            assertTrue(metrics.containsKey("reflexionEnabled"));
        }
    }

    @Nested
    @DisplayName("OrchestratorResult Tests")
    class OrchestratorResultTests {
        @Test
        @DisplayName("toSummary() genera resumen formateado")
        void toSummary_generatesFormattedSummary() {
            OrchestratorResult result = new OrchestratorResult(
                "Test response",
                null,
                Personas.DEVELOPER,
                null,
                ExecutionMode.FAST,
                100,
                Duration.ofMillis(500),
                true
            );

            String summary = result.toSummary();

            assertTrue(summary.contains("FAST"));
            assertTrue(summary.contains("success=true"));
            assertTrue(summary.contains("tokens=100"));
            assertTrue(summary.contains("developer"));
        }

        @Test
        @DisplayName("withDuration() crea nuevo resultado con duracion")
        void withDuration_createsNewResultWithDuration() {
            OrchestratorResult original = new OrchestratorResult(
                "Response",
                null,
                null,
                null,
                ExecutionMode.FAST,
                50,
                Duration.ofMillis(100),
                true
            );

            OrchestratorResult updated = original.withDuration(Duration.ofMillis(200));

            assertEquals(200, updated.duration().toMillis());
            assertEquals(original.response(), updated.response());
        }

        @Test
        @DisplayName("usedReAct() retorna false sin ReActResult")
        void usedReAct_returnsFalseWithoutReActResult() {
            OrchestratorResult result = new OrchestratorResult(
                "Response",
                null,
                null,
                null,
                ExecutionMode.FAST,
                50,
                Duration.ZERO,
                true
            );

            assertFalse(result.usedReAct());
        }

        @Test
        @DisplayName("stepCount() retorna 0 sin ReActResult")
        void stepCount_returnsZeroWithoutReActResult() {
            OrchestratorResult result = new OrchestratorResult(
                "Response",
                null,
                null,
                null,
                ExecutionMode.FAST,
                50,
                Duration.ZERO,
                true
            );

            assertEquals(0, result.stepCount());
        }

        @Test
        @DisplayName("isValidated() retorna false sin CognitiveResult")
        void isValidated_returnsFalseWithoutCognitiveResult() {
            OrchestratorResult result = new OrchestratorResult(
                "Response",
                null,
                null,
                null,
                ExecutionMode.FAST,
                50,
                Duration.ZERO,
                true
            );

            assertFalse(result.isValidated());
        }

        @Test
        @DisplayName("getValidationFeedback() retorna empty sin CognitiveResult")
        void getValidationFeedback_returnsEmptyWithoutCognitiveResult() {
            OrchestratorResult result = new OrchestratorResult(
                "Response",
                null,
                null,
                null,
                ExecutionMode.FAST,
                50,
                Duration.ZERO,
                true
            );

            assertEquals("", result.getValidationFeedback());
        }
    }

    private static class MockLlmClient implements LlmClient {
        private String response = "Mock response";
        private final AtomicInteger callCount = new AtomicInteger(0);

        void setResponse(String response) {
            this.response = response;
        }

        int getCallCount() {
            return callCount.get();
        }

        @Override
        public GenerationResponse generate(GenerationRequest request) {
            callCount.incrementAndGet();
            return new GenerationResponse(response, null, null, 100L, "stop", false, null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<GenerationResponse> generateAsync(GenerationRequest request) {
            return java.util.concurrent.CompletableFuture.completedFuture(generate(request));
        }

        @Override
        public void generateStream(
            GenerationRequest request,
            Consumer<String> onToken,
            Consumer<Throwable> onError,
            Runnable onComplete
        ) {
            callCount.incrementAndGet();
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
