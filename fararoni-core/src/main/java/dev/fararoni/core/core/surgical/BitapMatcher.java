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
package dev.fararoni.core.core.surgical;

import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class BitapMatcher {
    private static final int MAX_PATTERN_LENGTH = 63;

    public Optional<Integer> findExact(String text, String pattern) {
        if (text == null || pattern == null || pattern.isEmpty()) {
            return Optional.empty();
        }

        int index = text.indexOf(pattern);
        return index >= 0 ? Optional.of(index) : Optional.empty();
    }

    public int find(String text, String pattern, int maxErrors) {
        MatchResult result = findFuzzy(text, pattern, maxErrors);
        return result.found() ? result.position() : -1;
    }

    public int find(String text, String pattern, float threshold) {
        int k = (int) (pattern.length() * threshold);
        return find(text, pattern, k);
    }

    public MatchResult findFuzzy(String text, String pattern, int maxErrors) {
        if (text == null || pattern == null || pattern.isEmpty()) {
            return MatchResult.noMatch();
        }

        if (pattern.length() > MAX_PATTERN_LENGTH) {
            Optional<Integer> exact = findExact(text, pattern);
            return exact.map(pos -> new MatchResult(pos, 0, pattern.length()))
                       .orElse(MatchResult.noMatch());
        }

        return bitapSearch(text, pattern, maxErrors);
    }

    private MatchResult bitapSearch(String text, String pattern, int maxErrors) {
        int m = pattern.length();
        int n = text.length();

        if (m == 0) return MatchResult.noMatch();
        if (n == 0) return MatchResult.noMatch();

        long[] patternMask = new long[256];
        for (int i = 0; i < 256; i++) {
            patternMask[i] = ~0L;
        }
        for (int i = 0; i < m; i++) {
            patternMask[pattern.charAt(i) & 0xFF] &= ~(1L << i);
        }

        long[] state = new long[maxErrors + 1];
        for (int i = 0; i <= maxErrors; i++) {
            state[i] = ~1L;
        }

        long matchMask = 1L << (m - 1);

        for (int i = 0; i < n; i++) {
            long oldState0 = state[0];
            state[0] = (state[0] << 1) | patternMask[text.charAt(i) & 0xFF];

            for (int d = 1; d <= maxErrors; d++) {
                long tmp = state[d];
                state[d] = ((state[d] << 1) | patternMask[text.charAt(i) & 0xFF])
                         & (oldState0 << 1)
                         & (tmp << 1)
                         & oldState0;
                oldState0 = tmp;
            }

            for (int d = 0; d <= maxErrors; d++) {
                if ((state[d] & matchMask) == 0) {
                    int matchStart = i - m + 1;
                    if (matchStart >= 0) {
                        return new MatchResult(matchStart, d, m);
                    }
                }
            }
        }

        return MatchResult.noMatch();
    }

    public int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return s1 == s2 ? 0 : Integer.MAX_VALUE;
        }

        int m = s1.length();
        int n = s2.length();

        if (m == 0) return n;
        if (n == 0) return m;

        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                    Math.min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                );
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[n];
    }

    public record MatchResult(
        int position,
        int errors,
        int length
    ) {
        public boolean found() {
            return position >= 0;
        }

        public static MatchResult noMatch() {
            return new MatchResult(-1, -1, 0);
        }
    }
}
