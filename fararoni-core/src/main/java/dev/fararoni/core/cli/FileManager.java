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
package dev.fararoni.core.cli;

import dev.fararoni.core.tokenizer.Tokenizer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Stream;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class FileManager {
    private final Tokenizer tokenizer;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".java", ".js", ".ts", ".py", ".go", ".rs", ".cpp", ".c", ".h", ".hpp",
        ".cs", ".php", ".rb", ".kt", ".scala", ".swift", ".dart",
        ".json", ".xml", ".yaml", ".yml", ".toml", ".ini", ".properties",
        ".md", ".txt", ".rst", ".adoc",
        ".html", ".htm", ".css", ".scss", ".less",
        ".sh", ".bash", ".zsh", ".fish", ".ps1", ".bat",
        ".dockerfile", ".makefile", ".gradle", ".maven", ".sbt",
        ".sql", ".graphql", ".proto"
    );

    private static final Set<String> IGNORED_PATTERNS = Set.of(
        "target", "build", "dist", "out", ".gradle", ".mvn",
        "node_modules", "vendor", ".venv", "venv", "__pycache__",
        ".idea", ".vscode", ".eclipse", ".settings",
        ".git", ".svn", ".hg",
        ".DS_Store", "Thumbs.db",
        "*.log", "*.tmp", "*.temp", "*.cache"
    );

    public FileManager(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public LoadResult loadFiles(List<String> paths, String description) {
        var startTime = System.currentTimeMillis();

        printInfo("[SCAN] Analizando archivos...");

        var allFiles = new ArrayList<Path>();
        var errors = new ArrayList<String>();

        for (var pathStr : paths) {
            try {
                var path = Paths.get(pathStr);

                if (Files.isDirectory(path)) {
                    var dirFiles = scanDirectory(path);
                    allFiles.addAll(dirFiles);
                    printInfo("[DIR] Directorio: %s (%d archivos)".formatted(pathStr, dirFiles.size()));
                } else if (Files.isRegularFile(path)) {
                    if (isSupported(path)) {
                        allFiles.add(path);
                        printInfo("[FILE] Archivo: %s".formatted(pathStr));
                    } else {
                        printWarning("[WARN] Archivo no soportado: %s".formatted(pathStr));
                    }
                } else {
                    errors.add("Ruta no encontrada: " + pathStr);
                }
            } catch (Exception e) {
                errors.add("Error procesando " + pathStr + ": " + e.getMessage());
            }
        }

        if (allFiles.isEmpty()) {
            return LoadResult.error("No se encontraron archivos válidos", errors);
        }

        var contents = new ArrayList<String>();
        for (var file : allFiles) {
            try {
                var content = Files.readString(file);
                contents.add(String.format("=== %s ===\n%s\n", file.getFileName(), content));
            } catch (Exception e) {
                errors.add("Error leyendo " + file + ": " + e.getMessage());
            }
        }

        var combinedContext = String.join("\n\n", contents);
        var estimatedTokens = tokenizer.countTokens(combinedContext);

        var loadTime = System.currentTimeMillis() - startTime;

        printSuccess("[OK] %d archivos cargados (%,d tokens estimados)".formatted(
            allFiles.size(), estimatedTokens));

        return LoadResult.success(
            combinedContext,
            allFiles,
            estimatedTokens,
            errors,
            loadTime
        );
    }

    public LoadResult loadSingleFile(String filePath) {
        try {
            var path = Paths.get(filePath);

            if (!Files.isRegularFile(path)) {
                return LoadResult.error("No es un archivo válido: " + filePath, List.of());
            }

            if (!isSupported(path)) {
                return LoadResult.error("Tipo de archivo no soportado: " + filePath, List.of());
            }

            return loadFiles(List.of(filePath), "Análisis de archivo único: " + path.getFileName());
        } catch (Exception e) {
            return LoadResult.error("Error cargando archivo: " + e.getMessage(), List.of(e.toString()));
        }
    }

    private List<Path> scanDirectory(Path dir) throws IOException {
        var files = new ArrayList<Path>();

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                var dirName = dir.getFileName().toString();

                if (IGNORED_PATTERNS.contains(dirName) || dirName.startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isSupported(file) && attrs.size() < 10 * 1024 * 1024) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    private boolean isSupported(Path file) {
        var filename = file.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(filename::endsWith) ||
               filename.equals("dockerfile") ||
               filename.equals("makefile") ||
               filename.equals("readme");
    }

    public void listFiles(String directoryPath, boolean showDetails) {
        try {
            var dir = Paths.get(directoryPath);

            if (!Files.isDirectory(dir)) {
                printError("No es un directorio: " + directoryPath);
                return;
            }

            printInfo("[DIR] Contenido de: " + directoryPath);

            try (Stream<Path> files = Files.list(dir)) {
                var sortedFiles = files.sorted().toList();

                for (var file : sortedFiles) {
                    var attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    var isDir = attrs.isDirectory();
                    var size = attrs.size();

                    var icon = isDir ? "[D]" : (isSupported(file) ? "[F]" : "[?]");
                    var name = file.getFileName().toString();

                    if (showDetails && !isDir && isSupported(file)) {
                        var estimatedTokens = (int) (size / 4);
                        System.out.println("%s %s (%,d bytes, ~%,d tokens)".formatted(
                            icon, name, size, estimatedTokens));
                    } else {
                        System.out.println("%s %s%s".formatted(
                            icon, name, isDir ? "/" : " (" + formatSize(size) + ")"));
                    }
                }
            }
        } catch (Exception e) {
            printError("Error listando directorio: " + e.getMessage());
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }

    private void printInfo(String message) {
        System.out.println(ansi().fgBlue().a("[i] " + message).reset());
    }

    private void printWarning(String message) {
        System.out.println(ansi().fgYellow().a("[WARN] " + message).reset());
    }

    private void printError(String message) {
        System.out.println(ansi().fgRed().a("[ERROR] " + message).reset());
    }

    private void printSuccess(String message) {
        System.out.println(ansi().fgGreen().a("[OK] " + message).reset());
    }

    public record LoadResult(
        boolean success,
        String context,
        List<Path> processedFiles,
        int totalTokens,
        List<String> errors,
        long loadTimeMs,
        String errorMessage
    ) {
        public static LoadResult success(
            String context,
            List<Path> files,
            int tokens,
            List<String> errors,
            long loadTime) {
            return new LoadResult(
                true, context, files, tokens, errors, loadTime, null
            );
        }

        public static LoadResult error(String message, List<String> errors) {
            return new LoadResult(
                false, null, List.of(), 0, errors, 0, message
            );
        }

        public int getTotalFiles() {
            return processedFiles != null ? processedFiles.size() : 0;
        }

        public boolean hasWarnings() {
            return !errors.isEmpty();
        }
    }
}
