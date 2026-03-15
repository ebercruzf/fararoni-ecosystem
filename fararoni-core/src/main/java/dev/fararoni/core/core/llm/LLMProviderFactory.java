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
package dev.fararoni.core.core.llm;

import dev.fararoni.core.core.config.AgentConfig;
import dev.fararoni.core.core.llm.providers.OllamaProvider;
import dev.fararoni.core.core.llm.providers.OpenAIProvider;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class LLMProviderFactory {
    private static final Logger logger = Logger.getLogger(LLMProviderFactory.class.getName());

    private LLMProviderFactory() {
    }

    public static LLMProvider createRabbitProvider(AgentConfig config) {
        String ollamaUrl = config.getOllamaUrl();
        logger.info(() -> String.format("Creando RabbitProvider (Ollama local): %s", ollamaUrl));
        return new OllamaProvider(ollamaUrl);
    }

    public static LLMProvider createTurtleProvider(AgentConfig config) {
        String providerType = config.getProviderType().toLowerCase();

        return switch (providerType) {
            case "ollama" -> {
                String ollamaUrl = config.getOllamaUrl();
                logger.info(() -> String.format("Creando TurtleProvider (Ollama local 32B): %s", ollamaUrl));
                yield new OllamaProvider(ollamaUrl);
            }

            case "openai", "groq" -> {
                String apiKey = config.getCloudApiKey();
                if (apiKey == null || apiKey.isBlank()) {
                    String envVar = providerType.equals("groq") ? "GROQ_API_KEY" : "OPENAI_API_KEY";
                    throw new IllegalStateException(
                        String.format("API key no configurada. Establece la variable de entorno %s", envVar));
                }
                String baseUrl = config.getCloudBaseUrl();
                logger.info(() -> String.format("Creando TurtleProvider (nube %s): %s", providerType.toUpperCase(), baseUrl));
                yield new OpenAIProvider(apiKey, baseUrl);
            }

            default -> throw new IllegalArgumentException(
                String.format("Proveedor LLM desconocido: '%s'. Valores validos: ollama, openai, groq", providerType));
        };
    }

    @Deprecated
    public static LLMProvider createProvider(AgentConfig config) {
        return createTurtleProvider(config);
    }

    public static boolean isTurtleAvailable(AgentConfig config) {
        if (config.isCloudProvider()) {
            String apiKey = config.getCloudApiKey();
            return apiKey != null && !apiKey.isBlank();
        }
        return true;
    }

    public static String getTurtleProviderDescription(AgentConfig config) {
        if (config.isCloudProvider()) {
            return String.format("%s Cloud (%s)",
                config.getProviderType().toUpperCase(),
                config.getCloudModelName());
        } else {
            return String.format("Ollama Local (%s)",
                config.getTurtleModelName());
        }
    }
}
