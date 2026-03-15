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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public final class PytestOutputParser {
    private static final Pattern FAILED_LINE_PATTERN = Pattern.compile(
        "FAILED[ \\t]+([\\w:./_]+)[ \\t]*(?:-|\\[)[ \\t]*(\\w+(?:Error|Exception)?):?[ \\t]*([^\\r\\n]*)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TEST_PATH_FAILED_PATTERN = Pattern.compile(
        "([^\\s]+::[^\\s]+)\\s+FAILED",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ASSERT_EQUALS_PATTERN = Pattern.compile(
        "E\\s+assert\\s+(.+)\\s*==\\s*(.+)$"
    );

    private static final Pattern ASSERT_NOT_EQUALS_PATTERN = Pattern.compile(
        "E\\s+assert\\s+(.+)\\s*!=\\s*(.+)$"
    );

    private static final Pattern ASSERTION_ERROR_PATTERN = Pattern.compile(
        "E\\s+AssertionError:\\s*assert\\s+(.+)\\s*==\\s*(.+)$"
    );

    private static final Pattern ASSERTION_ERROR_MSG_PATTERN = Pattern.compile(
        "E\\s+AssertionError:\\s*(.+)$"
    );

    private static final Pattern TYPE_ERROR_PATTERN = Pattern.compile(
        "E\\s+(\\w+Error):\\s*(.+)$"
    );

    private static final Pattern CODE_LINE_PATTERN = Pattern.compile(
        ">\\s+assert\\s+(.+?)\\s*==\\s*(.+?)\\s*$"
    );

    private static final Pattern LOCATION_PATTERN = Pattern.compile(
        "([\\w_/]+\\.py):(\\d+):\\s*(?:in\\s+)?(\\w+)"
    );

    private static final Pattern QUOTED_VALUE_PATTERN = Pattern.compile(
        "['\"](.+?)['\"]"
    );

    public List<TestFailure> parse(String output) {
        Objects.requireNonNull(output, "output no puede ser null");

        if (output.isBlank()) {
            return List.of();
        }

        List<TestFailure> failures = new ArrayList<>();
        String[] lines = output.split("\\r?\\n");

        TestFailure.Builder currentBuilder = null;
        String currentTestName = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            Optional<TestFailure.Builder> newTest = tryParseFailedLine(line);
            if (newTest.isPresent()) {
                if (currentBuilder != null) {
                    failures.add(currentBuilder.build());
                }
                currentBuilder = newTest.get();
                currentTestName = currentBuilder.build().testName();
                continue;
            }

            if (currentBuilder != null) {
                Optional<String[]> assertResult = tryParseAssertEquals(line);
                if (assertResult.isPresent()) {
                    String[] pair = assertResult.get();
                    currentBuilder.expected(pair[0]);
                    currentBuilder.actual(pair[1]);
                }

                Optional<String[]> errorResult = tryParseErrorType(line);
                if (errorResult.isPresent()) {
                    String[] errorInfo = errorResult.get();
                    currentBuilder.errorType(errorInfo[0]);
                    if (errorInfo.length > 1 && !errorInfo[1].isEmpty()) {
                        currentBuilder.fullMessage(errorInfo[1]);
                    }
                }

                Optional<String[]> locResult = tryParseLocation(line);
                if (locResult.isPresent()) {
                    String[] loc = locResult.get();
                    currentBuilder.fileName(loc[0]);
                    try {
                        currentBuilder.lineNumber(Integer.parseInt(loc[1]));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (currentBuilder != null) {
            failures.add(currentBuilder.build());
        }

        return failures;
    }

    public Optional<TestFailure> parseSingleLine(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        return tryParseFailedLine(line)
            .map(builder -> {
                tryParseAssertEquals(line).ifPresent(pair -> {
                    builder.expected(pair[0]);
                    builder.actual(pair[1]);
                });
                return builder.build();
            });
    }

    public boolean hasFailures(String output) {
        if (output == null || output.isBlank()) {
            return false;
        }
        String lower = output.toLowerCase();
        return lower.contains("failed") || lower.contains("error");
    }

    public int countFailures(String output) {
        if (output == null || output.isBlank()) {
            return 0;
        }

        int count = 0;
        Matcher matcher = FAILED_LINE_PATTERN.matcher(output);
        while (matcher.find()) {
            count++;
        }

        matcher = TEST_PATH_FAILED_PATTERN.matcher(output);
        while (matcher.find()) {
            count++;
        }

        return count;
    }

    public List<String> extractFailedTestNames(String output) {
        List<String> names = new ArrayList<>();

        if (output == null || output.isBlank()) {
            return names;
        }

        Matcher matcher = FAILED_LINE_PATTERN.matcher(output);
        while (matcher.find()) {
            String name = cleanTestName(matcher.group(1));
            if (!names.contains(name)) {
                names.add(name);
            }
        }

        matcher = TEST_PATH_FAILED_PATTERN.matcher(output);
        while (matcher.find()) {
            String fullNodeId = matcher.group(1);
            String[] parts = fullNodeId.split("::");
            if (parts.length >= 2) {
                String testName = parts[parts.length - 1];
                if (!names.contains(testName)) {
                    names.add(testName);
                }
            }
        }

        return names;
    }

    private Optional<TestFailure.Builder> tryParseFailedLine(String line) {
        Matcher matcher = FAILED_LINE_PATTERN.matcher(line);
        if (matcher.find()) {
            TestFailure.Builder builder = TestFailure.builder()
                .testName(cleanTestName(matcher.group(1)))
                .errorType(matcher.group(2))
                .fullMessage(matcher.group(3));

            String message = matcher.group(3);
            tryExtractComparison(message).ifPresent(pair -> {
                builder.expected(pair[0]);
                builder.actual(pair[1]);
            });

            return Optional.of(builder);
        }

        matcher = TEST_PATH_FAILED_PATTERN.matcher(line);
        if (matcher.find()) {
            String fullNodeId = matcher.group(1);

            String[] parts = fullNodeId.split("::");

            if (parts.length >= 2) {
                String fileName = parts[0];
                String testName = parts[parts.length - 1];

                TestFailure.Builder builder = TestFailure.builder().testName(testName);

                if (fileName.contains(".") || fileName.endsWith(".py")) {
                    builder.fileName(fileName);
                }

                return Optional.of(builder);
            }
        }

        return Optional.empty();
    }

    private Optional<String[]> tryParseAssertEquals(String line) {
        Matcher matcher = ASSERT_EQUALS_PATTERN.matcher(line);
        if (matcher.find()) {
            return Optional.of(new String[]{
                cleanValue(matcher.group(1)),
                cleanValue(matcher.group(2))
            });
        }

        matcher = ASSERTION_ERROR_PATTERN.matcher(line);
        if (matcher.find()) {
            return Optional.of(new String[]{
                cleanValue(matcher.group(1)),
                cleanValue(matcher.group(2))
            });
        }

        matcher = CODE_LINE_PATTERN.matcher(line);
        if (matcher.find()) {
            return Optional.of(new String[]{
                cleanValue(matcher.group(2)),
                cleanValue(matcher.group(1))
            });
        }

        return Optional.empty();
    }

    private Optional<String[]> tryExtractComparison(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        Pattern inlineAssert = Pattern.compile("assert\\s+(.+)\\s*==\\s*(.+)$");
        Matcher matcher = inlineAssert.matcher(message);
        if (matcher.find()) {
            return Optional.of(new String[]{
                cleanValue(matcher.group(1)),
                cleanValue(matcher.group(2))
            });
        }

        return Optional.empty();
    }

    private Optional<String[]> tryParseErrorType(String line) {
        Matcher matcher = TYPE_ERROR_PATTERN.matcher(line);
        if (matcher.find()) {
            return Optional.of(new String[]{
                matcher.group(1),
                matcher.group(2)
            });
        }

        matcher = ASSERTION_ERROR_MSG_PATTERN.matcher(line);
        if (matcher.find()) {
            return Optional.of(new String[]{
                "AssertionError",
                matcher.group(1)
            });
        }

        return Optional.empty();
    }

    private Optional<String[]> tryParseLocation(String line) {
        Matcher matcher = LOCATION_PATTERN.matcher(line);
        if (matcher.find()) {
            return Optional.of(new String[]{
                matcher.group(1),
                matcher.group(2),
                matcher.group(3)
            });
        }
        return Optional.empty();
    }

    private String cleanTestName(String name) {
        if (name == null) return "unknown";

        name = name.replaceAll("^\\.+/+", "");

        name = name.replaceAll("^dev::", "");

        if (name.contains("::")) {
            String[] parts = name.split("::");
            name = parts[parts.length - 1];
        }

        return name.trim();
    }

    private String cleanValue(String value) {
        if (value == null) return "";

        value = value.trim();

        if ((value.startsWith("'") && value.endsWith("'")) ||
            (value.startsWith("\"") && value.endsWith("\""))) {
            value = value.substring(1, value.length() - 1);
        }

        return value;
    }
}
