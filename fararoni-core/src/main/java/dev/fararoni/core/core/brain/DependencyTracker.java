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
package dev.fararoni.core.core.brain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class DependencyTracker {
    private static final Logger LOG = Logger.getLogger(DependencyTracker.class.getName());

    private static final int MAX_FILES_SCANNED = 50;

    private static final Pattern PYTHON_IMPORT = Pattern.compile(
        "^(?:import|from)\\s+([a-zA-Z0-9_\\.]+)",
        Pattern.MULTILINE
    );

    private static final Pattern JAVA_IMPORT = Pattern.compile(
        "^import\\s+([a-zA-Z0-9_\\.]+);",
        Pattern.MULTILINE
    );

    private static final Pattern JS_IMPORT = Pattern.compile(
        "(?:import|require)\\s*(?:\\{[^}]*\\}\\s*from)?\\s*['\"]([^'\"]+)['\"]",
        Pattern.MULTILINE
    );

    private static final Pattern GO_IMPORT = Pattern.compile(
        "import\\s+[\"']([^\"']+)[\"']",
        Pattern.MULTILINE
    );

    public Set<String> findImpactRadius(Path root, String seedFile, int depth) {
        Objects.requireNonNull(root, "root no puede ser null");
        Objects.requireNonNull(seedFile, "seedFile no puede ser null");

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(seedFile);
        visited.add(seedFile);

        int currentDepth = 0;

        while (!queue.isEmpty() && currentDepth < depth) {
            if (visited.size() >= MAX_FILES_SCANNED) {
                LOG.warning("[BRAIN] Limite de archivos alcanzado (" + MAX_FILES_SCANNED +
                           "). Deteniendo expansion del grafo.");
                break;
            }

            int levelSize = queue.size();

            for (int i = 0; i < levelSize; i++) {
                String currentFile = queue.poll();
                List<String> neighbors = findDependencies(root, currentFile);

                for (String neighbor : neighbors) {
                    if (!visited.contains(neighbor) && visited.size() < MAX_FILES_SCANNED) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
            currentDepth++;
        }

        LOG.info(() -> String.format(
            "[BRAIN] Radio de Impacto: %d archivos encontrados desde '%s' (depth=%d)",
            visited.size(), seedFile, depth
        ));

        return visited;
    }

    public List<String> findDependents(Path root, String targetFile) {
        Objects.requireNonNull(root, "root no puede ser null");
        Objects.requireNonNull(targetFile, "targetFile no puede ser null");

        List<String> dependents = new ArrayList<>();
        String targetName = extractModuleName(targetFile);

        try {
            Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(this::isSourceFile)
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        if (content.contains(targetName)) {
                            String relativePath = root.relativize(file).toString();
                            if (!relativePath.equals(targetFile)) {
                                dependents.add(relativePath);
                            }
                        }
                    } catch (IOException e) {
                    }
                });
        } catch (IOException e) {
            LOG.warning("[BRAIN] Error buscando dependientes: " + e.getMessage());
        }

        return dependents;
    }

    private String filterComments(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }

        StringBuilder filtered = new StringBuilder();
        String[] lines = code.split("\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") ||
                trimmed.startsWith("#") ||
                trimmed.startsWith("/*") ||
                trimmed.startsWith("*") ||
                trimmed.startsWith("'''") ||
                trimmed.startsWith("\"\"\"")) {
                continue;
            }
            filtered.append(line).append("\n");
        }

        return filtered.toString();
    }

    private List<String> findDependencies(Path root, String filename) {
        List<String> found = new ArrayList<>();

        try {
            Path file = root.resolve(filename);
            if (!Files.exists(file)) {
                return found;
            }

            String content = Files.readString(file);
            String extension = getExtension(filename);

            String filteredContent = filterComments(content);

            Pattern pattern = selectPattern(extension);
            if (pattern == null) {
                return found;
            }

            Matcher m = pattern.matcher(filteredContent);
            while (m.find()) {
                String importPath = m.group(1);
                String resolvedPath = resolveImportPath(root, importPath, extension);

                if (resolvedPath != null && Files.exists(root.resolve(resolvedPath))) {
                    found.add(resolvedPath);
                }
            }
        } catch (IOException e) {
            LOG.fine("[BRAIN] Error leyendo archivo para dependencias: " + filename);
        }

        return found;
    }

    private Pattern selectPattern(String extension) {
        return switch (extension.toLowerCase()) {
            case "py" -> PYTHON_IMPORT;
            case "java" -> JAVA_IMPORT;
            case "js", "jsx", "ts", "tsx", "mjs" -> JS_IMPORT;
            case "go" -> GO_IMPORT;
            default -> null;
        };
    }

    private String resolveImportPath(Path root, String importPath, String extension) {
        importPath = importPath.trim();

        return switch (extension.toLowerCase()) {
            case "py" -> {
                String pythonPath = importPath.replace(".", "/") + ".py";
                yield Files.exists(root.resolve(pythonPath)) ? pythonPath : null;
            }
            case "java" -> {
                String javaPath = importPath.replace(".", "/") + ".java";
                Path srcMain = root.resolve("src/main/java").resolve(javaPath);
                if (Files.exists(srcMain)) {
                    yield "src/main/java/" + javaPath;
                }
                yield Files.exists(root.resolve(javaPath)) ? javaPath : null;
            }
            case "js", "jsx", "ts", "tsx", "mjs" -> {
                if (importPath.startsWith("./") || importPath.startsWith("../")) {
                    String basePath = importPath;
                    for (String ext : new String[]{".js", ".jsx", ".ts", ".tsx", "/index.js", "/index.ts"}) {
                        String fullPath = basePath + ext;
                        if (Files.exists(root.resolve(fullPath))) {
                            yield fullPath;
                        }
                    }
                }
                yield null;
            }
            case "go" -> {
                String goPath = importPath + ".go";
                yield Files.exists(root.resolve(goPath)) ? goPath : null;
            }
            default -> null;
        };
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }

    private boolean isSourceFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".py") || name.endsWith(".java") ||
               name.endsWith(".js") || name.endsWith(".jsx") ||
               name.endsWith(".ts") || name.endsWith(".tsx") ||
               name.endsWith(".go");
    }

    private String extractModuleName(String filePath) {
        String name = filePath;
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            name = name.substring(0, lastDot);
        }
        return name;
    }
}
