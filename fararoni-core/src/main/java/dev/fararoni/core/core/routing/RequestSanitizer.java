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
package dev.fararoni.core.core.routing;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class RequestSanitizer {
    private RequestSanitizer() {
    }

    private static final int MAX_SOLICITUD_LENGTH = 200;

    public static String extractSolicitud(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }

        String[] lineas = prompt.split("\\R");
        String primeraLinea = lineas[0].trim();

        if (primeraLinea.isEmpty()) {
            return "";
        }

        int separatorIndex = findIntentSeparator(primeraLinea);

        String resultado;
        if (separatorIndex != -1) {
            resultado = primeraLinea.substring(0, separatorIndex).trim();
        } else {
            resultado = extractFirstSentenceOrClause(primeraLinea);
        }

        resultado = sanitize(resultado);

        return resultado;
    }

    public static String extractPath(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }

        String[] lineas = prompt.split("\\R");
        String primeraLinea = lineas[0].trim();

        int separatorIndex = findIntentSeparator(primeraLinea);

        if (separatorIndex != -1 && separatorIndex + 1 < primeraLinea.length()) {
            String afterSeparator = primeraLinea.substring(separatorIndex + 1).trim();
            if (looksLikePath(afterSeparator)) {
                return afterSeparator;
            }
        }

        return findPathInText(prompt);
    }

    public static boolean containsPath(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        return prompt.contains("/") || prompt.contains("\\") ||
               prompt.contains(".java") || prompt.contains(".py") ||
               prompt.contains(".js") || prompt.contains(".ts");
    }

    private static int findIntentSeparator(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ':') {
                if (i + 1 < text.length()) {
                    char nextChar = text.charAt(i + 1);
                    if (nextChar == '/' || nextChar == '\\') {
                        continue;
                    }
                }

                if (i == 1 && Character.isLetter(text.charAt(0))) {
                    continue;
                }

                return i;
            }
        }
        return -1;
    }

    private static String extractFirstSentenceOrClause(String text) {
        int dotIndex = text.indexOf('.');
        if (dotIndex > 0 && dotIndex < text.length() - 1) {
            char afterDot = text.charAt(dotIndex + 1);
            if (!Character.isLetter(afterDot)) {
                return text.substring(0, dotIndex).trim();
            }
        }

        int slashIndex = Math.min(
            text.indexOf('/') == -1 ? Integer.MAX_VALUE : text.indexOf('/'),
            text.indexOf('\\') == -1 ? Integer.MAX_VALUE : text.indexOf('\\')
        );

        if (slashIndex != Integer.MAX_VALUE && slashIndex > 10) {
            return text.substring(0, slashIndex).trim();
        }

        return text;
    }

    private static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String cleaned = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");

        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        cleaned = cleaned.toLowerCase();

        if (cleaned.length() > MAX_SOLICITUD_LENGTH) {
            cleaned = cleaned.substring(0, MAX_SOLICITUD_LENGTH);
        }

        return cleaned;
    }

    private static boolean looksLikePath(String text) {
        if (text == null || text.length() < 3) {
            return false;
        }

        return text.contains("/") ||
               text.contains("\\") ||
               text.startsWith("src") ||
               text.startsWith("./") ||
               text.startsWith("../") ||
               text.matches(".*\\.[a-zA-Z]{1,5}$");
    }

    private static String findPathInText(String text) {
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (looksLikePath(word)) {
                return word;
            }
        }
        return "";
    }
}
