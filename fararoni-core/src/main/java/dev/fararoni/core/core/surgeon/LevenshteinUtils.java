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
package dev.fararoni.core.core.surgeon;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class LevenshteinUtils {
    private LevenshteinUtils() {
    }

    private static final int MAX_FULL_LEVENSHTEIN_LENGTH = 500;

    private static final int WINDOW_SIZE = 100;

    private static final int TRIGRAM_SIZE = 3;

    public static int calculateDistance(String x, String y) {
        if (x == null && y == null) return 0;
        if (x == null) return y.length();
        if (y == null) return x.length();
        if (x.equals(y)) return 0;

        int[] dp = new int[y.length() + 1];

        for (int i = 0; i <= y.length(); i++) {
            dp[i] = i;
        }

        for (int i = 1; i <= x.length(); i++) {
            int[] newDp = new int[y.length() + 1];
            newDp[0] = i;

            for (int j = 1; j <= y.length(); j++) {
                int cost = (x.charAt(i - 1) == y.charAt(j - 1)) ? 0 : 1;
                newDp[j] = Math.min(
                    Math.min(newDp[j - 1] + 1, dp[j] + 1),
                    dp[j - 1] + cost
                );
            }
            dp = newDp;
        }

        return dp[y.length()];
    }

    public static double calculateSimilarity(String x, String y) {
        if (x == null && y == null) return 1.0;
        if (x == null || y == null) return 0.0;
        if (x.equals(y)) return 1.0;

        int maxLen = Math.max(x.length(), y.length());
        if (maxLen == 0) return 1.0;

        int distance = calculateDistance(x, y);
        return 1.0 - ((double) distance / maxLen);
    }

    public static String normalizeCode(String code) {
        if (code == null) return null;
        return code.trim().replaceAll("\\s+", " ");
    }

    public static String normalizeIndentation(String code) {
        if (code == null) return null;

        StringBuilder result = new StringBuilder();
        String[] lines = code.split("\\n");

        for (int i = 0; i < lines.length; i++) {
            result.append(lines[i].trim());
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    public static boolean isSimilar(String x, String y, double threshold) {
        return calculateSimilarity(x, y) >= threshold;
    }

    public static double calculateSimilarityOptimized(String x, String y) {
        if (x == null && y == null) return 1.0;
        if (x == null || y == null) return 0.0;
        if (x.equals(y)) return 1.0;

        int lenX = x.length();
        int lenY = y.length();

        double lengthRatio = (double) Math.min(lenX, lenY) / Math.max(lenX, lenY);
        if (lengthRatio < 0.5) {
            return lengthRatio * 0.5;
        }

        if (lenX <= MAX_FULL_LEVENSHTEIN_LENGTH && lenY <= MAX_FULL_LEVENSHTEIN_LENGTH) {
            return calculateSimilarity(x, y);
        }

        double trigramSimilarity = calculateTrigramSimilarity(x, y);
        double windowSimilarity = calculateWindowSimilarity(x, y);

        return trigramSimilarity * 0.4 + windowSimilarity * 0.6;
    }

    public static double calculateTrigramSimilarity(String x, String y) {
        if (x == null || y == null) return 0.0;
        if (x.equals(y)) return 1.0;

        if (x.length() < TRIGRAM_SIZE || y.length() < TRIGRAM_SIZE) {
            return calculateSimilarity(x, y);
        }

        java.util.Set<String> trigramsX = extractTrigrams(x);
        java.util.Set<String> trigramsY = extractTrigrams(y);

        java.util.Set<String> intersection = new java.util.HashSet<>(trigramsX);
        intersection.retainAll(trigramsY);

        java.util.Set<String> union = new java.util.HashSet<>(trigramsX);
        union.addAll(trigramsY);

        if (union.isEmpty()) return 1.0;
        return (double) intersection.size() / union.size();
    }

    private static java.util.Set<String> extractTrigrams(String s) {
        java.util.Set<String> trigrams = new java.util.HashSet<>();
        for (int i = 0; i <= s.length() - TRIGRAM_SIZE; i++) {
            trigrams.add(s.substring(i, i + TRIGRAM_SIZE));
        }
        return trigrams;
    }

    public static double calculateWindowSimilarity(String x, String y) {
        if (x == null || y == null) return 0.0;
        if (x.equals(y)) return 1.0;

        int lenX = x.length();
        int lenY = y.length();

        double sumSimilarity = 0.0;
        int windowCount = 0;

        String startX = x.substring(0, Math.min(WINDOW_SIZE, lenX));
        String startY = y.substring(0, Math.min(WINDOW_SIZE, lenY));
        sumSimilarity += calculateSimilarity(startX, startY);
        windowCount++;

        if (lenX > WINDOW_SIZE * 2 && lenY > WINDOW_SIZE * 2) {
            int midX = lenX / 2 - WINDOW_SIZE / 2;
            int midY = lenY / 2 - WINDOW_SIZE / 2;
            String midWindowX = x.substring(midX, Math.min(midX + WINDOW_SIZE, lenX));
            String midWindowY = y.substring(midY, Math.min(midY + WINDOW_SIZE, lenY));
            sumSimilarity += calculateSimilarity(midWindowX, midWindowY);
            windowCount++;
        }

        String endX = x.substring(Math.max(0, lenX - WINDOW_SIZE));
        String endY = y.substring(Math.max(0, lenY - WINDOW_SIZE));
        sumSimilarity += calculateSimilarity(endX, endY);
        windowCount++;

        return sumSimilarity / windowCount;
    }

    public static int findBestMatchPosition(String largeText, String searchBlock, double minSimilarity) {
        if (largeText == null || searchBlock == null) return -1;
        if (searchBlock.length() > largeText.length()) return -1;

        java.util.Set<String> searchTrigrams = extractTrigrams(searchBlock);
        if (searchTrigrams.isEmpty()) return -1;

        int windowSize = searchBlock.length();
        int bestPosition = -1;
        double bestSimilarity = minSimilarity - 0.01;

        int step = Math.max(1, windowSize / 4);

        for (int i = 0; i <= largeText.length() - windowSize; i += step) {
            String window = largeText.substring(i, i + windowSize);

            java.util.Set<String> windowTrigrams = extractTrigrams(window);
            windowTrigrams.retainAll(searchTrigrams);

            double trigramOverlap = (double) windowTrigrams.size() / searchTrigrams.size();
            if (trigramOverlap >= minSimilarity * 0.5) {
                double similarity = calculateSimilarity(window, searchBlock);

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestPosition = i;
                }
            }
        }

        if (bestPosition != -1 && step > 1) {
            int refineStart = Math.max(0, bestPosition - step);
            int refineEnd = Math.min(largeText.length() - windowSize, bestPosition + step);

            for (int i = refineStart; i <= refineEnd; i++) {
                String window = largeText.substring(i, i + windowSize);
                double similarity = calculateSimilarity(window, searchBlock);

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestPosition = i;
                }
            }
        }

        return bestSimilarity >= minSimilarity ? bestPosition : -1;
    }

    public static boolean couldBeSimilar(String x, String y, double minSimilarity) {
        if (x == null || y == null) return x == y;
        if (x.equals(y)) return true;

        double lengthRatio = (double) Math.min(x.length(), y.length()) / Math.max(x.length(), y.length());
        if (lengthRatio < minSimilarity) {
            return false;
        }

        if (x.length() > 10 && y.length() > 10) {
            int commonChars = 0;
            for (int i = 0; i < Math.min(10, x.length()); i++) {
                if (y.indexOf(x.charAt(i)) >= 0) commonChars++;
            }
            if (commonChars < 3) return false;
        }

        return true;
    }
}
