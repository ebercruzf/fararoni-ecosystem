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
package dev.fararoni.core.core.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ChatSession {
    private final List<ChatMessage> history = new ArrayList<>();
    private final LLMProvider llmService;
    private final double temperature;

    public ChatSession(LLMProvider llmService, String systemPrompt, double temperature) {
        this.llmService = llmService;
        this.temperature = temperature;
        this.history.add(new ChatMessage(ChatRole.SYSTEM, systemPrompt));
    }

    public String sendUserMessage(String prompt) {
        ChatMessage userMsg = new ChatMessage(ChatRole.USER, prompt);
        this.history.add(userMsg);

        String responseRaw = llmService.generate(this.history, this.temperature);

        this.history.add(new ChatMessage(ChatRole.ASSISTANT, responseRaw));

        return responseRaw;
    }

    public List<ChatMessage> getHistory() {
        return List.copyOf(history);
    }

    public void clearHistory() {
        history.clear();
    }

    public enum ChatRole {
        SYSTEM,
        USER,
        ASSISTANT
    }

    public record ChatMessage(ChatRole role, String content) {}
}
