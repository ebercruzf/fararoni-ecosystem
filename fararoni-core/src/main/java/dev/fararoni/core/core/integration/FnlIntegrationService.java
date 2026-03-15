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
package dev.fararoni.core.core.integration;

import dev.fararoni.bus.agent.api.*;
import dev.fararoni.core.core.dispatcher.AgentDispatcher;
import dev.fararoni.core.core.dispatcher.ToolRegistryImpl;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class FnlIntegrationService {

    private final ToolRegistry registry;
    private final AgentDispatcher dispatcher;
    private final FnlToolRequestParser parser;

    public FnlIntegrationService() {
        this(new ToolRegistryImpl());
    }

    public FnlIntegrationService(ToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.dispatcher = new AgentDispatcher(registry);
        this.parser = new FnlToolRequestParser();
    }

    public void registerSkill(ToolSkill skill) {
        registry.register(skill);
    }

    public boolean unregisterSkill(String skillName) {
        return registry.unregister(skillName);
    }

    public boolean hasSkill(String skillName) {
        return registry.hasSkill(skillName);
    }

    public List<String> getSkillNames() {
        return registry.getAllSkillNames();
    }

    public String generateSystemPrompt() {
        if (registry.size() == 0) {
            return "{ \"tools\": [] }";
        }
        return registry.generateToolsJson();
    }

    public String generateToolsSummary() {
        if (registry.size() == 0) {
            return "No tools registered.";
        }
        return registry.generateToolsSummary();
    }

    public String generateInstructions() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n## Available Tools\n\n");
        sb.append("You can invoke tools by responding with a JSON object:\n");
        sb.append("```json\n");
        sb.append("{\"tool\": \"SKILL_NAME\", \"action\": \"action_name\", \"params\": {...}}\n");
        sb.append("```\n\n");

        if (registry.size() > 0) {
            sb.append(registry.generateToolsJson());
        } else {
            sb.append("No tools are currently registered.\n");
        }

        return sb.toString();
    }

    public Optional<ToolRequest> parseToolRequest(String json) {
        return parser.parse(json);
    }

    public boolean containsToolRequest(String response) {
        return parser.containsToolRequest(response);
    }

    public String extractJson(String response) {
        return parser.extractJson(response);
    }

    public ToolResponse execute(ToolRequest request) {
        return dispatcher.executeSingle(request);
    }

    public List<ToolResponse> executeBatch(List<ToolRequest> requests) {
        return dispatcher.executeBatch(requests);
    }

    public Optional<ToolResponse> parseAndExecute(String json) {
        return parseToolRequest(json).map(this::execute);
    }

    public boolean isSkillAvailable(String skillName) {
        return dispatcher.isSkillAvailable(skillName);
    }

    public int getSkillCount() {
        return registry.size();
    }

    public int getActiveExecutions() {
        return dispatcher.getActiveExecutions();
    }

    public void shutdown() {
        registry.shutdownAll();
    }

    public ToolRegistry getRegistry() {
        return registry;
    }

    public AgentDispatcher getDispatcher() {
        return dispatcher;
    }
}
