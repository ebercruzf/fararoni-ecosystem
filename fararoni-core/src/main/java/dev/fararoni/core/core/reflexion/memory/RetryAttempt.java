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
package dev.fararoni.core.core.reflexion.memory;

import dev.fararoni.core.core.reflexion.testoutput.FailurePattern;
import dev.fararoni.core.core.reflexion.testoutput.TestFailure;
import dev.fararoni.core.core.reflexion.testoutput.FailurePatternMatcher;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public record RetryAttempt(
    int attemptNumber,
    int codeHash,
    String code,
    List<TestFailure> failures,
    Set<FailurePattern> patterns,
    Instant timestamp
) {
    private static final int MAX_CODE_LENGTH = 500;

    public RetryAttempt {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber debe ser >= 1");
        }
        failures = failures != null ? List.copyOf(failures) : List.of();
        patterns = patterns != null ? Set.copyOf(patterns) : Set.of();
        code = code != null ? truncate(code, MAX_CODE_LENGTH) : "";
        timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static RetryAttempt of(int attemptNumber, String code, List<TestFailure> failures) {
        Objects.requireNonNull(failures, "failures no puede ser null");

        Set<FailurePattern> patterns = failures.stream()
            .map(f -> FailurePatternMatcher.match(f))
            .collect(Collectors.toSet());

        return new RetryAttempt(
            attemptNumber,
            code != null ? code.hashCode() : 0,
            code,
            failures,
            patterns,
            Instant.now()
        );
    }

    public static RetryAttempt ofCode(int attemptNumber, String code) {
        return new RetryAttempt(
            attemptNumber,
            code != null ? code.hashCode() : 0,
            code,
            List.of(),
            Set.of(),
            Instant.now()
        );
    }

    public boolean hasPattern(FailurePattern pattern) {
        return patterns.contains(pattern);
    }

    public boolean hasHighSeverityPattern() {
        return patterns.stream().anyMatch(FailurePattern::isHighSeverity);
    }

    public boolean hasSameCode(RetryAttempt other) {
        return this.codeHash == other.codeHash;
    }

    public boolean hasSamePatterns(RetryAttempt other) {
        return this.patterns.equals(other.patterns);
    }

    public boolean sharesSameFailingTests(RetryAttempt other) {
        Set<String> myTestNames = failures.stream()
            .map(TestFailure::testName)
            .collect(Collectors.toSet());

        return other.failures.stream()
            .map(TestFailure::testName)
            .anyMatch(myTestNames::contains);
    }

    public Set<String> failingTestNames() {
        return failures.stream()
            .map(TestFailure::testName)
            .collect(Collectors.toSet());
    }

    public boolean isSuccessful() {
        return failures.isEmpty();
    }

    public int failureCount() {
        return failures.size();
    }

    public String toSummary() {
        if (isSuccessful()) {
            return String.format("Intento #%d: EXITOSO", attemptNumber);
        }

        String patternStr = patterns.stream()
            .map(Enum::name)
            .collect(Collectors.joining(", "));

        return String.format("Intento #%d: %d failures, patrones=[%s]",
            attemptNumber, failures.size(), patternStr);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
