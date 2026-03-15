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
package dev.fararoni.bus.agent.api.io;

import java.util.function.Consumer;

/**
 * Puerto de entrada para eventos externos en la arquitectura de orquestacion.
 *
 * <p>Esta interface implementa el patron "Mano Izquierda" de la Orquestacion
 * Bidireccional: escuchar eventos de sistemas externos (email, Jira, Slack)
 * y despertar al agente para procesarlos.</p>
 *
 * <h2>Motivacion</h2>
 * <p>Para que Fararoni funcione como un Bus de Integracion Empresarial,
 * necesita recibir eventos de multiples fuentes:</p>
 * <ul>
 *   <li><b>Email:</b> IMAP IDLE para correos nuevos</li>
 *   <li><b>Jira:</b> Webhooks para tickets creados/actualizados</li>
 *   <li><b>Slack:</b> Events API para menciones y mensajes</li>
 *   <li><b>Webhooks genericos:</b> POST HTTP de cualquier sistema</li>
 * </ul>
 *
 * <h2>Arquitectura</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    ORQUESTACION BIDIRECCIONAL                   │
 * ├─────────────────────────────────────────────────────────────────┤
 * │                                                                  │
 * │  MANO IZQUIERDA (Escuchar)        MANO DERECHA (Actuar)         │
 * │  ─────────────────────────        ─────────────────────         │
 * │                                                                  │
 * │  IngestionChannel                 LlmClient / Tools              │
 * │  - ImapIngestionChannel           - SlackClient.sendMessage()    │
 * │  - JiraWebhookChannel             - JiraClient.createTicket()    │
 * │  - SlackIngestionChannel          - EmailClient.send()           │
 * │                                                                  │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Flujo de Eventos</h2>
 * <pre>
 * Fuente Externa (Email, Jira, Slack)
 *     │
 *     ▼
 * IngestionChannel.onMessage(handler)
 *     │
 *     ▼
 * IncomingMessage (DTO agnostico)
 *     │
 *     ▼
 * EventBus.publish(IncomingMessageEvent)
 *     │
 *     ▼
 * Agente procesa con LLM y ejecuta acciones
 * </pre>
 *
 * <h2>Ejemplo de Uso</h2>
 * <pre>{@code
 * // Configurar canal
 * IngestionChannel emailChannel = new ImapIngestionChannel(config);
 *
 * // Registrar handler
 * emailChannel.onMessage(message -> {
 *     eventBus.publish(new IncomingMessageEvent(message));
 * });
 *
 * // Iniciar escucha
 * emailChannel.start();
 *
 * // En shutdown
 * emailChannel.stop();
 * emailChannel.close();
 * }</pre>
 *
 * <h2>Implementaciones Esperadas</h2>
 * <ul>
 *   <li>{@code ImapIngestionChannel} - JavaMail IMAP IDLE</li>
 *   <li>{@code JiraWebhookChannel} - Usa TinyWebServer</li>
 *   <li>{@code SlackIngestionChannel} - Slack Events API</li>
 *   <li>{@code GenericWebhookChannel} - HTTP POST generico</li>
 * </ul>
 *
 * <h2>Lifecycle Management</h2>
 * <p>Los canales se integran con {@code GracefulShutdownService} para
 * cierre ordenado. Implementan {@code AutoCloseable} para uso con
 * try-with-resources.</p>
 *
 * <h2>Ventaja vs Zapier/n8n</h2>
 * <ul>
 *   <li><b>Privacidad:</b> Datos nunca salen de tu infraestructura</li>
 *   <li><b>Latencia:</b> Milisegundos vs segundos</li>
 *   <li><b>Costo:</b> Gratis (tu hardware) vs $20-500/mes</li>
 *   <li><b>Inteligencia:</b> LLM decide dinamicamente vs reglas fijas</li>
 * </ul>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see IncomingMessage
 */
public interface IngestionChannel extends AutoCloseable {

    /**
     * Retorna el nombre descriptivo del canal.
     *
     * <p>Usado para logging, metricas y configuracion.</p>
     *
     * @return nombre unico del canal (ej: "imap-soporte", "jira-webhook", "slack-alerts")
     */
    String getName();

    /**
     * Retorna el tipo de canal.
     *
     * <p>Permite agrupar canales por tipo para estadisticas.</p>
     *
     * @return tipo del canal (ej: "email", "webhook", "websocket")
     */
    String getType();

    /**
     * Inicia el canal y comienza a escuchar eventos.
     *
     * <p>Este metodo es asincrono: retorna inmediatamente y la escucha
     * ocurre en un thread separado (preferiblemente Virtual Thread).</p>
     *
     * <h3>Comportamiento esperado:</h3>
     * <ul>
     *   <li>Abre conexiones necesarias (socket, HTTP, etc.)</li>
     *   <li>Inicia thread de escucha</li>
     *   <li>Registra en GracefulShutdownService</li>
     * </ul>
     *
     * @throws IllegalStateException si el canal ya esta iniciado
     */
    void start();

    /**
     * Detiene el canal y cierra conexiones de forma ordenada.
     *
     * <p>Este metodo debe ser idemptotente: llamarlo multiples veces
     * no debe causar errores.</p>
     *
     * <h3>Comportamiento esperado:</h3>
     * <ul>
     *   <li>Deja de aceptar nuevos mensajes</li>
     *   <li>Procesa mensajes pendientes (grace period)</li>
     *   <li>Cierra conexiones</li>
     *   <li>Libera recursos</li>
     * </ul>
     */
    void stop();

    /**
     * Verifica si el canal esta activamente escuchando.
     *
     * @return true si el canal esta iniciado y escuchando, false en caso contrario
     */
    boolean isRunning();

    /**
     * Realiza health check del canal.
     *
     * <p>Verifica que las conexiones estan activas y el canal puede
     * recibir mensajes.</p>
     *
     * @return true si el canal esta saludable, false si hay problemas
     */
    boolean isHealthy();

    /**
     * Registra un handler para mensajes entrantes.
     *
     * <p>El handler se invoca en un Virtual Thread separado para cada
     * mensaje, permitiendo procesamiento paralelo.</p>
     *
     * <h3>Thread Safety:</h3>
     * <p>El handler puede ser invocado concurrentemente. Si el handler
     * modifica estado compartido, debe ser thread-safe.</p>
     *
     * @param handler Consumer que procesa cada mensaje entrante
     * @throws NullPointerException si handler es null
     */
    void onMessage(Consumer<IncomingMessage> handler);

    /**
     * Registra un handler para errores del canal.
     *
     * <p>Se invoca cuando hay errores de conexion, parsing, o internos.
     * Util para logging y alertas.</p>
     *
     * @param errorHandler Consumer que procesa errores
     */
    void onError(Consumer<Throwable> errorHandler);

    /**
     * Cierra el canal y libera todos los recursos.
     *
     * <p>Implementacion de AutoCloseable para uso con try-with-resources.
     * Equivalente a llamar stop() seguido de limpieza final.</p>
     */
    @Override
    void close();

    /**
     * Retorna estadisticas del canal.
     *
     * @return estadisticas actuales o null si no disponibles
     */
    default ChannelStats getStats() {
        return null;
    }

    /**
     * Estadisticas de un canal de ingestion.
     *
     * @param messagesReceived total de mensajes recibidos
     * @param messagesProcessed total de mensajes procesados exitosamente
     * @param messagesFailed total de mensajes con error
     * @param lastMessageAt timestamp del ultimo mensaje (epoch ms)
     * @param uptimeMs tiempo desde que inicio el canal (ms)
     */
    record ChannelStats(
        long messagesReceived,
        long messagesProcessed,
        long messagesFailed,
        long lastMessageAt,
        long uptimeMs
    ) {
        /**
         * Calcula la tasa de exito del canal.
         *
         * @return porcentaje de mensajes procesados exitosamente (0-100)
         */
        public double successRate() {
            if (messagesReceived == 0) return 100.0;
            return (double) messagesProcessed / messagesReceived * 100;
        }
    }
}
