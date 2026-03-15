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

import dev.fararoni.core.core.analysis.AnalysisContext;
import dev.fararoni.core.core.hybrid.IntentAwarePruner;
import dev.fararoni.core.core.hybrid.PruningStrategy;
import dev.fararoni.core.core.indexing.GitignoreFilter;
import dev.fararoni.core.core.indexing.MultiLanguageSurgicalParser;
import dev.fararoni.core.core.indexing.ParserFactory;
import dev.fararoni.core.core.indexing.SemanticChunker;
import dev.fararoni.core.core.indexing.SentinelJavaParser;
import dev.fararoni.core.core.indexing.SentinelVisitor;
import dev.fararoni.core.core.indexing.model.SemanticUnit;
import dev.fararoni.core.core.security.FileSecurityFilter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class CodeAnalysisService {
    private static final String JAVA_EXTENSION = ".java";

    private static final int CHARS_PER_TOKEN = 4;

    private static final int MAX_CACHE_SIZE = 100;

    private final SentinelJavaParser sentinelParser;

    private final SemanticChunker semanticChunker;

    private final StructureScannerService legacyMachete;

    private final MultiLanguageSurgicalParser treeSitterParser;

    private final FileSecurityFilter securityFilter;

    private GitignoreFilter gitignoreFilter;

    private final Map<Integer, AnalysisContext> contextCache = Collections.synchronizedMap(
        new LinkedHashMap<Integer, AnalysisContext>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, AnalysisContext> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        }
    );

    public CodeAnalysisService(SentinelJavaParser sentinelParser, StructureScannerService legacyMachete) {
        this.sentinelParser = sentinelParser;
        this.semanticChunker = new SemanticChunker(sentinelParser);
        this.legacyMachete = legacyMachete;
        this.treeSitterParser = new MultiLanguageSurgicalParser();
        this.securityFilter = FileSecurityFilter.standard();
        this.gitignoreFilter = GitignoreFilter.withDefaults();
    }

    public CodeAnalysisService() {
        this.sentinelParser = new SentinelJavaParser();
        this.semanticChunker = new SemanticChunker(sentinelParser);
        this.legacyMachete = new StructureScannerService();
        this.treeSitterParser = new MultiLanguageSurgicalParser();
        this.securityFilter = FileSecurityFilter.standard();
        this.gitignoreFilter = GitignoreFilter.withDefaults();
    }

    public void initGitignoreFilter(Path projectRoot) {
        if (projectRoot != null) {
            this.gitignoreFilter = GitignoreFilter.forProject(projectRoot);
        }
    }

    public AnalysisResult analyze(String filename, String content) {
        if (content == null || content.isBlank()) {
            return AnalysisResult.failure("Content is null or empty");
        }

        if (filename != null && filename.endsWith(JAVA_EXTENSION)) {
            return sentinelParser.parseStructure(content);
        }

        return legacyMachete.scanAsAnalysisResult(content);
    }

    public List<String> intelligentSplit(String filename, String content, int tokenLimit) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        if (filename != null && securityFilter.isSensitive(filename)) {
            return List.of();
        }

        if (filename != null && gitignoreFilter.isIgnored(filename)) {
            return List.of();
        }

        if (!needsChunking(content, tokenLimit)) {
            return List.of(content);
        }

        String extension = getExtension(filename);

        if (JAVA_EXTENSION.equals("." + extension)) {
            List<String> chunks = semanticChunker.chunkFile(content, tokenLimit);

            if (chunks.size() > 1 || (chunks.size() == 1 && !chunks.get(0).equals(content))) {
                return chunks;
            }
        }

        if (treeSitterParser.isSupported(extension)) {
            try {
                List<SemanticUnit> units = treeSitterParser.parse(content, extension);
                if (!units.isEmpty()) {
                    List<String> chunks = semanticChunker.chunkUnits(units, tokenLimit);
                    if (chunks.size() > 1 || (chunks.size() == 1 && !chunks.get(0).equals(content))) {
                        return chunks;
                    }
                }
            } catch (Exception e) {
            }
        }

        if (treeSitterParser.isHtmlLikeExtension(extension)) {
            try {
                List<SemanticUnit> units = treeSitterParser.parseHtml(content, extension);
                if (!units.isEmpty()) {
                    List<String> chunks = semanticChunker.chunkUnits(units, tokenLimit);
                    if (chunks.size() > 1 || (chunks.size() == 1 && !chunks.get(0).equals(content))) {
                        return chunks;
                    }
                }
            } catch (Exception e) {
            }
        }

        return splitByLines(content, tokenLimit);
    }

    public List<String> intelligentSplit(String filename, String content) {
        return intelligentSplit(filename, content, SemanticChunker.DEFAULT_MAX_TOKENS);
    }

    public boolean needsChunking(String content, int tokenLimit) {
        if (content == null) {
            return false;
        }
        int estimatedTokens = content.length() / CHARS_PER_TOKEN;
        return estimatedTokens > tokenLimit;
    }

    private List<String> splitByLines(String content, int tokenLimit) {
        int maxChars = tokenLimit * CHARS_PER_TOKEN;
        List<String> chunks = new ArrayList<>();

        String[] lines = content.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        int currentChars = 0;

        for (String line : lines) {
            int lineChars = line.length() + 1;

            if (currentChars + lineChars > maxChars && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString());
                currentChunk = new StringBuilder();
                currentChars = 0;
            }

            currentChunk.append(line).append("\n");
            currentChars += lineChars;
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }

    public SentinelJavaParser getSentinelParser() {
        return sentinelParser;
    }

    public SemanticChunker getSemanticChunker() {
        return semanticChunker;
    }

    public StructureScannerService getLegacyMachete() {
        return legacyMachete;
    }

    public MultiLanguageSurgicalParser getTreeSitterParser() {
        return treeSitterParser;
    }

    public FileSecurityFilter getSecurityFilter() {
        return securityFilter;
    }

    public GitignoreFilter getGitignoreFilter() {
        return gitignoreFilter;
    }

    public boolean isSensitiveFile(String filename) {
        return securityFilter.isSensitive(filename);
    }

    public boolean isIgnoredFile(String filename) {
        return gitignoreFilter.isIgnored(filename);
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

    public AnalysisDiagnostic diagnose(String filename, String content, int tokenLimit) {
        boolean isJava = filename != null && filename.endsWith(JAVA_EXTENSION);
        boolean needsChunk = needsChunking(content, tokenLimit);

        String strategy = "NONE";
        int chunksCount = 1;
        boolean astSuccess = false;

        if (needsChunk) {
            if (isJava) {
                List<String> astChunks = semanticChunker.chunkFile(content, tokenLimit);
                astSuccess = astChunks.size() > 1 || !astChunks.get(0).equals(content);
                strategy = astSuccess ? "AST_OJO" : "REGEX_MACHETE";
                chunksCount = astSuccess ? astChunks.size() : splitByLines(content, tokenLimit).size();
            } else {
                strategy = "REGEX_MACHETE";
                chunksCount = splitByLines(content, tokenLimit).size();
            }
        }

        return new AnalysisDiagnostic(
            filename,
            isJava,
            needsChunk,
            strategy,
            chunksCount,
            astSuccess,
            content != null ? content.length() / CHARS_PER_TOKEN : 0
        );
    }

    public record AnalysisDiagnostic(
        String filename,
        boolean isJavaFile,
        boolean needsChunking,
        String strategyUsed,
        int chunksGenerated,
        boolean astParsingSuccessful,
        int estimatedTokens
    ) {}

    public AnalysisContext createContext(String filename, String content) {
        return getOrCreateContext(filename, content);
    }

    private AnalysisContext getOrCreateContext(String filename, String content) {
        if (content == null || content.isBlank()) {
            return AnalysisContext.invalid(filename);
        }

        int contentHash = content.hashCode();

        return contextCache.computeIfAbsent(contentHash, hash -> {
            return ParserFactory.createContext(filename, content);
        });
    }

    public void invalidateCache(String content) {
        if (content != null) {
            contextCache.remove(content.hashCode());
        }
    }

    public void clearCache() {
        contextCache.clear();
    }

    public int getCacheSize() {
        return contextCache.size();
    }

    public UnifiedAnalysisResult analyzeAndPrune(
            String filename,
            String content,
            PruningStrategy strategy,
            String targetMethod) {
        AnalysisContext context = createContext(filename, content);

        if (!context.isValid()) {
            return UnifiedAnalysisResult.invalid(filename, "Parsing failed");
        }

        SentinelVisitor visitor = new SentinelVisitor();
        List<SemanticUnit> units = visitor.analyze(context);

        String prunedCode;
        try {
            prunedCode = IntentAwarePruner.getInstance()
                .applyStrategy(context, strategy, targetMethod);
        } catch (Exception e) {
            return UnifiedAnalysisResult.partialSuccess(
                filename,
                units,
                content,
                "Pruning failed: " + e.getMessage()
            );
        }

        return UnifiedAnalysisResult.success(filename, units, prunedCode);
    }

    public List<String> intelligentSplitWithContext(String filename, String content, int tokenLimit) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        if (!needsChunking(content, tokenLimit)) {
            return List.of(content);
        }

        if (filename != null && filename.endsWith(JAVA_EXTENSION)) {
            AnalysisContext context = createContext(filename, content);

            if (context.isValid()) {
                List<String> chunks = semanticChunker.chunkFile(context, tokenLimit);
                if (chunks.size() > 1 || (chunks.size() == 1 && !chunks.get(0).equals(content))) {
                    return chunks;
                }
            }
        }

        return splitByLines(content, tokenLimit);
    }

    public record UnifiedAnalysisResult(
        String filename,
        List<SemanticUnit> units,
        String prunedCode,
        boolean success,
        String errorMessage
    ) {
        public static UnifiedAnalysisResult success(
                String filename,
                List<SemanticUnit> units,
                String prunedCode) {
            return new UnifiedAnalysisResult(filename, units, prunedCode, true, null);
        }

        public static UnifiedAnalysisResult invalid(String filename, String error) {
            return new UnifiedAnalysisResult(filename, List.of(), null, false, error);
        }

        public static UnifiedAnalysisResult partialSuccess(
                String filename,
                List<SemanticUnit> units,
                String originalCode,
                String warning) {
            return new UnifiedAnalysisResult(filename, units, originalCode, true, warning);
        }
    }
}
