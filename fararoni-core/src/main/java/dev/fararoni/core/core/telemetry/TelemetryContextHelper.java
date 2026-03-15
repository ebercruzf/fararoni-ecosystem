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
package dev.fararoni.core.core.telemetry;

import java.util.Map;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class TelemetryContextHelper {
    private static final int MAX_HINT_LENGTH = 40;
    private static final int MAX_PATH_LENGTH = 30;
    private static final int MAX_COMMAND_LENGTH = 25;

    private TelemetryContextHelper() {
    }

    public static String extractUiHint(String toolName, Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return formatFallback(toolName);
        }

        Object activeForm = args.get("activeForm");
        if (activeForm != null && !activeForm.toString().isBlank()) {
            return truncate(activeForm.toString(), MAX_HINT_LENGTH);
        }

        return switch (toolName) {
            case "TaskCreate", "TaskUpdate" -> {
                Object subject = args.get("subject");
                yield subject != null
                    ? "Task: " + truncate(subject.toString(), MAX_HINT_LENGTH - 6)
                    : formatFallback(toolName);
            }

            case "WriteFile", "FilePut", "fs_write" -> {
                Object path = getPath(args);
                yield path != null
                    ? "Writing " + truncatePath(path.toString())
                    : "Writing file...";
            }

            case "ReadFile", "FileGet", "fs_read" -> {
                Object path = getPath(args);
                yield path != null
                    ? "Reading " + truncatePath(path.toString())
                    : "Reading file...";
            }

            case "ListFiles", "fs_list" -> {
                Object dir = args.get("directory");
                if (dir == null) dir = args.get("directoryPath");
                if (dir == null) dir = args.get("path");
                yield dir != null
                    ? "Listing " + truncatePath(dir.toString())
                    : "Listing files...";
            }

            case "FileSearch", "GlobGet", "DeepScan" -> {
                Object query = args.get("query");
                if (query == null) query = args.get("pattern");
                yield query != null
                    ? "Searching: " + truncate(query.toString(), MAX_HINT_LENGTH - 11)
                    : "Searching files...";
            }

            case "ShellCommand", "ExecuteCode" -> {
                Object cmd = args.get("command");
                if (cmd == null) cmd = args.get("code");
                yield cmd != null
                    ? "Exec: " + truncate(cmd.toString(), MAX_COMMAND_LENGTH)
                    : "Executing command...";
            }

            case "GitAction" -> {
                Object action = args.get("action");
                yield action != null
                    ? "Git: " + action.toString()
                    : "Git operation...";
            }

            case "EnterPlanMode" -> "Entering plan mode...";
            case "ExitPlanMode" -> "Exiting plan mode...";

            case "web_fetch", "WebFetch" -> {
                Object url = args.get("url");
                yield url != null
                    ? "Fetching: " + truncateUrl(url.toString())
                    : "Fetching web content...";
            }

            case "web_search", "WebSearch" -> {
                Object query = args.get("query");
                yield query != null
                    ? "Searching: " + truncate(query.toString(), MAX_HINT_LENGTH - 11)
                    : "Web search...";
            }

            case "start_mission" -> {
                Object objective = args.get("objective");
                yield objective != null
                    ? "Mission: " + truncate(objective.toString(), MAX_HINT_LENGTH - 9)
                    : "Starting mission...";
            }

            default -> formatFallback(toolName);
        };
    }

    private static Object getPath(Map<String, Object> args) {
        Object path = args.get("path");
        if (path == null) path = args.get("filePath");
        if (path == null) path = args.get("file_path");
        return path;
    }

    private static String formatFallback(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "Processing...";
        }
        String readable = toolName.replaceAll("([a-z])([A-Z])", "$1 $2");
        return readable + "...";
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        value = value.trim();
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 3) + "...";
    }

    private static String truncatePath(String path) {
        if (path == null || path.isBlank()) return "";
        path = path.trim();

        if (path.length() <= MAX_PATH_LENGTH) return path;

        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            String filename = path.substring(lastSlash + 1);
            if (filename.length() <= MAX_PATH_LENGTH - 4) {
                return ".../" + filename;
            }
            return truncate(filename, MAX_PATH_LENGTH);
        }

        return truncate(path, MAX_PATH_LENGTH);
    }

    private static String truncateUrl(String url) {
        if (url == null || url.isBlank()) return "";
        url = url.trim();

        String clean = url.replaceFirst("^https?://", "");

        if (clean.length() <= MAX_PATH_LENGTH) return clean;

        int slashIndex = clean.indexOf('/');
        if (slashIndex > 0 && slashIndex < clean.length() - 1) {
            String domain = clean.substring(0, Math.min(slashIndex, 15));
            return domain + "/...";
        }

        return truncate(clean, MAX_PATH_LENGTH);
    }
}
