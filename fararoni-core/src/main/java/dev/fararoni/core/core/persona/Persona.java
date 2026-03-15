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
package dev.fararoni.core.core.persona;

import dev.fararoni.core.core.reflexion.Critic;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record Persona(
    String id,
    String name,
    String description,
    Set<String> expertise,
    Set<String> allowedToolKeys,
    CommunicationStyle style,
    Set<Critic.CriticCategory> priorityCritics,
    String systemPrompt,
    Map<String, String> metadata
) {
    public Persona {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        description = description != null ? description : "";
        expertise = expertise != null ? Set.copyOf(expertise) : Set.of();
        allowedToolKeys = allowedToolKeys == null
            ? Collections.emptySet()
            : Set.copyOf(allowedToolKeys);
        style = style != null ? style : CommunicationStyle.BALANCED;
        priorityCritics = priorityCritics != null ? Set.copyOf(priorityCritics) : Set.of();
        systemPrompt = systemPrompt != null ? systemPrompt : "";
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public boolean hasExpertise(String area) {
        return expertise.stream()
            .anyMatch(e -> e.equalsIgnoreCase(area));
    }

    public boolean canUseTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        return allowedToolKeys.contains("*") || allowedToolKeys.contains(toolName);
    }

    public boolean isReadOnly() {
        return !allowedToolKeys.contains("*") &&
               !allowedToolKeys.stream().anyMatch(k ->
                   k.contains("write") || k.contains("execute") || k.contains("delete"));
    }

    public boolean prioritizes(Critic.CriticCategory category) {
        return priorityCritics.contains(category);
    }

    public String getMetadata(String key) {
        return metadata.get(key);
    }

    public String generateSystemPrompt() {
        if (!systemPrompt.isBlank()) {
            return systemPrompt;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("You are acting as a ").append(name).append(".\n\n");

        if (!description.isBlank()) {
            sb.append(description).append("\n\n");
        }

        if (!expertise.isEmpty()) {
            sb.append("Your areas of expertise include: ")
              .append(String.join(", ", expertise))
              .append(".\n\n");
        }

        sb.append("Communication style: ").append(style.getDescription()).append("\n");

        return sb.toString();
    }

    public Persona withMetadata(String key, String value) {
        var newMetadata = new java.util.HashMap<>(this.metadata);
        newMetadata.put(key, value);
        return new Persona(id, name, description, expertise, allowedToolKeys,
            style, priorityCritics, systemPrompt, newMetadata);
    }

    public Persona withSystemPrompt(String prompt) {
        return new Persona(id, name, description, expertise, allowedToolKeys,
            style, priorityCritics, prompt, metadata);
    }

    public Persona withAllowedTools(String... tools) {
        var newTools = new java.util.HashSet<>(this.allowedToolKeys);
        newTools.addAll(Set.of(tools));
        return new Persona(id, name, description, expertise, newTools,
            style, priorityCritics, systemPrompt, metadata);
    }

    public enum CommunicationStyle {
        CONCISE("Be concise and direct. Provide only essential information."),

        DETAILED("Provide detailed explanations with examples and context."),

        BALANCED("Balance brevity with sufficient detail for clarity."),

        TECHNICAL("Use technical terminology. Assume expert audience."),

        EDUCATIONAL("Explain concepts step by step. Use analogies when helpful."),

        FORMAL("Use formal language suitable for documentation.");

        private final String description;

        CommunicationStyle(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String name;
        private String description;
        private Set<String> expertise;
        private Set<String> allowedToolKeys;
        private CommunicationStyle style;
        private Set<Critic.CriticCategory> priorityCritics;
        private String systemPrompt;
        private Map<String, String> metadata;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            this.name = id;
            this.expertise = Set.of();
            this.allowedToolKeys = Set.of();
            this.style = CommunicationStyle.BALANCED;
            this.priorityCritics = Set.of();
            this.metadata = Map.of();
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder expertise(Set<String> expertise) {
            this.expertise = expertise;
            return this;
        }

        public Builder expertise(String... areas) {
            this.expertise = Set.of(areas);
            return this;
        }

        public Builder allowedTools(Set<String> tools) {
            this.allowedToolKeys = tools;
            return this;
        }

        public Builder allowedTools(String... tools) {
            this.allowedToolKeys = Set.of(tools);
            return this;
        }

        public Builder style(CommunicationStyle style) {
            this.style = style;
            return this;
        }

        public Builder priorityCritics(Set<Critic.CriticCategory> categories) {
            this.priorityCritics = categories;
            return this;
        }

        public Builder priorityCritics(Critic.CriticCategory... categories) {
            this.priorityCritics = Set.of(categories);
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder metadata(String key, String value) {
            if (this.metadata.isEmpty()) {
                this.metadata = new java.util.HashMap<>();
            }
            ((java.util.HashMap<String, String>) this.metadata).put(key, value);
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Persona build() {
            return new Persona(id, name, description, expertise, allowedToolKeys,
                style, priorityCritics, systemPrompt, metadata);
        }
    }

    public static Persona createAnalyst() {
        return Persona.builder("ANALYST")
            .name("Analista")
            .description("Analista de requisitos y código con acceso de solo lectura")
            .expertise("analysis", "requirements", "documentation")
            .allowedTools("fs_read", "db_select", "http_get", "code_search")
            .style(CommunicationStyle.DETAILED)
            .priorityCritics(Critic.CriticCategory.QUALITY)
            .systemPrompt("Eres un analista. Tu trabajo es observar, entender y documentar. No modificas código ni datos.")
            .build();
    }

    public static Persona createDeveloper() {
        return Persona.builder("DEVELOPER")
            .name("Desarrollador")
            .description("Desarrollador de software con permisos de escritura")
            .expertise("coding", "debugging", "testing")
            .allowedTools("fs_read", "fs_write", "shell_execute", "code_search", "git")
            .style(CommunicationStyle.BALANCED)
            .priorityCritics(Critic.CriticCategory.CODE, Critic.CriticCategory.QUALITY)
            .systemPrompt("Eres un desarrollador. Escribes código limpio, mantenible y bien probado.")
            .build();
    }

    public static Persona createQA() {
        return Persona.builder("QA")
            .name("Quality Assurance")
            .description("Ingeniero QA con permisos de testing")
            .expertise("testing", "quality", "automation")
            .allowedTools("fs_read", "test_run", "code_search", "report_generate")
            .style(CommunicationStyle.DETAILED)
            .priorityCritics(Critic.CriticCategory.QUALITY)
            .systemPrompt("Eres un ingeniero QA. Tu trabajo es asegurar la calidad del código mediante pruebas exhaustivas.")
            .build();
    }

    public static Persona createAdmin() {
        return Persona.builder("ADMIN")
            .name("Administrador")
            .description("Administrador del sistema con acceso completo")
            .expertise("administration", "security", "operations")
            .allowedTools("*")
            .style(CommunicationStyle.TECHNICAL)
            .priorityCritics(Critic.CriticCategory.SECURITY)
            .systemPrompt("Eres un administrador del sistema. Tienes acceso completo pero debes actuar con responsabilidad.")
            .build();
    }
}
