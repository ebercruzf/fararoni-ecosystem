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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class UndoCommand implements ConsoleCommand {
    private static final int GIT_TIMEOUT_SECONDS = 30;

    @Override
    public String getTrigger() {
        return "/undo";
    }

    @Override
    public String getDescription() {
        return "Revierte cambios no commiteados (git checkout/restore)";
    }

    @Override
    public String getUsage() {
        return "/undo [archivo] [--hard]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.GIT;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/revert", "/rollback" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /undo - Revierte cambios no commiteados

            Uso:
              /undo                  Revierte todos los cambios no staged
              /undo <archivo>        Revierte solo el archivo especificado
              /undo --hard           Revierte TODO incluyendo staged
              /undo <archivo> --hard Revierte archivo incluyendo staged

            Ejemplos:
              /undo                      # Descarta todos los cambios locales
              /undo src/Main.java        # Descarta cambios en Main.java
              /undo --hard               # Reset completo (staged + unstaged)

            Equivalentes Git:
              /undo           = git checkout -- .
              /undo file      = git checkout -- file
              /undo --hard    = git reset --hard HEAD

            Advertencia:
              - Esta operacion NO se puede deshacer
              - Los cambios revertidos se pierden permanentemente
              - Use con precaucion en archivos importantes

            Notas:
              - Requiere estar en un repositorio git
              - No afecta archivos nuevos (untracked)
              - Para descartar untracked: git clean -fd

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

        boolean hardMode = false;
        String targetFile = null;

        if (args != null && !args.isBlank()) {
            String[] parts = args.trim().split("\\s+");
            for (String part : parts) {
                if (part.equals("--hard") || part.equals("-h")) {
                    hardMode = true;
                } else if (targetFile == null) {
                    targetFile = part;
                }
            }
        }

        String status = executeGit(workDir, "status", "--porcelain");
        if (status.isEmpty()) {
            ctx.printWarning("No hay cambios para revertir");
            return;
        }

        ctx.print("Cambios detectados:");
        for (String line : status.split("\n")) {
            if (!line.isBlank()) {
                ctx.print("  " + line.trim());
            }
        }

        try {
            if (hardMode) {
                if (targetFile != null) {
                    executeGit(workDir, "checkout", "HEAD", "--", targetFile);
                    ctx.printSuccess("OK - Revertido (hard): " + targetFile);
                } else {
                    executeGit(workDir, "reset", "--hard", "HEAD");
                    ctx.printSuccess("OK - Reset completo realizado");
                }
            } else {
                if (targetFile != null) {
                    Path filePath = workDir.resolve(targetFile);
                    if (!Files.exists(filePath)) {
                        ctx.printError("Archivo no encontrado: " + targetFile);
                        return;
                    }
                    executeGit(workDir, "checkout", "--", targetFile);
                    ctx.printSuccess("OK - Revertido: " + targetFile);
                } else {
                    executeGit(workDir, "checkout", "--", ".");
                    ctx.printSuccess("OK - Cambios no staged revertidos");
                }
            }

            String newStatus = executeGit(workDir, "status", "--short");
            if (!newStatus.isEmpty()) {
                ctx.print("Estado actual:");
                ctx.print(newStatus);
            } else {
                ctx.print("Directorio de trabajo limpio");
            }
        } catch (Exception e) {
            ctx.printError("Error ejecutando undo: " + e.getMessage());
            ctx.printDebug("Causa: " + e.getClass().getSimpleName());
        }
    }

    private String executeGit(Path workDir, String... args) {
        try {
            var command = new java.util.ArrayList<String>();
            command.add("git");
            command.addAll(java.util.List.of(args));

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
