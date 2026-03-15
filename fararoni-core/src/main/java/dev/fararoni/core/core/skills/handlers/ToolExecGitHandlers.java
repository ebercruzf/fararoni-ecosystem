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
import dev.fararoni.core.core.skills.ToolExecutionResult;
import dev.fararoni.core.core.workspace.GitManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ToolExecGitHandlers {
    private static final Logger logger = Logger.getLogger(ToolExecGitHandlers.class.getName());

    private final ObjectMapper mapper;
    private final GitManager gitManager;
    private final Path workingDirectory;

    public enum GitRiskLevel { READ_ONLY, LOCAL_WRITE, FINALIZE, DISCARD, REMOTE, DANGEROUS }

    public ToolExecGitHandlers(ObjectMapper mapper, GitManager gitManager, Path workingDirectory) {
        this.mapper = mapper;
        this.gitManager = gitManager;
        this.workingDirectory = workingDirectory;
    }

    public GitRiskLevel classifyRisk(String action, String params) {
        return switch (action.toLowerCase()) {
            case "status", "log", "diff", "show" -> GitRiskLevel.READ_ONLY;
            case "add", "commit", "checkout", "stash", "init" -> GitRiskLevel.LOCAL_WRITE;
            case "branch" -> {
                if (params == null || params.isBlank() || params.equals("-a") || params.equals("--all") || params.equals("-l"))
                    yield GitRiskLevel.READ_ONLY;
                yield GitRiskLevel.LOCAL_WRITE;
            }
            case "finalize" -> GitRiskLevel.FINALIZE;
            case "discard" -> GitRiskLevel.DISCARD;
            case "push", "pull", "fetch", "clone" -> GitRiskLevel.REMOTE;
            default -> {
                if (isDangerousGitAction(action, params)) yield GitRiskLevel.DANGEROUS;
                yield GitRiskLevel.LOCAL_WRITE;
            }
        };
    }

    public boolean isRemoteGitAction(String action, String params) {
        return switch (action.toLowerCase()) {
            case "push", "pull", "fetch", "clone" -> true;
            default -> false;
        };
    }

    public boolean isDangerousGitAction(String action, String params) {
        if (params == null) return false;
        String p = params.toLowerCase();
        if (action.equalsIgnoreCase("reset") && p.contains("--hard")) return true;
        if (action.equalsIgnoreCase("clean") && p.contains("-f")) return true;
        return false;
    }

    public ToolExecutionResult handleGitAction(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String action = args.path("action").asText("").trim();
        String params = args.path("params").asText("").trim();

        if (action.isEmpty()) {
            return new ToolExecutionResult(false, "Error: 'action' es requerido.", Optional.empty(), Optional.empty());
        }

        GitRiskLevel risk = classifyRisk(action, params);

        if (risk == GitRiskLevel.REMOTE) {
            logger.warning("[ANILLO7] BLOCKED remote git: " + action);
            return new ToolExecutionResult(false,
                "[BLOCKED] Operacion remota '" + action + "' esta BLOQUEADA por seguridad.\n" +
                "Solo operaciones locales estan permitidas. El usuario puede ejecutar manualmente:\n  /run git " + action + " " + params,
                Optional.empty(), Optional.of(action));
        }

        if (risk == GitRiskLevel.DANGEROUS) {
            logger.warning("[ANILLO7] BLOCKED dangerous git: " + action + " " + params);
            return new ToolExecutionResult(false,
                "[BLOCKED] Operacion destructiva '" + action + " " + params + "' esta BLOQUEADA.\n" +
                "reset --hard y clean -f pueden causar perdida irreversible de datos.",
                Optional.empty(), Optional.of(action));
        }

        if (workingDirectory == null) {
            return new ToolExecutionResult(false, "Error: No hay directorio de trabajo configurado.", Optional.empty(), Optional.empty());
        }

        if (action.equalsIgnoreCase("init")) {
            return executeGitInit();
        }

        if (!Files.exists(workingDirectory.resolve(".git"))) {
            logger.info("[GITACTION] .git no existe en " + workingDirectory + ", auto-inicializando");
            var initResult = executeGitInit();
            if (!initResult.success()) {
                return new ToolExecutionResult(false,
                    "Error al auto-inicializar git: " + initResult.message(),
                    Optional.empty(), Optional.empty());
            }
            logger.info("[GITACTION] Auto-init exitoso: " + initResult.message());
        }

        if (risk == GitRiskLevel.FINALIZE) {
            return executeGitFinalize(params);
        }

        if (risk == GitRiskLevel.DISCARD) {
            return executeGitDiscard();
        }

        if (risk == GitRiskLevel.LOCAL_WRITE) {
            ensureEphemeralBranch();
        }

        return switch (action.toLowerCase()) {
            case "status", "diff", "show" -> executeGitReadOnly(action, params);
            case "log" -> executeGitLog(params);
            case "add" -> executeGitAdd(params);
            case "commit" -> executeGitCommit(params);
            case "checkout" -> executeGitCheckout(params);
            case "branch" -> executeGitBranch(params);
            case "stash" -> executeGitStash(params);
            default -> executeGitReadOnly(action, params);
        };
    }

    public void ensureEphemeralBranch() {
        if (gitManager == null) return;
        if (gitManager.hasActiveEphemeralBranch()) return;
        if (!gitManager.isGitRepository()) return;

        var state = gitManager.validateCleanState();
        if (state.issue() == GitManager.CleanStateIssue.MERGE_IN_PROGRESS ||
            state.issue() == GitManager.CleanStateIssue.REBASE_IN_PROGRESS) {
            logger.warning("[ANILLO2] Merge/rebase en progreso, fallback a snapshot");
            gitManager.createSnapshot();
            return;
        }

        String taskId = String.valueOf(System.currentTimeMillis());
        String branch = gitManager.startEphemeralBranch(taskId);
        if (branch == null) {
            logger.warning("[ANILLO2] Fallo al crear rama efímera, fallback a snapshot");
            gitManager.createSnapshot();
        } else {
            logger.info("[ANILLO2] Rama efímera activa: " + branch);
        }
    }

    private ToolExecutionResult executeGitReadOnly(String subcommand, String params) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add(subcommand);
        if (params != null && !params.isBlank()) {
            cmd.addAll(parseGitArgs(params));
        }
        return executeGitCommand(cmd);
    }

    private ToolExecutionResult executeGitLog(String params) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add("log");
        if (params == null || params.isBlank()) {
            cmd.add("--oneline"); cmd.add("-10");
        } else {
            cmd.addAll(parseGitArgs(params));
        }
        return executeGitCommand(cmd);
    }

    private ToolExecutionResult executeGitAdd(String params) {
        if (params == null || params.isBlank()) {
            return new ToolExecutionResult(false, "Error: especifica archivos para agregar. Ejemplo: params='.' o params='archivo.java'",
                Optional.empty(), Optional.empty());
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add("add");

        String trimParams = params.trim();
        if (".".equals(trimParams) || "--all".equals(trimParams) || "-A".equals(trimParams)) {
            cmd.add("--all");
            cmd.add("--");
            cmd.add(".");
            cmd.add(":!.fararoni/");
        } else {
            cmd.addAll(parseGitArgs(params));
        }
        return executeGitCommand(cmd);
    }

    private ToolExecutionResult executeGitCommit(String params) {
        if (params == null || params.isBlank() || !params.contains("-m")) {
            return new ToolExecutionResult(false, "Error: especifica un mensaje. Ejemplo: params='-m feat(auth): add JWT filters'",
                Optional.empty(), Optional.empty());
        }

        ensureGitIdentity();

        List<String> addCmd = new ArrayList<>();
        addCmd.add("git"); addCmd.add("add"); addCmd.add(".");
        executeGitCommand(addCmd);

        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add("commit");
        cmd.addAll(parseGitArgs(params));
        ToolExecutionResult commitResult = executeGitCommand(cmd);

        if (commitResult.success() && gitManager != null && gitManager.hasActiveEphemeralBranch()) {
            String reminder = commitResult.message() +
                "\n\n[SYSTEM] Commit registrado en rama efimera. " +
                "Para consolidar los cambios en la rama principal, ejecuta: " +
                "GitAction action=finalize params='descripcion del cambio'. " +
                "Sin finalize, los cambios quedan en una rama temporal.";
            return new ToolExecutionResult(true, reminder, commitResult.payload(), commitResult.targetId());
        }

        return commitResult;
    }

    private void ensureGitIdentity() {
        try {
            ProcessBuilder check = new ProcessBuilder("git", "config", "--local", "user.name");
            check.directory(workingDirectory.toFile());
            Process p = check.start();
            boolean hasIdentity = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            if (hasIdentity) return;

            String userName = System.getProperty("user.name", "developer");
            String safeName = userName.replaceAll("[^a-zA-Z0-9 ._-]", "") + " (Fararoni Core)";
            String safeEmail = userName.toLowerCase().replaceAll("[^a-z0-9]", ".") + "@fararoni.local";

            new ProcessBuilder("git", "config", "--local", "user.name", safeName)
                .directory(workingDirectory.toFile()).start().waitFor(5, TimeUnit.SECONDS);
            new ProcessBuilder("git", "config", "--local", "user.email", safeEmail)
                .directory(workingDirectory.toFile()).start().waitFor(5, TimeUnit.SECONDS);

            logger.info("[GITACTION] Identidad tactica configurada: " + safeName);
        } catch (IOException | InterruptedException e) {
            logger.warning("[GITACTION] No se pudo configurar identidad Git: " + e.getMessage());
        }
    }

    private ToolExecutionResult executeGitCheckout(String params) {
        if (params == null || params.isBlank()) {
            return new ToolExecutionResult(false, "Error: especifica rama o archivo.",
                Optional.empty(), Optional.empty());
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add("checkout");
        cmd.addAll(parseGitArgs(params));
        return executeGitCommand(cmd);
    }

    private ToolExecutionResult executeGitBranch(String params) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add("branch");
        if (params != null && !params.isBlank()) {
            cmd.addAll(parseGitArgs(params));
        }
        ToolExecutionResult result = executeGitCommand(cmd);

        if (result.success() && (result.message() == null || result.message().isBlank()
                || "(sin salida)".equals(result.message().trim()))) {
            String branchName = (params != null && !params.isBlank()) ? params.trim().split("\\s+")[0] : "?";
            if (params == null || params.isBlank() || params.trim().startsWith("-")) {
                return result;
            }
            return new ToolExecutionResult(true,
                "Rama '" + branchName + "' creada exitosamente. Usa 'checkout " + branchName + "' para cambiar a ella.",
                result.payload(), result.targetId());
        }

        return result;
    }

    private ToolExecutionResult executeGitStash(String params) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add("stash");
        if (params != null && !params.isBlank()) {
            cmd.addAll(parseGitArgs(params));
        }
        return executeGitCommand(cmd);
    }

    private ToolExecutionResult executeGitInit() {
        if (Files.exists(workingDirectory.resolve(".git"))) {
            List<String> cmd = List.of("git", "branch", "--show-current");
            var result = executeGitCommand(cmd);
            return new ToolExecutionResult(true,
                "Ya es un repositorio Git. Rama actual: " + result.message().trim(),
                Optional.empty(), Optional.of("init"));
        }
        var result = executeGitCommand(List.of("git", "init"));
        if (result.success()) {
            autoCreateGitignore();
        }
        return result;
    }

    private void autoCreateGitignore() {
        Path gitignore = workingDirectory.resolve(".gitignore");
        String fararoniEntry = ".fararoni/";
        try {
            if (!Files.exists(gitignore)) {
                Files.writeString(gitignore, """
                    # Fararoni System Files
                    .fararoni/

                    # Java/Maven
                    target/
                    *.class
                    *.jar
                    *.log

                    # IDE
                    .idea/
                    *.iml
                    .vscode/
                    .settings/
                    .project
                    .classpath
                    """);
                logger.info("[GIT-INIT] .gitignore creado automaticamente");
            } else {
                String content = Files.readString(gitignore);
                if (!content.contains(fararoniEntry)) {
                    Files.writeString(gitignore, content + "\n# Fararoni System Files\n.fararoni/\n",
                        java.nio.file.StandardOpenOption.APPEND);
                    logger.info("[GIT-INIT] .fararoni/ agregado a .gitignore existente");
                }
            }
        } catch (Exception e) {
            logger.warning("[GIT-INIT] Error creando .gitignore: " + e.getMessage());
        }
    }

    private ToolExecutionResult executeGitFinalize(String params) {
        if (gitManager == null) {
            return new ToolExecutionResult(false, "Error: GitManager no disponible.", Optional.empty(), Optional.empty());
        }
        if (!gitManager.hasActiveEphemeralBranch()) {
            return new ToolExecutionResult(false,
                "No hay rama efimera activa para finalizar. Usa primero acciones de escritura (add, commit).",
                Optional.empty(), Optional.empty());
        }
        String message = (params == null || params.isBlank()) ? "cambios consolidados" : params;
        GitManager.EphemeralResult result = gitManager.finalizeEphemeralBranch(message);
        return new ToolExecutionResult(result.success(), result.message(), Optional.empty(), Optional.of("finalize"));
    }

    private ToolExecutionResult executeGitDiscard() {
        if (gitManager == null) {
            return new ToolExecutionResult(false, "Error: GitManager no disponible.", Optional.empty(), Optional.empty());
        }
        if (!gitManager.hasActiveEphemeralBranch()) {
            return new ToolExecutionResult(false,
                "No hay rama efimera activa para descartar.",
                Optional.empty(), Optional.empty());
        }
        boolean ok = gitManager.discardEphemeralBranch();
        return new ToolExecutionResult(ok,
            ok ? "Rama efimera descartada. Restaurada rama original." : "Error descartando rama efimera.",
            Optional.empty(), Optional.of("discard"));
    }

    private ToolExecutionResult executeGitCommand(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(workingDirectory.toFile());

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            int lineCount = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    if (lineCount <= 100) {
                        output.append(line).append("\n");
                    }
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ToolExecutionResult(false, "Timeout: comando git excedio 30 segundos.",
                    Optional.empty(), Optional.empty());
            }

            if (lineCount > 100) {
                output.append("\n... (").append(lineCount - 100).append(" lineas mas truncadas)");
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (result.isEmpty()) result = "(sin salida)";

            return new ToolExecutionResult(exitCode == 0, result, Optional.empty(), Optional.of(command.get(1)));
        } catch (java.io.IOException e) {
            return new ToolExecutionResult(false, "Error ejecutando git: " + e.getMessage() +
                "\nVerifica que git esta instalado.", Optional.empty(), Optional.empty());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolExecutionResult(false, "Comando git interrumpido.", Optional.empty(), Optional.empty());
        }
    }

    private List<String> parseGitArgs(String params) {
        List<String> args = new ArrayList<>();
        if (params == null || params.isBlank()) return args;
        StringBuilder current = new StringBuilder();
        boolean inSingle = false, inDouble = false;
        for (char c : params.toCharArray()) {
            if (c == '\'' && !inDouble) { inSingle = !inSingle; continue; }
            if (c == '"' && !inSingle) { inDouble = !inDouble; continue; }
            if (c == ' ' && !inSingle && !inDouble) {
                if (current.length() > 0) { args.add(current.toString()); current.setLength(0); }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) args.add(current.toString());
        return args;
    }
}
