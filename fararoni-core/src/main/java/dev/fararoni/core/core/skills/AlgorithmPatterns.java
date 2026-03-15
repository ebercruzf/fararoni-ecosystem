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

import dev.fararoni.core.core.config.HardwareTier;

import java.util.logging.Logger;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class AlgorithmPatterns {
    private static final Logger LOG = Logger.getLogger(AlgorithmPatterns.class.getName());

    private static final int CONFIDENCE_THRESHOLD = 10;

    private static final String PROTOCOLO_PARSEL =
            "\n═══════════════════════════════════════════════════════════════\n" +
                    "[!] PROTOCOLO DE ALTA SEGURIDAD (Parsel + Critical Thinking) [!]\n" +
                    "MANTRA OPERATIVO: Piensa, Analiza, Valida, Corrobora y NO SUPONGAS nada.\n" +
                    "Prevé los escenarios 10 pasos adelante como en el ajedrez.\n\n" +

                    "TU MISIÓN: No escribas la solución final inmediatamente (One-Shot).\n" +
                    "Sigue estrictamente estas 3 FASES DE EJECUCIÓN:\n\n" +

                    "BLUEPRINT (Arquitectura & Firmas)\n" +
                    "   - Genera SOLO las firmas de funciones/clases y constantes.\n" +
                    "   - Escribe los `docstrings` definiendo inputs y outputs.\n" +
                    "   - NO implementes la lógica interna aún (usa `pass` o `return placeholder`).\n\n" +

                    "VALIDATION VECTORS (Tests Mentales)\n" +
                    "   - Escribe 3 casos de prueba unitarios (pseudo-código) para validar tu Blueprint.\n" +
                    "   - Enfócate en los Edge Cases que rompieron intentos previos.\n\n" +

                    "IMPLEMENTATION (Llenado del Contrato)\n" +
                    "   - Ahora escribe el código final que satisface la FASE 1 y pasa la FASE 2.\n" +
                    "   - APLICA EXACTAMENTE LA ESTRUCTURA DEL SIGUIENTE EJEMPLO DE REFERENCIA.\n" +
                    "═══════════════════════════════════════════════════════════════\n";

    public enum Complexity {
        STANDARD,
        CRITICAL
    }

    public enum PatternType {
        BACKTRACKING(
                Complexity.CRITICAL,
                PROTOCOLO_PARSEL + """
            CONTEXTO: BACKTRACKING / CSP (Constraint Satisfaction Problem)
            
            [FASE 1 - BLUEPRINT] Define `solve(chain, remaining)` que retorne `Optional`.
            [FASE 2 - TESTS] Valida lista vacía vs lista sin solución.
            [FASE 3 - IMPLEMENTATION] Usa el siguiente código canónico de N-Queens como plantilla estructural:
            """,
                """
            def solve_n_queens(board, col, n):
                if col >= n: return True # Caso Base: Éxito
                for i in range(n):
                    if is_safe(board, i, col): # Poda
                        board[i][col] = 1      # DO
                        if solve_n_queens(board, col + 1, n): # RECURSE
                            return True
                        board[i][col] = 0      # UNDO (Backtrack)
                return False # Caso Base: Fallo en esta rama
            """,
                Set.of("dominoes", "n-queens", "sudoku", "tsp", "backtracking", "chain", "reinas"),
                Set.of("permutation", "subset", "combination", "conflict")
        ),

        WRAPPER_PROXY(
                Complexity.CRITICAL,
                PROTOCOLO_PARSEL + """
            CONTEXTO: PROXY PATTERN / IO WRAPPER
            
            [FASE 1 - BLUEPRINT] Define clase con `__init__(*args)` y `__getattr__`.
            [FASE 2 - TESTS] Valida que un Mock pasado al constructor no se rompa.
            [FASE 3 - IMPLEMENTATION] Usa este ejemplo de Wrapper Seguro como plantilla:
            """,
                """
            class LoggedFile:
                def __init__(self, *args, **kwargs):
                    # NO hereda de io.FileIO. Usa Composición.
                    # Acepta cualquier argumento para no romper Mocks.
                    self._delegate = open(*args, **kwargs) 
            
                def __getattr__(self, name):
                    # Delegación automática de métodos no interceptados
                    return getattr(self._delegate, name)
            
                def read(self, size=-1):
                    # Intercepción explícita
                    data = self._delegate.read(size)
                    print(f"Read {len(data)} bytes") # Lógica extra
                    return data
                    
                def __enter__(self):
                    return self
                
                def __exit__(self, exc_type, exc_val, exc_tb):
                    return self._delegate.__exit__(exc_type, exc_val, exc_tb)
            """,
                Set.of("wrapper", "proxy", "decorator", "metered", "delegation", "byte count"),
                Set.of("wrap", "delegate", "interface", "io", "socket", "file")
        ),

        DSL_CONSTANTS(
                Complexity.CRITICAL,
                PROTOCOLO_PARSEL + """
            CONTEXTO: DOMAIN SPECIFIC LANGUAGE (DSL)
            
            [FASE 1 - BLUEPRINT] Define constantes globales y clases contenedoras.
            [FASE 2 - TESTS] Valida `isinstance` y chequeos de tipos.
            [FASE 3 - IMPLEMENTATION] Usa este patrón de Builder como referencia:
            """,
                """
            # Constantes globales requeridas por el test
            TAG_TYPE = "standard" 
            
            class HtmlTag:
                def __init__(self, name, *args):
                    self.name = name
                    self.attributes = []
                    # Manejo flexible de argumentos posicionales
                    if len(args) == 1 and isinstance(args[0], list):
                        self.attributes = args[0]
                    elif args:
                        raise TypeError("Argumento inválido")
            
                def render(self):
                    return f"<{self.name}>"
            """,
                Set.of("dsl", "domain specific", "constants", "graphviz", "dot language"),
                Set.of("node", "edge", "attr", "grammar", "parse")
        ),

        GRAPH_TRAVERSAL(
                Complexity.STANDARD,
                "CONTEXTO: GRAFOS (BFS/DFS). Usa `visited` set.",
                """
            def bfs(start_node):
                queue = [start_node]
                visited = {start_node}
                while queue:
                    node = queue.pop(0)
                    for neighbor in node.neighbors:
                        if neighbor not in visited:
                            visited.add(neighbor)
                            queue.append(neighbor)
            """,
                Set.of("bfs", "dfs", "dijkstra", "a-star", "graph", "grafo"),
                Set.of("tree", "node", "traverse", "shortest", "nodo", "vecino", "neighbor")
        ),

        DYNAMIC_PROGRAMMING(
                Complexity.STANDARD,
                "CONTEXTO: DP (Memoization).",
                """
            memo = {}
            def fib(n):
                if n in memo: return memo[n]
                if n <= 1: return n
                memo[n] = fib(n-1) + fib(n-2)
                return memo[n]
            """,
                Set.of("dp", "dynamic programming", "memoization", "knapsack", "fibonacci", "coin change"),
                Set.of("optimal", "maximize", "subproblem", "cache")
        ),

        SLIDING_WINDOW(
                Complexity.STANDARD,
                "CONTEXTO: VENTANA DESLIZANTE.",
                """
            def max_sum(arr, k):
                window_sum = sum(arr[:k])
                max_s = window_sum
                for i in range(len(arr) - k):
                    window_sum = window_sum - arr[i] + arr[i+k]
                    max_s = max(max_s, window_sum)
                return max_s
            """,
                Set.of("sliding window", "longest substring", "min subarray"),
                Set.of("contiguous", "stream", "window")
        ),

        GREEDY(
                Complexity.STANDARD,
                "CONTEXTO: ALGORITMOS VORACES.",
                """
            # Ejemplo: Selección de Actividades
            # Ordenar por tiempo de finalización es clave
            activities.sort(key=lambda x: x[1])
            last_end = -1
            for start, end in activities:
                if start >= last_end:
                    selected.append((start, end))
                    last_end = end
            """,
                Set.of("greedy", "huffman", "dijkstra"),
                Set.of("local optimum", "best choice", "minimize cost")
        );

        private final Complexity complexity;
        private final String instructions;
        private final String canonicalCode;
        private final Set<String> highValueKeywords;
        private final Set<String> lowValueKeywords;

        PatternType(Complexity complexity, String instructions, String canonicalCode, Set<String> high, Set<String> low) {
            this.complexity = complexity;
            this.instructions = instructions;
            this.canonicalCode = canonicalCode;
            this.highValueKeywords = high;
            this.lowValueKeywords = low;
        }

        public Complexity complexity() { return complexity; }

        public String template() {
            return instructions +
                    "\n\n=== REFERENCIA DE ARQUITECTURA (CANONICAL EXAMPLE) ===\n" +
                    "```python\n" +
                    canonicalCode +
                    "\n```\n" +
                    "Adapta este patrón estructural a tu problema específico.\n";
        }
    }

    public record DetectionResult(PatternType type, int score, String template, Complexity complexity) {
        public boolean isConfident() { return score >= CONFIDENCE_THRESHOLD; }
    }

    public Optional<DetectionResult> detectBestPatternDetailed(String description) {
        if (description == null || description.isBlank()) return Optional.empty();

        String normalizedInput = description.toLowerCase();
        DetectionResult bestMatch = null;

        for (PatternType pattern : PatternType.values()) {
            int score = calculateScore(normalizedInput, pattern);
            if (score >= CONFIDENCE_THRESHOLD) {
                if (bestMatch == null || score > bestMatch.score()) {
                    bestMatch = new DetectionResult(pattern, score, pattern.template(), pattern.complexity());
                }
            }
        }

        if (bestMatch != null) {
            LOG.info(String.format("[AlgorithmPatterns] Detection: %s (Score: %d, Complexity: %s)",
                    bestMatch.type(), bestMatch.score(), bestMatch.complexity()));
            return Optional.of(bestMatch);
        }
        return Optional.empty();
    }

    public Optional<String> detectBestPattern(String description) {
        return detectBestPatternDetailed(description).map(DetectionResult::template);
    }

    private int calculateScore(String text, PatternType pattern) {
        int score = 0;
        for (String k : pattern.highValueKeywords) if (containsWholeWord(text, k)) score += 20;
        for (String k : pattern.lowValueKeywords) if (containsWholeWord(text, k)) score += 5;
        return score;
    }

    private boolean containsWholeWord(String text, String keyword) {
        return Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b").matcher(text).find();
    }

    public Optional<String> getPattern(String typeName) {
        try { return Optional.of(PatternType.valueOf(typeName.toUpperCase()).template()); }
        catch (Exception e) { return Optional.empty(); }
    }
}
