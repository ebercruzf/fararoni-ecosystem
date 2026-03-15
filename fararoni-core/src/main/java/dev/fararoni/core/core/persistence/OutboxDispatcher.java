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
package dev.fararoni.core.core.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.core.core.bus.InMemorySovereignBus;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class OutboxDispatcher implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(OutboxDispatcher.class.getName());

    private static final int BATCH_SIZE = 100;

    private final SovereignJournal journal;
    private final InMemorySovereignBus bus;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;

    private final AtomicLong recoveredCount = new AtomicLong(0);
    private final AtomicLong failedRecoveryCount = new AtomicLong(0);

    public OutboxDispatcher(SovereignJournal journal, InMemorySovereignBus bus) {
        this.journal = journal;
        this.bus = bus;
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("outbox-dispatcher-", 0).factory()
        );
        this.running = new AtomicBoolean(false);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            LOG.info("[OUTBOX-DISPATCHER] Iniciando...");

            recover();

            scheduler.scheduleWithFixedDelay(
                this::periodicCleanup,
                1, 1, TimeUnit.HOURS
            );

            LOG.info("[OUTBOX-DISPATCHER] Iniciado. Recuperados: " + recoveredCount.get() +
                     ", Fallidos: " + failedRecoveryCount.get());
        }
    }

    private void recover() {
        List<OutboxEvent> pending = journal.recoverPending();

        if (pending.isEmpty()) {
            LOG.info("[OUTBOX-DISPATCHER] No hay mensajes pendientes");
            return;
        }

        LOG.info("[OUTBOX-DISPATCHER] Recuperando " + pending.size() + " mensajes pendientes...");

        for (OutboxEvent event : pending) {
            try {
                dispatchEvent(event);
                recoveredCount.incrementAndGet();
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                    "[OUTBOX-DISPATCHER] Fallo en recovery de: " + event.id(), e);
                failedRecoveryCount.incrementAndGet();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchEvent(OutboxEvent event) {
        try {
            if (event.isExpired()) {
                LOG.warning("[OUTBOX-DISPATCHER] Evento expirado, marcando como EXPIRED: " + event.id());
                journal.markFailed(event.id(), "Expirado durante recovery");
                return;
            }

            SovereignEnvelope<?> envelope = mapper.readValue(
                event.payloadJson(),
                SovereignEnvelope.class
            );

            bus.publishWithoutPersist(event.topic(), envelope)
                .thenRun(() -> {
                    journal.markDispatched(event.id());
                    LOG.fine("[OUTBOX-DISPATCHER] Recuperado exitosamente: " + event.id());
                })
                .exceptionally(ex -> {
                    handleDispatchError(event, ex);
                    return null;
                });
        } catch (Exception e) {
            handleDispatchError(event, e);
        }
    }

    private void handleDispatchError(OutboxEvent event, Throwable error) {
        LOG.warning("[OUTBOX-DISPATCHER] Error despachando " + event.id() +
                   ": " + error.getMessage());

        if (event.canRetry()) {
            LOG.fine("[OUTBOX-DISPATCHER] Reintento pendiente para: " + event.id());
        } else {
            journal.markFailed(event.id(), error.getMessage());
        }
    }

    private void periodicCleanup() {
        if (!running.get()) return;

        try {
            journal.cleanup();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[OUTBOX-DISPATCHER] Error en cleanup periódico", e);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            LOG.info("[OUTBOX-DISPATCHER] Detenido. Total recuperados: " + recoveredCount.get());
        }
    }

    public long getRecoveredCount() {
        return recoveredCount.get();
    }

    public long getFailedRecoveryCount() {
        return failedRecoveryCount.get();
    }

    @Override
    public void close() {
        stop();
    }
}
