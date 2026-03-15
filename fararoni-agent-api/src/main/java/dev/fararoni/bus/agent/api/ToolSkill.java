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

/**
 * Marker interface for classes that provide agent skills (capabilities).
 *
 * <p>A skill is a cohesive group of related actions that the LLM agent can
 * invoke. For example, a {@code FileSkill} might provide actions for reading,
 * writing, and listing files.</p>
 *
 * <h2>Design Philosophy</h2>
 * <ul>
 *   <li><strong>Single Responsibility:</strong> Each skill should focus on
 *       one domain (files, git, system, etc.)</li>
 *   <li><strong>Stateless Preferred:</strong> Skills should be stateless
 *       when possible for thread safety</li>
 *   <li><strong>Fail-Safe:</strong> Actions should handle errors gracefully
 *       and return meaningful error messages</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * public class FileSkill implements ToolSkill {
 *
 *     private final Path workspaceRoot;
 *
 *     public FileSkill(Path workspaceRoot) {
 *         this.workspaceRoot = workspaceRoot;
 *     }
 *
 *     @Override
 *     public String getSkillName() {
 *         return "FILE";
 *     }
 *
 *     @Override
 *     public String getDescription() {
 *         return "File system operations within the workspace";
 *     }
 *
 *     @AgentAction(name = "write_file", description = "Writes to a file")
 *     public WriteResult writeFile(String path, String content) {
 *         // Implementation with sandbox validation
 *     }
 *
 *     @AgentAction(name = "read_file", description = "Reads a file")
 *     public String readFile(String path) {
 *         // Implementation
 *     }
 * }
 * }</pre>
 *
 * <h2>Registration</h2>
 * <p>Skills must be registered with a {@link ToolRegistry}:</p>
 * <pre>{@code
 * registry.register(new FileSkill(workspacePath));
 * registry.register(new SystemSkill());
 * registry.register(new GitSkill());
 * }</pre>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see AgentAction
 * @see ToolRegistry
 */
public interface ToolSkill {

    /**
     * Returns the unique name of this skill.
     *
     * <p>This name is used in the JSON protocol to identify which skill
     * should handle a request. It should be uppercase for consistency.</p>
     *
     * <p>Examples: "FILE", "GIT", "SYSTEM", "DATETIME"</p>
     *
     * @return the skill name (uppercase recommended)
     */
    String getSkillName();

    /**
     * Returns a human-readable description of this skill.
     *
     * <p>This description is included in the LLM's system prompt to help
     * it understand the skill's purpose.</p>
     *
     * @return the skill description
     */
    default String getDescription() {
        return "";
    }

    /**
     * Returns the version of this skill.
     *
     * <p>Used for compatibility checking and documentation.</p>
     *
     * @return the version string (e.g., "1.0.0")
     */
    default String getVersion() {
        return "1.0.0";
    }

    /**
     * Initializes the skill.
     *
     * <p>Called once when the skill is registered with the registry.
     * Use this to set up resources like connections, caches, or file handles.</p>
     *
     * <p>Default implementation does nothing.</p>
     *
     * @throws SkillInitializationException if initialization fails
     */
    default void initialize() {
        // Default: no initialization needed
    }

    /**
     * Shuts down the skill and releases resources.
     *
     * <p>Called when the application is shutting down or the skill is
     * being unregistered. Use this to close connections, flush caches, etc.</p>
     *
     * <p>Default implementation does nothing.</p>
     */
    default void shutdown() {
        // Default: no cleanup needed
    }

    /**
     * Checks if this skill is currently available.
     *
     * <p>A skill might be unavailable if required resources are not
     * accessible (e.g., git not installed for GitSkill).</p>
     *
     * @return true if the skill can handle requests
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Returns the priority of this skill for conflict resolution.
     *
     * <p>If multiple skills can handle the same action name, the one
     * with higher priority is chosen. Default is 0.</p>
     *
     * @return the priority value (higher = more preferred)
     */
    default int getPriority() {
        return 0;
    }
}
