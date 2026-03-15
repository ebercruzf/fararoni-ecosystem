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
public class DryRunCommand implements ConsoleCommand {
    private static volatile boolean dryRunEnabled = false;

    @Override
    public String getTrigger() {
        return "/dryrun";
    }

    @Override
    public String getDescription() {
        return "Activa/desactiva modo de simulacion (no ejecuta cambios)";
    }

    @Override
    public String getUsage() {
        return "/dryrun [on|off|status]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIG;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/dry", "/simulate" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /dryrun - Activa/desactiva modo de simulacion

            Uso:
              /dryrun           Toggle del estado actual
              /dryrun on        Activa dry-run
              /dryrun off       Desactiva dry-run
              /dryrun status    Muestra estado actual

            Comportamiento en Dry-Run:
              - Archivos NO se crean ni modifican
              - Commits NO se ejecutan
              - Comandos shell NO se ejecutan
              - Se muestra lo que SE HARIA

            Ejemplos:
              /dryrun on
              /dryrun off
              /dryrun status

            Casos de Uso:
              - Revisar cambios propuestos antes de aplicarlos
              - Validar comportamiento del agente
              - Testing de prompts sin efectos secundarios
              - Auditoria de acciones

            Notas:
              - Por defecto: dry-run desactivado
              - Estado persiste durante la sesion
              - Use /dryrun status para verificar estado

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        String action = (args != null && !args.isBlank()) ? args.trim().toLowerCase() : "toggle";

        switch (action) {
            case "on", "true", "1", "enable" -> {
                dryRunEnabled = true;
                ctx.printSuccess("OK - Dry-run ACTIVADO");
                ctx.print("  Los cambios se mostraran pero NO se ejecutaran");
                ctx.printWarning("Recuerde desactivar dry-run para aplicar cambios reales");
            }

            case "off", "false", "0", "disable" -> {
                dryRunEnabled = false;
                ctx.printSuccess("OK - Dry-run DESACTIVADO");
                ctx.print("  Los cambios se ejecutaran normalmente");
            }

            case "status", "?" -> {
                String status = dryRunEnabled ? "ACTIVADO" : "DESACTIVADO";
                ctx.print("Dry-run: " + status);
                if (dryRunEnabled) {
                    ctx.printWarning("Los cambios NO se estan ejecutando");
                }
            }

            case "toggle" -> {
                dryRunEnabled = !dryRunEnabled;
                String status = dryRunEnabled ? "ACTIVADO" : "DESACTIVADO";
                ctx.printSuccess("OK - Dry-run " + status);
                if (dryRunEnabled) {
                    ctx.print("  Los cambios se mostraran pero NO se ejecutaran");
                } else {
                    ctx.print("  Los cambios se ejecutaran normalmente");
                }
            }

            default -> {
                ctx.printError("Opcion no reconocida: " + action);
                ctx.print("Uso: " + getUsage());
                ctx.print("  Opciones: on, off, status, toggle");
            }
        }

        ctx.printDebug("dryRunEnabled=" + dryRunEnabled);
    }

    public static boolean isDryRunEnabled() {
        return dryRunEnabled;
    }

    public static void setDryRunEnabled(boolean enabled) {
        dryRunEnabled = enabled;
    }
}
