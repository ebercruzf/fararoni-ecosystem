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
package dev.fararoni.core.core.services;

import dev.fararoni.core.core.commands.AddCommand;
import dev.fararoni.core.core.commands.DropCommand;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class FileContextService {
    private static final Set<String> CODE_EXTENSIONS = Set.of(
        ".java", ".js", ".ts", ".jsx", ".tsx", ".py", ".go", ".rs",
        ".cpp", ".c", ".h", ".hpp", ".cs", ".php", ".rb", ".kt", ".scala",
        ".swift", ".dart", ".vue", ".svelte"
    );

    private static final Set<String> CONFIG_EXTENSIONS = Set.of(
        ".json", ".xml", ".yaml", ".yml", ".toml", ".ini", ".properties",
        ".env", ".config"
    );

    private static final Set<String> DOC_EXTENSIONS = Set.of(
        ".md", ".txt", ".rst", ".adoc"
    );

    private static final Set<String> ALL_EXTENSIONS;
    static {
        Set<String> all = new HashSet<>();
        all.addAll(CODE_EXTENSIONS);
        all.addAll(CONFIG_EXTENSIONS);
        all.addAll(DOC_EXTENSIONS);
        ALL_EXTENSIONS = Collections.unmodifiableSet(all);
    }

    private static final Set<String> IGNORED_DIRS = Set.of(
        "target", "build", "dist", "out", "node_modules", "vendor",
        ".git", ".svn", ".hg", ".idea", ".vscode", "__pycache__",
        ".gradle", ".mvn", "venv", ".venv"
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final Map<Path, FileEntry> loadedFiles = new LinkedHashMap<>();

    private final Path workingDirectory;

    public FileContextService(Path workingDirectory) {
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
    }

    public FileContextService() {
        this(Path.of("."));
    }

    public AddResult addFiles(List<String> patterns) {
        List<Path> addedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;

        for (String pattern : patterns) {
            try {
                List<Path> matched = resolvePattern(pattern);

                if (matched.isEmpty()) {
                    errors.add("No se encontraron archivos: " + pattern);
                    continue;
                }

                for (Path file : matched) {
                    if (loadedFiles.containsKey(file)) {
                        skipped++;
                        continue;
                    }

                    try {
                        String content = Files.readString(file);
                        loadedFiles.put(file, new FileEntry(file, content, System.currentTimeMillis()));
                        addedFiles.add(file);
                    } catch (IOException e) {
                        errors.add("Error leyendo " + file.getFileName() + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                errors.add("Error procesando patron " + pattern + ": " + e.getMessage());
            }
        }

        return new AddResult(
            !addedFiles.isEmpty(),
            addedFiles,
            skipped,
            errors,
            getTotalChars()
        );
    }

    public AddResult addFile(String path) {
        return addFiles(List.of(path));
    }

    public DropResult dropFiles(List<String> patterns) {
        List<Path> droppedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String pattern : patterns) {
            List<Path> toRemove = loadedFiles.keySet().stream()
                .filter(p -> matchesPattern(p, pattern))
                .collect(Collectors.toList());

            if (toRemove.isEmpty()) {
                errors.add("No hay archivos cargados que coincidan: " + pattern);
                continue;
            }

            for (Path file : toRemove) {
                loadedFiles.remove(file);
                droppedFiles.add(file);
            }
        }

        return new DropResult(
            !droppedFiles.isEmpty(),
            droppedFiles,
            errors,
            getTotalChars()
        );
    }

    public DropResult dropFile(String path) {
        return dropFiles(List.of(path));
    }

    public DropResult dropAll() {
        List<Path> dropped = new ArrayList<>(loadedFiles.keySet());
        loadedFiles.clear();
        return new DropResult(true, dropped, List.of(), 0);
    }

    public List<FileInfo> listLoadedFiles() {
        return loadedFiles.entrySet().stream()
            .map(e -> new FileInfo(
                e.getKey(),
                e.getValue().content().length(),
                e.getValue().loadedAt()
            ))
            .collect(Collectors.toList());
    }

    public boolean isLoaded(Path path) {
        return loadedFiles.containsKey(path.toAbsolutePath().normalize());
    }

    public int getLoadedCount() {
        return loadedFiles.size();
    }

    public int getTotalChars() {
        return loadedFiles.values().stream()
            .mapToInt(e -> e.content().length())
            .sum();
    }

    public String formatForContext() {
        if (loadedFiles.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(">>> LOADED FILES (").append(loadedFiles.size()).append(" files, ");
        sb.append(String.format("%,d", getTotalChars())).append(" chars)\n\n");

        for (var entry : loadedFiles.entrySet()) {
            Path relativePath = workingDirectory.relativize(entry.getKey());
            sb.append("=== ").append(relativePath).append(" ===\n");
            sb.append(entry.getValue().content());
            sb.append("\n\n");
        }

        return sb.toString();
    }

    private List<Path> resolvePattern(String pattern) throws IOException {
        List<Path> result = new ArrayList<>();
        String trimmed = pattern.trim();

        Path path = trimmed.startsWith("/") || trimmed.matches("^[A-Za-z]:.*")
            ? Path.of(trimmed)
            : workingDirectory.resolve(trimmed);

        path = path.normalize();

        if (Files.isDirectory(path)) {
            result.addAll(scanDirectory(path));
            return result;
        }

        if (Files.isRegularFile(path)) {
            if (isSupported(path) && Files.size(path) <= MAX_FILE_SIZE) {
                result.add(path);
            }
            return result;
        }

        if (trimmed.contains("*") || trimmed.contains("?")) {
            PathMatcher matcher = FileSystems.getDefault()
                .getPathMatcher("glob:" + trimmed);

            Files.walkFileTree(workingDirectory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName().toString();
                    if (IGNORED_DIRS.contains(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relative = workingDirectory.relativize(file);
                    if (matcher.matches(relative) && isSupported(file)) {
                        try {
                            if (Files.size(file) <= MAX_FILE_SIZE) {
                                result.add(file.toAbsolutePath().normalize());
                            }
                        } catch (IOException ignored) {}
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return result;
    }

    private List<Path> scanDirectory(Path dir) throws IOException {
        List<Path> result = new ArrayList<>();

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                String name = d.getFileName().toString();
                if (IGNORED_DIRS.contains(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isSupported(file) && attrs.size() <= MAX_FILE_SIZE) {
                    result.add(file.toAbsolutePath().normalize());
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return result;
    }

    private boolean matchesPattern(Path path, String pattern) {
        String filename = path.getFileName().toString();
        String relativePath = workingDirectory.relativize(path).toString();

        if (filename.equals(pattern) || relativePath.equals(pattern)) {
            return true;
        }

        if (pattern.startsWith("*.")) {
            return filename.endsWith(pattern.substring(1));
        }

        return relativePath.contains(pattern) || filename.contains(pattern);
    }

    private boolean isSupported(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return ALL_EXTENSIONS.stream().anyMatch(name::endsWith) ||
               name.equals("dockerfile") ||
               name.equals("makefile");
    }

    private record FileEntry(Path path, String content, long loadedAt) {}

    public record FileInfo(Path path, int chars, long loadedAt) {}

    public record AddResult(
        boolean success,
        List<Path> addedFiles,
        int skipped,
        List<String> errors,
        int totalChars
    ) {
        public int addedCount() {
            return addedFiles.size();
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    public record DropResult(
        boolean success,
        List<Path> droppedFiles,
        List<String> errors,
        int remainingChars
    ) {
        public int droppedCount() {
            return droppedFiles.size();
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
