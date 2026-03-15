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
package dev.fararoni.core.core.indexing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class GitignoreFilter {
    private static final String GITIGNORE_FILENAME = ".gitignore";

    private static final Set<String> DEFAULT_IGNORED_DIRS = Set.of(
        "node_modules", ".git", ".svn", ".hg",
        "target", "build", "dist", "out",
        ".idea", ".vscode", ".eclipse",
        "__pycache__", ".pytest_cache", ".mypy_cache",
        "venv", ".venv", "env", ".env",
        ".gradle", ".m2",
        "vendor", "deps",
        ".next", ".nuxt",
        "coverage", ".nyc_output"
    );

    private static final Set<String> DEFAULT_IGNORED_PATTERNS = Set.of(
        "*.pyc", "*.pyo", "*.class",
        "*.log", "*.tmp", "*.temp",
        "*.swp", "*.swo",
        ".DS_Store", "Thumbs.db"
    );

    private final Path projectRoot;

    private final List<CompiledPattern> includePatterns;

    private final List<CompiledPattern> excludePatterns;

    private final boolean hasGitignore;

    private GitignoreFilter(
            Path projectRoot,
            List<CompiledPattern> includePatterns,
            List<CompiledPattern> excludePatterns,
            boolean hasGitignore) {
        this.projectRoot = projectRoot;
        this.includePatterns = List.copyOf(includePatterns);
        this.excludePatterns = List.copyOf(excludePatterns);
        this.hasGitignore = hasGitignore;
    }

    public static GitignoreFilter forProject(Path projectRoot) {
        Path gitignorePath = projectRoot.resolve(GITIGNORE_FILENAME);

        List<CompiledPattern> includes = new ArrayList<>();
        List<CompiledPattern> excludes = new ArrayList<>();
        boolean hasGitignore = false;

        if (Files.exists(gitignorePath)) {
            try {
                List<String> lines = Files.readAllLines(gitignorePath);
                parseGitignore(lines, includes, excludes);
                hasGitignore = true;
            } catch (IOException e) {
            }
        }

        addDefaultPatterns(excludes);

        return new GitignoreFilter(
            projectRoot.toAbsolutePath().normalize(),
            includes,
            excludes,
            hasGitignore
        );
    }

    public static GitignoreFilter withDefaults() {
        List<CompiledPattern> excludes = new ArrayList<>();
        addDefaultPatterns(excludes);
        return new GitignoreFilter(Path.of("."), List.of(), excludes, false);
    }

    public boolean isIgnored(Path path) {
        if (path == null) {
            return false;
        }

        String relativePath = makeRelative(path);
        return isIgnored(relativePath);
    }

    public boolean isIgnored(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }

        String normalized = normalizePath(relativePath);

        for (CompiledPattern pattern : includePatterns) {
            if (pattern.matches(normalized)) {
                return false;
            }
        }

        for (CompiledPattern pattern : excludePatterns) {
            if (pattern.matches(normalized)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasGitignore() {
        return hasGitignore;
    }

    public int getPatternCount() {
        return excludePatterns.size() + includePatterns.size();
    }

    private static void parseGitignore(
            List<String> lines,
            List<CompiledPattern> includes,
            List<CompiledPattern> excludes) {
        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            boolean isNegation = trimmed.startsWith("!");
            if (isNegation) {
                trimmed = trimmed.substring(1);
            }

            CompiledPattern pattern = compilePattern(trimmed);
            if (pattern != null) {
                if (isNegation) {
                    includes.add(pattern);
                } else {
                    excludes.add(pattern);
                }
            }
        }
    }

    private static void addDefaultPatterns(List<CompiledPattern> excludes) {
        for (String dir : DEFAULT_IGNORED_DIRS) {
            CompiledPattern pattern = compilePattern(dir + "/");
            if (pattern != null) {
                excludes.add(pattern);
            }
            pattern = compilePattern(dir);
            if (pattern != null) {
                excludes.add(pattern);
            }
        }

        for (String pat : DEFAULT_IGNORED_PATTERNS) {
            CompiledPattern pattern = compilePattern(pat);
            if (pattern != null) {
                excludes.add(pattern);
            }
        }
    }

    private static CompiledPattern compilePattern(String gitignorePattern) {
        if (gitignorePattern == null || gitignorePattern.isBlank()) {
            return null;
        }

        String pattern = gitignorePattern.trim();
        boolean isDirectory = pattern.endsWith("/");
        boolean isRooted = pattern.startsWith("/");

        if (isRooted) {
            pattern = pattern.substring(1);
        }
        if (isDirectory) {
            pattern = pattern.substring(0, pattern.length() - 1);
        }

        StringBuilder regex = new StringBuilder();

        if (!isRooted) {
            regex.append("(^|.*/)");
        } else {
            regex.append("^");
        }

        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);

            switch (c) {
                case '*' -> {
                    if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++;
                        if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '/') {
                            i++;
                        }
                    } else {
                        regex.append("[^/]*");
                    }
                }
                case '?' -> regex.append("[^/]");
                case '.' -> regex.append("\\.");
                case '/' -> regex.append("/");
                case '[', ']', '(', ')', '{', '}', '+', '^', '$', '|', '\\' ->
                    regex.append("\\").append(c);
                default -> regex.append(c);
            }
        }

        if (isDirectory) {
            regex.append("(/.*)?$");
        } else {
            regex.append("(/.*)?$");
        }

        try {
            return new CompiledPattern(
                gitignorePattern,
                Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE)
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String makeRelative(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized).toString();
        }
        return path.toString();
    }

    private String normalizePath(String path) {
        return path.replace('\\', '/');
    }

    private record CompiledPattern(String original, Pattern regex) {
        boolean matches(String path) {
            return regex.matcher(path).find();
        }
    }
}
