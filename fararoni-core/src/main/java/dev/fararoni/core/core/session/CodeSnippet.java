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
public record CodeSnippet(
    String code,
    String language,
    String description,
    long timestampEpochMs
) implements Promptable {
    public static CodeSnippet now(String code, String language, String description) {
        return new CodeSnippet(code, language, description, System.currentTimeMillis());
    }

    public static CodeSnippet autoDetect(String code, String description) {
        String detectedLang = detectLanguage(code);
        return now(code, detectedLang, description);
    }

    public Instant timestamp() {
        return Instant.ofEpochMilli(timestampEpochMs);
    }

    @JsonIgnore
    public Duration getAge() {
        return Duration.between(timestamp(), Instant.now());
    }

    @Override
    public String toStablePrompt() {
        StringBuilder sb = new StringBuilder();
        if (description != null && !description.isBlank()) {
            sb.append("// ").append(description).append("\n");
        }
        sb.append("```").append(language != null ? language : "").append("\n");
        sb.append(code);
        if (!code.endsWith("\n")) {
            sb.append("\n");
        }
        sb.append("```");
        return sb.toString();
    }

    @Deprecated
    public String toPromptContext() {
        return toStablePrompt();
    }

    @Override
    public String toDisplayString() {
        Duration age = getAge();
        String timeAgo = formatDuration(age);
        String lang = language != null ? language : "text";
        String desc = description != null ? description : "codigo";
        int lines = code.split("\n").length;
        return String.format("[%s] %s (%d lineas) - hace %s", lang, desc, lines, timeAgo);
    }

    private static String detectLanguage(String code) {
        if (code == null || code.isBlank()) {
            return "text";
        }
        String trimmed = code.trim();
        if (trimmed.contains("public class ") || trimmed.contains("private ") ||
            trimmed.contains("import java.") || trimmed.contains("@Override")) {
            return "java";
        }
        if (trimmed.contains("function ") || trimmed.contains("const ") ||
            trimmed.contains("=>") || trimmed.contains("async ")) {
            return "javascript";
        }
        if (trimmed.contains("def ") || trimmed.contains("import ") ||
            trimmed.startsWith("#") || trimmed.contains("self.")) {
            return "python";
        }
        if (trimmed.toUpperCase().startsWith("SELECT ") ||
            trimmed.toUpperCase().startsWith("INSERT ") ||
            trimmed.toUpperCase().startsWith("CREATE TABLE")) {
            return "sql";
        }
        if (trimmed.startsWith("#!/bin/") || trimmed.contains("$1") ||
            trimmed.startsWith("echo ")) {
            return "bash";
        }
        return "text";
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
