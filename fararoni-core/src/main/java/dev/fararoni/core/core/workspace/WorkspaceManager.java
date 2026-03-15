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
package dev.fararoni.core.core.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class WorkspaceManager {
    private static final String DEFAULT_DIR_NAME = ".llm-fararoni";

    private static final String ENV_VAR_NAME = "LLM_FARARONI_HOME";

    private static final String CLI_ARG_PREFIX = "--data-dir";

    private static final String PORTABLE_MARKER_FILE = ".llm-fararoni-portable";

    private static final String MEMORY_DB_FILE = "memory.db";
    private static final String INTERACTIONS_DB_FILE = "interactions.db";
    private static final String CACHE_DB_FILE = "cache.db";

    private static final String AUDIT_LOG_FILE = "audit.log";
    private static final String CONFIG_FILE = "config.properties";
    private static final String QUEUE_FILE = "queue.json";

    private static final String MODELS_DIR = "models";
    private static final String LIB_DIR = "lib";
    private static final String ROUTER_LOGS_FILE = "router_logs.jsonl";

    private static volatile WorkspaceManager instance;
    private static final Object LOCK = new Object();
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private final Path workspaceDir;
    private final ResolutionMode resolutionMode;
    private final String resolutionSource;

    public enum ResolutionMode {
        CLI_ARGUMENT("CLI Argument"),

        ENVIRONMENT_VARIABLE("Environment Variable"),

        PORTABLE_MODE("Portable Mode"),

        USER_HOME_DEFAULT("User Home Default");

        private final String displayName;

        ResolutionMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private WorkspaceManager(Path workspaceDir, ResolutionMode mode, String source) {
        this.workspaceDir = Objects.requireNonNull(workspaceDir, "workspaceDir cannot be null");
        this.resolutionMode = Objects.requireNonNull(mode, "resolutionMode cannot be null");
        this.resolutionSource = source != null ? source : mode.getDisplayName();

        ensureDirectoryExists(workspaceDir);
    }

    public static WorkspaceManager initialize(String[] args) {
        if (initialized.get()) {
            throw new IllegalStateException(
                "WorkspaceManager already initialized. Use getInstance() to access it.");
        }

        synchronized (LOCK) {
            if (instance != null) {
                throw new IllegalStateException(
                    "WorkspaceManager already initialized. Use getInstance() to access it.");
            }

            Resolution resolution = resolveWorkspace(args);
            instance = new WorkspaceManager(
                resolution.path(),
                resolution.mode(),
                resolution.source()
            );
            initialized.set(true);

            logInitialization(instance);
            return instance;
        }
    }

    public static WorkspaceManager getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    Resolution resolution = resolveWorkspace(new String[0]);
                    instance = new WorkspaceManager(
                        resolution.path(),
                        resolution.mode(),
                        resolution.source()
                    );
                    initialized.set(true);

                    logInitialization(instance);
                }
            }
        }
        return instance;
    }

    public static void reset() {
        synchronized (LOCK) {
            instance = null;
            initialized.set(false);
        }
    }

    public static boolean isInitialized() {
        return initialized.get();
    }

    private record Resolution(Path path, ResolutionMode mode, String source) {}

    private static Resolution resolveWorkspace(String[] args) {
        String cliPath = parseCliDataDir(args);
        if (cliPath != null && !cliPath.isBlank()) {
            Path path = Paths.get(cliPath).toAbsolutePath().normalize();
            return new Resolution(path, ResolutionMode.CLI_ARGUMENT, cliPath);
        }

        String envPath = System.getenv(ENV_VAR_NAME);
        if (envPath != null && !envPath.isBlank()) {
            Path path = Paths.get(envPath).toAbsolutePath().normalize();
            return new Resolution(path, ResolutionMode.ENVIRONMENT_VARIABLE, ENV_VAR_NAME + "=" + envPath);
        }

        Path portablePath = detectPortableMode();
        if (portablePath != null) {
            return new Resolution(portablePath, ResolutionMode.PORTABLE_MODE, PORTABLE_MARKER_FILE);
        }

        Path defaultPath = getDefaultPath();
        return new Resolution(defaultPath, ResolutionMode.USER_HOME_DEFAULT, "~/" + DEFAULT_DIR_NAME);
    }

    private static String parseCliDataDir(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.startsWith(CLI_ARG_PREFIX + "=")) {
                return arg.substring((CLI_ARG_PREFIX + "=").length());
            }

            if (arg.equals(CLI_ARG_PREFIX) && i + 1 < args.length) {
                return args[i + 1];
            }
        }

        return null;
    }

    private static Path detectPortableMode() {
        try {
            Path jarLocation = getJarLocation();
            if (jarLocation == null) {
                return null;
            }

            Path markerFile = jarLocation.resolve(PORTABLE_MARKER_FILE);
            if (Files.exists(markerFile)) {
                return jarLocation.resolve(DEFAULT_DIR_NAME);
            }
        } catch (Exception e) {
        }
        return null;
    }

    private static Path getJarLocation() {
        try {
            Path path = Paths.get(
                WorkspaceManager.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
            );

            if (Files.isRegularFile(path)) {
                return path.getParent();
            }

            return path;
        } catch (Exception e) {
            return null;
        }
    }

    private static Path getDefaultPath() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            userHome = System.getProperty("user.dir", ".");
        }
        return Paths.get(userHome, DEFAULT_DIR_NAME).toAbsolutePath().normalize();
    }

    public Path getWorkspaceDir() {
        return workspaceDir;
    }

    public Path resolve(String relativePath) {
        return workspaceDir.resolve(relativePath);
    }

    public Path getMemoryDbPath() {
        return workspaceDir.resolve(MEMORY_DB_FILE);
    }

    public String getMemoryDbUrl() {
        return "jdbc:sqlite:" + getMemoryDbPath().toAbsolutePath();
    }

    public Path getInteractionsDbPath() {
        return workspaceDir.resolve(INTERACTIONS_DB_FILE);
    }

    public String getInteractionsDbUrl() {
        return "jdbc:sqlite:" + getInteractionsDbPath().toAbsolutePath();
    }

    public Path getCacheDbPath() {
        return workspaceDir.resolve(CACHE_DB_FILE);
    }

    public String getCacheDbUrl() {
        return "jdbc:sqlite:" + getCacheDbPath().toAbsolutePath();
    }

    public Path getAuditLogPath() {
        return workspaceDir.resolve(AUDIT_LOG_FILE);
    }

    public Path getConfigPath() {
        return workspaceDir.resolve(CONFIG_FILE);
    }

    public Path getQueuePath() {
        return workspaceDir.resolve(QUEUE_FILE);
    }

    public Path getModelsDir() {
        Path modelsPath = workspaceDir.resolve(MODELS_DIR);
        ensureSubdirectoryExists(modelsPath);
        return modelsPath;
    }

    public Path getLibDir() {
        Path libPath = workspaceDir.resolve(LIB_DIR);
        ensureSubdirectoryExists(libPath);
        return libPath;
    }

    public Path getRouterLogsPath() {
        return workspaceDir.resolve(ROUTER_LOGS_FILE);
    }

    private void ensureSubdirectoryExists(Path subdir) {
        if (!Files.exists(subdir)) {
            try {
                Files.createDirectories(subdir);
            } catch (IOException e) {
                throw new WorkspaceException(
                    "Failed to create subdirectory: " + subdir, e);
            }
        }
    }

    public ResolutionMode getResolutionMode() {
        return resolutionMode;
    }

    public String getResolutionSource() {
        return resolutionSource;
    }

    public boolean isPortableMode() {
        return resolutionMode == ResolutionMode.PORTABLE_MODE;
    }

    public boolean isCustomPath() {
        return resolutionMode != ResolutionMode.USER_HOME_DEFAULT;
    }

    private void ensureDirectoryExists(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new WorkspaceException(
                    "Failed to create workspace directory: " + dir, e);
            }
        }

        if (!Files.isDirectory(dir)) {
            throw new WorkspaceException(
                "Workspace path exists but is not a directory: " + dir);
        }

        if (!Files.isWritable(dir)) {
            throw new WorkspaceException(
                "Workspace directory is not writable: " + dir);
        }
    }

    public boolean isValid() {
        return Files.exists(workspaceDir)
            && Files.isDirectory(workspaceDir)
            && Files.isWritable(workspaceDir);
    }

    public String getInfoString() {
        return String.format(
            "Workspace: %s [%s]",
            workspaceDir.toAbsolutePath(),
            resolutionMode.getDisplayName()
        );
    }

    private static void logInitialization(WorkspaceManager ws) {
        System.out.printf("[WorkspaceManager] Initialized: %s (%s)%n",
            ws.workspaceDir.toAbsolutePath(),
            ws.resolutionMode.getDisplayName());
    }

    @Override
    public String toString() {
        return "WorkspaceManager{" +
            "workspaceDir=" + workspaceDir +
            ", resolutionMode=" + resolutionMode +
            ", resolutionSource='" + resolutionSource + '\'' +
            '}';
    }

    public static class WorkspaceException extends RuntimeException {
        public WorkspaceException(String message) {
            super(message);
        }

        public WorkspaceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
