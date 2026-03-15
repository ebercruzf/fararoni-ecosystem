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
package dev.fararoni.core.core.search;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class BM25Engine {
    private static final double K1 = 1.5;

    private static final double B = 0.75;

    private static final int MIN_TOKEN_LENGTH = 3;

    private final List<Document> documents = new CopyOnWriteArrayList<>();

    private final Map<String, Integer> docFrequencies = new ConcurrentHashMap<>();

    private volatile double avgDocLength = 0.0;

    public static class Document {
        final String id;

        final String content;

        final List<String> tokens;

        public Document(String id, String content) {
            this.id = id;
            this.content = content;
            this.tokens = tokenize(content);
        }

        public String getId() {
            return id;
        }

        public String getContent() {
            return content;
        }

        public List<String> getTokens() {
            return tokens;
        }
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return Arrays.stream(text.toLowerCase().split("[^a-z0-9_.@$-]+"))
                .filter(t -> t.length() > 2)
                .collect(Collectors.toList());
    }

    public void index(String id, String content) {
        if (id == null || content == null) {
            return;
        }

        Document doc = new Document(id, content);
        documents.add(doc);

        Set<String> uniqueTokens = new HashSet<>(doc.tokens);
        for (String token : uniqueTokens) {
            docFrequencies.merge(token, 1, Integer::sum);
        }

        long totalLength = documents.stream()
                .mapToInt(d -> d.tokens.size())
                .sum();
        avgDocLength = (double) totalLength / documents.size();
    }

    public void clear() {
        documents.clear();
        docFrequencies.clear();
        avgDocLength = 0.0;
    }

    public int getDocumentCount() {
        return documents.size();
    }

    public Map<String, Double> search(String query) {
        if (query == null || query.isBlank()) {
            return Map.of();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> scores = new HashMap<>();

        for (Document doc : documents) {
            double score = calculateBM25Score(doc, queryTokens);
            if (score > 0) {
                scores.put(doc.id, score);
            }
        }

        return scores;
    }

    private double calculateBM25Score(Document doc, List<String> queryTokens) {
        double score = 0.0;

        for (String term : queryTokens) {
            Integer docFreq = docFrequencies.get(term);
            if (docFreq == null) {
                continue;
            }

            long termFreq = doc.tokens.stream()
                    .filter(t -> t.equals(term))
                    .count();

            if (termFreq == 0) {
                continue;
            }

            double idf = Math.log(1 + (documents.size() - docFreq + 0.5) / (docFreq + 0.5));

            double tfComponent = (K1 + 1) * termFreq;
            double lengthNorm = 1 - B + B * (doc.tokens.size() / avgDocLength);
            double denominator = termFreq + K1 * lengthNorm;

            score += idf * (tfComponent / denominator);
        }

        return score;
    }

    public String getStats() {
        return String.format(
            "BM25Engine Stats: %d documents, %d unique terms, avg length: %.1f tokens",
            documents.size(),
            docFrequencies.size(),
            avgDocLength
        );
    }

    public boolean containsTerm(String term) {
        if (term == null) {
            return false;
        }
        return docFrequencies.containsKey(term.toLowerCase());
    }

    public int getDocumentFrequency(String term) {
        if (term == null) {
            return 0;
        }
        return docFrequencies.getOrDefault(term.toLowerCase(), 0);
    }
}
