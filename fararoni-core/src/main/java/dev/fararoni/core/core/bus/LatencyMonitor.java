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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class LatencyMonitor {
    private static final Logger LOG = Logger.getLogger(LatencyMonitor.class.getName());

    private static final long WARNING_THRESHOLD_REPLAY_MS = 200;

    private static final long CRITICAL_THRESHOLD_MS = 500;

    private final LongAdder directTotalLatencyNanos = new LongAdder();
    private final LongAdder directMessageCount = new LongAdder();
    private final AtomicLong directMaxLatencyNanos = new AtomicLong(0);

    private final LongAdder standbyTotalLatencyNanos = new LongAdder();
    private final LongAdder standbyMessageCount = new LongAdder();
    private final AtomicLong standbyMaxLatencyNanos = new AtomicLong(0);

    private final LongAdder warningCount = new LongAdder();
    private final LongAdder criticalCount = new LongAdder();

    public long start() {
        return System.nanoTime();
    }

    public void record(String topic, long startNano, boolean isStandbyMode, boolean isCircuitOpen) {
        long durationNanos = System.nanoTime() - startNano;
        long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);

        if (isStandbyMode) {
            standbyTotalLatencyNanos.add(durationNanos);
            standbyMessageCount.increment();
            updateMax(standbyMaxLatencyNanos, durationNanos);

            if (durationMs > WARNING_THRESHOLD_REPLAY_MS) {
                warningCount.increment();
                LOG.warning(String.format("[LATENCY-MONITOR] ALTA LATENCIA en modo %s: %d ms | topic=%s",
                    isCircuitOpen ? "CIRCUIT-OPEN" : "FIFO-MODE", durationMs, topic));
            }
        } else {
            directTotalLatencyNanos.add(durationNanos);
            directMessageCount.increment();
            updateMax(directMaxLatencyNanos, durationNanos);
        }

        if (durationMs > CRITICAL_THRESHOLD_MS) {
            criticalCount.increment();
            LOG.severe(String.format("[LATENCY-MONITOR] LATENCIA CRITICA: %d ms | mode=%s | topic=%s",
                durationMs, isStandbyMode ? "STANDBY" : "DIRECT", topic));
        }
    }

    private void updateMax(AtomicLong maxHolder, long newValue) {
        long current;
        do {
            current = maxHolder.get();
            if (newValue <= current) {
                return;
            }
        } while (!maxHolder.compareAndSet(current, newValue));
    }

    public double getAverageLatencyMs(boolean standbyMode) {
        if (standbyMode) {
            long count = standbyMessageCount.sum();
            if (count == 0) return 0.0;
            return TimeUnit.NANOSECONDS.toMillis(standbyTotalLatencyNanos.sum()) / (double) count;
        } else {
            long count = directMessageCount.sum();
            if (count == 0) return 0.0;
            return TimeUnit.NANOSECONDS.toMillis(directTotalLatencyNanos.sum()) / (double) count;
        }
    }

    public long getMaxLatencyMs(boolean standbyMode) {
        if (standbyMode) {
            return TimeUnit.NANOSECONDS.toMillis(standbyMaxLatencyNanos.get());
        } else {
            return TimeUnit.NANOSECONDS.toMillis(directMaxLatencyNanos.get());
        }
    }

    public long getMessageCount(boolean standbyMode) {
        return standbyMode ? standbyMessageCount.sum() : directMessageCount.sum();
    }

    public double getOverheadPercentage() {
        double directAvg = getAverageLatencyMs(false);
        double standbyAvg = getAverageLatencyMs(true);

        if (directAvg == 0) return 0.0;
        return ((standbyAvg - directAvg) / directAvg) * 100;
    }

    public long getWarningCount() {
        return warningCount.sum();
    }

    public long getCriticalCount() {
        return criticalCount.sum();
    }

    public void printSummary() {
        LOG.info("=== INFORME DE RENDIMIENTO (LATENCY MONITOR) ===");
        LOG.info(String.format("  Modo DIRECTO:  %.2f ms avg | %d ms max | %d mensajes",
            getAverageLatencyMs(false), getMaxLatencyMs(false), getMessageCount(false)));
        LOG.info(String.format("  Modo STANDBY:  %.2f ms avg | %d ms max | %d mensajes",
            getAverageLatencyMs(true), getMaxLatencyMs(true), getMessageCount(true)));
        LOG.info(String.format("  Overhead:      %.1f%%", getOverheadPercentage()));
        LOG.info(String.format("  Alertas:       %d warnings | %d critical",
            getWarningCount(), getCriticalCount()));
        LOG.info("================================================");
    }

    public LatencySnapshot getSnapshot() {
        return new LatencySnapshot(
            getAverageLatencyMs(false),
            getAverageLatencyMs(true),
            getMaxLatencyMs(false),
            getMaxLatencyMs(true),
            getMessageCount(false),
            getMessageCount(true),
            getOverheadPercentage(),
            getWarningCount(),
            getCriticalCount()
        );
    }

    public record LatencySnapshot(
        double avgLatencyDirectMs,
        double avgLatencyStandbyMs,
        long maxLatencyDirectMs,
        long maxLatencyStandbyMs,
        long messageCountDirect,
        long messageCountStandby,
        double overheadPercentage,
        long warningCount,
        long criticalCount
    ) {}

    public void reset() {
        directTotalLatencyNanos.reset();
        directMessageCount.reset();
        directMaxLatencyNanos.set(0);
        standbyTotalLatencyNanos.reset();
        standbyMessageCount.reset();
        standbyMaxLatencyNanos.set(0);
        warningCount.reset();
        criticalCount.reset();
    }
}
