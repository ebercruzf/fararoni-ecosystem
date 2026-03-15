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
import dev.fararoni.core.core.swarm.base.SwarmAgent;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class StrategistAgent extends SwarmAgent {
    private static final Logger LOG = Logger.getLogger(StrategistAgent.class.getName());

    private static final Persona STRATEGIST_PERSONA = Persona.builder("STRATEGIST")
        .name("Strategist")
        .description("""
            Eres el Estratega de la Colmena.
            TU MISIÓN: Definir la estructura técnica ANTES de escribir código.

            TUS REGLAS:
            1. VALIDAR VIABILIDAD: Si es imposible o inseguro, RECHÁZALO.
            2. IMPONER PATRONES: Define qué patrones usar (Factory, Strategy, etc.).
            3. ESTÁNDARES: Asegura el uso de Java 25, Inmutabilidad, Records.
            4. SEGURIDAD PRIMERO: Nunca aprobar algo que comprometa el sistema.
            5. OUTPUT: Genera un 'System Design' conciso para el BLUEPRINT.""")
        .expertise("system_design", "design_patterns", "security", "scalability", "java_25")
        .allowedTools("fs_read", "code_search", "diagram_generate")
        .style(Persona.CommunicationStyle.TECHNICAL)
        .priorityCritics(Critic.CriticCategory.SECURITY, Critic.CriticCategory.CODE)
        .build();

    private int designsApproved = 0;
    private int projectsRejected = 0;

    public StrategistAgent() {
        super("STRATEGIST", STRATEGIST_PERSONA);
    }

    @Override
    protected void processMessage(SwarmMessage msg) {
        switch (msg.type()) {
            case SwarmMessage.TYPE_FUNCTIONAL_SPEC -> handleFunctionalSpec(msg);
            case SwarmMessage.TYPE_REQUIREMENTS -> handleRequirements(msg);
            case SwarmMessage.TYPE_ERROR -> handleError(msg);
            default -> LOG.fine(() -> "[STRATEGIST] Mensaje ignorado: " + msg.type());
        }
    }

    private void handleFunctionalSpec(SwarmMessage msg) {
        LOG.info(() -> "[STRATEGIST] Recibida especificación funcional del INTEL. Diseñando arquitectura...");

        String functionalSpec = msg.content();
        String filename = msg.getMetadata("filename", "output.txt");

        String designAnalysis = think("""
            El INTEL ha refinado los requisitos en esta Especificación Funcional:

            ESPECIFICACIÓN FUNCIONAL:
            %s

            Como Estratega, evalúa y diseña:
            1. VIABILIDAD TÉCNICA: ¿Es factible con las tecnologías actuales? (SI/NO)
            2. RIESGOS: Identifica riesgos de seguridad o escalabilidad.
            3. PATRONES: Define qué patrones de diseño usar (Factory, Strategy, Observer, etc.).
            4. COMPONENTES: Describe la estructura de módulos/clases/servicios.
            5. ESTÁNDARES: Especifica tecnologías (Java 25, Records, inmutabilidad).
            6. INTERFACES: Define contratos entre componentes.

            Si es viable, genera un SYSTEM_DESIGN detallado para el BLUEPRINT.
            Si NO es viable, indica VIABILIDAD: NO y explica claramente por qué.
            """.formatted(functionalSpec));

        processDesignDecision(designAnalysis, filename);
    }

    private void handleRequirements(SwarmMessage msg) {
        LOG.info(() -> "[STRATEGIST] Analizando requisitos en modo legacy (sin INTEL)...");

        String requirements = msg.content();

        String designAnalysis = think("""
            Analiza estos requisitos del COMMANDER:

            REQUISITOS:
            %s

            Evalúa y responde:
            1. VIABILIDAD: ¿Es técnicamente factible? (SI/NO y por qué)
            2. RIESGOS: ¿Hay riesgos de seguridad o escalabilidad?
            3. PATRONES: ¿Qué patrones de diseño aplicar?
            4. ARQUITECTURA: Describe la estructura de componentes.
            5. ESTÁNDARES: ¿Qué tecnologías/versiones usar?

            Si es viable, genera un SYSTEM_DESIGN conciso.
            Si NO es viable, indica VIABILIDAD: NO y explica por qué.
            """.formatted(requirements));

        String filename = inferFilename(requirements);
        processDesignDecision(designAnalysis, filename);
    }

    private void processDesignDecision(String designAnalysis, String filename) {
        String upperAnalysis = designAnalysis.toUpperCase();
        boolean isViable = !upperAnalysis.contains("VIABILIDAD: NO") &&
                          !upperAnalysis.contains("NO ES VIABLE") &&
                          !upperAnalysis.contains("CRITICAL_RISK") &&
                          !upperAnalysis.contains("RECHAZADO");

        if (isViable) {
            designsApproved++;
            LOG.info(() -> "[STRATEGIST] Diseño aprobado. Enviando a BLUEPRINT...");

            SwarmMessage designMsg = SwarmMessage.builder()
                .from(agentId)
                .to("BLUEPRINT")
                .type(SwarmMessage.TYPE_SYSTEM_DESIGN)
                .content(designAnalysis)
                .metadata("filename", filename)
                .metadata("strategist_approved", true)
                .build();
            getBus().send(designMsg);
        } else {
            projectsRejected++;
            LOG.warning(() -> "[STRATEGIST] Proyecto RECHAZADO por riesgos técnicos");

            sendTo("COMMANDER", SwarmMessage.TYPE_PROJECT_REJECTED,
                "ESTRATEGIA RECHAZA LA SOLICITUD\n\n" + designAnalysis);
        }
    }

    private String inferFilename(String requirements) {
        String lower = requirements.toLowerCase();

        if (lower.contains("saludo.py")) return "saludo.py";
        if (lower.contains("hello.py")) return "hello.py";
        if (lower.contains(".py") || lower.contains("python")) return "script.py";
        if (lower.contains(".java") || lower.contains("java")) return "Main.java";
        if (lower.contains(".js") || lower.contains("javascript")) return "script.js";
        if (lower.contains(".ts") || lower.contains("typescript")) return "script.ts";

        return "output.txt";
    }

    private void handleError(SwarmMessage msg) {
        LOG.severe(() -> "[STRATEGIST] Error recibido: " + msg.content());
        sendTo("COMMANDER", SwarmMessage.TYPE_ERROR, "Error en estrategia: " + msg.content());
    }

    @Override
    protected void onStartup() {
        LOG.info(() -> "[STRATEGIST] Strategist iniciado y listo para diseñar");
    }

    @Override
    protected void onShutdown() {
        LOG.info(() -> String.format(
            "[STRATEGIST] Shutdown. Diseños aprobados: %d, Proyectos rechazados: %d",
            designsApproved, projectsRejected));
    }
}
