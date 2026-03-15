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

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class IndentationNormalizer {
    public String alignIndentation(String source, int matchIndex, String rawReplacement) {
        if (rawReplacement == null || rawReplacement.isEmpty()) {
            return "";
        }

        if (source == null || matchIndex < 0 || matchIndex >= source.length()) {
            return rawReplacement;
        }

        String anchorIndent = detectLineIndentation(source, matchIndex);

        return applyIndentation(rawReplacement, anchorIndent);
    }

    private String detectLineIndentation(String source, int index) {
        int lineStart = source.lastIndexOf('\n', index - 1);
        if (lineStart == -1) {
            lineStart = 0;
        } else {
            lineStart++;
        }

        StringBuilder indent = new StringBuilder();
        for (int i = lineStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == ' ' || c == '\t') {
                indent.append(c);
            } else {
                break;
            }
        }
        return indent.toString();
    }

    private String applyIndentation(String rawBlock, String targetIndent) {
        List<String> lines = rawBlock.lines().collect(Collectors.toList());
        if (lines.isEmpty()) {
            return rawBlock;
        }

        int minCommonIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int currentIndent = countLeadingWhitespace(line);
            if (currentIndent < minCommonIndent) {
                minCommonIndent = currentIndent;
            }
        }
        if (minCommonIndent == Integer.MAX_VALUE) {
            minCommonIndent = 0;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line.isBlank()) {
                result.append("");
            } else {
                String strippedLine = (line.length() >= minCommonIndent)
                    ? line.substring(minCommonIndent)
                    : line.trim();

                result.append(targetIndent).append(strippedLine);
            }

            if (i < lines.size() - 1 || rawBlock.endsWith("\n")) {
                result.append('\n');
            }
        }
        return result.toString();
    }

    private int countLeadingWhitespace(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (Character.isWhitespace(c)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
