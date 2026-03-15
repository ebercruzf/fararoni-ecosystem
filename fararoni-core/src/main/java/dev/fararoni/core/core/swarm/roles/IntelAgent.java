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
import dev.fararoni.core.core.swarm.context.SwarmContext;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class IntelAgent extends SwarmAgent {
    private static final Logger LOG = Logger.getLogger(IntelAgent.class.getName());

    private static final Persona INTEL_PERSONA = Persona.builder("INTEL")
        .name("Intelligence Officer")
        .description("""
            Eres el Oficial de Inteligencia - "El Cazador de Ambigüedad".
            TU MISIÓN: Convertir deseos vagos en especificaciones precisas.

            TUS REGLAS:
            1. DETECTAR AMBIGÜEDAD: Si algo no está claro, identifícalo.
            2. CASOS DE USO: Lista Happy Path Y Edge Cases siempre.
            3. REGLAS DE NEGOCIO: Define validaciones, límites, formatos.
            4. NO DISEÑAR: Solo define EL QUÉ, no EL CÓMO (eso es del STRATEGIST).
            5. OUTPUT: Genera una Especificación Funcional clara y completa.""")
        .expertise("requirements_analysis", "business_rules", "use_cases", "edge_cases")
        .allowedTools("fs_read", "code_search", "doc_generate")
        .style(Persona.CommunicationStyle.DETAILED)
        .priorityCritics(Critic.CriticCategory.QUALITY)
        .build();

    private int specificationsGenerated = 0;
    private int ambiguitiesDetected = 0;

    public IntelAgent() {
        super("INTEL", INTEL_PERSONA);
    }

    @Override
    protected void processMessage(SwarmMessage msg) {
        switch (msg.type()) {
            case SwarmMessage.TYPE_REQUIREMENTS -> handleRequirements(msg);
            case SwarmMessage.TYPE_ERROR -> handleError(msg);
            default -> LOG.fine(() -> "[INTEL] Mensaje ignorado: " + msg.type());
        }
    }

    private void handleRequirements(SwarmMessage msg) {
        LOG.info(() -> "[INTEL] Analizando requisitos para eliminar ambigüedad...");

        String vagueRequest = msg.content();

        String contextHints = msg.getMetadata("context_hints");
        if (contextHints == null) {
            contextHints = msg.getMetadata("filename");
        }

        String functionalSpec = think("""
            Analiza esta solicitud de negocio del COMMANDER:

            REQUISITOS DEL COMMANDER:
            %s

            TU TRABAJO:
            1. AMBIGÜEDADES: ¿Qué falta definir? ¿Qué términos son vagos?
            2. CASOS DE USO:
               - Happy Path: Flujo normal esperado
               - Edge Cases: ¿Qué pasa si el usuario hace algo inesperado?
               - Error Cases: ¿Qué puede fallar?
            3. REGLAS DE NEGOCIO:
               - Validaciones necesarias (formatos, rangos, límites)
               - Restricciones de seguridad
               - Comportamientos por defecto
            4. CRITERIOS DE ACEPTACIÓN: ¿Cómo sabemos que está bien hecho?

            IMPORTANTE: NO diseñes la solución técnica (eso es del STRATEGIST).
            Solo define EL QUÉ, no EL CÓMO.

            Salida esperada: Documento de Especificación Funcional estructurado.
            """.formatted(vagueRequest));

        specificationsGenerated++;

        String upperSpec = functionalSpec.toUpperCase();
        if (upperSpec.contains("AMBIGÜEDAD") || upperSpec.contains("NO ESPECIFICADO") ||
            upperSpec.contains("FALTA DEFINIR") || upperSpec.contains("NO CLARO")) {
            ambiguitiesDetected++;
        }

        final String finalContextHints = contextHints;
        LOG.info(() -> "[INTEL] Especificación generada. Contexto: " +
            (finalContextHints != null ? finalContextHints : "Ninguno"));

        final String targetAgent = SwarmContext.getNextAgent("INTEL");

        if (!"STRATEGIST".equals(targetAgent)) {
            LOG.info(() -> "[INTEL] STRATEGIST no activo. Chain of Responsibility → " + targetAgent);
        }

        SwarmMessage specMsg = SwarmMessage.builder()
            .from(agentId)
            .to(targetAgent)
            .type(SwarmMessage.TYPE_FUNCTIONAL_SPEC)
            .content(functionalSpec)
            .metadata("context_hints", contextHints)
            .metadata("intel_reviewed", true)
            .metadata("original_requirements", truncate(vagueRequest, 500))
            .build();
        getBus().send(specMsg);
        LOG.info(() -> "[INTEL] Especificación enviada a: " + targetAgent);
    }

    private void handleError(SwarmMessage msg) {
        LOG.severe(() -> "[INTEL] Error recibido: " + msg.content());
        sendTo("COMMANDER", SwarmMessage.TYPE_ERROR, "Error en análisis: " + msg.content());
    }

    private String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    @Override
    protected void onStartup() {
        LOG.info(() -> "[INTEL] Intelligence Officer iniciado y listo para cazar ambigüedades");
    }

    @Override
    protected void onShutdown() {
        LOG.info(() -> String.format(
            "[INTEL] Shutdown. Specs generadas: %d, Ambigüedades detectadas: %d",
            specificationsGenerated, ambiguitiesDetected));
    }
}
