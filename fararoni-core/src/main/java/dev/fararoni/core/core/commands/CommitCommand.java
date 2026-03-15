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
package dev.fararoni.core.core.commands;

import dev.fararoni.bus.agent.api.command.CommandCategory;
import dev.fararoni.bus.agent.api.command.ConsoleCommand;
import dev.fararoni.bus.agent.api.command.ExecutionContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class CommitCommand implements ConsoleCommand {
    private static final int GIT_TIMEOUT_SECONDS = 30;
    private static final String COMMIT_PREFIX = "[FARARONI]";

    @Override
    public String getTrigger() {
        return "/commit";
    }

    @Override
    public String getDescription() {
        return "Hace commit de los cambios actuales";
    }

    @Override
    public String getUsage() {
        return "/commit [mensaje] [--all]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.GIT;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/save", "/ci" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /commit - Hace commit de los cambios

            Uso:
              /commit                    Stage y commit cambios con mensaje auto
              /commit <mensaje>          Commit con mensaje personalizado
              /commit --all              Stage todos y commit (git commit -a)
              /commit "fix: bug" --all   Mensaje + stage all

            Formato de mensaje:
              Sigue Conventional Commits:
              - feat: nueva funcionalidad
              - fix: correccion de bug
              - refactor: refactorizacion
              - docs: documentacion
              - test: tests
              - chore: mantenimiento

            Ejemplos:
              /commit
              /commit "feat: add user login"
              /commit "fix(auth): validate tokens" --all

            Notas:
              - Sin mensaje, genera uno basico
              - Agrega prefijo [FARARONI] automaticamente
              - Use /diff para ver cambios antes de commit
              - Use /undo para revertir si es necesario

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        if (!ctx.isGitRepository()) {
            ctx.printError("No es un repositorio git");
            ctx.print("Inicializa git con: git init");
            return;
        }

        Path workDir = ctx.getWorkingDirectory();

        boolean stageAll = false;
        String message = null;

        if (args != null && !args.isBlank()) {
            String trimmed = args.trim();

            if (trimmed.contains("--all") || trimmed.contains("-a")) {
                stageAll = true;
                trimmed = trimmed.replace("--all", "").replace("-a", "").trim();
            }

            if (!trimmed.isEmpty()) {
                if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                    (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                    trimmed = trimmed.substring(1, trimmed.length() - 1);
                }
                message = trimmed;
            }
        }

        String status = executeGit(workDir, "status", "--porcelain");
        if (status.isEmpty()) {
            ctx.printWarning("No hay cambios para commitear");
            return;
        }

        ctx.print("Cambios a commitear:");
        List<String> modifiedFiles = new ArrayList<>();
        for (String line : status.split("\n")) {
            if (!line.isBlank()) {
                ctx.print("  " + line.trim());
                String filename = line.substring(3).trim();
                modifiedFiles.add(filename);
            }
        }

        if (stageAll) {
            ctx.print("Staging todos los archivos...");
            executeGit(workDir, "add", "-A");
        } else {
            ctx.print("Staging archivos modificados...");
            executeGit(workDir, "add", "-u");
        }

        if (message == null || message.isEmpty()) {
            message = generateCommitMessage(modifiedFiles);
        }

        String fullMessage = COMMIT_PREFIX + " " + message;

        try {
            String result = executeGit(workDir, "commit", "-m", fullMessage);

            if (result.contains("nothing to commit")) {
                ctx.printWarning("Nada que commitear (archivos sin cambios en stage)");
                ctx.print("Use --all para incluir archivos no staged");
                return;
            }

            String hash = executeGit(workDir, "rev-parse", "--short", "HEAD");

            ctx.printSuccess(String.format(
                "OK - Commit %s creado",
                hash.isEmpty() ? "" : "[" + hash + "]"
            ));
            ctx.print("  Mensaje: " + fullMessage);
            ctx.print("  Archivos: " + modifiedFiles.size());

            ctx.printDebug("Branch: " + ctx.getCurrentBranch().orElse("unknown"));
        } catch (Exception e) {
            ctx.printError("Error ejecutando commit: " + e.getMessage());
            ctx.printDebug("Causa: " + e.getClass().getSimpleName());
        }
    }

    private String generateCommitMessage(List<String> files) {
        if (files.isEmpty()) {
            return "chore: update files";
        }

        boolean hasTests = files.stream().anyMatch(f ->
            f.contains("test") || f.contains("Test") || f.contains("spec"));
        boolean hasDocs = files.stream().anyMatch(f ->
            f.endsWith(".md") || f.endsWith(".txt") || f.contains("doc"));
        boolean hasConfig = files.stream().anyMatch(f ->
            f.endsWith(".json") || f.endsWith(".yaml") || f.endsWith(".yml") ||
            f.endsWith(".xml") || f.endsWith(".properties"));

        String prefix;
        if (hasTests && files.size() == 1) {
            prefix = "test";
        } else if (hasDocs && files.size() == 1) {
            prefix = "docs";
        } else if (hasConfig) {
            prefix = "chore";
        } else {
            prefix = "feat";
        }

        if (files.size() == 1) {
            String filename = files.get(0);
            int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
            if (lastSlash >= 0) {
                filename = filename.substring(lastSlash + 1);
            }
            return prefix + ": update " + filename;
        } else {
            return prefix + ": update " + files.size() + " files";
        }
    }

    private String executeGit(Path workDir, String... args) {
        try {
            var command = new ArrayList<String>();
            command.add("git");
            command.addAll(List.of(args));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return output.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }
}
