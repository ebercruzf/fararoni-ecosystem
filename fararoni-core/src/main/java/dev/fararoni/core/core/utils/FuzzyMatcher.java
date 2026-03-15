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
package dev.fararoni.core.core.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class FuzzyMatcher {
    private static final int WORD_START_BONUS = 20;

    private static final int ABSOLUTE_START_BONUS = 25;

    private static final int CONSECUTIVE_BONUS = 5;

    private static final int EXACT_SUBSTRING_BONUS = 50;

    private static final int DISTANCE_PENALTY = 1;

    private static final int BASE_MATCH_SCORE = 10;

    private static final int MIN_VALID_SCORE = 1;

    private FuzzyMatcher() {
    }

    public static List<String> rank(String pattern, List<String> candidates, int maxResults) {
        Objects.requireNonNull(pattern, "pattern no puede ser null");
        Objects.requireNonNull(candidates, "candidates no puede ser null");

        if (pattern.isEmpty() || maxResults <= 0) {
            return List.of();
        }

        String lowerPattern = pattern.toLowerCase();

        return candidates.stream()
            .map(candidate -> new ScoredCandidate(candidate, score(lowerPattern, candidate)))
            .filter(sc -> sc.score >= MIN_VALID_SCORE)
            .sorted(Comparator.comparingInt((ScoredCandidate sc) -> sc.score).reversed())
            .limit(maxResults)
            .map(sc -> sc.candidate)
            .toList();
    }

    public static List<String> rank(String pattern, List<String> candidates) {
        return rank(pattern, candidates, Integer.MAX_VALUE);
    }

    public static boolean matches(String pattern, String text) {
        if (pattern == null || text == null) {
            return false;
        }
        return score(pattern.toLowerCase(), text) >= MIN_VALID_SCORE;
    }

    public static int score(String pattern, String text) {
        if (pattern == null || text == null || pattern.isEmpty() || text.isEmpty()) {
            return 0;
        }

        String lowerPattern = pattern.toLowerCase();
        String lowerText = text.toLowerCase();

        if (!containsInOrder(lowerPattern, lowerText)) {
            return 0;
        }

        return calculateScore(lowerPattern, text, lowerText);
    }

    private static int calculateScore(String lowerPattern, String originalText, String lowerText) {
        int score = 0;
        int patternIdx = 0;
        int lastMatchIdx = -1;
        int consecutiveCount = 0;

        if (lowerText.contains(lowerPattern)) {
            score += EXACT_SUBSTRING_BONUS;
        }

        for (int textIdx = 0; textIdx < lowerText.length() && patternIdx < lowerPattern.length(); textIdx++) {
            char patternChar = lowerPattern.charAt(patternIdx);
            char textChar = lowerText.charAt(textIdx);

            if (patternChar == textChar) {
                score += BASE_MATCH_SCORE;

                if (textIdx == 0) {
                    score += ABSOLUTE_START_BONUS;
                }
                else if (isWordStart(originalText, textIdx)) {
                    score += WORD_START_BONUS;
                }

                if (lastMatchIdx >= 0 && textIdx == lastMatchIdx + 1) {
                    consecutiveCount++;
                    score += CONSECUTIVE_BONUS * consecutiveCount;
                } else {
                    consecutiveCount = 0;
                }

                if (patternIdx == 0) {
                    score -= textIdx * DISTANCE_PENALTY;
                }

                lastMatchIdx = textIdx;
                patternIdx++;
            }
        }

        double lengthRatio = (double) lowerPattern.length() / lowerText.length();
        if (lengthRatio > 0.5) {
            score += (int) (lengthRatio * 10);
        }

        return Math.max(0, score);
    }

    private static boolean isWordStart(String text, int idx) {
        if (idx == 0) {
            return true;
        }

        char current = text.charAt(idx);
        char previous = text.charAt(idx - 1);

        if (isSeparator(previous)) {
            return true;
        }

        if (Character.isUpperCase(current) && Character.isLowerCase(previous)) {
            return true;
        }

        if (Character.isDigit(current) != Character.isDigit(previous)) {
            return true;
        }

        return false;
    }

    private static boolean isSeparator(char c) {
        return c == '_' || c == '-' || c == '/' || c == '\\' || c == '.' || c == ' ';
    }

    private static boolean containsInOrder(String pattern, String text) {
        int patternIdx = 0;

        for (int textIdx = 0; textIdx < text.length() && patternIdx < pattern.length(); textIdx++) {
            if (text.charAt(textIdx) == pattern.charAt(patternIdx)) {
                patternIdx++;
            }
        }

        return patternIdx == pattern.length();
    }

    private static final class ScoredCandidate {
        final String candidate;
        final int score;

        ScoredCandidate(String candidate, int score) {
            this.candidate = candidate;
            this.score = score;
        }
    }

    public static String findBest(String pattern, List<String> candidates) {
        List<String> results = rank(pattern, candidates, 1);
        return results.isEmpty() ? null : results.get(0);
    }

    public static List<String> filter(String pattern, List<String> candidates) {
        if (pattern == null || pattern.isEmpty() || candidates == null) {
            return List.of();
        }

        String lowerPattern = pattern.toLowerCase();
        List<String> results = new ArrayList<>();

        for (String candidate : candidates) {
            if (candidate != null && matches(lowerPattern, candidate)) {
                results.add(candidate);
            }
        }

        return results;
    }
}
