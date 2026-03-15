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
package dev.fararoni.bus.spi;

/**
 * Health status indicators for FararoniModule instances.
 *
 * <p>Used by health check endpoints to report module status.
 * The system aggregates all module health statuses to determine
 * overall system health.</p>
 *
 * <h2>Status Hierarchy</h2>
 * <pre>
 * HEALTHY   - Module is fully operational
 * DEGRADED  - Module is operational but with reduced capacity
 * FAILED    - Module is not operational
 * UNKNOWN   - Module has not reported health yet
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * public ModuleHealth getHealth() {
 *     if (!server.isRunning()) {
 *         return ModuleHealth.FAILED;
 *     }
 *     if (rateLimiter.isThrottling()) {
 *         return ModuleHealth.DEGRADED;
 *     }
 *     return ModuleHealth.HEALTHY;
 * }
 * }</pre>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see FararoniModule#getHealth()
 */
public enum ModuleHealth {

    /**
     * Module is fully operational and accepting work.
     *
     * <p>All internal components are running correctly.
     * No errors or warnings to report.</p>
     */
    HEALTHY("healthy", true),

    /**
     * Module is operational but with reduced capacity.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Rate limiter is actively throttling requests</li>
     *   <li>Connection pool is near capacity</li>
     *   <li>Retry backoff is in effect</li>
     * </ul>
     */
    DEGRADED("degraded", true),

    /**
     * Module is not operational.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Server failed to bind to port</li>
     *   <li>Required dependency is unavailable</li>
     *   <li>Unrecoverable error occurred</li>
     * </ul>
     */
    FAILED("failed", false),

    /**
     * Module has not reported health yet.
     *
     * <p>This is the default state before the first health check.
     * Should not persist for long after startup.</p>
     */
    UNKNOWN("unknown", false);

    private final String status;
    private final boolean operational;

    ModuleHealth(String status, boolean operational) {
        this.status = status;
        this.operational = operational;
    }

    /**
     * Returns the string representation for JSON/API responses.
     *
     * @return lowercase status string
     */
    public String getStatus() {
        return status;
    }

    /**
     * Indicates if the module can accept work.
     *
     * <p>Both HEALTHY and DEGRADED are considered operational.
     * FAILED and UNKNOWN are not.</p>
     *
     * @return true if module can accept work
     */
    public boolean isOperational() {
        return operational;
    }

    /**
     * Aggregates multiple health statuses into a single status.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Any FAILED = FAILED</li>
     *   <li>Any UNKNOWN (without FAILED) = UNKNOWN</li>
     *   <li>Any DEGRADED (without FAILED/UNKNOWN) = DEGRADED</li>
     *   <li>All HEALTHY = HEALTHY</li>
     * </ul>
     *
     * @param statuses array of health statuses to aggregate
     * @return the aggregate health status
     */
    public static ModuleHealth aggregate(ModuleHealth... statuses) {
        if (statuses == null || statuses.length == 0) {
            return UNKNOWN;
        }

        boolean hasUnknown = false;
        boolean hasDegraded = false;

        for (ModuleHealth status : statuses) {
            if (status == FAILED) {
                return FAILED;
            }
            if (status == UNKNOWN) {
                hasUnknown = true;
            }
            if (status == DEGRADED) {
                hasDegraded = true;
            }
        }

        if (hasUnknown) {
            return UNKNOWN;
        }
        if (hasDegraded) {
            return DEGRADED;
        }
        return HEALTHY;
    }
}
