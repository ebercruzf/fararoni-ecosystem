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
package dev.fararoni.core.observability;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.*;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class TelemetryService implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(TelemetryService.class.getName());

    private static volatile TelemetryService instance;

    private final PrometheusMeterRegistry registry;

    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private final boolean enabled;

    private TelemetryService() {
        this.enabled = !"false".equalsIgnoreCase(
            System.getenv("FARARONI_METRICS_ENABLED")
        );

        if (enabled) {
            this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            initializeDefaultMetrics();
            LOG.info("[TELEMETRY] Servicio inicializado con Prometheus registry");
        } else {
            this.registry = null;
            LOG.info("[TELEMETRY] Métricas deshabilitadas (FARARONI_METRICS_ENABLED=false)");
        }
    }

    public static TelemetryService getInstance() {
        if (instance == null) {
            synchronized (TelemetryService.class) {
                if (instance == null) {
                    instance = new TelemetryService();
                }
            }
        }
        return instance;
    }

    private void initializeDefaultMetrics() {
        if (!enabled || initialized.getAndSet(true)) {
            return;
        }

        try {
            new JvmMemoryMetrics().bindTo(registry);
            new JvmGcMetrics().bindTo(registry);
            new JvmThreadMetrics().bindTo(registry);
            new ProcessorMetrics().bindTo(registry);
            new UptimeMetrics().bindTo(registry);

            LOG.info("[TELEMETRY] Métricas JVM/System registradas");
        } catch (Exception e) {
            LOG.warning("[TELEMETRY] Error registrando métricas default: " + e.getMessage());
        }
    }

    public void incrementCounter(String name, String... tags) {
        if (!enabled) return;

        String key = buildKey(name, tags);
        counters.computeIfAbsent(key, k ->
            Counter.builder(name)
                .tags(tags)
                .description("Counter: " + name)
                .register(registry)
        ).increment();
    }

    public void incrementCounter(String name, double amount, String... tags) {
        if (!enabled) return;

        String key = buildKey(name, tags);
        counters.computeIfAbsent(key, k ->
            Counter.builder(name)
                .tags(tags)
                .description("Counter: " + name)
                .register(registry)
        ).increment(amount);
    }

    public AgentSpan startSpan(String name, String... tags) {
        if (!enabled) {
            return AgentSpan.NOOP;
        }

        String key = buildKey(name, tags);
        Timer timer = timers.computeIfAbsent(key, k ->
            Timer.builder(name)
                .tags(tags)
                .description("Timer: " + name)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
        );

        return new AgentSpan(timer);
    }

    public void recordTime(String name, Duration duration, String... tags) {
        if (!enabled) return;

        String key = buildKey(name, tags);
        timers.computeIfAbsent(key, k ->
            Timer.builder(name)
                .tags(tags)
                .description("Timer: " + name)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
        ).record(duration);
    }

    public <T> T recordTime(String name, Supplier<T> operation, String... tags) {
        if (!enabled) {
            return operation.get();
        }

        String key = buildKey(name, tags);
        Timer timer = timers.computeIfAbsent(key, k ->
            Timer.builder(name)
                .tags(tags)
                .description("Timer: " + name)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
        );

        return timer.record(operation);
    }

    public void registerGauge(String name, Supplier<Number> valueSupplier, String... tags) {
        if (!enabled) return;

        Gauge.builder(name, valueSupplier)
            .tags(tags)
            .description("Gauge: " + name)
            .register(registry);
    }

    public void recordDistribution(String name, double value, String... tags) {
        if (!enabled) return;

        DistributionSummary.builder(name)
            .tags(tags)
            .description("Distribution: " + name)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
            .record(value);
    }

    public String scrape() {
        if (!enabled || registry == null) {
            return "# Metrics disabled\n";
        }
        return registry.scrape();
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private String buildKey(String name, String... tags) {
        if (tags.length == 0) {
            return name;
        }
        StringBuilder key = new StringBuilder(name);
        for (int i = 0; i < tags.length; i += 2) {
            key.append(".").append(tags[i]).append("=").append(tags[i + 1]);
        }
        return key.toString();
    }

    @Override
    public void close() {
        if (registry != null) {
            registry.close();
            LOG.info("[TELEMETRY] Registry cerrado");
        }
    }

    public static void reset() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}
