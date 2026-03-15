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
package dev.fararoni.core.core.ninja;

import java.util.*;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ExecutionModeSelector {
    private static final Logger LOG = Logger.getLogger(ExecutionModeSelector.class.getName());

    private static final Pattern COMPLEX_KEYWORDS = Pattern.compile(
        "(?i)(refactor|architect|design|security|migrate|optimize|" +
        "rewrite|restructure|integrate|implement\\s+new|create\\s+system)"
    );

    private static final Pattern SIMPLE_KEYWORDS = Pattern.compile(
        "(?i)(explain|what\\s+is|show|list|describe|help|how\\s+to)"
    );

    private static final Pattern SPECULATIVE_KEYWORDS = Pattern.compile(
        "(?i)(batch|multiple|all\\s+files|every|foreach|each)"
    );

    private static final int LONG_INPUT_THRESHOLD = 2000;
    private static final int VERY_LONG_INPUT_THRESHOLD = 5000;

    private final Map<String, ExecutionMode> overrides;
    private final boolean adaptiveEnabled;

    private ExecutionModeSelector(Builder builder) {
        this.overrides = new HashMap<>(builder.overrides);
        this.adaptiveEnabled = builder.adaptiveEnabled;
    }

    public static ExecutionModeSelector create() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public ExecutionMode selectMode(String task) {
        return selectMode(task, SelectionContext.empty());
    }

    public ExecutionMode selectMode(String task, SelectionContext context) {
        if (task == null || task.isBlank()) {
            return ExecutionMode.FAST;
        }

        for (Map.Entry<String, ExecutionMode> entry : overrides.entrySet()) {
            if (task.toLowerCase().contains(entry.getKey().toLowerCase())) {
                LOG.fine(() -> "[ModeSelector] Override match: " + entry.getKey());
                return entry.getValue();
            }
        }

        int length = task.length();
        if (length > VERY_LONG_INPUT_THRESHOLD) {
            return ExecutionMode.THOROUGH;
        }

        if (COMPLEX_KEYWORDS.matcher(task).find()) {
            return ExecutionMode.THOROUGH;
        }

        if (SPECULATIVE_KEYWORDS.matcher(task).find()) {
            return ExecutionMode.SPECULATIVE;
        }

        if (SIMPLE_KEYWORDS.matcher(task).find() && length < 500) {
            return ExecutionMode.FAST;
        }

        if (context.isHighPriority()) {
            return ExecutionMode.THOROUGH;
        }

        if (context.isCacheHit()) {
            return ExecutionMode.FAST;
        }

        if (context.hasMultipleTools()) {
            return ExecutionMode.BALANCED;
        }

        return ExecutionMode.BALANCED;
    }

    public ExecutionParams getParamsForMode(ExecutionMode mode) {
        return switch (mode) {
            case FAST -> new ExecutionParams(1, 5000, false, false);
            case BALANCED -> new ExecutionParams(3, 30000, true, false);
            case THOROUGH -> new ExecutionParams(5, 60000, true, true);
            case SPECULATIVE -> new ExecutionParams(3, 30000, true, true);
        };
    }

    public enum ExecutionMode {
        FAST,
        BALANCED,
        THOROUGH,
        SPECULATIVE
    }

    public record SelectionContext(
        boolean isHighPriority,
        boolean isCacheHit,
        boolean hasMultipleTools,
        int estimatedComplexity,
        Map<String, Object> metadata
    ) {
        public static SelectionContext empty() {
            return new SelectionContext(false, false, false, 0, Map.of());
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private boolean isHighPriority = false;
            private boolean isCacheHit = false;
            private boolean hasMultipleTools = false;
            private int estimatedComplexity = 0;
            private Map<String, Object> metadata = new HashMap<>();

            public Builder highPriority(boolean high) {
                this.isHighPriority = high;
                return this;
            }

            public Builder cacheHit(boolean hit) {
                this.isCacheHit = hit;
                return this;
            }

            public Builder multipleTools(boolean multiple) {
                this.hasMultipleTools = multiple;
                return this;
            }

            public Builder complexity(int complexity) {
                this.estimatedComplexity = complexity;
                return this;
            }

            public Builder metadata(String key, Object value) {
                this.metadata.put(key, value);
                return this;
            }

            public SelectionContext build() {
                return new SelectionContext(isHighPriority, isCacheHit, hasMultipleTools,
                    estimatedComplexity, Map.copyOf(metadata));
            }
        }
    }

    public record ExecutionParams(
        int maxReActTurns,
        long timeoutMs,
        boolean enableReflexion,
        boolean enableSpeculation
    ) {}

    public static final class Builder {
        private Map<String, ExecutionMode> overrides = new HashMap<>();
        private boolean adaptiveEnabled = true;

        private Builder() {}

        public Builder override(String pattern, ExecutionMode mode) {
            this.overrides.put(pattern, mode);
            return this;
        }

        public Builder adaptiveEnabled(boolean enabled) {
            this.adaptiveEnabled = enabled;
            return this;
        }

        public ExecutionModeSelector build() {
            return new ExecutionModeSelector(this);
        }
    }
}
