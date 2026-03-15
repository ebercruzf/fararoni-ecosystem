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
package dev.fararoni.core.context;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class OutputSanitizer {
    private static final int MAX_ALLOWED_REPEATS = 3;
    private static final int MIN_LINE_LENGTH = 10;

    private static final String TRUNCATION_NOTICE =
        "\n\n[...contenido repetitivo truncado para proteger memoria...]";

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```(?:java|python|javascript|typescript|sql|bash|sh|xml|json|yaml)?\\s*\\n([\\s\\S]*?)\\n```",
        Pattern.MULTILINE
    );

    private OutputSanitizer() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String sanitize(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return "";
        }

        String[] lines = rawResponse.split("\n");
        StringBuilder clean = new StringBuilder();
        String lastLine = "";
        int repeatCount = 0;
        boolean truncated = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.length() >= MIN_LINE_LENGTH) {
                if (isSimilarLine(trimmed, lastLine)) {
                    repeatCount++;
                    if (repeatCount >= MAX_ALLOWED_REPEATS) {
                        truncated = true;
                        break;
                    }
                } else {
                    repeatCount = 0;
                }
                lastLine = trimmed;
            }

            clean.append(line).append("\n");
        }

        String result = clean.toString().trim();

        if (truncated) {
            result += TRUNCATION_NOTICE;
        }

        return result;
    }

    public static List<String> extractCodeBlocks(String response) {
        List<String> codeBlocks = new ArrayList<>();

        if (response == null || response.isBlank()) {
            return codeBlocks;
        }

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(response);
        while (matcher.find()) {
            String code = matcher.group(1);
            if (code != null && !code.isBlank()) {
                codeBlocks.add(code.trim());
            }
        }

        return codeBlocks;
    }

    public static boolean containsRepetition(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }

        String[] lines = response.split("\n");
        String lastLine = "";
        int repeatCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() >= MIN_LINE_LENGTH) {
                if (isSimilarLine(trimmed, lastLine)) {
                    repeatCount++;
                    if (repeatCount >= MAX_ALLOWED_REPEATS) {
                        return true;
                    }
                } else {
                    repeatCount = 0;
                }
                lastLine = trimmed;
            }
        }

        return false;
    }

    public static SanitizeStats analyze(String response) {
        if (response == null || response.isBlank()) {
            return new SanitizeStats(0, 0, 0, false);
        }

        String[] lines = response.split("\n");
        int totalLines = lines.length;
        int codeBlocks = extractCodeBlocks(response).size();
        int maxRepeats = 0;
        String lastLine = "";
        int currentRepeats = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() >= MIN_LINE_LENGTH) {
                if (isSimilarLine(trimmed, lastLine)) {
                    currentRepeats++;
                    maxRepeats = Math.max(maxRepeats, currentRepeats);
                } else {
                    currentRepeats = 0;
                }
                lastLine = trimmed;
            }
        }

        return new SanitizeStats(
            totalLines,
            codeBlocks,
            maxRepeats,
            maxRepeats >= MAX_ALLOWED_REPEATS
        );
    }

    private static boolean isSimilarLine(String line1, String line2) {
        if (line1.equals(line2)) {
            return true;
        }

        String normalized1 = line1.replaceAll("\\d+", "#");
        String normalized2 = line2.replaceAll("\\d+", "#");

        return normalized1.equals(normalized2);
    }

    public record SanitizeStats(
        int totalLines,
        int codeBlocks,
        int maxConsecutiveRepeats,
        boolean wouldBeTruncated
    ) {
        @Override
        public String toString() {
            return String.format(
                "SanitizeStats[lines=%d, code=%d, maxRepeats=%d, truncate=%s]",
                totalLines, codeBlocks, maxConsecutiveRepeats, wouldBeTruncated
            );
        }
    }
}
