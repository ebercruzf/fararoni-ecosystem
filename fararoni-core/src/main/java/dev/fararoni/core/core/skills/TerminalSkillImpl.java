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
package dev.fararoni.core.core.skills;

import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.skills.TerminalSkill;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class TerminalSkillImpl implements TerminalSkill {
    private static final Logger LOG = Logger.getLogger(TerminalSkillImpl.class.getName());
    private static final String SKILL_NAME = "terminal";

    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;

    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
        Pattern.compile("rm\\s+-[rR]f?\\s+/(?!\\S)"),
        Pattern.compile("rm\\s+-[rR]f?\\s+/\\*"),
        Pattern.compile("rm\\s+-[rR]f?\\s+~"),
        Pattern.compile("rm\\s+-[rR]f?\\s+\\$HOME"),

        Pattern.compile("dd\\s+.*if=/dev/(zero|random|urandom)"),
        Pattern.compile("mkfs\\."),

        Pattern.compile(":\\(\\)\\{"),
        Pattern.compile("\\|\\s*xargs\\s+-P\\s*\\d+\\s*fork"),

        Pattern.compile("chmod\\s+(-[rR])?\\s*777\\s+/"),
        Pattern.compile("chmod\\s+.*\\s+\\.\\."),
        Pattern.compile("chown\\s+-[rR]?\\s+.*\\s+/"),

        Pattern.compile(">\\s*/dev/sda"),
        Pattern.compile("mv\\s+.*\\s+/dev/null"),
        Pattern.compile("shutdown|reboot|halt|poweroff"),
        Pattern.compile("init\\s+[0156]"),

        Pattern.compile("nc\\s+-[el]"),
        Pattern.compile("curl\\s+.*\\|\\s*(ba)?sh"),
        Pattern.compile("wget\\s+.*\\|\\s*(ba)?sh"),

        Pattern.compile("cat\\s+.*\\.(ssh|gpg|pem|key)"),
        Pattern.compile("cat\\s+/etc/(passwd|shadow|sudoers)"),

        Pattern.compile("history\\s+-[cd]"),
        Pattern.compile(">\\s*/var/log/"),

        Pattern.compile("eval\\s+.*\\$"),
        Pattern.compile("\\$\\(.*\\$\\{.*\\}.*\\)")
    );

    private final String[] shellCommand;

    private Path workspaceRoot;
    private Path workingDirectory;

    private final ExecutorService executor;
    private boolean allowOutsideWorkspace = false;

    public TerminalSkillImpl() {
        this.shellCommand = detectShell();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "terminal-exec");
            t.setDaemon(true);
            return t;
        });
        this.workingDirectory = Path.of(System.getProperty("user.dir"));
    }

    public TerminalSkillImpl(Path workspaceRoot) {
        this();
        this.workspaceRoot = workspaceRoot;
        this.workingDirectory = workspaceRoot;
    }

    @Override
    public String getSkillName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return "Secure shell command execution with sandboxing and timeout support";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public FNLResult<ExecutionResult> execute(String command) {
        return executeWithOptions(command, workingDirectory.toString(), Map.of(), DEFAULT_TIMEOUT_MS);
    }

    @Override
    public FNLResult<ExecutionResult> executeWithOptions(
            String command,
            String workingDirectory,
            Map<String, String> environment,
            long timeoutMs) {
        long startTime = System.currentTimeMillis();

        FNLResult<Boolean> validation = isCommandAllowed(command);
        if (!validation.success() || !validation.data()) {
            String reason = validation.success() ? "Command blocked by security policy" : validation.error();
            LOG.warning(() -> String.format("[TERMINAL] Blocked: %s - %s", command, reason));
            return FNLResult.failure(reason);
        }

        Path workDir = Path.of(workingDirectory).toAbsolutePath().normalize();
        if (workspaceRoot != null && !allowOutsideWorkspace) {
            if (!workDir.startsWith(workspaceRoot)) {
                return FNLResult.failure("Working directory must be within workspace: " + workspaceRoot);
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(shellCommand[0], shellCommand[1], command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(false);

            Map<String, String> env = pb.environment();
            if (environment != null) {
                env.putAll(environment);
            }

            LOG.fine(() -> String.format("[TERMINAL] Executing: %s in %s", command, workDir));

            Process process = pb.start();

            Future<String> stdoutFuture = executor.submit(() -> captureStream(process.getInputStream()));
            Future<String> stderrFuture = executor.submit(() -> captureStream(process.getErrorStream()));

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            long duration = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);

                LOG.warning(() -> String.format("[TERMINAL] Timeout after %dms: %s", timeoutMs, command));

                return FNLResult.success(new ExecutionResult(
                    -1,
                    getOrDefault(stdoutFuture, ""),
                    getOrDefault(stderrFuture, ""),
                    duration,
                    true,
                    command
                ));
            }

            String stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(1, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            LOG.fine(() -> String.format("[TERMINAL] Exit %d in %dms: %s", exitCode, duration, command));

            return FNLResult.success(new ExecutionResult(
                exitCode,
                stdout,
                stderr,
                duration,
                false,
                command
            ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FNLResult.failure("Execution interrupted");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[TERMINAL] Execution failed: " + command, e);
            return FNLResult.failure("Execution failed: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<ExecutionResult> executeStreaming(
            String command,
            OutputHandler outputHandler,
            long timeoutMs) {
        long startTime = System.currentTimeMillis();

        FNLResult<Boolean> validation = isCommandAllowed(command);
        if (!validation.success() || !validation.data()) {
            return FNLResult.failure(validation.success() ? "Command blocked" : validation.error());
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(shellCommand[0], shellCommand[1], command);
            pb.directory(workingDirectory.toFile());

            Process process = pb.start();

            Future<String> stdoutFuture = executor.submit(() ->
                streamOutput(process.getInputStream(), outputHandler, false));
            Future<String> stderrFuture = executor.submit(() ->
                streamOutput(process.getErrorStream(), outputHandler, true));

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            long duration = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);

                return FNLResult.success(new ExecutionResult(
                    -1,
                    getOrDefault(stdoutFuture, ""),
                    getOrDefault(stderrFuture, ""),
                    duration,
                    true,
                    command
                ));
            }

            return FNLResult.success(new ExecutionResult(
                process.exitValue(),
                stdoutFuture.get(1, TimeUnit.SECONDS),
                stderrFuture.get(1, TimeUnit.SECONDS),
                duration,
                false,
                command
            ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FNLResult.failure("Execution interrupted");
        } catch (Exception e) {
            return FNLResult.failure("Streaming execution failed: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<Boolean> isCommandAllowed(String command) {
        if (command == null || command.isBlank()) {
            return FNLResult.failure("Command cannot be empty");
        }

        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(command).find()) {
                String msg = String.format("Command matches blocked pattern: %s", pattern.pattern());
                return FNLResult.success(false);
            }
        }

        if (command.contains("../") && workspaceRoot != null) {
            return FNLResult.success(false);
        }

        return FNLResult.success(true);
    }

    @Override
    public FNLResult<String> getWorkingDirectory() {
        return FNLResult.success(workingDirectory.toString());
    }

    @Override
    public FNLResult<String> getEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null) {
            return FNLResult.failure("Environment variable not set: " + name);
        }
        return FNLResult.success(value);
    }

    public TerminalSkillImpl setWorkingDirectory(Path directory) {
        this.workingDirectory = directory.toAbsolutePath().normalize();
        return this;
    }

    public TerminalSkillImpl setWorkspaceRoot(Path root) {
        this.workspaceRoot = root.toAbsolutePath().normalize();
        return this;
    }

    public TerminalSkillImpl setAllowOutsideWorkspace(boolean allow) {
        this.allowOutsideWorkspace = allow;
        return this;
    }

    public boolean isPatternBlocked(String pattern) {
        for (Pattern blocked : BLOCKED_PATTERNS) {
            if (blocked.matcher(pattern).find()) {
                return true;
            }
        }
        return false;
    }

    private String[] detectShell() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return new String[]{"cmd.exe", "/c"};
        } else {
            String shell = System.getenv("SHELL");
            if (shell == null || shell.isBlank()) {
                shell = "/bin/sh";
            }
            return new String[]{shell, "-c"};
        }
    }

    private String captureStream(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;

        while ((read = is.read(buffer)) != -1) {
            if (total + read > MAX_OUTPUT_BYTES) {
                baos.write(buffer, 0, MAX_OUTPUT_BYTES - total);
                baos.write("\n[OUTPUT TRUNCATED]".getBytes(StandardCharsets.UTF_8));
                break;
            }
            baos.write(buffer, 0, read);
            total += read;
        }

        return baos.toString(StandardCharsets.UTF_8);
    }

    private String streamOutput(InputStream is, OutputHandler handler, boolean isError) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (handler != null) {
                    handler.onOutput(line, isError);
                }
                if (sb.length() < MAX_OUTPUT_BYTES) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line);
                }
            }
        }
        return sb.toString();
    }

    private String getOrDefault(Future<String> future, String defaultValue) {
        try {
            return future.get(100, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
