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
package dev.fararoni.core.core.swarm.base;

import dev.fararoni.core.core.kernel.HyperNativeKernel;
import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.core.skills.FileSystemSkillImpl;
import dev.fararoni.core.core.swarm.context.SwarmContext;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;
import dev.fararoni.core.core.swarm.infra.MessageBus;
import dev.fararoni.core.core.swarm.infra.SwarmTransport;
import dev.fararoni.core.observability.AgentSpan;
import dev.fararoni.core.observability.TelemetryService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class SwarmAgent implements Callable<Void> {
    private static final Logger LOG = Logger.getLogger(SwarmAgent.class.getName());

    protected final String agentId;
    protected final Persona myPersona;

    protected volatile boolean running = true;
    protected long messagesProcessed = 0;
    protected long errorsEncountered = 0;

    protected volatile String currentState = "IDLE";
    protected volatile String currentAction = "";

    protected final TelemetryService telemetry;

    private static final long RECEIVE_TIMEOUT_SECONDS = 5;
    private static final long IDLE_INTERVAL_MS = 100;

    public SwarmAgent(String agentId, Persona persona) {
        this.agentId = agentId;
        this.myPersona = persona;
        this.telemetry = TelemetryService.getInstance();
    }

    protected SwarmTransport getBus() {
        return SwarmContext.busOrDefault();
    }

    @Override
    public Void call() throws Exception {
        LOG.info(() -> String.format("[%s] Online en Virtual Thread: %s",
            agentId, Thread.currentThread()));

        try {
            onStartup();

            System.out.println("[DEBUG-AGENT-" + agentId + "] Entrando al loop...");
            while (running) {
                try {
                    SwarmMessage msg = getBus().receive(agentId, RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    if (msg != null) {
                        System.out.println("[DEBUG-AGENT-" + agentId + "] Mensaje recibido: " + msg.type());
                        if (isTerminationSignal(msg)) {
                            LOG.info(() -> String.format(
                                "[%s] Recibida orden de terminacion (%s). Apagando.",
                                agentId, msg.type()));
                            running = false;
                            break;
                        }

                        handleMessage(msg);
                    } else {
                        if (!"IDLE".equals(currentState)) {
                        } else {
                        }
                        onIdle();
                    }
                } catch (InterruptedException e) {
                    System.out.println("[DEBUG-AGENT-" + agentId + "] InterruptedException!");
                    LOG.info(() -> "[" + agentId + "] Interrumpido. Finalizando...");
                    running = false;
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("[DEBUG-AGENT-" + agentId + "] Loop terminado normalmente");
        } catch (Exception e) {
            System.out.println("[DEBUG-AGENT-" + agentId + "] EXCEPTION: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            LOG.severe(() -> String.format("[%s] ERROR CRÍTICO: %s",
                agentId, e.getMessage()));
            errorsEncountered++;
            throw new RuntimeException("Agente " + agentId + " colapsó", e);
        } finally {
            getBus().unregister(agentId);
            onShutdown();
        }

        return null;
    }

    protected boolean isTerminationSignal(SwarmMessage msg) {
        String type = msg.type();
        return "MISSION_COMPLETE".equals(type) || "MISSION_TERMINATE".equals(type);
    }

    protected abstract void processMessage(SwarmMessage msg);

    protected void onStartup() {
        LOG.fine(() -> "[" + agentId + "] Startup hook");
    }

    protected void onIdle() {
        try {
            Thread.sleep(IDLE_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected void onShutdown() {
        LOG.info(() -> String.format("[%s] Shutdown. Mensajes procesados: %d, Errores: %d",
            agentId, messagesProcessed, errorsEncountered));
    }

    private void handleMessage(SwarmMessage msg) {
        String traceId = SwarmContext.traceId();
        LOG.fine(() -> String.format("[%s][%s] Recibido: %s de %s",
            agentId, traceId, msg.type(), msg.senderId()));

        if (isReplayMessage(msg)) {
            if (shouldSkipReplay(msg)) {
                LOG.fine(() -> String.format(
                    "[%s] Saltando mensaje replay: %s (idempotencia)",
                    agentId, msg.type()));
                messagesProcessed++;
                return;
            }
            LOG.fine(() -> String.format(
                "[%s] Procesando mensaje replay: %s",
                agentId, msg.type()));
        }

        try (AgentSpan span = telemetry.startSpan("agent.process", "agentId", agentId, "messageType", msg.type())) {
            try {
                emitTelemetry("EXECUTING", "Procesando " + msg.type());
                processMessage(msg);
                messagesProcessed++;
                telemetry.incrementCounter("agent.messages.processed", "agentId", agentId, "type", msg.type());
                emitTelemetry("COMPLETED", "Mensaje " + msg.type() + " procesado");
            } catch (Exception e) {
                span.setError(e);
                LOG.warning(() -> String.format("[%s] Error procesando mensaje %s: %s",
                    agentId, msg.type(), e.getMessage()));
                errorsEncountered++;
                telemetry.incrementCounter("agent.messages.errors", "agentId", agentId, "type", msg.type());
                handleError(msg, e);
            }
        }
    }

    protected boolean isReplayMessage(SwarmMessage msg) {
        return Boolean.TRUE.equals(msg.getMetadata("IS_REPLAY"));
    }

    protected boolean shouldSkipReplay(SwarmMessage msg) {
        return true;
    }

    protected void handleError(SwarmMessage msg, Exception e) {
        SwarmMessage errorMsg = SwarmMessage.error(agentId, msg.senderId(),
            "Error procesando " + msg.type() + ": " + e.getMessage());
        getBus().send(errorMsg);
    }

    protected String think(String taskDescription) {
        HyperNativeKernel kernel = SwarmContext.kernel();
        String missionId = SwarmContext.missionId();

        LOG.fine(() -> String.format("[%s] Thinking para Misión: %s",
            agentId, missionId));

        emitTelemetry("THINKING", "Procesando con LLM...");

        try (AgentSpan span = telemetry.startSpan("agent.think", "agentId", agentId)) {
            try {
                String result = kernel.think(myPersona, taskDescription, null);
                telemetry.incrementCounter("agent.think.success", "agentId", agentId);
                telemetry.recordDistribution("agent.think.response_length", result.length(), "agentId", agentId);
                emitTelemetry("COMPLETED", "Respuesta generada (" + result.length() + " chars)");
                return result;
            } catch (Exception e) {
                span.setError(e);
                telemetry.incrementCounter("agent.think.errors", "agentId", agentId);
                emitTelemetry("FAILED", e.getMessage());
                throw e;
            } finally {
                currentState = "IDLE";
                currentAction = "";
            }
        }
    }

    protected String thinkWithContext(String taskDescription, String ragContext) {
        HyperNativeKernel kernel = SwarmContext.kernel();

        emitTelemetry("THINKING", "Procesando con RAG...");

        try {
            String result = kernel.think(myPersona, taskDescription, ragContext);
            emitTelemetry("COMPLETED", "Respuesta generada (" + result.length() + " chars)");
            return result;
        } catch (Exception e) {
            emitTelemetry("FAILED", e.getMessage());
            throw e;
        } finally {
            currentState = "IDLE";
            currentAction = "";
        }
    }

    protected void emitTelemetry(String state, String action) {
        this.currentState = state;
        this.currentAction = action;

        String icon = switch (state) {
            case "THINKING" -> "[THINK]";
            case "EXECUTING" -> "[EXEC]";
            case "COMPLETED" -> "[OK]";
            case "FAILED" -> "[ERROR]";
            case "IDLE" -> "[IDLE]";
            default -> "[?]";
        };

        System.out.println("[" + agentId + "] " + icon + " " + state + ": " + action);
    }

    private FileSystemSkillImpl fileSystemSkill;

    private FileSystemSkillImpl getFileSystemSkill() {
        if (fileSystemSkill == null) {
            Path workspace = SwarmContext.workspaceOrDefault();
            fileSystemSkill = new FileSystemSkillImpl(workspace);
        }
        return fileSystemSkill;
    }

    @Deprecated
    protected String useTool(String toolName, String args) {
        return executeTool(toolName, args);
    }

    protected String executeTool(String toolName, String... args) {
        HyperNativeKernel kernel = SwarmContext.kernel();

        if (!kernel.validateToolAccess(myPersona, toolName)) {
            String error = String.format(
                "ERROR: ACCESO DENEGADO. %s no tiene permiso para usar %s",
                myPersona.name(), toolName);
            LOG.warning(() -> "[" + agentId + "] " + error);
            return error;
        }

        LOG.info(() -> String.format("[%s] Ejecutando herramienta: %s", agentId, toolName));

        emitTelemetry("EXECUTING", "Herramienta: " + toolName);

        try {
            return switch (toolName) {
                case "fs_write" -> {
                    if (args.length < 2) {
                        yield "ERROR: fs_write requiere 2 argumentos: filename, content";
                    }
                    String filename = args[0];
                    String content = args[1];

                    String syntaxError = validateSyntax(filename, content);
                    if (syntaxError != null) {
                        LOG.warning(() -> String.format(
                            "[%s] AST GUARD: Codigo rechazado para %s - %s",
                            agentId, filename, syntaxError));
                        yield "RECHAZADO: El codigo generado tiene errores de sintaxis. " + syntaxError;
                    }

                    var result = getFileSystemSkill().writeFile(filename, content);
                    if (result.success()) {
                        LOG.info(() -> String.format("[%s] Archivo escrito: %s", agentId, filename));
                        yield "SUCCESS: Archivo creado: " + filename;
                    } else {
                        yield "ERROR: " + result.error();
                    }
                }

                case "fs_read" -> {
                    if (args.length < 1) {
                        yield "ERROR: fs_read requiere 1 argumento: filename";
                    }
                    var result = getFileSystemSkill().readFile(args[0]);
                    if (result.success()) {
                        yield result.data();
                    } else {
                        yield "ERROR: " + result.error();
                    }
                }

                case "fs_exists" -> {
                    if (args.length < 1) {
                        yield "ERROR: fs_exists requiere 1 argumento: filename";
                    }
                    var result = getFileSystemSkill().exists(args[0]);
                    yield result.success() && result.data() ? "true" : "false";
                }

                case "fs_patch", "apply_patch" -> {
                    if (args.length < 1) {
                        yield "ERROR: fs_patch requiere argumento con formato: PATH <<<SEARCH>>> OLD <<<REPLACE>>> NEW";
                    }
                    yield performSafePatch(args[0]);
                }

                default -> "ERROR: Herramienta no implementada: " + toolName;
            };
        } catch (Exception e) {
            LOG.severe(() -> String.format("[%s] Error ejecutando %s: %s",
                agentId, toolName, e.getMessage()));
            emitTelemetry("FAILED", "Error en " + toolName);
            return "ERROR: " + e.getMessage();
        } finally {
            currentState = "IDLE";
            currentAction = "";
        }
    }

    private String performSafePatch(String args) {
        try {
            String[] parts = args.split("<<<SEARCH>>>");
            if (parts.length < 2) {
                return "[ERROR] Formato invalido. Usa: PATH <<<SEARCH>>> OLD_BLOCK <<<REPLACE>>> NEW_BLOCK";
            }

            String pathStr = parts[0].trim();
            String remaining = parts[1];

            String[] codeParts = remaining.split("<<<REPLACE>>>");
            if (codeParts.length < 2) {
                return "[ERROR] Falta el bloque de reemplazo (<<<REPLACE>>> missing).";
            }

            String searchBlock = codeParts[0];
            String replaceBlock = codeParts[1];

            Path workspace = SwarmContext.workspaceOrDefault();
            Path path = workspace.resolve(pathStr).normalize();

            if (!path.startsWith(workspace)) {
                return "[ERROR] ACCESO DENEGADO: Intento de escritura fuera del workspace.";
            }
            if (!Files.exists(path)) {
                return "[ERROR] El archivo objetivo no existe: " + pathStr;
            }

            Path backupPath = path.resolveSibling(path.getFileName() + ".bak");
            Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
            LOG.info(() -> String.format("[%s] Backup creado: %s", agentId, backupPath.getFileName()));

            List<String> fileLines = Files.readAllLines(path);
            List<String> searchLines = new java.util.ArrayList<>(java.util.Arrays.asList(searchBlock.split("\r?\n")));
            List<String> replaceLines = new java.util.ArrayList<>(java.util.Arrays.asList(replaceBlock.split("\r?\n")));

            searchLines = trimEmptyLines(searchLines);
            replaceLines = trimEmptyLines(replaceLines);

            if (searchLines.isEmpty()) {
                return "[ERROR] El bloque de busqueda esta vacio.";
            }

            int matchIndex = findFuzzyMatch(fileLines, searchLines);

            if (matchIndex == -1) {
                String content = Files.readString(path);
                if (content.contains(searchBlock.trim())) {
                    String newContent = content.replace(searchBlock.trim(), replaceBlock.trim());
                    Files.writeString(path, newContent);
                    return "[OK] Parche aplicado (Match Exacto). Backup: " + backupPath.getFileName();
                }
                return "[WARN] FALLO QUIRURGICO: No encontre el bloque de codigo original.\n" +
                       "   Consejo: Verifica que el bloque <<<SEARCH>>> coincida con el archivo.";
            }

            List<String> newFileLines = new java.util.ArrayList<>();
            newFileLines.addAll(fileLines.subList(0, matchIndex));
            newFileLines.addAll(replaceLines);
            newFileLines.addAll(fileLines.subList(matchIndex + searchLines.size(), fileLines.size()));

            Files.write(path, newFileLines);
            LOG.info(() -> String.format("[%s] Parche aplicado en linea %d", agentId, matchIndex + 1));
            return "Parche aplicado con exito (Fuzzy Match linea " + (matchIndex + 1) + "). Backup: " + backupPath.getFileName();
        } catch (Exception e) {
            LOG.severe(() -> String.format("[%s] Error en parcheo: %s", agentId, e.getMessage()));
            return "ERROR FATAL EN PARCHEO: " + e.getMessage();
        }
    }

    private List<String> trimEmptyLines(List<String> lines) {
        java.util.LinkedList<String> list = new java.util.LinkedList<>(lines);
        while (!list.isEmpty() && list.getFirst().trim().isEmpty()) list.removeFirst();
        while (!list.isEmpty() && list.getLast().trim().isEmpty()) list.removeLast();
        return list;
    }

    private int findFuzzyMatch(List<String> source, List<String> target) {
        for (int i = 0; i <= source.size() - target.size(); i++) {
            boolean match = true;
            for (int j = 0; j < target.size(); j++) {
                String sourceLine = source.get(i + j).trim();
                String targetLine = target.get(j).trim();
                if (!sourceLine.equals(targetLine)) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private String validateSyntax(String filename, String content) {
        if (filename == null || content == null || content.isBlank()) {
            return null;
        }

        String lowerFilename = filename.toLowerCase();

        try {
            if (lowerFilename.endsWith(".py")) {
                return validatePythonSyntax(content);
            } else if (lowerFilename.endsWith(".java")) {
                return validateJavaSyntax(content);
            } else if (lowerFilename.endsWith(".js") || lowerFilename.endsWith(".ts")) {
                return validateJsSyntax(content);
            }
            return null;
        } catch (Exception e) {
            LOG.fine(() -> "[AST Guard] Error durante validacion: " + e.getMessage());
            return null;
        }
    }

    private String validatePythonSyntax(String content) {
        try {
            Path tempFile = Files.createTempFile("fararoni_validate_", ".py");
            Files.writeString(tempFile, content);

            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "python3", "-m", "py_compile", tempFile.toString());
                pb.redirectErrorStream(true);
                Process process = pb.start();

                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return null;
                }

                if (process.exitValue() != 0) {
                    String output = new String(process.getInputStream().readAllBytes());
                    String[] lines = output.split("\n");
                    for (String line : lines) {
                        if (line.contains("SyntaxError") || line.contains("IndentationError")) {
                            return line.trim();
                        }
                    }
                    return "Error de sintaxis Python";
                }

                return null;
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            LOG.fine(() -> "[AST Guard] Error validando Python: " + e.getMessage());
            return null;
        }
    }

    private String validateJavaSyntax(String content) {
        int braces = 0;
        int parens = 0;
        int brackets = 0;

        for (char c : content.toCharArray()) {
            switch (c) {
                case '{' -> braces++;
                case '}' -> braces--;
                case '(' -> parens++;
                case ')' -> parens--;
                case '[' -> brackets++;
                case ']' -> brackets--;
            }

            if (braces < 0) return "Llave de cierre } sin abrir";
            if (parens < 0) return "Parentesis ) sin abrir";
            if (brackets < 0) return "Corchete ] sin abrir";
        }

        if (braces != 0) return "Llaves desbalanceadas: " + braces + " sin cerrar";
        if (parens != 0) return "Parentesis desbalanceados: " + parens + " sin cerrar";
        if (brackets != 0) return "Corchetes desbalanceados: " + brackets + " sin cerrar";

        return null;
    }

    private String validateJsSyntax(String content) {
        return validateJavaSyntax(content);
    }

    protected void reply(SwarmMessage original, String type, String content) {
        SwarmMessage response = original.reply(agentId, type, content);
        getBus().send(response);
    }

    protected void sendTo(String receiverId, String type, String content) {
        SwarmMessage msg = SwarmMessage.builder()
            .from(agentId)
            .to(receiverId)
            .type(type)
            .content(content)
            .metadata("missionId", SwarmContext.MISSION_ID.orElse("UNKNOWN"))
            .metadata("traceId", SwarmContext.traceId())
            .build();
        getBus().send(msg);
    }

    protected void broadcast(String type, String content) {
        for (String receiver : getBus().getRegisteredAgents()) {
            if (!receiver.equals(agentId)) {
                sendTo(receiver, type, content);
            }
        }
    }

    public void stop() {
        LOG.info(() -> "[" + agentId + "] Stop requested");
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public String getAgentId() {
        return agentId;
    }

    public Persona getPersona() {
        return myPersona;
    }

    public long getMessagesProcessed() {
        return messagesProcessed;
    }

    public long getErrorsEncountered() {
        return errorsEncountered;
    }

    public AgentStats getStats() {
        return new AgentStats(
            agentId,
            myPersona.id(),
            messagesProcessed,
            errorsEncountered,
            running,
            getBus().pendingCount(agentId)
        );
    }

    public record AgentStats(
        String agentId,
        String personaId,
        long messagesProcessed,
        long errors,
        boolean running,
        int pendingMessages
    ) {}
}
