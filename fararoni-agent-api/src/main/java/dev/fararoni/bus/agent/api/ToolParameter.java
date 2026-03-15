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
 * Documents a parameter of an {@link AgentAction} method.
 *
 * <p>This annotation provides metadata about method parameters that is
 * used to generate the JSON schema for the LLM's system prompt. It helps
 * the LLM understand what values to provide for each parameter.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @AgentAction(name = "write_file", description = "Writes to a file")
 * public WriteResult writeFile(
 *     @ToolParameter(
 *         name = "path",
 *         description = "Relative path within the workspace",
 *         required = true,
 *         example = "src/Main.java"
 *     )
 *     String path,
 *
 *     @ToolParameter(
 *         name = "content",
 *         description = "Text content to write to the file"
 *     )
 *     String content
 * ) {
 *     // Implementation
 * }
 * }</pre>
 *
 * <h2>JSON Generation</h2>
 * <pre>{@code
 * {
 *   "name": "path",
 *   "type": "string",
 *   "required": true,
 *   "description": "Relative path within the workspace",
 *   "example": "src/Main.java"
 * }
 * }</pre>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see AgentAction
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParameter {

    /**
     * Name of the parameter in the JSON protocol.
     *
     * <p>This is the key that the LLM will use in the params object.
     * Should be lowercase with underscores (snake_case).</p>
     *
     * @return the parameter name
     */
    String name();

    /**
     * Human-readable description of the parameter.
     *
     * <p>Helps the LLM understand what value to provide.</p>
     *
     * @return the parameter description
     */
    String description() default "";

    /**
     * Whether this parameter is required.
     *
     * <p>If true and the LLM doesn't provide a value, the invocation
     * will fail with a validation error.</p>
     *
     * @return true if required, false if optional
     */
    boolean required() default true;

    /**
     * Default value if the parameter is not provided.
     *
     * <p>Only applicable when {@code required = false}. The string
     * will be converted to the appropriate type.</p>
     *
     * @return the default value as a string
     */
    String defaultValue() default "";

    /**
     * Example value for documentation.
     *
     * <p>Helps the LLM understand the expected format.</p>
     *
     * @return an example value
     */
    String example() default "";

    /**
     * Valid values for enum-like parameters.
     *
     * <p>If specified, the LLM should only use one of these values.</p>
     *
     * @return array of valid values
     */
    String[] allowedValues() default {};

    /**
     * Pattern for validation (regex).
     *
     * <p>If specified, the value must match this pattern.</p>
     *
     * @return regex pattern for validation
     */
    String pattern() default "";
}
