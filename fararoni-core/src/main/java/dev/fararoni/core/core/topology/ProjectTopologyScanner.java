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
package dev.fararoni.core.core.topology;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ProjectTopologyScanner {

    private static final int MAX_PACKAGE_DEPTH = 10;

    private static final Set<String> IGNORE_FILES = Set.of(
        ".git", ".gitignore", ".DS_Store", "Thumbs.db",
        ".idea", ".vscode", ".fararoni"
    );

    private static final Pattern POM_ARTIFACT_PATTERN =
        Pattern.compile("<artifactId>([^<]+)</artifactId>");

    private static final Pattern PACKAGE_JSON_NAME_PATTERN =
        Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    public ProjectTopology scan(Path root) {
        Objects.requireNonNull(root, "root no puede ser null");

        Path absoluteRoot = root.toAbsolutePath().normalize();

        boolean isEmpty = isEmpty(absoluteRoot);
        if (isEmpty) {
            return ProjectTopology.empty(absoluteRoot);
        }

        BuildSystem buildSystem = detectBuildSystem(absoluteRoot);

        Optional<Path> sourceRoot = findSourceRoot(absoluteRoot, buildSystem);

        List<String> packages = List.of();
        if (sourceRoot.isPresent() && buildSystem.usesJavaPackages()) {
            packages = findJavaPackages(sourceRoot.get());
        } else if (buildSystem.usesPythonModules()) {
            packages = findPythonModules(absoluteRoot);
        }

        boolean hasGit = Files.exists(absoluteRoot.resolve(".git"));

        Optional<String> projectName = extractProjectName(absoluteRoot, buildSystem);

        return new ProjectTopology(
            absoluteRoot,
            buildSystem,
            sourceRoot,
            packages,
            false,
            hasGit,
            projectName
        );
    }

    public BuildSystem detectBuildSystem(Path root) {
        Objects.requireNonNull(root, "root no puede ser null");

        for (BuildSystem bs : BuildSystem.values()) {
            if (bs == BuildSystem.UNKNOWN) continue;

            for (String marker : bs.getMarkerFiles()) {
                if (marker.contains("*")) {
                    if (matchesGlob(root, marker)) {
                        return bs;
                    }
                } else {
                    if (Files.exists(root.resolve(marker))) {
                        return bs;
                    }
                }
            }
        }

        return BuildSystem.UNKNOWN;
    }

    public Optional<Path> findSourceRoot(Path root, BuildSystem buildSystem) {
        Objects.requireNonNull(root, "root no puede ser null");

        if (buildSystem != null && buildSystem.isKnown()) {
            Path defaultRoot = root.resolve(buildSystem.getDefaultSourceRoot());
            if (Files.isDirectory(defaultRoot)) {
                return Optional.of(defaultRoot);
            }
        }

        for (String candidate : List.of("src/main/java", "src/main/kotlin", "src", "lib", "app")) {
            Path candidatePath = root.resolve(candidate);
            if (Files.isDirectory(candidatePath)) {
                return Optional.of(candidatePath);
            }
        }

        return Optional.empty();
    }

    public Optional<Path> findSourceRoot(Path root) {
        return findSourceRoot(root, detectBuildSystem(root));
    }

    public List<String> findJavaPackages(Path sourceRoot) {
        Objects.requireNonNull(sourceRoot, "sourceRoot no puede ser null");

        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }

        Set<String> packages = new HashSet<>();

        try (Stream<Path> walk = Files.walk(sourceRoot, MAX_PACKAGE_DEPTH)) {
            walk.filter(Files::isDirectory)
                .filter(dir -> containsJavaFiles(dir))
                .forEach(dir -> {
                    Path relative = sourceRoot.relativize(dir);
                    String packageName = relative.toString()
                        .replace("/", ".")
                        .replace("\\", ".");
                    if (!packageName.isEmpty()) {
                        packages.add(packageName);
                    }
                });
        } catch (IOException e) {
            return List.of();
        }

        List<String> sorted = new ArrayList<>(packages);
        sorted.sort(String::compareTo);
        return sorted;
    }

    public List<String> findExistingPackages(Path root) {
        Optional<Path> sourceRoot = findSourceRoot(root);
        if (sourceRoot.isEmpty()) {
            return List.of();
        }

        BuildSystem bs = detectBuildSystem(root);
        if (bs.usesJavaPackages()) {
            return findJavaPackages(sourceRoot.get());
        } else if (bs.usesPythonModules()) {
            return findPythonModules(root);
        }

        return List.of();
    }

    public List<String> findPythonModules(Path root) {
        Objects.requireNonNull(root, "root no puede ser null");

        Set<String> modules = new HashSet<>();

        try (Stream<Path> walk = Files.walk(root, MAX_PACKAGE_DEPTH)) {
            walk.filter(Files::isDirectory)
                .filter(dir -> Files.exists(dir.resolve("__init__.py")))
                .forEach(dir -> {
                    Path relative = root.relativize(dir);
                    String moduleName = relative.toString()
                        .replace("/", ".")
                        .replace("\\", ".");
                    if (!moduleName.isEmpty() && !moduleName.startsWith(".")) {
                        modules.add(moduleName);
                    }
                });
        } catch (IOException e) {
            return List.of();
        }

        List<String> sorted = new ArrayList<>(modules);
        sorted.sort(String::compareTo);
        return sorted;
    }

    public boolean isEmpty(Path directory) {
        Objects.requireNonNull(directory, "directory no puede ser null");

        if (!Files.isDirectory(directory)) {
            return true;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (!IGNORE_FILES.contains(name)) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return true;
        }
    }

    private boolean containsJavaFiles(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.java")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    private boolean matchesGlob(Path root, String pattern) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            PathMatcher matcher = root.getFileSystem().getPathMatcher("glob:" + pattern);
            for (Path entry : stream) {
                if (matcher.matches(entry.getFileName())) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private Optional<String> extractProjectName(Path root, BuildSystem buildSystem) {
        try {
            switch (buildSystem) {
                case MAVEN -> {
                    Path pom = root.resolve("pom.xml");
                    if (Files.exists(pom)) {
                        String content = Files.readString(pom);
                        Matcher m = POM_ARTIFACT_PATTERN.matcher(content);
                        if (m.find()) {
                            return Optional.of(m.group(1));
                        }
                    }
                }
                case GRADLE -> {
                    Path settings = root.resolve("settings.gradle.kts");
                    if (!Files.exists(settings)) {
                        settings = root.resolve("settings.gradle");
                    }
                    if (Files.exists(settings)) {
                        String content = Files.readString(settings);
                        Pattern pattern = Pattern.compile("rootProject\\.name\\s*=\\s*[\"']([^\"']+)[\"']");
                        Matcher m = pattern.matcher(content);
                        if (m.find()) {
                            return Optional.of(m.group(1));
                        }
                    }
                }
                case NPM -> {
                    Path pkg = root.resolve("package.json");
                    if (Files.exists(pkg)) {
                        String content = Files.readString(pkg);
                        Matcher m = PACKAGE_JSON_NAME_PATTERN.matcher(content);
                        if (m.find()) {
                            return Optional.of(m.group(1));
                        }
                    }
                }
                case CARGO -> {
                    Path cargo = root.resolve("Cargo.toml");
                    if (Files.exists(cargo)) {
                        String content = Files.readString(cargo);
                        Pattern pattern = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"");
                        Matcher m = pattern.matcher(content);
                        if (m.find()) {
                            return Optional.of(m.group(1));
                        }
                    }
                }
                case GO -> {
                    Path goMod = root.resolve("go.mod");
                    if (Files.exists(goMod)) {
                        String content = Files.readString(goMod);
                        Pattern pattern = Pattern.compile("module\\s+([^\\s]+)");
                        Matcher m = pattern.matcher(content);
                        if (m.find()) {
                            String moduleName = m.group(1);
                            int lastSlash = moduleName.lastIndexOf('/');
                            return Optional.of(lastSlash >= 0 ? moduleName.substring(lastSlash + 1) : moduleName);
                        }
                    }
                }
                default -> {
                    return Optional.of(root.getFileName().toString());
                }
            }
        } catch (IOException e) {
        }

        return Optional.ofNullable(root.getFileName()).map(Path::toString);
    }
}
