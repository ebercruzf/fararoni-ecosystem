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
package dev.fararoni.core.core.sati;

/**
 * @since 1.0.0
 */
public record SidecarNode(
    String id,
    String type,
    String target,
    int port,
    int priority,
    long lastLatencyUs,
    long lastSeen,
    String status,
    double loadFactor,
    int activeRequests,
    long totalRequests,
    long totalErrors
) {
    private static final long STALE_THRESHOLD_MS = 15_000;

    private static final long MAX_ACCEPTABLE_LATENCY_US = 100_000;

    public boolean isHealthy() {
        boolean recentHeartbeat = (System.currentTimeMillis() - lastSeen) < STALE_THRESHOLD_MS;
        boolean acceptableLatency = lastLatencyUs >= 0 && lastLatencyUs < MAX_ACCEPTABLE_LATENCY_US;
        return "READY".equals(status) && recentHeartbeat && acceptableLatency;
    }

    public boolean isAvailable() {
        boolean recentHeartbeat = (System.currentTimeMillis() - lastSeen) < STALE_THRESHOLD_MS;
        return !"OFFLINE".equals(status) && recentHeartbeat;
    }

    public double getScore() {
        if (!isAvailable()) return Double.NEGATIVE_INFINITY;

        double baseScore = priority * 1_000_000.0;

        double latencyPenalty = lastLatencyUs >= 0 ? lastLatencyUs : 500_000;

        double loadPenalty = loadFactor * 100_000;

        double activePenalty = activeRequests * 10_000;

        return baseScore - latencyPenalty - loadPenalty - activePenalty;
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - lastSeen;
    }

    public double getErrorRate() {
        if (totalRequests == 0) return 0.0;
        return (double) totalErrors / totalRequests;
    }

    @Override
    public String toString() {
        return String.format(
            "SidecarNode[id=%s, priority=%d, latency=%dus, status=%s, load=%.2f, active=%d]",
            id, priority, lastLatencyUs, status, loadFactor, activeRequests
        );
    }
}
