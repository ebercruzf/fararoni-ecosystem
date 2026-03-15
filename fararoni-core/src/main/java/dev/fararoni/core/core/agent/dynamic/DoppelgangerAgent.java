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
import dev.fararoni.bus.agent.api.security.HmacMessageSigner;
import dev.fararoni.core.core.agent.model.AgentInstanceConfig;
import dev.fararoni.core.core.agent.model.AgentTemplate;
import dev.fararoni.core.core.agent.validation.JsonSchemaValidator;
import dev.fararoni.core.core.resilience.IdempotencyFilter;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class DoppelgangerAgent implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(DoppelgangerAgent.class.getName());

    private final AgentInstanceConfig config;

    private final AgentTemplate template;

    private final SovereignEventBus bus;

    private final LlmInferenceProvider inferenceProvider;

    private final String outputSchema;

    private final AtomicBoolean active = new AtomicBoolean(false);

    private final AtomicLong messagesProcessed = new AtomicLong(0);

    private final AtomicLong errorsCount = new AtomicLong(0);

    private final IdempotencyFilter idempotencyFilter = new IdempotencyFilter();

    public DoppelgangerAgent(
            AgentInstanceConfig config,
            AgentTemplate template,
            SovereignEventBus bus,
            LlmInferenceProvider inferenceProvider
    ) {
        this.config = config;
        this.template = template;
        this.bus = bus;
        this.inferenceProvider = inferenceProvider;
        this.outputSchema = template.hasOutputSchema()
            ? template.outputJsonSchema()
            : null;

        LOG.info(() -> String.format(
            "[DoppelgangerAgent] Creado: id=%s, template=%s, inputs=%s, output=%s",
            config.id(), template.templateId(),
            config.wiring().inputTopics(), config.wiring().outputTopic()
        ));
    }

    public void activate() {
        if (active.getAndSet(true)) {
            LOG.warning(() -> "[DoppelgangerAgent] " + config.id() + " ya esta activo");
            return;
        }

        List<String> inputTopics = config.wiring().inputTopics();
        if (inputTopics.isEmpty()) {
            LOG.warning(() -> "[DoppelgangerAgent] " + config.id() + " no tiene topics de entrada");
            return;
        }

        for (String topic : inputTopics) {
            subscribeToTopic(topic);
        }

        LOG.info(() -> String.format(
            "[DoppelgangerAgent] %s activado, escuchando %d topics",
            config.id(), inputTopics.size()
        ));
    }

    public void deactivate() {
        if (!active.getAndSet(false)) {
            return;
        }

        LOG.info(() -> String.format(
            "[DoppelgangerAgent] %s desactivado. Procesados: %d, Errores: %d",
            config.id(), messagesProcessed.get(), errorsCount.get()
        ));
    }

    public void processSecureMessage(SovereignEnvelope<String> envelope) {
        try {
            if (!idempotencyFilter.tryProcess(envelope.id())) {
                LOG.fine(() -> "[DoppelgangerAgent] Mensaje duplicado ignorado: " + envelope.id());
                return;
            }

            if (!verifySignature(envelope)) {
                sendToDeadLetter(envelope, "Firma HMAC invalida");
                return;
            }

            if (envelope.isMaxHopsExceeded()) {
                sendToDeadLetter(envelope, "Max hops excedidos: " + envelope.hopCount());
                return;
            }

            String response = executeLlm(envelope.payload());

            if (outputSchema != null) {
                List<String> errors = JsonSchemaValidator.getInstance().validate(response, outputSchema);
                if (!errors.isEmpty()) {
                    LOG.warning(() -> "[DoppelgangerAgent] Schema invalido: " + errors);
                    sendToDeadLetter(envelope, "Schema invalido: " + errors);
                    return;
                }
            }

            publishResponse(envelope, response);
            messagesProcessed.incrementAndGet();
        } catch (Exception e) {
            errorsCount.incrementAndGet();
            LOG.log(Level.WARNING, "[DoppelgangerAgent] Error procesando mensaje", e);
            sendToDeadLetter(envelope, "Error: " + e.getMessage());
        }
    }

    public String getId() {
        return config.id();
    }

    public String getTemplateId() {
        return template.templateId();
    }

    public boolean isActive() {
        return active.get();
    }

    public long getMessagesProcessed() {
        return messagesProcessed.get();
    }

    public long getErrorsCount() {
        return errorsCount.get();
    }

    public AgentInstanceConfig getConfig() {
        return config;
    }

    public AgentTemplate getTemplate() {
        return template;
    }

    private void subscribeToTopic(String topic) {
        bus.subscribe(topic, String.class, (SovereignEnvelope<String> envelope) -> {
            if (active.get()) {
                try {
                    processSecureMessage(envelope);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[DoppelgangerAgent] Error procesando mensaje en " + topic, e);
                    errorsCount.incrementAndGet();
                }
            }
        });
    }

    private boolean verifySignature(SovereignEnvelope<String> envelope) {
        if (envelope.signature() == null || envelope.signature().isBlank()) {
            LOG.fine(() -> "[DoppelgangerAgent] Mensaje sin firma, aceptando (modo permisivo)");
            return true;
        }

        return HmacMessageSigner.verify(
            envelope.payload(),
            envelope.timestamp().toEpochMilli(),
            envelope.signature()
        );
    }

    private String executeLlm(String userMessage) {
        String fullPrompt = buildPrompt(userMessage);

        if (inferenceProvider != null) {
            return inferenceProvider.infer(template.systemPrompt(), fullPrompt);
        }

        return "{\"error\": \"No LLM inference provider available\", \"input\": \"" +
               userMessage.replace("\"", "\\\"") + "\"}";
    }

    private String buildPrompt(String userMessage) {
        StringBuilder prompt = new StringBuilder();

        if (!config.variables().isEmpty()) {
            prompt.append("Variables de contexto:\n");
            config.variables().forEach((k, v) ->
                prompt.append("- ").append(k).append(": ").append(v).append("\n")
            );
            prompt.append("\n");
        }

        prompt.append("Mensaje a procesar:\n");
        prompt.append(userMessage);

        if (template.hasOutputSchema()) {
            prompt.append("\n\nIMPORTANTE: Responde SOLO con JSON valido que cumpla el schema definido.");
        }

        return prompt.toString();
    }

    private void publishResponse(SovereignEnvelope<String> original, String response) {
        SovereignEnvelope<String> responseEnvelope = SovereignEnvelope.create(
            config.id(),
            template.roleName(),
            original.traceId(),
            response
        );

        if (original.correlationId() != null) {
            responseEnvelope = responseEnvelope.withCorrelation(original.correlationId());
        }

        String signature = HmacMessageSigner.sign(
            response,
            responseEnvelope.timestamp().toEpochMilli()
        );
        responseEnvelope = responseEnvelope.withSignature(signature);

        try {
            responseEnvelope = responseEnvelope.incrementHop();
        } catch (IllegalStateException e) {
            LOG.warning(() -> "[DoppelgangerAgent] Max hops en respuesta: " + e.getMessage());
            return;
        }

        final SovereignEnvelope<String> finalEnvelope = responseEnvelope;
        bus.publish(config.wiring().outputTopic(), finalEnvelope);

        LOG.fine(() -> String.format(
            "[DoppelgangerAgent] Respuesta publicada: %s -> %s (hop=%d)",
            config.id(), config.wiring().outputTopic(), finalEnvelope.hopCount()
        ));
    }

    private void sendToDeadLetter(SovereignEnvelope<String> envelope, String reason) {
        String dlqTopic = config.wiring().deadLetterTopic();

        SovereignEnvelope<String> dlqEnvelope = envelope
            .withHeader("dlq.reason", reason)
            .withHeader("dlq.agent", config.id())
            .withHeader("dlq.timestamp", String.valueOf(System.currentTimeMillis()))
            .withFinalIdempotencyKey(dlqTopic);

        bus.publish(dlqTopic, dlqEnvelope);
        errorsCount.incrementAndGet();

        LOG.warning(() -> String.format(
            "[DoppelgangerAgent] Mensaje a DLQ: %s | Razon: %s",
            envelope.id(), reason
        ));
    }

    @Override
    public void close() {
        deactivate();
    }
}
