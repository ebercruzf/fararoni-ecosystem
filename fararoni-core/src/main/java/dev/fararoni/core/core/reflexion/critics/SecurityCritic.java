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
public final class SecurityCritic implements Critic {
    private static final String NAME = "SecurityCritic";

    private static final List<VulnerabilityPattern> SQL_INJECTION_PATTERNS = List.of(
        new VulnerabilityPattern(
            Pattern.compile("\"\\s*\\+\\s*\\w+\\s*\\+\\s*\".*(?:SELECT|INSERT|UPDATE|DELETE|WHERE)",
                Pattern.CASE_INSENSITIVE),
            "SQL Injection",
            Severity.CRITICAL,
            "Concatenacion de string en SQL query - usar PreparedStatement"
        ),
        new VulnerabilityPattern(
            Pattern.compile("String\\.format\\s*\\([^)]*(?:SELECT|INSERT|UPDATE|DELETE)",
                Pattern.CASE_INSENSITIVE),
            "SQL Injection via String.format",
            Severity.CRITICAL,
            "String.format para SQL - usar PreparedStatement con parametros"
        ),
        new VulnerabilityPattern(
            Pattern.compile("execute(?:Query|Update)\\s*\\(\\s*[\"']\\s*\\+",
                Pattern.CASE_INSENSITIVE),
            "SQL Injection en execute",
            Severity.CRITICAL,
            "Query dinamica en execute - usar PreparedStatement"
        )
    );

    private static final List<VulnerabilityPattern> COMMAND_INJECTION_PATTERNS = List.of(
        new VulnerabilityPattern(
            Pattern.compile("Runtime\\.getRuntime\\(\\)\\.exec\\s*\\([^)]*\\+"),
            "Command Injection via Runtime.exec",
            Severity.CRITICAL,
            "No concatenar input de usuario en exec() - usar ProcessBuilder con array"
        ),
        new VulnerabilityPattern(
            Pattern.compile("ProcessBuilder\\s*\\([^)]*\\+"),
            "Posible Command Injection en ProcessBuilder",
            Severity.HIGH,
            "Validar y sanitizar input antes de pasar a ProcessBuilder"
        ),
        new VulnerabilityPattern(
            Pattern.compile("(?:os\\.system|subprocess\\.(?:call|run|Popen))\\s*\\([^)]*\\+",
                Pattern.CASE_INSENSITIVE),
            "Command Injection (Python)",
            Severity.CRITICAL,
            "No concatenar input en comandos de sistema - usar lista de argumentos"
        )
    );

    private static final List<VulnerabilityPattern> XSS_PATTERNS = List.of(
        new VulnerabilityPattern(
            Pattern.compile("innerHTML\\s*=\\s*[^;]*\\+"),
            "XSS via innerHTML",
            Severity.HIGH,
            "No usar innerHTML con datos de usuario - usar textContent o sanitizar"
        ),
        new VulnerabilityPattern(
            Pattern.compile("document\\.write\\s*\\([^)]*\\+"),
            "XSS via document.write",
            Severity.HIGH,
            "Evitar document.write - usar metodos DOM seguros"
        ),
        new VulnerabilityPattern(
            Pattern.compile("\\$\\{[^}]*\\}(?=.*<)"),
            "Posible XSS en template",
            Severity.MEDIUM,
            "Asegurar escape de HTML en templates"
        )
    );

    private static final List<VulnerabilityPattern> HARDCODED_SECRET_PATTERNS = List.of(
        new VulnerabilityPattern(
            Pattern.compile("(?:password|passwd|pwd)\\s*=\\s*[\"'][^\"']{4,}[\"']",
                Pattern.CASE_INSENSITIVE),
            "Hardcoded Password",
            Severity.CRITICAL,
            "No hardcodear contraseñas - usar variables de entorno o vault"
        ),
        new VulnerabilityPattern(
            Pattern.compile("(?:api[_-]?key|apikey|secret[_-]?key)\\s*=\\s*[\"'][^\"']{8,}[\"']",
                Pattern.CASE_INSENSITIVE),
            "Hardcoded API Key",
            Severity.CRITICAL,
            "No hardcodear API keys - usar variables de entorno"
        ),
        new VulnerabilityPattern(
            Pattern.compile("(?:aws_)?(?:access_key|secret_key)\\s*=\\s*[\"'][A-Za-z0-9+/]{20,}[\"']",
                Pattern.CASE_INSENSITIVE),
            "Hardcoded AWS Credentials",
            Severity.CRITICAL,
            "No hardcodear credenciales AWS - usar IAM roles o env vars"
        ),
        new VulnerabilityPattern(
            Pattern.compile("Bearer\\s+[A-Za-z0-9_-]{20,}"),
            "Hardcoded Bearer Token",
            Severity.HIGH,
            "No hardcodear tokens - obtener dinamicamente"
        ),
        new VulnerabilityPattern(
            Pattern.compile("-----BEGIN\\s+(?:RSA\\s+)?PRIVATE\\s+KEY-----"),
            "Hardcoded Private Key",
            Severity.CRITICAL,
            "No incluir private keys en codigo - usar key management"
        )
    );

    private static final List<VulnerabilityPattern> INSECURE_CRYPTO_PATTERNS = List.of(
        new VulnerabilityPattern(
            Pattern.compile("MessageDigest\\.getInstance\\s*\\(\\s*[\"']MD5[\"']",
                Pattern.CASE_INSENSITIVE),
            "Weak Hash: MD5",
            Severity.MEDIUM,
            "MD5 es inseguro - usar SHA-256 o mejor"
        ),
        new VulnerabilityPattern(
            Pattern.compile("MessageDigest\\.getInstance\\s*\\(\\s*[\"']SHA-?1[\"']",
                Pattern.CASE_INSENSITIVE),
            "Weak Hash: SHA-1",
            Severity.MEDIUM,
            "SHA-1 es debil - usar SHA-256 o mejor"
        ),
        new VulnerabilityPattern(
            Pattern.compile("Cipher\\.getInstance\\s*\\(\\s*[\"']DES[\"']",
                Pattern.CASE_INSENSITIVE),
            "Weak Cipher: DES",
            Severity.HIGH,
            "DES es inseguro - usar AES"
        ),
        new VulnerabilityPattern(
            Pattern.compile("Cipher\\.getInstance\\s*\\(\\s*[\"'][^\"']*ECB",
                Pattern.CASE_INSENSITIVE),
            "Insecure Cipher Mode: ECB",
            Severity.HIGH,
            "ECB mode es inseguro - usar GCM o CBC"
        ),
        new VulnerabilityPattern(
            Pattern.compile("new\\s+Random\\(\\)"),
            "Insecure Random",
            Severity.MEDIUM,
            "java.util.Random no es criptograficamente seguro - usar SecureRandom"
        )
    );

    private static final List<VulnerabilityPattern> PATH_TRAVERSAL_PATTERNS = List.of(
        new VulnerabilityPattern(
            Pattern.compile("new\\s+File\\s*\\([^)]*\\+[^)]*\\)"),
            "Posible Path Traversal",
            Severity.HIGH,
            "Validar path antes de usar - verificar que no contenga ../"
        ),
        new VulnerabilityPattern(
            Pattern.compile("Paths\\.get\\s*\\([^)]*\\+[^)]*\\)"),
            "Posible Path Traversal via Paths.get",
            Severity.HIGH,
            "Validar y normalizar path antes de usar"
        )
    );

    private static final List<VulnerabilityPattern> INSECURE_DESER_PATTERNS = List.of(
        new VulnerabilityPattern(
            Pattern.compile("new\\s+ObjectInputStream\\s*\\("),
            "Insecure Deserialization",
            Severity.HIGH,
            "ObjectInputStream puede ejecutar codigo - usar ValidatingObjectInputStream o JSON"
        ),
        new VulnerabilityPattern(
            Pattern.compile("XMLDecoder\\s*\\("),
            "Insecure XML Deserialization",
            Severity.CRITICAL,
            "XMLDecoder ejecuta codigo arbitrario - no usar con datos no confiables"
        )
    );

    private final boolean failOnCritical;
    private final boolean checkSecrets;
    private final boolean checkCrypto;

    public SecurityCritic() {
        this(true, true, true);
    }

    public SecurityCritic(boolean failOnCritical, boolean checkSecrets, boolean checkCrypto) {
        this.failOnCritical = failOnCritical;
        this.checkSecrets = checkSecrets;
        this.checkCrypto = checkCrypto;
    }

    public SecurityCritic withFailOnCritical(boolean fail) {
        return new SecurityCritic(fail, this.checkSecrets, this.checkCrypto);
    }

    public SecurityCritic withCheckSecrets(boolean check) {
        return new SecurityCritic(this.failOnCritical, check, this.checkCrypto);
    }

    @Override
    public Evaluation evaluate(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (response.isBlank()) {
            return Evaluation.skip(NAME, "Respuesta vacia");
        }

        if (!response.contains("```") && !context.expectsCode()) {
            return Evaluation.skip(NAME, "No se detecta codigo en la respuesta");
        }

        List<VulnerabilityMatch> vulnerabilities = new ArrayList<>();

        checkPatterns(response, SQL_INJECTION_PATTERNS, vulnerabilities);
        checkPatterns(response, COMMAND_INJECTION_PATTERNS, vulnerabilities);
        checkPatterns(response, XSS_PATTERNS, vulnerabilities);
        checkPatterns(response, PATH_TRAVERSAL_PATTERNS, vulnerabilities);
        checkPatterns(response, INSECURE_DESER_PATTERNS, vulnerabilities);

        if (checkSecrets) {
            checkPatterns(response, HARDCODED_SECRET_PATTERNS, vulnerabilities);
        }

        if (checkCrypto) {
            checkPatterns(response, INSECURE_CRYPTO_PATTERNS, vulnerabilities);
        }

        if (vulnerabilities.isEmpty()) {
            return Evaluation.pass(NAME, "No se detectaron vulnerabilidades de seguridad");
        }

        List<VulnerabilityMatch> critical = vulnerabilities.stream()
            .filter(v -> v.severity() == Severity.CRITICAL)
            .toList();

        List<VulnerabilityMatch> high = vulnerabilities.stream()
            .filter(v -> v.severity() == Severity.HIGH)
            .toList();

        List<String> issues = vulnerabilities.stream()
            .map(VulnerabilityMatch::toIssueString)
            .toList();

        List<String> suggestions = vulnerabilities.stream()
            .map(VulnerabilityMatch::suggestion)
            .distinct()
            .toList();

        if (failOnCritical && !critical.isEmpty()) {
            return new Evaluation.Fail(
                NAME,
                String.format("Vulnerabilidad CRITICA detectada: %s", critical.get(0).name()),
                Optional.of(critical.get(0).toIssueString()),
                Optional.of(critical.get(0).suggestion())
            );
        }

        if (critical.size() + high.size() > 2) {
            return new Evaluation.Fail(
                NAME,
                String.format("Multiples vulnerabilidades de seguridad: %d critical, %d high",
                    critical.size(), high.size()),
                Optional.of(String.join("; ", issues.subList(0, Math.min(3, issues.size())))),
                Optional.of("Revisar y corregir todas las vulnerabilidades antes de usar")
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
        return "Detecta vulnerabilidades de seguridad en codigo (OWASP Top 10)";
    }

    @Override
    public CriticCategory getCategory() {
        return CriticCategory.SECURITY;
    }

    private void checkPatterns(String text, List<VulnerabilityPattern> patterns,
                               List<VulnerabilityMatch> matches) {
        for (VulnerabilityPattern vp : patterns) {
            Matcher matcher = vp.pattern().matcher(text);
            while (matcher.find()) {
                matches.add(new VulnerabilityMatch(
                    vp.name(),
                    vp.severity(),
                    matcher.group(),
                    vp.suggestion()
                ));
            }
        }
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    private record VulnerabilityPattern(
        Pattern pattern,
        String name,
        Severity severity,
        String suggestion
    ) {}

    private record VulnerabilityMatch(
        String name,
        Severity severity,
        String matched,
        String suggestion
    ) {
        String toIssueString() {
            return String.format("[%s] %s: '%s'", severity, name, truncate(matched, 50));
        }

        private static String truncate(String s, int max) {
            if (s.length() <= max) return s;
            return s.substring(0, max - 3) + "...";
        }
    }
}
