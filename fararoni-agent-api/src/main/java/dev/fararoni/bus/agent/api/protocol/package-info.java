/*
 * Copyright (c) 2026 Eber Cruz Fararoni. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Aegis v2.0 - Protocolo Inter-Agente
 * FASE 30 - Inter-Agent Communication
 */

/**
 * Protocolo de comunicacion entre agentes.
 *
 * <p>Define el "lenguaje comun" para la comunicacion agente-a-agente
 * en el enjambre de Fararoni.</p>
 *
 * <h2>Componentes</h2>
 * <ul>
 *   <li>{@link dev.fararoni.bus.agent.api.protocol.AgentMessage} -
 *       Mensaje estructurado para comunicacion</li>
 * </ul>
 *
 * <h2>Convenciones de Direccionamiento (Topics)</h2>
 * <table border="1">
 *   <tr><th>Patron</th><th>Significado</th><th>Metodo Bus</th></tr>
 *   <tr>
 *     <td>{@code agent.{rol}.{id}.inbox}</td>
 *     <td>Mensaje directo a un agente</td>
 *     <td>request()</td>
 *   </tr>
 *   <tr>
 *     <td>{@code agent.{rol}.broadcast}</td>
 *     <td>Broadcast a todos los agentes de un rol</td>
 *     <td>publish()</td>
 *   </tr>
 *   <tr>
 *     <td>{@code agent.{rol}.pool}</td>
 *     <td>Load balancing entre agentes de un rol</td>
 *     <td>publish()</td>
 *   </tr>
 *   <tr>
 *     <td>{@code mission.{id}.update}</td>
 *     <td>Canal de una mision especifica</td>
 *     <td>publish()</td>
 *   </tr>
 *   <tr>
 *     <td>{@code swarm.knowledge.share}</td>
 *     <td>Conocimiento global del enjambre</td>
 *     <td>publish()</td>
 *   </tr>
 * </table>
 *
 * <h2>Patrones de Comunicacion</h2>
 * <ul>
 *   <li><b>Request/Reply:</b> Supervisor asigna tarea, espera confirmacion</li>
 *   <li><b>Pipeline:</b> Developer termina, pasa a QA automaticamente</li>
 *   <li><b>Broadcast/MapReduce:</b> Arquitecto pregunta, todos responden</li>
 * </ul>
 *
 * @author Fararoni Framework
 * @since 1.0.0
 * @see dev.fararoni.bus.agent.api.protocol.AgentMessage
 */
package dev.fararoni.bus.agent.api.protocol;
