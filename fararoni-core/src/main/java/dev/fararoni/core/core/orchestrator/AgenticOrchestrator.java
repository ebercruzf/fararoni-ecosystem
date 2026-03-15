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

import dev.fararoni.bus.agent.api.ToolRegistry;
import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.bus.agent.api.ToolResponse;
import dev.fararoni.core.client.LlmClient;
import dev.fararoni.core.core.engine.ReflexionGuard;
import dev.fararoni.core.core.memory.GraphRAGService;
import dev.fararoni.core.core.ninja.ExecutionModeSelector;
import dev.fararoni.core.core.ninja.ExecutionModeSelector.ExecutionMode;
import dev.fararoni.core.core.ninja.ExecutionModeSelector.ExecutionParams;
import dev.fararoni.core.core.ninja.NinjaDispatcher;
import dev.fararoni.core.core.ninja.SpeculativeCache;
import dev.fararoni.core.core.persona.CognitiveEngine;
import dev.fararoni.core.core.persona.CognitiveEngine.CognitiveResult;
import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.core.persona.Personas;
import dev.fararoni.core.core.prompt.PromptBuilder;
import dev.fararoni.core.core.react.ReActEngine;
import dev.fararoni.core.core.react.ReActResult;
import dev.fararoni.core.core.react.ReActStep;
import dev.fararoni.core.core.reflexion.EvaluationContext;
import dev.fararoni.core.model.GenerationRequest;
import dev.fararoni.core.model.GenerationResponse;
import dev.fararoni.core.model.Message;
import dev.fararoni.core.tokenizer.Tokenizer;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class AgenticOrchestrator {
    private static final Logger LOG = Logger.getLogger(AgenticOrchestrator.class.getName());

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final Tokenizer tokenizer;

    private final ReActEngine reActEngine;
    private final CognitiveEngine cognitiveEngine;
    private final ExecutionModeSelector modeSelector;
    private final NinjaDispatcher ninjaDispatcher;
    private final GraphRAGService ragService;
    private final ReflexionGuard reflexionGuard;

    private final boolean enableRAG;
    private final boolean enablePersonas;
    private final boolean enableReflexion;
    private final boolean enableSpeculativeCache;
    private final int maxRagItems;
    private final int maxContextTokens;
    private final int maxTokens;
    private final String defaultSystemPrompt;

    private final List<Message> conversationHistory = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Object> sessionContext = new ConcurrentHashMap<>();
    private Persona currentPersona;
    private Consumer<ReActStep> stepListener;
    private Consumer<String> tokenListener;

    private AgenticOrchestrator(Builder builder) {
        this.llmClient = Objects.requireNonNull(builder.llmClient, "llmClient required");
        this.toolRegistry = builder.toolRegistry;
        this.tokenizer = builder.tokenizer;

        this.enableRAG = builder.enableRAG;
        this.enablePersonas = builder.enablePersonas;
        this.enableReflexion = builder.enableReflexion;
        this.enableSpeculativeCache = builder.enableSpeculativeCache;
        this.maxRagItems = builder.maxRagItems;
        this.maxContextTokens = builder.maxContextTokens;
        this.maxTokens = builder.maxTokens;
        this.defaultSystemPrompt = builder.systemPrompt != null ? builder.systemPrompt :
            "You are a helpful AI assistant specialized in software development.";

        this.ragService = enableRAG ? GraphRAGService.create() : null;

        this.cognitiveEngine = enablePersonas
            ? CognitiveEngine.standard()
            : CognitiveEngine.withPersona(Personas.DEVELOPER);
        this.currentPersona = cognitiveEngine.getCurrentPersona();

        this.reflexionGuard = enableReflexion
            ? ReflexionGuard.standard()
            : null;

        this.modeSelector = ExecutionModeSelector.create();

        if (toolRegistry != null) {
            SpeculativeCache cache = enableSpeculativeCache
                ? SpeculativeCache.builder()
                    .maxSize(builder.cacheSize)
                    .ttl(Duration.ofMinutes(builder.cacheTtlMinutes))
                    .build()
                : null;

            this.ninjaDispatcher = NinjaDispatcher.builder()
                .registry(toolRegistry)
                .speculativeCache(cache)
                .maxConcurrency(builder.maxConcurrency)
                .speculativeEnabled(enableSpeculativeCache)
                .build();
        } else {
            this.ninjaDispatcher = null;
        }

        this.reActEngine = ReActEngine.builder()
            .llmClient(llmClient)
            .toolRegistry(toolRegistry)
            .reflexionGuard(reflexionGuard)
            .cognitiveEngine(cognitiveEngine)
            .maxTurns(builder.maxReActTurns)
            .maxTokens(builder.maxTokens)
            .temperature(builder.temperature)
            .enableReflexion(enableReflexion)
            .enablePersonas(enablePersonas)
            .build();
    }

    public OrchestratorResult execute(String task) {
        ExecutionMode mode = modeSelector.selectMode(task);
        return execute(task, mode);
    }

    public OrchestratorResult execute(String task, ExecutionMode mode) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(mode, "mode must not be null");

        Instant startTime = Instant.now();
        LOG.info(() -> String.format("[Orchestrator] Executing in %s mode: %s",
            mode, truncate(task, 50)));

        ExecutionParams params = modeSelector.getParamsForMode(mode);

        if (enablePersonas) {
            currentPersona = cognitiveEngine.selectPersonaFor(task);
            LOG.fine(() -> "[Orchestrator] Selected persona: " + currentPersona.id());
        }

        String enhancedPrompt = buildEnhancedPrompt(task);

        conversationHistory.add(Message.user(task));

        OrchestratorResult result;
        if (mode == ExecutionMode.FAST || !params.enableReflexion()) {
            result = executeDirect(enhancedPrompt, params);
        } else {
            result = executeReAct(task, enhancedPrompt, params);
        }

        if (result.response() != null) {
            conversationHistory.add(Message.assistant(result.response()));

            if (enableRAG && ragService != null) {
                ragService.addMessage("user", task, "orchestrator");
                ragService.addMessage("assistant", result.response(), "orchestrator");
            }
        }

        Duration totalDuration = Duration.between(startTime, Instant.now());
        LOG.info(() -> String.format("[Orchestrator] Completed in %dms, mode=%s, tokens=%d",
            totalDuration.toMillis(), mode, result.tokensUsed()));

        return result.withDuration(totalDuration);
    }

    public OrchestratorResult executeStreaming(String task, Consumer<String> tokenCallback) {
        this.tokenListener = tokenCallback;

        try {
            ExecutionMode mode = modeSelector.selectMode(task);
            ExecutionParams params = modeSelector.getParamsForMode(mode);

            if (enablePersonas) {
                currentPersona = cognitiveEngine.selectPersonaFor(task);
            }

            String enhancedPrompt = buildEnhancedPrompt(task);
            conversationHistory.add(Message.user(task));

            GenerationRequest request = GenerationRequest.builder()
                .messages(List.of(
                    Message.system(buildSystemPrompt()),
                    Message.user(enhancedPrompt)
                ))
                .maxTokens(maxTokens)
                .stream(true)
                .build();

            StringBuilder responseBuilder = new StringBuilder();
            Instant startTime = Instant.now();

            llmClient.generateStream(
                request,
                token -> {
                    responseBuilder.append(token);
                    if (tokenCallback != null) {
                        tokenCallback.accept(token);
                    }
                },
                error -> LOG.warning("[Orchestrator] Stream error: " + error.getMessage()),
                () -> LOG.fine("[Orchestrator] Stream completed")
            );

            String response = responseBuilder.toString();
            conversationHistory.add(Message.assistant(response));

            CognitiveResult cogResult = null;
            if (enableReflexion && reflexionGuard != null) {
                cogResult = cognitiveEngine.process(task, response, EvaluationContext.empty());
            }

            Duration duration = Duration.between(startTime, Instant.now());

            return new OrchestratorResult(
                response,
                null,
                currentPersona,
                cogResult,
                mode,
                estimateTokens(enhancedPrompt, response),
                duration,
                true
            );
        } finally {
            this.tokenListener = null;
        }
    }

    public ToolResponse executeTool(ToolRequest request) {
        if (ninjaDispatcher != null) {
            return ninjaDispatcher.executeSingle(request);
        } else if (reActEngine != null) {
            return reActEngine.executeTool(request);
        }
        return ToolResponse.error("No tool executor available");
    }

    public List<ToolResponse> executeToolsBatch(List<ToolRequest> requests) {
        if (ninjaDispatcher != null) {
            return ninjaDispatcher.executeBatchOptimized(requests);
        }
        return requests.stream()
            .map(this::executeTool)
            .toList();
    }

    private OrchestratorResult executeDirect(String prompt, ExecutionParams params) {
        Instant startTime = Instant.now();

        GenerationRequest request = GenerationRequest.builder()
            .messages(List.of(
                Message.system(buildSystemPrompt()),
                Message.user(prompt)
            ))
            .maxTokens(maxTokens)
            .build();

        GenerationResponse genResponse = llmClient.generate(request);
        String response = genResponse.text();

        CognitiveResult cogResult = null;
        if (enableReflexion) {
            cogResult = cognitiveEngine.process(prompt, response, EvaluationContext.empty());

            if (!cogResult.isAccepted()) {
                String improvedPrompt = cogResult.improvedPrompt(prompt);
                request = GenerationRequest.builder()
                    .messages(List.of(
                        Message.system(buildSystemPrompt()),
                        Message.user(improvedPrompt)
                    ))
                    .maxTokens(maxTokens)
                    .build();

                genResponse = llmClient.generate(request);
                response = genResponse.text();
                cogResult = cognitiveEngine.process(prompt, response, EvaluationContext.empty());
            }
        }

        Duration duration = Duration.between(startTime, Instant.now());

        return new OrchestratorResult(
            response,
            null,
            currentPersona,
            cogResult,
            ExecutionMode.FAST,
            estimateTokens(prompt, response),
            duration,
            true
        );
    }

    private OrchestratorResult executeReAct(String task, String prompt, ExecutionParams params) {
        if (stepListener != null) {
            reActEngine.setStepListener(stepListener);
        }

        ReActResult reActResult = reActEngine.execute(task, conversationHistory);

        CognitiveResult cogResult = null;
        if (enableReflexion && reActResult.finalAnswer() != null) {
            cogResult = cognitiveEngine.process(
                task,
                reActResult.finalAnswer(),
                EvaluationContext.empty()
            );
        }

        return new OrchestratorResult(
            reActResult.finalAnswer(),
            reActResult,
            reActResult.persona() != null ? reActResult.persona() : currentPersona,
            cogResult,
            ExecutionMode.THOROUGH,
            estimateTokens(prompt, reActResult.finalAnswer()),
            reActResult.totalDuration(),
            reActResult.completed()
        );
    }

    private String buildEnhancedPrompt(String task) {
        PromptBuilder builder = PromptBuilder.create()
            .userQuery(task);

        if (enableRAG && ragService != null) {
            builder.withRAGContext(ragService, task, maxRagItems);
        }

        if (!conversationHistory.isEmpty()) {
            StringBuilder contextSb = new StringBuilder();
            int recentCount = Math.min(conversationHistory.size(), 6);
            List<Message> recent = conversationHistory.subList(
                conversationHistory.size() - recentCount,
                conversationHistory.size()
            );
            for (Message msg : recent) {
                contextSb.append(msg.role()).append(": ")
                    .append(truncate(msg.content(), 200)).append("\n");
            }
            builder.withRAGContext(contextSb.toString());
        }

        return builder.build();
    }

    private String buildSystemPrompt() {
        PromptBuilder builder = PromptBuilder.create()
            .systemPrompt(defaultSystemPrompt);

        if (enablePersonas && currentPersona != null) {
            builder.withPersona(currentPersona);
        }

        if (toolRegistry != null) {
            String toolsSummary = toolRegistry.generateToolsSummary();
            if (toolsSummary != null && !toolsSummary.isBlank()) {
                builder.addConstraint("Use the available tools when needed");
            }
        }

        return builder.build();
    }

    private int estimateTokens(String prompt, String response) {
        if (tokenizer != null) {
            int promptTokens = tokenizer.countTokens(prompt != null ? prompt : "");
            int responseTokens = tokenizer.countTokens(response != null ? response : "");
            return promptTokens + responseTokens;
        }
        int promptLen = prompt != null ? prompt.length() : 0;
        int responseLen = response != null ? response.length() : 0;
        return (promptLen + responseLen) / 4;
    }

    public void setStepListener(Consumer<ReActStep> listener) {
        this.stepListener = listener;
        if (reActEngine != null) {
            reActEngine.setStepListener(listener);
        }
    }

    public Persona getCurrentPersona() {
        return currentPersona;
    }

    public void setPersona(Persona persona) {
        this.currentPersona = persona;
        cognitiveEngine.setCurrentPersona(persona);
    }

    public void clearHistory() {
        conversationHistory.clear();
        if (ragService != null) {
            ragService.clearSession("orchestrator");
        }
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("conversationLength", conversationHistory.size());
        metrics.put("currentPersona", currentPersona != null ? currentPersona.id() : "none");
        metrics.put("ragEnabled", enableRAG);
        metrics.put("reflexionEnabled", enableReflexion);

        if (ninjaDispatcher != null) {
            metrics.putAll(ninjaDispatcher.getMetrics());
        }

        return metrics;
    }

    private static String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() <= len ? s : s.substring(0, len - 3) + "...";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgenticOrchestrator minimal(LlmClient llmClient) {
        return builder().llmClient(llmClient).build();
    }

    public static AgenticOrchestrator full(LlmClient llmClient, ToolRegistry toolRegistry) {
        return builder()
            .llmClient(llmClient)
            .toolRegistry(toolRegistry)
            .enableRAG(true)
            .enablePersonas(true)
            .enableReflexion(true)
            .enableSpeculativeCache(true)
            .build();
    }

    public static final class Builder {
        private LlmClient llmClient;
        private ToolRegistry toolRegistry;
        private Tokenizer tokenizer;
        private String systemPrompt;
        private boolean enableRAG = true;
        private boolean enablePersonas = true;
        private boolean enableReflexion = true;
        private boolean enableSpeculativeCache = true;
        private int maxRagItems = 10;
        private int maxContextTokens = 4096;
        private int maxReActTurns = 5;
        private int maxTokens = 2048;
        private double temperature = 0.7;
        private int cacheSize = 1000;
        private int cacheTtlMinutes = 5;
        private int maxConcurrency = 200;

        private Builder() {}

        public Builder llmClient(LlmClient client) {
            this.llmClient = client;
            return this;
        }

        public Builder toolRegistry(ToolRegistry registry) {
            this.toolRegistry = registry;
            return this;
        }

        public Builder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder systemPrompt(String prompt) {
            this.systemPrompt = prompt;
            return this;
        }

        public Builder enableRAG(boolean enable) {
            this.enableRAG = enable;
            return this;
        }

        public Builder enablePersonas(boolean enable) {
            this.enablePersonas = enable;
            return this;
        }

        public Builder enableReflexion(boolean enable) {
            this.enableReflexion = enable;
            return this;
        }

        public Builder enableSpeculativeCache(boolean enable) {
            this.enableSpeculativeCache = enable;
            return this;
        }

        public Builder maxRagItems(int max) {
            this.maxRagItems = max;
            return this;
        }

        public Builder maxContextTokens(int max) {
            this.maxContextTokens = max;
            return this;
        }

        public Builder maxReActTurns(int max) {
            this.maxReActTurns = max;
            return this;
        }

        public Builder maxTokens(int max) {
            this.maxTokens = max;
            return this;
        }

        public Builder temperature(double temp) {
            this.temperature = temp;
            return this;
        }

        public Builder cacheSize(int size) {
            this.cacheSize = size;
            return this;
        }

        public Builder cacheTtlMinutes(int minutes) {
            this.cacheTtlMinutes = minutes;
            return this;
        }

        public Builder maxConcurrency(int max) {
            this.maxConcurrency = max;
            return this;
        }

        public AgenticOrchestrator build() {
            return new AgenticOrchestrator(this);
        }
    }

    public record OrchestratorResult(
        String response,
        ReActResult reActResult,
        Persona persona,
        CognitiveResult cognitiveResult,
        ExecutionMode mode,
        int tokensUsed,
        Duration duration,
        boolean success
    ) {
        public boolean usedReAct() {
            return reActResult != null;
        }

        public int stepCount() {
            return reActResult != null ? reActResult.steps().size() : 0;
        }

        public boolean isValidated() {
            return cognitiveResult != null && cognitiveResult.isAccepted();
        }

        public String getValidationFeedback() {
            return cognitiveResult != null ? cognitiveResult.getFeedback() : "";
        }

        public OrchestratorResult withDuration(Duration newDuration) {
            return new OrchestratorResult(
                response, reActResult, persona, cognitiveResult,
                mode, tokensUsed, newDuration, success
            );
        }

        public String toSummary() {
            return String.format(
                "OrchestratorResult[mode=%s, success=%s, tokens=%d, duration=%dms, steps=%d, persona=%s]",
                mode, success, tokensUsed, duration.toMillis(), stepCount(),
                persona != null ? persona.id() : "none"
            );
        }
    }
}
