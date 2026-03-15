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
package dev.fararoni.core.core.skills.bridge;

import java.time.Instant;

public record SidecarRoute(
    String sidecarId,
    int priority,
    Instant connectedAt,
    double loadFactor,
    double healthScore,
    boolean isIsolated
) implements Comparable<SidecarRoute> {
    public static SidecarRoute from(CapabilityManager.SidecarInfo info) {
        return new SidecarRoute(
            info.sidecarId(),
            info.priority(),
            info.connectedAt(),
            0.0,
            1.0,
            false
        );
    }

    public SidecarRoute withDegradedHealth(double penalty) {
        return new SidecarRoute(
            this.sidecarId,
            this.priority,
            this.connectedAt,
            this.loadFactor,
            Math.max(0.0, this.healthScore - penalty),
            this.isIsolated
        );
    }

    public SidecarRoute withLoadFactor(double newLoadFactor) {
        return new SidecarRoute(
            this.sidecarId,
            this.priority,
            this.connectedAt,
            newLoadFactor,
            this.healthScore,
            this.isIsolated
        );
    }

    public SidecarRoute isolated() {
        return new SidecarRoute(
            this.sidecarId,
            this.priority,
            this.connectedAt,
            this.loadFactor,
            this.healthScore,
            true
        );
    }

    public SidecarRoute rehabilitated() {
        return new SidecarRoute(
            this.sidecarId,
            this.priority,
            this.connectedAt,
            this.loadFactor,
            this.healthScore,
            false
        );
    }

    @Override
    public int compareTo(SidecarRoute other) {
        if (this.isIsolated != other.isIsolated) {
            return this.isIsolated ? 1 : -1;
        }

        if (this.priority != other.priority) {
            return Integer.compare(other.priority, this.priority);
        }

        if (Double.compare(this.healthScore, other.healthScore) != 0) {
            return Double.compare(other.healthScore, this.healthScore);
        }

        return Double.compare(this.loadFactor, other.loadFactor);
    }

    public double computeScore() {
        if (isIsolated) return Double.NEGATIVE_INFINITY;
        return (priority * 0.6) + (healthScore * 100 * 0.3) - (loadFactor * 100 * 0.1);
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - connectedAt.toEpochMilli();
    }

    public boolean isActive() {
        return !isIsolated && healthScore > 0;
    }

    @Override
    public String toString() {
        return String.format("SidecarRoute[%s, p:%d, h:%.2f, l:%.2f%s]",
            sidecarId, priority, healthScore, loadFactor,
            isIsolated ? ", ISOLATED" : "");
    }
}
