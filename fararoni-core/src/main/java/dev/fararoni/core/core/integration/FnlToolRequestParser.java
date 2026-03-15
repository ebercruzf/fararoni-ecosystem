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
package dev.fararoni.core.core.integration;

import dev.fararoni.bus.agent.api.ToolRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class FnlToolRequestParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern TOOL_PATTERN = Pattern.compile(
        "\"tool\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ACTION_PATTERN = Pattern.compile(
        "\"action\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PARAMS_PATTERN = Pattern.compile(
        "\"params\"\\s*:\\s*(\\{[^}]*\\})", Pattern.CASE_INSENSITIVE
    );

    public FnlToolRequestParser() {
    }

    public Optional<ToolRequest> parse(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }

        String extracted = extractJson(json);
        if (extracted == null) {
            return Optional.empty();
        }

        extracted = repairTruncatedJson(extracted);

        try {
            return parseWithJackson(extracted);
        } catch (Exception e) {
            return parseWithRegex(extracted);
        }
    }

    public boolean containsToolRequest(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }

        String lower = response.toLowerCase();
        return lower.contains("\"tool\"") && lower.contains("\"action\"");
    }

    public String extractJson(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        String trimmed = response.trim();

        if (trimmed.contains("```json")) {
            int start = trimmed.indexOf("```json") + 7;
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
        }

        if (trimmed.contains("```")) {
            int start = trimmed.indexOf("```") + 3;
            int newline = trimmed.indexOf("\n", start);
            if (newline > start) {
                start = newline + 1;
            }
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                String content = trimmed.substring(start, end).trim();
                if (content.startsWith("{")) {
                    return content;
                }
            }
        }

        int start = trimmed.indexOf("{");
        if (start < 0) {
            return null;
        }

        int end = trimmed.lastIndexOf("}");
        if (end <= start) {
            return trimmed.substring(start);
        }

        return trimmed.substring(start, end + 1);
    }

    public String repairTruncatedJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }

        int openBraces = 0;
        int closeBraces = 0;
        boolean inString = false;
        char prevChar = 0;

        for (char c : json.toCharArray()) {
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{') openBraces++;
                if (c == '}') closeBraces++;
            }
            prevChar = c;
        }

        int missing = openBraces - closeBraces;
        if (missing > 0) {
            StringBuilder sb = new StringBuilder(json);
            for (int i = 0; i < missing; i++) {
                sb.append("}");
            }
            return sb.toString();
        }

        return json;
    }

    private Optional<ToolRequest> parseWithJackson(String json) throws JsonProcessingException {
        JsonNode root = MAPPER.readTree(json);

        JsonNode toolNode = root.get("tool");
        if (toolNode == null || !toolNode.isTextual()) {
            return Optional.empty();
        }
        String tool = toolNode.asText().toUpperCase();

        JsonNode actionNode = root.get("action");
        if (actionNode == null || !actionNode.isTextual()) {
            return Optional.empty();
        }
        String action = actionNode.asText();

        Map<String, Object> params = new HashMap<>();
        JsonNode paramsNode = root.get("params");
        if (paramsNode != null && paramsNode.isObject()) {
            Iterator<String> fieldNames = paramsNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode valueNode = paramsNode.get(fieldName);
                params.put(fieldName, convertJsonValue(valueNode));
            }
        }

        captureRootLevelParam(root, params, "command");
        captureRootLevelParam(root, params, "path");
        captureRootLevelParam(root, params, "content");
        captureRootLevelParam(root, params, "format");

        return Optional.of(ToolRequest.of(tool, action, params));
    }

    private Object convertJsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            return node.toString();
        }
        if (node.isObject()) {
            return node.toString();
        }
        return node.asText();
    }

    private void captureRootLevelParam(JsonNode root, Map<String, Object> params, String paramName) {
        if (!params.containsKey(paramName)) {
            JsonNode node = root.get(paramName);
            if (node != null && !node.isNull()) {
                params.put(paramName, convertJsonValue(node));
            }
        }
    }

    private Optional<ToolRequest> parseWithRegex(String json) {
        Matcher toolMatcher = TOOL_PATTERN.matcher(json);
        if (!toolMatcher.find()) {
            return Optional.empty();
        }
        String tool = toolMatcher.group(1).toUpperCase();

        Matcher actionMatcher = ACTION_PATTERN.matcher(json);
        if (!actionMatcher.find()) {
            return Optional.empty();
        }
        String action = actionMatcher.group(1);

        Map<String, Object> params = new HashMap<>();
        Matcher paramsMatcher = PARAMS_PATTERN.matcher(json);
        if (paramsMatcher.find()) {
            String paramsJson = paramsMatcher.group(1);
            params = parseSimpleParams(paramsJson);
        }

        Pattern commandPattern = Pattern.compile("\"command\"\\s*:\\s*\"([^\"]+)\"");
        Matcher commandMatcher = commandPattern.matcher(json);
        if (commandMatcher.find() && !params.containsKey("command")) {
            params.put("command", commandMatcher.group(1));
        }

        return Optional.of(ToolRequest.of(tool, action, params));
    }

    private Map<String, Object> parseSimpleParams(String paramsJson) {
        Map<String, Object> params = new HashMap<>();

        Pattern kvPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|(-?\\d+(?:\\.\\d+)?)|true|false)");
        Matcher kvMatcher = kvPattern.matcher(paramsJson);

        while (kvMatcher.find()) {
            String key = kvMatcher.group(1);
            String stringValue = kvMatcher.group(2);
            String numValue = kvMatcher.group(3);

            if (stringValue != null) {
                params.put(key, stringValue);
            } else if (numValue != null) {
                if (numValue.contains(".")) {
                    params.put(key, Double.parseDouble(numValue));
                } else {
                    params.put(key, Integer.parseInt(numValue));
                }
            }
        }

        return params;
    }
}
