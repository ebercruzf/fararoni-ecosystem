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
package dev.fararoni.core.core.skills;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class BuildOutputDistiller {
    private static final int MAX_DISTILLED_LENGTH = 3000;
    private static final int THRESHOLD_TO_DISTILL = 5000;
    private static final int TAIL_LINES = 20;

    private BuildOutputDistiller() {}

    public static String distill(String rawOutput) {
        if (rawOutput == null || rawOutput.length() < THRESHOLD_TO_DISTILL) {
            return rawOutput;
        }

        String[] lines = rawOutput.split("\n");

        String errorLines = Arrays.stream(lines)
            .filter(BuildOutputDistiller::isErrorLine)
            .collect(Collectors.joining("\n"));

        String tailLines = Arrays.stream(lines)
            .skip(Math.max(0, lines.length - TAIL_LINES))
            .collect(Collectors.joining("\n"));

        String combined;
        if (errorLines.isEmpty()) {
            combined = tailLines;
        } else {
            combined = errorLines + "\n\n--- BUILD SUMMARY ---\n" + tailLines;
        }

        if (combined.length() > MAX_DISTILLED_LENGTH) {
            combined = "... [ERRORES ANTERIORES OMITIDOS] ...\n"
                + combined.substring(combined.length() - MAX_DISTILLED_LENGTH);
        }

        return """
            [DISTILLED BUILD OUTPUT]
            Original: %d chars → Distilled: %d chars
            Status: BUILD FAILURE detected

            --- ERRORS ---
            %s
            --- END ---""".formatted(rawOutput.length(), combined.length(), combined);
    }

    private static boolean isErrorLine(String line) {
        if (line == null) return false;
        return line.contains("[ERROR]")
            || line.contains("BUILD FAILURE")
            || line.contains("COMPILATION ERROR")
            || line.contains("<<< FAILURE!")
            || line.contains("cannot find symbol")
            || line.contains("package does not exist")
            || line.contains("incompatible types")
            || line.contains("error:")
            || line.contains("FAILED")
            || line.matches(".*\\d+ error.*")
            || line.contains("Caused by:")
            || line.contains("APPLICATION FAILED TO START")
            || line.contains("Error creating bean")
            || line.contains("Unsatisfied dependency")
            || line.contains("BeanCreationException")
            || line.contains("NoSuchBeanDefinitionException")
            || line.contains("ClassNotFoundException")
            || line.contains("NoClassDefFoundError")
            || line.matches("^\\s+at .*\\([^)]*\\.java:\\d+\\).*");
    }
}
