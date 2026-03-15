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
package dev.fararoni.core.core.brain;

import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class TokenMonitor {
    private final int maxContextWindow;

    private static final double CHARS_PER_TOKEN = 4.0;

    private static final double CONSERVATIVE_CHARS_PER_TOKEN = 2.5;

    private static final double SAFETY_MARGIN = 0.9;

    public TokenMonitor() {
        this(30000);
    }

    public TokenMonitor(int maxContextWindow) {
        if (maxContextWindow < 1000) {
            throw new IllegalArgumentException(
                "maxContextWindow debe ser al menos 1000 tokens, recibido: " + maxContextWindow
            );
        }
        this.maxContextWindow = maxContextWindow;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    public boolean isSafe(String prompt) {
        int tokens = estimateTokens(prompt);
        int safeLimit = (int) (maxContextWindow * SAFETY_MARGIN);
        return tokens < safeLimit;
    }

    public String truncateSmart(String text, int maxTokens) {
        Objects.requireNonNull(text, "text no puede ser null");

        int currentTokens = estimateTokens(text);
        if (currentTokens <= maxTokens) {
            return text;
        }

        int maxChars = (int) (maxTokens * CHARS_PER_TOKEN);

        int keepChars = maxChars / 2;

        int startEnd = Math.min(keepChars, text.length());
        int endStart = Math.max(text.length() - keepChars, startEnd);

        String start = text.substring(0, startEnd);
        String end = text.substring(endStart);

        return start +
               "\n\n... [SECCION PODADA POR LIMITE DE MEMORIA - " +
               (currentTokens - maxTokens) + " tokens removidos] ...\n\n" +
               end;
    }

    public int getRemainingBudget(String currentPrompt) {
        int used = estimateTokens(currentPrompt);
        int safeLimit = (int) (maxContextWindow * SAFETY_MARGIN);
        return safeLimit - used;
    }

    public boolean safeGuard(int apiLimit, String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        int conservativeEstimate = (int) Math.ceil(text.length() / CONSERVATIVE_CHARS_PER_TOKEN);
        return conservativeEstimate < apiLimit;
    }

    public int estimateTokensConservative(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CONSERVATIVE_CHARS_PER_TOKEN);
    }

    public int getMaxContextWindow() {
        return maxContextWindow;
    }

    public String getBudgetReport(String prompt) {
        int used = estimateTokens(prompt);
        int safeLimit = (int) (maxContextWindow * SAFETY_MARGIN);
        double usage = (double) used / safeLimit * 100;

        return String.format(
            "TokenMonitor Budget Report:\n" +
            "  - Tokens usados: %,d\n" +
            "  - Limite seguro: %,d\n" +
            "  - Uso: %.1f%%\n" +
            "  - Estado: %s",
            used,
            safeLimit,
            usage,
            usage < 80 ? "OK" : (usage < 100 ? "ADVERTENCIA" : "EXCEDIDO")
        );
    }
}
