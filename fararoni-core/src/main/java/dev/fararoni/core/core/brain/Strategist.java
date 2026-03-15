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
package dev.fararoni.core.core.brain;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class Strategist {
    private static final Logger LOG = Logger.getLogger(Strategist.class.getName());

    public enum StrategyType {
        HOTFIX("Parche Rapido", 0.8, "🩹"),

        REFACTOR("Reingenieria", 0.4, "[RFT]"),

        EXPLAIN("Solo Explicar", 1.0, "📚");

        private final String label;
        private final double baseSuccessProbability;
        private final String emoji;

        StrategyType(String label, double probability, String emoji) {
            this.label = label;
            this.baseSuccessProbability = probability;
            this.emoji = emoji;
        }

        public String getLabel() {
            return label;
        }

        public double getBaseSuccessProbability() {
            return baseSuccessProbability;
        }

        public String getEmoji() {
            return emoji;
        }

        @Override
        public String toString() {
            return emoji + " " + label;
        }
    }

    private static final int REFACTOR_SAFE_THRESHOLD = 1000;

    private static final String[] EXPLAIN_KEYWORDS = {
        "explain", "why", "what", "how does", "que es", "por que",
        "como funciona", "entender", "understand", "debug"
    };

    private static final String[] REFACTOR_KEYWORDS = {
        "refactor", "redesign", "architecture", "structure", "clean",
        "rewrite", "redisenar", "reestructurar", "limpiar"
    };

    private static final int COMPLEXITY_THRESHOLD = 10;

    public StrategyType classifyIntent(String userQuery, String errorLog) {
        String input = ((userQuery != null ? userQuery : "") + " " +
                       (errorLog != null ? errorLog : "")).toLowerCase();

        for (String keyword : EXPLAIN_KEYWORDS) {
            if (input.contains(keyword)) {
                LOG.info("[BRAIN] Intencion detectada: EXPLAIN (keyword: " + keyword + ")");
                return StrategyType.EXPLAIN;
            }
        }

        for (String keyword : REFACTOR_KEYWORDS) {
            if (input.contains(keyword)) {
                LOG.info("[BRAIN] Intencion detectada: REFACTOR (keyword: " + keyword + ")");
                return StrategyType.REFACTOR;
            }
        }

        LOG.info("[BRAIN] Intencion detectada: HOTFIX (default)");
        return StrategyType.HOTFIX;
    }

    public StrategyType selectBestPath(String fileContent) {
        Objects.requireNonNull(fileContent, "fileContent no puede ser null");

        int complexity = fileContent.length();

        double refactorScore = StrategyType.REFACTOR.baseSuccessProbability *
                              ((double) REFACTOR_SAFE_THRESHOLD / Math.max(complexity, 1));
        double hotfixScore = StrategyType.HOTFIX.baseSuccessProbability;

        LOG.info(() -> String.format(
            "[BRAIN] MCTS-Lite: complejidad=%d, refactorScore=%.3f, hotfixScore=%.3f",
            complexity, refactorScore, hotfixScore
        ));

        StrategyType selected = refactorScore > hotfixScore
            ? StrategyType.REFACTOR
            : StrategyType.HOTFIX;

        LOG.info("[BRAIN] Estrategia seleccionada: " + selected);
        return selected;
    }

    public StrategyType evaluateMultipleFiles(java.util.Map<String, String> fileContents) {
        if (fileContents == null || fileContents.isEmpty()) {
            return StrategyType.EXPLAIN;
        }

        int totalComplexity = fileContents.values().stream()
            .mapToInt(String::length)
            .sum();

        int fileCount = fileContents.size();

        if (fileCount > 3 || totalComplexity > 5000) {
            LOG.info("[BRAIN] Multi-file analysis: " + fileCount +
                    " archivos, " + totalComplexity + " chars -> HOTFIX");
            return StrategyType.HOTFIX;
        }

        if (fileCount == 1 && totalComplexity < REFACTOR_SAFE_THRESHOLD) {
            LOG.info("[BRAIN] Multi-file analysis: archivo unico pequeno -> REFACTOR");
            return StrategyType.REFACTOR;
        }

        return StrategyType.HOTFIX;
    }

    public String justifyStrategy(StrategyType strategy, int complexity) {
        return switch (strategy) {
            case HOTFIX -> String.format(
                "Se selecciono %s porque el archivo tiene %d caracteres. " +
                "Un parche rapido minimiza el riesgo de introducir nuevos bugs.",
                strategy, complexity
            );
            case REFACTOR -> String.format(
                "Se selecciono %s porque el archivo tiene solo %d caracteres. " +
                "Es lo suficientemente pequeno para una reingenieria segura.",
                strategy, complexity
            );
            case EXPLAIN -> "Se selecciono " + strategy +
                " porque la intencion detectada es obtener una explicacion, no modificar codigo.";
        };
    }

    public int estimateCyclomaticComplexity(String code) {
        if (code == null || code.isEmpty()) {
            return 1;
        }

        int complexity = 1;

        String[] controlKeywords = {"if", "else", "for", "while", "switch", "case", "try", "catch"};
        for (String keyword : controlKeywords) {
            complexity += countOccurrences(code, "\\b" + keyword + "\\b");
        }

        complexity += countOccurrences(code, "&&");
        complexity += countOccurrences(code, "\\|\\|");

        int deepNestingPenalty = calculateDeepNestingPenalty(code);
        complexity += deepNestingPenalty;

        final int finalComplexity = complexity;
        final int finalPenalty = deepNestingPenalty;
        LOG.info(() -> String.format(
            "[BRAIN] Complejidad ciclomatica estimada: %d (nesting penalty: %d)",
            finalComplexity, finalPenalty
        ));

        return complexity;
    }

    private int countOccurrences(String text, String pattern) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    private int calculateDeepNestingPenalty(String code) {
        int penalty = 0;
        String[] lines = code.split("\\n");

        for (String line : lines) {
            int indent = 0;
            for (char c : line.toCharArray()) {
                if (c == ' ') {
                    indent++;
                } else if (c == '\t') {
                    indent += 4;
                } else {
                    break;
                }
            }

            if (indent > 16) {
                penalty++;
            }
            if (indent > 24) {
                penalty++;
            }
        }

        return penalty;
    }

    public StrategyType selectBestPathWithComplexity(String fileContent) {
        Objects.requireNonNull(fileContent, "fileContent no puede ser null");

        int complexity = estimateCyclomaticComplexity(fileContent);
        int length = fileContent.length();

        if (complexity > COMPLEXITY_THRESHOLD) {
            LOG.info("[BRAIN] Complejidad alta (" + complexity +
                    ") -> HOTFIX (minimizar riesgo)");
            return StrategyType.HOTFIX;
        }

        if (length < REFACTOR_SAFE_THRESHOLD && complexity <= COMPLEXITY_THRESHOLD) {
            LOG.info("[BRAIN] Archivo pequeno (" + length +
                    " chars) y baja complejidad (" + complexity + ") -> REFACTOR");
            return StrategyType.REFACTOR;
        }

        return selectBestPath(fileContent);
    }
}
