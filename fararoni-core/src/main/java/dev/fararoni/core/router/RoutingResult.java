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
package dev.fararoni.core.router;

import java.util.Map;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record RoutingResult(
    Tool tool,
    String action,
    Map<String, Object> parameters,
    double confidence,
    boolean fromFallback,
    long latencyMs
) {
    public static final double CONFIDENCE_THRESHOLD_HIGH = 0.9;
    public static final double CONFIDENCE_THRESHOLD_MIN = 0.7;
    public static final double CONFIDENCE_THRESHOLD_CONFIRM = 0.5;

    public RoutingResult {
        Objects.requireNonNull(tool, "tool cannot be null");
        action = action != null ? action.trim() : "";
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();

        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                "confidence must be between 0.0 and 1.0, got: " + confidence);
        }

        if (latencyMs < 0) {
            throw new IllegalArgumentException(
                "latencyMs cannot be negative, got: " + latencyMs);
        }
    }

    public static RoutingResult of(Tool tool, String action, double confidence, long latencyMs) {
        return new RoutingResult(tool, action, Map.of(), confidence, false, latencyMs);
    }

    public static RoutingResult of(Tool tool, String action, Map<String, Object> parameters,
                                   double confidence, long latencyMs) {
        return new RoutingResult(tool, action, parameters, confidence, false, latencyMs);
    }

    public static RoutingResult fallback(Tool tool, long latencyMs) {
        return new RoutingResult(tool, "message", Map.of(), 0.6, true, latencyMs);
    }

    public static RoutingResult fallback(Tool tool, String action, long latencyMs) {
        return new RoutingResult(tool, action, Map.of(), 0.6, true, latencyMs);
    }

    public static RoutingResult chat(long latencyMs) {
        return new RoutingResult(Tool.CHAT, "message", Map.of(), 0.95, false, latencyMs);
    }

    public static RoutingResult unknown(long latencyMs) {
        return new RoutingResult(Tool.UNKNOWN, "", Map.of(), 0.0, true, latencyMs);
    }

    public boolean isConfident() {
        return confidence >= CONFIDENCE_THRESHOLD_MIN;
    }

    public boolean isHighConfidence() {
        return confidence >= CONFIDENCE_THRESHOLD_HIGH;
    }

    public boolean requiresConfirmation() {
        return confidence >= CONFIDENCE_THRESHOLD_CONFIRM && confidence < CONFIDENCE_THRESHOLD_MIN;
    }

    public boolean isLowConfidence() {
        return confidence < CONFIDENCE_THRESHOLD_CONFIRM;
    }

    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key) {
        return (T) parameters.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        Object value = parameters.get(key);
        return value != null ? (T) value : defaultValue;
    }

    public boolean hasParameter(String key) {
        return parameters.containsKey(key);
    }

    public boolean hasAction() {
        return !action.isEmpty();
    }

    public String toLogString() {
        return String.format(
            "{tool:%s, action:%s, confidence:%.2f, fallback:%s, latency:%dms}",
            tool.getId(), action, confidence, fromFallback, latencyMs
        );
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"tool\":\"").append(tool.getId()).append("\",");
        sb.append("\"action\":\"").append(action).append("\",");
        sb.append("\"confidence\":").append(String.format("%.2f", confidence));
        if (!parameters.isEmpty()) {
            sb.append(",\"params\":{");
            boolean first = true;
            for (var entry : parameters.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":\"")
                  .append(entry.getValue()).append("\"");
                first = false;
            }
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return toLogString();
    }
}
