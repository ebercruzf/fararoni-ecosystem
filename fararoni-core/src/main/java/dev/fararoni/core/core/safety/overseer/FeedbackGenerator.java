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

import dev.fararoni.core.core.safety.overseer.OverseerValidator.ValidationResult;

import java.util.List;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class FeedbackGenerator {
    private static final Logger LOG = Logger.getLogger(FeedbackGenerator.class.getName());

    private static final String LOMBOK_CORRECTION = """
        - Elimina TODAS las anotaciones de Lombok (@Data, @Builder, @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor)
        - Usa Java Records para DTOs inmutables
        - Escribe getters/setters/constructores manualmente si necesitas mutabilidad
        - No incluyas la dependencia de Lombok en pom.xml""";

    private static final String FILE_FORMAT_CORRECTION = """
        - Usa el formato >>>FILE: seguido de la ruta completa
        - No uses bloques de código markdown (```)
        - Cada archivo debe empezar con >>>FILE: y terminar antes del siguiente >>>FILE:""";

    private static final String PACKAGE_CORRECTION = """
        - Incluye una declaración 'package' válida al inicio de cada archivo Java
        - El paquete debe coincidir con la estructura de directorios""";

    public String generateFeedback(ValidationResult result, String filePath, int retryCount) {
        if (result.isValid()) {
            return "";
        }

        StringBuilder feedback = new StringBuilder();

        feedback.append("""
            ═══════════════════════════════════════════════════════════════════════════
            [OVERSEER] CONTENIDO RECHAZADO - CORRECCIÓN REQUERIDA
            ═══════════════════════════════════════════════════════════════════════════

            """);

        if (filePath != null && !filePath.isEmpty()) {
            feedback.append("ARCHIVO AFECTADO: ").append(filePath).append("\n\n");
        }

        feedback.append("VIOLACIONES DETECTADAS:\n");
        int index = 1;
        for (String violation : result.violations()) {
            feedback.append(String.format("%d. %s%n", index++, violation));
        }
        feedback.append("\n");

        feedback.append("INSTRUCCIONES DE CORRECCIÓN:\n");
        feedback.append(generateCorrectionInstructions(result.violations()));
        feedback.append("\n");

        if (retryCount >= 2) {
            feedback.append("""
                [!] ADVERTENCIA: Este es el intento #%d.
                Si continuas violando las reglas, la mision sera cancelada.

                """.formatted(retryCount + 1));
        }

        feedback.append("""
            REGENERA EL CÓDIGO CUMPLIENDO TODAS LAS REGLAS.
            ═══════════════════════════════════════════════════════════════════════════
            """);

        LOG.info("Feedback generado para " + result.violations().size() + " violaciones");

        return feedback.toString();
    }

    public String generateFeedback(ValidationResult result) {
        return generateFeedback(result, null, 0);
    }

    public String tuneSystemPrompt(String originalPrompt, List<String> violations) {
        StringBuilder tuned = new StringBuilder();

        tuned.append("""
            ========================================================================
            [!] ADVERTENCIA CRITICA DEL OVERSEER - REGLAS VIOLADAS REPETIDAMENTE [!]
            ========================================================================

            Las siguientes reglas son OBLIGATORIAS y NO NEGOCIABLES:

            """);

        for (String violation : violations) {
            if (violation.toLowerCase().contains("lombok") || violation.contains("@Data")) {
                tuned.append("[X] LOMBOK ESTA TERMINANTEMENTE PROHIBIDO\n");
                tuned.append("   - NO uses @Data, @Builder, @Getter, @Setter\n");
                tuned.append("   - NO incluyas dependencia de Lombok en pom.xml\n");
                tuned.append("   - USA Java Records para DTOs\n\n");
            }
            if (violation.contains(">>>FILE:") || violation.contains("FALTANTE")) {
                tuned.append("[FILE] FORMATO DE ARCHIVO OBLIGATORIO\n");
                tuned.append("   - CADA archivo DEBE empezar con >>>FILE:\n");
                tuned.append("   - NO uses bloques markdown (```)\n\n");
            }
        }

        tuned.append("========================================================================\n\n");

        tuned.append(originalPrompt);

        LOG.info("System prompt tuneado con " + violations.size() + " advertencias");

        return tuned.toString();
    }

    private String generateCorrectionInstructions(List<String> violations) {
        StringBuilder instructions = new StringBuilder();
        boolean hasLombok = false;
        boolean hasFileFormat = false;
        boolean hasPackage = false;

        for (String violation : violations) {
            String lower = violation.toLowerCase();

            if ((lower.contains("lombok") || lower.contains("@data") ||
                 lower.contains("@builder") || lower.contains("@getter")) && !hasLombok) {
                instructions.append(LOMBOK_CORRECTION).append("\n\n");
                hasLombok = true;
            }

            if ((lower.contains(">>>file:") || lower.contains("markdown") ||
                 lower.contains("```")) && !hasFileFormat) {
                instructions.append(FILE_FORMAT_CORRECTION).append("\n\n");
                hasFileFormat = true;
            }

            if (lower.contains("package") && !hasPackage) {
                instructions.append(PACKAGE_CORRECTION).append("\n\n");
                hasPackage = true;
            }
        }

        if (instructions.isEmpty()) {
            instructions.append("- Revisa las violaciones listadas arriba\n");
            instructions.append("- Corrige el código para cumplir con todas las políticas\n");
        }

        return instructions.toString();
    }
}
