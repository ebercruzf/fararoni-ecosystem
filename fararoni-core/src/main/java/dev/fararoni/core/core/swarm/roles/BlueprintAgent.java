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
public class BlueprintAgent extends SwarmAgent {
    private static final Logger LOG = Logger.getLogger(BlueprintAgent.class.getName());

    private static final Persona BLUEPRINT_PERSONA = Persona.builder("BLUEPRINT")
        .name("Blueprint Master")
        .description("""
            Eres el Maestro de Planos de la Colmena. Tu trabajo es:
            1. Diseñar arquitecturas robustas y escalables
            2. Crear especificaciones técnicas claras
            3. Elegir patrones de diseño apropiados
            4. Guiar al equipo técnicamente""")
        .expertise("architecture", "design-patterns", "code-review", "java")
        .allowedTools("fs_read", "code_search", "diagram_generate")
        .style(Persona.CommunicationStyle.TECHNICAL)
        .priorityCritics(Critic.CriticCategory.CODE, Critic.CriticCategory.QUALITY)
        .build();

    private int blueprintsCreated = 0;

    public BlueprintAgent() {
        super("BLUEPRINT", BLUEPRINT_PERSONA);
    }

    @Override
    protected void processMessage(SwarmMessage msg) {
        System.out.println("[DEBUG-BLUEPRINT] ===== MENSAJE RECIBIDO: " + msg.type() + " de " + msg.senderId() + " =====");
        switch (msg.type()) {
            case SwarmMessage.TYPE_SYSTEM_DESIGN -> handleSystemDesign(msg);
            case SwarmMessage.TYPE_FUNCTIONAL_SPEC -> handleFunctionalSpec(msg);
            case SwarmMessage.TYPE_REQUIREMENTS -> handleRequirements(msg);
            case SwarmMessage.TYPE_SRE_FAILURE -> handleOperatorFailure(msg);
            case "ISSUE_ESCALATION" -> handleEscalation(msg);
            case SwarmMessage.TYPE_ERROR -> handleError(msg);
            default -> LOG.fine(() -> "[BLUEPRINT] Mensaje ignorado: " + msg.type());
        }
    }

    private void handleSystemDesign(SwarmMessage msg) {
        LOG.info(() -> "[BLUEPRINT] Recibido diseño de sistema del STRATEGIST. Detallando blueprint agnóstico...");

        String contextHints = msg.getMetadata("context_hints");
        if (contextHints == null) {
            contextHints = msg.getMetadata("filename");
        }

        final String finalHints = contextHints;

        String prompt = """
            El STRATEGIST ha aprobado este diseño de sistema. Detállalo en un blueprint implementable:

            DISEÑO DE SISTEMA APROBADO:
            %s

            CONTEXTO DEL USUARIO: %s

            REGLAS DE RUTAS Y ARCHIVOS (AGNÓSTICAS):
            1. **RESPETA LA RUTA DEL USUARIO:** Si el contexto especifica una ruta, ÚSALA.
            2. **MULTI-ARCHIVO:** Si la solución requiere múltiples archivos, defínelos todos.
            3. **SIN ALUCINACIONES:** No inventes paquetes como `com.example`.

            FORMATO DE SALIDA REQUERIDO (ESTRICTO):
            Para cada archivo necesario, usa este bloque:

            FILE: <ruta_relativa_exacta>
            PLAN: <instrucciones de implementación detalladas>

            El BUILDER debe poder implementar directamente desde esta especificación.
            """.formatted(msg.content(), (contextHints != null ? contextHints : "Ninguno"));

        String blueprint = think(prompt);
        blueprintsCreated++;

        LOG.info(() -> "[BLUEPRINT] Blueprint Agnóstico detallado creado. Enviando a BUILDER...");

        SwarmMessage blueprintMsg = SwarmMessage.builder()
            .from(agentId)
            .to("BUILDER")
            .type(SwarmMessage.TYPE_BLUEPRINT)
            .content(blueprint)
            .metadata("context_hints", finalHints)
            .metadata("strategist_approved", msg.getMetadata("strategist_approved", true))
            .build();
        getBus().send(blueprintMsg);
    }

    private void handleFunctionalSpec(SwarmMessage msg) {
        String contextHints = msg.getMetadata("context_hints");

        String projectStructure = msg.getMetadata("project_structure");
        if (projectStructure == null) {
            projectStructure = "(Estructura desconocida, asume estándar según lenguaje)";
        }

        final String finalHints = contextHints;
        LOG.info(() -> "[BLUEPRINT] Diseñando Blueprint Agnóstico. Pistas: " +
            (finalHints != null ? finalHints : "Ninguna"));

        String prompt = """
            ESPECIFICACIÓN FUNCIONAL:
            %s

            CONTEXTO DEL PROYECTO:
            Pistas del Usuario: %s
            Estructura Actual:
            %s

            TU MISIÓN (BLUEPRINT MASTER):
            Genera un BLUEPRINT técnico para implementar esto.

            REGLAS DE RUTAS Y ARCHIVOS (AGNÓSTICAS):
            1. **RESPETA LA RUTA DEL USUARIO:** Si las 'Pistas del Usuario' especifican una ruta exacta, ÚSALA.
            2. **ADAPTABILIDAD:**
               - Si es un proyecto Java Maven, usa `src/main/java/...`.
               - Si es Python/JS o un script simple, usa la raíz o la carpeta correspondiente.
               - Si el archivo ya existe en la 'Estructura Actual', usa ESA ruta exacta.
            3. **MULTI-ARCHIVO:** Si la solución requiere múltiples archivos (clases, interfaces, configs), defínelos todos.
            4. **SIN ALUCINACIONES:** No inventes paquetes (como `com.example`) si no existen en la estructura actual.

            FORMATO DE SALIDA REQUERIDO (ESTRICTO):
            Para cada archivo necesario, usa este bloque:

            FILE: <ruta_relativa_exacta>
            PLAN: <instrucciones de implementación>

            Ejemplo genérico:
            FILE: backend/server.js
            PLAN: Agregar endpoint de login...

            FILE: models/User.java
            PLAN: Crear clase con campos nombre, email, password...
            """.formatted(
                msg.content(),
                (contextHints != null ? contextHints : "Ninguna específica"),
                projectStructure
            );

        String blueprint = think(prompt);
        blueprintsCreated++;

        LOG.info(() -> "[BLUEPRINT] Blueprint Agnóstico creado. Enviando a BUILDER...");

        SwarmMessage blueprintMsg = SwarmMessage.builder()
            .from(agentId)
            .to("BUILDER")
            .type(SwarmMessage.TYPE_BLUEPRINT)
            .content(blueprint)
            .metadata("context_hints", contextHints)
            .metadata("intel_reviewed", msg.getMetadata("intel_reviewed", true))
            .metadata("strategist_bypassed", true)
            .build();
        getBus().send(blueprintMsg);
    }

    private void handleRequirements(SwarmMessage msg) {
        System.out.println("[DEBUG-BLUEPRINT] handleRequirements iniciado");
        LOG.info(() -> "[BLUEPRINT] Recibidos requisitos (modo legacy). Diseñando arquitectura agnóstica...");

        String contextHints = msg.getMetadata("context_hints");
        if (contextHints == null) {
            contextHints = msg.getMetadata("filename");
        }

        final String finalHints = contextHints;
        System.out.println("[DEBUG-BLUEPRINT] context_hints recibido: " + contextHints);

        System.out.println("[DEBUG-BLUEPRINT] Llamando think() para blueprint agnóstico...");
        String prompt = """
            Diseña la arquitectura técnica para estos requisitos:

            Requisitos:
            %s

            CONTEXTO DEL USUARIO: %s

            REGLAS DE RUTAS Y ARCHIVOS (AGNÓSTICAS):
            1. **RESPETA LA RUTA DEL USUARIO:** Si el contexto especifica una ruta, ÚSALA.
            2. **ADAPTABILIDAD:** Usa convenciones del lenguaje detectado.
            3. **MULTI-ARCHIVO:** Si requiere múltiples archivos, defínelos todos.
            4. **SIN ALUCINACIONES:** No inventes paquetes como `com.example`.

            FORMATO DE SALIDA REQUERIDO (ESTRICTO):
            Para cada archivo necesario, usa este bloque:

            FILE: <ruta_relativa_exacta>
            PLAN: <instrucciones de implementación>

            Sé específico y práctico. El BUILDER debe poder implementar
            directamente desde tu especificación.
            """.formatted(msg.content(), (contextHints != null ? contextHints : "Ninguno"));

        String blueprint = think(prompt);
        System.out.println("[DEBUG-BLUEPRINT] blueprint recibido, length=" + blueprint.length());
        blueprintsCreated++;

        LOG.info(() -> "[BLUEPRINT] Blueprint Agnóstico creado. Enviando a BUILDER...");
        System.out.println("[DEBUG-BLUEPRINT] Enviando a BUILDER con context_hints=" + finalHints);

        SwarmMessage blueprintMsg = SwarmMessage.builder()
            .from(agentId)
            .to("BUILDER")
            .type(SwarmMessage.TYPE_BLUEPRINT)
            .content(blueprint)
            .metadata("context_hints", finalHints)
            .build();
        getBus().send(blueprintMsg);
        System.out.println("[DEBUG-BLUEPRINT] Mensaje enviado a BUILDER con context_hints=" + finalHints);
    }

    private void handleEscalation(SwarmMessage msg) {
        LOG.warning(() -> "[BLUEPRINT] Recibida escalación. Ajustando diseño...");

        String prompt = """
            El diseño anterior tuvo problemas. Evalúa y ajusta:

            Problema reportado:
            %s

            Proporciona:
            1. Análisis de la causa raíz
            2. Diseño corregido
            3. Explicación de los cambios
            """.formatted(msg.content());

        String adjustedBlueprint = think(prompt);
        blueprintsCreated++;

        LOG.info(() -> "[BLUEPRINT] Diseño ajustado. Reenviando a BUILDER...");
        sendTo("BUILDER", SwarmMessage.TYPE_BLUEPRINT, adjustedBlueprint);
    }

    private void handleOperatorFailure(SwarmMessage msg) {
        LOG.severe(() -> "[BLUEPRINT] Fallo crítico de OPERATOR recibido. Analizando...");

        String prompt = """
            El OPERATOR ha reportado un fallo crítico de despliegue:

            REPORTE OPERATOR:
            %s

            Analiza y proporciona:
            1. Causa raíz probable
            2. Acciones correctivas necesarias
            3. Plan de contingencia
            """.formatted(msg.content());

        String analysis = think(prompt);

        sendTo("COMMANDER", SwarmMessage.TYPE_ERROR, "Fallo de despliegue:\n\n" + analysis);
    }

    private void handleError(SwarmMessage msg) {
        LOG.severe(() -> "[BLUEPRINT] Error recibido: " + msg.content());
        sendTo("COMMANDER", SwarmMessage.TYPE_ERROR, "Error técnico: " + msg.content());
    }

    @Override
    protected void onStartup() {
        LOG.info(() -> "[BLUEPRINT] Blueprint Master iniciado y listo para diseñar");
    }

    @Override
    protected void onShutdown() {
        LOG.info(() -> String.format("[BLUEPRINT] Shutdown. Blueprints creados: %d", blueprintsCreated));
    }
}
