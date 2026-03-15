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

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public enum HardwareTier {
    LOW_RESOURCE(12_000, 1_024, "DIRECTIVO", false),

    MEDIUM(24_000, 2_048, "CONTEXTUAL", false),

    HIGH_PERFORMANCE(64_000, 4_096, "ARQUITECTONICO", true);

    private final int maxInputChars;
    private final int maxOutputTokens;
    private final String promptTone;
    private final boolean supportsNativeTools;

    HardwareTier(int maxInputChars, int maxOutputTokens, String promptTone, boolean supportsNativeTools) {
        this.maxInputChars = maxInputChars;
        this.maxOutputTokens = maxOutputTokens;
        this.promptTone = promptTone;
        this.supportsNativeTools = supportsNativeTools;
    }

    public int getMaxInputChars() {
        return maxInputChars;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public String getPromptTone() {
        return promptTone;
    }

    public int getMaxInputTokens() {
        return maxInputChars / 4;
    }

    public boolean supportsNativeTools() {
        return supportsNativeTools;
    }

    public static HardwareTier fromModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return MEDIUM;
        }

        String lower = modelName.toLowerCase();

        if (containsAny(lower, "30b", "32b", "33b", "34b", "70b", "72b", "gpt-4", "claude-3", "opus")) {
            return HIGH_PERFORMANCE;
        }

        if (containsAny(lower, "0.5b", "1.5b", "1b", "3b", ":0.5", ":1.5", ":1b", ":3b")) {
            return LOW_RESOURCE;
        }

        if (containsAny(lower, "7b", "8b", "13b", "14b", ":7b", ":8b", ":13b", ":14b")) {
            return MEDIUM;
        }

        return MEDIUM;
    }

    private static boolean containsAny(String str, String... patterns) {
        for (String pattern : patterns) {
            if (str.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s[maxInput=%d chars, maxOutput=%d tokens, tone=%s, nativeTools=%b]",
            name(), maxInputChars, maxOutputTokens, promptTone, supportsNativeTools);
    }
}
