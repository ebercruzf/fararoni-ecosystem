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
package dev.fararoni.core.core.kernel;

import dev.fararoni.core.client.LlmClient;
import dev.fararoni.core.core.config.HardwareTier;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.kernel.router.SimpleVectorRouter;
import dev.fararoni.core.core.llm.LocalLlmService;
import dev.fararoni.core.core.memory.Wisdom;
import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.core.prompt.PromptBuilder;
import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.model.GenerationRequest;
import dev.fararoni.core.model.Message;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class HyperNativeKernel {
    private static final Logger LOG = Logger.getLogger(HyperNativeKernel.class.getName());

    private volatile LlmClient rabbitClient;
    private volatile LlmClient turtleClient;
    private volatile LlmClient llmClient;
    private final LocalLlmService nativeEngine;
    private final SimpleVectorRouter semanticRouter;
    private final SecurityGuard securityGuard;

    private final String fastModelName;
    private final String expertModelName;

    private final Map<String, String> responseCache = new ConcurrentHashMap<>();
    private final boolean cacheEnabled;
    private static final int MAX_CACHE_SIZE = 500;

    private long thinkCount = 0;
    private long cacheHits = 0;
    private long securityBlocks = 0;
    private long truncations = 0;
    private long rabbitCalls = 0;
    private long turtleCalls = 0;

    private static final int MAX_INPUT_TOKENS = 12000;
    private static final int MAX_RAG_TOKENS = 6000;
    private static final int CHARS_PER_TOKEN = 4;

    public HyperNativeKernel(LlmClient rabbitClient, LlmClient turtleClient,
                             LocalLlmService nativeEngine, boolean enableCache,
                             String fastModelName, String expertModelName) {
        this.rabbitClient = rabbitClient;
        this.turtleClient = turtleClient;
        this.llmClient = turtleClient;
        this.nativeEngine = nativeEngine;
        this.cacheEnabled = enableCache;
        this.semanticRouter = new SimpleVectorRouter(rabbitClient);
        this.securityGuard = new SecurityGuard();

        this.fastModelName = resolveModelName(fastModelName,
            AppDefaults.ENV_RABBIT_MODEL, AppDefaults.DEFAULT_RABBIT_MODEL);
        this.expertModelName = resolveModelName(expertModelName,
            AppDefaults.ENV_TURTLE_MODEL, AppDefaults.DEFAULT_TURTLE_MODEL);

        LOG.info(() -> String.format(
            "[Kernel] BLINDADO - Rabbit: %s (FIJO LOCAL), Turtle: %s (DINÁMICO)",
            this.fastModelName, this.expertModelName));
    }

    @Deprecated(since = "7.5.2", forRemoval = false)
    public HyperNativeKernel(LlmClient llmClient, LocalLlmService nativeEngine,
                             boolean enableCache, String fastModelName, String expertModelName) {
        this.rabbitClient = llmClient;
        this.turtleClient = llmClient;
        this.llmClient = llmClient;
        this.nativeEngine = nativeEngine;
        this.cacheEnabled = enableCache;
        this.semanticRouter = new SimpleVectorRouter(llmClient);
        this.securityGuard = new SecurityGuard();

        this.fastModelName = resolveModelName(fastModelName,
            AppDefaults.ENV_RABBIT_MODEL, AppDefaults.DEFAULT_RABBIT_MODEL);
        this.expertModelName = resolveModelName(expertModelName,
            AppDefaults.ENV_TURTLE_MODEL, AppDefaults.DEFAULT_TURTLE_MODEL);

        boolean nativeReady = nativeEngine != null && nativeEngine.isNativeAvailable();
        LOG.info(() -> String.format("[Kernel] Dos Corazones - Rabbit: %s (%s), Turtle: %s (Ollama)",
            this.fastModelName,
            nativeReady ? "NATIVO" : "REMOTO",
            this.expertModelName));
    }

    public HyperNativeKernel(LlmClient llmClient, boolean enableCache,
                             String fastModelName, String expertModelName) {
        this(llmClient, null, enableCache, fastModelName, expertModelName);
    }

    public HyperNativeKernel(LlmClient llmClient, boolean enableCache) {
        this(llmClient, null, enableCache, null, null);
    }

    public HyperNativeKernel(LlmClient llmClient) {
        this(llmClient, null, false, null, null);
    }

    private static String resolveModelName(String explicit, String envKey, String defaultValue) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return defaultValue;
    }

    public String think(Persona persona, String taskContext, String dynamicRagData) {
        return think(persona, taskContext, dynamicRagData, null);
    }

    public String think(Persona persona, String taskContext, String dynamicRagData, List<Wisdom> wisdomList) {
        thinkCount++;

        var routeResult = semanticRouter.route(taskContext);
        String intent = routeResult.intent();

        LOG.fine(() -> String.format("[Kernel] Intent: %s (score: %.3f, confident: %b)",
            intent, routeResult.score(), routeResult.confident()));

        String selectedModel = selectModelStrategy(persona, intent, taskContext);

        if (cacheEnabled) {
            String cacheKey = buildCacheKey(persona, taskContext, intent);
            String cached = responseCache.get(cacheKey);
            if (cached != null) {
                cacheHits++;
                LOG.fine(() -> "[Kernel] Cache HIT: " + cacheKey.substring(0, Math.min(50, cacheKey.length())));
                return cached;
            }
        }

        String safeRagData = truncateToTokenBudget(dynamicRagData, MAX_RAG_TOKENS);

        PromptBuilder builder = PromptBuilder.create()
            .withPersona(persona)
            .withRAGContext(safeRagData)
            .variable("intent", intent)
            .variable("persona_id", persona.id())
            .userQuery(taskContext);

        if (wisdomList != null && !wisdomList.isEmpty()) {
            builder.addContext(wisdomList);
        }

        String fullPrompt = builder.build();

        String response = generateResponse(fullPrompt, selectedModel);

        if (cacheEnabled) {
            String cacheKey = buildCacheKey(persona, taskContext, intent);
            if (responseCache.size() < MAX_CACHE_SIZE) {
                responseCache.put(cacheKey, response);
            }
        }

        return response;
    }

    public String think(Persona persona, String taskContext) {
        return think(persona, taskContext, null, null);
    }

    public boolean validateToolAccess(Persona persona, String toolName) {
        boolean allowed = securityGuard.validateToolAccess(persona, toolName);
        if (!allowed) {
            securityBlocks++;
            LOG.warning(() -> String.format("[Kernel] SECURITY BLOCK: %s tried to access %s",
                persona.id(), toolName));
        }
        return allowed;
    }

    public ToolExecutionResult executeToolSafe(Persona persona, String toolName, Map<String, Object> toolArgs) {
        if (!validateToolAccess(persona, toolName)) {
            return ToolExecutionResult.denied(
                persona.id(),
                toolName,
                "Persona " + persona.name() + " no tiene permiso para usar " + toolName
            );
        }

        return ToolExecutionResult.success(toolName, "Tool executed: " + toolName);
    }

    public SimpleVectorRouter getRouter() {
        return semanticRouter;
    }

    public String routeIntent(String query) {
        return semanticRouter.routeSimple(query);
    }

    public SimpleVectorRouter.RouteResult routeDetailed(String query) {
        return semanticRouter.route(query);
    }

    public KernelMetrics getMetrics() {
        double cacheHitRate = thinkCount > 0 ? (double) cacheHits / thinkCount : 0.0;
        return new KernelMetrics(
            thinkCount,
            cacheHits,
            securityBlocks,
            truncations,
            rabbitCalls,
            turtleCalls,
            cacheHitRate,
            semanticRouter.getMetrics()
        );
    }

    public void resetMetrics() {
        thinkCount = 0;
        cacheHits = 0;
        securityBlocks = 0;
        rabbitCalls = 0;
        turtleCalls = 0;
    }

    public String getFastModelName() {
        return fastModelName;
    }

    public String getExpertModelName() {
        return expertModelName;
    }

    public synchronized void hotSwapRabbitClient(LlmClient newRabbitClient, String newModelName) {
        this.rabbitClient = newRabbitClient;
    }

    public synchronized void hotSwapTurtleClient(LlmClient newTurtleClient, String newModelName) {
        this.turtleClient = newTurtleClient;
        this.llmClient = newTurtleClient;
    }

    public void clearCache() {
        responseCache.clear();
    }

    private String selectModelStrategy(Persona persona, String intent, String task) {
        Set<Critic.CriticCategory> critics = persona.priorityCritics();
        if (critics != null) {
            if (critics.contains(Critic.CriticCategory.SECURITY) ||
                critics.contains(Critic.CriticCategory.CODE)) {
                LOG.fine(() -> "[Kernel] Turtle seleccionado por ROL: " + persona.name());
                return expertModelName;
            }
        }

        if (task != null) {
            String taskLower = task.toLowerCase();
            if (taskLower.contains("arregl") ||
                taskLower.contains("fix") ||
                taskLower.contains("corrige") ||
                taskLower.contains("error") ||
                taskLower.contains("exception") ||
                taskLower.contains("roto") ||
                taskLower.contains("broken") ||
                (intent != null && intent.equalsIgnoreCase("DEBUGGING"))) {
                LOG.info(() -> "[Kernel] PROTOCOLO QUIRURGICO: Elevando a TURTLE por tarea de reparacion");
                return expertModelName;
            }
        }

        String personaName = persona.name() != null ? persona.name().toUpperCase() : "";
        if (personaName.contains("ARCHITECT") ||
            personaName.contains("SRE") ||
            personaName.contains("SECURITY")) {
            LOG.fine(() -> "[Kernel] Turtle seleccionado por NOMBRE: " + persona.name());
            return expertModelName;
        }

        if (intent != null) {
            switch (intent.toUpperCase()) {
                case "CODING", "DEBUGGING", "PLANNING", "SECURITY", "REFACTORING" -> {
                    LOG.fine(() -> "[Kernel] Turtle seleccionado por INTENT: " + intent);
                    return expertModelName;
                }
            }
        }

        int volumeThreshold = (MAX_RAG_TOKENS * CHARS_PER_TOKEN) / 4;
        if (task != null && task.length() > volumeThreshold) {
            LOG.fine(() -> "[Kernel] Turtle seleccionado por VOLUMEN: " + task.length() + " chars > " + volumeThreshold);
            return expertModelName;
        }

        LOG.fine(() -> "[Kernel] Rabbit seleccionado (default economico)");
        return fastModelName;
    }

    private String generateResponse(String prompt) {
        return generateResponse(prompt, expertModelName);
    }

    private String generateResponse(String prompt, String modelName) {
        boolean isRabbit = modelName.equals(fastModelName);
        if (isRabbit) {
            rabbitCalls++;
        } else {
            turtleCalls++;
        }

        HardwareTier tier = HardwareTier.fromModelName(modelName);
        boolean useToolsStrategy = tier.supportsNativeTools();
        int maxTokens = tier.getMaxOutputTokens();

        double temperature = useToolsStrategy ? 0.7 : 0.2;

        LOG.info(() -> String.format("[Kernel] Modelo: %s | Tier: %s | Estrategia: %s | maxTokens=%d",
            modelName, tier.name(),
            useToolsStrategy ? "NATIVE TOOLS" : "TEXT PARSING",
            maxTokens));

        try {
            if (useToolsStrategy) {
                return executeGeneration(prompt, modelName, maxTokens, temperature);
            } else {
                return executeGeneration(prompt, modelName, maxTokens, temperature);
            }
        } catch (Exception e) {
            LOG.severe(() -> "[Kernel] Error generating response: " + e.getMessage());
            return handleGenerationFailover(prompt, modelName, e);
        }
    }

    private String executeGeneration(String prompt, String modelName, int maxTokens, double temperature) {
        boolean isRabbit = modelName.equals(fastModelName);

        if (isRabbit && nativeEngine != null && nativeEngine.isNativeAvailable()) {
            LOG.info(() -> "[Kernel] NATIVE ENGINE: Ejecutando Rabbit localmente (sin red)");
            try {
                String response = nativeEngine.generate(prompt, maxTokens);
                LOG.fine(() -> "[Kernel] Respuesta nativa recibida (" + response.length() + " chars)");
                return response;
            } catch (Exception e) {
                LOG.warning(() -> "[Kernel] Motor nativo fallo: " + e.getMessage() + ". Intentando Ollama...");
                return executeRemoteGeneration(prompt, modelName, maxTokens, temperature);
            }
        }

        if (isRabbit) {
            LOG.info(() -> "[Kernel] REMOTE FALLBACK: Motor nativo no disponible, usando Ollama para Rabbit");
        } else {
            LOG.info(() -> "[Kernel] REMOTE ENGINE: Ejecutando Turtle via Ollama");
        }
        return executeRemoteGeneration(prompt, modelName, maxTokens, temperature);
    }

    private String executeRemoteGeneration(String prompt, String modelName, int maxTokens, double temperature) {
        var request = GenerationRequest.builder()
            .model(modelName)
            .messages(List.of(Message.user(prompt)))
            .maxTokens(maxTokens)
            .temperature(temperature)
            .build();

        boolean isRabbit = modelName.equals(fastModelName);
        LlmClient clientToUse = isRabbit ? rabbitClient : turtleClient;

        LOG.fine(() -> String.format("[Kernel] Cliente: %s para modelo %s",
            isRabbit ? "RABBIT (local)" : "TURTLE (dinámico)", modelName));

        return clientToUse.generate(request).text();
    }

    private String handleGenerationFailover(String prompt, String failedModel, Exception originalError) {
        String alternativeModel = failedModel.equals(fastModelName) ? expertModelName : fastModelName;

        LOG.warning(() -> String.format(
            "[Kernel] Failover: %s fallo, intentando con %s. Error: %s",
            failedModel, alternativeModel, originalError.getMessage()));

        try {
            HardwareTier altTier = HardwareTier.fromModelName(alternativeModel);
            return executeGeneration(prompt, alternativeModel,
                altTier.getMaxOutputTokens(),
                altTier.supportsNativeTools() ? 0.7 : 0.2);
        } catch (Exception e) {
            LOG.severe(() -> "[Kernel] Failover tambien fallo: " + e.getMessage());
            return "Error: Ambos modelos fallaron. Original: " + originalError.getMessage()
                + " | Failover: " + e.getMessage();
        }
    }

    private String buildCacheKey(Persona persona, String context, String intent) {
        return String.format("%s|%s|%s",
            persona.id(),
            intent,
            context.hashCode());
    }

    private String truncateToTokenBudget(String text, int maxTokens) {
        if (text == null || text.isBlank()) {
            return "";
        }

        int estimatedTokens = text.length() / CHARS_PER_TOKEN;

        if (estimatedTokens <= maxTokens) {
            return text;
        }

        truncations++;
        int maxChars = maxTokens * CHARS_PER_TOKEN;

        LOG.warning(() -> String.format(
            "[Kernel] TRUNCANDO contexto: %d tokens estimados > %d límite. Recortando a %d chars.",
            estimatedTokens, maxTokens, maxChars));

        String truncated = text.substring(0, maxChars);
        int lastNewline = truncated.lastIndexOf('\n');

        if (lastNewline > maxChars * 0.8) {
            truncated = truncated.substring(0, lastNewline);
        }

        return truncated + "\n\n... [CONTEXTO TRUNCADO - " + (estimatedTokens - maxTokens) + " tokens omitidos]";
    }

    public int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / CHARS_PER_TOKEN;
    }

    public int getMaxRagTokens() {
        return MAX_RAG_TOKENS;
    }

    public record ToolExecutionResult(
        boolean success,
        String toolName,
        String result,
        String errorMessage,
        String deniedPersonaId
    ) {
        public static ToolExecutionResult success(String toolName, String result) {
            return new ToolExecutionResult(true, toolName, result, null, null);
        }

        public static ToolExecutionResult error(String toolName, String errorMessage) {
            return new ToolExecutionResult(false, toolName, null, errorMessage, null);
        }

        public static ToolExecutionResult denied(String personaId, String toolName, String message) {
            return new ToolExecutionResult(false, toolName, null, message, personaId);
        }

        public boolean isDenied() {
            return deniedPersonaId != null;
        }
    }

    public record KernelMetrics(
        long totalThinks,
        long cacheHits,
        long securityBlocks,
        long contextTruncations,
        long rabbitCalls,
        long turtleCalls,
        double cacheHitRate,
        SimpleVectorRouter.RouterMetrics routerMetrics
    ) {
        public double truncationRate() {
            return totalThinks > 0 ? (double) contextTruncations / totalThinks : 0.0;
        }

        public double rabbitUsageRate() {
            long total = rabbitCalls + turtleCalls;
            return total > 0 ? (double) rabbitCalls / total : 0.0;
        }

        public String hybridBalance() {
            return String.format("[RAB] Rabbit: %d (%.1f%%) | [TUR] Turtle: %d (%.1f%%)",
                rabbitCalls, rabbitUsageRate() * 100,
                turtleCalls, (1 - rabbitUsageRate()) * 100);
        }
    }
}
