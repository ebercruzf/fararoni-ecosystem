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
package dev.fararoni.core.core.safety.overseer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class OverseerMetrics {
    private static final Logger LOG = Logger.getLogger(OverseerMetrics.class.getName());

    private final AtomicLong totalValidations = new AtomicLong(0);
    private final AtomicLong successfulValidations = new AtomicLong(0);
    private final AtomicLong failedValidations = new AtomicLong(0);
    private final AtomicLong retriesSuccessful = new AtomicLong(0);
    private final AtomicLong retriesFailed = new AtomicLong(0);

    private final Map<String, AtomicLong> violationsByType = new ConcurrentHashMap<>();

    private final Map<String, AtomicLong> violationsByPattern = new ConcurrentHashMap<>();

    public void recordSuccess() {
        totalValidations.incrementAndGet();
        successfulValidations.incrementAndGet();
    }

    public void recordViolation(String type, String pattern) {
        totalValidations.incrementAndGet();
        failedValidations.incrementAndGet();

        violationsByType.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();
        violationsByPattern.computeIfAbsent(pattern, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordRetrySuccess() {
        retriesSuccessful.incrementAndGet();
    }

    public void recordRetryExhausted() {
        retriesFailed.incrementAndGet();
    }

    public long getTotalValidations() {
        return totalValidations.get();
    }

    public long getSuccessfulValidations() {
        return successfulValidations.get();
    }

    public long getFailedValidations() {
        return failedValidations.get();
    }

    public long getRetriesSuccessful() {
        return retriesSuccessful.get();
    }

    public long getRetriesFailed() {
        return retriesFailed.get();
    }

    public double getSuccessRate() {
        long total = totalValidations.get();
        if (total == 0) return 1.0;
        return (double) successfulValidations.get() / total;
    }

    public Map<String, Long> getViolationsByType() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        violationsByType.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public Map<String, Long> getViolationsByPattern() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        violationsByPattern.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public Map<String, Long> getTopViolatedPatterns(int n) {
        return violationsByPattern.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(n)
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().get(),
                (a, b) -> a,
                java.util.LinkedHashMap::new
            ));
    }

    public String toPrometheusFormat() {
        StringBuilder sb = new StringBuilder();

        sb.append("# HELP overseer_validations_total Total de validaciones ejecutadas\n");
        sb.append("# TYPE overseer_validations_total counter\n");
        sb.append("overseer_validations_total ").append(totalValidations.get()).append("\n\n");

        sb.append("# HELP overseer_validations_success Validaciones exitosas\n");
        sb.append("# TYPE overseer_validations_success counter\n");
        sb.append("overseer_validations_success ").append(successfulValidations.get()).append("\n\n");

        sb.append("# HELP overseer_validations_failed Validaciones fallidas\n");
        sb.append("# TYPE overseer_validations_failed counter\n");
        sb.append("overseer_validations_failed ").append(failedValidations.get()).append("\n\n");

        sb.append("# HELP overseer_success_rate Tasa de exito\n");
        sb.append("# TYPE overseer_success_rate gauge\n");
        sb.append("overseer_success_rate ").append(String.format("%.4f", getSuccessRate())).append("\n\n");

        sb.append("# HELP overseer_retries_successful Reintentos exitosos\n");
        sb.append("# TYPE overseer_retries_successful counter\n");
        sb.append("overseer_retries_successful ").append(retriesSuccessful.get()).append("\n\n");

        sb.append("# HELP overseer_retries_failed Reintentos agotados\n");
        sb.append("# TYPE overseer_retries_failed counter\n");
        sb.append("overseer_retries_failed ").append(retriesFailed.get()).append("\n\n");

        sb.append("# HELP overseer_violations_by_type Violaciones por tipo\n");
        sb.append("# TYPE overseer_violations_by_type counter\n");
        violationsByType.forEach((type, count) -> {
            sb.append("overseer_violations_by_type{type=\"").append(type).append("\"} ")
              .append(count.get()).append("\n");
        });

        return sb.toString();
    }

    public void reset() {
        totalValidations.set(0);
        successfulValidations.set(0);
        failedValidations.set(0);
        retriesSuccessful.set(0);
        retriesFailed.set(0);
        violationsByType.clear();
        violationsByPattern.clear();
        LOG.info("OverseerMetrics reset");
    }

    @Override
    public String toString() {
        return String.format(
            "OverseerMetrics{total=%d, success=%d (%.1f%%), failed=%d, retries_ok=%d, retries_fail=%d}",
            totalValidations.get(),
            successfulValidations.get(),
            getSuccessRate() * 100,
            failedValidations.get(),
            retriesSuccessful.get(),
            retriesFailed.get()
        );
    }
}
