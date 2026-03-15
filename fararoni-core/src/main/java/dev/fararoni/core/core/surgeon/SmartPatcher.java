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
package dev.fararoni.core.core.surgeon;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SmartPatcher {
    private static final Logger LOG = Logger.getLogger(SmartPatcher.class.getName());

    private static final double MAX_FUZZY_THRESHOLD = 0.20;

    private static final double MIN_MATCH_SCORE = 0.85;

    private static final double AMBIGUITY_THRESHOLD = 0.10;

    private static final int CONTEXT_ANCHOR_LINES = 3;

    public enum PatchResult {
        EXACT_MATCH,
        NORMALIZED_MATCH,
        FUZZY_MATCH,
        NOT_FOUND
    }

    private PatchResult lastResult = null;

    public String applyPatch(String originalContent, String searchBlock, String replaceBlock) {
        Objects.requireNonNull(originalContent, "originalContent no puede ser null");
        Objects.requireNonNull(searchBlock, "searchBlock no puede ser null");
        Objects.requireNonNull(replaceBlock, "replaceBlock no puede ser null");

        if (originalContent.contains(searchBlock)) {
            LOG.info("[SURGEON] Coincidencia exacta encontrada.");
            lastResult = PatchResult.EXACT_MATCH;
            return originalContent.replace(searchBlock, replaceBlock);
        }

        int lineCount = searchBlock.split("\n").length;
        if (lineCount < 3) {
            LOG.warning("[SURGEON] Bloque pequeno (" + lineCount + " lineas) no encontrado exactamente. Abortando Fuzzy Match por seguridad.");
            lastResult = PatchResult.NOT_FOUND;
            return originalContent;
        }

        String normalizedSource = LevenshteinUtils.normalizeCode(originalContent);
        String normalizedSearch = LevenshteinUtils.normalizeCode(searchBlock);

        if (normalizedSource.contains(normalizedSearch)) {
            LOG.info("[SURGEON] Coincidencia aproximada (Whitespace mismatch). Procediendo con cautela.");
            lastResult = PatchResult.NORMALIZED_MATCH;
            return fuzzyLineReplace(originalContent, searchBlock, replaceBlock);
        }

        String fuzzyResult = fuzzyLineReplace(originalContent, searchBlock, replaceBlock);
        if (!fuzzyResult.equals(originalContent)) {
            LOG.info("[SURGEON] Coincidencia fuzzy encontrada (Levenshtein).");
            lastResult = PatchResult.FUZZY_MATCH;
            return fuzzyResult;
        }

        LOG.severe("[SURGEON] No se encontro el bloque de codigo a reemplazar.");
        lastResult = PatchResult.NOT_FOUND;
        return originalContent;
    }

    private String fuzzyLineReplace(String content, String searchBlock, String replaceBlock) {
        String[] contentLines = content.split("\n", -1);
        String[] searchLines = searchBlock.split("\n", -1);

        if (searchLines.length == 0) return content;

        String firstLine = searchLines[0].trim();
        if (firstLine.isEmpty() && searchLines.length > 1) {
            firstLine = searchLines[1].trim();
        }

        int bestLineIndex = -1;
        int minDistance = Integer.MAX_VALUE;

        for (int i = 0; i < contentLines.length; i++) {
            String targetTrimmed = contentLines[i].trim();
            if (targetTrimmed.isEmpty()) continue;

            int dist = LevenshteinUtils.calculateDistance(firstLine, targetTrimmed);
            int maxAllowedDist = (int) (firstLine.length() * MAX_FUZZY_THRESHOLD);

            if (dist < minDistance && dist <= maxAllowedDist) {
                if (verifyBlockMatch(contentLines, i, searchLines)) {
                    minDistance = dist;
                    bestLineIndex = i;
                }
            }
        }

        if (bestLineIndex != -1) {
            return reconstructWithReplacement(contentLines, bestLineIndex, searchLines.length, replaceBlock);
        }

        return content;
    }

    private boolean verifyBlockMatch(String[] contentLines, int startIndex, String[] searchLines) {
        if (startIndex + searchLines.length > contentLines.length) {
            return false;
        }

        int matchCount = 0;
        for (int i = 0; i < searchLines.length; i++) {
            String contentLine = contentLines[startIndex + i].trim();
            String searchLine = searchLines[i].trim();

            if (contentLine.isEmpty() && searchLine.isEmpty()) {
                matchCount++;
                continue;
            }

            double similarity = LevenshteinUtils.calculateSimilarity(contentLine, searchLine);
            if (similarity >= (1.0 - MAX_FUZZY_THRESHOLD)) {
                matchCount++;
            }
        }

        return (double) matchCount / searchLines.length >= 0.70;
    }

    private String reconstructWithReplacement(String[] contentLines, int startIndex,
                                              int linesToSkip, String replaceBlock) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < startIndex; i++) {
            sb.append(contentLines[i]).append("\n");
        }

        sb.append(replaceBlock);
        if (!replaceBlock.endsWith("\n")) {
            sb.append("\n");
        }

        for (int i = startIndex + linesToSkip; i < contentLines.length; i++) {
            sb.append(contentLines[i]);
            if (i < contentLines.length - 1) {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    public PatchResult getLastResult() {
        return lastResult;
    }

    public boolean wasLastPatchSuccessful() {
        return lastResult != null && lastResult != PatchResult.NOT_FOUND;
    }

    private static class SlidingWindowMatch {
        final int startIndex;
        final double score;

        SlidingWindowMatch(int startIndex, double score) {
            this.startIndex = startIndex;
            this.score = score;
        }
    }

    private int slidingWindowMatch(String[] contentLines, String[] searchLines) {
        if (searchLines.length == 0 || contentLines.length < searchLines.length) {
            return -1;
        }

        java.util.List<SlidingWindowMatch> candidates = new java.util.ArrayList<>();

        for (int i = 0; i <= contentLines.length - searchLines.length; i++) {
            double score = calculateBlockScore(contentLines, i, searchLines);

            if (score >= MIN_MATCH_SCORE) {
                candidates.add(new SlidingWindowMatch(i, score));
            }
        }

        if (candidates.isEmpty()) {
            return -1;
        }

        candidates.sort((a, b) -> Double.compare(b.score, a.score));

        SlidingWindowMatch best = candidates.get(0);

        if (candidates.size() > 1) {
            SlidingWindowMatch second = candidates.get(1);
            if (best.score - second.score < AMBIGUITY_THRESHOLD) {
                LOG.warning("[SURGEON] Ambiguedad detectada: 2 candidatos con scores " +
                           String.format("%.2f y %.2f", best.score, second.score) +
                           ". Abortando para evitar reemplazo incorrecto.");
                return -1;
            }
        }

        LOG.info("[SURGEON] Candidato unico encontrado en linea " + best.startIndex +
                " con score " + String.format("%.2f", best.score));
        return best.startIndex;
    }

    private double calculateBlockScore(String[] contentLines, int startIndex, String[] searchLines) {
        double totalScore = 0.0;
        int validLines = 0;

        for (int i = 0; i < searchLines.length; i++) {
            String contentLine = contentLines[startIndex + i].trim();
            String searchLine = searchLines[i].trim();

            if (contentLine.isEmpty() && searchLine.isEmpty()) {
                totalScore += 1.0;
                validLines++;
                continue;
            }

            if (contentLine.isEmpty() || searchLine.isEmpty()) {
                validLines++;
                continue;
            }

            double similarity = LevenshteinUtils.calculateSimilarity(contentLine, searchLine);
            totalScore += similarity;
            validLines++;
        }

        return validLines > 0 ? totalScore / validLines : 0.0;
    }

    public boolean verifyContextAnchors(String[] contentLines, int matchIndex,
                                        String[] searchLines,
                                        String[] contextAbove, String[] contextBelow) {
        if (contextAbove != null && contextAbove.length > 0) {
            int anchorStart = matchIndex - contextAbove.length;
            if (anchorStart < 0) return false;

            for (int i = 0; i < contextAbove.length; i++) {
                String contentLine = contentLines[anchorStart + i].trim();
                String anchorLine = contextAbove[i].trim();
                double similarity = LevenshteinUtils.calculateSimilarity(contentLine, anchorLine);
                if (similarity < MIN_MATCH_SCORE) {
                    LOG.fine("[SURGEON] Ancla superior no coincide en linea " + (anchorStart + i));
                    return false;
                }
            }
        }

        if (contextBelow != null && contextBelow.length > 0) {
            int anchorStart = matchIndex + searchLines.length;
            if (anchorStart + contextBelow.length > contentLines.length) return false;

            for (int i = 0; i < contextBelow.length; i++) {
                String contentLine = contentLines[anchorStart + i].trim();
                String anchorLine = contextBelow[i].trim();
                double similarity = LevenshteinUtils.calculateSimilarity(contentLine, anchorLine);
                if (similarity < MIN_MATCH_SCORE) {
                    LOG.fine("[SURGEON] Ancla inferior no coincide en linea " + (anchorStart + i));
                    return false;
                }
            }
        }

        return true;
    }

    public String applyPatchWithAmbiguityCheck(String originalContent, String searchBlock, String replaceBlock) {
        Objects.requireNonNull(originalContent, "originalContent no puede ser null");
        Objects.requireNonNull(searchBlock, "searchBlock no puede ser null");
        Objects.requireNonNull(replaceBlock, "replaceBlock no puede ser null");

        if (originalContent.contains(searchBlock)) {
            int firstIndex = originalContent.indexOf(searchBlock);
            int secondIndex = originalContent.indexOf(searchBlock, firstIndex + 1);

            if (secondIndex != -1) {
                LOG.warning("[SURGEON] Multiples ocurrencias exactas encontradas. " +
                           "Reemplazando solo la primera.");
            }

            LOG.info("[SURGEON] Coincidencia exacta encontrada.");
            lastResult = PatchResult.EXACT_MATCH;
            return originalContent.replaceFirst(
                java.util.regex.Pattern.quote(searchBlock),
                java.util.regex.Matcher.quoteReplacement(replaceBlock)
            );
        }

        String[] contentLines = originalContent.split("\n", -1);
        String[] searchLines = searchBlock.split("\n", -1);

        int matchIndex = slidingWindowMatch(contentLines, searchLines);

        if (matchIndex != -1) {
            LOG.info("[SURGEON] Match encontrado via sliding window en linea " + matchIndex);
            lastResult = PatchResult.FUZZY_MATCH;
            return reconstructWithReplacement(contentLines, matchIndex, searchLines.length, replaceBlock);
        }

        LOG.severe("[SURGEON] No se encontro match seguro (ambiguedad o score bajo).");
        lastResult = PatchResult.NOT_FOUND;
        return originalContent;
    }
}
