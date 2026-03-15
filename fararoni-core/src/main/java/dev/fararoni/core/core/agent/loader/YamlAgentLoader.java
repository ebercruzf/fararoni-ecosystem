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
package dev.fararoni.core.core.agent.loader;

import dev.fararoni.core.core.agent.model.AgentInstanceConfig;
import dev.fararoni.core.core.agent.model.AgentTemplate;
import dev.fararoni.core.core.agent.model.MissionManifest;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class YamlAgentLoader {
    private static final Logger LOG = Logger.getLogger(YamlAgentLoader.class.getName());

    private static final Path CONFIG_BASE = Path.of(
        System.getProperty("user.home"), ".fararoni", "config"
    );

    private static final String TEMPLATES_DIR = "templates";

    private static final String MISSIONS_DIR = "missions";

    private static final String INSTANCES_DIR = "instances";

    private final Map<String, AgentTemplate> templates = new ConcurrentHashMap<>();

    private final Map<String, MissionManifest> missions = new ConcurrentHashMap<>();

    private final Map<String, AgentInstanceConfig> instances = new ConcurrentHashMap<>();

    private final ThreadLocal<Yaml> yamlParser = ThreadLocal.withInitial(() -> {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
    });

    private final List<LoadError> loadErrors = Collections.synchronizedList(new ArrayList<>());

    public int scanAll() {
        loadErrors.clear();
        int loaded = 0;

        loaded += scanTemplates();
        loaded += scanMissions();
        loaded += scanInstances();

        LOG.info(() -> String.format(
            "[YamlAgentLoader] Scan completo: %d templates, %d missions, %d instances, %d errores",
            templates.size(), missions.size(), instances.size(), loadErrors.size()
        ));

        return loaded;
    }

    public int scanTemplates() {
        Path templatesPath = CONFIG_BASE.resolve(TEMPLATES_DIR);
        return scanDirectory(templatesPath, this::loadTemplate);
    }

    public int scanMissions() {
        Path missionsPath = CONFIG_BASE.resolve(MISSIONS_DIR);
        return scanDirectory(missionsPath, this::loadMission);
    }

    public int scanInstances() {
        Path instancesPath = CONFIG_BASE.resolve(INSTANCES_DIR);
        return scanDirectory(instancesPath, this::loadInstance);
    }

    public Optional<AgentTemplate> loadTemplate(Path yamlPath) {
        try (InputStream is = Files.newInputStream(yamlPath)) {
            Map<String, Object> data = yamlParser.get().load(is);
            if (data == null) {
                recordError(yamlPath, "Archivo YAML vacio");
                return Optional.empty();
            }

            AgentTemplate template = parseTemplate(data, yamlPath);
            templates.put(template.templateId(), template);
            LOG.fine(() -> "[YamlAgentLoader] Template cargado: " + template.templateId());
            return Optional.of(template);
        } catch (IOException e) {
            recordError(yamlPath, "Error IO: " + e.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            recordError(yamlPath, "Validacion fallida: " + e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            recordError(yamlPath, "Error inesperado: " + e.getMessage());
            LOG.log(Level.WARNING, "[YamlAgentLoader] Error cargando template: " + yamlPath, e);
            return Optional.empty();
        }
    }

    public Optional<MissionManifest> loadMission(Path yamlPath) {
        try (InputStream is = Files.newInputStream(yamlPath)) {
            Map<String, Object> data = yamlParser.get().load(is);
            if (data == null) {
                recordError(yamlPath, "Archivo YAML vacio");
                return Optional.empty();
            }

            MissionManifest mission = parseMission(data, yamlPath);
            missions.put(mission.missionId(), mission);
            LOG.fine(() -> "[YamlAgentLoader] Mission cargada: " + mission.missionId());
            return Optional.of(mission);
        } catch (IOException e) {
            recordError(yamlPath, "Error IO: " + e.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            recordError(yamlPath, "Validacion fallida: " + e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            recordError(yamlPath, "Error inesperado: " + e.getMessage());
            LOG.log(Level.WARNING, "[YamlAgentLoader] Error cargando mission: " + yamlPath, e);
            return Optional.empty();
        }
    }

    public Optional<AgentInstanceConfig> loadInstance(Path yamlPath) {
        try (InputStream is = Files.newInputStream(yamlPath)) {
            Map<String, Object> data = yamlParser.get().load(is);
            if (data == null) {
                recordError(yamlPath, "Archivo YAML vacio");
                return Optional.empty();
            }

            AgentInstanceConfig instance = parseInstance(data, yamlPath);
            instances.put(instance.id(), instance);
            LOG.fine(() -> "[YamlAgentLoader] Instance cargada: " + instance.id());
            return Optional.of(instance);
        } catch (IOException e) {
            recordError(yamlPath, "Error IO: " + e.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            recordError(yamlPath, "Validacion fallida: " + e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            recordError(yamlPath, "Error inesperado: " + e.getMessage());
            LOG.log(Level.WARNING, "[YamlAgentLoader] Error cargando instance: " + yamlPath, e);
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private AgentInstanceConfig parseInstance(Map<String, Object> data, Path source) {
        String id = requireString(data, "id", source);
        String templateRef = requireString(data, "templateRef", source);

        AgentInstanceConfig.WiringConfig wiring = null;
        if (data.containsKey("wiring")) {
            wiring = parseWiringConfig((Map<String, Object>) data.get("wiring"));
        }

        AgentInstanceConfig.RoutingConfig routing = null;
        if (data.containsKey("routing")) {
            routing = parseRoutingConfig((Map<String, Object>) data.get("routing"));
        }

        Map<String, String> variables = getStringMap(data, "variables");

        return new AgentInstanceConfig(id, templateRef, wiring, routing, variables);
    }

    public Optional<AgentTemplate> getTemplate(String templateId) {
        return Optional.ofNullable(templates.get(templateId));
    }

    public Optional<MissionManifest> getMission(String missionId) {
        return Optional.ofNullable(missions.get(missionId));
    }

    public Map<String, AgentTemplate> getAllTemplates() {
        return Map.copyOf(templates);
    }

    public Map<String, MissionManifest> getAllMissions() {
        return Map.copyOf(missions);
    }

    public Optional<AgentInstanceConfig> getInstance(String instanceId) {
        return Optional.ofNullable(instances.get(instanceId));
    }

    public Map<String, AgentInstanceConfig> getAllInstances() {
        return Map.copyOf(instances);
    }

    public List<LoadError> getLoadErrors() {
        return List.copyOf(loadErrors);
    }

    public void clear() {
        templates.clear();
        missions.clear();
        instances.clear();
        loadErrors.clear();
    }

    public boolean ensureDirectoriesExist() {
        try {
            Files.createDirectories(CONFIG_BASE.resolve(TEMPLATES_DIR));
            Files.createDirectories(CONFIG_BASE.resolve(MISSIONS_DIR));
            return true;
        } catch (IOException e) {
            LOG.warning(() -> "[YamlAgentLoader] No se pudieron crear directorios: " + e.getMessage());
            return false;
        }
    }

    private AgentTemplate parseTemplate(Map<String, Object> data, Path source) {
        String templateId = requireString(data, "templateId", source);
        String roleName = requireString(data, "roleName", source);
        String systemPrompt = requireString(data, "systemPrompt", source);

        String outputJsonSchema = getString(data, "outputJsonSchema");
        List<String> capabilities = getStringList(data, "capabilities");
        Map<String, String> metadata = getStringMap(data, "metadata");

        return new AgentTemplate(
            templateId,
            roleName,
            systemPrompt,
            outputJsonSchema,
            capabilities,
            metadata
        );
    }

    private MissionManifest parseMission(Map<String, Object> data, Path source) {
        String missionId = requireString(data, "missionId", source);
        String description = getString(data, "description");
        String version = getString(data, "version");

        List<MissionManifest.AgentEntry> agents = parseAgentEntries(data, source);
        Map<String, String> metadata = getStringMap(data, "metadata");

        return new MissionManifest(
            missionId,
            description,
            version,
            agents,
            metadata
        );
    }

    @SuppressWarnings("unchecked")
    private List<MissionManifest.AgentEntry> parseAgentEntries(Map<String, Object> data, Path source) {
        Object agentsObj = data.get("agents");
        if (agentsObj == null) {
            return List.of();
        }

        if (!(agentsObj instanceof List<?> agentsList)) {
            throw new IllegalArgumentException("'agents' debe ser una lista en " + source);
        }

        List<MissionManifest.AgentEntry> entries = new ArrayList<>();
        for (Object entry : agentsList) {
            if (entry instanceof Map<?, ?> agentMap) {
                entries.add(parseAgentEntry((Map<String, Object>) agentMap, source));
            }
        }
        return entries;
    }

    @SuppressWarnings("unchecked")
    private MissionManifest.AgentEntry parseAgentEntry(Map<String, Object> data, Path source) {
        String id = requireString(data, "id", source);
        String templateRef = requireString(data, "templateRef", source);

        AgentInstanceConfig.WiringConfig wiring = null;
        if (data.containsKey("wiring")) {
            wiring = parseWiringConfig((Map<String, Object>) data.get("wiring"));
        }

        AgentInstanceConfig.RoutingConfig routing = null;
        if (data.containsKey("routing")) {
            routing = parseRoutingConfig((Map<String, Object>) data.get("routing"));
        }

        List<String> dependsOn = getStringList(data, "dependsOn");
        Map<String, String> variables = getStringMap(data, "variables");

        return new MissionManifest.AgentEntry(
            id,
            templateRef,
            wiring,
            routing,
            dependsOn,
            variables
        );
    }

    private AgentInstanceConfig.WiringConfig parseWiringConfig(Map<String, Object> data) {
        if (data == null) return null;

        List<String> inputTopics = getStringList(data, "inputTopics");
        String outputTopic = getString(data, "outputTopic");
        String deadLetterTopic = getString(data, "deadLetterTopic");

        return new AgentInstanceConfig.WiringConfig(inputTopics, outputTopic, deadLetterTopic);
    }

    private AgentInstanceConfig.RoutingConfig parseRoutingConfig(Map<String, Object> data) {
        if (data == null) return null;

        int priority = getInt(data, "priority", -1);
        int maxConcurrent = getInt(data, "maxConcurrent", -1);
        long timeoutMs = getLong(data, "timeoutMs", -1);

        return new AgentInstanceConfig.RoutingConfig(priority, maxConcurrent, timeoutMs);
    }

    private int scanDirectory(Path directory, java.util.function.Function<Path, Optional<?>> loader) {
        if (!Files.isDirectory(directory)) {
            LOG.fine(() -> "[YamlAgentLoader] Directorio no existe: " + directory);
            return 0;
        }

        int loaded = 0;
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> yamlFiles = files
                .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                .toList();

            for (Path file : yamlFiles) {
                if (loader.apply(file).isPresent()) {
                    loaded++;
                }
            }
        } catch (IOException e) {
            LOG.warning(() -> "[YamlAgentLoader] Error escaneando directorio " + directory + ": " + e.getMessage());
        }
        return loaded;
    }

    private String requireString(Map<String, Object> data, String key, Path source) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Campo requerido '" + key + "' no encontrado en " + source);
        }
        return value.toString();
    }

    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getStringMap(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }
            return result;
        }
        return Map.of();
    }

    private int getInt(Map<String, Object> data, String key, int defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number num) return num.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long getLong(Map<String, Object> data, String key, long defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number num) return num.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void recordError(Path path, String message) {
        LoadError error = new LoadError(path, message);
        loadErrors.add(error);
        LOG.warning(() -> "[YamlAgentLoader] " + error);
    }

    public record LoadError(Path path, String message) {
        @Override
        public String toString() {
            return path.getFileName() + ": " + message;
        }
    }
}
