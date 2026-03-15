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
package dev.fararoni.core.faracore;

import dev.fararoni.core.FararoniCore;
import dev.fararoni.core.client.LlmClient;
import dev.fararoni.core.client.VllmClient;
import dev.fararoni.core.core.clients.AgentClient.AgentResponse;
import dev.fararoni.core.core.clients.AgentClient.ToolCall;
import dev.fararoni.core.core.clients.AnthropicClient;
import dev.fararoni.core.core.clients.OpenAICompatibleClient;
import dev.fararoni.core.core.skills.HardwareTelemetryScanner;
import dev.fararoni.core.core.skills.ModelFamily;
import dev.fararoni.core.core.skills.ToolRegistry;
import dev.fararoni.core.core.skills.ToolExecutor;
import dev.fararoni.core.core.skills.BuildOutputDistiller;
import dev.fararoni.core.core.skills.ToolExecutionResult;
import dev.fararoni.core.core.react.IPostFixStrategy;
import dev.fararoni.core.core.react.IRepairDirective;
import dev.fararoni.core.core.react.PostFixStrategyResolver;
import dev.fararoni.core.core.react.RepairDirectiveResolver;
import dev.fararoni.core.core.react.MessageHistoryDistiller;
import dev.fararoni.core.core.react.RepairModeContext;
import dev.fararoni.core.core.safety.SafetyLayer;
import dev.fararoni.core.core.safety.IroncladGuard;
import dev.fararoni.core.core.safety.audit.NotaryAuditListener;
import dev.fararoni.core.core.safety.listeners.FileSystemIntentListener;
import dev.fararoni.core.service.FilesystemService;
import dev.fararoni.core.core.routing.EnterpriseRouter;
import dev.fararoni.core.core.routing.RoutingPlan;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.security.SecureConfigService;
import dev.fararoni.core.core.llm.LocalLlmConfig;
import dev.fararoni.core.core.llm.LocalLlmService;
import dev.fararoni.core.core.llm.providers.OllamaProvider;
import dev.fararoni.core.core.llm.StreamingLlmCallback;
import dev.fararoni.core.core.workspace.GitManager;
import dev.fararoni.core.core.index.ProjectKnowledgeBase;
import dev.fararoni.core.core.index.ProjectKnowledgeBase.ContextProfile;
import dev.fararoni.core.core.telemetry.ToolAwareTelemetry;
import dev.fararoni.core.core.mission.engine.AgentTemplateManager;
import dev.fararoni.core.core.mission.engine.MissionTemplateManager;
import dev.fararoni.core.config.CliConfig;
import dev.fararoni.core.config.ConfigPriorityResolver;
import dev.fararoni.core.core.bootstrap.LlmProviderDiscovery;
import dev.fararoni.core.core.kernel.HyperNativeKernel;
import dev.fararoni.core.model.GenerationRequest;
import dev.fararoni.core.model.Message;
import dev.fararoni.core.tokenizer.EstimationTokenizer;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class FaraCoreLlmDispatcher {
    private static final Logger LOG = Logger.getLogger(FaraCoreLlmDispatcher.class.getName());

    private static void traceToFile(String msg) {
        try {
            String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            java.nio.file.Files.writeString(
                java.nio.file.Path.of("/tmp/fararoni-trace.log"),
                "[" + ts + "] " + msg + "\n",
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    private final Path workingDirectory;
    private final FaraCoreContextVault contextVault;
    private final GitManager gitManager;

    static final com.fasterxml.jackson.databind.ObjectMapper JSON_MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    volatile String lastExpertError = null;

    volatile VllmClient fastClient;
    volatile VllmClient expertClient;
    volatile LocalLlmService localLlmService;

    volatile OllamaProvider ollamaProviderForThinking;

    volatile java.util.function.Consumer<String> thinkingStreamCallback;

    volatile OpenAICompatibleClient agenticClient;
    volatile OpenAICompatibleClient rabbitAgenticClient;
    volatile AnthropicClient claudeClient;
    volatile boolean claudePreferred = false;
    ToolRegistry toolRegistry;
    ToolExecutor toolExecutor;

    private final HardwareTelemetryScanner hardwareScanner = new HardwareTelemetryScanner();

    private final PostFixStrategyResolver postFixResolver = new PostFixStrategyResolver();

    private final RepairDirectiveResolver repairDirectiveResolver = new RepairDirectiveResolver();

    private final RepairModeContext repairModeContext = new RepairModeContext();

    volatile FararoniCore.RabbitMode currentRabbitMode = FararoniCore.RabbitMode.NATIVE;
    EnterpriseRouter enterpriseRouter;

    String originalRabbitModel;
    String originalServerUrl;

    volatile RoutingPlan.TargetModel lastRoutingTarget = RoutingPlan.TargetModel.LOCAL;

    FileSystemIntentListener fileSystemListener;

    NotaryAuditListener notaryAuditListener;

    private static final java.util.Set<String> SAFE_BARE_COMMANDS = java.util.Set.of(
        "pwd", "ls", "date", "whoami", "hostname", "uname", "uptime", "id",
        "cat", "find", "tree", "wc", "head", "tail", "mkdir",
        "grep", "du", "df",
        "mvn", "gradle", "gradlew", "npm", "npx", "go", "cargo", "make"
    );

    private static final java.util.Set<String> BUILD_COMMANDS = java.util.Set.of(
        "mvn", "gradle", "gradlew", "npm", "npx", "go", "cargo", "make"
    );

    private static final java.util.Set<String> SAFE_GIT_SUBCOMMANDS = java.util.Set.of(
        "status", "log", "diff", "show", "branch", "add", "commit",
        "checkout", "stash", "init", "tag"
    );

    private static final java.util.Set<String> BLOCKED_GIT_SUBCOMMANDS = java.util.Set.of(
        "push", "pull", "fetch", "clone"
    );

    private static final java.util.regex.Pattern AGENT_CREATION_INTENT = java.util.regex.Pattern.compile(
        "(crea|genera|create|diseña|sintetiza|hazme)\\s+(?:un|el|nuevo|new)?\\s*(?:agente|agent)",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static final java.util.regex.Pattern COMMIT_INTENT = java.util.regex.Pattern.compile(
        "(haz|has|hazme|hacer|realiza|ejecuta|crea|genera|create|manda|sube|registra).*commit" +
        "|commitea.*" +
        "|(haz|crea|genera).*el.*commit.*" +
        "|agrega.*y.*commit.*" +
        "|commit.*los.*cambios.*" +
        "|guarda.*los.*cambios.*en.*git" +
        "|git.*add.*&&.*git.*commit",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    static final int MAX_TOOL_CALL_TURNS = 5;

    private int calculateDynamicTimeout(int baseTimeout) {
        double factor = hardwareScanner.getLoadFactor();
        int dynamicTimeout = (int) (baseTimeout * factor);
        if (factor > 1.0) {
            LOG.info("[BACKPRESSURE] Timeout ajustado: " + baseTimeout + "s → " + dynamicTimeout + "s (factor=" + factor + ")");
        }
        return dynamicTimeout;
    }

    public FaraCoreLlmDispatcher(Path workingDirectory, FaraCoreContextVault contextVault, GitManager gitManager) {
        this.workingDirectory = workingDirectory;
        this.contextVault = contextVault;
        this.gitManager = gitManager;
    }

    public LocalLlmService initializeNativeEngine() {
        try {
            LocalLlmConfig localConfig = LocalLlmConfig.fromEnvironment();

            if (!localConfig.isModelDownloaded()) {
                LOG.warning("[FARARONI] Motor nativo: Modelo GGUF no encontrado. Rabbit usara Ollama.");
                return null;
            }

            LocalLlmService service = new LocalLlmService(localConfig);

            if (!service.isNativeAvailable()) {
                LOG.warning("[FARARONI] Motor nativo: libjllama no disponible. Rabbit usara Ollama.");
                return null;
            }

            LOG.info(() -> "[FARARONI] Motor nativo CONFIGURADO (lazy): " + localConfig.modelPath());
            return service;
        } catch (Exception e) {
            LOG.warning(() -> "[FARARONI] Motor nativo: Error de inicializacion: " + e.getMessage());
            return null;
        }
    }

    public void initializeClients(ConfigPriorityResolver resolver,
                                   LlmProviderDiscovery.DiscoveryResult discoveryResult,
                                   String apiKey) {
        if (discoveryResult.success()) {
            String rabbitModelName;
            String rabbitServerUrl = discoveryResult.serverUrl();

            String savedRabbitModel = SecureConfigService.getInstance().getProperty("rabbit.model.name");
            String savedRabbitUrl = SecureConfigService.getInstance().getProperty("rabbit.server.url");

            if (savedRabbitModel != null && !savedRabbitModel.isBlank()) {
                rabbitModelName = savedRabbitModel;
                if (savedRabbitUrl != null && !savedRabbitUrl.isBlank()) {
                    rabbitServerUrl = savedRabbitUrl;
                }
                LOG.info(() -> "[FARARONI] Rabbit restaurado de Hot-Swap: " + rabbitModelName);
            } else {
                String envRabbit = System.getenv(AppDefaults.ENV_RABBIT_MODEL);
                if (envRabbit != null && !envRabbit.isBlank()) {
                    rabbitModelName = envRabbit;
                    LOG.info(() -> "[FARARONI] Rabbit configurado por ENV: " + rabbitModelName);
                } else {
                    rabbitModelName = AppDefaults.DEFAULT_RABBIT_MODEL;
                    LOG.info(() -> "[FARARONI] Rabbit usando default: " + rabbitModelName);
                }
            }

            CliConfig rabbitConfig = CliConfig.builder()
                .serverUrl(rabbitServerUrl)
                .modelName(rabbitModelName)
                .contextWindow(AppDefaults.RABBIT_CONTEXT_WINDOW)
                .maxTokens(2048)
                .build();

            this.fastClient = new VllmClient(
                rabbitServerUrl,
                apiKey,
                rabbitModelName,
                rabbitConfig,
                new EstimationTokenizer()
            );

            String turtleModelName = AppDefaults.DEFAULT_TURTLE_MODEL;

            CliConfig turtleConfig = CliConfig.builder()
                .serverUrl(discoveryResult.serverUrl())
                .modelName(turtleModelName)
                .contextWindow(AppDefaults.TURTLE_CONTEXT_WINDOW)
                .maxTokens(8192)
                .build();

            this.expertClient = new VllmClient(
                discoveryResult.serverUrl(),
                apiKey,
                turtleModelName,
                turtleConfig,
                new EstimationTokenizer()
            );

            LOG.info(() -> "[FARARONI] Arquitectura Dual Activada: Rabbit(" + fastClient.getModelName() +
                ", " + AppDefaults.RABBIT_CONTEXT_WINDOW + " ctx) | Turtle(" + expertClient.getModelName() +
                ", " + AppDefaults.TURTLE_CONTEXT_WINDOW + " ctx)");
        } else {
            LOG.warning("[FARARONI] " + discoveryResult.message());

            String configuredUrl = resolver.resolveServerUrl(null);

            String envRabbitFallback = System.getenv(AppDefaults.ENV_RABBIT_MODEL);
            String rabbitModelFallback = (envRabbitFallback != null && !envRabbitFallback.isBlank())
                ? envRabbitFallback
                : AppDefaults.DEFAULT_RABBIT_MODEL;

            CliConfig rabbitConfig = CliConfig.builder()
                .serverUrl(configuredUrl)
                .modelName(rabbitModelFallback)
                .contextWindow(AppDefaults.RABBIT_CONTEXT_WINDOW)
                .maxTokens(2048)
                .build();

            this.fastClient = new VllmClient(
                configuredUrl,
                apiKey,
                rabbitModelFallback,
                rabbitConfig,
                new EstimationTokenizer()
            );

            CliConfig turtleConfig = CliConfig.builder()
                .serverUrl(configuredUrl)
                .modelName(AppDefaults.DEFAULT_TURTLE_MODEL)
                .contextWindow(AppDefaults.TURTLE_CONTEXT_WINDOW)
                .maxTokens(8192)
                .build();

            this.expertClient = new VllmClient(
                configuredUrl,
                apiKey,
                AppDefaults.DEFAULT_TURTLE_MODEL,
                turtleConfig,
                new EstimationTokenizer()
            );
        }

        this.localLlmService = initializeNativeEngine();

        this.enterpriseRouter = new EnterpriseRouter(localLlmService);
        LOG.info(() -> "[FARARONI] EnterpriseRouter: " +
            (enterpriseRouter.isFullyOperational() ? "4 capas (LLM)" : "2 capas (Regex+Cache)"));

        String savedTurtleModel = SecureConfigService.getInstance().getProperty("turtle.model.name");
        String savedTurtleUrl = SecureConfigService.getInstance().getProperty("turtle.server.url");
        String savedTurtleMode = SecureConfigService.getInstance().getProperty("turtle.mode");

        if (savedTurtleModel != null && !savedTurtleModel.isBlank()
                && "REMOTE".equals(savedTurtleMode)) {
            String turtleUrl = (savedTurtleUrl != null && !savedTurtleUrl.isBlank())
                ? savedTurtleUrl : discoveryResult.serverUrl();
            try {
                CliConfig savedTurtleConfig = CliConfig.builder()
                    .serverUrl(turtleUrl)
                    .modelName(savedTurtleModel)
                    .contextWindow(AppDefaults.TURTLE_CONTEXT_WINDOW)
                    .maxTokens(8192)
                    .build();
                VllmClient restoredTurtle = new VllmClient(
                    turtleUrl, apiKey, savedTurtleModel, savedTurtleConfig, new EstimationTokenizer());
                this.expertClient = restoredTurtle;
                LOG.info(() -> "[FARARONI] Turtle restaurado de Hot-Swap: " + savedTurtleModel + " @ " + turtleUrl);
            } catch (Exception e) {
                LOG.warning("[FARARONI] No se pudo restaurar Turtle de Hot-Swap: " + e.getMessage());
            }
        }

        String claudeKey = System.getenv(AppDefaults.ENV_CLAUDE_API_KEY);
        if (claudeKey == null || claudeKey.isBlank()) {
            claudeKey = System.getenv("ANTHROPIC_API_KEY");
        }
        if (claudeKey != null && !claudeKey.isBlank()) {
            String claudeModel = System.getenv(AppDefaults.ENV_CLAUDE_MODEL);
            if (claudeModel == null || claudeModel.isBlank()) claudeModel = AppDefaults.DEFAULT_CLAUDE_MODEL;
            this.claudeClient = new AnthropicClient(claudeKey, claudeModel);
            String envPref = System.getenv(AppDefaults.ENV_CLAUDE_PREFERRED);
            this.claudePreferred = "true".equalsIgnoreCase(envPref);
            String finalModel = claudeModel;
            System.out.println("[FARARONI] Claude API activado: " + finalModel + " (preferred=" + claudePreferred + ")");
            LOG.info(() -> "[FARARONI] Claude API activado: " + finalModel + " (preferred=" + claudePreferred + ")");
        }

        if (this.claudeClient == null) {
            String savedClaudeMode = SecureConfigService.getInstance().getProperty("claude.mode");
            if ("ACTIVE".equals(savedClaudeMode)) {
                String savedClaudeKey = SecureConfigService.getInstance().getSecureProperty("claude.api.key");
                String savedClaudeModel = SecureConfigService.getInstance().getProperty("claude.model");
                if (savedClaudeKey != null && !savedClaudeKey.isBlank()) {
                    if (savedClaudeModel == null || savedClaudeModel.isBlank()) {
                        savedClaudeModel = AppDefaults.DEFAULT_CLAUDE_MODEL;
                    }
                    this.claudeClient = new AnthropicClient(savedClaudeKey, savedClaudeModel);
                    this.claudePreferred = true;
                    String restoredModel = savedClaudeModel;
                    System.out.println("[FARARONI] Claude restaurado de SecureConfig: " + restoredModel);
                    LOG.info(() -> "[FARARONI] Claude restaurado de SecureConfig: " + restoredModel);
                }
            }
        }
    }

    public void initializeAgenticClient(String serverUrl, String apiKey,
                                         ProjectKnowledgeBase memoryVault,
                                         SovereignEventBus sovereignBus,
                                         MissionTemplateManager missionTemplateManager,
                                         AgentTemplateManager agentTemplateManager) {
        try {
            String modelName = AppDefaults.DEFAULT_TURTLE_MODEL;

            System.out.println("Inicializando cliente agéntico...");
            System.out.println("Server URL: " + serverUrl);
            System.out.println("Model: " + modelName);
            System.out.println("Working Dir: " + this.workingDirectory);

            this.agenticClient = new OpenAICompatibleClient(serverUrl, apiKey, modelName,
                    calculateDynamicTimeout(AppDefaults.DEFAULT_TURTLE_TIMEOUT_SECONDS));
            this.toolRegistry = new ToolRegistry();
            SafetyLayer safetyLayer = new SafetyLayer(this.workingDirectory);
            this.toolExecutor = ToolExecutor.builder(this.workingDirectory, new FilesystemService(this.workingDirectory))
                .knowledgeBase(memoryVault)
                .safetyLayer(safetyLayer)
                .agentClient(this.agenticClient)
                .sovereignBus(sovereignBus)
                .missionTemplateManager(missionTemplateManager)
                .agentTemplateManager(agentTemplateManager)
                .gitManager(this.gitManager)
                .build();

            if (sovereignBus != null) {
                var ironcladGuard = new IroncladGuard();
                this.fileSystemListener = new FileSystemIntentListener(
                    sovereignBus,
                    ironcladGuard,
                    safetyLayer,
                    this.workingDirectory
                ).withOverseer(agentTemplateManager);
                this.fileSystemListener.start();
                boolean overseerActive = this.fileSystemListener.isOverseerEnabled();
                LOG.info("FileSystemIntentListener activo (Event-Driven File Writing)");
                LOG.info("[B] " + (overseerActive ? "[OK]" : "[!]") +
                    " Overseer Validator " + (overseerActive ? "ACTIVO" : "INACTIVO (sin AgentTemplateManager)"));

                this.notaryAuditListener = new NotaryAuditListener(
                    sovereignBus
                );
                this.notaryAuditListener.start();
                System.out.println(" NotaryAuditListener activo (SHA-256 Ledger)");
            }

            if (agentTemplateManager != null && missionTemplateManager != null) {
                java.util.Set<String> discoveredRoles = new java.util.HashSet<>(
                    agentTemplateManager.listAgentIds()
                );
                java.util.Set<String> discoveredMissions = new java.util.HashSet<>(
                    missionTemplateManager.getAvailableTemplateIds()
                );
                this.toolRegistry.injectDynamicContext(discoveredRoles, discoveredMissions);
                System.out.println(" Dynamic Tool Prompting activado");
                System.out.println(" Roles: " + discoveredRoles.size() +
                    " | Misiones: " + discoveredMissions.size());
            }

            int baseTools = toolRegistry.getAvailableTools(false).size();
            int exercismTools = toolRegistry.getAvailableTools(true).size();
            System.out.println(" Cliente agéntico inicializado");
            System.out.println(" Herramientas base: " + baseTools + " | Con Exercism: " + exercismTools);
            LOG.info(() -> "[FARARONI]  Cliente Agéntico inicializado: " + modelName +
                " (Tools: " + baseTools + " base, " + exercismTools + " exercism)");
        } catch (Exception e) {
            System.out.println(" Error inicializando cliente agéntico: " + e.getMessage());
            e.printStackTrace();
            LOG.warning("[FARARONI] Cliente agentico no disponible: " + e.getMessage());
        }
    }

    public String tryLocalExecution(String prompt, VllmClient client) throws Exception {
        boolean useThinking = Boolean.getBoolean(AppDefaults.ENV_SHOW_REASONING);
        String trimmed = prompt.trim().toLowerCase();

        String bareResult = executeBareCommand(trimmed);
        if (bareResult != null) {
            LOG.info("[BARE-CMD] Respuesta directa del Kernel: " + truncateForLog(bareResult, 80));
            return bareResult;
        }

        boolean isSimpleChat = trimmed.length() < 30 &&
            trimmed.matches("(hola|hello|hi|hey|buenos? (dias|tardes|noches)|buenas|que tal|" +
                "como estas|saludos|gracias|thank|ok|si|no|adios|bye|chao|dale|perfecto|entendido|" +
                "claro|listo|vale|genial|excelente|bien|cool).*");

        if (!isSimpleChat && client != null && toolRegistry != null) {
            try {
                String systemPrompt = contextVault.buildExpertPrompt(prompt, client.getConfig().contextWindow());
                String toolResult = executeWithToolCalling(systemPrompt, prompt, client);
                if (toolResult != null && !toolResult.isBlank()) {
                    LOG.info("[TOOL-CALLING] Respuesta exitosa via tool calling nativo");
                    return toolResult;
                }
            } catch (Exception e) {
                LOG.warning("[TOOL-CALLING] Fallback a siguiente intento: " + e.getMessage());
            }
        }

        if (useThinking && client != null && isOllamaEndpoint(client)) {
            try {
                String richPrompt = contextVault.buildRabbitPrompt(prompt);
                return executeWithOllamaThinking(richPrompt, client);
            } catch (Exception e) {
                LOG.warning("[THINKING] Fallback a GGUF/VllmClient: " + e.getMessage());
            }
        }

        if (!useThinking && localLlmService != null && localLlmService.isNativeAvailable()) {
            try {
                String richPrompt = contextVault.buildRabbitPrompt(prompt);
                String response = localLlmService.generate(richPrompt);
                if (response != null && !response.isBlank()) {
                    return response;
                }
            } catch (Exception e) {
                if (e.getMessage() != null &&
                    (e.getMessage().contains("context") || e.getMessage().contains("length"))) {
                    throw e;
                }
            }
        }

        if (client != null) {
            try {
                String richPrompt = contextVault.buildRabbitPrompt(prompt);

                boolean showThinking = Boolean.getBoolean("FARARONI_SHOW_REASONING");

                var request = GenerationRequest.builder()
                    .model(client.getModelName())
                    .messages(java.util.List.of(Message.user(richPrompt)))
                    .maxTokens(client.getConfig().maxTokens())
                    .temperature(0.7)
                    .think(showThinking)
                    .build();

                var responseObj = client.generate(request);
                return responseObj.text();
            } catch (Exception e) {
                if (e.getMessage() != null &&
                    (e.getMessage().contains("context") || e.getMessage().contains("length"))) {
                    throw e;
                }
                throw e;
            }
        }

        LOG.fine("[CHAT] Rabbit no disponible (ni GGUF ni Ollama)");
        return null;
    }

    public String tryRemoteExecution(String prompt, VllmClient client) throws Exception {
        if (client == null) {
            throw new IllegalStateException("Cliente experto no disponible (expertClient null)");
        }

        if (toolRegistry != null) {
            try {
                String systemPrompt = contextVault.buildExpertPrompt(prompt, client.getConfig().contextWindow());
                String toolResult = executeWithToolCalling(systemPrompt, prompt, client);
                if (toolResult != null && !toolResult.isBlank()) {
                    LOG.info("[TOOL-CALLING] Respuesta exitosa via Turtle con tool calling nativo");
                    return toolResult;
                }
            } catch (Exception e) {
                LOG.warning("[TOOL-CALLING] Fallback Turtle sin tools: " + e.getMessage());
            }
        }

        long startTime = System.currentTimeMillis();
        String modelName = client.getModelName();

        LOG.info(() -> "[CHAT] Consultando a Oficial Experto (" + modelName +
            ", " + client.getConfig().contextWindow() + " ctx)...");

        String missionBriefing = contextVault.buildExpertPrompt(prompt, client.getConfig().contextWindow());

        var request = GenerationRequest.builder()
                .model(modelName)
                .messages(java.util.List.of(
                    Message.user(missionBriefing)
                ))
                .maxTokens(client.getConfig().maxTokens())
                .temperature(0.7)
                .build();

        var responseObj = client.generate(request);
        String response = responseObj.text();

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("El experto devolvió silencio de radio (respuesta vacía).");
        }

        LOG.info(() -> "[CHAT] Respuesta recibida en " + (System.currentTimeMillis() - startTime) + "ms");
        return response;
    }

    public String tryRemoteExecutionSilent(String prompt) {
        try {
            return tryRemoteExecution(prompt, this.expertClient);
        } catch (Exception e) {
            throw new RuntimeException("Fallo total (Local + Experto): " + e.getMessage(), e);
        }
    }

    public String executeExpertWithFallbackSilent(String prompt) {
        try {
            return tryRemoteExecution(prompt, this.expertClient);
        } catch (Exception e) {
            LOG.severe("FALLO EXPERTO: " + e.getMessage());
            this.lastExpertError = e.getMessage();

            try {
                String localResponse = tryLocalExecution(prompt, this.fastClient);
                if (localResponse != null) {
                    String errorDetail = (lastExpertError != null) ? lastExpertError : "sin detalles";
                    this.lastRoutingTarget = RoutingPlan.TargetModel.LOCAL;
                    this.lastExpertError = null;
                    return "[SISTEMA: Experto no disponible (" + errorDetail + "). Usando Rabbit.]\n\n" + localResponse;
                }
            } catch (Exception fatal) {
                LOG.severe("Failover tambien fallo: " + fatal.getMessage());
            }

            return "[ERROR CRÍTICO] Ambos modelos fallaron: " + e.getMessage();
        }
    }

    public String executeWithToolCalling(String systemPrompt, String userMessage, VllmClient client) {
        if (client == null || toolRegistry == null || toolExecutor == null) {
            return null;
        }

        try {
            ModelFamily family = ModelFamily.fromModelName(client.getModelName());
            List<ObjectNode> tools = toolRegistry.getAvailableTools(family);
            LOG.info(() -> "[TOOL-ORCHESTRATOR] " + family + ": " + tools.size() + " tools seleccionadas");
            if (tools == null || tools.isEmpty()) {
                return null;
            }

            ArrayNode messages = JSON_MAPPER.createArrayNode();
            ObjectNode sysMsg = JSON_MAPPER.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);

            ObjectNode userMsg = JSON_MAPPER.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            ArrayNode toolsArray = JSON_MAPPER.createArrayNode();
            for (ObjectNode tool : tools) {
                toolsArray.add(tool);
            }

            for (int turn = 0; turn < MAX_TOOL_CALL_TURNS; turn++) {
                ObjectNode payload = JSON_MAPPER.createObjectNode();
                payload.put("model", client.getModelName());
                payload.set("messages", messages);
                payload.set("tools", toolsArray);
                payload.put("tool_choice", "auto");

                JsonNode response = client.generateWithTools(payload);
                JsonNode choices = response.path("choices");
                if (choices.isEmpty() || !choices.isArray()) {
                    LOG.warning("[TOOL-CALLING] Respuesta sin choices");
                    return null;
                }

                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.path("message");
                String finishReason = firstChoice.path("finish_reason").asText("stop");
                String contentText = message.path("content").asText(null);
                JsonNode toolCalls = message.path("tool_calls");

                if (toolCalls.isMissingNode() || toolCalls.isEmpty()) {
                    if (contentText != null && !contentText.isBlank()) {
                        List<JsonNode> textCalls = extractTextToolCalls(contentText);
                        if (!textCalls.isEmpty()) {
                            LOG.info("[TOOL-CALLING] FALLBACK: " + textCalls.size() +
                                " tool_calls detectados en texto plano (turn " + turn + ")");

                            ObjectNode assistFallback = JSON_MAPPER.createObjectNode();
                            assistFallback.put("role", "assistant");
                            assistFallback.put("content", contentText);
                            messages.add(assistFallback);

                            StringBuilder combinedResults = new StringBuilder();
                            int callIdx = 0;
                            for (JsonNode tc : textCalls) {
                                String functionName = tc.path("name").asText("");
                                String functionArgs = tc.path("arguments").toString();
                                callIdx++;

                                LOG.info("[TOOL-CALLING] FALLBACK [" + callIdx + "/" +
                                    textCalls.size() + "] ejecutando: " + functionName);

                                String toolResult;
                                try {
                                    ToolCall toolCall = new ToolCall(functionName, functionArgs);
                                    ToolExecutionResult execResult = toolExecutor.executeTool(toolCall);
                                    toolResult = execResult.message();
                                } catch (Exception e) {
                                    toolResult = "Error ejecutando " + functionName + ": " + e.getMessage();
                                    LOG.warning("[TOOL-CALLING] FALLBACK error en " +
                                        functionName + ": " + e.getMessage());
                                }

                                ObjectNode fallbackToolMsg = JSON_MAPPER.createObjectNode();
                                fallbackToolMsg.put("role", "tool");
                                fallbackToolMsg.put("content", toolResult != null ? toolResult : "");
                                fallbackToolMsg.put("tool_call_id", "fallback_" + turn + "_" + callIdx);
                                messages.add(fallbackToolMsg);

                                combinedResults.append("[").append(functionName).append("] ")
                                    .append(toolResult != null ? toolResult : "(sin resultado)")
                                    .append("\n");

                                LOG.info("[TOOL-CALLING] FALLBACK resultado " + functionName +
                                    ": " + truncateForLog(toolResult, 80));
                            }

                            String results = combinedResults.toString().trim();
                            if (!results.isBlank()) {
                                return results;
                            }
                            continue;
                        }

                        LOG.info("[TOOL-CALLING] Respuesta directa (sin tool_calls) en turn " + turn);
                        return contentText;
                    }
                    return null;
                }

                ObjectNode assistantMsg = JSON_MAPPER.createObjectNode();
                assistantMsg.put("role", "assistant");
                if (contentText != null) {
                    assistantMsg.put("content", contentText);
                } else {
                    assistantMsg.putNull("content");
                }
                assistantMsg.set("tool_calls", toolCalls);
                messages.add(assistantMsg);

                LOG.info("[TOOL-CALLING] Turn " + turn + ": " + toolCalls.size() + " tool_calls detectados");

                for (JsonNode tc : toolCalls) {
                    String callId = tc.path("id").asText("call_" + turn);
                    String functionName = tc.path("function").path("name").asText("");
                    String functionArgs = tc.path("function").path("arguments").asText("{}");

                    LOG.info("[TOOL-CALLING] Ejecutando: " + functionName + "(" + truncateForLog(functionArgs, 100) + ")");

                    String toolResult;
                    try {
                        ToolCall toolCall = new ToolCall(functionName, functionArgs);
                        ToolExecutionResult execResult = toolExecutor.executeTool(toolCall);
                        toolResult = execResult.message();
                    } catch (Exception e) {
                        toolResult = "Error ejecutando " + functionName + ": " + e.getMessage();
                        LOG.warning("[TOOL-CALLING] Error en " + functionName + ": " + e.getMessage());
                    }

                    ObjectNode toolMsg = JSON_MAPPER.createObjectNode();
                    toolMsg.put("role", "tool");
                    toolMsg.put("content", toolResult != null ? toolResult : "");
                    toolMsg.put("tool_call_id", callId);
                    messages.add(toolMsg);

                    LOG.info("[TOOL-CALLING] Resultado " + functionName + ": " + truncateForLog(toolResult, 80));
                }
            }

            LOG.warning("[TOOL-CALLING] Se alcanzo el limite de " + MAX_TOOL_CALL_TURNS + " turnos");
            return null;
        } catch (Exception e) {
            LOG.warning("[TOOL-CALLING] Error en ciclo: " + e.getMessage());
            return null;
        }
    }

    public List<JsonNode> extractTextToolCalls(String text) {
        List<JsonNode> calls = new ArrayList<>();
        if (text == null || text.isBlank()) return calls;

        String cleaned = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");

        int i = 0;
        while (i < cleaned.length()) {
            if (cleaned.charAt(i) == '{') {
                int depth = 0;
                int start = i;
                boolean inString = false;
                boolean escaped = false;

                for (int j = i; j < cleaned.length(); j++) {
                    char c = cleaned.charAt(j);
                    if (escaped) { escaped = false; continue; }
                    if (c == '\\') { escaped = true; continue; }
                    if (c == '"') { inString = !inString; continue; }
                    if (!inString) {
                        if (c == '{') depth++;
                        else if (c == '}') {
                            depth--;
                            if (depth == 0) {
                                String jsonStr = cleaned.substring(start, j + 1);
                                try {
                                    JsonNode node = JSON_MAPPER.readTree(jsonStr);
                                    if (node.has("name") && node.has("arguments")) {
                                        calls.add(node);
                                    }
                                } catch (Exception ignored) {  }
                                i = j + 1;
                                break;
                            }
                        }
                    }
                }
                if (depth != 0) break;
            } else {
                i++;
            }
        }
        return calls;
    }

    public String executeBareCommand(String input) {
        if (input == null || input.isBlank()) return null;

        String trimmed = input.trim();

        if (trimmed.length() > 500) return null;

        if (AGENT_CREATION_INTENT.matcher(trimmed).find()) {
            return executeAgentSynthesis(trimmed);
        }

        if (COMMIT_INTENT.matcher(trimmed).find()) {
            return executeCompositeCommit(trimmed);
        }

        java.util.regex.Matcher gitNlMatcher = java.util.regex.Pattern.compile(
            "(?:haz|ejecuta|muestra|corre|muestrame|dame|hazme|realiza|haz(?:me)?\\s+un)\\s+(?:un|el|la|los)?\\s*(git\\s+\\w+.*)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        ).matcher(trimmed);
        if (gitNlMatcher.find()) {
            String gitCmd = gitNlMatcher.group(1).trim();
            LOG.info("[FIX-K] NL→Git extraído: \"" + trimmed + "\" → \"" + gitCmd + "\"");
            return executeGitDirect(gitCmd);
        }

        if (trimmed.startsWith("git ")) {
            return executeGitDirect(trimmed);
        }

        String baseCmd = trimmed.split("\\s+")[0];
        if (!SAFE_BARE_COMMANDS.contains(baseCmd)) return null;

        if ("pwd".equals(baseCmd) && workingDirectory != null) {
            return workingDirectory.toAbsolutePath().normalize().toString();
        }

        int timeout = BUILD_COMMANDS.contains(baseCmd) ? 240 : 10;
        return executeShellDirect(trimmed, timeout);
    }

    private String executeAgentSynthesis(String userRequest) {
        String qmpPrompt = loadQuartermasterPrimePrompt();
        if (qmpPrompt == null) {
            return "[QMP] QuarterMasterPrime no disponible. Verifica qartermaster-prime-agent.yaml";
        }
        System.out.println("[QMP] QuarterMasterPrime procesando solicitud...");
        System.out.flush();
        return forceDirectResponse(qmpPrompt, userRequest);
    }

    @SuppressWarnings("unchecked")
    private String loadQuartermasterPrimePrompt() {
        Path yamlPath = Path.of(System.getProperty("user.home"),
            ".fararoni/config/agentes/qartermaster-prime-agent.yaml");
        if (!java.nio.file.Files.exists(yamlPath)) return null;
        try {
            String content = java.nio.file.Files.readString(yamlPath);
            var yaml = new org.yaml.snakeyaml.Yaml();
            java.util.Map<String, Object> data = yaml.load(content);
            if (data == null) return null;
            Object prompt = data.get("systemPrompt");
            return prompt != null ? prompt.toString() : null;
        } catch (java.io.IOException e) {
            LOG.warning("[QMP] Error leyendo YAML: " + e.getMessage());
            return null;
        }
    }

    public String executeGitDirect(String input) {
        String[] tokens = input.split("\\s+");
        if (tokens.length < 2) return null;

        String subcommand = tokens[1].toLowerCase();

        if (BLOCKED_GIT_SUBCOMMANDS.contains(subcommand)) {
            LOG.warning("[BARE-GIT] BLOQUEADO: " + subcommand + " (Anillo 7)");
            return "BLOQUEADO: 'git " + subcommand + "' no esta permitido por seguridad. " +
                "Fararoni opera solo en modo local.";
        }

        if ("reset".equals(subcommand) && input.contains("--hard")) {
            return "BLOQUEADO: 'git reset --hard' es una operacion destructiva.";
        }
        if ("clean".equals(subcommand) && input.contains("-f")) {
            return "BLOQUEADO: 'git clean -f' es una operacion destructiva.";
        }

        if (!SAFE_GIT_SUBCOMMANDS.contains(subcommand)) {
            return null;
        }

        if (!"init".equals(subcommand) && workingDirectory != null &&
            !java.nio.file.Files.exists(workingDirectory.resolve(".git"))) {
            LOG.info("[BARE-GIT] .git no existe, auto-inicializando antes de: " + input);
            String initResult = executeShellDirect("git init");
            if (initResult == null || initResult.contains("fatal")) {
                return "Error al inicializar git: " + initResult;
            }
            autoCreateGitignoreIfMissing();
        }

        LOG.info("[BARE-GIT] Ejecutando directo: " + input);
        return executeShellDirect(input);
    }

    public String executeCompositeCommit(String input) {
        if (workingDirectory == null) return null;

        if (!java.nio.file.Files.exists(workingDirectory.resolve(".git"))) {
            LOG.info("[COMPOSITE-GIT] .git no existe, auto-inicializando repositorio");
            String initResult = executeShellDirect("git init");
            if (initResult == null || initResult.contains("fatal")) {
                return "Error al inicializar git: " + initResult;
            }
            autoCreateGitignoreIfMissing();
            LOG.info("[COMPOSITE-GIT] git init + .gitignore creados automáticamente");
        }

        ensureGitIdentityComposite();

        LOG.info("[COMPOSITE-GIT] Ejecutando commit compuesto");

        String addResult = executeShellDirect("git add --all -- . :!.fararoni/");
        if (addResult == null) {
            return "Error al agregar archivos al stage.";
        }

        String statusResult = executeShellDirect("git diff --cached --stat");
        if (statusResult == null || statusResult.isBlank() || "(sin salida)".equals(statusResult)) {
            return "No hay cambios nuevos para guardar en git.";
        }

        String commitMsg = extractCommitMessage(input);
        String commitResult = executeShellDirect("git commit -m \"" + commitMsg + "\"");
        if (commitResult != null && commitResult.contains("create mode")) {
            LOG.info("[COMPOSITE-GIT] Commit exitoso");
        }

        return commitResult;
    }

    private void ensureGitIdentityComposite() {
        try {
            ProcessBuilder check = new ProcessBuilder("git", "config", "--local", "user.name");
            check.directory(workingDirectory.toFile());
            Process p = check.start();
            boolean hasIdentity = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
            if (hasIdentity) return;

            String userName = System.getProperty("user.name", "developer");
            String safeName = userName.replaceAll("[^a-zA-Z0-9 ._-]", "") + " (Fararoni Core)";
            String safeEmail = userName.toLowerCase().replaceAll("[^a-z0-9]", ".") + "@fararoni.local";

            new ProcessBuilder("git", "config", "--local", "user.name", safeName)
                .directory(workingDirectory.toFile()).start().waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            new ProcessBuilder("git", "config", "--local", "user.email", safeEmail)
                .directory(workingDirectory.toFile()).start().waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

            LOG.info("[COMPOSITE-GIT] Identidad tactica configurada: " + safeName + " <" + safeEmail + ">");
        } catch (Exception e) {
            LOG.warning("[COMPOSITE-GIT] No se pudo configurar identidad Git: " + e.getMessage());
        }
    }

    public String extractCommitMessage(String input) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "['\"]([^'\"]+)['\"]").matcher(input);
        if (m.find()) {
            return m.group(1);
        }

        String diffStat = executeShellDirect("git diff --cached --name-only");
        if (diffStat != null && !diffStat.isBlank() && !"(sin salida)".equals(diffStat)) {
            String[] files = diffStat.split("\n");
            String headCheck = executeShellDirect("git rev-parse HEAD");
            boolean isInitial = (headCheck == null || headCheck.contains("fatal") || headCheck.isBlank());

            if (isInitial) {
                return "chore(init): initial commit with " + files.length + " files";
            }

            String scope = inferScope(files);

            if (files.length == 1) {
                String filename = java.nio.file.Path.of(files[0]).getFileName().toString();
                return "chore(" + scope + "): update " + filename;
            }
            return "chore(" + scope + "): update " + files.length + " files";
        }
        return "chore: auto-commit by Fararoni";
    }

    private String inferScope(String[] files) {
        if (files.length == 0) return "project";

        java.util.Set<String> parentDirs = new java.util.HashSet<>();
        for (String file : files) {
            java.nio.file.Path p = java.nio.file.Path.of(file);
            if (p.getParent() != null) {
                java.nio.file.Path parent = p.getParent();
                String dirName = parent.getFileName().toString();
                parentDirs.add(dirName);
            } else {
                parentDirs.add("root");
            }
        }

        if (parentDirs.size() == 1) {
            return parentDirs.iterator().next();
        }

        if (files.length > 5) return "project";
        return parentDirs.iterator().next();
    }

    public void autoCreateGitignoreIfMissing() {
        if (workingDirectory == null) return;
        java.nio.file.Path gitignore = workingDirectory.resolve(".gitignore");
        try {
            if (!java.nio.file.Files.exists(gitignore)) {
                java.nio.file.Files.writeString(gitignore,
                    "# Fararoni System Files\n.fararoni/\n\n" +
                    "# Java/Maven\ntarget/\n*.class\n*.jar\n*.log\n\n" +
                    "# IDE\n.idea/\n*.iml\n.vscode/\n");
                LOG.info("[COMPOSITE-GIT] .gitignore creado automaticamente");
            } else {
                String content = java.nio.file.Files.readString(gitignore);
                if (!content.contains(".fararoni/")) {
                    java.nio.file.Files.writeString(gitignore,
                        content + "\n# Fararoni System Files\n.fararoni/\n",
                        java.nio.file.StandardOpenOption.APPEND);
                }
            }
        } catch (Exception e) {
            LOG.warning("[COMPOSITE-GIT] Error creando .gitignore: " + e.getMessage());
        }
    }

    public String executeShellDirect(String command) {
        return executeShellDirect(command, 10);
    }

    public String executeShellDirect(String command, int timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            if (workingDirectory != null) {
                pb.directory(workingDirectory.toFile());
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "[TIMEOUT] Comando excedio " + timeoutSeconds + " segundos: " + command;
            }

            return output.isBlank() ? "(sin salida)" : output;
        } catch (Exception e) {
            LOG.warning("[SHELL] Error ejecutando '" + command + "': " + e.getMessage());
            return null;
        }
    }

    public String truncateForLog(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    public boolean isOllamaEndpoint(VllmClient client) {
        if (client == null) return false;
        String baseUrl = client.getBaseUrl();
        return baseUrl != null && baseUrl.contains(":11434");
    }

    public String executeWithOllamaThinking(String prompt, VllmClient client) {
        if (ollamaProviderForThinking == null) {
            synchronized (this) {
                if (ollamaProviderForThinking == null) {
                    ollamaProviderForThinking = new OllamaProvider(client.getBaseUrl());
                }
            }
        }

        String modelName = client.getModelName();
        StringBuilder response = new StringBuilder();
        StringBuilder thinking = new StringBuilder();

        ollamaProviderForThinking.inferStreaming(
            modelName,
            "",
            prompt,
            new StreamingLlmCallback() {
                @Override
                public void onToken(String token) {
                    if (token == null) return;

                    if (token.startsWith(AppDefaults.PREFIX_THOUGHT)) {
                        String thoughtContent = token.substring(AppDefaults.PREFIX_THOUGHT.length());
                        thinking.append(thoughtContent);
                        if (dev.fararoni.core.cli.ui.ThinkingTelemetry.hasActiveInstance()) {
                            dev.fararoni.core.cli.ui.ThinkingTelemetry.sendToken(thoughtContent);
                        } else if (thinkingStreamCallback != null) {
                            thinkingStreamCallback.accept(thoughtContent);
                        } else {
                            System.out.print("\033[3;90m" + thoughtContent + "\033[0m");
                            System.out.flush();
                        }
                    } else {
                        response.append(token);
                    }
                }

                @Override
                public void onComplete(String fullResponse) {
                }

                @Override
                public void onError(Throwable error) {
                    LOG.warning("[THINKING] Error en streaming: " + error.getMessage());
                }
            }
        );

        if (!thinking.isEmpty()) {
            return "[THOUGHT]" + thinking.toString() + "\n\n" + response.toString();
        }
        return response.toString();
    }

    public boolean containsEmbeddedToolCall(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }

        return (response.contains("{\"tool\"") ||
                response.contains("{\"function\"") ||
                (response.contains("{\"action\"") && response.contains("\"exec\"")));
    }

    public String extractEmbeddedJson(String response) {
        if (response == null) return null;

        int jsonStart = response.indexOf("{\"tool\"");
        if (jsonStart == -1) {
            jsonStart = response.indexOf("{\"function\"");
        }
        if (jsonStart == -1) {
            jsonStart = response.indexOf("{\"action\"");
        }
        if (jsonStart == -1) {
            return null;
        }

        int depth = 0;
        int jsonEnd = -1;
        for (int i = jsonStart; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    jsonEnd = i + 1;
                    break;
                }
            }
        }

        if (jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd);
        }
        return null;
    }

    public String executeEmbeddedToolCall(String responseWithJson, String originalQuery) {
        try {
            LOG.info("[REACT-LOOP] Detectado JSON de herramienta embebido, ejecutando...");

            String jsonOnly = extractEmbeddedJson(responseWithJson);
            if (jsonOnly == null) {
                LOG.warning("[REACT-LOOP] No se pudo extraer JSON de: " + responseWithJson);
                return "No pude procesar la respuesta.";
            }

            var jsonNode = JSON_MAPPER.readTree(jsonOnly);

            String tool = jsonNode.has("tool") ? jsonNode.get("tool").asText() : null;
            String action = jsonNode.has("action") ? jsonNode.get("action").asText() : null;
            var params = jsonNode.has("params") ? jsonNode.get("params") : null;

            String toolResult = null;

            if (toolExecutor != null) {
                String embeddedToolName = null;
                String embeddedArgs = null;

                if ("GitAction".equalsIgnoreCase(tool) || "gitaction".equalsIgnoreCase(tool)
                    || ("git".equalsIgnoreCase(tool) && action != null)) {
                    embeddedToolName = "GitAction";
                    var argsNode = JSON_MAPPER.createObjectNode();
                    if ("git".equalsIgnoreCase(tool) && action != null) {
                        argsNode.put("action", action);
                        if (params != null && params.has("args")) {
                            argsNode.put("params", params.get("args").asText());
                        } else if (params != null && params.isTextual()) {
                            argsNode.put("params", params.asText());
                        }
                    } else {
                        if (action != null) argsNode.put("action", action);
                        if (params != null) {
                            if (params.isTextual()) argsNode.put("params", params.asText());
                            else if (params.has("params")) argsNode.put("params", params.get("params").asText());
                            else argsNode.set("params", params);
                        }
                    }
                    embeddedArgs = argsNode.toString();
                }

                if (embeddedToolName != null) {
                    LOG.info("[REACT-LOOP] Redirigiendo JSON embebido a ToolExecutor: " + embeddedToolName);
                    var syntheticCall = new dev.fararoni.core.core.clients.AgentClient.ToolCall(embeddedToolName, embeddedArgs);
                    var result = toolExecutor.executeTool(syntheticCall);
                    return result.message();
                }
            }

            if ("system".equals(tool) && "exec".equals(action) && params != null) {
                String command = params.has("command") ? params.get("command").asText() : "";

                if ("date".equalsIgnoreCase(command) || command.startsWith("date")) {
                    toolResult = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm:ss",
                            new java.util.Locale("es", "ES")));
                } else if ("whoami".equalsIgnoreCase(command)) {
                    toolResult = System.getProperty("user.name");
                } else if ("pwd".equalsIgnoreCase(command)) {
                    toolResult = workingDirectory != null ? workingDirectory.toString() : System.getProperty("user.dir");
                } else if (command.startsWith("mkdir")) {
                    String dir = command.replace("mkdir -p ", "").replace("mkdir ", "").trim();
                    java.nio.file.Path targetDir = workingDirectory != null
                        ? workingDirectory.resolve(dir) : java.nio.file.Path.of(dir);
                    try {
                        java.nio.file.Files.createDirectories(targetDir);
                        toolResult = "Directorio creado: " + targetDir;
                        LOG.info("[REACT-LOOP] mkdir: " + targetDir);
                    } catch (Exception e) {
                        toolResult = "Error creando directorio: " + e.getMessage();
                    }
                } else {
                    toolResult = "[Sistema] Comando '" + command + "' no permitido en modo embebido.";
                }
            } else if ("datetime".equals(tool) || "date".equals(tool)) {
                toolResult = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm:ss",
                        new java.util.Locale("es", "ES")));
            }

            if (toolResult != null) {
                LOG.info("[REACT-LOOP] Herramienta ejecutada exitosamente: " + tool);
                return formatToolResultForUser(originalQuery, toolResult);
            }

            LOG.warning("[REACT-LOOP] No se pudo mapear herramienta embebida: " + tool);
            return "No pude ejecutar esa operación. Intenta ser más específico.";
        } catch (Exception e) {
            LOG.warning("[REACT-LOOP] Error parseando JSON embebido: " + e.getMessage());
            return "Ocurrió un error procesando tu solicitud.";
        }
    }

    public String formatToolResultForUser(String originalQuery, String toolResult) {
        String query = originalQuery != null ? originalQuery.toLowerCase() : "";

        if (query.contains("día") || query.contains("fecha") || query.contains("hoy")) {
            return "Hoy es " + toolResult + ".";
        } else if (query.contains("hora") || query.contains("tiempo")) {
            return "Son las " + toolResult + ".";
        } else if (query.contains("quien") || query.contains("usuario")) {
            return "Tu usuario es: " + toolResult;
        } else if (query.contains("donde") || query.contains("directorio") || query.contains("carpeta")) {
            return "Estás en: " + toolResult;
        }

        return toolResult;
    }

    public boolean isCognitiveToolCall(String toolName) {
        if (toolName == null) return false;
        String lower = toolName.toLowerCase();
        return lower.equals("enterplanmode") ||
                lower.equals("exitplanmode") ||
                lower.equals("planmode");
    }

    public String executeCognitiveToolContinuation(
            String systemPrompt,
            String userPrompt,
            ToolCall toolCall,
            ToolExecutionResult toolResult,
            List<ObjectNode> tools,
            ToolAwareTelemetry monitor) {
        try {
            String continuationPrompt = contextVault.buildCognitivePrompt(userPrompt, toolCall, toolResult);
            var cogClient = resolveAgenticClient(null);
            AgentResponse continuationResponse = cogClient.generateWithTools(
                    systemPrompt,
                    continuationPrompt,
                    tools
            );

            if (continuationResponse.isToolCall()) {
                String nextTool = continuationResponse.toolCall().functionName();

                if (isCognitiveToolCall(nextTool)) {
                    LOG.warning("[REACT-LOOP] Bucle infinito de planificacion. Forzando respuesta directa.");
                    return forceDirectResponse(systemPrompt, userPrompt);
                }

                if (!isClaudePreferred()) {
                    toolExecutor.setCurrentUserRequest(userPrompt);
                }
                ToolExecutionResult nextResult = toolExecutor.executeTool(continuationResponse.toolCall(), monitor);
                return nextResult.message();
            }

            String finalText = continuationResponse.textContent();
            if (finalText != null && containsEmbeddedToolCall(finalText)) {
                return executeEmbeddedToolCall(finalText, userPrompt);
            }

            return finalText != null ? finalText : toolResult.message();
        } catch (Exception e) {
            LOG.severe("[REACT-LOOP] Error en continuación: " + e.getMessage());
            return toolResult.message();
        }
    }

    public String executeToolResultContinuation(
            String systemPrompt,
            String userPrompt,
            ToolCall toolCall,
            ToolExecutionResult toolResult,
            List<ObjectNode> tools,
            ToolAwareTelemetry monitor) {
        if (isEnvironmentError(toolResult.message())) {
            traceToFile("FIX-E-GATE → ENVIRONMENT ERROR detected, routing to FIX-E");
            LOG.info("[FIX-E] Error de entorno/runtime detectado. Derivando a carril FIX-E.");
            return executeEnvironmentRepairContinuation(
                systemPrompt, userPrompt, toolCall, toolResult, tools, monitor);
        }

        final int MAX_REACT_DEPTH = 10;
        ToolExecutionResult currentResult = toolResult;

        try {
            ArrayNode messages = JSON_MAPPER.createArrayNode();

            ObjectNode sysMsg = JSON_MAPPER.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);

            ObjectNode userMsg = JSON_MAPPER.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);

            String syntheticId = "call_react_" + System.currentTimeMillis() % 10000;
            ObjectNode assistantMsg = JSON_MAPPER.createObjectNode();
            assistantMsg.put("role", "assistant");
            assistantMsg.putNull("content");
            ArrayNode toolCallsArr = assistantMsg.putArray("tool_calls");
            ObjectNode callObj = toolCallsArr.addObject();
            callObj.put("id", syntheticId);
            callObj.put("type", "function");
            ObjectNode funcObj = callObj.putObject("function");
            funcObj.put("name", toolCall.functionName());
            funcObj.put("arguments", toolCall.argumentsJson());
            messages.add(assistantMsg);

            String distilledOutput = BuildOutputDistiller.distill(toolResult.message());

            IRepairDirective repairDirective = repairDirectiveResolver.resolve(distilledOutput);
            traceToFile("REPAIR-DIRECTIVE resolved=" + repairDirective.getClass().getSimpleName()
                + " distilledLen=" + (distilledOutput != null ? distilledOutput.length() : 0));
            if (distilledOutput != null) {
                StringBuilder errorLines = new StringBuilder();
                for (String line : distilledOutput.split("\n")) {
                    if (line.contains("[ERROR]") || line.contains("error:") || line.contains("cannot find symbol")
                        || line.contains("Caused by:") || line.contains("Exception") || line.contains("APPLICATION FAILED TO START")) {
                        errorLines.append(line).append("\\n");
                    }
                }
                traceToFile("DISTILLED-ERRORS: " + (errorLines.length() > 0 ? errorLines.toString() : "(no error lines found)"));
            }
            if (!isClaudePreferred()) {
                String customProtocol = repairDirective.initialProtocol();
                if (customProtocol != null) {
                    distilledOutput += "\n\n" + customProtocol;
                } else {
                    distilledOutput += "\n\n[PROTOCOLO DE REPARACIÓN — OBLIGATORIO]\n"
                        + "1. Usa fs_read para leer el archivo con errores.\n"
                        + "2. Usa fs_patch para corregir TODOS los errores en UNA sola llamada. "
                        + "Agrupa todas las correcciones (imports, métodos, tipos) en un solo patch.\n"
                        + "3. Usa ShellCommand para recompilar y verificar que los errores desaparecieron.\n"
                        + "PROHIBIDO: NO recompiles sin haber parcheado primero. "
                        + "NO expliques los errores — corrígelos directamente con herramientas. "
                        + "Si corriges un archivo, verifica la compilación inmediatamente después.";
                }
            }

            ObjectNode toolMsg = JSON_MAPPER.createObjectNode();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", syntheticId);
            toolMsg.put("content", distilledOutput);
            messages.add(toolMsg);

            currentResult = toolResult;
            String lastToolUsed = toolCall.functionName();
            int patchFailCount = 0;
            String lastReadFilePath = null;
            boolean distilled = false;

            for (int depth = 0; depth < MAX_REACT_DEPTH; depth++) {
                if (!distilled && !isClaudePreferred() && (patchFailCount == 2 || depth == 6)) {
                    int beforeDistill = messages.size();
                    int removed = MessageHistoryDistiller.distill(messages, 3);
                    distilled = true;
                    traceToFile("CONTEXT-DISTILL before=" + beforeDistill + " after=" + messages.size()
                        + " removed=" + removed + " trigger=" + (patchFailCount >= 2 ? "PATCH-FAIL" : "DEPTH-6"));
                }

                if (!isClaudePreferred()) {
                    injectCertaintyDirective(messages, lastToolUsed, currentResult.message(), repairDirective, currentResult.success(), patchFailCount, lastReadFilePath);
                }

                final int MAX_INTERCEPTOR_RETRIES = 2;
                int interceptorRetries = 0;
                ToolCall nextCall = null;
                boolean terminated = false;

                while (interceptorRetries <= MAX_INTERCEPTOR_RETRIES) {
                    LOG.info("[REACT-CONTINUATION] Depth " + (depth + 1) + "/" + MAX_REACT_DEPTH
                        + " — msgs=" + messages.size() + " lastTool=" + lastToolUsed
                        + " retry=" + interceptorRetries);
                    traceToFile("REACT depth=" + (depth+1) + " msgs=" + messages.size()
                        + " lastTool=" + lastToolUsed + " retry=" + interceptorRetries
                        + " toolsCount=" + tools.size() + " mode=ONE-SHOT+INTERCEPTOR");

                    var continuationClient = (claudePreferred && claudeClient != null)
                        ? (dev.fararoni.core.core.clients.AgentClient) claudeClient
                        : agenticClient;
                    String toolChoice = isClaudePreferred() ? "auto" : "required";
                    AgentResponse response = continuationClient.generateWithFullHistory(
                        messages, tools, toolChoice);

                    ToolCall rawCall = null;
                    if (response.isToolCall()) {
                        rawCall = response.toolCall();
                        traceToFile("REACT depth=" + (depth+1) + " rawCall=NATIVE tool=" + rawCall.functionName());
                    } else {
                        String content = response.textContent();
                        boolean hasName = content != null && content.contains("\"name\"");
                        boolean hasArgs = content != null && content.contains("\"arguments\"");
                        traceToFile("REACT depth=" + (depth+1) + " EXTRACTOR-CHECK hasName=" + hasName
                            + " hasArgs=" + hasArgs + " contentLen=" + (content != null ? content.length() : 0));
                        if (hasName && hasArgs) {
                            LOG.warning("[REACT-FIX] LLM mezcló texto con JSON en depth " + (depth + 1));
                            rawCall = extractToolCallFromText(content);
                            traceToFile("REACT depth=" + (depth+1) + " EXTRACTOR-RESULT="
                                + (rawCall != null ? "SUCCESS tool=" + rawCall.functionName() : "FAILED"));
                        }
                    }

                    if (rawCall == null) {
                        String text = response.textContent();
                        LOG.info("[REACT-CONTINUATION] LLM respondió solo con texto en depth " + (depth + 1));
                        traceToFile("REACT depth=" + (depth+1) + " → TERMINATED (text only). Preview="
                            + (text != null ? text.substring(0, Math.min(150, text.length())).replace("\n", "\\n") : "null"));
                        terminated = true;
                        break;
                    }

                    String normalizedName = normalizeToolName(rawCall.functionName());
                    traceToFile("INTERCEPTOR raw=" + rawCall.functionName() + " normalized=" + normalizedName);

                    if (isClaudePreferred()) {
                        nextCall = new ToolCall(normalizedName, rawCall.argumentsJson());
                        traceToFile("INTERCEPTOR → BYPASSED (Claude) tool=" + normalizedName);
                        break;
                    }

                    java.util.Set<String> allowedNow = getReactAllowedTools(lastToolUsed);
                    traceToFile("INTERCEPTOR state=POST_" + lastToolUsed.toUpperCase()
                        + " allowed=" + allowedNow + " requested=" + normalizedName);

                    if (allowedNow.contains(normalizedName)) {
                        nextCall = new ToolCall(normalizedName, rawCall.argumentsJson());
                        traceToFile("INTERCEPTOR → ACCEPTED tool=" + normalizedName);
                        break;
                    } else {
                        interceptorRetries++;
                        LOG.warning("[INTERCEPTOR] RECHAZO #" + interceptorRetries
                            + " en depth " + (depth+1) + ": " + rawCall.functionName()
                            + " (norm=" + normalizedName + ") no permitido en POST_"
                            + lastToolUsed.toUpperCase() + ". Válidas: " + allowedNow);
                        traceToFile("INTERCEPTOR → REJECTED #" + interceptorRetries
                            + " tool=" + normalizedName + " allowed=" + allowedNow);

                        if (interceptorRetries > MAX_INTERCEPTOR_RETRIES) {
                            LOG.warning("[INTERCEPTOR] Budget agotado en depth " + (depth+1)
                                + ". Ejecutando " + normalizedName + " como fallback.");
                            traceToFile("INTERCEPTOR → FALLBACK (budget exhausted) executing=" + normalizedName);
                            nextCall = new ToolCall(normalizedName, rawCall.argumentsJson());
                            break;
                        }

                        String rejectId = "call_reject_" + depth + "_" + interceptorRetries + "_" + System.currentTimeMillis() % 10000;

                        ObjectNode rejectAssistant = JSON_MAPPER.createObjectNode();
                        rejectAssistant.put("role", "assistant");
                        rejectAssistant.putNull("content");
                        ArrayNode rejectToolCalls = rejectAssistant.putArray("tool_calls");
                        ObjectNode rejectCallObj = rejectToolCalls.addObject();
                        rejectCallObj.put("id", rejectId);
                        rejectCallObj.put("type", "function");
                        ObjectNode rejectFuncObj = rejectCallObj.putObject("function");
                        rejectFuncObj.put("name", rawCall.functionName());
                        rejectFuncObj.put("arguments", rawCall.argumentsJson());
                        messages.add(rejectAssistant);

                        ObjectNode rejectToolMsg = JSON_MAPPER.createObjectNode();
                        rejectToolMsg.put("role", "tool");
                        rejectToolMsg.put("tool_call_id", rejectId);
                        rejectToolMsg.put("content",
                            "[ERROR-INTERCEPTOR] La herramienta '" + rawCall.functionName()
                            + "' NO está permitida en este momento.\n"
                            + "Estado actual: acabas de ejecutar '" + lastToolUsed + "'.\n"
                            + "Herramientas válidas ahora: " + allowedNow + "\n"
                            + "DEBES usar una de las herramientas válidas. "
                            + "NO repitas '" + rawCall.functionName() + "'.");
                        messages.add(rejectToolMsg);
                    }
                }

                if (terminated) {
                    String text = currentResult.message();
                    return text;
                }

                if (nextCall == null) {
                    return currentResult.message();
                }

                LOG.info("[REACT-CONTINUATION] Depth " + (depth + 1) + ": ejecutando " + nextCall.functionName());

                if (!isClaudePreferred()) {
                    String normalizedNext = normalizeToolName(nextCall.functionName());
                    if (!"fs_read".equalsIgnoreCase(normalizedNext)) {
                        toolExecutor.setCurrentUserRequest(userPrompt);
                    } else {
                        toolExecutor.setCurrentUserRequest(null);
                    }
                }
                ToolExecutionResult nextResult = toolExecutor.executeTool(nextCall, monitor);
                currentResult = nextResult;
                lastToolUsed = nextCall.functionName();

                String normalizedForTrace = normalizeToolName(nextCall.functionName());
                if ("fs_patch".equalsIgnoreCase(normalizedForTrace) || "fs_write".equalsIgnoreCase(normalizedForTrace)) {
                    String msgPreview = nextResult.message() != null
                        ? nextResult.message().substring(0, Math.min(200, nextResult.message().length())).replace("\n", "\\n")
                        : "null";
                    traceToFile("PATCH-RESULT success=" + nextResult.success()
                        + " msgPreview=" + msgPreview);
                    if (!nextResult.success()) {
                        patchFailCount++;
                        traceToFile("PATCH-FAIL-COUNT=" + patchFailCount);
                    }
                }

                if ("fs_read".equalsIgnoreCase(normalizedForTrace) && nextResult.success()) {
                    try {
                        JsonNode readArgs = JSON_MAPPER.readTree(nextCall.argumentsJson());
                        if (readArgs.has("path")) {
                            lastReadFilePath = readArgs.get("path").asText();
                            traceToFile("LAST-READ-PATH=" + lastReadFilePath);
                        }
                    } catch (Exception ignored) {}
                }

                if (!nextResult.success() && nextResult.message() != null
                    && nextResult.message().contains("[Kill Switch] BLOQUEADO")
                    && "fs_write".equalsIgnoreCase(normalizedForTrace)) {
                    try {
                        com.fasterxml.jackson.databind.node.ObjectNode retryArgs =
                            (com.fasterxml.jackson.databind.node.ObjectNode) JSON_MAPPER.readTree(nextCall.argumentsJson());
                        String ksPath = retryArgs.has("path") ? retryArgs.get("path").asText() : "unknown";
                        retryArgs.put("force_destruction",
                            "FIX-H: REPAIR_MODE — reduccion de tamano autorizada por Kernel (limpieza de duplicidad)");
                        ToolCall overrideCall = new ToolCall(nextCall.functionName(),
                            JSON_MAPPER.writeValueAsString(retryArgs));
                        traceToFile("FIX-H SAFE-OVERRIDE re-executing immediately for: " + ksPath);
                        ToolExecutionResult overrideResult = toolExecutor.executeTool(overrideCall, monitor);
                        if (overrideResult.success()) {
                            nextResult = overrideResult;
                            currentResult = overrideResult;
                            traceToFile("FIX-H SAFE-OVERRIDE SUCCESS for: " + ksPath);
                        } else {
                            traceToFile("FIX-H SAFE-OVERRIDE FAILED for: " + ksPath
                                + " msg=" + (overrideResult.message() != null
                                    ? overrideResult.message().substring(0, Math.min(200, overrideResult.message().length()))
                                    : "null"));
                        }
                    } catch (Exception e) {
                        LOG.warning("[FIX-H] Error en re-ejecucion inmediata: " + e.getMessage());
                    }
                }

                String nextId = "call_react_" + (depth + 1) + "_" + System.currentTimeMillis() % 10000;

                ObjectNode nextAssistant = JSON_MAPPER.createObjectNode();
                nextAssistant.put("role", "assistant");
                nextAssistant.putNull("content");
                ArrayNode nextToolCalls = nextAssistant.putArray("tool_calls");
                ObjectNode nextCallObj = nextToolCalls.addObject();
                nextCallObj.put("id", nextId);
                nextCallObj.put("type", "function");
                ObjectNode nextFuncObj = nextCallObj.putObject("function");
                nextFuncObj.put("name", nextCall.functionName());
                nextFuncObj.put("arguments", nextCall.argumentsJson());
                messages.add(nextAssistant);

                String nextContent;
                String normalizedForContent = normalizeToolName(nextCall.functionName());
                if ("ShellCommand".equalsIgnoreCase(normalizedForContent)) {
                    nextContent = BuildOutputDistiller.distill(nextResult.message());
                    if (!isClaudePreferred() && nextContent != null && nextContent.contains("BUILD FAILURE")) {
                        String custom = repairDirective.afterShellFailure();
                        nextContent += "\n\n" + (custom != null ? custom
                            : "[PROTOCOLO] Lee el archivo con errores (fs_read) y luego corrígelo (fs_patch).");
                    }
                } else if ("fs_read".equalsIgnoreCase(normalizedForContent)) {
                    nextContent = truncateForLog(nextResult.message(), 4000);
                    if (!isClaudePreferred()) {
                        String custom = repairDirective.afterRead();
                        nextContent += "\n\n" + (custom != null ? custom
                            : "[DIRECTIVA] Archivo leído. Ahora usa fs_patch para corregir TODOS los errores. NO leas otro archivo.");
                    }
                } else if ("fs_patch".equalsIgnoreCase(normalizedForContent) || "fs_write".equalsIgnoreCase(normalizedForContent)) {
                    nextContent = truncateForLog(nextResult.message(), 2000);
                    String custom = repairDirective.afterPatch();
                    nextContent += "\n\n" + (custom != null ? custom
                        : "[DIRECTIVA] Parche aplicado. Ahora usa ShellCommand con 'mvn compile' para verificar que los errores se resolvieron.");
                } else {
                    nextContent = truncateForLog(nextResult.message(), 4000);
                }

                ObjectNode nextToolMsg = JSON_MAPPER.createObjectNode();
                nextToolMsg.put("role", "tool");
                nextToolMsg.put("tool_call_id", nextId);
                nextToolMsg.put("content", nextContent);
                messages.add(nextToolMsg);

                if ("ShellCommand".equalsIgnoreCase(normalizedForContent) && nextResult.success()
                    && nextResult.message() != null && nextResult.message().contains("[exit_code: 0")) {
                    LOG.info("[REACT-CONTINUATION] Build exitoso en depth " + (depth + 1));

                    String originalCommand = toolCall.argumentsJson();
                    IPostFixStrategy strategy = postFixResolver.resolve(userPrompt, originalCommand);
                    String continuation = strategy.getContinuationDirective(userPrompt, originalCommand);

                    if (continuation == null) {
                        return forceDirectResponse(systemPrompt,
                            "Corregiste los errores de compilación exitosamente. " +
                            "Resume brevemente al usuario qué errores encontraste y cómo los corregiste.");
                    }

                    traceToFile("POSTFIX-STRATEGY class=" + strategy.getClass().getSimpleName()
                        + " → continuing loop with directive");
                    LOG.info("[POSTFIX] Aplicando " + strategy.getClass().getSimpleName());
                    ObjectNode transitionMsg = JSON_MAPPER.createObjectNode();
                    transitionMsg.put("role", "system");
                    transitionMsg.put("content", continuation);
                    messages.add(transitionMsg);
                    lastToolUsed = "ShellCommand";
                }
            }

            LOG.warning("[REACT-CONTINUATION] Máxima profundidad ReAct alcanzada (" + MAX_REACT_DEPTH + ")");
            return forceDirectResponse(systemPrompt,
                "Intentaste corregir los errores de compilación pero no se resolvieron completamente. " +
                "Último resultado: " + truncateForLog(currentResult.message(), 2000) +
                "\nExplica al usuario qué errores quedan y cómo solucionarlos manualmente.");
        } catch (Exception e) {
            LOG.severe("[REACT-CONTINUATION] Error en continuación: " + e.getMessage());
            traceToFile("REACT-EXCEPTION class=" + e.getClass().getSimpleName()
                + " msg=" + e.getMessage()
                + " cause=" + (e.getCause() != null ? e.getCause().getMessage() : "none"));
            return currentResult != null ? currentResult.message() : toolResult.message();
        }
    }

    private static final String[] ENVIRONMENT_ERROR_PATTERNS = {
        "Unsupported class file major version",
        "java.lang.UnsupportedClassVersionError",
        "ClassNotFoundException",
        "NoClassDefFoundError",
        "DependencyResolutionException",
        "PluginExecutionException",
        "Could not resolve dependencies",
        "Cannot resolve plugin",
        "incompatible types: cannot",
        "has been compiled by a more recent version",
        "class file has wrong version",
        "UnsupportedOperationException: The Security Manager is deprecated",
        "Unable to make field",
        "module java.base does not",
    };

    private boolean isEnvironmentError(String buildOutput) {
        if (buildOutput == null || buildOutput.isBlank()) return false;
        for (String pattern : ENVIRONMENT_ERROR_PATTERNS) {
            if (buildOutput.contains(pattern)) {
                traceToFile("FIX-E-CLASSIFIER match='" + pattern + "'");
                return true;
            }
        }
        return false;
    }

    private String executeEnvironmentRepairContinuation(
            String systemPrompt,
            String userPrompt,
            ToolCall toolCall,
            ToolExecutionResult toolResult,
            List<ObjectNode> tools,
            ToolAwareTelemetry monitor) {
        final int MAX_FIXE_DEPTH = 12;
        ToolExecutionResult currentResult = toolResult;

        try {
            ArrayNode messages = JSON_MAPPER.createArrayNode();

            ObjectNode sysMsg = JSON_MAPPER.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);

            ObjectNode userMsg = JSON_MAPPER.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);

            String syntheticId = "call_fixe_" + System.currentTimeMillis() % 10000;
            ObjectNode assistantMsg = JSON_MAPPER.createObjectNode();
            assistantMsg.put("role", "assistant");
            assistantMsg.putNull("content");
            ArrayNode toolCallsArr = assistantMsg.putArray("tool_calls");
            ObjectNode callObj = toolCallsArr.addObject();
            callObj.put("id", syntheticId);
            callObj.put("type", "function");
            ObjectNode funcObj = callObj.putObject("function");
            funcObj.put("name", toolCall.functionName());
            funcObj.put("arguments", toolCall.argumentsJson());
            messages.add(assistantMsg);

            String distilledOutput = BuildOutputDistiller.distill(toolResult.message());

            if (!isClaudePreferred()) {
                distilledOutput += "\n\n[PROTOCOLO FIX-E: REPARACIÓN DE ENTORNO Y DEPENDENCIAS — OBLIGATORIO]\n"
                    + "1. DIAGNÓSTICO: El error detectado NO es de sintaxis de código, es un error fatal de "
                    + "incompatibilidad de versiones, dependencias o tiempo de ejecución (Runtime).\n"
                    + "2. INSPECCIÓN: Usa 'fs_read' para leer ÚNICAMENTE el archivo de configuración del proyecto "
                    + "(ej. pom.xml o build.gradle). NO busques archivos .java.\n"
                    + "3. ANÁLISIS DE VERSIÓN: Revisa la versión de Java del sistema (ej. major version 69 = Java 25) "
                    + "y compárala con las versiones declaradas en el pom.xml (ej. Spring Boot). "
                    + "Identifica la incompatibilidad.\n"
                    + "4. RESOLUCIÓN: Usa 'fs_patch' en el pom.xml para actualizar las versiones de las librerías "
                    + "a una versión compatible.\n"
                    + "5. VERIFICACIÓN: Usa 'ShellCommand' para ejecutar nuevamente el comando original de despliegue "
                    + "una vez aplicado el parche.\n\n"
                    + "[PROTOCOLO FIX-E: REPARACIÓN DE CÓDIGO SEVERAMENTE CORRUPTO]\n"
                    + "Si al leer un archivo fuente (.java, .ts, .py, etc.) detectas estos patrones:\n"
                    + "- Bloques de código DUPLICADOS o TRIPLICADOS (el mismo return/builder repetido varias veces)\n"
                    + "- Métodos con cuerpo INCORRECTO (ej. un método de lectura que tiene código de escritura)\n"
                    + "- Imports FALTANTES (ej. clases usadas sin import correspondiente)\n"
                    + "- Líneas CONCATENADAS (código basura pegado después de un punto y coma)\n"
                    + "SOLUCIÓN: Usa fs_patch para REESCRIBIR los métodos corruptos COMPLETOS. "
                    + "NO intentes agregar punto y coma — ELIMINA el código duplicado y REESCRIBE el método limpio. "
                    + "Agrega los imports faltantes al inicio del archivo.";
            }

            ObjectNode toolMsg = JSON_MAPPER.createObjectNode();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", syntheticId);
            toolMsg.put("content", distilledOutput);
            messages.add(toolMsg);

            String lastToolUsed = toolCall.functionName();

            for (int depth = 0; depth < MAX_FIXE_DEPTH; depth++) {
                if (!isClaudePreferred()) {
                    injectFixECertaintyDirective(messages, lastToolUsed);
                }

                LOG.info("[FIX-E] Depth " + (depth + 1) + "/" + MAX_FIXE_DEPTH
                    + " — msgs=" + messages.size() + " lastTool=" + lastToolUsed);
                traceToFile("FIX-E depth=" + (depth + 1) + " msgs=" + messages.size()
                    + " lastTool=" + lastToolUsed + " mode=ENVIRONMENT-REPAIR");

                var continuationClient = (claudePreferred && claudeClient != null)
                    ? (dev.fararoni.core.core.clients.AgentClient) claudeClient
                    : agenticClient;
                String toolChoice = isClaudePreferred() ? "auto" : "required";
                AgentResponse response = continuationClient.generateWithFullHistory(
                    messages, tools, toolChoice);

                ToolCall rawCall = null;
                if (response.isToolCall()) {
                    rawCall = response.toolCall();
                    traceToFile("FIX-E depth=" + (depth + 1) + " rawCall=NATIVE tool=" + rawCall.functionName());
                } else {
                    String content = response.textContent();
                    if (content != null && content.contains("\"name\"") && content.contains("\"arguments\"")) {
                        rawCall = extractToolCallFromText(content);
                        traceToFile("FIX-E depth=" + (depth + 1) + " EXTRACTOR-RESULT="
                            + (rawCall != null ? "SUCCESS tool=" + rawCall.functionName() : "FAILED"));
                    }
                }

                if (rawCall == null) {
                    LOG.info("[FIX-E] LLM respondió solo con texto en depth " + (depth + 1));
                    traceToFile("FIX-E depth=" + (depth + 1) + " → TERMINATED (text only)");
                    return currentResult.message();
                }

                String normalizedName = normalizeToolName(rawCall.functionName());
                ToolCall nextCall = new ToolCall(normalizedName, rawCall.argumentsJson());

                if (!isClaudePreferred() && !"fs_read".equalsIgnoreCase(normalizedName)) {
                    toolExecutor.setCurrentUserRequest(userPrompt);
                } else if (!isClaudePreferred()) {
                    toolExecutor.setCurrentUserRequest(null);
                }
                ToolExecutionResult nextResult = toolExecutor.executeTool(nextCall, monitor);
                currentResult = nextResult;
                lastToolUsed = nextCall.functionName();

                String nextId = "call_fixe_" + (depth + 1) + "_" + System.currentTimeMillis() % 10000;

                ObjectNode nextAssistant = JSON_MAPPER.createObjectNode();
                nextAssistant.put("role", "assistant");
                nextAssistant.putNull("content");
                ArrayNode nextToolCalls = nextAssistant.putArray("tool_calls");
                ObjectNode nextCallObj = nextToolCalls.addObject();
                nextCallObj.put("id", nextId);
                nextCallObj.put("type", "function");
                ObjectNode nextFuncObj = nextCallObj.putObject("function");
                nextFuncObj.put("name", nextCall.functionName());
                nextFuncObj.put("arguments", nextCall.argumentsJson());
                messages.add(nextAssistant);

                String nextContent;
                String normalizedForContent = normalizeToolName(nextCall.functionName());
                if ("ShellCommand".equalsIgnoreCase(normalizedForContent)) {
                    nextContent = BuildOutputDistiller.distill(nextResult.message());
                    if (!isClaudePreferred() && nextContent != null
                        && (nextContent.contains("BUILD FAILURE") || nextContent.contains("[exit_code: 1"))) {
                        nextContent += "\n\n[FIX-E] El error persiste. Revisa si el pom.xml fue parcheado correctamente. "
                            + "Usa fs_read para releer la configuración y fs_patch para ajustar las versiones.";
                    }
                } else if ("fs_read".equalsIgnoreCase(normalizedForContent)) {
                    nextContent = truncateForLog(nextResult.message(), 4000);
                    if (!isClaudePreferred()) {
                        nextContent += "\n\n[FIX-E] Archivo de configuración leído. Analiza las versiones declaradas. "
                            + "Usa fs_patch para actualizar las dependencias incompatibles. "
                            + "NO busques archivos .java — el problema es de configuración, no de código.";
                    }
                } else if ("fs_patch".equalsIgnoreCase(normalizedForContent) || "fs_write".equalsIgnoreCase(normalizedForContent)) {
                    nextContent = truncateForLog(nextResult.message(), 2000);
                    nextContent += "\n\n[FIX-E] Configuración parcheada. Ahora usa ShellCommand para ejecutar "
                        + "el comando original de despliegue y verificar que el error se resolvió.";
                } else {
                    nextContent = truncateForLog(nextResult.message(), 4000);
                }

                ObjectNode nextToolMsg = JSON_MAPPER.createObjectNode();
                nextToolMsg.put("role", "tool");
                nextToolMsg.put("tool_call_id", nextId);
                nextToolMsg.put("content", nextContent);
                messages.add(nextToolMsg);

                if ("ShellCommand".equalsIgnoreCase(normalizedForContent) && nextResult.success()
                    && nextResult.message() != null && nextResult.message().contains("[exit_code: 0")) {
                    LOG.info("[FIX-E] Build exitoso en depth " + (depth + 1));
                    traceToFile("FIX-E-SUCCESS depth=" + (depth + 1) + " → checking PostFixStrategy");

                    String originalCommand = toolCall.argumentsJson();
                    IPostFixStrategy strategy = postFixResolver.resolve(userPrompt, originalCommand);
                    String continuation = strategy.getContinuationDirective(userPrompt, originalCommand);

                    if (continuation == null) {
                        return forceDirectResponse(systemPrompt,
                            "Corregiste el error de entorno/configuración exitosamente. " +
                            "Resume brevemente al usuario qué incompatibilidad encontraste y cómo la resolviste.");
                    }

                    traceToFile("FIX-E-POSTFIX class=" + strategy.getClass().getSimpleName());
                    LOG.info("[FIX-E-POSTFIX] Aplicando " + strategy.getClass().getSimpleName());
                    ObjectNode transitionMsg = JSON_MAPPER.createObjectNode();
                    transitionMsg.put("role", "system");
                    transitionMsg.put("content", continuation);
                    messages.add(transitionMsg);
                    lastToolUsed = "ShellCommand";
                }
            }

            LOG.warning("[FIX-E] Máxima profundidad FIX-E alcanzada (" + MAX_FIXE_DEPTH + ")");
            return forceDirectResponse(systemPrompt,
                "Intentaste corregir el error de entorno/configuración pero no se resolvió completamente. " +
                "Último resultado: " + truncateForLog(currentResult.message(), 2000) +
                "\nExplica al usuario qué incompatibilidad queda y cómo solucionarla manualmente.");
        } catch (Exception e) {
            LOG.severe("[FIX-E] Error en continuación: " + e.getMessage());
            traceToFile("FIX-E-EXCEPTION class=" + e.getClass().getSimpleName()
                + " msg=" + e.getMessage()
                + " cause=" + (e.getCause() != null ? e.getCause().getMessage() : "none"));
            return currentResult != null ? currentResult.message() : toolResult.message();
        }
    }

    private void injectFixECertaintyDirective(ArrayNode messages, String lastToolUsed) {
        String normalized = normalizeToolName(lastToolUsed);
        String directive = switch (normalized) {
            case "fs_read" ->
                "PROTOCOL-MANDATE: You have read the configuration file. "
                + "Analyze the declared versions (Java, Spring Boot, frameworks). "
                + "Identify the version incompatibility and apply the fix using 'fs_patch'. "
                + "Do NOT open .java source files — this is a configuration/dependency issue.";
            case "fs_patch", "fs_write" ->
                "PROTOCOL-MANDATE: Configuration has been patched successfully. "
                + "Now verify the fix by running the ORIGINAL deployment command via ShellCommand. "
                + "Do NOT read more files — execute the command now.";
            default ->
                "PROTOCOL-MANDATE: Environment/Runtime error detected. "
                + "Use fs_read to inspect the project configuration file (pom.xml, build.gradle, package.json). "
                + "Do NOT open .java source files. The error is in the build configuration, not in the code.";
        };

        ObjectNode sysDirective = JSON_MAPPER.createObjectNode();
        sysDirective.put("role", "system");
        sysDirective.put("content", directive);
        messages.add(sysDirective);
        traceToFile("FIX-E INJECTED state=POST_" + normalized.toUpperCase()
            + " directive=" + directive.substring(0, Math.min(80, directive.length())) + "...");
    }

    private String normalizeToolName(String rawName) {
        if (rawName == null) return "UNKNOWN";
        return switch (rawName.toLowerCase()) {
            case "fs_read", "readfile", "read" -> "fs_read";
            case "fs_write", "writefile", "write" -> "fs_write";
            case "fs_patch", "edit" -> "fs_patch";
            case "shellcommand", "bash", "shell_execute" -> "ShellCommand";
            default -> rawName;
        };
    }

    private void injectCertaintyDirective(ArrayNode messages, String lastToolUsed, String lastToolOutput,
                                          IRepairDirective repairDirective, boolean lastToolSuccess,
                                          int patchFailCount) {
        String normalized = normalizeToolName(lastToolUsed);
        String directive = switch (normalized) {
            case "fs_read" -> {
                if (patchFailCount >= 2) {
                    yield "PROTOCOL-MANDATE: File analysis is complete. "
                        + "fs_patch has FAILED " + patchFailCount + " times — it is now FORBIDDEN. "
                        + "You MUST use fs_write to REWRITE the entire file with all corrections. "
                        + "EXACT FORMAT: {\"path\": \"relative/path/File.java\", \"content\": \"complete file content\"} "
                        + "Only two fields: 'path' and 'content'. Include EVERY line of the file. "
                        + "Do NOT attempt fs_patch — it WILL be rejected.";
                }
                String custom = repairDirective.certitudeAfterRead();
                yield custom != null ? custom
                    : "PROTOCOL-MANDATE: File analysis is complete. "
                    + "You MUST now apply the fix using the 'fs_patch' tool. "
                    + "Group ALL corrections (imports, methods, types) into a SINGLE fs_patch call. "
                    + "Do NOT re-read the file. Do NOT compile yet. Apply the fix NOW.";
            }
            case "fs_patch", "fs_write" -> {
                if (!lastToolSuccess && lastToolOutput != null
                    && lastToolOutput.contains("[Kill Switch] BLOQUEADO")) {
                    String ksDirective = repairDirective.certitudeAfterKillSwitchBlock();
                    yield ksDirective != null ? ksDirective
                        : "PROTOCOL-MANDATE: Security block detected. Retry the same fs_write — override authorized.";
                }
                if (!lastToolSuccess && lastToolOutput != null
                    && lastToolOutput.contains("requiere 'path' y 'content'")) {
                    String argsDirective = repairDirective.certitudeAfterWriteArgsError();
                    yield argsDirective != null ? argsDirective
                        : "PROTOCOL-MANDATE: fs_write requires exactly {\"path\": \"...\", \"content\": \"...\"}. Fix the JSON.";
                }
                if (!lastToolSuccess && patchFailCount >= 2) {
                    yield "PROTOCOL-MANDATE: fs_patch has FAILED " + patchFailCount + " times. "
                        + "It is now PERMANENTLY FORBIDDEN for this repair cycle. "
                        + "You MUST use fs_write to REWRITE the entire file. "
                        + "EXACT FORMAT: {\"path\": \"relative/path/File.java\", \"content\": \"complete file content\"} "
                        + "Only two fields: 'path' and 'content'. Include EVERY line of the file. "
                        + "Do NOT attempt fs_patch again — it WILL fail for the same reason.";
                }
                if (!lastToolSuccess) {
                    String failDirective = repairDirective.certitudeAfterPatchFailure();
                    yield failDirective != null ? failDirective
                        : "PROTOCOL-MANDATE: Your patch FAILED. Re-read the file with fs_read and try again.";
                }
                String custom = repairDirective.certitudeAfterPatch();
                yield custom != null ? custom
                    : "PROTOCOL-MANDATE: Your patch has been applied to disk successfully. "
                    + "If there are MORE errors to fix in this or another file, use fs_patch again. "
                    + "If you have fixed ALL errors, verify by running 'mvn compile' via ShellCommand. "
                    + "Do NOT re-read a file you already patched.";
            }
            default -> {
                boolean isDeployShortcut = lastToolOutput != null
                    && lastToolOutput.contains("[DEPLOY-SHORTCUT]");
                if (isDeployShortcut) {
                    yield "PROTOCOL-MANDATE: BUILD SUCCESS detected but you ONLY compiled the project. "
                        + "The user requested DEPLOYMENT — a running service, not just compiled classes. "
                        + "Examine the project config (pom.xml, package.json, angular.json, requirements.txt, Cargo.toml, go.mod) "
                        + "to determine the correct START/RUN command and execute it via ShellCommand. "
                        + "A successful compilation does NOT complete a deployment request.";
                }

                boolean isRuntimeFailure = lastToolOutput != null
                    && !lastToolOutput.contains("[ERROR]")
                    && (lastToolOutput.contains("Caused by:")
                        || lastToolOutput.contains("APPLICATION FAILED TO START")
                        || lastToolOutput.contains("Exception"));
                if (isRuntimeFailure) {
                    String rtDirective = repairDirective.certitudeAfterRuntimeFailure();
                    yield rtDirective != null ? rtDirective
                        : "PROTOCOL-MANDATE: Runtime failure detected. Find 'Caused by:' in the output and fix the root cause.";
                }

                String symbols = extractMissingSymbols(lastToolOutput);
                String symbolHint = symbols.isEmpty() ? "" : " Missing symbols detected: [" + symbols + "].";
                yield "PROTOCOL-MANDATE: Build failed." + symbolHint
                    + " Use fs_read to analyze the file that USES the missing symbol to understand its API (fields, methods, constructor). "
                    + "Then CREATE the missing class/file with fs_write. "
                    + "Do NOT re-run the build without creating the missing file first.";
            }
        };

        ObjectNode sysDirective = JSON_MAPPER.createObjectNode();
        sysDirective.put("role", "system");
        sysDirective.put("content", directive);
        messages.add(sysDirective);
        traceToFile("FIX-G INJECTED state=POST_" + normalized.toUpperCase()
            + " directive=" + directive.substring(0, Math.min(80, directive.length())) + "...");
    }

    private void injectCertaintyDirective(ArrayNode messages, String lastToolUsed, String lastToolOutput,
                                          IRepairDirective repairDirective, boolean lastToolSuccess,
                                          int patchFailCount, String lastReadFilePath) {
        injectCertaintyDirective(messages, lastToolUsed, lastToolOutput,
            repairDirective, lastToolSuccess, patchFailCount);

        if (lastReadFilePath != null && patchFailCount >= 2) {
            String normalized = normalizeToolName(lastToolUsed);
            if ("fs_read".equalsIgnoreCase(normalized)
                || ("fs_write".equalsIgnoreCase(normalized) && !lastToolSuccess)
                || ("fs_patch".equalsIgnoreCase(normalized) && !lastToolSuccess)) {
                ObjectNode hardFormat = JSON_MAPPER.createObjectNode();
                hardFormat.put("role", "system");
                hardFormat.put("content",
                    "[HARD-FORMAT] Use EXACTLY this path — do NOT invent another: "
                    + "{\"path\": \"" + lastReadFilePath + "\", \"content\": \"...complete file...\"}");
                messages.add(hardFormat);
                traceToFile("HARD-FORMAT-INJECT path=" + lastReadFilePath);
            }
        }
    }

    private String extractMissingSymbols(String buildOutput) {
        if (buildOutput == null || buildOutput.isBlank()) return "";
        java.util.Set<String> missing = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("symbol:\\s+(?:class|variable|method)\\s+(\\w+)")
            .matcher(buildOutput);
        while (m.find()) {
            missing.add(m.group(1));
        }
        m = java.util.regex.Pattern.compile("'(\\w+)'\\s+was not declared|unknown type name '(\\w+)'")
            .matcher(buildOutput);
        while (m.find()) {
            String sym = m.group(1) != null ? m.group(1) : m.group(2);
            if (sym != null) missing.add(sym);
        }
        m = java.util.regex.Pattern.compile("Cannot find name '(\\w+)'")
            .matcher(buildOutput);
        while (m.find()) {
            missing.add(m.group(1));
        }
        return String.join(", ", missing);
    }

    private java.util.Set<String> getReactAllowedTools(String lastToolUsed) {
        String normalized = normalizeToolName(lastToolUsed);
        return switch (normalized) {
            case "fs_read" ->
                java.util.Set.of("fs_patch", "fs_write");
            case "fs_patch", "fs_write" ->
                java.util.Set.of("ShellCommand", "fs_patch", "fs_write", "fs_read");
            default ->
                java.util.Set.of("fs_read", "fs_patch", "fs_write", "ShellCommand");
        };
    }

    private ToolCall extractToolCallFromText(String text) {
        try {
            int startIndex = text.indexOf('{');
            if (startIndex == -1) {
                traceToFile("EXTRACTOR first-brace=NOT_FOUND textLen=" + text.length());
                return null;
            }
            traceToFile("EXTRACTOR first-brace-at=" + startIndex + " textLen=" + text.length());

            int depth = 0;
            boolean inString = false;
            boolean escape = false;
            int blocksFound = 0;

            for (int i = startIndex; i < text.length(); i++) {
                char c = text.charAt(i);

                if (escape) { escape = false; continue; }
                if (c == '\\' && inString) { escape = true; continue; }
                if (c == '"') { inString = !inString; continue; }

                if (!inString) {
                    if (c == '{') depth++;
                    if (c == '}') depth--;

                    if (depth == 0) {
                        blocksFound++;
                        String jsonPart = text.substring(startIndex, i + 1);
                        traceToFile("EXTRACTOR block#" + blocksFound + " start=" + startIndex + " end=" + i
                            + " len=" + jsonPart.length() + " preview=" + jsonPart.substring(0, Math.min(100, jsonPart.length())).replace("\n", "\\n"));
                        try {
                            JsonNode node = JSON_MAPPER.readTree(jsonPart);

                            if (node.has("name") && node.has("arguments")) {
                                String toolName = node.get("name").asText();
                                String args = node.get("arguments").isTextual()
                                    ? node.get("arguments").asText()
                                    : node.get("arguments").toString();

                                traceToFile("EXTRACTOR SUCCESS tool=" + toolName + " argsLen=" + args.length());
                                LOG.info("[REACT-FIX] Tool call extraído: " + toolName);
                                return new ToolCall(toolName, args);
                            }
                            traceToFile("EXTRACTOR block#" + blocksFound + " valid JSON but no name/arguments");
                        } catch (Exception parseEx) {
                            traceToFile("EXTRACTOR block#" + blocksFound + " parse failed: " + parseEx.getMessage());
                        }
                        startIndex = text.indexOf('{', i + 1);
                        if (startIndex == -1) {
                            traceToFile("EXTRACTOR no more braces after block#" + blocksFound);
                            return null;
                        }
                        i = startIndex - 1;
                        depth = 0;
                    }
                }
            }
            traceToFile("EXTRACTOR exhausted text without finding tool call. blocks=" + blocksFound);
        } catch (Exception e) {
            traceToFile("EXTRACTOR global exception: " + e.getMessage());
            LOG.warning("[REACT-FIX] Error inesperado en extracción: " + e.getMessage());
        }
        return null;
    }

    public String forceDirectResponse(String systemPrompt, String userPrompt) {
        try {
            LOG.info("[REACT-LOOP] Forzando respuesta directa sin herramientas...");

            var forceClient = resolveAgenticClient(null);
            AgentResponse response = forceClient.generateWithTools(
                systemPrompt + "\n\n[MODO FORZADO] NO uses ninguna herramienta. Responde DIRECTAMENTE con texto.",
                userPrompt,
                List.of()
            );

            return response.textContent() != null ? response.textContent() :
                   "No pude generar una respuesta. Por favor, intenta reformular tu pregunta.";
        } catch (Exception e) {
            LOG.severe("[REACT-LOOP] Error forzando respuesta: " + e.getMessage());
            return "Error procesando tu solicitud.";
        }
    }

    public boolean isCognitiveToolTextResponse(String textResponse) {
        if (textResponse == null || textResponse.isBlank()) {
            return false;
        }

        String trimmed = textResponse.trim().toLowerCase();

        return trimmed.equals("enterplanmode") ||
               trimmed.equals("exitplanmode") ||
               trimmed.equals("planmode") ||
               trimmed.startsWith("enterplanmode") ||
               trimmed.startsWith("exitplanmode") ||
               trimmed.matches("^enterplanmode\\s*\\(?\\)?.*") ||
               trimmed.matches("^exitplanmode\\s*\\(?\\)?.*");
    }

    public String handleCognitiveTextResponse(
            String systemPrompt,
            String userPrompt,
            String cognitiveText,
            List<ObjectNode> tools) {
        try {
            LOG.info("[REACT-LOOP] Forzando continuacion despues de texto cognitivo...");

            String forcedPrompt = """
                [CONTEXTO PREVIO]
                El usuario solicitó: "%s"

                [ESTADO ACTUAL]
                Indicaste que querías entrar en modo planificación ("%s").
                El sistema ya cargó todo el contexto necesario (~15k caracteres).

                [INSTRUCCIÓN OBLIGATORIA]
                NO repitas "EnterPlanMode". El modo ya está activo.
                GENERA LA RESPUESTA AHORA basándote en el contexto cargado.

                Comienza directamente con: "Basado en el análisis del proyecto..."
                """.formatted(userPrompt, cognitiveText.trim());

            var cogForceClient = resolveAgenticClient(null);
            AgentResponse response = cogForceClient.generateWithTools(
                systemPrompt + "\n\n[MODO FORZADO] Responde DIRECTAMENTE. NO uses herramientas.",
                forcedPrompt,
                List.of()
            );

            String result = response.textContent();

            if (result != null && isCognitiveToolTextResponse(result)) {
                LOG.warning("[REACT-LOOP] Modelo insiste en texto cognitivo. Generando respuesta manual.");
                return contextVault.generateFallbackAnalysis(userPrompt);
            }

            return result != null ? result : "No pude generar el análisis. Intenta con una pregunta más específica.";
        } catch (Exception e) {
            LOG.severe("[REACT-LOOP] Error manejando texto cognitivo: " + e.getMessage());
            return "Error procesando tu solicitud de análisis.";
        }
    }

    public synchronized void hotSwapRabbitClient(String newUrl, String newModel, HyperNativeKernel kernel) {
        LOG.info("[HOT-SWAP] Iniciando secuencia de cambio a: " + newModel + " en " + newUrl);

        VllmClient candidate = null;
        try {
            ConfigPriorityResolver resolver = new ConfigPriorityResolver();
            String apiKey = resolver.resolveApiKey(null);

            int contextWindow = newModel.contains("35b") || newModel.contains("32b") || newModel.contains("70b")
                ? AppDefaults.TURTLE_CONTEXT_WINDOW
                : AppDefaults.RABBIT_CONTEXT_WINDOW;

            CliConfig candidateConfig = CliConfig.builder()
                .serverUrl(newUrl)
                .modelName(newModel)
                .contextWindow(contextWindow)
                .maxTokens(contextWindow > 16000 ? 8192 : 2048)
                .build();

            candidate = new VllmClient(
                newUrl,
                apiKey,
                newModel,
                candidateConfig,
                new EstimationTokenizer()
            );

            LOG.info("[HOT-SWAP] Cliente candidato instanciado");
        } catch (Exception e) {
            throw new IllegalStateException(
                "[ERROR] FALLO EN INSTANCIACION: " + e.getMessage(), e);
        }

        try {
            LOG.info("[HOT-SWAP] Validando conexion de red...");
            LlmClient.ServerStatus status = candidate.checkServerStatus();

            if (!status.isAlive()) {
                throw new IllegalStateException(
                    "El servidor " + newUrl + " no responde: " + status.errorMessage());
            }

            LOG.info("[HOT-SWAP] Servidor accesible: " + status.version());
        } catch (IllegalStateException e) {
            candidate.close();
            throw e;
        } catch (Exception e) {
            candidate.close();
            throw new IllegalStateException(
                "[ERROR] VALIDACION DE RED FALLIDA: " + e.getMessage(), e);
        }

        try {
            LOG.info("[HOT-SWAP] Ejecutando prueba de fuego (inferencia real)...");

            var testRequest = GenerationRequest.builder()
                .model(newModel)
                .messages(List.of(Message.user("ping")))
                .maxTokens(5)
                .temperature(0.0)
                .build();

            var testResponse = candidate.generate(testRequest);

            if (testResponse == null || testResponse.text() == null) {
                throw new IllegalStateException("El modelo generó respuesta vacía");
            }

            LOG.info("[HOT-SWAP] Prueba de fuego exitosa: modelo operativo");
        } catch (IllegalStateException e) {
            candidate.close();
            throw e;
        } catch (Exception e) {
            candidate.close();
            throw new IllegalStateException(
                "[ERROR] PRUEBA DE FUEGO FALLIDA (el modelo no puede inferir): " + e.getMessage(), e);
        }

        try {
            SecureConfigService config = SecureConfigService.getInstance();
            config.setProperty("rabbit.server.url", newUrl);
            config.setProperty("rabbit.model.name", newModel);
            config.setProperty("rabbit.mode", "REMOTE");
            LOG.info("[HOT-SWAP] Configuración persistida");
        } catch (Exception e) {
            candidate.close();
            throw new IllegalStateException(
                "[ERROR] ERROR DE E/S: No se pudo guardar la configuracion. Abortando.", e);
        }

        VllmClient veteran = this.fastClient;
        LocalLlmService oldNative = this.localLlmService;

        this.fastClient = candidate;
        this.localLlmService = null;

        if (kernel != null) {
            kernel.hotSwapRabbitClient(candidate, newModel);
        }

        this.currentRabbitMode = (newModel.contains("35b") || newModel.contains("32b") || newModel.contains("70b"))
            ? FararoniCore.RabbitMode.REMOTE_LARGE
            : FararoniCore.RabbitMode.REMOTE_SMALL;

        if (this.currentRabbitMode == FararoniCore.RabbitMode.REMOTE_LARGE) {
            ConfigPriorityResolver resolverForAgentic = new ConfigPriorityResolver();
            String agenticApiKey = resolverForAgentic.resolveApiKey(null);
            int rabbitTimeout = (newModel.contains("35b") || newModel.contains("32b") || newModel.contains("70b"))
                ? AppDefaults.DEFAULT_TURTLE_TIMEOUT_SECONDS
                : AppDefaults.DEFAULT_CLOUD_TIMEOUT_SECONDS;
            this.rabbitAgenticClient = new OpenAICompatibleClient(newUrl, agenticApiKey, newModel, calculateDynamicTimeout(rabbitTimeout));
            LOG.info("[HOT-SWAP] Cliente agéntico Rabbit creado: " + newModel + " (timeout=" + rabbitTimeout + "s, dynamic)");
        } else {
            this.rabbitAgenticClient = null;
        }

        if (veteran != null) {
            try {
                veteran.close();
                LOG.info("[HOT-SWAP] Cliente anterior desechado");
            } catch (Exception e) {
                LOG.warning("[HOT-SWAP] Warning al cerrar cliente anterior: " + e.getMessage());
            }
        }

        if (oldNative != null) {
            try {
                oldNative.close();
                LOG.info("[HOT-SWAP] Motor nativo desechado");
            } catch (Exception e) {
                LOG.warning("[HOT-SWAP] Warning al cerrar motor nativo: " + e.getMessage());
            }
        }

        LOG.info("[HOT-SWAP] MISION CUMPLIDA. Sistema reconfigurado.");
        LOG.info("[HOT-SWAP] Nuevo Enlace: " + newUrl);
        LOG.info("[HOT-SWAP] Nuevo Cerebro: " + newModel);
    }

    public synchronized void restoreNativeRabbit() {
        LOG.info("[HOT-SWAP] Iniciando restauración a motor nativo...");

        LocalLlmService newNative = initializeNativeEngine();
        if (newNative == null) {
            throw new IllegalStateException(
                "Motor GGUF no disponible. ¿Está instalado el modelo en ~/.fararoni/models/?");
        }

        if (!newNative.isNativeAvailable()) {
            throw new IllegalStateException(
                "Librería nativa (libjllama) no disponible. Verifique la instalación.");
        }

        try {
            SecureConfigService config = SecureConfigService.getInstance();
            config.setProperty("rabbit.mode", "NATIVE");
            config.removeProperty("rabbit.server.url");
            config.removeProperty("rabbit.model.name");
        } catch (Exception e) {
            LOG.warning("[HOT-SWAP] No se pudo persistir config: " + e.getMessage());
        }

        LocalLlmService oldNative = this.localLlmService;
        VllmClient oldRemote = this.fastClient;

        this.localLlmService = newNative;
        this.currentRabbitMode = FararoniCore.RabbitMode.NATIVE;
        this.rabbitAgenticClient = null;

        if (oldNative != null && oldNative != newNative) {
            try {
                oldNative.close();
            } catch (Exception ignored) {}
        }

        LOG.info("[HOT-SWAP] Motor nativo restaurado. Modo: NATIVE");
    }

    public synchronized void hotSwapTurtleClient(String newUrl, String newModel, HyperNativeKernel kernel) {
        LOG.info("[TURTLE-SWAP] Iniciando cambio de Turtle a: " + newModel + " en " + newUrl);

        VllmClient candidate;
        try {
            ConfigPriorityResolver resolver = new ConfigPriorityResolver();
            String apiKey = resolver.resolveApiKey(null);

            CliConfig candidateConfig = CliConfig.builder()
                .serverUrl(newUrl)
                .modelName(newModel)
                .contextWindow(AppDefaults.TURTLE_CONTEXT_WINDOW)
                .maxTokens(8192)
                .build();

            candidate = new VllmClient(
                newUrl, apiKey, newModel, candidateConfig, new EstimationTokenizer());

            LOG.info("[TURTLE-SWAP] Cliente candidato instanciado");
        } catch (Exception e) {
            throw new IllegalStateException("[TURTLE-SWAP] Fallo en instanciacion: " + e.getMessage(), e);
        }

        try {
            LlmClient.ServerStatus status = candidate.checkServerStatus();
            if (!status.isAlive()) {
                throw new IllegalStateException("Servidor " + newUrl + " no responde: " + status.errorMessage());
            }
            LOG.info("[TURTLE-SWAP] Servidor accesible: " + status.version());
        } catch (IllegalStateException e) {
            candidate.close();
            throw e;
        } catch (Exception e) {
            candidate.close();
            throw new IllegalStateException("[TURTLE-SWAP] Validacion de red fallida: " + e.getMessage(), e);
        }

        try {
            var testRequest = GenerationRequest.builder()
                .model(newModel)
                .messages(List.of(Message.user("ping")))
                .maxTokens(5)
                .temperature(0.0)
                .build();
            var testResponse = candidate.generate(testRequest);
            if (testResponse == null || testResponse.text() == null) {
                throw new IllegalStateException("El modelo genero respuesta vacia");
            }
            LOG.info("[TURTLE-SWAP] Prueba de fuego exitosa");
        } catch (IllegalStateException e) {
            candidate.close();
            throw e;
        } catch (Exception e) {
            candidate.close();
            throw new IllegalStateException("[TURTLE-SWAP] Prueba de fuego fallida: " + e.getMessage(), e);
        }

        try {
            SecureConfigService config = SecureConfigService.getInstance();
            config.setProperty("turtle.server.url", newUrl);
            config.setProperty("turtle.model.name", newModel);
            config.setProperty("turtle.mode", "REMOTE");
            LOG.info("[TURTLE-SWAP] Configuracion persistida");
        } catch (Exception e) {
            candidate.close();
            throw new IllegalStateException("[TURTLE-SWAP] Error de persistencia: " + e.getMessage(), e);
        }

        VllmClient veteran = this.expertClient;
        this.expertClient = candidate;

        this.claudePreferred = false;

        try {
            ConfigPriorityResolver resolverAgentic = new ConfigPriorityResolver();
            String agenticApiKey = resolverAgentic.resolveApiKey(null);
            int turtleTimeout = (newModel.contains("35b") || newModel.contains("32b") || newModel.contains("70b"))
                ? AppDefaults.DEFAULT_TURTLE_TIMEOUT_SECONDS
                : AppDefaults.DEFAULT_CLOUD_TIMEOUT_SECONDS;
            this.agenticClient = new OpenAICompatibleClient(newUrl, agenticApiKey, newModel, calculateDynamicTimeout(turtleTimeout));
            LOG.info("[TURTLE-SWAP] Cliente agentico actualizado: " + newModel + " (timeout=" + turtleTimeout + "s, dynamic)");
        } catch (Exception e) {
            LOG.warning("[TURTLE-SWAP] No se pudo actualizar cliente agentico: " + e.getMessage());
        }

        if (kernel != null) {
            kernel.hotSwapTurtleClient(candidate, newModel);
        }

        if (veteran != null && veteran != candidate) {
            try { veteran.close(); } catch (Exception e) {
                LOG.warning("[TURTLE-SWAP] Warning al cerrar cliente anterior: " + e.getMessage());
            }
        }

        LOG.info("[TURTLE-SWAP] MISION CUMPLIDA. Turtle reconfigurado: " + newModel + " @ " + newUrl);
    }

    public synchronized void activateClaudeAsTurtle(String apiKey, String modelName) {
        LOG.info("[CLAUDE-SWAP] Activando Claude como Turtle: " + modelName);

        this.claudeClient = new AnthropicClient(apiKey, modelName);
        this.claudePreferred = true;

        try {
            SecureConfigService config = SecureConfigService.getInstance();
            config.setSecureProperty("claude.api.key", apiKey);
            config.setProperty("claude.model", modelName);
            config.setProperty("claude.mode", "ACTIVE");
            config.setProperty("turtle.mode", "CLAUDE");
            LOG.info("[CLAUDE-SWAP] Configuracion persistida");
        } catch (Exception e) {
            LOG.warning("[CLAUDE-SWAP] No se pudo persistir config: " + e.getMessage());
        }

        LOG.info("[CLAUDE-SWAP] Claude activado como Turtle: " + modelName);
    }

    public synchronized void deactivateClaudeAsTurtle() {
        LOG.info("[CLAUDE-SWAP] Desactivando Claude como Turtle");
        this.claudePreferred = false;

        try {
            SecureConfigService config = SecureConfigService.getInstance();
            config.setProperty("claude.mode", "INACTIVE");
            config.setProperty("turtle.mode", "REMOTE");
        } catch (Exception e) {
            LOG.warning("[CLAUDE-SWAP] No se pudo persistir config: " + e.getMessage());
        }
    }

    public String executeWithClaude(String prompt) {
        if (claudeClient == null) {
            return executeExpertWithFallbackSilent(prompt);
        }
        try {
            AgentResponse resp = claudeClient.generateWithTools("", prompt, List.of());
            String text = resp.textContent();
            if (text != null && !text.isBlank()) {
                return text;
            }
            LOG.warning("[CLAUDE-CHAT] Respuesta vacia, fallback a Turtle Ollama");
            return executeExpertWithFallbackSilent(prompt);
        } catch (Exception e) {
            LOG.warning("[CLAUDE-CHAT] Error: " + e.getMessage() + ", fallback a Turtle Ollama");
            return executeExpertWithFallbackSilent(prompt);
        }
    }

    public boolean isClaudePreferred() { return claudePreferred && claudeClient != null; }

    public String getClaudeModelName() {
        return claudeClient != null ? claudeClient.getModelName() : "";
    }

    public EnterpriseRouter getRouter() {
        return enterpriseRouter;
    }

    public LocalLlmService getLocalLlmService() {
        return localLlmService;
    }

    public boolean isUsingLocalModel() {
        return lastRoutingTarget == RoutingPlan.TargetModel.LOCAL;
    }

    public RoutingPlan.TargetModel getLastRoutingTarget() {
        return lastRoutingTarget;
    }

    public void setLastRoutingTarget(RoutingPlan.TargetModel target) {
        this.lastRoutingTarget = target;
    }

    public boolean isRemoteModelAvailable() {
        return expertClient != null;
    }

    public String getTurtleModelName() {
        if (claudePreferred && claudeClient != null) {
            return claudeClient.getModelName();
        }
        if (expertClient != null) {
            return expertClient.getModelName();
        }
        return AppDefaults.DEFAULT_TURTLE_MODEL;
    }

    public String getRabbitModelName() {
        if (currentRabbitMode == FararoniCore.RabbitMode.NATIVE) {
            return AppDefaults.DEFAULT_RABBIT_MODEL;
        }
        if (fastClient != null) {
            return fastClient.getModelName();
        }
        return AppDefaults.DEFAULT_RABBIT_MODEL;
    }

    public boolean isNativeEngineReady() {
        return localLlmService != null &&
               localLlmService.isNativeAvailable() &&
               localLlmService.isModelLoaded();
    }

    public boolean isAgenticModeAvailable() {
        return agenticClient != null && toolRegistry != null && toolExecutor != null;
    }

    public FararoniCore.RabbitMode getCurrentRabbitMode() {
        return currentRabbitMode;
    }

    public void setThinkingStreamCallback(java.util.function.Consumer<String> callback) {
        this.thinkingStreamCallback = callback;
    }

    public VllmClient getFastClient() { return fastClient; }
    public VllmClient getExpertClient() { return expertClient; }
    public OpenAICompatibleClient getAgenticClient() { return agenticClient; }
    public AnthropicClient getClaudeClient() { return claudeClient; }
    public ToolRegistry getToolRegistry() { return toolRegistry; }
    public ToolExecutor getToolExecutor() { return toolExecutor; }

    public String resolveEffectiveModelName(RoutingPlan.TargetModel routedTarget) {
        if (claudePreferred && claudeClient != null) {
            return claudeClient.getModelName();
        }
        if (currentRabbitMode == FararoniCore.RabbitMode.REMOTE_LARGE && fastClient != null) {
            return fastClient.getModelName();
        }
        return expertClient != null ? expertClient.getModelName() : AppDefaults.DEFAULT_TURTLE_MODEL;
    }

    public dev.fararoni.core.core.clients.AgentClient resolveAgenticClient(RoutingPlan.TargetModel routedTarget) {
        if (claudeClient != null && claudePreferred) {
            LOG.info("[AGENTIC-RESOLVE] Claude API (preferred)");
            return claudeClient;
        }
        if (currentRabbitMode == FararoniCore.RabbitMode.REMOTE_LARGE
                && rabbitAgenticClient != null) {
            LOG.info("[AGENTIC-RESOLVE] Auto: Rabbit LARGE para tool calling");
            return rabbitAgenticClient;
        }
        return agenticClient;
    }

    public void shutdownResources() {
        LOG.info("[DISPATCHER] Shutdown de recursos LLM...");
        if (notaryAuditListener != null) {
            try { notaryAuditListener.stop(); } catch (Exception e) {
                LOG.warning("[DISPATCHER] Error al detener NotaryAuditListener: " + e.getMessage());
            }
        }
        if (localLlmService != null) {
            try { localLlmService.close(); } catch (Exception e) {
                LOG.warning("[DISPATCHER] Error al cerrar localLlmService: " + e.getMessage());
            }
        }
        if (fastClient != null) {
            try { fastClient.close(); } catch (Exception e) {
                LOG.warning("[DISPATCHER] Error al cerrar fastClient: " + e.getMessage());
            }
        }
        if (expertClient != null) {
            try { expertClient.close(); } catch (Exception e) {
                LOG.warning("[DISPATCHER] Error al cerrar expertClient: " + e.getMessage());
            }
        }
    }
}
