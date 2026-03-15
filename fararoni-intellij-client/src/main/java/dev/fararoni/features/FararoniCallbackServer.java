/*
 * Copyright 2026 Eber Cruz Fararoni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ---------------------------------------------------------------------------
 *
 * Copyright 2026 Eber Cruz Fararoni
 *
 * Licenciado bajo la Licencia Apache, Version 2.0 (la "Licencia");
 * no puede usar este archivo excepto en cumplimiento con la Licencia.
 * Puede obtener una copia de la Licencia en
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * A menos que lo exija la ley aplicable o se acuerde por escrito, el software
 * distribuido bajo la Licencia se distribuye "TAL CUAL", SIN GARANTIAS NI
 * CONDICIONES DE NINGUN TIPO, ya sean expresas o implicitas.
 * Consulte la Licencia para conocer el lenguaje especifico que rige los
 * permisos y las limitaciones de la misma.
 */
package dev.fararoni.features;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@Service(Service.Level.PROJECT)
public final class FararoniCallbackServer implements Disposable {

    private static final Logger LOG = Logger.getLogger(FararoniCallbackServer.class.getName());

    /** Puerto por defecto del servidor */
    public static final int DEFAULT_PORT = 9999;

    /** Endpoint para recibir callbacks */
    public static final String CALLBACK_ENDPOINT = "/push";

    /** Timeout de shutdown en segundos */
    private static final int SHUTDOWN_DELAY_SECONDS = 2;

    private final Project project;
    private final Gson gson;
    private HttpServer server;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Métricas
    private final AtomicLong totalReceived = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);

    // Referencia al renderer de streaming (se inyecta desde ToolWindow)
    private volatile StreamingCallback streamingCallback;

    /**
     * Crea un nuevo CallbackServer para el proyecto dado.
     *
     * @param project el proyecto de IntelliJ
     */
    public FararoniCallbackServer(Project project) {
        this.project = project;
        this.gson = new Gson();
        LOG.info("[CallbackServer] Instance created for project: " + project.getName());
    }

    /**
     * Inicia el servidor HTTP en el puerto configurado.
     *
     * <p>Usa Virtual Threads (JEP 444) para manejo concurrente de
     * múltiples sugerencias entrantes sin bloquear el IDE.</p>
     *
     * @return true si el servidor inició correctamente
     */
    public boolean start() {
        return start(DEFAULT_PORT);
    }

    /**
     * Inicia el servidor HTTP en el puerto especificado.
     *
     * @param port puerto a usar
     * @return true si el servidor inició correctamente
     */
    public boolean start(int port) {
        if (running.get()) {
            LOG.warning("[CallbackServer] Already running on port " + getPort());
            return true;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // Executor con hilos de maxima prioridad
            // Evita que IntelliJ degrade el procesamiento de respuestas
            ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "Fararoni-Callback-Worker");
                t.setPriority(Thread.MAX_PRIORITY); // Prioridad tactica
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);

            // Endpoint principal
            server.createContext(CALLBACK_ENDPOINT, this::handlePush);

            // Health check
            server.createContext("/health", this::handleHealth);

            server.start();
            running.set(true);

            LOG.info("[CallbackServer] Started on port " + port);
            LOG.info("[CallbackServer] Endpoint: POST http://localhost:" + port + CALLBACK_ENDPOINT);

            return true;

        } catch (IOException e) {
            LOG.log(Level.SEVERE, "[CallbackServer] Failed to start on port " + port, e);
            return false;
        }
    }

    /**
     * Detiene el servidor de forma graceful.
     *
     * <p>Espera a que terminen los requests en vuelo antes de cerrar.</p>
     */
    public void stop() {
        if (!running.get() || server == null) {
            return;
        }

        LOG.info("[CallbackServer] Stopping (received: " + totalReceived.get() +
                 ", processed: " + totalProcessed.get() +
                 ", failed: " + totalFailed.get() + ")");

        server.stop(SHUTDOWN_DELAY_SECONDS);
        running.set(false);

        LOG.info("[CallbackServer] Stopped");
    }

    /**
     * Maneja POST /push - Recibe callbacks del Core Fararoni.
     *
     * <p>Parsea el JSON, determina el intent, y ejecuta la acción apropiada:</p>
     * <ul>
     *   <li>QUICK_FIX → Notificación con acción de aplicar</li>
     *   <li>SMART_SUGGESTION → Registrar en cache para ExternalAnnotator</li>
     *   <li>SURGICAL_FIX → Notificación + Cache para aplicar</li>
     *   <li>CHAT_RESPONSE → Enviar a SafeStreamingRenderer</li>
     * </ul>
     *
     * @param exchange el intercambio HTTP
     */
    private void handlePush(HttpExchange exchange) {
        totalReceived.incrementAndGet();

        // Solo aceptar POST
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }

        try {
            // Leer body
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String body = new String(bodyBytes, StandardCharsets.UTF_8);

            // --- LOG DE GRADO MILITAR: INSPECCION DE RESPUESTA ENTRANTE ---
            LOG.info("================ [FARARONI DEBUG: INBOUND FROM CORE] ================");
            LOG.info("FULL PAYLOAD: " + truncate(body, 500));

            // Parsear JSON
            JsonObject data = JsonParser.parseString(body).getAsJsonObject();

            // Verificar metadatos devueltos por el Core
            if (data.has("metadata")) {
                LOG.info("METADATA RECEIVED: " + data.get("metadata").toString());
            } else {
                LOG.warning("CRITICO: La respuesta del Core NO contiene metadatos. Sesion posiblemente desvinculada.");
            }

            String traceId = getStringOrDefault(data, "traceId", "unknown");
            String intent = getStringOrDefault(data, "intent", "CHAT_RESPONSE");
            String content = getStringOrDefault(data, "content", "");
            String filePath = data.has("filePath") ? data.get("filePath").getAsString() : null;
            boolean isFinal = data.has("isFinal") && data.get("isFinal").getAsBoolean();

            LOG.info("TRACE ID: " + traceId);
            LOG.info("INTENT: " + intent);
            LOG.info("FILE PATH: " + (filePath != null ? filePath : "N/A"));
            LOG.info("CONTENT PREVIEW: " + truncate(content, 200));
            LOG.info("IS FINAL: " + isFinal);
            LOG.info("=====================================================================");

            // Procesar segun intent
            processIntent(intent, content, filePath, isFinal, traceId);

            // Responder OK
            sendResponse(exchange, 200, "{\"status\":\"received\",\"traceId\":\"" + traceId + "\"}");
            totalProcessed.incrementAndGet();

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[CallbackServer] Error processing request", e);
            sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            totalFailed.incrementAndGet();
        }
    }

    /**
     * Procesa el mensaje según su intent.
     *
     * @param intent   tipo de mensaje
     * @param content  contenido del LLM
     * @param filePath archivo relacionado (puede ser null)
     * @param isFinal  true si es el último chunk de streaming
     * @param traceId  ID para tracking
     */
    private void processIntent(String intent, String content, String filePath,
                               boolean isFinal, String traceId) {
        LOG.info("[CallbackServer] Processing intent=" + intent + ", trace=" + traceId);

        switch (intent.toUpperCase()) {
            case "QUICK_FIX" -> handleQuickFix(content, filePath, traceId);
            case "SMART_SUGGESTION" -> handleSmartSuggestion(content, filePath, traceId);
            case "SURGICAL_FIX" -> handleSurgicalFix(content, filePath, traceId);
            case "CHAT_RESPONSE" -> handleChatResponse(content, isFinal, traceId);
            case "PROJECT_ANALYSIS" -> handleProjectAnalysis(content, traceId);
            default -> handleChatResponse(content, isFinal, traceId);
        }
    }

    /**
     * QUICK_FIX: Muestra notificación con sugerencia de corrección rápida.
     */
    private void handleQuickFix(String content, String filePath, String traceId) {
        // Registrar en cache para ExternalAnnotator
        if (filePath != null && !filePath.isEmpty()) {
            FararoniSuggestionCache cache = FararoniSuggestionCache.getInstance(project);
            if (cache != null) {
                cache.put(filePath, content, "QUICK_FIX", traceId);
                LOG.info("[CallbackServer] QuickFix cached for " + truncate(filePath, 50));
            }
        }

        SwingUtilities.invokeLater(() -> {
            showNotification(
                "Corrección Disponible",
                "Fararoni encontró una solución. Busca el foquito amarillo en el editor.",
                NotificationType.INFORMATION
            );
        });
    }

    /**
     * SMART_SUGGESTION: Sugerencia proactiva de mejora de código.
     */
    private void handleSmartSuggestion(String content, String filePath, String traceId) {
        // Registrar en cache para ExternalAnnotator
        if (filePath != null && !filePath.isEmpty()) {
            FararoniSuggestionCache cache = FararoniSuggestionCache.getInstance(project);
            if (cache != null) {
                cache.put(filePath, content, "SMART_SUGGESTION", traceId);
                LOG.info("[CallbackServer] SmartSuggestion cached for " + truncate(filePath, 50));
            }
        }

        SwingUtilities.invokeLater(() -> {
            showNotification(
                "Sugerencia de Mejora",
                "Fararoni sugiere: " + truncate(content, 100),
                NotificationType.INFORMATION
            );
        });
    }

    /**
     * SURGICAL_FIX: Parche listo para aplicar con Alt+Enter.
     */
    private void handleSurgicalFix(String content, String filePath, String traceId) {
        // Registrar en cache con código completo para aplicar
        if (filePath != null && !filePath.isEmpty()) {
            FararoniSuggestionCache cache = FararoniSuggestionCache.getInstance(project);
            if (cache != null) {
                cache.put(filePath, content, "SURGICAL_FIX", traceId);
                LOG.info("[CallbackServer] SurgicalFix cached for " + truncate(filePath, 50));
            }
        }

        SwingUtilities.invokeLater(() -> {
            showNotification(
                "Parche Listo",
                "Haz clic en el foquito amarillo para aplicar la mejora",
                NotificationType.INFORMATION
            );
        });
    }

    /**
     * CHAT_RESPONSE: Envía respuesta al ToolWindow con efecto streaming.
     */
    private void handleChatResponse(String content, boolean isFinal, String traceId) {
        if (streamingCallback != null) {
            // Enviar al renderer de streaming
            SwingUtilities.invokeLater(() -> {
                streamingCallback.onChunkReceived(content, isFinal);
            });
        } else {
            // Fallback: mostrar como notificación
            SwingUtilities.invokeLater(() -> {
                showNotification(
                    "Respuesta de Fararoni",
                    truncate(content, 200),
                    NotificationType.INFORMATION
                );
            });
        }
    }

    /**
     * PROJECT_ANALYSIS: Resultado de análisis de proyecto completo.
     */
    private void handleProjectAnalysis(String content, String traceId) {
        SwingUtilities.invokeLater(() -> {
            showNotification(
                "Análisis de Proyecto Completo",
                "Fararoni analizó tu proyecto. Ver detalles en el panel.",
                NotificationType.INFORMATION
            );

            // Enviar contenido completo al ToolWindow
            if (streamingCallback != null) {
                streamingCallback.onChunkReceived(content, true);
            }
        });
    }

    /**
     * Maneja GET /health - Health check del servidor.
     */
    private void handleHealth(HttpExchange exchange) {
        String json = String.format(
            "{\"status\":\"healthy\",\"port\":%d,\"received\":%d,\"processed\":%d,\"failed\":%d}",
            getPort(),
            totalReceived.get(),
            totalProcessed.get(),
            totalFailed.get()
        );
        sendResponse(exchange, 200, json);
    }

    /**
     * Envía una respuesta HTTP.
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[CallbackServer] Error sending response", e);
        }
    }

    /**
     * Muestra una notificación en IntelliJ.
     */
    private void showNotification(String title, String content, NotificationType type) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Fararoni Notifications")
                .createNotification(title, content, type)
                .notify(project);
        } catch (Exception e) {
            // Fallback si el NotificationGroup no está registrado
            LOG.info("[CallbackServer] " + title + ": " + content);
        }
    }

    /**
     * Extrae string de JsonObject con valor por defecto.
     */
    private String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }

    /**
     * Trunca string para logging.
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    /**
     * Retorna el puerto en uso.
     *
     * @return puerto o -1 si no está corriendo
     */
    public int getPort() {
        return server != null ? server.getAddress().getPort() : -1;
    }

    /**
     * Verifica si el servidor está corriendo.
     *
     * @return true si está activo
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Retorna la URL de callback para enviar al servidor.
     *
     * @return URL completa del endpoint /push
     */
    public String getCallbackUrl() {
        return "http://localhost:" + getPort() + CALLBACK_ENDPOINT;
    }

    /**
     * Registra un callback para streaming de respuestas al ToolWindow.
     *
     * @param callback el callback a invocar cuando lleguen chunks
     */
    public void setStreamingCallback(StreamingCallback callback) {
        this.streamingCallback = callback;
    }

    /**
     * Obtiene instancia del servicio para un proyecto.
     *
     * @param project el proyecto
     * @return instancia del CallbackServer
     */
    public static FararoniCallbackServer getInstance(Project project) {
        return project.getService(FararoniCallbackServer.class);
    }

    public long getTotalReceived() { return totalReceived.get(); }
    public long getTotalProcessed() { return totalProcessed.get(); }
    public long getTotalFailed() { return totalFailed.get(); }

    @Override
    public void dispose() {
        stop();
    }

    /**
     * Interface para recibir chunks de streaming.
     *
     * <p>Implementada por SafeStreamingRenderer para mostrar
     * respuestas letra por letra en el ToolWindow.</p>
     */
    @FunctionalInterface
    public interface StreamingCallback {
        /**
         * Llamado cuando llega un chunk de respuesta.
         *
         * @param content  contenido del chunk
         * @param isFinal  true si es el último chunk
         */
        void onChunkReceived(String content, boolean isFinal);
    }
}
