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

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ModeCommand implements ConsoleCommand {
    public enum AgentMode {
        AUTO("auto", "Automatico - ejecuta sin confirmar"),

        ASK("ask", "Pregunta - confirma cambios importantes"),

        SAFE("safe", "Seguro - confirma todos los cambios");

        private final String id;
        private final String description;

        AgentMode(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String getId() { return id; }
        public String getDescription() { return description; }

        public static AgentMode fromId(String id) {
            for (AgentMode mode : values()) {
                if (mode.id.equalsIgnoreCase(id)) {
                    return mode;
                }
            }
            return null;
        }
    }

    private static volatile AgentMode currentMode = AgentMode.ASK;

    @Override
    public String getTrigger() {
        return "/mode";
    }

    @Override
    public String getDescription() {
        return "Cambia el modo de operacion del agente (auto/ask/safe)";
    }

    @Override
    public String getUsage() {
        return "/mode [auto|ask|safe]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIG;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/behavior" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /mode - Cambia el modo de operacion del agente

            Uso:
              /mode           Muestra el modo actual
              /mode auto      Modo automatico (no pregunta)
              /mode ask       Modo preguntar (default)
              /mode safe      Modo seguro (confirma todo)

            Modos:
              auto  - El agente ejecuta cambios automaticamente
                      Ideal para tareas repetitivas de confianza
                      NO se recomiendo para codigo critico

              ask   - El agente pregunta antes de cambios importantes
                      Balance entre productividad y control
                      Modo por defecto

              safe  - El agente confirma TODOS los cambios
                      Maximo control sobre las acciones
                      Recomendado para codigo en produccion

            Ejemplos:
              /mode auto      # Confianza total en el agente
              /mode ask       # Balance (default)
              /mode safe      # Maximo control

            Comportamiento por Modo:
              ┌──────────┬────────────┬────────────┬────────────┐
              │ Accion   │   AUTO     │    ASK     │   SAFE     │
              ├──────────┼────────────┼────────────┼────────────┤
              │ Crear    │ Ejecuta    │ Pregunta   │ Pregunta   │
              │ Modificar│ Ejecuta    │ Pregunta   │ Pregunta   │
              │ Borrar   │ Ejecuta    │ Pregunta   │ Pregunta   │
              │ Commit   │ Ejecuta    │ Pregunta   │ Pregunta   │
              │ Shell    │ Ejecuta    │ Ejecuta    │ Pregunta   │
              └──────────┴────────────┴────────────┴────────────┘

            Notas:
              - El modo persiste durante la sesion
              - Modo default: ask
              - Use /mode sin args para ver estado actual

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        if (args == null || args.isBlank()) {
            showCurrentMode(ctx);
            return;
        }

        String modeId = args.trim().toLowerCase();

        AgentMode newMode = AgentMode.fromId(modeId);

        if (newMode == null) {
            ctx.printError("Modo no reconocido: " + modeId);
            ctx.print("Modos disponibles:");
            for (AgentMode mode : AgentMode.values()) {
                ctx.print("  " + mode.getId() + " - " + mode.getDescription());
            }
            return;
        }

        AgentMode oldMode = currentMode;
        currentMode = newMode;

        ctx.printSuccess("OK - Modo cambiado a: " + newMode.getId().toUpperCase());
        ctx.print("  " + newMode.getDescription());

        if (newMode == AgentMode.AUTO && oldMode != AgentMode.AUTO) {
            ctx.printWarning("ADVERTENCIA: En modo AUTO el agente ejecutara cambios sin confirmar");
        }

        switch (newMode) {
            case AUTO -> ctx.print("  Tip: Use /dryrun para simular antes de ejecutar");
            case ASK -> ctx.print("  Tip: El agente preguntara antes de cambios importantes");
            case SAFE -> ctx.print("  Tip: Todos los cambios requeriran confirmacion");
        }

        ctx.printDebug("Modo anterior: " + oldMode.getId());
    }

    private void showCurrentMode(ExecutionContext ctx) {
        ctx.print("Modo actual: " + currentMode.getId().toUpperCase());
        ctx.print("  " + currentMode.getDescription());
        ctx.print("");
        ctx.print("Modos disponibles:");
        for (AgentMode mode : AgentMode.values()) {
            String marker = (mode == currentMode) ? " [*]" : "    ";
            ctx.print(marker + " " + mode.getId() + " - " + mode.getDescription());
        }
        ctx.print("");
        ctx.print("Use /mode <modo> para cambiar");
    }

    public static AgentMode getCurrentMode() {
        return currentMode;
    }

    public static void setCurrentMode(AgentMode mode) {
        currentMode = mode;
    }

    public static boolean requiresConfirmation(String actionType) {
        return switch (currentMode) {
            case AUTO -> false;
            case ASK -> switch (actionType.toLowerCase()) {
                case "shell" -> false;
                default -> true;
            };
            case SAFE -> true;
        };
    }
}
