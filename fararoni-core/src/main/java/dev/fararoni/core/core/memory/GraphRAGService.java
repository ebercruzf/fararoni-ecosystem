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
package dev.fararoni.core.core.memory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class GraphRAGService {
    private static final Logger LOG = Logger.getLogger(GraphRAGService.class.getName());

    private static final Pattern FILE_PATTERN = Pattern.compile(
        "([\\w/.-]+\\.(java|py|js|ts|json|xml|yaml|yml|md|txt|html|css|sql))");

    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "(?:class|interface|enum|record)\\s+([A-Z][\\w]+)");

    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "(?:function|method|def)\\s+(\\w+)\\s*\\(");

    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
        "\\b([a-z][\\w]*(?:Id|Name|Value|Count|List|Map|Set))\\b");

    private final InMemoryGraph graph;
    private final Map<String, List<ConversationMessage>> sessions = new ConcurrentHashMap<>();
    private final int maxContextItems;
    private final int maxContextDepth;

    private GraphRAGService(Builder builder) {
        this.graph = builder.graph != null ? builder.graph : new InMemoryGraph(builder.graphCapacity);
        this.maxContextItems = builder.maxContextItems;
        this.maxContextDepth = builder.maxContextDepth;
    }

    public String addFact(String subject, String predicate, String object) {
        return addFact(subject, predicate, object, 1.0);
    }

    public String addFact(String subject, String predicate, String object, double weight) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        Objects.requireNonNull(object, "object must not be null");

        Edge edge = Edge.of(
            normalizeEntity(subject),
            normalizeRelation(predicate),
            normalizeEntity(object),
            weight
        );

        graph.addEdge(edge);
        LOG.fine(() -> "[GraphRAG] Added fact: " + edge.toTriplet());

        return edge.id();
    }

    public String addFact(String subject, String predicate, String object,
                          Map<String, Object> metadata) {
        Edge edge = Edge.builder(
            normalizeEntity(subject),
            normalizeRelation(predicate),
            normalizeEntity(object)
        ).weight(1.0).build();

        Edge.Builder builder = Edge.builder(
            normalizeEntity(subject),
            normalizeRelation(predicate),
            normalizeEntity(object)
        );
        metadata.forEach(builder::metadata);

        graph.addEdge(builder.build());
        return edge.id();
    }

    public boolean removeFact(String factId) {
        return graph.removeEdge(factId) != null;
    }

    public int removeEntity(String entity) {
        return graph.removeNode(normalizeEntity(entity));
    }

    public void addMessage(String role, String content, String sessionId) {
        Objects.requireNonNull(content, "content must not be null");

        String session = sessionId != null ? sessionId : "default";

        ConversationMessage message = new ConversationMessage(role, content, Instant.now());
        sessions.computeIfAbsent(session, k -> new ArrayList<>()).add(message);

        extractAndStoreFacts(content, session, role);
    }

    public List<ConversationMessage> getConversationHistory(String sessionId) {
        String session = sessionId != null ? sessionId : "default";
        return sessions.getOrDefault(session, List.of());
    }

    public void clearSession(String sessionId) {
        String session = sessionId != null ? sessionId : "default";
        sessions.remove(session);
    }

    public String retrieveContext(String query) {
        return retrieveContext(query, maxContextItems);
    }

    public String retrieveContext(String query, int maxItems) {
        if (query == null || query.isBlank()) {
            return "";
        }

        List<Edge> relevantEdges = findRelevantEdges(query, maxItems);

        if (relevantEdges.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Relevant Context\n\n");

        for (Edge edge : relevantEdges) {
            sb.append("- ").append(edge.toNaturalLanguage()).append("\n");
        }

        return sb.toString();
    }

    public List<String[]> retrieveFacts(String query, int maxItems) {
        List<Edge> relevantEdges = findRelevantEdges(query, maxItems);

        return relevantEdges.stream()
            .map(e -> new String[]{e.source(), e.relation(), e.target()})
            .toList();
    }

    public Set<String> retrieveRelatedEntities(String query, int depth) {
        Set<String> queryEntities = extractEntities(query);
        Set<String> related = new HashSet<>();

        for (String entity : queryEntities) {
            related.addAll(graph.getNeighbors(entity, depth));
        }

        Set<String> queryWords = extractQueryWords(query);
        for (String word : queryWords) {
            graph.findBy(edge ->
                edge.source().toLowerCase().contains(word) ||
                edge.target().toLowerCase().contains(word)
            ).forEach(edge -> {
                if (edge.source().toLowerCase().contains(word)) {
                    related.addAll(graph.getNeighbors(edge.source(), depth));
                }
                if (edge.target().toLowerCase().contains(word)) {
                    related.addAll(graph.getNeighbors(edge.target(), depth));
                }
            });
        }

        return related;
    }

    private List<Edge> findRelevantEdges(String query, int maxItems) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Set<Edge> relevant = new HashSet<>();

        Set<String> entities = extractEntities(query);

        for (String entity : entities) {
            String normalized = normalizeEntity(entity);
            relevant.addAll(graph.getConnected(normalized));

            String entityLower = entity.toLowerCase();
            graph.findBy(edge ->
                edge.source().contains(entityLower) ||
                edge.target().contains(entityLower)
            ).forEach(relevant::add);
        }

        Set<String> queryWords = extractQueryWords(query);
        for (String word : queryWords) {
            graph.findBy(edge ->
                edge.source().contains(word) ||
                edge.target().contains(word) ||
                edge.relation().contains(word)
            ).forEach(relevant::add);
        }

        return relevant.stream()
            .sorted((a, b) -> Double.compare(b.weight(), a.weight()))
            .limit(maxItems)
            .toList();
    }

    private Set<String> extractQueryWords(String query) {
        Set<String> words = new HashSet<>();
        String[] parts = query.toLowerCase().split("\\W+");
        for (String part : parts) {
            if (part.length() >= 2 && !isStopWord(part)) {
                words.add(part);
            }
        }
        return words;
    }

    public Set<String> extractEntities(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }

        Set<String> entities = new HashSet<>();

        Matcher fileMatcher = FILE_PATTERN.matcher(text);
        while (fileMatcher.find()) {
            entities.add("file:" + fileMatcher.group(1));
        }

        Matcher classMatcher = CLASS_PATTERN.matcher(text);
        while (classMatcher.find()) {
            entities.add("class:" + classMatcher.group(1));
        }

        Matcher methodMatcher = METHOD_PATTERN.matcher(text);
        while (methodMatcher.find()) {
            entities.add("method:" + methodMatcher.group(1));
        }

        Matcher varMatcher = VARIABLE_PATTERN.matcher(text);
        while (varMatcher.find()) {
            entities.add("var:" + varMatcher.group(1));
        }

        String[] words = text.toLowerCase().split("\\W+");
        for (String word : words) {
            if (word.length() >= 4 && !isStopWord(word)) {
                entities.add("keyword:" + word);
            }
        }

        return entities;
    }

    private void extractAndStoreFacts(String content, String sessionId, String role) {
        Set<String> entities = extractEntities(content);

        String messageNode = "message:" + sessionId + ":" + System.currentTimeMillis();

        for (String entity : entities) {
            addFact(messageNode, "mentions", entity, 0.8);
        }

        List<String> entityList = new ArrayList<>(entities);
        for (int i = 0; i < entityList.size() - 1; i++) {
            for (int j = i + 1; j < entityList.size(); j++) {
                addFact(entityList.get(i), "related_to", entityList.get(j), 0.5);
            }
        }
    }

    private boolean isStopWord(String word) {
        return STOP_WORDS.contains(word);
    }

    private static final Set<String> STOP_WORDS = Set.of(
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "must", "shall", "can", "need", "dare",
        "this", "that", "these", "those", "what", "which", "who", "whom",
        "whose", "when", "where", "why", "how", "all", "each", "every",
        "both", "few", "more", "most", "other", "some", "such", "only",
        "own", "same", "than", "too", "very", "just", "also", "and", "but",
        "or", "nor", "for", "yet", "with", "about", "from", "into", "through",
        "during", "before", "after", "above", "below", "between", "under",
        "again", "further", "then", "once", "here", "there", "any", "if",
        "while", "of", "at", "by", "on", "off", "over", "out", "in", "up",
        "down", "to", "so", "not", "no", "as"
    );

    private String normalizeEntity(String entity) {
        if (entity == null) return "";
        return entity.trim().toLowerCase().replaceAll("\\s+", "_");
    }

    private String normalizeRelation(String relation) {
        if (relation == null) return "related_to";
        return relation.trim().toLowerCase().replaceAll("\\s+", "_");
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>(graph.getStats());
        stats.put("sessions", sessions.size());
        stats.put("totalMessages", sessions.values().stream()
            .mapToInt(List::size).sum());
        return stats;
    }

    public int runMaintenance(long maxAgeSeconds, double minWeight) {
        int evicted = 0;
        evicted += graph.evictOlderThan(maxAgeSeconds);
        evicted += graph.evictBelowWeight(minWeight);
        return evicted;
    }

    public void clear() {
        graph.clear();
        sessions.clear();
    }

    public InMemoryGraph getGraph() {
        return graph;
    }

    public static GraphRAGService create() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private InMemoryGraph graph;
        private int graphCapacity = 10000;
        private int maxContextItems = 20;
        private int maxContextDepth = 2;

        private Builder() {}

        public Builder graph(InMemoryGraph graph) {
            this.graph = graph;
            return this;
        }

        public Builder graphCapacity(int capacity) {
            this.graphCapacity = capacity;
            return this;
        }

        public Builder maxContextItems(int items) {
            this.maxContextItems = items;
            return this;
        }

        public Builder maxContextDepth(int depth) {
            this.maxContextDepth = depth;
            return this;
        }

        public GraphRAGService build() {
            return new GraphRAGService(this);
        }
    }

    public record ConversationMessage(
        String role,
        String content,
        Instant timestamp
    ) {}
}
