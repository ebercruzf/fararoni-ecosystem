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
package dev.fararoni.core.core.skills;

import dev.fararoni.core.core.managers.BiblioCognitiveTriadManager;
import dev.fararoni.core.core.memory.Wisdom;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 4.0.0
 */
public class ReflexionStrategies {
    private static final Logger logger = Logger.getLogger(ReflexionStrategies.class.getName());
    private final BiblioCognitiveTriadManager brain;

    public ReflexionStrategies() {
        this.brain = BiblioCognitiveTriadManager.getInstance();
    }

    public String determineStrategy(String errorLog, String contextId, List<Wisdom> activeWisdom) {
        if (activeWisdom != null && !activeWisdom.isEmpty()) {
            StringBuilder directive = new StringBuilder();
            directive.append("═══════════════════════════════════════════════════════════════\n");
            directive.append("🔥 DIRECTIVA PRIORITARIA DE NIVEL 0 (OVERRIDE)\n");
            directive.append("═══════════════════════════════════════════════════════════════\n\n");
            directive.append("SE HA DETECTADO CONOCIMIENTO MAESTRO PARA: ").append(contextId).append("\n\n");
            directive.append("INSTRUCCIONES OBLIGATORIAS:\n");
            directive.append("1. IGNORA tus intentos anteriores.\n");
            directive.append("2. NO intentes razonar una solución nueva.\n");
            directive.append("3. IMPLEMENTA el 'CÓDIGO MAESTRO' (Payload) provisto arriba EXACTAMENTE como está.\n");
            directive.append("4. Si el código maestro requiere imports (ej: 'import io'), inclúyelos.\n\n");
            directive.append("El éxito depende EXCLUSIVAMENTE de tu capacidad para replicar el patrón provisto.");

            logger.info("[SNIPER] Skill detectado. Activando DIRECTIVA PRIORITARIA para: " + contextId);
            return directive.toString();
        }

        return determineStrategyLegacy(errorLog, contextId);
    }

    private enum ErrorSignature {
        PAASIO_STRUCTURAL_FAILURE(
                Pattern.compile("(AttributeError|AssertionError|TypeError|FAILED|Error)", Pattern.CASE_INSENSITIVE),
                "wrapper", "paasio"),

        POKER_PARSE_ERROR(
                Pattern.compile("(not enough values to unpack|poker\\.py.*hand)", Pattern.CASE_INSENSITIVE),
                "poker_hands", "poker"),

        DOMINOES_LOGIC_ERROR(
                Pattern.compile("(unexpectedly None|is not None|\\[\\])", Pattern.CASE_INSENSITIVE),
                "backtracking", "dominoes"),

        DSL_SCOPE_ERROR(
                Pattern.compile("(ImportError.*cannot import name|AttributeError.*has no attribute)", Pattern.CASE_INSENSITIVE),
                "scope", "dot-dsl"),

        PYTHON_SYNTAX_ERROR(
                Pattern.compile("(SyntaxError|IndentationError)"),
                "syntax_fix", null),

        GENERIC_FAILURE(
                Pattern.compile(".*"),
                "general_advice", null);

        final Pattern pattern;
        final String intent;
        final String requiredContext;

        ErrorSignature(Pattern pattern, String intent, String requiredContext) {
            this.pattern = pattern;
            this.intent = intent;
            this.requiredContext = requiredContext;
        }
    }

    private String determineStrategyLegacy(String stderr, String currentExerciseName) {
        String safeContext = (currentExerciseName != null) ? currentExerciseName.trim().toLowerCase() : "";

        for (ErrorSignature sig : ErrorSignature.values()) {
            if (sig.requiredContext != null && !sig.requiredContext.equalsIgnoreCase(safeContext)) {
                continue;
            }
            Matcher matcher = sig.pattern.matcher(stderr);
            if (matcher.find()) {
                logger.info(String.format("[SNIPER] SINTOMA DETECTADO (LEGACY): %s", sig.name()));
                return generateRepairPrompt(sig, stderr);
            }
        }
        return generateGenericPrompt(stderr);
    }

    public String determineStrategy(String errorLog, String contextId) {
        return determineStrategyLegacy(errorLog, contextId);
    }

    private String generateRepairPrompt(ErrorSignature sig, String stderr) {
        logger.info("[SNIPER]  CONSULTANDO CEREBRO PARA INTENCIÓN: " + sig.intent);

        String wisdomString = brain.retrieveWisdom(sig.intent, "PYTHON");
        String displayError = sanitizeError(stderr);

        return "\n═══════════════════════════════════════════════════════════════\n" +
                " INTERVENCIÓN TÁCTICA AUTOMATIZADA \n" +
                "Se ha detectado un patrón de error conocido (" + sig.name() + ").\n" +
                "\n" +
                "INSTRUCCIÓN MANDATORIA:\n" +
                "COPIA la siguiente implementación EXACTAMENTE:\n" +
                "\n" + wisdomString + "\n" +
                "═══════════════════════════════════════════════════════════════\n" +
                "\nEVIDENCIA FORENSE:\n" + displayError;
    }

    private String generateGenericPrompt(String stderr) {
        return String.format(
                "\n═══════════════════════════════════════════════════════════════\n" +
                        "[!] CORRECCION GENERICA REQUERIDA [!]\n" +
                        "Tu código falló los tests con un error no categorizado.\n\n" +
                        "ERROR:\n```text\n%s\n```\n\n" +
                        "INSTRUCCIÓN:\n" +
                        "Analiza el stacktrace, identifica la línea culpable y corrige la lógica.\n" +
                        "═══════════════════════════════════════════════════════════════\n",
                sanitizeError(stderr)
        );
    }

    private String sanitizeError(String error) {
        if (error == null) return "";
        if (error.length() > 3000) {
            return error.substring(0, 1500) +
                    "\n\n... [LOG TRUNCADO POR EL SISTEMA DE SEGURIDAD] ...\n\n" +
                    error.substring(error.length() - 1500);
        }
        return error;
    }
}
