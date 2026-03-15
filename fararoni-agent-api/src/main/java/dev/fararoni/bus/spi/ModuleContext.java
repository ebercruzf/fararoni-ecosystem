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
package dev.fararoni.bus.spi;

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Context object providing dependencies to FararoniModule instances.
 *
 * <p>This record acts as a lightweight dependency injection container,
 * providing modules with access to core services without tight coupling.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * public void initialize(ModuleContext context) {
 *     this.bus = context.bus();
 *     int port = context.getInt("gateway.port", 7071);
 *     String token = context.getString("gateway.api.token");
 * }
 * }</pre>
 *
 * <h2>Configuration Resolution</h2>
 * <p>Configuration is loaded from:</p>
 * <ol>
 *   <li>Environment variables (highest priority)</li>
 *   <li>~/.fararoni/config/modules.yml</li>
 *   <li>Default values in code (lowest priority)</li>
 * </ol>
 *
 * @param bus        the sovereign event bus for pub/sub
 * @param config     configuration map from modules.yml
 * @param configPath path to the config directory (~/.fararoni/config)
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see FararoniModule
 */
public record ModuleContext(
    SovereignEventBus bus,
    Map<String, Object> config,
    Path configPath
) {

    /**
     * Gets a string configuration value.
     *
     * <p>Supports environment variable expansion with "env:" prefix:</p>
     * <pre>
     * gateway.api.token: "env:FARARONI_GATEWAY_TOKEN"
     * </pre>
     *
     * @param key configuration key (dot-notation, e.g., "gateway.port")
     * @return the value or null if not found
     */
    public String getString(String key) {
        return getString(key, null);
    }

    /**
     * Gets a string configuration value with default.
     *
     * @param key          configuration key
     * @param defaultValue value to return if key not found
     * @return the value or defaultValue
     */
    public String getString(String key, String defaultValue) {
        Object value = getNestedValue(key);
        if (value == null) {
            return defaultValue;
        }

        String strValue = String.valueOf(value);

        // Environment variable expansion
        if (strValue.startsWith("env:")) {
            String envVar = strValue.substring(4);
            String envValue = System.getenv(envVar);
            return envValue != null ? envValue : defaultValue;
        }

        return strValue;
    }

    /**
     * Gets an integer configuration value.
     *
     * @param key          configuration key
     * @param defaultValue value to return if key not found or invalid
     * @return the value or defaultValue
     */
    public int getInt(String key, int defaultValue) {
        Object value = getNestedValue(key);
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Number n) {
            return n.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a boolean configuration value.
     *
     * @param key          configuration key
     * @param defaultValue value to return if key not found
     * @return the value or defaultValue
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = getNestedValue(key);
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Boolean b) {
            return b;
        }

        return Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * Gets a configuration sub-map.
     *
     * <p>Useful for getting all channel configurations:</p>
     * <pre>{@code
     * Map<String, Object> channels = context.getMap("channels");
     * }</pre>
     *
     * @param key configuration key
     * @return the sub-map or empty map if not found
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String key) {
        Object value = getNestedValue(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }

    /**
     * Checks if a configuration key exists.
     *
     * @param key configuration key
     * @return true if the key exists (even if value is null)
     */
    public boolean has(String key) {
        return getNestedValue(key) != null;
    }

    /**
     * Gets an optional configuration value.
     *
     * @param key configuration key
     * @return Optional containing the value, or empty if not found
     */
    public Optional<Object> get(String key) {
        return Optional.ofNullable(getNestedValue(key));
    }

    /**
     * Resolves a path relative to the config directory.
     *
     * @param relativePath path relative to ~/.fararoni/config
     * @return absolute path
     */
    public Path resolvePath(String relativePath) {
        return configPath.resolve(relativePath);
    }

    /**
     * Gets a nested value using dot notation.
     *
     * <p>Example: "gateway.rest.port" navigates to:</p>
     * <pre>
     * gateway:
     *   rest:
     *     port: 7071
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private Object getNestedValue(String key) {
        if (key == null || key.isEmpty() || config == null) {
            return null;
        }

        String[] parts = key.split("\\.");
        Object current = config;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }

            if (current == null) {
                return null;
            }
        }

        return current;
    }
}
