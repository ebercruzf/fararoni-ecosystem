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

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.ToolParameter;
import dev.fararoni.bus.agent.api.ToolSkill;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SystemSkill implements ToolSkill {
    private static final String SKILL_NAME = "SYSTEM";
    private static final int COMMAND_TIMEOUT_SECONDS = 30;

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
        "date",
        "whoami",
        "pwd",
        "hostname",
        "uname",
        "uptime",
        "df",
        "free",
        "echo",
        "cat",
        "head",
        "tail",
        "wc",
        "ls",
        "find",
        "grep",
        "which",
        "env",
        "printenv",
        "id",
        "groups"
    );

    private static final Set<String> BLOCKED_PATTERNS = Set.of(
        "rm -rf",
        "rm -r",
        "sudo",
        "su ",
        "chmod 777",
        "chown",
        "> /",
        ">> /",
        "| sh",
        "| bash",
        "`",
        "$(",
        "eval",
        "exec",
        "curl",
        "wget",
        "nc ",
        "netcat"
    );

    private final Path workspaceRoot;

    public SystemSkill(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot != null
            ? workspaceRoot.toAbsolutePath().normalize()
            : Path.of(System.getProperty("user.dir"));
    }

    public SystemSkill() {
        this(null);
    }

    @Override
    public String getSkillName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return "Execute safe system commands (date, whoami, pwd, etc.)";
    }

    @AgentAction(
        name = "exec",
        description = "Execute a system command (whitelist: date, whoami, pwd, hostname, uname, uptime, ls, cat, head, tail)"
    )
    public String exec(
        @ToolParameter(
            name = "command",
            description = "The command to execute (e.g., 'date', 'whoami', 'pwd')"
        ) String command
    ) {
        if (command == null || command.isBlank()) {
            return "Error: Command is required";
        }

        String validation = validateCommand(command);
        if (validation != null) {
            return validation;
        }

        try {
            return executeCommand(command);
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    @AgentAction(
        name = "info",
        description = "Get basic system information (OS, user, hostname)"
    )
    public String info() {
        StringBuilder sb = new StringBuilder();
        sb.append("OS: ").append(System.getProperty("os.name")).append("\n");
        sb.append("Version: ").append(System.getProperty("os.version")).append("\n");
        sb.append("Arch: ").append(System.getProperty("os.arch")).append("\n");
        sb.append("User: ").append(System.getProperty("user.name")).append("\n");
        sb.append("Home: ").append(System.getProperty("user.home")).append("\n");
        sb.append("Java: ").append(System.getProperty("java.version"));
        return sb.toString();
    }

    @AgentAction(
        name = "env",
        description = "Get the value of an environment variable"
    )
    public String env(
        @ToolParameter(
            name = "name",
            description = "The environment variable name (e.g., 'HOME', 'PATH', 'USER')"
        ) String name
    ) {
        if (name == null || name.isBlank()) {
            return "Error: Variable name is required";
        }

        String upper = name.toUpperCase();
        if (upper.contains("PASSWORD") || upper.contains("SECRET") ||
            upper.contains("TOKEN") || upper.contains("KEY") ||
            upper.contains("CREDENTIAL")) {
            return "Error: Cannot access sensitive environment variables";
        }

        String value = System.getenv(name);
        return value != null ? value : "Not set";
    }

    @AgentAction(
        name = "pwd",
        description = "Get the current working directory"
    )
    public String pwd() {
        return workspaceRoot.toString();
    }

    @AgentAction(
        name = "whoami",
        description = "Get the current username"
    )
    public String whoami() {
        return System.getProperty("user.name");
    }

    private String validateCommand(String command) {
        String lowerCommand = command.toLowerCase().trim();

        for (String blocked : BLOCKED_PATTERNS) {
            if (lowerCommand.contains(blocked)) {
                return "Error: Command contains blocked pattern: " + blocked;
            }
        }

        String baseCommand = lowerCommand.split("\\s+")[0];

        if (!ALLOWED_COMMANDS.contains(baseCommand)) {
            return "Error: Command '" + baseCommand + "' is not in whitelist. " +
                   "Allowed: " + String.join(", ", ALLOWED_COMMANDS);
        }

        return null;
    }

    private String executeCommand(String command) throws IOException, InterruptedException {
        List<String> cmdList = new ArrayList<>();

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            cmdList.add("cmd.exe");
            cmdList.add("/c");
        } else {
            cmdList.add("/bin/sh");
            cmdList.add("-c");
        }
        cmdList.add(command);

        ProcessBuilder pb = new ProcessBuilder(cmdList);
        pb.directory(workspaceRoot.toFile());
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

        boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return "Error: Command timed out after " + COMMAND_TIMEOUT_SECONDS + " seconds";
        }

        int exitCode = process.exitValue();
        String result = output.toString().trim();

        if (exitCode != 0) {
            return "Error (exit " + exitCode + "): " + result;
        }

        return result.isEmpty() ? "(no output)" : result;
    }
}
