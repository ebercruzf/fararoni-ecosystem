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
 * Contratos para clientes LLM vendor-agnostic.
 *
 * <p>Este paquete define interfaces que permiten conectar con diferentes
 * proveedores de LLM (Ollama, OpenAI, Anthropic, vLLM) sin acoplar el
 * codigo a un proveedor especifico.</p>
 *
 * <h2>Principio de Diseno: Ports &amp; Adapters</h2>
 * <p>Las interfaces en este paquete son "puertos" (contracts) que viven
 * en el API. Las implementaciones (adaptadores) viven en fararoni-core
 * y contienen las dependencias especificas del proveedor.</p>
 *
 * <pre>
 * fararoni-agent-api (este paquete)
 *     │
 *     │  StreamParser (interface)
 *     │
 *     └──────────────────────────────────────┐
 *                                            │ implementa
 *                                            ▼
 * fararoni-core
 *     │
 *     ├── OpenAiStreamParser (usa Jackson)
 *     ├── OllamaStreamParser (usa Jackson)
 *     └── AnthropicStreamParser (futuro)
 * </pre>
 *
 * <h2>Componentes Principales</h2>
 * <dl>
 *   <dt>{@link dev.fararoni.bus.agent.api.client.StreamParser}</dt>
 *   <dd>Interface para parsing de streaming SSE de diferentes proveedores LLM</dd>
 * </dl>
 *
 * <h2>Beneficios</h2>
 * <ul>
 *   <li><b>Vendor-Agnostic:</b> Agregar proveedores sin modificar codigo existente</li>
 *   <li><b>Testeable:</b> Facil de mockear en tests unitarios</li>
 *   <li><b>Extensible:</b> Nuevos proveedores = nueva clase, no cambios</li>
 * </ul>
 *
 * <h2>Parte de Track A: Vendor-Agnostic</h2>
 * <p>Este paquete es parte de la iniciativa Track A para hacer Fararoni
 * completamente agnostico del proveedor LLM.</p>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
package dev.fararoni.bus.agent.api.client;
