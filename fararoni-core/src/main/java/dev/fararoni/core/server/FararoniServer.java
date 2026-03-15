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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.swarm.HiveMind;
import dev.fararoni.core.FararoniCore;
import dev.fararoni.core.observability.MetricsDashboard;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class FararoniServer {
    private static final Logger LOG = Logger.getLogger(FararoniServer.class.getName());

    private final SessionManager sessionManager;
    private final FararoniCore fararoniCore;
    private final ObjectMapper mapper;
    private Javalin app;

    private final Map<String, WsContext> activeSockets = new ConcurrentHashMap<>();

    private final Map<String, Long> lastRequestTime = new ConcurrentHashMap<>();
    private static final long MIN_REQUEST_INTERVAL_MS = 1000;

    private static final long MAX_REQUEST_SIZE = 10 * 1024 * 1024;

    private static final String HEADER_USER_ID = "X-User-ID";
    private static final String HEADER_API_KEY = "X-API-Key";

    private boolean authRequired = false;

    private PluginWebSocketBridge pluginBridge;

    private MetricsEndpoint metricsEndpoint;

    private MetricsDashboard metricsDashboard;

    public FararoniServer(SessionManager sessionManager, FararoniCore fararoniCore) {
        this.sessionManager = sessionManager;
        this.fararoniCore = fararoniCore;
        this.mapper = new ObjectMapper();
    }

    public FararoniServer(SessionManager sessionManager) {
        this(sessionManager, null);
    }

    public void start(int port) {
        app = Javalin.create(config -> {
            config.http.maxRequestSize = MAX_REQUEST_SIZE;
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            config.useVirtualThreads = true;
        });

        registerRestEndpoints();
        registerWebSocket();
        registerPluginWebSocket();
        registerMetricsEndpoints();

        app.start(port);

        LOG.info(() -> String.format(
            "[SERVER] Fararoni Fortress escuchando en puerto %d", port));
    }

    public void stop() {
        if (metricsDashboard != null) {
            metricsDashboard.close();
        }

        if (app != null) {
            app.stop();
            LOG.info("[SERVER] Servidor detenido.");
        }
        sessionManager.shutdown();
    }

    public void setAuthRequired(boolean required) {
        this.authRequired = required;
    }

    public Javalin getJavalinApp() {
        return app;
    }

    private void registerRestEndpoints() {
        app.get("/health", ctx -> {
            ctx.json(Map.of("status", "healthy"));
        });

        app.get("/api/status", ctx -> {
            var metrics = sessionManager.getMetrics();
            ctx.json(Map.of(
                "status", "running",
                "sessions", Map.of(
                    "active", metrics.currentActive(),
                    "total_created", metrics.totalCreated(),
                    "total_expired", metrics.totalExpired(),
                    "max", metrics.maxAllowed()
                )
            ));
        });

        app.post("/api/task", ctx -> {
            String userId = ctx.header(HEADER_USER_ID);
            if (userId == null || userId.isBlank()) {
                ctx.status(401).json(Map.of(
                    "error", "Missing X-User-ID header",
                    "status", 401
                ));
                return;
            }

            if (authRequired) {
                String apiKey = ctx.header(HEADER_API_KEY);
                if (!isValidApiKey(apiKey)) {
                    ctx.status(401).json(Map.of(
                        "error", "Invalid or missing API key",
                        "status", 401
                    ));
                    return;
                }
            }

            if (isRateLimited(userId)) {
                ctx.status(429).json(Map.of(
                    "error", "Too Many Requests. Wait 1 second.",
                    "status", 429
                ));
                return;
            }

            String prompt = ctx.body();
            if (prompt == null || prompt.isBlank()) {
                ctx.status(400).json(Map.of(
                    "error", "Empty request body",
                    "status", 400
                ));
                return;
            }

            try {
                var silo = sessionManager.getOrCreateSession(userId);

                silo.bus().setOutputHook(msg -> sendToSocket(userId, msg));

                String missionId = "TASK-" + System.currentTimeMillis();

                Thread.ofVirtual().name("mission-" + missionId).start(() -> {
                    LOG.info(() -> String.format(
                        "[SERVER] Iniciando misión %s para usuario %s",
                        missionId, userId));
                    try {
                        var result = silo.hive().executeMission(prompt);
                        LOG.info(() -> String.format(
                            "[SERVER] Misión %s completada: %s",
                            missionId, result.status()));

                        sendToSocket(userId, Map.of(
                            "type", "MISSION_COMPLETE",
                            "missionId", missionId,
                            "status", result.status().toString(),
                            "result", result.result() != null ? result.result() : ""
                        ));
                    } catch (Exception e) {
                        LOG.severe(() -> String.format(
                            "[SERVER] Error en misión %s: %s",
                            missionId, e.getMessage()));
                        sendToSocket(userId, Map.of(
                            "type", "MISSION_ERROR",
                            "missionId", missionId,
                            "error", e.getMessage()
                        ));
                    }
                });

                ctx.status(202).json(Map.of(
                    "status", "started",
                    "missionId", missionId,
                    "userId", userId,
                    "message", "Connect to WebSocket /ws/events?userId=" + userId + " for live updates"
                ));
            } catch (IllegalStateException e) {
                ctx.status(503).json(Map.of(
                    "error", e.getMessage(),
                    "status", 503
                ));
            }
        });

        app.post("/api/chat", ctx -> {
            String body = ctx.body();
            if (body == null || body.isBlank()) {
                ctx.status(400).json(Map.of(
                    "error", "Empty request body",
                    "status", 400
                ));
                return;
            }

            try {
                JsonNode json = mapper.readTree(body);

                String prompt;
                if (json.has("context") && json.get("context").has("prompt")) {
                    String userPrompt = json.get("context").get("prompt").asText("");
                    String codeContent = json.get("context").has("content")
                        ? json.get("context").get("content").asText("")
                        : "";

                    if (!codeContent.isBlank()) {
                        prompt = userPrompt + "\n\nCódigo de contexto:\n```\n" + codeContent + "\n```";
                    } else {
                        prompt = userPrompt;
                    }
                } else if (json.has("prompt")) {
                    prompt = json.get("prompt").asText();
                } else if (json.has("message")) {
                    prompt = json.get("message").asText();
                } else {
                    prompt = body;
                }

                if (prompt == null || prompt.isBlank()) {
                    ctx.status(400).json(Map.of(
                        "error", "No prompt found in request",
                        "status", 400
                    ));
                    return;
                }

                if (fararoniCore == null) {
                    ctx.status(503).json(Map.of(
                        "error", "FararoniCore not available. Use /api/task instead.",
                        "status", 503
                    ));
                    return;
                }

                LOG.info(() -> "[CHAT] Procesando: " + prompt.substring(0, Math.min(50, prompt.length())) + "...");

                String response;
                if (fararoniCore.isAgenticModeAvailable()) {
                    response = fararoniCore.chatAgentic(prompt);
                } else {
                    response = fararoniCore.chat(prompt);
                }

                ctx.status(200).json(Map.of(
                    "status", "success",
                    "response", response != null ? response : ""
                ));
            } catch (Exception e) {
                LOG.severe(() -> "[CHAT] Error: " + e.getMessage());
                ctx.status(500).json(Map.of(
                    "error", "Internal error: " + e.getMessage(),
                    "status", 500
                ));
            }
        });

        app.get("/api/session/{userId}", ctx -> {
            String userId = ctx.pathParam("userId");
            var silo = sessionManager.getSession(userId);

            if (silo == null) {
                ctx.status(404).json(Map.of(
                    "error", "Session not found",
                    "status", 404
                ));
                return;
            }

            var metrics = silo.hive().getMetrics();
            ctx.json(Map.of(
                "userId", userId,
                "created", silo.createdAt().toString(),
                "lastActivity", silo.lastActivity().toString(),
                "missions", Map.of(
                    "total", metrics.totalMissions(),
                    "success", metrics.successfulMissions(),
                    "failed", metrics.failedMissions(),
                    "timedOut", metrics.timedOutMissions()
                )
            ));
        });

        app.delete("/api/session/{userId}", ctx -> {
            String userId = ctx.pathParam("userId");
            boolean removed = sessionManager.removeSession(userId);

            WsContext wsCtx = activeSockets.remove(userId);
            if (wsCtx != null && wsCtx.session.isOpen()) {
                wsCtx.closeSession();
            }

            if (removed) {
                ctx.json(Map.of(
                    "status", "deleted",
                    "userId", userId
                ));
            } else {
                ctx.status(404).json(Map.of(
                    "error", "Session not found",
                    "status", 404
                ));
            }
        });
    }

    private void registerWebSocket() {
        app.ws("/ws/events", ws -> {
            ws.onConnect(ctx -> {
                String userId = ctx.queryParam("userId");
                if (userId != null && !userId.isBlank()) {
                    activeSockets.put(userId, ctx);
                    LOG.info(() -> "[WS] Cliente conectado: " + userId);

                    ctx.send(Map.of(
                        "type", "CONNECTED",
                        "userId", userId,
                        "message", "Live Feed activo. Recibirás eventos del Swarm."
                    ));
                } else {
                    ctx.closeSession(4001, "Missing userId parameter");
                }
            });

            ws.onClose(ctx -> {
                String userId = ctx.queryParam("userId");
                if (userId != null) {
                    activeSockets.remove(userId);
                    LOG.info(() -> "[WS] Cliente desconectado: " + userId);
                }
            });

            ws.onMessage(ctx -> {
                String message = ctx.message();
                LOG.fine(() -> "[WS] Mensaje recibido: " + message);
            });

            ws.onError(ctx -> {
                String userId = ctx.queryParam("userId");
                LOG.warning(() -> "[WS] Error en conexión " + userId + ": " + ctx.error());
            });
        });
    }

    private void registerPluginWebSocket() {
        SovereignEventBus bus = sessionManager.getDefaultBus();
        if (bus == null) {
            LOG.warning("[SERVER] No se puede iniciar /api/bus - Bus no disponible");
            return;
        }

        String authToken = System.getenv("FARARONI_PLUGIN_TOKEN");
        pluginBridge = new PluginWebSocketBridge(bus, authToken);

        app.ws("/api/bus", ws -> {
            ws.onConnect(pluginBridge::onConnect);
            ws.onClose(pluginBridge::onClose);
            ws.onMessage(pluginBridge::onMessage);
            ws.onError(ctx -> {
                String pluginId = ctx.queryParam("pluginId");
                LOG.warning(() -> "[PLUGIN-WS] Error en conexión " + pluginId + ": " + ctx.error());
            });
        });

        LOG.info("[SERVER] Endpoint /api/bus registrado para plugins");
    }

    public PluginWebSocketBridge getPluginBridge() {
        return pluginBridge;
    }

    private void registerMetricsEndpoints() {
        metricsEndpoint = new MetricsEndpoint();

        if (pluginBridge != null) {
            metricsEndpoint.setPluginBridge(pluginBridge);
        }

        metricsEndpoint.register(app);

        metricsDashboard = new MetricsDashboard();
        metricsDashboard.register(app);

        LOG.info("[SERVER] Endpoints de métricas registrados: /api/metrics, /api/health, /api/stats, /api/metrics/live");
    }

    public MetricsEndpoint getMetricsEndpoint() {
        return metricsEndpoint;
    }

    public MetricsDashboard getMetricsDashboard() {
        return metricsDashboard;
    }

    private void sendToSocket(String userId, Object message) {
        WsContext ctx = activeSockets.get(userId);
        if (ctx != null && ctx.session.isOpen()) {
            try {
                ctx.send(message);
            } catch (Exception e) {
                LOG.warning(() -> "[WS] Error enviando a " + userId + ": " + e.getMessage());
            }
        }
    }

    private boolean isValidApiKey(String apiKey) {
        return apiKey != null && apiKey.length() >= 10;
    }

    private boolean isRateLimited(String userId) {
        long now = System.currentTimeMillis();
        Long lastRequest = lastRequestTime.get(userId);

        if (lastRequest != null && (now - lastRequest) < MIN_REQUEST_INTERVAL_MS) {
            return true;
        }

        lastRequestTime.put(userId, now);
        return false;
    }

    public Set<String> getConnectedUsers() {
        return Set.copyOf(activeSockets.keySet());
    }

    public boolean isRunning() {
        return app != null;
    }
}
