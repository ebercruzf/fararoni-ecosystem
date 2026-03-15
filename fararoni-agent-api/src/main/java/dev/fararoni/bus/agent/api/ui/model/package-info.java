/*
 * Copyright 2025-2026 Eber Cruz Fararoni
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
 */

/**
 * Modelos de datos para la interfaz de usuario del agente.
 *
 * <h2>Proposito</h2>
 * <p>Este paquete contiene DTOs (Data Transfer Objects) que representan
 * los datos que el modulo Enterprise puede enviar al Core para ser
 * renderizados en la interfaz de usuario.</p>
 *
 * <h2>Patron Arquitectonico: Render Server</h2>
 * <p>Estos modelos son parte del patron "Render Server" donde:</p>
 * <ul>
 *   <li><b>Enterprise (Backend):</b> Decide QUE mostrar - construye estos modelos</li>
 *   <li><b>Core (Frontend):</b> Decide COMO mostrarlo - renderiza usando JLine</li>
 * </ul>
 *
 * <h2>Regla de Oro</h2>
 * <p><b>Estos modelos son SOLO datos, NO contienen logica de renderizado.</b></p>
 * <p>Nunca deben importar org.jline.* ni ninguna dependencia de UI.</p>
 *
 * <h2>Modelos Disponibles</h2>
 * <table border="1">
 *   <caption>UI models available in FNL</caption>
 *   <tr><th>Modelo</th><th>Uso</th></tr>
 *   <tr><td>{@link DashboardModel}</td><td>Dashboard del agente con status, tokens, skills</td></tr>
 *   <tr><td>{@link StatusBarModel}</td><td>Barra de estado global con progreso</td></tr>
 *   <tr><td>{@link MenuModel}</td><td>Menu interactivo con opciones</td></tr>
 *   <tr><td>{@link MenuItem}</td><td>Item individual de un menu</td></tr>
 *   <tr><td>{@link LogEntry}</td><td>Entrada de log para mostrar en dashboard</td></tr>
 * </table>
 *
 * <h2>Ejemplo de Uso (desde Enterprise)</h2>
 * <pre>{@code
 * // Enterprise construye el modelo (SOLO datos)
 * var dashboard = new DashboardModel(
 *     "THINKING",
 *     4500,
 *     8000,
 *     List.of("RAG", "GitSkill"),
 *     "Analizando User.java",
 *     List.of()
 * );
 *
 * // Enterprise pide a Core que lo renderice (sin saber como)
 * context.getUI().renderDashboard(dashboard);
 * }</pre>
 *
 * <h2>Por Que Usar Records</h2>
 * <p>Usamos Java Records (Java 16+) porque:</p>
 * <ul>
 *   <li>Son inmutables por defecto</li>
 *   <li>Generan automaticamente equals, hashCode, toString</li>
 *   <li>Son ligeros y faciles de serializar</li>
 *   <li>Expresan claramente que son solo contenedores de datos</li>
 * </ul>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see dev.fararoni.bus.agent.api.interaction.AgentUserInterface
 */
package dev.fararoni.bus.agent.api.ui.model;
