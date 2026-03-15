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

import dev.fararoni.core.core.memory.GraphRAGService;
import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.tokenizer.Tokenizer;

import java.util.*;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ContextInjector {
    private static final Logger LOG = Logger.getLogger(ContextInjector.class.getName());

    private final Tokenizer tokenizer;
    private final int maxContextTokens;
    private final int reservedForResponse;
    private final GraphRAGService ragService;
    private final int ragMaxItems;
    private final int ragDepth;

    private final Map<String, CachedContext> contextCache = new HashMap<>();

    private ContextInjector(Builder builder) {
        this.tokenizer = builder.tokenizer;
        this.maxContextTokens = builder.maxContextTokens;
        this.reservedForResponse = builder.reservedForResponse;
        this.ragService = builder.ragService;
        this.ragMaxItems = builder.ragMaxItems;
        this.ragDepth = builder.ragDepth;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ContextInjector simple(Tokenizer tokenizer, int maxTokens) {
        return builder()
            .tokenizer(tokenizer)
            .maxContextTokens(maxTokens)
            .build();
    }

    public String inject(String systemPrompt, String userQuery, String sessionId) {
        List<ContextItem> items = gatherContext(userQuery, sessionId);
        return assemblePrompt(systemPrompt, userQuery, items);
    }

    public PromptBuilder enrichBuilder(PromptBuilder builder, String query) {
        if (ragService != null && query != null) {
            String ragContext = ragService.retrieveContext(query, ragMaxItems);
            builder.withRAGContext(ragContext);
        }
        return builder;
    }

    public PromptBuilder enrichBuilder(PromptBuilder builder, String query, Persona persona) {
        enrichBuilder(builder, query);
        if (persona != null) {
            builder.withPersona(persona);
        }
        return builder;
    }

    public List<ContextItem> gatherContext(String query, String sessionId) {
        List<ContextItem> items = new ArrayList<>();

        if (ragService != null && query != null) {
            String ragContext = ragService.retrieveContext(query, ragMaxItems);
            if (!ragContext.isBlank()) {
                items.add(new ContextItem(
                    ContextType.RAG,
                    "Knowledge Graph Context",
                    ragContext,
                    60
                ));
            }

            Set<String> relatedEntities = ragService.retrieveRelatedEntities(query, ragDepth);
            if (!relatedEntities.isEmpty()) {
                items.add(new ContextItem(
                    ContextType.ENTITIES,
                    "Related Entities",
                    String.join(", ", relatedEntities),
                    50
                ));
            }
        }

        if (sessionId != null) {
            CachedContext cached = contextCache.get(sessionId);
            if (cached != null && !cached.isExpired()) {
                items.add(new ContextItem(
                    ContextType.SESSION,
                    "Session Context",
                    cached.context(),
                    70
                ));
            }
        }

        items.sort((a, b) -> Integer.compare(b.priority(), a.priority()));

        return items;
    }

    public List<ContextItem> compress(List<ContextItem> items, int maxTokens) {
        if (tokenizer == null) {
            return items;
        }

        List<ContextItem> result = new ArrayList<>();
        int usedTokens = 0;

        for (ContextItem item : items) {
            int itemTokens = tokenizer.countTokens(item.content());

            if (usedTokens + itemTokens <= maxTokens) {
                result.add(item);
                usedTokens += itemTokens;
            } else {
                int remainingBudget = maxTokens - usedTokens;
                if (remainingBudget > 100) {
                    String compressed = truncateToTokens(item.content(), remainingBudget);
                    result.add(new ContextItem(
                        item.type(),
                        item.label() + " (truncated)",
                        compressed,
                        item.priority()
                    ));
                    usedTokens += tokenizer.countTokens(compressed);
                }

                LOG.fine(() -> String.format(
                    "[ContextInjector] Truncated context '%s': %d tokens exceeds budget",
                    item.label(), itemTokens
                ));
            }
        }

        return result;
    }

    public void cacheContext(String sessionId, String context, long ttlMs) {
        contextCache.put(sessionId, new CachedContext(
            context,
            System.currentTimeMillis() + ttlMs
        ));
    }

    public void clearCache(String sessionId) {
        contextCache.remove(sessionId);
    }

    public void clearAllCache() {
        contextCache.clear();
    }

    private String assemblePrompt(String systemPrompt, String userQuery, List<ContextItem> items) {
        int systemTokens = tokenizer != null ? tokenizer.countTokens(systemPrompt) : 0;
        int queryTokens = tokenizer != null ? tokenizer.countTokens(userQuery) : 0;
        int availableForContext = maxContextTokens - systemTokens - queryTokens - reservedForResponse;

        List<ContextItem> compressedItems = compress(items, Math.max(0, availableForContext));

        StringBuilder prompt = new StringBuilder();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            prompt.append("<system>\n").append(systemPrompt).append("\n</system>\n\n");
        }

        for (ContextItem item : compressedItems) {
            prompt.append("<").append(item.type().tag()).append(">\n");
            prompt.append("## ").append(item.label()).append("\n");
            prompt.append(item.content()).append("\n");
            prompt.append("</").append(item.type().tag()).append(">\n\n");
        }

        if (userQuery != null && !userQuery.isBlank()) {
            prompt.append("<user_query>\n").append(userQuery).append("\n</user_query>");
        }

        return prompt.toString();
    }

    private String truncateToTokens(String text, int maxTokens) {
        if (tokenizer == null) {
            int maxChars = maxTokens * 4;
            if (text.length() <= maxChars) {
                return text;
            }
            return text.substring(0, maxChars) + "...";
        }

        int low = 0;
        int high = text.length();

        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = text.substring(0, mid);
            if (tokenizer.countTokens(candidate) <= maxTokens) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        if (low < text.length()) {
            return text.substring(0, low) + "...";
        }
        return text;
    }

    public enum ContextType {
        RAG("context"),
        SESSION("session"),
        ENTITIES("entities"),
        FILES("files"),
        HISTORY("history"),
        ERROR("error");

        private final String tag;

        ContextType(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    public record ContextItem(
        ContextType type,
        String label,
        String content,
        int priority
    ) {}

    private record CachedContext(String context, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public static final class Builder {
        private Tokenizer tokenizer;
        private int maxContextTokens = 4096;
        private int reservedForResponse = 1024;
        private GraphRAGService ragService;
        private int ragMaxItems = 20;
        private int ragDepth = 2;

        private Builder() {}

        public Builder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder maxContextTokens(int maxTokens) {
            this.maxContextTokens = maxTokens;
            return this;
        }

        public Builder reservedForResponse(int reserved) {
            this.reservedForResponse = reserved;
            return this;
        }

        public Builder ragService(GraphRAGService ragService) {
            this.ragService = ragService;
            return this;
        }

        public Builder ragMaxItems(int maxItems) {
            this.ragMaxItems = maxItems;
            return this;
        }

        public Builder ragDepth(int depth) {
            this.ragDepth = depth;
            return this;
        }

        public ContextInjector build() {
            return new ContextInjector(this);
        }
    }
}
