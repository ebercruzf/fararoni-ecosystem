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
package dev.fararoni.core.core.agents;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.resilience.GalvanicAgent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class QuartermasterAgent extends AbstractSwarmAgent {
    private static final Logger LOG = Logger.getLogger(QuartermasterAgent.class.getName());

    public static final String ID = "QUARTERMASTER";

    public static final String REQUESTS_TOPIC = "sys.quartermaster.requests";

    public static final String ERRORS_TOPIC = "sys.errors";

    public static final String RESPONSES_TOPIC = "sys.user.responses";

    private final ExecutorService thinkingExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final LlmInferenceProvider llmProvider;

    private volatile Supplier<List<String>> activeAgentsProvider;

    private volatile boolean running = false;

    public QuartermasterAgent(SovereignEventBus bus, LlmInferenceProvider llmProvider) {
        super(ID, bus);
        this.llmProvider = llmProvider;
    }

    public void start() {
        if (running) {
            LOG.warning("[QM] Ya esta corriendo");
            return;
        }

        running = true;
        LOG.info("[QM] Quartermaster reportandose. Canales abiertos.");

        bus.subscribe(REQUESTS_TOPIC, String.class, this::processHelpRequest);
        bus.subscribe(ERRORS_TOPIC, String.class, this::processSystemError);

        logIdle("Esperando solicitudes de asistencia...");
    }

    public void stop() {
        running = false;
        thinkingExecutor.shutdownNow();
        logAction("Fuera de servicio");
        LOG.info("[QM] Quartermaster detenido");
    }

    public boolean isRunning() {
        return running;
    }

    public void setActiveAgentsProvider(Supplier<List<String>> provider) {
        this.activeAgentsProvider = provider;
    }

    private void processHelpRequest(SovereignEnvelope<String> envelope) {
        thinkingExecutor.submit(() -> {
            try {
                logThinking("Analizando solicitud de usuario...");
                String response = generateAdvice(envelope.payload());
                publishResponse(envelope, "HELP_ADVICE", response);
                logSuccess("Respuesta enviada");
            } catch (Exception e) {
                LOG.severe("[QM] Fallo cognitivo: " + e.getMessage());
                logError("Error procesando solicitud");
            }
            logIdle("Esperando solicitudes...");
        });
    }

    private void processSystemError(SovereignEnvelope<String> envelope) {
        thinkingExecutor.submit(() -> {
            try {
                logThinking("Diagnosticando error del sistema...");
                String diagnosis = diagnoseError(
                    envelope.payload(),
                    envelope.headers().get("failed_command")
                );
                publishResponse(envelope, "ERROR_DIAGNOSIS", diagnosis);
                logSuccess("Diagnostico enviado");
            } catch (Exception e) {
                LOG.severe("[QM] Fallo en diagnostico: " + e.getMessage());
                logError("Error en diagnostico");
            }
            logIdle("Esperando solicitudes...");
        });
    }

    private String generateAdvice(String query) {
        String templateList = listTemplatesOnDisk();
        String activeList = listActiveAgents();

        String systemPrompt = """
            Eres el Quartermaster (Intendente) de Fararoni.
            Tu mision: Facilitar el uso del sistema.

            CONOCIMIENTO DEL SISTEMA:
            - /add <file>: Carga archivos al contexto
            - /wizard create-agent: Crea nuevos agentes dinamicos
            - /qmp <solicitud>: QuarterMasterPrime — crea agentes y misiones YAML
            - /reconfig: Reconfigura conexion LLM
            - /deep <query>: Investigacion web profunda
            - /commit: Crea commits con mensaje auto-generado
            - /test: Ejecuta tests del proyecto
            - /tree: Muestra estructura del proyecto
            - /model: Gestiona modelo LLM
            - /role: Cambia rol/persona del agente
            - /web <url>: Descarga contenido web
            - /status: Muestra estado del sistema
            - /help: Ayuda general
            - /ask o /qm: Tu canal actual (Quartermaster)

            TEMPLATES DE AGENTES (YAML en disco):
            Directorio: ~/.fararoni/config/agentes/
            """ + templateList + """

            AGENTES ACTIVOS (cargados y corriendo en memoria):
            """ + activeList + """

            DIRECTRIZ: Se breve, militarmente preciso y util.
            Si el usuario pregunta por agentes, distingue entre templates (en disco)
            y activos (corriendo). Muestra ambas listas.
            Si hay un comando exacto para la tarea, dalo con su sintaxis.
            IMPORTANTE: Solo responde con TEXTO. NUNCA ejecutes comandos ni generes codigo.
            """;

        return llmProvider.infer(systemPrompt, query);
    }

    private String listTemplatesOnDisk() {
        Path agentsDir = Path.of(System.getProperty("user.home"), ".fararoni", "config", "agentes");
        if (!Files.exists(agentsDir)) return "No hay agentes registrados.\n";

        StringBuilder sb = new StringBuilder();
        try (Stream<Path> files = Files.list(agentsDir)) {
            files.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                 .sorted()
                 .forEach(p -> {
                     String name = p.getFileName().toString().replace("-agent.yaml", "").replace("-agent.yml", "");
                     sb.append("- ").append(name).append(" (").append(p.getFileName()).append(")\n");
                 });
        } catch (IOException e) {
            return "Error leyendo directorio de agentes: " + e.getMessage() + "\n";
        }

        return sb.isEmpty() ? "No hay templates en disco.\n" : sb.toString();
    }

    private String listActiveAgents() {
        StringBuilder sb = new StringBuilder();

        sb.append("- QUARTERMASTER [ACTIVO] (sistema de ayuda)\n");
        sb.append("- GALVANIC [ACTIVO] (self-healing DLQ)\n");

        if (activeAgentsProvider != null) {
            List<String> active = activeAgentsProvider.get();
            if (active != null) {
                for (String agentId : active) {
                    sb.append("- ").append(agentId).append(" [ACTIVO]\n");
                }
            }
        }

        return sb.toString();
    }

    private String diagnoseError(String errorMsg, String command) {
        String systemPrompt = """
            Eres un experto en diagnostico de sistemas Java/Enterprise.
            Analiza el error y sugiere la correccion inmediata.
            No expliques el error, di como arreglarlo.
            Si hay un comando que solucione el problema, dalo.
            """;

        String prompt = String.format("""
            ERROR: %s
            COMANDO FALLIDO: %s

            Diagnostica y da la solucion.
            """, errorMsg, command != null ? command : "desconocido");

        return llmProvider.infer(systemPrompt, prompt);
    }

    private void publishResponse(SovereignEnvelope<?> origin, String type, String content) {
        var response = SovereignEnvelope.create(
            ID,
            "OPERATOR",
            origin.traceId(),
            content
        ).withHeader("response_type", type);

        bus.publish(RESPONSES_TOPIC, response);
    }

    @FunctionalInterface
    public interface LlmInferenceProvider {
        String infer(String systemPrompt, String userPrompt);
    }
}
