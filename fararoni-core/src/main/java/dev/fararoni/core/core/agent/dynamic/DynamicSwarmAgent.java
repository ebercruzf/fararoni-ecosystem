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

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.agents.AbstractSwarmAgent;
import dev.fararoni.core.core.agent.model.AgentTemplate;
import dev.fararoni.core.core.agent.model.AgentInstanceConfig.WiringConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class DynamicSwarmAgent extends AbstractSwarmAgent {
    private static final Logger LOG = Logger.getLogger(DynamicSwarmAgent.class.getName());

    private final String id;
    private final AgentTemplate template;
    private final WiringConfig wiring;
    private final LlmInferenceProvider llmProvider;

    private final ExecutorService brainExecutor;

    public DynamicSwarmAgent(
            String id,
            AgentTemplate template,
            WiringConfig wiring,
            SovereignEventBus bus,
            LlmInferenceProvider llmProvider) {
        super(template.roleName(), bus);
        this.id = id;
        this.template = template;
        this.wiring = wiring;
        this.llmProvider = llmProvider;
        this.brainExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public String getId() {
        return id;
    }

    public void start() {
        LOG.info(() -> String.format("[%s] Iniciando (Role: %s)", id, template.roleName()));

        if (wiring.inputTopics() != null) {
            for (String topic : wiring.inputTopics()) {
                bus.subscribe(topic, String.class, this::handleMessage);
                LOG.info(() -> String.format("[%s] Escuchando: %s", id, topic));
            }
        }

        logIdle("Esperando mensajes...");
    }

    public void stop() {
        brainExecutor.shutdownNow();
        LOG.info(() -> String.format("[%s] Detenido.", id));
    }

    public String getPrimaryInputTopic() {
        if (wiring.inputTopics() != null && !wiring.inputTopics().isEmpty()) {
            return wiring.inputTopics().get(0);
        }
        return null;
    }

    public AgentTemplate getTemplate() {
        return template;
    }

    public WiringConfig getWiring() {
        return wiring;
    }

    private void handleMessage(SovereignEnvelope<String> envelope) {
        if (id.equals(envelope.userId())) {
            return;
        }

        brainExecutor.submit(() -> processCognitiveLoad(envelope));
    }

    private void processCognitiveLoad(SovereignEnvelope<String> envelope) {
        try {
            logThinking("Procesando de: " + envelope.userId());

            String inputContent = envelope.payload();
            String fullPrompt = buildPrompt(inputContent);

            String response = llmProvider.infer(template.systemPrompt(), fullPrompt);

            if (wiring.outputTopic() != null && !wiring.outputTopic().isBlank()) {
                publishResult(envelope, response);
            } else {
                logSuccess("Tarea completada (sin salida configurada).");
            }
        } catch (Exception e) {
            LOG.severe(() -> String.format("[%s] Fallo: %s", id, e.getMessage()));
            logError("Error procesando mensaje");

            if (wiring.deadLetterTopic() != null) {
                publishError(envelope, e.getMessage());
            }
        }

        logIdle("Esperando mensajes...");
    }

    private String buildPrompt(String input) {
        return String.format("""
            ENTRADA RECIBIDA:
            %s

            INSTRUCCIONES:
            Actua estrictamente bajo tu rol de %s.
            """, input, template.roleName());
    }

    private void publishResult(SovereignEnvelope<?> original, String content) {
        var outputEnvelope = SovereignEnvelope.create(
            id,
            template.roleName(),
            original.traceId(),
            content
        ).withHeader("response_to", original.id());

        bus.publish(wiring.outputTopic(), outputEnvelope);
        logSuccess("Publicado en: " + wiring.outputTopic());
    }

    private void publishError(SovereignEnvelope<?> original, String error) {
        var errorEnvelope = SovereignEnvelope.create(
            id, "ERROR_HANDLER", original.traceId(), error
        ).withHeader("original_payload", original.payload().toString());

        bus.publish(wiring.deadLetterTopic(), errorEnvelope);
    }

    @FunctionalInterface
    public interface LlmInferenceProvider {
        String infer(String systemPrompt, String userPrompt);
    }
}
