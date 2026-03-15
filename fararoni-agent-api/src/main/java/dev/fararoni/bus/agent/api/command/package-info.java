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
 * API de comandos de consola para FARARONI.
 *
 * <p>Este paquete define las interfaces para comandos dinamicos
 * ejecutados desde el shell interactivo.
 *
 * <h2>Componentes Principales:</h2>
 * <ul>
 *   <li>{@link dev.fararoni.bus.agent.api.command.ConsoleCommand} - Interfaz para comandos</li>
 *   <li>{@link dev.fararoni.bus.agent.api.command.ExecutionContext} - Contexto de ejecucion</li>
 *   <li>{@link dev.fararoni.bus.agent.api.command.CommandCategory} - Categorias para /help</li>
 * </ul>
 *
 * <h2>Arquitectura Hibrida:</h2>
 * <pre>
 * Usuario: /web example.com
 *         │
 *         ▼
 * ┌────────────────────────┐
 * │   InteractiveShell     │
 * │   1. Switch legacy     │  &lt;-- /help, /load, /git (manejados aqui)
 * │   2. CommandRegistry   │  &lt;-- /web, /tree (manejados via ConsoleCommand)
 * └────────────────────────┘
 * </pre>
 *
 * <h2>Relacion con Sistema de Skills:</h2>
 * <p>Este sistema es paralelo pero independiente del sistema de Skills
 * (ToolSkill/SkillModule) que maneja LLM function calling.
 *
 * <table border="1">
 *   <caption>ConsoleCommand vs ToolSkill systems</caption>
 *   <tr><th>Sistema</th><th>Proposito</th><th>Trigger</th></tr>
 *   <tr><td>ConsoleCommand</td><td>Comandos CLI</td><td>Usuario escribe /web</td></tr>
 *   <tr><td>ToolSkill</td><td>LLM Tools</td><td>LLM decide "use tool: web"</td></tr>
 * </table>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see dev.fararoni.bus.agent.api.command.ConsoleCommand
 */
package dev.fararoni.bus.agent.api.command;
