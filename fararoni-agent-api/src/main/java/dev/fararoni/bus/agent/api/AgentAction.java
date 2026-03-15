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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an action that can be invoked by an LLM agent.
 *
 * <p>Methods annotated with {@code @AgentAction} are exposed to the LLM
 * through the tool registry. The LLM can invoke these methods by generating
 * a JSON request with the action name and parameters.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * public class FileSkill implements ToolSkill {
 *
 *     @AgentAction(
 *         name = "write_file",
 *         description = "Writes content to a file in the workspace"
 *     )
 *     public WriteResult writeFile(
 *         @ToolParameter(name = "path", description = "File path") String path,
 *         @ToolParameter(name = "content", description = "Content to write") String content
 *     ) {
 *         // Implementation
 *     }
 * }
 * }</pre>
 *
 * <h2>JSON Generation</h2>
 * <p>The annotation metadata is used to generate the tools JSON that is
 * injected into the LLM's system prompt:</p>
 * <pre>{@code
 * {
 *   "name": "write_file",
 *   "description": "Writes content to a file in the workspace",
 *   "parameters": [...]
 * }
 * }</pre>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see ToolSkill
 * @see ToolParameter
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AgentAction {

    /**
     * Unique name of the action.
     *
     * <p>This name is used in the JSON protocol between the LLM and the system.
     * It should be lowercase with underscores (snake_case) for consistency.</p>
     *
     * <p>Examples: "write_file", "read_file", "exec_command", "git_status"</p>
     *
     * @return the action name
     */
    String name();

    /**
     * Human-readable description of what the action does.
     *
     * <p>This description is included in the LLM's system prompt to help
     * the model understand when and how to use this action. It should be
     * clear and concise.</p>
     *
     * <p>Example: "Writes text content to a file. Creates the file if it
     * doesn't exist, or overwrites it if it does."</p>
     *
     * @return the action description
     */
    String description();

    /**
     * Category for grouping related actions in documentation.
     *
     * <p>Optional. Used for organizing actions in generated documentation
     * and help systems.</p>
     *
     * @return the category name, or empty string if not specified
     */
    String category() default "";

    /**
     * Whether this action requires user confirmation before execution.
     *
     * <p>Actions that modify files, execute commands, or perform other
     * potentially destructive operations should set this to {@code true}.</p>
     *
     * @return true if confirmation is required
     */
    boolean requiresConfirmation() default false;

    /**
     * Maximum execution time in milliseconds.
     *
     * <p>If the action takes longer than this, it will be terminated.
     * Default is 30 seconds (30000ms).</p>
     *
     * @return timeout in milliseconds
     */
    long timeoutMs() default 30000;
}
