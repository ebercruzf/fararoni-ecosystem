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
package dev.fararoni.core.core.mission.engine;

import dev.fararoni.core.core.utils.MultiFileParser;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public final class FileIntentCollector {
    private static final Logger LOG = Logger.getLogger(FileIntentCollector.class.getName());

    public List<FileIntent> collect(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            LOG.warning("Contenido vacio, no hay archivos que colectar");
            return List.of();
        }

        if (!MultiFileParser.isMultiFile(rawContent)) {
            LOG.info("Contenido no tiene marcadores >>>FILE:");
            return List.of();
        }

        Map<String, String> files = MultiFileParser.parse(rawContent);

        if (files.isEmpty()) {
            LOG.warning("Parser no extrajo archivos del contenido");
            return List.of();
        }

        List<FileIntent> intents = files.entrySet().stream()
            .map(entry -> FileIntent.pending(entry.getKey(), entry.getValue()))
            .toList();

        LOG.info(String.format(
            "COLECCION CREADA: %d archivos detectados en Blueprint",
            intents.size()
        ));
        System.out.println("[BLUEPRINT] ════════════════════════════════════════════════════════");
        System.out.println("[BLUEPRINT] PASO B - COLECCIONADOR DE ARCHIVOS");
        System.out.println("[BLUEPRINT] ════════════════════════════════════════════════════════");
        System.out.println("[BLUEPRINT]    Archivos detectados: " + intents.size());

        int idx = 1;
        for (FileIntent intent : intents) {
            String shortPath = intent.path().length() > 60
                ? "..." + intent.path().substring(intent.path().length() - 57)
                : intent.path();
            System.out.printf("[BLUEPRINT]    [%d] %s (%d bytes)%n",
                idx++, shortPath, intent.content().length());
        }
        System.out.println("[BLUEPRINT] ════════════════════════════════════════════════════════");

        return intents;
    }

    public boolean hasFiles(String rawContent) {
        return rawContent != null && MultiFileParser.isMultiFile(rawContent);
    }
}
