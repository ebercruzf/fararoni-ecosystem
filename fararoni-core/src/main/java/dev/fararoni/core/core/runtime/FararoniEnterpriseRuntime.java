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

import dev.fararoni.core.core.runtime.jobs.JobManager;
import dev.fararoni.core.core.runtime.pty.InteractiveShell;
import dev.fararoni.core.core.runtime.sandbox.DockerSandbox;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class FararoniEnterpriseRuntime implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(FararoniEnterpriseRuntime.class.getName());

    public enum ExecutionMode {
        LOCAL,
        SANDBOXED,
        HYBRID
    }

    private final Path projectRoot;
    private final ExecutionMode mode;
    private final ShellSession localShell;
    private final JobManager jobManager;
    private final DockerSandbox sandbox;
    private final String dockerImage;
    private final Consumer<String> outputListener;
    private boolean sandboxStarted;

    public static class Builder {
        private Path projectRoot;
        private ExecutionMode mode = ExecutionMode.LOCAL;
        private String dockerImage = DockerSandbox.IMAGE_NODE_18;
        private Consumer<String> outputListener = line -> {};

        public Builder projectRoot(Path projectRoot) {
            this.projectRoot = projectRoot;
            return this;
        }

        public Builder mode(ExecutionMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder enableSandbox(boolean enable) {
            this.mode = enable ? ExecutionMode.SANDBOXED : ExecutionMode.LOCAL;
            return this;
        }

        public Builder dockerImage(String dockerImage) {
            this.dockerImage = dockerImage;
            return this;
        }

        public Builder outputListener(Consumer<String> listener) {
            this.outputListener = listener;
            return this;
        }

        public FararoniEnterpriseRuntime build() {
            if (projectRoot == null) {
                projectRoot = Path.of(".");
            }
            return new FararoniEnterpriseRuntime(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private FararoniEnterpriseRuntime(Builder builder) {
        this.projectRoot = builder.projectRoot.toAbsolutePath();
        this.mode = builder.mode;
        this.dockerImage = builder.dockerImage;
        this.outputListener = builder.outputListener;
        this.sandboxStarted = false;

        this.localShell = new ShellSession(projectRoot);
        this.localShell.enableSafeMode();

        this.jobManager = new JobManager();

        this.sandbox = (mode == ExecutionMode.SANDBOXED || mode == ExecutionMode.HYBRID)
                ? new DockerSandbox(dockerImage)
                : null;

        LOG.info("[ENTERPRISE-RUNTIME] Initialized in " + mode + " mode at: " + projectRoot);
    }

    public record ExecutionResult(
            boolean success,
            String stdout,
            String stderr,
            String command,
            long durationMs,
            boolean wasSandboxed
    ) {
        public String getCombinedOutput() {
            if (stderr == null || stderr.isBlank()) return stdout;
            return stdout + "\n[STDERR]\n" + stderr;
        }
    }

    public ExecutionResult execute(String... command) {
        String commandStr = String.join(" ", command);
        LOG.info("[ENTERPRISE-RUNTIME] Executing: " + commandStr);

        return switch (mode) {
            case LOCAL -> executeLocal(command);
            case SANDBOXED -> executeSandboxed(command);
            case HYBRID -> executeHybrid(command);
        };
    }

    private ExecutionResult executeLocal(String... command) {
        String commandStr = String.join(" ", command);
        ShellSession.CommandResult result = localShell.execute(commandStr);

        outputListener.accept(result.stdout());

        return new ExecutionResult(
                result.isSuccess(),
                result.stdout(),
                result.stderr(),
                commandStr,
                result.durationMs(),
                false
        );
    }

    private ExecutionResult executeSandboxed(String... command) {
        ensureSandboxStarted();

        try {
            DockerSandbox.SandboxResult result = sandbox.execute(command);

            outputListener.accept(result.stdout());

            return new ExecutionResult(
                    result.isSuccess(),
                    result.stdout(),
                    result.stderr(),
                    result.command(),
                    result.durationMs(),
                    true
            );
        } catch (DockerSandbox.SandboxException e) {
            LOG.severe("[ENTERPRISE-RUNTIME] Sandbox error: " + e.getMessage());
            return new ExecutionResult(false, "", e.getMessage(),
                    String.join(" ", command), 0, true);
        }
    }

    private ExecutionResult executeHybrid(String... command) {
        String baseCmd = command.length > 0 ? command[0] : "";

        if (isDangerousCommand(baseCmd)) {
            LOG.info("[ENTERPRISE-RUNTIME] Routing to sandbox: " + baseCmd);
            return executeSandboxed(command);
        }

        return executeLocal(command);
    }

    private boolean isDangerousCommand(String command) {
        return command.equals("sh") ||
               command.equals("bash") ||
               command.equals("eval") ||
               command.equals("exec") ||
               command.contains("curl") ||
               command.contains("wget");
    }

    private void ensureSandboxStarted() {
        if (sandbox != null && !sandboxStarted) {
            try {
                sandbox.startSandbox(projectRoot);
                sandboxStarted = true;
            } catch (DockerSandbox.SandboxException e) {
                throw new RuntimeException("Failed to start sandbox: " + e.getMessage(), e);
            }
        }
    }

    public String startServerInBackground(String... command) {
        try {
            String jobId = jobManager.startBackgroundJob(projectRoot, command);
            LOG.info("[ENTERPRISE-RUNTIME] Started background job: " + jobId);
            return jobId;
        } catch (IOException e) {
            throw new RuntimeException("Failed to start background job: " + e.getMessage(), e);
        }
    }

    public boolean stopBackgroundJob(String jobId) {
        return jobManager.killJob(jobId);
    }

    public String getJobLogs(String jobId, int lines) {
        return jobManager.getJobLog(jobId, lines);
    }

    public boolean isJobAlive(String jobId) {
        return jobManager.isJobAlive(jobId);
    }

    public int executeInteractive(Predicate<String> autoResponder, String response, String... command) {
        try {
            final InteractiveShell[] shellRef = new InteractiveShell[1];

            shellRef[0] = new InteractiveShell(projectRoot, line -> {
                outputListener.accept(line);

                if (autoResponder.test(line) && shellRef[0] != null) {
                    LOG.info("[ENTERPRISE-RUNTIME] Auto-responding to prompt");
                    shellRef[0].writeInput(response);
                }
            });

            shellRef[0].start(command);
            return shellRef[0].waitFor();
        } catch (Exception e) {
            LOG.severe("[ENTERPRISE-RUNTIME] Interactive execution failed: " + e.getMessage());
            return -1;
        }
    }

    public int executeWithAutoConfirm(String... command) {
        return executeInteractive(
                line -> InteractiveShell.isInteractivePrompt(line),
                "y\n",
                command
        );
    }

    public boolean waitForPort(int port, int timeoutSeconds) {
        LOG.info("[ENTERPRISE-RUNTIME] Waiting for port " + port);

        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

        while (System.currentTimeMillis() < deadline) {
            try {
                try (java.net.Socket socket = new java.net.Socket("localhost", port)) {
                    LOG.info("[ENTERPRISE-RUNTIME] Port " + port + " is ready");
                    return true;
                }
            } catch (IOException e) {
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        LOG.warning("[ENTERPRISE-RUNTIME] Timeout waiting for port " + port);
        return false;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public boolean isDockerAvailable() {
        return DockerSandbox.isDockerAvailable();
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public ShellSession getLocalShell() {
        return localShell;
    }

    public Optional<DockerSandbox> getSandbox() {
        return Optional.ofNullable(sandbox);
    }

    public void shutdown() {
        LOG.info("[ENTERPRISE-RUNTIME] Shutting down...");

        jobManager.killAll();

        if (sandbox != null && sandboxStarted) {
            sandbox.destroySandbox();
        }

        LOG.info("[ENTERPRISE-RUNTIME] Shutdown complete");
    }

    @Override
    public void close() {
        shutdown();
    }
}
