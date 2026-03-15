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
 * Immutable metadata record for a tool action.
 *
 * <p>This record holds all the information extracted from {@link AgentAction}
 * and {@link ToolParameter} annotations, used for generating the JSON
 * documentation for the LLM's system prompt.</p>
 *
 * <h2>JSON Generation</h2>
 * <p>This metadata is serialized to JSON as part of the system prompt:</p>
 * <pre>{@code
 * {
 *   "name": "write_file",
 *   "description": "Writes content to a file",
 *   "skillName": "FILE",
 *   "parameters": [
 *     {
 *       "name": "path",
 *       "type": "string",
 *       "description": "File path",
 *       "required": true
 *     },
 *     {
 *       "name": "content",
 *       "type": "string",
 *       "description": "Content to write",
 *       "required": true
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @param name                 action name from {@link AgentAction#name()}
 * @param description          action description
 * @param skillName            parent skill name
 * @param category             optional category for grouping
 * @param parameters           list of parameter metadata
 * @param requiresConfirmation whether user confirmation is needed
 * @param timeoutMs            maximum execution time
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see AgentAction
 * @see ParameterMetadata
 */
public record ToolMetadata(
    String name,
    String description,
    String skillName,
    String category,
    List<ParameterMetadata> parameters,
    boolean requiresConfirmation,
    long timeoutMs
) {

    /**
     * Compact constructor with validation.
     */
    public ToolMetadata {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Action name cannot be null or blank");
        }
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("Skill name cannot be null or blank");
        }
        if (parameters == null) {
            parameters = List.of();
        } else {
            parameters = List.copyOf(parameters);
        }
        if (description == null) {
            description = "";
        }
        if (category == null) {
            category = "";
        }
    }

    /**
     * Creates a simplified metadata with just name and description.
     *
     * @param name        the action name
     * @param description the description
     * @param skillName   the parent skill name
     * @return a new ToolMetadata instance
     */
    public static ToolMetadata of(String name, String description, String skillName) {
        return new ToolMetadata(name, description, skillName, "", List.of(), false, 30000);
    }

    /**
     * Returns the full qualified name (skill.action).
     *
     * @return formatted name
     */
    public String getFullName() {
        return skillName + "." + name;
    }

    /**
     * Checks if this action has any parameters.
     *
     * @return true if there are parameters
     */
    public boolean hasParameters() {
        return !parameters.isEmpty();
    }

    /**
     * Returns the count of required parameters.
     *
     * @return number of required parameters
     */
    public long getRequiredParameterCount() {
        return parameters.stream().filter(ParameterMetadata::required).count();
    }

    /**
     * Metadata for an action parameter.
     *
     * @param name          parameter name
     * @param type          Java type as string (e.g., "string", "int", "boolean")
     * @param description   parameter description
     * @param required      whether the parameter is required
     * @param defaultValue  default value if optional
     * @param example       example value for documentation
     * @param allowedValues list of valid values for enum-like parameters
     */
    public record ParameterMetadata(
        String name,
        String type,
        String description,
        boolean required,
        String defaultValue,
        String example,
        List<String> allowedValues
    ) {

        /**
         * Compact constructor with validation.
         */
        public ParameterMetadata {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Parameter name cannot be null or blank");
            }
            if (type == null) {
                type = "string";
            }
            if (description == null) {
                description = "";
            }
            if (defaultValue == null) {
                defaultValue = "";
            }
            if (example == null) {
                example = "";
            }
            if (allowedValues == null) {
                allowedValues = List.of();
            } else {
                allowedValues = List.copyOf(allowedValues);
            }
        }

        /**
         * Creates a simple required string parameter.
         *
         * @param name        parameter name
         * @param description parameter description
         * @return a new ParameterMetadata
         */
        public static ParameterMetadata required(String name, String description) {
            return new ParameterMetadata(name, "string", description, true, "", "", List.of());
        }

        /**
         * Creates a simple optional string parameter.
         *
         * @param name         parameter name
         * @param description  parameter description
         * @param defaultValue default value
         * @return a new ParameterMetadata
         */
        public static ParameterMetadata optional(String name, String description, String defaultValue) {
            return new ParameterMetadata(name, "string", description, false, defaultValue, "", List.of());
        }

        /**
         * Maps Java types to JSON schema types.
         *
         * @param javaType the Java class
         * @return the JSON schema type name
         */
        public static String javaTypeToJsonType(Class<?> javaType) {
            if (javaType == String.class) return "string";
            if (javaType == int.class || javaType == Integer.class) return "integer";
            if (javaType == long.class || javaType == Long.class) return "integer";
            if (javaType == double.class || javaType == Double.class) return "number";
            if (javaType == float.class || javaType == Float.class) return "number";
            if (javaType == boolean.class || javaType == Boolean.class) return "boolean";
            if (javaType.isArray() || java.util.Collection.class.isAssignableFrom(javaType)) return "array";
            return "object";
        }
    }
}
