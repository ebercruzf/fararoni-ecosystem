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
package dev.fararoni.core.core.swarm.roles;

import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.skills.WebSearchSkill;
import dev.fararoni.core.core.skills.impl.SovereignSearchSkill;
import dev.fararoni.core.core.swarm.base.SwarmAgent;
import dev.fararoni.core.core.swarm.context.SwarmContext;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class DeepResearchAgent extends SwarmAgent {
    private static final Logger LOG = Logger.getLogger(DeepResearchAgent.class.getName());

    public static final String TYPE_DEEP_DIVE_REQUEST = "DEEP_DIVE_REQUEST";

    public static final String TYPE_REPORT_READY = "REPORT_READY";

    private static final int MAX_RESEARCH_VECTORS = 4;

    private static final Pattern JSON_ARRAY_PATTERN =
        Pattern.compile("\\[\\s*\"([^\"]+)\"(?:\\s*,\\s*\"([^\"]+)\")*\\s*\\]");

    private static final Persona RESEARCHER_PERSONA = Persona.builder("RESEARCHER")
        .name("Analista de Estrategia")
        .description("""
            Eres un analista de inteligencia estrategica especializado en
            investigaciones profundas. Tu trabajo es:
            1. Planificar vectores de investigacion
            2. Ejecutar busquedas exhaustivas
            3. Analizar y sintetizar informacion
            4. Generar reportes ejecutivos profesionales

            Tomas tu tiempo. Eres metodico. Cubres todos los angulos.
            Tu output final es siempre un REPORTE MARKDOWN completo.""")
        .expertise("research", "analysis", "synthesis", "reporting")
        .allowedTools("web_search", "url_scrape", "fs_write")
        .style(Persona.CommunicationStyle.FORMAL)
        .priorityCritics(Critic.CriticCategory.QUALITY)
        .build();

    private final WebSearchSkill searchSkill;
    private int reportsGenerated = 0;

    public DeepResearchAgent(WebSearchSkill searchSkill) {
        super("RESEARCHER", RESEARCHER_PERSONA);
        this.searchSkill = searchSkill;
    }

    public DeepResearchAgent() {
        this(new SovereignSearchSkill());
    }

    @Override
    protected void processMessage(SwarmMessage msg) {
        LOG.fine(() -> "[RESEARCHER] Mensaje recibido: " + msg.type());

        switch (msg.type()) {
            case TYPE_DEEP_DIVE_REQUEST -> handleDeepDiveRequest(msg);
            default -> LOG.fine(() -> "[RESEARCHER] Mensaje ignorado: " + msg.type());
        }
    }

    private void handleDeepDiveRequest(SwarmMessage msg) {
        String topic = msg.content();
        LOG.info(() -> "[RESEARCHER] Iniciando investigacion profunda: " + topic);
        System.out.println("[RESEARCHER] Iniciando protocolo de investigacion profunda...");
        System.out.println("[RESEARCHER] Tema: " + topic);

        try {
            System.out.println("[RESEARCHER] Paso 1/3: Planificando vectores de investigacion...");

            List<String> researchQuestions = generateResearchPlan(topic);
            System.out.println("[RESEARCHER] Vectores generados: " + researchQuestions.size());

            System.out.println("[RESEARCHER] Paso 2/3: Ejecutando busquedas...");

            StringBuilder knowledgeBase = new StringBuilder();
            int vectorNum = 0;

            for (String question : researchQuestions) {
                vectorNum++;
                System.out.println("[RESEARCHER]   -> Vector " + vectorNum + ": " + question);

                String searchResult = searchSkill.search(question);
                knowledgeBase.append("\n\n--- VECTOR ").append(vectorNum).append(": ")
                             .append(question).append(" ---\n");
                knowledgeBase.append(searchResult);

                Thread.sleep(500);
            }

            System.out.println("[RESEARCHER] Paso 3/3: Redactando informe final...");

            String finalReport = generateExecutiveReport(topic, knowledgeBase.toString());

            String filename = saveReport(topic, finalReport);

            reportsGenerated++;
            LOG.info(() -> "[RESEARCHER] Investigacion completada: " + filename);
            System.out.println("[RESEARCHER] Investigacion completada!");
            System.out.println("[RESEARCHER] Reporte guardado: " + filename);

            String recipient = msg.senderId() != null ? msg.senderId() : "USER";
            String notification = "Investigacion completada. Reporte guardado en: " + filename;
            sendTo(recipient, TYPE_REPORT_READY, notification);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warning(() -> "[RESEARCHER] Investigacion interrumpida");
        } catch (Exception e) {
            LOG.severe(() -> "[RESEARCHER] Error en investigacion: " + e.getMessage());
            sendTo(msg.senderId() != null ? msg.senderId() : "USER",
                   SwarmMessage.TYPE_ERROR, "Error en investigacion: " + e.getMessage());
        }
    }

    private List<String> generateResearchPlan(String topic) {
        String planPrompt = """
            Para investigar a fondo el tema: "%s"

            Genera %d preguntas de busqueda ESPECIFICAS que cubran diferentes angulos:
            - Estado actual del tema
            - Problemas o desafios conocidos
            - Tendencias y futuro
            - Casos de uso o ejemplos

            IMPORTANTE: Responde SOLO con un JSON array de strings.
            Ejemplo: ["pregunta 1", "pregunta 2", "pregunta 3"]

            NO incluyas explicaciones. SOLO el JSON array.
            """.formatted(topic, MAX_RESEARCH_VECTORS);

        String response = think(planPrompt);

        List<String> questions = parseQuestionsFromResponse(response, topic);

        if (questions.isEmpty()) {
            LOG.warning(() -> "[RESEARCHER] Fallback: usando preguntas genericas");
            questions = List.of(
                topic + " estado actual 2026",
                topic + " problemas y desafios",
                topic + " tendencias futuras",
                topic + " casos de uso ejemplos"
            );
        }

        return questions;
    }

    private List<String> parseQuestionsFromResponse(String response, String topic) {
        List<String> questions = new ArrayList<>();

        Matcher matcher = JSON_ARRAY_PATTERN.matcher(response);
        if (matcher.find()) {
            String arrayContent = matcher.group(0);
            Pattern elementPattern = Pattern.compile("\"([^\"]+)\"");
            Matcher elementMatcher = elementPattern.matcher(arrayContent);
            while (elementMatcher.find() && questions.size() < MAX_RESEARCH_VECTORS) {
                questions.add(elementMatcher.group(1));
            }
        }

        if (questions.isEmpty()) {
            String[] lines = response.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                trimmed = trimmed.replaceFirst("^[\\d\\.\\-\\*]+\\s*", "");
                if (trimmed.length() > 10 && questions.size() < MAX_RESEARCH_VECTORS) {
                    questions.add(trimmed);
                }
            }
        }

        return questions;
    }

    private String generateExecutiveReport(String topic, String knowledgeBase) {
        String reportPrompt = """
            Actua como un Analista Senior de Estrategia.

            Basandote en esta base de conocimiento recopilada de Internet:
            %s

            Escribe un REPORTE EJECUTIVO completo sobre: "%s"

            El reporte debe incluir:
            1. **Resumen Ejecutivo** (2-3 parrafos)
            2. **Hallazgos Clave** (bullets)
            3. **Analisis Detallado** (secciones por tema)
            4. **Pros y Contras** (si aplica)
            5. **Conclusiones y Recomendaciones**
            6. **Fuentes Consultadas**

            Formato: Markdown profesional con headers (##), bullets, y enfasis.
            Extension: 500-1000 palabras.
            Tono: Profesional, objetivo, basado en evidencia.

            NO inventes datos que no esten en la base de conocimiento.
            Si algo no quedo claro, indicalo como "requiere investigacion adicional".
            """.formatted(knowledgeBase, topic);

        return think(reportPrompt);
    }

    private String saveReport(String topic, String report) throws IOException {
        String sanitizedTopic = topic.replaceAll("[^a-zA-Z0-9\\s]", "")
                                     .replaceAll("\\s+", "_")
                                     .toLowerCase();
        if (sanitizedTopic.length() > 30) {
            sanitizedTopic = sanitizedTopic.substring(0, 30);
        }

        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        String filename = "Investigacion_" + sanitizedTopic + "_" + timestamp + ".md";

        Path workspace = SwarmContext.workspaceOrDefault();
        Path reportsDir = workspace.resolve("reports");

        if (!Files.exists(reportsDir)) {
            Files.createDirectories(reportsDir);
        }

        Path reportPath = reportsDir.resolve(filename);

        String fullReport = """
            # Reporte de Investigacion: %s

            **Fecha:** %s
            **Generado por:** Fararoni RESEARCHER Agent
            **Modelo:** Turtle (Deep Analysis)

            ---

            %s

            ---
            *Reporte generado automaticamente por Fararoni Core*
            """.formatted(
                topic,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                report
            );

        Files.writeString(reportPath, fullReport);

        return reportPath.toString();
    }

    public String researchDirect(String topic) {
        LOG.info(() -> "[RESEARCHER] Investigacion directa: " + topic);

        try {
            SwarmMessage fakeMsg = SwarmMessage.builder()
                .from("CLI")
                .to("RESEARCHER")
                .type(TYPE_DEEP_DIVE_REQUEST)
                .content(topic)
                .build();

            handleDeepDiveRequest(fakeMsg);

            return "Investigacion completada. Ver directorio 'reports/'.";
        } catch (Exception e) {
            return "Error en investigacion: " + e.getMessage();
        }
    }

    public int getReportsGenerated() {
        return reportsGenerated;
    }
}
