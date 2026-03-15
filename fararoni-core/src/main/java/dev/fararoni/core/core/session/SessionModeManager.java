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
package dev.fararoni.core.core.session;

import dev.fararoni.core.core.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SessionModeManager {
    private static final Logger log = LoggerFactory.getLogger(SessionModeManager.class);

    private static final String CLI_INCOGNITO = "--incognito";

    private static final String CLI_PURGE = "--purge";

    private static final String ENV_PERSISTENCE = "FARARONI_PERSISTENCE";

    private static final String MEMORY_DB_URL = "jdbc:sqlite::memory:";

    private static final String FILE_DB_PREFIX = "jdbc:sqlite:";

    private static volatile SessionModeManager instance;
    private static final Object LOCK = new Object();
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private final SessionMode mode;
    private final boolean purgeRequested;
    private final String modeSource;

    public enum SessionMode {
        PERSISTENT("Persistent", "[PERSIST]", true),

        INCOGNITO("Incognito", "[INCOGNITO]", false),

        CORPORATE_NO_PERSIST("Corporate (No Persist)", "[CORPORATE]", false);

        private final String displayName;
        private final String icon;
        private final boolean persistsToDisk;

        SessionMode(String displayName, String icon, boolean persistsToDisk) {
            this.displayName = displayName;
            this.icon = icon;
            this.persistsToDisk = persistsToDisk;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIcon() {
            return icon;
        }

        public boolean persistsToDisk() {
            return persistsToDisk;
        }
    }

    private SessionModeManager(SessionMode mode, boolean purgeRequested, String modeSource) {
        this.mode = Objects.requireNonNull(mode, "mode cannot be null");
        this.purgeRequested = purgeRequested;
        this.modeSource = modeSource;
    }

    public static SessionModeManager initialize(String[] args) {
        if (initialized.get()) {
            throw new IllegalStateException(
                    "SessionModeManager already initialized. Use getInstance() to access it.");
        }

        synchronized (LOCK) {
            if (instance != null) {
                throw new IllegalStateException(
                        "SessionModeManager already initialized. Use getInstance() to access it.");
            }

            Resolution resolution = resolveMode(args);
            instance = new SessionModeManager(
                    resolution.mode(),
                    resolution.purgeRequested(),
                    resolution.source()
            );
            initialized.set(true);

            logInitialization(instance);
            return instance;
        }
    }

    public static SessionModeManager getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new SessionModeManager(
                            SessionMode.PERSISTENT,
                            false,
                            "Default (auto-initialized)"
                    );
                    initialized.set(true);
                    logInitialization(instance);
                }
            }
        }
        return instance;
    }

    public static boolean isInitialized() {
        return initialized.get();
    }

    public static void reset() {
        synchronized (LOCK) {
            instance = null;
            initialized.set(false);
        }
    }

    private record Resolution(SessionMode mode, boolean purgeRequested, String source) {}

    private static Resolution resolveMode(String[] args) {
        boolean purgeRequested = hasPurgeArg(args);

        if (hasIncognitoArg(args)) {
            return new Resolution(SessionMode.INCOGNITO, purgeRequested, CLI_INCOGNITO + " argument");
        }

        String envPersistence = System.getenv(ENV_PERSISTENCE);
        if (envPersistence != null && envPersistence.equalsIgnoreCase("false")) {
            return new Resolution(SessionMode.CORPORATE_NO_PERSIST, purgeRequested,
                    ENV_PERSISTENCE + "=false");
        }

        return new Resolution(SessionMode.PERSISTENT, purgeRequested, "Default");
    }

    private static boolean hasIncognitoArg(String[] args) {
        if (args == null) return false;
        for (String arg : args) {
            if (CLI_INCOGNITO.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPurgeArg(String[] args) {
        if (args == null) return false;
        for (String arg : args) {
            if (CLI_PURGE.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    public SessionMode getMode() {
        return mode;
    }

    public boolean isIncognito() {
        return mode == SessionMode.INCOGNITO;
    }

    public boolean isCorporateNoPerist() {
        return mode == SessionMode.CORPORATE_NO_PERSIST;
    }

    public boolean isPersistent() {
        return mode == SessionMode.PERSISTENT;
    }

    public boolean persistsToDisk() {
        return mode.persistsToDisk();
    }

    public boolean isPurgeRequested() {
        return purgeRequested;
    }

    public String getModeSource() {
        return modeSource;
    }

    public String getMemoryDbUrl() {
        if (!mode.persistsToDisk()) {
            return MEMORY_DB_URL;
        }
        return WorkspaceManager.getInstance().getMemoryDbUrl();
    }

    public String getInteractionsDbUrl() {
        if (!mode.persistsToDisk()) {
            return MEMORY_DB_URL;
        }
        return WorkspaceManager.getInstance().getInteractionsDbUrl();
    }

    public String getCacheDbUrl() {
        if (!mode.persistsToDisk()) {
            return MEMORY_DB_URL;
        }
        return WorkspaceManager.getInstance().getCacheDbUrl();
    }

    public Path getAuditLogPath() {
        if (!mode.persistsToDisk()) {
            return null;
        }
        return WorkspaceManager.getInstance().getAuditLogPath();
    }

    public Path getConfigPath() {
        if (!mode.persistsToDisk()) {
            return null;
        }
        return WorkspaceManager.getInstance().getConfigPath();
    }

    public String getInfoString() {
        return String.format("%s Session Mode: %s [%s]",
                mode.getIcon(), mode.getDisplayName(), modeSource);
    }

    public String getBannerMessage() {
        return switch (mode) {
            case INCOGNITO -> """
                    [INCOGNITO] SESION INCOGNITA ACTIVA
                    - Datos almacenados solo en memoria
                    - Historial no se guardara en disco
                    - API keys no se persistiran
                    - Todo se borrara al cerrar
                    """;
            case CORPORATE_NO_PERSIST -> """
                    [CORPORATE] MODO CORPORATIVO (SIN PERSISTENCIA)
                    - Politica FARARONI_PERSISTENCE=false activa
                    - Datos almacenados solo en memoria
                    - Cumplimiento de politicas de seguridad
                    """;
            case PERSISTENT -> null;
        };
    }

    private static void logInitialization(SessionModeManager sm) {
        log.info("[SessionModeManager] {} - persistsToDisk={}",
                sm.getInfoString(), sm.persistsToDisk());

        if (sm.purgeRequested) {
            log.info("[SessionModeManager] Data purge requested via --purge");
        }
    }

    @Override
    public String toString() {
        return "SessionModeManager{" +
                "mode=" + mode +
                ", purgeRequested=" + purgeRequested +
                ", modeSource='" + modeSource + '\'' +
                '}';
    }
}
