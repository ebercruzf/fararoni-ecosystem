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
package dev.fararoni.core.core.mission.engine;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.agent.dynamic.LlmInferenceProvider;
import dev.fararoni.core.core.agents.ReactiveSwarmAgent;
import dev.fararoni.core.core.mission.model.AgentTemplate;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class AgentTemplateManager implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(AgentTemplateManager.class.getName());

    private static final String DEFAULT_AGENTS_DIR =
        System.getProperty("user.home") + "/.fararoni/config/agentes";

    private final Map<String, ReactiveSwarmAgent> activeAgents = new ConcurrentHashMap<>();

    private final Map<String, AgentTemplate> templates = new ConcurrentHashMap<>();

    private final Map<String, String> capabilityIndex = new ConcurrentHashMap<>();

    private final Path agentsDir;

    private final SovereignEventBus bus;

    private final LlmInferenceProvider llm;

    private final Yaml yaml;

    private WatchService watchService;

    private final ExecutorService watchExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public AgentTemplateManager(SovereignEventBus bus, LlmInferenceProvider llm) {
        this(Path.of(DEFAULT_AGENTS_DIR), bus, llm);
    }

    public AgentTemplateManager(Path agentsDir, SovereignEventBus bus, LlmInferenceProvider llm) {
        this.agentsDir = agentsDir;
        this.bus = bus;
        this.llm = llm;
        this.yaml = new Yaml();
        this.watchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AgentTemplateWatcher");
            t.setDaemon(true);
            return t;
        });

        LOG.info("AgentTemplateManager initializing...");
        LOG.info("Agents dir: " + agentsDir);
        LOG.info("LLM Provider: " + (llm != null ? "Injected" : "NULL (agents won't think)"));

        ensureDirectoryExists();
        loadAllAgents();
        startHotReloadWatchService();

        LOG.info("Loaded " + activeAgents.size() + " agents (Hot Reload ACTIVO)");
    }

    private void startHotReloadWatchService() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            agentsDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            );

            running.set(true);

            watchExecutor.submit(() -> {
                LOG.info("Hot Reload WatchService started for: " + agentsDir);

                while (running.get()) {
                    try {
                        WatchKey key = watchService.take();

                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();

                            if (kind == StandardWatchEventKinds.OVERFLOW) {
                                continue;
                            }

                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                            Path filename = pathEvent.context();
                            Path fullPath = agentsDir.resolve(filename);

                            String name = filename.toString();
                            if (!isValidYamlFile(name)) {
                                continue;
                            }

                            if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                                kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                                LOG.info("Hot Reload: Detected " + kind.name() +
                                         " for " + filename);

                                Thread.sleep(100);
                                processNewOrUpdatedYaml(fullPath);
                            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                handleDeletedAgent(fullPath);
                            }
                        }

                        boolean valid = key.reset();
                        if (!valid) {
                            LOG.warning("WatchKey no longer valid");
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error in WatchService loop", e);
                    }
                }

                LOG.info("Hot Reload WatchService stopped");
            });
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to start WatchService", e);
        }
    }

    private boolean isValidYamlFile(String filename) {
        if (filename.startsWith("_")) return false;
        if (filename.endsWith(".invalid")) return false;
        if (filename.endsWith(".bak")) return false;
        if (filename.endsWith(".error")) return false;
        return filename.endsWith(".yaml") || filename.endsWith(".yml");
    }

    private void processNewOrUpdatedYaml(Path yamlPath) {
        String filename = yamlPath.getFileName().toString();

        try {
            Map<String, Object> data;
            try (InputStream is = Files.newInputStream(yamlPath)) {
                data = yaml.load(is);
            }

            if (data == null || data.isEmpty()) {
                throw new InvalidAgentYamlException("Archivo vacío o inválido");
            }

            validateStructure(data, filename);
            AgentTemplate template = parseTemplate(data);

            String agentId = template.id();
            ReactiveSwarmAgent oldAgent = activeAgents.remove(agentId);

            if (oldAgent != null) {
                LOG.info("Hot Swap: Shutting down old agent '" + agentId + "'");

                AgentTemplate oldTemplate = templates.get(agentId);
                if (oldTemplate != null && oldTemplate.capabilities() != null) {
                    for (String cap : oldTemplate.capabilities()) {
                        capabilityIndex.remove(cap);
                    }
                }

                oldAgent.shutdown();
            }

            ReactiveSwarmAgent newAgent = AgentFactory.createFromTemplate(template, bus, llm);
            newAgent.start();

            activeAgents.put(agentId, newAgent);
            templates.put(agentId, template);

            for (String capability : template.capabilities()) {
                String previousOwner = capabilityIndex.put(capability, agentId);
                if (previousOwner != null && !previousOwner.equals(agentId)) {
                    LOG.warning("Capability '" + capability +
                               "' transferred from '" + previousOwner + "' to '" + agentId + "'");
                }
            }

            if (oldAgent == null) {
                try {
                    bus.publish("agency.agent.loaded",
                        SovereignEnvelope.create("agent-template-manager", agentId));
                    LOG.info("[HOT-RELOAD] Notificado agency.agent.loaded para: " + agentId);
                } catch (Exception e) {
                    LOG.warning("[HOT-RELOAD] No se pudo notificar nuevo agente: " + e.getMessage());
                }
            }

            LOG.info("Agent '" + agentId + "' " +
                     (oldAgent != null ? "HOT SWAPPED" : "CREATED") +
                     " (capabilities=" + template.capabilities() + ")");
        } catch (InvalidAgentYamlException e) {
            LOG.severe("Validation FAILED for " + filename + ": " + e.getMessage());
            quarantineFile(yamlPath, e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected error processing " + filename, e);
            quarantineFile(yamlPath, "Unexpected error: " + e.getMessage());
        }
    }

    private void handleDeletedAgent(Path yamlPath) {
        String filename = yamlPath.getFileName().toString();
        String agentId = extractAgentId(filename);

        ReactiveSwarmAgent agent = activeAgents.remove(agentId);
        AgentTemplate template = templates.remove(agentId);

        if (agent != null) {
            LOG.info("Agent deleted: Shutting down '" + agentId + "'");
            agent.shutdown();

            if (template != null && template.capabilities() != null) {
                for (String cap : template.capabilities()) {
                    capabilityIndex.remove(cap);
                }
            }

            LOG.info("Agent '" + agentId + "' removed");

            try {
                bus.publish("agency.agent.removed",
                    SovereignEnvelope.create("agent-template-manager", agentId));
                LOG.info("[HOT-RELOAD] Notificado agency.agent.removed para: " + agentId);
            } catch (Exception e) {
                LOG.warning("[HOT-RELOAD] No se pudo notificar agente eliminado: " + e.getMessage());
            }
        }
    }

    private void validateStructure(Map<String, Object> data, String filename) {
        if (!data.containsKey("id")) {
            throw new InvalidAgentYamlException("Missing required field: 'id'");
        }
        if (!data.containsKey("capabilities")) {
            throw new InvalidAgentYamlException("Missing required field: 'capabilities'");
        }

        Object capabilities = data.get("capabilities");
        if (!(capabilities instanceof List) || ((List<?>) capabilities).isEmpty()) {
            throw new InvalidAgentYamlException("'capabilities' must be a non-empty list");
        }

        if (!data.containsKey("systemPrompt")) {
            throw new InvalidAgentYamlException("Missing required field: 'systemPrompt'");
        }
    }

    private void quarantineFile(Path yamlPath, String error) {
        try {
            Path invalidPath = yamlPath.resolveSibling(yamlPath.getFileName() + ".invalid");

            if (Files.exists(invalidPath)) {
                invalidPath = yamlPath.resolveSibling(
                    yamlPath.getFileName() + "." + System.currentTimeMillis() + ".invalid"
                );
            }

            Files.move(yamlPath, invalidPath, StandardCopyOption.REPLACE_EXISTING);

            LOG.warning("Agent QUARANTINED: " + invalidPath.getFileName());

            Path errorLog = invalidPath.resolveSibling(invalidPath.getFileName() + ".error");
            String errorContent = """
                ═══════════════════════════════════════════════════════════════
                AGENTE EN CUARENTENA AgentTemplateManager
                ═══════════════════════════════════════════════════════════════
                Archivo Original: %s
                Fecha: %s
                Error: %s

                Para corregir:
                1. Edita el archivo y corrige el error
                2. Renómbralo quitando .invalid
                3. El sistema lo detectará automáticamente (Hot Reload)
                ═══════════════════════════════════════════════════════════════
                """.formatted(
                    yamlPath.getFileName(),
                    java.time.Instant.now(),
                    error
                );
            Files.writeString(errorLog, errorContent);
        } catch (IOException ex) {
            LOG.severe("Failed to quarantine file: " + ex.getMessage());
        }
    }

    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(agentsDir)) {
                Files.createDirectories(agentsDir);
                LOG.info("Created agents directory: " + agentsDir);
            }
            provisionDefaultAgents();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not create agents dir", e);
        }
    }

    private void provisionDefaultAgents() {
        String[] defaultAgents = {
            "qartermaster-prime-agent.yaml",
            "agentmail-agent.yaml",
            "commander-agent.yaml",
            "intel-agent.yaml",
            "strategist-agent.yaml",
            "blueprint-agent.yaml",
            "builder-agent.yaml",
            "sentinel-agent.yaml",
            "operator-agent.yaml",
            "notary-agent.yaml"
        };

        int provisioned = 0;
        for (String agentFile : defaultAgents) {
            Path target = agentsDir.resolve(agentFile);
            if (Files.exists(target)) continue;

            String resource = "default-config/agentes/" + agentFile;
            try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    LOG.fine("No default agent in classpath: " + resource);
                    continue;
                }
                Files.copy(in, target);
                provisioned++;
                LOG.info("[AGENT-PROVISION] Auto-provisioned: " + agentFile);
            } catch (IOException e) {
                LOG.warning("[AGENT-PROVISION] Failed to provision " + agentFile + ": " + e.getMessage());
            }
        }

        if (provisioned > 0) {
            System.out.println("[FARARONI]  Auto-provisioned " + provisioned + " agentes default desde resources");
        }
    }

    public void loadAllAgents() {
        for (ReactiveSwarmAgent agent : activeAgents.values()) {
            agent.shutdown();
        }
        activeAgents.clear();
        templates.clear();
        capabilityIndex.clear();

        if (!Files.exists(agentsDir)) {
            LOG.warning("Agents directory does not exist: " + agentsDir);
            return;
        }

        try (Stream<Path> paths = Files.list(agentsDir)) {
            paths.filter(p -> isValidYamlFile(p.getFileName().toString()))
                 .forEach(this::processNewOrUpdatedYaml);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to list agents", e);
        }
    }

    @SuppressWarnings("unchecked")
    private AgentTemplate parseTemplate(Map<String, Object> data) {
        String id = (String) data.get("id");
        String name = (String) data.getOrDefault("name", id);
        String role = (String) data.getOrDefault("role", id);
        String systemPrompt = (String) data.get("systemPrompt");

        List<String> capabilities = new ArrayList<>();
        Object capsObj = data.get("capabilities");
        if (capsObj instanceof List<?> capsList) {
            for (Object cap : capsList) {
                if (cap != null) {
                    capabilities.add(cap.toString());
                }
            }
        }

        Map<String, String> prompts = new HashMap<>();
        if (data.containsKey("prompts") && data.get("prompts") instanceof Map) {
            Map<String, Object> promptsData = (Map<String, Object>) data.get("prompts");
            for (Map.Entry<String, Object> entry : promptsData.entrySet()) {
                prompts.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        List<String> allowedTools = new ArrayList<>();
        if (data.containsKey("allowedTools") && data.get("allowedTools") instanceof List) {
            List<?> toolsList = (List<?>) data.get("allowedTools");
            for (Object tool : toolsList) {
                allowedTools.add(String.valueOf(tool));
            }
        }

        AgentTemplate.CompensationConfig compensation = null;
        if (data.containsKey("compensation") && data.get("compensation") instanceof Map) {
            Map<String, Object> compData = (Map<String, Object>) data.get("compensation");
            boolean enabled = Boolean.TRUE.equals(compData.get("enabled"));
            String strategy = (String) compData.getOrDefault("strategy", "LIFO");
            compensation = new AgentTemplate.CompensationConfig(enabled, strategy, List.of());
        }

        List<Integer> defconActivation = new ArrayList<>();
        if (data.containsKey("defconActivation") && data.get("defconActivation") instanceof List) {
            List<?> defconList = (List<?>) data.get("defconActivation");
            for (Object level : defconList) {
                if (level instanceof Number) {
                    defconActivation.add(((Number) level).intValue());
                }
            }
        }

        Map<String, Object> metadata = new HashMap<>();
        if (data.containsKey("metadata") && data.get("metadata") instanceof Map) {
            metadata = (Map<String, Object>) data.get("metadata");
        }

        AgentTemplate.StreamingConfig streamingConfig = null;
        if (data.containsKey("streamingConfig") && data.get("streamingConfig") instanceof Map) {
            Map<String, Object> streamData = (Map<String, Object>) data.get("streamingConfig");
            boolean enabled = Boolean.TRUE.equals(streamData.get("enabled"));
            String mode = (String) streamData.getOrDefault("mode", AgentTemplate.StreamingConfig.MODE_BATCH);
            boolean parallelWrites = Boolean.TRUE.equals(streamData.get("parallelWrites"));
            int maxRetries = streamData.containsKey("maxRetries")
                ? ((Number) streamData.get("maxRetries")).intValue() : 2;
            int timeoutPerFile = streamData.containsKey("timeoutPerFile")
                ? ((Number) streamData.get("timeoutPerFile")).intValue() : 30000;

            streamingConfig = new AgentTemplate.StreamingConfig(
                enabled, mode, parallelWrites, maxRetries, timeoutPerFile
            );
            LOG.fine("Parsed streamingConfig for '" + id + "': enabled=" +
                     enabled + ", mode=" + mode);
        }

        AgentTemplate.ValidationPolicy validationPolicy = null;
        if (data.containsKey("validationPolicy") && data.get("validationPolicy") instanceof Map) {
            Map<String, Object> valData = (Map<String, Object>) data.get("validationPolicy");
            boolean enabled = Boolean.TRUE.equals(valData.get("enabled"));

            List<String> prohibitedRegex = new ArrayList<>();
            if (valData.containsKey("prohibitedRegex") && valData.get("prohibitedRegex") instanceof List) {
                for (Object item : (List<?>) valData.get("prohibitedRegex")) {
                    if (item != null) prohibitedRegex.add(item.toString());
                }
            }

            List<String> requiredPatterns = new ArrayList<>();
            if (valData.containsKey("requiredPatterns") && valData.get("requiredPatterns") instanceof List) {
                for (Object item : (List<?>) valData.get("requiredPatterns")) {
                    if (item != null) requiredPatterns.add(item.toString());
                }
            }

            int maxFileSize = valData.containsKey("maxFileSize")
                ? ((Number) valData.get("maxFileSize")).intValue() : 100_000;

            validationPolicy = new AgentTemplate.ValidationPolicy(
                enabled, prohibitedRegex, requiredPatterns, maxFileSize
            );
            LOG.fine("Parsed validationPolicy for '" + id + "': enabled=" +
                     enabled + ", prohibitions=" + prohibitedRegex.size() +
                     ", requirements=" + requiredPatterns.size());
        }

        return new AgentTemplate(
            id, name, role, capabilities, systemPrompt,
            prompts, allowedTools, compensation, defconActivation, metadata,
            streamingConfig, validationPolicy
        );
    }

    private String extractAgentId(String filename) {
        return filename.replace(".yaml", "")
                       .replace(".yml", "")
                       .replace("-agent", "");
    }

    public Optional<ReactiveSwarmAgent> getAgent(String agentId) {
        return Optional.ofNullable(activeAgents.get(agentId));
    }

    public Optional<ReactiveSwarmAgent> getAgentByCapability(String capability) {
        String agentId = capabilityIndex.get(capability);
        if (agentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeAgents.get(agentId));
    }

    public boolean hasCapability(String capability) {
        return capabilityIndex.containsKey(capability);
    }

    public Set<String> listCapabilities() {
        return Set.copyOf(capabilityIndex.keySet());
    }

    public List<String> listAgentIds() {
        return List.copyOf(activeAgents.keySet());
    }

    public int agentCount() {
        return activeAgents.size();
    }

    public Path getAgentsDir() {
        return agentsDir;
    }

    public AgentTemplate getTemplate(String agentId) {
        return templates.get(agentId);
    }

    public boolean removeAgentById(String agentId) {
        if (agentId == null || agentId.isBlank()) return false;

        ReactiveSwarmAgent agent = activeAgents.remove(agentId);
        AgentTemplate template = templates.remove(agentId);

        if (agent == null && template == null) {
            LOG.info("[REMOVE] Agente '" + agentId + "' no encontrado en ATM");
            return false;
        }

        if (agent != null) {
            LOG.info("[REMOVE] Shutting down agent '" + agentId + "'");
            agent.shutdown();
        }

        if (template != null && template.capabilities() != null) {
            for (String cap : template.capabilities()) {
                capabilityIndex.remove(cap);
            }
        }

        try {
            bus.publish("agency.agent.removed",
                SovereignEnvelope.create("agent-template-manager", agentId));
            LOG.info("[REMOVE] Notificado agency.agent.removed para: " + agentId);
        } catch (Exception e) {
            LOG.warning("[REMOVE] No se pudo notificar agente eliminado: " + e.getMessage());
        }

        deleteAgentYamlFromDisk(agentId);

        LOG.info("[REMOVE] Agente '" + agentId + "' eliminado completamente");
        return true;
    }

    private void deleteAgentYamlFromDisk(String agentId) {
        String[] patterns = {
            agentId + "-agent.yaml",
            agentId + "-agent.yml",
            agentId + ".yaml",
            agentId + ".yml"
        };

        for (String pattern : patterns) {
            Path yamlFile = agentsDir.resolve(pattern);
            try {
                if (Files.exists(yamlFile)) {
                    Files.delete(yamlFile);
                    LOG.info("[REMOVE] YAML eliminado: " + yamlFile);
                    return;
                }
            } catch (IOException e) {
                LOG.warning("[REMOVE] Error borrando " + yamlFile + ": " + e.getMessage());
            }
        }

        LOG.fine("[REMOVE] No se encontro YAML en disco para agente '" + agentId + "'");
    }

    @Override
    public void close() {
        LOG.info("Shutting down AgentTemplateManager...");

        running.set(false);

        for (ReactiveSwarmAgent agent : activeAgents.values()) {
            try {
                agent.shutdown();
            } catch (Exception e) {
                LOG.warning("Error shutting down agent: " + e.getMessage());
            }
        }
        activeAgents.clear();
        templates.clear();
        capabilityIndex.clear();

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error closing WatchService", e);
            }
        }

        watchExecutor.shutdownNow();

        LOG.info("AgentTemplateManager shutdown complete");
    }

    public static class InvalidAgentYamlException extends RuntimeException {
        public InvalidAgentYamlException(String message) {
            super(message);
        }
    }
}
