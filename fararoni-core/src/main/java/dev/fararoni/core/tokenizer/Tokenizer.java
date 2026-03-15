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
package dev.fararoni.core.tokenizer;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public interface Tokenizer {
    List<Integer> encode(String text);

    String decode(List<Integer> tokenIds);

    List<String> tokenize(String text);

    default int countTokens(String text) {
        return encode(text).size();
    }

    default String truncateToTokenLimit(String text, int maxTokens) {
        if (countTokens(text) <= maxTokens) {
            return text;
        }

        var tokens = encode(text);
        if (tokens.size() <= maxTokens) {
            return text;
        }

        var truncatedTokens = tokens.subList(0, maxTokens);
        var truncatedText = decode(truncatedTokens);

        return findBestTruncationPoint(truncatedText);
    }

    default List<String> chunkText(String text, int maxTokensPerChunk) {
        var result = new java.util.ArrayList<String>();

        if (countTokens(text) <= maxTokensPerChunk) {
            result.add(text);
            return result;
        }

        var paragraphs = text.split("\n\n+");
        var currentChunk = new StringBuilder();
        int currentTokens = 0;

        for (var paragraph : paragraphs) {
            int paragraphTokens = countTokens(paragraph);

            if (paragraphTokens > maxTokensPerChunk) {
                if (currentChunk.length() > 0) {
                    result.add(currentChunk.toString().trim());
                    currentChunk.setLength(0);
                    currentTokens = 0;
                }

                result.addAll(splitLargeParagraph(paragraph, maxTokensPerChunk));
            } else if (currentTokens + paragraphTokens > maxTokensPerChunk) {
                if (currentChunk.length() > 0) {
                    result.add(currentChunk.toString().trim());
                }
                currentChunk = new StringBuilder(paragraph);
                currentTokens = paragraphTokens;
            } else {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(paragraph);
                currentTokens += paragraphTokens + 1;
            }
        }

        if (currentChunk.length() > 0) {
            result.add(currentChunk.toString().trim());
        }

        return result;
    }

    default boolean isWithinTokenLimit(String text, int maxTokens) {
        return countTokens(text) <= maxTokens;
    }

    default int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }

    private String findBestTruncationPoint(String truncatedText) {
        var sentenceEnders = new String[]{".", "!", "?"};

        for (var ender : sentenceEnders) {
            int lastIndex = truncatedText.lastIndexOf(ender);
            if (lastIndex > truncatedText.length() * 0.8) {
                return truncatedText.substring(0, lastIndex + 1).trim();
            }
        }

        int lastComma = truncatedText.lastIndexOf(',');
        int lastSpace = truncatedText.lastIndexOf(' ');

        if (lastComma > truncatedText.length() * 0.9) {
            return truncatedText.substring(0, lastComma).trim();
        }

        if (lastSpace > truncatedText.length() * 0.95) {
            return truncatedText.substring(0, lastSpace).trim();
        }

        return truncatedText.trim();
    }

    private List<String> splitLargeParagraph(String paragraph, int maxTokensPerChunk) {
        var result = new java.util.ArrayList<String>();

        var sentences = paragraph.split("(?<=[.!?])\\s+");
        var currentChunk = new StringBuilder();
        int currentTokens = 0;

        for (var sentence : sentences) {
            int sentenceTokens = countTokens(sentence);

            if (currentTokens + sentenceTokens > maxTokensPerChunk) {
                if (currentChunk.length() > 0) {
                    result.add(currentChunk.toString().trim());
                    currentChunk.setLength(0);
                    currentTokens = 0;
                }

                if (sentenceTokens > maxTokensPerChunk) {
                    result.addAll(splitByWords(sentence, maxTokensPerChunk));
                } else {
                    currentChunk.append(sentence);
                    currentTokens = sentenceTokens;
                }
            } else {
                if (currentChunk.length() > 0) {
                    currentChunk.append(" ");
                }
                currentChunk.append(sentence);
                currentTokens += sentenceTokens;
            }
        }

        if (currentChunk.length() > 0) {
            result.add(currentChunk.toString().trim());
        }

        return result;
    }

    private List<String> splitByWords(String text, int maxTokensPerChunk) {
        var result = new java.util.ArrayList<String>();
        var words = text.split("\\s+");
        var currentChunk = new StringBuilder();

        for (var word : words) {
            var testChunk = currentChunk.length() == 0 ? word : currentChunk + " " + word;

            if (countTokens(testChunk) > maxTokensPerChunk) {
                if (currentChunk.length() > 0) {
                    result.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder(word);
                } else {
                    result.add(word);
                }
            } else {
                if (currentChunk.length() > 0) {
                    currentChunk.append(" ");
                }
                currentChunk.append(word);
            }
        }

        if (currentChunk.length() > 0) {
            result.add(currentChunk.toString().trim());
        }

        return result;
    }
}
