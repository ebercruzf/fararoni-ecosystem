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
package dev.fararoni.core.core.gateway.routing;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.context.ContextManager;
import dev.fararoni.core.core.agents.AbstractSwarmAgent;
import dev.fararoni.core.core.gateway.CircuitBreaker;
import dev.fararoni.core.core.gateway.registry.ChannelRegistry;
import dev.fararoni.core.core.orchestrator.SovereignOrchestrator;
import dev.fararoni.core.core.persistence.spi.ConversationRepository;
import dev.fararoni.core.model.Message;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.security.auth.ISecurityInterceptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class OmniChannelRouter extends AbstractSwarmAgent {
    private static final Logger LOG = Logger.getLogger(OmniChannelRouter.class.getName());

    public static final String ID = "OMNI_CHANNEL_ROUTER";

    @Deprecated(since = "FASE 71.8", forRemoval = false)
    public static final List<String> INPUT_TOPICS = List.of(
        "agency.input.whatsapp",
        "agency.input.telegram",
        "agency.input.slack",
        "agency.input.discord",
        "agency.input.imessage",
        "agency.input.web"
    );

    public static final String OUTPUT_TOPIC = "agency.output.main";

    public static final String MISSION_TOPIC = "agency.mission.request";

    private static final Pattern GREETING_PATTERN = Pattern.compile(
        "^(hola|hey|hi|buenos? (dias?|tardes?|noches?)|saludos?|que tal|como estas?)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern THANKS_PATTERN = Pattern.compile(
        "^(gracias?|thanks?|thx|te lo agradezco|muchas gracias)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern FAREWELL_PATTERN = Pattern.compile(
        "^(adios|bye|hasta (luego|pronto|manana)|nos vemos|chao|chau)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern ACTION_VERBS = Pattern.compile(
        "\\b(crea|genera|implementa|desarrolla|escribe|modifica|edita|" +
        "analiza|revisa|optimiza|refactoriza|migra|actualiza|corrige|" +
        "busca|encuentra|lista|muestra|explica detalladamente|" +
        "disena|planifica|arquitectura|configura|despliega)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern TECHNICAL_OBJECTS = Pattern.compile(
        "\\b(clase|metodo|funcion|archivo|proyecto|base de datos|api|" +
        "endpoint|servicio|componente|modulo|paquete|repositorio|" +
        "\\.java|\\.py|\\.js|\\.ts|\\.go|\\.rs)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private final LlmInferenceProvider llmProvider;

    private final ConversationRepository conversationRepo;

    private final ContextManager contextManager;

    private final CircuitBreaker circuitBreaker;

    private final SystemPromptBuilder promptBuilder;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile boolean running = false;

    private volatile ISecurityInterceptor securityInterceptor;

    private static final java.util.Set<String> INTERNAL_PROTOCOLS = java.util.Set.of(
        "TERMINAL", "CLI", "TERMINAL_DEV", "INTELLIJ"
    );

    private long messagesProcessed = 0;
    private long simpleResponses = 0;
    private long complexResponses = 0;

    public OmniChannelRouter(
            SovereignEventBus bus,
            LlmInferenceProvider llmProvider,
            ConversationRepository conversationRepo,
            ContextManager contextManager,
            CircuitBreaker circuitBreaker) {
        super(ID, bus);
        this.llmProvider = llmProvider;
        this.conversationRepo = conversationRepo;
        this.contextManager = contextManager;
        this.circuitBreaker = circuitBreaker != null ? circuitBreaker : CircuitBreaker.Factory.standard();
        this.promptBuilder = new SystemPromptBuilder();
    }

    public OmniChannelRouter(SovereignEventBus bus, LlmInferenceProvider llmProvider) {
        this(bus, llmProvider, null, null, null);
    }

    public void setSecurityInterceptor(ISecurityInterceptor interceptor) {
        this.securityInterceptor = interceptor;
        LOG.info("[OmniChannel] Security Interceptor enganchado — canales externos requieren TOTP");
    }

    public void start() {
        if (running) {
            LOG.warning("[OmniChannel] Ya esta corriendo");
            return;
        }

        running = true;
        LOG.info("[OmniChannel] Iniciando router multi-canal...");

        List<String> topics = getActiveTopics();

        for (String topic : topics) {
            bus.subscribe(topic, String.class, this::onMessage);
            LOG.info("[OmniChannel] Suscrito a: " + topic);
        }

        LOG.info("[OmniChannel] Router multi-canal iniciado - " + topics.size() + " canales activos");
        logIdle("Esperando mensajes de cualquier canal...");
    }

    private List<String> getActiveTopics() {
        try {
            ChannelRegistry registry = ChannelRegistry.getInstance();

            if (registry.getActiveChannelCount() == 0) {
                registry.initialize();
            }

            List<String> topics = registry.getInputTopics();

            if (topics.isEmpty()) {
                LOG.warning("[OmniChannel] ChannelRegistry vacio, usando fallback INPUT_TOPICS");
                return INPUT_TOPICS;
            }

            LOG.info("[OmniChannel] Usando topics de ChannelRegistry: " + topics.size() + " canales");
            return topics;
        } catch (Exception e) {
            LOG.warning("[OmniChannel] Error accediendo ChannelRegistry: " + e.getMessage());
            LOG.warning("[OmniChannel] Usando fallback INPUT_TOPICS");
            return INPUT_TOPICS;
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        executor.shutdown();
        logAction("Detenido");
        LOG.info("[OmniChannel] Detenido - Mensajes: " + messagesProcessed +
                 " (Simple: " + simpleResponses + ", Complex: " + complexResponses + ")");
    }

    public boolean isRunning() {
        return running;
    }

    private void onMessage(SovereignEnvelope<String> envelope) {
        if (!running) return;

        executor.submit(() -> processMessage(envelope));
    }

    private void processMessage(SovereignEnvelope<String> envelope) {
        String text = envelope.payload();
        String protocol = envelope.headers().getOrDefault("X-Origin-Protocol", "UNKNOWN");
        String sender = extractSenderId(envelope);

        messagesProcessed++;

        LOG.info("[OmniChannel] [" + protocol + "] Procesando de " + sender + ": " + truncate(text, 50));
        logThinking("Clasificando mensaje de " + sender + " via " + protocol);

        if (securityInterceptor != null && !INTERNAL_PROTOCOLS.contains(protocol.toUpperCase())) {
            LOG.info("[OmniChannel] [SECURITY] Canal externo — pasando por interceptor TOTP");
            try {
                String securityResponse = securityInterceptor.processSecureRequest(protocol, sender, text);
                publishResponse(envelope, securityResponse, "Security");
                logSuccess("Respuesta de seguridad enviada");
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "[OmniChannel] [SECURITY] Error procesando mensaje seguro de " + sender, e);
                logError("Error en procesamiento seguro");
                publishResponse(envelope,
                    "Lo siento, ocurrio un error procesando tu mensaje. Intenta de nuevo.",
                    "SecurityError");
            }
            logIdle("Esperando mensajes de cualquier canal...");
            return;
        }

        try {
            if (detectActionIntent(text)) {
                LOG.info("[OmniChannel] [" + protocol + "] -> RUTA: Delegando a SovereignOrchestrator");
                logThinking("Delegando tarea compleja al Orchestrator...");
                complexResponses++;

                var missionEnvelope = SovereignEnvelope.create(
                    ID,
                    UUID.randomUUID().toString(),
                    text
                )
                .withHeader("X-Reply-Channel-Id", envelope.headers().get("X-Reply-Channel-Id"))
                .withHeader("X-Conversation-Id", envelope.headers().get("X-Conversation-Id"))
                .withHeader("X-Origin-Protocol", protocol)
                .withHeader("X-Sender-Id", sender)
                .withHeader("X-Original-Sender", sender)
                .withHeader("X-Callback-Url", envelope.headers().get("X-Callback-Url"))
                .withHeader("X-Intent", envelope.headers().get("X-Intent"))
                .withHeader("X-Requirement", "COMPLEX_ORCHESTRATION")
                .withHeader("X-Priority", "NORMAL")
                .withHeader("X-Message-Type", envelope.headers().getOrDefault("X-Message-Type", "text"))
                .withHeader("X-Sender-Phone", envelope.headers().get("X-Sender-Phone"));

                bus.publish(MISSION_TOPIC, missionEnvelope);

                LOG.info("[OmniChannel] Mision publicada en " + MISSION_TOPIC);
                logSuccess("Delegado al Orchestrator");
                logIdle("Esperando mensajes de cualquier canal...");
            } else {
                LOG.info("[OmniChannel] [" + protocol + "] -> RUTA: Respuesta simple (local)");
                logThinking("Generando respuesta rapida...");
                simpleResponses++;

                String response = generateSimpleResponse(text, protocol, sender);

                publishResponse(envelope, response, "Simple");
                logSuccess("Respuesta enviada");
                logIdle("Esperando mensajes de cualquier canal...");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[OmniChannel] Error procesando mensaje", e);
            logError("Error procesando mensaje");
            publishResponse(envelope,
                "Lo siento, ocurrio un error procesando tu mensaje. Intenta de nuevo.",
                "Error"
            );
            logIdle("Esperando mensajes de cualquier canal...");
        }
    }

    private String generateSimpleResponse(String userMessage, String protocol, String senderId) {
        if (userMessage != null && userMessage.matches(AppDefaults.IDENTITY_TRIGGER_REGEX)) {
            LOG.info("[OmniChannel] Identity reflex triggered: " + userMessage);
            String identityPlain = AppDefaults.IDENTITY_RESPONSE.replaceAll("\u001B\\[[0-9;]*m", "");
            if (conversationRepo != null) {
                String sessionId = generateSessionId(protocol, senderId);
                executor.submit(() -> {
                    conversationRepo.saveMessage(sessionId, Message.user(userMessage));
                    conversationRepo.saveMessage(sessionId, Message.assistant(identityPlain));
                });
            }
            return identityPlain;
        }

        if (userMessage != null && userMessage.matches(AppDefaults.CREATOR_TRIGGER_REGEX)) {
            LOG.info("[OmniChannel] Creator bio reflex triggered: " + userMessage);
            String bioRaw = AppDefaults.CREATOR_RESPONSE_HEADER + AppDefaults.CREATOR_BIO;
            String bioPlain = bioRaw.replaceAll("\u001B\\[[0-9;]*m", "");
            if (conversationRepo != null) {
                String sessionId = generateSessionId(protocol, senderId);
                executor.submit(() -> {
                    conversationRepo.saveMessage(sessionId, Message.user(userMessage));
                    conversationRepo.saveMessage(sessionId, Message.assistant(bioPlain));
                });
            }
            return bioPlain;
        }

        if (conversationRepo == null || contextManager == null) {
            return generateSimpleResponseLegacy(userMessage, protocol);
        }

        String sessionId = generateSessionId(protocol, senderId);

        return circuitBreaker.executeWithFallback(
            () -> {
                List<Message> history = conversationRepo.getHistory(sessionId, 10);

                String systemPrompt = promptBuilder.buildSystemPrompt(protocol);

                String fullPrompt = contextManager.assemblePrompt(
                    systemPrompt,
                    List.of(),
                    history,
                    userMessage
                );

                String response = llmProvider.infer(fullPrompt, "");

                executor.submit(() -> {
                    try {
                        conversationRepo.saveMessage(sessionId, Message.user(userMessage));
                        conversationRepo.saveMessage(sessionId, Message.assistant(response));
                        LOG.fine(() -> "[OmniChannel] Historial guardado para " + sessionId);
                    } catch (Exception e) {
                        LOG.warning("[OmniChannel] Error guardando historial: " + e.getMessage());
                    }
                });

                return response;
            },
            () -> {
                LOG.warning("[OmniChannel] CircuitBreaker fallback activado para " + sessionId);
                return generateSimpleResponseLegacy(userMessage, protocol);
            }
        );
    }

    private String generateSessionId(String protocol, String senderId) {
        String prefix = (protocol != null && protocol.length() >= 3)
            ? protocol.toUpperCase().substring(0, 3)
            : "UNK";
        String id = (senderId != null && !senderId.isBlank())
            ? senderId
            : "ANONYMOUS";
        return prefix + "_" + id;
    }

    private String generateSimpleResponseLegacy(String userMessage, String protocol) {
        String systemPrompt = (promptBuilder != null)
            ? promptBuilder.buildSystemPrompt(protocol)
            : buildLegacyPrompt(protocol);

        return llmProvider.infer(systemPrompt, userMessage);
    }

    private String buildLegacyPrompt(String protocol) {
        String channelHint = switch (protocol) {
            case "WHATSAPP" -> "via WhatsApp";
            case "TELEGRAM" -> "via Telegram";
            case "SLACK" -> "en Slack";
            case "DISCORD" -> "en Discord";
            case "IMESSAGE" -> "via iMessage";
            default -> "via mensajeria";
        };

        return """
            Eres un asistente amigable %s.
            Responde de forma breve, natural y conversacional.
            Usa un tono informal pero profesional.
            Maximo 2-3 oraciones.
            """.formatted(channelHint);
    }

    private boolean detectActionIntent(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        String normalized = input.toLowerCase().trim();

        if (GREETING_PATTERN.matcher(normalized).find() && normalized.length() < 30) {
            return false;
        }

        if (THANKS_PATTERN.matcher(normalized).find()) {
            return false;
        }

        if (FAREWELL_PATTERN.matcher(normalized).find()) {
            return false;
        }

        if (normalized.length() < 20 && !TECHNICAL_OBJECTS.matcher(normalized).find()) {
            return false;
        }

        if (ACTION_VERBS.matcher(normalized).find()) {
            return true;
        }

        if (TECHNICAL_OBJECTS.matcher(normalized).find()) {
            return true;
        }

        return normalized.length() > 100;
    }

    private void publishResponse(SovereignEnvelope<String> original, String response, String route) {
        String protocol = original.headers().getOrDefault("X-Origin-Protocol", "UNKNOWN");

        System.out.println("[OmniChannel] publishResponse() - " + protocol);
        System.out.println("[OmniChannel]    X-Reply-Channel-Id: " + original.headers().get("X-Reply-Channel-Id"));

        var responseEnvelope = SovereignEnvelope.create(
            ID,
            UUID.randomUUID().toString(),
            response
        )
        .withHeader("X-Origin-Protocol", protocol)
        .withHeader("X-Reply-Channel-Id", original.headers().get("X-Reply-Channel-Id"))
        .withHeader("X-Conversation-Id", original.headers().get("X-Conversation-Id"))
        .withHeader("X-Route-Used", route)
        .withHeader("X-Original-Sender", extractSenderId(original))
        .withHeader("X-Callback-Url", original.headers().get("X-Callback-Url"))
        .withHeader("X-Intent", original.headers().get("X-Intent"));

        bus.publish(OUTPUT_TOPIC, responseEnvelope);
        LOG.fine("[OmniChannel] Respuesta publicada en " + OUTPUT_TOPIC + " para " + protocol);
    }

    private String extractSenderId(SovereignEnvelope<String> envelope) {
        var headers = envelope.headers();
        String sender = headers.get("X-Sender-Phone");
        if (sender == null || sender.isBlank()) {
            sender = headers.get("X-Sender-Id");
        }
        if (sender == null || sender.isBlank()) {
            sender = headers.get("X-Original-Sender");
        }
        return sender != null ? sender : "unknown";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    @FunctionalInterface
    public interface LlmInferenceProvider {
        String infer(String systemPrompt, String userPrompt);
    }
}
