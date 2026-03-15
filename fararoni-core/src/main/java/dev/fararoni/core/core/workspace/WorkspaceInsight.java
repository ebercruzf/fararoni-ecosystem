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
package dev.fararoni.core.core.workspace;

import dev.fararoni.core.core.services.StructureScannerService;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class WorkspaceInsight {
    private static final Set<String> IGNORED_DIRS = new HashSet<>(Arrays.asList(
        ".git",
        ".svn",
        ".hg",

        ".idea",
        ".vscode",
        ".eclipse",
        ".settings",

        "__pycache__",
        ".pytest_cache",
        ".mypy_cache",
        ".tox",
        ".nox",
        "venv",
        ".venv",
        "env",
        ".env",
        "virtualenv",

        "node_modules",
        "dist",
        "build",
        "coverage",
        ".angular",
        ".next",
        ".nuxt",
        ".cache",
        ".parcel-cache",

        "target",
        "bin",
        "out",
        ".gradle",
        ".classpath",
        ".project",

        "vendor",

        ".bundle",

        "logs",
        "tmp",
        "temp"
    ));

    private static final Set<String> IGNORED_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".class",
        ".jar",
        ".war",
        ".ear",

        ".pyc",
        ".pyo",
        ".pyd",
        ".whl",
        ".egg",

        ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".webp", ".bmp",

        ".zip", ".tar", ".gz", ".rar", ".7z", ".bz2",

        ".lock",
        "-lock.json",

        ".exe",
        ".dll",
        ".so",
        ".dylib",
        ".o",
        ".a",
        ".pdf",
        ".doc", ".docx",
        ".xls", ".xlsx",

        ".log"
    ));

    private static final Set<String> IGNORED_FILES = new HashSet<>(Arrays.asList(
        ".DS_Store",
        "Thumbs.db",

        ".gitignore",
        ".gitattributes",

        "package-lock.json",
        "yarn.lock",
        "pnpm-lock.yaml",
        "poetry.lock",
        "Pipfile.lock",
        "Gemfile.lock",
        "Cargo.lock",
        "go.sum"
    ));

    public enum ProjectType {
        PYTHON("Python"),

        JAVA_MAVEN("Java (Maven)"),

        JAVA_GRADLE("Java (Gradle)"),

        ANGULAR("Angular"),

        NODE_JS("Node.js"),

        TYPESCRIPT("TypeScript"),

        GO("Go"),

        RUST("Rust"),

        RUBY("Ruby"),

        C_CPP("C/C++"),

        CSHARP("C#"),

        KOTLIN("Kotlin"),

        PHP("PHP"),

        UNKNOWN("Generico");

        private final String label;

        ProjectType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private static final int MAX_SCAN_DEPTH = 10;

    private static final int MAX_FILES_IN_REPORT = 100;

    private static final int MAX_TREE_LINES = 300;

    private final List<String> ioWarnings = new ArrayList<>();

    public String scanAndReport(Path rootPath) {
        Objects.requireNonNull(rootPath, "rootPath no puede ser null");

        StringBuilder report = new StringBuilder();
        List<Path> allFiles = new ArrayList<>();

        try {
            scanDirectoryRobustly(rootPath, allFiles);
        } catch (IOException e) {
            return "Error al escanear directorio: " + e.getMessage();
        }

        ProjectType type = detectProjectType(rootPath, allFiles);

        report.append("============ CONCIENCIA SITUACIONAL ============\n");
        report.append("Proyecto Detectado: ").append(type.getLabel()).append("\n");
        report.append("Ubicacion: ").append(rootPath.getFileName()).append("\n");
        report.append("Archivos encontrados: ").append(allFiles.size()).append("\n");
        report.append("------------------------------------------------\n");
        report.append("ESTRUCTURA DE ARCHIVOS:\n");
        report.append(generateTree(rootPath, allFiles));
        report.append("================================================\n");

        return report.toString();
    }

    public ProjectType detectType(Path rootPath) {
        Objects.requireNonNull(rootPath, "rootPath no puede ser null");

        List<Path> allFiles = new ArrayList<>();
        try {
            scanDirectoryRobustly(rootPath, allFiles);
        } catch (IOException e) {
            return ProjectType.UNKNOWN;
        }

        return detectProjectType(rootPath, allFiles);
    }

    public String detectProjectIdentity(Path rootPath) {
        Objects.requireNonNull(rootPath, "rootPath no puede ser null");

        List<Path> allFiles = new ArrayList<>();
        try {
            scanDirectoryRobustly(rootPath, allFiles);
        } catch (IOException e) {
            return ProjectType.UNKNOWN.getLabel();
        }

        return detectMultipleTypes(rootPath, allFiles);
    }

    public EnumSet<ProjectType> detectAllTypes(Path rootPath) {
        Objects.requireNonNull(rootPath, "rootPath no puede ser null");

        List<Path> allFiles = new ArrayList<>();
        try {
            scanDirectoryRobustly(rootPath, allFiles);
        } catch (IOException e) {
            return EnumSet.of(ProjectType.UNKNOWN);
        }

        return detectAllProjectTypes(rootPath, allFiles);
    }

    public ScanResult scan(Path rootPath) {
        Objects.requireNonNull(rootPath, "rootPath no puede ser null");

        List<Path> allFiles = new ArrayList<>();
        try {
            scanDirectoryRobustly(rootPath, allFiles);
        } catch (IOException e) {
            return new ScanResult(rootPath, ProjectType.UNKNOWN, List.of(), e.getMessage());
        }

        ProjectType type = detectProjectType(rootPath, allFiles);
        return new ScanResult(rootPath, type, allFiles, null);
    }

    public List<Path> getCleanFileList(Path rootPath) {
        Objects.requireNonNull(rootPath, "rootPath no puede ser null");

        List<Path> allFiles = new ArrayList<>();
        try {
            scanDirectoryRobustly(rootPath, allFiles);
        } catch (IOException e) {
            return List.of();
        }

        return allFiles;
    }

    private ProjectType detectProjectType(Path root, List<Path> files) {
        Set<String> fileNames = files.stream()
                .map(p -> p.getFileName().toString().toLowerCase())
                .collect(Collectors.toSet());

        if (fileNames.contains("angular.json")) {
            return ProjectType.ANGULAR;
        }

        if (fileNames.contains("pom.xml")) {
            return ProjectType.JAVA_MAVEN;
        }

        if (fileNames.contains("build.gradle") || fileNames.contains("build.gradle.kts")) {
            return ProjectType.JAVA_GRADLE;
        }

        if (fileNames.contains("go.mod")) {
            return ProjectType.GO;
        }

        if (fileNames.contains("cargo.toml")) {
            return ProjectType.RUST;
        }

        if (fileNames.contains("gemfile") || fileNames.contains("rakefile")) {
            return ProjectType.RUBY;
        }

        if (fileNames.stream().anyMatch(f -> f.endsWith(".csproj") || f.endsWith(".sln"))) {
            return ProjectType.CSHARP;
        }

        if (fileNames.stream().anyMatch(f -> f.endsWith(".kt") || f.endsWith(".kts"))) {
            if (!fileNames.contains("build.gradle") && !fileNames.contains("build.gradle.kts")) {
                return ProjectType.KOTLIN;
            }
        }

        if (fileNames.contains("composer.json")) {
            return ProjectType.PHP;
        }

        if (fileNames.contains("cmakelists.txt") || fileNames.contains("makefile")) {
            return ProjectType.C_CPP;
        }

        if (fileNames.contains("requirements.txt") ||
            fileNames.contains("pyproject.toml") ||
            fileNames.contains("setup.py") ||
            fileNames.contains("pipfile")) {
            return ProjectType.PYTHON;
        }

        if (fileNames.contains("tsconfig.json")) {
            if (fileNames.contains("package.json")) {
                if (hasExtension(files, ".ts") && !hasExtension(files, ".js")) {
                    return ProjectType.TYPESCRIPT;
                }
            }
            return ProjectType.TYPESCRIPT;
        }

        if (fileNames.contains("package.json")) {
            return ProjectType.NODE_JS;
        }

        if (hasExtension(files, ".py")) {
            return ProjectType.PYTHON;
        }

        if (hasExtension(files, ".go")) {
            return ProjectType.GO;
        }

        if (hasExtension(files, ".rs")) {
            return ProjectType.RUST;
        }

        if (hasExtension(files, ".rb")) {
            return ProjectType.RUBY;
        }

        if (hasExtension(files, ".java")) {
            return ProjectType.JAVA_MAVEN;
        }

        if (hasExtension(files, ".ts") || hasExtension(files, ".tsx")) {
            return ProjectType.TYPESCRIPT;
        }

        if (hasExtension(files, ".js") || hasExtension(files, ".jsx")) {
            return ProjectType.NODE_JS;
        }

        if (hasExtension(files, ".c") || hasExtension(files, ".cpp") ||
            hasExtension(files, ".h") || hasExtension(files, ".hpp")) {
            return ProjectType.C_CPP;
        }

        if (hasExtension(files, ".cs")) {
            return ProjectType.CSHARP;
        }

        if (hasExtension(files, ".php")) {
            return ProjectType.PHP;
        }

        return ProjectType.UNKNOWN;
    }

    private EnumSet<ProjectType> detectAllProjectTypes(Path root, List<Path> files) {
        EnumSet<ProjectType> types = EnumSet.noneOf(ProjectType.class);

        Set<String> fileNames = files.stream()
                .map(p -> p.getFileName().toString().toLowerCase())
                .collect(Collectors.toSet());

        if (fileNames.contains("angular.json")) {
            types.add(ProjectType.ANGULAR);
        }

        if (fileNames.contains("pom.xml")) {
            types.add(ProjectType.JAVA_MAVEN);
        }

        if (fileNames.contains("build.gradle") || fileNames.contains("build.gradle.kts")) {
            types.add(ProjectType.JAVA_GRADLE);
        }

        if (fileNames.contains("go.mod")) {
            types.add(ProjectType.GO);
        }

        if (fileNames.contains("cargo.toml")) {
            types.add(ProjectType.RUST);
        }

        if (fileNames.contains("gemfile") || fileNames.contains("rakefile")) {
            types.add(ProjectType.RUBY);
        }

        if (fileNames.stream().anyMatch(f -> f.endsWith(".csproj") || f.endsWith(".sln"))) {
            types.add(ProjectType.CSHARP);
        }

        if (fileNames.contains("composer.json")) {
            types.add(ProjectType.PHP);
        }

        if (fileNames.contains("cmakelists.txt") || fileNames.contains("makefile")) {
            types.add(ProjectType.C_CPP);
        }

        if (fileNames.contains("requirements.txt") ||
            fileNames.contains("pyproject.toml") ||
            fileNames.contains("setup.py") ||
            fileNames.contains("pipfile") ||
            hasExtension(files, ".py")) {
            types.add(ProjectType.PYTHON);
        }

        if (fileNames.contains("tsconfig.json") && !types.contains(ProjectType.ANGULAR)) {
            types.add(ProjectType.TYPESCRIPT);
        }

        if (fileNames.contains("package.json") &&
            !types.contains(ProjectType.ANGULAR) &&
            !types.contains(ProjectType.TYPESCRIPT)) {
            types.add(ProjectType.NODE_JS);
        }

        if (types.isEmpty()) {
            types.add(ProjectType.UNKNOWN);
        }

        return types;
    }

    private String detectMultipleTypes(Path root, List<Path> files) {
        EnumSet<ProjectType> types = detectAllProjectTypes(root, files);

        if (types.isEmpty() || (types.size() == 1 && types.contains(ProjectType.UNKNOWN))) {
            return ProjectType.UNKNOWN.getLabel();
        }

        types.remove(ProjectType.UNKNOWN);

        return types.stream()
                .map(ProjectType::getLabel)
                .collect(Collectors.joining(" + "));
    }

    private boolean hasExtension(List<Path> files, String ext) {
        String lowerExt = ext.toLowerCase();
        return files.stream()
                .anyMatch(p -> p.toString().toLowerCase().endsWith(lowerExt));
    }

    private void scanDirectoryRobustly(Path start, List<Path> collector) throws IOException {
        ioWarnings.clear();

        if (!Files.exists(start) || !Files.isDirectory(start)) {
            return;
        }

        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            private int currentDepth = 0;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (currentDepth > MAX_SCAN_DEPTH) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                String dirName = dir.getFileName() != null
                        ? dir.getFileName().toString()
                        : "";

                if (IGNORED_DIRS.contains(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                if (!dir.equals(start) && dirName.startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                currentDepth++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                currentDepth--;

                if (exc != null) {
                    ioWarnings.add("Warning al salir de: " + dir + " - " + exc.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName() != null
                        ? file.getFileName().toString()
                        : "";

                if (!isIgnoredFile(fileName)) {
                    collector.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                ioWarnings.add("Acceso denegado a: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public List<String> getLastScanWarnings() {
        return new ArrayList<>(ioWarnings);
    }

    private boolean isIgnoredFile(String name) {
        if (IGNORED_FILES.contains(name)) {
            return true;
        }

        String lowerName = name.toLowerCase();
        for (String ext : IGNORED_EXTENSIONS) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }

        return false;
    }

    private String generateTree(Path root, List<Path> files) {
        if (files.size() > MAX_TREE_LINES) {
            return generateStrategicSummary(root, files);
        }

        StringBuilder sb = new StringBuilder();

        int count = 0;
        for (Path file : files) {
            if (count >= MAX_FILES_IN_REPORT) {
                sb.append("... y ")
                  .append(files.size() - MAX_FILES_IN_REPORT)
                  .append(" archivos mas\n");
                break;
            }

            Path relative = root.relativize(file);
            sb.append("- ").append(relative.toString()).append("\n");
            count++;
        }

        return sb.toString();
    }

    private String generateStrategicSummary(Path root, List<Path> files) {
        StringBuilder sb = new StringBuilder();

        sb.append("ESTRUCTURA MASIVA (").append(files.size()).append(" archivos). ");
        sb.append("Resumen estrategico:\n\n");

        Set<String> configFiles = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "package.json",
            "tsconfig.json", "angular.json", "requirements.txt", "pyproject.toml",
            "Cargo.toml", "go.mod", "composer.json", "Gemfile", "CMakeLists.txt",
            "application.properties", "application.yml", ".env.example"
        );

        sb.append("ARCHIVOS DE CONFIGURACION:\n");
        int configCount = 0;
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            if (configFiles.contains(fileName) || fileName.endsWith(".xml") ||
                fileName.endsWith(".json") || fileName.endsWith(".yml") ||
                fileName.endsWith(".yaml") || fileName.endsWith(".properties")) {
                Path relative = root.relativize(file);
                if (relative.getNameCount() <= 2) {
                    sb.append("- ").append(relative.toString()).append("\n");
                    configCount++;
                    if (configCount >= 20) {
                        sb.append("  ... y mas archivos de configuracion\n");
                        break;
                    }
                }
            }
        }
        if (configCount == 0) {
            sb.append("  (ninguno detectado en raiz)\n");
        }

        sb.append("\nDIRECTORIOS PRINCIPALES:\n");
        java.util.Map<String, Long> dirCounts = files.stream()
                .map(root::relativize)
                .filter(p -> p.getNameCount() > 1)
                .collect(Collectors.groupingBy(
                        p -> p.getName(0).toString(),
                        Collectors.counting()
                ));

        dirCounts.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .forEach(e -> sb.append("- ").append(e.getKey())
                        .append("/ (").append(e.getValue()).append(" archivos)\n"));

        sb.append("\nARCHIVOS EN RAIZ:\n");
        int rootFileCount = 0;
        for (Path file : files) {
            Path relative = root.relativize(file);
            if (relative.getNameCount() == 1) {
                sb.append("- ").append(relative.toString()).append("\n");
                rootFileCount++;
                if (rootFileCount >= 20) {
                    sb.append("  ... y mas archivos en raiz\n");
                    break;
                }
            }
        }
        if (rootFileCount == 0) {
            sb.append("  (ninguno)\n");
        }

        return sb.toString();
    }

    public record ScanResult(
        Path rootPath,
        ProjectType projectType,
        List<Path> files,
        String error
    ) {
        public boolean isSuccess() {
            return error == null;
        }

        public int fileCount() {
            return files.size();
        }
    }
}
