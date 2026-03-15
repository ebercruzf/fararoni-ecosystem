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
package dev.fararoni.core.core.utils;

import dev.fararoni.core.core.mission.engine.FileIntent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Gatherers;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class MultiFileParser {
    private static final Logger LOG = Logger.getLogger(MultiFileParser.class.getName());

    private static final Pattern FILE_MARKER = Pattern.compile("(?m)^>>>FILE:\\s*(.+)$");

    private static final Pattern DEGENERATE_PATTERN = Pattern.compile("(?m)^FILE:/PLAN:");

    public static boolean isDegenerateFormat(String response) {
        if (response == null) return false;
        Matcher matcher = DEGENERATE_PATTERN.matcher(response);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count >= 3) return true;
        }
        return false;
    }

    public static boolean isMultiFile(String content) {
        return content != null && content.contains(">>>FILE:");
    }

    public static Map<String, String> parse(String rawContent) {
        if (isDegenerateFormat(rawContent)) {
            throw new IllegalStateException(
                "[MULTIFILE] Formato degenerativo detectado. El modelo está alucinando en bucle (FILE:/PLAN: x3+). " +
                "Causa probable: modelo 1.5B intentando generar código."
            );
        }

        Map<String, String> files = new LinkedHashMap<>();

        if (rawContent == null || rawContent.isBlank()) {
            return files;
        }

        Matcher matcher = FILE_MARKER.matcher(rawContent);
        int lastEnd = 0;
        String currentPath = null;

        while (matcher.find()) {
            if (currentPath != null) {
                String code = rawContent.substring(lastEnd, matcher.start()).trim();
                files.put(currentPath, cleanCodeInternal(code));
            }

            currentPath = matcher.group(1).trim();

            if (isPlaceholder(currentPath)) {
                LOG.warning("[PARSER-GUARD] Placeholder neutralizado: " + currentPath);
                currentPath = null;
                lastEnd = matcher.end();
                continue;
            }

            lastEnd = matcher.end();
        }

        if (currentPath != null && lastEnd < rawContent.length()) {
            String code = rawContent.substring(lastEnd).trim();
            files.put(currentPath, cleanCodeInternal(code));
        }

        return files;
    }

    public static String cleanCode(String content) {
        return cleanCodeInternal(content);
    }

    private static String cleanCodeInternal(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }

        String result = code.trim();

        int blockStart = result.indexOf("```");
        if (blockStart >= 0) {
            int codeStart = result.indexOf('\n', blockStart);
            if (codeStart > 0) {
                codeStart++;

                int blockEnd = result.indexOf("```", codeStart);
                if (blockEnd > codeStart) {
                    result = result.substring(codeStart, blockEnd).trim();
                } else {
                    result = result.substring(codeStart).trim();
                }
            } else {
                result = result.substring(0, blockStart).trim();
            }
        }

        if (result.startsWith("```")) {
            int firstNewline = result.indexOf('\n');
            if (firstNewline > 0) {
                result = result.substring(firstNewline + 1);
            } else {
                result = result.substring(3);
            }
        }

        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }

        result = removeStrayBackticks(result);

        return result.trim();
    }

    private static String removeStrayBackticks(String code) {
        if (code == null || !code.contains("`")) {
            return code;
        }

        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean inChar = false;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);

            if (c == '"' && !inChar) {
                inString = !inString;
            } else if (c == '\'' && !inString) {
                inChar = !inChar;
            }

            if (c == '`' && !inString && !inChar) {
                if (i + 2 < code.length() && code.charAt(i + 1) == '`' && code.charAt(i + 2) == '`') {
                    i += 2;
                    continue;
                }
                continue;
            }

            result.append(c);
        }

        return result.toString();
    }

    public static int countFiles(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }

        int count = 0;
        Matcher matcher = FILE_MARKER.matcher(content);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static boolean isPlaceholder(String path) {
        if (path == null || path.isBlank()) {
            return true;
        }

        if (path.contains("<") || path.contains(">")) {
            LOG.warning("[PLACEHOLDER] XML-style: " + path);
            return true;
        }

        String lower = path.toLowerCase();
        if (lower.contains("ruta_del_archivo") || lower.contains("contenido") ||
            lower.contains("nombre_archivo") || lower.contains("ruta_archivo")) {
            LOG.warning("[PLACEHOLDER] Placeholder literal: " + path);
            return true;
        }

        if (path.equals("...") || path.startsWith("...") || path.endsWith("...")) {
            LOG.warning("[PLACEHOLDER] Elipsis: " + path);
            return true;
        }

        if (path.equals("java") || path.equals("python") || path.equals("javascript") ||
            path.equals("typescript") || path.equals("kotlin") || path.equals("xml")) {
            LOG.warning("[PLACEHOLDER] Nombre de lenguaje suelto: " + path);
            return true;
        }

        if (path.endsWith("/")) {
            LOG.warning("[PLACEHOLDER] Ruta de directorio: " + path);
            return true;
        }

        if (!path.contains("/") && !path.contains(".")) {
            LOG.warning("[PLACEHOLDER] Ruta sin estructura: " + path);
            return true;
        }

        return false;
    }

    private static final List<String> FORBIDDEN_ENDINGS = List.of(
        "/java", "/src", "/main", "/test", "/resources"
    );

    public static boolean isValidPath(String path, boolean isNewProject) {
        if (path == null || path.isBlank()) {
            LOG.warning("[SECURITY] Ruta vacia o nula bloqueada");
            return false;
        }

        if (path.contains("<") || path.contains(">") || path.startsWith("...")) {
            LOG.warning("[SECURITY] Ruta placeholder bloqueada: " + path);
            return false;
        }

        if (isNewProject) {
            String[] parts = path.split("/");
            if (parts.length < 3) {
                LOG.severe("[IO-GUARD] Proyecto nuevo requiere carpeta raiz y estructura src: " + path);
                return false;
            }
        }

        for (String ending : FORBIDDEN_ENDINGS) {
            if (path.endsWith(ending)) {
                LOG.severe("[IO-GUARD] Intento de crear carpeta como archivo: " + path);
                return false;
            }
        }

        return true;
    }

    private static final int DEFAULT_BATCH_SIZE = 4;

    public static List<FileIntent> parseToIntents(String rawContent) {
        if (isDegenerateFormat(rawContent)) {
            throw new IllegalStateException(
                "[MULTIFILE] Formato degenerativo detectado. El modelo está alucinando en bucle."
            );
        }

        List<FileIntent> intents = new ArrayList<>();

        if (rawContent == null || rawContent.isBlank()) {
            return intents;
        }

        Matcher matcher = FILE_MARKER.matcher(rawContent);
        int lastEnd = 0;
        String currentPath = null;

        while (matcher.find()) {
            if (currentPath != null) {
                String code = rawContent.substring(lastEnd, matcher.start()).trim();
                String cleanedCode = cleanCodeInternal(code);
                if (!cleanedCode.isBlank()) {
                    intents.add(FileIntent.pending(currentPath, cleanedCode));
                }
            }

            currentPath = matcher.group(1).trim();

            if (isPlaceholder(currentPath)) {
                LOG.warning("Placeholder neutralizado: " + currentPath);
                currentPath = null;
                lastEnd = matcher.end();
                continue;
            }

            lastEnd = matcher.end();
        }

        if (currentPath != null && lastEnd < rawContent.length()) {
            String code = rawContent.substring(lastEnd).trim();
            String cleanedCode = cleanCodeInternal(code);
            if (!cleanedCode.isBlank()) {
                intents.add(FileIntent.pending(currentPath, cleanedCode));
            }
        }

        LOG.info(() -> "Parseados " + intents.size() + " FileIntents");
        return intents;
    }

    @SuppressWarnings("preview")
    public static void processInBatches(
            List<FileIntent> intents,
            int batchSize,
            Consumer<FileIntent> processor) {
        if (intents == null || intents.isEmpty()) {
            return;
        }

        LOG.info(() -> String.format(
            "Procesando %d intents en batches de %d (Virtual Threads)",
            intents.size(), batchSize));

        var batches = intents.stream()
            .gather(Gatherers.windowFixed(batchSize))
            .toList();

        int batchNum = 0;
        for (List<FileIntent> batch : batches) {
            batchNum++;
            final int currentBatch = batchNum;

            LOG.fine(() -> String.format(
                "Batch %d/%d: %d archivos",
                currentBatch, batches.size(), batch.size()));

            try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
                for (FileIntent intent : batch) {
                    scope.fork(() -> {
                        processor.accept(intent);
                        return intent;
                    });
                }

                scope.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.severe("Batch interrumpido: " + e.getMessage());
                break;
            } catch (Exception e) {
                LOG.severe("Error en batch: " + e.getMessage());
            }
        }

        LOG.info("Procesamiento completado: " + intents.size() + " archivos");
    }

    public static void processInBatches(List<FileIntent> intents, Consumer<FileIntent> processor) {
        processInBatches(intents, DEFAULT_BATCH_SIZE, processor);
    }

    public static int parseAndProcessParallel(String rawContent, Consumer<FileIntent> processor) {
        List<FileIntent> intents = parseToIntents(rawContent);
        processInBatches(intents, processor);
        return intents.size();
    }
}
