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
package dev.fararoni.core.core.session;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Duration;
import java.time.Instant;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record FileModification(
    String filepath,
    FileAction action,
    long timestampEpochMs
) implements Promptable {
    public static FileModification now(String filepath, FileAction action) {
        return new FileModification(filepath, action, System.currentTimeMillis());
    }

    public Instant timestamp() {
        return Instant.ofEpochMilli(timestampEpochMs);
    }

    @JsonIgnore
    public Duration getAge() {
        return Duration.between(timestamp(), Instant.now());
    }

    @Override
    public String toDisplayString() {
        Duration age = getAge();
        String timeAgo = formatDuration(age);
        return String.format("%s - %s hace %s", filepath, action.getDisplayName(), timeAgo);
    }

    @Override
    public String toStablePrompt() {
        return String.format("[%s] %s", action.name(), filepath);
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m";
        } else if (seconds < 86400) {
            return (seconds / 3600) + "h";
        } else {
            return (seconds / 86400) + "d";
        }
    }
}
