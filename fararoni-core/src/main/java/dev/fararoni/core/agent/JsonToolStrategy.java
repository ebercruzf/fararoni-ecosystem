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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class JsonToolStrategy implements OutputProtocolStrategy {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getSystemInstructions() {
        return """
            You are FARARONI, a Software Engineering Agent. You can CREATE and MODIFY files.

            When you need to perform file operations, respond with a JSON object:

            {
              "actions": [
                {"type": "FILE_WRITE", "path": "filename.ext", "content": "file content"},
                {"type": "MKDIR", "path": "directory/path"}
              ],
              "message": "Optional explanation of what you did"
            }

            ACTION TYPES:
            - FILE_WRITE: Create or overwrite a file
            - FILE_APPEND: Append content to existing file
            - MKDIR: Create a directory
            - FILE_DELETE: Delete a file (use with caution)

            RULES:
            1. Always use valid JSON format
            2. Escape special characters in content (\\n for newlines, \\" for quotes)
            3. You can include multiple actions in one response
            4. The "message" field is optional but helpful for the user
            5. If no file operations needed, respond with plain text (no JSON)

            EXAMPLES:

            User: "crea una clase Usuario con nombre"
            Assistant: {
              "actions": [
                {
                  "type": "FILE_WRITE",
                  "path": "Usuario.java",
                  "content": "public class Usuario {\\n    private String nombre;\\n\\n    public Usuario(String nombre) {\\n        this.nombre = nombre;\\n    }\\n\\n    public String getNombre() { return nombre; }\\n    public void setNombre(String nombre) { this.nombre = nombre; }\\n}"
                }
              ],
              "message": "Clase Usuario creada con constructor y getters/setters"
            }

            User: "hola"
            Assistant: Hola! Soy FARARONI, tu asistente de programacion. En que te puedo ayudar?
            """;
    }

    @Override
    public List<ToolAction> parseResponse(String modelOutput) {
        List<ToolAction> actions = new ArrayList<>();

        if (modelOutput == null || modelOutput.isBlank()) {
            return actions;
        }

        String trimmed = modelOutput.trim();

        String jsonStr = extractJson(trimmed);

        if (jsonStr == null) {
            actions.add(ToolAction.chatResponse(trimmed));
            return actions;
        }

        try {
            JsonNode root = MAPPER.readTree(jsonStr);

            JsonNode actionsNode = root.get("actions");
            if (actionsNode != null && actionsNode.isArray()) {
                for (JsonNode actionNode : actionsNode) {
                    ToolAction action = parseAction(actionNode);
                    if (action != null) {
                        actions.add(action);
                    }
                }
            }

            JsonNode messageNode = root.get("message");
            if (messageNode != null && !messageNode.isNull()) {
                String message = messageNode.asText();
                if (!message.isBlank()) {
                    actions.add(ToolAction.chatResponse(message));
                }
            }

            if (actions.isEmpty()) {
                actions.add(ToolAction.chatResponse(trimmed));
            }
        } catch (Exception e) {
            actions.add(ToolAction.chatResponse(trimmed));
        }

        return actions;
    }

    @Override
    public String getProtocolName() {
        return "JsonToolUse";
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        if (start < 0) {
            return null;
        }

        int end = text.lastIndexOf("}");
        if (end <= start) {
            return repairJson(text.substring(start));
        }

        return text.substring(start, end + 1);
    }

    private String repairJson(String json) {
        int openBraces = 0;
        int closeBraces = 0;
        int openBrackets = 0;
        int closeBrackets = 0;
        boolean inString = false;
        char prev = 0;

        for (char c : json.toCharArray()) {
            if (c == '"' && prev != '\\') {
                inString = !inString;
            }
            if (!inString) {
                switch (c) {
                    case '{' -> openBraces++;
                    case '}' -> closeBraces++;
                    case '[' -> openBrackets++;
                    case ']' -> closeBrackets++;
                }
            }
            prev = c;
        }

        StringBuilder repaired = new StringBuilder(json);

        if (inString) {
            repaired.append("\"");
        }

        for (int i = 0; i < openBrackets - closeBrackets; i++) {
            repaired.append("]");
        }

        for (int i = 0; i < openBraces - closeBraces; i++) {
            repaired.append("}");
        }

        return repaired.toString();
    }

    private ToolAction parseAction(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }

        JsonNode typeNode = node.get("type");
        if (typeNode == null) {
            return null;
        }

        String type = typeNode.asText().toUpperCase();
        JsonNode pathNode = node.get("path");
        String path = pathNode != null ? pathNode.asText() : null;

        return switch (type) {
            case "FILE_WRITE" -> {
                JsonNode contentNode = node.get("content");
                String content = contentNode != null ? contentNode.asText() : "";
                yield path != null ? ToolAction.fileWrite(path, content) : null;
            }
            case "FILE_APPEND" -> {
                JsonNode contentNode = node.get("content");
                String content = contentNode != null ? contentNode.asText() : "";
                yield path != null ? ToolAction.fileAppend(path, content) : null;
            }
            case "MKDIR" -> path != null ? ToolAction.mkdir(path) : null;
            case "FILE_DELETE" -> path != null ? ToolAction.fileDelete(path) : null;
            default -> null;
        };
    }
}
