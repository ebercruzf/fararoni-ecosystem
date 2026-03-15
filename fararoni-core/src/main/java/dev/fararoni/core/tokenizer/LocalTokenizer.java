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

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class LocalTokenizer implements Tokenizer {
    private final HuggingFaceTokenizer tokenizer;
    private final String modelId;

    public LocalTokenizer(String modelId) {
        this.modelId = modelId;
        try {
            System.out.println("[START] Cargando tokenizador local: " + modelId);
            this.tokenizer = HuggingFaceTokenizer.newInstance(modelId);
            System.out.println("[OK] Tokenizador local cargado exitosamente");
        } catch (Exception e) {
            throw new TokenizationException("Error cargando tokenizador desde: " + modelId, e);
        }
    }

    public LocalTokenizer(Path tokenizerPath) {
        this.modelId = tokenizerPath.toString();
        try {
            System.out.println("[START] Cargando tokenizador desde archivo: " + tokenizerPath);
            this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
            System.out.println("[OK] Tokenizador local cargado desde archivo");
        } catch (IOException e) {
            throw new TokenizationException("Error cargando tokenizador desde: " + tokenizerPath, e);
        }
    }

    @Override
    public List<Integer> encode(String text) {
        if (text == null) {
            throw new TokenizationException("El texto a tokenizar no puede ser nulo");
        }

        if (text.isEmpty()) {
            return List.of();
        }

        try {
            var encoded = tokenizer.encode(text).getIds();
            return Arrays.stream(encoded)
                .mapToInt(l -> (int) l)
                .boxed()
                .toList();
        } catch (Exception e) {
            throw TokenizationException.encode(text, e);
        }
    }

    @Override
    public String decode(List<Integer> tokenIds) {
        if (tokenIds == null) {
            throw new TokenizationException("La lista de token IDs no puede ser nula");
        }

        if (tokenIds.isEmpty()) {
            return "";
        }

        for (int i = 0; i < tokenIds.size(); i++) {
            if (tokenIds.get(i) < 0) {
                throw new TokenizationException("Token ID inválido en posición %d: %d"
                    .formatted(i, tokenIds.get(i)));
            }
        }

        try {
            var ids = tokenIds.stream()
                .mapToLong(Integer::longValue)
                .toArray();
            return tokenizer.decode(ids);
        } catch (Exception e) {
            throw TokenizationException.decode(tokenIds.toString(), e);
        }
    }

    @Override
    public List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        try {
            var tokens = tokenizer.encode(text).getTokens();
            return Arrays.asList(tokens);
        } catch (Exception e) {
            throw new TokenizationException("tokenize", text, "Error obteniendo tokens como strings", e);
        }
    }

    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        try {
            return tokenizer.encode(text).getIds().length;
        } catch (Exception e) {
            return estimateTokens(text);
        }
    }

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        var baseEstimate = Math.max(1, text.length() / 4);
        var complexity = calculateTextComplexity(text);
        return (int) (baseEstimate * complexity);
    }

    private double calculateTextComplexity(String text) {
        double complexity = 1.0;

        long specialChars = text.chars().filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch)).count();
        complexity += (specialChars * 0.1) / text.length();

        long digits = text.chars().filter(Character::isDigit).count();
        complexity += (digits * 0.05) / text.length();

        var words = text.split("\\s+");
        var avgWordLength = Arrays.stream(words)
            .mapToInt(String::length)
            .average()
            .orElse(4.0);

        if (avgWordLength > 6) {
            complexity *= 0.9;
        }

        return Math.max(0.5, Math.min(2.0, complexity));
    }

    public int getVocabSize() {
        return 151936;
    }

    public boolean isValidTokenId(int tokenId) {
        return tokenId >= 0 && tokenId < getVocabSize();
    }

    public TokenInfo getSpecialTokens() {
        try {
            var bosToken = encode("<|im_start|>");
            var eosToken = encode("<|im_end|>");
            var padToken = encode("<|endoftext|>");

            return new TokenInfo(
                bosToken.isEmpty() ? -1 : bosToken.get(0),
                eosToken.isEmpty() ? -1 : eosToken.get(0),
                padToken.isEmpty() ? -1 : padToken.get(0)
            );
        } catch (Exception e) {
            return new TokenInfo(-1, -1, -1);
        }
    }

    public record TokenInfo(int bosToken, int eosToken, int padToken) {
        public boolean hasBosToken() { return bosToken >= 0; }
        public boolean hasEosToken() { return eosToken >= 0; }
        public boolean hasPadToken() { return padToken >= 0; }
    }

    public String preprocessText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        text = text.replaceAll("\\s+", " ").trim();
        text = text.replace("\u00A0", " ");
        text = text.replace("\u2009", " ");
        text = text.replace("\u200B", "");

        return text;
    }

    public String getTokenizerInfo() {
        return "LocalTokenizer{model=%s, vocab_size=%d}".formatted(modelId, getVocabSize());
    }

    public String getModelId() {
        return modelId;
    }

    public void close() {
        try {
            tokenizer.close();
        } catch (Exception e) {
        }
    }
}
