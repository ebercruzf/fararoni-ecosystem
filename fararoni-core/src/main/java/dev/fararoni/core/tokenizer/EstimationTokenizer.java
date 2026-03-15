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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class EstimationTokenizer implements Tokenizer {
    private static final double CHARS_PER_TOKEN = 4.0;

    public EstimationTokenizer() {
        System.out.println("Usando tokenizador de estimacion (sin dependencias JNI)");
    }

    @Override
    public List<Integer> encode(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<Integer> ids = new ArrayList<>();
        String[] words = text.split("\\s+");
        for (String word : words) {
            int tokensForWord = Math.max(1, (int) Math.ceil(word.length() / CHARS_PER_TOKEN));
            for (int i = 0; i < tokensForWord; i++) {
                ids.add(Math.abs((word + i).hashCode() % 100000));
            }
        }
        return ids;
    }

    @Override
    public String decode(List<Integer> tokenIds) {
        return "[Decodificación no disponible en modo estimación]";
    }

    @Override
    public List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.length() <= 4) {
                tokens.add(word);
            } else {
                int pos = 0;
                while (pos < word.length()) {
                    int end = Math.min(pos + 4, word.length());
                    tokens.add(word.substring(pos, end));
                    pos = end;
                }
            }
        }
        return tokens;
    }

    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return estimateTokens(text);
    }

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int baseEstimate = (int) Math.ceil(text.length() / CHARS_PER_TOKEN);

        long specialChars = text.chars()
            .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
            .count();

        long digits = text.chars().filter(Character::isDigit).count();

        double adjustment = 1.0 + (specialChars * 0.1 + digits * 0.05) / Math.max(1, text.length());

        return (int) Math.ceil(baseEstimate * Math.min(adjustment, 1.5));
    }
}
