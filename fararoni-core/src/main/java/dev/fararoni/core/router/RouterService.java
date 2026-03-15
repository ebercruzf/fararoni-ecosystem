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
package dev.fararoni.core.router;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public interface RouterService {
    RoutingResult route(String userInput);

    boolean isAvailable();

    void warmup();

    void shutdown();

    default String getName() {
        return getClass().getSimpleName();
    }

    default boolean isLlmBased() {
        return false;
    }

    default RouterStats getStats() {
        return null;
    }

    default void setActiveContext(String context) {
    }

    default void clearActiveContext() {
        setActiveContext(null);
    }

    record RouterStats(
        long totalRequests,
        long successfulRoutes,
        long fallbackRoutes,
        double averageLatencyMs,
        double p95LatencyMs,
        long modelLoadTimeMs,
        long memoryUsageBytes
    ) {
        public double getSuccessRate() {
            return totalRequests > 0 ? (double) successfulRoutes / totalRequests : 0.0;
        }

        public double getFallbackRate() {
            return totalRequests > 0 ? (double) fallbackRoutes / totalRequests : 0.0;
        }
    }

    class RouterException extends RuntimeException {
        public RouterException(String message) {
            super(message);
        }

        public RouterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
