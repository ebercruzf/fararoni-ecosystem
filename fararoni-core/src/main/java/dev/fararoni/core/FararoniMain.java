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

import dev.fararoni.core.server.SessionManager;
import dev.fararoni.core.cli.ConsoleUtils;
import dev.fararoni.core.cli.FirstRunExperience;
import dev.fararoni.core.cli.InteractiveShell;
import dev.fararoni.core.config.ConfigPriorityResolver;
import dev.fararoni.core.core.bootstrap.EngineBootstrap;
import dev.fararoni.core.core.bootstrap.LlmProviderDiscovery;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.kernel.HyperNativeKernel;
import dev.fararoni.core.core.swarm.HiveMind;
import dev.fararoni.core.core.security.SecureConfigService;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;
import dev.fararoni.core.core.utils.NativeSilencer;
import dev.fararoni.core.server.FararoniServer;
import dev.fararoni.core.ui.DynamicConsoleHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.fusesource.jansi.Ansi.ansi;

import org.fusesource.jansi.AnsiConsole;

/**
 * @author Eber Cruz
 * @version 3.0.0
 * @since 1.0.0
 */
public class FararoniMain {
    private static final String PROMPT = ">>> ";
    private static final String ASSISTANT_PREFIX = "Fararoni: ";

    private static final String CMD_EXIT = "/exit";
    private static final String CMD_QUIT = "/quit";
    private static final String CMD_HELP = "/help";
    private static final String CMD_STATUS = "/status";
    private static final String CMD_CLEAR = "/clear";
    private static final String CMD_MISSION = "/mission";
    private static final String CMD_GIT = "/git";
    private static final String CMD_CONTEXT = "/context";
    private static final String CMD_RECONFIG = "/reconfig";

    private static FararoniCore core;
    private static boolean running = true;
    private static boolean useSwarm = false;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("[UNCAUGHT] Thread: " + t.getName() + " Error: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.err);
            System.err.flush();
        });

        configureLogging();

        String workingDir = System.getProperty("user.dir");
        String command = null;
        boolean serverMode = false;
        int serverPort = AppDefaults.DEFAULT_SERVER_PORT;
        boolean versionMode = false;
        boolean helpMode = false;
        boolean debugMode = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cwd" -> {
                    if (i + 1 < args.length) workingDir = args[++i];
                }
                case "--server" -> serverMode = true;
                case "--port" -> {
                    if (i + 1 < args.length) {
                        try {
                            serverPort = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Error: --port requiere un numero valido. Usando default: " + serverPort);
                        }
                    }
                }
                case "--version" -> versionMode = true;
                case "--help", "-h" -> helpMode = true;
                case "--debug" -> debugMode = true;
                case "legacy", "/legacy", "-legacy", "--legacy" -> {
                    printLegacyStory();
                    return;
                }
                default -> {
                    if (!args[i].startsWith("--")) {
                        command = args[i];
                    }
                }
            }
        }

        if (versionMode) {
            System.out.println(FararoniCore.getVersion());
            return;
        }

        if (helpMode) {
            printHelp();
            return;
        }

        System.out.println("[KERNEL] Iniciando Fararoni en: " + workingDir);
        Path workingPath = Paths.get(workingDir).toAbsolutePath();

        NativeSilencer.silencePermanently();

        try {
            core = new FararoniCore(workingPath);

            boolean isHeadless = (command != null);
            if (!isHeadless) {
                FirstRunExperience.checkAndRun();
            }

            EngineBootstrap bootstrap = new EngineBootstrap();
            boolean localReady = bootstrap.ensureEngineReady(isHeadless);

            if (!localReady && isHeadless) {
                System.err.println("[WARN] [BOOTSTRAP] Motor local no disponible.");
                System.err.println("   Continuando con modelo remoto si está configurado...");
            }

            if (serverMode) {
                runServerMode(serverPort, core);
            }
            else if (command != null) {
                runHeadlessMission(command, debugMode);
            }
            else {
                runInteractiveSession();
            }
        } catch (Exception e) {
            System.err.println("[CRASH] Excepcion no controlada: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(2);
        }
    }

    private static void runHeadlessMission(String prompt, boolean debug) {
        System.out.println("[HEADLESS] Ejecutando orden: " + prompt);

        try {
            System.out.println("[HEADLESS] Conectando al LLM...");
            core.initialize();

            HiveMind.MissionResult result = core.executeMission(prompt);

            if (result.isSuccess()) {
                System.out.println("[SUCCESS] Resultado:");
                System.out.println(result.result() != null ? result.result() : "Mision completada.");

                core.shutdown();
                System.exit(0);
            } else {
                System.err.println("[FAILURE] Error: " + result.errorMessage());

                core.shutdown();
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("[CRASH] Excepcion: " + e.getMessage());
            e.printStackTrace(System.err);

            if (core != null) {
                core.shutdown();
            }
            System.exit(2);
        }
    }

    private static void runServerMode(int port, FararoniCore fararoniCore) {
        try {
            System.out.println();
            System.out.println("╔═══════════════════════════════════════════════════════════════╗");
            System.out.println("║           FARARONI SERVER - Modo IDE Plugin                   ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("[SERVER] Inicializando componentes...");

            HyperNativeKernel kernel = fararoniCore.getKernel();
            System.out.println("[SERVER] ✓ HyperNativeKernel inicializado (via FararoniCore)");

            SessionManager sessionManager = new SessionManager(kernel);
            System.out.println("[SERVER] ✓ SessionManager configurado (Multi-Tenant)");

            FararoniServer server = new FararoniServer(sessionManager, fararoniCore);

            server.setAuthRequired(false);
            System.out.println("[SERVER] ✓ Autenticacion: DESACTIVADA (desarrollo)");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println();
                System.out.println("[SERVER] Recibida señal de apagado...");
                server.stop();
                System.out.println("[SERVER] Servidor detenido correctamente.");
            }));

            System.out.println("[DEBUG] Antes de server.start()...");
            System.out.flush();
            server.start(port);
            System.out.println("[DEBUG] Despues de server.start() - servidor arrancado");
            System.out.flush();

            System.out.println();
            System.out.println("╔═══════════════════════════════════════════════════════════════╗");
            System.out.println("║  [ACTIVE] SERVIDOR ACTIVO                                     ║");
            System.out.println("╠═══════════════════════════════════════════════════════════════╣");
            System.out.println("║  URL Base:    http://localhost:" + port + "                           ║");
            System.out.println("║                                                               ║");
            System.out.println("║  Endpoints REST:                                              ║");
            System.out.println("║    POST   /api/chat           - Chat sincronico (IDE)         ║");
            System.out.println("║    POST   /api/task           - Ejecutar mision (async)       ║");
            System.out.println("║    GET    /api/status         - Estado del servidor           ║");
            System.out.println("║    GET    /api/session/{id}   - Estado de sesion              ║");
            System.out.println("║    DELETE /api/session/{id}   - Cerrar sesion                 ║");
            System.out.println("║    GET    /health             - Health check                  ║");
            System.out.println("║                                                               ║");
            System.out.println("║  WebSocket:                                                   ║");
            System.out.println("║    WS     /ws/events?userId=X - Live feed de eventos          ║");
            System.out.println("║                                                               ║");
            System.out.println("║  Presiona CTRL+C para detener el servidor                     ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════╝");
            System.out.println();

            System.out.println("[DEBUG] Entrando a CountDownLatch.await() - servidor deberia mantenerse vivo");
            System.out.flush();
            var shutdownLatch = new java.util.concurrent.CountDownLatch(1);
            shutdownLatch.await();
            System.out.println("[DEBUG] Saliendo de CountDownLatch.await() - esto NO deberia pasar");
        } catch (InterruptedException e) {
            System.out.println("[SERVER] Interrumpido. Cerrando...");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println();
            System.err.println("╔═══════════════════════════════════════════════════════════════╗");
            System.err.println("║  [ERROR] ERROR FATAL INICIANDO SERVIDOR                       ║");
            System.err.println("╠═══════════════════════════════════════════════════════════════╣");
            System.err.println("║  " + truncateForBox(e.getMessage(), 59) + " ║");
            System.err.println("║                                                               ║");
            System.err.println("║  Posibles causas:                                             ║");
            System.err.println("║    - Puerto " + port + " ya esta en uso                            ║");
            System.err.println("║    - Permisos insuficientes                                   ║");
            System.err.println("║    - Dependencia Javalin no encontrada                        ║");
            System.err.println("╚═══════════════════════════════════════════════════════════════╝");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String truncateForBox(String msg, int max) {
        if (msg == null) return "Error desconocido".concat(" ".repeat(max - 17));
        if (msg.length() > max) return msg.substring(0, max - 3) + "...";
        return msg + " ".repeat(max - msg.length());
    }

    private static void runInteractiveSession() {
        AnsiConsole.systemInstall();

        try {
            System.out.println(ansi().fgBrightCyan().bold().a("Fararoni Online.").reset()
                .a(" Analizando territorio...").toString());
            System.out.println();

            runProactiveDiagnostic();

            if (!core.isReady()) {
                System.out.println(ansi().fgYellow().a("Inicializando subsistemas...").reset());
                core.initialize();
            }

            InteractiveShell ui = new InteractiveShell(core);
            ui.start();
        } finally {
            if (core != null) {
                core.shutdown();
            }
            AnsiConsole.systemUninstall();
        }
    }

    private static void runProactiveDiagnostic() {
        String report = core.generateDiagnosticReport();
        System.out.println(report);

        var gitManager = core.getGitInspector();
        if (gitManager.isGitRepository() && gitManager.hasUncommittedChanges()) {
            System.out.println(ansi().fgYellow()
                .a("Tienes trabajo pendiente sin guardar.")
                .reset().toString());
            System.out.println("Quieres que revise estos cambios? (Escribe tu orden)");
        } else if (gitManager.isGitRepository()) {
            System.out.println(ansi().fgGreen()
                .a("Proyecto limpio.")
                .reset().a(" Que construimos hoy?").toString());
        } else {
            System.out.println("Que necesitas?");
        }
        System.out.println();
    }

    private static void startInteractiveChat() {
        System.out.println(ansi().fgBrightBlue()
            .a("Modo Interactivo. Escribe ")
            .bold().a("/help").boldOff()
            .a(" para ver comandos disponibles.")
            .reset().toString());
        System.out.println();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (running) {
                System.out.print(ansi().fgBrightGreen().bold().a(PROMPT).reset());
                System.out.flush();

                String input = reader.readLine();

                if (input == null) {
                    System.out.println();
                    System.out.println(ansi().fgCyan().a("Hasta luego!").reset());
                    break;
                }

                input = input.trim();

                if (input.isEmpty()) {
                    continue;
                }

                processInput(input);
            }
        } catch (IOException e) {
            System.err.println("Error de I/O: " + e.getMessage());
        }
    }

    private static void processInput(String input) {
        if (input.startsWith("/")) {
            processCommand(input);
            return;
        }

        try {
            String response;

            if (useSwarm) {
                System.out.println(ansi().fgCyan().a("Ejecutando mision con el Swarm...").reset());

                if (!core.isReady()) {
                    System.out.println(ansi().fgYellow().a("Conectando al LLM...").reset());
                    core.initialize();
                }

                HiveMind.MissionResult result = core.executeMission(input);

                if (result.isSuccess()) {
                    response = result.result() != null ? result.result() : "Mision completada.";
                    System.out.println(ansi().fgGreen().a("Mision exitosa!")
                        .fgDefault().a(" (").a(result.duration().toSeconds()).a("s)").reset());
                } else {
                    response = "Error: " + result.errorMessage();
                    System.out.println(ansi().fgRed().a("Mision fallida: ")
                        .a(result.errorMessage()).reset());
                }
            } else {
                if (!core.isReady()) {
                    System.out.println(ansi().fgYellow().a("Conectando al LLM...").reset());
                    core.initialize();
                }

                String contextualPrompt = buildContextualPrompt(input);
                response = core.chat(contextualPrompt);
            }

            System.out.println();
            System.out.println(ansi().fgBrightMagenta().bold().a(ASSISTANT_PREFIX).reset());
            System.out.println(response);
            System.out.println();
        } catch (Exception e) {
            System.out.println(ansi().fgRed().a("Error: " + e.getMessage()).reset());
            System.out.println();
        }
    }

    private static String buildContextualPrompt(String userInput) {
        String quickStatus = core.getQuickStatus();

        return String.format("""
            Contexto del proyecto: %s
            Directorio: %s

            Usuario: %s
            """,
            quickStatus,
            core.getWorkingDirectory().getFileName(),
            userInput
        );
    }

    private static void processCommand(String command) {
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case CMD_EXIT, CMD_QUIT -> {
                System.out.println(ansi().fgCyan().a("Hasta luego!").reset());
                running = false;
            }

            case CMD_HELP -> printCommandHelp();

            case CMD_STATUS -> {
                System.out.println(core.generateDiagnosticReport());
            }

            case CMD_CLEAR -> {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }

            case CMD_MISSION -> {
                useSwarm = !useSwarm;
                String mode = useSwarm ? "SWARM (multi-agente)" : "CHAT (respuesta directa)";
                System.out.println(ansi().fgYellow()
                    .a("Modo cambiado a: ").bold().a(mode).reset());

                if (useSwarm) {
                    setupLogListener();
                }
            }

            case CMD_CONTEXT -> {
                String contextReport = core.getWorkspaceInsight().scanAndReport(core.getWorkingDirectory());
                System.out.println(contextReport);
            }

            case CMD_GIT -> processGitCommand(arg);

            case CMD_RECONFIG -> processReconfigCommand(arg);

            default -> {
                System.out.println(ansi().fgYellow()
                    .a("Comando desconocido: ").a(cmd)
                    .a(". Escribe /help para ver comandos.").reset());
            }
        }
        System.out.println();
    }

    private static void processGitCommand(String subCommand) {
        var git = core.getGitInspector();

        if (!git.isGitRepository()) {
            System.out.println(ansi().fgRed().a("No es un repositorio Git.").reset());
            return;
        }

        switch (subCommand.toLowerCase()) {
            case "status", "" -> {
                if (git.hasUncommittedChanges()) {
                    System.out.println(ansi().fgYellow().a("Hay cambios sin commitear:").reset());
                    System.out.println(git.getDiff().lines().limit(20).reduce("", (a, b) -> a + "\n" + b));
                } else {
                    System.out.println(ansi().fgGreen().a("Directorio limpio.").reset());
                }
            }

            case "snapshot" -> {
                if (git.createSnapshot()) {
                    System.out.println(ansi().fgGreen().a("Snapshot creado.").reset());
                } else {
                    System.out.println(ansi().fgRed().a("Error creando snapshot.").reset());
                }
            }

            case "undo" -> {
                if (git.undoLastChange()) {
                    System.out.println(ansi().fgGreen().a("Cambios revertidos.").reset());
                } else {
                    System.out.println(ansi().fgRed().a("Error revirtiendo cambios.").reset());
                }
            }

            case "branch" -> {
                String branch = git.getCurrentBranch();
                System.out.println("Rama actual: " + (branch != null ? branch : "desconocida"));
            }

            default -> {
                System.out.println("Subcomandos de /git: status, snapshot, undo, branch");
            }
        }
    }

    private static void processReconfigCommand(String subCommand) {
        if (subCommand == null || subCommand.isBlank()) {
            System.out.println();
            System.out.println(ansi().fgCyan().bold().a("[CONFIG] Abriendo Asistente de Configuracion...").reset());
            new FirstRunExperience().runSetupWizard();
            System.out.println(ansi().fgGreen().a("[OK] Configuracion actualizada.").reset());
            System.out.println("   Los cambios aplicarán en la próxima operación LLM.");
            return;
        }

        var discovery = new LlmProviderDiscovery();
        var resolver = new ConfigPriorityResolver();

        switch (subCommand.toLowerCase().trim()) {
            case "scan" -> {
                System.out.println();
                System.out.println(ansi().fgCyan().bold().a("[SCAN] ESCANEO DE PROVEEDORES").reset());
                String currentUrl = resolver.resolveServerUrl(null);
                String currentModel = resolver.resolveModelName(null);
                System.out.println("Config actual: " + currentUrl + " (" + currentModel + ")");
                System.out.println();
                discovery.discoverBestProvider(currentUrl, currentModel, null);
            }

            case "reset" -> {
                System.out.println();
                try {
                    var configService = SecureConfigService.getInstance();

                    configService.setProperty("server.url",
                        AppDefaults.DEFAULT_SERVER_URL);
                    configService.setProperty("model.name",
                        AppDefaults.DEFAULT_MODEL_NAME);

                    System.out.println(ansi().fgGreen().a("[OK] Configuracion restaurada a valores por defecto.").reset());
                    System.out.println("   URL:    " + AppDefaults.DEFAULT_SERVER_URL);
                    System.out.println("   Modelo: " + AppDefaults.DEFAULT_MODEL_NAME);
                    System.out.println("   Los cambios aplicarán en la próxima operación LLM.");
                } catch (Exception e) {
                    System.out.println(ansi().fgRed().a("[ERROR] Error: " + e.getMessage()).reset());
                }
            }

            default -> System.out.println(ansi().fgYellow()
                .a("Subcomando desconocido. Use /reconfig, /reconfig scan, o /reconfig reset").reset());
        }
    }

    private static void setupLogListener() {
        if (!core.isReady()) {
            System.out.println(ansi().fgYellow().a("Inicializando conexion al LLM...").reset());
            core.initialize();
        }

        core.getMessageBus().subscribeAll(msg -> {
            String action = formatMessageAction(msg);
            ConsoleUtils.printAgentThinking(msg.senderId(), action);
        });

        System.out.println(ansi().fgGreen().a("Visualizacion en tiempo real activada.").reset());
    }

    private static String formatMessageAction(SwarmMessage msg) {
        String type = msg.type();
        String to = msg.receiverId();

        return switch (type) {
            case SwarmMessage.TYPE_USER_REQUEST -> "Recibiendo solicitud del usuario...";
            case SwarmMessage.TYPE_REQUIREMENTS -> "Enviando requerimientos a " + to + "...";
            case SwarmMessage.TYPE_BLUEPRINT -> "Enviando especificacion tecnica a " + to + "...";
            case SwarmMessage.TYPE_CODE_DRAFT -> "Enviando codigo a " + to + " para revision...";
            case SwarmMessage.TYPE_TEST_RESULT -> "Reportando resultado de pruebas a " + to + "...";
            case SwarmMessage.TYPE_BUG_REPORT -> "Reportando bug a " + to + "...";
            case SwarmMessage.TYPE_APPROVAL -> "Aprobacion enviada a " + to + "!";
            case SwarmMessage.TYPE_FINAL_DELIVERY -> "Entrega final lista!";
            case SwarmMessage.TYPE_ERROR -> "Error: " + truncate(msg.content(), 50);
            case SwarmMessage.TYPE_ACKNOWLEDGE -> "ACK recibido de " + to;
            default -> "Procesando " + type + " -> " + to;
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static void printCommandHelp() {
        String help = """

            Comandos Disponibles:

              /help      - Muestra esta ayuda
              /status    - Muestra estado del workspace
              /context   - Muestra archivos que Fararoni "ve"
              /mission   - Alterna entre modo CHAT y SWARM (activa logs en vivo)
              /git       - Operaciones Git (status, snapshot, undo, branch)
              /reconfig  - Reconfigura conexión LLM (scan/reset)
              /clear     - Limpia la pantalla
              /exit      - Salir

            Modo actual: %s

            Escribe cualquier texto para chatear con Fararoni.
            En modo SWARM veras el flujo de agentes en tiempo real.
            """.formatted(useSwarm ? "SWARM (multi-agente)" : "CHAT (respuesta directa)");

        System.out.println(ansi().fgCyan().a(help).reset());
    }

    private static void printHelp() {
        String help = """
            FARARONI - CLI Inteligente para Desarrollo

            Uso:
              fararoni                         Modo interactivo (chat)
              fararoni "comando"               Modo headless (one-shot)
              fararoni --cwd PATH              Especifica directorio de trabajo
              fararoni --cwd PATH "comando"    Headless en directorio especifico
              fararoni --server                Modo servidor HTTP (puerto 7070)
              fararoni --server --port 8080    Servidor en puerto especifico
              fararoni --version               Muestra version
              fararoni --help                  Muestra esta ayuda
              fararoni --debug                 Modo debug (stack traces)

            Modos de Operacion:
              INTERACTIVO   Chat conversacional tipo Claude Code
              HEADLESS      Ejecucion one-shot para scripts/CI
              SERVIDOR      API REST + WebSocket para plugins IDE

            Ejemplos:
              fararoni                                    # Chat interactivo
              fararoni "Crea un README.md"                # Ejecuta y termina
              fararoni --cwd /mi/proyecto "Analiza"       # Headless en otro dir
              fararoni --server                           # Servidor en :7070
              fararoni --server --port 3000               # Servidor en :3000

            Modo Servidor (--server):
              Levanta un servidor HTTP que expone:
                POST   /api/task              Ejecutar mision
                GET    /api/status            Estado del servidor
                GET    /api/session/{userId}  Estado de sesion
                DELETE /api/session/{userId}  Cerrar sesion
                GET    /health                Health check
                WS     /ws/events?userId=X    Live feed WebSocket

              Usado por: VS Code Extension, IntelliJ Plugin, Dashboard Web

            Variables de Entorno:
              FARARONI_SERVER_PORT             Puerto del servidor (default: 7070)
              FARARONI_OLLAMA_URL              URL de Ollama (default: localhost:11434)
              FARARONI_RABBIT_MODEL            Modelo rapido (default: qwen2.5-coder:1.5b)
              FARARONI_TURTLE_MODEL            Modelo experto (default: qwen2.5-coder:32b)

            Codigos de Salida (para scripts):
              0 = Exito
              1 = Fallo del agente
              2 = Error critico/crash

            Dentro del modo interactivo, usa /help para ver comandos disponibles.
            """;
        System.out.println(help);
    }

    private static void printLegacyStory() {
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

    private static void typewrite(String text, int delayMs) throws InterruptedException {
        for (char c : text.toCharArray()) {
            System.out.print(c);
            System.out.flush();
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
        }
    }

    private static void configureLogging() {
        try {
            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");

            for (java.util.logging.Handler h : rootLogger.getHandlers()) {
                rootLogger.removeHandler(h);
            }

            rootLogger.addHandler(new DynamicConsoleHandler());
        } catch (Exception e) {
            System.err.println("[WARN] No se pudo configurar DynamicConsoleHandler: " + e.getMessage());
        }
    }
}
