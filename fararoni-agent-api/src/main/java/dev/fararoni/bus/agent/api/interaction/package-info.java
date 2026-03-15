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
 * Interfaces de interaccion entre el Agente y el Usuario.
 *
 * <h2>Proposito Arquitectonico</h2>
 * <p>Este paquete contiene las interfaces que definen <b>como</b> el modulo
 * Enterprise puede comunicarse con el usuario, <b>sin conocer</b> la
 * implementacion especifica de UI (JLine, REST, Slack, etc.).</p>
 *
 * <h2>Patron Render Server</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                     FLUJO DE COMUNICACION                        │
 * └─────────────────────────────────────────────────────────────────┘
 *
 *     fararoni-enterprise                    fararoni-core
 *     ┌──────────────────┐                  ┌──────────────────┐
 *     │                  │                  │                  │
 *     │  "Mostrar esto:" │ ──────────────►  │  JLine Terminal  │
 *     │  DashboardModel  │   INTERFACE      │  AttributedStr   │
 *     │  MenuModel       │   (contrato)     │  Display.update  │
 *     │                  │                  │                  │
 *     └──────────────────┘                  └──────────────────┘
 *            │                                      │
 *            │ NO conoce JLine                      │ USA JLine
 *            │ NO conoce Terminal                   │ USA Terminal
 *            │                                      │
 *            ▼                                      ▼
 *      Solo pide QUE mostrar                  Decide COMO pintarlo
 * </pre>
 *
 * <h2>Regla de Oro</h2>
 * <blockquote>
 * <b>El modulo Enterprise NUNCA debe importar org.jline.*</b>
 * <br>
 * Solo debe usar las interfaces de este paquete.
 * </blockquote>
 *
 * <h2>Interfaces Disponibles</h2>
 * <table border="1">
 *   <caption>Available interaction interfaces</caption>
 *   <tr><th>Interface</th><th>Responsabilidad</th></tr>
 *   <tr>
 *     <td>{@link AgentUserInterface}</td>
 *     <td>Contrato principal para toda interaccion UI</td>
 *   </tr>
 * </table>
 *
 * <h2>Implementaciones (en fararoni-core)</h2>
 * <ul>
 *   <li><b>JLineAgentUI:</b> Implementacion usando JLine 3 para terminal</li>
 *   <li><b>TestAgentUI:</b> Mock para tests unitarios (futuro)</li>
 *   <li><b>RestAgentUI:</b> Para exponer via API REST (futuro)</li>
 * </ul>
 *
 * <h2>Por Que Este Diseno</h2>
 * <ol>
 *   <li><b>GraalVM Native:</b> JLine solo en Core, compilado estaticamente</li>
 *   <li><b>Testabilidad:</b> Enterprise se puede testear con mocks de UI</li>
 *   <li><b>Portabilidad:</b> Mismo Enterprise sirve para CLI, REST, Slack</li>
 *   <li><b>Separacion de Concerns:</b> Logica de negocio vs presentacion</li>
 * </ol>
 *
 * <h2>Ejemplo de Uso</h2>
 * <pre>{@code
 * // En Enterprise (NO conoce JLine)
 * public class DatabaseSkill implements Skill {
 *
 *     public FNLResult connect(ExecutionContext context, String connStr) {
 *         // Pedir password sin saber como se implementa
 *         String password = context.getUI().promptSecret("Password de Oracle");
 *
 *         // Mostrar progreso sin saber de JLine
 *         context.getUI().updateStatusBar(
 *             StatusBarModel.loading("Conectando a base de datos...")
 *         );
 *
 *         // Conectar...
 *         return FNLResult.success("Conectado");
 *     }
 * }
 * }</pre>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see AgentUserInterface
 * @see dev.fararoni.bus.agent.api.ui.model
 */
package dev.fararoni.bus.agent.api.interaction;
