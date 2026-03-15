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

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class VectorUtils {
    private VectorUtils() {
        throw new UnsupportedOperationException("Clase de utilidades - no instanciar");
    }

    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null) {
            return 0.0;
        }

        if (vectorA.length != vectorB.length) {
            return 0.0;
        }

        if (vectorA.length == 0) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += (double) vectorA[i] * vectorB[i];
            normA += (double) vectorA[i] * vectorA[i];
            normB += (double) vectorB[i] * vectorB[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static double euclideanDistance(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null) {
            return Double.MAX_VALUE;
        }

        if (vectorA.length != vectorB.length) {
            return Double.MAX_VALUE;
        }

        double sumSquares = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            double diff = vectorA[i] - vectorB[i];
            sumSquares += diff * diff;
        }

        return Math.sqrt(sumSquares);
    }

    public static float[] normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return new float[0];
        }

        double norm = 0.0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);

        if (norm == 0.0) {
            return vector.clone();
        }

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }

        return normalized;
    }

    public static double dotProduct(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null) {
            return 0.0;
        }

        if (vectorA.length != vectorB.length) {
            return 0.0;
        }

        double result = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            result += (double) vectorA[i] * vectorB[i];
        }

        return result;
    }
}
