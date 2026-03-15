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
package dev.fararoni.core.core.safety;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record ContentIntegrityPolicy(
    double maxDestructionRatio,
    double strictDestructionRatio,
    int tinyFileThreshold,
    int largeFileThreshold,
    List<Pattern> lazyPatterns
) {
    public static ContentIntegrityPolicy defaultPolicy() {
        return new ContentIntegrityPolicy(
            0.20,
            0.10,
            50,
            2000,
            defaultLazyPatterns()
        );
    }

    public static List<Pattern> defaultLazyPatterns() {
        return List.of(
            Pattern.compile("//\\s*\\.\\.\\."),
            Pattern.compile("//\\s*\\.\\.\\.[^\\n]*omitido"),

            Pattern.compile("//\\s*existentes?"),
            Pattern.compile("//\\s*[Aa]tributos?\\s+existentes?"),
            Pattern.compile("//\\s*[Cc]onstructor.*existentes?"),
            Pattern.compile("//\\s*[Mm]etodos?\\s+existentes?"),

            Pattern.compile("//\\s*[Gg]etters?.*[Ss]etters?"),
            Pattern.compile("//\\s*[Rr]esto\\s+del\\s+codigo"),
            Pattern.compile("//\\s*TODO:?\\s*[Aa]gregar"),
            Pattern.compile("//\\s*\\[\\s*\\.\\.\\.\\s*\\]"),

            Pattern.compile("//\\s*[Cc]odigo\\s+anterior"),
            Pattern.compile("//\\s*[Ss]in\\s+cambios?"),
            Pattern.compile("//\\s*[Mm]antener\\s+como\\s+esta")
        );
    }

    public boolean isDestructive(int originalSize, int newSize) {
        if (originalSize < tinyFileThreshold) {
            return false;
        }

        double reduction = 1.0 - ((double) newSize / originalSize);

        double threshold = originalSize > largeFileThreshold
            ? strictDestructionRatio
            : maxDestructionRatio;

        return reduction > threshold;
    }

    public Pattern detectLazyPattern(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        for (Pattern pattern : lazyPatterns) {
            if (pattern.matcher(content).find()) {
                return pattern;
            }
        }

        return null;
    }

    public static double calculateReduction(int originalSize, int newSize) {
        if (originalSize <= 0) {
            return 0.0;
        }
        return 1.0 - ((double) newSize / originalSize);
    }
}
