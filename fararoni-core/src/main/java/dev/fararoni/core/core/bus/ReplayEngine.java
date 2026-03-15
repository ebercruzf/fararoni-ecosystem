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

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ReplayEngine {
    private static final Logger LOG = Logger.getLogger(ReplayEngine.class.getName());

    private static final int HEALTH_CHECK_INTERVAL_SECONDS = 5;

    private static final int REPLAY_THROTTLE_MS = 10;

    private static final int LATENCY_THRESHOLD_MS = 200;

    private static final int ADAPTIVE_THROTTLE_MS = 50;

    private final SovereignEventBus primaryBus;

    private final SovereignEventBus standbyBus;

    private final Runnable onMessageReplayed;

    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final AtomicBoolean replayInProgress = new AtomicBoolean(false);

    private final AtomicLong totalReplayed = new AtomicLong(0);

    private final AtomicLong replayFailures = new AtomicLong(0);

    public ReplayEngine(SovereignEventBus primaryBus, SovereignEventBus standbyBus, Runnable onMessageReplayed) {
        this.primaryBus = primaryBus;
        this.standbyBus = standbyBus;
        this.onMessageReplayed = onMessageReplayed;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ReplayEngine-Monitor");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(
                this::checkAndReplay,
                HEALTH_CHECK_INTERVAL_SECONDS,
                HEALTH_CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
            );
            LOG.info("[REPLAY-ENGINE] Iniciado - Health check cada " + HEALTH_CHECK_INTERVAL_SECONDS + "s");
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
            LOG.info("[REPLAY-ENGINE] Detenido - Total replayados: " + totalReplayed.get());
        }
    }

    private void checkAndReplay() {
        try {
            if (replayInProgress.get()) {
                return;
            }

            boolean primaryUp = primaryBus.isAvailable();
            boolean hasPending = standbyBus.hasPendingMessages();

            if (primaryUp && hasPending) {
                LOG.info("[REPLAY-ENGINE] Condiciones cumplidas - Iniciando replay...");
                executeReplay();
            } else if (!primaryUp && hasPending) {
                LOG.fine("[REPLAY-ENGINE] Primary offline - " + getPendingCount() + " mensajes en espera");
            }
        } catch (Exception e) {
            LOG.warning("[REPLAY-ENGINE] Error en health check: " + e.getMessage());
        }
    }

    private void executeReplay() {
        if (!replayInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            LOG.info("[MILITARY-RECOVERY] Iniciando drenaje de mensajes acumulados...");

            int batchCount = 0;
            long startTime = System.currentTimeMillis();

            while (running.get() && primaryBus.isAvailable() && standbyBus.hasPendingMessages()) {
                try {
                    if (standbyBus instanceof ChronicleQueueBus chronicleBus) {
                        int replayed = chronicleBus.replayNextBatch(primaryBus, 100);
                        if (replayed > 0) {
                            batchCount += replayed;
                            totalReplayed.addAndGet(replayed);
                            for (int i = 0; i < replayed; i++) {
                                onMessageReplayed.run();
                            }
                        }
                    } else {
                        LOG.warning("[REPLAY-ENGINE] Standby no soporta replay directo");
                        break;
                    }

                    Thread.sleep(REPLAY_THROTTLE_MS);

                    if (batchCount % 1000 == 0 && batchCount > 0) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        double rate = (batchCount * 1000.0) / elapsed;
                        LOG.info("[REPLAY-ENGINE] Progreso: " + batchCount + " mensajes (" +
                            String.format("%.1f", rate) + " msg/s)");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warning("[REPLAY-ENGINE] Replay interrumpido");
                    break;
                } catch (Exception e) {
                    replayFailures.incrementAndGet();
                    LOG.warning("[REPLAY-ENGINE] Error durante replay: " + e.getMessage());

                    if (!primaryBus.isAvailable()) {
                        LOG.warning("[REPLAY-ENGINE] Primary caido durante replay - Pausando");
                        break;
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            if (batchCount > 0) {
                LOG.info("[REPLAY-ENGINE] Replay completado: " + batchCount + " mensajes en " + elapsed + "ms");
            }
        } finally {
            replayInProgress.set(false);
        }
    }

    private long getPendingCount() {
        if (standbyBus instanceof ChronicleQueueBus chronicleBus) {
            return chronicleBus.getInFlightCount();
        }
        return -1;
    }

    public long getTotalReplayed() {
        return totalReplayed.get();
    }

    public long getReplayFailures() {
        return replayFailures.get();
    }

    public boolean isReplayInProgress() {
        return replayInProgress.get();
    }

    public boolean isRunning() {
        return running.get();
    }
}
