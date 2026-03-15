/*
 * Copyright (c) 2026 Eber Cruz Fararoni. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Aegis v2.0 - Sistema Nervioso Soberano
 * FASE 28.2.1 - Bus API
 */

/**
 * API del Sistema Nervioso Soberano (Sovereign Bus).
 *
 * <p>Define los contratos para comunicacion asincrona entre agentes
 * sin acoplarse a una implementacion especifica.</p>
 *
 * <h2>Componentes Principales</h2>
 * <ul>
 *   <li>{@link dev.fararoni.bus.agent.api.bus.SovereignEnvelope} -
 *       Atomo de comunicacion (Record inmutable)</li>
 *   <li>{@link dev.fararoni.bus.agent.api.bus.SovereignEventBus} -
 *       Contrato del bus (Interface)</li>
 *   <li>{@link dev.fararoni.bus.agent.api.bus.BusOverloadException} -
 *       Excepcion de backpressure</li>
 * </ul>
 *
 * <h2>Garantias de Diseno</h2>
 * <table border="1">
 *   <tr><th>Garantia</th><th>Mecanismo</th></tr>
 *   <tr><td>Asincrono</td><td>CompletableFuture en todos los metodos</td></tr>
 *   <tr><td>Backpressure</td><td>Semaforo con limite configurable</td></tr>
 *   <tr><td>Timeout</td><td>Obligatorio en request()</td></tr>
 *   <tr><td>Observabilidad</td><td>traceId + correlationId en envelope</td></tr>
 * </table>
 *
 * <h2>Implementaciones</h2>
 * <ul>
 *   <li>{@code InMemorySovereignBus} - fararoni-core (single JVM)</li>
 *   <li>{@code NatsSovereignBus} - fararoni-enterprise (distribuido)</li>
 * </ul>
 *
 * @author Fararoni Framework
 * @since 1.0.0
 * @see dev.fararoni.bus.agent.api.bus.SovereignEnvelope
 * @see dev.fararoni.bus.agent.api.bus.SovereignEventBus
 */
package dev.fararoni.bus.agent.api.bus;
