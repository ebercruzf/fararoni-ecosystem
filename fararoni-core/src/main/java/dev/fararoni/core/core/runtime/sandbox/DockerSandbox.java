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
package dev.fararoni.core.core.runtime.sandbox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class DockerSandbox {
    private static final Logger LOG = Logger.getLogger(DockerSandbox.class.getName());

    public static final String WORKSPACE_PATH = "/workspace";

    private static final int START_TIMEOUT_SECONDS = 30;

    private static final int EXECUTE_TIMEOUT_SECONDS = 300;

    private static volatile Boolean dockerAvailableCache = null;

    private static volatile long dockerCheckTimestamp = 0;

    private static final long DOCKER_CHECK_CACHE_TTL_MS = 60_000;

    public static final String DEFAULT_MEMORY_LIMIT = "1g";

    public static final String DEFAULT_CPU_LIMIT = "1.5";

    public static final String DEFAULT_PIDS_LIMIT = "100";

    public static final String IMAGE_NODE_18 = "node:18-alpine";
    public static final String IMAGE_NODE_20 = "node:20-alpine";
    public static final String IMAGE_PYTHON_39 = "python:3.9-slim";
    public static final String IMAGE_PYTHON_311 = "python:3.11-slim";
    public static final String IMAGE_JAVA_17 = "eclipse-temurin:17-jdk-alpine";
    public static final String IMAGE_JAVA_21 = "eclipse-temurin:21-jdk-alpine";
    public static final String IMAGE_GO = "golang:1.21-alpine";
    public static final String IMAGE_RUST = "rust:1.75-alpine";

    public record SandboxResult(
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
            if (stderr == null || stderr.isBlank()) return stdout;
            return stdout + "\n[STDERR]\n" + stderr;
        }
    }

    private String containerId;
    private final String imageName;
    private Path mountedProjectRoot;
    private boolean running;
    private final Map<String, String> envVars;
    private final List<String> capabilities;

    private String memoryLimit = DEFAULT_MEMORY_LIMIT;
    private String cpuLimit = DEFAULT_CPU_LIMIT;
    private String pidsLimit = DEFAULT_PIDS_LIMIT;
    private boolean mapUserEnabled = true;

    public DockerSandbox(String imageName) {
        this.imageName = imageName != null ? imageName : IMAGE_NODE_18;
        this.running = false;
        this.envVars = new HashMap<>();
        this.capabilities = new ArrayList<>();
    }

    public DockerSandbox withEnv(String key, String value) {
        envVars.put(key, value);
        return this;
    }

    public DockerSandbox withCapability(String capability) {
        capabilities.add(capability);
        return this;
    }

    public DockerSandbox withMemoryLimit(String limit) {
        this.memoryLimit = limit;
        return this;
    }

    public DockerSandbox withCpuLimit(String limit) {
        this.cpuLimit = limit;
        return this;
    }

    public DockerSandbox withPidsLimit(String limit) {
        this.pidsLimit = limit;
        return this;
    }

    public DockerSandbox withUserMapping(boolean enabled) {
        this.mapUserEnabled = enabled;
        return this;
    }

    public static boolean isDockerAvailable() {
        long now = System.currentTimeMillis();

        if (dockerAvailableCache != null && (now - dockerCheckTimestamp) < DOCKER_CHECK_CACHE_TTL_MS) {
            LOG.fine("[DOCKER-CACHE] Usando resultado cacheado: " + dockerAvailableCache);
            return dockerAvailableCache;
        }

        boolean available = checkDockerDaemon();

        dockerAvailableCache = available;
        dockerCheckTimestamp = now;

        LOG.info("[DOCKER-CACHE] Verificación fresca: disponible=" + available + " (TTL=60s)");
        return available;
    }

    private static boolean checkDockerDaemon() {
        try {
            Process p = new ProcessBuilder("docker", "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            LOG.fine("[DOCKER-CACHE] Error verificando Docker: " + e.getMessage());
            return false;
        }
    }

    public static void invalidateDockerCache() {
        dockerAvailableCache = null;
        dockerCheckTimestamp = 0;
        LOG.info("[DOCKER-CACHE] Caché invalidado manualmente");
    }

    public void startSandbox(Path projectRoot) throws SandboxException {
        if (running) {
            throw new SandboxException("Sandbox already running");
        }

        if (!isDockerAvailable()) {
            throw new SandboxException("Docker is not available");
        }

        this.mountedProjectRoot = projectRoot.toAbsolutePath();
        LOG.info("[SANDBOX] Starting with image: " + imageName);

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");
        cmd.add("--rm");

        cmd.add("-v");
        cmd.add(mountedProjectRoot + ":" + WORKSPACE_PATH);

        cmd.add("-w");
        cmd.add(WORKSPACE_PATH);

        cmd.add("--memory=" + memoryLimit);

        cmd.add("--cpus=" + cpuLimit);

        cmd.add("--pids-limit=" + pidsLimit);

        if (mapUserEnabled && !isWindows()) {
            String uid = getUid();
            String gid = getGid();
            if (uid != null && gid != null) {
                cmd.add("-u");
                cmd.add(uid + ":" + gid);
                LOG.fine("[SANDBOX] User mapping: " + uid + ":" + gid);
            }
        }

        cmd.add("-e");
        cmd.add("CI=true");
        cmd.add("-e");
        cmd.add("TERM=dumb");

        for (Map.Entry<String, String> e : envVars.entrySet()) {
            cmd.add("-e");
            cmd.add(e.getKey() + "=" + e.getValue());
        }

        for (String cap : capabilities) {
            cmd.add("--cap-add");
            cmd.add(cap);
        }

        cmd.add(imageName);
        cmd.add("tail");
        cmd.add("-f");
        cmd.add("/dev/null");

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            Process p = pb.start();
            boolean finished = p.waitFor(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                p.destroyForcibly();
                throw new SandboxException("Timeout starting container");
            }

            if (p.exitValue() != 0) {
                String error = readOutput(p);
                throw new SandboxException("Failed to start container: " + error);
            }

            String fullId = readOutput(p).trim();
            this.containerId = fullId.length() >= 12 ? fullId.substring(0, 12) : fullId;
            this.running = true;

            LOG.info("[SANDBOX] Container started: " + containerId);
        } catch (IOException | InterruptedException e) {
            throw new SandboxException("Error starting sandbox: " + e.getMessage(), e);
        }
    }

    public SandboxResult execute(String... command) throws SandboxException {
        return execute(EXECUTE_TIMEOUT_SECONDS, command);
    }

    public SandboxResult execute(int timeoutSeconds, String... command) throws SandboxException {
        if (!running || containerId == null) {
            throw new SandboxException("Sandbox not running");
        }

        String commandStr = String.join(" ", command);
        LOG.fine("[SANDBOX] Executing: " + commandStr);

        long startTime = System.currentTimeMillis();

        List<String> dockerCmd = new ArrayList<>();
        dockerCmd.add("docker");
        dockerCmd.add("exec");
        dockerCmd.add(containerId);

        for (String arg : command) {
            dockerCmd.add(arg);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(dockerCmd);
            Process p = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread outThread = new Thread(() -> readStream(p.getInputStream(), stdout));
            Thread errThread = new Thread(() -> readStream(p.getErrorStream(), stderr));
            outThread.start();
            errThread.start();

            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            outThread.join(1000);
            errThread.join(1000);

            long duration = System.currentTimeMillis() - startTime;

            if (!finished) {
                p.destroyForcibly();
                return new SandboxResult(-1, stdout.toString(),
                        "Timeout after " + timeoutSeconds + " seconds", commandStr, duration);
            }

            return new SandboxResult(
                    p.exitValue(),
                    stdout.toString().trim(),
                    stderr.toString().trim(),
                    commandStr,
                    duration
            );
        } catch (IOException | InterruptedException e) {
            throw new SandboxException("Error executing command: " + e.getMessage(), e);
        }
    }

    public SandboxResult executeShell(String shellCommand) throws SandboxException {
        return execute("sh", "-c", shellCommand);
    }

    public void destroySandbox() {
        if (containerId == null) {
            return;
        }

        LOG.info("[SANDBOX] Destroying container: " + containerId);

        try {
            new ProcessBuilder("docker", "kill", containerId)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warning("[SANDBOX] Error destroying container: " + e.getMessage());
        }

        containerId = null;
        running = false;
        LOG.info("[SANDBOX] Container destroyed");
    }

    public boolean isRunning() {
        return running && containerId != null;
    }

    public String getContainerId() {
        return containerId;
    }

    public String getImageName() {
        return imageName;
    }

    public Path getMountedProjectRoot() {
        return mountedProjectRoot;
    }

    private String readOutput(Process p) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private void readStream(java.io.InputStream is, StringBuilder sb) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    private static String getUid() {
        try {
            Process p = new ProcessBuilder("id", "-u")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    return reader.readLine().trim();
                }
            }
        } catch (Exception e) {
            LOG.fine("[SANDBOX] Could not get UID: " + e.getMessage());
        }
        return null;
    }

    private static String getGid() {
        try {
            Process p = new ProcessBuilder("id", "-g")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    return reader.readLine().trim();
                }
            }
        } catch (Exception e) {
            LOG.fine("[SANDBOX] Could not get GID: " + e.getMessage());
        }
        return null;
    }

    public static class SandboxException extends Exception {
        public SandboxException(String message) {
            super(message);
        }

        public SandboxException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
