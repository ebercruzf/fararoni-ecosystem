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
package dev.fararoni.core.server;

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.bus.BusFactory;
import dev.fararoni.core.core.kernel.HyperNativeKernel;
import dev.fararoni.core.core.persistence.JournalManager;
import dev.fararoni.core.core.swarm.HiveMind;
import dev.fararoni.core.core.swarm.infra.MessageBus;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SessionManager {
    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());

    public record TenantSilo(
        String userId,
        HiveMind hive,
        MessageBus bus,
        Instant createdAt,
        Instant lastActivity
    ) {
        public TenantSilo touch() {
            return new TenantSilo(userId, hive, bus, createdAt, Instant.now());
        }
    }

    private final Map<String, TenantSilo> sessions = new ConcurrentHashMap<>();
    private final HyperNativeKernel sharedKernel;

    private final JournalManager journal;

    private SovereignEventBus defaultBus;

    private final ScheduledExecutorService janitor = Executors.newSingleThreadScheduledExecutor(
        Thread.ofVirtual().name("session-janitor-", 0).factory()
    );

    private long sessionTimeoutSeconds = 1800;
    private long cleanupIntervalSeconds = 600;
    private int maxSessionsPerIp = 10;
    private int maxTotalSessions = 1000;

    private long sessionsCreated = 0;
    private long sessionsExpired = 0;
    private long sessionsActive = 0;

    public SessionManager(HyperNativeKernel kernel) {
        this(kernel, (JournalManager) null);
    }

    public SessionManager(HyperNativeKernel kernel, JournalManager journal) {
        this.sharedKernel = kernel;
        this.journal = journal;
        startJanitor();

        if (journal != null) {
            LOG.info(() -> "[SESSION] Persistencia habilitada: " + journal.getJournalDir());
        }
    }

    public SessionManager(HyperNativeKernel kernel, Path journalPath) {
        this(kernel, journalPath != null ? new JournalManager(journalPath) : null);
    }

    public SessionManager(HyperNativeKernel kernel, String journalPath) {
        this(kernel, journalPath != null ? new JournalManager(journalPath) : null);
    }

    private void startJanitor() {
        janitor.scheduleAtFixedRate(
            this::cleanup,
            cleanupIntervalSeconds,
            cleanupIntervalSeconds,
            TimeUnit.SECONDS
        );
        LOG.info(() -> String.format(
            "[SESSION] Janitor iniciado. Limpieza cada %d segundos, timeout %d segundos.",
            cleanupIntervalSeconds, sessionTimeoutSeconds));
    }

    public TenantSilo getOrCreateSession(String userId) {
        return sessions.compute(userId, (id, existing) -> {
            if (existing != null) {
                return existing.touch();
            }

            if (sessions.size() >= maxTotalSessions) {
                throw new IllegalStateException(
                    "Límite de sesiones alcanzado: " + maxTotalSessions);
            }

            return createSilo(id);
        });
    }

    private TenantSilo createSilo(String userId) {
        LOG.info(() -> "[SESSION] Creando Silo para: " + userId);

        MessageBus bus = new MessageBus();

        if (journal != null) {
            bus.connectJournal(journal);
            LOG.fine(() -> "[SESSION] Journal conectado para usuario: " + userId);
        }

        HiveMind hive = new HiveMind(sharedKernel, bus);

        Instant now = Instant.now();
        sessionsCreated++;
        sessionsActive = sessions.size() + 1;

        return new TenantSilo(userId, hive, bus, now, now);
    }

    public JournalManager getJournal() {
        return journal;
    }

    public boolean isPersistenceEnabled() {
        return journal != null;
    }

    public SovereignEventBus getDefaultBus() {
        if (defaultBus == null) {
            defaultBus = BusFactory.create();
            LOG.info("[SESSION] Default SovereignEventBus creado para plugins");
        }
        return defaultBus;
    }

    public TenantSilo getSession(String userId) {
        TenantSilo silo = sessions.get(userId);
        if (silo != null) {
            sessions.put(userId, silo.touch());
        }
        return silo;
    }

    public boolean hasSession(String userId) {
        return sessions.containsKey(userId);
    }

    public boolean removeSession(String userId) {
        TenantSilo removed = sessions.remove(userId);
        if (removed != null) {
            LOG.info(() -> "[SESSION] Sesión eliminada: " + userId);
            sessionsActive = sessions.size();
            return true;
        }
        return false;
    }

    public Set<String> getActiveSessionIds() {
        return Set.copyOf(sessions.keySet());
    }

    private void cleanup() {
        Instant threshold = Instant.now().minusSeconds(sessionTimeoutSeconds);
        int beforeCount = sessions.size();

        sessions.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().lastActivity().isBefore(threshold);
            if (expired) {
                LOG.info(() -> "[JANITOR] Eliminando sesión inactiva: " + entry.getKey());
                sessionsExpired++;
            }
            return expired;
        });

        int removed = beforeCount - sessions.size();
        sessionsActive = sessions.size();

        if (removed > 0) {
            LOG.info(() -> String.format(
                "[JANITOR] Limpieza completada. Eliminadas: %d, Activas: %d",
                removed, sessionsActive));
        }
    }

    public int forceCleanup() {
        int before = sessions.size();
        cleanup();
        return before - sessions.size();
    }

    public void setSessionTimeout(long seconds) {
        this.sessionTimeoutSeconds = seconds;
    }

    public void setCleanupInterval(long seconds) {
        this.cleanupIntervalSeconds = seconds;
    }

    public void setMaxTotalSessions(int max) {
        this.maxTotalSessions = max;
    }

    public SessionMetrics getMetrics() {
        return new SessionMetrics(
            sessionsCreated,
            sessionsExpired,
            sessions.size(),
            maxTotalSessions,
            sessionTimeoutSeconds
        );
    }

    public record SessionMetrics(
        long totalCreated,
        long totalExpired,
        int currentActive,
        int maxAllowed,
        long timeoutSeconds
    ) {
        public double utilizationRate() {
            return maxAllowed > 0 ? (double) currentActive / maxAllowed : 0.0;
        }
    }

    public void shutdown() {
        LOG.info("[SESSION] Shutdown iniciado...");
        janitor.shutdown();
        try {
            if (!janitor.awaitTermination(5, TimeUnit.SECONDS)) {
                janitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            janitor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (defaultBus != null && defaultBus instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOG.warning("[SESSION] Error cerrando defaultBus: " + e.getMessage());
            }
        }

        sessions.clear();
        LOG.info("[SESSION] Shutdown completado.");
    }
}
