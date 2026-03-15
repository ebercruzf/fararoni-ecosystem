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
package dev.fararoni.core.cli;

import dev.fararoni.core.FararoniCore;
import dev.fararoni.core.agent.ActionParser;
import dev.fararoni.core.agent.ContextCollector;
import dev.fararoni.core.agent.OutputProtocolStrategy;
import dev.fararoni.core.agent.ProtocolStrategyFactory;
import dev.fararoni.core.client.VllmClient;
import dev.fararoni.core.config.ConfigPriorityResolver;
import dev.fararoni.core.core.commands.TestCommand;
import dev.fararoni.core.core.dispatcher.ToolRegistryImpl;
import dev.fararoni.core.core.hooks.PostWriteHook;
import dev.fararoni.core.core.hooks.TestOnWriteHook;
import dev.fararoni.core.core.saga.SagaOrchestrator;
import dev.fararoni.core.core.command.CommandRegistry;
import dev.fararoni.core.core.agents.QuartermasterAgent;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.integration.FnlIntegrationService;
import dev.fararoni.bus.agent.api.command.CommandCategory;
import dev.fararoni.bus.agent.api.command.ConsoleCommand;
import dev.fararoni.bus.agent.api.command.ExecutionContext;
import dev.fararoni.core.core.skills.SkillProvider;
import dev.fararoni.core.core.security.SecureConfigService;
import dev.fararoni.core.enterprise.git.GitService;
import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.bus.agent.api.ToolResponse;
import dev.fararoni.core.client.LlmClient;
import dev.fararoni.core.config.CliConfig;
import dev.fararoni.core.config.ServiceRegistry;
import dev.fararoni.core.context.ContextManager;
import dev.fararoni.core.context.OutputSanitizer;
import dev.fararoni.core.core.session.SessionContextPersistence;
import dev.fararoni.core.core.audit.AuditLogger;
import dev.fararoni.core.core.download.DownloadProgress;
import dev.fararoni.core.core.download.DownloadState;
import dev.fararoni.core.core.download.ModelDownloader;
import dev.fararoni.core.core.download.NativeEngineDownloader;
import dev.fararoni.core.core.llm.LocalLlmConfig;
import dev.fararoni.core.core.llm.LocalLlmService;
import dev.fararoni.core.core.routing.EnterpriseRouter;
import dev.fararoni.core.core.routing.RoutingPlan;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.index.IndexStore;
import dev.fararoni.core.model.GenerationRequest;
import dev.fararoni.core.model.GenerationResponse;
import dev.fararoni.core.model.Message;
import dev.fararoni.core.router.RouterService;
import dev.fararoni.core.router.RoutingResult;
import dev.fararoni.core.router.Tool;
import dev.fararoni.core.service.FilesystemService;
import dev.fararoni.core.tokenizer.EstimationTokenizer;
import dev.fararoni.core.tokenizer.Tokenizer;
import dev.fararoni.core.ui.SpinnerUI;
import dev.fararoni.core.cli.ui.ConsoleTelemetry;
import dev.fararoni.core.cli.ui.PulseTelemetry;
import dev.fararoni.core.cli.ui.ThinkingTelemetry;
import dev.fararoni.core.core.telemetry.OperationTelemetry;
import dev.fararoni.core.ui.TerminalCapabilityDetector;
import dev.fararoni.core.core.utils.CommandSuggester;
import dev.fararoni.core.core.utils.NativeLoader;
import dev.fararoni.core.ui.OutputCoordinator;
import dev.fararoni.core.ui.TerminalGuard;
import dev.fararoni.core.ui.ThoughtRenderer;
import dev.fararoni.core.ui.JLineAgentUI;
import dev.fararoni.core.client.OpenAiStreamParser;
import dev.fararoni.bus.agent.api.interaction.AgentUserInterface;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class InteractiveShell {
    private LlmClient client;
    private Tokenizer tokenizer;
    private CliConfig config;
    private final ContextManager contextManager;
    private final FileManager fileManager;

    private final Terminal terminal;
    private final LineReader lineReader;

    private final List<Message> conversationHistory;
    private boolean showTokens;
    private boolean debugMode;
    private String activeContext = null;

    private final TerminalCapabilityDetector terminalDetector;
    private final OutputCoordinator outputCoordinator;
    private final AgentUserInterface agentUI;

    private final AuditLogger auditLogger;

    private boolean routerEnabled;

    private EnterpriseRouter enterpriseRouter;

    private LocalLlmService localLlmService;
    private String lastUserQuestion;

    private final FilesystemService filesystemService;
    private final ActionParser actionParser;
    private ActionParser silentActionParser;
    private final OutputProtocolStrategy protocolStrategy;
    private boolean agentModeEnabled = true;

    private final ContextCollector contextCollector;
    private String projectContext;

    private final GitService gitService;

    private final FnlIntegrationService fnlService;

    private final CommandRegistry commandRegistry;
    private final ExecutionContext executionContext;

    private Status statusFooter;
    private boolean isCurrentlyUsingLocal = false;

    private volatile boolean isProcessing = false;

    private FararoniCore fararoniCore;

    private enum SystemState {
        FREE_TALK,
        AWAITING_CHOICE
    }

    private SystemState currentState = SystemState.FREE_TALK;
    private java.util.List<String> pendingOptions = new java.util.ArrayList<>();
    private String pendingOriginalCommand = null;

    public InteractiveShell(FararoniCore core) {
        this.fararoniCore = core;

        this.client = null;
        this.tokenizer = new EstimationTokenizer();
        CliConfig.Builder cfgBuilder = CliConfig.builder();
        try {
            ConfigPriorityResolver resolver = new ConfigPriorityResolver();
            String resolvedUrl = resolver.resolveServerUrl(null);
            String resolvedModel = resolver.resolveModelName(null);
            if (resolvedUrl != null && !resolvedUrl.isBlank()) cfgBuilder.serverUrl(resolvedUrl);
            if (resolvedModel != null && !resolvedModel.isBlank()) cfgBuilder.modelName(resolvedModel);
            cfgBuilder.contextWindow(AppDefaults.TURTLE_CONTEXT_WINDOW);
        } catch (Exception e) {
        }
        this.config = cfgBuilder.build();
        this.contextManager = ServiceRegistry.getContextManager();
        this.fileManager = new FileManager(tokenizer);

        try {
            this.terminal = JLineConfig.buildTerminal();
            this.lineReader = JLineConfig.buildLineReader(terminal, new CommandCompleter());
        } catch (IOException e) {
            throw new RuntimeException("No se pudo inicializar JLine Terminal", e);
        }

        this.conversationHistory = new ArrayList<>();
        this.showTokens = false;
        this.debugMode = "true".equalsIgnoreCase(System.getenv("FARARONI_DEBUG")) ||
            "1".equals(System.getenv("FARARONI_DEBUG"));

        this.terminalDetector = new TerminalCapabilityDetector();
        var displayMode = terminalDetector.detectBestMode();
        this.outputCoordinator = new OutputCoordinator(displayMode, terminal);

        TerminalGuard.install(outputCoordinator);

        TerminalGuard.setFooterActive(true);

        fararoniCore.setMissionMonitorCoordinator(outputCoordinator);

        this.agentUI = new JLineAgentUI(terminal, lineReader);

        this.auditLogger = AuditLogger.getInstance();

        this.routerEnabled = false;
        this.enterpriseRouter = null;

        this.protocolStrategy = ProtocolStrategyFactory.selectDefault();
        this.filesystemService = new FilesystemService(core.getWorkingDirectory());

        this.gitService = new GitService(core.getWorkingDirectory());

        SagaOrchestrator sagaOrchestrator = new SagaOrchestrator(new ToolRegistryImpl());
        TestCommand testCommand = new TestCommand();
        PostWriteHook regressionGuard = new TestOnWriteHook(testCommand);
        List<PostWriteHook> hooks = List.of(regressionGuard);

        this.actionParser = new ActionParser(
            filesystemService,
            this::printAgentOutput,
            gitService,
            sagaOrchestrator,
            hooks
        );

        this.silentActionParser = new ActionParser(
            filesystemService,
            (msg) -> {
                if (msg != null && (msg.contains("Error") || msg.contains("error") ||
                    msg.contains("Rollback") || msg.contains("rollback") ||
                    msg.contains("Warning") || msg.contains("⚠") ||
                    msg.contains("[ERROR]") || msg.contains("[WARN]"))) {
                    System.out.println(msg);
                }
            },
            gitService,
            sagaOrchestrator,
            hooks
        );

        this.contextCollector = new ContextCollector(core.getWorkingDirectory());
        this.projectContext = contextCollector.collectFullContext();

        this.fnlService = SkillProvider.getInstance(core.getWorkingDirectory());

        this.commandRegistry = CommandRegistry.getInstance();
        commandRegistry.reloadCommands();

        this.executionContext = createExecutionContext();

        this.statusFooter = Status.getStatus(terminal);

        subscribeToQuartermasterResponses();
    }

    private void subscribeToQuartermasterResponses() {
        SovereignEventBus bus = fararoniCore.getSovereignBus();
        if (bus == null) {
            return;
        }

        bus.subscribe(QuartermasterAgent.RESPONSES_TOPIC, String.class, envelope -> {
            String responseType = envelope.headers().get("response_type");
            String content = envelope.payload();

            if ("ERROR_DIAGNOSIS".equals(responseType)) {
                System.out.println("\n[QUARTERMASTER - DIAGNOSTICO]");
                System.out.println(content);
                System.out.println();
            } else {
                System.out.println("\n[QUARTERMASTER]");
                System.out.println(content);
                System.out.println();
            }

            if (lineReader != null) {
                lineReader.callWidget(org.jline.reader.LineReader.REDRAW_LINE);
                lineReader.callWidget(org.jline.reader.LineReader.REDISPLAY);
            }
        });
    }

    public InteractiveShell(LlmClient client, Tokenizer tokenizer, CliConfig config) {
        this.client = client;
        this.tokenizer = tokenizer;
        this.config = config;
        this.contextManager = ServiceRegistry.getContextManager();
        this.fileManager = new FileManager(tokenizer);

        try {
            this.terminal = JLineConfig.buildTerminal();
            this.lineReader = JLineConfig.buildLineReader(terminal, new CommandCompleter());
        } catch (IOException e) {
            throw new RuntimeException("No se pudo inicializar JLine Terminal", e);
        }

        this.conversationHistory = new ArrayList<>();
        this.showTokens = config.showTokens();
        this.debugMode = config.enableDebugMode() ||
            "true".equalsIgnoreCase(System.getenv("FARARONI_DEBUG")) ||
            "1".equals(System.getenv("FARARONI_DEBUG"));

        this.terminalDetector = new TerminalCapabilityDetector();
        var displayMode = terminalDetector.detectBestMode();
        this.outputCoordinator = new OutputCoordinator(displayMode, terminal);

        TerminalGuard.install(outputCoordinator);

        TerminalGuard.setFooterActive(true);

        this.agentUI = new JLineAgentUI(terminal, lineReader);

        this.auditLogger = AuditLogger.getInstance();

        this.routerEnabled = ServiceRegistry.isRouterEnabled();
        if (routerEnabled) {
            var router = ServiceRegistry.getRouterService();
            System.out.println("[ROUTER] Router Semantico: " + router.getName() +
                (router.isLlmBased() ? " (LLM)" : " (Regex)"));
        }

        this.enterpriseRouter = ServiceRegistry.getEnterpriseRouter();
        if (enterpriseRouter != null) {
            System.out.println("[ROUTER] Enterprise Router: " +
                (enterpriseRouter.isFullyOperational() ? "3 capas (LLM)" : "2 capas (Regex+Cache)"));
        }

        this.protocolStrategy = ProtocolStrategyFactory.selectDefault();
        this.filesystemService = new FilesystemService(Paths.get("."));

        this.gitService = new GitService(Paths.get("."));
        if (gitService.isGitRepo()) {
            System.out.println("[GIT] Git Auto-commit: habilitado");
        }

        SagaOrchestrator sagaOrchestrator = new SagaOrchestrator(new ToolRegistryImpl());
        TestCommand testCommand = new TestCommand();
        PostWriteHook regressionGuard = new TestOnWriteHook(testCommand);
        List<PostWriteHook> hooks = List.of(regressionGuard);
        System.out.println("[GUARD] Self-Healing: habilitado (RegressionGuard)");

        this.actionParser = new ActionParser(
            filesystemService,
            this::printAgentOutput,
            gitService,
            sagaOrchestrator,
            hooks
        );

        this.contextCollector = new ContextCollector(Paths.get("."));
        this.projectContext = contextCollector.collectFullContext();

        this.fnlService = SkillProvider.getInstance(Paths.get("."));

        this.commandRegistry = CommandRegistry.getInstance();
        int dynamicCommands = commandRegistry.reloadCommands();
        if (dynamicCommands > 0) {
            System.out.println("[COMMANDS] Comandos dinamicos: " + dynamicCommands);
        }

        this.executionContext = createExecutionContext();

        this.statusFooter = Status.getStatus(terminal);
    }

    private ExecutionContext createExecutionContext() {
        final InteractiveShell shell = this;

        return new ExecutionContext() {
            @Override
            public void print(String message) {
                outputCoordinator.print(message);
            }

            @Override
            public void printSuccess(String message) {
                outputCoordinator.print(message, OutputCoordinator.MessageType.SUCCESS);
            }

            @Override
            public void printWarning(String message) {
                outputCoordinator.print(message, OutputCoordinator.MessageType.WARNING);
            }

            @Override
            public void printError(String message) {
                outputCoordinator.print(message, OutputCoordinator.MessageType.ERROR);
            }

            @Override
            public void printDebug(String message) {
                if (debugMode) {
                    outputCoordinator.print("[DEBUG] " + message);
                }
            }

            @Override
            public void addToContext(String content) {
                conversationHistory.add(Message.user(content));
            }

            @Override
            public void addToSystemContext(String content) {
                conversationHistory.add(Message.system(content));
            }

            @Override
            public Path getWorkingDirectory() {
                return Paths.get(".").toAbsolutePath().normalize();
            }

            @Override
            public java.util.Optional<Path> getProjectRoot() {
                Path cwd = getWorkingDirectory();
                if (java.nio.file.Files.exists(cwd.resolve("pom.xml")) ||
                    java.nio.file.Files.exists(cwd.resolve("package.json")) ||
                    java.nio.file.Files.exists(cwd.resolve("build.gradle")) ||
                    java.nio.file.Files.exists(cwd.resolve(".git"))) {
                    return java.util.Optional.of(cwd);
                }
                return java.util.Optional.empty();
            }

            @Override
            public boolean isDebugMode() {
                return debugMode;
            }

            @Override
            public boolean isGitRepository() {
                return gitService.isGitRepo();
            }

            @Override
            public java.util.Optional<String> getCurrentBranch() {
                return gitService.getCurrentBranch();
            }

            @Override
            public <T> T withFileService(java.util.function.Function<FileServiceAccessor, T> operation) {
                return operation.apply(new FileServiceAccessor() {
                    @Override
                    public String readFile(Path path) {
                        try {
                            return java.nio.file.Files.readString(path);
                        } catch (java.io.IOException e) {
                            throw new RuntimeException("Error leyendo archivo: " + path, e);
                        }
                    }

                    @Override
                    public void writeFile(Path path, String content) {
                        try {
                            java.nio.file.Files.writeString(path, content);
                        } catch (java.io.IOException e) {
                            throw new RuntimeException("Error escribiendo archivo: " + path, e);
                        }
                    }

                    @Override
                    public boolean exists(Path path) {
                        return java.nio.file.Files.exists(path);
                    }
                });
            }

            @Override
            public void logAudit(String action, String details) {
                auditLogger.logSystem(action, details);
            }

            @Override
            public AgentUserInterface getUI() {
                return agentUI;
            }

            @Override
            public Object getCore() {
                return fararoniCore;
            }

            @Override
            public boolean supportsInteractiveInput() {
                return true;
            }

            @Override
            public String readLine() {
                try {
                    return lineReader != null ? lineReader.readLine() : null;
                } catch (Exception e) {
                    return null;
                }
            }
        };
    }

    private void printAgentOutput(String line) {
        outputCoordinator.print(line);
    }

    public void start() {
        if (fararoniCore == null) {
            throw new IllegalStateException("start() requiere constructor con FararoniCore");
        }

        printWelcomeBanner();
        System.out.println("[CORE] Cerebro: FararoniCore (MVC Mode)");

        TerminalGuard.setFooterActive(true);

        try {
        while (true) {
            try {
                updateStatusFooter();

                String input;
                try {
                    input = lineReader.readLine(getPrompt());
                } catch (UserInterruptException e) {
                    System.out.println();
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                if (input == null) {
                    break;
                }

                input = input.trim();

                if (input.isEmpty()) {
                    continue;
                }

                if (currentState == SystemState.AWAITING_CHOICE) {
                    if (handleUserSelection(input)) {
                        continue;
                    } else {
                        terminal.writer().println("Opcion invalida. Selecciona 1-" + pendingOptions.size() +
                                                 " o escribe 'cancelar' para abortar.");
                        terminal.writer().flush();
                        continue;
                    }
                }

                if (input.startsWith("/")) {
                    if (!processStartCommand(input)) {
                        break;
                    }
                    continue;
                }

                terminal.writer().println();
                terminal.writer().flush();

                String response = null;

                boolean requiresAction = detectActionIntent(input);

                if (agentModeEnabled && fararoniCore.isAgenticModeAvailable() && requiresAction) {
                    if (fararoniCore.getContextHealer().hasPendingContext()) {
                        String pending = fararoniCore.getContextHealer().peekLimbo();
                        String pendingMsg = ">> Contexto previo interrumpido: \"" +
                            truncateForDisplay(pending, 40) + "\" (Se intentara fusionar)";
                        AttributedString styledPending = new AttributedString(
                            pendingMsg,
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW)
                        );
                        terminal.writer().println(styledPending.toAnsi());
                        terminal.writer().flush();
                    }

                    AttributedString cancelHint = new AttributedString(
                        "   [ESC] o [Ctrl+C] para cancelar...",
                        AttributedStyle.DEFAULT.faint()
                    );
                    terminal.writer().println(cancelHint.toAnsi());
                    terminal.writer().flush();

                    var ctx = new dev.fararoni.core.core.context.ExecutionContext();

                    Terminal.SignalHandler prevHandler = terminal.handle(Terminal.Signal.INT, sig -> {
                        ctx.cancel("Usuario presiono Ctrl+C (SIGINT)");
                    });

                    outputCoordinator.setSilenceMode(true);
                    try (PulseTelemetry ui = new PulseTelemetry(this.terminal, "Agent")
                            .withLocalModelName(fararoniCore.getRabbitModelName())
                            .withRemoteModelName(fararoniCore.getTurtleModelName())
                            .withProcessingStateCallback(processing -> {
                                this.isProcessing = processing;
                                updateStatusFooter();
                            })
                            .withModelSwitchCallback(target -> {
                                updateStatusFooter();
                            })) {
                        final String userInput = input;
                        final String[] resultHolder = new String[1];
                        final Exception[] errorHolder = new Exception[1];

                        var worker = Thread.ofVirtual().name("agent-worker").start(() -> {
                            try {
                                resultHolder[0] = fararoniCore.chatAgentic(userInput, ui, ctx);
                            } catch (InterruptedException e) {
                                ui.showCancelled("CANCELLED");
                            } catch (Exception e) {
                                errorHolder[0] = e;
                                ui.showError("ERROR");
                            }
                        });

                        var sentinel = Thread.ofVirtual().name("input-sentinel").start(() -> {
                            try {
                                while (worker.isAlive() && !ctx.isCancelled()) {
                                    int key = terminal.reader().peek(100);
                                    if (key == 27) {
                                        terminal.reader().read();
                                        ctx.cancel("Usuario presiono ESCAPE");
                                        break;
                                    }
                                    Thread.sleep(50);
                                }
                            } catch (Exception ignored) {
                            }
                        });

                        worker.join();
                        sentinel.interrupt();
                        if (ctx.isCancelled()) {
                            terminal.writer().println();
                            terminal.writer().println("\u001B[33m[INTERRUPTED] Operation was interrupted. Your prompt has been saved.\u001B[0m");
                            terminal.writer().flush();
                        } else if (errorHolder[0] != null) {
                            terminal.writer().println("Error: " + errorHolder[0].getMessage());
                        } else {
                            response = resultHolder[0];
                        }

                        this.isProcessing = false;
                    } catch (Exception e) {
                        this.isProcessing = false;
                        terminal.writer().println("Error: " + e.getMessage());
                    } finally {
                        terminal.handle(Terminal.Signal.INT, prevHandler);
                        outputCoordinator.setSilenceMode(false);
                    }
                } else {
                    outputCoordinator.setSilenceMode(true);

                    boolean useThinking = Boolean.getBoolean(AppDefaults.ENV_SHOW_REASONING);
                    boolean thinkingWasStreamed = false;

                    if (useThinking) {
                        try (ThinkingTelemetry thinkTelemetry = new ThinkingTelemetry(this.terminal)) {
                            thinkTelemetry.activate();
                            response = fararoniCore.chat(input, OperationTelemetry.noOp());
                            thinkingWasStreamed = thinkTelemetry.hasThinkingStarted();
                            thinkTelemetry.finishThinking();
                            thinkTelemetry.deactivate();
                        } catch (Exception e) {
                            terminal.writer().println("Error: " + e.getMessage());
                        } finally {
                            outputCoordinator.setSilenceMode(false);
                        }

                        if (thinkingWasStreamed && response != null && response.startsWith(OpenAiStreamParser.THOUGHT_PREFIX)) {
                            int separator = response.lastIndexOf("\n\n");
                            if (separator > 0) {
                                response = response.substring(separator + 2).trim();
                            } else {
                                response = response.substring(OpenAiStreamParser.THOUGHT_PREFIX.length()).trim();
                            }
                        }
                    } else {
                        try (ConsoleTelemetry telemetry = new ConsoleTelemetry(this.terminal, this.statusFooter)
                                .withProcessingStateCallback(processing -> {
                                    this.isProcessing = processing;
                                    updateStatusFooter();
                                })
                                .withModelSwitchCallback(target -> {
                                    updateStatusFooter();
                                })) {
                            response = fararoniCore.chat(input, telemetry);
                        } catch (Exception e) {
                            terminal.writer().println("Error: " + e.getMessage());
                        } finally {
                            outputCoordinator.setSilenceMode(false);
                        }
                    }
                }

                if (response != null) {
                    if (response.contains(OpenAiStreamParser.THOUGHT_PREFIX)) {
                        terminal.writer().print("Fararoni: ");
                        ThoughtRenderer renderer = new ThoughtRenderer(new java.io.PrintStream(terminal.output()));
                        String[] parts = response.split("(?=" + java.util.regex.Pattern.quote(OpenAiStreamParser.THOUGHT_PREFIX) + ")|(?<=" + java.util.regex.Pattern.quote(OpenAiStreamParser.THOUGHT_PREFIX) + ")");
                        for (String part : parts) {
                            renderer.render(part);
                        }
                        renderer.finish();
                        terminal.writer().println();
                    } else {
                        terminal.writer().println("Fararoni: " + response);
                    }
                    terminal.writer().println();
                    terminal.writer().flush();

                    if (response.contains(AppDefaults.AMBIGUITY_HEADER) &&
                        response.contains(AppDefaults.AMBIGUITY_INSTRUCTION)) {
                        java.util.List<String> extractedPaths = extractAmbiguousPaths(response);
                        if (!extractedPaths.isEmpty()) {
                            this.pendingOptions = extractedPaths;
                            this.pendingOriginalCommand = input;
                            this.currentState = SystemState.AWAITING_CHOICE;
                            terminal.writer().println("\n\u001B[33m[FSM] Escribe el número (1-" +
                                extractedPaths.size() + ") para seleccionar, o 'cancelar':\u001B[0m");
                            terminal.writer().flush();
                        }
                    }
                }

                updateStatusFooter();

                if (agentModeEnabled && response != null) {
                    boolean nativeToolExecuted = response.contains("[OK] Archivo creado") ||
                                                 response.contains("[OK] Directorio creado") ||
                                                 response.contains("🛠️ Tool Call detectado");

                    if (!nativeToolExecuted) {
                        boolean hasSecureBlock = response.contains(">>>FILE:") || response.contains(">>>MKDIR:");

                        if (hasSecureBlock) {
                            terminal.writer().println("\u001B[33m[WARN] [SISTEMA] Function Calling nativo no disponible. Activando protocolo de contingencia (Action Blocks)...\u001B[0m");

                            silentActionParser.reset();
                            for (String line : response.split("\n")) {
                                silentActionParser.processLine(line);
                            }
                            silentActionParser.flush();
                        }
                    }
                }
            } catch (Exception e) {
                outputCoordinator.printError("Error inesperado: " + e.getMessage());
                auditLogger.logError(AuditLogger.Category.SYSTEM, "Error en shell interactivo", e);
                if (debugMode) {
                    e.printStackTrace();
                }
            }
        }
        } finally {
            TerminalGuard.setFooterActive(false);
        }

        printGoodbye();
    }

    private boolean processStartCommand(String command) {
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();

        return switch (cmd) {
            case "/exit", "/quit" -> {
                yield false;
            }
            case "/help" -> {
                printHelp();
                yield true;
            }
            case "/status" -> {
                System.out.println(fararoniCore.generateDiagnosticReport());
                yield true;
            }
            case "/clear" -> {
                System.out.print("\033[H\033[2J");
                System.out.flush();
                yield true;
            }
            case "/debug" -> {
                debugMode = !debugMode;
                System.out.println("Debug mode: " + (debugMode ? "ON" : "OFF"));
                yield true;
            }
            case "/legacy" -> {
                printLegacyStory();
                yield true;
            }
            default -> {
                var dynCmd = commandRegistry.findCommand(cmd);
                if (dynCmd.isPresent()) {
                    String arg = parts.length > 1 ? parts[1] : "";
                    dynCmd.get().execute(arg, executionContext);
                } else {
                    outputCoordinator.print("Comando desconocido: " + cmd, OutputCoordinator.MessageType.WARNING);
                }
                yield true;
            }
        };
    }

    public void run() {
        printWelcomeBanner();
        printQuickHelp();

        performStartupNativeEngineCheck();

        TerminalGuard.setFooterActive(true);

        try {
        while (true) {
            try {
                updateStatusFooter();

                String input;
                try {
                    input = lineReader.readLine(getPrompt());
                } catch (UserInterruptException e) {
                    System.out.println();
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                if (input == null) {
                    break;
                }

                input = input.trim();

                if (input.isEmpty()) {
                    continue;
                }

                var shouldContinue = processInput(input);
                if (!shouldContinue) {
                    break;
                }
            } catch (Exception e) {
                outputCoordinator.printError("Error inesperado: " + e.getMessage());
                auditLogger.logError(AuditLogger.Category.SYSTEM, "Error en shell interactivo", e);
                if (debugMode) {
                    e.printStackTrace();
                }
            }
        }
        } finally {
            TerminalGuard.setFooterActive(false);
        }

        printGoodbye();
    }

    private boolean processInput(String input) {
        if (input.startsWith("/")) {
            return handleCommand(input);
        }

        if (enterpriseRouter != null) {
            RoutingPlan plan = enterpriseRouter.route(input);

            if (debugMode) {
                outputCoordinator.printDebug("[FastPath] EnterpriseRouter: " + plan.toAuditString());
            }

            if (plan.target() == RoutingPlan.TargetModel.LOCAL &&
                plan.decisionSource() == RoutingPlan.DecisionSource.LAYER_0_REFLEX) {
                if (debugMode) {
                    outputCoordinator.printDebug("[FastPath] Saludo trivial detectado, usando modelo local");
                }

                conversationHistory.add(Message.user(input));

                if (handleWithLocalModel(input, plan)) {
                    return true;
                }

                if (debugMode) {
                    outputCoordinator.printDebug("[FastPath] Modelo local falló, usando flujo normal");
                }
            }
        }

        if (routerEnabled) {
            return processWithRouter(input);
        }

        handleQuestion(input);
        return true;
    }

    private boolean processWithRouter(String input) {
        RouterService router = ServiceRegistry.getRouterService();
        if (router == null) {
            handleQuestion(input);
            return true;
        }

        router.setActiveContext(activeContext);

        var spinner = SpinnerUI.withState(SpinnerUI.SpinnerState.ANALYZING);
        RoutingResult result;
        try {
            result = router.route(input);
        } finally {
            spinner.stop();
        }

        if (debugMode) {
            outputCoordinator.printDebug("[Router] " + result.toLogString());
        }

        if (result.tool() == Tool.CHAT || result.tool() == Tool.UNKNOWN) {
            if (result.parameters() != null &&
                Boolean.TRUE.equals(result.parameters().get("fromLocalModel"))) {
                String localResponse = (String) result.parameters().get("localResponse");
                if (localResponse != null && !localResponse.isBlank()) {
                    if (enterpriseRouter != null) {
                        RoutingPlan plan = enterpriseRouter.route(input);
                        if (plan.target() == RoutingPlan.TargetModel.EXPERT) {
                            if (debugMode) {
                                outputCoordinator.printDebug("[FIX 9.2] EnterpriseRouter dice EXPERT, " +
                                    "ignorando respuesta local para evitar alucinaciones");
                            }
                            handleQuestion(input);
                            return true;
                        }
                    }
                    return processLocalModelResponse(localResponse);
                }
            }
            handleQuestion(input);
            return true;
        }

        if (!result.isConfident()) {
            if (debugMode) {
                outputCoordinator.printDebug("[Router] Low confidence, treating as question");
            }
            handleQuestion(input);
            return true;
        }

        return executeRoutedCommand(result, input);
    }

    private boolean executeRoutedCommand(RoutingResult result, String originalInput) {
        auditLogger.log(AuditLogger.Level.INFO, AuditLogger.Category.COMMAND,
            "Router detected: " + result.tool() + ":" + result.action(),
            "confidence=" + result.confidence() + ", input=" + originalInput);

        if (debugMode) {
            outputCoordinator.print(String.format("[RTR] [%s] %s:%s (%.0f%%)",
                result.fromFallback() ? "regex" : "LLM",
                result.tool().getId(),
                result.action(),
                result.confidence() * 100),
                OutputCoordinator.MessageType.NORMAL);
        }

        return switch (result.tool()) {
            case GIT -> {
                handleGitCommand(result);
                yield true;
            }
            case FILE -> {
                handleFileCommand(result);
                yield true;
            }
            case CONFIG -> {
                handleConfigFromRouter(result);
                yield true;
            }
            case FEATURE -> {
                handleFeatureCommand(result);
                yield true;
            }
            case SYSTEM -> {
                handleSystemCommand(result);
                yield true;
            }
            default -> {
                handleQuestion(originalInput);
                yield true;
            }
        };
    }

    private void handleGitCommand(RoutingResult result) {
        String action = result.action();

        switch (action) {
            case "push" -> {
                outputCoordinator.print("Ejecutando: git push", OutputCoordinator.MessageType.NORMAL);
                executeShellCommand("git push");
            }
            case "pull" -> {
                outputCoordinator.print("Ejecutando: git pull", OutputCoordinator.MessageType.NORMAL);
                executeShellCommand("git pull");
            }
            case "status" -> {
                outputCoordinator.print("Ejecutando: git status", OutputCoordinator.MessageType.NORMAL);
                executeShellCommand("git status");
            }
            case "diff" -> {
                outputCoordinator.print("Ejecutando: git diff", OutputCoordinator.MessageType.NORMAL);
                executeShellCommand("git diff");
            }
            case "commit" -> {
                String message = result.getParameter("message", "");
                if (message.isEmpty()) {
                    outputCoordinator.printError("Falta el mensaje de commit. Usa: commitea con mensaje <tu mensaje>");
                } else {
                    outputCoordinator.print("Ejecutando: git commit -m \"" + message + "\"",
                        OutputCoordinator.MessageType.NORMAL);
                    executeShellCommand("git commit -m \"" + message + "\"");
                }
            }
            case "branch" -> {
                String branch = result.getParameter("branch", "");
                if (branch.isEmpty()) {
                    executeShellCommand("git branch");
                } else {
                    executeShellCommand("git checkout -b " + branch);
                }
            }
            case "log" -> executeShellCommand("git log --oneline -10");
            default -> outputCoordinator.print("Acción git no soportada: " + action,
                OutputCoordinator.MessageType.WARNING);
        }
    }

    private void handleFileCommand(RoutingResult result) {
        String action = result.action();
        String file = result.getParameter("file", "");

        switch (action) {
            case "load" -> {
                if (file.isEmpty()) {
                    outputCoordinator.printError("Especifica el archivo a cargar");
                } else {
                    loadFiles(file);
                }
            }
            case "unload" -> unloadContext();
            case "search" -> {
                String query = result.getParameter("query", "");
                if (!query.isEmpty()) {
                    executeShellCommand("grep -rn \"" + query + "\" .");
                }
            }
            case "list" -> listFiles(file.isEmpty() ? "." : file);
            default -> outputCoordinator.print("Acción file no soportada: " + action,
                OutputCoordinator.MessageType.WARNING);
        }
    }

    private void handleConfigFromRouter(RoutingResult result) {
        String action = result.action();

        switch (action) {
            case "show" -> showConfig();
            case "set" -> {
                String key = result.getParameter("key", "");
                String value = result.getParameter("value", "");
                if (!key.isEmpty() && !value.isEmpty()) {
                    setConfigValue(key, value);
                } else {
                    outputCoordinator.print("Uso: configura <clave> a <valor>",
                        OutputCoordinator.MessageType.WARNING);
                }
            }
            case "list" -> listConfigKeys();
            default -> showConfig();
        }
    }

    private void handleSystemCommand(RoutingResult result) {
        String action = result.action();
        String command = result.getParameter("command", null);

        if (command == null || command.isBlank()) {
            command = action;
        }

        if (command != null && !command.isBlank()) {
            executeSystemToolCommand(command);
        } else {
            outputCoordinator.print("No se pudo determinar el comando a ejecutar",
                OutputCoordinator.MessageType.WARNING);
        }
    }

    private void handleFeatureCommand(RoutingResult result) {
        String action = result.action();
        String description = result.getParameter("description", "");

        switch (action) {
            case "plan" -> {
                if (description.isEmpty()) {
                    outputCoordinator.print("Describe la feature a planificar",
                        OutputCoordinator.MessageType.WARNING);
                } else {
                    handleQuestion("Planifica la implementación de: " + description);
                }
            }
            case "execute" -> outputCoordinator.print("Ejecutando plan... (no implementado aún)",
                OutputCoordinator.MessageType.WARNING);
            default -> outputCoordinator.print("Acción feature no soportada: " + action,
                OutputCoordinator.MessageType.WARNING);
        }
    }

    private void executeShellCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                outputCoordinator.print("Comando terminó con código: " + exitCode,
                    OutputCoordinator.MessageType.WARNING);
            }
        } catch (Exception e) {
            outputCoordinator.printError("Error ejecutando comando: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
        }
    }

    private boolean handleCommand(String command) {
        var parts = command.split("\\s+", 2);
        var cmd = parts[0].toLowerCase();
        var args = parts.length > 1 ? parts[1] : "";

        auditLogger.logCommand(command, null);

        return switch (cmd) {
            case "/help", "/h" -> {
                printHelp();
                yield true;
            }
            case "/exit", "/quit", "/q" -> {
                yield false;
            }
            case "/clear", "/cls" -> {
                clearHistory();
                yield true;
            }
            case "/load" -> {
                loadFiles(args);
                yield true;
            }
            case "/unload" -> {
                unloadContext();
                yield true;
            }
            case "/list", "/ls" -> {
                listFiles(args);
                yield true;
            }
            case "/tokens" -> {
                toggleTokens();
                yield true;
            }
            case "/debug" -> {
                toggleDebug();
                yield true;
            }
            case "/history" -> {
                showHistory();
                yield true;
            }
            case "/status" -> {
                showStatus();
                yield true;
            }
            case "/reconnect" -> {
                handleReconnect(args);
                yield true;
            }
            case "/config" -> {
                handleConfigCommand(args);
                yield true;
            }
            case "/router" -> {
                handleRouterCommand(args);
                yield true;
            }
            case "/context" -> {
                handleContextCommand(args);
                yield true;
            }
            case "/git" -> {
                handleGitAgentCommand(args);
                yield true;
            }
            case "/index" -> {
                showIndexStatus();
                yield true;
            }
            case "/legacy" -> {
                printLegacyStory();
                yield true;
            }
            default -> {
                var dynamicCommand = commandRegistry.findCommand(cmd);
                if (dynamicCommand.isPresent()) {
                    try {
                        dynamicCommand.get().execute(args, executionContext);
                    } catch (Exception e) {
                        outputCoordinator.print("Error ejecutando " + cmd + ": " + e.getMessage(),
                            OutputCoordinator.MessageType.ERROR);
                        if (debugMode) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    outputCoordinator.print("Comando desconocido: " + cmd,
                        OutputCoordinator.MessageType.WARNING);

                    Set<String> allCommands = new java.util.HashSet<>(STATIC_SLASH_COMMANDS);
                    for (var dynCmd : commandRegistry.getAllCommands()) {
                        String trigger = dynCmd.getTrigger();
                        allCommands.add(trigger.startsWith("/") ? trigger : "/" + trigger);
                    }

                    var suggestion = CommandSuggester.suggest(cmd, allCommands);
                    if (suggestion.isPresent()) {
                        outputCoordinator.print("  Did you mean: " + suggestion.get() + "?",
                            OutputCoordinator.MessageType.NORMAL);
                    } else {
                        outputCoordinator.print("  Usa /help para ver comandos disponibles.",
                            OutputCoordinator.MessageType.NORMAL);
                    }
                }
                yield true;
            }
        };
    }

    private void handleContextCommand(String args) {
        if (args.isEmpty() || args.equals("show")) {
            if (projectContext == null || projectContext.isBlank()) {
                outputCoordinator.print("No hay contexto del proyecto cargado.",
                    OutputCoordinator.MessageType.WARNING);
            } else {
                outputCoordinator.print("\n" + projectContext);
            }
        } else if (args.equals("refresh")) {
            outputCoordinator.print("Refrescando contexto del proyecto...",
                OutputCoordinator.MessageType.NORMAL);
            this.projectContext = contextCollector.refresh();
            outputCoordinator.print("Contexto actualizado.",
                OutputCoordinator.MessageType.SUCCESS);
        } else if (args.equals("tree")) {
            String tree = contextCollector.collectTreeContext();
            outputCoordinator.print("\n" + tree);
        } else if (args.equals("info")) {
            var info = contextCollector.detectProjectType();
            outputCoordinator.print(String.format("""

                Proyecto: %s
                Tipo: %s
                Lenguaje: %s
                Build: %s
                """,
                info.name(),
                info.type(),
                info.language() != null ? info.language() : "N/A",
                info.buildTool() != null ? info.buildTool() : "N/A"
            ));
        } else {
            outputCoordinator.print("""

                Uso: /context [subcomando]

                Subcomandos:
                  show     - Muestra el contexto actual (default)
                  refresh  - Refresca el contexto del proyecto
                  tree     - Muestra estructura en formato árbol
                  info     - Muestra solo información del proyecto

                """, OutputCoordinator.MessageType.NORMAL);
        }
    }

    private void handleGitAgentCommand(String args) {
        if (args.isEmpty() || args.equals("status")) {
            if (!gitService.isGitRepo()) {
                outputCoordinator.print("No es un repositorio git.",
                    OutputCoordinator.MessageType.WARNING);
                return;
            }

            var branch = gitService.getCurrentBranch().orElse("unknown");
            var autoCommit = gitService.isAutoCommitEnabled() ? "habilitado" : "deshabilitado";

            outputCoordinator.print(String.format("""

                Git Agent Status:
                  Rama: %s
                  Auto-commit: %s
                """, branch, autoCommit));

            gitService.getLastCommit().ifPresent(commit -> {
                outputCoordinator.print(String.format("  Último commit: %s - %s",
                    commit.shortHash(), commit.message()));
            });
        } else if (args.equals("enable")) {
            gitService.setAutoCommitEnabled(true);
            outputCoordinator.print("Auto-commit habilitado.",
                OutputCoordinator.MessageType.SUCCESS);
        } else if (args.equals("disable")) {
            gitService.setAutoCommitEnabled(false);
            outputCoordinator.print("Auto-commit deshabilitado.",
                OutputCoordinator.MessageType.WARNING);
        } else if (args.equals("log")) {
            var commits = gitService.getRecentCommits(5);
            if (commits.isEmpty()) {
                outputCoordinator.print("No hay commits recientes.");
            } else {
                outputCoordinator.print("\nÚltimos commits:");
                for (var commit : commits) {
                    String prefix = commit.isFararoniCommit() ? "[F]" : "   ";
                    outputCoordinator.print(String.format("%s %s - %s",
                        prefix, commit.shortHash(), commit.message()));
                }
            }
        } else {
            outputCoordinator.print("""

                Uso: /git [subcomando]

                Subcomandos:
                  status   - Muestra estado del agente git (default)
                  enable   - Habilita auto-commit
                  disable  - Deshabilita auto-commit
                  log      - Muestra últimos commits

                """, OutputCoordinator.MessageType.NORMAL);
        }
    }

    private String cleanLocalModelResponse(String response) {
        if (response == null || response.isBlank()) {
            return "No pude generar una respuesta.";
        }

        String cleaned = response.trim();

        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            if (cleaned.contains("\"tool\"") && cleaned.contains("\"action\"")) {
                if (cleaned.contains("\"unknown\"")) {
                    return "No entendi ese comando. Puedes intentar de otra forma?";
                }
                return "Comando procesado.";
            }
        }

        if (cleaned.contains("{") && cleaned.contains("}") &&
            cleaned.contains("\"tool\"") && cleaned.contains("\"action\"")) {
            int jsonStart = cleaned.indexOf("{");
            int jsonEnd = cleaned.lastIndexOf("}") + 1;

            if (jsonStart == 0 && jsonEnd < cleaned.length()) {
                String afterJson = cleaned.substring(jsonEnd).trim();
                if (!afterJson.isEmpty()) {
                    return afterJson;
                }
            }
            else if (jsonStart > 0) {
                String beforeJson = cleaned.substring(0, jsonStart).trim();
                if (!beforeJson.isEmpty()) {
                    return beforeJson;
                }
            }
        }

        return cleaned;
    }

    private static final Set<String> STATIC_SLASH_COMMANDS = Set.of(
        "/help", "/h",
        "/exit", "/quit", "/q",
        "/clear", "/cls",
        "/load", "/unload",
        "/list", "/ls",
        "/tokens", "/debug",
        "/history", "/status", "/reconnect",
        "/config", "/router",
        "/context", "/git", "/index"
    );

    private static final Set<String> SAFE_SYSTEM_COMMANDS = Set.of(
        "date", "whoami", "hostname", "pwd", "uname", "uptime", "id", "env"
    );

    private String extractJsonFromResponse(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        String trimmed = response.trim();

        int start = trimmed.indexOf("{");
        if (start < 0) {
            return null;
        }

        int end = trimmed.lastIndexOf("}");

        String json;
        if (end > start) {
            json = trimmed.substring(start, end + 1);
        } else {
            json = trimmed.substring(start);
        }

        json = repairTruncatedJson(json);

        return json;
    }

    private String repairTruncatedJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }

        int openBraces = 0;
        int closeBraces = 0;
        boolean inString = false;
        char prevChar = 0;

        for (char c : json.toCharArray()) {
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            }

            if (!inString) {
                if (c == '{') openBraces++;
                if (c == '}') closeBraces++;
            }

            prevChar = c;
        }

        int missing = openBraces - closeBraces;
        if (missing > 0) {
            if (debugMode) {
                outputCoordinator.printDebug("[JSON Repair] Agregando " + missing + " llaves de cierre");
            }
            StringBuilder sb = new StringBuilder(json);
            for (int i = 0; i < missing; i++) {
                sb.append("}");
            }
            return sb.toString();
        }

        return json;
    }

    private boolean isValidToolJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        return json.contains("\"tool\"") && json.contains("\"action\"");
    }

    private boolean isSystemToolJson(String json) {
        if (json == null) return false;
        String lower = json.toLowerCase();
        return lower.contains("\"tool\"") &&
               (lower.contains("\"system\"") || lower.contains(": \"system\""));
    }

    private boolean isGitToolJson(String json) {
        if (json == null) return false;
        String lower = json.toLowerCase();
        return lower.contains("\"tool\"") &&
               (lower.contains("\"git\"") || lower.contains(": \"git\""));
    }

    private String extractCommandFromJson(String json) {
        if (json == null) return null;

        int cmdIndex = json.indexOf("\"command\"");
        if (cmdIndex == -1) return null;

        int colonIndex = json.indexOf(":", cmdIndex);
        if (colonIndex == -1) return null;

        int valueStart = json.indexOf("\"", colonIndex) + 1;
        if (valueStart <= 0) return null;

        int valueEnd = json.indexOf("\"", valueStart);
        if (valueEnd <= valueStart) return null;

        return json.substring(valueStart, valueEnd);
    }

    private String extractActionFromJson(String json) {
        if (json == null) return null;

        int actIndex = json.indexOf("\"action\"");
        if (actIndex == -1) return null;

        int colonIndex = json.indexOf(":", actIndex);
        if (colonIndex == -1) return null;

        int valueStart = json.indexOf("\"", colonIndex) + 1;
        if (valueStart <= 0) return null;

        int valueEnd = json.indexOf("\"", valueStart);
        if (valueEnd <= valueStart) return null;

        return json.substring(valueStart, valueEnd);
    }

    private boolean executeSystemToolCommand(String command) {
        if (command == null || command.isBlank()) {
            outputCoordinator.printError("Comando vacio");
            return false;
        }

        String baseCommand = command.split("\\s+")[0];

        if (!SAFE_SYSTEM_COMMANDS.contains(baseCommand)) {
            outputCoordinator.print("[WARN] Comando no permitido: " + command,
                OutputCoordinator.MessageType.WARNING);
            outputCoordinator.print("   Comandos seguros: " + SAFE_SYSTEM_COMMANDS,
                OutputCoordinator.MessageType.NORMAL);
            return false;
        }

        auditLogger.log(AuditLogger.Level.INFO, AuditLogger.Category.COMMAND,
            "SYSTEM tool execution: " + command, "source=local_model");

        if (debugMode) {
            outputCoordinator.print("[EXEC] Ejecutando: " + command, OutputCoordinator.MessageType.NORMAL);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputCoordinator.print(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 && debugMode) {
                outputCoordinator.printDebug("Comando termino con codigo: " + exitCode);
            }

            return true;
        } catch (Exception e) {
            outputCoordinator.printError("Error ejecutando comando: " + e.getMessage());
            auditLogger.logError(AuditLogger.Category.COMMAND, "Error en SYSTEM tool", e);
            if (debugMode) {
                e.printStackTrace();
            }
            return false;
        }
    }

    private boolean processLocalModelResponse(String localResponse) {
        if (localResponse == null || localResponse.isBlank()) {
            outputCoordinator.print("\nNo pude generar una respuesta.");
            return true;
        }

        if (agentModeEnabled && containsActionBlocks(localResponse)) {
            outputCoordinator.print("");
            processWithActionParser(localResponse);
            return true;
        }

        if (tryExecuteWithFnl(localResponse)) {
            return true;
        }

        String json = extractJsonFromResponse(localResponse);

        if (json != null && isValidToolJson(json)) {
            if (isSystemToolJson(json)) {
                String command = extractCommandFromJson(json);
                if (command != null) {
                    outputCoordinator.print("");
                    boolean executed = executeSystemToolCommand(command);
                    if (executed) {
                        return true;
                    }
                }
            }

            if (isGitToolJson(json)) {
                String action = extractActionFromJson(json);
                if (action != null) {
                    outputCoordinator.print("");
                    outputCoordinator.print("[RTR] [local] git:" + action,
                        OutputCoordinator.MessageType.NORMAL);

                    switch (action.toLowerCase()) {
                        case "push" -> executeShellCommand("git push");
                        case "pull" -> executeShellCommand("git pull");
                        case "status" -> executeShellCommand("git status");
                        case "diff" -> executeShellCommand("git diff");
                        case "log" -> executeShellCommand("git log --oneline -10");
                        default -> outputCoordinator.print("Accion git no soportada: " + action,
                            OutputCoordinator.MessageType.WARNING);
                    }
                    return true;
                }
            }

            if (debugMode) {
                outputCoordinator.printDebug("[Tool] JSON detectado pero no ejecutado: " + json);
            }
        }

        String cleanResponse = cleanLocalModelResponse(localResponse);
        outputCoordinator.print("\n" + cleanResponse);
        String sanitizedResponse = OutputSanitizer.sanitize(cleanResponse);
        List<String> codeBlocks = OutputSanitizer.extractCodeBlocks(cleanResponse);
        if (!codeBlocks.isEmpty()) {
            SessionContextPersistence.getInstance().trackCodeBlocks(codeBlocks);
        }
        conversationHistory.add(Message.assistant(sanitizedResponse));

        return true;
    }

    private boolean containsActionBlocks(String response) {
        if (response == null) return false;
        String upper = response.toUpperCase();
        return upper.contains(">>>FILE:") || upper.contains(">>>MKDIR:");
    }

    private boolean tryExecuteWithFnl(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }

        var toolRequest = fnlService.parseToolRequest(response);

        if (toolRequest.isEmpty()) {
            if (debugMode) {
                outputCoordinator.printDebug("[FNL] No se detectó herramienta en respuesta");
            }
            return false;
        }

        ToolRequest req = toolRequest.get();
        String toolName = req.toolName();
        String action = req.action();

        if (debugMode) {
            outputCoordinator.printDebug("[FNL] Detectado: " + toolName + ":" + action);
        }

        if (!fnlService.hasSkill(toolName)) {
            if (debugMode) {
                outputCoordinator.printDebug("[FNL] Skill no registrado: " + toolName +
                    ". Disponibles: " + fnlService.getSkillNames());
            }
            return false;
        }

        try {
            outputCoordinator.print("");

            if (debugMode) {
                outputCoordinator.print("[FNL] Ejecutando: " + toolName + ":" + action,
                    OutputCoordinator.MessageType.NORMAL);
            }

            ToolResponse result = fnlService.execute(req);

            if (result.success()) {
                String output = result.result();
                if (output != null && !output.isBlank()) {
                    outputCoordinator.print(output);
                }

                auditLogger.log(AuditLogger.Level.INFO, AuditLogger.Category.COMMAND,
                    "FNL executed: " + toolName + ":" + action,
                    "success=true, output_length=" + (output != null ? output.length() : 0));
            } else {
                String errorMsg = result.errorMessage() != null ? result.errorMessage() : result.result();
                outputCoordinator.print("[WARN] " + errorMsg,
                    OutputCoordinator.MessageType.WARNING);

                auditLogger.log(AuditLogger.Level.WARN, AuditLogger.Category.COMMAND,
                    "FNL execution failed: " + toolName + ":" + action,
                    "error=" + errorMsg);
            }

            return true;
        } catch (Exception e) {
            if (debugMode) {
                outputCoordinator.printDebug("[FNL] Error ejecutando: " + e.getMessage());
            }
            auditLogger.logError(AuditLogger.Category.COMMAND, "FNL execution error", e);
            return false;
        }
    }

    private void processWithActionParser(String response) {
        actionParser.reset();

        String[] lines = response.split("\n");
        for (String line : lines) {
            actionParser.processLine(line);
        }

        actionParser.flush();

        var results = actionParser.getResults();
        for (var result : results) {
            if (result.success()) {
                auditLogger.log(AuditLogger.Level.INFO, AuditLogger.Category.FILE_OP,
                    "Agent created: " + result.path(), result.type().name());
            } else {
                auditLogger.log(AuditLogger.Level.WARN, AuditLogger.Category.FILE_OP,
                    "Agent failed: " + result.path(), result.message());
            }
        }

        if (actionParser.hasFileOperations()) {
            int fileCount = (int) results.stream().filter(r -> r.success()).count();
            conversationHistory.add(Message.assistant(
                "[Agente: " + fileCount + " archivo(s) creado(s)]"));
        }
    }

    private void handleQuestion(String question) {
        this.lastUserQuestion = question;

        conversationHistory.add(Message.user(question));

        if (enterpriseRouter != null) {
            RoutingPlan plan = enterpriseRouter.route(question);

            if (debugMode) {
                outputCoordinator.printDebug("[Router] " + plan.toAuditString());
            }

            switch (plan.target()) {
                case LOCAL -> {
                    if (handleWithLocalModel(question, plan)) {
                        return;
                    }
                    if (debugMode) {
                        outputCoordinator.printDebug("[Router] LOCAL failed, falling back to EXPERT");
                    }
                    handleWithExpertModel(question);
                }
                case EXPERT -> {
                    if (!handleWithExpertModel(question)) {
                        if (debugMode) {
                            outputCoordinator.printDebug("[Router] EXPERT failed, falling back to LOCAL");
                        }
                        handleWithLocalModel(question, plan);
                    }
                }
            }
        } else {
            handleWithExpertModel(question);
        }
    }

    private boolean handleWithLocalModel(String question, RoutingPlan plan) {
        var spinner = SpinnerUI.generating();

        try {
            if (!NativeLoader.isNativeLibraryAvailable()) {
                if (debugMode) {
                    outputCoordinator.printDebug("[LocalLLM] Libreria nativa no disponible - escalando a EXPERT");
                }
                return false;
            }

            LocalLlmConfig localConfig = LocalLlmConfig.fromEnvironment();

            if (!localConfig.isModelDownloaded()) {
                if (debugMode) {
                    outputCoordinator.printDebug("[LocalLLM] Modelo no descargado - escalando a EXPERT");
                }
                return false;
            }

            if (!localConfig.hasEnoughRam()) {
                if (debugMode) {
                    outputCoordinator.printDebug("[LocalLLM] RAM insuficiente - escalando a EXPERT");
                }
                return false;
            }

            isCurrentlyUsingLocal = true;
            updateStatusFooter();

            if (localLlmService == null) {
                LocalLlmService shared = ServiceRegistry.getSharedLocalLlm();
                if (shared != null) {
                    localLlmService = shared;
                } else {
                    System.out.println(ansi().fgCyan().a("\n[START] Cargando modelo local (primera vez)...").reset());
                    localLlmService = new LocalLlmService(localConfig);
                }
            }

            System.out.println(ansi().fgGreen().a("\n💻 El Capataz (Qwen 1.5B) - " +
                plan.detectedIntent().getDisplayName()).reset());

            String formattedPrompt = buildLocalLlmPrompt(question);

            StringBuilder responseBuilder = new StringBuilder();
            localLlmService.generateStream(
                formattedPrompt,
                token -> responseBuilder.append(token)
            );

            String rawResponse = responseBuilder.toString().trim();

            String sanitizedResponse = OutputSanitizer.sanitize(rawResponse);
            List<String> codeBlocks = OutputSanitizer.extractCodeBlocks(rawResponse);
            if (!codeBlocks.isEmpty()) {
                SessionContextPersistence.getInstance().trackCodeBlocks(codeBlocks);
            }
            conversationHistory.add(Message.assistant(sanitizedResponse));

            processLocalModelResponse(rawResponse);

            auditLogger.log(AuditLogger.Level.INFO, AuditLogger.Category.LLM_CALL,
                "Local LLM (El Capataz) inference successful",
                "intent=" + plan.detectedIntent().name() + ", complexity=" + plan.complexityScore());

            return true;
        } catch (NoClassDefFoundError | UnsatisfiedLinkError | ExceptionInInitializerError e) {
            if (debugMode) {
                outputCoordinator.printDebug("[LocalLLM] JNI/Native error: " + e.getMessage());
            }
            return false;
        } catch (Exception e) {
            if (debugMode) {
                outputCoordinator.printDebug("[LocalLLM] Error: " + e.getMessage());
            }
            auditLogger.logError(AuditLogger.Category.LLM_CALL, "Local LLM (El Capataz) failed", e);
            return false;
        } finally {
            spinner.stop();
        }
    }

    private boolean handleWithExpertModel(String question) {
        var spinner = SpinnerUI.generating();
        isCurrentlyUsingLocal = false;
        updateStatusFooter();

        try {
            String fullPrompt = contextManager.assemblePrompt(
                config.systemPrompt(),
                activeContext != null ? List.of(activeContext) : List.of(),
                conversationHistory,
                question
            );

            boolean showThinking = Boolean.getBoolean("FARARONI_SHOW_REASONING");
            var request = GenerationRequest.builder()
                .messages(List.of(Message.system(config.systemPrompt()), Message.user(fullPrompt)))
                .maxTokens(config.maxTokens())
                .temperature(config.temperature())
                .topP(config.topP())
                .stream(config.streaming())
                .think(showThinking)
                .build();

            if (config.streaming()) {
                handleStreamingResponse(request, spinner);
            } else {
                var response = client.generate(request);
                String sanitizedResponse = OutputSanitizer.sanitize(response.text());
                List<String> codeBlocks = OutputSanitizer.extractCodeBlocks(response.text());
                if (!codeBlocks.isEmpty()) {
                    SessionContextPersistence.getInstance().trackCodeBlocks(codeBlocks);
                }
                conversationHistory.add(Message.assistant(sanitizedResponse));
                outputCoordinator.print("\n" + response.text());

                ServiceRegistry.clearSessionError();

                if (showTokens) {
                    printUsageStats(response.usage());
                }
            }

            return true;
        } catch (Exception e) {
            spinner.stop();
            handleGenerationError(e);
            return false;
        } finally {
            spinner.stop();
        }
    }

    private void handleGenerationError(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        ServiceRegistry.recordError(e);

        if (isConnectionError(message)) {
            outputCoordinator.printError("No se puede conectar al servidor LLM");

            if (tryLocalModelInference()) {
                return;
            }

            if (offerLocalModelDownload()) {
                return;
            }

            System.out.println(ansi().fgYellow().a("""

                [*] FARARONI necesita un servidor LLM para responder preguntas.

                Opciones disponibles:

                1. Ollama (local, gratis):
                   brew install ollama
                   ollama run qwen2.5-coder
                   ./fararoni-core -u http:

                2. vLLM (local, requiere GPU):
                   python -m vllm.entrypoints.openai.api_server --model Qwen/Qwen2.5-Coder-7B
                   ./fararoni-core -u http:

                3. OpenAI (nube, requiere API key):
                   ./fararoni-core -u https:

                Nota: No necesitas agregar /v1, FARARONI lo añade automaticamente.

                """).reset());
        } else {
            outputCoordinator.printError("Error generando respuesta: " + e.getMessage());
        }

        auditLogger.logError(AuditLogger.Category.LLM_CALL, "Error generando respuesta LLM", e);
        if (debugMode) {
            e.printStackTrace();
        }
    }

    private boolean tryLocalModelInference() {
        try {
            if (!NativeLoader.isNativeLibraryAvailable()) {
                if (debugMode) {
                    outputCoordinator.printDebug("[LocalLLM] Libreria nativa no disponible");
                }
                return false;
            }

            LocalLlmConfig localConfig = LocalLlmConfig.fromEnvironment();

            if (!localConfig.isModelDownloaded()) {
                if (debugMode) {
                    outputCoordinator.printDebug("[LocalLLM] Modelo no descargado");
                }
                return false;
            }

            if (!localConfig.hasEnoughRam()) {
                if (debugMode) {
                    outputCoordinator.printDebug("[LocalLLM] RAM insuficiente: " +
                        localConfig.getAvailableRamMb() + "MB disponible, " +
                        localConfig.minFreeRamMb() + "MB requerido");
                }
                return false;
            }

            if (lastUserQuestion == null || lastUserQuestion.isBlank()) {
                return false;
            }

            if (localLlmService == null) {
                System.out.println(ansi().fgCyan().a("\n[START] Cargando modelo local (primera vez)...").reset());
                localLlmService = new LocalLlmService(localConfig);
            }

            System.out.println(ansi().fgGreen().a("\n💻 Usando modelo local (Qwen 2.5 Coder 1.5B)").reset());

            String formattedPrompt = buildLocalLlmPrompt(lastUserQuestion);

            StringBuilder responseBuilder = new StringBuilder();

            localLlmService.generateStream(
                formattedPrompt,
                token -> {
                    responseBuilder.append(token);
                }
            );

            String rawResponse = responseBuilder.toString().trim();

            processLocalModelResponse(rawResponse);

            auditLogger.log(AuditLogger.Level.INFO, AuditLogger.Category.LLM_CALL,
                "Local LLM inference successful", "model=qwen-1.5b");

            return true;
        } catch (NoClassDefFoundError | UnsatisfiedLinkError | ExceptionInInitializerError e) {
            if (debugMode) {
                outputCoordinator.printDebug("[LocalLLM] JNI/Native error: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println(ansi().fgYellow().a("""

                [WARN] El modelo local requiere ejecutar con JAR (no binario nativo).

                El binario nativo tiene limitaciones con librerías JNI.
                Para usar el modelo local Qwen 2.5 Coder, ejecuta:

                  java -jar fararoni-core/target/fararoni-core-1.0.0.jar

                O usa un servidor externo (Ollama, vLLM, OpenAI).

                """).reset());
            return false;
        } catch (Exception e) {
            if (debugMode) {
                outputCoordinator.printDebug("[LocalLLM] Error: " + e.getMessage());
                e.printStackTrace();
            }
            auditLogger.logError(AuditLogger.Category.LLM_CALL, "Local LLM inference failed", e);
            return false;
        }
    }

    private String buildLocalLlmPrompt(String userInput) {
        String agentInstructions = protocolStrategy.getSystemInstructions();

        String systemBlock = """
            <|im_start|>system
            You are Fararoni, an intelligent CLI assistant that can CREATE FILES.

            Available Tools:
            - system: exec (for date, time, whoami, pwd, uname, hostname)
            - git: push, pull, status, diff, commit, branch, log
            - file: load, unload, search, list
            - config: set, get, show, delete

            FILE CREATION (IMPORTANT):
            When asked to create a file or write code, use this EXACT format:

            >>>FILE: path/to/file.ext
            file content here
            <<<END_FILE

            RULES:

            1. DATE/TIME/USER QUESTIONS:
               Use system tool. Example: {"tool":"system","action":"exec","params":{"command":"date"}}

            2. GIT/FILE/CONFIG TASKS:
               Use JSON. Example: {"tool":"git","action":"push"}

            3. FILE CREATION:
               Use >>>FILE: format. The file will be created on disk.

            4. GREETINGS/CHAT:
               Reply in plain text. NO JSON.

            EXAMPLES:

            User: hola
            Assistant: Hola! En que te ayudo?

            User: que fecha es hoy
            Assistant: {"tool":"system","action":"exec","params":{"command":"date"}}

            User: crea Alumno.java
            Assistant: >>>FILE: Alumno.java
            public class Alumno {
                private String nombre;
                private int edad;

                public Alumno(String nombre, int edad) {
                    this.nombre = nombre;
                    this.edad = edad;
                }

                public String getNombre() { return nombre; }
                public int getEdad() { return edad; }
            }
            <<<END_FILE

            User: sube cambios
            Assistant: {"tool":"git","action":"push"}

            User: carga pom.xml
            Assistant: {"tool":"file","action":"load","params":{"file":"pom.xml"}}
            <|im_end|>
            """;

        String contextBlock = "";
        if (projectContext != null && !projectContext.isBlank()) {
            contextBlock = String.format("""
            <|im_start|>system
            CURRENT PROJECT CONTEXT:
            %s
            Use this information to suggest appropriate file locations and follow existing conventions.
            <|im_end|>
            """, projectContext);
        }

        String sessionBlock = "";
        try {
            String sessionCtx = ServiceRegistry.getSessionContextForPrompt();
            if (sessionCtx != null && !sessionCtx.isBlank()) {
                sessionBlock = String.format("""
            <|im_start|>system
            SESSION CONTEXT (recent activity):
            %s
            <|im_end|>
            """, sessionCtx);
            }
        } catch (Exception e) {
        }

        String loadedContextBlock = "";
        if (activeContext != null && !activeContext.isBlank()) {
            String ctx = activeContext.length() > 2000
                ? activeContext.substring(0, 1997) + "..."
                : activeContext;
            loadedContextBlock = String.format("""
            <|im_start|>system
            LOADED FILES CONTEXT (use this information to answer):
            %s
            <|im_end|>
            """, ctx);
        }

        String historyBlock = "";
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            int maxMessages = 4;
            int startIdx = Math.max(0, conversationHistory.size() - maxMessages);
            StringBuilder histSb = new StringBuilder();

            for (int i = startIdx; i < conversationHistory.size(); i++) {
                Message msg = conversationHistory.get(i);
                String content = msg.content();
                if (content.length() > 2000) {
                    content = content.substring(0, 1997) + "...";
                }
                String role = msg.role().toLowerCase();
                histSb.append("<|im_start|>").append(role).append("\n");
                histSb.append(content).append("\n");
                histSb.append("<|im_end|>\n");
            }

            if (histSb.length() > 0) {
                historyBlock = histSb.toString();
            }
        }

        String userBlock = String.format("""
            <|im_start|>user
            %s<|im_end|>
            <|im_start|>assistant
            """, sanitizeUserInput(userInput));

        return systemBlock + contextBlock + sessionBlock + loadedContextBlock + historyBlock + userBlock;
    }

    private String sanitizeUserInput(String input) {
        if (input == null) {
            return "";
        }
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", "")
            .trim();
    }

    private boolean offerLocalModelDownload() {
        try {
            LocalLlmService.EngineState state = getEngineState();

            switch (state) {
                case READY:
                    if (debugMode) {
                        outputCoordinator.printDebug("[LocalLLM] Motor listo pero inferencia falló");
                    }
                    return false;

                case MISSING_ENGINE:
                    if (debugMode) {
                        outputCoordinator.printDebug("[LocalLLM] Motor no instalado - ofreciendo instalación");
                    }
                    return offerEngineInstall();

                case DISABLED:
                    if (debugMode) {
                        outputCoordinator.printDebug("[LocalLLM] Motor deshabilitado - recursos insuficientes");
                    }
                    return false;

                case MISSING_MODEL:
                    break;
            }

            if (!NativeLoader.isNativeLibraryAvailable()) {
                if (debugMode) {
                    outputCoordinator.printDebug("[LocalLLM] No se ofrece descarga: libreria nativa no disponible");
                }
                return false;
            }

            LocalLlmConfig config = LocalLlmConfig.fromEnvironment();

            if (config.isModelDownloaded()) {
                if (debugMode) {
                    outputCoordinator.printDebug("[LocalLLM] Modelo existe pero no se pudo usar");
                }
                return false;
            }

            if (!config.hasEnoughRam()) {
                if (debugMode) {
                    System.out.println(ansi().fgYellow().a(
                        "[Debug] RAM insuficiente para modelo local: " +
                        config.getAvailableRamMb() + "MB disponible, " +
                        config.minFreeRamMb() + "MB requerido"
                    ).reset());
                }
                return false;
            }

            ModelDownloader downloader = new ModelDownloader();
            if (!downloader.hasEnoughDiskSpace()) {
                if (debugMode) {
                    System.out.println(ansi().fgYellow().a(
                        "[Debug] Espacio en disco insuficiente para modelo"
                    ).reset());
                }
                return false;
            }

            System.out.println(ansi().fgCyan().a("""

                +==============================================================+
                |         [*] MODO LOCAL DISPONIBLE (On-Demand AI)             |
                +==============================================================+
                |                                                              |
                |  No detecte un servidor LLM externo.                         |
                |  Puedo descargar un modelo local para funcionar sin          |
                |  necesidad de Ollama, vLLM u OpenAI.                         |
                |                                                              |
                |  [PKG] Modelo: Qwen 2.5 Coder 1.5B                           |
                |  [DSK] Tamano: ~1.2 GB                                       |
                |  [RAM] Requerida: 2 GB                                       |
                |                                                              |
                +==============================================================+

                """).reset());

            String response;
            try {
                response = lineReader.readLine(
                    ansi().fgYellow().a("¿Deseas descargar el modelo ahora? [S/n]: ").reset().toString());
            } catch (UserInterruptException | EndOfFileException e) {
                return false;
            }

            if (response == null) {
                return false;
            }

            response = response.trim().toLowerCase();
            if (response.isEmpty() || response.equals("s") || response.equals("si") || response.equals("y") || response.equals("yes")) {
                return downloadLocalModel(downloader);
            }

            return false;
        } catch (Exception e) {
            if (debugMode) {
                System.out.println(ansi().fgRed().a("[Debug] Error verificando modelo local: " + e.getMessage()).reset());
            }
            return false;
        }
    }

    private LocalLlmService.EngineState getEngineState() {
        try {
            LocalLlmService service = new LocalLlmService();
            return service.getEngineState();
        } catch (Exception e) {
            return LocalLlmService.EngineState.DISABLED;
        }
    }

    private boolean offerEngineInstall() {
        try {
            LocalLlmService service = new LocalLlmService();

            if (!service.shouldOfferEngineInstall()) {
                if (debugMode) {
                    outputCoordinator.printDebug("[LocalLLM] No se puede ofrecer instalación del motor");
                }
                return false;
            }

            String platform = service.getPlatformInfo();
            String libName = NativeLoader.getLibraryName();

            System.out.println(ansi().fgCyan().a(String.format("""

                +==============================================================+
                |         [CFG] MOTOR LOCAL NO INSTALADO                       |
                +==============================================================+
                |                                                              |
                |  Para habilitar el modo local, necesito descargar           |
                |  la libreria nativa de inferencia.                          |
                |                                                              |
                |  [LIB] Libreria: %s                              |
                |  [SYS] Plataforma: %s                                |
                |  [DSK] Tamano: ~5 MB                                         |
                |  [DIR] Ubicacion: ~/.llm-fararoni/lib/                       |
                |                                                              |
                +==============================================================+

                """, padRight(libName, 18), padRight(platform, 24))).reset());

            String response;
            try {
                response = lineReader.readLine(
                    ansi().fgYellow().a("¿Deseas instalar el motor ahora? [S/n]: ").reset().toString());
            } catch (UserInterruptException | EndOfFileException e) {
                return false;
            }

            if (response == null) {
                return false;
            }

            response = response.trim().toLowerCase();
            if (response.isEmpty() || response.equals("s") || response.equals("si") || response.equals("y") || response.equals("yes")) {
                return installEngine(service);
            }

            return false;
        } catch (Exception e) {
            if (debugMode) {
                outputCoordinator.printDebug("[LocalLLM] Error ofreciendo instalación: " + e.getMessage());
            }
            return false;
        }
    }

    private boolean installEngine(LocalLlmService service) {
        System.out.println(ansi().fgCyan().a("\n[DOWNLOAD] Instalando motor local...\n").reset());

        try {
            final long[] lastPrintTime = {0};

            boolean success = service.installEngine(progress -> {
                long now = System.currentTimeMillis();

                if (now - lastPrintTime[0] > 500 || progress.state() != DownloadState.DOWNLOADING) {
                    lastPrintTime[0] = now;

                    switch (progress.state()) {
                        case STARTING -> System.out.println(ansi().fgCyan().a(
                            "  ▶ " + progress.message()).reset());
                        case DOWNLOADING -> System.out.print(ansi().fgGreen().a(
                            "\r  ⬇ " + progress.message() + "          ").reset());
                        case RETRYING -> System.out.println(ansi().fgYellow().a(
                            "\n  ⟳ " + progress.message()).reset());
                        case COMPLETED -> System.out.println(ansi().fgGreen().a(
                            "\n\n  [OK] " + progress.message()).reset());
                        case FAILED -> System.out.println(ansi().fgRed().a(
                            "\n  [ERROR] " + progress.message()).reset());
                    }
                }
            });

            if (success) {
                System.out.println(ansi().fgGreen().a("""

                    ╔══════════════════════════════════════════════════════════════╗
                    ║           🎉 MOTOR INSTALADO EXITOSAMENTE                    ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║                                                              ║
                    ║  El motor de inferencia local está listo.                    ║
                    ║  Ahora puedo descargar el modelo para funcionar              ║
                    ║  completamente sin conexión a internet.                      ║
                    ║                                                              ║
                    ╚══════════════════════════════════════════════════════════════╝

                    """).reset());

                auditLogger.log(AuditLogger.Level.INFO, AuditLogger.Category.SYSTEM,
                    "Motor nativo instalado", service.getPlatformInfo());

                return offerLocalModelDownload();
            } else {
                System.out.println(ansi().fgRed().a("\n[ERROR] La instalacion fallo. Intenta de nuevo mas tarde.").reset());
                return false;
            }
        } catch (Exception e) {
            System.out.println(ansi().fgRed().a("\n[ERROR] Error durante la instalacion: " + e.getMessage()).reset());
            if (debugMode) {
                e.printStackTrace();
            }
            return false;
        }
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    private boolean downloadLocalModel(ModelDownloader downloader) {
        System.out.println(ansi().fgCyan().a("\n[DOWNLOAD] Iniciando descarga del modelo...\n").reset());

        try {
            final long[] lastPrintTime = {0};

            boolean success = downloader.download(progress -> {
                long now = System.currentTimeMillis();

                if (now - lastPrintTime[0] > 500 || progress.state() != DownloadState.DOWNLOADING) {
                    lastPrintTime[0] = now;

                    switch (progress.state()) {
                        case STARTING -> System.out.println(ansi().fgCyan().a(
                            "  ▶ " + progress.message()).reset());
                        case DOWNLOADING -> System.out.print(ansi().fgGreen().a(
                            "\r  ⬇ " + progress.message() + "          ").reset());
                        case RETRYING -> System.out.println(ansi().fgYellow().a(
                            "\n  ⟳ " + progress.message()).reset());
                        case COMPLETED -> System.out.println(ansi().fgGreen().a(
                            "\n\n  [OK] " + progress.message()).reset());
                        case FAILED -> System.out.println(ansi().fgRed().a(
                            "\n  [ERROR] " + progress.message()).reset());
                    }
                }
            });

            if (success) {
                System.out.println(ansi().fgGreen().a("""

                    +==============================================================+
                    |           [OK] MODELO INSTALADO EXITOSAMENTE                 |
                    +==============================================================+
                    |                                                              |
                    |  El modelo Qwen 2.5 Coder 1.5B esta listo.                   |
                    |                                                              |
                    |  [DIR] Ubicacion: ~/.llm-fararoni/models/                    |
                    |                                                              |
                    +==============================================================+

                    """).reset());

                auditLogger.log(AuditLogger.Level.INFO, AuditLogger.Category.SYSTEM,
                    "Modelo local descargado", "Qwen 2.5 Coder 1.5B");

                System.out.println(ansi().fgCyan().a("[LOADING] Cargando modelo en memoria...").reset());
                if (ServiceRegistry.reloadLocalLlm()) {
                    System.out.println(ansi().fgGreen().a("""

                    ✨ ¡Modelo listo! Escribe tu pregunta para usar el modelo local.

                    """).reset());
                } else {
                    System.out.println(ansi().fgYellow().a("""

                    [WARN] El modelo se descargo pero no se pudo cargar automaticamente.
                    Reinicia FARARONI para usar el modelo local.

                    """).reset());
                }

                return true;
            } else {
                System.out.println(ansi().fgRed().a("\n[ERROR] La descarga fallo. Intenta de nuevo mas tarde.").reset());
                return false;
            }
        } catch (Exception e) {
            System.out.println(ansi().fgRed().a("\n[ERROR] Error durante la descarga: " + e.getMessage()).reset());
            if (debugMode) {
                e.printStackTrace();
            }
            return false;
        }
    }

    private boolean isConnectionError(String message) {
        return message.contains("failed to connect") ||
               message.contains("connection refused") ||
               message.contains("connect timed out") ||
               message.contains("no route to host") ||
               message.contains("network is unreachable") ||
               message.contains("servidor no responde") ||
               message.contains("server not responding");
    }

    private void handleStreamingResponse(GenerationRequest request, SpinnerUI spinner) {
        var responseBuilder = new StringBuilder();
        var hasError = new AtomicBoolean(false);

        client.generateStream(
            request,
            token -> {
                System.out.print(token);
                System.out.flush();
                responseBuilder.append(token);
            },
            error -> {
                hasError.set(true);
                spinner.stop();
                handleGenerationError(new Exception(error));
            },
            () -> {
                if (!hasError.get()) {
                    System.out.println();
                    String rawResponse = responseBuilder.toString();
                    String sanitizedResponse = OutputSanitizer.sanitize(rawResponse);
                    List<String> codeBlocks = OutputSanitizer.extractCodeBlocks(rawResponse);
                    if (!codeBlocks.isEmpty()) {
                        SessionContextPersistence.getInstance().trackCodeBlocks(codeBlocks);
                    }
                    conversationHistory.add(Message.assistant(sanitizedResponse));
                    ServiceRegistry.clearSessionError();
                }
            }
        );
    }

    private void loadFiles(String paths) {
        if (paths.isEmpty()) {
            outputCoordinator.printError("Debes especificar rutas: /load <archivo1> [archivo2] ...");
            return;
        }

        var pathList = List.of(paths.split("\\s+"));
        var result = fileManager.loadFiles(pathList, "Archivos cargados por usuario");

        if (result.success()) {
            activeContext = result.context();
            outputCoordinator.print(
                String.format("[OK] %d archivos cargados (%,d tokens)",
                    result.getTotalFiles(), result.totalTokens()),
                OutputCoordinator.MessageType.SUCCESS);
        } else {
            outputCoordinator.printError("Error cargando archivos: " + result.errorMessage());
        }
    }

    private void unloadContext() {
        if (activeContext == null) {
            outputCoordinator.print("No hay contexto cargado.", OutputCoordinator.MessageType.WARNING);
            return;
        }

        activeContext = null;
        outputCoordinator.print("[OK] Contexto descargado", OutputCoordinator.MessageType.SUCCESS);
    }

    private void listFiles(String directory) {
        var dir = directory.isEmpty() ? "." : directory;
        fileManager.listFiles(dir, true);
    }

    private void toggleTokens() {
        showTokens = !showTokens;
        outputCoordinator.print("Mostrar tokens: " + (showTokens ? "ACTIVADO" : "DESACTIVADO"),
            OutputCoordinator.MessageType.SUCCESS);
    }

    private void toggleDebug() {
        debugMode = !debugMode;
        outputCoordinator.print("Modo debug: " + (debugMode ? "ACTIVADO" : "DESACTIVADO"),
            OutputCoordinator.MessageType.SUCCESS);
    }

    private void clearHistory() {
        conversationHistory.clear();
        activeContext = null;
        outputCoordinator.print("[OK] Historial y contexto limpiados", OutputCoordinator.MessageType.SUCCESS);
    }

    private void showHistory() {
        if (conversationHistory.isEmpty()) {
            outputCoordinator.print("Historial vacío", OutputCoordinator.MessageType.WARNING);
            return;
        }

        outputCoordinator.print("[HIST] Historial de conversacion:");
        for (int i = 0; i < conversationHistory.size(); i++) {
            var msg = conversationHistory.get(i);
            var prefix = msg.role().equals("user") ? "[U]" : "[A]";
            outputCoordinator.print(String.format("%s [%d] %s", prefix, i + 1, msg.content()));
        }
    }

    private void showStatus() {
        String routerInfo = "DESHABILITADO";
        if (routerEnabled) {
            var router = ServiceRegistry.getRouterService();
            if (router != null) {
                var stats = router.getStats();
                routerInfo = router.getName() + (router.isLlmBased() ? " (LLM)" : " (Regex)");
                if (stats != null && stats.totalRequests() > 0) {
                    routerInfo += String.format(" [%d requests, %.1f%% LLM]",
                        stats.totalRequests(),
                        (1.0 - stats.getFallbackRate()) * 100);
                }
            }
        }

        outputCoordinator.print(String.format("""

            [SYS] Estado del Sistema:
            - Modelo: %s
            - Servidor: %s
            - Contexto cargado: %s
            - Mensajes en historial: %d
            - Tokenizador: %s
            - Estrategia de contexto: %s
            - Router Semantico: %s
            - Mostrar tokens: %s
            - Modo debug: %s
            """,
            config.modelName(),
            config.serverUrl(),
            activeContext != null ? "SÍ" : "NO",
            conversationHistory.size(),
            config.tokenizerMode(),
            contextManager.getStrategyName(),
            routerInfo,
            showTokens ? "SÍ" : "NO",
            debugMode ? "SÍ" : "NO"
        ));
    }

    private void showIndexStatus() {
        outputCoordinator.print("\n[IDX] Estado del Indice (Memoria Persistente):\n");

        try {
            try (var indexStore = new IndexStore()) {
                if (indexStore.isAvailable()) {
                    var stats = indexStore.getStats();

                    outputCoordinator.print(String.format("""
                        • Archivos indexados: %d
                        • Exitosos: %d (%.1f%%)
                        • Fallidos: %d
                        • No parseables: %d
                        • Pendientes: %d
                        • DB: %s
                        """,
                        stats.totalFiles(),
                        stats.successFiles(),
                        stats.successRate(),
                        stats.failedFiles(),
                        stats.unparseableFiles(),
                        stats.pendingFiles(),
                        indexStore.getDbPath()
                    ));
                } else {
                    outputCoordinator.print("    [WARN] IndexStore no disponible.\n");
                    outputCoordinator.print("    Ejecuta primero en modo Swarm para inicializar el indexado.\n");
                }
            }
        } catch (Exception e) {
            outputCoordinator.print("    [ERROR] Error obteniendo estadisticas: " + e.getMessage() + "\n",
                OutputCoordinator.MessageType.ERROR);
        }
    }

    private void handleReconnect(String args) {
        outputCoordinator.print("\n[NET] Reconectando al servidor LLM...\n");

        try {
            var resolver = new ConfigPriorityResolver();

            String newServerUrl;
            String newModelName;

            if (args.isEmpty()) {
                newServerUrl = resolver.resolve("server-url", config.serverUrl());
                newModelName = resolver.resolve("model-name", config.modelName());
            } else {
                var parts = args.trim().split("\\s+", 2);
                newServerUrl = parts[0];
                newModelName = parts.length > 1 ? parts[1] : config.modelName();
            }

            boolean urlChanged = !newServerUrl.equals(config.serverUrl());
            boolean modelChanged = !newModelName.equals(config.modelName());

            if (!urlChanged && !modelChanged) {
                outputCoordinator.print("[i] Sin cambios en la configuracion. Reconectando...\n");
            } else {
                if (urlChanged) {
                    outputCoordinator.print(String.format("[NET] Servidor: %s -> %s\n",
                        config.serverUrl(), newServerUrl));
                }
                if (modelChanged) {
                    outputCoordinator.print(String.format("[MDL] Modelo: %s -> %s\n",
                        config.modelName(), newModelName));
                }
            }

            CliConfig newConfig = config.withServerUrl(newServerUrl).withModelName(newModelName);

            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {}
            }

            var newClient = new VllmClient(
                newConfig.serverUrl(),
                newConfig.apiKey(),
                newConfig.modelName(),
                newConfig,
                tokenizer
            );

            var status = newClient.checkServerStatus();
            if (!status.isAlive()) {
                outputCoordinator.printError("[ERROR] No se pudo conectar: " + status.errorMessage());
                outputCoordinator.print("[*] Manteniendo configuracion anterior.\n");
                return;
            }

            this.client = newClient;
            this.config = newConfig;

            outputCoordinator.print(String.format("""

                [OK] Reconexion exitosa!

                [CFG] Nueva configuracion:
                - Servidor: %s
                - Modelo: %s
                • Version: %s
                • Contexto max: %,d tokens

                """,
                newConfig.serverUrl(),
                status.modelName() != null ? status.modelName() : newConfig.modelName(),
                status.version() != null ? status.version() : "N/A",
                status.maxContextLength() > 0 ? status.maxContextLength() : newConfig.contextWindow()
            ), OutputCoordinator.MessageType.SUCCESS);
        } catch (Exception e) {
            outputCoordinator.printError("[ERROR] Error al reconectar: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
        }
    }

    private void handleConfigCommand(String args) {
        if (args.isEmpty()) {
            outputCoordinator.print("""

                Uso: /config <subcomando>

                Subcomandos:
                  show           - Mostrar configuracion actual
                  list           - Listar claves disponibles
                  set <k> <v>    - Establecer valor
                  get <key>      - Obtener valor
                  unset <key>    - Eliminar configuracion

                Ejemplo: /config show
                """);
            return;
        }

        var parts = args.split("\\s+", 3);
        var subCmd = parts[0].toLowerCase();

        switch (subCmd) {
            case "show" -> showConfig();
            case "list" -> listConfigKeys();
            case "set" -> {
                if (parts.length < 3) {
                    outputCoordinator.printError("Uso: /config set <clave> <valor>");
                } else {
                    setConfigValue(parts[1], parts[2]);
                }
            }
            case "get" -> {
                if (parts.length < 2) {
                    outputCoordinator.printError("Uso: /config get <clave>");
                } else {
                    getConfigValue(parts[1]);
                }
            }
            case "unset" -> {
                if (parts.length < 2) {
                    outputCoordinator.printError("Uso: /config unset <clave>");
                } else {
                    unsetConfigValue(parts[1]);
                }
            }
            default -> outputCoordinator.printError("Subcomando desconocido: " + subCmd);
        }
    }

    private void showConfig() {
        var resolver = new ConfigPriorityResolver();
        outputCoordinator.print("\nConfiguracion actual:\n");

        for (var entry : ConfigCommand.AVAILABLE_KEYS.entrySet()) {
            var key = entry.getKey();
            var info = entry.getValue();
            var resolved = resolver.resolveWithSource(key, null);

            String value = resolved.isPresent()
                ? (info.secure() ? ConfigCommand.maskSecret(resolved.value()) : resolved.value())
                : "(no configurado)";

            outputCoordinator.print(String.format("  %-18s = %s", key, value));
        }
    }

    private void listConfigKeys() {
        outputCoordinator.print("\nClaves de configuracion disponibles:\n");
        for (var entry : ConfigCommand.AVAILABLE_KEYS.entrySet()) {
            outputCoordinator.print(String.format("  %-18s - %s",
                entry.getKey(), entry.getValue().description()));
        }
    }

    private void setConfigValue(String key, String value) {
        var keyInfo = ConfigCommand.AVAILABLE_KEYS.get(key);
        if (keyInfo == null) {
            outputCoordinator.printError("Clave desconocida: " + key);
            return;
        }

        var configService = SecureConfigService.getInstance();
        if (keyInfo.secure()) {
            configService.setSecureProperty(keyInfo.internalKey(), value);
        } else {
            configService.setProperty(keyInfo.internalKey(), value);
        }

        String displayValue = keyInfo.secure() ? ConfigCommand.maskSecret(value) : value;
        outputCoordinator.print("Guardado: " + key + " = " + displayValue,
            OutputCoordinator.MessageType.SUCCESS);
    }

    private void getConfigValue(String key) {
        var keyInfo = ConfigCommand.AVAILABLE_KEYS.get(key);
        if (keyInfo == null) {
            outputCoordinator.printError("Clave desconocida: " + key);
            return;
        }

        var resolver = new ConfigPriorityResolver();
        var resolved = resolver.resolveWithSource(key, null);

        if (!resolved.isPresent()) {
            outputCoordinator.print(key + " = (no configurado)", OutputCoordinator.MessageType.WARNING);
        } else {
            String displayValue = keyInfo.secure()
                ? ConfigCommand.maskSecret(resolved.value())
                : resolved.value();
            outputCoordinator.print(String.format("%s = %s [%s]",
                key, displayValue, resolved.getSourceDescription()));
        }
    }

    private void unsetConfigValue(String key) {
        var keyInfo = ConfigCommand.AVAILABLE_KEYS.get(key);
        if (keyInfo == null) {
            outputCoordinator.printError("Clave desconocida: " + key);
            return;
        }

        var configService = SecureConfigService.getInstance();
        configService.removeProperty(keyInfo.internalKey());
        outputCoordinator.print("Eliminado: " + key, OutputCoordinator.MessageType.SUCCESS);
    }

    private void handleRouterCommand(String args) {
        if (args.isEmpty()) {
            printRouterHelp();
            return;
        }

        var parts = args.split("\\s+", 2);
        var subCmd = parts[0].toLowerCase();

        switch (subCmd) {
            case "status" -> showRouterStatus();
            case "stats" -> showRouterStats();
            case "enable", "on" -> enableRouter();
            case "disable", "off" -> disableRouter();
            case "test" -> {
                if (parts.length > 1) {
                    testRouter(parts[1]);
                } else {
                    outputCoordinator.printError("Uso: /router test <input>");
                }
            }
            default -> printRouterHelp();
        }
    }

    private void printRouterHelp() {
        outputCoordinator.print("""

            [RTR] Router Semantico - Comandos disponibles:

            /router status     - Muestra estado del router
            /router stats      - Muestra estadisticas de uso
            /router enable     - Habilita el router
            /router disable    - Deshabilita el router
            /router test <txt> - Prueba el router con un texto

            El router permite escribir comandos en lenguaje natural:
            - "sube los cambios" -> git push
            - "carga el archivo pom.xml" -> /load pom.xml
            - "muestra la configuracion" -> /config show

            """);
    }

    private void showRouterStatus() {
        if (!routerEnabled) {
            outputCoordinator.print("[RTR] Router: DESHABILITADO", OutputCoordinator.MessageType.WARNING);
            outputCoordinator.print("   Usa /router enable para activar");
            return;
        }

        var router = ServiceRegistry.getRouterService();
        if (router == null) {
            outputCoordinator.print("[RTR] Router: NO DISPONIBLE", OutputCoordinator.MessageType.WARNING);
            return;
        }

        outputCoordinator.print(String.format("""

            [RTR] Router Semantico:
            - Nombre: %s
            - Tipo: %s
            - Disponible: %s
            """,
            router.getName(),
            router.isLlmBased() ? "LLM (Qwen 2.5 Coder)" : "Regex (Fallback)",
            router.isAvailable() ? "SÍ" : "NO"
        ));
    }

    private void showRouterStats() {
        var router = ServiceRegistry.getRouterService();
        if (router == null) {
            outputCoordinator.print("Router no disponible", OutputCoordinator.MessageType.WARNING);
            return;
        }

        var stats = router.getStats();
        if (stats == null || stats.totalRequests() == 0) {
            outputCoordinator.print("No hay estadísticas disponibles aún", OutputCoordinator.MessageType.NORMAL);
            return;
        }

        outputCoordinator.print(String.format("""

            [STATS] Estadisticas del Router:
            - Total requests: %d
            - Rutas exitosas (LLM): %d (%.1f%%)
            - Rutas fallback (regex): %d (%.1f%%)
            - Latencia promedio: %.1f ms
            """,
            stats.totalRequests(),
            stats.successfulRoutes(),
            stats.getSuccessRate() * 100,
            stats.fallbackRoutes(),
            stats.getFallbackRate() * 100,
            stats.averageLatencyMs()
        ));
    }

    private void enableRouter() {
        ServiceRegistry.enableRouter();
        routerEnabled = true;
        var router = ServiceRegistry.getRouterService();
        outputCoordinator.print("[OK] Router habilitado: " +
            (router != null ? router.getName() : "BasicRouter"),
            OutputCoordinator.MessageType.SUCCESS);
    }

    private void disableRouter() {
        ServiceRegistry.disableRouter();
        routerEnabled = false;
        outputCoordinator.print("Router deshabilitado. Los comandos se tratarán como preguntas.",
            OutputCoordinator.MessageType.SUCCESS);
    }

    private void testRouter(String input) {
        var router = ServiceRegistry.getRouterService();
        if (router == null) {
            outputCoordinator.printError("Router no disponible");
            return;
        }

        var result = router.route(input);
        outputCoordinator.print(String.format("""

            [TEST] Test de Router:
            - Input: "%s"
            - Tool: %s
            - Action: %s
            - Params: %s
            - Confidence: %.1f%%
            - Fallback: %s
            - Latencia: %d ms
            """,
            input,
            result.tool().getId(),
            result.action(),
            result.parameters(),
            result.confidence() * 100,
            result.fromFallback() ? "SÍ" : "NO",
            result.latencyMs()
        ));
    }

    private void printUsageStats(GenerationResponse.Usage usage) {
        if (usage != null) {
            outputCoordinator.printDebug(String.format(
                "[TKN] Tokens: prompt=%d, completion=%d, total=%d",
                usage.promptTokens(), usage.completionTokens(), usage.totalTokens()));
        }
    }

    private String getPrompt() {
        String promptLine = ansi()
            .bold()
            .fgCyan()
            .a("❯")
            .reset()
            .a(" ")
            .toString();

        return "\n" + promptLine;
    }

    private void printWelcomeBanner() {
        String serverUrl = config.serverUrl();
        String modelName = config.modelName();
        int contextWindow = config.contextWindow();
        boolean streaming = config.streaming();
        String tokenizerMode = config.tokenizerMode().toString();

        String serverSource = System.getenv("LLM_SERVER_URL") != null ? "(env)"
                : !serverUrl.equals(AppDefaults.DEFAULT_SERVER_URL) ? "(config)" : "(default)";
        String modelSource = System.getenv("LLM_MODEL_NAME") != null ? "(env)"
                : !modelName.equals(AppDefaults.DEFAULT_MODEL_NAME) ? "(config)" : "(default)";

        String displayUrl = serverUrl.length() > 45
            ? serverUrl.substring(0, 42) + "..."
            : serverUrl;

        String displayModel = modelName.length() > 30
            ? modelName.substring(0, 27) + "..."
            : modelName;

        System.out.print(ansi().fgCyan().a("""

            ╔═══════════════════════════════════════════════════════════════════════╗
            ║                                                                       ║
            ║  ███████╗ █████╗ ██████╗  █████╗ ██████╗  ██████╗ ███╗   ██╗██╗       ║
            ║  ██╔════╝██╔══██╗██╔══██╗██╔══██╗██╔══██╗██╔═══██╗████╗  ██║██║       ║
            ║  █████╗  ███████║██████╔╝███████║██████╔╝██║   ██║██╔██╗ ██║██║       ║
            ║  ██╔══╝  ██╔══██║██╔══██╗██╔══██║██╔══██╗██║   ██║██║╚██╗██║██║       ║
            ║  ██║     ██║  ██║██║  ██║██║  ██║██║  ██║╚██████╔╝██║ ╚████║██║       ║
            ║  ╚═╝     ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═══╝╚═╝       ║
            ║                                                                       ║
            ║                   THE EXTRA COMPASS EDITION                           ║
            ║     Inspired by the legacy of: Crispin Fararoni Alfonso (C.F. I)      ║
            ║                                                                       ║
            ║                        FARARONI Core v1.0.0                           ║
            ║                         Open Source LLM CLI                           ║
            ╠═══════════════════════════════════════════════════════════════════════╣
            ║  CONFIGURACION ACTIVA:                                                ║
            """).reset());

        String servidorLine = String.format("[>] Servidor: %-42s %s", displayUrl, serverSource);
        String modeloLine = String.format("[>] Modelo:   %-42s %s", displayModel, modelSource);
        String contextoLine = String.format("[>] Contexto: %,d tokens | Streaming: %s | Tokenizer: %s",
            contextWindow, streaming ? "+" : "-", tokenizerMode);

        System.out.println(ansi()
            .fgCyan().a("║  ")
            .fgYellow().a(String.format("%-69s", servidorLine))
            .fgCyan().a("║").reset());
        System.out.println(ansi()
            .fgCyan().a("║  ")
            .fgYellow().a(String.format("%-69s", modeloLine))
            .fgCyan().a("║").reset());
        System.out.println(ansi()
            .fgCyan().a("║  ")
            .fgYellow().a(String.format("%-69s", contextoLine))
            .fgCyan().a("║").reset());

        System.out.println(ansi().fgCyan().a("""
            ╠═══════════════════════════════════════════════════════════════════════╣
            ║  [i] Variables de entorno: LLM_SERVER_URL, LLM_MODEL_NAME, LLM_API_KEY║
            ║  [>] Configurar: fararoni config set <key> <value>                    ║
            ╚═══════════════════════════════════════════════════════════════════════╝
            """).reset());
    }

    private void printQuickHelp() {
        var routerHint = routerEnabled
            ? "• Comandos naturales: \"sube cambios\", \"carga pom.xml\"\n            "
            : "";

        System.out.println(ansi().fgYellow().a("""
            [*] Inicio rapido:
            - Escribe tu pregunta directamente
            %s- /load <archivos> - Carga archivos para contexto
            - /help - Muestra todos los comandos
            - /exit - Sale de FARARONI

            """.formatted(routerHint)).reset());
    }

    private void printLegacyStory() {
        final String GOLD = "\u001B[38;5;214m";
        final String DIM = "\u001B[2m";
        final String ITALIC = "\u001B[3m";
        final String RESET = "\u001B[0m";
        final String CYAN = "\u001B[36m";

        try {
            typewrite("\n  Initializing Legacy Protocol... ", 40);
            System.out.println(GOLD + "[ ACCESS GRANTED ]" + RESET);
            Thread.sleep(600);

            System.out.println();
            System.out.println(CYAN + "  ╔════════════════════════════════════════════════════════════════════╗" + RESET);
            System.out.println(CYAN + "  ║" + GOLD + "                    THE FOUNDER'S COMPASS                           " + CYAN + "║" + RESET);
            System.out.println(CYAN + "  ╚════════════════════════════════════════════════════════════════════╝" + RESET);
            System.out.println();
            Thread.sleep(400);

            String[] storyLines = {
                "\"Los padres suelen ser importantes.",
                " A veces uno piensa que lo que es,",
                " se le debe la mitad al padre y la otra mitad a la madre.",
                "",
                " Pero si tienes a estos dos, y aparte tienes a tus abuelos...",
                " entonces tienes algo más.",
                "",
                " Este núcleo está dedicado a mi abuelo,",
                " Crispin Fararoni Alfonso, hijo de Crispin Fararoni.",
                "",
                " Con sus historias y sabiduría,",
                " él me dio una brújula extra para navegar con propósito en la vida.\""
            };

            for (String line : storyLines) {
                System.out.print("  ");
                typewrite(ITALIC + line + RESET, line.isEmpty() ? 0 : 25);
                System.out.println();
                if (!line.isEmpty()) {
                    Thread.sleep(100);
                }
            }

            System.out.println();
            Thread.sleep(300);
            System.out.println(DIM + "  ──────────────────────────────────────────────────────────────────────" + RESET);
            System.out.print("  Signed architecture by: ");
            typewrite(GOLD + "C. Fararoni II" + RESET, 60);
            System.out.println();
            System.out.println(DIM + "  ──────────────────────────────────────────────────────────────────────" + RESET);
            System.out.println();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("\n  [Legacy Protocol interrupted]");
        }
    }

    private void typewrite(String text, int delayMs) throws InterruptedException {
        for (char c : text.toCharArray()) {
            System.out.print(c);
            System.out.flush();
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
        }
    }

    private void printHelp() {
        StringBuilder help = new StringBuilder();
        help.append("""

            📚 Comandos disponibles:

            BÁSICOS (Legacy):
              /help, /h          - Muestra esta ayuda
              /exit, /quit, /q   - Sale del programa
              /clear, /cls       - Limpia historial y contexto
              /load <paths>      - Carga archivos/directorios para contexto
              /unload            - Descarga el contexto actual
              /list [dir]        - Lista archivos en directorio
              /history           - Muestra historial de conversación
              /status            - Muestra estado del sistema
              /index             - Muestra estado del índice de memoria persistente
              /reconnect [url] [modelo] - Reconecta al servidor LLM
              /tokens            - Activa/desactiva mostrar tokens
              /debug             - Activa/desactiva modo debug

            CONFIGURACIÓN (Legacy):
              /config show       - Muestra configuración actual
              /config list       - Lista claves disponibles
              /config set <k> <v> - Establece un valor
              /config get <key>  - Obtiene un valor

            ROUTER SEMÁNTICO (Legacy):
              /router status     - Muestra estado del router
              /router stats      - Muestra estadísticas de uso
              /router enable     - Habilita el router
              /router disable    - Deshabilita el router
              /router test <txt> - Prueba el router con un texto

            AGENTE (Legacy):
              /context show      - Muestra contexto del proyecto
              /context refresh   - Refresca el contexto
              /context tree      - Muestra estructura en árbol
              /context info      - Muestra info del proyecto
              /git status        - Muestra estado del agente git
              /git enable        - Habilita auto-commit
              /git disable       - Deshabilita auto-commit
              /git log           - Muestra últimos commits

            HERITAGE:
              /legacy            - 🧭 The Founder's Compass

            """);

        CommandRegistry registry = CommandRegistry.getInstance();
        Map<CommandCategory, List<ConsoleCommand>> byCategory = registry.getCommandsByCategory();

        if (!byCategory.isEmpty()) {
            help.append("────────────────────────────────────────────\n");
            help.append("            COMANDOS DINÁMICOS (SPI)\n");
            help.append("────────────────────────────────────────────\n\n");

            for (Map.Entry<CommandCategory, List<ConsoleCommand>> entry : byCategory.entrySet()) {
                CommandCategory category = entry.getKey();
                List<ConsoleCommand> commands = entry.getValue();

                help.append("            ").append(category.getDisplayName().toUpperCase()).append(":\n");

                for (ConsoleCommand cmd : commands) {
                    String trigger = cmd.getTrigger();
                    String desc = cmd.getDescription();
                    String[] aliases = cmd.getAliases();
                    String enterprise = cmd.requiresEnterprise() ? " [E]" : "";

                    String aliasStr = aliases.length > 0
                        ? ", " + String.join(", ", aliases)
                        : "";
                    help.append(String.format("              %-18s - %s%s%n",
                        trigger + aliasStr, desc, enterprise));
                }
                help.append("\n");
            }
        }

        help.append("""
            EJEMPLOS:
              > ¿Cómo funciona el patrón Builder?
              > /load src/main/java/MyClass.java
              > /model - Ver configuración del modelo local
              > /web https:

            [E] = Requiere Enterprise
            """);

        System.out.println(ansi().fgGreen().a(help.toString()).reset());
    }

    private void performStartupNativeEngineCheck() {
        boolean nativeAvailable = NativeLoader.isNativeLibraryAvailable();
        boolean remoteConfigured = isRemoteModelConfigured();
        LocalLlmConfig localConfig = LocalLlmConfig.fromEnvironment();
        boolean localModelAvailable = localConfig.isModelDownloaded();

        if (nativeAvailable) {
            if (!localModelAvailable) {
                offerLocalModelDownload(localConfig, remoteConfigured);
            } else {
                if (debugMode) {
                    outputCoordinator.printDebug("[Startup] Motor + Modelo local disponibles");
                }
                performLocalModelWarmup();
                printStartupModeInfo(true, remoteConfigured);
            }
            return;
        }

        System.out.println(ansi().fgYellow().a("""

            [WARN] Motor Nativo no detectado

            Para habilitar el modelo local (1.5b) como orquestador:
            - Se descargará una librería nativa (~5 MB)
            - Se descargará el modelo pequeño (~1 GB)
            - Esto permite ejecutar tareas simples localmente (sin latencia)

            """).reset());

        if (remoteConfigured) {
            System.out.println(ansi().fgCyan().a(
                "   ✓ Modelo remoto detectado: " + config.modelName() + "\n"
            ).reset());
        }

        try {
            String response = lineReader.readLine(
                ansi().fgCyan().a("¿Deseas instalar el motor local ahora? [S/n] ").reset().toString()
            );

            boolean userAccepted = response != null && (response.isBlank() ||
                response.trim().equalsIgnoreCase("s") ||
                response.trim().equalsIgnoreCase("si") ||
                response.trim().equalsIgnoreCase("y") ||
                response.trim().equalsIgnoreCase("yes"));

            if (userAccepted) {
                boolean motorOk = performNativeEngineDownload();
                if (motorOk) {
                    offerLocalModelDownload(localConfig, remoteConfigured);
                }
            } else {
                if (remoteConfigured) {
                    System.out.println(ansi().fgCyan().a("""

                        [i] Iniciando en modo 'Solo Remoto'
                        - El modelo grande (%s) manejara todas las tareas
                        - Footer mostrara "Local (No disponible)" en rojo
                        - Puedes instalar el motor despues con: /install-engine

                        """.formatted(config.modelName())).reset());
                } else {
                    System.out.println(ansi().fgRed().a("""

                        [WARN] Sin modelos disponibles
                        - No hay motor local instalado
                        - No hay modelo remoto configurado
                        - Por favor configura un servidor remoto o instala el motor local

                        """).reset());
                }
            }
        } catch (Exception e) {
            if (debugMode) {
                outputCoordinator.printDebug("[Startup] Error: " + e.getMessage());
            }
        }
    }

    private boolean isRemoteModelConfigured() {
        try {
            String serverUrl = config.serverUrl();
            return serverUrl != null && !serverUrl.isBlank() && !serverUrl.equals("none");
        } catch (Exception e) {
            return false;
        }
    }

    private void offerLocalModelDownload(LocalLlmConfig localConfig, boolean remoteConfigured) {
        if (localConfig.isModelDownloaded()) {
            printStartupModeInfo(true, remoteConfigured);
            return;
        }

        System.out.println(ansi().fgYellow().a("""

            [WARN] Modelo local (1.5b) no encontrado

            Para que el orquestador local funcione, se necesita descargar el modelo:
            - Tamaño: ~1 GB
            - Ubicación: ~/.llm-fararoni/models/

            """).reset());

        try {
            String response = lineReader.readLine(
                ansi().fgCyan().a("¿Deseas descargar el modelo ahora? [S/n] ").reset().toString()
            );

            boolean userAccepted = response != null && (response.isBlank() ||
                response.trim().equalsIgnoreCase("s") ||
                response.trim().equalsIgnoreCase("si") ||
                response.trim().equalsIgnoreCase("y") ||
                response.trim().equalsIgnoreCase("yes"));

            if (userAccepted) {
                performLocalModelDownload();
            } else {
                if (remoteConfigured) {
                    System.out.println(ansi().fgCyan().a("""

                        [i] Usando solo modelo remoto por ahora
                        - El modelo grande manejara todas las tareas
                        - Puedes descargar el modelo local despues con: /download-model

                        """).reset());
                }
            }
        } catch (Exception e) {
            if (debugMode) {
                outputCoordinator.printDebug("[Startup] Error: " + e.getMessage());
            }
        }

        printStartupModeInfo(localConfig.isModelDownloaded(), remoteConfigured);
    }

    private void performLocalModelDownload() {
        System.out.println(ansi().fgCyan().a("\n[DOWNLOAD] Descargando modelo local (qwen:1.5b)...\n").reset());

        try {
            ModelDownloader downloader = new ModelDownloader();
            boolean success = downloader.download(progress -> {
                if (progress.state() == DownloadState.DOWNLOADING) {
                    System.out.print(ansi().eraseLine().cursorToColumn(0)
                        .a(String.format("   Descargando: %.1f%% (%s / %s)",
                            progress.percentage(),
                            DownloadProgress.formatSize(progress.downloadedBytes()),
                            DownloadProgress.formatSize(progress.totalBytes())))
                        .toString());
                } else if (progress.state() == DownloadState.COMPLETED) {
                    System.out.println(ansi().eraseLine().cursorToColumn(0)
                        .fgGreen().a("   [OK] Modelo descargado").reset().toString());
                }
            });

            if (success) {
                System.out.println(ansi().fgGreen().a("\n[OK] Modelo local instalado\n").reset());
                ServiceRegistry.reloadLocalLlm();
                performLocalModelWarmup();
            }
        } catch (Exception e) {
            System.out.println(ansi().fgRed().a("\n[ERROR] " + e.getMessage()).reset());
        }
    }

    private boolean performNativeEngineDownload() {
        System.out.println(ansi().fgCyan().a("\n[DOWNLOAD] Descargando motor nativo (~5 MB)...\n").reset());

        NativeEngineDownloader downloader = new NativeEngineDownloader();

        try {
            boolean success = downloader.download(progress -> {
                if (progress.state() == DownloadState.DOWNLOADING) {
                    System.out.print(ansi().eraseLine().cursorToColumn(0)
                        .a(String.format("   Descargando: %.1f%% (%s / %s)",
                            progress.percentage(),
                            DownloadProgress.formatSize(progress.downloadedBytes()),
                            DownloadProgress.formatSize(progress.totalBytes())))
                        .toString());
                } else if (progress.state() == DownloadState.COMPLETED) {
                    System.out.println(ansi().eraseLine().cursorToColumn(0)
                        .fgGreen().a("   [OK] Motor descargado").reset().toString());
                }
            });

            if (success) {
                System.out.println(ansi().fgGreen().a("\n[OK] Motor nativo instalado\n").reset());
                return true;
            } else {
                System.out.println(ansi().fgRed().a("\n[ERROR] Error instalando motor\n").reset());
                return false;
            }
        } catch (Exception e) {
            System.out.println(ansi().fgRed().a("\n[ERROR] " + e.getMessage()).reset());
            return false;
        }
    }

    private void printStartupModeInfo(boolean localAvailable, boolean remoteAvailable) {
        if (localAvailable && remoteAvailable) {
            System.out.println(ansi().fgGreen().a("""
                [OK] Modo Hibrido activo
                   - Modelo pequeño (1.5b): Orquestador (tareas simples)
                   - Modelo grande (%s): Tareas complejas
                """.formatted(config.modelName())).reset());
        } else if (localAvailable) {
            System.out.println(ansi().fgCyan().a("""
                [OK] Modo Ligero activo
                   - Solo modelo local (1.5b)
                   - Para tareas complejas, configura un modelo remoto
                """).reset());
        } else if (remoteAvailable) {
            System.out.println(ansi().fgCyan().a("""
                [OK] Modo Remoto activo
                   - Solo modelo grande (%s)
                   - El grande manejará todas las tareas
                """.formatted(config.modelName())).reset());
        }
        System.out.println();
    }

    private void performLocalModelWarmup() {
        try {
            LocalLlmConfig localConfig = LocalLlmConfig.fromEnvironment();
            if (!NativeLoader.isNativeLibraryAvailable() || !localConfig.isModelDownloaded()) {
                if (debugMode) {
                    outputCoordinator.printDebug("[Warmup] Motor o modelo no disponible, saltando warmup");
                }
                return;
            }

            System.out.println(ansi().fgCyan().a("   [START] Cargando modelo local en memoria...").reset());
            long startTime = System.currentTimeMillis();

            LocalLlmService shared = ServiceRegistry.getSharedLocalLlm();
            if (shared == null) {
                if (debugMode) {
                    outputCoordinator.printDebug("[Warmup] No se pudo crear LocalLlmService");
                }
                return;
            }

            shared.warmup();

            this.localLlmService = shared;

            EnterpriseRouter oldRouter = this.enterpriseRouter;
            this.enterpriseRouter = new EnterpriseRouter(shared);
            if (oldRouter != null) {
                try {
                    oldRouter.close();
                } catch (Exception ignored) {}
            }
            if (debugMode) {
                outputCoordinator.printDebug("[Warmup] EnterpriseRouter recreado con LocalLlmService CALIENTE");
            }

            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println(ansi().fgGreen().a("   [OK] Modelo cargado en " + String.format("%.1f", elapsed / 1000.0) + "s - Listo para respuestas instantaneas\n").reset());
        } catch (Exception e) {
            System.out.println(ansi().fgYellow().a("   [WARN] Warmup fallido: " + e.getMessage() + " (se cargara en el primer uso)\n").reset());
            if (debugMode) {
                outputCoordinator.printDebug("[Warmup] Error: " + e.getMessage());
            }
        }
    }

    private void printGoodbye() {
        TerminalGuard.uninstall();

        if (statusFooter != null) {
            statusFooter.update(List.of());
        }
        System.out.println(ansi().fgCyan().a("""

            Gracias por usar FARARONI. Hasta pronto!

            """).reset());

        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (Exception e) {
        }

        if (fararoniCore != null) {
            fararoniCore.shutdown();
        }

        System.exit(0);
    }

    private void updateStatusFooter() {
        if (statusFooter == null || terminal == null) {
            return;
        }

        try {
            int width = terminal.getWidth();
            if (width <= 0) {
                width = 80;
            }

            AttributedString separator = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.faint())
                .append("─".repeat(Math.max(width, 10)))
                .toAttributedString();

            AttributedStringBuilder statusBuilder = new AttributedStringBuilder();

            String turtleName = (fararoniCore != null)
                ? fararoniCore.getTurtleModelName()
                : AppDefaults.DEFAULT_TURTLE_MODEL;

            String rabbitName = getLocalModelName();
            boolean hasLocalBackup = isLocalModelAvailable();

            boolean usingLocal = (fararoniCore != null)
                ? fararoniCore.isUsingLocalModel()
                : isCurrentlyUsingLocal;

            statusBuilder.style(AttributedStyle.BOLD.foreground(AttributedStyle.CYAN))
                .append(" ▶▶ ");

            boolean nativeEngineAvailable = NativeLoader.isNativeLibraryAvailable();

            statusBuilder.style(AttributedStyle.DEFAULT)
                .append(rabbitName != null ? rabbitName : AppDefaults.DEFAULT_RABBIT_MODEL)
                .append(" · ");

            if (usingLocal) {
                if (isProcessing) {
                    statusBuilder.style(AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW))
                        .append("Local -A");
                } else {
                    statusBuilder.style(AttributedStyle.BOLD.foreground(AttributedStyle.GREEN))
                        .append("Local -A");
                }
            } else if (nativeEngineAvailable && hasLocalBackup) {
                statusBuilder.style(AttributedStyle.DEFAULT.faint())
                    .append("Local -I");
            } else {
                statusBuilder.style(AttributedStyle.BOLD.foreground(AttributedStyle.RED))
                    .append("Local (N/A)");
            }

            boolean remoteConfigured = (fararoniCore != null && fararoniCore.isReady())
                ? fararoniCore.isRemoteModelAvailable()
                : isRemoteModelConfigured();

            statusBuilder.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT))
                .append(" | ")
                .style(AttributedStyle.DEFAULT)
                .append(turtleName)
                .append(" · ");

            if (!remoteConfigured) {
                statusBuilder.style(AttributedStyle.BOLD.foreground(AttributedStyle.RED))
                    .append("Remote (N/A)");
            } else if (!usingLocal) {
                if (isProcessing) {
                    statusBuilder.style(AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW))
                        .append("Remote -A");
                } else {
                    statusBuilder.style(AttributedStyle.BOLD.foreground(AttributedStyle.GREEN))
                        .append("Remote -A");
                }
            } else {
                statusBuilder.style(AttributedStyle.DEFAULT.faint())
                    .append("Remote -I");
            }

            String leftPart = statusBuilder.toAttributedString().toString();
            int leftLength = stripAnsi(leftPart).length();

            int contextPercent = calculateContextUsagePercent();
            String rightPart = String.format("Context: %d%% ", contextPercent);
            int rightLength = rightPart.length();

            int paddingLength = width - leftLength - rightLength;
            if (paddingLength < 1) {
                paddingLength = 1;
            }

            statusBuilder.style(AttributedStyle.DEFAULT)
                .append(" ".repeat(paddingLength))
                .style(AttributedStyle.DEFAULT.faint())
                .append(rightPart);

            AttributedString statusLine = statusBuilder.toAttributedString();

            var footerLines = List.of(separator, statusLine);
            statusFooter.update(footerLines);

            outputCoordinator.setCurrentStatusLines(footerLines);
        } catch (Exception e) {
            if (debugMode) {
                System.err.println("[DEBUG] Error actualizando status footer: " + e.getMessage());
            }
        }
    }

    private boolean isLocalModelAvailable() {
        try {
            if (!NativeLoader.isNativeLibraryAvailable()) {
                return false;
            }
            LocalLlmConfig localConfig = LocalLlmConfig.fromEnvironment();
            return localConfig.isModelDownloaded();
        } catch (Exception e) {
            return false;
        }
    }

    private String getLocalModelName() {
        if (fararoniCore != null) {
            return fararoniCore.getRabbitModelName();
        }

        try {
            if (!NativeLoader.isNativeLibraryAvailable()) {
                return null;
            }
            LocalLlmConfig localConfig = LocalLlmConfig.fromEnvironment();
            if (localConfig.isModelDownloaded()) {
                return AppDefaults.DEFAULT_RABBIT_MODEL;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private int calculateContextUsagePercent() {
        try {
            int usedTokens = 0;
            for (Message msg : conversationHistory) {
                usedTokens += tokenizer.estimateTokens(msg.content());
            }
            if (activeContext != null) {
                usedTokens += tokenizer.estimateTokens(activeContext);
            }

            int maxTokens = config.contextWindow();
            if (maxTokens <= 0) {
                maxTokens = 8192;
            }

            return Math.min(100, (int) ((usedTokens * 100.0) / maxTokens));
        } catch (Exception e) {
            return 0;
        }
    }

    private String stripAnsi(String str) {
        return str.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    private String truncateForDisplay(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }

    private void clearStatusFooter() {
        if (statusFooter != null) {
            statusFooter.update(List.of());
        }
    }

    private boolean detectActionIntent(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        String normalized = input.toLowerCase().trim();

        if (normalized.length() < 12) {
            boolean hasQuickAction = normalized.startsWith("crea") ||
                                     normalized.startsWith("lee") ||
                                     normalized.startsWith("borra") ||
                                     normalized.startsWith("ver") ||
                                     normalized.startsWith("abre") ||
                                     normalized.startsWith("compila") ||
                                     normalized.startsWith("build") ||
                                     normalized.startsWith("ejecuta") ||
                                     normalized.startsWith("run") ||
                                     normalized.startsWith("testea") ||
                                     normalized.startsWith("mvn") ||
                                     normalized.startsWith("npm") ||
                                     normalized.startsWith("gradle");
            if (!hasQuickAction) {
                return false;
            }
        }

        boolean hasTechnicalVerb =
            normalized.contains("compila") ||
            normalized.contains("compile") ||
            normalized.contains("build") ||
            normalized.contains("package") ||
            normalized.contains("empaqueta") ||
            normalized.contains("testea") ||
            normalized.contains("prueba") ||
            normalized.contains("instala") ||
            normalized.contains("install") ||
            normalized.contains("deploy") ||
            normalized.contains("despliega") ||
            normalized.contains("ejecuta") ||
            normalized.contains("run") ||
            normalized.contains("debug") ||
            normalized.contains("mvn") ||
            normalized.contains("gradle") ||
            normalized.contains("npm") ||
            normalized.contains("yarn") ||
            normalized.contains("make") ||
            normalized.contains("cargo");

        if (hasTechnicalVerb) {
            return true;
        }

        boolean hasActionVerb =
            normalized.contains("crea") ||
            normalized.contains("genera") ||
            normalized.contains("escribe") ||
            normalized.contains("redacta") ||
            normalized.contains("guarda") ||
            normalized.contains("borra") ||
            normalized.contains("elimina") ||
            normalized.contains("modifica") ||
            normalized.contains("edita") ||
            normalized.contains("refactoriza") ||
            normalized.contains("renombra") ||
            normalized.contains("mueve") ||
            normalized.contains("copia") ||
            normalized.contains("restaura") ||
            normalized.contains("implementa") ||
            normalized.contains("agrega") ||
            normalized.contains("añade") ||
            normalized.contains("corrige") ||
            normalized.contains("repara") ||
            normalized.startsWith("haz ") ||
            normalized.startsWith("hazme ") ||
            normalized.startsWith("dame ") ||
            normalized.startsWith("realiza ") ||

            normalized.contains("lee") ||
            normalized.contains("busca") ||
            normalized.contains("investiga") ||
            normalized.contains("analisa") ||
            normalized.contains("analiza") ||
            normalized.contains("valida") ||
            normalized.contains("ver") ||
            normalized.contains("muestra") ||
            normalized.contains("revisa") ||
            normalized.contains("corrobora") ||
            normalized.contains("chequea") ||
            normalized.contains("lista") ||
            normalized.contains("dime que") ||

            normalized.contains("explica") ||
            normalized.contains("describe") ||
            normalized.contains("resume") ||
            normalized.contains("documenta") ||
            normalized.contains("comenta") ||
            normalized.contains("audita") ||
            normalized.contains("diagnostica") ||
            normalized.contains("evalua") ||
            normalized.contains("interpreta") ||
            normalized.contains("compara") ||
            normalized.contains("sugiere") ||
            normalized.contains("recomienda") ||
            normalized.contains("optimiza") ||

            normalized.contains("comunica") ||
            normalized.contains("informa") ||
            normalized.contains("informame");

        boolean hasTechnicalObject =
            normalized.contains(".java") ||
            normalized.contains(".xml") ||
            normalized.contains(".json") ||
            normalized.contains(".yaml") ||
            normalized.contains(".yml") ||
            normalized.contains(".md") ||
            normalized.contains(".txt") ||
            normalized.contains(".properties") ||
            normalized.contains("src/") ||
            normalized.contains("pom.xml") ||
            normalized.contains("archivo") ||
            normalized.contains("clase") ||
            normalized.contains("directorio") ||
            normalized.contains("carpeta") ||
            normalized.contains("paquete") ||
            normalized.contains("código") ||
            normalized.contains("codigo") ||
            normalized.contains("proyecto");

        boolean hasInstruction = normalized.contains("no supongas") ||
                                 normalized.contains("no inventes") ||
                                 normalized.contains("usa el archivo");

        return hasActionVerb || hasTechnicalObject || hasInstruction;
    }

    private boolean handleUserSelection(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        String normalized = input.trim().toLowerCase();

        if (normalized.equals("cancelar") || normalized.equals("cancel") ||
            normalized.equals("c") || normalized.equals("salir")) {
            outputCoordinator.print("Seleccion cancelada.", OutputCoordinator.MessageType.WARNING);
            resetFsmState();
            return true;
        }

        if (!input.trim().matches("\\d+")) {
            return false;
        }

        int selection;
        try {
            selection = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return false;
        }

        if (selection < 1 || selection > pendingOptions.size()) {
            return false;
        }

        String selectedPath = pendingOptions.get(selection - 1);
        outputCoordinator.print("Seleccionado: " + selectedPath, OutputCoordinator.MessageType.SUCCESS);

        if (pendingOriginalCommand != null && fararoniCore != null) {
            String correctedCommand = pendingOriginalCommand + " [RUTA SELECCIONADA: " + selectedPath + "]";

            outputCoordinator.print("Re-ejecutando con ruta seleccionada...", OutputCoordinator.MessageType.NORMAL);

            try {
                fararoniCore.chatAgentic(correctedCommand);
            } catch (Exception e) {
                outputCoordinator.printError("Error re-ejecutando: " + e.getMessage());
            }
        }

        resetFsmState();
        return true;
    }

    public void requestUserSelection(java.util.List<String> options, String originalCommand) {
        if (options == null || options.isEmpty()) {
            outputCoordinator.printError("Error interno: lista de opciones vacía.");
            return;
        }

        this.pendingOptions = new java.util.ArrayList<>(options);
        this.pendingOriginalCommand = originalCommand;
        this.currentState = SystemState.AWAITING_CHOICE;

        outputCoordinator.print("\nAmbiguedad detectada. Por favor selecciona:", OutputCoordinator.MessageType.WARNING);
        outputCoordinator.print("");

        for (int i = 0; i < options.size(); i++) {
            outputCoordinator.print("  " + (i + 1) + ". " + options.get(i));
        }

        outputCoordinator.print("");
        outputCoordinator.print("Escribe el número (1-" + options.size() + ") o 'cancelar' para abortar.");
    }

    private void resetFsmState() {
        this.currentState = SystemState.FREE_TALK;
        this.pendingOptions.clear();
        this.pendingOriginalCommand = null;
    }

    private java.util.List<String> extractAmbiguousPaths(String response) {
        java.util.List<String> paths = new java.util.ArrayList<>();

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            AppDefaults.AMBIGUITY_PATH_REGEX,
            java.util.regex.Pattern.MULTILINE
        );

        java.util.regex.Matcher matcher = pattern.matcher(response);
        while (matcher.find()) {
            String path = matcher.group(2);
            path = path.replaceAll("[\\n\\r]", "").trim();
            if (!path.isEmpty()) {
                paths.add(path);
            }
        }

        return paths;
    }
}
