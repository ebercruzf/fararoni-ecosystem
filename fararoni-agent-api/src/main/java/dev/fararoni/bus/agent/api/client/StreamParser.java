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

import java.util.Optional;

/**
 * Interface para parsing de respuestas de streaming de diferentes proveedores LLM.
 *
 * <p>Esta interface permite desacoplar la logica de parsing de SSE (Server-Sent Events)
 * del cliente HTTP, habilitando soporte para multiples proveedores sin modificar
 * el codigo del cliente.</p>
 *
 * <h2>Motivacion (Vendor-Agnostic)</h2>
 * <p>Cada proveedor LLM tiene un formato diferente para streaming:</p>
 * <ul>
 *   <li><b>OpenAI/vLLM:</b> {@code choices[0].delta.content}</li>
 *   <li><b>Ollama:</b> {@code response} o {@code message.content}</li>
 *   <li><b>Anthropic:</b> {@code delta.text} en eventos content_block_delta</li>
 * </ul>
 *
 * <p>Sin esta interface, agregar un nuevo proveedor requiere modificar VllmClient.
 * Con esta interface, solo se crea una nueva implementacion.</p>
 *
 * <h2>Patron de Diseno</h2>
 * <p>Strategy Pattern - permite intercambiar algoritmos de parsing en runtime.</p>
 *
 * <h2>Ejemplo de Uso</h2>
 * <pre>{@code
 * StreamParser parser = new OllamaStreamParser();
 *
 * // En el handler de SSE:
 * eventSource.onEvent((event, data) -> {
 *     if (parser.isEndOfStream(data)) {
 *         onComplete.run();
 *         return;
 *     }
 *
 *     parser.parseChunk(data).ifPresent(content -> {
 *         onToken.accept(content);
 *     });
 * });
 * }</pre>
 *
 * <h2>Implementaciones Esperadas</h2>
 * <ul>
 *   <li>{@code OpenAiStreamParser} - Para OpenAI, Azure OpenAI, vLLM</li>
 *   <li>{@code OllamaStreamParser} - Para Ollama nativo</li>
 *   <li>{@code AnthropicStreamParser} - Para Claude API (futuro)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Las implementaciones deben ser thread-safe si se comparten entre requests.
 * Se recomienda que sean stateless para maxima seguridad.</p>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see <a href="https://platform.openai.com/docs/api-reference/streaming">OpenAI Streaming</a>
 * @see <a href="https://github.com/ollama/ollama/blob/main/docs/api.md">Ollama API</a>
 */
public interface StreamParser {

    /**
     * Extrae el contenido de texto de un chunk de streaming.
     *
     * <p>Cada proveedor envia datos en formato diferente. Esta metodo
     * abstrae esas diferencias y retorna solo el texto generado.</p>
     *
     * <h3>Comportamiento esperado:</h3>
     * <ul>
     *   <li>Si el chunk contiene texto generado, retorna Optional con el texto</li>
     *   <li>Si el chunk es de control (ej: [DONE]), retorna Optional.empty()</li>
     *   <li>Si el chunk es invalido, retorna Optional.empty() (no lanza excepcion)</li>
     * </ul>
     *
     * <h3>Ejemplos por proveedor:</h3>
     * <pre>
     * // OpenAI: {"choices":[{"delta":{"content":"Hola"}}]}
     * parseChunk(data) -> Optional.of("Hola")
     *
     * // Ollama: {"response":"Hola"}
     * parseChunk(data) -> Optional.of("Hola")
     *
     * // Control: data: [DONE]
     * parseChunk(data) -> Optional.empty()
     * </pre>
     *
     * @param rawData el dato crudo del stream (JSON string, SSE event data, etc.)
     * @return Optional con el contenido extraido, o empty si es chunk de control
     */
    Optional<String> parseChunk(String rawData);

    /**
     * Determina si el chunk indica fin del stream.
     *
     * <p>Los proveedores usan diferentes marcadores de fin:</p>
     * <ul>
     *   <li><b>OpenAI:</b> {@code data: [DONE]}</li>
     *   <li><b>Ollama:</b> {@code {"done": true}}</li>
     *   <li><b>Anthropic:</b> evento {@code message_stop}</li>
     * </ul>
     *
     * @param rawData el dato crudo del stream
     * @return true si el stream ha terminado, false en caso contrario
     */
    boolean isEndOfStream(String rawData);

    /**
     * Retorna el nombre del proveedor que este parser soporta.
     *
     * <p>Usado para logging y seleccion automatica de parser.</p>
     *
     * @return nombre del proveedor (ej: "openai", "ollama", "anthropic")
     */
    String getProviderName();

    /**
     * Indica si este parser soporta el modo chat (multi-turn conversation).
     *
     * <p>Algunos proveedores tienen formatos diferentes para completion
     * vs chat. Este metodo permite adaptar el parsing.</p>
     *
     * @return true si soporta modo chat, false si solo soporta completion
     */
    default boolean supportsChatMode() {
        return true;
    }

    /**
     * Extrae metadatos adicionales del chunk si estan disponibles.
     *
     * <p>Algunos proveedores incluyen informacion adicional como:</p>
     * <ul>
     *   <li>Tokens usados</li>
     *   <li>Finish reason</li>
     *   <li>Model name</li>
     * </ul>
     *
     * <p>Implementacion por defecto retorna empty.</p>
     *
     * @param rawData el dato crudo del stream
     * @return Optional con metadatos parseados, o empty si no hay
     */
    default Optional<StreamMetadata> parseMetadata(String rawData) {
        return Optional.empty();
    }

    /**
     * Metadatos extraidos de un chunk de streaming.
     *
     * @param finishReason razon de finalizacion (stop, length, etc.)
     * @param promptTokens tokens usados en el prompt (puede ser null)
     * @param completionTokens tokens generados (puede ser null)
     * @param modelName nombre del modelo usado (puede ser null)
     */
    record StreamMetadata(
        String finishReason,
        Integer promptTokens,
        Integer completionTokens,
        String modelName
    ) {
        /**
         * Verifica si el finish_reason indica terminacion normal.
         *
         * @return true si termino normalmente (stop), false en caso contrario
         */
        public boolean isNormalFinish() {
            return "stop".equalsIgnoreCase(finishReason)
                || "end_turn".equalsIgnoreCase(finishReason);
        }

        /**
         * Calcula el total de tokens si ambos valores estan disponibles.
         *
         * @return total de tokens o null si no hay datos
         */
        public Integer totalTokens() {
            if (promptTokens == null || completionTokens == null) {
                return null;
            }
            return promptTokens + completionTokens;
        }
    }
}
