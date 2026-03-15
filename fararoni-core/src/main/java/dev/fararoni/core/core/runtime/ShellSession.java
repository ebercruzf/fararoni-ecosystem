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
package dev.fararoni.core.core.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ShellSession {
    private static final Logger LOG = Logger.getLogger(ShellSession.class.getName());

    public static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private static final int MAX_HISTORY_SIZE = 100;

    private static final Pattern COMMAND_PATTERN =
            Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "mvn", "gradle", "./gradlew", "./mvnw", "ant",
            "npm", "yarn", "pnpm", "npx", "node", "bun",
            "python", "python3", "pytest", "pip", "pip3", "poetry", "uv",
            "java", "javac", "jar",
            "git",
            "ls", "pwd", "echo", "cat", "head", "tail", "grep", "find", "which", "env",
            "cargo", "rustc",
            "go",
            "ruby", "bundle", "rake",
            "dotnet",
            "make"
    );

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "rm", "rmdir", "del", "format",
            "dd", "mkfs", "fdisk",
            "sudo", "su", "doas",
            "chmod", "chown", "chgrp",
            "kill", "killall", "pkill",
            "shutdown", "reboot", "halt",
            "curl", "wget",
            ">", ">>",
            "|"
    );

    private static final int SAFE_TIMEOUT_SECONDS = 30;

    private Path currentDirectory;

    private final Map<String, String> environment;

    private int timeoutSeconds;

    private final List<String> commandHistory;

    private CommandResult lastResult;

    private boolean safeMode = false;

    private boolean closeStdinImmediately = false;

    public ShellSession(Path rootPath) {
        this(rootPath, DEFAULT_TIMEOUT_SECONDS);
    }

    public ShellSession(Path rootPath, int timeoutSeconds) {
        this.currentDirectory = rootPath != null ? rootPath.toAbsolutePath() : Path.of(".").toAbsolutePath();
        this.environment = new HashMap<>(System.getenv());
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        this.commandHistory = new ArrayList<>();
        this.lastResult = null;

        LOG.info("[SHELL] Session started at: " + currentDirectory);
    }

    public record CommandResult(
            int exitCode,
            String stdout,
            String stderr,
            String command,
            long durationMs
    ) {
        public boolean isSuccess() {
            return exitCode == 0;
        }

        public String getCombinedOutput() {
            if (stderr == null || stderr.isBlank()) {
                return stdout;
            }
            return stdout + "\n[STDERR]\n" + stderr;
        }
    }

    public CommandResult execute(String commandString) {
        if (commandString == null || commandString.isBlank()) {
            lastResult = new CommandResult(0, "", "", "", 0);
            return lastResult;
        }

        String trimmed = commandString.trim();

        if (safeMode && !isCommandAllowed(trimmed)) {
            String reason = getBlockReason(trimmed);
            LOG.warning("[SHELL-SAFE] Blocked command (safe mode): " + trimmed + " - " + reason);
            lastResult = new CommandResult(-1, "", "BLOCKED: " + reason, trimmed, 0);
            addToHistory(trimmed);
            return lastResult;
        }

        addToHistory(trimmed);

        List<String> args = parseCommand(trimmed);
        if (args.isEmpty()) {
            lastResult = new CommandResult(0, "", "", trimmed, 0);
            return lastResult;
        }

        String cmd = args.get(0);

        switch (cmd) {
            case "cd" -> {
                lastResult = handleCd(args);
                return lastResult;
            }
            case "pwd" -> {
                lastResult = new CommandResult(0, currentDirectory.toString(), "", trimmed, 0);
                return lastResult;
            }
            case "export" -> {
                lastResult = handleExport(args, trimmed);
                return lastResult;
            }
        }

        lastResult = runSystemCommand(args, trimmed);
        return lastResult;
    }

    public List<CommandResult> executeAll(List<String> commands) {
        List<CommandResult> results = new ArrayList<>();
        for (String cmd : commands) {
            results.add(execute(cmd));
        }
        return results;
    }

    public boolean executeSuccess(String commandString) {
        return execute(commandString).isSuccess();
    }

    private CommandResult handleCd(List<String> args) {
        if (args.size() < 2) {
            String home = System.getProperty("user.home");
            currentDirectory = Path.of(home);
            return new CommandResult(0, "Changed to: " + currentDirectory, "", "cd", 0);
        }

        String target = args.get(1);

        if (target.startsWith("~")) {
            String home = System.getProperty("user.home");
            target = target.replaceFirst("~", home);
        }

        Path newPath = currentDirectory.resolve(target).normalize();

        if (Files.isDirectory(newPath)) {
            currentDirectory = newPath;
            LOG.fine("[SHELL] CWD changed to: " + currentDirectory);
            return new CommandResult(0, "Changed to: " + currentDirectory, "", "cd " + target, 0);
        } else {
            String error = "Directory not found: " + target;
            LOG.warning("[SHELL] " + error);
            return new CommandResult(1, "", error, "cd " + target, 0);
        }
    }

    private CommandResult handleExport(List<String> args, String original) {
        if (args.size() < 2) {
            StringBuilder sb = new StringBuilder();
            environment.forEach((k, v) -> sb.append(k).append("=").append(v).append("\n"));
            return new CommandResult(0, sb.toString(), "", original, 0);
        }

        String assignment = args.get(1);
        int eqIndex = assignment.indexOf('=');
        if (eqIndex > 0) {
            String key = assignment.substring(0, eqIndex);
            String value = assignment.substring(eqIndex + 1);
            environment.put(key, value);
            LOG.fine("[SHELL] Set env: " + key + "=" + value);
            return new CommandResult(0, "", "", original, 0);
        }

        return new CommandResult(1, "", "Invalid export syntax. Use: export VAR=value", original, 0);
    }

    private CommandResult runSystemCommand(List<String> command, String original) {
        long startTime = System.currentTimeMillis();

        List<String> shellCommand = wrapInShell(command, original);

        ProcessBuilder pb = new ProcessBuilder(shellCommand);
        pb.directory(currentDirectory.toFile());
        pb.environment().putAll(this.environment);

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        try {
            Process process = pb.start();

            if (closeStdinImmediately) {
                try {
                    process.getOutputStream().close();
                } catch (IOException e) {
                }
            }

            Thread outThread = new Thread(() ->
                    readStream(new BufferedReader(new InputStreamReader(process.getInputStream())), stdout),
                    "stdout-reader");
            Thread errThread = new Thread(() ->
                    readStream(new BufferedReader(new InputStreamReader(process.getErrorStream())), stderr),
                    "stderr-reader");

            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            outThread.join(1000);
            errThread.join(1000);

            long duration = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                LOG.warning("[SHELL] Command timeout after " + timeoutSeconds + "s: " + original);
                return new CommandResult(-1, stdout.toString(),
                        "Timeout after " + timeoutSeconds + " seconds", original, duration);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                LOG.fine("[SHELL] Command failed (exit=" + exitCode + "): " + original);
            }

            return new CommandResult(exitCode, stdout.toString().trim(),
                    stderr.toString().trim(), original, duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "", "Interrupted: " + e.getMessage(), original,
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            LOG.severe("[SHELL] Execution error: " + e.getMessage());
            return new CommandResult(-1, "", "Execution error: " + e.getMessage(), original,
                    System.currentTimeMillis() - startTime);
        }
    }

    private List<String> wrapInShell(List<String> command, String original) {
        if (original.contains("|") || original.contains(">") || original.contains("<") ||
            original.contains("$") || original.contains("&&") || original.contains("||")) {
            String shell = System.getProperty("os.name").toLowerCase().contains("win")
                    ? "cmd.exe" : "/bin/sh";
            String flag = shell.contains("cmd") ? "/c" : "-c";
            return List.of(shell, flag, original);
        }
        return command;
    }

    private void readStream(BufferedReader reader, StringBuilder sb) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
        }
    }

    private List<String> parseCommand(String cmd) {
        List<String> args = new ArrayList<>();
        Matcher matcher = COMMAND_PATTERN.matcher(cmd);

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                args.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                args.add(matcher.group(2));
            } else if (matcher.group(3) != null) {
                args.add(matcher.group(3));
            }
        }

        return args;
    }

    private void addToHistory(String command) {
        commandHistory.add(command);
        if (commandHistory.size() > MAX_HISTORY_SIZE) {
            commandHistory.remove(0);
        }
    }

    public List<String> getHistory() {
        return Collections.unmodifiableList(commandHistory);
    }

    public void clearHistory() {
        commandHistory.clear();
    }

    public Path getCurrentDirectory() {
        return currentDirectory;
    }

    public boolean setCurrentDirectory(Path path) {
        if (path != null && Files.isDirectory(path)) {
            currentDirectory = path.toAbsolutePath();
            return true;
        }
        return false;
    }

    public Map<String, String> getEnvironment() {
        return new HashMap<>(environment);
    }

    public void setEnv(String key, String value) {
        if (key != null && !key.isBlank()) {
            environment.put(key, value);
        }
    }

    public String getEnv(String key) {
        return environment.get(key);
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int seconds) {
        this.timeoutSeconds = Math.max(1, seconds);
    }

    public CommandResult getLastResult() {
        return lastResult;
    }

    public CommandResult executeSafe(String commandString) {
        if (commandString == null || commandString.isBlank()) {
            lastResult = new CommandResult(0, "", "", "", 0);
            return lastResult;
        }

        String trimmed = commandString.trim();

        if (!isCommandAllowed(trimmed)) {
            String reason = getBlockReason(trimmed);
            LOG.warning("[SHELL-SAFE] Blocked command: " + trimmed + " - " + reason);
            lastResult = new CommandResult(-1, "", "BLOCKED: " + reason, trimmed, 0);
            return lastResult;
        }

        boolean prevCloseStdin = this.closeStdinImmediately;
        int prevTimeout = this.timeoutSeconds;

        this.closeStdinImmediately = true;
        this.timeoutSeconds = Math.min(this.timeoutSeconds, SAFE_TIMEOUT_SECONDS);

        try {
            return execute(trimmed);
        } finally {
            this.closeStdinImmediately = prevCloseStdin;
            this.timeoutSeconds = prevTimeout;
        }
    }

    public boolean isCommandAllowed(String commandString) {
        if (commandString == null || commandString.isBlank()) {
            return true;
        }

        String trimmed = commandString.trim();
        List<String> args = parseCommand(trimmed);
        if (args.isEmpty()) {
            return true;
        }

        String baseCommand = extractBaseCommand(args.get(0));

        if (BLOCKED_COMMANDS.contains(baseCommand)) {
            return false;
        }

        if (containsDangerousOperator(trimmed)) {
            return false;
        }

        return ALLOWED_COMMANDS.contains(baseCommand);
    }

    private String getBlockReason(String commandString) {
        String trimmed = commandString.trim();
        List<String> args = parseCommand(trimmed);
        if (args.isEmpty()) {
            return "Empty command";
        }

        String baseCommand = extractBaseCommand(args.get(0));

        if (BLOCKED_COMMANDS.contains(baseCommand)) {
            return "Command '" + baseCommand + "' is in the blocklist (dangerous operation)";
        }

        if (containsDangerousOperator(trimmed)) {
            return "Command contains dangerous operator (|, >, >> are restricted in safe mode)";
        }

        if (!ALLOWED_COMMANDS.contains(baseCommand)) {
            return "Command '" + baseCommand + "' is not in the whitelist";
        }

        return "Unknown reason";
    }

    private String extractBaseCommand(String command) {
        if (command.startsWith("./") || command.startsWith("../")) {
            return command;
        }
        int lastSlash = command.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < command.length() - 1) {
            return command.substring(lastSlash + 1);
        }
        return command;
    }

    private boolean containsDangerousOperator(String command) {
        return command.contains("|") ||
               command.contains(">>") ||
               (command.contains(">") && !command.contains("->"));
    }

    public ShellSession enableSafeMode() {
        this.safeMode = true;
        this.closeStdinImmediately = true;

        this.environment.put("CI", "true");
        this.environment.put("DEBIAN_FRONTEND", "noninteractive");
        this.environment.put("TERM", "dumb");

        LOG.info("[SHELL-SAFE] Safe mode enabled");
        return this;
    }

    public ShellSession disableSafeMode() {
        this.safeMode = false;
        this.closeStdinImmediately = false;
        LOG.info("[SHELL-SAFE] Safe mode disabled");
        return this;
    }

    public boolean isSafeModeEnabled() {
        return safeMode;
    }

    public static Set<String> getAllowedCommands() {
        return Set.copyOf(ALLOWED_COMMANDS);
    }

    public static Set<String> getBlockedCommands() {
        return Set.copyOf(BLOCKED_COMMANDS);
    }
}
