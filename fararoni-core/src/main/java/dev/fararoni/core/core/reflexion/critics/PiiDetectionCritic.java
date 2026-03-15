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
public final class PiiDetectionCritic implements Critic {
    private static final String NAME = "PiiDetectionCritic";

    private static final Pattern SSN_PATTERN =
        Pattern.compile("\\b\\d{3}[-\\s]?\\d{2}[-\\s]?\\d{4}\\b");

    private static final List<Pattern> CREDIT_CARD_PATTERNS = List.of(
        Pattern.compile("\\b4\\d{3}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b"),
        Pattern.compile("\\b5[1-5]\\d{2}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b"),
        Pattern.compile("\\b3[47]\\d{2}[-\\s]?\\d{6}[-\\s]?\\d{5}\\b"),
        Pattern.compile("\\b(?:\\d{4}[-\\s]?){3}\\d{4}\\b")
    );

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");

    private static final List<Pattern> PHONE_PATTERNS = List.of(
        Pattern.compile("\\b\\(?\\d{3}\\)?[-\\s.]?\\d{3}[-\\s.]?\\d{4}\\b"),
        Pattern.compile("\\b\\+\\d{1,3}[-\\s.]?\\d{2,4}[-\\s.]?\\d{3,4}[-\\s.]?\\d{3,4}\\b"),
        Pattern.compile("\\b\\+?52[-\\s.]?\\d{2,3}[-\\s.]?\\d{3,4}[-\\s.]?\\d{4}\\b")
    );

    private static final Pattern DOB_PATTERN =
        Pattern.compile("(?:born|DOB|date\\s+of\\s+birth|nacimiento)[:\\s]+\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IP_ADDRESS_PATTERN =
        Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b");

    private static final List<Pattern> NATIONAL_ID_PATTERNS = List.of(
        Pattern.compile("\\b[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z\\d]\\d\\b"),
        Pattern.compile("\\b[A-Z]{3,4}\\d{6}[A-Z\\d]{3}\\b"),
        Pattern.compile("\\b[A-CEGHJ-PR-TW-Z][A-CEGHJ-NPR-TW-Z]\\d{6}[A-D]\\b"),
        Pattern.compile("(?:passport|pasaporte)[:\\s#]+[A-Z0-9]{6,12}", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> BANK_ACCOUNT_PATTERNS = List.of(
        Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{7}(?:[A-Z0-9]?){0,16}\\b"),
        Pattern.compile("(?:routing|ABA)[:\\s#]*\\d{9}", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b\\d{18}\\b")
    );

    private static final List<Pattern> HEALTH_DATA_PATTERNS = List.of(
        Pattern.compile("(?:patient|paciente)\\s*(?:ID|#|numero)[:\\s]*[A-Z0-9]+",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:medical\\s+record|historia\\s+clinica)[:\\s#]*[A-Z0-9]+",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:diagnosed|diagnosticado)\\s+with\\s+\\w+", Pattern.CASE_INSENSITIVE)
    );

    private final boolean failOnFinancial;
    private final boolean checkEmail;
    private final boolean checkPhone;
    private final boolean checkHealthData;

    public PiiDetectionCritic() {
        this(true, true, true, true);
    }

    public PiiDetectionCritic(boolean failOnFinancial, boolean checkEmail,
                              boolean checkPhone, boolean checkHealthData) {
        this.failOnFinancial = failOnFinancial;
        this.checkEmail = checkEmail;
        this.checkPhone = checkPhone;
        this.checkHealthData = checkHealthData;
    }

    public PiiDetectionCritic withFailOnFinancial(boolean fail) {
        return new PiiDetectionCritic(fail, this.checkEmail, this.checkPhone, this.checkHealthData);
    }

    public PiiDetectionCritic withCheckEmail(boolean check) {
        return new PiiDetectionCritic(this.failOnFinancial, check, this.checkPhone, this.checkHealthData);
    }

    public PiiDetectionCritic withCheckPhone(boolean check) {
        return new PiiDetectionCritic(this.failOnFinancial, this.checkEmail, check, this.checkHealthData);
    }

    @Override
    public Evaluation evaluate(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (response.isBlank()) {
            return Evaluation.skip(NAME, "Respuesta vacia");
        }

        List<PiiMatch> findings = new ArrayList<>();

        checkPattern(response, SSN_PATTERN, "SSN (Social Security Number)", PiiCategory.FINANCIAL, findings);

        for (Pattern p : CREDIT_CARD_PATTERNS) {
            checkPattern(response, p, "Credit Card Number", PiiCategory.FINANCIAL, findings);
        }

        for (Pattern p : BANK_ACCOUNT_PATTERNS) {
            checkPattern(response, p, "Bank Account", PiiCategory.FINANCIAL, findings);
        }

        for (Pattern p : NATIONAL_ID_PATTERNS) {
            checkPattern(response, p, "National ID", PiiCategory.IDENTIFIER, findings);
        }

        if (checkEmail) {
            checkPattern(response, EMAIL_PATTERN, "Email Address", PiiCategory.CONTACT, findings);
        }

        if (checkPhone) {
            for (Pattern p : PHONE_PATTERNS) {
                checkPattern(response, p, "Phone Number", PiiCategory.CONTACT, findings);
            }
        }

        checkPattern(response, DOB_PATTERN, "Date of Birth", PiiCategory.IDENTIFIER, findings);

        checkPattern(response, IP_ADDRESS_PATTERN, "IP Address", PiiCategory.TECHNICAL, findings);

        if (checkHealthData) {
            for (Pattern p : HEALTH_DATA_PATTERNS) {
                checkPattern(response, p, "Health Data", PiiCategory.HEALTH, findings);
            }
        }

        findings = filterFalsePositives(findings, response);

        if (findings.isEmpty()) {
            return Evaluation.pass(NAME, "No se detectaron datos personales (PII)");
        }

        List<PiiMatch> financial = findings.stream()
            .filter(f -> f.category() == PiiCategory.FINANCIAL)
            .toList();

        List<PiiMatch> health = findings.stream()
            .filter(f -> f.category() == PiiCategory.HEALTH)
            .toList();

        List<String> issues = findings.stream()
            .map(PiiMatch::toIssueString)
            .toList();

        List<String> suggestions = List.of(
            "Eliminar o enmascarar datos personales",
            "Usar datos de ejemplo en lugar de datos reales",
            "Verificar cumplimiento con GDPR/CCPA"
        );

        if (failOnFinancial && (!financial.isEmpty() || !health.isEmpty())) {
            String type = !financial.isEmpty() ? "financiero" : "de salud";
            return new Evaluation.Fail(
                NAME,
                String.format("PII %s detectado: %s", type,
                    !financial.isEmpty() ? financial.get(0).type() : health.get(0).type()),
                Optional.of(issues.get(0)),
                Optional.of("Eliminar o enmascarar datos personales antes de compartir")
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
        return "Detecta Informacion Personal Identificable (PII) en respuestas";
    }

    @Override
    public CriticCategory getCategory() {
        return CriticCategory.COMPLIANCE;
    }

    private void checkPattern(String text, Pattern pattern, String type,
                              PiiCategory category, List<PiiMatch> findings) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            findings.add(new PiiMatch(type, category, maskPii(matcher.group()), matcher.start()));
        }
    }

    private List<PiiMatch> filterFalsePositives(List<PiiMatch> findings, String response) {
        return findings.stream()
            .filter(f -> !isFalsePositive(f, response))
            .toList();
    }

    private boolean isFalsePositive(PiiMatch match, String response) {
        String masked = match.masked();

        if (match.type().equals("Email Address")) {
            if (masked.contains("example.com") ||
                masked.contains("test.com") ||
                masked.contains("localhost") ||
                masked.contains("@domain") ||
                masked.equals("user@...")) {
                return true;
            }
        }

        if (match.type().equals("IP Address")) {
            if (masked.startsWith("127.") ||
                masked.startsWith("192.168.") ||
                masked.startsWith("10.") ||
                masked.equals("0.0.0.0")) {
                return true;
            }
        }

        if (match.type().equals("Credit Card Number")) {
            if (masked.startsWith("4111") || masked.startsWith("5500")) {
                return true;
            }
        }

        return false;
    }

    private String maskPii(String pii) {
        if (pii.length() <= 4) {
            return "****";
        }
        return pii.substring(0, 2) + "***" + pii.substring(pii.length() - 2);
    }

    public enum PiiCategory {
        FINANCIAL,
        IDENTIFIER,
        CONTACT,
        HEALTH,
        TECHNICAL
    }

    private record PiiMatch(
        String type,
        PiiCategory category,
        String masked,
        int position
    ) {
        String toIssueString() {
            return String.format("[%s] %s detectado: %s", category, type, masked);
        }
    }
}
