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
package dev.fararoni.bus.agent.api.telemetry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tablero de control agnostico para metricas del sistema.
 *
 * <p>Singleton ligero que mide el pulso del sistema sin acoplarse
 * a vendors de monitoreo. En "Modo Laptop" imprime en consola,
 * en "Modo Enterprise" envia datos a Prometheus/Datadog.</p>
 *
 * <h2>Tipos de Metricas</h2>
 * <ul>
 *   <li><b>Counters:</b> Acumulativos (Total mensajes, Total errores)</li>
 *   <li><b>Gauges:</b> Estado actual (Mensajes en vuelo, Agentes activos)</li>
 * </ul>
 *
 * <h2>Performance</h2>
 * <p>Usa {@link LongAdder} que es 10x mas rapido que AtomicLong
 * en alta concurrencia (Java 8+).</p>
 *
 * <h2>Uso Tipico</h2>
 * <pre>{@code
 * // Incrementar contador
 * SovereignMetrics.INSTANCE.increment("bus.messages.published");
 *
 * // Modificar gauge
 * SovereignMetrics.INSTANCE.gauge("bus.inflight", 1);   // +1
 * SovereignMetrics.INSTANCE.gauge("bus.inflight", -1);  // -1
 *
 * // Snapshot para Prometheus
 * Map<String, Long> metrics = SovereignMetrics.INSTANCE.snapshot();
 * }</pre>
 *
 * <h2>Metricas Estandar del Bus</h2>
 * <ul>
 *   <li>{@code bus.messages.published} - Total publicados</li>
 *   <li>{@code bus.messages.delivered} - Total entregados</li>
 *   <li>{@code bus.messages.success} - Total exitosos</li>
 *   <li>{@code bus.messages.failed} - Total fallidos (a DLQ)</li>
 *   <li>{@code bus.inflight} - En procesamiento ahora</li>
 * </ul>
 *
 * @author Fararoni Framework
 * @since 1.0.0
 */
public final class SovereignMetrics {

    /**
     * Instancia singleton.
     */
    public static final SovereignMetrics INSTANCE = new SovereignMetrics();

    // Contadores acumulativos
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();

    // Medidores de estado actual
    private final Map<String, LongAdder> gauges = new ConcurrentHashMap<>();

    // Constructor privado (singleton)
    private SovereignMetrics() {
    }

    // =========================================================================
    // COUNTERS (Acumulativos)
    // =========================================================================

    /**
     * Incrementa un contador en 1.
     *
     * @param metricName Nombre de la metrica (ej: "bus.messages.published")
     */
    public void increment(String metricName) {
        counters.computeIfAbsent(metricName, k -> new LongAdder()).increment();
    }

    /**
     * Incrementa un contador en una cantidad especifica.
     *
     * @param metricName Nombre de la metrica
     * @param delta      Cantidad a incrementar
     */
    public void add(String metricName, long delta) {
        counters.computeIfAbsent(metricName, k -> new LongAdder()).add(delta);
    }

    /**
     * Obtiene el valor actual de un contador.
     *
     * @param metricName Nombre de la metrica
     * @return Valor actual o 0 si no existe
     */
    public long getCount(String metricName) {
        LongAdder adder = counters.get(metricName);
        return adder != null ? adder.sum() : 0;
    }

    // =========================================================================
    // GAUGES (Estado Actual)
    // =========================================================================

    /**
     * Modifica un gauge (medidor de estado actual).
     *
     * @param metricName Nombre de la metrica (ej: "bus.inflight")
     * @param delta      Cambio (+1 para incrementar, -1 para decrementar)
     */
    public void gauge(String metricName, long delta) {
        gauges.computeIfAbsent(metricName, k -> new LongAdder()).add(delta);
    }

    /**
     * Establece el valor absoluto de un gauge.
     *
     * @param metricName Nombre de la metrica
     * @param value      Valor absoluto
     */
    public void setGauge(String metricName, long value) {
        LongAdder adder = gauges.computeIfAbsent(metricName, k -> new LongAdder());
        adder.reset();
        adder.add(value);
    }

    /**
     * Obtiene el valor actual de un gauge.
     *
     * @param metricName Nombre de la metrica
     * @return Valor actual o 0 si no existe
     */
    public long getGauge(String metricName) {
        LongAdder adder = gauges.get(metricName);
        return adder != null ? adder.sum() : 0;
    }

    // =========================================================================
    // SNAPSHOT (Para Exportacion)
    // =========================================================================

    /**
     * Obtiene snapshot de todas las metricas.
     *
     * <p>Util para exportar a Prometheus, Datadog, etc.</p>
     *
     * @return Mapa inmutable con todas las metricas
     */
    public Map<String, Long> snapshot() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();

        // Counters
        counters.forEach((k, v) -> snapshot.put(k, v.sum()));

        // Gauges (prefijo para distinguir)
        gauges.forEach((k, v) -> snapshot.put("gauge." + k, v.sum()));

        return Map.copyOf(snapshot);
    }

    /**
     * Obtiene snapshot solo de counters.
     *
     * @return Mapa con contadores
     */
    public Map<String, Long> snapshotCounters() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        counters.forEach((k, v) -> snapshot.put(k, v.sum()));
        return Map.copyOf(snapshot);
    }

    /**
     * Obtiene snapshot solo de gauges.
     *
     * @return Mapa con gauges
     */
    public Map<String, Long> snapshotGauges() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        gauges.forEach((k, v) -> snapshot.put(k, v.sum()));
        return Map.copyOf(snapshot);
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    /**
     * Reinicia todas las metricas (para testing).
     */
    public void reset() {
        counters.clear();
        gauges.clear();
    }

    /**
     * Obtiene representacion legible de las metricas.
     *
     * @return String con formato para consola
     */
    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Sovereign Metrics ===\n");

        sb.append("\nCounters:\n");
        counters.forEach((k, v) ->
            sb.append(String.format("  %s: %d%n", k, v.sum()))
        );

        sb.append("\nGauges:\n");
        gauges.forEach((k, v) ->
            sb.append(String.format("  %s: %d%n", k, v.sum()))
        );

        return sb.toString();
    }
}
