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
package dev.fararoni.bus.agent.api.client;

import java.util.Arrays;
import java.util.Optional;

/**
 * Proveedor de LLM soportado por Fararoni.
 *
 * <p>Este enum define los proveedores LLM disponibles y sus caracteristicas.
 * Se usa junto con {@link StreamParser} para seleccionar el parser correcto
 * segun el formato de streaming del proveedor.</p>
 *
 * <h2>Proveedores Soportados</h2>
 * <table>
 *   <tr><th>Proveedor</th><th>Formato</th><th>Descripcion</th></tr>
 *   <tr><td>OPENAI</td><td>OpenAI SSE</td><td>OpenAI, vLLM, Azure OpenAI, LM Studio</td></tr>
 *   <tr><td>OLLAMA</td><td>Ollama JSON</td><td>Ollama nativo (sin /v1/)</td></tr>
 *   <tr><td>ANTHROPIC</td><td>Anthropic SSE</td><td>Claude API (futuro)</td></tr>
 *   <tr><td>LOCAL</td><td>N/A</td><td>LLM local via java-llama.cpp</td></tr>
 * </table>
 *
 * <h2>Uso</h2>
 * <pre>{@code
 * // Via variable de entorno
 * export LLM_PROVIDER=ollama
 *
 * // Via config
 * fararoni config set llm-provider ollama
 *
 * // Obtener proveedor
 * LlmProvider provider = LlmProvider.fromString("ollama")
 *     .orElse(LlmProvider.OPENAI);
 * }</pre>
 *
 * <h2>Deteccion Automatica</h2>
 * <p>Fararoni puede detectar el proveedor automaticamente basado en la URL:</p>
 * <ul>
 *   <li>URL contiene "ollama" o puerto 11434 → OLLAMA</li>
 *   <li>URL contiene "openai.com" → OPENAI</li>
 *   <li>URL contiene "anthropic.com" → ANTHROPIC</li>
 *   <li>localhost con /v1/ → OPENAI (vLLM/LM Studio)</li>
 * </ul>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see StreamParser
 */
public enum LlmProvider {

    /**
     * OpenAI y compatibles (vLLM, Azure OpenAI, LM Studio).
     *
     * <p>Usa formato SSE con estructura:</p>
     * <pre>
     * data: {"choices":[{"delta":{"content":"..."}}]}
     * data: [DONE]
     * </pre>
     */
    OPENAI("openai", "OpenAI API compatible (vLLM, Azure, LM Studio)"),

    /**
     * Ollama nativo (sin endpoint /v1/).
     *
     * <p>Usa formato JSON linea por linea:</p>
     * <pre>
     * {"message":{"content":"..."},"done":false}
     * {"message":{"content":""},"done":true}
     * </pre>
     */
    OLLAMA("ollama", "Ollama API nativa"),

    /**
     * Anthropic Claude API (futuro).
     *
     * <p>Usa formato SSE con eventos tipados:</p>
     * <pre>
     * event: content_block_delta
     * data: {"type":"content_block_delta","delta":{"text":"..."}}
     * </pre>
     */
    ANTHROPIC("anthropic", "Anthropic Claude API"),

    /**
     * LLM local via java-llama.cpp.
     *
     * <p>No usa HTTP, ejecuta el modelo directamente en proceso.</p>
     */
    LOCAL("local", "LLM local (java-llama.cpp)");

    private final String id;
    private final String description;

    LlmProvider(String id, String description) {
        this.id = id;
        this.description = description;
    }

    /**
     * Identificador del proveedor (minusculas).
     *
     * @return id del proveedor (ej: "openai", "ollama")
     */
    public String getId() {
        return id;
    }

    /**
     * Descripcion del proveedor.
     *
     * @return descripcion legible
     */
    public String getDescription() {
        return description;
    }

    /**
     * Obtiene el proveedor por defecto.
     *
     * @return OPENAI como proveedor por defecto
     */
    public static LlmProvider getDefault() {
        return OPENAI;
    }

    /**
     * Busca un proveedor por su ID (case-insensitive).
     *
     * @param id identificador del proveedor
     * @return Optional con el proveedor encontrado
     */
    public static Optional<LlmProvider> fromString(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        String normalized = id.trim().toLowerCase();
        return Arrays.stream(values())
            .filter(p -> p.id.equals(normalized))
            .findFirst();
    }

    /**
     * Detecta el proveedor basado en la URL del servidor.
     *
     * <p>Heuristicas de deteccion:</p>
     * <ul>
     *   <li>Puerto 11434 o "ollama" en URL → OLLAMA</li>
     *   <li>"openai.com" en URL → OPENAI</li>
     *   <li>"anthropic.com" en URL → ANTHROPIC</li>
     *   <li>"/v1/" en URL → OPENAI (asume vLLM/LM Studio)</li>
     *   <li>Default → OPENAI</li>
     * </ul>
     *
     * @param serverUrl URL del servidor LLM
     * @return proveedor detectado
     */
    public static LlmProvider detectFromUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return OPENAI;
        }

        String url = serverUrl.toLowerCase();

        // Ollama detection
        if (url.contains(":11434") || url.contains("ollama")) {
            return OLLAMA;
        }

        // Anthropic detection
        if (url.contains("anthropic.com")) {
            return ANTHROPIC;
        }

        // OpenAI detection (explicit)
        if (url.contains("openai.com") || url.contains("azure.com")) {
            return OPENAI;
        }

        // Default to OpenAI for /v1/ endpoints (vLLM, LM Studio)
        return OPENAI;
    }

    /**
     * Verifica si este proveedor usa streaming SSE.
     *
     * @return true si usa SSE (OpenAI, Anthropic), false si usa JSON lines (Ollama)
     */
    public boolean usesSseStreaming() {
        return this == OPENAI || this == ANTHROPIC;
    }

    /**
     * Verifica si este proveedor es local (sin HTTP).
     *
     * @return true si es LOCAL
     */
    public boolean isLocal() {
        return this == LOCAL;
    }

    @Override
    public String toString() {
        return id;
    }
}
