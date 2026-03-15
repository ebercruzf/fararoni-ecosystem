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

import dev.fararoni.bus.agent.api.command.CommandCategory;
import dev.fararoni.bus.agent.api.command.ConsoleCommand;
import dev.fararoni.bus.agent.api.command.ExecutionContext;
import dev.fararoni.core.FararoniCore;
import dev.fararoni.core.core.mission.engine.AgentTemplateManager;
import dev.fararoni.core.core.mission.model.AgentTemplate;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class AgentCommand implements ConsoleCommand {
    private static final Path AGENTS_DIR = Path.of(
        System.getProperty("user.home"), ".fararoni", "config", "agentes"
    );

    public AgentCommand() {}

    @Override
    public String getTrigger() {
        return "/agent";
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/ag" };
    }

    @Override
    public String getDescription() {
        return "Invoca un agente dinamico por nombre: /agent <id> <mensaje>";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.ENTERPRISE;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        if (args == null || args.isBlank()) {
            printUsage();
            return;
        }

        String[] parts = args.trim().split("\\s+", 2);
        String agentId = parts[0].toLowerCase();
        String message = parts.length > 1 ? parts[1] : null;

        if (message == null || message.isBlank()) {
            System.out.println("[AGENT] Falta el mensaje. Uso: /agent " + agentId + " <tu mensaje>");
            return;
        }

        Object coreObj = ctx.getCore();
        if (coreObj == null) {
            System.out.println("[ERROR] Core no disponible.");
            return;
        }
        FararoniCore core = (FararoniCore) coreObj;

        if (executeViaRegistry(core, agentId, message)) {
            return;
        }

        String systemPrompt = loadAgentSystemPrompt(agentId);
        if (systemPrompt == null) {
            System.out.println("[AGENT] Agente '" + agentId + "' no encontrado.");
            listAvailableAgents();
            return;
        }

        String agentName = loadAgentField(agentId, "name");
        String displayName = agentName != null ? agentName : agentId;

        System.out.println("[" + displayName.toUpperCase() + "] Procesando solicitud...");
        System.out.flush();

        String response = core.chatWithSystemPrompt(
            systemPrompt, message, "AGENT-" + agentId + "-" + System.nanoTime()
        );

        if (response != null && !response.isBlank()) {
            System.out.println();
            System.out.println("[" + displayName.toUpperCase() + "]");
            System.out.println(response);
        } else {
            System.out.println("[" + displayName.toUpperCase() + "] No se obtuvo respuesta.");
        }
    }

    private boolean executeViaRegistry(FararoniCore core, String agentId, String message) {
        AgentTemplateManager atm = core.getAgentTemplateManager();
        if (atm == null) return false;

        AgentTemplate template = atm.getTemplate(agentId);
        if (template == null) return false;

        String systemPrompt = template.systemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) return false;

        String displayName = template.name() != null ? template.name() : agentId;

        AgentChatStrategy strategy = resolveRegistryStrategy(core, template);

        System.out.println("[" + displayName.toUpperCase() + "] Procesando solicitud...");
        if (template.capabilities() != null && !template.capabilities().isEmpty()) {
            System.out.println("[" + displayName.toUpperCase() + "] Capabilities: " +
                String.join(", ", template.capabilities()));
        }
        System.out.flush();

        String response = strategy.chat(systemPrompt, message);

        if (response != null && !response.isBlank()) {
            System.out.println();
            System.out.println("[" + displayName.toUpperCase() + "]");
            System.out.println(response);
        } else {
            System.out.println("[" + displayName.toUpperCase() + "] No se obtuvo respuesta.");
        }

        return true;
    }

    private AgentChatStrategy resolveRegistryStrategy(FararoniCore core, AgentTemplate template) {
        String mode = "agentic";
        if (template.metadata() != null) {
            Object modeObj = template.metadata().get("executionMode");
            if (modeObj != null) mode = modeObj.toString().trim().toLowerCase();
        }

        return switch (mode) {
            case "simple" -> {
                String traceId = "AGENT-" + template.id() + "-" + System.nanoTime();
                yield (sp, msg) -> core.chatWithSystemPrompt(sp, msg, traceId);
            }
            default -> (sp, msg) -> core.chatAgenticWithSystemPrompt(sp, msg);
        };
    }

    @SuppressWarnings("unchecked")
    private String loadAgentSystemPrompt(String agentId) {
        Path yamlPath = resolveAgentPath(agentId);
        if (yamlPath == null) return null;

        try {
            String content = Files.readString(yamlPath);
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(content);
            if (data == null) return null;

            Object prompt = data.get("systemPrompt");
            if (prompt != null) return prompt.toString();

            Object prompts = data.get("prompts");
            if (prompts instanceof Map) {
                Map<String, Object> promptMap = (Map<String, Object>) prompts;
                for (Object value : promptMap.values()) {
                    if (value != null) return value.toString();
                }
            }

            return null;
        } catch (IOException e) {
            System.out.println("[AGENT] Error leyendo " + yamlPath + ": " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String loadAgentField(String agentId, String field) {
        Path yamlPath = resolveAgentPath(agentId);
        if (yamlPath == null) return null;

        try {
            String content = Files.readString(yamlPath);
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(content);
            if (data == null) return null;
            Object value = data.get(field);
            return value != null ? value.toString() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private Path resolveAgentPath(String agentId) {
        if (!Files.exists(AGENTS_DIR)) return null;

        Path path = AGENTS_DIR.resolve(agentId + "-agent.yaml");
        if (Files.exists(path)) return path;

        path = AGENTS_DIR.resolve(agentId + "-agent.yml");
        if (Files.exists(path)) return path;

        path = AGENTS_DIR.resolve(agentId + ".yaml");
        if (Files.exists(path)) return path;

        return null;
    }

    private void listAvailableAgents() {
        if (!Files.exists(AGENTS_DIR)) {
            System.out.println("[AGENT] No hay directorio de agentes.");
            return;
        }

        System.out.println("[AGENT] Agentes disponibles:");
        try (Stream<Path> files = Files.list(AGENTS_DIR)) {
            files.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                 .sorted()
                 .forEach(p -> {
                     String name = p.getFileName().toString()
                         .replace("-agent.yaml", "")
                         .replace("-agent.yml", "")
                         .replace(".yaml", "")
                         .replace(".yml", "");
                     System.out.println("  - " + name);
                 });
        } catch (IOException e) {
            System.out.println("[AGENT] Error listando agentes: " + e.getMessage());
        }
    }

    private void printUsage() {
        System.out.println("""

            Uso: /agent <id> <mensaje>

            Alias: /ag

            Invoca cualquier agente dinamico por su ID.
            El agente responde usando su personalidad y reglas definidas en YAML.

            Ejemplos:
              /agent agentmail revisa mi correo
              /agent sentinel audita este archivo
              /agent blueprint diseña la arquitectura
              /ag agentmail dame un resumen

            Agentes disponibles:""");
        listAvailableAgents();
    }
}
