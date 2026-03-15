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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class TheHound {
    private static final int RRF_K = 60;

    private final BM25Engine bm25 = new BM25Engine();

    private final VectorEngine vectorEngine = new VectorEngine();

    private final EmbeddingProvider embeddingProvider;

    private volatile boolean lexicalOnlyMode = false;

    private int dummyEmbeddingsDetected = 0;

    @FunctionalInterface
    public interface EmbeddingProvider {
        float[] getEmbedding(String text);
    }

    public TheHound(EmbeddingProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("EmbeddingProvider no puede ser null");
        }
        this.embeddingProvider = provider;
    }

    private boolean isDummyEmbedding(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return true;
        }

        double sumSquares = 0.0;
        for (float value : embedding) {
            sumSquares += value * value;
        }

        double norm = Math.sqrt(sumSquares);
        return norm < 1e-6;
    }

    public void learn(String id, String content) {
        if (id == null || content == null || content.isBlank()) {
            return;
        }

        bm25.index(id, content);

        float[] embedding = embeddingProvider.getEmbedding(content);

        if (isDummyEmbedding(embedding)) {
            dummyEmbeddingsDetected++;

            if (!lexicalOnlyMode && dummyEmbeddingsDetected == 1) {
                lexicalOnlyMode = true;
                System.err.println("[TheHound] VULNERABILIDAD 4 CORREGIDA: " +
                    "Embedding provider dummy detectado. Activando modo SOLO-LEXICO (BM25).");
            }
            return;
        }

        vectorEngine.index(id, embedding);
    }

    public void learnWithEmbedding(String id, String content, float[] embedding) {
        if (id == null || content == null) {
            return;
        }

        bm25.index(id, content);

        if (embedding != null && embedding.length > 0) {
            vectorEngine.index(id, embedding);
        }
    }

    public void forget() {
        bm25.clear();
        vectorEngine.clear();

        lexicalOnlyMode = false;
        dummyEmbeddingsDetected = 0;
    }

    public List<String> hunt(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }

        if (lexicalOnlyMode) {
            return huntLexical(query, topK);
        }

        Map<String, Double> bm25Scores = bm25.search(query);

        float[] queryVector = embeddingProvider.getEmbedding(query);

        Map<String, Double> vectorScores;
        if (isDummyEmbedding(queryVector)) {
            vectorScores = Map.of();
        } else {
            vectorScores = vectorEngine.search(queryVector);
        }

        Map<String, Double> rrfScores = new HashMap<>();
        mergeRRF(rrfScores, bm25Scores);
        mergeRRF(rrfScores, vectorScores);

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<String> huntLexical(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }

        return bm25.search(query).entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<String> huntSemantic(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }

        float[] queryVector = embeddingProvider.getEmbedding(query);
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }

        return vectorEngine.search(queryVector).entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private void mergeRRF(Map<String, Double> finalScores, Map<String, Double> sourceScores) {
        if (sourceScores == null || sourceScores.isEmpty()) {
            return;
        }

        List<String> rankedIds = sourceScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (int i = 0; i < rankedIds.size(); i++) {
            String id = rankedIds.get(i);
            int rank = i + 1;

            double rrfScore = 1.0 / (RRF_K + rank);

            finalScores.merge(id, rrfScore, Double::sum);
        }
    }

    public String getStats() {
        String modeInfo = lexicalOnlyMode
            ? "SOLO-LEXICO (embeddings dummy detectados: " + dummyEmbeddingsDetected + ")"
            : "HIBRIDO (BM25 + Vector)";

        return String.format(
            "TheHound Stats:\n  - Modo: %s\n  - %s\n  - %s",
            modeInfo,
            bm25.getStats(),
            vectorEngine.getStats()
        );
    }

    public boolean isLexicalOnlyMode() {
        return lexicalOnlyMode;
    }

    public int getDocumentCount() {
        return bm25.getDocumentCount();
    }

    public BM25Engine getBM25Engine() {
        return bm25;
    }

    public VectorEngine getVectorEngine() {
        return vectorEngine;
    }
}
