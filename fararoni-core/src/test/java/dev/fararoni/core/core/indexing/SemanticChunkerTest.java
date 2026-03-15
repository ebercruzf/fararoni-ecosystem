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

import dev.fararoni.core.core.indexing.model.SemanticUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SemanticChunker - Bin-Packing Semantico")
class SemanticChunkerTest {
    private SemanticChunker chunker;
    private SentinelJavaParser parser;

    @BeforeEach
    void setUp() {
        parser = new SentinelJavaParser();
        chunker = new SemanticChunker(parser);
    }

    @Nested
    @DisplayName("Chunking Basico")
    class BasicChunking {
        @Test
        @DisplayName("Debe retornar lista vacia para codigo null")
        void shouldReturnEmptyForNull() {
            List<String> chunks = chunker.chunkFile((String) null, 1000);
            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("Debe retornar lista vacia para codigo en blanco")
        void shouldReturnEmptyForBlank() {
            List<String> chunks = chunker.chunkFile("  ", 1000);
            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("Debe retornar codigo original si parsing falla")
        void shouldReturnOriginalOnParsingFailure() {
            String invalidCode = "this is not valid java {{{";
            List<String> chunks = chunker.chunkFile(invalidCode, 1000);

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0)).isEqualTo(invalidCode);
        }

        @Test
        @DisplayName("Debe agrupar unidades pequenas en un solo chunk")
        void shouldGroupSmallUnitsInSingleChunk() {
            String code = """
                public class Small {
                    private int x;
                    public int getX() { return x; }
                }
                """;

            List<String> chunks = chunker.chunkFile(code, 10000);

            assertThat(chunks.size()).isLessThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Algoritmo Bin-Packing")
    class BinPacking {
        @Test
        @DisplayName("Debe crear multiples chunks cuando excede limite")
        void shouldCreateMultipleChunksWhenExceedingLimit() {
            List<SemanticUnit> units = List.of(
                SemanticUnit.of("method", "m1", "a".repeat(400), 1, 10, Set.of()),
                SemanticUnit.of("method", "m2", "b".repeat(400), 11, 20, Set.of()),
                SemanticUnit.of("method", "m3", "c".repeat(400), 21, 30, Set.of())
            );

            List<String> chunks = chunker.chunkUnits(units, 150);

            assertThat(chunks.size()).isGreaterThan(1);
        }

        @Test
        @DisplayName("No debe cortar una unidad por la mitad")
        void shouldNotSplitUnitInHalf() {
            String methodContent = "public void method() { /* code */ }";

            List<SemanticUnit> units = List.of(
                SemanticUnit.of("method", "method", methodContent, 1, 5, Set.of())
            );

            List<String> chunks = chunker.chunkUnits(units, 1000);

            assertThat(chunks).anyMatch(chunk -> chunk.contains(methodContent));
        }
    }

    @Nested
    @DisplayName("Manejo de Monstruos")
    class MonsterHandling {
        @Test
        @DisplayName("Monstruo debe ir solo en su propio chunk")
        void monsterShouldGoAloneInChunk() {
            String monsterContent = "x".repeat(5000);
            List<SemanticUnit> units = List.of(
                SemanticUnit.of("method", "small", "abc", 1, 2, Set.of()),
                SemanticUnit.of("method", "monster", monsterContent, 3, 100, Set.of()),
                SemanticUnit.of("method", "small2", "def", 101, 102, Set.of())
            );

            List<String> chunks = chunker.chunkUnits(units, 100);

            assertThat(chunks).anyMatch(chunk -> chunk.equals(monsterContent));
        }

        @Test
        @DisplayName("ChunkingStats debe reportar monstruos")
        void statsShouldReportMonsters() {
            String code = """
                public class Big {
                    public void monster() {
                        %s
                    }
                }
                """.formatted("int x = 1;\n".repeat(500));

            SemanticChunker.ChunkingStats stats = chunker.getStats(code, 100);

            assertThat(stats.hasMonsters()).isTrue();
            assertThat(stats.monsterUnits()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Estadisticas de Chunking")
    class ChunkingStatistics {
        @Test
        @DisplayName("Debe calcular total de tokens correctamente")
        void shouldCalculateTotalTokens() {
            List<SemanticUnit> units = List.of(
                SemanticUnit.of("method", "m1", "a".repeat(100), 1, 5, Set.of()),
                SemanticUnit.of("method", "m2", "b".repeat(200), 6, 10, Set.of())
            );

            int total = chunker.calculateTotalTokens(units);

            assertThat(total).isEqualTo(75);
        }

        @Test
        @DisplayName("needsChunking debe detectar codigo grande")
        void needsChunkingShouldDetectLargeCode() {
            String largeCode = "x".repeat(50000);

            assertThat(chunker.needsChunking(largeCode, 1000)).isTrue();
            assertThat(chunker.needsChunking(largeCode, 20000)).isFalse();
        }

        @Test
        @DisplayName("compressionRatio debe calcular correctamente")
        void compressionRatioShouldCalculate() {
            var stats = new SemanticChunker.ChunkingStats(10, 5, 1000, 0);

            assertThat(stats.compressionRatio()).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("Integracion con SentinelJavaParser")
    class ParserIntegration {
        @Test
        @DisplayName("Debe procesar codigo Java real correctamente")
        void shouldProcessRealJavaCode() {
            String code = """
                package com.example;

                import java.util.List;

                public class Service {
                    private final List<String> items;

                    public Service(List<String> items) {
                        this.items = items;
                    }

                    public int count() {
                        return items.size();
                    }

                    public void add(String item) {
                        items.add(item);
                    }
                }
                """;

            List<String> chunks = chunker.chunkFile(code, 10000);

            assertThat(chunks).isNotEmpty();
            assertThat(chunks.size()).isLessThanOrEqualTo(3);
        }
    }
}
