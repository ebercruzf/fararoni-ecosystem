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
package dev.fararoni.core.agent;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ContextCollector {
    private final Path projectRoot;
    private final int maxFiles;
    private final int maxDepth;

    private static final Set<String> CODE_EXTENSIONS = Set.of(
        ".java", ".kt", ".scala",
        ".py", ".pyx",
        ".js", ".ts", ".jsx", ".tsx",
        ".go",
        ".rs",
        ".c", ".cpp", ".h", ".hpp",
        ".cs",
        ".rb",
        ".php",
        ".swift",
        ".xml", ".json", ".yaml", ".yml",
        ".md", ".txt",
        ".html", ".css", ".scss",
        ".sql",
        ".sh", ".bash",
        ".gradle", ".properties"
    );

    private static final Set<String> IGNORED_DIRS = Set.of(
        "node_modules", ".git", ".svn", ".hg",
        "target", "build", "dist", "out",
        ".idea", ".vscode", ".eclipse",
        "__pycache__", ".pytest_cache",
        "venv", ".venv", "env",
        ".gradle", ".m2",
        "vendor", "deps"
    );

    private static final Set<String> IGNORED_FILES = Set.of(
        ".DS_Store", "Thumbs.db",
        ".gitignore", ".gitattributes",
        "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
        "Gemfile.lock", "poetry.lock", "Cargo.lock"
    );

    public ContextCollector(Path projectRoot) {
        this(projectRoot, 200, 10);
    }

    public ContextCollector(Path projectRoot, int maxFiles, int maxDepth) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.maxFiles = maxFiles;
        this.maxDepth = maxDepth;
    }

    public String collectCompactContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("PROJECT STRUCTURE:\n");

        try {
            List<Path> files = collectFiles();

            if (files.isEmpty()) {
                sb.append("(empty project)\n");
                return sb.toString();
            }

            Map<Path, List<Path>> byDirectory = files.stream()
                .collect(Collectors.groupingBy(
                    p -> projectRoot.relativize(p).getParent() == null
                        ? Paths.get(".")
                        : projectRoot.relativize(p).getParent()
                ));

            List<Path> sortedDirs = new ArrayList<>(byDirectory.keySet());
            Collections.sort(sortedDirs);

            for (Path dir : sortedDirs) {
                String dirStr = dir.toString();
                if (!dirStr.equals(".")) {
                    sb.append(dirStr).append("/\n");
                }

                List<Path> dirFiles = byDirectory.get(dir);
                Collections.sort(dirFiles);

                String indent = dirStr.equals(".") ? "" : "  ";
                for (Path file : dirFiles) {
                    sb.append(indent).append(file.getFileName()).append("\n");
                }
            }

            sb.append("\n[").append(files.size()).append(" files");
            if (files.size() >= maxFiles) {
                sb.append(" (truncated)");
            }
            sb.append("]\n");
        } catch (IOException e) {
            sb.append("(error reading project: ").append(e.getMessage()).append(")\n");
        }

        return sb.toString();
    }

    public String collectTreeContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("PROJECT TREE:\n");
        sb.append(".\n");

        try {
            buildTree(projectRoot, sb, "", true, 0);
        } catch (IOException e) {
            sb.append("(error: ").append(e.getMessage()).append(")\n");
        }

        return sb.toString();
    }

    public ProjectInfo detectProjectType() {
        ProjectType type = ProjectType.UNKNOWN;
        String name = projectRoot.getFileName().toString();
        String buildTool = null;
        String language = null;

        if (Files.exists(projectRoot.resolve("pom.xml"))) {
            type = ProjectType.JAVA_MAVEN;
            buildTool = "Maven";
            language = "Java";
        } else if (Files.exists(projectRoot.resolve("build.gradle")) ||
                   Files.exists(projectRoot.resolve("build.gradle.kts"))) {
            type = ProjectType.JAVA_GRADLE;
            buildTool = "Gradle";
            language = "Java/Kotlin";
        } else if (Files.exists(projectRoot.resolve("package.json"))) {
            type = ProjectType.NODE;
            buildTool = "npm/yarn";
            language = "JavaScript/TypeScript";
        } else if (Files.exists(projectRoot.resolve("requirements.txt")) ||
                   Files.exists(projectRoot.resolve("setup.py")) ||
                   Files.exists(projectRoot.resolve("pyproject.toml"))) {
            type = ProjectType.PYTHON;
            buildTool = "pip/poetry";
            language = "Python";
        } else if (Files.exists(projectRoot.resolve("Cargo.toml"))) {
            type = ProjectType.RUST;
            buildTool = "Cargo";
            language = "Rust";
        } else if (Files.exists(projectRoot.resolve("go.mod"))) {
            type = ProjectType.GO;
            buildTool = "Go Modules";
            language = "Go";
        }

        return new ProjectInfo(type, name, buildTool, language);
    }

    public String collectFullContext() {
        StringBuilder sb = new StringBuilder();

        ProjectInfo info = detectProjectType();
        sb.append("PROJECT INFO:\n");
        sb.append("  Name: ").append(info.name()).append("\n");
        sb.append("  Type: ").append(info.type()).append("\n");
        if (info.language() != null) {
            sb.append("  Language: ").append(info.language()).append("\n");
        }
        if (info.buildTool() != null) {
            sb.append("  Build: ").append(info.buildTool()).append("\n");
        }
        sb.append("\n");

        sb.append(collectCompactContext());

        return sb.toString();
    }

    public String refresh() {
        return collectFullContext();
    }

    private List<Path> collectFiles() throws IOException {
        List<Path> files = new ArrayList<>();

        Files.walkFileTree(projectRoot, EnumSet.noneOf(FileVisitOption.class), maxDepth,
            new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (IGNORED_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (files.size() >= maxFiles) {
                        return FileVisitResult.TERMINATE;
                    }

                    String fileName = file.getFileName().toString();

                    if (IGNORED_FILES.contains(fileName)) {
                        return FileVisitResult.CONTINUE;
                    }

                    if (isRelevantFile(fileName)) {
                        files.add(file);
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

        return files;
    }

    private boolean isRelevantFile(String fileName) {
        if (fileName.equals("Makefile") || fileName.equals("Dockerfile") ||
            fileName.equals("Jenkinsfile") || fileName.equals("Rakefile")) {
            return true;
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String ext = fileName.substring(dotIndex).toLowerCase();
            return CODE_EXTENSIONS.contains(ext);
        }

        return false;
    }

    private void buildTree(Path dir, StringBuilder sb, String prefix, boolean isLast, int depth)
            throws IOException {
        if (depth >= maxDepth) {
            return;
        }

        List<Path> children;
        try (Stream<Path> stream = Files.list(dir)) {
            children = stream
                .filter(p -> {
                    String name = p.getFileName().toString();
                    if (Files.isDirectory(p)) {
                        return !IGNORED_DIRS.contains(name);
                    } else {
                        return !IGNORED_FILES.contains(name) && isRelevantFile(name);
                    }
                })
                .sorted((a, b) -> {
                    boolean aIsDir = Files.isDirectory(a);
                    boolean bIsDir = Files.isDirectory(b);
                    if (aIsDir != bIsDir) {
                        return aIsDir ? -1 : 1;
                    }
                    return a.getFileName().toString().compareTo(b.getFileName().toString());
                })
                .collect(Collectors.toList());
        }

        for (int i = 0; i < children.size(); i++) {
            Path child = children.get(i);
            boolean last = (i == children.size() - 1);
            String connector = last ? "└── " : "├── ";
            String childPrefix = last ? "    " : "│   ";

            String name = child.getFileName().toString();
            if (Files.isDirectory(child)) {
                name += "/";
            }

            sb.append(prefix).append(connector).append(name).append("\n");

            if (Files.isDirectory(child)) {
                buildTree(child, sb, prefix + childPrefix, last, depth + 1);
            }
        }
    }

    public enum ProjectType {
        JAVA_MAVEN("Java (Maven)"),
        JAVA_GRADLE("Java (Gradle)"),
        NODE("Node.js"),
        PYTHON("Python"),
        RUST("Rust"),
        GO("Go"),
        UNKNOWN("Unknown");

        private final String displayName;

        ProjectType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public record ProjectInfo(
        ProjectType type,
        String name,
        String buildTool,
        String language
    ) {}
}
