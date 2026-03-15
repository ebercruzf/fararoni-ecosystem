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
package dev.fararoni.bus.agent.api.skills;

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.Sanitized;
import dev.fararoni.bus.agent.api.ToolSkill;
import dev.fararoni.bus.agent.api.security.AuditLog;
import dev.fararoni.bus.agent.api.security.RateLimit;
import dev.fararoni.bus.agent.api.security.RequiresRole;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Contract for shell/terminal command execution.
 *
 * <p>This interface defines how the AI agent can execute shell commands.
 * All commands are sandboxed, rate-limited, and logged for security.</p>
 *
 * <h2>Security Model</h2>
 * <pre>
 * Agent Request: "run ls -la /etc/passwd"
 *       │
 *       ▼
 * ┌─────────────────────────────────────────────────┐
 * │ 1. Rate Limit Check (10 calls/minute)           │
 * │ 2. Role Check (terminal:execute required)       │
 * │ 3. Command Sanitization                         │
 * │    - Block dangerous patterns (rm -rf /, etc.)  │
 * │    - Validate working directory in sandbox      │
 * │ 4. Audit Log Entry                              │
 * │ 5. Execute with timeout                         │
 * │ 6. Capture stdout/stderr                        │
 * └─────────────────────────────────────────────────┘
 *       │
 *       ▼
 * Response: ExecutionResult(exitCode, stdout, stderr, duration)
 * </pre>
 *
 * <h2>Blocked Commands</h2>
 * <p>The following patterns are blocked by default:</p>
 * <ul>
 *   <li>{@code rm -rf /} - Destructive system wipe</li>
 *   <li>{@code dd if=/dev/zero} - Disk destruction</li>
 *   <li>{@code :(){ :|:& };:} - Fork bombs</li>
 *   <li>{@code chmod 777 /} - Permission disasters</li>
 *   <li>Commands accessing outside workspace sandbox</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Simple command
 * FNLResult<ExecutionResult> result = terminal.execute("ls -la");
 *
 * if (result.success()) {
 *     String output = result.data().stdout();
 *     int exitCode = result.data().exitCode();
 * }
 *
 * // Command with environment and timeout
 * FNLResult<ExecutionResult> build = terminal.executeWithOptions(
 *     "npm run build",
 *     "/app",                          // working directory
 *     Map.of("NODE_ENV", "production"), // environment
 *     120_000                           // 2 minute timeout
 * );
 * }</pre>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 */
public interface TerminalSkill extends ToolSkill {

    /**
     * Executes a shell command with default settings.
     *
     * <p>Uses current working directory, default environment, and
     * default timeout (30 seconds).</p>
     *
     * @param command the command to execute
     * @return result containing execution output
     */
    @AgentAction(
        name = "execute",
        description = "Executes a shell command and returns stdout/stderr"
    )
    @RequiresRole("terminal:execute")
    @RateLimit(calls = 10, period = 1, unit = TimeUnit.MINUTES)
    @AuditLog(severity = "WARN", category = "SHELL_EXEC", captureInputs = true)
    FNLResult<ExecutionResult> execute(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.SHELL) String command
    );

    /**
     * Executes a command with custom options.
     *
     * @param command the command to execute
     * @param workingDirectory the directory to run from
     * @param environment additional environment variables
     * @param timeoutMs timeout in milliseconds
     * @return result containing execution output
     */
    @AgentAction(
        name = "execute_with_options",
        description = "Executes command with custom working directory, environment, and timeout"
    )
    @RequiresRole("terminal:execute")
    @RateLimit(calls = 10, period = 1, unit = TimeUnit.MINUTES)
    @AuditLog(severity = "WARN", category = "SHELL_EXEC", captureInputs = true)
    FNLResult<ExecutionResult> executeWithOptions(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.SHELL) String command,
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String workingDirectory,
        Map<String, String> environment,
        long timeoutMs
    );

    /**
     * Executes a command and streams output in real-time.
     *
     * <p>For long-running commands where you need incremental output.</p>
     *
     * @param command the command to execute
     * @param outputHandler callback for each line of output
     * @param timeoutMs timeout in milliseconds
     * @return result containing final execution status
     */
    @AgentAction(
        name = "execute_streaming",
        description = "Executes command with real-time output streaming"
    )
    @RequiresRole("terminal:execute")
    @RateLimit(calls = 5, period = 1, unit = TimeUnit.MINUTES)
    @AuditLog(severity = "WARN", category = "SHELL_EXEC_STREAM")
    FNLResult<ExecutionResult> executeStreaming(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.SHELL) String command,
        OutputHandler outputHandler,
        long timeoutMs
    );

    /**
     * Checks if a command is allowed by the security policy.
     *
     * <p>Use this to pre-validate commands before execution.</p>
     *
     * @param command the command to check
     * @return result with true if allowed, false with reason if blocked
     */
    @AgentAction(
        name = "is_command_allowed",
        description = "Checks if a command passes security validation"
    )
    FNLResult<Boolean> isCommandAllowed(String command);

    /**
     * Gets the current working directory.
     *
     * @return result containing the working directory path
     */
    @AgentAction(
        name = "pwd",
        description = "Returns current working directory"
    )
    FNLResult<String> getWorkingDirectory();

    /**
     * Gets the value of an environment variable.
     *
     * @param name the variable name
     * @return result containing the value, or failure if not set
     */
    @AgentAction(
        name = "get_env",
        description = "Gets an environment variable value"
    )
    FNLResult<String> getEnvironmentVariable(String name);

    // ==================== Nested Types ====================

    /**
     * Result of a command execution.
     *
     * @param exitCode the process exit code (0 = success)
     * @param stdout standard output content
     * @param stderr standard error content
     * @param durationMs execution duration in milliseconds
     * @param timedOut whether execution was terminated due to timeout
     * @param command the executed command (for audit)
     */
    record ExecutionResult(
        int exitCode,
        String stdout,
        String stderr,
        long durationMs,
        boolean timedOut,
        String command
    ) {
        /**
         * Checks if the command succeeded (exit code 0).
         *
         * @return true if successful
         */
        public boolean success() {
            return exitCode == 0 && !timedOut;
        }

        /**
         * Gets combined stdout and stderr.
         *
         * @return combined output
         */
        public String combinedOutput() {
            StringBuilder sb = new StringBuilder();
            if (stdout != null && !stdout.isEmpty()) {
                sb.append(stdout);
            }
            if (stderr != null && !stderr.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(stderr);
            }
            return sb.toString();
        }
    }

    /**
     * Callback interface for streaming command output.
     */
    @FunctionalInterface
    interface OutputHandler {
        /**
         * Called for each line of output.
         *
         * @param line the output line
         * @param isError true if from stderr, false if from stdout
         */
        void onOutput(String line, boolean isError);
    }
}
