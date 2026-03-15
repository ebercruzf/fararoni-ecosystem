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
package dev.fararoni.bus.agent.api.bus;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Contrato del Sistema Nervioso Soberano.
 *
 * <p>Define las operaciones asincronas para publicar, solicitar y suscribirse
 * a mensajes. Es agnostico a la implementacion subyacente.</p>
 *
 * <h2>Implementaciones</h2>
 * <table>
 *   <tr><th>Clase</th><th>Modulo</th><th>Uso</th></tr>
 *   <tr><td>InMemorySovereignBus</td><td>fararoni-core</td><td>Desarrollo, single-JVM</td></tr>
 *   <tr><td>NatsSovereignBus</td><td>fararoni-enterprise</td><td>Produccion distribuida</td></tr>
 * </table>
 *
 * <h2>Garantias de Diseno</h2>
 * <ul>
 *   <li>Asincrono por defecto (CompletableFuture)</li>
 *   <li>Timeout obligatorio en request()</li>
 *   <li>Backpressure via BusOverloadException</li>
 * </ul>
 *
 * <h2>Convenciones de Topicos</h2>
 * <ul>
 *   <li>{@code skill.*} - Tools/Capabilities (ej: skill.fs_write)</li>
 *   <li>{@code event.*} - Eventos del sistema (ej: event.file_changed)</li>
 *   <li>{@code sys.*} - Topicos del sistema (ej: sys.dlq.main)</li>
 *   <li>{@code agent.*} - Comunicacion entre agentes (ej: agent.pm.inbox)</li>
 * </ul>
 *
 * @author Fararoni Framework
 * @since 1.0.0
 * @see SovereignEnvelope
 * @see BusOverloadException
 */
public interface SovereignEventBus {

    // =========================================================================
    // PUBLICACION
    // =========================================================================

    /**
     * Publicacion asincrona "Fire and Forget".
     *
     * <p>El Future completa cuando el mensaje ha sido aceptado por el bus,
     * NO cuando ha sido procesado por el consumidor.</p>
     *
     * @param topic    Topico destino (ej: "skill.fs_write")
     * @param envelope Sobre con payload y metadatos
     * @param <T>      Tipo del payload
     * @return Future que completa cuando el mensaje esta encolado
     * @throws BusOverloadException si el backpressure rechaza el mensaje
     */
    <T> CompletableFuture<Void> publish(String topic, SovereignEnvelope<T> envelope);

    // =========================================================================
    // REQUEST/REPLY
    // =========================================================================

    /**
     * Patron Request/Reply asincrono con timeout estricto.
     *
     * <p>Esencial para que los agentes orquesten herramientas. El timeout
     * es OBLIGATORIO para evitar "agentes zombis" esperando eternamente.</p>
     *
     * @param topic        Topico destino
     * @param envelope     Sobre con la peticion
     * @param responseType Clase del tipo de respuesta esperado
     * @param timeout      Tiempo maximo de espera
     * @param <T>          Tipo del payload de la peticion
     * @param <R>          Tipo de la respuesta esperada
     * @return Future con la respuesta tipada
     * @throws java.util.concurrent.TimeoutException si excede el timeout
     */
    <T, R> CompletableFuture<R> request(
        String topic,
        SovereignEnvelope<T> envelope,
        Class<R> responseType,
        Duration timeout
    );

    // =========================================================================
    // SUSCRIPCION
    // =========================================================================

    /**
     * Suscripcion reactiva a un topico.
     *
     * <p>El consumer se ejecuta en un Virtual Thread por cada mensaje recibido.</p>
     *
     * @param topic       Topico a escuchar
     * @param payloadType Clase del tipo de payload esperado
     * @param consumer    Logica a ejecutar por cada mensaje
     * @param <T>         Tipo del payload
     */
    <T> void subscribe(String topic, Class<T> payloadType, Consumer<SovereignEnvelope<T>> consumer);

    // =========================================================================
    // METRICAS Y ESTADO
    // =========================================================================

    /**
     * Obtiene el numero de mensajes actualmente en vuelo.
     *
     * @return Cantidad de mensajes siendo procesados
     */
    long getInFlightCount();

    /**
     * Verifica si el bus esta operativo y saludable.
     *
     * @return true si puede aceptar mensajes
     */
    boolean isHealthy();

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Cierra el bus de forma ordenada.
     *
     * <p>Espera a que los mensajes en vuelo se procesen antes de cerrar.</p>
     *
     * @param timeout Tiempo maximo de espera para shutdown
     */
    default void shutdown(Duration timeout) {
        // Implementacion por defecto vacia
    }

    // =========================================================================
    // SPI (Service Provider Interface)
    // =========================================================================

    /**
     * Define la prioridad de carga del Bus.
     *
     * <p>El sistema usara la implementacion con el numero mas alto encontrada
     * en el classpath. Permite "plugin" de buses Enterprise sin recompilar Core.</p>
     *
     * <ul>
     *   <li>0 = Default (InMemorySovereignBus / Core Open Source)</li>
     *   <li>50 = Persistente Local (ChronicleQueueBus)</li>
     *   <li>100 = Enterprise (NatsSovereignBus / Comercial)</li>
     * </ul>
     *
     * @return nivel de prioridad (mayor = preferido)
     * @since 1.0.0
     */
    default int getPriority() {
        return 0;
    }

    // =========================================================================
    // HEALTH CHECK (FASE 80.1.3)
    // =========================================================================

    /**
     * Validacion de Grado Militar: Verifica si el transporte esta listo para operar.
     *
     * <p>Para buses distribuidos (NATS), verifica conexion al servidor.
     * Para buses locales (InMemory, Chronicle), siempre retorna true.</p>
     *
     * <p>El {@link SovereignBusFactory} usa este metodo para validar la salud
     * del bus ANTES de seleccionarlo. Si el bus de prioridad 100 no esta disponible,
     * el Factory degradara al siguiente nivel.</p>
     *
     * @return true si el bus puede aceptar mensajes
     * @since 1.0.0
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Verifica si el bus tiene mensajes pendientes de enviar.
     *
     * <p>Usado por el {@link ReplayEngine} para determinar si debe
     * mantener el modo FIFO (nuevos mensajes van al buffer local
     * hasta que el replay complete).</p>
     *
     * @return true si hay mensajes en buffer esperando ser enviados
     * @since 1.0.0
     */
    default boolean hasPendingMessages() {
        return false;
    }
}
