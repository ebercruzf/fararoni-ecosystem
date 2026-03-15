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
package dev.fararoni.core.core.reflexion;

import java.util.List;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public sealed interface Evaluation
    permits Evaluation.Pass, Evaluation.Warning, Evaluation.Fail, Evaluation.Skip {
    String criticName();

    default boolean isBlocking() {
        return this instanceof Fail;
    }

    default boolean isPassed() {
        return this instanceof Pass;
    }

    default boolean hasWarnings() {
        return this instanceof Warning;
    }

    default Severity severity() {
        return switch (this) {
            case Pass ignored -> Severity.INFO;
            case Warning ignored -> Severity.WARNING;
            case Fail ignored -> Severity.ERROR;
            case Skip ignored -> Severity.DEBUG;
        };
    }

    String toSummary();

    record Pass(
        String criticName,
        String message,
        double confidence
    ) implements Evaluation {
        public Pass(String criticName) {
            this(criticName, "Evaluacion exitosa", 1.0);
        }

        public Pass(String criticName, String message) {
            this(criticName, message, 1.0);
        }

        @Override
        public String toSummary() {
            return String.format("[PASS] %s: %s (confianza: %.0f%%)",
                criticName, message, confidence * 100);
        }
    }

    record Warning(
        String criticName,
        List<String> issues,
        List<String> suggestions
    ) implements Evaluation {
        public Warning {
            issues = issues != null ? List.copyOf(issues) : List.of();
            suggestions = suggestions != null ? List.copyOf(suggestions) : List.of();
        }

        public Warning(String criticName, String issue) {
            this(criticName, List.of(issue), List.of());
        }

        public Warning(String criticName, List<String> issues) {
            this(criticName, issues, List.of());
        }

        @Override
        public String toSummary() {
            return String.format("[WARN] %s: %d issue(s) - %s",
                criticName, issues.size(), String.join("; ", issues));
        }
    }

    record Fail(
        String criticName,
        String reason,
        Optional<String> evidence,
        Optional<String> suggestedFix
    ) implements Evaluation {
        public Fail {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
            evidence = evidence != null ? evidence : Optional.empty();
            suggestedFix = suggestedFix != null ? suggestedFix : Optional.empty();
        }

        public Fail(String criticName, String reason) {
            this(criticName, reason, Optional.empty(), Optional.empty());
        }

        public Fail(String criticName, String reason, String suggestedFix) {
            this(criticName, reason, Optional.empty(), Optional.of(suggestedFix));
        }

        @Override
        public String toSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[FAIL] %s: %s", criticName, reason));
            suggestedFix.ifPresent(fix -> sb.append(" | Fix: ").append(fix));
            return sb.toString();
        }
    }

    record Skip(
        String criticName,
        String reason
    ) implements Evaluation {
        public Skip(String criticName) {
            this(criticName, "Evaluacion no aplica");
        }

        @Override
        public String toSummary() {
            return String.format("[SKIP] %s: %s", criticName, reason);
        }
    }

    enum Severity {
        DEBUG,
        INFO,
        WARNING,
        ERROR
    }

    static Pass pass(String criticName) {
        return new Pass(criticName);
    }

    static Pass pass(String criticName, String message) {
        return new Pass(criticName, message);
    }

    static Warning warning(String criticName, String issue) {
        return new Warning(criticName, issue);
    }

    static Warning warning(String criticName, List<String> issues) {
        return new Warning(criticName, issues);
    }

    static Fail fail(String criticName, String reason) {
        return new Fail(criticName, reason);
    }

    static Fail fail(String criticName, String reason, String suggestedFix) {
        return new Fail(criticName, reason, suggestedFix);
    }

    static Skip skip(String criticName) {
        return new Skip(criticName);
    }

    static Skip skip(String criticName, String reason) {
        return new Skip(criticName, reason);
    }
}
