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
package dev.fararoni.core.agent;

import java.util.Map;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record ToolAction(
    ActionType type,
    String path,
    String content,
    Map<String, Object> metadata
) {
    public enum ActionType {
        FILE_WRITE,
        FILE_APPEND,
        MKDIR,
        FILE_DELETE,
        GIT_COMMIT,
        CHAT_RESPONSE
    }

    public static ToolAction fileWrite(String path, String content) {
        return new ToolAction(ActionType.FILE_WRITE, path, content, Map.of());
    }

    public static ToolAction fileAppend(String path, String content) {
        return new ToolAction(ActionType.FILE_APPEND, path, content, Map.of());
    }

    public static ToolAction mkdir(String path) {
        return new ToolAction(ActionType.MKDIR, path, null, Map.of());
    }

    public static ToolAction fileDelete(String path) {
        return new ToolAction(ActionType.FILE_DELETE, path, null, Map.of());
    }

    public static ToolAction chatResponse(String message) {
        return new ToolAction(ActionType.CHAT_RESPONSE, null, message, Map.of());
    }

    public static ToolAction gitCommit(String message) {
        return new ToolAction(ActionType.GIT_COMMIT, null, message, Map.of());
    }

    public boolean isFileOperation() {
        return type == ActionType.FILE_WRITE ||
               type == ActionType.FILE_APPEND ||
               type == ActionType.MKDIR ||
               type == ActionType.FILE_DELETE;
    }

    public boolean isChatResponse() {
        return type == ActionType.CHAT_RESPONSE;
    }

    @Override
    public String toString() {
        return switch (type) {
            case FILE_WRITE -> "FILE_WRITE: " + path + " (" + (content != null ? content.length() : 0) + " chars)";
            case FILE_APPEND -> "FILE_APPEND: " + path;
            case MKDIR -> "MKDIR: " + path;
            case FILE_DELETE -> "FILE_DELETE: " + path;
            case GIT_COMMIT -> "GIT_COMMIT: " + content;
            case CHAT_RESPONSE -> "CHAT: " + (content != null ? content.substring(0, Math.min(50, content.length())) : "") + "...";
        };
    }
}
