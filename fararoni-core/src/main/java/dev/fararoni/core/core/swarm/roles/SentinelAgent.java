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
public class SentinelAgent extends SwarmAgent {
    private static final Logger LOG = Logger.getLogger(SentinelAgent.class.getName());

    private static final Persona SENTINEL_PERSONA = Persona.builder("SENTINEL")
        .name("Sentinel")
        .description("""
            Eres el Centinela de la Colmena. Tu trabajo es:
            1. Revisar código rigurosamente
            2. Identificar bugs, edge cases y vulnerabilidades
            3. Verificar que se cumplan los requisitos
            4. Aprobar solo código de alta calidad""")
        .expertise("testing", "code-review", "quality-assurance", "security")
        .allowedTools("fs_read", "test_run", "code_search", "report_generate")
        .style(Persona.CommunicationStyle.DETAILED)
        .priorityCritics(Critic.CriticCategory.QUALITY, Critic.CriticCategory.SECURITY)
        .build();

    private int reviewsPerformed = 0;
    private int approvals = 0;
    private int rejections = 0;

    private static final int MAX_REVIEW_ITERATIONS = 5;
    private int currentIterations = 0;

    public SentinelAgent() {
        super("SENTINEL", SENTINEL_PERSONA);
    }

    @Override
    protected void processMessage(SwarmMessage msg) {
        switch (msg.type()) {
            case SwarmMessage.TYPE_CODE_DRAFT -> handleCodeDraft(msg);
            case SwarmMessage.TYPE_ERROR -> handleError(msg);
            default -> LOG.fine(() -> "[SENTINEL] Mensaje ignorado: " + msg.type());
        }
    }

    private void handleCodeDraft(SwarmMessage msg) {
        LOG.info(() -> "[SENTINEL] Recibido código para revisión...");
        reviewsPerformed++;
        currentIterations++;

        String filename = msg.getMetadata("filename", "output.txt");

        if (currentIterations > MAX_REVIEW_ITERATIONS) {
            LOG.warning(() -> "[SENTINEL] Máximo de iteraciones alcanzado. Aprobando con advertencias...");
            approvals++;

            SwarmMessage forceApproval = SwarmMessage.builder()
                .from(agentId)
                .to("BUILDER")
                .type(SwarmMessage.TYPE_CODE_APPROVED)
                .content(msg.content())
                .metadata("filename", filename)
                .metadata("review", "APROBADO CON ADVERTENCIAS (max iterations)")
                .build();
            getBus().send(forceApproval);

            currentIterations = 0;
            return;
        }

        String prompt = """
            Revisa el siguiente código como un Centinela experto:

            Código a revisar:
            %s

            Evalúa:
            1. Corrección funcional (¿hace lo que debe?)
            2. Calidad del código (legibilidad, mantenibilidad)
            3. Manejo de errores (¿es robusto?)
            4. Seguridad (¿hay vulnerabilidades?)
            5. Tests (¿son suficientes?)

            Responde en formato estructurado:
            - VEREDICTO: APROBAR o RECHAZAR
            - BUGS: Lista de problemas encontrados (si hay)
            - SUGERENCIAS: Mejoras opcionales

            Sé riguroso pero justo.
            """.formatted(msg.content());

        String review = think(prompt);

        boolean isApproved = review.toUpperCase().contains("VEREDICTO: APROBAR") ||
                            review.toUpperCase().contains("APROBADO");

        if (isApproved) {
            approvals++;
            LOG.info(() -> "[SENTINEL] Código APROBADO. Enviando a BUILDER para despliegue...");

            String approvedCode = msg.content();

            SwarmMessage approvalMsg = SwarmMessage.builder()
                .from(agentId)
                .to("BUILDER")
                .type(SwarmMessage.TYPE_CODE_APPROVED)
                .content(approvedCode)
                .metadata("filename", filename)
                .metadata("review", review)
                .build();
            getBus().send(approvalMsg);

            currentIterations = 0;
        } else {
            rejections++;
            LOG.warning(() -> "[SENTINEL] Código RECHAZADO. Enviando bugs a BUILDER...");

            SwarmMessage bugMsg = SwarmMessage.builder()
                .from(agentId)
                .to("BUILDER")
                .type(SwarmMessage.TYPE_BUG_REPORT)
                .content(review)
                .metadata("filename", filename)
                .build();
            getBus().send(bugMsg);
        }
    }

    private void handleError(SwarmMessage msg) {
        LOG.severe(() -> "[SENTINEL] Error recibido: " + msg.content());
        sendTo("COMMANDER", SwarmMessage.TYPE_ERROR, "Error en SENTINEL: " + msg.content());
    }

    @Override
    protected void onStartup() {
        LOG.info(() -> "[SENTINEL] Sentinel iniciado y listo para revisar");
    }

    @Override
    protected void onShutdown() {
        LOG.info(() -> String.format(
            "[SENTINEL] Shutdown. Reviews: %d, Aprobaciones: %d, Rechazos: %d",
            reviewsPerformed, approvals, rejections));
    }
}
