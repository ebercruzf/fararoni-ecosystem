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

import dev.fararoni.core.core.analysis.AnalysisContext;
import dev.fararoni.core.core.indexing.model.SemanticUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SemanticChunker {
    public static final int DEFAULT_MAX_TOKENS = 3000;

    private static final String UNIT_SEPARATOR = "\n\n";

    private final SentinelJavaParser parser;

    public SemanticChunker(SentinelJavaParser parser) {
        this.parser = parser;
    }

    public List<String> chunkFile(String sourceCode, int maxTokens) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return List.of();
        }

        List<SemanticUnit> units = parser.parse(sourceCode);

        if (units.isEmpty()) {
            return List.of(sourceCode);
        }

        return chunkUnits(units, maxTokens);
    }

    public List<String> chunkFile(String sourceCode) {
        return chunkFile(sourceCode, DEFAULT_MAX_TOKENS);
    }

    public List<String> chunkFile(AnalysisContext context, int maxTokens) {
        if (context == null || !context.isValid()) {
            return List.of();
        }

        SentinelVisitor visitor = new SentinelVisitor();
        List<SemanticUnit> units = visitor.analyze(context);

        if (units.isEmpty()) {
            return List.of(context.compilationUnit().toString());
        }

        return chunkUnits(units, maxTokens);
    }

    public List<String> chunkFile(AnalysisContext context) {
        return chunkFile(context, DEFAULT_MAX_TOKENS);
    }

    public List<String> chunkUnits(List<SemanticUnit> units, int maxTokens) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int currentTokens = 0;

        for (SemanticUnit unit : units) {
            int unitTokens = unit.tokenEstimate();

            if (unitTokens > maxTokens) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                    currentTokens = 0;
                }

                chunks.add(unit.content());
                continue;
            }

            if (currentTokens + unitTokens > maxTokens) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                }
                currentChunk = new StringBuilder();
                currentTokens = 0;
            }

            if (currentChunk.length() > 0) {
                currentChunk.append(UNIT_SEPARATOR);
            }
            currentChunk.append(unit.content());
            currentTokens += unitTokens;
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    public int calculateTotalTokens(List<SemanticUnit> units) {
        return units.stream()
            .mapToInt(SemanticUnit::tokenEstimate)
            .sum();
    }

    public boolean needsChunking(String sourceCode, int maxTokens) {
        if (sourceCode == null) {
            return false;
        }
        int estimatedTokens = sourceCode.length() / 4;
        return estimatedTokens > maxTokens;
    }

    public ChunkingStats getStats(String sourceCode, int maxTokens) {
        List<SemanticUnit> units = parser.parse(sourceCode);
        List<String> chunks = chunkUnits(units, maxTokens);

        int totalTokens = calculateTotalTokens(units);
        int monsters = (int) units.stream()
            .filter(u -> u.tokenEstimate() > maxTokens)
            .count();

        return new ChunkingStats(
            units.size(),
            chunks.size(),
            totalTokens,
            monsters
        );
    }

    public record ChunkingStats(
        int totalUnits,
        int totalChunks,
        int totalTokens,
        int monsterUnits
    ) {
        public boolean hasMonsters() {
            return monsterUnits > 0;
        }

        public double compressionRatio() {
            return totalUnits > 0 ? (double) totalChunks / totalUnits : 0;
        }
    }
}
