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
public final class EvidenceCritic implements Critic {
    private static final String NAME = "EvidenceCritic";

    private static final List<Pattern> STRONG_CLAIM_PATTERNS = List.of(
        Pattern.compile("\\balways\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bnever\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:all|every)\\s+\\w+\\s+(?:is|are|will|must)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bnone\\s+(?:of|will|can)\\b", Pattern.CASE_INSENSITIVE),

        Pattern.compile("\\bthe\\s+(?:best|worst|only|fastest|slowest)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bdefinitely\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bcertainly\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\babsolutely\\b", Pattern.CASE_INSENSITIVE),

        Pattern.compile("\\bmuch\\s+(?:faster|slower|better|worse)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bsignificantly\\s+(?:more|less|better|worse)\\b", Pattern.CASE_INSENSITIVE),

        Pattern.compile("\\b\\d{1,2}%\\s+(?:of|faster|slower|more|less)\\b"),
        Pattern.compile("\\b(?:studies|research)\\s+(?:show|prove|indicate)\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> STRONG_CLAIM_PATTERNS_ES = List.of(
        Pattern.compile("\\bsiempre\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bnunca\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\btodos?\\s+(?:los|las)?\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bel\\s+(?:mejor|peor|unico)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bdefinitivamente\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bmucho\\s+(?:mas|menos|mejor|peor)\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> EVIDENCE_PATTERNS = List.of(
        Pattern.compile("\\baccording\\s+to\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bbased\\s+on\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bas\\s+(?:shown|documented|described)\\s+in\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bthe\\s+documentation\\s+(?:states|shows|indicates)\\b", Pattern.CASE_INSENSITIVE),

        Pattern.compile("\\bfor\\s+example\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bsuch\\s+as\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\be\\.g\\.\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bi\\.e\\.\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bhere(?:'s|\\s+is)\\s+(?:an?|the)\\s+example\\b", Pattern.CASE_INSENSITIVE),

        Pattern.compile("```"),

        Pattern.compile("https?://"),
        Pattern.compile("\\[.+?\\]\\(.+?\\)")
    );

    private final boolean requireEvidenceForAllClaims;
    private final int maxUnsubstantiatedClaims;
    private final boolean includeSpanish;

    public EvidenceCritic() {
        this(false, 3, true);
    }

    public EvidenceCritic(boolean requireEvidenceForAllClaims,
                          int maxUnsubstantiatedClaims, boolean includeSpanish) {
        this.requireEvidenceForAllClaims = requireEvidenceForAllClaims;
        this.maxUnsubstantiatedClaims = maxUnsubstantiatedClaims;
        this.includeSpanish = includeSpanish;
    }

    public EvidenceCritic withRequireEvidenceForAll(boolean require) {
        return new EvidenceCritic(require, this.maxUnsubstantiatedClaims, this.includeSpanish);
    }

    public EvidenceCritic withMaxUnsubstantiated(int max) {
        return new EvidenceCritic(this.requireEvidenceForAllClaims, max, this.includeSpanish);
    }

    @Override
    public Evaluation evaluate(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (response.isBlank()) {
            return Evaluation.skip(NAME, "Respuesta vacia");
        }

        List<ClaimMatch> claims = findStrongClaims(response);

        if (claims.isEmpty()) {
            return Evaluation.pass(NAME, "No se detectaron afirmaciones que requieran evidencia");
        }

        boolean hasEvidence = hasEvidence(response);
        int evidenceCount = countEvidence(response);

        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (!hasEvidence && !claims.isEmpty()) {
            for (ClaimMatch claim : claims) {
                issues.add(String.format("Afirmacion sin evidencia: '%s'", claim.matched()));
            }
            suggestions.add("Agregar ejemplos o referencias que respalden las afirmaciones");
            suggestions.add("Usar lenguaje mas moderado si no hay evidencia disponible");
        } else if (claims.size() > evidenceCount * 2) {
            issues.add(String.format("Proporcion afirmaciones/evidencia desbalanceada: %d claims, %d evidence",
                claims.size(), evidenceCount));
            suggestions.add("Agregar mas ejemplos o referencias");
        }

        if (issues.isEmpty()) {
            return Evaluation.pass(NAME,
                String.format("Afirmaciones respaldadas (%d claims, %d evidence)", claims.size(), evidenceCount));
        }

        if (requireEvidenceForAllClaims || claims.size() > maxUnsubstantiatedClaims) {
            return new Evaluation.Fail(
                NAME,
                String.format("Afirmaciones fuertes sin evidencia suficiente (%d)", claims.size()),
                java.util.Optional.of(issues.get(0)),
                java.util.Optional.of(suggestions.isEmpty() ? "Agregar evidencia" : suggestions.get(0))
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
        return "Valida que las afirmaciones fuertes esten respaldadas por evidencia";
    }

    @Override
    public CriticCategory getCategory() {
        return CriticCategory.QUALITY;
    }

    private List<ClaimMatch> findStrongClaims(String response) {
        List<ClaimMatch> matches = new ArrayList<>();

        for (Pattern pattern : STRONG_CLAIM_PATTERNS) {
            Matcher matcher = pattern.matcher(response);
            while (matcher.find()) {
                matches.add(new ClaimMatch(matcher.group(), matcher.start()));
            }
        }

        if (includeSpanish) {
            for (Pattern pattern : STRONG_CLAIM_PATTERNS_ES) {
                Matcher matcher = pattern.matcher(response);
                while (matcher.find()) {
                    matches.add(new ClaimMatch(matcher.group(), matcher.start()));
                }
            }
        }

        return matches;
    }

    private boolean hasEvidence(String response) {
        for (Pattern pattern : EVIDENCE_PATTERNS) {
            if (pattern.matcher(response).find()) {
                return true;
            }
        }
        return false;
    }

    private int countEvidence(String response) {
        int count = 0;
        for (Pattern pattern : EVIDENCE_PATTERNS) {
            Matcher matcher = pattern.matcher(response);
            while (matcher.find()) {
                count++;
            }
        }
        return count;
    }

    private record ClaimMatch(String matched, int position) {}
}
