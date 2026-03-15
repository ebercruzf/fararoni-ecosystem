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
package dev.fararoni.core.core.kernel.router;

import dev.fararoni.core.client.LlmClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SimpleVectorRouter {
    private final LlmClient llmClient;
    private final Map<String, float[]> anchors = new ConcurrentHashMap<>();
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();

    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.75;
    private double confidenceThreshold;

    private long routeCount = 0;
    private long cacheHits = 0;

    private int expectedDimensions = -1;
    private long dimensionMismatches = 0;

    public SimpleVectorRouter(LlmClient llmClient) {
        this(llmClient, DEFAULT_CONFIDENCE_THRESHOLD);
    }

    public SimpleVectorRouter(LlmClient llmClient, double confidenceThreshold) {
        this.llmClient = llmClient;
        this.confidenceThreshold = confidenceThreshold;
        initializeAnchors();
    }

    private void initializeAnchors() {
        anchors.put("CODING", computeEmbedding(
            "Generate code, write java class, implement feature, create function, programming"));
        anchors.put("ANALYSIS", computeEmbedding(
            "Analyze logs, find bugs, explain architecture, review code, investigate issue"));
        anchors.put("TESTING", computeEmbedding(
            "Write tests, unit testing, mockito, test coverage, QA, verify functionality"));
        anchors.put("PLANNING", computeEmbedding(
            "Create plan, design architecture, project management, requirements, roadmap"));
        anchors.put("DOCUMENTATION", computeEmbedding(
            "Write docs, documentation, README, API reference, comments, javadoc"));
        anchors.put("REFACTORING", computeEmbedding(
            "Refactor code, clean code, improve structure, simplify, optimize code"));
        anchors.put("DEBUGGING", computeEmbedding(
            "Debug issue, fix error, stacktrace, exception, crash, not working"));
        anchors.put("SECURITY", computeEmbedding(
            "Security review, vulnerability, authentication, authorization, encryption"));
    }

    public RouteResult route(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return new RouteResult("GENERAL", 0.0, false);
        }

        routeCount++;
        float[] queryVector = computeEmbeddingCached(userQuery);

        if (queryVector.length == 0) {
            return new RouteResult("GENERAL", 0.0, false);
        }

        String bestMatch = "GENERAL";
        double bestScore = -1.0;

        for (var entry : anchors.entrySet()) {
            if (entry.getValue().length == 0) continue;

            double score = cosineSimilarity(queryVector, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestMatch = entry.getKey();
            }
        }

        boolean confident = bestScore >= confidenceThreshold;
        return new RouteResult(
            confident ? bestMatch : "GENERAL",
            bestScore,
            confident
        );
    }

    public String routeSimple(String userQuery) {
        return route(userQuery).intent();
    }

    public void addAnchor(String intentName, String representativeText) {
        anchors.put(intentName, computeEmbedding(representativeText));
    }

    public void removeAnchor(String intentName) {
        anchors.remove(intentName);
    }

    public java.util.Set<String> getRegisteredIntents() {
        return java.util.Set.copyOf(anchors.keySet());
    }

    public void setConfidenceThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0");
        }
        this.confidenceThreshold = threshold;
    }

    public RouterMetrics getMetrics() {
        return new RouterMetrics(
            routeCount,
            cacheHits,
            embeddingCache.size(),
            anchors.size(),
            routeCount > 0 ? (double) cacheHits / routeCount : 0.0,
            expectedDimensions,
            dimensionMismatches
        );
    }

    public void clearCache() {
        embeddingCache.clear();
        cacheHits = 0;
    }

    public int getExpectedDimensions() {
        return expectedDimensions;
    }

    public void resetExpectedDimensions() {
        expectedDimensions = -1;
        dimensionMismatches = 0;
        anchors.clear();
        embeddingCache.clear();
        initializeAnchors();
    }

    public boolean isDimensionsInitialized() {
        return expectedDimensions > 0;
    }

    private float[] computeEmbedding(String text) {
        try {
            float[] vector = llmClient.getEmbeddings(text);

            if (vector.length > 0) {
                if (expectedDimensions == -1) {
                    expectedDimensions = vector.length;
                    System.out.println("[SimpleVectorRouter] Dimensión de embeddings establecida: " + expectedDimensions);
                } else if (vector.length != expectedDimensions) {
                    dimensionMismatches++;
                    throw new IllegalStateException(String.format(
                        "Dimensión de vector inestable. Esperaba %d pero recibí %d. " +
                        "¿Cambió el modelo de embeddings en Ollama?",
                        expectedDimensions, vector.length));
                }
            }

            return vector;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[SimpleVectorRouter] Error computing embedding: " + e.getMessage());
            return new float[0];
        }
    }

    private float[] computeEmbeddingCached(String text) {
        String key = text.toLowerCase().trim();
        if (embeddingCache.containsKey(key)) {
            cacheHits++;
            return embeddingCache.get(key);
        }
        float[] embedding = computeEmbedding(text);
        if (embedding.length > 0) {
            embeddingCache.put(key, embedding);
        }
        return embedding;
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length || vectorA.length == 0) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0.0) {
            return 0.0;
        }

        return dotProduct / denominator;
    }

    public record RouteResult(
        String intent,
        double score,
        boolean confident
    ) {
        public String intentOrDefault(String defaultIntent) {
            return confident ? intent : defaultIntent;
        }
    }

    public record RouterMetrics(
        long totalRoutes,
        long cacheHits,
        int cacheSize,
        int anchorCount,
        double cacheHitRate,
        int expectedDimensions,
        long dimensionMismatches
    ) {}
}
