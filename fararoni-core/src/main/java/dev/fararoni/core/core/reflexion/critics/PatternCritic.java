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
package dev.fararoni.core.core.reflexion.critics;

import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.reflexion.Evaluation;
import dev.fararoni.core.core.reflexion.EvaluationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class PatternCritic implements Critic {
    private final String name;
    private final String description;
    private final List<PatternRule> rules;
    private final CriticCategory category;
    private final boolean failOnFirstViolation;

    private PatternCritic(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.rules = List.copyOf(builder.rules);
        this.category = builder.category;
        this.failOnFirstViolation = builder.failOnFirstViolation;
    }

    @Override
    public Evaluation evaluate(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (response.isBlank()) {
            return Evaluation.skip(name, "Respuesta vacia");
        }

        if (rules.isEmpty()) {
            return Evaluation.skip(name, "No hay reglas configuradas");
        }

        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        for (PatternRule rule : rules) {
            RuleResult result = evaluateRule(rule, response);

            if (!result.passed()) {
                issues.add(result.issue());
                if (rule.suggestion() != null) {
                    suggestions.add(rule.suggestion());
                }

                if (failOnFirstViolation && rule.severity() == RuleSeverity.ERROR) {
                    return new Evaluation.Fail(
                        name,
                        result.issue(),
                        Optional.of(result.evidence()),
                        Optional.ofNullable(rule.suggestion())
                    );
                }
            }
        }

        if (issues.isEmpty()) {
            return Evaluation.pass(name, "Todos los patrones validados correctamente");
        }

        long errorCount = rules.stream()
            .filter(r -> r.severity() == RuleSeverity.ERROR)
            .filter(r -> !evaluateRule(r, response).passed())
            .count();

        if (errorCount > 0) {
            return new Evaluation.Fail(
                name,
                String.format("%d violaciones de patron", issues.size()),
                Optional.of(String.join("; ", issues.subList(0, Math.min(3, issues.size())))),
                Optional.of(suggestions.isEmpty() ? "Corregir patrones violados" : suggestions.get(0))
            );
        }

        return new Evaluation.Warning(name, issues, suggestions);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public CriticCategory getCategory() {
        return category;
    }

    private RuleResult evaluateRule(PatternRule rule, String response) {
        Matcher matcher = rule.pattern().matcher(response);
        boolean found = matcher.find();

        if (rule.type() == RuleType.REQUIRED) {
            if (found) {
                return RuleResult.ok();
            } else {
                return RuleResult.fail(
                    rule.message(),
                    "Patron no encontrado: " + rule.pattern().pattern()
                );
            }
        } else {
            if (!found) {
                return RuleResult.ok();
            } else {
                return RuleResult.fail(
                    rule.message(),
                    "Patron prohibido encontrado: " + matcher.group()
                );
            }
        }
    }

    public List<PatternRule> getRules() {
        return rules;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private String description = "Custom pattern validator";
        private final List<PatternRule> rules = new ArrayList<>();
        private CriticCategory category = CriticCategory.GENERAL;
        private boolean failOnFirstViolation = false;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder category(CriticCategory category) {
            this.category = category;
            return this;
        }

        public Builder failOnFirstViolation(boolean fail) {
            this.failOnFirstViolation = fail;
            return this;
        }

        public Builder requirePattern(String regex, String message) {
            return requirePattern(Pattern.compile(regex), message, null, RuleSeverity.ERROR);
        }

        public Builder requirePattern(String regex, String message, String suggestion) {
            return requirePattern(Pattern.compile(regex), message, suggestion, RuleSeverity.ERROR);
        }

        public Builder requirePattern(String regex, int flags, String message) {
            return requirePattern(Pattern.compile(regex, flags), message, null, RuleSeverity.ERROR);
        }

        public Builder requirePattern(Pattern pattern, String message,
                                      String suggestion, RuleSeverity severity) {
            rules.add(new PatternRule(RuleType.REQUIRED, pattern, message, suggestion, severity));
            return this;
        }

        public Builder forbidPattern(String regex, String message) {
            return forbidPattern(Pattern.compile(regex), message, null, RuleSeverity.ERROR);
        }

        public Builder forbidPattern(String regex, String message, String suggestion) {
            return forbidPattern(Pattern.compile(regex), message, suggestion, RuleSeverity.ERROR);
        }

        public Builder forbidPattern(String regex, int flags, String message) {
            return forbidPattern(Pattern.compile(regex, flags), message, null, RuleSeverity.ERROR);
        }

        public Builder forbidPattern(Pattern pattern, String message,
                                     String suggestion, RuleSeverity severity) {
            rules.add(new PatternRule(RuleType.FORBIDDEN, pattern, message, suggestion, severity));
            return this;
        }

        public Builder warnPattern(String regex, String message) {
            rules.add(new PatternRule(
                RuleType.FORBIDDEN,
                Pattern.compile(regex),
                message,
                null,
                RuleSeverity.WARNING
            ));
            return this;
        }

        public PatternCritic build() {
            return new PatternCritic(this);
        }
    }

    public enum RuleType {
        REQUIRED,
        FORBIDDEN
    }

    public enum RuleSeverity {
        WARNING,
        ERROR
    }

    public record PatternRule(
        RuleType type,
        Pattern pattern,
        String message,
        String suggestion,
        RuleSeverity severity
    ) {}

    private record RuleResult(boolean success, String issue, String evidence) {
        boolean passed() {
            return success;
        }

        static RuleResult ok() {
            return new RuleResult(true, null, null);
        }

        static RuleResult fail(String issue, String evidence) {
            return new RuleResult(false, issue, evidence);
        }
    }

    public static PatternCritic jsonValidator() {
        return builder("JsonValidator")
            .description("Valida formato JSON basico")
            .category(CriticCategory.FORMAT)
            .requirePattern("^\\s*[{\\[]", "JSON debe empezar con { o [")
            .requirePattern("[}\\]]\\s*$", "JSON debe terminar con } o ]")
            .forbidPattern("undefined", "No usar 'undefined' en JSON", "Usar 'null'")
            .forbidPattern("'[^']*'\\s*:", "JSON requiere comillas dobles", "Usar comillas dobles")
            .build();
    }

    public static PatternCritic markdownValidator() {
        return builder("MarkdownValidator")
            .description("Valida formato Markdown")
            .category(CriticCategory.FORMAT)
            .forbidPattern("<script", "No incluir scripts en Markdown")
            .forbidPattern("<style", "No incluir estilos inline en Markdown")
            .build();
    }

    public static PatternCritic structuredResponseValidator() {
        return builder("StructuredResponseValidator")
            .description("Valida respuestas con estructura esperada")
            .category(CriticCategory.FORMAT)
            .requirePattern("(?:#+|\\*{2}).*(?:Summary|Resumen|Conclusion)",
                Pattern.CASE_INSENSITIVE,
                "Respuesta debe incluir seccion de resumen/conclusion")
            .forbidPattern("TODO|FIXME|XXX",
                "No dejar marcadores de tareas pendientes")
            .build();
    }
}
