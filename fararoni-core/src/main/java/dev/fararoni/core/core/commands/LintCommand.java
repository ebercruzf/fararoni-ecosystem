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
public class LintCommand implements ConsoleCommand {
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_LINES = 300;

    @Override
    public String getTrigger() {
        return "/lint";
    }

    @Override
    public String getDescription() {
        return "Ejecuta linter/formatter del proyecto";
    }

    @Override
    public String getUsage() {
        return "/lint [--fix] [archivo]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.DEBUG;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/format", "/style" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /lint - Ejecuta linter/formatter del proyecto

            Uso:
              /lint                  Ejecuta lint en todo el proyecto
              /lint --fix            Corrige errores automaticamente
              /lint <archivo>        Lint de archivo especifico
              /lint --fix <archivo>  Corrige archivo especifico

            Deteccion Automatica:
              - .eslintrc.* -> ESLint (npm run lint)
              - .prettierrc -> Prettier (npx prettier --check)
              - pom.xml + checkstyle -> Maven Checkstyle
              - build.gradle + spotless -> Gradle Spotless
              - pyproject.toml + ruff -> Ruff
              - setup.cfg + pylint -> PyLint
              - .rubocop.yml -> RuboCop
              - .golangci.yml -> golangci-lint
              - Cargo.toml -> cargo clippy

            Ejemplos:
              /lint                      # Lint de todo el proyecto
              /lint --fix                # Lint + fix automatico
              /lint src/Main.java        # Lint de archivo
              /lint --fix src/*.ts       # Fix archivos TypeScript

            Frameworks Soportados:
              ┌─────────────┬──────────────────────────────┐
              │ Lenguaje    │ Linters                      │
              ├─────────────┼──────────────────────────────┤
              │ JavaScript  │ ESLint, Prettier             │
              │ TypeScript  │ ESLint, Prettier             │
              │ Java        │ Checkstyle, Spotless, PMD    │
              │ Python      │ Ruff, PyLint, Black, Flake8  │
              │ Go          │ golangci-lint, gofmt         │
              │ Rust        │ Clippy, rustfmt              │
              │ Ruby        │ RuboCop                      │
              └─────────────┴──────────────────────────────┘

            Notas:
              - El linter debe estar instalado
              - Use --fix con cuidado en codigo compartido
              - Timeout por defecto: 120 segundos

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        Path workDir = ctx.getWorkingDirectory();

        boolean autoFix = false;
        String targetFile = null;

        if (args != null && !args.isBlank()) {
            String[] parts = args.trim().split("\\s+");
            for (String part : parts) {
                if (part.equals("--fix") || part.equals("-f")) {
                    autoFix = true;
                } else if (targetFile == null && !part.startsWith("-")) {
                    targetFile = part;
                }
            }
        }

        LinterConfig linter = detectLinter(workDir);

        if (linter == null) {
            ctx.printError("No se detecto linter en el proyecto");
            ctx.print("Linters soportados:");
            ctx.print("  - ESLint (.eslintrc.*)");
            ctx.print("  - Prettier (.prettierrc)");
            ctx.print("  - Checkstyle (pom.xml)");
            ctx.print("  - Ruff (pyproject.toml)");
            ctx.print("  - Clippy (Cargo.toml)");
            ctx.print("  - golangci-lint (.golangci.yml)");
            return;
        }

        String command = buildCommand(linter, autoFix, targetFile);

        ctx.print("Linter detectado: " + linter.name);
        ctx.print("Ejecutando: " + command);
        if (autoFix) {
            ctx.printWarning("Modo --fix activado: se modificaran archivos");
        }
        ctx.print("");

        long startTime = System.currentTimeMillis();

        try {
            String[] shellCommand;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                shellCommand = new String[] { "cmd.exe", "/c", command };
            } else {
                shellCommand = new String[] { "/bin/sh", "-c", command };
            }

            ProcessBuilder pb = new ProcessBuilder(shellCommand);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            int lineCount = 0;
            int errorCount = 0;
            int warningCount = 0;
            boolean truncated = false;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineCount++;

                    String lower = line.toLowerCase();
                    if (lower.contains("error") || lower.contains("✖") || lower.contains("[e]")) {
                        errorCount++;
                    }
                    if (lower.contains("warning") || lower.contains("⚠") || lower.contains("[w]")) {
                        warningCount++;
                    }

                    if (lineCount <= MAX_OUTPUT_LINES) {
                        ctx.print("  " + line);
                    } else if (!truncated) {
                        truncated = true;
                    }
                }
            }

            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                ctx.printError("Timeout: lint excedio " + DEFAULT_TIMEOUT_SECONDS + " segundos");
                return;
            }

            int exitCode = process.exitValue();

            ctx.print("");

            if (truncated) {
                ctx.printWarning("Output truncado (" + (lineCount - MAX_OUTPUT_LINES) + " lineas omitidas)");
            }

            if (exitCode == 0) {
                ctx.printSuccess(String.format(
                    "OK - Lint completado en %.2fs",
                    elapsed / 1000.0
                ));
                if (errorCount == 0 && warningCount == 0) {
                    ctx.print("  Sin problemas encontrados");
                }
            } else {
                ctx.printError(String.format(
                    "FALLO - %d errores, %d warnings (en %.2fs)",
                    errorCount, warningCount, elapsed / 1000.0
                ));
                if (autoFix) {
                    ctx.print("  Algunos errores no pudieron corregirse automaticamente");
                } else {
                    ctx.print("  Tip: Use /lint --fix para corregir automaticamente");
                }
            }

            ctx.printDebug("exitCode=" + exitCode + ", lines=" + lineCount);
        } catch (Exception e) {
            ctx.printError("Error ejecutando lint: " + e.getMessage());
        }
    }

    private LinterConfig detectLinter(Path workDir) {
        if (hasFile(workDir, ".eslintrc.js") || hasFile(workDir, ".eslintrc.json") ||
            hasFile(workDir, ".eslintrc.yml") || hasFile(workDir, ".eslintrc.cjs") ||
            hasFile(workDir, "eslint.config.js")) {
            return new LinterConfig("ESLint", "npx eslint .", "npx eslint . --fix");
        }

        if (hasFile(workDir, ".prettierrc") || hasFile(workDir, ".prettierrc.json") ||
            hasFile(workDir, "prettier.config.js")) {
            return new LinterConfig("Prettier", "npx prettier --check .", "npx prettier --write .");
        }

        if (hasFile(workDir, "pom.xml")) {
            if (hasFile(workDir, "checkstyle.xml") || hasFile(workDir, "src/main/resources/checkstyle.xml")) {
                return new LinterConfig("Checkstyle", "mvn checkstyle:check", "mvn checkstyle:check");
            }
            return new LinterConfig("Maven Verify", "mvn verify -DskipTests", "mvn verify -DskipTests");
        }

        if (hasFile(workDir, "build.gradle") || hasFile(workDir, "build.gradle.kts")) {
            return new LinterConfig("Spotless", "gradle spotlessCheck", "gradle spotlessApply");
        }

        if (hasFile(workDir, "pyproject.toml") || hasFile(workDir, "ruff.toml")) {
            return new LinterConfig("Ruff", "ruff check .", "ruff check . --fix");
        }

        if (hasFile(workDir, ".pylintrc") || hasFile(workDir, "setup.cfg")) {
            return new LinterConfig("PyLint", "pylint **/*.py", "pylint **/*.py");
        }

        if (hasFile(workDir, ".golangci.yml") || hasFile(workDir, ".golangci.yaml") ||
            hasFile(workDir, "go.mod")) {
            if (hasFile(workDir, ".golangci.yml") || hasFile(workDir, ".golangci.yaml")) {
                return new LinterConfig("golangci-lint", "golangci-lint run", "golangci-lint run --fix");
            }
            return new LinterConfig("go vet", "go vet ./...", "go fmt ./...");
        }

        if (hasFile(workDir, "Cargo.toml")) {
            return new LinterConfig("Clippy", "cargo clippy", "cargo clippy --fix --allow-dirty");
        }

        if (hasFile(workDir, ".rubocop.yml") || hasFile(workDir, "Gemfile")) {
            return new LinterConfig("RuboCop", "rubocop", "rubocop -a");
        }

        if (hasFile(workDir, "phpcs.xml") || hasFile(workDir, "phpcs.xml.dist")) {
            return new LinterConfig("PHP_CodeSniffer", "phpcs", "phpcbf");
        }

        return null;
    }

    private String buildCommand(LinterConfig linter, boolean autoFix, String targetFile) {
        String base = autoFix ? linter.fixCommand : linter.checkCommand;

        if (targetFile != null && !targetFile.isEmpty()) {
            return base.replace(" .", " " + targetFile).replace("./...", targetFile);
        }

        return base;
    }

    private boolean hasFile(Path dir, String filename) {
        return Files.exists(dir.resolve(filename));
    }

    private record LinterConfig(
        String name,
        String checkCommand,
        String fixCommand
    ) {}
}
