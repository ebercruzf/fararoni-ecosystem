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

import dev.fararoni.core.core.commands.TreeCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class StructureScannerService {
    public static final int DEFAULT_MAX_DEPTH = 5;

    public static final int DEFAULT_MAX_TOKENS = 50_000;

    private static final int CHARS_PER_TOKEN = 4;

    private static final Map<String, LanguagePatterns> LANGUAGE_PATTERNS = new HashMap<>();

    static {
        registerLanguage(
            List.of(".java"),
            "Java",
            List.of(
                Pattern.compile("^\\s*(public|protected|private)?\\s*(abstract|final)?\\s*(class|interface|enum|record)\\s+(\\w+)"),
                Pattern.compile("^\\s*(public|protected)\\s+.*\\s+(\\w+)\\s*\\([^)]*\\)\\s*(throws\\s+\\w+)?\\s*\\{?")
            )
        );

        registerLanguage(
            List.of(".py"),
            "Python",
            List.of(
                Pattern.compile("^class\\s+(\\w+)"),
                Pattern.compile("^\\s*def\\s+(\\w+)\\s*\\("),
                Pattern.compile("^\\s*async\\s+def\\s+(\\w+)\\s*\\(")
            )
        );

        registerLanguage(
            List.of(".js", ".jsx", ".ts", ".tsx", ".mjs"),
            "JavaScript/TypeScript",
            List.of(
                Pattern.compile("^\\s*(export\\s+)?(class|interface|type)\\s+(\\w+)"),
                Pattern.compile("^\\s*(export\\s+)?(async\\s+)?function\\s+(\\w+)"),
                Pattern.compile("^\\s*(export\\s+)?(const|let|var)\\s+(\\w+)\\s*=\\s*(async\\s+)?\\([^)]*\\)\\s*=>"),
                Pattern.compile("^\\s*(export\\s+)?(const|let|var)\\s+(\\w+)\\s*=\\s*(async\\s+)?function")
            )
        );

        registerLanguage(
            List.of(".go"),
            "Go",
            List.of(
                Pattern.compile("^func\\s+(\\(\\w+\\s+\\*?\\w+\\)\\s+)?(\\w+)\\s*\\("),
                Pattern.compile("^type\\s+(\\w+)\\s+(struct|interface)\\s*\\{")
            )
        );

        registerLanguage(
            List.of(".rs"),
            "Rust",
            List.of(
                Pattern.compile("^\\s*(pub\\s+)?(async\\s+)?fn\\s+(\\w+)"),
                Pattern.compile("^\\s*(pub\\s+)?struct\\s+(\\w+)"),
                Pattern.compile("^\\s*(pub\\s+)?enum\\s+(\\w+)"),
                Pattern.compile("^\\s*(pub\\s+)?trait\\s+(\\w+)"),
                Pattern.compile("^\\s*impl(<[^>]+>)?\\s+(\\w+)")
            )
        );

        registerLanguage(
            List.of(".kt", ".kts"),
            "Kotlin",
            List.of(
                Pattern.compile("^\\s*(class|interface|object|data\\s+class|sealed\\s+class)\\s+(\\w+)"),
                Pattern.compile("^\\s*(fun|suspend\\s+fun)\\s+(\\w+)\\s*\\(")
            )
        );

        registerLanguage(
            List.of(".c", ".h", ".cpp", ".hpp", ".cc", ".cxx"),
            "C/C++",
            List.of(
                Pattern.compile("^\\s*(class|struct)\\s+(\\w+)"),
                Pattern.compile("^\\s*(\\w+\\s+)+\\*?(\\w+)\\s*\\([^)]*\\)\\s*(const)?\\s*\\{")
            )
        );

        registerLanguage(
            List.of(".cs"),
            "C#",
            List.of(
                Pattern.compile("^\\s*(public|private|protected|internal)?\\s*(abstract|sealed|static)?\\s*(class|interface|struct|record|enum)\\s+(\\w+)"),
                Pattern.compile("^\\s*(public|protected|internal)\\s+.*\\s+(\\w+)\\s*\\([^)]*\\)")
            )
        );

        registerLanguage(
            List.of(".rb"),
            "Ruby",
            List.of(
                Pattern.compile("^\\s*class\\s+(\\w+)"),
                Pattern.compile("^\\s*module\\s+(\\w+)"),
                Pattern.compile("^\\s*def\\s+(\\w+)")
            )
        );

        registerLanguage(
            List.of(".php"),
            "PHP",
            List.of(
                Pattern.compile("^\\s*(abstract\\s+)?(class|interface|trait)\\s+(\\w+)"),
                Pattern.compile("^\\s*(public|protected|private)\\s+(static\\s+)?function\\s+(\\w+)")
            )
        );
    }

    private static void registerLanguage(List<String> extensions, String name, List<Pattern> patterns) {
        LanguagePatterns lp = new LanguagePatterns(name, patterns);
        for (String ext : extensions) {
            LANGUAGE_PATTERNS.put(ext.toLowerCase(), lp);
        }
    }

    public SkeletonResult generateSkeleton(Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath no puede ser null");

        String ext = getExtension(filePath);
        LanguagePatterns lp = LANGUAGE_PATTERNS.get(ext.toLowerCase());

        if (lp == null) {
            return new SkeletonResult(
                filePath,
                "// Lenguaje no soportado: " + ext,
                0,
                "unknown"
            );
        }

        List<String> lines = Files.readAllLines(filePath);
        List<String> signatures = extractSignatures(lines, lp.patterns());

        String skeleton = formatSkeleton(filePath, signatures);

        return new SkeletonResult(
            filePath,
            skeleton,
            signatures.size(),
            lp.name()
        );
    }

    public DirectoryScanResult scanDirectory(Path rootPath, int maxDepth, int maxTokens) throws IOException {
        Objects.requireNonNull(rootPath, "rootPath no puede ser null");

        List<SkeletonResult> results = new ArrayList<>();
        int totalChars = 0;
        int maxChars = maxTokens * CHARS_PER_TOKEN;

        try (Stream<Path> walk = Files.walk(rootPath, maxDepth)) {
            List<Path> files = walk
                .filter(Files::isRegularFile)
                .filter(this::isSupportedFile)
                .filter(this::isNotIgnored)
                .sorted()
                .collect(Collectors.toList());

            for (Path file : files) {
                try {
                    SkeletonResult result = generateSkeleton(file);
                    int resultChars = result.skeleton().length();

                    if (totalChars + resultChars > maxChars) {
                        break;
                    }

                    results.add(result);
                    totalChars += resultChars;
                } catch (IOException e) {
                }
            }
        }

        return new DirectoryScanResult(rootPath, results, totalChars, totalChars >= maxChars);
    }

    public DirectoryScanResult scanDirectory(Path rootPath) throws IOException {
        return scanDirectory(rootPath, DEFAULT_MAX_DEPTH, DEFAULT_MAX_TOKENS);
    }

    public String formatForContext(DirectoryScanResult scanResult) {
        StringBuilder sb = new StringBuilder();
        sb.append(">>> SKELETON MAP: ").append(scanResult.rootPath()).append("\n");
        sb.append("Files scanned: ").append(scanResult.results().size()).append("\n");
        sb.append("Total chars: ").append(scanResult.totalChars()).append("\n");
        if (scanResult.truncated()) {
            sb.append("[TRUNCATED - exceeded token limit]\n");
        }
        sb.append("\n");

        for (SkeletonResult result : scanResult.results()) {
            sb.append(result.skeleton()).append("\n");
        }

        return sb.toString();
    }

    public boolean isSupported(String extension) {
        return LANGUAGE_PATTERNS.containsKey(extension.toLowerCase());
    }

    public List<String> getSupportedExtensions() {
        return new ArrayList<>(LANGUAGE_PATTERNS.keySet());
    }

    public AnalysisResult scanAsAnalysisResult(String content) {
        if (content == null || content.isBlank()) {
            return AnalysisResult.failure("Content is null or empty");
        }

        return AnalysisResult.fallback(Map.of());
    }

    public AnalysisResult scanAsAnalysisResult(String content, String extension) {
        if (content == null || content.isBlank()) {
            return AnalysisResult.failure("Content is null or empty");
        }

        LanguagePatterns lp = LANGUAGE_PATTERNS.get(extension.toLowerCase());
        if (lp == null) {
            return AnalysisResult.failure("Unsupported language: " + extension);
        }

        List<String> lines = List.of(content.split("\n"));
        List<String> signatures = extractSignatures(lines, lp.patterns());

        return AnalysisResult.fallback(Map.of());
    }

    private List<String> extractSignatures(List<String> lines, List<Pattern> patterns) {
        List<String> signatures = new ArrayList<>();
        boolean inBlockComment = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.contains("/*") && !trimmed.contains("*/")) {
                inBlockComment = true;
                continue;
            }
            if (trimmed.contains("*/")) {
                inBlockComment = false;
                continue;
            }
            if (inBlockComment) continue;

            if (trimmed.startsWith("//") ||
                trimmed.startsWith("#") ||
                trimmed.startsWith("*") ||
                trimmed.startsWith("'''") ||
                trimmed.startsWith("\"\"\"")) {
                continue;
            }

            if (trimmed.startsWith("import ") ||
                trimmed.startsWith("from ") ||
                trimmed.startsWith("#include") ||
                trimmed.startsWith("using ") ||
                trimmed.startsWith("package ")) {
                continue;
            }

            for (Pattern p : patterns) {
                Matcher m = p.matcher(line);
                if (m.find()) {
                    String signature = cleanSignature(trimmed);
                    if (!signature.isEmpty()) {
                        signatures.add(signature);
                    }
                    break;
                }
            }
        }

        return signatures;
    }

    private String cleanSignature(String line) {
        int braceIndex = line.indexOf('{');
        if (braceIndex > 0) {
            line = line.substring(0, braceIndex).trim();
        }

        int commentIndex = line.indexOf("//");
        if (commentIndex > 0) {
            line = line.substring(0, commentIndex).trim();
        }

        return line;
    }

    private String formatSkeleton(Path filePath, List<String> signatures) {
        if (signatures.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// ").append(filePath.getFileName()).append("\n");
        for (String sig : signatures) {
            sb.append("  ").append(sig).append("\n");
        }
        return sb.toString();
    }

    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }

    private boolean isSupportedFile(Path path) {
        String ext = getExtension(path);
        return LANGUAGE_PATTERNS.containsKey(ext.toLowerCase());
    }

    private boolean isNotIgnored(Path path) {
        String pathStr = path.toString();
        return !pathStr.contains("/target/") &&
               !pathStr.contains("/build/") &&
               !pathStr.contains("/node_modules/") &&
               !pathStr.contains("/.git/") &&
               !pathStr.contains("/vendor/") &&
               !pathStr.contains("/__pycache__/") &&
               !pathStr.contains("/dist/") &&
               !pathStr.contains("/.idea/") &&
               !pathStr.contains("/.vscode/");
    }

    private record LanguagePatterns(String name, List<Pattern> patterns) {}

    public record SkeletonResult(
        Path filePath,
        String skeleton,
        int signatureCount,
        String language
    ) {}

    public record DirectoryScanResult(
        Path rootPath,
        List<SkeletonResult> results,
        int totalChars,
        boolean truncated
    ) {
        public int totalSignatures() {
            return results.stream()
                .mapToInt(SkeletonResult::signatureCount)
                .sum();
        }

        public int fileCount() {
            return results.size();
        }
    }
}
