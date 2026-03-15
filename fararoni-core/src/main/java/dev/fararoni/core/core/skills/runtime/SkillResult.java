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
package dev.fararoni.core.core.skills.runtime;

import java.util.Map;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public record SkillResult(
    boolean success,

    String output,

    Map<String, Object> metadata
) {
    public static SkillResult success(String output) {
        return new SkillResult(true, output, Map.of());
    }

    public static SkillResult success(String output, Map<String, Object> metadata) {
        return new SkillResult(true, output, metadata);
    }

    public static SkillResult error(String error) {
        return new SkillResult(false, error, Map.of());
    }

    public static SkillResult error(String error, Map<String, Object> metadata) {
        return new SkillResult(false, error, metadata);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMeta(String key) {
        return (T) metadata.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMeta(String key, T defaultValue) {
        return (T) metadata.getOrDefault(key, defaultValue);
    }
}
