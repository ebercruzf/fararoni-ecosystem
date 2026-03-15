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
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class CompletenessCritic implements Critic {
    private static final String NAME = "CompletenessCritic";

    private static final List<Pattern> INCOMPLETE_PATTERNS = List.of(
        Pattern.compile("\\.\\.\\.$"),
        Pattern.compile("etc\\.?$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("and\\s+so\\s+on\\.?$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("to\\s+be\\s+continued", Pattern.CASE_INSENSITIVE),
        Pattern.compile("more\\s+to\\s+come", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\[truncated\\]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\[continued\\]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("I'll\\s+continue", Pattern.CASE_INSENSITIVE),
        Pattern.compile("let\\s+me\\s+continue", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> INCOMPLETE_PATTERNS_ES = List.of(
        Pattern.compile("etcetera\\.?$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("y\\s+asi\\s+sucesivamente\\.?$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("continuara\\.?$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("continuo\\s+despues", Pattern.CASE_INSENSITIVE)
    );

    private final int minLength;
    private final boolean checkCodeBlocks;
    private final boolean checkDelimiters;
    private final boolean includeSpanish;

    public CompletenessCritic() {
        this(20, true, true, true);
    }

    public CompletenessCritic(int minLength, boolean checkCodeBlocks,
                              boolean checkDelimiters, boolean includeSpanish) {
        this.minLength = minLength;
        this.checkCodeBlocks = checkCodeBlocks;
        this.checkDelimiters = checkDelimiters;
        this.includeSpanish = includeSpanish;
    }

    public CompletenessCritic withMinLength(int length) {
        return new CompletenessCritic(length, this.checkCodeBlocks,
            this.checkDelimiters, this.includeSpanish);
    }

    public CompletenessCritic withCheckCodeBlocks(boolean check) {
        return new CompletenessCritic(this.minLength, check,
            this.checkDelimiters, this.includeSpanish);
    }

    public CompletenessCritic withCheckDelimiters(boolean check) {
        return new CompletenessCritic(this.minLength, this.checkCodeBlocks,
            check, this.includeSpanish);
    }

    @Override
    public Evaluation evaluate(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (response.isBlank()) {
            return Evaluation.fail(NAME, "Respuesta vacia");
        }

        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (response.length() < minLength) {
            issues.add(String.format("Respuesta muy corta: %d chars (min: %d)",
                response.length(), minLength));
            suggestions.add("Proporcionar una respuesta mas detallada");
        }

        checkIncompletePatterns(response, issues);

        if (checkDelimiters) {
            checkDelimiterBalance(response, issues, suggestions);
        }

        if (checkCodeBlocks) {
            checkCodeBlockCompleteness(response, issues, suggestions);
        }

        if (issues.isEmpty()) {
            return Evaluation.pass(NAME, "Respuesta completa");
        }

        boolean hasCritical = issues.stream()
            .anyMatch(i -> i.contains("sin cerrar") || i.contains("unclosed"));

        if (hasCritical) {
            return new Evaluation.Fail(
                NAME,
                "Respuesta incompleta: " + issues.get(0),
                java.util.Optional.of(String.join("; ", issues)),
                java.util.Optional.of(suggestions.isEmpty() ? "Completar la respuesta" : suggestions.get(0))
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
        return "Evalua la completitud de respuestas del agente";
    }

    @Override
    public CriticCategory getCategory() {
        return CriticCategory.QUALITY;
    }

    private void checkIncompletePatterns(String response, List<String> issues) {
        String trimmed = response.trim();

        for (Pattern pattern : INCOMPLETE_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                issues.add("Patron de incompletitud detectado: " + pattern.pattern());
            }
        }

        if (includeSpanish) {
            for (Pattern pattern : INCOMPLETE_PATTERNS_ES) {
                if (pattern.matcher(trimmed).find()) {
                    issues.add("Patron de incompletitud detectado (ES): " + pattern.pattern());
                }
            }
        }
    }

    private void checkDelimiterBalance(String response, List<String> issues,
                                       List<String> suggestions) {
        int parenBalance = countBalance(response, '(', ')');
        int braceBalance = countBalance(response, '{', '}');
        int bracketBalance = countBalance(response, '[', ']');

        if (parenBalance != 0) {
            issues.add(String.format("Parentesis desbalanceados: %+d", parenBalance));
            suggestions.add("Verificar parentesis abiertos/cerrados");
        }

        if (braceBalance != 0) {
            issues.add(String.format("Llaves desbalanceadas: %+d", braceBalance));
            suggestions.add("Verificar llaves abiertos/cerrados");
        }

        if (bracketBalance != 0) {
            issues.add(String.format("Corchetes desbalanceados: %+d", bracketBalance));
            suggestions.add("Verificar corchetes abiertos/cerrados");
        }

        int doubleQuotes = countChar(response, '"');
        if (doubleQuotes % 2 != 0) {
            issues.add("Comillas dobles desbalanceadas");
            suggestions.add("Verificar comillas abiertas/cerradas");
        }

        int backticks = countChar(response, '`');
        if (backticks % 3 != 0 && backticks % 2 != 0 && backticks != 0) {
            issues.add("Backticks desbalanceados");
        }
    }

    private void checkCodeBlockCompleteness(String response, List<String> issues,
                                            List<String> suggestions) {
        int codeBlockStarts = countOccurrences(response, "```");
        if (codeBlockStarts % 2 != 0) {
            issues.add("Bloque de codigo markdown sin cerrar");
            suggestions.add("Agregar ``` para cerrar el bloque de codigo");
        }

        if (response.contains("```") && response.trim().endsWith("{")) {
            issues.add("Bloque de codigo parece truncado (termina con '{')");
            suggestions.add("Completar el bloque de codigo");
        }
    }

    private int countBalance(String text, char open, char close) {
        int balance = 0;
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if ((c == '"' || c == '\'') && (i == 0 || text.charAt(i - 1) != '\\')) {
                if (!inString) {
                    inString = true;
                    stringChar = c;
                } else if (c == stringChar) {
                    inString = false;
                }
            }

            if (!inString) {
                if (c == open) balance++;
                else if (c == close) balance--;
            }
        }

        return balance;
    }

    private int countChar(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) count++;
        }
        return count;
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
