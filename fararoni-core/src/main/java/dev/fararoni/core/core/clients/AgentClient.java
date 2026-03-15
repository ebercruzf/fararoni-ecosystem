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
package dev.fararoni.core.core.clients;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fararoni.core.core.context.ExecutionContext;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 2.1.0 (Phoenix 2.1)
 */
public interface AgentClient {
    AgentResponse generateWithTools(String systemPrompt, String userPrompt, List<ObjectNode> tools);

    default AgentResponse generateWithTools(String systemPrompt, String userPrompt,
                                            List<ObjectNode> tools, ExecutionContext ctx)
            throws InterruptedException {
        return generateWithTools(systemPrompt, userPrompt, tools);
    }

    default String generateStrict(String prompt, List<String> stopSequences, double temperature) {
        AgentResponse resp = generateWithTools(prompt, "", List.of());
        return resp.textContent() != null ? resp.textContent() : "";
    }

    default AgentResponse generateWithFullHistory(com.fasterxml.jackson.databind.node.ArrayNode messages,
                                                   List<ObjectNode> tools) {
        return generateWithFullHistory(messages, tools, "auto");
    }

    default AgentResponse generateWithFullHistory(com.fasterxml.jackson.databind.node.ArrayNode messages,
                                                   List<ObjectNode> tools, String toolChoice) {
        String systemPrompt = "";
        String userPrompt = "";
        for (int i = 0; i < messages.size(); i++) {
            var msg = messages.get(i);
            String role = msg.path("role").asText("");
            if ("system".equals(role)) systemPrompt = msg.path("content").asText("");
            if ("user".equals(role)) userPrompt = msg.path("content").asText("");
        }
        return generateWithTools(systemPrompt, userPrompt, tools);
    }

    record ToolCall(String functionName, String argumentsJson) {}

    record AgentResponse(String textContent, ToolCall toolCall, double tokensPerSecond) {
        public AgentResponse(String textContent, ToolCall toolCall) {
            this(textContent, toolCall, 0.0);
        }

        public boolean isToolCall() {
            return toolCall != null;
        }
    }
}
