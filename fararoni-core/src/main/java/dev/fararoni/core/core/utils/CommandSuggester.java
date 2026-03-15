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

import java.util.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class CommandSuggester {
    private static final double MAX_ERROR_RATIO = 0.5;

    private static final int MAX_ABSOLUTE_ERRORS = 3;

    private static final int MIN_LENGTH_FOR_RATIO = 5;

    private static final int SHORT_COMMAND_MAX_ERRORS = 2;

    private static final int MIN_DISTANCE_FOR_SUGGESTION = 1;

    private CommandSuggester() {
        throw new AssertionError("CommandSuggester es una clase de utilidades estaticas");
    }

    public static Optional<String> suggest(String input, Collection<String> validCommands) {
        Objects.requireNonNull(validCommands, "validCommands no puede ser null");

        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String normalizedInput = input.trim().toLowerCase();

        for (String command : validCommands) {
            if (command != null && normalizedInput.equals(command.trim().toLowerCase())) {
                return Optional.empty();
            }
        }

        String bestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (String command : validCommands) {
            if (command == null || command.isBlank()) {
                continue;
            }

            String normalizedCommand = command.trim().toLowerCase();

            int distance = computeLevenshtein(normalizedInput, normalizedCommand);

            if (isAcceptableDistance(distance, normalizedCommand.length())) {
                if (distance < minDistance) {
                    minDistance = distance;
                    bestMatch = command;
                }
            }
        }

        return Optional.ofNullable(bestMatch);
    }

    public static List<String> suggestMultiple(String input, Collection<String> validCommands, int maxSuggestions) {
        Objects.requireNonNull(validCommands, "validCommands no puede ser null");

        if (maxSuggestions < 1) {
            throw new IllegalArgumentException("maxSuggestions debe ser al menos 1");
        }

        if (input == null || input.isBlank()) {
            return Collections.emptyList();
        }

        String normalizedInput = input.trim().toLowerCase();

        for (String command : validCommands) {
            if (command != null && normalizedInput.equals(command.trim().toLowerCase())) {
                return Collections.emptyList();
            }
        }

        List<ScoredCommand> candidates = new ArrayList<>();

        for (String command : validCommands) {
            if (command == null || command.isBlank()) {
                continue;
            }

            String normalizedCommand = command.trim().toLowerCase();
            int distance = computeLevenshtein(normalizedInput, normalizedCommand);

            if (isAcceptableDistance(distance, normalizedCommand.length())) {
                candidates.add(new ScoredCommand(command, distance));
            }
        }

        return candidates.stream()
                .sorted(Comparator.comparingInt(ScoredCommand::distance))
                .limit(maxSuggestions)
                .map(ScoredCommand::command)
                .toList();
    }

    public static int computeLevenshtein(String s1, String s2) {
        Objects.requireNonNull(s1, "s1 no puede ser null");
        Objects.requireNonNull(s2, "s2 no puede ser null");

        if (s1.equals(s2)) {
            return 0;
        }

        int n = s1.length();
        int m = s2.length();

        if (n == 0) return m;
        if (m == 0) return n;

        if (n > m) {
            String temp = s1;
            s1 = s2;
            s2 = temp;
            n = s1.length();
            m = s2.length();
        }

        int[] prevRow = new int[n + 1];
        int[] currRow = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prevRow[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            currRow[0] = i;

            for (int j = 1; j <= n; j++) {
                int cost = (s1.charAt(j - 1) == s2.charAt(i - 1)) ? 0 : 1;

                currRow[j] = Math.min(
                        Math.min(currRow[j - 1] + 1, prevRow[j] + 1),
                        prevRow[j - 1] + cost
                );
            }

            int[] temp = prevRow;
            prevRow = currRow;
            currRow = temp;
        }

        return prevRow[n];
    }

    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }

        int distance = computeLevenshtein(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }

    private static boolean isAcceptableDistance(int distance, int commandLength) {
        if (distance < MIN_DISTANCE_FOR_SUGGESTION) {
            return false;
        }

        if (distance > MAX_ABSOLUTE_ERRORS) {
            return false;
        }

        if (commandLength < MIN_LENGTH_FOR_RATIO) {
            return distance <= SHORT_COMMAND_MAX_ERRORS;
        }

        double errorRatio = (double) distance / commandLength;
        return errorRatio <= MAX_ERROR_RATIO;
    }

    private record ScoredCommand(String command, int distance) {}
}
