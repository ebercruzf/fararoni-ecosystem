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

import java.util.Map;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class RoleCommand implements ConsoleCommand {
    private static volatile String currentRole = "dev";

    private static final Map<String, RoleDefinition> ROLES = Map.of(
        "dev", new RoleDefinition(
            "Developer",
            "Desarrollador de Software",
            """
            Eres un desarrollador senior enfocado en implementacion de codigo.
            Tu objetivo es escribir codigo limpio, eficiente y mantenible.
            Prioridades:
            - Implementacion practica y funcional
            - Codigo legible y bien estructurado
            - Patrones SOLID y mejores practicas
            - Tests unitarios cuando sea apropiado
            - Documentacion de codigo clara
            """
        ),
        "architect", new RoleDefinition(
            "Architect",
            "Arquitecto de Software",
            """
            Eres un arquitecto de software enfocado en diseno de sistemas.
            Tu objetivo es disenar soluciones escalables y mantenibles.
            Prioridades:
            - Patrones arquitectonicos apropiados
            - Modularidad y separacion de responsabilidades
            - Escalabilidad y performance
            - Decisiones tecnicas justificadas
            - Trade-offs documentados
            - Diagramas y documentacion de arquitectura
            """
        ),
        "security", new RoleDefinition(
            "Security",
            "Especialista en Seguridad",
            """
            Eres un especialista en seguridad de aplicaciones.
            Tu objetivo es identificar y mitigar vulnerabilidades.
            Prioridades:
            - OWASP Top 10 y vulnerabilidades comunes
            - Validacion de entrada y escape de salida
            - Autenticacion y autorizacion segura
            - Gestion de secretos y datos sensibles
            - Revision de codigo con enfoque en seguridad
            - Recomendaciones de hardening
            """
        ),
        "qa", new RoleDefinition(
            "QA",
            "Quality Assurance",
            """
            Eres un ingeniero de QA enfocado en calidad de software.
            Tu objetivo es asegurar que el software funcione correctamente.
            Prioridades:
            - Cobertura de pruebas completa
            - Tests unitarios, integracion y e2e
            - Casos de borde y manejo de errores
            - Estrategias de testing apropiadas
            - Automatizacion de pruebas
            - Reportes de defectos claros
            """
        ),
        "ux", new RoleDefinition(
            "UX",
            "UX Designer",
            """
            Eres un disenador UX enfocado en experiencia de usuario.
            Tu objetivo es crear interfaces intuitivas y accesibles.
            Prioridades:
            - Usabilidad y accesibilidad (WCAG)
            - Flujos de usuario optimizados
            - Feedback visual claro
            - Consistencia en la interfaz
            - Manejo de errores amigable
            - Responsive design
            """
        )
    );

    @Override
    public String getTrigger() {
        return "/role";
    }

    @Override
    public String getDescription() {
        return "Cambia el rol/persona del agente (dev, architect, security, qa, ux)";
    }

    @Override
    public String getUsage() {
        return "/role [dev|architect|security|qa|ux]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIG;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/persona" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /role - Cambia el rol/persona del agente

            Uso:
              /role                  Muestra el rol actual
              /role <rol>            Cambia al rol especificado
              /role list             Lista todos los roles disponibles
              /role reset            Vuelve al rol por defecto (dev)

            Roles Disponibles:
              dev        - Desarrollador: codigo limpio y funcional
              architect  - Arquitecto: diseno y patrones
              security   - Seguridad: vulnerabilidades y hardening
              qa         - QA: testing y calidad
              ux         - UX: experiencia de usuario

            Ejemplos:
              /role dev              # Modo desarrollador
              /role architect        # Modo arquitecto
              /role security         # Modo seguridad
              /role qa               # Modo QA/testing
              /role ux               # Modo UX/diseno

            Comportamiento por Rol:
              ┌─────────────┬─────────────────────────────────────┐
              │ Rol         │ Enfoque Principal                   │
              ├─────────────┼─────────────────────────────────────┤
              │ dev         │ Implementacion, codigo limpio       │
              │ architect   │ Patrones, escalabilidad, diagramas  │
              │ security    │ OWASP, vulnerabilidades, hardening  │
              │ qa          │ Tests, cobertura, casos de borde    │
              │ ux          │ Usabilidad, accesibilidad, flujos   │
              └─────────────┴─────────────────────────────────────┘

            Notas:
              - El rol persiste durante la sesion
              - Afecta el system prompt del LLM
              - Use /role reset para volver al default

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        if (args == null || args.isBlank()) {
            showCurrentRole(ctx);
            return;
        }

        String action = args.trim().toLowerCase();

        switch (action) {
            case "list", "ls", "all" -> listRoles(ctx);
            case "reset", "default", "clear" -> setRole("dev", ctx);
            default -> {
                if (ROLES.containsKey(action)) {
                    setRole(action, ctx);
                } else {
                    ctx.printError("Rol no reconocido: " + action);
                    ctx.print("Roles validos: dev, architect, security, qa, ux");
                    ctx.print("Use /role list para ver descripciones");
                }
            }
        }
    }

    private void showCurrentRole(ExecutionContext ctx) {
        RoleDefinition def = ROLES.get(currentRole);
        if (def != null) {
            ctx.print("Rol actual: " + def.name + " (" + currentRole + ")");
            ctx.print("  " + def.description);
            ctx.print("");
            ctx.print("Use /role <rol> para cambiar");
            ctx.print("Use /role list para ver todos los roles");
        } else {
            ctx.print("Rol actual: " + currentRole);
        }
    }

    private void listRoles(ExecutionContext ctx) {
        ctx.print("Roles disponibles:");
        ctx.print("");

        for (Map.Entry<String, RoleDefinition> entry : ROLES.entrySet()) {
            String key = entry.getKey();
            RoleDefinition def = entry.getValue();

            String marker = key.equals(currentRole) ? " [activo]" : "";
            ctx.print("  " + key + " - " + def.name + marker);
            ctx.print("    " + def.description);
        }

        ctx.print("");
        ctx.print("Uso: /role <rol>");
    }

    private void setRole(String role, ExecutionContext ctx) {
        RoleDefinition def = ROLES.get(role);

        if (def == null) {
            ctx.printError("Rol no encontrado: " + role);
            return;
        }

        String previousRole = currentRole;

        currentRole = role;

        ctx.addToSystemContext("[ROLE: " + def.name + "] " + def.systemPrompt);

        ctx.printSuccess("OK - Rol cambiado a: " + def.name);
        ctx.print("  " + def.description);

        if (!previousRole.equals(role)) {
            ctx.printDebug("Rol anterior: " + previousRole);
        }

        String tip = getRoleTip(role);
        if (tip != null) {
            ctx.print("");
            ctx.print("Tip: " + tip);
        }
    }

    private String getRoleTip(String role) {
        return switch (role) {
            case "dev" -> "Enfocate en implementacion practica y codigo limpio";
            case "architect" -> "Considera escalabilidad, patrones y trade-offs";
            case "security" -> "Revisa OWASP Top 10 y validacion de inputs";
            case "qa" -> "Piensa en casos de borde y cobertura de tests";
            case "ux" -> "Prioriza usabilidad y accesibilidad";
            default -> null;
        };
    }

    public static String getCurrentRole() {
        return currentRole;
    }

    public static String getCurrentSystemPrompt() {
        RoleDefinition def = ROLES.get(currentRole);
        return def != null ? def.systemPrompt : ROLES.get("dev").systemPrompt;
    }

    public static String getSystemPromptForRole(String role) {
        if (role == null) role = "dev";
        RoleDefinition def = ROLES.get(role);
        return def != null ? def.systemPrompt : ROLES.get("dev").systemPrompt;
    }

    public static Map<String, RoleDefinition> getAvailableRoles() {
        return ROLES;
    }

    public record RoleDefinition(
        String name,
        String description,
        String systemPrompt
    ) {}
}
