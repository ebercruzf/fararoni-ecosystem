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
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class QuartermasterPrimeCommand implements ConsoleCommand {
    private static final Path QMP_YAML_PATH = Path.of(
        System.getProperty("user.home"),
        ".fararoni/config/agentes/qartermaster-prime-agent.yaml"
    );

    public QuartermasterPrimeCommand() {}

    @Override
    public String getTrigger() {
        return "/qmp";
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/qm-prime" };
    }

    @Override
    public String getDescription() {
        return "Sintetiza agentes, misiones y flujos DAG via QuarterMasterPrime";
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

        Object coreObj = ctx.getCore();
        if (coreObj == null) {
            System.out.println("[ERROR] Core no disponible.");
            return;
        }

        FararoniCore core = (FararoniCore) coreObj;

        String qmpSystemPrompt = loadQmpSystemPrompt();
        if (qmpSystemPrompt == null) {
            System.out.println("[QMP] QuarterMasterPrime no disponible.");
            System.out.println("Tip: Verifica que existe " + QMP_YAML_PATH);
            return;
        }

        System.out.println("[QMP] QuarterMasterPrime procesando solicitud...");
        System.out.flush();

        String response = core.chatAgenticWithSystemPrompt(
            qmpSystemPrompt, args
        );

        if (response != null && !response.isBlank()) {
            System.out.println(response);

            if (response.contains("id:") && response.contains("capabilities:")
                    && (response.contains("systemPrompt:") || response.contains("prompts:"))) {
                autoSaveAgent(response);
            }

            if (response.contains("missionId:") && response.contains("steps:")) {
                autoSaveMission(response);
            }
        } else {
            System.out.println("[QMP] No se obtuvo respuesta del LLM.");
        }
    }

    private void autoSaveAgent(String response) {
        try {
            String yamlBlock = extractAgentYaml(response);
            if (yamlBlock == null || yamlBlock.isBlank()) {
                System.out.println("[QMP] No se pudo extraer YAML valido para auto-guardar.");
                return;
            }

            Yaml yamlParser = new Yaml();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = yamlParser.load(yamlBlock);
            if (data == null || !data.containsKey("id")) {
                System.out.println("[QMP] YAML sin campo 'id'. No se auto-guardo.");
                return;
            }

            String agentId = data.get("id").toString();
            Path agentsDir = QMP_YAML_PATH.getParent();
            Files.createDirectories(agentsDir);

            Path targetFile = agentsDir.resolve(agentId + "-agent.yaml");

            if (Files.exists(targetFile)) {
                System.out.println("[QMP] Agente '" + agentId + "' ya existe en " + targetFile);
                System.out.println("[QMP] No se sobreescribio. Elimina el archivo manualmente si deseas reemplazarlo.");
                return;
            }

            Files.writeString(targetFile, yamlBlock);
            System.out.println();
            System.out.println("[QMP] Agente auto-guardado: " + targetFile);
            System.out.println("[QMP] Hot Reload lo activara automaticamente.");
        } catch (Exception e) {
            System.out.println("[QMP] Error auto-guardando agente: " + e.getMessage());
        }
    }

    private void autoSaveMission(String response) {
        try {
            String yamlBlock = extractMissionYaml(response);
            if (yamlBlock == null || yamlBlock.isBlank()) return;

            Yaml yamlParser = new Yaml();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = yamlParser.load(yamlBlock);
            if (data == null || !data.containsKey("missionId")) return;

            String missionId = data.get("missionId").toString();
            Path missionsDir = Path.of(
                System.getProperty("user.home"), ".fararoni", "config", "missions"
            );
            Files.createDirectories(missionsDir);

            Path targetFile = missionsDir.resolve("mission-" + missionId + ".yaml");

            if (Files.exists(targetFile)) {
                System.out.println("[QMP] Mision '" + missionId + "' ya existe en " + targetFile);
                return;
            }

            Files.writeString(targetFile, yamlBlock);
            System.out.println("[QMP] Mision auto-guardada: " + targetFile);
        } catch (Exception e) {
            System.out.println("[QMP] Error auto-guardando mision: " + e.getMessage());
        }
    }

    private String extractMissionYaml(String response) {
        String[] lines = response.split("\n");
        StringBuilder yaml = new StringBuilder();
        boolean inYaml = false;

        for (String line : lines) {
            if (line.matches("^\\s*```.*")) {
                if (inYaml) break;
                continue;
            }

            if (!inYaml && line.matches("^missionId:\\s+.*")) {
                inYaml = true;
            }

            if (inYaml) {
                if (line.matches("^(Diagrama|Ruta|Comando|#|grep|python3|bash).*") ||
                    line.matches("^\\s*->\\s*.*")) {
                    break;
                }
                yaml.append(line).append("\n");
            }
        }

        String result = yaml.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private String extractAgentYaml(String response) {
        String[] lines = response.split("\n");
        StringBuilder yaml = new StringBuilder();
        boolean inYaml = false;

        for (String line : lines) {
            if (line.matches("^\\s*```.*")) {
                if (inYaml) break;
                continue;
            }

            if (!inYaml && line.matches("^id:\\s+.*")) {
                inYaml = true;
            }

            if (inYaml) {
                if (line.matches("^(Diagrama|Ruta completa|Comando de|mission-|~/).*") ||
                    line.matches("^\\s*->\\s*.*") ||
                    line.matches("^(grep|python3|bash|#)\\s.*")) {
                    break;
                }
                yaml.append(line).append("\n");
            }
        }

        String result = yaml.toString().trim();
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private String loadQmpSystemPrompt() {
        if (!Files.exists(QMP_YAML_PATH)) return null;
        try {
            String content = Files.readString(QMP_YAML_PATH);
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(content);
            if (data == null) return null;
            Object prompt = data.get("systemPrompt");
            return prompt != null ? prompt.toString() : null;
        } catch (IOException e) {
            System.out.println("[QMP] Error leyendo " + QMP_YAML_PATH + ": " + e.getMessage());
            return null;
        }
    }

    private void printUsage() {
        System.out.println("""

            Uso: /qmp <solicitud en lenguaje natural>

            Aliases: /qm-prime

            QuarterMasterPrime es el arquitecto de doctrina. Te ayuda a:
            - Crear nuevos agentes YAML listos para Hot Reload
            - Disenar misiones DAG con flujos multi-agente
            - Sintetizar enjambres y protocolos de coordinacion

            Ejemplos:
              /qmp crea un agente para analizar logs de servidor
              /qmp diseña una mision para investigar noticias
              /qmp genera un agente sentinel para monitorear APIs
            """);
    }
}
