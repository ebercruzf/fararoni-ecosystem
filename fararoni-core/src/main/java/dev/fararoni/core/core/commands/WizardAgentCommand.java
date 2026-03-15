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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import dev.fararoni.bus.agent.api.command.CommandCategory;
import dev.fararoni.bus.agent.api.command.ConsoleCommand;
import dev.fararoni.bus.agent.api.command.ExecutionContext;
import dev.fararoni.core.core.agent.loader.YamlAgentLoader;
import dev.fararoni.core.core.agent.model.AgentInstanceConfig;
import dev.fararoni.core.core.agent.model.AgentInstanceConfig.RoutingConfig;
import dev.fararoni.core.core.agent.model.AgentInstanceConfig.WiringConfig;
import dev.fararoni.core.core.agent.model.AgentTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class WizardAgentCommand implements ConsoleCommand {
    private static final String BASE_DIR = System.getProperty("user.home") + "/.fararoni/config";

    private static final Path TEMPLATES_DIR = Path.of(BASE_DIR, "templates");

    private static final Path INSTANCES_DIR = Path.of(BASE_DIR, "instances");

    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{2,30}$");

    private static final List<String> RESERVED_IDS = List.of(
        "SYSTEM", "ADMIN", "ROOT", "BROADCAST", "HIVEMIND", "COMMANDER"
    );

    private final ObjectMapper yamlMapper;

    public WizardAgentCommand() {
        YAMLFactory yamlFactory = new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);

        this.yamlMapper = new ObjectMapper(yamlFactory)
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public String getTrigger() {
        return "/wizard";
    }

    @Override
    public String getDescription() {
        return "Wizard transaccional para crear y desplegar agentes dinamicos";
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

        String subcommand = args.trim().toLowerCase();

        try {
            Files.createDirectories(TEMPLATES_DIR);
            Files.createDirectories(INSTANCES_DIR);

            switch (subcommand) {
                case "create-agent" -> runCreateAgentTransaction(ctx);
                case "list" -> listAgents();
                default -> printUsage();
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Fallo critico: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void runCreateAgentTransaction(ExecutionContext ctx) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║       WIZARD ENTERPRISE: Despliegue de Agente            ║");
        System.out.println("║                    FASE 44.2                             ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        String agentId = promptSafeId("ID Unico del Agente (ej: RISK_L1, ANALISTA_LATAM)");
        if (agentId == null) {
            System.out.println("[CANCELADO] Operacion abortada por el usuario.");
            return;
        }

        if (exists(agentId)) {
            System.out.println("[ERROR] El agente '" + agentId + "' ya existe en el sistema.");
            System.out.println("        Usa otro ID o elimina los archivos existentes:");
            System.out.println("        - " + TEMPLATES_DIR.resolve(agentId + "-TPL.yaml"));
            System.out.println("        - " + INSTANCES_DIR.resolve(agentId + ".yaml"));
            return;
        }

        String roleName = promptRequired("Nombre del Rol (ej: RISK_ANALYST, CODE_REVIEWER)");
        if (roleName == null) return;
        roleName = roleName.toUpperCase().replace(" ", "_");

        System.out.println();
        System.out.println("System Prompt (Personalidad/Instrucciones del Agente):");
        System.out.println("(Escribe multiples lineas. Termina con una linea vacia)");
        System.out.println("─".repeat(60));
        String systemPrompt = readMultiLine();
        if (systemPrompt.isBlank()) {
            System.out.println("[ERROR] System Prompt no puede estar vacio.");
            return;
        }

        String capsInput = promptRequired("Capacidades (separadas por coma, ej: SEARCH,CODE,ANALYZE)");
        if (capsInput == null) return;
        List<String> capabilities = Arrays.stream(capsInput.split(","))
            .map(String::trim)
            .map(String::toUpperCase)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toList());

        System.out.println();
        System.out.println("─".repeat(60));
        System.out.println("Construyendo objetos en memoria...");

        try {
            String templateId = agentId + "-TPL";
            AgentTemplate template = new AgentTemplate(
                templateId,
                roleName,
                systemPrompt,
                null,
                capabilities,
                Map.of(
                    "created_at", LocalDate.now().toString(),
                    "author", System.getProperty("user.name"),
                    "version", "1.0.0",
                    "generator", "wizard-cli-v2"
                )
            );
            System.out.println("[OK] Template construido: " + templateId);

            String inputTopic = "agency.input." + agentId.toLowerCase();
            String outputTopic = "agency.output.main";
            String dlqTopic = "sys.dlq." + agentId.toLowerCase();

            WiringConfig wiring = new WiringConfig(
                List.of(inputTopic),
                outputTopic,
                dlqTopic
            );
            System.out.println("[OK] Wiring configurado: " + inputTopic);

            AgentInstanceConfig instance = new AgentInstanceConfig(
                agentId,
                template.templateId(),
                wiring,
                RoutingConfig.defaults(),
                Map.of("ENV", "PRODUCTION", "CREATED_BY", "wizard")
            );
            System.out.println("[OK] Instance construida: " + agentId);

            System.out.println();
            System.out.println("═".repeat(60));
            System.out.println("INICIANDO TRANSACCION DE ESCRITURA");
            System.out.println("═".repeat(60));

            Path templatePath = TEMPLATES_DIR.resolve(template.templateId() + ".yaml");
            Path instancePath = INSTANCES_DIR.resolve(instance.id() + ".yaml");

            yamlMapper.writeValue(templatePath.toFile(), template);
            System.out.println("[1/2] Template persistido: " + templatePath.getFileName());

            System.out.println();
            String deploy = promptRequired("¿Desplegar instancia activa ahora? (s/n)");

            if (deploy != null && ("s".equalsIgnoreCase(deploy) || "si".equalsIgnoreCase(deploy) || "y".equalsIgnoreCase(deploy))) {
                try {
                    yamlMapper.writeValue(instancePath.toFile(), instance);
                    System.out.println("[2/2] Instancia desplegada: " + instancePath.getFileName());

                    System.out.println();
                    System.out.println("╔══════════════════════════════════════════════════════════╗");
                    System.out.println("║                    ✓ EXITO                               ║");
                    System.out.println("╚══════════════════════════════════════════════════════════╝");
                    System.out.println();
                    System.out.println("Agente '" + agentId + "' desplegado y activo.");
                    System.out.println();
                    System.out.println("Para asignar tareas usa:");
                    System.out.println("  /task " + agentId + " <tu mensaje>");
                    System.out.println();
                    System.out.println("El agente escucha en: " + inputTopic);
                    System.out.println();
                } catch (Exception e) {
                    System.out.println();
                    System.out.println("[ERROR] Fallo al escribir instancia: " + e.getMessage());
                    System.out.println("[ROLLBACK] Eliminando template para mantener consistencia...");
                    Files.deleteIfExists(templatePath);
                    System.out.println("[ROLLBACK] Template eliminado. Sistema limpio.");
                    throw new RuntimeException("Transaccion revertida. Verifique permisos.", e);
                }
            } else {
                System.out.println();
                System.out.println("[INFO] Instancia NO desplegada. Solo se guardo el Template.");
                System.out.println("       Para desplegar manualmente, crea el archivo:");
                System.out.println("       " + instancePath);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("[ERROR] Datos invalidos: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[ERROR] Error en transaccion: " + e.getMessage());
        }

        System.out.println();
    }

    private String promptSafeId(String prompt) {
        int attempts = 0;
        while (attempts < 5) {
            System.out.print(prompt + ": ");
            String input = readLine();

            if (input == null || input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("q")) {
                return null;
            }

            input = input.trim().toUpperCase().replace(" ", "_").replace("-", "_");

            if (input.isEmpty()) {
                attempts++;
                continue;
            }

            if (RESERVED_IDS.contains(input)) {
                System.out.println("[ERROR] '" + input + "' es una palabra reservada del sistema.");
                System.out.println("        IDs reservados: " + String.join(", ", RESERVED_IDS));
                attempts++;
                continue;
            }

            if (SAFE_ID_PATTERN.matcher(input).matches()) {
                return input;
            }

            System.out.println("[ERROR] ID invalido. Requisitos:");
            System.out.println("        - Solo letras A-Z, numeros 0-9 y guion bajo _");
            System.out.println("        - Debe empezar con letra");
            System.out.println("        - Minimo 3 caracteres, maximo 31");
            System.out.println("        Ejemplo: RISK_L1, ANALISTA_LATAM, CODE_REVIEWER_V2");
            attempts++;
        }

        System.out.println("[ERROR] Demasiados intentos fallidos.");
        return null;
    }

    private String promptRequired(String prompt) {
        int attempts = 0;
        while (attempts < 3) {
            System.out.print(prompt + ": ");
            String input = readLine();

            if (input == null || input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("q")) {
                return null;
            }

            input = input.trim();
            if (!input.isEmpty()) {
                return input;
            }

            System.out.println("[ERROR] Este campo es obligatorio.");
            attempts++;
        }

        System.out.println("[ERROR] Campo obligatorio no proporcionado.");
        return null;
    }

    private String readMultiLine() {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = readLine()) != null && !line.isBlank()) {
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    private String readLine() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = reader.readLine();
            return line != null ? line : "";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean exists(String agentId) {
        String templateFile = agentId + "-TPL.yaml";
        String instanceFile = agentId + ".yaml";
        return Files.exists(TEMPLATES_DIR.resolve(templateFile)) ||
               Files.exists(INSTANCES_DIR.resolve(instanceFile));
    }

    private void listAgents() {
        YamlAgentLoader loader = new YamlAgentLoader();
        loader.scanAll();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║              AGENTES CONFIGURADOS                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        System.out.println();
        System.out.println("─── TEMPLATES (Alma) ───────────────────────────────────────");
        var templates = loader.getAllTemplates();
        if (templates.isEmpty()) {
            System.out.println("  (ninguno)");
        } else {
            templates.forEach((id, template) -> {
                System.out.println("  • " + id + " [" + template.roleName() + "]");
                if (!template.capabilities().isEmpty()) {
                    System.out.println("    Caps: " + String.join(", ", template.capabilities()));
                }
            });
        }

        System.out.println();
        System.out.println("─── INSTANCES (Cuerpo) ─────────────────────────────────────");
        var instances = loader.getAllInstances();
        if (instances.isEmpty()) {
            System.out.println("  (ninguno)");
            System.out.println();
            System.out.println("  [!] Sin instances, los agentes NO pueden recibir tareas.");
            System.out.println("      Usa '/wizard create-agent' para crear uno completo.");
        } else {
            instances.forEach((id, instance) -> {
                System.out.println("  • " + id + " -> " + instance.templateRef());
                System.out.println("    Input: " + instance.wiring().inputTopics());
            });
        }

        if (!loader.getLoadErrors().isEmpty()) {
            System.out.println();
            System.out.println("─── ERRORES DE CARGA ───────────────────────────────────────");
            loader.getLoadErrors().forEach(err ->
                System.out.println("  [!] " + err.toString())
            );
        }

        System.out.println();
    }

    private void printUsage() {
        System.out.println("""

            ╔══════════════════════════════════════════════════════════╗
            ║                  /wizard - Ayuda                         ║
            ╚══════════════════════════════════════════════════════════╝

            Uso: /wizard <subcomando>

            Subcomandos:
              create-agent  Crea un nuevo agente (template + instance)
              list          Lista templates e instances existentes

            Ejemplo:
              /wizard create-agent

            Notas:
              - El wizard crea AMBOS archivos (template e instance)
              - ConfigSentinel detecta automaticamente nuevos agentes
              - Usa '/task <AGENT_ID> mensaje' para asignar tareas

            Directorios:
              Templates:  ~/.fararoni/config/templates/
              Instances:  ~/.fararoni/config/instances/
            """);
    }
}
