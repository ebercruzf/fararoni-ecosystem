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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.fararoni.core.core.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SessionContextPersistence implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SessionContextPersistence.class);
    private static final String LOG_PREFIX = "[SessionContext] ";

    private static final String SESSION_FILE_NAME = "session_context.json";

    private static final String TEMP_SUFFIX = ".tmp";

    private static final String CORRUPT_SUFFIX = ".corrupt";

    private static volatile SessionContextPersistence instance;
    private static final Object INSTANCE_LOCK = new Object();

    private final Path sessionFile;
    private final Path tempFile;
    private final ObjectMapper mapper;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final boolean persistsToDisk;

    private volatile SessionContextState currentState;

    private SessionContextPersistence() {
        SessionModeManager sessionMode = SessionModeManager.getInstance();
        this.persistsToDisk = sessionMode.persistsToDisk();

        if (persistsToDisk) {
            Path workspaceDir = WorkspaceManager.getInstance().getWorkspaceDir();
            this.sessionFile = workspaceDir.resolve(SESSION_FILE_NAME);
            this.tempFile = workspaceDir.resolve(SESSION_FILE_NAME + TEMP_SUFFIX);
        } else {
            this.sessionFile = null;
            this.tempFile = null;
            log.info(LOG_PREFIX + "Modo {} - sesion no se persistira a disco",
                sessionMode.getMode().getDisplayName());
        }

        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);

        this.currentState = loadOrCreate();

        log.info(LOG_PREFIX + "Inicializado - sessionId={}, interacciones={}",
            currentState.sessionId(), currentState.totalInteractions());
    }

    public static SessionContextPersistence getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new SessionContextPersistence();
                }
            }
        }
        return instance;
    }

    public static void reset() {
        synchronized (INSTANCE_LOCK) {
            if (instance != null) {
                try {
                    instance.close();
                } catch (Exception ignored) {}
            }
            instance = null;
        }
    }

    private SessionContextState loadOrCreate() {
        if (!persistsToDisk || sessionFile == null) {
            log.debug(LOG_PREFIX + "Creando sesion en memoria (no persistente)");
            return SessionContextState.createNew();
        }

        try {
            if (Files.exists(sessionFile)) {
                SessionContextState loaded = mapper.readValue(sessionFile.toFile(), SessionContextState.class);
                log.info(LOG_PREFIX + "Sesion restaurada desde {}", sessionFile);
                return loaded;
            }
        } catch (Exception e) {
            log.warn(LOG_PREFIX + "Error cargando sesion: {}. Creando nueva.", e.getMessage());
            backupCorruptFile();
        }

        log.info(LOG_PREFIX + "Creando nueva sesion");
        return SessionContextState.createNew();
    }

    private void persist() {
        if (!persistsToDisk || sessionFile == null) {
            return;
        }

        writeLock.lock();
        try {
            Files.createDirectories(sessionFile.getParent());

            mapper.writeValue(tempFile.toFile(), currentState);

            Files.move(tempFile, sessionFile,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);

            log.debug(LOG_PREFIX + "Sesion guardada en {}", sessionFile);
        } catch (IOException e) {
            log.error(LOG_PREFIX + "Error guardando sesion: {}", e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    private void backupCorruptFile() {
        if (sessionFile == null || !Files.exists(sessionFile)) {
            return;
        }

        try {
            Path corruptBackup = sessionFile.resolveSibling(SESSION_FILE_NAME + CORRUPT_SUFFIX);
            Files.move(sessionFile, corruptBackup, StandardCopyOption.REPLACE_EXISTING);
            log.warn(LOG_PREFIX + "Archivo corrupto movido a {}", corruptBackup);
        } catch (IOException e) {
            log.error(LOG_PREFIX + "Error moviendo archivo corrupto: {}", e.getMessage());
        }
    }

    public void trackFile(String filepath, FileAction action) {
        FileModification mod = FileModification.now(filepath, action);
        currentState = currentState.withFileModification(mod);
        persist();
        log.debug(LOG_PREFIX + "Archivo registrado: {} ({})", filepath, action);
    }

    public void recordError(ErrorContext error) {
        if (error == null) {
            return;
        }
        currentState = currentState.withError(error);
        persist();
        log.info(LOG_PREFIX + "Error registrado: {} - {}", error.errorType(), error.message());
    }

    public void recordException(Throwable exception) {
        recordError(ErrorContext.fromException(exception));
    }

    public void clearError() {
        if (currentState.lastError() != null) {
            currentState = currentState.withErrorCleared();
            persist();
            log.info(LOG_PREFIX + "Error limpiado");
        }
    }

    public void trackCodeSnippet(String code, String language, String description) {
        if (code == null || code.isBlank()) {
            return;
        }
        CodeSnippet snippet = CodeSnippet.now(code, language, description);
        currentState = currentState.withCodeSnippet(snippet);
        persist();
        log.debug(LOG_PREFIX + "Codigo preservado: [{}] {} ({} lineas)",
            language, description, code.split("\n").length);
    }

    public void trackCodeBlocks(java.util.List<String> codeBlocks) {
        if (codeBlocks == null || codeBlocks.isEmpty()) {
            return;
        }
        for (String code : codeBlocks) {
            CodeSnippet snippet = CodeSnippet.autoDetect(code, "codigo generado");
            currentState = currentState.withCodeSnippet(snippet);
        }
        persist();
        log.debug(LOG_PREFIX + "{} bloques de codigo preservados", codeBlocks.size());
    }

    public boolean hasCodeSnippets() {
        return currentState.hasCodeSnippets();
    }

    public void incrementInteraction() {
        currentState = currentState.withInteractionIncremented();
        persist();
    }

    public String getContextForPrompt() {
        return currentState.toPromptContext();
    }

    public boolean hasRecentError() {
        return currentState.hasRecentError();
    }

    public SessionContextState getState() {
        return currentState;
    }

    public String getSessionId() {
        return currentState.sessionId();
    }

    public int getTotalInteractions() {
        return currentState.totalInteractions();
    }

    public boolean isPersistent() {
        return persistsToDisk;
    }

    @Override
    public void close() {
        if (persistsToDisk) {
            persist();
            log.info(LOG_PREFIX + "Sesion guardada al cerrar - {} interacciones",
                currentState.totalInteractions());
        }
    }

    public String getInfoString() {
        if (!persistsToDisk) {
            return "Session: In-memory (no persistence)";
        }
        return String.format("Session: %s (%d interactions)",
            currentState.sessionId().substring(0, 8), currentState.totalInteractions());
    }
}
