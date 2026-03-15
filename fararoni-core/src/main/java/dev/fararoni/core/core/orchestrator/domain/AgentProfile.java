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

import java.time.Instant;
import java.util.Set;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public record AgentProfile(

    String id,

    String name,

    AgentType type,

    Set<String> cognitiveSkills,

    Set<String> availableTools,

    HardwareCapabilities hardware,

    AgentStatus status,

    int loadFactor,

    Instant lastHeartbeat

) {
    public static final String TOOL_BROWSER_PLAYWRIGHT = "BROWSER_PLAYWRIGHT";

    public static final String TOOL_DOCKER_CLIENT = "DOCKER_CLIENT";

    public static final String TOOL_GIT = "GIT";

    public static final String TOOL_MAVEN = "MAVEN";

    public static final String TOOL_FFMPEG = "FFMPEG";

    public static final String TOOL_WHISPER = "WHISPER";

    public static final String SKILL_JAVA_ARCHITECT = "JAVA_ARCHITECT";

    public static final String SKILL_CODE_ANALYSIS = "CODE_ANALYSIS";

    public static final String SKILL_FINANCIAL_ANALYSIS = "FINANCIAL_ANALYSIS";

    public static final String SKILL_VISUAL_QA = "VISUAL_QA";

    public static final String SKILL_BASIC_COMMANDS = "BASIC_COMMANDS";

    @Deprecated(since = "FASE 43.2.1")
    public static final String HW_BROWSER_PLAYWRIGHT = "BROWSER_PLAYWRIGHT";

    @Deprecated(since = "FASE 43.2.1")
    public static final String HW_DOCKER_SANDBOX = "DOCKER_SANDBOX";

    @Deprecated(since = "FASE 43.2.1")
    public static final String HW_IOT_HUE = "IOT_HUE";

    @Deprecated(since = "FASE 43.2.1")
    public static final String HW_AUDIO_WHISPER = "AUDIO_WHISPER";

    @Deprecated(since = "FASE 43.2.1")
    public static final String HW_VOICE_TTS = "VOICE_TTS";

    @Deprecated(since = "FASE 43.2.1")
    public static final String HW_CAMERA_VISION = "CAMERA_VISION";

    public enum AgentStatus {
        IDLE,

        BUSY,

        OFFLINE,

        MAINTENANCE
    }

    public boolean canHandle(MissionRequirement req) {
        if (status != AgentStatus.IDLE) {
            return false;
        }

        if (req.requiresLocation()) {
            if (hardware == null || !hardware.isAtLocation(req.targetLocation())) {
                return false;
            }
        }

        if (req.requiresGpu()) {
            if (hardware == null || !hardware.hasGpuAcceleration()) {
                return false;
            }
        }

        if (req.requiredSkills() != null && !req.requiredSkills().isEmpty()) {
            if (cognitiveSkills == null || !cognitiveSkills.containsAll(req.requiredSkills())) {
                return false;
            }
        }

        if (req.requiredTools() != null && !req.requiredTools().isEmpty()) {
            if (availableTools == null || !availableTools.containsAll(req.requiredTools())) {
                return false;
            }
        }

        return true;
    }

    public boolean hasSkill(String skill) {
        return cognitiveSkills != null && cognitiveSkills.contains(skill);
    }

    public boolean hasTool(String tool) {
        return availableTools != null && availableTools.contains(tool);
    }

    public boolean hasDevice(String device) {
        return hardware != null && hardware.hasDevice(device);
    }

    public boolean isAtLocation(String location) {
        return hardware != null && hardware.isAtLocation(location);
    }

    public boolean isAvailable() {
        return status == AgentStatus.IDLE || status == AgentStatus.BUSY;
    }

    public boolean isEmbodied() {
        return type == AgentType.EMBODIED;
    }

    public boolean isVirtual() {
        return type == AgentType.VIRTUAL;
    }

    @Deprecated(since = "FASE 43.2.1")
    public boolean canHandle(String requirement) {
        if (status != AgentStatus.IDLE) {
            return false;
        }

        if (cognitiveSkills != null && cognitiveSkills.contains(requirement)) {
            return true;
        }

        if (availableTools != null && availableTools.contains(requirement)) {
            return true;
        }

        if (hardware != null && hardware.hasDevice(requirement)) {
            return true;
        }

        return false;
    }

    @Deprecated(since = "FASE 43.2.1")
    public boolean hasHardware(String hardware) {
        return hasTool(hardware) || hasDevice(hardware);
    }

    @Deprecated(since = "FASE 43.2.1")
    public boolean canHandleWithHardware(String capability, String hardware) {
        MissionRequirement req = MissionRequirement.builder()
            .addSkill(capability)
            .addTool(hardware)
            .build();
        return canHandle(req);
    }

    public static AgentProfile virtual(String id, String name, Set<String> skills) {
        return new AgentProfile(
            id, name, AgentType.VIRTUAL,
            skills, Set.of(), null,
            AgentStatus.IDLE, 0, Instant.now()
        );
    }

    public static AgentProfile hybrid(String id, String name, Set<String> skills, Set<String> tools) {
        return new AgentProfile(
            id, name, AgentType.HYBRID,
            skills, tools, null,
            AgentStatus.IDLE, 0, Instant.now()
        );
    }

    public static AgentProfile embodied(String id, String name, Set<String> skills, HardwareCapabilities hardware) {
        return new AgentProfile(
            id, name, AgentType.EMBODIED,
            skills, Set.of(), hardware,
            AgentStatus.IDLE, 0, Instant.now()
        );
    }
}
