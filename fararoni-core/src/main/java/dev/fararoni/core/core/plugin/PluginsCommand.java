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

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class PluginsCommand implements ConsoleCommand {

    @Override
    public String getTrigger() {
        return "/plugins";
    }

    @Override
    public String getDescription() {
        return "Gestiona plugins descargables (vision, voice)";
    }

    @Override
    public String getUsage() {
        return "/plugins [list|install|uninstall|update|info] [plugin-id]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.EXTENSIONS;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/plugin", "/extensions" };
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        String[] parts = args == null || args.isBlank()
            ? new String[] { "list" }
            : args.trim().split("\\s+", 2);

        String subcommand = parts[0].toLowerCase();
        String argument = parts.length > 1 ? parts[1] : null;

        switch (subcommand) {
            case "list", "ls" -> listPlugins(ctx);
            case "install", "add" -> installPlugin(argument, ctx);
            case "uninstall", "remove", "rm" -> uninstallPlugin(argument, ctx);
            case "update", "refresh" -> updateCatalog(ctx);
            case "info", "show" -> showPluginInfo(argument, ctx);
            default -> {
                if (argument == null) {
                    showPluginInfo(subcommand, ctx);
                } else {
                    ctx.printError("Subcomando desconocido: " + subcommand);
                    showHelp(ctx);
                }
            }
        }
    }

    private void listPlugins(ExecutionContext ctx) {
        PluginManager manager = PluginManager.getInstance();
        List<PluginInfo> plugins = manager.getAvailablePlugins();

        if (plugins.isEmpty()) {
            ctx.print("No hay plugins disponibles.");
            ctx.print("Use /plugins update para actualizar el catalogo.");
            return;
        }

        ctx.print("");
        ctx.print("╭─────────────────────────────────────────────────────────────╮");
        ctx.print("│                    PLUGINS FARARONI                         │");
        ctx.print("╰─────────────────────────────────────────────────────────────╯");
        ctx.print("");

        boolean hasInstalled = false;
        boolean hasAvailable = false;

        for (PluginInfo plugin : plugins) {
            if (manager.isInstalled(plugin.id())) {
                if (!hasInstalled) {
                    ctx.print("  INSTALADOS:");
                    hasInstalled = true;
                }
                printPluginLine(plugin, true, manager.isLoaded(plugin.id()), ctx);
            }
        }

        if (hasInstalled) {
            ctx.print("");
        }

        for (PluginInfo plugin : plugins) {
            if (!manager.isInstalled(plugin.id())) {
                if (!hasAvailable) {
                    ctx.print("  DISPONIBLES:");
                    hasAvailable = true;
                }
                printPluginLine(plugin, false, false, ctx);
            }
        }

        ctx.print("");
        ctx.print("Comandos:");
        ctx.print("  /plugins install <id>     Instalar plugin");
        ctx.print("  /plugins uninstall <id>   Desinstalar plugin");
        ctx.print("  /plugins info <id>        Ver detalles");
        ctx.print("");
    }

    private void printPluginLine(PluginInfo plugin, boolean installed, boolean loaded,
            ExecutionContext ctx) {

        String status = installed
            ? (loaded ? "[ACTIVO]" : "[instalado]")
            : "[disponible]";

        String line = String.format("    %-10s %-25s v%-8s %8s  %s",
            plugin.id(),
            truncate(plugin.name(), 25),
            plugin.version(),
            plugin.getFormattedSize(),
            status);

        ctx.print(line);
    }

    private void installPlugin(String pluginId, ExecutionContext ctx) {
        if (pluginId == null || pluginId.isBlank()) {
            ctx.printError("Especifique el ID del plugin a instalar");
            ctx.print("Uso: /plugins install <plugin-id>");
            ctx.print("Ejemplo: /plugins install vision");
            return;
        }

        PluginManager manager = PluginManager.getInstance();

        if (manager.isInstalled(pluginId)) {
            ctx.print("El plugin '" + pluginId + "' ya esta instalado.");
            if (!manager.isLoaded(pluginId)) {
                ctx.print("Reinicie FARARONI para cargarlo.");
            }
            return;
        }

        PluginInfo info = manager.getAvailablePlugins().stream()
            .filter(p -> p.id().equalsIgnoreCase(pluginId))
            .findFirst()
            .orElse(null);

        if (info == null) {
            ctx.printError("Plugin no encontrado: " + pluginId);
            ctx.print("Use /plugins list para ver plugins disponibles.");
            return;
        }

        ctx.print("");
        ctx.print("Instalando: " + info.name() + " v" + info.version());
        ctx.print("Tamano: " + info.getFormattedSize());
        ctx.print("");

        try {
            boolean success = manager.installPlugin(pluginId, progress -> {
                switch (progress.state()) {
                    case CONNECTING -> ctx.print("  Conectando al servidor...");
                    case DOWNLOADING -> {
                        int percent = (int) ((progress.downloadedBytes() * 100) / progress.totalBytes());
                        ctx.print("  Descargando: " + percent + "%");
                    }
                    case VERIFYING -> ctx.print("  Verificando integridad...");
                    case COMPLETED -> ctx.print("  Cargando plugin...");
                    case ERROR -> ctx.printError("  Error: " + progress.message());
                    default -> { }
                }
            });

            if (success) {
                ctx.print("");
                ctx.printSuccess("Plugin instalado correctamente!");
                ctx.print("");
                ctx.print("Comandos disponibles:");
                for (String cmd : info.commands()) {
                    ctx.print("  " + cmd);
                }
            } else {
                ctx.printError("Error instalando plugin");
            }

        } catch (PluginManager.PluginInstallException e) {
            ctx.printError("Error: " + e.getMessage());
        }
    }

    private void uninstallPlugin(String pluginId, ExecutionContext ctx) {
        if (pluginId == null || pluginId.isBlank()) {
            ctx.printError("Especifique el ID del plugin a desinstalar");
            ctx.print("Uso: /plugins uninstall <plugin-id>");
            return;
        }

        PluginManager manager = PluginManager.getInstance();

        if (!manager.isInstalled(pluginId)) {
            ctx.print("El plugin '" + pluginId + "' no esta instalado.");
            return;
        }

        ctx.print("Desinstalando " + pluginId + "...");

        boolean success = manager.uninstallPlugin(pluginId);

        if (success) {
            ctx.printSuccess("Plugin desinstalado correctamente");
            ctx.print("Los comandos del plugin ya no estan disponibles.");
        } else {
            ctx.printError("Error desinstalando plugin");
        }
    }

    private void updateCatalog(ExecutionContext ctx) {
        ctx.print("Actualizando catalogo de plugins...");

        PluginManager manager = PluginManager.getInstance();
        boolean success = manager.refreshCatalog();

        if (success) {
            ctx.printSuccess("Catalogo actualizado");
            listPlugins(ctx);
        } else {
            ctx.printError("Error actualizando catalogo");
            ctx.print("Verifique su conexion a internet.");
        }
    }

    private void showPluginInfo(String pluginId, ExecutionContext ctx) {
        if (pluginId == null || pluginId.isBlank()) {
            ctx.printError("Especifique el ID del plugin");
            ctx.print("Uso: /plugins info <plugin-id>");
            return;
        }

        PluginManager manager = PluginManager.getInstance();

        PluginInfo info = manager.getAvailablePlugins().stream()
            .filter(p -> p.id().equalsIgnoreCase(pluginId))
            .findFirst()
            .orElse(null);

        if (info == null) {
            ctx.printError("Plugin no encontrado: " + pluginId);
            return;
        }

        boolean installed = manager.isInstalled(pluginId);
        boolean loaded = manager.isLoaded(pluginId);

        ctx.print("");
        ctx.print("╭─────────────────────────────────────────────────────────────╮");
        ctx.print("│  " + padRight(info.name(), 56) + " │");
        ctx.print("╰─────────────────────────────────────────────────────────────╯");
        ctx.print("");
        ctx.print("  ID:          " + info.id());
        ctx.print("  Version:     " + info.version());
        ctx.print("  Tamano:      " + info.getFormattedSize());
        ctx.print("  Estado:      " + (installed ? (loaded ? "ACTIVO" : "Instalado") : "No instalado"));
        ctx.print("");
        ctx.print("  Descripcion:");
        ctx.print("    " + info.description());
        ctx.print("");
        ctx.print("  Comandos incluidos:");
        for (String cmd : info.commands()) {
            ctx.print("    " + cmd);
        }

        if (!info.dependencies().isEmpty()) {
            ctx.print("");
            ctx.print("  Dependencias:");
            for (String dep : info.dependencies()) {
                ctx.print("    - " + dep);
            }
        }

        ctx.print("");

        if (!installed) {
            ctx.print("  Para instalar: /plugins install " + info.id());
        } else if (!loaded) {
            ctx.print("  Reinicie FARARONI para cargar el plugin.");
        }
        ctx.print("");
    }

    private void showHelp(ExecutionContext ctx) {
        ctx.print("");
        ctx.print("Uso: /plugins [subcomando] [argumentos]");
        ctx.print("");
        ctx.print("Subcomandos:");
        ctx.print("  list               Lista plugins disponibles");
        ctx.print("  install <id>       Instala un plugin");
        ctx.print("  uninstall <id>     Desinstala un plugin");
        ctx.print("  update             Actualiza catalogo");
        ctx.print("  info <id>          Muestra informacion detallada");
        ctx.print("");
    }

    private String truncate(String str, int max) {
        if (str == null) return "";
        return str.length() > max ? str.substring(0, max - 3) + "..." : str;
    }

    private String padRight(String str, int length) {
        if (str == null) str = "";
        if (str.length() >= length) return str.substring(0, length);
        return str + " ".repeat(length - str.length());
    }

    @Override
    public String getExtendedHelp() {
        return """
            /plugins - Gestion de Plugins Descargables

            FARARONI soporta plugins multimodales que se descargan on-demand:

            PLUGINS DISPONIBLES:
              vision     Analisis de imagenes con LLaVA 1.5 local
                         Comandos: /vision, /vision-status, /clipboard, /ocr

              voice      Transcripcion de audio con Whisper local
                         Comandos: /voice

            SUBCOMANDOS:
              /plugins                    Lista todos los plugins
              /plugins list               Lista todos los plugins
              /plugins install <id>       Descarga e instala un plugin
              /plugins uninstall <id>     Elimina un plugin instalado
              /plugins update             Actualiza catalogo desde servidor
              /plugins info <id>          Muestra detalles de un plugin

            EJEMPLOS:
              /plugins install vision     Instala el plugin de vision
              /plugins info voice         Muestra info del plugin voice
              /plugins uninstall vision   Elimina el plugin vision

            UBICACION DE PLUGINS:
              ~/.llm-fararoni/libs/       JARs descargados
              ~/.llm-fararoni/plugins/    Cache del catalogo

            NOTAS:
              - Los plugins se descargan del servidor de Fararoni
              - Se verifica la integridad via SHA-256
              - Una vez instalados, estan disponibles inmediatamente
              - Los plugins no afectan el rendimiento hasta usarse

            Relacionados:
              /vision    Analizar imagenes (requiere plugin vision)
              /voice     Transcribir audio (requiere plugin voice)
            """;
    }
}
