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
package dev.fararoni.core.core.hybrid;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class UserIntentRouter {
    private static final UserIntentRouter INSTANCE = new UserIntentRouter();

    private UserIntentRouter() {
    }

    public static UserIntentRouter getInstance() {
        return INSTANCE;
    }

    private static final Pattern METHOD_NAME_PATTERN = Pattern.compile(
        "(?:método|method|función|function|metodo|funcion)\\s+(\\w+)|" +
        "\\b([a-z][a-zA-Z0-9]*(?:[A-Z][a-zA-Z0-9]*)*)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile(
        "\\b([a-z][a-zA-Z0-9]*[A-Z][a-zA-Z0-9]*)\\b"
    );

    public UserIntent detectIntent(String userQuery) {
        Objects.requireNonNull(userQuery, "userQuery no puede ser null");

        String normalizedQuery = userQuery.toLowerCase();

        for (UserIntent intent : new UserIntent[]{
            UserIntent.DEBUG,
            UserIntent.REFACTOR,
            UserIntent.WRITE_TESTS,
            UserIntent.CODE_GEN,
            UserIntent.EXPLAIN,
            UserIntent.AUDIT,
            UserIntent.DOCUMENTATION
        }) {
            for (String keyword : intent.getKeywords()) {
                if (normalizedQuery.contains(keyword.toLowerCase())) {
                    return intent;
                }
            }
        }

        return UserIntent.GENERAL;
    }

    public String extractTargetMethod(String userQuery) {
        Objects.requireNonNull(userQuery, "userQuery no puede ser null");

        Matcher explicitMatcher = METHOD_NAME_PATTERN.matcher(userQuery);
        while (explicitMatcher.find()) {
            String explicit = explicitMatcher.group(1);
            if (explicit != null && !isCommonWord(explicit)) {
                return explicit;
            }
        }

        Matcher camelMatcher = CAMEL_CASE_PATTERN.matcher(userQuery);
        while (camelMatcher.find()) {
            String camel = camelMatcher.group(1);
            if (!isCommonWord(camel)) {
                return camel;
            }
        }

        return null;
    }

    public DetectionResult detect(String userQuery) {
        UserIntent intent = detectIntent(userQuery);
        String target = extractTargetMethod(userQuery);
        return new DetectionResult(intent, target);
    }

    private boolean isCommonWord(String word) {
        if (word == null || word.length() < 3) {
            return true;
        }
        String lower = word.toLowerCase();
        return switch (lower) {
            case "the", "this", "that", "with", "from", "into", "for",
                 "del", "los", "las", "para", "como", "que", "por",
                 "código", "codigo", "code", "file", "archivo",
                 "método", "metodo", "method", "función", "funcion", "function" -> true;
            default -> false;
        };
    }

    public record DetectionResult(UserIntent intent, String targetMethod) {
        public boolean hasTarget() {
            return targetMethod != null && !targetMethod.isBlank();
        }

        public PruningStrategy getRecommendedStrategy() {
            return intent.getRecommendedStrategy();
        }
    }
}
