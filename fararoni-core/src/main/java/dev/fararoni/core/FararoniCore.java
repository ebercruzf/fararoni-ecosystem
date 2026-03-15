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
package dev.fararoni.core;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.fararoni.core.client.VllmClient;
import dev.fararoni.core.core.clients.OpenAICompatibleClient;
import dev.fararoni.core.core.skills.ToolRegistry;
import dev.fararoni.core.core.skills.ToolExecutor;
import dev.fararoni.core.config.ConfigPriorityResolver;
import dev.fararoni.core.core.security.auth.FaraSecurityInterceptor;
import dev.fararoni.core.core.security.auth.FaraSecurityManager;
import dev.fararoni.core.core.security.auth.FaraSecurityVault;
import dev.fararoni.core.core.security.auth.GuestManager;
import dev.fararoni.core.config.ServiceRegistry;
import dev.fararoni.core.core.kernel.HyperNativeKernel;
import dev.fararoni.core.core.llm.LocalLlmService;
import dev.fararoni.core.core.commands.AskCommand;
import dev.fararoni.core.core.commands.ChannelAccessCommand;
import dev.fararoni.core.core.gateway.MessagingChannelManager;
import dev.fararoni.core.core.persistence.JournalManager;
import dev.fararoni.core.core.persistence.SovereignJournal;
import dev.fararoni.core.core.persistence.OutboxDispatcher;
import dev.fararoni.core.core.swarm.HiveMind;
import dev.fararoni.core.core.routing.EnterpriseRouter;
import dev.fararoni.core.core.routing.RoutingPlan;
import dev.fararoni.core.core.swarm.infra.MessageBus;
import dev.fararoni.core.core.swarm.infra.SwarmTransport;
import dev.fararoni.core.core.swarm.infra.SovereignBridgeBus;
import dev.fararoni.core.core.bus.InMemorySovereignBus;
import dev.fararoni.core.core.bus.SovereignBusFactory;
import dev.fararoni.core.core.bus.SovereignBusGuard;
import dev.fararoni.core.core.resilience.GalvanicAgent;
import dev.fararoni.core.core.agents.QuartermasterAgent;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.protocol.AgentMessage;
import dev.fararoni.core.core.agents.AbstractSwarmAgent;
import java.util.ServiceLoader;
import dev.fararoni.core.core.workspace.GitManager;
import dev.fararoni.core.core.workspace.WorkspaceInsight;
import dev.fararoni.core.core.workspace.WorkspaceManager;
import dev.fararoni.core.core.index.IndexStore;
import dev.fararoni.core.core.index.ProjectKnowledgeBase;
import dev.fararoni.core.core.index.ProjectKnowledgeBase.ContextProfile;
import dev.fararoni.core.core.bootstrap.LlmProviderDiscovery;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.security.SecureConfigService;
import dev.fararoni.core.core.security.ChannelAccessGuard;
import dev.fararoni.core.core.persistence.ChannelContactStore;
import dev.fararoni.core.core.persistence.impl.SqliteChannelContactStore;
import dev.fararoni.core.core.config.ChannelAccessConfigLoader;
import dev.fararoni.core.core.config.ChannelsConfig;
import dev.fararoni.core.core.gateway.ChannelLoader;
import dev.fararoni.core.core.gateway.spi.GenericWebhookFactory;
import dev.fararoni.core.core.persistence.PersistenceFactory;
import dev.fararoni.core.core.persistence.spi.SovereignRepository;
import dev.fararoni.core.core.persistence.spi.SovereignRepository.ChannelRecord;
import dev.fararoni.core.core.persistence.spi.ConversationRepository;
import dev.fararoni.core.core.persistence.SqliteConversationRepository;
import dev.fararoni.core.core.gateway.CircuitBreaker;
import dev.fararoni.core.context.ContextManager;
import dev.fararoni.core.core.gateway.security.GenericWebhookAdapter;
import dev.fararoni.core.core.gateway.routing.OmniChannelRouter;
import dev.fararoni.core.core.telemetry.OperationTelemetry;
import dev.fararoni.core.core.telemetry.ToolAwareTelemetry;
import dev.fararoni.core.core.context.ExecutionContext;
import dev.fararoni.core.core.memory.ContextHealer;
import dev.fararoni.core.core.agent.dynamic.DynamicSwarmAgent;
import dev.fararoni.core.core.orchestrator.SovereignOrchestrator;
import dev.fararoni.core.core.orchestrator.registry.AgentRegistry;
import dev.fararoni.core.core.mission.engine.AgentTemplateManager;
import dev.fararoni.core.core.mission.engine.MissionTemplateManager;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine;
import dev.fararoni.core.core.mission.api.MissionRecoveryManager;
import dev.fararoni.core.core.infra.ConfigSentinel;
import dev.fararoni.core.cli.ui.SwarmMissionMonitor;
import dev.fararoni.core.model.Message;
import dev.fararoni.bus.module.ModuleRegistry;
import dev.fararoni.core.core.safety.audit.NotaryAuditListener;
import dev.fararoni.core.core.safety.listeners.FileSystemIntentListener;

import dev.fararoni.core.faracore.FaraCoreContextVault;
import dev.fararoni.core.faracore.FaraCoreLlmDispatcher;
import dev.fararoni.core.faracore.FaraCoreMissionControl;
import dev.fararoni.core.faracore.FaraCoreRoutingEngine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class FararoniCore {
    private static final Logger LOG = Logger.getLogger(FararoniCore.class.getName());

    private final AtomicBoolean shutdownDone = new AtomicBoolean(false);

    private final Path workingDirectory;
    private final WorkspaceInsight workspaceInsight;
    private final GitManager gitManager;
    private final WorkspaceManager workspaceManager;

    private HyperNativeKernel kernel;
    private SwarmTransport messageBus;
    private SovereignEventBus sovereignBus;
    private GalvanicAgent galvanicAgent;
    private QuartermasterAgent quartermasterAgent;
    private SovereignJournal sovereignJournal;
    private OutboxDispatcher outboxDispatcher;
    private JournalManager journalManager;

    private ChannelContactStore channelContactStore;
    private ChannelAccessGuard channelAccessGuard;
    private MessagingChannelManager messagingChannelManager;
    private OmniChannelRouter omniChannelRouter;

    private FileSystemIntentListener fileSystemListener;

    private NotaryAuditListener notaryAuditListener;

    private ModuleRegistry moduleRegistry;

    private FaraSecurityVault securityVault;
    private FaraSecurityInterceptor securityInterceptor;
    private GuestManager guestManager;

    private boolean enableCache = true;
    private boolean enablePersistence = true;

    public enum RabbitMode {
        NATIVE,
        REMOTE_SMALL,
        REMOTE_LARGE
    }

    private FaraCoreContextVault contextVault;
    private FaraCoreLlmDispatcher llmDispatcher;
    private FaraCoreMissionControl missionControl;
    private FaraCoreRoutingEngine routingEngine;

    public FararoniCore(String workingDirectory) {
        this(Paths.get(workingDirectory));
    }

    public FararoniCore(Path workingDirectory) {
        this.workingDirectory = workingDirectory.toAbsolutePath();
        this.workspaceInsight = new WorkspaceInsight();
        this.gitManager = new GitManager(this.workingDirectory);
        this.workspaceManager = WorkspaceManager.getInstance();

        LOG.info(() -> "[FARARONI] Core inicializado en: " + this.workingDirectory);
    }

    public FararoniCore initialize() {
        if (kernel != null) {
            return this;
        }

        LOG.info("[FARARONI] Inicializando subsistemas...");

        try {
            this.contextVault = new FaraCoreContextVault(workingDirectory, workspaceManager);
            this.contextVault.initializeMemoryVault();

            this.llmDispatcher = new FaraCoreLlmDispatcher(workingDirectory, contextVault, gitManager);

            ConfigPriorityResolver resolver = new ConfigPriorityResolver();
            String configuredUrl = resolver.resolveServerUrl(null);
            String configuredModel = resolver.resolveModelName(null);
            String apiKey = resolver.resolveApiKey(null);

            LOG.info(() -> "[FARARONI] Configuracion primaria: " + configuredUrl + " (modelo: " + configuredModel + ")");

            LlmProviderDiscovery discovery = new LlmProviderDiscovery();
            LlmProviderDiscovery.DiscoveryResult discoveryResult =
                discovery.discoverBestProvider(configuredUrl, configuredModel, apiKey);

            if (discoveryResult.success()) {
                LOG.info(() -> "[FARARONI] " + discoveryResult.message());
                System.out.println("[FARARONI] " + discoveryResult.message());
            } else {
                LOG.warning("[FARARONI] " + discoveryResult.message());
            }

            llmDispatcher.initializeClients(resolver, discoveryResult, apiKey);

            LocalLlmService localLlmService = llmDispatcher.getLocalLlmService();

            this.kernel = new HyperNativeKernel(
                llmDispatcher.getFastClient(),
                llmDispatcher.getExpertClient(),
                localLlmService,
                enableCache,
                null,
                null
            );

            LOG.info(() -> "[FARARONI] Kernel BLINDADO: Rabbit=" + llmDispatcher.getRabbitModelName() +
                " (FIJO), Turtle=" + llmDispatcher.getTurtleModelName() + " (DINAMICO)");

            this.sovereignBus = SovereignBusFactory.resolveGuardedBus();

            if (this.sovereignBus instanceof SovereignBusGuard guard) {
                System.out.println("[FARARONI]  Bus Grado Militar ACTIVO (Circuit Breaker + Replay Engine)");
                if (guard.isCircuitOpen()) {
                    System.out.println("[FARARONI]  ADVERTENCIA: Circuit Breaker ABIERTO - Primary no disponible");
                }
            } else if (this.sovereignBus instanceof InMemorySovereignBus localBus) {
                try {
                    Path dbPath = workspaceManager.getWorkspaceDir().resolve("sovereign_bus.db");
                    this.sovereignJournal = new SovereignJournal(dbPath);
                    localBus.connectJournal(this.sovereignJournal);
                    this.outboxDispatcher = new OutboxDispatcher(this.sovereignJournal, localBus);
                    this.outboxDispatcher.start();
                    System.out.println("[FARARONI]  Persistencia Blindada ACTIVA (Outbox Pattern: " + dbPath.getFileName() + ")");
                } catch (Exception e) {
                    LOG.warning("[FARARONI] Persistencia no disponible: " + e.getMessage());
                }
            }

            try {
                ChannelAccessConfigLoader.ensureExampleConfigs();
                this.channelContactStore = new SqliteChannelContactStore();
                this.channelAccessGuard = new ChannelAccessGuard(this.channelContactStore);

                var configLoader = new ChannelAccessConfigLoader(this.channelContactStore);
                int loaded = configLoader.loadFromDefault();
                if (loaded > 0) {
                    LOG.info("[FARARONI] Seguridad Multicanal ACTIVA - " + loaded + " entradas cargadas");
                } else {
                    LOG.info("[FARARONI] Seguridad Multicanal ACTIVA");
                }

                ChannelsConfig channelsConfig = ChannelsConfig.load();
                this.messagingChannelManager = new MessagingChannelManager(this.sovereignBus, this.channelAccessGuard, channelsConfig);

                try {
                    System.out.println("[FARARONI] Iniciando carga de canales desde DB...");
                    SovereignRepository channelRepository = PersistenceFactory.createRepository();
                    channelRepository.initialize();
                    System.out.println("[FARARONI]  Repositorio de canales inicializado");

                    ChannelLoader channelLoader = new ChannelLoader(this.sovereignBus, this.channelAccessGuard);
                    channelLoader.registerFactory(new GenericWebhookFactory());

                    var activeChannels = channelRepository.findActiveChannels();
                    System.out.println("[FARARONI]  Canales en DB: " + activeChannels.size());

                    int channelsLoaded = 0;
                    for (ChannelRecord record : activeChannels) {
                        try {
                            if ("GENERIC_WEBHOOK".equals(record.type())) {
                                GenericWebhookAdapter adapter = new GenericWebhookAdapter(
                                    record.id(), record.config(), this.sovereignBus, this.channelAccessGuard
                                );
                                this.messagingChannelManager.registerAdapter(adapter);
                                channelsLoaded++;
                                System.out.println("[FARARONI]  Canal cargado: " + record.id() + " (" + record.type() + ")");
                            } else {
                                System.out.println("[FARARONI]  Canal pendiente (factory no disponible): " + record.id() + " (" + record.type() + ")");
                            }
                        } catch (Exception e) {
                            System.out.println("[FARARONI]  Error cargando canal " + record.id() + ": " + e.getMessage());
                        }
                    }
                    if (channelsLoaded > 0) {
                        System.out.println("[FARARONI] " + channelsLoaded + " canales cargados desde DB");
                    }
                } catch (Exception e) {
                    System.out.println("[FARARONI]  Carga de canales desde DB no disponible: " + e.getMessage());
                }

                this.messagingChannelManager.start();
                System.out.println("[FARARONI]  MessagingChannelManager iniciado (sin canales externos)");

                final var core = this;
                OmniChannelRouter.LlmInferenceProvider llmProvider =
                    (systemPrompt, userPrompt) -> core.chatWithSystemPrompt(
                        systemPrompt, userPrompt, "INBOX-" + System.nanoTime()
                    );

                ConversationRepository conversationRepo = null;
                try {
                    Path conversationsDbPath = Path.of(
                        System.getProperty("user.home"), ".fararoni", "data", "conversations.db"
                    );
                    conversationRepo = new SqliteConversationRepository(conversationsDbPath);
                    System.out.println("[FARARONI] ConversationRepository inicializado: " + conversationsDbPath);
                } catch (Exception e) {
                    LOG.warning("[FARARONI] ConversationRepository no disponible: " + e.getMessage());
                }

                ContextManager ctxManager = ServiceRegistry.getContextManager();
                CircuitBreaker circuitBreaker = CircuitBreaker.Factory.standard();

                this.omniChannelRouter = new OmniChannelRouter(
                    this.sovereignBus, llmProvider, conversationRepo, ctxManager, circuitBreaker
                );
                this.omniChannelRouter.start();
                System.out.println("[FARARONI] OmniChannelRouter iniciado (contexto por sesion)");
            } catch (Exception e) {
                LOG.warning("[FARARONI] Seguridad Multicanal no disponible: " + e.getMessage());
            }

            try {
                var secManager = new FaraSecurityManager();
                if (!secManager.isFirstRun()) {
                    String totpSecret = secManager.loadOrGenerateSecret();
                    String masterHash = secManager.loadMasterPasswordHash();
                    this.securityVault = new FaraSecurityVault(
                            totpSecret, masterHash);
                    this.guestManager = new GuestManager();
                    final var coreRef = this;
                    this.securityInterceptor = new FaraSecurityInterceptor(
                            this.securityVault,
                            this.guestManager,
                            (message, isAdmin) -> coreRef.getRoutingEngine().chat(message),
                            (message) -> coreRef.getRoutingEngine().chat(message)
                    );
                    if (this.omniChannelRouter != null) {
                        this.omniChannelRouter.setSecurityInterceptor(this.securityInterceptor);
                    }
                    System.out.println("[FARARONI]  Security Auth ACTIVO (TOTP + Sandbox + BCrypt)");
                }
            } catch (Exception e) {
                LOG.warning("[FARARONI] Security Auth no disponible: " + e.getMessage());
            }

            boolean useSovereignBridge = Boolean.parseBoolean(
                System.getProperty("fararoni.swarm.use.sovereign.bridge", "false")
            );
            if (useSovereignBridge) {
                this.messageBus = new SovereignBridgeBus(this.sovereignBus, this.sovereignJournal);
                System.out.println("[FARARONI]  SwarmTransport: SovereignBridgeBus (NUEVO - Patron Strangler Fig)");
            } else {
                this.messageBus = new MessageBus();
                System.out.println("[FARARONI]  SwarmTransport: MessageBus (LEGACY)");
            }

            this.galvanicAgent = new GalvanicAgent(this.sovereignBus);
            this.galvanicAgent.start();
            System.out.println("[FARARONI]  GalvanicAgent iniciado en Bus Soberano");

            this.quartermasterAgent = new QuartermasterAgent(
                this.sovereignBus,
                (systemPrompt, userPrompt) -> this.chatWithSystemPrompt(
                    systemPrompt, userPrompt, "QM-" + System.nanoTime()
                )
            );
            this.quartermasterAgent.start();
            System.out.println("[FARARONI]  QuartermasterAgent iniciado en Bus Soberano");

            this.missionControl = new FaraCoreMissionControl(workingDirectory);

            ProjectKnowledgeBase memoryVault = contextVault.getMemoryVault();
            IndexStore idxForHive = (memoryVault instanceof IndexStore idx) ? idx : null;

            missionControl.initializeMissionSystem(
                this.sovereignBus, this.kernel, this.messageBus, workingDirectory, idxForHive, this
            );

            var atm = missionControl.getAgentTemplateManager();
            if (atm != null) {
                this.quartermasterAgent.setActiveAgentsProvider(atm::listAgentIds);
            }

            this.sovereignBus.subscribe(AbstractSwarmAgent.TELEMETRY_TOPIC, AgentMessage.class, envelope -> {
                AgentMessage msg = envelope.payload();
                if (AgentMessage.TYPE_STATUS_UPDATE.equals(msg.type())) {
                    String role = msg.senderRole();
                    String status = msg.getContent("status", "UNKNOWN");
                    String action = msg.naturalLanguageHint();
                    String icon = switch(status) {
                        case "EXECUTING" -> ">";
                        case "COMPLETED" -> "+";
                        case "FAILED" -> "!";
                        case "PLANNING" -> "?";
                        case "PAUSED" -> "#";
                        default -> ".";
                    };
                    System.out.printf("\u001B[36m[%s]\u001B[0m %s %s%n", role, icon, action);
                }
            });
            System.out.println("[FARARONI]  State Broadcasting habilitado (sys.telemetry.agents)");

            if (enablePersistence) {
                Path journalPath = workspaceManager.getWorkspaceDir().resolve("journals");
                this.journalManager = new JournalManager(journalPath);
                this.messageBus.connectJournal(journalManager);
                LOG.info(() -> "[FARARONI] Persistencia habilitada: " + journalPath);
            }

            HiveMind hiveMind = new HiveMind(kernel, messageBus, workingDirectory, idxForHive);
            missionControl.setHiveMind(hiveMind);

            final String agenticServerUrl = discoveryResult.serverUrl();
            final String agenticApiKey = apiKey;
            llmDispatcher.initializeAgenticClient(
                agenticServerUrl, agenticApiKey, memoryVault,
                this.sovereignBus, missionControl.getMissionTemplateManager(),
                missionControl.getAgentTemplateManager()
            );

            this.routingEngine = new FaraCoreRoutingEngine(llmDispatcher, contextVault);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("[FARARONI]  Shutdown Hook activado - Cerrando conexiones...");
                try {
                    if (contextVault != null && contextVault.getMemoryVault() != null
                            && contextVault.getMemoryVault().isAvailable()) {
                        this.shutdown();
                        LOG.info("[FARARONI] Conexiones cerradas limpiamente");
                    }
                } catch (Exception e) {
                    LOG.warning("[FARARONI] Error en shutdown: " + e.getMessage());
                }
            }, "Fararoni-Shutdown-Guard"));

            System.out.println("[DEBUG-FASE71] Intentando cargar ModuleRegistry...");
            try {
                this.moduleRegistry = new ModuleRegistry(this.sovereignBus);
                int loaded = this.moduleRegistry.loadModules();
                if (loaded > 0) {
                    int started = this.moduleRegistry.startModules();
                    LOG.info("[FARARONI] ModuleRegistry: " + started + " modulos activos");
                } else {
                    LOG.info("[FARARONI] ModuleRegistry: Sin modulos adicionales");
                }
            } catch (Exception e) {
                System.out.println("[DEBUG-FASE71] ERROR en ModuleRegistry: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
                LOG.warning("[FARARONI] ModuleRegistry no disponible: " + e.getMessage());
            }

            LOG.info("[FARARONI] Subsistemas inicializados correctamente");
        } catch (Exception e) {
            LOG.severe(() -> "[FARARONI] Error inicializando: " + e.getMessage());
            throw new RuntimeException("Fallo de inicializacion de Fararoni", e);
        }

        return this;
    }

    private void ensureInitialized() {
        if (kernel == null) {
            initialize();
        }
    }

    private SovereignEventBus loadBusImplementation() {
        ServiceLoader<SovereignEventBus> loader = ServiceLoader.load(SovereignEventBus.class);

        SovereignEventBus bestBus = null;
        int maxPriority = -1;

        System.out.println("[SPI] Buscando implementaciones de SovereignEventBus...");

        for (SovereignEventBus bus : loader) {
            int priority = bus.getPriority();
            System.out.println("[SPI]    -> Encontrado: " + bus.getClass().getSimpleName() + " (Prioridad: " + priority + ")");

            if (priority > maxPriority) {
                bestBus = bus;
                maxPriority = priority;
            }
        }

        if (bestBus == null) {
            System.out.println("[SPI]  No se encontraron proveedores SPI. Usando fallback InMemory.");
            return new InMemorySovereignBus();
        }

        final SovereignEventBus selectedBus = bestBus;
        System.out.println("[SPI]  Bus seleccionado: " + selectedBus.getClass().getSimpleName());
        return selectedBus;
    }

    public String chat(String prompt) {
        ensureInitialized();
        return routingEngine.chat(prompt);
    }

    public String chat(String prompt, OperationTelemetry telemetry) {
        ensureInitialized();
        return routingEngine.chat(prompt, telemetry);
    }

    public String chatWithSystemPrompt(String systemPrompt, String userMessage, String traceId) {
        ensureInitialized();
        return routingEngine.chatWithSystemPrompt(systemPrompt, userMessage, traceId);
    }

    public String chatWithSystemPrompt(String systemPrompt, String userMessage) {
        ensureInitialized();
        return routingEngine.chatWithSystemPrompt(systemPrompt, userMessage);
    }

    @Deprecated
    private String chatLegacyFlow(String prompt) {
        return chat(prompt);
    }

    public String chatAgenticWithSystemPrompt(String agentSystemPrompt, String userMessage) {
        ensureInitialized();
        return routingEngine.chatAgenticWithSystemPrompt(agentSystemPrompt, userMessage);
    }

    public String chatAgentic(String prompt) {
        ensureInitialized();
        return routingEngine.chatAgentic(prompt);
    }

    public String chatAgentic(String prompt, ToolAwareTelemetry telemetry) {
        ensureInitialized();
        return routingEngine.chatAgentic(prompt, telemetry);
    }

    public String chatAgentic(String prompt, ToolAwareTelemetry telemetry, ExecutionContext ctx)
            throws InterruptedException {
        ensureInitialized();
        return routingEngine.chatAgentic(prompt, telemetry, ctx);
    }

    public String executeWithTelemetry(String modelName, Supplier<String> operation) {
        ensureInitialized();
        return routingEngine.executeWithTelemetry(modelName, operation);
    }

    public HiveMind.MissionResult executeMission(String userRequest) {
        ensureInitialized();
        return missionControl.executeMission(userRequest);
    }

    public CompletableFuture<HiveMind.MissionResult> executeMissionAsync(String userRequest) {
        ensureInitialized();
        return missionControl.executeMissionAsync(userRequest);
    }

    public void deployDynamicAgents() {
        missionControl.deployDynamicAgents();
    }

    public DynamicSwarmAgent getDynamicAgent(String agentId) {
        return missionControl.getDynamicAgent(agentId);
    }

    public Map<String, DynamicSwarmAgent> getAllDynamicAgents() {
        return missionControl.getAllDynamicAgents();
    }

    public FararoniCore enableCache(boolean enable) {
        this.enableCache = enable;
        if (contextVault != null) {
            contextVault.setEnableCache(enable);
        }
        return this;
    }

    public FararoniCore enablePersistence(boolean enable) {
        this.enablePersistence = enable;
        if (contextVault != null) {
            contextVault.setEnablePersistence(enable);
        }
        return this;
    }

    public synchronized void hotSwapRabbitClient(String newUrl, String newModel) {
        llmDispatcher.hotSwapRabbitClient(newUrl, newModel, this.kernel);
    }

    public synchronized void restoreNativeRabbit() {
        llmDispatcher.restoreNativeRabbit();
    }

    public RabbitMode getCurrentRabbitMode() {
        return llmDispatcher.getCurrentRabbitMode();
    }

    public synchronized void hotSwapTurtleClient(String newUrl, String newModel) {
        llmDispatcher.hotSwapTurtleClient(newUrl, newModel, this.kernel);
    }

    public void activateClaudeAsTurtle(String apiKey, String model) {
        llmDispatcher.activateClaudeAsTurtle(apiKey, model);
    }

    public void deactivateClaudeAsTurtle() {
        llmDispatcher.deactivateClaudeAsTurtle();
    }

    public boolean isClaudeActive() {
        return llmDispatcher != null && llmDispatcher.isClaudePreferred();
    }

    public String getClaudeModelName() {
        return llmDispatcher != null ? llmDispatcher.getClaudeModelName() : "";
    }

    public void shutdown() {
        if (!shutdownDone.compareAndSet(false, true)) {
            LOG.info("[FARARONI] Shutdown ya ejecutado, ignorando llamada duplicada");
            return;
        }
        LOG.info("[FARARONI] Shutdown iniciado...");

        try {
            if (moduleRegistry != null && moduleRegistry.isRunning()) {
                moduleRegistry.stopModules();
                LOG.info("[FARARONI] ModuleRegistry detenido");
            }
        } catch (Exception e) {
            LOG.warning("[RECOVERY] Error deteniendo ModuleRegistry: " + e.getMessage());
        }

        try {
            if (contextVault != null) {
                contextVault.shutdownResources();
            }
        } catch (Exception e) {
            LOG.warning("[RECOVERY] Error cerrando ContextVault: " + e.getMessage());
        }

        try {
            if (galvanicAgent != null) {
                galvanicAgent.stop();
                LOG.info("[FARARONI] GalvanicAgent detenido");
            }
        } catch (Exception e) {
            LOG.warning("[RECOVERY] Error deteniendo GalvanicAgent: " + e.getMessage());
        }

        try {
            if (quartermasterAgent != null) {
                quartermasterAgent.stop();
                LOG.info("[FARARONI] QuartermasterAgent detenido");
            }
        } catch (Exception e) {
            LOG.warning("[RECOVERY] Error deteniendo QuartermasterAgent: " + e.getMessage());
        }

        try {
            if (missionControl != null) {
                missionControl.shutdownResources();
            }
        } catch (Exception e) {
            LOG.warning("[RECOVERY] Error cerrando MissionControl: " + e.getMessage());
        }

        try {
            if (outboxDispatcher != null) {
                outboxDispatcher.close();
                LOG.info("[FARARONI] OutboxDispatcher detenido");
            }
        } catch (Exception e) {
            LOG.warning("[RECOVERY] Error cerrando OutboxDispatcher: " + e.getMessage());
        }

        try {
            if (sovereignBus != null) {
                sovereignBus.shutdown(java.time.Duration.ofSeconds(2));
                LOG.info("[FARARONI] Bus Soberano cerrado");
            }
        } catch (Exception e) {
            LOG.warning("[RECOVERY] Shutdown silencioso del bus soberano: " + e.getMessage());
        }

        try {
            if (sovereignJournal != null) {
                sovereignJournal.close();
                LOG.info("[FARARONI] SovereignJournal cerrado");
            }
        } catch (Exception e) {
            LOG.warning("[FARARONI] Error cerrando SovereignJournal: " + e.getMessage());
        }

        try {
            if (llmDispatcher != null) {
                llmDispatcher.shutdownResources();
            }
        } catch (Exception e) {
            LOG.warning("[RECOVERY] Error cerrando LlmDispatcher: " + e.getMessage());
        }

        LOG.info("[FARARONI] Shutdown completado");
    }

    public void setMissionMonitorCoordinator(dev.fararoni.core.ui.OutputCoordinator coordinator) {
        if (missionControl != null) {
            missionControl.setMissionMonitorCoordinator(coordinator);
        }
    }

    public void setThinkingStreamCallback(java.util.function.Consumer<String> callback) {
        if (llmDispatcher != null) {
            llmDispatcher.setThinkingStreamCallback(callback);
        }
    }

    public String generateDiagnosticReport() {
        StringBuilder report = new StringBuilder();

        String workspaceReport = workspaceInsight.scanAndReport(workingDirectory);
        report.append(workspaceReport);

        if (gitManager.isGitRepository()) {
            report.append("\n============ ESTADO GIT ============\n");

            String branch = gitManager.getCurrentBranch();
            report.append("Rama actual: ").append(branch != null ? branch : "desconocida").append("\n");

            if (gitManager.hasUncommittedChanges()) {
                report.append("Estado: ").append("Cambios pendientes").append("\n");
                String diff = gitManager.getDiff();
                if (!diff.isBlank()) {
                    int lineCount = diff.split("\n").length;
                    report.append("Lineas modificadas: ~").append(lineCount).append("\n");
                }
            } else {
                report.append("Estado: Limpio (sin cambios pendientes)\n");
            }

            var snapshots = gitManager.listFararoniSnapshots();
            if (!snapshots.isEmpty()) {
                report.append("Snapshots de seguridad: ").append(snapshots.size()).append("\n");
            }

            report.append("====================================\n");
        } else {
            report.append("\n[INFO] Este directorio no es un repositorio Git\n");
        }

        return report.toString();
    }

    public String getQuickStatus() {
        var type = workspaceInsight.detectType(workingDirectory);
        boolean hasChanges = gitManager.isGitRepository() && gitManager.hasUncommittedChanges();

        return String.format("Proyecto: %s | Git: %s",
            type.getLabel(),
            hasChanges ? "cambios pendientes" : "limpio");
    }

    public WorkspaceInsight getWorkspaceInsight() {
        return workspaceInsight;
    }

    public GitManager getGitInspector() {
        return gitManager;
    }

    public WorkspaceManager getWorkspaceManager() {
        return workspaceManager;
    }

    public HyperNativeKernel getKernel() {
        ensureInitialized();
        return kernel;
    }

    public SwarmTransport getMessageBus() {
        ensureInitialized();
        return messageBus;
    }

    public SovereignEventBus getSovereignBus() {
        return this.sovereignBus;
    }

    public ChannelContactStore getChannelContactStore() {
        return this.channelContactStore;
    }

    public ChannelAccessGuard getChannelAccessGuard() {
        return this.channelAccessGuard;
    }

    public MessagingChannelManager getMessagingChannelManager() {
        return this.messagingChannelManager;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public FaraSecurityInterceptor getSecurityInterceptor() {
        return this.securityInterceptor;
    }

    public FaraSecurityVault getSecurityVault() {
        return this.securityVault;
    }

    public GuestManager getGuestManager() {
        return this.guestManager;
    }

    public LocalLlmService getLocalLlmService() {
        return llmDispatcher != null ? llmDispatcher.getLocalLlmService() : null;
    }

    public EnterpriseRouter getRouter() {
        return llmDispatcher != null ? llmDispatcher.getRouter() : null;
    }

    public boolean isUsingLocalModel() {
        return routingEngine != null
            ? routingEngine.getLastRoutingTarget() == RoutingPlan.TargetModel.LOCAL
            : true;
    }

    public RoutingPlan.TargetModel getLastRoutingTarget() {
        return routingEngine != null ? routingEngine.getLastRoutingTarget() : RoutingPlan.TargetModel.LOCAL;
    }

    public boolean isRemoteModelAvailable() {
        return llmDispatcher != null && llmDispatcher.isRemoteModelAvailable();
    }

    public String getTurtleModelName() {
        if (llmDispatcher != null) {
            return llmDispatcher.getTurtleModelName();
        }
        if (kernel != null) {
            return kernel.getExpertModelName();
        }
        return AppDefaults.DEFAULT_TURTLE_MODEL;
    }

    public String getRabbitModelName() {
        if (llmDispatcher != null) {
            return llmDispatcher.getRabbitModelName();
        }
        if (kernel != null) {
            return kernel.getFastModelName();
        }
        return AppDefaults.DEFAULT_RABBIT_MODEL;
    }

    public boolean isNativeEngineReady() {
        return llmDispatcher != null && llmDispatcher.isNativeEngineReady();
    }

    public boolean isAgenticModeAvailable() {
        return llmDispatcher != null && llmDispatcher.isAgenticModeAvailable();
    }

    public ProjectKnowledgeBase getMemoryVault() {
        return contextVault != null ? contextVault.getMemoryVault() : null;
    }

    public IndexStore.IndexStats getIndexStats() {
        ProjectKnowledgeBase mv = getMemoryVault();
        if (mv instanceof IndexStore idx && idx.isAvailable()) {
            return idx.getStats();
        }
        return IndexStore.IndexStats.EMPTY;
    }

    public long getIndexedFileCount() {
        return contextVault != null ? contextVault.getIndexedFileCount() : 0;
    }

    public boolean isIndexingComplete() {
        return contextVault != null && contextVault.isIndexingComplete();
    }

    public List<Message> getConversationHistory() {
        return contextVault != null ? contextVault.getConversationHistory() : List.of();
    }

    public ContextHealer getContextHealer() {
        return contextVault != null ? contextVault.getContextHealer() : new ContextHealer();
    }

    public HiveMind getHiveMind() {
        ensureInitialized();
        return missionControl.getHiveMind();
    }

    public AgentRegistry getAgentRegistry() {
        return missionControl != null ? missionControl.getAgentRegistry() : null;
    }

    public SovereignOrchestrator getSovereignOrchestrator() {
        return missionControl != null ? missionControl.getSovereignOrchestrator() : null;
    }

    public SovereignMissionEngine getMissionEngine() {
        return missionControl != null ? missionControl.getMissionEngine() : null;
    }

    public MissionTemplateManager getMissionTemplateManager() {
        return missionControl != null ? missionControl.getMissionTemplateManager() : null;
    }

    public AgentTemplateManager getAgentTemplateManager() {
        return missionControl != null ? missionControl.getAgentTemplateManager() : null;
    }

    public SwarmMissionMonitor getMissionMonitor() {
        return missionControl != null ? missionControl.getMissionMonitor() : null;
    }

    public ConfigSentinel getConfigSentinel() {
        return missionControl != null ? missionControl.getConfigSentinel() : null;
    }

    public MissionRecoveryManager getMissionRecovery() {
        return missionControl != null ? missionControl.getMissionRecovery() : null;
    }

    public boolean removeAgent(String agentId) {
        ensureInitialized();
        return missionControl.removeAgent(agentId);
    }

    public boolean isReady() {
        return kernel != null && llmDispatcher != null
            && (llmDispatcher.getFastClient() != null || llmDispatcher.getExpertClient() != null);
    }

    public static String getVersion() {
        return "FARARONI Core v1.0.0 - La Colmena Blindada";
    }

    public FaraCoreContextVault getContextVault() {
        return contextVault;
    }

    public FaraCoreLlmDispatcher getLlmDispatcher() {
        return llmDispatcher;
    }

    public FaraCoreMissionControl getMissionControl() {
        return missionControl;
    }

    public FaraCoreRoutingEngine getRoutingEngine() {
        return routingEngine;
    }
}
