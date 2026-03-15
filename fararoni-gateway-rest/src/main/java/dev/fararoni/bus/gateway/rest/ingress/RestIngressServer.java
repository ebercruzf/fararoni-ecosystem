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
package dev.fararoni.bus.gateway.rest.ingress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.gateway.registry.ChannelRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.fararoni.bus.gateway.rest.dto.UniversalMessage;
import dev.fararoni.bus.gateway.rest.security.RateLimiter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class RestIngressServer {

    private static final Logger LOG = Logger.getLogger(RestIngressServer.class.getName());

    /** Default port for the ingress server */
    public static final int DEFAULT_PORT = 7071;

    /** Maximum payload size (20 MB) */
    public static final int MAX_PAYLOAD_BYTES = 20 * 1024 * 1024;

    /** Topic prefix for publishing to bus */
    private static final String TOPIC_PREFIX = "agency.input.";

    /** Topic for audio messages (processed by Voice extension) */
    private static final String AUDIO_TOPIC = "agency.input.voice.raw";

    private final SovereignEventBus bus;
    private final int port;
    private final String expectedToken;
    private final String metaVerifyToken;  // Token para verificación de Meta Webhook
    private final String adminToken;       // Token para endpoints administrativos
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private HttpServer server;
    private volatile boolean running = false;

    /**
     * Creates a new RestIngressServer.
     *
     * @param bus           the event bus to publish messages to
     * @param port          port to listen on (default: 7071)
     * @param expectedToken API token for authentication (nullable for dev)
     * @param rateLimiter   rate limiter instance
     */
    public RestIngressServer(
            SovereignEventBus bus,
            int port,
            String expectedToken,
            RateLimiter rateLimiter) {
        this(bus, port, expectedToken, null, null, rateLimiter);
    }

    /**
     * Creates a new RestIngressServer with Meta Enterprise support.
     *
     * <p>Constructor con soporte para WhatsApp Business API (Meta Enterprise).</p>
     *
     * @param bus             the event bus to publish messages to
     * @param port            port to listen on (default: 7071)
     * @param expectedToken   API token for Sidecar authentication (nullable for dev)
     * @param metaVerifyToken Token para verificación de Meta Webhook (nullable si no usa Enterprise)
     * @param rateLimiter     rate limiter instance
     */
    public RestIngressServer(
            SovereignEventBus bus,
            int port,
            String expectedToken,
            String metaVerifyToken,
            RateLimiter rateLimiter) {
        this(bus, port, expectedToken, metaVerifyToken, null, rateLimiter);
    }

    /**
     * Creates a new RestIngressServer with full configuration.
     *
     * <p>Constructor completo con soporte para admin endpoints.</p>
     *
     * @param bus             the event bus to publish messages to
     * @param port            port to listen on (default: 7071)
     * @param expectedToken   API token for Sidecar authentication (nullable for dev)
     * @param metaVerifyToken Token para verificación de Meta Webhook (nullable)
     * @param adminToken      Token para endpoints administrativos (nullable para dev)
     * @param rateLimiter     rate limiter instance
     */
    public RestIngressServer(
            SovereignEventBus bus,
            int port,
            String expectedToken,
            String metaVerifyToken,
            String adminToken,
            RateLimiter rateLimiter) {
        this.bus = bus;
        this.port = port > 0 ? port : DEFAULT_PORT;
        this.expectedToken = expectedToken;
        this.metaVerifyToken = metaVerifyToken;
        this.adminToken = adminToken;
        this.rateLimiter = rateLimiter != null ? rateLimiter : new RateLimiter();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Starts the HTTP server.
     *
     * @throws RuntimeException if server fails to start
     */
    public void start() {
        if (running) {
            LOG.warning("[GATEWAY-INGRESS] Server already running");
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // Main ingress endpoint (for Sidecars like Node.js/Baileys)
            server.createContext("/gateway/v1/inbound", this::handleInbound);

            // Health check endpoint
            server.createContext("/gateway/v1/health", this::handleHealth);

            // Meta Enterprise Webhook (WhatsApp Business API)
            server.createContext("/gateway/v1/meta/webhook", this::handleMetaWebhook);

            // Admin endpoint para hot-reload de canales
            server.createContext("/gateway/v1/admin/channels/reload", this::handleAdminChannelsReload);
            server.createContext("/gateway/v1/admin/channels/status", this::handleAdminChannelsStatus);

            // Use Virtual Threads (JEP 444) for high concurrency
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();

            running = true;
            LOG.info("[GATEWAY-INGRESS] Server started on port " + port);
            LOG.info("[GATEWAY-INGRESS] Endpoint: POST /gateway/v1/inbound");
            LOG.info("[GATEWAY-INGRESS] Meta:     GET/POST /gateway/v1/meta/webhook");
            LOG.info("[GATEWAY-INGRESS] Admin:    POST /gateway/v1/admin/channels/reload");
            LOG.info("[GATEWAY-INGRESS] Admin:    GET  /gateway/v1/admin/channels/status");
            LOG.info("[GATEWAY-INGRESS] Health:   GET  /gateway/v1/health");

        } catch (IOException e) {
            throw new RuntimeException("Failed to start ingress server on port " + port, e);
        }
    }

    /**
     * Stops the HTTP server gracefully.
     *
     * @param delaySeconds seconds to wait for in-flight requests
     */
    public void stop(int delaySeconds) {
        if (!running || server == null) {
            return;
        }

        LOG.info("[GATEWAY-INGRESS] Stopping server (grace period: " + delaySeconds + "s)");
        server.stop(delaySeconds);
        running = false;
        LOG.info("[GATEWAY-INGRESS] Server stopped");
    }

    /**
     * Stops the server immediately.
     */
    public void stop() {
        stop(2);
    }

    /**
     * Returns whether the server is running.
     *
     * @return true if server is accepting requests
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the port the server is listening on.
     *
     * @return port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Handles POST /gateway/v1/inbound
     */
    private void handleInbound(HttpExchange exchange) {
        String clientIp = getClientIp(exchange);
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        try {
            // 1. Method check
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            // 2. Rate limiting
            if (!rateLimiter.tryAcquire(clientIp)) {
                LOG.warning("[GATEWAY-INGRESS] [" + requestId + "] Rate limited: " + clientIp);
                sendError(exchange, 429, "Too Many Requests");
                return;
            }

            // 3. Authentication (skip if no token configured - dev mode)
            if (expectedToken != null && !expectedToken.isBlank()) {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.equals("Bearer " + expectedToken)) {
                    LOG.warning("[GATEWAY-INGRESS] [" + requestId + "] Unauthorized: " + clientIp);
                    sendError(exchange, 401, "Unauthorized");
                    return;
                }
            }

            // 4. Read body
            byte[] body = exchange.getRequestBody().readAllBytes();
            if (body.length > MAX_PAYLOAD_BYTES) {
                LOG.warning("[GATEWAY-INGRESS] [" + requestId + "] Payload too large: " + body.length);
                sendError(exchange, 413, "Payload Too Large");
                return;
            }

            // 5. Parse UniversalMessage
            UniversalMessage message;
            try {
                message = objectMapper.readValue(body, UniversalMessage.class);
            } catch (Exception e) {
                LOG.warning("[GATEWAY-INGRESS] [" + requestId + "] Invalid JSON: " + e.getMessage());
                sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
                return;
            }

            // 6. Validate required fields
            if (message.channelId() == null || message.channelId().isBlank()) {
                sendError(exchange, 400, "Missing required field: channelId");
                return;
            }
            if (message.senderId() == null || message.senderId().isBlank()) {
                sendError(exchange, 400, "Missing required field: senderId");
                return;
            }

            // 7. Route to appropriate topic
            String topic;
            String payload;

            if (message.isAudio() && message.hasMedia()) {
                // Audio messages go to voice processing
                topic = AUDIO_TOPIC;
                payload = message.mediaContent();
            } else {
                // Text and other messages go to channel-specific topic
                topic = TOPIC_PREFIX + message.channelId().toLowerCase();
                payload = message.textContent() != null ? message.textContent() : "";
            }

            // 8. Create SovereignEnvelope with full context
            SovereignEnvelope<String> envelope = SovereignEnvelope
                .create(message.senderId(), requestId, payload)
                .withHeader("X-Origin-Channel", message.channelId())
                .withHeader("X-Origin-Protocol", message.channelId().toUpperCase())
                .withHeader("X-Reply-Channel-Id", message.senderId())
                .withHeader("X-Sender-Id", message.senderId())
                .withHeader("X-Message-Id", message.messageId() != null ? message.messageId() : requestId)
                .withHeader("X-Message-Type", message.type() != null ? message.type().name() : "TEXT")
                .withHeader("X-Client-Ip", clientIp);

            // Add conversation ID if present
            if (message.conversationId() != null) {
                envelope = envelope.withHeader("X-Conversation-Id", message.conversationId());
            }

            // Add MIME type for media
            if (message.mimeType() != null) {
                envelope = envelope.withHeader("X-Mime-Type", message.mimeType());
            }

            // [FASE 7.8.5] Add callback URL for IntelliJ plugin and similar clients
            if (message.getMetadata("callback_url") != null) {
                envelope = envelope.withHeader("X-Callback-Url", message.getMetadata("callback_url"));
            }

            // [FASE 7.8.5] Add intent header for response routing
            if (message.getMetadata("intent") != null) {
                envelope = envelope.withHeader("X-Intent", message.getMetadata("intent"));
            }

            // 9. Publish to bus
            bus.publish(topic, envelope);

            LOG.info("[GATEWAY-INGRESS] [" + requestId + "] Published to " + topic +
                     " from " + message.channelId() + "/" + truncate(message.senderId(), 20));

            // 10. Return 202 Accepted
            sendResponse(exchange, 202, "{\"status\":\"accepted\",\"requestId\":\"" + requestId + "\"}");

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[GATEWAY-INGRESS] [" + requestId + "] Error processing request", e);
            try {
                sendError(exchange, 500, "Internal Server Error");
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Handles GET /gateway/v1/health
     */
    private void handleHealth(HttpExchange exchange) {
        try {
            String status = running ? "healthy" : "unhealthy";
            String json = String.format(
                "{\"status\":\"%s\",\"port\":%d,\"activeClients\":%d,\"throttleRate\":%.4f}",
                status,
                port,
                rateLimiter.getActiveClientCount(),
                rateLimiter.getThrottleRate()
            );
            sendResponse(exchange, 200, json);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[GATEWAY-INGRESS] Error sending health response", e);
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        String json = "{\"error\":\"" + message + "\"}";
        sendResponse(exchange, code, json);
    }

    private String getClientIp(HttpExchange exchange) {
        // Check X-Forwarded-For first (for proxies)
        String xff = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    /**
     * Handles requests to /gateway/v1/meta/webhook
     *
     * <p>Este endpoint recibe tráfico directo de los servidores de Meta:</p>
     * <ul>
     *   <li>GET: Verificación inicial del webhook (hub.challenge)</li>
     *   <li>POST: Mensajes entrantes de usuarios de WhatsApp</li>
     * </ul>
     *
     * <p>Con este endpoint, el sistema puede recibir mensajes de WhatsApp Business API
     * sin necesidad del Sidecar de Node.js (Baileys).</p>
     */
    private void handleMetaWebhook(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        try {
            if ("GET".equalsIgnoreCase(method)) {
                handleMetaVerification(exchange, requestId);
            } else if ("POST".equalsIgnoreCase(method)) {
                handleMetaMessage(exchange, requestId);
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[META-WEBHOOK] [" + requestId + "] Error: " + e.getMessage(), e);
            try {
                sendError(exchange, 500, "Internal Server Error");
            } catch (IOException ignored) {}
        }
    }

    /**
     * Handles GET /gateway/v1/meta/webhook (Verification)
     *
     * <p>Meta envía una petición GET con estos parámetros:</p>
     * <ul>
     *   <li>hub.mode=subscribe</li>
     *   <li>hub.challenge=1158201444 (número aleatorio)</li>
     *   <li>hub.verify_token=TU_TOKEN_SECRETO</li>
     * </ul>
     *
     * <p>Debemos responder SOLO con el valor de hub.challenge si el token es válido.</p>
     */
    private void handleMetaVerification(HttpExchange exchange, String requestId) throws IOException {
        String query = exchange.getRequestURI().getQuery();

        if (query == null || query.isBlank()) {
            LOG.warning("[META-WEBHOOK] [" + requestId + "] Verification request without query params");
            sendError(exchange, 400, "Missing query parameters");
            return;
        }

        // Verificar que tenemos token configurado
        if (metaVerifyToken == null || metaVerifyToken.isBlank()) {
            LOG.warning("[META-WEBHOOK] [" + requestId + "] Meta verify token not configured");
            sendError(exchange, 500, "Meta webhook not configured");
            return;
        }

        // Verificar el token
        String receivedToken = extractQueryParam(query, "hub.verify_token");
        if (!metaVerifyToken.equals(receivedToken)) {
            LOG.warning("[META-WEBHOOK] [" + requestId + "] Invalid verify token: " + receivedToken);
            sendError(exchange, 403, "Invalid verify token");
            return;
        }

        // Extraer y responder con el challenge
        String challenge = extractQueryParam(query, "hub.challenge");
        if (challenge == null || challenge.isBlank()) {
            LOG.warning("[META-WEBHOOK] [" + requestId + "] Missing hub.challenge");
            sendError(exchange, 400, "Missing hub.challenge");
            return;
        }

        // Meta espera SOLO el challenge como respuesta (sin JSON)
        byte[] responseBytes = challenge.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }

        LOG.info("[META-WEBHOOK] [" + requestId + "] Verification successful");
    }

    /**
     * Handles POST /gateway/v1/meta/webhook (Incoming Messages)
     *
     * <p>Meta envía un JSON profundamente anidado:</p>
     * <pre>
     * {
     *   "entry": [{
     *     "changes": [{
     *       "value": {
     *         "messages": [{
     *           "from": "5212291234567",
     *           "id": "wamid.xxx",
     *           "type": "text",
     *           "text": {"body": "Hola"}
     *         }]
     *       }
     *     }]
     *   }]
     * }
     * </pre>
     *
     * <p>Traducimos esto a SovereignEnvelope y publicamos al bus.</p>
     */
    private void handleMetaMessage(HttpExchange exchange, String requestId) throws IOException {
        String clientIp = getClientIp(exchange);

        // Rate limiting
        if (!rateLimiter.tryAcquire(clientIp)) {
            LOG.warning("[META-WEBHOOK] [" + requestId + "] Rate limited: " + clientIp);
            sendError(exchange, 429, "Too Many Requests");
            return;
        }

        try {
            // Leer body
            byte[] body = exchange.getRequestBody().readAllBytes();
            if (body.length > MAX_PAYLOAD_BYTES) {
                LOG.warning("[META-WEBHOOK] [" + requestId + "] Payload too large: " + body.length);
                sendError(exchange, 413, "Payload Too Large");
                return;
            }

            // Parsear JSON de Meta
            JsonNode root = objectMapper.readTree(body);

            // Navegar el laberinto de Meta: entry[0].changes[0].value.messages[0]
            JsonNode messageNode = root.at("/entry/0/changes/0/value/messages/0");

            // Si no hay mensaje (ej: confirmaciones de lectura), responder OK y salir
            if (messageNode.isMissingNode()) {
                LOG.fine("[META-WEBHOOK] [" + requestId + "] No message in payload (status update?)");
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }

            // Extraer datos del mensaje
            String senderId = messageNode.path("from").asText();
            String messageId = messageNode.path("id").asText();
            String messageType = messageNode.path("type").asText();

            // Extraer contenido según tipo
            String textContent = null;
            if ("text".equals(messageType)) {
                textContent = messageNode.at("/text/body").asText(null);
            } else if ("button".equals(messageType)) {
                textContent = messageNode.at("/button/text").asText(null);
            } else if ("interactive".equals(messageType)) {
                // Botones interactivos o listas
                textContent = messageNode.at("/interactive/button_reply/title").asText(null);
                if (textContent == null) {
                    textContent = messageNode.at("/interactive/list_reply/title").asText(null);
                }
            }

            // Si no hay texto, log y salir
            if (textContent == null || textContent.isBlank()) {
                LOG.info("[META-WEBHOOK] [" + requestId + "] Non-text message type: " + messageType);
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }

            // Crear SovereignEnvelope con headers para identificar origen Meta
            SovereignEnvelope<String> envelope = SovereignEnvelope
                .create(senderId, requestId, textContent)
                .withHeader("X-Origin-Channel", "whatsapp")
                .withHeader("X-Origin-Protocol", "WHATSAPP_META")  // Diferenciador clave
                .withHeader("X-Reply-Channel-Id", senderId)
                .withHeader("X-Sender-Id", senderId)
                .withHeader("X-Message-Id", messageId)
                .withHeader("X-Message-Type", messageType.toUpperCase())
                .withHeader("X-Security-Level", "SECURE_ENTERPRISE")
                .withHeader("X-Client-Ip", clientIp);

            // Publicar al bus (el OmniChannelRouter lo procesará igual que Baileys)
            bus.publish(TOPIC_PREFIX + "whatsapp", envelope);

            LOG.info("[META-WEBHOOK] [" + requestId + "] Published from Meta: " +
                     truncate(senderId, 15) + " -> " + truncate(textContent, 30));

            // Meta exige respuesta HTTP 200 rápida (Fire and Forget)
            exchange.sendResponseHeaders(200, -1);
            exchange.close();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[META-WEBHOOK] [" + requestId + "] Error parsing Meta payload", e);
            sendError(exchange, 500, "Error processing webhook");
        }
    }

    /**
     * Extrae un parámetro de la query string.
     *
     * @param query query string completa (ej: "hub.mode=subscribe&hub.challenge=123")
     * @param param nombre del parámetro a extraer
     * @return valor del parámetro o null si no existe
     */
    private String extractQueryParam(String query, String param) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            if (pair.startsWith(param + "=")) {
                return pair.substring(param.length() + 1);
            }
        }
        return null;
    }

    /**
     * Handles POST /gateway/v1/admin/channels/reload
     *
     * <p>Endpoint para recargar la configuracion de canales en caliente
     * sin reiniciar el servidor. Implementa arquitectura "Control Plane".</p>
     *
     * <h2>Seguridad</h2>
     * <ul>
     *   <li>Requiere header X-Fararoni-Admin-Token con token valido</li>
     *   <li>Solo acepta metodo POST (mutacion de estado)</li>
     *   <li>En modo desarrollo (adminToken=null), permite acceso libre</li>
     * </ul>
     *
     * <h2>Uso con cURL</h2>
     * <pre>
     * curl -X POST http://localhost:7071/gateway/v1/admin/channels/reload \
     *      -H "X-Fararoni-Admin-Token: TU_TOKEN"
     * </pre>
     */
    private void handleAdminChannelsReload(HttpExchange exchange) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        try {
            // 1. Validar metodo (solo POST para mutaciones)
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                LOG.warning("[ADMIN] [" + requestId + "] Method not allowed: " + exchange.getRequestMethod());
                sendError(exchange, 405, "Method Not Allowed. Use POST.");
                return;
            }

            // 2. Validar token de admin (si esta configurado)
            if (!validateAdminToken(exchange, requestId)) {
                return; // Error ya enviado en validateAdminToken
            }

            // 3. Ejecutar recarga atomica
            LOG.info("[ADMIN] [" + requestId + "] Iniciando recarga de canales...");
            ChannelRegistry registry = ChannelRegistry.getInstance();
            boolean success = registry.reloadState();

            // 4. Responder con resultado
            if (success) {
                String json = String.format(
                    "{\"status\":\"success\",\"message\":\"Canales recargados correctamente\"," +
                    "\"activeChannels\":%d,\"timestamp\":%d}",
                    registry.getActiveChannelCount(),
                    registry.getLastReloadTimestamp()
                );
                LOG.info("[ADMIN] [" + requestId + "] Recarga exitosa: " + registry.getActiveChannelCount() + " canales");
                sendResponse(exchange, 200, json);
            } else {
                String json = "{\"status\":\"warning\",\"message\":\"Recarga completada con advertencias. Revisa logs.\"}";
                LOG.warning("[ADMIN] [" + requestId + "] Recarga completada con advertencias");
                sendResponse(exchange, 200, json);
            }

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[ADMIN] [" + requestId + "] Error en recarga: " + e.getMessage(), e);
            try {
                sendError(exchange, 500, "Internal Server Error during reload");
            } catch (IOException ignored) {}
        }
    }

    /**
     * Handles GET /gateway/v1/admin/channels/status
     *
     * <p>Endpoint para consultar el estado actual del ChannelRegistry.</p>
     *
     * <h2>Uso con cURL</h2>
     * <pre>
     * curl http://localhost:7071/gateway/v1/admin/channels/status \
     *      -H "X-Fararoni-Admin-Token: TU_TOKEN"
     * </pre>
     */
    private void handleAdminChannelsStatus(HttpExchange exchange) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        try {
            // 1. Validar metodo
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed. Use GET.");
                return;
            }

            // 2. Validar token de admin (si esta configurado)
            if (!validateAdminToken(exchange, requestId)) {
                return;
            }

            // 3. Obtener status del registry
            ChannelRegistry registry = ChannelRegistry.getInstance();
            String json = registry.getStatusJson();

            LOG.fine("[ADMIN] [" + requestId + "] Status consultado");
            sendResponse(exchange, 200, json);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[ADMIN] [" + requestId + "] Error obteniendo status: " + e.getMessage(), e);
            try {
                sendError(exchange, 500, "Internal Server Error");
            } catch (IOException ignored) {}
        }
    }

    /**
     * Valida el token de administrador.
     *
     * <p>Si adminToken es null (modo desarrollo), permite acceso libre.
     * Si adminToken esta configurado, requiere header X-Fararoni-Admin-Token.</p>
     *
     * @param exchange el intercambio HTTP
     * @param requestId ID de la peticion para logging
     * @return true si la autorizacion es valida, false si no (y envia error)
     */
    private boolean validateAdminToken(HttpExchange exchange, String requestId) throws IOException {
        // Modo desarrollo: sin token requerido
        if (adminToken == null || adminToken.isBlank()) {
            LOG.fine("[ADMIN] [" + requestId + "] Modo desarrollo: acceso admin sin token");
            return true;
        }

        // Produccion: validar token
        String receivedToken = exchange.getRequestHeaders().getFirst("X-Fararoni-Admin-Token");

        if (receivedToken == null || !adminToken.equals(receivedToken)) {
            LOG.warning("[ADMIN] [" + requestId + "] Intento de acceso no autorizado bloqueado");
            sendError(exchange, 401, "Unauthorized. Invalid or missing Admin Token.");
            return false;
        }

        return true;
    }
}
