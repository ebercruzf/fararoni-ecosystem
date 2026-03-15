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
package dev.fararoni.core.core.spi;

import dev.fararoni.bus.agent.api.ToolSkill;
import dev.fararoni.bus.agent.api.spi.SkillModule;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SkillRegistry {
    private static final Logger LOG = Logger.getLogger(SkillRegistry.class.getName());

    private final Map<String, ToolSkill> skills = new ConcurrentHashMap<>();
    private final List<SkillModule> loadedModules = new ArrayList<>();
    private volatile boolean initialized = false;

    public SkillRegistry() {
    }

    public synchronized SkillRegistry loadAllSkills() {
        if (initialized) {
            LOG.fine("[SkillRegistry] Already initialized, skipping");
            return this;
        }

        LOG.info("[SkillRegistry] Discovering skill modules...");

        try {
            ServiceLoader<SkillModule> loader = ServiceLoader.load(SkillModule.class);

            List<SkillModule> modules = new ArrayList<>();
            loader.forEach(modules::add);
            modules.sort(Comparator.comparingInt(SkillModule::priority).reversed());

            LOG.info(() -> String.format("[SkillRegistry] Found %d modules", modules.size()));

            for (SkillModule module : modules) {
                loadModule(module);
            }

            initialized = true;
            LOG.info(() -> String.format(
                "[SkillRegistry] Loaded %d modules with %d total skills",
                loadedModules.size(),
                skills.size()
            ));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[SkillRegistry] Failed to load skill modules", e);
        }

        return this;
    }

    private void loadModule(SkillModule module) {
        String moduleName = module.getModuleName();

        try {
            if (!module.isEnabled()) {
                LOG.info(() -> String.format(
                    "[SkillRegistry] Module disabled: %s v%s",
                    moduleName,
                    module.getVersion()
                ));
                return;
            }

            module.initialize();

            List<ToolSkill> providedSkills = module.provideSkills();
            if (providedSkills == null || providedSkills.isEmpty()) {
                LOG.fine(() -> "[SkillRegistry] Module " + moduleName + " provides no skills");
                return;
            }

            int registered = 0;
            int skipped = 0;
            for (ToolSkill skill : providedSkills) {
                if (skill == null) continue;

                String skillName = skill.getSkillName();
                if (skillName == null || skillName.isBlank()) {
                    LOG.warning("[SkillRegistry] Skill with null/blank name from " + moduleName);
                    continue;
                }

                if (skills.containsKey(skillName)) {
                    LOG.fine(() -> String.format(
                        "[SkillRegistry] Skill '%s' already registered, skipping from %s",
                        skillName,
                        moduleName
                    ));
                    skipped++;
                } else {
                    skills.put(skillName, skill);
                    registered++;
                }
            }

            loadedModules.add(module);

            final int finalRegistered = registered;
            final int finalSkipped = skipped;
            LOG.info(() -> String.format(
                "[SkillRegistry] Loaded: %s v%s (priority=%d, skills: %d registered, %d skipped)",
                moduleName,
                module.getVersion(),
                module.priority(),
                finalRegistered,
                finalSkipped
            ));
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                "[SkillRegistry] Failed to load module " + moduleName + ": " + e.getMessage(),
                e
            );
        }
    }

    public void register(ToolSkill skill) {
        Objects.requireNonNull(skill, "skill cannot be null");
        String name = skill.getSkillName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name cannot be null or blank");
        }

        ToolSkill previous = skills.put(name, skill);
        if (previous != null) {
            LOG.fine(() -> "[SkillRegistry] Replaced skill: " + name);
        } else {
            LOG.fine(() -> "[SkillRegistry] Registered skill: " + name);
        }
    }

    public boolean unregister(String skillName) {
        ToolSkill removed = skills.remove(skillName);
        return removed != null;
    }

    public Optional<ToolSkill> getSkill(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public boolean hasSkill(String name) {
        return skills.containsKey(name);
    }

    public List<String> getSkillNames() {
        return List.copyOf(skills.keySet());
    }

    public Collection<ToolSkill> getAllSkills() {
        return Collections.unmodifiableCollection(skills.values());
    }

    public int size() {
        return skills.size();
    }

    public boolean isEmpty() {
        return skills.isEmpty();
    }

    public List<SkillModule> getLoadedModules() {
        return Collections.unmodifiableList(loadedModules);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public synchronized void shutdown() {
        LOG.info("[SkillRegistry] Shutting down...");

        for (int i = loadedModules.size() - 1; i >= 0; i--) {
            SkillModule module = loadedModules.get(i);
            try {
                module.shutdown();
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                    "[SkillRegistry] Error shutting down " + module.getModuleName(),
                    e
                );
            }
        }

        skills.clear();
        loadedModules.clear();
        initialized = false;

        LOG.info("[SkillRegistry] Shutdown complete");
    }

    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Skill Registry Summary ===\n");
        sb.append(String.format("Modules: %d, Skills: %d\n\n", loadedModules.size(), skills.size()));

        for (SkillModule module : loadedModules) {
            sb.append(String.format("[MOD] %s v%s (priority=%d)\n",
                module.getModuleName(),
                module.getVersion(),
                module.priority()
            ));
            if (module.getDescription() != null) {
                sb.append("   ").append(module.getDescription()).append("\n");
            }
        }

        sb.append("\nRegistered Skills:\n");
        List<String> sortedNames = new ArrayList<>(skills.keySet());
        Collections.sort(sortedNames);
        for (String name : sortedNames) {
            ToolSkill skill = skills.get(name);
            sb.append(String.format("  - %s: %s\n", name, skill.getDescription()));
        }

        return sb.toString();
    }
}
