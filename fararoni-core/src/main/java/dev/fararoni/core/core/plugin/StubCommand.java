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
package dev.fararoni.core.core.plugin;

import dev.fararoni.bus.agent.api.command.CommandCategory;
import dev.fararoni.bus.agent.api.command.ConsoleCommand;
import dev.fararoni.bus.agent.api.command.ExecutionContext;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class StubCommand implements ConsoleCommand {

    private final String trigger;
    private final PluginInfo pluginInfo;

    public StubCommand(String trigger, PluginInfo pluginInfo) {
        this.trigger = trigger;
        this.pluginInfo = pluginInfo;
    }

    @Override
    public String getTrigger() {
        return trigger;
    }

    @Override
    public String getDescription() {
        return "[No instalado] " + pluginInfo.description();
    }

    @Override
    public String getUsage() {
        return trigger + " (requiere plugin: " + pluginInfo.name() + ")";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.EXTENSIONS;
    }

    @Override
    public String[] getAliases() {
        return new String[0];
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        ctx.print("");
        ctx.print("╭─────────────────────────────────────────────────────────╮");
        ctx.print("│  PLUGIN REQUERIDO                                       │");
        ctx.print("╰─────────────────────────────────────────────────────────╯");
        ctx.print("");
        ctx.print("El comando " + trigger + " requiere el plugin:");
        ctx.print("");
        ctx.print("  " + pluginInfo.name() + " v" + pluginInfo.version());
        ctx.print("  " + pluginInfo.description());
        ctx.print("  Tamano: " + pluginInfo.getFormattedSize());
        ctx.print("");

        if (pluginInfo.commands().size() > 1) {
            ctx.print("Este plugin tambien incluye:");
            for (String cmd : pluginInfo.commands()) {
                if (!cmd.equalsIgnoreCase(trigger)) {
                    ctx.print("  - " + cmd);
                }
            }
            ctx.print("");
        }

        ctx.print("Para instalar, ejecute:");
        ctx.print("  /plugins install " + pluginInfo.id());
        ctx.print("");

        if (ctx.supportsInteractiveInput()) {
            ctx.print("¿Desea instalar ahora? (s/n): ");
            String response = ctx.readLine();

            if (response != null && (response.equalsIgnoreCase("s") ||
                    response.equalsIgnoreCase("si") ||
                    response.equalsIgnoreCase("y") ||
                    response.equalsIgnoreCase("yes"))) {

                installAndExecute(args, ctx);
            } else {
                ctx.print("Instalacion cancelada.");
            }
        }
    }

    private void installAndExecute(String originalArgs, ExecutionContext ctx) {
        ctx.print("");
        ctx.print("Instalando " + pluginInfo.name() + "...");
        ctx.print("");

        try {
            PluginManager manager = PluginManager.getInstance();

            boolean success = manager.installPlugin(pluginInfo.id(), progress -> {
                switch (progress.state()) {
                    case CONNECTING -> ctx.print("  Conectando...");
                    case DOWNLOADING -> ctx.print("\r  " + progress.message());
                    case VERIFYING -> ctx.print("  Verificando integridad...");
                    case COMPLETED -> ctx.print("  Plugin instalado!");
                    case ERROR -> ctx.printError("  Error: " + progress.message());
                    default -> { }
                }
            });

            if (success) {
                ctx.printSuccess("Plugin instalado correctamente!");
                ctx.print("");

                ctx.print("Ejecutando " + trigger + "...");
                ctx.print("");

                ConsoleCommand realCommand = manager.getPluginCommand(trigger).orElse(null);
                if (realCommand != null) {
                    realCommand.execute(originalArgs, ctx);
                } else {
                    ctx.printError("Error: comando no encontrado tras instalacion");
                }
            } else {
                ctx.printError("Error instalando plugin");
            }

        } catch (PluginManager.PluginInstallException e) {
            ctx.printError("Error: " + e.getMessage());
            ctx.print("");
            ctx.print("Intente nuevamente o instale manualmente:");
            ctx.print("  /plugins install " + pluginInfo.id());
        }
    }

    @Override
    public String getExtendedHelp() {
        return String.format("""
            %s - Requiere Plugin

            Este comando requiere el plugin "%s" que no esta instalado.

            Plugin: %s
            Version: %s
            Tamano: %s

            Descripcion:
              %s

            Comandos incluidos:
              %s

            Instalacion:
              /plugins install %s

            Una vez instalado, el comando estara disponible inmediatamente.
            """,
            trigger,
            pluginInfo.name(),
            pluginInfo.name(),
            pluginInfo.version(),
            pluginInfo.getFormattedSize(),
            pluginInfo.description(),
            String.join(", ", pluginInfo.commands()),
            pluginInfo.id()
        );
    }

    public PluginInfo getPluginInfo() {
        return pluginInfo;
    }
}
