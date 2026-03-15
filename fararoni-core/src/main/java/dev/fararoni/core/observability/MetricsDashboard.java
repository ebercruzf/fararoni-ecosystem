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

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.bus.agent.api.telemetry.SovereignMetrics;
import io.javalin.Javalin;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class MetricsDashboard implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(MetricsDashboard.class.getName());

    private static final long MIN_INTERVAL_MS = 500;
    private static final long DEFAULT_INTERVAL_MS = 1000;
    private static final int MAX_CLIENTS = 100;

    private final ObjectMapper mapper;
    private final TelemetryService telemetry;
    private final Instant startTime;

    private final Map<WsContext, Long> subscribers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private ScheduledFuture<?> broadcastTask;

    public MetricsDashboard() {
        this.mapper = new ObjectMapper();
        this.telemetry = TelemetryService.getInstance();
        this.startTime = Instant.now();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-dashboard");
            t.setDaemon(true);
            return t;
        });
    }

    public void register(Javalin app) {
        app.ws("/api/metrics/live", ws -> {
            ws.onConnect(this::handleConnect);
            ws.onMessage(this::handleMessage);
            ws.onClose(this::handleClose);
            ws.onError(ctx -> {
                LOG.warning("[DASHBOARD] Error en conexión: " + ctx.error());
                subscribers.remove(ctx);
            });
        });

        startBroadcast();

        LOG.info("[DASHBOARD] Endpoint /api/metrics/live registrado");
    }

    private void handleConnect(WsContext ctx) {
        if (subscribers.size() >= MAX_CLIENTS) {
            ctx.closeSession(4001, "Max clients exceeded");
            return;
        }

        subscribers.put(ctx, DEFAULT_INTERVAL_MS);

        LOG.info("[DASHBOARD] Cliente conectado. Total: " + subscribers.size());

        sendJson(ctx, Map.of(
            "type", "connected",
            "message", "Subscribed to metrics stream",
            "interval_ms", DEFAULT_INTERVAL_MS,
            "timestamp", Instant.now().toString()
        ));

        sendMetrics(ctx);
    }

    private void handleMessage(WsMessageContext ctx) {
        try {
            String message = ctx.message();
            var json = mapper.readTree(message);
            String action = json.has("action") ? json.get("action").asText() : "";

            switch (action) {
                case "subscribe" -> {
                    long interval = json.has("interval_ms")
                        ? Math.max(MIN_INTERVAL_MS, json.get("interval_ms").asLong())
                        : DEFAULT_INTERVAL_MS;
                    subscribers.put(ctx, interval);
                    sendJson(ctx, Map.of(
                        "type", "subscribed",
                        "interval_ms", interval,
                        "timestamp", Instant.now().toString()
                    ));
                }
                case "unsubscribe" -> {
                    subscribers.remove(ctx);
                    sendJson(ctx, Map.of(
                        "type", "unsubscribed",
                        "timestamp", Instant.now().toString()
                    ));
                }
                case "ping" -> sendJson(ctx, Map.of(
                    "type", "pong",
                    "timestamp", System.currentTimeMillis()
                ));
                case "snapshot" -> sendMetrics(ctx);
                default -> LOG.fine("[DASHBOARD] Acción desconocida: " + action);
            }
        } catch (Exception e) {
            LOG.warning("[DASHBOARD] Error procesando mensaje: " + e.getMessage());
        }
    }

    private void handleClose(WsCloseContext ctx) {
        subscribers.remove(ctx);
        LOG.info("[DASHBOARD] Cliente desconectado. Total: " + subscribers.size());
    }

    private void startBroadcast() {
        broadcastTask = scheduler.scheduleAtFixedRate(
            this::broadcastMetrics,
            1000,
            MIN_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    private void broadcastMetrics() {
        if (!running.get() || subscribers.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();

        for (var entry : subscribers.entrySet()) {
            WsContext ctx = entry.getKey();
            if (ctx.session.isOpen()) {
                sendMetrics(ctx);
            } else {
                subscribers.remove(ctx);
            }
        }
    }

    private void sendMetrics(WsContext ctx) {
        try {
            Map<String, Object> metrics = collectMetrics();
            sendJson(ctx, Map.of(
                "type", "metrics",
                "timestamp", Instant.now().toString(),
                "data", metrics
            ));
        } catch (Exception e) {
            LOG.fine("[DASHBOARD] Error enviando métricas: " + e.getMessage());
        }
    }

    private Map<String, Object> collectMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        double heapPercent = (double) heapUsed / heapMax * 100;

        return Map.of(
            "uptime", Map.of(
                "seconds", Duration.between(startTime, Instant.now()).getSeconds(),
                "started", startTime.toString()
            ),
            "bus", Map.of(
                "messages_published", SovereignMetrics.INSTANCE.getCount("bus.messages.published"),
                "messages_delivered", SovereignMetrics.INSTANCE.getCount("bus.messages.delivered"),
                "messages_success", SovereignMetrics.INSTANCE.getCount("bus.messages.success"),
                "messages_failed", SovereignMetrics.INSTANCE.getCount("bus.messages.failed"),
                "inflight", SovereignMetrics.INSTANCE.getGauge("bus.inflight")
            ),
            "jvm", Map.of(
                "heap_used_bytes", heapUsed,
                "heap_max_bytes", heapMax,
                "heap_percent", String.format("%.1f", heapPercent),
                "threads", threadBean.getThreadCount(),
                "peak_threads", threadBean.getPeakThreadCount()
            ),
            "dashboard", Map.of(
                "subscribers", subscribers.size()
            )
        );
    }

    private void sendJson(WsContext ctx, Object data) {
        try {
            ctx.send(mapper.writeValueAsString(data));
        } catch (Exception e) {
            LOG.fine("[DASHBOARD] Error serializando JSON: " + e.getMessage());
        }
    }

    public int getSubscriberCount() {
        return subscribers.size();
    }

    public Set<WsContext> getSubscribers() {
        return Set.copyOf(subscribers.keySet());
    }

    @Override
    public void close() {
        running.set(false);
        if (broadcastTask != null) {
            broadcastTask.cancel(false);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("[DASHBOARD] Dashboard cerrado");
    }
}
