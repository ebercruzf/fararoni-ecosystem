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
package dev.fararoni.core.core.command;

import dev.fararoni.bus.agent.api.command.CommandCategory;
import dev.fararoni.bus.agent.api.command.ConsoleCommand;
import dev.fararoni.core.cli.CommandProvider;
import dev.fararoni.core.core.plugin.PluginInfo;
import dev.fararoni.core.core.plugin.PluginManager;
import dev.fararoni.core.core.plugin.StubCommand;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class CommandRegistry {

    private static final Logger LOGGER = Logger.getLogger(CommandRegistry.class.getName());
    private static final String LOG_PREFIX = "[CommandRegistry] ";

    private static volatile CommandRegistry INSTANCE;

    private final Map<String, ConsoleCommand> commandMap;

    private final List<ConsoleCommand> allCommands;

    private final Map<String, String> downloadableCommands;

    private volatile boolean loaded = false;

    private CommandRegistry() {
        this.commandMap = new ConcurrentHashMap<>();
        this.allCommands = new ArrayList<>();
        this.downloadableCommands = new ConcurrentHashMap<>();
    }

    public static CommandRegistry getInstance() {
        if (INSTANCE == null) {
            synchronized (CommandRegistry.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CommandRegistry();
                }
            }
        }
        return INSTANCE;
    }

    public synchronized int reloadCommands() {
        LOGGER.info(LOG_PREFIX + "Iniciando carga de comandos...");

        commandMap.clear();
        allCommands.clear();

        int totalLoaded = 0;

        try {
            ServiceLoader<CommandProvider> loader = ServiceLoader.load(CommandProvider.class);

            for (CommandProvider provider : loader) {
                if (!provider.isEnabled()) {
                    LOGGER.fine(LOG_PREFIX + "Provider deshabilitado: " + provider.getProviderName());
                    continue;
                }

                try {
                    provider.initialize();
                    List<ConsoleCommand> commands = provider.provideConsoleCommands();

                    LOGGER.info(LOG_PREFIX + "Cargando " + commands.size() +
                        " comandos de " + provider.getProviderName() +
                        " v" + provider.getVersion());

                    for (ConsoleCommand cmd : commands) {
                        if (!cmd.isEnabled()) {
                            LOGGER.fine(LOG_PREFIX + "Comando deshabilitado: " + cmd.getTrigger());
                            continue;
                        }

                        registerCommand(cmd);
                        totalLoaded++;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                        LOG_PREFIX + "Error cargando provider " + provider.getProviderName(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, LOG_PREFIX + "Error fatal cargando comandos", e);
        }

        registerDownloadableCommands();

        loaded = true;
        LOGGER.info(LOG_PREFIX + "Total comandos registrados via SPI: " + totalLoaded);

        return totalLoaded;
    }

    private void registerDownloadableCommands() {
        try {
            PluginManager pluginManager = PluginManager.getInstance();

            for (PluginInfo plugin : pluginManager.getAvailablePlugins()) {
                if (pluginManager.isLoaded(plugin.id())) {
                    pluginManager.getPluginCommands().forEach((trigger, cmd) -> {
                        if (!commandMap.containsKey(trigger.toLowerCase())) {
                            commandMap.put(trigger.toLowerCase(), cmd);
                            LOGGER.fine(LOG_PREFIX + "Comando de plugin registrado: " + trigger);
                        }
                    });
                    continue;
                }

                for (String trigger : plugin.commands()) {
                    String normalizedTrigger = trigger.toLowerCase();

                    if (commandMap.containsKey(normalizedTrigger)) {
                        continue;
                    }

                    downloadableCommands.put(normalizedTrigger, plugin.id());

                    StubCommand stub = new StubCommand(trigger, plugin);
                    commandMap.put(normalizedTrigger, stub);
                    allCommands.add(stub);

                    LOGGER.fine(LOG_PREFIX + "StubCommand registrado: " + trigger +
                        " (plugin: " + plugin.id() + ")");
                }
            }

            LOGGER.info(LOG_PREFIX + "Comandos descargables registrados: " +
                downloadableCommands.size());

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, LOG_PREFIX +
                "Error registrando comandos descargables", e);
        }
    }

    private void registerCommand(ConsoleCommand cmd) {
        String trigger = cmd.getTrigger().toLowerCase();

        if (commandMap.containsKey(trigger)) {
            LOGGER.warning(LOG_PREFIX + "Conflicto: " + trigger +
                " ya registrado. Se usara el primero cargado.");
            return;
        }

        commandMap.put(trigger, cmd);
        allCommands.add(cmd);

        for (String alias : cmd.getAliases()) {
            String normalizedAlias = alias.toLowerCase();
            if (!commandMap.containsKey(normalizedAlias)) {
                commandMap.put(normalizedAlias, cmd);
            } else {
                LOGGER.fine(LOG_PREFIX + "Alias ignorado (conflicto): " + normalizedAlias);
            }
        }

        LOGGER.fine(LOG_PREFIX + "Registrado: " + trigger +
            (cmd.getAliases().length > 0 ? " (aliases: " + String.join(", ", cmd.getAliases()) + ")" : ""));
    }

    public Optional<ConsoleCommand> findCommand(String trigger) {
        ensureLoaded();
        return Optional.ofNullable(commandMap.get(trigger.toLowerCase()));
    }

    public List<ConsoleCommand> getAllCommands() {
        ensureLoaded();
        return Collections.unmodifiableList(allCommands);
    }

    public Map<CommandCategory, List<ConsoleCommand>> getCommandsByCategory() {
        ensureLoaded();
        return allCommands.stream()
            .collect(Collectors.groupingBy(
                ConsoleCommand::getCategory,
                () -> new TreeMap<>(Comparator.comparingInt(CommandCategory::getOrder)),
                Collectors.toList()
            ));
    }

    public boolean hasCommand(String trigger) {
        ensureLoaded();
        return commandMap.containsKey(trigger.toLowerCase());
    }

    public int size() {
        ensureLoaded();
        return allCommands.size();
    }

    private void ensureLoaded() {
        if (!loaded) {
            reloadCommands();
        }
    }

    public synchronized void clear() {
        commandMap.clear();
        allCommands.clear();
        downloadableCommands.clear();
        loaded = false;
    }

    public boolean isDownloadableCommand(String trigger) {
        ensureLoaded();
        return downloadableCommands.containsKey(trigger.toLowerCase());
    }

    public Optional<String> getPluginIdForCommand(String trigger) {
        ensureLoaded();
        return Optional.ofNullable(downloadableCommands.get(trigger.toLowerCase()));
    }

    public void reloadPluginCommands(String pluginId) {
        PluginManager pluginManager = PluginManager.getInstance();

        if (!pluginManager.isLoaded(pluginId)) {
            return;
        }

        PluginInfo info = pluginManager.getAvailablePlugins().stream()
            .filter(p -> p.id().equalsIgnoreCase(pluginId))
            .findFirst()
            .orElse(null);

        if (info == null) {
            return;
        }

        for (String trigger : info.commands()) {
            String normalized = trigger.toLowerCase();

            ConsoleCommand existing = commandMap.get(normalized);
            if (existing instanceof StubCommand) {
                commandMap.remove(normalized);
                allCommands.remove(existing);
            }

            ConsoleCommand real = pluginManager.getPluginCommand(trigger).orElse(null);
            if (real != null) {
                commandMap.put(normalized, real);
                allCommands.add(real);
                downloadableCommands.remove(normalized);

                LOGGER.info(LOG_PREFIX + "Comando de plugin activado: " + trigger);
            }
        }
    }

    public String generateHelpText() {
        ensureLoaded();

        if (allCommands.isEmpty()) {
            return "No hay comandos dinamicos registrados.";
        }

        StringBuilder help = new StringBuilder();
        help.append("\nComandos Dinamicos:\n");
        help.append("===================\n\n");

        Map<CommandCategory, List<ConsoleCommand>> byCategory = getCommandsByCategory();

        for (Map.Entry<CommandCategory, List<ConsoleCommand>> entry : byCategory.entrySet()) {
            CommandCategory category = entry.getKey();
            List<ConsoleCommand> commands = entry.getValue();

            help.append(category.getDisplayName()).append(":\n");

            for (ConsoleCommand cmd : commands) {
                String trigger = cmd.getTrigger();
                String desc = cmd.getDescription();
                String enterprise = cmd.requiresEnterprise() ? " [Enterprise]" : "";

                help.append(String.format("  %-15s %s%s%n", trigger, desc, enterprise));
            }
            help.append("\n");
        }

        return help.toString();
    }
}
