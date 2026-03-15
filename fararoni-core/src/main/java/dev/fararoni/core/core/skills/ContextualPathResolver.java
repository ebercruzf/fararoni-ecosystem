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

import dev.fararoni.core.core.index.IndexStore;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0 (FASE 28.2.8.4 - Heuristic Path Resolution)
 */
public class ContextualPathResolver {
    private static final Logger logger = Logger.getLogger(ContextualPathResolver.class.getName());

    public static final double CONFIDENCE_THRESHOLD = 0.30;

    private static final Set<String> NOISE_TOKENS = Set.of(
        "src", "main", "java", "test", "resources",
        "com", "org", "net", "io",
        "example", "app", "demo"
    );

    public static final double FUZZY_NAME_THRESHOLD = 0.75;

    public Path resolve(String requestedPathStr, List<Path> allProjectFiles)
            throws AmbiguousPathException {
        Path requestedPath = Path.of(requestedPathStr);
        String requestedFileName = requestedPath.getFileName().toString();

        logger.fine("[PATH-RESOLVER] Resolviendo: " + requestedPathStr);

        for (Path projectFile : allProjectFiles) {
            if (projectFile.endsWith(requestedPath) || projectFile.toString().endsWith(requestedPathStr)) {
                logger.fine("[PATH-RESOLVER] Coincidencia exacta: " + projectFile);
                return projectFile;
            }
        }

        List<Path> candidates = allProjectFiles.stream()
                .filter(p -> p.getFileName().toString().equals(requestedFileName))
                .collect(Collectors.toList());

        logger.info("[PATH-RESOLVER] Candidatos exactos: " + candidates.size() +
                    " para archivo: " + requestedFileName);

        if (candidates.isEmpty()) {
            System.out.println("[PATH-RESOLVER] Sin coincidencia exacta. Iniciando fuzzy matching...");

            String requestedNameLower = requestedFileName.toLowerCase();

            for (Path projectFile : allProjectFiles) {
                String candidateName = projectFile.getFileName().toString();
                double similarity = calculateStringSimilarity(requestedNameLower, candidateName.toLowerCase());

                if (similarity >= FUZZY_NAME_THRESHOLD) {
                    candidates.add(projectFile);
                    System.out.println("[FUZZY-MATCH] " + candidateName +
                                      " (similitud: " + String.format("%.0f%%", similarity * 100) + ")");
                }
            }

            if (!candidates.isEmpty()) {
                System.out.println("[PATH-RESOLVER] Encontrados " + candidates.size() +
                                  " archivos similares via fuzzy matching");
            }
        }

        if (candidates.isEmpty()) {
            throw new RuntimeException(
                "Archivo no encontrado: " + requestedPathStr +
                ". Verifica que el nombre del archivo sea correcto.");
        }

        if (candidates.size() == 1) {
            Path resolved = candidates.get(0);
            System.out.println("[AUTO-FIX] Ruta corregida: " + requestedPathStr + " → " + resolved);
            logger.info("[PATH-RESOLVER] Auto-fix (único candidato): " + resolved);
            return resolved;
        }

        return selectBestCandidate(requestedPath, candidates);
    }

    public Path resolve(String requestedPathStr, Path workingDirectory,
                       IndexStore indexStore)
            throws AmbiguousPathException {
        List<Path> allFiles = indexStore.getAllJavaFiles(workingDirectory);
        return resolve(requestedPathStr, allFiles);
    }

    private Path selectBestCandidate(Path requested, List<Path> candidates)
            throws AmbiguousPathException {
        Map<Path, Double> scores = new HashMap<>();
        Set<String> requestedTokens = tokenizePath(requested);

        logger.fine("[PATH-RESOLVER] Tokens de ruta solicitada: " + requestedTokens);

        for (Path candidate : candidates) {
            Set<String> candidateTokens = tokenizePath(candidate);

            long matchCount = requestedTokens.stream()
                    .filter(candidateTokens::contains)
                    .count();

            double score = (double) matchCount /
                    Math.max(requestedTokens.size(), candidateTokens.size());

            scores.put(candidate, score);

            logger.fine("[PATH-RESOLVER] Candidato: " + candidate +
                       " | Tokens: " + candidateTokens +
                       " | Matches: " + matchCount +
                       " | Score: " + String.format("%.2f", score));
        }

        List<Map.Entry<Path, Double>> ranked = scores.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(Collectors.toList());

        Map.Entry<Path, Double> winner = ranked.get(0);
        Map.Entry<Path, Double> runnerUp = ranked.get(1);

        double scoreDiff = winner.getValue() - runnerUp.getValue();

        System.out.println("[PATH-RESOLVER] Análisis de similitud:");
        System.out.println("  - Ganador:  " + winner.getKey() + " (score: " +
                          String.format("%.2f", winner.getValue()) + ")");
        System.out.println("  - Segundo:  " + runnerUp.getKey() + " (score: " +
                          String.format("%.2f", runnerUp.getValue()) + ")");
        System.out.println("  - Diferencia: " + String.format("%.2f", scoreDiff) +
                          " (umbral: " + CONFIDENCE_THRESHOLD + ")");

        if (scoreDiff > CONFIDENCE_THRESHOLD) {
            System.out.println("[AUTO-FIX] Inferencia Contextual Exitosa: " + winner.getKey());
            logger.info("[PATH-RESOLVER] Ganador claro (diff=" +
                       String.format("%.2f", scoreDiff) + "): " + winner.getKey());
            return winner.getKey();
        }

        logger.warning("[PATH-RESOLVER] Ambigüedad detectada. Solicitando intervención.");
        throw new AmbiguousPathException(candidates, requested.toString());
    }

    private Set<String> tokenizePath(Path path) {
        return Arrays.stream(path.toString().split("[/\\\\]"))
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .filter(s -> !NOISE_TOKENS.contains(s))
                .collect(Collectors.toSet());
    }

    public static boolean looksLikeExamplePackage(String path) {
        return path != null &&
               (path.contains("com/example") || path.contains("com\\example"));
    }

    public double getConfidenceThreshold() {
        return CONFIDENCE_THRESHOLD;
    }

    private static final Set<String> IGNORED_TOKENS = Set.of(
        "src", "main", "java", "test", "resources",
        "com", "org", "net", "example", ".", ".."
    );

    public double calculateStringSimilarity(String strA, String strB) {
        if (strA == null || strB == null) return 0.0;

        String sA = strA.toLowerCase();
        String sB = strB.toLowerCase();

        if (sA.endsWith(".java")) sA = sA.substring(0, sA.length() - 5);
        if (sB.endsWith(".java")) sB = sB.substring(0, sB.length() - 5);

        if (sA.equals(sB)) return 1.0;

        int distance = levenshteinDistance(sA, sB);

        int maxLength = Math.max(sA.length(), sB.length());
        if (maxLength == 0) return 1.0;

        double similarity = 1.0 - ((double) distance / maxLength);

        return Math.max(0.0, similarity);
    }

    private int levenshteinDistance(String s1, String s2) {
        if (s1.equals(s2)) return 0;
        if (s1.length() == 0) return s2.length();
        if (s2.length() == 0) return s1.length();

        int lenDiff = Math.abs(s1.length() - s2.length());
        if (lenDiff > 5) return 999;

        int[] costs = new int[s2.length() + 1];

        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;

            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(
                    1 + Math.min(costs[j], costs[j - 1]),
                    s1.charAt(i - 1) == s2.charAt(j - 1)
                        ? nw
                        : nw + 1
                );

                nw = costs[j];
                costs[j] = cj;
            }
        }

        return costs[s2.length()];
    }

    private Set<String> tokenizeForSimilarity(String pathStr) {
        return Arrays.stream(pathStr.split("/"))
            .filter(token -> !token.isBlank())
            .filter(token -> !IGNORED_TOKENS.contains(token))
            .collect(Collectors.toSet());
    }

    private String extractFileName(String pathStr) {
        int lastSlash = pathStr.lastIndexOf('/');
        return lastSlash >= 0 ? pathStr.substring(lastSlash + 1) : pathStr;
    }
}
