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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable data transfer object representing a tool invocation request.
 *
 * <p>This record encapsulates a request from the LLM to invoke a specific
 * action on a skill. It is created by parsing the JSON response from the LLM.</p>
 *
 * <h2>JSON Protocol</h2>
 * <p>The LLM generates a JSON object like:</p>
 * <pre>{@code
 * {
 *   "tool": "FILE",
 *   "action": "write_file",
 *   "params": {
 *     "path": "test.txt",
 *     "content": "Hello World"
 *   }
 * }
 * }</pre>
 *
 * <p>This is deserialized into a {@code ToolRequest}:</p>
 * <pre>{@code
 * ToolRequest request = ToolRequest.of("FILE", "write_file",
 *     Map.of("path", "test.txt", "content", "Hello World"));
 * }</pre>
 *
 * <h2>Immutability</h2>
 * <p>As a Java record, this class is immutable. The params map is
 * defensively copied in the compact constructor.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>Instances are thread-safe and can be shared across virtual threads.</p>
 *
 * @param toolName   the skill name (e.g., "FILE", "GIT")
 * @param action     the action to invoke (e.g., "write_file")
 * @param params     the parameters for the action
 * @param requestId  unique identifier for tracing
 * @param createdAt  timestamp of creation
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see ToolResponse
 * @see ToolSkill
 */
public record ToolRequest(
    String toolName,
    String action,
    Map<String, Object> params,
    String requestId,
    Instant createdAt
) {

    /**
     * Compact constructor with validation and defaults.
     */
    public ToolRequest {
        Objects.requireNonNull(toolName, "toolName cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        // Normalize tool name to uppercase
        toolName = toolName.toUpperCase();

        // Defensive copy of params
        if (params == null) {
            params = Map.of();
        } else {
            params = Map.copyOf(params);
        }

        // Generate ID if not provided
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }

        // Set creation time if not provided
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Creates a new request with the specified tool, action, and parameters.
     *
     * <p>Generates a unique request ID and sets the creation time automatically.</p>
     *
     * @param toolName the skill name
     * @param action   the action name
     * @param params   the action parameters
     * @return a new ToolRequest instance
     */
    public static ToolRequest of(String toolName, String action, Map<String, Object> params) {
        return new ToolRequest(toolName, action, params, null, null);
    }

    /**
     * Creates a new request with no parameters.
     *
     * @param toolName the skill name
     * @param action   the action name
     * @return a new ToolRequest instance
     */
    public static ToolRequest of(String toolName, String action) {
        return new ToolRequest(toolName, action, Map.of(), null, null);
    }

    /**
     * Gets a parameter value with type casting.
     *
     * @param name the parameter name
     * @param type the expected type
     * @param <T>  the type parameter
     * @return the parameter value, or null if not present
     * @throws ClassCastException if the value cannot be cast to the type
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String name, Class<T> type) {
        Object value = params.get(name);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * Gets a parameter value as a String.
     *
     * @param name the parameter name
     * @return the string value, or null if not present
     */
    public String getParamAsString(String name) {
        Object value = params.get(name);
        return value != null ? value.toString() : null;
    }

    /**
     * Checks if a parameter is present.
     *
     * @param name the parameter name
     * @return true if the parameter exists
     */
    public boolean hasParam(String name) {
        return params.containsKey(name);
    }

    /**
     * Returns a formatted string for logging.
     *
     * @return log-friendly representation
     */
    public String toLogString() {
        return String.format("[%s] %s.%s params=%s",
            requestId, toolName, action, params.keySet());
    }
}
