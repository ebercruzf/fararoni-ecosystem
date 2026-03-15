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
package dev.fararoni.core.core.config;

import dev.fararoni.core.core.constants.AppDefaults;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record AgentConfig(
    int maxAttempts,
    float rabbitTemperature,
    float turtleTemperature,
    boolean hybridEnabled,
    int rabbitMaxChars,
    float fuzzyThreshold
) {
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private static final float DEFAULT_RABBIT_TEMP = 0.1f;

    private static final float DEFAULT_TURTLE_TEMP = 0.3f;

    private static final boolean DEFAULT_HYBRID_MODE = true;

    private static final int DEFAULT_RABBIT_MAX_CHARS = 12000;

    private static final float DEFAULT_FUZZY_THRESHOLD = 0.15f;

    private static final String ENV_MAX_ATTEMPTS = "FARARONI_MAX_ATTEMPTS";
    private static final String ENV_RABBIT_TEMP = "FARARONI_RABBIT_TEMP";
    private static final String ENV_TURTLE_TEMP = "FARARONI_TURTLE_TEMP";
    private static final String ENV_HYBRID_MODE = "FARARONI_HYBRID_MODE";
    private static final String ENV_RABBIT_MAX_CHARS = "FARARONI_RABBIT_MAX_CHARS";
    private static final String ENV_FUZZY_THRESHOLD = "FARARONI_FUZZY_THRESHOLD";

    public static AgentConfig load() {
        return new AgentConfig(
            getInt(ENV_MAX_ATTEMPTS, DEFAULT_MAX_ATTEMPTS),
            getFloat(ENV_RABBIT_TEMP, DEFAULT_RABBIT_TEMP),
            getFloat(ENV_TURTLE_TEMP, DEFAULT_TURTLE_TEMP),
            getBoolean(ENV_HYBRID_MODE, DEFAULT_HYBRID_MODE),
            getInt(ENV_RABBIT_MAX_CHARS, DEFAULT_RABBIT_MAX_CHARS),
            getFloat(ENV_FUZZY_THRESHOLD, DEFAULT_FUZZY_THRESHOLD)
        );
    }

    public static AgentConfig defaults() {
        return new AgentConfig(
            DEFAULT_MAX_ATTEMPTS,
            DEFAULT_RABBIT_TEMP,
            DEFAULT_TURTLE_TEMP,
            DEFAULT_HYBRID_MODE,
            DEFAULT_RABBIT_MAX_CHARS,
            DEFAULT_FUZZY_THRESHOLD
        );
    }

    public int rabbitMaxTokens() {
        return rabbitMaxChars / 4;
    }

    public boolean fitsInRabbit(int contextLength) {
        return contextLength <= rabbitMaxChars;
    }

    public String getTurtleModelName() {
        return getString(AppDefaults.ENV_TURTLE_MODEL, AppDefaults.DEFAULT_TURTLE_MODEL);
    }

    public String getRabbitModelName() {
        return getString(AppDefaults.ENV_RABBIT_MODEL, AppDefaults.DEFAULT_RABBIT_MODEL);
    }

    public String getOllamaUrl() {
        return getString(AppDefaults.ENV_OLLAMA_URL, AppDefaults.DEFAULT_OLLAMA_URL);
    }

    public int getTurtleTimeoutSeconds() {
        return getInt(AppDefaults.ENV_TURTLE_TIMEOUT, AppDefaults.DEFAULT_TURTLE_TIMEOUT_SECONDS);
    }

    public String getProviderType() {
        return getString(AppDefaults.ENV_PROVIDER_TYPE, AppDefaults.DEFAULT_PROVIDER_TYPE);
    }

    public String getCloudModelName() {
        return getString(AppDefaults.ENV_CLOUD_MODEL, AppDefaults.DEFAULT_CLOUD_MODEL);
    }

    public String getCloudBaseUrl() {
        String customUrl = System.getenv(AppDefaults.ENV_CLOUD_BASE_URL);
        if (customUrl != null && !customUrl.isBlank()) {
            return customUrl.trim();
        }
        return switch (getProviderType().toLowerCase()) {
            case "groq" -> AppDefaults.DEFAULT_GROQ_URL;
            default -> AppDefaults.DEFAULT_OPENAI_URL;
        };
    }

    public String getCloudApiKey() {
        String providerType = getProviderType().toLowerCase();
        return switch (providerType) {
            case "groq" -> System.getenv(AppDefaults.ENV_GROQ_API_KEY);
            case "openai" -> System.getenv(AppDefaults.ENV_OPENAI_API_KEY);
            default -> null;
        };
    }

    public int getCloudTimeoutSeconds() {
        return getInt(AppDefaults.ENV_CLOUD_TIMEOUT, AppDefaults.DEFAULT_CLOUD_TIMEOUT_SECONDS);
    }

    public boolean isCloudProvider() {
        String type = getProviderType().toLowerCase();
        return "openai".equals(type) || "groq".equals(type);
    }

    public boolean isAgenticEnabled() {
        return getBoolean(AppDefaults.ENV_AGENTIC_ENABLED, AppDefaults.DEFAULT_AGENTIC_ENABLED);
    }

    public String getAgenticUrl() {
        return getString(AppDefaults.ENV_AGENTIC_URL, AppDefaults.DEFAULT_AGENTIC_URL);
    }

    public String getAgenticModel() {
        return getString(AppDefaults.ENV_AGENTIC_MODEL, AppDefaults.DEFAULT_AGENTIC_MODEL);
    }

    public String getToolChoice() {
        return getString(AppDefaults.ENV_TOOL_CHOICE, AppDefaults.DEFAULT_TOOL_CHOICE);
    }

    public String getAgenticApiKey() {
        return getString(AppDefaults.ENV_AGENTIC_API_KEY, AppDefaults.DEFAULT_AGENTIC_API_KEY);
    }

    public HardwareTier detectHardwareTier(String modelName) {
        return HardwareTier.fromModelName(modelName);
    }

    public int getMaxInputChars(String modelName) {
        return detectHardwareTier(modelName).getMaxInputChars();
    }

    public int getMaxOutputTokens(String modelName) {
        return detectHardwareTier(modelName).getMaxOutputTokens();
    }

    public String getPromptTone(String modelName) {
        return detectHardwareTier(modelName).getPromptTone();
    }

    public boolean fitsInModel(int contextLength, String modelName) {
        return contextLength <= getMaxInputChars(modelName);
    }

    public int getRemainingBudget(int currentLength, String modelName) {
        return getMaxInputChars(modelName) - currentLength;
    }

    private static int getInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("[AgentConfig] Invalid int for " + key + ": " + value + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    private static float getFloat(String key, float defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("[AgentConfig] Invalid float for " + key + ": " + value + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static String getString(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return String.format(
            "AgentConfig[maxAttempts=%d, rabbitTemp=%.1f, turtleTemp=%.1f, hybrid=%s, rabbitMaxChars=%d, fuzzyThreshold=%.2f]",
            maxAttempts, rabbitTemperature, turtleTemperature, hybridEnabled, rabbitMaxChars, fuzzyThreshold
        );
    }
}
