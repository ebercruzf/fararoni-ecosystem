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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Registry interface for managing and discovering tool skills.
 *
 * <p>The registry is the central catalog of all available skills and their
 * actions. It provides methods for registration, discovery, and metadata
 * generation for the LLM's system prompt.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Register and unregister skills</li>
 *   <li>Find skills and actions by name</li>
 *   <li>Generate JSON documentation for LLM prompts</li>
 *   <li>Manage skill lifecycle (initialization/shutdown)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Implementations must be thread-safe as the registry is accessed
 * concurrently by virtual threads executing tool calls.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ToolRegistry registry = new ToolRegistryImpl();
 *
 * // Register skills
 * registry.register(new FileSkill(workspacePath));
 * registry.register(new SystemSkill());
 * registry.register(new GitSkill());
 *
 * // Generate JSON for LLM prompt
 * String toolsJson = registry.generateToolsJson();
 *
 * // Find and invoke
 * Optional<ToolSkill> skill = registry.findSkill("FILE");
 * Optional<Method> action = registry.findAction("FILE", "write_file");
 * }</pre>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see ToolSkill
 * @see AgentAction
 */
public interface ToolRegistry {

    /**
     * Registers a skill in the registry.
     *
     * <p>This will:</p>
     * <ol>
     *   <li>Validate the skill name is unique</li>
     *   <li>Scan for methods annotated with {@link AgentAction}</li>
     *   <li>Call {@link ToolSkill#initialize()}</li>
     *   <li>Add to the registry</li>
     * </ol>
     *
     * @param skill the skill to register
     * @throws IllegalArgumentException if a skill with the same name exists
     * @throws SkillInitializationException if initialization fails
     */
    void register(ToolSkill skill);

    /**
     * Registers multiple skills at once.
     *
     * @param skills the skills to register
     */
    default void registerAll(ToolSkill... skills) {
        for (ToolSkill skill : skills) {
            register(skill);
        }
    }

    /**
     * Unregisters a skill from the registry.
     *
     * <p>Calls {@link ToolSkill#shutdown()} before removing.</p>
     *
     * @param skillName the name of the skill to unregister
     * @return true if the skill was found and removed
     */
    boolean unregister(String skillName);

    /**
     * Finds a skill by name.
     *
     * @param skillName the skill name (case-insensitive)
     * @return Optional containing the skill, or empty if not found
     */
    Optional<ToolSkill> findSkill(String skillName);

    /**
     * Finds an action method within a skill.
     *
     * @param skillName  the skill name
     * @param actionName the action name from {@link AgentAction#name()}
     * @return Optional containing the method, or empty if not found
     */
    Optional<Method> findAction(String skillName, String actionName);

    /**
     * Returns all registered skills.
     *
     * @return unmodifiable list of all skills
     */
    List<ToolSkill> getAllSkills();

    /**
     * Returns the names of all registered skills.
     *
     * @return list of skill names
     */
    List<String> getAllSkillNames();

    /**
     * Returns metadata for all actions in all skills.
     *
     * @return list of action metadata
     */
    List<ToolMetadata> getAllActionMetadata();

    /**
     * Generates JSON documentation of all tools for the LLM system prompt.
     *
     * <p>The generated JSON follows this format:</p>
     * <pre>{@code
     * {
     *   "tools": [
     *     {
     *       "name": "FILE",
     *       "description": "File system operations",
     *       "actions": [
     *         {
     *           "name": "write_file",
     *           "description": "Writes to a file",
     *           "parameters": [
     *             {"name": "path", "type": "string", "required": true},
     *             {"name": "content", "type": "string", "required": true}
     *           ]
     *         }
     *       ]
     *     }
     *   ]
     * }
     * }</pre>
     *
     * @return JSON string for the LLM prompt
     */
    String generateToolsJson();

    /**
     * Generates a human-readable summary of all tools.
     *
     * <p>Useful for debugging and documentation.</p>
     *
     * @return formatted summary string
     */
    String generateToolsSummary();

    /**
     * Returns the number of registered skills.
     *
     * @return skill count
     */
    int size();

    /**
     * Checks if a skill is registered.
     *
     * @param skillName the skill name
     * @return true if registered
     */
    boolean hasSkill(String skillName);

    /**
     * Shuts down all registered skills.
     *
     * <p>Should be called when the application is shutting down.</p>
     */
    void shutdownAll();

    /**
     * Clears all registered skills.
     *
     * <p>Calls shutdown on each skill before clearing.</p>
     */
    void clear();
}
