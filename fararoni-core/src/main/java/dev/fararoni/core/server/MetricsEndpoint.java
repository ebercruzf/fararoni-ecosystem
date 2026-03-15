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

import dev.fararoni.bus.agent.api.telemetry.SovereignMetrics;
import dev.fararoni.core.observability.TelemetryService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class MetricsEndpoint {
    private static final Logger LOG = Logger.getLogger(MetricsEndpoint.class.getName());

    private final TelemetryService telemetry;
    private final Instant startTime;

    private PluginWebSocketBridge pluginBridge;

    public MetricsEndpoint() {
        this.telemetry = TelemetryService.getInstance();
        this.startTime = Instant.now();
    }

    public void register(Javalin app) {
        app.get("/api/metrics", this::handleMetrics);
        app.get("/api/health", this::handleHealth);
        app.get("/api/stats", this::handleStats);

        LOG.info("[METRICS] Endpoints registrados: /api/metrics, /api/health, /api/stats");
    }

    public void setPluginBridge(PluginWebSocketBridge bridge) {
        this.pluginBridge = bridge;
    }

    private void handleMetrics(Context ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Fararoni Metrics\n");
        sb.append("# Generated: ").append(Instant.now()).append("\n\n");

        long uptimeSeconds = Duration.between(startTime, Instant.now()).getSeconds();
        appendMetric(sb, "fararoni_uptime_seconds", "gauge",
            "Server uptime in seconds", uptimeSeconds);

        appendMetric(sb, "fararoni_bus_messages_published_total", "counter",
            "Total messages published to bus",
            SovereignMetrics.INSTANCE.getCount("bus.messages.published"));

        appendMetric(sb, "fararoni_bus_messages_delivered_total", "counter",
            "Total messages delivered",
            SovereignMetrics.INSTANCE.getCount("bus.messages.delivered"));

        appendMetric(sb, "fararoni_bus_messages_dlq_total", "counter",
            "Messages sent to Dead Letter Queue",
            SovereignMetrics.INSTANCE.getCount("bus.messages.max_hops"));

        if (pluginBridge != null) {
            appendMetric(sb, "fararoni_plugins_connected", "gauge",
                "Currently connected plugins",
                pluginBridge.getConnectionCount());
        }

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();

        appendMetric(sb, "fararoni_jvm_heap_used_bytes", "gauge",
            "JVM heap memory used", heapUsed);
        appendMetric(sb, "fararoni_jvm_heap_max_bytes", "gauge",
            "JVM heap memory max", heapMax);

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        appendMetric(sb, "fararoni_jvm_threads", "gauge",
            "Current thread count", threadBean.getThreadCount());

        if (telemetry.isEnabled()) {
            sb.append("\n# Micrometer metrics\n");
            sb.append(telemetry.scrape());
        }

        ctx.contentType("text/plain; version=0.0.4; charset=utf-8");
        ctx.result(sb.toString());
    }

    private void handleHealth(Context ctx) {
        boolean healthy = true;
        StringBuilder details = new StringBuilder();

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        double heapUsage = (double) memoryBean.getHeapMemoryUsage().getUsed() /
                           memoryBean.getHeapMemoryUsage().getMax();
        if (heapUsage > 0.9) {
            healthy = false;
            details.append("WARN: High heap usage (").append(String.format("%.1f", heapUsage * 100)).append("%)");
        }

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (threadBean.getThreadCount() > 1000) {
            healthy = false;
            details.append("WARN: High thread count (").append(threadBean.getThreadCount()).append(")");
        }

        ctx.json(Map.of(
            "status", healthy ? "UP" : "DEGRADED",
            "timestamp", Instant.now().toString(),
            "uptime_seconds", Duration.between(startTime, Instant.now()).getSeconds(),
            "checks", Map.of(
                "memory", Map.of(
                    "status", heapUsage < 0.9 ? "OK" : "WARN",
                    "heap_usage_percent", String.format("%.1f", heapUsage * 100)
                ),
                "threads", Map.of(
                    "status", threadBean.getThreadCount() < 1000 ? "OK" : "WARN",
                    "count", threadBean.getThreadCount()
                ),
                "plugins", Map.of(
                    "connected", pluginBridge != null ? pluginBridge.getConnectionCount() : 0
                )
            ),
            "details", details.toString()
        ));
    }

    private void handleStats(Context ctx) {
        ctx.json(Map.of(
            "timestamp", Instant.now().toString(),
            "uptime_seconds", Duration.between(startTime, Instant.now()).getSeconds(),
            "bus", Map.of(
                "messages_published", SovereignMetrics.INSTANCE.getCount("bus.messages.published"),
                "messages_delivered", SovereignMetrics.INSTANCE.getCount("bus.messages.delivered"),
                "dlq_count", SovereignMetrics.INSTANCE.getCount("bus.messages.max_hops")
            ),
            "plugins", Map.of(
                "connected", pluginBridge != null ? pluginBridge.getConnectionCount() : 0,
                "ids", pluginBridge != null ? pluginBridge.getConnectedPluginIds() : java.util.Set.of()
            ),
            "jvm", Map.of(
                "heap_used_mb", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024),
                "heap_max_mb", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / (1024 * 1024),
                "threads", ManagementFactory.getThreadMXBean().getThreadCount()
            )
        ));
    }

    private void appendMetric(StringBuilder sb, String name, String type,
                              String help, long value) {
        sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
        sb.append("# TYPE ").append(name).append(" ").append(type).append("\n");
        sb.append(name).append(" ").append(value).append("\n\n");
    }
}
