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
package dev.fararoni.core.core.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fararoni.core.core.clients.AgentClient;
import dev.fararoni.core.core.clients.AgentClient.AgentResponse;
import dev.fararoni.core.core.clients.AgentClient.ToolCall;
import dev.fararoni.core.core.skills.ToolExecutionResult;
import dev.fararoni.core.core.skills.ToolRegistry;
import dev.fararoni.core.core.tools.ContextMeasurer;
import dev.fararoni.core.faracore.FaraCoreLlmDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Ejecutor directo para agentes con allowedTools.
 *
 * <p>Camino desacoplado que NO pasa por FaraCoreRoutingEngine ni chatAgenticInternal.
 * Resuelve el cliente LLM directamente (DeepSeek > Claude > Ollama) y filtra
 * los tools por la lista de allowedTools del agente YAML.</p>
 *
 * <p>start_mission NUNCA se incluye en el tool set — los agentes directos
 * no pueden lanzar misiones del Swarm.</p>
 *
 * @author Eber Cruz
 * @version 1.2.0
 * @since 1.2.0
 */
public final class DirectAgentExecutor {

    private static final Logger LOG = Logger.getLogger(DirectAgentExecutor.class.getName());

    private final FaraCoreLlmDispatcher dispatcher;

    public DirectAgentExecutor(FaraCoreLlmDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Ejecuta un agente con su systemPrompt, filtrando tools por allowedTools.
     *
     * @param systemPrompt  prompt del agente (personalidad + reglas)
     * @param userMessage   mensaje del usuario
     * @param allowedTools  lista de nombres de tools permitidos (del YAML)
     * @return respuesta del LLM
     */
    public String execute(String systemPrompt, String userMessage, List<String> allowedTools) {
        AgentClient client = resolveClient();
        String clientName = client.getClass().getSimpleName();

        String projectContext = prefetchProjectContext();

        List<ObjectNode> tools = buildFilteredTools(allowedTools);

        String enrichedMessage = userMessage + "\n\n" +
            "══════════════════════════════════════════════════════\n" +
            "[CONTEXTO DEL PROYECTO — PRE-CARGADO POR EL SISTEMA]\n" +
            "══════════════════════════════════════════════════════\n" +
            projectContext +
            "\n══════════════════════════════════════════════════════\n";

        var measurer = new ContextMeasurer(systemPrompt + enrichedMessage);
        System.out.println("[DIRECT-AGENT] Client: " + clientName +
            " | Mode: PRE-FETCH+TOOLS | Context: " + (projectContext.length() / 1024) + "KB" +
            " | Tools: " + tools.size() + " | Tokens: ~" + measurer.estimatedTokens() +
            " | " + measurer.status());

        if (measurer.exceedsInputLimit()) {
            LOG.warning("[DIRECT-AGENT] Context exceeds 120K tokens! Truncating pre-fetch.");
            enrichedMessage = userMessage;
        }

        try {
            AgentResponse response = client.generateWithTools(systemPrompt, enrichedMessage, tools);

            if (response.isToolCall()) {
                return handleToolCallLoop(client, systemPrompt, enrichedMessage, tools, response);
            }

            return response.textContent() != null ? response.textContent()
                : "[DIRECT-AGENT] Sin respuesta de " + clientName;
        } catch (Exception e) {
            LOG.severe("[DIRECT-AGENT] Error: " + e.getMessage());
            return "[ERROR] DirectAgentExecutor: " + e.getMessage();
        }
    }

    /**
     * Pre-carga contexto clave del proyecto fararoni-core.
     * Lee archivos estrategicos (interfaces, configs, estructura) de forma quirurgica.
     * Maximo ~30KB de contexto para no saturar el context window.
     */
    private String prefetchProjectContext() {
        StringBuilder context = new StringBuilder();
        var fs = new java.io.File(System.getProperty("user.dir"));

        String[][] filesToRead = {
            {"pom.xml", "50"},
            {"fararoni-core/src/main/java/dev/fararoni/core/FararoniCore.java", "100"},
            {"fararoni-core/src/main/java/dev/fararoni/core/faracore/FaraCoreLlmDispatcher.java", "120"},
            {"fararoni-core/src/main/java/dev/fararoni/core/core/skills/ToolRegistry.java", "80"},
            {"fararoni-core/src/main/java/dev/fararoni/core/core/mission/engine/SovereignMissionEngine.java", "100"},
            {"fararoni-core/src/main/java/dev/fararoni/core/core/bus/InMemorySovereignBus.java", "80"},
            {"fararoni-core/src/main/java/dev/fararoni/core/core/agents/ReactiveSwarmAgent.java", "80"},
        };

        for (String[] entry : filesToRead) {
            String path = entry[0];
            int maxLines = Integer.parseInt(entry[1]);
            java.io.File file = new java.io.File(fs, path);

            if (file.exists()) {
                try {
                    var lines = java.nio.file.Files.readAllLines(file.toPath());
                    int linesToRead = Math.min(maxLines, lines.size());
                    context.append("\n--- ").append(path).append(" (lineas 1-").append(linesToRead)
                           .append(" de ").append(lines.size()).append(") ---\n");
                    for (int i = 0; i < linesToRead; i++) {
                        context.append(lines.get(i)).append("\n");
                    }
                } catch (Exception e) {
                    context.append("\n--- ").append(path).append(" [ERROR: ").append(e.getMessage()).append("] ---\n");
                }
            }
        }

        LOG.info("[DIRECT-AGENT] Pre-fetch context: " + (context.length() / 1024) + "KB, " + filesToRead.length + " archivos");
        return context.toString();
    }

    /**
     * Resuelve el cliente LLM en orden de prioridad:
     * DeepSeek (cloud) > Claude (cloud) > agenticClient (Ollama local)
     */
    private AgentClient resolveClient() {
        if (dispatcher.isDeepSeekPreferred() && dispatcher.getDeepSeekClient() != null) {
            return dispatcher.getDeepSeekClient();
        }
        if (dispatcher.isClaudePreferred() && dispatcher.getClaudeClient() != null) {
            return dispatcher.getClaudeClient();
        }
        return dispatcher.getAgenticClient();
    }

    /**
     * Construye la lista de tools filtrada por allowedTools.
     * start_mission se excluye SIEMPRE.
     */
    private List<ObjectNode> buildFilteredTools(List<String> allowedTools) {
        ToolRegistry registry = dispatcher.getToolRegistry();
        if (registry == null) {
            LOG.warning("[DIRECT-AGENT] ToolRegistry null — sin tools");
            return List.of();
        }

        List<ObjectNode> allTools = registry.getAvailableTools();
        List<ObjectNode> filtered = new ArrayList<>();

        for (ObjectNode tool : allTools) {
            String name = extractToolName(tool);
            if ("start_mission".equals(name)) continue;
            if (allowedTools == null || allowedTools.isEmpty() || allowedTools.contains(name)) {
                filtered.add(tool);
            }
        }

        LOG.info("[DIRECT-AGENT] Tools: " + filtered.size() + "/" + allTools.size() +
            " (allowed=" + allowedTools + ")");
        return filtered;
    }

    /**
     * Extrae el nombre del tool desde el ObjectNode (formato OpenAI).
     */
    private String extractToolName(ObjectNode tool) {
        if (tool.has("function") && tool.get("function").has("name")) {
            return tool.get("function").get("name").asText();
        }
        return "";
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Loop de tool calling con historial completo (OpenAI format).
     * Usa generateWithFullHistory para que el LLM vea los resultados
     * de tools anteriores como mensajes role:tool.
     * Maximo 5 iteraciones para evitar loops infinitos.
     */
    private String handleToolCallLoop(AgentClient client, String systemPrompt,
                                       String userMessage, List<ObjectNode> tools,
                                       AgentResponse response) {
        int maxIterations = 10;

        ArrayNode messages = MAPPER.createArrayNode();

        ObjectNode sysMsg = MAPPER.createObjectNode();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        ObjectNode userMsg = MAPPER.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        AgentResponse current = response;

        for (int i = 0; i < maxIterations && current.isToolCall(); i++) {
            var toolCall = current.toolCall();
            LOG.info("[DIRECT-AGENT] Tool call #" + (i + 1) + ": " + toolCall.functionName());

            ObjectNode assistantMsg = MAPPER.createObjectNode();
            assistantMsg.put("role", "assistant");
            assistantMsg.putNull("content");
            ArrayNode toolCalls = MAPPER.createArrayNode();
            ObjectNode tc = MAPPER.createObjectNode();
            tc.put("id", "call_" + i);
            tc.put("type", "function");
            ObjectNode fn = MAPPER.createObjectNode();
            fn.put("name", toolCall.functionName());
            fn.put("arguments", toolCall.argumentsJson());
            tc.set("function", fn);
            toolCalls.add(tc);
            assistantMsg.set("tool_calls", toolCalls);
            messages.add(assistantMsg);

            String result;
            try {
                ToolExecutionResult execResult = dispatcher.getToolExecutor().executeTool(toolCall);
                result = execResult.message();
            } catch (Exception e) {
                result = "[ERROR] Tool execution failed: " + e.getMessage();
                LOG.warning("[DIRECT-AGENT] Tool error: " + e.getMessage());
            }

            System.out.println("[DIRECT-AGENT] Tool: " + toolCall.functionName() +
                " → " + (result.length() > 100 ? result.substring(0, 100) + "..." : result));

            ObjectNode toolMsg = MAPPER.createObjectNode();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", "call_" + i);
            toolMsg.put("content", result);
            messages.add(toolMsg);

            try {
                current = client.generateWithFullHistory(messages, tools, "auto");
            } catch (Exception e) {
                return "[ERROR] Continuation failed: " + e.getMessage();
            }
        }

        if (current.isToolCall()) {
            LOG.info("[DIRECT-AGENT] Max iteraciones alcanzado. Forzando respuesta final sin tools.");
            System.out.println("[DIRECT-AGENT] Forzando respuesta final (max tools alcanzado)...");
            try {
                current = client.generateWithFullHistory(messages, List.of(), "none");
            } catch (Exception e) {
                LOG.warning("[DIRECT-AGENT] Error en respuesta forzada: " + e.getMessage());
            }
        }

        return current.textContent() != null ? current.textContent() : "[DIRECT-AGENT] Sin respuesta.";
    }
}
