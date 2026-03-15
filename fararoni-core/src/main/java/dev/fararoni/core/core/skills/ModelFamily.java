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
package dev.fararoni.core.core.skills;

public enum ModelFamily {
    QWEN_2_5,
    QWEN_3,
    DEEPSEEK_R1,
    CLAUDE,
    GENERIC_OPENAI;

    public static ModelFamily fromModelName(String modelName) {
        if (modelName == null) return GENERIC_OPENAI;
        String lower = modelName.toLowerCase();
        if (lower.contains("qwen") && lower.contains("2.5")) return QWEN_2_5;
        if (lower.contains("qwen")) return QWEN_3;
        if (lower.contains("deepseek")) return DEEPSEEK_R1;
        if (lower.contains("claude") || lower.contains("anthropic")) return CLAUDE;
        return GENERIC_OPENAI;
    }
}
