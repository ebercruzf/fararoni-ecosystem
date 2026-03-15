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
package dev.fararoni.core.core.bus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.telemetry.SovereignMetrics;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ChronicleQueueBus implements SovereignEventBus, AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ChronicleQueueBus.class.getName());

    private static final int PRIORITY = 50;

    private static final String MSG_PREFIX = "ENV:";

    private final Path basePath;
    private final ObjectMapper objectMapper;

    private final Map<String, ChronicleQueue> queues;

    private final Map<String, CopyOnWriteArrayList<SubscriberEntry<?>>> subscribers;

    private final Map<String, Future<?>> tailerTasks;

    private final ExecutorService vExecutor;

    private volatile boolean running = true;

    public ChronicleQueueBus(Path basePath) {
        this.basePath = basePath;
        this.queues = new ConcurrentHashMap<>();
        this.subscribers = new ConcurrentHashMap<>();
        this.tailerTasks = new ConcurrentHashMap<>();
        this.vExecutor = Executors.newVirtualThreadPerTaskExecutor();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        try {
            Files.createDirectories(basePath);
            LOG.info("[CHRONICLE-BUS] Inicializado en: " + basePath);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "[CHRONICLE-BUS] Error creando directorio", e);
            throw new IllegalStateException("No se pudo crear directorio para Chronicle Queue", e);
        }
    }

    @Override
    public <T> CompletableFuture<Void> publish(String topic, SovereignEnvelope<T> envelope) {
        if (!running) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Bus is shutting down")
            );
        }

        return CompletableFuture.runAsync(() -> {
            try {
                String json = serializeEnvelope(envelope);

                ChronicleQueue queue = getOrCreateQueue(topic);
                try (ExcerptAppender appender = queue.createAppender()) {
                    appender.writeText(MSG_PREFIX + json);
                }

                SovereignMetrics.INSTANCE.increment("bus.chronicle.published");

                LOG.fine(() -> "[CHRONICLE-BUS] Publicado en " + topic + ": " + envelope.id());

                notifySubscribers(topic, envelope);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "[CHRONICLE-BUS] Error publicando en " + topic, e);
                throw new CompletionException(e);
            }
        }, vExecutor);
    }

    @Override
    public <T, R> CompletableFuture<R> request(
            String topic,
            SovereignEnvelope<T> envelope,
            Class<R> responseType,
            Duration timeout) {
        String correlationId = envelope.correlationId() != null
            ? envelope.correlationId()
            : java.util.UUID.randomUUID().toString();

        String replyTopic = "sys.reply." + correlationId;
        CompletableFuture<R> future = new CompletableFuture<>();

        subscribe(replyTopic, responseType, replyEnvelope -> {
            future.complete(replyEnvelope.payload());
        });

        SovereignEnvelope<T> requestEnvelope = envelope
            .withCorrelation(correlationId)
            .withReplyTo(replyTopic);

        publish(topic, requestEnvelope);

        return future
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .whenComplete((res, ex) -> {
                subscribers.remove(replyTopic);
            });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void subscribe(String topic, Class<T> payloadType, Consumer<SovereignEnvelope<T>> consumer) {
        subscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
            .add(new SubscriberEntry<>(payloadType, (Consumer<SovereignEnvelope<?>>) (Consumer<?>) consumer));

        startTailerIfNeeded(topic);

        LOG.info("[CHRONICLE-BUS] Suscrito a: " + topic);
    }

    @Override
    public long getInFlightCount() {
        return tailerTasks.size();
    }

    @Override
    public boolean isHealthy() {
        return running && Files.isWritable(basePath);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public void shutdown(Duration timeout) {
        LOG.info("[CHRONICLE-BUS] Iniciando shutdown...");
        running = false;

        tailerTasks.values().forEach(f -> f.cancel(true));
        tailerTasks.clear();

        queues.values().forEach(ChronicleQueue::close);
        queues.clear();

        vExecutor.shutdown();
        try {
            if (!vExecutor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                vExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            vExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOG.info("[CHRONICLE-BUS] Shutdown completado");
    }

    @Override
    public void close() {
        shutdown(Duration.ofSeconds(5));
    }

    public int recoverPendingMessages(String topic, long fromIndex) {
        ChronicleQueue queue = getOrCreateQueue(topic);
        int recovered = 0;

        try (ExcerptTailer tailer = queue.createTailer()) {
            if (fromIndex > 0) {
                tailer.moveToIndex(fromIndex);
            }

            String text;
            while ((text = tailer.readText()) != null && running) {
                if (text.startsWith(MSG_PREFIX)) {
                    try {
                        String json = text.substring(MSG_PREFIX.length());
                        SovereignEnvelope<?> envelope = deserializeEnvelope(json);
                        notifySubscribers(topic, envelope);
                        recovered++;
                    } catch (Exception e) {
                        LOG.warning("[CHRONICLE-BUS] Error deserializando mensaje: " + e.getMessage());
                    }
                }
            }
        }

        final int finalRecovered = recovered;
        LOG.info(() -> "[CHRONICLE-BUS] Recuperados " + finalRecovered + " mensajes de " + topic);
        return recovered;
    }

    public long getLastIndex(String topic) {
        ChronicleQueue queue = getOrCreateQueue(topic);
        try (ExcerptTailer tailer = queue.createTailer()) {
            tailer.toEnd();
            return tailer.index();
        }
    }

    private ChronicleQueue getOrCreateQueue(String topic) {
        return queues.computeIfAbsent(topic, t -> {
            Path queuePath = basePath.resolve(sanitizeTopicName(t));
            try {
                Files.createDirectories(queuePath);
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo crear directorio para topic: " + t, e);
            }

            return ChronicleQueue.singleBuilder(queuePath)
                .rollCycle(RollCycles.DAILY)
                .build();
        });
    }

    private String sanitizeTopicName(String topic) {
        return topic.replace('.', '_').replace('/', '_');
    }

    private void startTailerIfNeeded(String topic) {
        tailerTasks.computeIfAbsent(topic, t -> {
            return vExecutor.submit(() -> {
                ChronicleQueue queue = getOrCreateQueue(t);
                try (ExcerptTailer tailer = queue.createTailer()) {
                    tailer.toEnd();

                    while (running) {
                        String text = tailer.readText();
                        if (text != null && text.startsWith(MSG_PREFIX)) {
                            try {
                                String json = text.substring(MSG_PREFIX.length());
                                SovereignEnvelope<?> envelope = deserializeEnvelope(json);
                            } catch (Exception e) {
                                LOG.warning("[CHRONICLE-BUS] Error en tailer: " + e.getMessage());
                            }
                        } else {
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
            });
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void notifySubscribers(String topic, SovereignEnvelope<T> envelope) {
        var subs = subscribers.get(topic);
        if (subs == null || subs.isEmpty()) {
            return;
        }

        for (SubscriberEntry<?> entry : subs) {
            if (entry.payloadType.isInstance(envelope.payload()) ||
                entry.payloadType == Object.class) {
                vExecutor.submit(() -> {
                    try {
                        ((Consumer) entry.consumer).accept(envelope);
                        SovereignMetrics.INSTANCE.increment("bus.chronicle.delivered");
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "[CHRONICLE-BUS] Error en subscriber", e);
                    }
                });
            }
        }
    }

    private <T> String serializeEnvelope(SovereignEnvelope<T> envelope) throws JsonProcessingException {
        return objectMapper.writeValueAsString(envelope);
    }

    @SuppressWarnings("unchecked")
    private SovereignEnvelope<?> deserializeEnvelope(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, SovereignEnvelope.class);
    }

    private final Map<String, Long> replayIndices = new ConcurrentHashMap<>();

    @Override
    public boolean hasPendingMessages() {
        for (String topic : queues.keySet()) {
            ChronicleQueue queue = queues.get(topic);
            if (queue == null) continue;

            try (ExcerptTailer tailer = queue.createTailer()) {
                long replayIndex = replayIndices.getOrDefault(topic, 0L);
                if (replayIndex > 0) {
                    tailer.moveToIndex(replayIndex);
                }
                if (tailer.readText() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public int replayNextBatch(SovereignEventBus targetBus, int maxBatchSize) {
        int replayed = 0;

        for (String topic : queues.keySet()) {
            if (replayed >= maxBatchSize) break;

            ChronicleQueue queue = queues.get(topic);
            if (queue == null) continue;

            try (ExcerptTailer tailer = queue.createTailer()) {
                long replayIndex = replayIndices.getOrDefault(topic, 0L);
                if (replayIndex > 0) {
                    tailer.moveToIndex(replayIndex);
                }

                String text;
                while (replayed < maxBatchSize && (text = tailer.readText()) != null) {
                    if (text.startsWith(MSG_PREFIX)) {
                        try {
                            String json = text.substring(MSG_PREFIX.length());
                            SovereignEnvelope<?> envelope = deserializeEnvelope(json);

                            targetBus.publish(topic, envelope).join();

                            replayIndices.put(topic, tailer.index());
                            replayed++;

                            SovereignMetrics.INSTANCE.increment("bus.chronicle.replayed");
                        } catch (Exception e) {
                            LOG.warning("[CHRONICLE-BUS] Error en replay: " + e.getMessage());
                            break;
                        }
                    }
                }
            }
        }

        return replayed;
    }

    public void resetReplayIndices() {
        replayIndices.clear();
        LOG.info("[CHRONICLE-BUS] Indices de replay reseteados");
    }

    public long getPendingMessageCount() {
        long total = 0;
        for (String topic : queues.keySet()) {
            ChronicleQueue queue = queues.get(topic);
            if (queue == null) continue;

            try (ExcerptTailer tailer = queue.createTailer()) {
                long replayIndex = replayIndices.getOrDefault(topic, 0L);
                if (replayIndex > 0) {
                    tailer.moveToIndex(replayIndex);
                }
                while (tailer.readText() != null) {
                    total++;
                }
            }
        }
        return total;
    }

    private record SubscriberEntry<T>(
        Class<T> payloadType,
        Consumer<SovereignEnvelope<?>> consumer
    ) {}
}
