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
package dev.fararoni.core.core.telemetry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.fararoni.core.core.session.SessionModeManager;
import dev.fararoni.core.core.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class TelemetryQueue implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TelemetryQueue.class);

    private static final int MAX_QUEUE_SIZE = 1000;

    private static final int MAX_RETRIES = 5;

    private static final long MIN_PERSIST_INTERVAL_MS = 5000;

    private static volatile TelemetryQueue instance;
    private static final Object LOCK = new Object();

    private final ConcurrentLinkedDeque<TelemetryEvent> pendingEvents;
    private final ObjectMapper objectMapper;
    private final Path queuePath;
    private final boolean persistEnabled;

    private final AtomicLong eventsEnqueued = new AtomicLong(0);
    private final AtomicLong eventsSent = new AtomicLong(0);
    private final AtomicLong eventsDropped = new AtomicLong(0);
    private final AtomicLong persistCount = new AtomicLong(0);

    private volatile long lastPersistTime = 0;
    private volatile boolean dirty = false;

    private TelemetryQueue(Path queuePath, boolean persistEnabled) {
        this.queuePath = queuePath;
        this.persistEnabled = persistEnabled;
        this.pendingEvents = new ConcurrentLinkedDeque<>();

        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        if (persistEnabled && queuePath != null) {
            loadFromDisk();
        }

        log.info("[TelemetryQueue] Initialized - persist={}, pending={}, path={}",
                persistEnabled, pendingEvents.size(),
                queuePath != null ? queuePath.getFileName() : "null");
    }

    public static TelemetryQueue getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    boolean persist = shouldPersist();
                    Path path = persist ? resolveQueuePath() : null;
                    instance = new TelemetryQueue(path, persist);
                }
            }
        }
        return instance;
    }

    private static boolean shouldPersist() {
        try {
            return SessionModeManager.getInstance().persistsToDisk();
        } catch (Exception e) {
            return true;
        }
    }

    private static Path resolveQueuePath() {
        try {
            return WorkspaceManager.getInstance().getQueuePath();
        } catch (Exception e) {
            log.warn("[TelemetryQueue] WorkspaceManager not available, using temp directory");
            return null;
        }
    }

    public static void resetForTesting() {
        synchronized (LOCK) {
            if (instance != null) {
                try {
                    instance.close();
                } catch (Exception ignored) {
                }
            }
            instance = null;
        }
    }

    public boolean enqueue(TelemetryEvent event) {
        if (event == null) {
            return false;
        }

        while (pendingEvents.size() >= MAX_QUEUE_SIZE) {
            TelemetryEvent dropped = pendingEvents.pollFirst();
            if (dropped != null) {
                eventsDropped.incrementAndGet();
                log.debug("[TelemetryQueue] Dropped old event: {}", dropped.id());
            }
        }

        pendingEvents.addLast(event);
        eventsEnqueued.incrementAndGet();
        dirty = true;

        log.debug("[TelemetryQueue] Enqueued event: {} (type={})", event.id(), event.eventType());

        persistIfNeeded();

        return true;
    }

    public boolean enqueue(String eventType, String payload) {
        return enqueue(TelemetryEvent.create(eventType, payload));
    }

    public TelemetryEvent peek() {
        return pendingEvents.peekFirst();
    }

    public TelemetryEvent poll() {
        TelemetryEvent event = pendingEvents.pollFirst();
        if (event != null) {
            dirty = true;
        }
        return event;
    }

    public FlushResult flush(Function<TelemetryEvent, Boolean> sender) {
        if (sender == null || pendingEvents.isEmpty()) {
            return new FlushResult(0, 0, 0);
        }

        int sent = 0;
        int failed = 0;
        int dropped = 0;

        List<TelemetryEvent> toRetry = new ArrayList<>();

        while (!pendingEvents.isEmpty()) {
            TelemetryEvent event = pendingEvents.pollFirst();
            if (event == null) break;

            try {
                Boolean success = sender.apply(event);
                if (Boolean.TRUE.equals(success)) {
                    sent++;
                    eventsSent.incrementAndGet();
                    log.debug("[TelemetryQueue] Sent event: {}", event.id());
                } else {
                    if (event.retryCount() < MAX_RETRIES) {
                        toRetry.add(event.incrementRetry());
                        failed++;
                    } else {
                        dropped++;
                        eventsDropped.incrementAndGet();
                        log.warn("[TelemetryQueue] Dropped event after {} retries: {}",
                                MAX_RETRIES, event.id());
                    }
                }
            } catch (Exception e) {
                log.warn("[TelemetryQueue] Error sending event {}: {}", event.id(), e.getMessage());
                if (event.retryCount() < MAX_RETRIES) {
                    toRetry.add(event.incrementRetry());
                    failed++;
                } else {
                    dropped++;
                    eventsDropped.incrementAndGet();
                }
            }
        }

        for (TelemetryEvent event : toRetry) {
            pendingEvents.addFirst(event);
        }

        dirty = true;
        persistIfNeeded();

        log.info("[TelemetryQueue] Flush complete: sent={}, failed={}, dropped={}",
                sent, failed, dropped);

        return new FlushResult(sent, failed, dropped);
    }

    public int clear() {
        int count = pendingEvents.size();
        pendingEvents.clear();
        dirty = true;
        persistNow();
        log.info("[TelemetryQueue] Cleared {} pending events", count);
        return count;
    }

    private void persistIfNeeded() {
        if (!persistEnabled || !dirty) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastPersistTime >= MIN_PERSIST_INTERVAL_MS) {
            persistNow();
        }
    }

    public void persistNow() {
        if (!persistEnabled || queuePath == null) {
            return;
        }

        try {
            List<TelemetryEvent> snapshot = new ArrayList<>(pendingEvents);
            objectMapper.writeValue(queuePath.toFile(), snapshot);
            lastPersistTime = System.currentTimeMillis();
            dirty = false;
            persistCount.incrementAndGet();

            log.debug("[TelemetryQueue] Persisted {} events to {}", snapshot.size(), queuePath);
        } catch (IOException e) {
            log.error("[TelemetryQueue] Failed to persist queue: {}", e.getMessage());
        }
    }

    private void loadFromDisk() {
        if (queuePath == null || !Files.exists(queuePath)) {
            return;
        }

        try {
            List<TelemetryEvent> loaded = objectMapper.readValue(
                    queuePath.toFile(),
                    new TypeReference<List<TelemetryEvent>>() {}
            );

            if (loaded != null && !loaded.isEmpty()) {
                pendingEvents.addAll(loaded);
                log.info("[TelemetryQueue] Loaded {} pending events from disk", loaded.size());
            }
        } catch (IOException e) {
            log.warn("[TelemetryQueue] Failed to load queue from disk: {}", e.getMessage());
            try {
                Files.deleteIfExists(queuePath);
            } catch (IOException ignored) {
            }
        }
    }

    public int size() {
        return pendingEvents.size();
    }

    public boolean isEmpty() {
        return pendingEvents.isEmpty();
    }

    public boolean isPersistEnabled() {
        return persistEnabled;
    }

    public Path getQueuePath() {
        return queuePath;
    }

    public QueueStats getStats() {
        return new QueueStats(
                pendingEvents.size(),
                eventsEnqueued.get(),
                eventsSent.get(),
                eventsDropped.get(),
                persistCount.get(),
                persistEnabled
        );
    }

    public List<TelemetryEvent> getPendingEvents() {
        return Collections.unmodifiableList(new ArrayList<>(pendingEvents));
    }

    @Override
    public void close() {
        if (persistEnabled && dirty) {
            persistNow();
        }

        log.info("[TelemetryQueue] Closed with {} pending events", pendingEvents.size());
    }

    public record TelemetryEvent(
            @JsonProperty("id") String id,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("payload") String payload,
            @JsonProperty("retryCount") int retryCount
    ) {
        @JsonCreator
        public TelemetryEvent {
        }

        public static TelemetryEvent create(String eventType, String payload) {
            return new TelemetryEvent(
                    UUID.randomUUID().toString(),
                    Instant.now().toEpochMilli(),
                    eventType,
                    payload,
                    0
            );
        }

        public TelemetryEvent incrementRetry() {
            return new TelemetryEvent(id, timestamp, eventType, payload, retryCount + 1);
        }

        public boolean isExpired() {
            long age = System.currentTimeMillis() - timestamp;
            return age > 24 * 60 * 60 * 1000;
        }
    }

    public record FlushResult(int sent, int failed, int dropped) {
        public boolean isSuccess() {
            return failed == 0 && dropped == 0;
        }

        public int total() {
            return sent + failed + dropped;
        }

        public String getSummary() {
            return String.format("Flush: %d sent, %d failed, %d dropped", sent, failed, dropped);
        }
    }

    public record QueueStats(
            int pending,
            long enqueued,
            long sent,
            long dropped,
            long persistCount,
            boolean persistEnabled
    ) {
        public String getSummary() {
            return String.format(
                    "[TelemetryQueue] pending=%d, enqueued=%d, sent=%d, dropped=%d, persists=%d",
                    pending, enqueued, sent, dropped, persistCount
            );
        }
    }
}
