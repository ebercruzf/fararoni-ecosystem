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
package dev.fararoni.core.core.index;

import dev.fararoni.core.core.indexing.GitignoreFilter;
import dev.fararoni.core.core.services.AnalysisResult;
import dev.fararoni.core.core.services.CodeAnalysisService;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SafeCrawler implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(SafeCrawler.class.getName());

    private final IndexStore indexStore;

    private final CodeAnalysisService analysisService;

    private final GitignoreFilter gitignoreFilter;

    private final CrawlConfig config;

    private final ExecutorService executor;

    public SafeCrawler(Path projectRoot) {
        this(
            new IndexStore(),
            new CodeAnalysisService(),
            GitignoreFilter.forProject(projectRoot),
            CrawlConfig.DEFAULT
        );
    }

    public SafeCrawler(
            IndexStore indexStore,
            CodeAnalysisService analysisService,
            GitignoreFilter gitignoreFilter,
            CrawlConfig config) {
        this.indexStore = indexStore;
        this.analysisService = analysisService;
        this.gitignoreFilter = gitignoreFilter;
        this.config = config;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public CrawlResult crawl(Path root) {
        if (root == null || !Files.exists(root)) {
            LOG.warning("[SafeCrawler] Root path does not exist: " + root);
            return CrawlResult.EMPTY;
        }

        if (!Files.isDirectory(root)) {
            LOG.warning("[SafeCrawler] Root path is not a directory: " + root);
            return CrawlResult.EMPTY;
        }

        CrawlResult.Builder resultBuilder = CrawlResult.builder().start();
        Set<Path> visitedCanonicalPaths = new HashSet<>();

        LOG.info("[SafeCrawler] Starting crawl: " + root);

        try {
            Files.walkFileTree(
                root,
                EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                config.maxDepth(),
                new SafeFileVisitor(resultBuilder, visitedCanonicalPaths, root)
            );
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[SafeCrawler] Error during file tree walk", e);
            resultBuilder.addError(root.toString(), e.getMessage(), CrawlResult.ErrorType.IO_ERROR);
        }

        int pruned = indexStore.pruneDeleted();
        if (pruned > 0) {
            LOG.info("[SafeCrawler] Pruned " + pruned + " deleted files from index");
        }

        CrawlResult result = resultBuilder.build();
        LOG.info(result.toReport());

        return result;
    }

    public boolean processFile(Path file) {
        if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
            return false;
        }

        CrawlResult.Builder resultBuilder = CrawlResult.builder();
        processFileInternal(file, resultBuilder);

        return resultBuilder.getProcessedCount() > 0;
    }

    private class SafeFileVisitor extends SimpleFileVisitor<Path> {
        private final CrawlResult.Builder resultBuilder;
        private final Set<Path> visitedCanonicalPaths;
        private final Path root;

        SafeFileVisitor(CrawlResult.Builder resultBuilder, Set<Path> visitedCanonicalPaths, Path root) {
            this.resultBuilder = resultBuilder;
            this.visitedCanonicalPaths = visitedCanonicalPaths;
            this.root = root;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            try {
                Path canonical = dir.toRealPath();
                if (!visitedCanonicalPaths.add(canonical)) {
                    LOG.fine("[SafeCrawler] Skipping cyclic symlink: " + dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
            } catch (IOException e) {
                return FileVisitResult.SKIP_SUBTREE;
            }

            String relativePath = root.relativize(dir).toString();
            if (gitignoreFilter.isIgnored(relativePath)) {
                LOG.fine("[SafeCrawler] Skipping ignored directory: " + dir);
                return FileVisitResult.SKIP_SUBTREE;
            }

            if (config.shouldExclude(dir.toString())) {
                LOG.fine("[SafeCrawler] Skipping excluded directory: " + dir);
                return FileVisitResult.SKIP_SUBTREE;
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            processFileInternal(file, resultBuilder);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            resultBuilder.incrementFailed();
            resultBuilder.addError(file.toString(), exc.getMessage(), CrawlResult.ErrorType.IO_ERROR);
            return FileVisitResult.CONTINUE;
        }
    }

    private void processFileInternal(Path file, CrawlResult.Builder resultBuilder) {
        String filename = file.getFileName().toString();

        String extension = getExtension(filename);
        if (!config.isExtensionIncluded(extension)) {
            return;
        }

        if (gitignoreFilter.isIgnored(file)) {
            return;
        }

        try {
            long size = Files.size(file);
            if (config.exceedsMaxSize(size)) {
                resultBuilder.incrementSkipped();
                LOG.fine("[SafeCrawler] Skipping large file: " + file + " (" + size + " bytes)");
                return;
            }
        } catch (IOException e) {
            resultBuilder.incrementFailed();
            resultBuilder.addError(file.toString(), "Cannot read file size: " + e.getMessage(),
                    CrawlResult.ErrorType.IO_ERROR);
            return;
        }

        String contentHash;
        try {
            contentHash = ContentHasher.hashFile(file);
        } catch (IOException e) {
            resultBuilder.incrementFailed();
            resultBuilder.addError(file.toString(), "Cannot hash file: " + e.getMessage(),
                    CrawlResult.ErrorType.IO_ERROR);
            return;
        }

        if (!indexStore.needsReindexing(file, contentHash)) {
            resultBuilder.incrementSkipped();
            return;
        }

        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() ->
                analyzeAndIndex(file, contentHash, extension), executor);

            boolean success = future.orTimeout(config.timeoutMillis(), TimeUnit.MILLISECONDS).join();

            if (success) {
                resultBuilder.incrementProcessed();
            } else {
                resultBuilder.incrementFailed();
            }
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                resultBuilder.incrementTimeout();
                resultBuilder.addError(file.toString(), "Processing timeout exceeded",
                        CrawlResult.ErrorType.TIMEOUT);
                indexStore.markAsFailed(file, "Timeout");
            } else {
                resultBuilder.incrementFailed();
                resultBuilder.addError(file.toString(),
                        cause != null ? cause.getMessage() : e.getMessage(),
                        CrawlResult.ErrorType.PARSE_ERROR);
            }
        }
    }

    private boolean analyzeAndIndex(Path file, String contentHash, String extension) {
        try {
            String content = Files.readString(file);

            FileManifest.ParseStatus status;
            if (analysisService != null) {
                var result = analysisService.analyze(file.getFileName().toString(), content);

                if (result.confidence() == AnalysisResult.Confidence.HIGH ||
                    result.confidence() == AnalysisResult.Confidence.LOW) {
                    status = FileManifest.ParseStatus.SUCCESS;
                } else if (result.confidence() == AnalysisResult.Confidence.NONE) {
                    status = FileManifest.ParseStatus.FAILED;
                } else {
                    status = FileManifest.ParseStatus.SUCCESS;
                }
            } else {
                status = FileManifest.ParseStatus.PENDING;
                LOG.fine(() -> "[SafeCrawler] Modo degradado (sin analysisService): " + file);
            }

            String language = detectLanguage(extension);

            long lastModified = Files.getLastModifiedTime(file).toMillis();
            FileManifest manifest = new FileManifest(
                file.toAbsolutePath(),
                lastModified,
                contentHash,
                status,
                language,
                System.currentTimeMillis()
            );

            indexStore.updateManifest(manifest);
            return status == FileManifest.ParseStatus.SUCCESS;
        } catch (java.nio.charset.MalformedInputException e) {
            LOG.warning("[SafeCrawler] Archivo con encoding invalido (saltando): " + file);
            indexStore.markAsFailed(file, "Invalid encoding: " + e.getMessage());
            return false;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[SafeCrawler] Failed to analyze: " + file, e);
            indexStore.markAsFailed(file, e.getMessage());
            return false;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SafeCrawler] Unexpected error analyzing: " + file, e);
            indexStore.markAsUnparseable(file, e.getMessage());
            return false;
        }
    }

    private String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }

    private String detectLanguage(String extension) {
        return switch (extension) {
            case "java" -> "java";
            case "py" -> "python";
            case "js", "jsx" -> "javascript";
            case "ts", "tsx" -> "typescript";
            case "go" -> "go";
            case "rs" -> "rust";
            case "rb" -> "ruby";
            case "kt" -> "kotlin";
            case "scala" -> "scala";
            case "c", "h" -> "c";
            case "cpp", "hpp", "cc", "cxx" -> "cpp";
            case "html", "vue", "svelte" -> "html";
            case "xml" -> "xml";
            case "json" -> "json";
            case "yaml", "yml" -> "yaml";
            case "md" -> "markdown";
            case "sh", "bash" -> "shell";
            default -> extension;
        };
    }

    public IndexStore getIndexStore() {
        return indexStore;
    }

    public CrawlConfig getConfig() {
        return config;
    }

    public IndexStore.IndexStats getStats() {
        return indexStore.getStats();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (indexStore != null) {
            indexStore.close();
        }

        LOG.info("[SafeCrawler] Closed");
    }
}
