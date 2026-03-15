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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class FallbackTokenizer implements Tokenizer {
    private final Tokenizer primary;
    private final EstimationTokenizer fallback;
    private final AtomicBoolean primaryFailed;
    private final boolean silentFallback;

    public FallbackTokenizer(Tokenizer primary) {
        this(primary, true);
    }

    public FallbackTokenizer(Tokenizer primary, boolean silentFallback) {
        this.primary = primary;
        this.fallback = new EstimationTokenizer();
        this.primaryFailed = new AtomicBoolean(false);
        this.silentFallback = silentFallback;
    }

    @Override
    public List<Integer> encode(String text) {
        if (primaryFailed.get()) {
            return fallback.encode(text);
        }

        try {
            return primary.encode(text);
        } catch (Exception e) {
            return handleFallback("encode", text, e, () -> fallback.encode(text));
        }
    }

    @Override
    public String decode(List<Integer> tokenIds) {
        if (primaryFailed.get()) {
            return fallback.decode(tokenIds);
        }

        try {
            return primary.decode(tokenIds);
        } catch (Exception e) {
            return handleFallback("decode", tokenIds.toString(), e, () -> fallback.decode(tokenIds));
        }
    }

    @Override
    public List<String> tokenize(String text) {
        if (primaryFailed.get()) {
            return fallback.tokenize(text);
        }

        try {
            return primary.tokenize(text);
        } catch (Exception e) {
            return handleFallback("tokenize", text, e, () -> fallback.tokenize(text));
        }
    }

    @Override
    public int countTokens(String text) {
        if (primaryFailed.get()) {
            return fallback.countTokens(text);
        }

        try {
            return primary.countTokens(text);
        } catch (Exception e) {
            return handleFallback("countTokens", text, e, () -> fallback.countTokens(text));
        }
    }

    @Override
    public int estimateTokens(String text) {
        return fallback.estimateTokens(text);
    }

    private <T> T handleFallback(String operation, String input, Exception e,
                                  java.util.function.Supplier<T> fallbackOp) {
        if (!primaryFailed.getAndSet(true) && !silentFallback) {
            System.err.println("[WARN] Tokenizador primario falló en " + operation +
                             ". Usando estimación. Error: " + e.getMessage());
        }

        return fallbackOp.get();
    }

    public void resetPrimaryState() {
        primaryFailed.set(false);
    }

    public boolean isPrimaryActive() {
        return !primaryFailed.get();
    }

    public String getStatus() {
        return "FallbackTokenizer{primary=%s, usingFallback=%s}"
            .formatted(primary.getClass().getSimpleName(), primaryFailed.get());
    }
}
