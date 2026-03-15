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
package dev.fararoni.core.core.react;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.Set;

public final class MessageHistoryDistiller {
    private MessageHistoryDistiller() {}

    public static int distill(ArrayNode messages, int keepCycles) {
        int before = messages.size();
        if (before <= 8) return 0;

        int tailStart = Math.max(2, before - (keepCycles * 3));

        int readAssistantIdx = -1;
        int readToolIdx = -1;
        for (int i = tailStart - 1; i >= 2; i--) {
            JsonNode msg = messages.get(i);
            if (!"tool".equals(msg.path("role").asText(""))) continue;
            String content = msg.path("content").asText("");
            for (int j = i - 1; j >= Math.max(2, i - 2); j--) {
                JsonNode prev = messages.get(j);
                if (!"assistant".equals(prev.path("role").asText(""))) continue;
                JsonNode toolCalls = prev.get("tool_calls");
                if (toolCalls == null || !toolCalls.isArray()) continue;
                for (JsonNode tc : toolCalls) {
                    String name = tc.path("function").path("name").asText("");
                    if (name.toLowerCase().contains("read") && !content.contains("FALLO")) {
                        readAssistantIdx = j;
                        readToolIdx = i;
                    }
                }
                if (readAssistantIdx >= 0) break;
            }
            if (readAssistantIdx >= 0) break;
        }

        Set<Integer> skip = new HashSet<>();
        for (int i = tailStart; i < before; i++) {
            JsonNode msg = messages.get(i);
            String role = msg.path("role").asText("");
            String content = msg.path("content").asText("");
            if ("system".equals(role) && content.contains("PROTOCOL-MANDATE") && i < before - 3) {
                skip.add(i);
                continue;
            }
            if ("tool".equals(role)
                && (content.contains("FALLO DE PARCHE") || content.contains("requiere 'path'"))) {
                skip.add(i);
                for (int j = i - 1; j >= tailStart; j--) {
                    if ("assistant".equals(messages.get(j).path("role").asText(""))) {
                        skip.add(j);
                        break;
                    }
                    if (!"system".equals(messages.get(j).path("role").asText(""))) break;
                }
            }
        }

        ArrayNode preserved = messages.arrayNode();
        preserved.add(messages.get(0).deepCopy());
        preserved.add(messages.get(1).deepCopy());

        if (readAssistantIdx >= 0 && readAssistantIdx < tailStart) {
            preserved.add(messages.get(readAssistantIdx).deepCopy());
            preserved.add(messages.get(readToolIdx).deepCopy());
        }

        int distilledCount = tailStart - 2;
        ObjectNode reset = preserved.objectNode();
        reset.put("role", "system");
        reset.put("content", "[CONTEXT RESET] " + distilledCount
            + " intermediate messages compacted. "
            + "Focus on recent tool results. Generate valid JSON for tool calls.");
        preserved.add(reset);

        for (int i = tailStart; i < before; i++) {
            if (!skip.contains(i)) {
                preserved.add(messages.get(i).deepCopy());
            }
        }

        messages.removeAll();
        for (JsonNode node : preserved) {
            messages.add(node);
        }
        return before - messages.size();
    }
}
