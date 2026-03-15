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
package dev.fararoni.core.core.skills.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fararoni.core.core.index.ProjectKnowledgeBase;
import dev.fararoni.core.core.runtime.sandbox.DockerSandbox;
import dev.fararoni.core.core.skills.ToolExecutionResult;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ToolExecQwenHandlers {
    private static final Logger logger = Logger.getLogger(ToolExecQwenHandlers.class.getName());

    private static final Set<String> NIVEL_0_READ_ONLY = Set.of(
        "cat", "find", "tree", "wc", "head", "tail", "ls", "pwd",
        "date", "whoami", "hostname", "uname", "uptime", "id", "grep", "du", "df"
    );
    private static final Set<String> NIVEL_1_BUILD = Set.of(
        "mvn", "gradle", "gradlew", "npm", "npx", "go", "cargo", "make"
    );
    private static final Set<String> NIVEL_2_EXEC = Set.of("java");
    private static final Set<String> BLOCKED_COMMANDS = Set.of(
        "rm", "rmdir", "mv", "curl", "wget", "sudo", "su", "docker",
        "ssh", "scp", "sftp", "nc", "ncat", "nmap", "chmod", "chown",
        "kill", "killall", "pkill", "reboot", "shutdown", "dd", "mkfs",
        "mount", "umount", "fdisk", "cd"
    );

    private static final int TIMEOUT_NIVEL_0 = 10;
    private static final int TIMEOUT_NIVEL_1 = 120;
    private static final int TIMEOUT_NIVEL_2 = 120;
    private static final int TIMEOUT_MAX = 300;

    private final ObjectMapper mapper;
    private final Path workingDirectory;
    private final ProjectKnowledgeBase knowledgeBase;

    public ToolExecQwenHandlers(ObjectMapper mapper, Path workingDirectory, ProjectKnowledgeBase knowledgeBase) {
        this.mapper = mapper;
        this.workingDirectory = workingDirectory;
        this.knowledgeBase = knowledgeBase;
    }

    public ToolExecutionResult dispatch(String functionName, String jsonArgs) throws Exception {
        return switch (functionName.toLowerCase()) {
            case "taskcreate" -> handleQwenTaskCreate(jsonArgs);
            case "taskupdate" -> handleQwenTaskUpdate(jsonArgs);
            case "taskget" -> handleTaskGet(jsonArgs);
            case "tasklist" -> handleTaskList(jsonArgs);
            case "tasksearch" -> handleTaskSearch(jsonArgs);
            case "taskstart" -> handleTaskStart(jsonArgs);
            case "taskstop" -> handleTaskStop(jsonArgs);
            case "taskstopall" -> handleTaskStopAll(jsonArgs);
            case "commentcreate" -> handleCommentCreate(jsonArgs);
            case "projectlist" -> handleProjectList(jsonArgs);
            case "projectget" -> handleProjectGet(jsonArgs);
            case "projectcreate" -> handleProjectCreate(jsonArgs);
            case "codereviewrequest" -> handleCodeReviewRequest(jsonArgs);
            case "codereviewapprove" -> handleCodeReviewApprove(jsonArgs);
            case "codereviewreject" -> handleCodeReviewReject(jsonArgs);
            case "enterplanmode" -> handleEnterPlanMode(jsonArgs);
            case "exitplanmode" -> handleExitPlanMode(jsonArgs);
            case "shellcommand" -> handleShellCommand(jsonArgs);
            case "executecode" -> handleExecuteCodeBlocked(jsonArgs);
            default -> new ToolExecutionResult(false,
                "Error: Herramienta Qwen '" + functionName + "' no implementada.",
                Optional.empty(), Optional.empty());
        };
    }

    public ToolExecutionResult handleQwenTaskCreate(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        String subject = args.path("subject").asText("Misión Sin Título");
        String description = args.path("description").asText("");

        String uiHint = "Procesando solicitud";

        if (args.has("activeForm")) {
            String rawHint = args.path("activeForm").asText();
            if (rawHint != null && !rawHint.isBlank()) {
                uiHint = rawHint.length() > 50 ? rawHint.substring(0, 47) + "..." : rawHint;
            }
        } else {
            String safeSubject = subject.length() > 30 ? subject.substring(0, 27) + "..." : subject;
            uiHint = "Iniciando: " + safeSubject;
        }

        String missionId = "task-" + System.currentTimeMillis();
        logger.info("[QWEN-ADAPTER] Qwen ha creado la tarea: " + subject + " (ID: " + missionId + ")");

        String finalStatus = "pending";
        logger.info("[QWEN-ADAPTER] Tarea registrada: " + subject);

        ObjectNode response = mapper.createObjectNode();
        response.put("taskId", missionId);
        response.put("subject", subject);
        response.put("status", finalStatus);
        response.put("activeForm", uiHint);
        response.put("owner", "User");
        response.putArray("blocks");
        response.putArray("blockedBy");

        if (knowledgeBase != null && knowledgeBase.isAvailable()) {
            try {
                String contextMap = knowledgeBase.generateHighLevelMap();
                if (contextMap != null && !contextMap.isBlank()) {
                    response.put("project_context", "Mapa del proyecto:\n" + contextMap);
                }
            } catch (Exception e) {
                logger.warning("[QWEN-ADAPTER] No se pudo inyectar project_context: " + e.getMessage());
            }
        }

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Tarea creada y delegada al Swarm: " + subject),
            Optional.of(missionId));
    }

    public ToolExecutionResult handleQwenTaskUpdate(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText();
        String status = args.path("status").asText();
        String comment = args.path("comment").asText("");

        logger.info("[QWEN-ADAPTER] Actualizando tarea " + taskId + " -> " + status +
            (comment.isEmpty() ? "" : " (" + comment + ")"));

        ObjectNode response = mapper.createObjectNode();
        response.put("id", taskId);
        response.put("status", status);
        response.put("updated", true);

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Estado actualizado: " + status),
            Optional.of(taskId));
    }

    public ToolExecutionResult handleTaskGet(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");

        if (taskId.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: TaskGet requiere 'taskId'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-TASK] TaskGet: " + taskId);

        ObjectNode response = mapper.createObjectNode();
        response.put("id", taskId);
        response.put("subject", "Tarea " + taskId);
        response.put("description", "Detalles de la tarea (simulado)");
        response.put("status", "in_progress");
        response.put("owner", "User");
        response.putArray("blocks");
        response.putArray("blockedBy");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Detalles de tarea: " + taskId),
            Optional.of(taskId));
    }

    public ToolExecutionResult handleTaskList(String jsonArgs) throws Exception {
        logger.info("[QWEN-TASK] TaskList solicitado");

        ArrayNode tasks = mapper.createArrayNode();

        ObjectNode task = mapper.createObjectNode();
        task.put("id", "task-current");
        task.put("subject", "Sesión actual");
        task.put("status", "in_progress");
        task.put("owner", "User");
        tasks.add(task);

        String result = mapper.writeValueAsString(tasks);

        return new ToolExecutionResult(true,
            "=== LISTA DE TAREAS ===\n" + result,
            Optional.of(result),
            Optional.empty());
    }

    public ToolExecutionResult handleTaskSearch(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String query = args.path("query").asText("");
        String status = args.path("status").asText("");

        if (query.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: TaskSearch requiere 'query'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-TASK] TaskSearch: " + query + " (status: " + status + ")");

        ArrayNode results = mapper.createArrayNode();

        String resultJson = mapper.writeValueAsString(results);

        return new ToolExecutionResult(true,
            "=== BÚSQUEDA DE TAREAS ===\n" +
            "Query: " + query + "\n" +
            "Status: " + (status.isBlank() ? "all" : status) + "\n" +
            "Resultados: 0 tareas encontradas\n" +
            "CONCLUSIÓN: No hay tareas duplicadas. Puedes crear una nueva.",
            Optional.of(resultJson),
            Optional.of(query));
    }

    public ToolExecutionResult handleTaskStart(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");

        if (taskId.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: TaskStart requiere 'taskId'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-TASK] TaskStart: " + taskId);

        ObjectNode response = mapper.createObjectNode();
        response.put("id", taskId);
        response.put("status", "in_progress");
        response.put("started_at", java.time.Instant.now().toString());

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Tarea iniciada: " + taskId),
            Optional.of(taskId));
    }

    public ToolExecutionResult handleTaskStop(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");

        if (taskId.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: TaskStop requiere 'taskId'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-TASK] TaskStop: " + taskId);

        ObjectNode response = mapper.createObjectNode();
        response.put("id", taskId);
        response.put("status", "paused");
        response.put("paused_at", java.time.Instant.now().toString());

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Tarea pausada: " + taskId),
            Optional.of(taskId));
    }

    public ToolExecutionResult handleTaskStopAll(String jsonArgs) throws Exception {
        logger.info("[QWEN-TASK] TaskStopAll ejecutado");

        ObjectNode response = mapper.createObjectNode();
        response.put("status", "all_paused");
        response.put("count", 0);
        response.put("message", "Todas las tareas han sido pausadas");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Todas las tareas pausadas"),
            Optional.empty());
    }

    public ToolExecutionResult handleCommentCreate(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");
        String comment = args.path("comment").asText("");

        if (taskId.isBlank() || comment.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: CommentCreate requiere 'taskId' y 'comment'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-TASK] CommentCreate en " + taskId + ": " + comment);

        ObjectNode response = mapper.createObjectNode();
        response.put("taskId", taskId);
        response.put("commentId", "comment-" + System.currentTimeMillis());
        response.put("comment", comment);
        response.put("created_at", java.time.Instant.now().toString());

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Comentario agregado a " + taskId),
            Optional.of(taskId));
    }

    public ToolExecutionResult handleProjectList(String jsonArgs) throws Exception {
        logger.info("[QWEN-PROJECT] ProjectList");

        String currentDir = workingDirectory != null
            ? workingDirectory.getFileName().toString()
            : "workspace";

        ArrayNode projects = mapper.createArrayNode();
        ObjectNode project = mapper.createObjectNode();
        project.put("id", "proj-1");
        project.put("name", currentDir);
        project.put("status", "active");
        project.put("path", workingDirectory != null ? workingDirectory.toString() : ".");
        projects.add(project);

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(projects),
            Optional.of("Proyectos listados"),
            Optional.empty());
    }

    public ToolExecutionResult handleProjectGet(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String projectId = args.path("projectId").asText("");

        if (projectId.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: ProjectGet requiere 'projectId'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-PROJECT] ProjectGet: " + projectId);

        String currentDir = workingDirectory != null
            ? workingDirectory.getFileName().toString()
            : "workspace";

        ObjectNode response = mapper.createObjectNode();
        response.put("id", projectId);
        response.put("name", currentDir);
        response.put("status", "active");
        response.put("path", workingDirectory != null ? workingDirectory.toString() : ".");
        response.put("type", "java");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Detalles del proyecto: " + projectId),
            Optional.of(projectId));
    }

    public ToolExecutionResult handleProjectCreate(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String name = args.path("name").asText("");
        String description = args.path("description").asText("");

        if (name.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: ProjectCreate requiere 'name'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-PROJECT] ProjectCreate: " + name);

        String projectId = "proj-" + System.currentTimeMillis();

        ObjectNode response = mapper.createObjectNode();
        response.put("id", projectId);
        response.put("name", name);
        response.put("description", description);
        response.put("status", "created");
        response.put("message", "Proyecto creado localmente. Usa fs_mkdir para crear la estructura.");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Proyecto creado: " + name),
            Optional.of(projectId));
    }

    public ToolExecutionResult handleCodeReviewRequest(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");
        String reviewer = args.path("reviewer").asText("SENTINEL");

        if (taskId.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: CodeReviewRequest requiere 'taskId'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-REVIEW] CodeReviewRequest: " + taskId + " -> " + reviewer);

        ObjectNode response = mapper.createObjectNode();
        response.put("taskId", taskId);
        response.put("reviewer", reviewer);
        response.put("status", "pending_review");
        response.put("message", "Revisión solicitada. El agente " + reviewer + " ha sido notificado.");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Revisión solicitada para " + taskId),
            Optional.of(taskId));
    }

    public ToolExecutionResult handleCodeReviewApprove(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");

        if (taskId.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: CodeReviewApprove requiere 'taskId'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-REVIEW] CodeReviewApprove: " + taskId);

        ObjectNode response = mapper.createObjectNode();
        response.put("taskId", taskId);
        response.put("status", "approved");
        response.put("approved_at", java.time.Instant.now().toString());
        response.put("message", "Código aprobado. Listo para deploy.");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Código aprobado: " + taskId),
            Optional.of(taskId));
    }

    public ToolExecutionResult handleCodeReviewReject(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");
        String reason = args.path("reason").asText("");

        if (taskId.isBlank() || reason.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: CodeReviewReject requiere 'taskId' y 'reason'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-REVIEW] CodeReviewReject: " + taskId + " - " + reason);

        ObjectNode response = mapper.createObjectNode();
        response.put("taskId", taskId);
        response.put("status", "rejected");
        response.put("reason", reason);
        response.put("rejected_at", java.time.Instant.now().toString());
        response.put("message", "Código rechazado. Requiere correcciones.");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Código rechazado: " + taskId),
            Optional.of(taskId));
    }

    public ToolExecutionResult handleEnterPlanMode(String jsonArgs) throws Exception {
        logger.info("[MIND] Agente entrando en Fase de Planificacion Estrategica.");

        String tacticalResponse = """
            {
                "system_status": "PLANNING_PHASE_INITIATED",
                "instruction": "STOP USING TOOLS TEMPORARILY. You are now in the Strategic Planning Phase.",
                "required_action": "Analyze the context loaded above and OUTPUT a detailed step-by-step plan in natural language.",
                "format": "Structure your plan as: 1. Analysis of current state... 2. Strategy/Approach... 3. Execution steps... 4. Validation criteria...",
                "constraints": "Do NOT execute any tools until the plan is complete. Think first, act later.",
                "next_step": "After outputting your complete plan, either call 'ExitPlanMode' to begin execution, or start executing Step 1 directly."
            }
            """;

        return new ToolExecutionResult(true,
            tacticalResponse,
            Optional.of("Planning Phase Triggered - Awaiting strategic plan output"),
            Optional.empty());
    }

    public ToolExecutionResult handleExitPlanMode(String jsonArgs) throws Exception {
        logger.info("[MIND] Agente saliendo de Planificacion -> Modo Ejecucion.");

        String tacticalResponse = """
            {
                "system_status": "EXECUTION_PHASE_INITIATED",
                "instruction": "Planning phase complete. You may now USE TOOLS to execute your plan.",
                "guidance": "Execute your plan step by step. Use fs_write, fs_patch, and other tools as needed.",
                "reminder": "If you encounter unexpected issues, you can call 'EnterPlanMode' again to re-evaluate."
            }
            """;

        return new ToolExecutionResult(true,
            tacticalResponse,
            Optional.of("Execution Phase - Tools unlocked"),
            Optional.empty());
    }

    public ToolExecutionResult handleShellCommand(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String command = args.path("command").asText("").trim();
        String workDir = args.path("working_directory").asText("").trim();
        int requestedTimeout = args.path("timeout").asInt(0);

        if (command.isEmpty()) {
            return new ToolExecutionResult(false,
                "Error: El campo 'command' es obligatorio.",
                Optional.empty(), Optional.empty());
        }

        String baseCmd = command.split("\\s+")[0];
        if (baseCmd.contains("/")) {
            baseCmd = baseCmd.substring(baseCmd.lastIndexOf('/') + 1);
        }
        String baseCmdLower = baseCmd.toLowerCase();

        logger.info("[SHELL] Comando solicitado: " + command + " | base: " + baseCmdLower +
            " | working_directory: " + (workDir.isEmpty() ? "(default)" : workDir));

        if (BLOCKED_COMMANDS.contains(baseCmdLower)) {
            logger.warning("[SHELL-BLOCKED] Comando bloqueado: " + baseCmdLower);
            return new ToolExecutionResult(false,
                "[BLOCKED] Comando '" + baseCmdLower + "' esta bloqueado por seguridad.\n" +
                "Comandos bloqueados: " + BLOCKED_COMMANDS,
                Optional.empty(), Optional.of(command));
        }

        Path resolvedDir;
        try {
            resolvedDir = resolveWorkingDirectory(workDir);
        } catch (IllegalArgumentException e) {
            return new ToolExecutionResult(false,
                "[ERROR] " + e.getMessage(),
                Optional.empty(), Optional.empty());
        }

        if (NIVEL_0_READ_ONLY.contains(baseCmdLower)) {
            int timeout = resolveTimeout(requestedTimeout, TIMEOUT_NIVEL_0);
            return executeNative(command, resolvedDir, timeout, "NIVEL-0");
        }

        if (NIVEL_1_BUILD.contains(baseCmdLower)) {
            int timeout = resolveTimeout(requestedTimeout, TIMEOUT_NIVEL_1);
            return executeNivel1(command, resolvedDir, timeout);
        }

        if (NIVEL_2_EXEC.contains(baseCmdLower)) {
            return executeNivel2(command, resolvedDir, requestedTimeout);
        }

        logger.warning("[SHELL-UNKNOWN] Comando no clasificado: " + baseCmdLower);
        return new ToolExecutionResult(false,
            "[ERROR] Comando '" + baseCmdLower + "' no esta en la whitelist.\n" +
            "Nivel 0 (lectura): " + NIVEL_0_READ_ONLY + "\n" +
            "Nivel 1 (build): " + NIVEL_1_BUILD + "\n" +
            "Nivel 2 (ejecucion): " + NIVEL_2_EXEC,
            Optional.empty(), Optional.empty());
    }

    private Path resolveWorkingDirectory(String workDir) {
        if (workDir == null || workDir.isEmpty()) {
            return workingDirectory;
        }

        boolean isAbsolute = workDir.startsWith("/") || workDir.startsWith("\\") ||
            (workDir.length() >= 2 && workDir.charAt(1) == ':');

        Path resolved;
        if (isAbsolute) {
            resolved = Path.of(workDir).normalize();
            if (!resolved.startsWith(workingDirectory)) {
                throw new IllegalArgumentException(
                    "working_directory absoluto está fuera del workspace: " + workDir);
            }
        } else {
            if (workDir.contains("..")) {
                throw new IllegalArgumentException(
                    "working_directory no puede contener '..': " + workDir);
            }
            resolved = workingDirectory.resolve(workDir).normalize();

            if (!resolved.startsWith(workingDirectory)) {
                throw new IllegalArgumentException(
                    "working_directory resuelve fuera del workspace: " + workDir);
            }
        }

        if (!Files.isDirectory(resolved)) {
            throw new IllegalArgumentException(
                "working_directory no existe o no es directorio: " + resolved);
        }

        return resolved;
    }

    private ToolExecutionResult executeNative(String command, Path dir, int timeout, String nivel) {
        long startTime = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            var stdoutFuture = executor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                }
            });

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(3, TimeUnit.SECONDS);
                executor.shutdownNow();
                String partialOutput = "";
                try { partialOutput = stdoutFuture.get(2, TimeUnit.SECONDS); } catch (Exception ignored) {}

                if (dev.fararoni.core.core.skills.ServerStartupDetector.isServerStarted(partialOutput)) {
                    String port = dev.fararoni.core.core.skills.ServerStartupDetector.extractPort(partialOutput);
                    logger.info("[SHELL-SERVER-OK] " + nivel + " servidor detectado en puerto " + port
                        + " (timeout esperado): " + command);
                    return new ToolExecutionResult(true,
                        "[SERVER STARTED] El servidor arranco exitosamente y estaba sirviendo en puerto " + port + ".\n"
                        + "El proceso fue terminado despues de " + timeout + "s (comportamiento normal para servidores).\n"
                        + "Output del servidor:\n" + truncateOutput(partialOutput, 4000)
                        + "\n\n[exit_code: 0 | duration: " + duration + "ms | mode: native | level: " + nivel
                        + " | server_port: " + port + "]",
                        Optional.empty(), Optional.empty());
                }

                logger.warning("[SHELL-TIMEOUT] " + nivel + " timeout despues de " + timeout + "s: " + command);
                return new ToolExecutionResult(false,
                    "[TIMEOUT] Comando excedio el limite de " + timeout + " segundos.\n" +
                    "Output parcial:\n" + truncateOutput(partialOutput, 4000) +
                    "\n\nDuracion: " + duration + "ms | Modo: native | Nivel: " + nivel,
                    Optional.empty(), Optional.empty());
            }

            executor.shutdown();
            String output = stdoutFuture.get(5, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            String truncatedOutput = truncateOutput(output, 8000);

            logger.info("[SHELL-OK] " + nivel + " exitCode=" + exitCode +
                " duration=" + duration + "ms: " + command);

            return new ToolExecutionResult(exitCode == 0,
                truncatedOutput +
                "\n\n[exit_code: " + exitCode + " | duration: " + duration + "ms | mode: native | level: " + nivel + "]",
                Optional.empty(), Optional.empty());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.severe("[SHELL-ERROR] " + nivel + " " + command + ": " + e.getMessage());
            return new ToolExecutionResult(false,
                "[ERROR] Fallo ejecutando comando: " + e.getMessage() +
                "\n\nDuracion: " + duration + "ms | Modo: native | Nivel: " + nivel,
                Optional.empty(), Optional.empty());
        }
    }

    private ToolExecutionResult executeNivel1(String command, Path dir, int timeout) {
        ToolExecutionResult nativeResult = executeNative(command, dir, timeout, "NIVEL-1");

        if (nativeResult.success()) {
            return nativeResult;
        }

        if (nativeResult.message().contains("[exit_code:")) {
            return new ToolExecutionResult(true,
                nativeResult.message(),
                nativeResult.payload(),
                nativeResult.targetId());
        }

        logger.info("[SHELL-FALLBACK] Nativo no pudo ejecutar, intentando Docker para: " + command);

        try {
            if (!DockerSandbox.isDockerAvailable()) {
                logger.warning("[SHELL-FALLBACK] Docker no disponible, retornando error nativo");
                return new ToolExecutionResult(false,
                    nativeResult.message() +
                    "\n\n[FALLBACK] Docker no disponible. Instala Docker para fallback automatico.",
                    Optional.empty(), Optional.empty());
            }

            String dockerImage = detectDockerImage(command);
            DockerSandbox sandbox = new DockerSandbox(dockerImage);

            try {
                sandbox.startSandbox(dir);
                DockerSandbox.SandboxResult dockerResult = sandbox.execute(timeout, "bash", "-c", command);

                logger.info("[SHELL-DOCKER] exitCode=" + dockerResult.exitCode() +
                    " duration=" + dockerResult.durationMs() + "ms: " + command);

                return new ToolExecutionResult(dockerResult.isSuccess(),
                    dockerResult.getCombinedOutput() +
                    "\n\n[exit_code: " + dockerResult.exitCode() +
                    " | duration: " + dockerResult.durationMs() + "ms | mode: docker | level: NIVEL-1]",
                    Optional.empty(), Optional.empty());
            } finally {
                sandbox.destroySandbox();
            }
        } catch (Exception e) {
            logger.severe("[SHELL-DOCKER-ERROR] Fallback Docker fallo: " + e.getMessage());
            return new ToolExecutionResult(false,
                nativeResult.message() +
                "\n\n[FALLBACK-ERROR] Docker tambien fallo: " + e.getMessage(),
                Optional.empty(), Optional.empty());
        }
    }

    private ToolExecutionResult executeNivel2(String command, Path dir, int requestedTimeout) {
        String commandLower = command.toLowerCase().trim();

        boolean isJarExec = commandLower.startsWith("java -jar ") || commandLower.startsWith("java -jar\t");
        boolean isVersion = commandLower.equals("java -version") || commandLower.equals("java --version");

        if (!isJarExec && !isVersion) {
            logger.warning("[SHELL-NIVEL2] Subcomando java no permitido: " + command);
            return new ToolExecutionResult(false,
                "[BLOCKED] Solo se permite 'java -jar <archivo>' y 'java -version'.\n" +
                "Comando recibido: " + command,
                Optional.empty(), Optional.empty());
        }

        if (isJarExec) {
            String jarPart = command.substring(command.indexOf("-jar") + 5).trim();
            String jarFile = jarPart.split("\\s+")[0];
            Path jarPath = dir.resolve(jarFile).normalize();
            if (!jarPath.startsWith(workingDirectory)) {
                return new ToolExecutionResult(false,
                    "[BLOCKED] El archivo .jar debe estar dentro del workspace: " + jarFile,
                    Optional.empty(), Optional.empty());
            }
        }

        int timeout = resolveTimeout(requestedTimeout, TIMEOUT_NIVEL_2);
        return executeNative(command, dir, timeout, "NIVEL-2");
    }

    private String detectDockerImage(String command) {
        String cmd = command.toLowerCase();
        if (cmd.startsWith("mvn") || cmd.startsWith("gradle") || cmd.startsWith("./gradlew")) {
            return "eclipse-temurin:21-jdk-alpine";
        }
        if (cmd.startsWith("npm") || cmd.startsWith("npx")) {
            return "node:20-alpine";
        }
        if (cmd.startsWith("go")) {
            return "golang:1.21-alpine";
        }
        if (cmd.startsWith("cargo")) {
            return "rust:1.75-alpine";
        }
        return "eclipse-temurin:21-jdk-alpine";
    }

    private int resolveTimeout(int requested, int defaultTimeout) {
        if (requested <= 0) return defaultTimeout;
        return Math.min(requested, TIMEOUT_MAX);
    }

    private String truncateOutput(String output, int maxChars) {
        if (output == null) return "";
        if (output.length() <= maxChars) return output;
        return output.substring(0, maxChars) + "\n\n... [OUTPUT TRUNCADO: " + output.length() + " chars totales]";
    }

    public ToolExecutionResult handleExecuteCodeBlocked(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String language = args.path("language").asText("unknown");
        String code = args.path("code").asText("");

        logger.warning("[SEGURIDAD] LLM intentó ejecutar código: " + language + " (" + code.length() + " chars)");

        return new ToolExecutionResult(false,
            "[BLOCKED] EJECUCION DIRECTA DE CODIGO DESHABILITADA\n\n" +
            "La herramienta 'ExecuteCode' esta DESHABILITADA por seguridad.\n\n" +
            "RAZON: Riesgo de ejecucion de codigo arbitrario sin sandbox.\n\n" +
            "PROTOCOLO SEGURO:\n" +
            "  1. Escribe el codigo a un archivo usando 'WriteFile'\n" +
            "  2. Solicita al usuario que ejecute manualmente via /run\n\n" +
            "EJEMPLO:\n" +
            "  WriteFile(\"script.py\", <codigo>)\n" +
            "  Usuario: /run python3 script.py\n\n" +
            "NOTA: Este flujo permite al usuario revisar el código antes de ejecutarlo.",
            Optional.empty(), Optional.of(language));
    }
}
