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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class IgnoreCommand implements ConsoleCommand {
    private static final String GITIGNORE_FILE = ".gitignore";

    @Override
    public String getTrigger() {
        return "/ign";
    }

    @Override
    public String getDescription() {
        return "Gestiona patrones en .gitignore";
    }

    @Override
    public String getUsage() {
        return "/ign [add|remove|list|template] [patron|tecnologia]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.GIT;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/ignore", "/gitignore" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /ign - Gestiona patrones en .gitignore

            Uso:
              /ign                     Lista patrones actuales
              /ign list                Lista patrones actuales
              /ign add <patron>        Agrega patron al .gitignore
              /ign remove <patron>     Remueve patron del .gitignore
              /ign template <tech>     Genera template para tecnologia

            Tecnologias Soportadas (template):
              java      - Maven/Gradle, .class, .jar
              node      - node_modules, npm/yarn locks
              python    - __pycache__, .pyc, venv
              rust      - target/, Cargo.lock
              go        - vendor/, bin/
              idea      - .idea/, *.iml
              vscode    - .vscode/
              macos     - .DS_Store, .Spotlight-V100

            Ejemplos:
              /ign add *.log           # Ignorar archivos .log
              /ign add build/          # Ignorar directorio build
              /ign remove *.bak        # Dejar de ignorar .bak
              /ign template java       # Template para Java
              /ign template node       # Template para Node.js

            Patrones Comunes:
              *.log         - Archivos de log
              *.tmp         - Archivos temporales
              .env          - Variables de entorno
              target/       - Build Maven
              node_modules/ - Dependencias Node
              __pycache__/  - Cache Python
              .DS_Store     - Metadata macOS

            Notas:
              - Los patrones se agregan al final del archivo
              - Si .gitignore no existe, se crea automaticamente
              - Use / al final para directorios

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        Path workDir = ctx.getWorkingDirectory();
        Path gitignorePath = workDir.resolve(GITIGNORE_FILE);

        if (args == null || args.isBlank()) {
            listPatterns(gitignorePath, ctx);
            return;
        }

        String[] parts = args.trim().split("\\s+", 2);
        String action = parts[0].toLowerCase();
        String argument = parts.length > 1 ? parts[1].trim() : null;

        switch (action) {
            case "list", "ls", "show" -> listPatterns(gitignorePath, ctx);
            case "add", "a", "+" -> addPattern(gitignorePath, argument, ctx);
            case "remove", "rm", "delete", "-" -> removePattern(gitignorePath, argument, ctx);
            case "template", "init", "generate" -> generateTemplate(gitignorePath, argument, ctx);
            default -> {
                addPattern(gitignorePath, args.trim(), ctx);
            }
        }
    }

    private void listPatterns(Path gitignorePath, ExecutionContext ctx) {
        if (!Files.exists(gitignorePath)) {
            ctx.printWarning("No existe .gitignore en este directorio");
            ctx.print("Use /ign add <patron> para crear uno");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(gitignorePath);
            List<String> patterns = lines.stream()
                .filter(l -> !l.isBlank() && !l.startsWith("#"))
                .toList();

            if (patterns.isEmpty()) {
                ctx.printWarning(".gitignore esta vacio (solo comentarios)");
                return;
            }

            ctx.print("Patrones en .gitignore (" + patterns.size() + "):");
            ctx.print("");
            for (String pattern : patterns) {
                ctx.print("  " + pattern);
            }

            ctx.printDebug("Total lineas: " + lines.size());
        } catch (IOException e) {
            ctx.printError("Error leyendo .gitignore: " + e.getMessage());
        }
    }

    private void addPattern(Path gitignorePath, String pattern, ExecutionContext ctx) {
        if (pattern == null || pattern.isBlank()) {
            ctx.printError("Uso: /ign add <patron>");
            ctx.print("  Ejemplo: /ign add *.log");
            return;
        }

        try {
            if (Files.exists(gitignorePath)) {
                List<String> existing = Files.readAllLines(gitignorePath);
                if (existing.contains(pattern)) {
                    ctx.printWarning("El patron ya existe en .gitignore: " + pattern);
                    return;
                }
            }

            String line = pattern + System.lineSeparator();
            Files.writeString(gitignorePath, line,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);

            ctx.printSuccess("OK - Patron agregado: " + pattern);
            ctx.printDebug("Archivo: " + gitignorePath);
        } catch (IOException e) {
            ctx.printError("Error escribiendo .gitignore: " + e.getMessage());
        }
    }

    private void removePattern(Path gitignorePath, String pattern, ExecutionContext ctx) {
        if (pattern == null || pattern.isBlank()) {
            ctx.printError("Uso: /ign remove <patron>");
            ctx.print("  Ejemplo: /ign remove *.log");
            return;
        }

        if (!Files.exists(gitignorePath)) {
            ctx.printError("No existe .gitignore");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(gitignorePath);
            List<String> newLines = new ArrayList<>();
            boolean found = false;

            for (String line : lines) {
                if (line.equals(pattern)) {
                    found = true;
                } else {
                    newLines.add(line);
                }
            }

            if (!found) {
                ctx.printWarning("Patron no encontrado: " + pattern);
                return;
            }

            Files.write(gitignorePath, newLines);

            ctx.printSuccess("OK - Patron removido: " + pattern);
            ctx.printDebug("Lineas restantes: " + newLines.size());
        } catch (IOException e) {
            ctx.printError("Error modificando .gitignore: " + e.getMessage());
        }
    }

    private void generateTemplate(Path gitignorePath, String tech, ExecutionContext ctx) {
        if (tech == null || tech.isBlank()) {
            ctx.printError("Uso: /ign template <tecnologia>");
            ctx.print("  Tecnologias: java, node, python, rust, go, idea, vscode, macos");
            return;
        }

        String template = getTemplateFor(tech.toLowerCase());
        if (template == null) {
            ctx.printError("Tecnologia no soportada: " + tech);
            ctx.print("  Soportadas: java, node, python, rust, go, idea, vscode, macos");
            return;
        }

        try {
            if (Files.exists(gitignorePath)) {
                long size = Files.size(gitignorePath);
                if (size > 0) {
                    ctx.printWarning(".gitignore ya tiene contenido (" + size + " bytes)");
                    ctx.print("Se agregara el template al final");
                }
            }

            String header = System.lineSeparator() +
                "# === " + tech.toUpperCase() + " ===" + System.lineSeparator();

            Files.writeString(gitignorePath, header + template,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);

            int patterns = (int) template.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).count();
            ctx.printSuccess("OK - Template " + tech + " agregado (" + patterns + " patrones)");
        } catch (IOException e) {
            ctx.printError("Error escribiendo template: " + e.getMessage());
        }
    }

    private String getTemplateFor(String tech) {
        return switch (tech) {
            case "java", "maven", "gradle" -> """
                # Java
                *.class
                *.jar
                *.war
                *.ear
                target/
                build/
                out/
                .gradle/
                gradle-app.setting
                !gradle-wrapper.jar
                """;

            case "node", "nodejs", "npm", "yarn" -> """
                # Node.js
                node_modules/
                npm-debug.log*
                yarn-debug.log*
                yarn-error.log*
                .npm
                .yarn-integrity
                dist/
                build/
                .env
                .env.local
                """;

            case "python", "py" -> """
                # Python
                __pycache__/
                *.py[cod]
                *$py.class
                *.so
                .Python
                venv/
                .venv/
                ENV/
                .eggs/
                *.egg-info/
                .pytest_cache/
                """;

            case "rust", "cargo" -> """
                # Rust
                target/
                Cargo.lock
                **/*.rs.bk
                """;

            case "go", "golang" -> """
                # Go
                bin/
                vendor/
                *.exe
                *.test
                *.out
                go.work
                """;

            case "idea", "intellij" -> """
                # IntelliJ IDEA
                .idea/
                *.iml
                *.iws
                *.ipr
                out/
                """;

            case "vscode", "vs" -> """
                # VS Code
                .vscode/
                *.code-workspace
                .history/
                """;

            case "macos", "mac", "osx" -> """
                # macOS
                .DS_Store
                .AppleDouble
                .LSOverride
                ._*
                .Spotlight-V100
                .Trashes
                """;

            default -> null;
        };
    }
}
