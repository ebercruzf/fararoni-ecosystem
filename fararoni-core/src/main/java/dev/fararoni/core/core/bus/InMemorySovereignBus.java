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

import dev.fararoni.bus.agent.api.bus.BusOverloadException;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.telemetry.SovereignMetrics;
import dev.fararoni.core.core.persistence.SovereignJournal;
import dev.fararoni.core.core.resilience.IdempotencyFilter;
import dev.fararoni.core.core.resilience.PoisonPill;
import dev.fararoni.core.observability.AgentSpan;
import dev.fararoni.core.observability.TelemetryService;

import java.time.Duration;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class InMemorySovereignBus implements SovereignEventBus {
    private static final Logger LOG = Logger.getLogger(InMemorySovereignBus.class.getName());

    public static final String DLQ_TOPIC = "sys.dlq.main";

    private static final int MAX_INFLIGHT = 10_000;

    private static final int MAX_DLQ_SIZE = 1_000;

    private static final int ACQUIRE_TIMEOUT_SECONDS = 5;

    private static final double SHEDDING_THRESHOLD_LOW = 0.70;

    private static final double SHEDDING_THRESHOLD_MEDIUM = 0.85;

    private static final double SHEDDING_THRESHOLD_HIGH = 0.95;

    private static final Map<String, Integer> TOPIC_PRIORITIES = Map.of(
        "agency.mission.start", 0,
        "agency.mission.resume", 0,
        "swarm.result", 1,
        "swarm.task", 1,
        "swarm.compensation", 1,
        "swarm.telemetry", 3,
        "sys.heartbeat", 2,
        "sys.dlq", 0
    );

    private static final int DEFAULT_PRIORITY = 1;

    private final ExecutorService vExecutor;

    private final Map<String, SubmissionPublisher<SovereignEnvelope<?>>> channels;

    private final Semaphore globalInFlight;

    private final Map<String, CompletableFuture<Object>> pendingRequests;

    private final Queue<PoisonPill> deadLetterQueue;

    private volatile boolean running = true;

    private SovereignJournal journal;

    private final TelemetryService telemetry;

    private final IdempotencyFilter globalIdempotency;

    public InMemorySovereignBus() {
        this.vExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.channels = new ConcurrentHashMap<>();
        this.globalInFlight = new Semaphore(MAX_INFLIGHT);
        this.pendingRequests = new ConcurrentHashMap<>();
        this.deadLetterQueue = new ConcurrentLinkedQueue<>();
        this.telemetry = TelemetryService.getInstance();

        this.globalIdempotency = new IdempotencyFilter(
            java.time.Duration.ofMinutes(5),
            50_000
        );

        telemetry.registerGauge("bus.inflight.total", this::getInFlightCount);
        telemetry.registerGauge("bus.dlq.size", () -> (long) deadLetterQueue.size());

        LOG.info("[SOVEREIGN-BUS] Inicializado con capacidad: " + MAX_INFLIGHT);
    }

    public void connectJournal(SovereignJournal journal) {
        this.journal = journal;
        LOG.info("[SOVEREIGN-BUS] Journal conectado - Persistencia Blindada ACTIVA");
    }

    public boolean isJournalConnected() {
        return journal != null;
    }

    @Override
    public <T> CompletableFuture<Void> publish(String topic, SovereignEnvelope<T> envelope) {
        if (!running) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Bus is shutting down")
            );
        }

        LoadSheddingDecision decision = evaluateLoadShedding(topic);
        if (decision == LoadSheddingDecision.DROP) {
            LOG.fine(() -> "[SOVEREIGN-BUS] Mensaje descartado por load shedding: " + topic);
            return CompletableFuture.completedFuture(null);
        }

        SovereignEnvelope<T> finalEnvelope = envelope;
        if (envelope.hasTemporaryIdempotencyKey()) {
            finalEnvelope = envelope.withFinalIdempotencyKey(topic);
        }

        String idempotencyKey = finalEnvelope.idempotencyKey();
        if (idempotencyKey != null && !globalIdempotency.tryProcess(idempotencyKey)) {
            LOG.fine(() -> "[SOVEREIGN-BUS] Mensaje duplicado ignorado (idempotencyKey): " + idempotencyKey);
            SovereignMetrics.INSTANCE.increment("bus.messages.duplicates");
            return CompletableFuture.completedFuture(null);
        }

        final SovereignEnvelope<T> envelopeToPublish = finalEnvelope;

        return CompletableFuture.runAsync(() -> {
            try (AgentSpan span = telemetry.startSpan("bus.publish", "topic", topic)) {
                try {
                    if (journal != null) {
                        journal.persistEvent(topic, envelopeToPublish);
                    }

                    if (!globalInFlight.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        long inFlight = MAX_INFLIGHT - globalInFlight.availablePermits();
                        span.setStatus(AgentSpan.Status.ERROR);
                        throw new BusOverloadException(
                            "Bus sobrecargado - backpressure activo",
                            inFlight,
                            MAX_INFLIGHT
                        );
                    }

                    var publisher = channels.computeIfAbsent(topic, k ->
                        new SubmissionPublisher<>(vExecutor, Flow.defaultBufferSize())
                    );

                    publisher.submit(envelopeToPublish);

                    if (journal != null) {
                        journal.markDispatched(envelopeToPublish.id());
                    }

                    SovereignMetrics.INSTANCE.increment("bus.messages.published");
                    telemetry.incrementCounter("bus.publish.total", "topic", topic);

                    LOG.fine(() -> "[SOVEREIGN-BUS] Publicado en " + topic + ": " + envelopeToPublish.id());
                } catch (InterruptedException e) {
                    span.setError(e);
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                } finally {
                    globalInFlight.release();
                }
            }
        }, vExecutor);
    }

    public <T> CompletableFuture<Void> publishWithoutPersist(String topic, SovereignEnvelope<T> envelope) {
        if (!running) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Bus is shutting down")
            );
        }

        return CompletableFuture.runAsync(() -> {
            try {
                if (!globalInFlight.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    long inFlight = MAX_INFLIGHT - globalInFlight.availablePermits();
                    throw new BusOverloadException(
                        "Bus sobrecargado - backpressure activo",
                        inFlight,
                        MAX_INFLIGHT
                    );
                }

                var publisher = channels.computeIfAbsent(topic, k ->
                    new SubmissionPublisher<>(vExecutor, Flow.defaultBufferSize())
                );

                publisher.submit(envelope);

                SovereignMetrics.INSTANCE.increment("bus.messages.recovered");

                LOG.fine(() -> "[SOVEREIGN-BUS] Recuperado en " + topic + ": " + envelope.id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } finally {
                globalInFlight.release();
            }
        }, vExecutor);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> CompletableFuture<R> request(
            String topic,
            SovereignEnvelope<T> envelope,
            Class<R> responseType,
            Duration timeout) {
        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<Object> futureResponse = new CompletableFuture<>();

        pendingRequests.put(correlationId, futureResponse);

        String replyTopic = "sys.reply." + correlationId;
        SovereignEnvelope<T> requestEnvelope = envelope
            .withCorrelation(correlationId)
            .withReplyTo(replyTopic);

        subscribeOnce(replyTopic, Object.class, replyEnvelope -> {
            CompletableFuture<Object> pending = pendingRequests.remove(correlationId);
            if (pending != null) {
                pending.complete(replyEnvelope.payload());
            }
        });

        this.publish(topic, requestEnvelope)
            .exceptionally(ex -> {
                pendingRequests.remove(correlationId);
                futureResponse.completeExceptionally(ex);
                return null;
            });

        return futureResponse
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .whenComplete((res, ex) -> pendingRequests.remove(correlationId))
            .thenApply(res -> {
                if (responseType.isInstance(res)) {
                    return responseType.cast(res);
                }
                throw new IllegalStateException(
                    "Tipo de respuesta invalido. Esperado: " + responseType.getName()
                );
            });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void subscribe(String topic, Class<T> payloadType, Consumer<SovereignEnvelope<T>> consumer) {
        var publisher = channels.computeIfAbsent(topic, k ->
            new SubmissionPublisher<>(vExecutor, Flow.defaultBufferSize())
        );

        publisher.subscribe(new Subscriber<SovereignEnvelope<?>>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(SovereignEnvelope<?> rawEnvelope) {
                vExecutor.submit(() -> {
                    SovereignMetrics.INSTANCE.gauge("bus.inflight", 1);

                    try (AgentSpan span = telemetry.startSpan("bus.deliver", "topic", topic)) {
                        try {
                            if (rawEnvelope.isExpired()) {
                                LOG.warning("[SOVEREIGN-BUS] Mensaje expirado (TTL): " + rawEnvelope.id());
                                SovereignMetrics.INSTANCE.increment("bus.messages.expired");
                                telemetry.incrementCounter("bus.deliver.expired", "topic", topic);
                                span.setStatus(AgentSpan.Status.TIMEOUT);
                                handleExpiredMessage(topic, rawEnvelope);
                                return;
                            }

                            if (rawEnvelope.isMaxRetriesExceeded()) {
                                LOG.warning("[SOVEREIGN-BUS] Max reintentos excedidos: " + rawEnvelope.id());
                                SovereignMetrics.INSTANCE.increment("bus.messages.max_retries");
                                telemetry.incrementCounter("bus.deliver.max_retries", "topic", topic);
                                span.setStatus(AgentSpan.Status.ERROR);
                                sendToDeadLetter(topic, rawEnvelope, "Max reintentos excedidos");
                                return;
                            }

                            if (rawEnvelope.isMaxHopsExceeded()) {
                                LOG.severe("[SOVEREIGN-BUS] MAX_HOP_COUNT excedido (anti-loop): " + rawEnvelope.id() +
                                    " | hopCount=" + rawEnvelope.hopCount());
                                SovereignMetrics.INSTANCE.increment("bus.messages.max_hops");
                                telemetry.incrementCounter("bus.deliver.max_hops", "topic", topic);
                                span.setStatus(AgentSpan.Status.ERROR);
                                sendToDeadLetter(topic, rawEnvelope, "Max hops excedidos (" +
                                    rawEnvelope.hopCount() + "/" + SovereignEnvelope.MAX_HOP_COUNT + ") - posible bucle infinito");
                                return;
                            }

                            if (payloadType.isInstance(rawEnvelope.payload())) {
                                consumer.accept((SovereignEnvelope<T>) rawEnvelope);
                                SovereignMetrics.INSTANCE.increment("bus.messages.success");
                                telemetry.incrementCounter("bus.deliver.success", "topic", topic);

                                long ageMs = rawEnvelope.ageMs();
                                telemetry.recordDistribution("bus.message.age_ms", ageMs, "topic", topic);
                            }
                        } catch (Exception e) {
                            span.setError(e);
                            handleFailure(topic, rawEnvelope, consumer, e);
                        } finally {
                            SovereignMetrics.INSTANCE.gauge("bus.inflight", -1);
                        }
                    }
                });
            }

            @Override
            public void onError(Throwable throwable) {
                LOG.log(Level.SEVERE, "[SOVEREIGN-BUS] Error en canal " + topic, throwable);
            }

            @Override
            public void onComplete() {
                LOG.info("[SOVEREIGN-BUS] Canal cerrado: " + topic);
            }
        });

        LOG.info("[SOVEREIGN-BUS] Suscrito a: " + topic);
    }

    private <T> void subscribeOnce(String topic, Class<T> payloadType, Consumer<SovereignEnvelope<T>> consumer) {
        var publisher = channels.computeIfAbsent(topic, k ->
            new SubmissionPublisher<>(vExecutor, Flow.defaultBufferSize())
        );

        publisher.subscribe(new Subscriber<SovereignEnvelope<?>>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void onNext(SovereignEnvelope<?> rawEnvelope) {
                try {
                    consumer.accept((SovereignEnvelope<T>) rawEnvelope);
                } finally {
                    subscription.cancel();
                    channels.remove(topic);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                LOG.log(Level.WARNING, "[SOVEREIGN-BUS] Error en reply " + topic, throwable);
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void handleFailure(String topic, SovereignEnvelope<?> envelope, Object consumer, Throwable error) {
        SovereignMetrics.INSTANCE.increment("bus.messages.failed");
        LOG.warning("[SOVEREIGN-BUS] Fallo en " + topic + ": " + error.getMessage());

        if (DLQ_TOPIC.equals(topic)) {
            LOG.severe("[SOVEREIGN-BUS] CRITICAL: Fallo dentro de DLQ. Mensaje descartado.");
            return;
        }

        if (!envelope.isMaxRetriesExceeded()) {
            SovereignEnvelope<?> retryEnvelope = envelope.incrementRetry();
            LOG.info("[SOVEREIGN-BUS] Reintentando mensaje " + envelope.id() +
                     " (intento " + retryEnvelope.retryCount() + "/" + SovereignEnvelope.MAX_RETRY_COUNT + ")");
            this.publish(topic, retryEnvelope)
                .exceptionally(ex -> {
                    LOG.severe("[SOVEREIGN-BUS] Fallo al reencolar: " + ex.getMessage());
                    sendToDeadLetter(topic, envelope, error.getMessage());
                    return null;
                });
            return;
        }

        sendToDeadLetter(topic, envelope, error, consumer);
    }

    private void handleExpiredMessage(String topic, SovereignEnvelope<?> envelope) {
        sendToDeadLetter(topic, envelope, "Mensaje expirado (TTL: " + envelope.ttlMs() + "ms, edad: " + envelope.ageMs() + "ms)");
    }

    private void sendToDeadLetter(String topic, SovereignEnvelope<?> envelope, String reason) {
        sendToDeadLetter(topic, envelope, new RuntimeException(reason), null);
    }

    private void sendToDeadLetter(String topic, SovereignEnvelope<?> envelope, Throwable error, Object consumer) {
        PoisonPill corpse = PoisonPill.create(topic, error, consumer, envelope);

        if (journal != null) {
            journal.savePoisonPill(corpse);
        }

        if (deadLetterQueue.size() < MAX_DLQ_SIZE) {
            deadLetterQueue.offer(corpse);

            var dlqEnvelope = SovereignEnvelope.create("system-guard", envelope.traceId(), corpse);
            this.publish(DLQ_TOPIC, dlqEnvelope)
                .exceptionally(ex -> {
                    LOG.severe("[SOVEREIGN-BUS] FATAL: No se pudo enviar a DLQ: " + ex.getMessage());
                    return null;
                });
        } else {
            LOG.severe("[SOVEREIGN-BUS] DLQ llena. Mensaje perdido: " + envelope.id());
        }
    }

    @Override
    public long getInFlightCount() {
        return MAX_INFLIGHT - globalInFlight.availablePermits();
    }

    @Override
    public boolean isHealthy() {
        return running && getInFlightCount() < MAX_INFLIGHT * 0.9;
    }

    public int getDeadLetterQueueSize() {
        return deadLetterQueue.size();
    }

    public PoisonPill pollDeadLetter() {
        return deadLetterQueue.poll();
    }

    @Override
    public void shutdown(Duration timeout) {
        LOG.info("[SOVEREIGN-BUS] Iniciando shutdown...");
        running = false;

        channels.values().forEach(SubmissionPublisher::close);
        channels.clear();

        vExecutor.shutdown();
        try {
            if (!vExecutor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                vExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            vExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOG.info("[SOVEREIGN-BUS] Shutdown completado. DLQ size: " + deadLetterQueue.size());
    }

    public enum LoadSheddingDecision {
        ACCEPT,
        DROP,
        SPILLOVER
    }

    public LoadSheddingDecision evaluateLoadShedding(String topic) {
        double currentLoad = (double) getInFlightCount() / MAX_INFLIGHT;
        int priority = getTopicPriority(topic);

        if (priority == 0) {
            return LoadSheddingDecision.ACCEPT;
        }

        if (currentLoad >= SHEDDING_THRESHOLD_HIGH) {
            LOG.warning("Load shedding HIGH (" + (int)(currentLoad*100) +
                        "%): SPILLOVER topic=" + topic);
            SovereignMetrics.INSTANCE.increment("bus.loadshed.spillover");
            return LoadSheddingDecision.SPILLOVER;
        }

        if (currentLoad >= SHEDDING_THRESHOLD_MEDIUM && priority >= 2) {
            LOG.warning("Load shedding MEDIUM (" + (int)(currentLoad*100) +
                        "%): DROP topic=" + topic);
            SovereignMetrics.INSTANCE.increment("bus.loadshed.drop_medium");
            return LoadSheddingDecision.DROP;
        }

        if (currentLoad >= SHEDDING_THRESHOLD_LOW && priority >= 3) {
            LOG.info("Load shedding LOW (" + (int)(currentLoad*100) +
                     "%): DROP telemetry topic=" + topic);
            SovereignMetrics.INSTANCE.increment("bus.loadshed.drop_low");
            return LoadSheddingDecision.DROP;
        }

        return LoadSheddingDecision.ACCEPT;
    }

    public int getTopicPriority(String topic) {
        Integer exact = TOPIC_PRIORITIES.get(topic);
        if (exact != null) {
            return exact;
        }

        for (Map.Entry<String, Integer> entry : TOPIC_PRIORITIES.entrySet()) {
            if (topic.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return DEFAULT_PRIORITY;
    }

    public double getCurrentLoadPercentage() {
        return (double) getInFlightCount() / MAX_INFLIGHT;
    }
}
