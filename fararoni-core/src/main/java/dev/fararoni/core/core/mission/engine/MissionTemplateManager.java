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

import dev.fararoni.core.core.mission.model.MissionStep;
import dev.fararoni.core.core.mission.model.MissionTemplate;
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
public class MissionTemplateManager implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(MissionTemplateManager.class.getName());

    private static final String DEFAULT_TEMPLATES_DIR =
        System.getProperty("user.home") + "/.fararoni/config/missions";

    private final Map<String, MissionTemplate> activeTemplates = new ConcurrentHashMap<>();

    private final Path templatesDir;

    private final Yaml yaml;

    private WatchService watchService;

    private final ExecutorService watchExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public MissionTemplateManager() {
        this(Path.of(DEFAULT_TEMPLATES_DIR));
    }

    public MissionTemplateManager(Path templatesDir) {
        this.templatesDir = templatesDir;
        this.yaml = new Yaml();
        this.watchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MissionTemplateWatcher");
            t.setDaemon(true);
            return t;
        });

        LOG.info("[MissionTemplateManager] Initializing...");
        LOG.info("[MissionTemplateManager] Templates dir: " + templatesDir);

        ensureDirectoryExists();
        loadAllTemplates();
        startHotReloadWatchService();

        LOG.info("[MissionTemplateManager] Loaded " + activeTemplates.size() + " mission templates (Hot Reload ACTIVO)");
    }

    private void startHotReloadWatchService() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            templatesDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            );

            running.set(true);

            watchExecutor.submit(() -> {
                LOG.info("[MissionTemplateManager] Hot Reload WatchService started for: " + templatesDir);

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
                            Path fullPath = templatesDir.resolve(filename);

                            String name = filename.toString();
                            if (!isValidYamlFile(name)) {
                                continue;
                            }

                            if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                                kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                                LOG.info("[MissionTemplateManager] Hot Reload: Detected " + kind.name() +
                                         " for " + filename);

                                Thread.sleep(100);

                                processNewOrUpdatedYaml(fullPath);
                            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                handleDeletedTemplate(fullPath);
                            }
                        }

                        boolean valid = key.reset();
                        if (!valid) {
                            LOG.warning("[MissionTemplateManager] WatchKey no longer valid");
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "[MissionTemplateManager] Error in WatchService loop", e);
                    }
                }

                LOG.info("[MissionTemplateManager] Hot Reload WatchService stopped");
            });
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "[MissionTemplateManager] Failed to start WatchService", e);
        }
    }

    private boolean isValidYamlFile(String filename) {
        if (filename.startsWith("_")) return false;
        if (filename.endsWith(".invalid")) return false;
        if (filename.endsWith(".bak")) return false;
        return filename.endsWith(".yaml") || filename.endsWith(".yml");
    }

    private void handleDeletedTemplate(Path yamlPath) {
        String filename = yamlPath.getFileName().toString();
        String missionId = filename.replace(".yaml", "").replace(".yml", "");

        String removedId = null;
        for (String id : activeTemplates.keySet()) {
            if (id.equals(missionId) || id.equalsIgnoreCase(missionId)) {
                removedId = id;
                break;
            }
        }

        if (removedId != null) {
            activeTemplates.remove(removedId);
            LOG.info("[MissionTemplateManager] Hot Reload: Removed template '" + removedId + "'");
        }
    }

    private void processNewOrUpdatedYaml(Path yamlPath) {
        String filename = yamlPath.getFileName().toString();

        try {
            Map<String, Object> data;
            try (InputStream is = Files.newInputStream(yamlPath)) {
                data = yaml.load(is);
            }

            if (data == null || data.isEmpty()) {
                throw new InvalidMissionYamlException("Archivo vacío o inválido");
            }

            validateStructure(data, filename);

            MissionTemplate newTemplate = parseTemplate(data);
            validateGraphIntegrity(newTemplate);

            activeTemplates.put(newTemplate.missionId(), newTemplate);
            LOG.info("[MissionTemplateManager] Template '" + newTemplate.missionId() +
                     "' cargado/actualizado EXITOSAMENTE (" + newTemplate.stepCount() + " pasos)");
        } catch (InvalidMissionYamlException | InvalidMissionGraphException e) {
            LOG.severe("[MissionTemplateManager] Validacion FALLIDA para " + filename + ": " + e.getMessage());
            quarantineFile(yamlPath, e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[MissionTemplateManager] Error inesperado procesando " + filename, e);
            quarantineFile(yamlPath, "Error inesperado: " + e.getMessage());
        }
    }

    private void validateStructure(Map<String, Object> data, String filename) {
        if (!data.containsKey("missionId")) {
            throw new InvalidMissionYamlException("Falta campo obligatorio: 'missionId'");
        }
        if (!data.containsKey("initialStepId")) {
            throw new InvalidMissionYamlException("Falta campo obligatorio: 'initialStepId'");
        }
        if (!data.containsKey("steps")) {
            throw new InvalidMissionYamlException("Falta campo obligatorio: 'steps'");
        }

        Object steps = data.get("steps");
        if (!(steps instanceof Map) || ((Map<?, ?>) steps).isEmpty()) {
            throw new InvalidMissionYamlException("'steps' debe ser un mapa no vacío");
        }

        String initialStepId = (String) data.get("initialStepId");
        @SuppressWarnings("unchecked")
        Map<String, Object> stepsMap = (Map<String, Object>) steps;
        if (!stepsMap.containsKey(initialStepId)) {
            throw new InvalidMissionYamlException(
                "initialStepId '" + initialStepId + "' no existe en steps"
            );
        }
    }

    private void validateGraphIntegrity(MissionTemplate template) {
        Map<String, MissionStep> steps = template.steps();

        if (steps == null || steps.isEmpty()) {
            throw new InvalidMissionGraphException("La misión no tiene pasos definidos");
        }

        Set<String> validTargets = new HashSet<>(steps.keySet());
        validTargets.add(MissionStep.END_SUCCESS);
        validTargets.add(MissionStep.END_FAILURE);
        validTargets.add(MissionStep.END_ROLLBACK);

        for (MissionStep step : steps.values()) {
            Map<String, String> transitions = step.transitions();

            if (transitions == null || transitions.isEmpty()) {
                throw new InvalidMissionGraphException(
                    "Paso '" + step.stepId() + "' no tiene transiciones definidas"
                );
            }

            for (Map.Entry<String, String> transition : transitions.entrySet()) {
                String transitionType = transition.getKey();
                String targetStepId = transition.getValue();

                if (targetStepId == null || targetStepId.isBlank()) {
                    throw new InvalidMissionGraphException(
                        "Paso '" + step.stepId() + "' tiene transición '" +
                        transitionType + "' vacía"
                    );
                }

                if (!validTargets.contains(targetStepId)) {
                    throw new InvalidMissionGraphException(
                        "Paso '" + step.stepId() + "' tiene transición '" +
                        transitionType + "' apuntando a paso inexistente: '" + targetStepId + "'"
                    );
                }
            }
        }

        detectPotentialCycles(template);

        LOG.fine("[MissionTemplateManager] Grafo DAG validado para: " + template.missionId());
    }

    private void detectPotentialCycles(MissionTemplate template) {
        for (String stepId : template.steps().keySet()) {
            Set<String> visited = new HashSet<>();
            if (hasCycle(template, stepId, visited)) {
                MissionStep step = template.steps().get(stepId);
                if (!step.compensationStep()) {
                    LOG.warning("[MissionTemplateManager] Posible ciclo detectado desde paso '" +
                               stepId + "' en mision '" + template.missionId() + "'");
                }
            }
        }
    }

    private boolean hasCycle(MissionTemplate template, String startStep, Set<String> visited) {
        if (visited.contains(startStep)) {
            return true;
        }

        MissionStep step = template.steps().get(startStep);
        if (step == null || step.isTerminal()) {
            return false;
        }

        visited.add(startStep);

        for (String target : step.transitions().values()) {
            if (!target.startsWith("end_") && template.steps().containsKey(target)) {
                if (hasCycle(template, target, new HashSet<>(visited))) {
                    return true;
                }
            }
        }

        return false;
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

            LOG.warning("[MissionTemplateManager] Archivo movido a CUARENTENA: " + invalidPath.getFileName());
            LOG.warning("[MissionTemplateManager] Razon: " + error);

            Path errorLog = invalidPath.resolveSibling(invalidPath.getFileName() + ".error");
            String errorContent = """
                ═══════════════════════════════════════════════════════════════
                ARCHIVO EN CUARENTENA MissionTemplateManager
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
            LOG.severe("[MissionTemplateManager] No se pudo mover archivo a cuarentena: " + ex.getMessage());
        }
    }

    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(templatesDir)) {
                Files.createDirectories(templatesDir);
                LOG.info("[MissionTemplateManager] Created templates directory: " + templatesDir);
                createExampleTemplate();
            }
            copyDefaultTemplatesIfMissing();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[MissionTemplateManager] Could not create templates dir", e);
        }
    }

    private void copyDefaultTemplatesIfMissing() {
        String[] defaultTemplates = {
            "mission-defcon1-enterprise.yaml",
            "mission-defcon3-standard.yaml",
            "mission-defcon5-simple.yaml"
        };

        for (String templateName : defaultTemplates) {
            Path targetPath = templatesDir.resolve(templateName);
            if (Files.exists(targetPath)) {
                continue;
            }

            String resourcePath = "default-config/missions/" + templateName;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    LOG.warning("[MissionTemplateManager] Default template not found in classpath: " + resourcePath);
                    continue;
                }
                Files.copy(is, targetPath);
                LOG.info("[MissionTemplateManager] Copied default template: " + templateName);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "[MissionTemplateManager] Failed to copy default template: " + templateName, e);
            }
        }
    }

    private void createExampleTemplate() {
        String exampleYaml = """
            # ═══════════════════════════════════════════════════════════════════
            # Ejemplo de Misión DAG
            # Este archivo sirve como referencia. Los archivos que empiezan con _
            # son ignorados por el sistema.
            # ═══════════════════════════════════════════════════════════════════

            missionId: example-development-flow
            description: "Flujo de ejemplo: análisis → implementación → testing"
            maxIterations: 10
            initialStepId: analyze

            steps:
              analyze:
                requiredCapability: code_analysis
                transitions:
                  success: implement
                  failure: end_failure

              implement:
                requiredCapability: code_generation
                transitions:
                  success: test
                  failure: rollback_implement

              test:
                requiredCapability: testing
                transitions:
                  success: end_success
                  failure: rollback_implement

              rollback_implement:
                requiredCapability: code_generation
                compensationStep: true
                transitions:
                  success: end_rollback
                  failure: end_failure

            # ═══════════════════════════════════════════════════════════════════
            # NOTAS:
            # - Los pasos terminales son: end_success, end_failure, end_rollback
            # - compensationStep: true marca pasos de rollback (Saga Pattern)
            # - requiredCapability debe coincidir con una capability de agente
            # - Las transiciones 'success' y 'failure' son obligatorias
            # ═══════════════════════════════════════════════════════════════════
            """;

        Path examplePath = templatesDir.resolve("_example-development-flow.yaml");
        try {
            Files.writeString(examplePath, exampleYaml);
            LOG.info("[MissionTemplateManager] Created example template: " + examplePath);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[MissionTemplateManager] Could not create example template", e);
        }
    }

    public void loadAllTemplates() {
        activeTemplates.clear();

        if (!Files.exists(templatesDir)) {
            LOG.warning("[MissionTemplateManager] Templates directory does not exist: " + templatesDir);
            return;
        }

        try (Stream<Path> paths = Files.list(templatesDir)) {
            paths.filter(p -> isValidYamlFile(p.getFileName().toString()))
                 .forEach(this::processNewOrUpdatedYaml);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "[MissionTemplateManager] Failed to list templates", e);
        }
    }

    @SuppressWarnings("unchecked")
    private MissionTemplate parseTemplate(Map<String, Object> data) {
        String missionId = (String) data.get("missionId");
        String description = (String) data.getOrDefault("description", "");
        int maxIterations = ((Number) data.getOrDefault("maxIterations",
            MissionTemplate.DEFAULT_MAX_ITERATIONS)).intValue();
        String initialStepId = (String) data.get("initialStepId");

        Map<String, Object> stepsData = (Map<String, Object>) data.get("steps");
        Map<String, MissionStep> steps = new HashMap<>();

        for (Map.Entry<String, Object> entry : stepsData.entrySet()) {
            String stepId = entry.getKey();
            Map<String, Object> stepData = (Map<String, Object>) entry.getValue();
            MissionStep step = parseStep(stepId, stepData);
            steps.put(stepId, step);
        }

        int minBlueprintFiles = ((Number) data.getOrDefault("minBlueprintFiles",
            MissionTemplate.DEFAULT_MIN_BLUEPRINT_FILES)).intValue();

        return new MissionTemplate(missionId, description, maxIterations, initialStepId, steps, minBlueprintFiles);
    }

    @SuppressWarnings("unchecked")
    private MissionStep parseStep(String stepId, Map<String, Object> data) {
        String requiredCapability = (String) data.get("requiredCapability");
        String systemPromptOverride = (String) data.get("systemPromptOverride");
        Map<String, String> transitions = (Map<String, String>) data.get("transitions");
        boolean compensationStep = Boolean.TRUE.equals(data.get("compensationStep"));

        int maxRetries = MissionStep.DEFAULT_MAX_RETRIES;
        if (data.containsKey("maxRetries")) {
            Object maxRetriesObj = data.get("maxRetries");
            if (maxRetriesObj instanceof Number) {
                maxRetries = ((Number) maxRetriesObj).intValue();
            }
        }

        return new MissionStep(stepId, requiredCapability, systemPromptOverride,
                               transitions, compensationStep, maxRetries);
    }

    public Optional<MissionTemplate> getTemplate(String missionId) {
        return Optional.ofNullable(activeTemplates.get(missionId));
    }

    public MissionTemplate getTemplateOrThrow(String missionId) {
        return getTemplate(missionId).orElseThrow(() ->
            new IllegalArgumentException("Mission template not found: " + missionId));
    }

    public boolean hasTemplate(String missionId) {
        return activeTemplates.containsKey(missionId);
    }

    public List<String> listTemplateIds() {
        return List.copyOf(activeTemplates.keySet());
    }

    public int templateCount() {
        return activeTemplates.size();
    }

    public java.util.List<String> getAvailableTemplateIds() {
        return new java.util.ArrayList<>(activeTemplates.keySet());
    }

    public void reloadTemplates() {
        LOG.info("[MissionTemplateManager] Forcing manual reload of templates...");
        loadAllTemplates();
        LOG.info("[MissionTemplateManager] Reloaded " + activeTemplates.size() + " templates");
    }

    public Path getTemplatesDir() {
        return templatesDir;
    }

    @Override
    public void close() {
        LOG.info("[MissionTemplateManager] Shutting down...");

        running.set(false);

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "[MissionTemplateManager] Error closing WatchService", e);
            }
        }

        watchExecutor.shutdownNow();

        LOG.info("[MissionTemplateManager] Shutdown complete");
    }

    public static class InvalidMissionYamlException extends RuntimeException {
        public InvalidMissionYamlException(String message) {
            super(message);
        }
    }

    public static class InvalidMissionGraphException extends RuntimeException {
        public InvalidMissionGraphException(String message) {
            super(message);
        }
    }
}
