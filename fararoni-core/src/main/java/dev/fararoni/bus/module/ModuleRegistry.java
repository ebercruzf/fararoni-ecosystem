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
package dev.fararoni.bus.module;

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.spi.FararoniModule;
import dev.fararoni.bus.spi.ModuleContext;
import dev.fararoni.bus.spi.ModuleHealth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ModuleRegistry {
    private static final Logger LOG = Logger.getLogger(ModuleRegistry.class.getName());

    private static final Path DEFAULT_CONFIG_PATH = Path.of(
        System.getProperty("user.home"), ".fararoni", "config"
    );

    private static final String MODULES_CONFIG_FILE = "modules.yml";

    private final SovereignEventBus bus;
    private final Path configPath;
    private final Map<String, FararoniModule> loadedModules;
    private final List<FararoniModule> startedModules;
    private final boolean hotReloadEnabled;
    private volatile boolean running = false;
    private ModuleConfigSentinel configSentinel;

    public ModuleRegistry(SovereignEventBus bus) {
        this(bus, DEFAULT_CONFIG_PATH, true);
    }

    public ModuleRegistry(SovereignEventBus bus, Path configPath) {
        this(bus, configPath, true);
    }

    public ModuleRegistry(SovereignEventBus bus, Path configPath, boolean hotReloadEnabled) {
        this.bus = bus;
        this.configPath = configPath != null ? configPath : DEFAULT_CONFIG_PATH;
        this.hotReloadEnabled = hotReloadEnabled;
        this.loadedModules = new ConcurrentHashMap<>();
        this.startedModules = Collections.synchronizedList(new ArrayList<>());
    }

    public int loadModules() {
        if (running) {
            LOG.warning("[MODULE-REGISTRY] Already running, cannot reload");
            return 0;
        }

        System.out.println("[MODULE-REGISTRY] Discovering FararoniModule implementations...");

        Map<String, Object> config = loadConfiguration();
        System.out.println("[MODULE-REGISTRY] Config loaded: " + config.keySet());

        ModuleContext context = new ModuleContext(bus, config, configPath);

        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        System.out.println("[MODULE-REGISTRY] Using ClassLoader: " + classLoader.getClass().getName());
        ServiceLoader<FararoniModule> loader = ServiceLoader.load(FararoniModule.class, classLoader);
        List<FararoniModule> discovered = new ArrayList<>();

        System.out.println("[MODULE-REGISTRY] Iterating ServiceLoader...");
        for (FararoniModule module : loader) {
            System.out.println("[MODULE-REGISTRY] Found module: " + module.getModuleId());
            discovered.add(module);
            LOG.info("[MODULE-REGISTRY] Discovered: " + module.getModuleId() +
                     " (priority: " + module.priority() + ")");
        }
        System.out.println("[MODULE-REGISTRY] Total modules found: " + discovered.size());

        if (discovered.isEmpty()) {
            System.out.println("[MODULE-REGISTRY] No modules discovered");
            return 0;
        }

        discovered.sort((a, b) -> Integer.compare(b.priority(), a.priority()));

        int loaded = 0;
        for (FararoniModule module : discovered) {
            String moduleId = module.getModuleId();

            try {
                System.out.println("[MODULE-REGISTRY] Checking if enabled: " + moduleId);
                if (!module.isEnabled()) {
                    System.out.println("[MODULE-REGISTRY] Module disabled: " + moduleId);
                    continue;
                }

                System.out.println("[MODULE-REGISTRY] Checking prerequisites: " + moduleId);
                if (!module.prerequisitesMet(context)) {
                    System.out.println("[MODULE-REGISTRY] Prerequisites not met: " + moduleId);
                    continue;
                }

                System.out.println("[MODULE-REGISTRY] Initializing: " + moduleId);
                module.initialize(context);

                loadedModules.put(moduleId, module);
                loaded++;

                System.out.println("[MODULE-REGISTRY] Initialized: " + moduleId);
            } catch (Exception e) {
                System.out.println("[MODULE-REGISTRY] FAILED to initialize " + moduleId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("[MODULE-REGISTRY] Loaded " + loaded + "/" + discovered.size() + " modules");
        return loaded;
    }

    public int startModules() {
        System.out.println("[MODULE-REGISTRY] startModules() called, loadedModules=" + loadedModules.size());
        if (running) {
            System.out.println("[MODULE-REGISTRY] Already running");
            return 0;
        }

        System.out.println("[MODULE-REGISTRY] Starting modules...");

        int started = 0;
        for (FararoniModule module : loadedModules.values()) {
            String moduleId = module.getModuleId();

            try {
                System.out.println("[MODULE-REGISTRY] Starting: " + moduleId);
                module.start();

                startedModules.add(module);
                started++;

                System.out.println("[MODULE-REGISTRY] Started: " + moduleId);
            } catch (Exception e) {
                System.out.println("[MODULE-REGISTRY] FAILED to start " + moduleId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        running = true;

        if (hotReloadEnabled) {
            startConfigSentinel();
        }

        LOG.info("[MODULE-REGISTRY] Started " + started + "/" + loadedModules.size() + " modules");
        return started;
    }

    private void startConfigSentinel() {
        if (configSentinel != null) {
            return;
        }

        configSentinel = new ModuleConfigSentinel(configPath, this::onConfigurationChanged);
        configSentinel.start();
        LOG.info("[MODULE-REGISTRY] Hot reload enabled");
    }

    private void onConfigurationChanged(Path configFile) {
        LOG.info("[MODULE-REGISTRY] Configuration changed, reloading...");

        Map<String, Object> newConfig = loadConfiguration();

        ModuleContext context = new ModuleContext(bus, newConfig, configPath);

        for (FararoniModule module : loadedModules.values()) {
            try {
                if (module instanceof ConfigReloadable reloadable) {
                    reloadable.onConfigurationReload(context);
                    LOG.info("[MODULE-REGISTRY] Reloaded config for: " + module.getModuleId());
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[MODULE-REGISTRY] Failed to reload: " + module.getModuleId(), e);
            }
        }
    }

    public interface ConfigReloadable {
        void onConfigurationReload(ModuleContext context);
    }

    public void stopModules() {
        if (!running) {
            return;
        }

        LOG.info("[MODULE-REGISTRY] Stopping modules...");

        if (configSentinel != null) {
            configSentinel.stop();
            configSentinel = null;
        }

        List<FararoniModule> toStop = new ArrayList<>(startedModules);
        Collections.reverse(toStop);

        for (FararoniModule module : toStop) {
            String moduleId = module.getModuleId();

            try {
                LOG.info("[MODULE-REGISTRY] Stopping: " + moduleId);
                module.stop();
                LOG.info("[MODULE-REGISTRY] Stopped: " + moduleId);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[MODULE-REGISTRY] Error stopping: " + moduleId, e);
            }
        }

        startedModules.clear();
        loadedModules.clear();
        running = false;

        LOG.info("[MODULE-REGISTRY] All modules stopped");
    }

    public Map<String, ModuleHealth> getHealthStatus() {
        Map<String, ModuleHealth> status = new HashMap<>();
        for (Map.Entry<String, FararoniModule> entry : loadedModules.entrySet()) {
            status.put(entry.getKey(), entry.getValue().getHealth());
        }
        return status;
    }

    public ModuleHealth getAggregateHealth() {
        if (loadedModules.isEmpty()) {
            return ModuleHealth.UNKNOWN;
        }

        ModuleHealth[] statuses = loadedModules.values().stream()
            .map(FararoniModule::getHealth)
            .toArray(ModuleHealth[]::new);

        return ModuleHealth.aggregate(statuses);
    }

    public FararoniModule getModule(String moduleId) {
        return loadedModules.get(moduleId);
    }

    public Set<String> getLoadedModuleIds() {
        return Collections.unmodifiableSet(loadedModules.keySet());
    }

    public int getModuleCount() {
        return loadedModules.size();
    }

    public boolean isRunning() {
        return running;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadConfiguration() {
        Path configFile = configPath.resolve(MODULES_CONFIG_FILE);

        if (!Files.exists(configFile)) {
            provisionDefaultConfig(configFile);
        }

        if (!Files.exists(configFile)) {
            LOG.info("[MODULE-REGISTRY] No modules.yml available (neither disk nor classpath)");
            return new HashMap<>();
        }

        try {
            com.fasterxml.jackson.dataformat.yaml.YAMLMapper yamlMapper =
                new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();

            Map<String, Object> config = yamlMapper.readValue(
                configFile.toFile(),
                Map.class
            );

            LOG.info("[MODULE-REGISTRY] Config loaded: " + configFile);
            return config;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[MODULE-REGISTRY] Failed to load modules.yml", e);
            return new HashMap<>();
        }
    }

    private void provisionDefaultConfig(Path target) {
        String resource = "default-config/" + MODULES_CONFIG_FILE;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                LOG.info("[MODULE-REGISTRY] No default " + resource + " in classpath");
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("[MODULE-REGISTRY] Auto-provisioned modules.yml -> " + target);
        } catch (IOException e) {
            LOG.warning("[MODULE-REGISTRY] Failed to provision default config: " + e.getMessage());
        }
    }
}
