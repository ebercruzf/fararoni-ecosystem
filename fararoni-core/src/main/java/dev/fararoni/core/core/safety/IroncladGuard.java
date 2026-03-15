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
package dev.fararoni.core.core.safety;

import dev.fararoni.core.core.safety.IntentExtractor.Intent;
import dev.fararoni.core.core.safety.IntentExtractor.IntentType;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class IroncladGuard {
    private final ContentIntegrityPolicy policy;

    private static final double JACCARD_MIN_THRESHOLD = 0.10;

    private String currentUserRequest;

    public IroncladGuard() {
        this(ContentIntegrityPolicy.defaultPolicy());
    }

    public IroncladGuard(ContentIntegrityPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy no puede ser null");
    }

    public void setUserRequest(String userRequest) {
        this.currentUserRequest = userRequest;
    }

    public void validate(String path, String originalContent, String newContent, String forceReason) {
        Objects.requireNonNull(path, "path no puede ser null");
        Objects.requireNonNull(newContent, "newContent no puede ser null");

        if (forceReason != null && !forceReason.isBlank()) {
            System.out.println("[IRONCLAD-GUARD] FORCE_OVERRIDE activado para: " + path);
            System.out.println("[IRONCLAD-GUARD] Razon: " + forceReason);
            return;
        }

        if (originalContent == null || originalContent.isEmpty()) {
            return;
        }

        int originalSize = originalContent.length();
        int newSize = newContent.length();

        if (policy.isDestructive(originalSize, newSize)) {
            double reductionPct = ContentIntegrityPolicy.calculateReduction(originalSize, newSize) * 100;

            String message = String.format(
                "[Kill Switch] BLOQUEADO: Contenido truncado detectado en '%s'. " +
                "Original: %d chars, Nuevo: %d chars (reduccion: %.1f%%). " +
                "Esto indica que el LLM omitio contenido existente. " +
                "Para forzar, proporciona 'force_destruction' con la razon.",
                path, originalSize, newSize, reductionPct
            );

            System.out.println("[IRONCLAD-GUARD] " + message);

            throw new SafetyException(
                message,
                SafetyException.SafetyErrorCode.DESTRUCTIVE_WRITE
            );
        }

        Pattern lazyPattern = policy.detectLazyPattern(newContent);
        if (lazyPattern != null) {
            String message = String.format(
                "[Kill Switch] BLOQUEADO: Lazy pattern detectado en '%s'. " +
                "El LLM uso un placeholder en lugar de codigo real: '%s'. " +
                "Esto indica que el LLM no genero el contenido completo. " +
                "Para forzar, proporciona 'force_destruction' con la razon.",
                path, lazyPattern.pattern()
            );

            System.out.println("[IRONCLAD-GUARD] " + message);

            throw new SafetyException(
                message,
                SafetyException.SafetyErrorCode.DESTRUCTIVE_WRITE
            );
        }

        validateAnillo3(path, originalContent, newContent);

        System.out.println("[IRONCLAD-GUARD] Validacion OK para: " + path +
            " (original: " + originalSize + ", nuevo: " + newSize + " chars)");
    }

    private void validateAnillo3(String path, String originalContent, String newContent) {
        double similarity = jaccardSimilarity(originalContent, newContent);

        Intent intent = currentUserRequest != null
            ? IntentExtractor.parse(currentUserRequest)
            : new Intent(IntentType.UNKNOWN, null);

        if (similarity < JACCARD_MIN_THRESHOLD && !intent.isRefactor()) {
            String message = String.format(
                "[Kill Switch] BLOQUEADO: Cambio radical detectado en '%s'. " +
                "Similitud Jaccard: %.1f%% (mínimo: %.1f%%). " +
                "El LLM generó código muy diferente al original sin que se pidiera refactor. " +
                "Para forzar, usa 'force_destruction' con la razón.",
                path, similarity * 100, JACCARD_MIN_THRESHOLD * 100
            );

            System.out.println("[IRONCLAD-GUARD] " + message);

            throw new SafetyException(
                message,
                SafetyException.SafetyErrorCode.DESTRUCTIVE_WRITE
            );
        }

        if (intent.requiresComplianceCheck() && intent.subject() != null) {
            if (newContent.equals(originalContent)) {
                String message = String.format(
                    "[Kill Switch] BLOQUEADO: Sin cambios detectados en '%s'. " +
                    "Se solicitó '%s' pero el archivo no fue modificado.",
                    path, currentUserRequest
                );

                System.out.println("[IRONCLAD-GUARD] " + message);

                throw new SafetyException(
                    message,
                    SafetyException.SafetyErrorCode.COMPLIANCE_ERROR
                );
            }

            switch (intent.type()) {
                case ADD_FIELD, ADD_METHOD -> {
                    if (!newContent.contains(intent.subject())) {
                        String message = String.format(
                            "[Kill Switch] BLOQUEADO: Cumplimiento fallido en '%s'. " +
                            "Se solicitó agregar '%s' pero no se encontró en el resultado.",
                            path, intent.subject()
                        );

                        System.out.println("[IRONCLAD-GUARD] " + message);

                        throw new SafetyException(
                            message,
                            SafetyException.SafetyErrorCode.COMPLIANCE_ERROR
                        );
                    }
                    System.out.println("[IRONCLAD-GUARD] Cumplimiento OK: '" + intent.subject() + "' agregado");
                }
                case RENAME -> {
                    String newName = intent.subject().contains("->")
                        ? intent.subject().split("->")[1].trim()
                        : intent.subject();
                    if (!newContent.contains(newName)) {
                        String message = String.format(
                            "[Kill Switch] BLOQUEADO: Cumplimiento fallido en '%s'. " +
                            "Se solicitó renombrar a '%s' pero no se encontró.",
                            path, newName
                        );

                        System.out.println("[IRONCLAD-GUARD] " + message);

                        throw new SafetyException(
                            message,
                            SafetyException.SafetyErrorCode.COMPLIANCE_ERROR
                        );
                    }
                }
                default -> {
                }
            }
        }

        System.out.println("[IRONCLAD-GUARD] Anillo 3 OK (Jaccard: " +
            String.format("%.1f%%", similarity * 100) + ", Intent: " + intent.type() + ")");
    }

    private double jaccardSimilarity(String a, String b) {
        Set<String> tokensA = tokenize(a);
        Set<String> tokensB = tokenize(b);

        if (tokensA.isEmpty() && tokensB.isEmpty()) {
            return 1.0;
        }
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);

        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);

        return (double) intersection.size() / union.size();
    }

    private Set<String> tokenize(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }

        Set<String> tokens = new HashSet<>();
        String[] words = content.toLowerCase()
            .replaceAll("[^a-zA-Z0-9_]", " ")
            .split("\\s+");

        for (String word : words) {
            if (word.length() > 2) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    public void validate(String path, String originalContent, String newContent) {
        validate(path, originalContent, newContent, null);
    }

    public ContentIntegrityPolicy getPolicy() {
        return policy;
    }
}
