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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class GitSubcommand implements ConsoleCommand {
    private static final int GIT_TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LINES = 100;

    @Override
    public String getTrigger() {
        return "/git";
    }

    @Override
    public String getDescription() {
        return "Ejecuta operaciones git (init, checkout, branch, status, log)";
    }

    @Override
    public String getUsage() {
        return "/git <subcomando> [argumentos]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.GIT;
    }

    @Override
    public String[] getAliases() {
        return new String[] {};
    }

    @Override
    public String getExtendedHelp() {
        return """

            /git - Operaciones git extendidas

            Uso:
              /git init                  Inicializa repositorio git
              /git checkout <branch>     Cambia a un branch
              /git checkout -b <branch>  Crea y cambia a un branch
              /git checkout -- <file>    Restaura archivo a ultimo commit
              /git branch                Lista branches
              /git branch <name>         Crea un branch
              /git branch -d <name>      Elimina un branch
              /git status                Muestra estado detallado
              /git log                   Muestra ultimos commits
              /git log -n <N>            Muestra N commits
              /git stash                 Guarda cambios temporalmente
              /git stash pop             Restaura cambios guardados

            Ejemplos:
              /git init                      # Iniciar repo
              /git checkout main             # Cambiar a main
              /git checkout -b feature/new   # Crear branch
              /git branch                    # Listar branches
              /git status                    # Ver estado
              /git log -n 5                  # Ver 5 commits

            Comandos Relacionados:
              /commit   - Crear commits (con mensaje auto)
              /undo     - Revertir cambios
              /diff     - Ver diferencias

            Notas:
              - Use /commit para commits normales
              - Use /undo para revertir cambios rapido
              - /git provee acceso a operaciones avanzadas

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        Path workDir = ctx.getWorkingDirectory();

        if (args == null || args.isBlank()) {
            showSubcommands(ctx);
            return;
        }

        String[] parts = args.trim().split("\\s+", 2);
        String subcommand = parts[0].toLowerCase();
        String subArgs = parts.length > 1 ? parts[1] : "";

        switch (subcommand) {
            case "init" -> handleInit(workDir, ctx);
            case "checkout", "co" -> handleCheckout(workDir, subArgs, ctx);
            case "branch", "br" -> handleBranch(workDir, subArgs, ctx);
            case "status", "st" -> handleStatus(workDir, ctx);
            case "log" -> handleLog(workDir, subArgs, ctx);
            case "stash" -> handleStash(workDir, subArgs, ctx);
            case "pull" -> handlePull(workDir, ctx);
            case "push" -> handlePush(workDir, ctx);
            default -> {
                ctx.printError("Subcomando no reconocido: " + subcommand);
                showSubcommands(ctx);
            }
        }
    }

    private void showSubcommands(ExecutionContext ctx) {
        ctx.print("Subcomandos disponibles:");
        ctx.print("  init       Inicializa repositorio");
        ctx.print("  checkout   Cambia branch o restaura archivo");
        ctx.print("  branch     Lista o crea branches");
        ctx.print("  status     Muestra estado del repo");
        ctx.print("  log        Muestra historial");
        ctx.print("  stash      Guarda/restaura cambios temporales");
        ctx.print("  pull       Trae cambios del remoto");
        ctx.print("  push       Envia cambios al remoto");
        ctx.print("");
        ctx.print("Uso: /git <subcomando> [args]");
    }

    private void handleInit(Path workDir, ExecutionContext ctx) {
        if (Files.exists(workDir.resolve(".git"))) {
            ctx.printWarning("Ya es un repositorio git");
            ctx.print("Use /git status para ver el estado");
            return;
        }

        GitResult result = executeGit(workDir, "init");

        if (result.success) {
            ctx.printSuccess("OK - Repositorio git inicializado");
            ctx.print("  " + workDir);

            Path gitignore = workDir.resolve(".gitignore");
            if (!Files.exists(gitignore)) {
                ctx.print("  Tip: Use /ign template <tech> para crear .gitignore");
            }
        } else {
            ctx.printError("Error inicializando repo: " + result.error);
        }
    }

    private void handleCheckout(Path workDir, String args, ExecutionContext ctx) {
        if (!isGitRepo(workDir)) {
            ctx.printError("No es un repositorio git");
            return;
        }

        if (args.isBlank()) {
            ctx.printError("Uso: /git checkout <branch|archivo>");
            ctx.print("  /git checkout main         # Cambiar a branch");
            ctx.print("  /git checkout -b new       # Crear branch");
            ctx.print("  /git checkout -- file.txt  # Restaurar archivo");
            return;
        }

        GitResult result;

        if (args.startsWith("-b ")) {
            String branchName = args.substring(3).trim();
            result = executeGit(workDir, "checkout", "-b", branchName);
            if (result.success) {
                ctx.printSuccess("OK - Branch creado y activado: " + branchName);
            } else {
                ctx.printError("Error creando branch: " + result.error);
            }
            return;
        }

        if (args.startsWith("-- ")) {
            String file = args.substring(3).trim();
            result = executeGit(workDir, "checkout", "--", file);
            if (result.success) {
                ctx.printSuccess("OK - Archivo restaurado: " + file);
            } else {
                ctx.printError("Error restaurando archivo: " + result.error);
            }
            return;
        }

        result = executeGit(workDir, "checkout", args.trim());
        if (result.success) {
            ctx.printSuccess("OK - Cambiado a: " + args.trim());
        } else {
            ctx.printError("Error en checkout: " + result.error);
        }
    }

    private void handleBranch(Path workDir, String args, ExecutionContext ctx) {
        if (!isGitRepo(workDir)) {
            ctx.printError("No es un repositorio git");
            return;
        }

        GitResult result;

        if (args.isBlank()) {
            result = executeGit(workDir, "branch", "-a");
            if (result.success) {
                ctx.print("Branches:");
                for (String line : result.output.split("\n")) {
                    if (!line.isBlank()) {
                        ctx.print("  " + line.trim());
                    }
                }
            } else {
                ctx.printError("Error listando branches: " + result.error);
            }
            return;
        }

        if (args.startsWith("-d ") || args.startsWith("-D ")) {
            String branchName = args.substring(3).trim();
            String flag = args.startsWith("-D") ? "-D" : "-d";
            result = executeGit(workDir, "branch", flag, branchName);
            if (result.success) {
                ctx.printSuccess("OK - Branch eliminado: " + branchName);
            } else {
                ctx.printError("Error eliminando branch: " + result.error);
            }
            return;
        }

        result = executeGit(workDir, "branch", args.trim());
        if (result.success) {
            ctx.printSuccess("OK - Branch creado: " + args.trim());
            ctx.print("  Use /git checkout " + args.trim() + " para activarlo");
        } else {
            ctx.printError("Error creando branch: " + result.error);
        }
    }

    private void handleStatus(Path workDir, ExecutionContext ctx) {
        if (!isGitRepo(workDir)) {
            ctx.printError("No es un repositorio git");
            ctx.print("Use /git init para inicializar");
            return;
        }

        GitResult result = executeGit(workDir, "status");

        if (result.success) {
            ctx.print("Estado del repositorio:");
            ctx.print("");
            for (String line : result.output.split("\n")) {
                ctx.print("  " + line);
            }
        } else {
            ctx.printError("Error obteniendo status: " + result.error);
        }
    }

    private void handleLog(Path workDir, String args, ExecutionContext ctx) {
        if (!isGitRepo(workDir)) {
            ctx.printError("No es un repositorio git");
            return;
        }

        int count = 10;

        if (args.contains("-n ")) {
            try {
                String numStr = args.replaceAll(".*-n\\s+(\\d+).*", "$1");
                count = Integer.parseInt(numStr);
                if (count < 1) count = 1;
                if (count > 50) count = 50;
            } catch (NumberFormatException e) {
            }
        }

        GitResult result = executeGit(workDir, "log", "--oneline", "-" + count);

        if (result.success) {
            ctx.print("Ultimos " + count + " commits:");
            ctx.print("");
            for (String line : result.output.split("\n")) {
                if (!line.isBlank()) {
                    ctx.print("  " + line);
                }
            }
        } else {
            ctx.printError("Error obteniendo log: " + result.error);
        }
    }

    private void handleStash(Path workDir, String args, ExecutionContext ctx) {
        if (!isGitRepo(workDir)) {
            ctx.printError("No es un repositorio git");
            return;
        }

        GitResult result;

        if (args.isBlank() || args.equals("push")) {
            result = executeGit(workDir, "stash");
            if (result.success) {
                ctx.printSuccess("OK - Cambios guardados en stash");
            } else {
                ctx.printError("Error en stash: " + result.error);
            }
        } else if (args.equals("pop")) {
            result = executeGit(workDir, "stash", "pop");
            if (result.success) {
                ctx.printSuccess("OK - Cambios restaurados del stash");
            } else {
                ctx.printError("Error restaurando stash: " + result.error);
            }
        } else if (args.equals("list")) {
            result = executeGit(workDir, "stash", "list");
            if (result.success) {
                if (result.output.isBlank()) {
                    ctx.print("No hay stashes guardados");
                } else {
                    ctx.print("Stashes:");
                    for (String line : result.output.split("\n")) {
                        ctx.print("  " + line);
                    }
                }
            } else {
                ctx.printError("Error listando stash: " + result.error);
            }
        } else {
            ctx.printError("Uso: /git stash [push|pop|list]");
        }
    }

    private void handlePull(Path workDir, ExecutionContext ctx) {
        if (!isGitRepo(workDir)) {
            ctx.printError("No es un repositorio git");
            return;
        }

        ctx.print("Trayendo cambios del remoto...");
        GitResult result = executeGit(workDir, "pull");

        if (result.success) {
            ctx.printSuccess("OK - Pull completado");
            if (!result.output.isBlank()) {
                ctx.print(result.output.trim());
            }
        } else {
            ctx.printError("Error en pull: " + result.error);
        }
    }

    private void handlePush(Path workDir, ExecutionContext ctx) {
        if (!isGitRepo(workDir)) {
            ctx.printError("No es un repositorio git");
            return;
        }

        ctx.print("Enviando cambios al remoto...");
        GitResult result = executeGit(workDir, "push");

        if (result.success) {
            ctx.printSuccess("OK - Push completado");
            if (!result.output.isBlank()) {
                ctx.print(result.output.trim());
            }
        } else {
            ctx.printError("Error en push: " + result.error);
            if (result.error.contains("no upstream branch")) {
                ctx.print("  Tip: Use 'git push -u origin <branch>' la primera vez");
            }
        }
    }

    private boolean isGitRepo(Path workDir) {
        return Files.exists(workDir.resolve(".git"));
    }

    private GitResult executeGit(Path workDir, String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(List.of(args));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            int lineCount = 0;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (lineCount < MAX_OUTPUT_LINES) {
                        output.append(line).append("\n");
                    }
                    lineCount++;
                }
            }

            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new GitResult(false, "", "Timeout ejecutando git");
            }

            int exitCode = process.exitValue();
            return new GitResult(
                exitCode == 0,
                output.toString(),
                exitCode == 0 ? "" : output.toString()
            );
        } catch (Exception e) {
            return new GitResult(false, "", e.getMessage());
        }
    }

    private record GitResult(boolean success, String output, String error) {}
}
