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
package dev.fararoni.core.core.reflexion.testoutput;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public final class FailurePatternMatcher {
    private static final double PRECISION_THRESHOLD = 0.01;

    private static final int TYPO_THRESHOLD = 1;

    private static final int MISMATCH_THRESHOLD = 5;

    public static FailurePattern match(TestFailure failure) {
        Objects.requireNonNull(failure, "failure no puede ser null");

        FailurePattern byErrorType = matchByErrorType(failure);
        if (byErrorType != FailurePattern.UNKNOWN) {
            return byErrorType;
        }

        if (failure.hasComparison()) {
            FailurePattern byComparison = matchByComparison(failure);
            if (byComparison != FailurePattern.UNKNOWN) {
                return byComparison;
            }
        }

        FailurePattern byMessage = matchByMessage(failure);
        if (byMessage != FailurePattern.UNKNOWN) {
            return byMessage;
        }

        return FailurePattern.UNKNOWN;
    }

    public static List<FailurePattern> matchAll(TestFailure failure) {
        Objects.requireNonNull(failure, "failure no puede ser null");

        java.util.List<FailurePattern> patterns = new java.util.ArrayList<>();

        FailurePattern byError = matchByErrorType(failure);
        if (byError != FailurePattern.UNKNOWN) {
            patterns.add(byError);
        }

        if (failure.hasComparison()) {
            FailurePattern byComparison = matchByComparison(failure);
            if (byComparison != FailurePattern.UNKNOWN && !patterns.contains(byComparison)) {
                patterns.add(byComparison);
            }
        }

        FailurePattern byMessage = matchByMessage(failure);
        if (byMessage != FailurePattern.UNKNOWN && !patterns.contains(byMessage)) {
            patterns.add(byMessage);
        }

        if (patterns.isEmpty()) {
            patterns.add(FailurePattern.UNKNOWN);
        }

        return patterns;
    }

    public static boolean matches(TestFailure failure, FailurePattern pattern) {
        return match(failure) == pattern || matchAll(failure).contains(pattern);
    }

    private static FailurePattern matchByErrorType(TestFailure failure) {
        String errorType = failure.errorType().toLowerCase();

        if (errorType.contains("type")) {
            return FailurePattern.TYPE_MISMATCH;
        }

        if (errorType.contains("index")) {
            return FailurePattern.INDEX_ERROR;
        }

        if (errorType.contains("key")) {
            return FailurePattern.KEY_ERROR;
        }

        if (errorType.contains("attribute")) {
            return FailurePattern.ATTRIBUTE_ERROR;
        }

        if (errorType.contains("zerodivision")) {
            return FailurePattern.UNHANDLED_EXCEPTION;
        }

        if (errorType.contains("value") || errorType.contains("runtime")) {
            return FailurePattern.UNKNOWN;
        }

        return FailurePattern.UNKNOWN;
    }

    private static FailurePattern matchByComparison(TestFailure failure) {
        String expected = failure.expected();
        String actual = failure.actual();

        if (failure.isEmptyActual()) {
            return FailurePattern.EMPTY_RESULT;
        }

        if (isEmptyValue(expected) && !isEmptyValue(actual)) {
            return FailurePattern.EXPECTED_EMPTY;
        }

        if (isLogicInversion(expected, actual)) {
            return FailurePattern.LOGIC_INVERSION;
        }

        if (failure.isNumericComparison()) {
            return matchNumericComparison(failure);
        }

        if (failure.isStringComparison()) {
            return matchStringComparison(failure);
        }

        return FailurePattern.UNKNOWN;
    }

    private static FailurePattern matchNumericComparison(TestFailure failure) {
        Optional<Double> diff = failure.numericDifference();

        if (diff.isEmpty()) {
            return FailurePattern.UNKNOWN;
        }

        double absDiff = Math.abs(diff.get());

        if (absDiff == 1.0) {
            return FailurePattern.OFF_BY_ONE;
        }

        if (absDiff > 0 && absDiff < PRECISION_THRESHOLD) {
            return FailurePattern.PRECISION_ERROR;
        }

        try {
            int expInt = Integer.parseInt(failure.expected());
            int actInt = Integer.parseInt(failure.actual());

            if ((expInt == 0 && actInt == 1) || (expInt == 1 && actInt == 0)) {
                return FailurePattern.LOGIC_INVERSION;
            }
        } catch (NumberFormatException ignored) {
        }

        return FailurePattern.UNKNOWN;
    }

    private static FailurePattern matchStringComparison(TestFailure failure) {
        int editDistance = failure.stringEditDistance();

        if (editDistance <= TYPO_THRESHOLD) {
            return FailurePattern.STRING_TYPO;
        }

        if (isSameCharactersDifferentOrder(failure.expected(), failure.actual())) {
            return FailurePattern.ORDER_MISMATCH;
        }

        return FailurePattern.STRING_MISMATCH;
    }

    private static FailurePattern matchByMessage(TestFailure failure) {
        String message = failure.fullMessage().toLowerCase();

        if (message.contains("index out of") || message.contains("out of range")) {
            return FailurePattern.INDEX_ERROR;
        }

        if (message.contains("key not found") || message.contains("keyerror")) {
            return FailurePattern.KEY_ERROR;
        }

        if (message.contains("none") || message.contains("null") || message.contains("undefined")) {
            return FailurePattern.EMPTY_RESULT;
        }

        if (message.contains("type") && (message.contains("expected") || message.contains("got"))) {
            return FailurePattern.TYPE_MISMATCH;
        }

        return FailurePattern.UNKNOWN;
    }

    private static boolean isEmptyValue(String value) {
        if (value == null || value.isEmpty()) return true;
        String normalized = value.toLowerCase().trim();
        return normalized.equals("none")
            || normalized.equals("null")
            || normalized.equals("[]")
            || normalized.equals("{}")
            || normalized.equals("''")
            || normalized.equals("\"\"");
    }

    private static boolean isLogicInversion(String expected, String actual) {
        String exp = expected.toLowerCase().trim();
        String act = actual.toLowerCase().trim();

        if ((exp.equals("true") && act.equals("false")) ||
            (exp.equals("false") && act.equals("true"))) {
            return true;
        }

        if ((exp.equals("0") && act.equals("1")) ||
            (exp.equals("1") && act.equals("0"))) {
            return true;
        }

        return false;
    }

    private static boolean isSameCharactersDifferentOrder(String s1, String s2) {
        if (s1 == null || s2 == null) return false;
        if (s1.length() != s2.length()) return false;

        char[] chars1 = s1.toCharArray();
        char[] chars2 = s2.toCharArray();
        java.util.Arrays.sort(chars1);
        java.util.Arrays.sort(chars2);

        return java.util.Arrays.equals(chars1, chars2);
    }

    private FailurePatternMatcher() {
        throw new AssertionError("No instanciable");
    }
}
