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
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@FunctionalInterface
public interface Critic {
    Evaluation evaluate(String response, EvaluationContext context);

    default String getName() {
        return getClass().getSimpleName();
    }

    default String getDescription() {
        return "Critic: " + getName();
    }

    default CriticCategory getCategory() {
        return CriticCategory.GENERAL;
    }

    default boolean isEnabled() {
        return true;
    }

    default Critic andThen(Critic other) {
        Objects.requireNonNull(other, "other critic must not be null");
        Critic self = this;
        return new Critic() {
            @Override
            public Evaluation evaluate(String response, EvaluationContext context) {
                Evaluation first = self.evaluate(response, context);
                if (first.isBlocking()) {
                    return first;
                }
                return other.evaluate(response, context);
            }

            @Override
            public String getName() {
                return self.getName() + "+" + other.getName();
            }

            @Override
            public String getDescription() {
                return "Combined: " + self.getName() + " -> " + other.getName();
            }
        };
    }

    default Critic when(Predicate<EvaluationContext> condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        Critic self = this;
        return new Critic() {
            @Override
            public Evaluation evaluate(String response, EvaluationContext context) {
                if (condition.test(context)) {
                    return self.evaluate(response, context);
                }
                return Evaluation.skip(self.getName(), "Condicion no cumplida");
            }

            @Override
            public String getName() {
                return self.getName() + "[conditional]";
            }
        };
    }

    default Critic asNonBlocking() {
        Critic self = this;
        return new Critic() {
            @Override
            public Evaluation evaluate(String response, EvaluationContext context) {
                Evaluation result = self.evaluate(response, context);
                if (result instanceof Evaluation.Fail fail) {
                    return new Evaluation.Warning(
                        fail.criticName(),
                        List.of(fail.reason()),
                        fail.suggestedFix().map(List::of).orElse(List.of())
                    );
                }
                return result;
            }

            @Override
            public String getName() {
                return self.getName() + "[non-blocking]";
            }
        };
    }

    enum CriticCategory {
        GENERAL,
        QUALITY,
        SECURITY,
        CODE,
        FORMAT,
        COMPLIANCE
    }

    static Critic alwaysPass(String name) {
        return (response, context) -> Evaluation.pass(name);
    }

    static Critic alwaysFail(String name, String reason) {
        return (response, context) -> Evaluation.fail(name, reason);
    }

    static Critic combine(List<Critic> critics) {
        Objects.requireNonNull(critics, "critics list must not be null");
        if (critics.isEmpty()) {
            return alwaysPass("EmptyCritic");
        }

        return (response, context) -> {
            List<String> allWarnings = new java.util.ArrayList<>();
            List<String> allSuggestions = new java.util.ArrayList<>();

            for (Critic critic : critics) {
                if (!critic.isEnabled()) {
                    continue;
                }

                Evaluation result = critic.evaluate(response, context);

                if (result instanceof Evaluation.Fail) {
                    return result;
                }

                if (result instanceof Evaluation.Warning warning) {
                    allWarnings.addAll(warning.issues());
                    allSuggestions.addAll(warning.suggestions());
                }
            }

            if (!allWarnings.isEmpty()) {
                return new Evaluation.Warning("CombinedCritic", allWarnings, allSuggestions);
            }

            return Evaluation.pass("CombinedCritic", "Todos los criticos pasaron");
        };
    }
}
