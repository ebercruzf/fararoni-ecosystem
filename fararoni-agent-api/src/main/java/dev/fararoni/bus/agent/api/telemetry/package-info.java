/*
 * Copyright (c) 2026 Eber Cruz Fararoni. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Aegis v2.0 - Telemetria Soberana
 * FASE 28.2.3 - Telemetry API
 */

/**
 * API de Telemetria Soberana para observabilidad del sistema.
 *
 * <p>Proporciona metricas de alto rendimiento sin depender de vendors
 * externos. En modo standalone imprime a consola, en modo enterprise
 * exporta a Prometheus/Datadog.</p>
 *
 * <h2>Componentes</h2>
 * <ul>
 *   <li>{@link dev.fararoni.bus.agent.api.telemetry.SovereignMetrics} -
 *       Singleton de metricas con LongAdder</li>
 * </ul>
 *
 * <h2>Tipos de Metricas</h2>
 * <table border="1">
 *   <tr><th>Tipo</th><th>Uso</th><th>Ejemplo</th></tr>
 *   <tr><td>Counter</td><td>Acumulativo</td><td>Total mensajes publicados</td></tr>
 *   <tr><td>Gauge</td><td>Estado actual</td><td>Mensajes en vuelo</td></tr>
 * </table>
 *
 * <h2>Performance</h2>
 * <p>Usa {@link java.util.concurrent.atomic.LongAdder} que es 10x mas
 * rapido que AtomicLong en alta concurrencia.</p>
 *
 * @author Fararoni Framework
 * @since 1.0.0
 * @see dev.fararoni.bus.agent.api.telemetry.SovereignMetrics
 */
package dev.fararoni.bus.agent.api.telemetry;
