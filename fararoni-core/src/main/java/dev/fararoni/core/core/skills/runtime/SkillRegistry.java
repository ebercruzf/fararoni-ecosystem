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
package dev.fararoni.core.core.skills.runtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SkillRegistry {
    private static final Logger LOG = Logger.getLogger(SkillRegistry.class.getName());

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    public void registerSkill(Skill skill) {
        skills.put(skill.getId(), skill);
        LOG.info(() -> String.format(
            "[SKILL_REGISTRY] Skill registrado: %s [%s] - Hardware: %s - Disponible: %s",
            skill.getId(),
            skill.getName(),
            skill.getRequiredHardware(),
            skill.isAvailable()
        ));
    }

    public boolean unregisterSkill(String skillId) {
        Skill removed = skills.remove(skillId);
        if (removed != null) {
            LOG.info(() -> "[SKILL_REGISTRY] Skill eliminado: " + skillId);
            return true;
        }
        return false;
    }

    public Optional<Skill> getSkill(String skillId) {
        return Optional.ofNullable(skills.get(skillId));
    }

    public List<Skill> getSkillsForHardware(String hardware) {
        return skills.values().stream()
            .filter(s -> s.getRequiredHardware().equals(hardware))
            .collect(Collectors.toList());
    }

    public List<Skill> getAvailableSkills() {
        return skills.values().stream()
            .filter(Skill::isAvailable)
            .collect(Collectors.toList());
    }

    public List<Skill> getAllSkills() {
        return List.copyOf(skills.values());
    }

    public SkillResult executeSkill(String skillId, Map<String, Object> params) {
        Optional<Skill> skill = getSkill(skillId);

        if (skill.isEmpty()) {
            return SkillResult.error("Skill no encontrado: " + skillId);
        }

        if (!skill.get().isAvailable()) {
            return SkillResult.error("Skill no disponible: " + skillId);
        }

        try {
            return skill.get().execute(params);
        } catch (Exception e) {
            LOG.warning(() -> String.format(
                "[SKILL_REGISTRY] Error ejecutando skill %s: %s",
                skillId, e.getMessage()
            ));
            return SkillResult.error("Error ejecutando skill: " + e.getMessage());
        }
    }

    public int size() {
        return skills.size();
    }

    public boolean contains(String skillId) {
        return skills.containsKey(skillId);
    }

    public Map<String, Long> getStats() {
        long total = skills.size();
        long available = skills.values().stream().filter(Skill::isAvailable).count();
        long unavailable = total - available;

        return Map.of(
            "total", total,
            "available", available,
            "unavailable", unavailable
        );
    }
}
