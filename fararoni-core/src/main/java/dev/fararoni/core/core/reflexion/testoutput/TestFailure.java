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

import java.util.Objects;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public record TestFailure(
    String testName,
    String errorType,
    String expected,
    String actual,
    Integer lineNumber,
    String fullMessage,
    String fileName
) {
    public TestFailure {
        Objects.requireNonNull(testName, "testName es obligatorio para identificar el test fallido");
        if (testName.isBlank()) {
            throw new IllegalArgumentException("testName no puede estar vacio");
        }
        errorType = errorType != null ? errorType : "UnknownError";
        expected = expected != null ? expected : "";
        actual = actual != null ? actual : "";
        fullMessage = fullMessage != null ? fullMessage : "";
        fileName = fileName != null ? fileName : "";
    }

    public static TestFailure ofName(String testName) {
        return new TestFailure(testName, null, null, null, null, null, null);
    }

    public static TestFailure of(String testName, String errorType, String message) {
        return new TestFailure(testName, errorType, null, null, null, message, null);
    }

    public static TestFailure ofComparison(String testName, String errorType,
                                           String expected, String actual) {
        return new TestFailure(testName, errorType, expected, actual, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasComparison() {
        return !expected.isEmpty() && !actual.isEmpty();
    }

    public boolean isNumericComparison() {
        if (!hasComparison()) return false;
        return isNumeric(expected) && isNumeric(actual);
    }

    public boolean isStringComparison() {
        return hasComparison() && !isNumericComparison();
    }

    public Optional<Double> numericDifference() {
        if (!isNumericComparison()) return Optional.empty();
        try {
            double exp = Double.parseDouble(expected);
            double act = Double.parseDouble(actual);
            return Optional.of(exp - act);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public boolean isOffByOne() {
        return numericDifference()
            .map(diff -> Math.abs(diff) == 1.0)
            .orElse(false);
    }

    public int stringEditDistance() {
        if (!isStringComparison()) return -1;
        return levenshteinDistance(expected, actual);
    }

    public boolean isSingleCharDifference() {
        return stringEditDistance() == 1;
    }

    public boolean isEmptyActual() {
        if (actual.isEmpty()) return true;
        String normalized = actual.toLowerCase().trim();
        return normalized.equals("none")
            || normalized.equals("null")
            || normalized.equals("[]")
            || normalized.equals("{}")
            || normalized.equals("''")
            || normalized.equals("\"\"");
    }

    public boolean isTypeError() {
        return errorType.toLowerCase().contains("type");
    }

    public boolean isAssertionError() {
        return errorType.toLowerCase().contains("assertion");
    }

    public Optional<Integer> getLineNumber() {
        return Optional.ofNullable(lineNumber);
    }

    public Optional<String> getFileName() {
        return fileName.isEmpty() ? Optional.empty() : Optional.of(fileName);
    }

    public String toShortSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(testName).append(": ").append(errorType);
        if (hasComparison()) {
            sb.append(" (esperado: ").append(expected)
              .append(", recibido: ").append(actual).append(")");
        }
        return sb.toString();
    }

    public String toDetailedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(testName).append("\n");
        sb.append("- **Error:** ").append(errorType).append("\n");

        if (hasComparison()) {
            sb.append("- **Esperado:** `").append(expected).append("`\n");
            sb.append("- **Recibido:** `").append(actual).append("`\n");

            if (isOffByOne()) {
                sb.append("- **Patron:** OFF_BY_ONE (diferencia de 1)\n");
            } else if (isSingleCharDifference()) {
                sb.append("- **Patron:** STRING_TYPO (1 caracter diferente)\n");
            } else if (isEmptyActual()) {
                sb.append("- **Patron:** EMPTY_RESULT (resultado vacio)\n");
            }
        }

        getLineNumber().ifPresent(line ->
            sb.append("- **Linea:** ").append(line).append("\n"));

        if (!fullMessage.isEmpty()) {
            sb.append("- **Mensaje:** ").append(fullMessage).append("\n");
        }

        return sb.toString();
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                    );
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    public static final class Builder {
        private String testName;
        private String errorType;
        private String expected;
        private String actual;
        private Integer lineNumber;
        private String fullMessage;
        private String fileName;

        private Builder() {}

        public Builder testName(String testName) {
            this.testName = testName;
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder expected(String expected) {
            this.expected = expected;
            return this;
        }

        public Builder actual(String actual) {
            this.actual = actual;
            return this;
        }

        public Builder lineNumber(Integer lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder fullMessage(String fullMessage) {
            this.fullMessage = fullMessage;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public TestFailure build() {
            return new TestFailure(testName, errorType, expected, actual,
                                   lineNumber, fullMessage, fileName);
        }
    }
}
