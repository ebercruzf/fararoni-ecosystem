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
import dev.fararoni.core.core.resilience.PoisonPill;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SovereignJournal implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(SovereignJournal.class.getName());

    private final SovereignOutboxRepository repository;
    private final ObjectMapper mapper;

    private final AtomicLong eventsPersisted = new AtomicLong(0);
    private final AtomicLong eventsDispatched = new AtomicLong(0);
    private final AtomicLong eventsFailed = new AtomicLong(0);
    private final AtomicLong poisonPillsSaved = new AtomicLong(0);

    public SovereignJournal(Path dbPath) throws SQLException {
        this.repository = new SovereignOutboxRepository(dbPath);
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
        LOG.info("[SOVEREIGN-JOURNAL] Inicializado");
    }

    public <T> boolean persistEvent(String topic, SovereignEnvelope<T> envelope) {
        try {
            String json = mapper.writeValueAsString(envelope);
            boolean saved = repository.saveEvent(OutboxEvent.fromEnvelope(topic, envelope, json));
            if (saved) {
                eventsPersisted.incrementAndGet();
                LOG.fine("[SOVEREIGN-JOURNAL] Evento persistido: " + envelope.id());
            }
            return saved;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[SOVEREIGN-JOURNAL] Fallo crítico persistiendo evento " + envelope.id(), e);
            return false;
        }
    }

    public void markDispatched(String eventId) {
        try {
            repository.markDispatched(eventId);
            eventsDispatched.incrementAndGet();
            LOG.fine("[SOVEREIGN-JOURNAL] Evento despachado: " + eventId);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[SOVEREIGN-JOURNAL] Error marcando despachado: " + eventId, e);
        }
    }

    public void markFailed(String eventId, String error) {
        try {
            repository.markFailed(eventId, error);
            eventsFailed.incrementAndGet();
            LOG.warning("[SOVEREIGN-JOURNAL] Evento fallido: " + eventId + " - " + error);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "[SOVEREIGN-JOURNAL] Error marcando fallido: " + eventId, e);
        }
    }

    public void savePoisonPill(PoisonPill pill) {
        try {
            String json = pill.originalEnvelope() != null
                ? mapper.writeValueAsString(pill.originalEnvelope())
                : "{}";
            repository.savePoisonPill(pill, json);
            poisonPillsSaved.incrementAndGet();
            LOG.info("[SOVEREIGN-JOURNAL] PoisonPill persistido: " + pill.originalTopic());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[SOVEREIGN-JOURNAL] Error persistiendo PoisonPill", e);
        }
    }

    public List<SovereignOutboxRepository.PoisonPillRecord> getPendingPoisonPills(int limit) {
        try {
            return repository.getPendingPoisonPills(limit);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "[SOVEREIGN-JOURNAL] Error obteniendo PoisonPills", e);
            return List.of();
        }
    }

    public void markResurrected(String pillId) {
        try {
            repository.markPoisonPillProcessed(pillId, "RESURRECTED");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[SOVEREIGN-JOURNAL] Error marcando resurrección", e);
        }
    }

    public void markBuried(String pillId) {
        try {
            repository.markPoisonPillProcessed(pillId, "BURIED");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[SOVEREIGN-JOURNAL] Error marcando entierro", e);
        }
    }

    public List<OutboxEvent> recoverPending() {
        try {
            List<OutboxEvent> events = repository.getPendingEvents(1000);
            if (!events.isEmpty()) {
                LOG.info("[SOVEREIGN-JOURNAL] Recuperados " + events.size() + " eventos pendientes");
            }
            return events;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "[SOVEREIGN-JOURNAL] Error en recovery", e);
            return List.of();
        }
    }

    public void cleanup() {
        try {
            int expired = repository.cleanupExpired();
            int purged = repository.purgeOldEvents(7);
            if (expired > 0 || purged > 0) {
                LOG.info("[SOVEREIGN-JOURNAL] Cleanup: " + expired + " expirados, " + purged + " purgados");
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[SOVEREIGN-JOURNAL] Error en cleanup", e);
        }
    }

    public JournalMetrics getMetrics() {
        try {
            return new JournalMetrics(
                eventsPersisted.get(),
                eventsDispatched.get(),
                eventsFailed.get(),
                poisonPillsSaved.get(),
                repository.countByStatus(OutboxEvent.OutboxStatus.PENDING),
                repository.countByStatus(OutboxEvent.OutboxStatus.DISPATCHED)
            );
        } catch (SQLException e) {
            return new JournalMetrics(
                eventsPersisted.get(),
                eventsDispatched.get(),
                eventsFailed.get(),
                poisonPillsSaved.get(),
                -1, -1
            );
        }
    }

    public record JournalMetrics(
        long eventsPersisted,
        long eventsDispatched,
        long eventsFailed,
        long poisonPillsSaved,
        int pendingInDb,
        int dispatchedInDb
    ) {
        public double successRate() {
            if (eventsPersisted == 0) return 1.0;
            return (double) eventsDispatched / eventsPersisted;
        }
    }

    @Override
    public void close() throws Exception {
        repository.close();
        LOG.info("[SOVEREIGN-JOURNAL] Cerrado. Métricas finales: " + getMetrics());
    }
}
