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
package dev.fararoni.core.core.safety.overseer;

import dev.fararoni.core.core.mission.model.AgentTemplate.ValidationPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class OverseerValidator {
    private static final Logger LOG = Logger.getLogger(OverseerValidator.class.getName());

    private final OverseerMetrics metrics;

    public OverseerValidator() {
        this.metrics = new OverseerMetrics();
    }

    public OverseerValidator(OverseerMetrics metrics) {
        this.metrics = metrics != null ? metrics : new OverseerMetrics();
    }

    public ValidationResult validate(String content, ValidationPolicy policy) {
        if (policy == null || !policy.enabled()) {
            return ValidationResult.valid();
        }

        List<String> violations = new ArrayList<>();

        if (policy.hasProhibitions()) {
            for (String regex : policy.prohibitedRegex()) {
                if (matchesPattern(content, regex)) {
                    String violation = "PROHIBIDO: Encontrado patrón '" + regex + "'";
                    violations.add(violation);
                    metrics.recordViolation("prohibited", regex);
                    LOG.warning(violation);
                }
            }
        }

        if (policy.hasRequirements()) {
            for (String regex : policy.requiredPatterns()) {
                if (isParsingMarker(regex)) {
                    LOG.fine("Ignorando patrón de parsing: " + regex);
                    continue;
                }
                if (!matchesPattern(content, regex)) {
                    String violation = "FALTANTE: No se encontró patrón requerido '" + regex + "'";
                    violations.add(violation);
                    metrics.recordViolation("missing", regex);
                    LOG.warning(violation);
                }
            }
        }

        int contentSize = content.getBytes().length;
        if (contentSize > policy.maxFileSize()) {
            String violation = "TAMAÑO: Contenido (" + contentSize + " bytes) excede máximo (" +
                               policy.maxFileSize() + " bytes)";
            violations.add(violation);
            metrics.recordViolation("size", String.valueOf(contentSize));
            LOG.warning(violation);
        }

        if (violations.isEmpty()) {
            metrics.recordSuccess();
            return ValidationResult.valid();
        } else {
            return ValidationResult.invalid(violations);
        }
    }

    public ValidationResult validateFile(String filePath, String content, ValidationPolicy policy) {
        ValidationPolicy filteredPolicy = filterPolicyByFileType(filePath, policy);
        ValidationResult result = validate(content, filteredPolicy);

        if (!result.isValid()) {
            LOG.info("Archivo '" + filePath + "' rechazado: " + result.violations().size() + " violaciones");
        } else {
            LOG.fine("Archivo '" + filePath + "' validado OK");
        }

        return result;
    }

    private ValidationPolicy filterPolicyByFileType(String filePath, ValidationPolicy policy) {
        if (policy == null || !policy.enabled()) {
            return policy;
        }

        String lowerPath = filePath.toLowerCase();
        boolean isJava = lowerPath.endsWith(".java");

        List<String> filteredRequired = new ArrayList<>();
        if (policy.hasRequirements()) {
            for (String pattern : policy.requiredPatterns()) {
                if (pattern.contains("package") && !isJava) {
                    LOG.fine("Ignorando patrón '" + pattern + "' para archivo no-Java: " + filePath);
                    continue;
                }
                filteredRequired.add(pattern);
            }
        }

        if (filteredRequired.size() == (policy.requiredPatterns() != null ? policy.requiredPatterns().size() : 0)) {
            return policy;
        }

        return new ValidationPolicy(
            policy.enabled(),
            policy.prohibitedRegex(),
            filteredRequired,
            policy.maxFileSize()
        );
    }

    private boolean isParsingMarker(String regex) {
        if (regex.contains(">>>FILE") || regex.contains("FILE:")) {
            return true;
        }
        if (regex.contains("<<<END") || regex.contains("END>>>")) {
            return true;
        }
        return false;
    }

    private boolean matchesPattern(String content, String regex) {
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
            return pattern.matcher(content).find();
        } catch (PatternSyntaxException e) {
            LOG.warning("Regex inválido: " + regex + " - " + e.getMessage());
            return false;
        }
    }

    public OverseerMetrics getMetrics() {
        return metrics;
    }

    public record ValidationResult(
        boolean isValid,
        List<String> violations
    ) {
        public static ValidationResult valid() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult invalid(List<String> violations) {
            return new ValidationResult(false, List.copyOf(violations));
        }

        public String violationsSummary() {
            if (violations.isEmpty()) {
                return "Sin violaciones";
            }
            return String.join("; ", violations);
        }
    }
}
