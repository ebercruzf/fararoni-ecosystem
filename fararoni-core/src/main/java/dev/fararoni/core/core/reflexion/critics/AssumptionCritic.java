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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class AssumptionCritic implements Critic {
    private static final String NAME = "AssumptionCritic";

    private static final List<Pattern> ASSUMPTION_PATTERNS_EN = List.of(
        Pattern.compile("\\b(?:I\\s+)?assum(?:e|ing|ed)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bprobably\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\blikely\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bI\\s+think\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bI\\s+believe\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bmight\\s+be\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bcould\\s+be\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bperhaps\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bmaybe\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bnot\\s+sure\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bI\\s+don'?t\\s+know\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bunclear\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bif\\s+I\\s+understand\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bguess(?:ing)?\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> ASSUMPTION_PATTERNS_ES = List.of(
        Pattern.compile("\\bsupongo\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\basumiendo\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bprobablemente\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bcreo\\s+que\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bpienso\\s+que\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bpodria\\s+ser\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\btal\\s+vez\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bquizas?\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bno\\s+estoy\\s+seguro\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bno\\s+se\\s+si\\b", Pattern.CASE_INSENSITIVE)
    );

    private final int maxAssumptions;
    private final boolean strictMode;
    private final boolean includeSpanish;

    public AssumptionCritic() {
        this(2, false, true);
    }

    public AssumptionCritic(int maxAssumptions, boolean strictMode, boolean includeSpanish) {
        this.maxAssumptions = maxAssumptions;
        this.strictMode = strictMode;
        this.includeSpanish = includeSpanish;
    }

    public AssumptionCritic withMaxAssumptions(int max) {
        return new AssumptionCritic(max, this.strictMode, this.includeSpanish);
    }

    public AssumptionCritic withStrictMode(boolean strict) {
        return new AssumptionCritic(this.maxAssumptions, strict, this.includeSpanish);
    }

    public AssumptionCritic withSpanish(boolean spanish) {
        return new AssumptionCritic(this.maxAssumptions, this.strictMode, spanish);
    }

    @Override
    public Evaluation evaluate(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (response.isBlank()) {
            return Evaluation.skip(NAME, "Respuesta vacia");
        }

        List<AssumptionMatch> matches = findAssumptions(response);

        if (matches.isEmpty()) {
            return Evaluation.pass(NAME, "No se detectaron suposiciones");
        }

        List<String> issues = matches.stream()
            .map(AssumptionMatch::toIssueString)
            .toList();

        List<String> suggestions = List.of(
            "Verificar la informacion antes de afirmar",
            "Preguntar al usuario para clarificar dudas",
            "Usar lenguaje mas asertivo si hay certeza"
        );

        if (strictMode && !matches.isEmpty()) {
            return new Evaluation.Fail(
                NAME,
                String.format("Se detectaron %d suposiciones no verificadas", matches.size()),
                java.util.Optional.of(issues.get(0)),
                java.util.Optional.of("Eliminar suposiciones y verificar informacion")
            );
        }

        if (matches.size() > maxAssumptions) {
            return new Evaluation.Fail(
                NAME,
                String.format("Demasiadas suposiciones: %d (max: %d)", matches.size(), maxAssumptions),
                java.util.Optional.of(String.join("; ", issues)),
                java.util.Optional.of("Reducir suposiciones y verificar informacion")
            );
        }

        return new Evaluation.Warning(NAME, issues, suggestions);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Detecta suposiciones no verificadas en respuestas del agente";
    }

    @Override
    public CriticCategory getCategory() {
        return CriticCategory.QUALITY;
    }

    private List<AssumptionMatch> findAssumptions(String response) {
        List<AssumptionMatch> matches = new ArrayList<>();

        for (Pattern pattern : ASSUMPTION_PATTERNS_EN) {
            findMatches(response, pattern, "EN", matches);
        }

        if (includeSpanish) {
            for (Pattern pattern : ASSUMPTION_PATTERNS_ES) {
                findMatches(response, pattern, "ES", matches);
            }
        }

        return matches;
    }

    private void findMatches(String response, Pattern pattern, String lang, List<AssumptionMatch> matches) {
        Matcher matcher = pattern.matcher(response);
        while (matcher.find()) {
            matches.add(new AssumptionMatch(
                matcher.group(),
                matcher.start(),
                extractContext(response, matcher.start(), matcher.end()),
                lang
            ));
        }
    }

    private String extractContext(String response, int start, int end) {
        int ctxStart = Math.max(0, start - 30);
        int ctxEnd = Math.min(response.length(), end + 30);
        String ctx = response.substring(ctxStart, ctxEnd);

        if (ctxStart > 0) {
            ctx = "..." + ctx;
        }
        if (ctxEnd < response.length()) {
            ctx = ctx + "...";
        }

        return ctx.replaceAll("\\s+", " ");
    }

    private record AssumptionMatch(
        String matched,
        int position,
        String context,
        String language
    ) {
        String toIssueString() {
            return String.format("'%s' encontrado: \"%s\"", matched, context);
        }
    }
}
