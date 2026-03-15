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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class VectorEngine {
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.2;

    private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;

    private final Map<String, float[]> vectorIndex = new ConcurrentHashMap<>();

    public VectorEngine() {
    }

    public VectorEngine(double similarityThreshold) {
        this.similarityThreshold = Math.max(0.0, Math.min(1.0, similarityThreshold));
    }

    public void index(String id, float[] embedding) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id no puede ser null o vacio");
        }
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("embedding no puede ser null o vacio");
        }

        vectorIndex.put(id, embedding.clone());
    }

    public boolean remove(String id) {
        return vectorIndex.remove(id) != null;
    }

    public void clear() {
        vectorIndex.clear();
    }

    public boolean contains(String id) {
        return vectorIndex.containsKey(id);
    }

    public int size() {
        return vectorIndex.size();
    }

    public Map<String, Double> search(float[] queryEmbedding) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            return Map.of();
        }

        Map<String, Double> scores = new HashMap<>();

        for (Map.Entry<String, float[]> entry : vectorIndex.entrySet()) {
            double similarity = VectorUtils.cosineSimilarity(queryEmbedding, entry.getValue());

            if (similarity > similarityThreshold) {
                scores.put(entry.getKey(), similarity);
            }
        }

        return scores;
    }

    public Map<String, Double> searchTopK(float[] queryEmbedding, int topK) {
        if (queryEmbedding == null || queryEmbedding.length == 0 || topK <= 0) {
            return Map.of();
        }

        Map<String, Double> allScores = new HashMap<>();
        for (Map.Entry<String, float[]> entry : vectorIndex.entrySet()) {
            double similarity = VectorUtils.cosineSimilarity(queryEmbedding, entry.getValue());
            allScores.put(entry.getKey(), similarity);
        }

        return allScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double threshold) {
        this.similarityThreshold = Math.max(0.0, Math.min(1.0, threshold));
    }

    public String getStats() {
        if (vectorIndex.isEmpty()) {
            return "VectorEngine Stats: empty index";
        }

        int dimension = vectorIndex.values().iterator().next().length;

        return String.format(
            "VectorEngine Stats: %d documents, %d dimensions, threshold: %.2f",
            vectorIndex.size(),
            dimension,
            similarityThreshold
        );
    }

    public float[] getEmbedding(String id) {
        float[] embedding = vectorIndex.get(id);
        return embedding != null ? embedding.clone() : null;
    }

    public double similarity(String id1, String id2) {
        float[] v1 = vectorIndex.get(id1);
        float[] v2 = vectorIndex.get(id2);

        if (v1 == null || v2 == null) {
            return -1.0;
        }

        return VectorUtils.cosineSimilarity(v1, v2);
    }
}
