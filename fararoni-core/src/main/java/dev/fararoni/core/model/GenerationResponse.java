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
package dev.fararoni.core.model;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record GenerationResponse(
    String text,
    List<Integer> tokenIds,
    Usage usage,
    long latencyMs,
    String finishReason,
    boolean wasTruncated,
    String continuationId
) {
    public GenerationResponse {
        if (latencyMs < 0) {
            throw new IllegalArgumentException("La latencia no puede ser negativa");
        }

        tokenIds = tokenIds != null ? List.copyOf(tokenIds) : null;

        if ((text == null || text.isEmpty()) && (tokenIds == null || tokenIds.isEmpty())) {
            throw new IllegalArgumentException("La respuesta debe tener texto o token IDs");
        }
    }

    public record Usage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        double tokensPerSecond
    ) {
        public Usage {
            if (promptTokens < 0) {
                throw new IllegalArgumentException("promptTokens no puede ser negativo");
            }
            if (completionTokens < 0) {
                throw new IllegalArgumentException("completionTokens no puede ser negativo");
            }
            if (totalTokens < 0) {
                throw new IllegalArgumentException("totalTokens no puede ser negativo");
            }
            if (tokensPerSecond < 0) {
                throw new IllegalArgumentException("tokensPerSecond no puede ser negativo");
            }

            if (totalTokens != promptTokens + completionTokens && totalTokens > 0) {
                throw new IllegalArgumentException(
                    "totalTokens debe ser la suma de promptTokens + completionTokens. " +
                    "Esperado: " + (promptTokens + completionTokens) + ", Recibido: " + totalTokens
                );
            }
        }

        public static Usage of(int promptTokens, int completionTokens) {
            int total = promptTokens + completionTokens;
            return new Usage(promptTokens, completionTokens, total, 0.0);
        }

        public static Usage of(int promptTokens, int completionTokens, long latencyMs) {
            int total = promptTokens + completionTokens;
            double tokensPerSecond = latencyMs > 0 ? (completionTokens * 1000.0) / latencyMs : 0.0;
            return new Usage(promptTokens, completionTokens, total, tokensPerSecond);
        }

        public boolean isWithinLimits(int maxTokens) {
            return totalTokens <= maxTokens;
        }

        public double getUsagePercentage(int maxTokens) {
            return maxTokens > 0 ? (totalTokens * 100.0) / maxTokens : 0.0;
        }
    }

    public static GenerationResponse success(String text, Usage usage, long latencyMs) {
        return new GenerationResponse(text, null, usage, latencyMs, "stop", false, null);
    }

    public static GenerationResponse successWithTokens(String text, List<Integer> tokenIds,
                                                      Usage usage, long latencyMs) {
        return new GenerationResponse(text, tokenIds, usage, latencyMs, "stop", false, null);
    }

    public static GenerationResponse truncated(String text, Usage usage, long latencyMs) {
        return new GenerationResponse(text, null, usage, latencyMs, "length", true, null);
    }

    public static GenerationResponse continuation(String text, Usage usage, long latencyMs, String continuationId) {
        return new GenerationResponse(text, null, usage, latencyMs, "length", true, continuationId);
    }

    public static GenerationResponse error(String errorText) {
        return new GenerationResponse(
            errorText,
            null,
            Usage.of(0, 0),
            0L,
            "error",
            false,
            null
        );
    }

    public boolean isSuccessful() {
        return "stop".equals(finishReason) || "length".equals(finishReason);
    }

    public boolean isError() {
        return "error".equals(finishReason);
    }

    public boolean hasTokenIds() {
        return tokenIds != null && !tokenIds.isEmpty();
    }

    public boolean needsContinuation() {
        return wasTruncated && continuationId != null;
    }

    public int getTextLength() {
        return text != null ? text.length() : 0;
    }

    public int getTokenCount() {
        return hasTokenIds() ? tokenIds.size() : (usage != null ? usage.completionTokens() : 0);
    }

    public ResponseQuality getQuality() {
        if (isError()) {
            return ResponseQuality.ERROR;
        }

        if (wasTruncated) {
            return ResponseQuality.TRUNCATED;
        }

        if (usage != null && usage.tokensPerSecond() > 50) {
            return ResponseQuality.EXCELLENT;
        }

        if (usage != null && usage.tokensPerSecond() > 20) {
            return ResponseQuality.GOOD;
        }

        return ResponseQuality.AVERAGE;
    }

    public enum ResponseQuality {
        EXCELLENT, GOOD, AVERAGE, TRUNCATED, ERROR
    }

    public GenerationResponse appendText(String additionalText) {
        var newText = (text != null ? text : "") + additionalText;
        return new GenerationResponse(
            newText, tokenIds, usage, latencyMs, finishReason, wasTruncated, continuationId
        );
    }

    public String getSummary() {
        var sb = new StringBuilder();
        sb.append("Texto: ").append(getTextLength()).append(" chars");

        if (usage != null) {
            sb.append(", Tokens: ").append(usage.totalTokens());
            if (usage.tokensPerSecond() > 0) {
                sb.append(", Velocidad: ").append(String.format("%.1f", usage.tokensPerSecond())).append(" tok/s");
            }
        }

        sb.append(", Latencia: ").append(latencyMs).append("ms");

        if (wasTruncated) {
            sb.append(" [TRUNCADO]");
        }

        return sb.toString();
    }
}
