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

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SovereignBusGuard implements SovereignEventBus {
    private static final Logger LOG = Logger.getLogger(SovereignBusGuard.class.getName());

    private static final int GUARD_PRIORITY = 100;

    private final SovereignEventBus primaryBus;

    private final SovereignEventBus standbyBus;

    private final AtomicBoolean circuitOpen;

    private final ReplayEngine replayEngine;

    private final LatencyMonitor latencyMonitor;

    private final AtomicLong messagesSentToPrimary = new AtomicLong(0);
    private final AtomicLong messagesSentToStandby = new AtomicLong(0);
    private final AtomicLong messagesReplayed = new AtomicLong(0);

    public SovereignBusGuard(SovereignEventBus primaryBus, SovereignEventBus standbyBus) {
        this.primaryBus = primaryBus;
        this.standbyBus = standbyBus;
        this.circuitOpen = new AtomicBoolean(false);
        this.latencyMonitor = new LatencyMonitor();
        this.replayEngine = new ReplayEngine(primaryBus, standbyBus, this::onMessageReplayed);

        LOG.info("[BUS-GUARD] Inicializado:");
        LOG.info("    Primary: " + primaryBus.getClass().getSimpleName() + " (P:" + primaryBus.getPriority() + ")");
        LOG.info("    Standby: " + standbyBus.getClass().getSimpleName() + " (P:" + standbyBus.getPriority() + ")");

        replayEngine.start();
    }

    @Override
    public <T> CompletableFuture<Void> publish(String topic, SovereignEnvelope<T> envelope) {
        long startTime = latencyMonitor.start();
        boolean replayInProgress = standbyBus.hasPendingMessages();

        try {
            if (!primaryBus.isAvailable() || circuitOpen.get()) {
                return publishToStandby(topic, envelope, startTime, true);
            }

            if (replayInProgress) {
                LOG.fine("[BUS-GUARD] Modo FIFO activo - desviando a standby para mantener orden");
                return publishToStandby(topic, envelope, startTime, false);
            }

            return publishToPrimary(topic, envelope, startTime);
        } catch (Exception e) {
            LOG.warning("[BUS-GUARD] Fallo en primary, activando circuit breaker: " + e.getMessage());
            circuitOpen.set(true);
            return publishToStandby(topic, envelope, startTime, true);
        }
    }

    private <T> CompletableFuture<Void> publishToPrimary(String topic, SovereignEnvelope<T> envelope, long startTime) {
        return primaryBus.publish(topic, envelope)
            .whenComplete((result, error) -> {
                if (error != null) {
                    LOG.warning("[BUS-GUARD] Primary fallo post-envio: " + error.getMessage());
                    circuitOpen.set(true);
                    standbyBus.publish(topic, envelope);
                    messagesSentToStandby.incrementAndGet();
                    latencyMonitor.record(topic, startTime, true, true);
                } else {
                    messagesSentToPrimary.incrementAndGet();
                    latencyMonitor.record(topic, startTime, false, false);
                }
            });
    }

    private <T> CompletableFuture<Void> publishToStandby(String topic, SovereignEnvelope<T> envelope,
                                                          long startTime, boolean isCircuitOpen) {
        if (isCircuitOpen) {
            LOG.fine("[BUS-GUARD] Circuito ABIERTO - desviando a standby persistente");
        }

        return standbyBus.publish(topic, envelope)
            .whenComplete((result, error) -> {
                if (error == null) {
                    messagesSentToStandby.incrementAndGet();
                }
                latencyMonitor.record(topic, startTime, true, isCircuitOpen);
            });
    }

    private void onMessageReplayed() {
        messagesReplayed.incrementAndGet();
    }

    @Override
    public <T, R> CompletableFuture<R> request(String topic, SovereignEnvelope<T> envelope,
                                                Class<R> responseType, Duration timeout) {
        if (primaryBus.isAvailable() && !circuitOpen.get()) {
            return primaryBus.request(topic, envelope, responseType, timeout);
        }

        return standbyBus.request(topic, envelope, responseType, timeout);
    }

    @Override
    public <T> void subscribe(String topic, Class<T> payloadType, Consumer<SovereignEnvelope<T>> consumer) {
        primaryBus.subscribe(topic, payloadType, consumer);
        standbyBus.subscribe(topic, payloadType, consumer);
    }

    @Override
    public long getInFlightCount() {
        return primaryBus.getInFlightCount() + standbyBus.getInFlightCount();
    }

    @Override
    public boolean isHealthy() {
        return primaryBus.isHealthy() || standbyBus.isHealthy();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean hasPendingMessages() {
        return standbyBus.hasPendingMessages();
    }

    @Override
    public int getPriority() {
        return GUARD_PRIORITY;
    }

    @Override
    public void shutdown(Duration timeout) {
        LOG.info("[BUS-GUARD] Iniciando shutdown...");

        replayEngine.stop();

        primaryBus.shutdown(timeout.dividedBy(2));
        standbyBus.shutdown(timeout.dividedBy(2));

        LOG.info("[BUS-GUARD] Metricas finales:");
        LOG.info("    Mensajes a Primary: " + messagesSentToPrimary.get());
        LOG.info("    Mensajes a Standby: " + messagesSentToStandby.get());
        LOG.info("    Mensajes Replayed:  " + messagesReplayed.get());
        latencyMonitor.printSummary();

        LOG.info("[BUS-GUARD] Shutdown completado");
    }

    public void openCircuit() {
        circuitOpen.set(true);
        LOG.warning("[BUS-GUARD] Circuit breaker ABIERTO manualmente");
    }

    public void closeCircuit() {
        if (primaryBus.isAvailable()) {
            circuitOpen.set(false);
            LOG.info("[BUS-GUARD] Circuit breaker CERRADO manualmente");
        } else {
            LOG.warning("[BUS-GUARD] No se puede cerrar circuito - primary no disponible");
        }
    }

    public boolean isCircuitOpen() {
        return circuitOpen.get();
    }

    public GuardStats getStats() {
        return new GuardStats(
            messagesSentToPrimary.get(),
            messagesSentToStandby.get(),
            messagesReplayed.get(),
            circuitOpen.get(),
            primaryBus.isAvailable(),
            standbyBus.hasPendingMessages(),
            latencyMonitor.getAverageLatencyMs(false),
            latencyMonitor.getAverageLatencyMs(true)
        );
    }

    public record GuardStats(
        long messagesSentToPrimary,
        long messagesSentToStandby,
        long messagesReplayed,
        boolean circuitOpen,
        boolean primaryAvailable,
        boolean hasPendingMessages,
        double avgLatencyDirectMs,
        double avgLatencyReplayMs
    ) {}
}
