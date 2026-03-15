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

/**
 * Excepcion lanzada cuando el bus rechaza un mensaje por backpressure.
 *
 * <p>Esto ocurre cuando el Semaforo del bus alcanza su limite
 * (por defecto 10,000 mensajes en vuelo).</p>
 *
 * <h2>Manejo Recomendado</h2>
 * <pre>{@code
 * bus.publish(topic, envelope)
 *     .exceptionally(ex -> {
 *         if (ex.getCause() instanceof BusOverloadException boe) {
 *             log.warn("Bus al {}% de capacidad", boe.getUtilizationPercent());
 *             // Retry con backoff exponencial
 *         }
 *         return null;
 *     });
 * }</pre>
 *
 * @author Fararoni Framework
 * @since 1.0.0
 */
public class BusOverloadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long inFlightCount;
    private final long maxCapacity;

    /**
     * Constructor completo.
     *
     * @param message       Mensaje descriptivo
     * @param inFlightCount Mensajes actualmente en vuelo
     * @param maxCapacity   Capacidad maxima del bus
     */
    public BusOverloadException(String message, long inFlightCount, long maxCapacity) {
        super(message);
        this.inFlightCount = inFlightCount;
        this.maxCapacity = maxCapacity;
    }

    /**
     * Constructor simplificado.
     *
     * @param message Mensaje descriptivo
     */
    public BusOverloadException(String message) {
        super(message);
        this.inFlightCount = -1;
        this.maxCapacity = -1;
    }

    /**
     * Obtiene el numero de mensajes en vuelo cuando ocurrio la excepcion.
     *
     * @return Cantidad de mensajes en vuelo
     */
    public long getInFlightCount() {
        return inFlightCount;
    }

    /**
     * Obtiene la capacidad maxima del bus.
     *
     * @return Capacidad maxima
     */
    public long getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * Calcula el porcentaje de utilizacion del bus.
     *
     * @return Porcentaje de 0 a 100, o -1 si no hay datos
     */
    public double getUtilizationPercent() {
        if (maxCapacity <= 0) {
            return -1;
        }
        return ((double) inFlightCount / maxCapacity) * 100.0;
    }

    /**
     * Calcula tiempo sugerido de espera para retry (backoff).
     *
     * @return Milisegundos sugeridos para esperar
     */
    public long getSuggestedRetryMs() {
        double utilization = getUtilizationPercent();
        if (utilization < 0) {
            return 1000; // Default 1s
        }
        if (utilization >= 99) {
            return 5000; // Muy saturado: 5s
        }
        if (utilization >= 95) {
            return 2000; // Saturado: 2s
        }
        if (utilization >= 90) {
            return 1000; // Alto: 1s
        }
        return 500; // Moderado: 500ms
    }

    @Override
    public String toString() {
        if (maxCapacity > 0) {
            return String.format(
                "BusOverloadException[inflight=%d/%d (%.1f%%), suggestedRetry=%dms]",
                inFlightCount, maxCapacity, getUtilizationPercent(), getSuggestedRetryMs()
            );
        }
        return "BusOverloadException: " + getMessage();
    }
}
