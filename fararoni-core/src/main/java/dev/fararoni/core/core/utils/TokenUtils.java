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

import dev.fararoni.core.core.config.AgentConfig;

import java.time.Duration;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class TokenUtils {
    private static final double CHARS_PER_TOKEN = 4.0;

    public static int getRabbitMaxChars() {
        return AgentConfig.load().rabbitMaxChars();
    }

    public static int getRabbitMaxTokens() {
        return AgentConfig.load().rabbitMaxTokens();
    }

    @Deprecated
    public static final int RABBIT_MAX_CHARS = 12000;

    @Deprecated
    public static final int RABBIT_MAX_TOKENS = 3000;

    private static final String UP_ARROW = "↑";

    private static final String DOWN_ARROW = "↓";

    private static final String SEPARATOR = " · ";

    private TokenUtils() {
        throw new AssertionError("TokenUtils es una clase de utilidades estaticas");
    }

    public static long calculateTokens(long charCount) {
        if (charCount <= 0) {
            return 0;
        }
        return (long) Math.ceil(charCount / CHARS_PER_TOKEN);
    }

    public static String formatTokenCount(long tokenCount) {
        if (tokenCount < 0) {
            return "0";
        }
        if (tokenCount < 1000) {
            return String.valueOf(tokenCount);
        }
        return String.format("%.1fk", tokenCount / 1000.0);
    }

    public static String formatMeta(long durationMs, long inputChars, long outputChars) {
        String timeStr = UxToolkit.formatDuration(Duration.ofMillis(Math.max(0, durationMs)));

        long inputTokens = calculateTokens(inputChars);
        long outputTokens = calculateTokens(outputChars);

        String inStr = formatTokenCount(inputTokens);
        String outStr = formatTokenCount(outputTokens);

        return timeStr + SEPARATOR + UP_ARROW + " " + inStr + SEPARATOR + DOWN_ARROW + " " + outStr;
    }

    public static String formatMetaInputOnly(long durationMs, long inputChars) {
        String timeStr = UxToolkit.formatDuration(Duration.ofMillis(Math.max(0, durationMs)));

        long inputTokens = calculateTokens(inputChars);
        String inStr = formatTokenCount(inputTokens);

        return timeStr + SEPARATOR + UP_ARROW + " " + inStr;
    }

    public static double estimateCost(long inputTokens, long outputTokens,
                                       double inputPricePerMillion, double outputPricePerMillion) {
        double inputCost = (inputTokens * inputPricePerMillion) / 1_000_000.0;
        double outputCost = (outputTokens * outputPricePerMillion) / 1_000_000.0;
        return inputCost + outputCost;
    }

    public static String formatCost(double costUsd) {
        if (costUsd <= 0) {
            return "$0.00";
        }
        if (costUsd < 0.01) {
            return "<$0.01";
        }
        return String.format("$%.2f", costUsd);
    }
}
