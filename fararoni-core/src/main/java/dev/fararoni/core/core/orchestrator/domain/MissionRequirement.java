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
package dev.fararoni.core.core.orchestrator.domain;

import dev.fararoni.core.core.orchestrator.SovereignOrchestrator;

import java.util.Set;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public record MissionRequirement(

    Set<String> requiredSkills,

    Set<String> requiredTools,

    String targetLocation,

    boolean requiresGpu

) {
    public boolean requiresLocation() {
        return targetLocation != null && !targetLocation.isBlank();
    }

    public boolean requiresHardware() {
        return requiresLocation() || requiresGpu;
    }

    public boolean isPurelyCognitive() {
        return !requiresHardware();
    }

    public static MissionRequirement ofSkill(String skill) {
        return new MissionRequirement(Set.of(skill), Set.of(), null, false);
    }

    public static MissionRequirement ofSkillAndTool(String skill, String tool) {
        return new MissionRequirement(Set.of(skill), Set.of(tool), null, false);
    }

    public static MissionRequirement atLocation(String skill, String location) {
        return new MissionRequirement(Set.of(skill), Set.of(), location, false);
    }

    public static MissionRequirement withGpu(String skill) {
        return new MissionRequirement(Set.of(skill), Set.of(), null, true);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final java.util.HashSet<String> skills = new java.util.HashSet<>();
        private final java.util.HashSet<String> tools = new java.util.HashSet<>();
        private String location = null;
        private boolean gpu = false;

        public Builder addSkill(String skill) {
            skills.add(skill);
            return this;
        }

        public Builder addTool(String tool) {
            tools.add(tool);
            return this;
        }

        public Builder atLocation(String loc) {
            this.location = loc;
            return this;
        }

        public Builder requiresGpu() {
            this.gpu = true;
            return this;
        }

        public MissionRequirement build() {
            return new MissionRequirement(
                Set.copyOf(skills),
                Set.copyOf(tools),
                location,
                gpu
            );
        }
    }
}
