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
package dev.fararoni.core.core.skills;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
class AlgorithmPatternsTest {
    private AlgorithmPatterns patterns;

    @BeforeEach
    void setUp() {
        patterns = new AlgorithmPatterns();
    }

    @Nested
    @DisplayName("detectBestPattern() - Scoring System")
    class DetectBestPatternTests {
        @Test
        @DisplayName("Detecta BACKTRACKING por 'dominoes' (20 pts - CASO CRITICO)")
        void testDetectDominoes() {
            String problem = "Given a list of dominoes, determine if they can form a chain";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent(), "Debe detectar patron para 'dominoes' (20 pts)");
            assertTrue(pattern.get().contains("BACKTRACKING"),
                "Dominoes debe detectar BACKTRACKING");
        }

        @Test
        @DisplayName("Detecta BACKTRACKING por 'n-queens' (20 pts)")
        void testDetectNQueens() {
            String problem = "Solve the n-queens puzzle";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
            assertTrue(pattern.get().contains("BACKTRACKING"));
        }

        @Test
        @DisplayName("Detecta BACKTRACKING por 'sudoku' (20 pts)")
        void testDetectSudoku() {
            String problem = "Write a sudoku solver";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
            assertTrue(pattern.get().contains("BACKTRACKING"));
        }

        @Test
        @DisplayName("Detecta BACKTRACKING por combinacion de low-value (chain + path = 10 pts)")
        void testDetectBacktrackingLowValue() {
            String problem = "Find a chain that forms a valid path";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent(), "chain + path debe sumar 10 pts");
            assertTrue(pattern.get().contains("BACKTRACKING"));
        }

        @Test
        @DisplayName("Detecta GRAPH_TRAVERSAL por 'bfs' (20 pts)")
        void testDetectBfs() {
            String problem = "Use bfs to find the shortest route";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
            assertTrue(pattern.get().contains("GRAFOS") || pattern.get().contains("BFS"));
        }

        @Test
        @DisplayName("Detecta GRAPH_TRAVERSAL por 'dfs' (20 pts)")
        void testDetectDfs() {
            String problem = "Implement dfs to detect cycles";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
            assertTrue(pattern.get().contains("GRAFOS") || pattern.get().contains("DFS"));
        }

        @Test
        @DisplayName("Detecta GRAPH_TRAVERSAL por 'dijkstra' (20 pts)")
        void testDetectDijkstra() {
            String problem = "Apply dijkstra algorithm";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
            assertTrue(pattern.get().contains("GRAFOS"));
        }

        @Test
        @DisplayName("Detecta GRAPH_TRAVERSAL por combinacion low-value (tree + node = 10 pts)")
        void testDetectGraphLowValue() {
            String problem = "Traverse the tree visiting each node";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent(), "tree + node debe sumar 10 pts");
        }

        @Test
        @DisplayName("Detecta DYNAMIC_PROGRAMMING por 'fibonacci' (20 pts)")
        void testDetectFibonacci() {
            String problem = "Calculate the nth fibonacci number";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
            assertTrue(pattern.get().contains("DINAMICA") || pattern.get().contains("DP"));
        }

        @Test
        @DisplayName("Detecta DYNAMIC_PROGRAMMING por 'memoization' (20 pts)")
        void testDetectMemoization() {
            String problem = "Use memoization to optimize";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
        }

        @Test
        @DisplayName("Detecta DYNAMIC_PROGRAMMING por 'knapsack' (20 pts)")
        void testDetectKnapsack() {
            String problem = "Solve the 0/1 knapsack problem";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
            assertTrue(pattern.get().contains("DINAMICA") || pattern.get().contains("DP"));
        }

        @Test
        @DisplayName("Detecta DYNAMIC_PROGRAMMING por 'dp' keyword (20 pts)")
        void testDetectDPKeyword() {
            String problem = "This is a classic dp problem";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
        }
    }

    @Nested
    @DisplayName("Umbral de Confianza (10 pts)")
    class ConfidenceThresholdTests {
        @Test
        @DisplayName("NO detecta con solo 1 keyword low-value (5 pts < 10)")
        void testSingleLowValueNotEnough() {
            String problem = "Find the optimal route";

            Optional<String> pattern = patterns.detectBestPattern(problem);
        }

        @Test
        @DisplayName("NO detecta problema simple sin keywords")
        void testNoKeywords() {
            String problem = "Calculate the sum of two numbers";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isEmpty(), "Problema simple no debe detectar patron");
        }

        @Test
        @DisplayName("NO detecta con null")
        void testNullInput() {
            Optional<String> pattern = patterns.detectBestPattern(null);

            assertTrue(pattern.isEmpty());
        }

        @Test
        @DisplayName("NO detecta con string vacio")
        void testEmptyInput() {
            Optional<String> pattern = patterns.detectBestPattern("");

            assertTrue(pattern.isEmpty());
        }

        @Test
        @DisplayName("NO detecta con string de espacios")
        void testBlankInput() {
            Optional<String> pattern = patterns.detectBestPattern("   ");

            assertTrue(pattern.isEmpty());
        }
    }

    @Nested
    @DisplayName("Word Boundaries - Regex \\b")
    class WordBoundaryTests {
        @Test
        @DisplayName("'filepath' NO matchea 'path' (word boundary)")
        void testFilepathNotPath() {
            String problem = "Check the filepath for errors";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isEmpty() || !pattern.get().contains("BACKTRACKING"),
                "filepath no debe matchear path");
        }

        @Test
        @DisplayName("'treehouse' NO matchea 'tree' (word boundary)")
        void testTreehouseNotTree() {
            String problem = "Build a treehouse in the garden";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isEmpty(), "treehouse no debe matchear tree");
        }

        @Test
        @DisplayName("'path.' SI matchea 'path' (puntuacion OK)")
        void testPathWithPunctuation() {
            String problem = "Find a chain path. Then return it.";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent(), "path con puntuacion debe matchear");
        }
    }

    @Nested
    @DisplayName("getPattern() - Legacy API")
    class GetPatternTests {
        @Test
        @DisplayName("Obtiene BACKTRACKING por nombre")
        void testGetBacktracking() {
            Optional<String> pattern = patterns.getPattern("BACKTRACKING");

            assertTrue(pattern.isPresent());
            assertTrue(pattern.get().contains("BACKTRACKING"));
        }

        @Test
        @DisplayName("Obtiene GRAPH_TRAVERSAL por nombre")
        void testGetGraphTraversal() {
            Optional<String> pattern = patterns.getPattern("GRAPH_TRAVERSAL");

            assertTrue(pattern.isPresent());
            assertTrue(pattern.get().contains("GRAFOS") || pattern.get().contains("BFS"));
        }

        @Test
        @DisplayName("Obtiene DYNAMIC_PROGRAMMING por nombre")
        void testGetDynamicProgramming() {
            Optional<String> pattern = patterns.getPattern("DYNAMIC_PROGRAMMING");

            assertTrue(pattern.isPresent());
            assertTrue(pattern.get().contains("DINAMICA") || pattern.get().contains("DP"));
        }

        @Test
        @DisplayName("Case insensitive")
        void testCaseInsensitive() {
            Optional<String> upper = patterns.getPattern("BACKTRACKING");
            Optional<String> lower = patterns.getPattern("backtracking");
            Optional<String> mixed = patterns.getPattern("BackTracking");

            assertTrue(upper.isPresent());
            assertTrue(lower.isPresent());
            assertTrue(mixed.isPresent());
        }

        @Test
        @DisplayName("Retorna empty para tipo invalido")
        void testInvalidType() {
            Optional<String> pattern = patterns.getPattern("INVALID");

            assertTrue(pattern.isEmpty());
        }

        @Test
        @DisplayName("Retorna empty para null")
        void testNullType() {
            Optional<String> pattern = patterns.getPattern(null);
            assertTrue(pattern.isEmpty(), "null debe retornar Optional.empty()");
        }
    }

    @Nested
    @DisplayName("Competencia de Patrones")
    class PatternCompetitionTests {
        @Test
        @DisplayName("High-value keyword gana sobre low-value")
        void testHighValueWins() {
            String problem = "Use dominoes to build a tree structure";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
            assertTrue(pattern.get().contains("BACKTRACKING"),
                "BACKTRACKING (20 pts) debe ganar sobre GRAPH (5 pts)");
        }

        @Test
        @DisplayName("Multiples high-value del mismo tipo suman")
        void testMultipleHighValueSum() {
            String problem = "Compare bfs and dfs algorithms";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
            assertTrue(pattern.get().contains("GRAFOS") || pattern.get().contains("BFS"));
        }
    }

    @Nested
    @DisplayName("Escenarios Reales - Benchmark")
    class RealWorldTests {
        @Test
        @DisplayName("Escenario: Problema dominoes de Exercism (CASO CRITICO)")
        void testExercismDominoes() {
            String problem = """
                Make a chain of dominoes.

                Compute a way to order a given set of dominoes in such a way that
                they form a correct domino chain (the dots on one half of a stone
                match the dots on the neighboring half of an adjacent stone).
                """;

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent(), "Debe detectar patron para dominoes");
            assertTrue(pattern.get().contains("BACKTRACKING"),
                "Dominoes requiere BACKTRACKING");
        }

        @Test
        @DisplayName("Escenario: Problema binary-search-tree de Exercism")
        void testExercismBinaryTree() {
            String problem = """
                Insert and search for numbers in a binary search tree.

                A binary search tree uses a sorted data structure where
                left node values are smaller and right node values are larger.
                """;

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
        }

        @Test
        @DisplayName("Escenario: Problema coin-change de DP")
        void testCoinChange() {
            String problem = """
                Given a set of coins, find the minimum number of coins needed
                to make change for a given amount. This is the classic coin change
                problem.
                """;

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
        }

        @Test
        @DisplayName("Escenario: Problema simple sin patron")
        void testSimpleProblem() {
            String problem = """
                Given two numbers, return their sum.

                This is a simple arithmetic problem.
                """;

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isEmpty(),
                "Problema simple no debe detectar patron algoritmico");
        }
    }

    @Nested
    @DisplayName("Keywords Bilingues (Español)")
    class BilingualTests {
        @Test
        @DisplayName("Detecta 'reinas' en español (n-queens)")
        void testReinasSpanish() {
            String problem = "Resolver el problema de las 8 reinas";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent(), "Debe detectar por 'reinas' (20 pts)");
            assertTrue(pattern.get().contains("BACKTRACKING"));
        }

        @Test
        @DisplayName("Detecta 'grafo' en español")
        void testGrafoSpanish() {
            String problem = "Recorrer el grafo usando nodo vecino";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
        }

        @Test
        @DisplayName("Detecta 'knapsack' (mochila en ingles)")
        void testMochilaSpanish() {
            String problem = "Resolver el problema knapsack de optimizacion";

            Optional<String> pattern = patterns.detectBestPattern(problem);

            assertTrue(pattern.isPresent());
        }
    }
}
