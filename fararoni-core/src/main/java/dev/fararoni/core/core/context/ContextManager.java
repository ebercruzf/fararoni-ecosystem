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
package dev.fararoni.core.core.context;

import dev.fararoni.core.core.search.TheHound;
import dev.fararoni.core.core.prompt.ContextInjector;
import dev.fararoni.core.core.workspace.WorkspaceInsight;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ContextManager {
    private static final Logger LOG = Logger.getLogger(ContextManager.class.getName());

    private static final Set<String> CODE_EXTENSIONS = Set.of(
        ".java", ".kt", ".kts", ".scala", ".groovy",
        ".py", ".pyw",
        ".js", ".jsx", ".mjs", ".ts", ".tsx",
        ".go", ".rs", ".c", ".cpp", ".h", ".hpp", ".cc",
        ".php", ".rb", ".cs",
        ".md", ".txt", ".rst",
        ".json", ".yaml", ".yml", ".xml", ".toml", ".properties"
    );

    private static final long MAX_FILE_SIZE = 100 * 1024;

    private static final int MAX_CONTENT_LENGTH = 2000;

    private static final int DEFAULT_TOP_K = 3;

    private final WorkspaceInsight insight;

    private final TheHound hound;

    private final AtomicBoolean isIndexed = new AtomicBoolean(false);

    private final AtomicInteger indexedFileCount = new AtomicInteger(0);

    private volatile Path indexedProjectPath;

    public ContextManager(TheHound.EmbeddingProvider embeddingProvider) {
        Objects.requireNonNull(embeddingProvider, "embeddingProvider no puede ser null");
        this.insight = new WorkspaceInsight();
        this.hound = new TheHound(embeddingProvider);
    }

    public ContextManager(WorkspaceInsight insight, TheHound hound) {
        this.insight = Objects.requireNonNull(insight, "insight no puede ser null");
        this.hound = Objects.requireNonNull(hound, "hound no puede ser null");
    }

    public void indexProject(Path rootPath) {
        Objects.requireNonNull(rootPath, "rootPath no puede ser null");

        if (isIndexed.get() && rootPath.equals(indexedProjectPath)) {
            LOG.fine(() -> "[CONTEXT] Proyecto ya indexado: " + rootPath);
            return;
        }

        doIndex(rootPath);
    }

    public void forceReindex(Path rootPath) {
        Objects.requireNonNull(rootPath, "rootPath no puede ser null");

        hound.forget();
        isIndexed.set(false);
        indexedFileCount.set(0);

        doIndex(rootPath);
    }

    public void refreshIndex(Path rootPath) {
        Objects.requireNonNull(rootPath, "rootPath no puede ser null");

        if (rootPath.equals(indexedProjectPath) && isIndexed.get()) {
            LOG.info("[CONTEXT] Refrescando indice del proyecto...");

            List<Path> currentFiles = insight.getCleanFileList(rootPath);

            final int[] refreshedCount = {0};
            for (Path file : currentFiles) {
                if (isIndexableFile(file)) {
                    try {
                        String content = Files.readString(file);
                        String relativeName = rootPath.relativize(file).toString();
                        hound.learn(relativeName, content);
                        refreshedCount[0]++;
                    } catch (IOException e) {
                        LOG.fine(() -> "[CONTEXT] Error refrescando: " + file);
                    }
                }
            }

            LOG.info(() -> String.format(
                "[CONTEXT] Indice refrescado: %d archivos actualizados.", refreshedCount[0]
            ));
        } else {
            if (isIndexed.get()) {
                LOG.info("[CONTEXT] Cambiando a proyecto diferente, re-indexando...");
                forceReindex(rootPath);
            } else {
                LOG.info("[CONTEXT] Proyecto no indexado, indexando por primera vez...");
                indexProject(rootPath);
            }
        }
    }

    public void updateFileIndex(Path filePath) {
        Objects.requireNonNull(filePath, "filePath no puede ser null");

        if (indexedProjectPath == null) {
            LOG.warning("[CONTEXT] No hay proyecto indexado. Use indexProject() primero.");
            return;
        }

        if (!Files.exists(filePath)) {
            LOG.warning(() -> "[CONTEXT] Archivo no existe: " + filePath);
            return;
        }

        if (!filePath.startsWith(indexedProjectPath)) {
            LOG.warning(() -> "[CONTEXT] Archivo fuera del proyecto indexado: " + filePath);
            return;
        }

        if (!isIndexableFile(filePath)) {
            LOG.fine(() -> "[CONTEXT] Archivo no indexable (extension o tamano): " + filePath);
            return;
        }

        try {
            String content = Files.readString(filePath);
            String relativeName = indexedProjectPath.relativize(filePath).toString();

            hound.learn(relativeName, content);

            LOG.info(() -> "[CONTEXT] Indice actualizado para: " + relativeName);
        } catch (IOException e) {
            LOG.warning(() -> "[CONTEXT] Error actualizando indice: " + filePath + " - " + e.getMessage());
        }
    }

    public void updateFilesIndex(List<Path> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return;
        }

        for (Path path : filePaths) {
            updateFileIndex(path);
        }
    }

    private void doIndex(Path rootPath) {
        LOG.info(() -> "[CONTEXT] Iniciando indexacion de proyecto: " + rootPath);

        List<Path> cleanFiles = insight.getCleanFileList(rootPath);

        cleanFiles.stream()
                .filter(this::isIndexableFile)
                .forEach(path -> indexFile(rootPath, path));

        isIndexed.set(true);
        indexedProjectPath = rootPath;

        LOG.info(() -> String.format(
            "[CONTEXT] Proyecto indexado: %d archivos en memoria vectorial/lexica.",
            indexedFileCount.get()
        ));
    }

    private boolean isIndexableFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();

        boolean hasValidExtension = CODE_EXTENSIONS.stream()
                .anyMatch(fileName::endsWith);

        if (!hasValidExtension) {
            return false;
        }

        try {
            long size = Files.size(path);
            return size <= MAX_FILE_SIZE;
        } catch (IOException e) {
            return false;
        }
    }

    private void indexFile(Path rootPath, Path filePath) {
        try {
            String content = Files.readString(filePath);
            String relativeName = rootPath.relativize(filePath).toString();

            hound.learn(relativeName, content);
            indexedFileCount.incrementAndGet();

            LOG.fine(() -> "[CONTEXT] Indexado: " + relativeName);
        } catch (IOException e) {
            LOG.warning(() -> "[CONTEXT] Error leyendo archivo para indice: " + filePath);
        }
    }

    public String buildContextPayload(Path rootPath, String userQueryOrError) {
        return buildContextPayload(rootPath, userQueryOrError, DEFAULT_TOP_K);
    }

    public String buildContextPayload(Path rootPath, String userQueryOrError, int topK) {
        Objects.requireNonNull(rootPath, "rootPath no puede ser null");

        StringBuilder payload = new StringBuilder();

        String situationalAwareness = insight.scanAndReport(rootPath);
        payload.append(situationalAwareness).append("\n");

        if (userQueryOrError != null && !userQueryOrError.isBlank() && isIndexed.get()) {
            List<String> relevantFiles = hound.hunt(userQueryOrError, Math.max(1, topK));

            if (!relevantFiles.isEmpty()) {
                payload.append("============ ARCHIVOS RELEVANTES (RAG) ============\n");
                payload.append("El sistema ha detectado que estos archivos son clave para tu tarea:\n\n");

                for (String filename : relevantFiles) {
                    appendFileContent(payload, rootPath, filename);
                }

                payload.append("===================================================\n");
            }
        }

        return payload.toString();
    }

    private void appendFileContent(StringBuilder payload, Path rootPath, String filename) {
        try {
            Path filePath = rootPath.resolve(filename);

            if (!Files.exists(filePath)) {
                return;
            }

            String content = Files.readString(filePath);

            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH) + "\n...[TRUNCADO]";
            }

            payload.append("ARCHIVO: ").append(filename).append("\n");
            payload.append("```\n").append(content).append("\n```\n\n");
        } catch (IOException e) {
            LOG.fine(() -> "[CONTEXT] No se pudo leer archivo relevante: " + filename);
        }
    }

    public boolean isIndexed() {
        return isIndexed.get();
    }

    public int getIndexedFileCount() {
        return indexedFileCount.get();
    }

    public Path getIndexedProjectPath() {
        return indexedProjectPath;
    }

    public String getStats() {
        return String.format(
            "ContextManager Stats:\n" +
            "  - Indexed: %s\n" +
            "  - Files: %d\n" +
            "  - Project: %s\n" +
            "  - %s",
            isIndexed.get(),
            indexedFileCount.get(),
            indexedProjectPath != null ? indexedProjectPath : "(none)",
            hound.getStats()
        );
    }

    public void clear() {
        hound.forget();
        isIndexed.set(false);
        indexedFileCount.set(0);
        indexedProjectPath = null;
        LOG.info("[CONTEXT] Indice limpiado.");
    }

    public WorkspaceInsight getInsight() {
        return insight;
    }

    public TheHound getHound() {
        return hound;
    }
}
