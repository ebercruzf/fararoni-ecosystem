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
package dev.fararoni.core.core.dispatcher;

import dev.fararoni.bus.agent.api.*;
import dev.fararoni.bus.agent.api.ToolMetadata.ParameterMetadata;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ToolRegistryImpl implements ToolRegistry {

    private final Map<String, ToolSkill> skills = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Method>> actionCache = new ConcurrentHashMap<>();
    private final Map<String, ToolMetadata> metadataCache = new ConcurrentHashMap<>();

    public ToolRegistryImpl() {
    }

    @Override
    public void register(ToolSkill skill) {
        Objects.requireNonNull(skill, "skill cannot be null");

        String name = skill.getSkillName().toUpperCase();

        if (skills.containsKey(name)) {
            throw new IllegalArgumentException("Skill already registered: " + name);
        }

        Map<String, Method> actions = scanActions(skill);

        if (actions.isEmpty()) {
            throw new IllegalArgumentException(
                "Skill '" + name + "' has no methods annotated with @AgentAction");
        }

        try {
            skill.initialize();
        } catch (Exception e) {
            throw new SkillInitializationException(name, e.getMessage(), e);
        }

        skills.put(name, skill);
        actionCache.put(name, actions);

        for (Map.Entry<String, Method> entry : actions.entrySet()) {
            String actionName = entry.getKey();
            Method method = entry.getValue();
            ToolMetadata metadata = buildMetadata(skill, actionName, method);
            metadataCache.put(name + "." + actionName, metadata);
        }
    }

    @Override
    public boolean unregister(String skillName) {
        String name = skillName.toUpperCase();
        ToolSkill skill = skills.remove(name);

        if (skill != null) {
            try {
                skill.shutdown();
            } catch (Exception e) {
            }
            actionCache.remove(name);
            metadataCache.keySet().removeIf(key -> key.startsWith(name + "."));
            return true;
        }
        return false;
    }

    @Override
    public Optional<ToolSkill> findSkill(String skillName) {
        return Optional.ofNullable(skills.get(skillName.toUpperCase()));
    }

    @Override
    public Optional<Method> findAction(String skillName, String actionName) {
        Map<String, Method> actions = actionCache.get(skillName.toUpperCase());
        if (actions == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(actions.get(actionName));
    }

    @Override
    public List<ToolSkill> getAllSkills() {
        return List.copyOf(skills.values());
    }

    @Override
    public List<String> getAllSkillNames() {
        return List.copyOf(skills.keySet());
    }

    @Override
    public List<ToolMetadata> getAllActionMetadata() {
        return List.copyOf(metadataCache.values());
    }

    @Override
    public String generateToolsJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"tools\": [\n");

        List<String> skillJsons = new ArrayList<>();
        for (ToolSkill skill : skills.values()) {
            skillJsons.add(generateSkillJson(skill));
        }

        json.append(String.join(",\n", skillJsons));
        json.append("\n  ]\n}");

        return json.toString();
    }

    @Override
    public String generateToolsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Registered Tools ===\n\n");

        for (ToolSkill skill : skills.values()) {
            sb.append(String.format("SKILL: %s (v%s)\n", skill.getSkillName(), skill.getVersion()));
            sb.append(String.format("  Description: %s\n", skill.getDescription()));
            sb.append(String.format("  Available: %s\n", skill.isAvailable()));
            sb.append("  Actions:\n");

            Map<String, Method> actions = actionCache.get(skill.getSkillName().toUpperCase());
            if (actions != null) {
                for (Map.Entry<String, Method> entry : actions.entrySet()) {
                    AgentAction annotation = entry.getValue().getAnnotation(AgentAction.class);
                    sb.append(String.format("    - %s: %s\n", entry.getKey(), annotation.description()));
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    public int size() {
        return skills.size();
    }

    @Override
    public boolean hasSkill(String skillName) {
        return skills.containsKey(skillName.toUpperCase());
    }

    @Override
    public void shutdownAll() {
        for (ToolSkill skill : skills.values()) {
            try {
                skill.shutdown();
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void clear() {
        shutdownAll();
        skills.clear();
        actionCache.clear();
        metadataCache.clear();
    }

    public List<String> getActionNames(String skillName) {
        Map<String, Method> actions = actionCache.get(skillName.toUpperCase());
        if (actions == null) {
            return List.of();
        }
        return List.copyOf(actions.keySet());
    }

    private Map<String, Method> scanActions(ToolSkill skill) {
        Map<String, Method> actions = new HashMap<>();

        for (Method method : skill.getClass().getMethods()) {
            if (method.isAnnotationPresent(AgentAction.class)) {
                AgentAction annotation = method.getAnnotation(AgentAction.class);
                String actionName = annotation.name();

                if (actions.containsKey(actionName)) {
                    throw new IllegalArgumentException(
                        "Duplicate action name '" + actionName + "' in skill " + skill.getSkillName());
                }

                method.setAccessible(true);
                actions.put(actionName, method);
            }
        }

        return actions;
    }

    private ToolMetadata buildMetadata(ToolSkill skill, String actionName, Method method) {
        AgentAction annotation = method.getAnnotation(AgentAction.class);

        List<ParameterMetadata> params = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            ParameterMetadata paramMeta = buildParameterMetadata(param);
            params.add(paramMeta);
        }

        return new ToolMetadata(
            actionName,
            annotation.description(),
            skill.getSkillName(),
            annotation.category(),
            params,
            annotation.requiresConfirmation(),
            annotation.timeoutMs()
        );
    }

    private ParameterMetadata buildParameterMetadata(Parameter param) {
        ToolParameter annotation = param.getAnnotation(ToolParameter.class);

        String name;
        String description;
        boolean required;
        String defaultValue;
        String example;
        List<String> allowedValues;

        if (annotation != null) {
            name = annotation.name();
            description = annotation.description();
            required = annotation.required();
            defaultValue = annotation.defaultValue();
            example = annotation.example();
            allowedValues = List.of(annotation.allowedValues());
        } else {
            name = param.getName();
            description = "";
            required = true;
            defaultValue = "";
            example = "";
            allowedValues = List.of();
        }

        String type = ParameterMetadata.javaTypeToJsonType(param.getType());

        return new ParameterMetadata(name, type, description, required, defaultValue, example, allowedValues);
    }

    private String generateSkillJson(ToolSkill skill) {
        StringBuilder json = new StringBuilder();
        String name = skill.getSkillName().toUpperCase();

        json.append("    {\n");
        json.append(String.format("      \"name\": \"%s\",\n", name));
        json.append(String.format("      \"description\": \"%s\",\n",
            escapeJson(skill.getDescription())));
        json.append("      \"actions\": [\n");

        Map<String, Method> actions = actionCache.get(name);
        List<String> actionJsons = new ArrayList<>();

        if (actions != null) {
            for (Map.Entry<String, Method> entry : actions.entrySet()) {
                actionJsons.add(generateActionJson(entry.getKey(), entry.getValue()));
            }
        }

        json.append(String.join(",\n", actionJsons));
        json.append("\n      ]\n");
        json.append("    }");

        return json.toString();
    }

    private String generateActionJson(String actionName, Method method) {
        AgentAction annotation = method.getAnnotation(AgentAction.class);
        StringBuilder json = new StringBuilder();

        json.append("        {\n");
        json.append(String.format("          \"name\": \"%s\",\n", actionName));
        json.append(String.format("          \"description\": \"%s\",\n",
            escapeJson(annotation.description())));
        json.append("          \"parameters\": [\n");

        List<String> paramJsons = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            paramJsons.add(generateParameterJson(param));
        }

        json.append(String.join(",\n", paramJsons));
        json.append("\n          ]\n");
        json.append("        }");

        return json.toString();
    }

    private String generateParameterJson(Parameter param) {
        ToolParameter annotation = param.getAnnotation(ToolParameter.class);

        String name = annotation != null ? annotation.name() : param.getName();
        String type = ParameterMetadata.javaTypeToJsonType(param.getType());
        boolean required = annotation == null || annotation.required();
        String description = annotation != null ? annotation.description() : "";

        StringBuilder json = new StringBuilder();
        json.append("            {\n");
        json.append(String.format("              \"name\": \"%s\",\n", name));
        json.append(String.format("              \"type\": \"%s\",\n", type));
        json.append(String.format("              \"required\": %s", required));

        if (!description.isEmpty()) {
            json.append(String.format(",\n              \"description\": \"%s\"", escapeJson(description)));
        }

        json.append("\n            }");

        return json.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
