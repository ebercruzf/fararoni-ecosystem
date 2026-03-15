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
import dev.fararoni.core.core.hooks.PostWriteHook;
import dev.fararoni.core.core.hooks.TestOnWriteHook;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class TestCommand implements ConsoleCommand {
    private static final Logger LOG = Logger.getLogger(TestCommand.class.getName());

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int QUICK_TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LINES = 500;

    @Override
    public String getTrigger() {
        return "/test";
    }

    @Override
    public String getDescription() {
        return "Ejecuta los tests del proyecto";
    }

    @Override
    public String getUsage() {
        return "/test [patron] [--verbose] [--timeout=N]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.DEBUG;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/tests", "/check" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /test - Ejecuta los tests del proyecto

            Uso:
              /test                  Ejecuta todos los tests
              /test <patron>         Ejecuta tests que coincidan con patron
              /test --verbose        Output detallado
              /test --timeout=N      Timeout en segundos (default: 300)

            Deteccion Automatica:
              - pom.xml       -> mvn test
              - build.gradle  -> gradle test
              - package.json  -> npm test
              - Cargo.toml    -> cargo test
              - go.mod        -> go test ./...
              - pytest.ini    -> pytest
              - setup.py      -> python -m pytest

            Ejemplos:
              /test                      # Todos los tests
              /test UserService          # Tests de UserService
              /test --verbose            # Output detallado
              /test "*.integration.*"    # Tests de integracion

            Notas:
              - Timeout por defecto: 5 minutos
              - El output se muestra en tiempo real
              - Exit code 0 = todos los tests pasan
              - Exit code != 0 = hay tests fallidos

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        Path workDir = ctx.getWorkingDirectory();

        TestFramework framework = detectFramework(workDir);
        if (framework == null) {
            ctx.printError("No se detecto framework de testing");
            ctx.print("Frameworks soportados:");
            ctx.print("  - Maven (pom.xml)");
            ctx.print("  - Gradle (build.gradle)");
            ctx.print("  - npm (package.json)");
            ctx.print("  - Cargo (Cargo.toml)");
            ctx.print("  - Go (go.mod)");
            ctx.print("  - pytest (pytest.ini, setup.py)");
            return;
        }

        boolean verbose = false;
        int timeout = DEFAULT_TIMEOUT_SECONDS;
        String pattern = null;

        if (args != null && !args.isBlank()) {
            String[] parts = args.trim().split("\\s+");
            for (String part : parts) {
                if (part.equals("--verbose") || part.equals("-v")) {
                    verbose = true;
                } else if (part.startsWith("--timeout=")) {
                    try {
                        timeout = Integer.parseInt(part.split("=")[1]);
                    } catch (Exception e) {
                    }
                } else if (pattern == null && !part.startsWith("-")) {
                    pattern = part;
                }
            }
        }

        String command = buildTestCommand(framework, pattern, verbose);

        ctx.print("Framework detectado: " + framework.name);
        ctx.print("Ejecutando: " + command);
        ctx.print("Timeout: " + timeout + " segundos");
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
            int passed = 0;
            int failed = 0;
            boolean truncated = false;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineCount++;

                    if (line.contains("PASSED") || line.contains("OK") || line.contains("passed")) {
                        passed++;
                    }
                    if (line.contains("FAILED") || line.contains("FAILURE") || line.contains("failed")) {
                        failed++;
                    }

                    if (lineCount <= MAX_OUTPUT_LINES) {
                        ctx.print("  " + line);
                    } else if (!truncated) {
                        truncated = true;
                    }
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                ctx.printError("Timeout: los tests excedieron " + timeout + " segundos");
                return;
            }

            int exitCode = process.exitValue();

            ctx.print("");

            if (truncated) {
                ctx.printWarning("Output truncado (" + (lineCount - MAX_OUTPUT_LINES) + " lineas omitidas)");
            }

            if (exitCode == 0) {
                ctx.printSuccess(String.format(
                    "OK - Tests completados en %.2fs",
                    elapsed / 1000.0
                ));
            } else {
                ctx.printError(String.format(
                    "FALLO - Tests fallaron (exit code: %d) en %.2fs",
                    exitCode, elapsed / 1000.0
                ));
            }

            ctx.printDebug("Lineas output: " + lineCount);
        } catch (Exception e) {
            ctx.printError("Error ejecutando tests: " + e.getMessage());
            ctx.printDebug("Causa: " + e.getClass().getSimpleName());
        }
    }

    private TestFramework detectFramework(Path workDir) {
        if (Files.exists(workDir.resolve("pom.xml"))) {
            return new TestFramework("Maven", "mvn", "test", "-Dtest=");
        }

        if (Files.exists(workDir.resolve("build.gradle")) ||
            Files.exists(workDir.resolve("build.gradle.kts"))) {
            return new TestFramework("Gradle", "gradle", "test", "--tests ");
        }

        if (Files.exists(workDir.resolve("package.json"))) {
            return new TestFramework("npm", "npm", "test", "-- ");
        }

        if (Files.exists(workDir.resolve("Cargo.toml"))) {
            return new TestFramework("Cargo", "cargo", "test", "");
        }

        if (Files.exists(workDir.resolve("go.mod"))) {
            return new TestFramework("Go", "go", "test ./...", "-run ");
        }

        if (Files.exists(workDir.resolve("pytest.ini")) ||
            Files.exists(workDir.resolve("setup.py")) ||
            Files.exists(workDir.resolve("pyproject.toml"))) {
            return new TestFramework("pytest", "pytest", "", "-k ");
        }

        return null;
    }

    private String buildTestCommand(TestFramework framework, String pattern, boolean verbose) {
        StringBuilder cmd = new StringBuilder();
        cmd.append(framework.executable);
        cmd.append(" ").append(framework.testCommand);

        if (pattern != null && !pattern.isEmpty()) {
            cmd.append(" ").append(framework.patternFlag).append(pattern);
        }

        if (verbose) {
            switch (framework.name) {
                case "Maven" -> cmd.append(" -X");
                case "Gradle" -> cmd.append(" --info");
                case "pytest" -> cmd.append(" -v");
                case "Cargo" -> cmd.append(" -- --nocapture");
                case "Go" -> cmd.append(" -v");
            }
        }

        return cmd.toString().trim();
    }

    private record TestFramework(
        String name,
        String executable,
        String testCommand,
        String patternFlag
    ) {}

    public record TestResult(
        boolean success,
        int exitCode,
        long durationMs,
        String errorSummary
    ) {
        public static TestResult pass(long durationMs) {
            return new TestResult(true, 0, durationMs, null);
        }

        public static TestResult fail(int exitCode, String error) {
            return new TestResult(false, exitCode, 0, error);
        }

        public static TestResult timeout(int timeoutSeconds) {
            return new TestResult(false, -1, timeoutSeconds * 1000L,
                "Timeout: tests exceeded " + timeoutSeconds + "s");
        }

        public static TestResult noFramework() {
            return new TestResult(true, 0, 0, null);
        }
    }

    public TestResult executeQuick(Path workDir) {
        LOG.fine(() -> "[TestCommand] executeQuick started for: " + workDir);

        TestFramework framework = detectFramework(workDir);
        if (framework == null) {
            LOG.fine(() -> "[TestCommand] No test framework detected - skipping");
            return TestResult.noFramework();
        }

        LOG.info(() -> "[TestCommand] Running quick tests with " + framework.name());

        String command = buildTestCommand(framework, null, false);

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

            StringBuilder outputBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuilder.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(QUICK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                LOG.warning(() -> "[TestCommand] Quick test timed out after " + QUICK_TIMEOUT_SECONDS + "s");
                return TestResult.timeout(QUICK_TIMEOUT_SECONDS);
            }

            int exitCode = process.exitValue();

            if (exitCode == 0) {
                LOG.info(() -> String.format("[TestCommand] Tests PASSED in %dms", elapsed));
                return TestResult.pass(elapsed);
            } else {
                String errorSummary = extractErrorSummary(outputBuilder.toString());
                LOG.warning(() -> "[TestCommand] Tests FAILED: " + errorSummary);
                return TestResult.fail(exitCode, errorSummary);
            }
        } catch (Exception e) {
            LOG.warning(() -> "[TestCommand] Test execution error: " + e.getMessage());
            return TestResult.fail(-99, "Execution error: " + e.getMessage());
        }
    }

    private String extractErrorSummary(String output) {
        if (output == null || output.isEmpty()) {
            return "Unknown error";
        }

        if (output.contains("COMPILATION ERROR") || output.contains("cannot find symbol")) {
            return "Compilation error";
        }
        if (output.contains("BUILD FAILURE")) {
            int idx = output.indexOf("Failed tests:");
            if (idx > 0) {
                int endIdx = output.indexOf("\n\n", idx);
                if (endIdx > idx) {
                    return output.substring(idx, Math.min(endIdx, idx + 200)).trim();
                }
            }
            return "Build failure";
        }
        if (output.contains("AssertionError") || output.contains("assertion failed")) {
            return "Assertion failed";
        }
        if (output.contains("NullPointerException")) {
            return "NullPointerException";
        }
        if (output.contains("FAILED") || output.contains("Failures:")) {
            return "Test failures detected";
        }

        String[] lines = output.split("\n");
        if (lines.length > 3) {
            return lines[lines.length - 3] + "..." + lines[lines.length - 1];
        }
        return output.length() > 200 ? output.substring(0, 200) + "..." : output;
    }
}
