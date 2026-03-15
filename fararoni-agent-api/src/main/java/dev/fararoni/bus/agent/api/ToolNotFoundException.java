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
package dev.fararoni.bus.agent.api;

import java.util.List;

/**
 * Exception thrown when a requested tool skill is not found in the registry.
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see ToolRegistry#findSkill(String)
 */
public class ToolNotFoundException extends RuntimeException {

    private final String toolName;
    private final List<String> availableTools;

    /**
     * Creates a new exception with the tool name.
     *
     * @param toolName the name of the tool that was not found
     */
    public ToolNotFoundException(String toolName) {
        super(String.format("Tool not found: '%s'", toolName));
        this.toolName = toolName;
        this.availableTools = List.of();
    }

    /**
     * Creates a new exception with available tools for context.
     *
     * @param toolName       the name of the tool that was not found
     * @param availableTools list of available tool names
     */
    public ToolNotFoundException(String toolName, List<String> availableTools) {
        super(String.format("Tool not found: '%s'. Available tools: %s", toolName, availableTools));
        this.toolName = toolName;
        this.availableTools = List.copyOf(availableTools);
    }

    /**
     * Returns the name of the tool that was not found.
     *
     * @return the tool name
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * Returns the list of available tools.
     *
     * @return available tool names
     */
    public List<String> getAvailableTools() {
        return availableTools;
    }
}
