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
package dev.fararoni.core.core.agent.model;

import java.util.List;
import java.util.Map;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record AgentTemplate(
    String templateId,
    String roleName,
    String systemPrompt,
    String outputJsonSchema,
    List<String> capabilities,
    Map<String, String> metadata
) {
    public AgentTemplate {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId no puede ser null o vacio");
        }
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("roleName no puede ser null o vacio");
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt no puede ser null o vacio");
        }
        capabilities = capabilities != null ? List.copyOf(capabilities) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public boolean hasOutputSchema() {
        return outputJsonSchema != null && !outputJsonSchema.isBlank();
    }

    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String templateId;
        private String roleName;
        private String systemPrompt;
        private String outputJsonSchema;
        private List<String> capabilities = List.of();
        private Map<String, String> metadata = Map.of();

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder roleName(String roleName) {
            this.roleName = roleName;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder outputJsonSchema(String outputJsonSchema) {
            this.outputJsonSchema = outputJsonSchema;
            return this;
        }

        public Builder capabilities(List<String> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public AgentTemplate build() {
            return new AgentTemplate(templateId, roleName, systemPrompt,
                outputJsonSchema, capabilities, metadata);
        }
    }
}
