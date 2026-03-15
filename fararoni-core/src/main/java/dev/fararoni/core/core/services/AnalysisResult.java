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
package dev.fararoni.core.core.services;

import dev.fararoni.core.core.indexing.SentinelJavaParser;
import dev.fararoni.core.core.indexing.model.LineRange;

import java.util.Map;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record AnalysisResult(
    Map<String, LineRange> methodRanges,
    boolean success,
    Confidence confidence,
    String errorMessage
) {
    public enum Confidence {
        HIGH,

        LOW,

        NONE
    }

    public static AnalysisResult success(Map<String, LineRange> methodRanges) {
        return new AnalysisResult(methodRanges, true, Confidence.HIGH, null);
    }

    public static AnalysisResult fallback(Map<String, LineRange> methodRanges) {
        return new AnalysisResult(
            methodRanges,
            true,
            Confidence.LOW,
            "Heuristic Fallback: AST parsing failed, using Regex approximation"
        );
    }

    public static AnalysisResult failure(String errorMessage) {
        return new AnalysisResult(Map.of(), false, Confidence.NONE, errorMessage);
    }

    public boolean isHighConfidence() {
        return confidence == Confidence.HIGH;
    }

    public boolean isLowConfidence() {
        return confidence == Confidence.LOW;
    }

    public boolean hasMethodRanges() {
        return methodRanges != null && !methodRanges.isEmpty();
    }

    public int methodCount() {
        return methodRanges != null ? methodRanges.size() : 0;
    }
}
