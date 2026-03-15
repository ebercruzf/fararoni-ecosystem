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
package dev.fararoni.core.core.prompt;

import dev.fararoni.core.core.memory.Wisdom;
import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.core.memory.GraphRAGService;
import dev.fararoni.core.tokenizer.Tokenizer;

import java.util.*;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class PromptBuilder {
    private static final Logger LOG = Logger.getLogger(PromptBuilder.class.getName());

    private String systemPrompt = "";
    private String userQuery = "";
    private Persona persona;
    private String ragContext = "";
    private final List<String> constraints = new ArrayList<>();
    private final List<ToolDescription> tools = new ArrayList<>();
    private final List<Example> examples = new ArrayList<>();
    private final Map<String, String> variables = new HashMap<>();

    private Tokenizer tokenizer;
    private int maxTokens = Integer.MAX_VALUE;
    private int reservedForResponse = 1024;

    private static final int PRIORITY_SYSTEM = 100;
    private static final int PRIORITY_USER = 100;
    private static final int PRIORITY_PERSONA = 90;
    private static final int PRIORITY_RAG = 70;
    private static final int PRIORITY_TOOLS = 80;
    private static final int PRIORITY_CONSTRAINTS = 85;
    private static final int PRIORITY_EXAMPLES = 50;

    private PromptBuilder() {}

    public static PromptBuilder create() {
        return new PromptBuilder();
    }

    public PromptBuilder systemPrompt(String prompt) {
        this.systemPrompt = prompt != null ? prompt : "";
        return this;
    }

    public PromptBuilder userQuery(String query) {
        this.userQuery = query != null ? query : "";
        return this;
    }

    public PromptBuilder withPersona(Persona persona) {
        this.persona = persona;
        return this;
    }

    public PromptBuilder withRAGContext(String context) {
        if (context != null && !context.isBlank()) {
            if (this.ragContext.isEmpty()) {
                this.ragContext = context;
            } else {
                this.ragContext += "\n\n" + context;
            }
        }
        return this;
    }

    public PromptBuilder withRAGContext(GraphRAGService ragService, String query, int maxItems) {
        if (ragService != null && query != null) {
            String retrieved = ragService.retrieveContext(query, maxItems);
            withRAGContext(retrieved);
        }
        return this;
    }

    public PromptBuilder addContext(List<Wisdom> wisdomList) {
        if (wisdomList == null || wisdomList.isEmpty()) {
            return this;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(">>> CONTEXTO TÉCNICO ESPECÍFICO (SURGICAL INJECTION):\n");
        sb.append("══════════════════════════════════════════════════════\n");

        for (Wisdom w : wisdomList) {
            sb.append("• [").append(w.id).append("]: ")
                    .append(w.description).append("\n");

            if (w.codeSnippet != null && !w.codeSnippet.isBlank()) {
            sb.append("  Code Reference:\n")
                        .append("  ```\n")
                        .append(w.codeSnippet)
                        .append("\n  ```\n");
            }
        }
        sb.append("══════════════════════════════════════════════════════\n");

        return withRAGContext(sb.toString());
    }

    public PromptBuilder addTool(String name, String description, String parameters) {
        tools.add(new ToolDescription(name, description, parameters));
        return this;
    }

    public PromptBuilder addTools(List<ToolDescription> toolList) {
        if (toolList != null) {
            tools.addAll(toolList);
        }
        return this;
    }

    public PromptBuilder addConstraint(String constraint) {
        if (constraint != null && !constraint.isBlank()) {
            constraints.add(constraint);
        }
        return this;
    }

    public PromptBuilder addConstraints(List<String> constraintList) {
        if (constraintList != null) {
            for (String c : constraintList) {
                addConstraint(c);
            }
        }
        return this;
    }

    public PromptBuilder addExample(String input, String output) {
        examples.add(new Example(input, output, null));
        return this;
    }

    public PromptBuilder addExample(String input, String output, String explanation) {
        examples.add(new Example(input, output, explanation));
        return this;
    }

    public PromptBuilder variable(String name, String value) {
        if (name != null && value != null) {
            variables.put(name, value);
        }
        return this;
    }

    public PromptBuilder variables(Map<String, String> vars) {
        if (vars != null) {
            variables.putAll(vars);
        }
        return this;
    }

    public PromptBuilder withTokenizer(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        return this;
    }

    public PromptBuilder maxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    public PromptBuilder reserveForResponse(int reserved) {
        this.reservedForResponse = reserved;
        return this;
    }

    public String build() {
        StringBuilder prompt = new StringBuilder();

        if (!systemPrompt.isBlank()) {
            prompt.append("<system>\n");
            prompt.append(replaceVariables(systemPrompt));
            prompt.append("\n</system>\n\n");
        }

        if (persona != null) {
            prompt.append("<persona>\n");
            prompt.append(buildPersonaSection());
            prompt.append("\n</persona>\n\n");
        }

        if (!constraints.isEmpty()) {
            prompt.append("<constraints>\n");
            for (String constraint : constraints) {
                prompt.append("- ").append(replaceVariables(constraint)).append("\n");
            }
            prompt.append("</constraints>\n\n");
        }

        if (!ragContext.isBlank()) {
            prompt.append("<context>\n");
            prompt.append(ragContext);
            prompt.append("\n</context>\n\n");
        }

        if (!tools.isEmpty()) {
            prompt.append("<available_tools>\n");
            prompt.append(buildToolsSection());
            prompt.append("</available_tools>\n\n");
        }

        if (!examples.isEmpty()) {
            prompt.append("<examples>\n");
            prompt.append(buildExamplesSection());
            prompt.append("</examples>\n\n");
        }

        if (!userQuery.isBlank()) {
            prompt.append("<user_query>\n");
            prompt.append(replaceVariables(userQuery));
            prompt.append("\n</user_query>");
        }

        String result = prompt.toString();

        if (tokenizer != null && maxTokens < Integer.MAX_VALUE) {
            result = applyTokenBudget(result);
        }

        return result;
    }

    public List<String[]> buildMessages() {
        List<String[]> messages = new ArrayList<>();

        StringBuilder system = new StringBuilder();
        if (!systemPrompt.isBlank()) {
            system.append(replaceVariables(systemPrompt));
        }
        if (persona != null) {
            system.append("\n\n").append(buildPersonaSection());
        }
        if (!constraints.isEmpty()) {
            system.append("\n\nConstraints:\n");
            for (String c : constraints) {
                system.append("- ").append(replaceVariables(c)).append("\n");
            }
        }
        if (!tools.isEmpty()) {
            system.append("\n\nAvailable Tools:\n").append(buildToolsSection());
        }

        if (!system.isEmpty()) {
            messages.add(new String[]{"system", system.toString()});
        }

        for (Example ex : examples) {
            messages.add(new String[]{"user", ex.input()});
            messages.add(new String[]{"assistant", ex.output()});
        }

        StringBuilder userMsg = new StringBuilder();
        if (!ragContext.isBlank()) {
            userMsg.append(ragContext).append("\n\n");
        }
        userMsg.append(replaceVariables(userQuery));

        if (!userMsg.isEmpty()) {
            messages.add(new String[]{"user", userMsg.toString()});
        }

        return messages;
    }

    private String buildPersonaSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("Role: ").append(persona.name()).append("\n");
        sb.append("Description: ").append(persona.description()).append("\n");
        sb.append("Communication Style: ").append(persona.style()).append("\n");

        if (!persona.expertise().isEmpty()) {
            sb.append("Expertise: ").append(String.join(", ", persona.expertise())).append("\n");
        }

        if (!persona.priorityCritics().isEmpty()) {
            List<String> priorities = persona.priorityCritics().stream()
                .map(Enum::name)
                .toList();
            sb.append("Priorities: ").append(String.join(", ", priorities)).append("\n");
        }

        if (persona.systemPrompt() != null && !persona.systemPrompt().isBlank()) {
            sb.append("\n").append(persona.systemPrompt());
        }

        return sb.toString();
    }

    private String buildToolsSection() {
        StringBuilder sb = new StringBuilder();
        for (ToolDescription tool : tools) {
            sb.append("Tool: ").append(tool.name()).append("\n");
            sb.append("  Description: ").append(tool.description()).append("\n");
            if (tool.parameters() != null && !tool.parameters().isBlank()) {
                sb.append("  Parameters: ").append(tool.parameters()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildExamplesSection() {
        StringBuilder sb = new StringBuilder();
        int num = 1;
        for (Example ex : examples) {
            sb.append("Example ").append(num++).append(":\n");
            sb.append("  Input: ").append(ex.input()).append("\n");
            sb.append("  Output: ").append(ex.output()).append("\n");
            if (ex.explanation() != null) {
                sb.append("  Explanation: ").append(ex.explanation()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String replaceVariables(String text) {
        String result = text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String applyTokenBudget(String prompt) {
        int currentTokens = tokenizer.countTokens(prompt);
        int availableBudget = maxTokens - reservedForResponse;

        if (currentTokens <= availableBudget) {
            return prompt;
        }

        LOG.warning(() -> String.format(
            "[PromptBuilder] Prompt exceeds budget: %d tokens > %d available. Truncating.",
            currentTokens, availableBudget
        ));

        int excessTokens = currentTokens - availableBudget;
        int charsToRemove = (int) (excessTokens * 4.0);

        if (prompt.length() > charsToRemove) {
            return prompt.substring(0, prompt.length() - charsToRemove) + "\n[... truncated ...]";
        }

        return prompt;
    }

    public record ToolDescription(String name, String description, String parameters) {}

    public record Example(String input, String output, String explanation) {}
}
