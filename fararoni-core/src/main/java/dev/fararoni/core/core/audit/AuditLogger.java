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
package dev.fararoni.core.core.audit;

import dev.fararoni.core.core.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class AuditLogger implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private static volatile AuditLogger instance;
    private static final Object LOCK = new Object();

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneId.of("UTC"));

    private static final String AUDIT_FILENAME = "audit.log";

    private final BlockingQueue<AuditEntry> messageQueue;
    private final ExecutorService writerExecutor;
    private final ScheduledExecutorService flushScheduler;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong entryCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    private static final int QUEUE_CAPACITY = 10000;
    private static final int FLUSH_INTERVAL_SECONDS = 5;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final Path auditFilePath;

    public enum Level {
        INFO, WARN, ERROR, DEBUG
    }

    public enum Category {
        COMMAND,
        LLM_CALL,
        FILE_OP,
        SYSTEM,
        SECURITY,
        MEMORY,
        CACHE
    }

    private record AuditEntry(
            Instant timestamp,
            Level level,
            Category category,
            String message,
            String details
    ) {
        String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(TIMESTAMP_FORMAT.format(timestamp)).append("] ");
            sb.append("[").append(level.name()).append("] ");
            sb.append("[").append(category.name()).append("] ");
            sb.append(message);
            if (details != null && !details.isBlank()) {
                sb.append(" | ").append(details);
            }
            return sb.toString();
        }
    }

    private AuditLogger() {
        this(WorkspaceManager.getInstance().getWorkspaceDir().resolve(AUDIT_FILENAME));
    }

    AuditLogger(Path auditFilePath) {
        this.auditFilePath = auditFilePath;
        this.messageQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.writerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AuditLogger-Writer");
            t.setDaemon(true);
            return t;
        });
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuditLogger-Flush");
            t.setDaemon(true);
            return t;
        });

        startAsyncWriter();

        flushScheduler.scheduleAtFixedRate(
                this::flush,
                FLUSH_INTERVAL_SECONDS,
                FLUSH_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        log.info("[AuditLogger] Initialized - logging to: {}", auditFilePath);
    }

    public static AuditLogger getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new AuditLogger();
                }
            }
        }
        return instance;
    }

    public void logCommand(String command, String description) {
        log(Level.INFO, Category.COMMAND, "Comando: " + command, description);
    }

    public void logLlmCall(String model, int inputTokens, int outputTokens, boolean success) {
        Level level = success ? Level.INFO : Level.WARN;
        String status = success ? "OK" : "FAILED";
        log(level, Category.LLM_CALL,
                "LLM Request [" + status + "]",
                String.format("model=%s, in=%d, out=%d", model, inputTokens, outputTokens));
    }

    public void logLlmCall(String model, int inputTokens, int outputTokens, long durationMs, boolean success) {
        Level level = success ? Level.INFO : Level.WARN;
        String status = success ? "OK" : "FAILED";
        log(level, Category.LLM_CALL,
                "LLM Request [" + status + "]",
                String.format("model=%s, in=%d, out=%d, duration=%dms", model, inputTokens, outputTokens, durationMs));
    }

    public void logFileOperation(String operation, String filePath, boolean success) {
        Level level = success ? Level.INFO : Level.WARN;
        String status = success ? "OK" : "FAILED";
        log(level, Category.FILE_OP,
                "File " + operation + " [" + status + "]",
                "path=" + sanitizePath(filePath));
    }

    public void logSystem(String event, String details) {
        log(Level.INFO, Category.SYSTEM, event, details);
    }

    public void logSecurity(String event, String details) {
        log(Level.WARN, Category.SECURITY, event, details);
    }

    public void logMemory(String operation, String details) {
        log(Level.INFO, Category.MEMORY, "Memory " + operation, details);
    }

    public void logCache(String operation, String details) {
        log(Level.DEBUG, Category.CACHE, "Cache " + operation, details);
    }

    public void logError(Category category, String message, Throwable throwable) {
        String details = throwable != null
                ? throwable.getClass().getSimpleName() + ": " + throwable.getMessage()
                : null;
        log(Level.ERROR, category, message, details);
    }

    public void log(Level level, Category category, String message, String details) {
        if (closed.get()) {
            log.warn("[AuditLogger] Attempted to log after close: {}", message);
            return;
        }

        AuditEntry entry = new AuditEntry(
                Instant.now(),
                level,
                category,
                message,
                details
        );

        boolean offered = messageQueue.offer(entry);
        if (!offered) {
            log.warn("[AuditLogger] Queue full, dropping audit entry: {}", message);
            errorCount.incrementAndGet();
        }
    }

    private void startAsyncWriter() {
        writerExecutor.submit(() -> {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    auditFilePath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {

                while (running.get() || !messageQueue.isEmpty()) {
                    try {
                        AuditEntry entry = messageQueue.poll(1, TimeUnit.SECONDS);
                        if (entry != null) {
                            writer.write(entry.format());
                            writer.newLine();
                            entryCount.incrementAndGet();

                            if (entry.level == Level.ERROR) {
                                writer.flush();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        log.error("[AuditLogger] Write error: {}", e.getMessage());
                        errorCount.incrementAndGet();
                    }
                }

                writer.flush();
                log.info("[AuditLogger] Writer shutdown complete. Entries written: {}", entryCount.get());

            } catch (IOException e) {
                log.error("[AuditLogger] Failed to open audit file: {}", e.getMessage());
            }
        });
    }

    public void flush() {
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        log.info("[AuditLogger] Shutting down...");

        log(Level.INFO, Category.SYSTEM, "AuditLogger shutdown",
                String.format("entries=%d, errors=%d", entryCount.get(), errorCount.get()));

        running.set(false);

        flushScheduler.shutdown();

        writerExecutor.shutdown();
        try {
            if (!writerExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("[AuditLogger] Writer did not terminate in time, forcing shutdown");
                writerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writerExecutor.shutdownNow();
        }

        log.info("[AuditLogger] Shutdown complete. Total entries: {}", entryCount.get());
    }

    public long getEntryCount() {
        return entryCount.get();
    }

    public long getErrorCount() {
        return errorCount.get();
    }

    public boolean isRunning() {
        return running.get() && !closed.get();
    }

    public Path getAuditFilePath() {
        return auditFilePath;
    }

    private String sanitizePath(String path) {
        if (path == null) return "null";
        String home = System.getProperty("user.home");
        if (home != null && path.startsWith(home)) {
            return "~" + path.substring(home.length());
        }
        return path;
    }

    static void resetForTesting() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}
