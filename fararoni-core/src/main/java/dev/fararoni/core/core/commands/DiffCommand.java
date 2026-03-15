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
public class DiffCommand implements ConsoleCommand {
    private static final int GIT_TIMEOUT_SECONDS = 30;
    private static final int MAX_DIFF_LINES = 500;

    @Override
    public String getTrigger() {
        return "/diff";
    }

    @Override
    public String getDescription() {
        return "Muestra cambios no commiteados (git diff)";
    }

    @Override
    public String getUsage() {
        return "/diff [archivo] [--staged] [--stat]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.GIT;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/changes", "/delta" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /diff - Muestra cambios no commiteados

            Uso:
              /diff                  Muestra todos los cambios no staged
              /diff <archivo>        Muestra cambios de un archivo especifico
              /diff --staged         Muestra cambios en staging area
              /diff --stat           Muestra estadisticas resumidas
              /diff <archivo> --stat Estadisticas de archivo especifico

            Ejemplos:
              /diff                      # Ver todos los cambios
              /diff src/Main.java        # Cambios en Main.java
              /diff --staged             # Solo cambios staged
              /diff --stat               # Resumen de cambios

            Equivalentes Git:
              /diff           = git diff
              /diff file      = git diff file
              /diff --staged  = git diff --staged
              /diff --stat    = git diff --stat

            Notas:
              - Requiere estar en un repositorio git
              - Los cambios se muestran con formato coloreado
              - El output se trunca si excede 500 lineas
              - Use /commit para commitear los cambios

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

        boolean stagedOnly = false;
        boolean statOnly = false;
        String targetFile = null;

        if (args != null && !args.isBlank()) {
            String[] parts = args.trim().split("\\s+");
            for (String part : parts) {
                if (part.equals("--staged") || part.equals("-s")) {
                    stagedOnly = true;
                } else if (part.equals("--stat")) {
                    statOnly = true;
                } else if (targetFile == null && !part.startsWith("-")) {
                    targetFile = part;
                }
            }
        }

        String status = executeGit(workDir, "status", "--porcelain");
        if (status.isEmpty()) {
            ctx.printWarning("No hay cambios para mostrar");
            ctx.print("El directorio de trabajo esta limpio");
            return;
        }

        List<String> diffArgs = new ArrayList<>();
        diffArgs.add("diff");

        if (stagedOnly) {
            diffArgs.add("--staged");
        }

        if (statOnly) {
            diffArgs.add("--stat");
        }

        if (targetFile != null) {
            diffArgs.add("--");
            diffArgs.add(targetFile);
        }

        String diff = executeGit(workDir, diffArgs.toArray(new String[0]));

        if (diff.isEmpty()) {
            if (stagedOnly) {
                ctx.printWarning("No hay cambios staged");
                ctx.print("Use 'git add' para agregar archivos al staging");
            } else {
                ctx.printWarning("No hay cambios en archivos tracked");
                ctx.print("Use /diff --staged para ver cambios staged");
            }
            return;
        }

        String[] lines = diff.split("\n");
        boolean truncated = false;
        if (lines.length > MAX_DIFF_LINES) {
            StringBuilder truncatedDiff = new StringBuilder();
            for (int i = 0; i < MAX_DIFF_LINES; i++) {
                truncatedDiff.append(lines[i]).append("\n");
            }
            diff = truncatedDiff.toString();
            truncated = true;
        }

        String header = stagedOnly ? "Cambios staged" : "Cambios no commiteados";
        if (targetFile != null) {
            header += " en " + targetFile;
        }

        ctx.printSuccess("OK - " + header);
        ctx.print("");

        for (String line : diff.split("\n")) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                ctx.print("  " + line);
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                ctx.print("  " + line);
            } else if (line.startsWith("@@")) {
                ctx.print("  " + line);
            } else {
                ctx.print("  " + line);
            }
        }

        if (truncated) {
            ctx.printWarning("... output truncado (" + (lines.length - MAX_DIFF_LINES) + " lineas mas)");
        }

        int additions = 0, deletions = 0;
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) additions++;
            if (line.startsWith("-") && !line.startsWith("---")) deletions++;
        }

        ctx.print("");
        ctx.print(String.format("  %d lineas agregadas, %d lineas eliminadas", additions, deletions));

        ctx.printDebug("Branch: " + ctx.getCurrentBranch().orElse("unknown"));
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
