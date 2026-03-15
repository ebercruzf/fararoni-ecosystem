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
package dev.fararoni.core.core.agent.dynamic;

import dev.fararoni.core.core.llm.StreamingLlmCallback;
import dev.fararoni.core.core.llm.providers.OllamaProvider;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class OllamaStreamingProvider implements StreamingLlmInferenceProvider {
    private static final Logger LOG = Logger.getLogger(OllamaStreamingProvider.class.getName());

    private final OllamaProvider ollamaProvider;
    private final String model;

    public OllamaStreamingProvider(OllamaProvider ollamaProvider, String model) {
        this.ollamaProvider = ollamaProvider;
        this.model = model;
        LOG.info("OllamaStreamingProvider creado con modelo: " + model);
    }

    @Override
    public String infer(String systemPrompt, String userMessage) {
        LOG.fine("Usando modo BATCH (infer) para tarea simple");

        return ollamaProvider.inferStreaming(model, systemPrompt, userMessage,
            new StreamingLlmCallback() {
                @Override
                public void onToken(String token) {
                }

                @Override
                public void onComplete(String fullResponse) {
                    LOG.fine("Inferencia batch completada");
                }

                @Override
                public void onError(Throwable error) {
                    LOG.warning("Error en inferencia batch: " + error.getMessage());
                }
            });
    }

    @Override
    public String inferStreamingParallel(
            String systemPrompt,
            String userMessage,
            Path outputDir,
            StreamingLlmCallback callback) {
        LOG.info("Usando modo STREAMING PARALELO");
        LOG.info("Output dir: " + outputDir);
        System.out.println("[STREAMING-PARALLEL] Modo paralelo MIL-SPEC activado");
        System.out.println("[STREAMING-PARALLEL] Output: " + outputDir);

        return ollamaProvider.inferStreamingParallel(
            model,
            systemPrompt,
            userMessage,
            outputDir,
            callback
        );
    }

    @Override
    public String getCurrentModel() {
        return model;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    public OllamaProvider getOllamaProvider() {
        return ollamaProvider;
    }
}
