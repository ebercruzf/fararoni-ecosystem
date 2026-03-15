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
package dev.fararoni.core.core.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public final class DomainInferenceEngine {

    private DomainInferenceEngine() {}

    public static DomainInferenceResult analyze(String className) {
        if (className == null || className.isBlank()) {
            return DomainInferenceResult.unknown(List.of());
        }

        List<String> words = splitCamelCase(className);

        for (String word : words) {
            String domain = BusinessDomainDictionary.lookup(word);
            if (domain != null) {
                double confidence = 1.0 - (words.indexOf(word) * 0.1);
                return new DomainInferenceResult(domain, word, confidence, words);
            }
        }

        return DomainInferenceResult.unknown(words);
    }

    public static List<String> splitCamelCase(String s) {
        if (s == null || s.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(s.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])"))
                     .filter(w -> w != null && !w.isBlank())
                     .collect(Collectors.toList());
    }

    public static String extractClassName(String path) {
        if (path == null) return "";
        String filename = path.contains("/")
            ? path.substring(path.lastIndexOf('/') + 1)
            : path.substring(path.lastIndexOf('\\') + 1);
        return filename.replace(".java", "");
    }
}
