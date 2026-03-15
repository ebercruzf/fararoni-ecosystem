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

import dev.fararoni.core.core.constants.AppDefaults;
import java.nio.file.Path;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0 (FASE 28.2.8.4 - Heuristic Path Resolution)
 */
public class AmbiguousPathException extends Exception {
    private final List<Path> candidates;
    private final String requestedPath;
    private final String fileName;

    public AmbiguousPathException(List<Path> candidates, String requestedPath) {
        super(buildMessage(candidates, requestedPath));
        this.candidates = candidates;
        this.requestedPath = requestedPath;
        this.fileName = extractFileName(requestedPath);
    }

    public List<Path> getCandidates() {
        return List.copyOf(candidates);
    }

    public String getRequestedPath() {
        return requestedPath;
    }

    public String getFileName() {
        return fileName;
    }

    public String toUserFriendlyMessage() {
        StringBuilder msg = new StringBuilder();
        msg.append(AppDefaults.AMBIGUITY_HEADER)
           .append(". Encontré varios archivos '")
           .append(fileName).append("'.\n");
        msg.append("Basado en tu solicitud, no estoy 100% seguro de cuál usar.\n");
        msg.append("Por favor, vuelve a intentar usando una de estas ")
           .append(AppDefaults.AMBIGUITY_INSTRUCTION).append("\n\n");

        for (int i = 0; i < candidates.size(); i++) {
            msg.append(i + 1).append(". ").append(candidates.get(i)).append("\n");
        }

        return msg.toString();
    }

    private static String buildMessage(List<Path> candidates, String requestedPath) {
        return "Ruta ambigua: '" + requestedPath + "'. " +
               "Encontrados " + candidates.size() + " candidatos con scores similares.";
    }

    private static String extractFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
