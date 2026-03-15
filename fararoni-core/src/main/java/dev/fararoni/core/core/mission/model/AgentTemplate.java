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
package dev.fararoni.core.core.mission.model;

import java.util.List;
import java.util.Map;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public record AgentTemplate(
    String id,
    String name,
    String role,
    List<String> capabilities,
    String systemPrompt,
    Map<String, String> prompts,
    List<String> allowedTools,
    CompensationConfig compensation,
    List<Integer> defconActivation,
    Map<String, Object> metadata,
    StreamingConfig streamingConfig,
    ValidationPolicy validationPolicy
) {
    public AgentTemplate(String id, String role, List<String> capabilities, String systemPrompt) {
        this(id, role, role, capabilities, systemPrompt, Map.of(), List.of(), null, List.of(), Map.of(), null, null);
    }

    public AgentTemplate(
            String id, String name, String role, List<String> capabilities,
            String systemPrompt, Map<String, String> prompts, List<String> allowedTools,
            CompensationConfig compensation, List<Integer> defconActivation,
            Map<String, Object> metadata) {
        this(id, name, role, capabilities, systemPrompt, prompts, allowedTools,
             compensation, defconActivation, metadata, null, null);
    }

    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.contains(capability);
    }

    public boolean isActiveForDefcon(int level) {
        if (defconActivation == null || defconActivation.isEmpty()) {
            return true;
        }
        return defconActivation.contains(level);
    }

    public boolean hasCompensation() {
        return compensation != null && compensation.enabled();
    }

    public String getPrompt(String promptName) {
        if (prompts == null || !prompts.containsKey(promptName)) {
            return systemPrompt;
        }
        return prompts.get(promptName);
    }

    public String primaryCapability() {
        if (capabilities == null || capabilities.isEmpty()) {
            return role;
        }
        return capabilities.getFirst();
    }

    public boolean isStreamingEnabled() {
        return streamingConfig != null && streamingConfig.enabled();
    }

    public String getStreamingMode() {
        if (streamingConfig == null) {
            return StreamingConfig.MODE_BATCH;
        }
        return streamingConfig.mode() != null ? streamingConfig.mode() : StreamingConfig.MODE_BATCH;
    }

    public boolean isValidationEnabled() {
        return validationPolicy != null && validationPolicy.enabled();
    }

    public ValidationPolicy getValidationPolicyOrDefault() {
        return validationPolicy != null ? validationPolicy : new ValidationPolicy(false);
    }

    public record CompensationConfig(
        boolean enabled,
        String strategy,
        List<CompensationAction> actions
    ) {
        public static final String STRATEGY_LIFO = "LIFO";
        public static final String STRATEGY_FIFO = "FIFO";

        public CompensationConfig(boolean enabled) {
            this(enabled, STRATEGY_LIFO, List.of());
        }
    }

    public record CompensationAction(
        String type,
        Map<String, Object> config
    ) {
        public static final String TYPE_DELETE_FILES = "DELETE_FILES";
        public static final String TYPE_REVERT_DB = "REVERT_DB";
        public static final String TYPE_NOTIFY = "NOTIFY";
    }

    public record StreamingConfig(
        boolean enabled,
        String mode,
        boolean parallelWrites,
        int maxRetries,
        int timeoutPerFile
    ) {
        public static final String MODE_MANIFEST_FIRST = "manifest_first";
        public static final String MODE_BATCH = "batch";

        public StreamingConfig(boolean enabled) {
            this(enabled, MODE_BATCH, false, 2, 30000);
        }

        public boolean isManifestFirst() {
            return MODE_MANIFEST_FIRST.equals(mode);
        }
    }

    public record ValidationPolicy(
        boolean enabled,
        List<String> prohibitedRegex,
        List<String> requiredPatterns,
        int maxFileSize
    ) {
        public ValidationPolicy(boolean enabled) {
            this(enabled, List.of(), List.of(), 100_000);
        }

        public boolean hasProhibitions() {
            return prohibitedRegex != null && !prohibitedRegex.isEmpty();
        }

        public boolean hasRequirements() {
            return requiredPatterns != null && !requiredPatterns.isEmpty();
        }
    }
}
