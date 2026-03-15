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
package dev.fararoni.core.core.middleware;

import dev.fararoni.core.core.config.HardwareTier;
import dev.fararoni.core.core.skills.AlgorithmPatterns;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
class PromptInjectorTest {
    private PromptInjector injector;

    @BeforeEach
    void setUp() {
        injector = new PromptInjector();
    }

    @Nested
    @DisplayName("inject() - API Principal")
    class InjectTests {
        @Test
        @DisplayName("Inyecta patron con tono DIRECTIVO para LOW_RESOURCE")
        void testInjectLowResource() {
            String basePrompt = "Resuelve el problema";
            String pattern = "PATRON DE PRUEBA";

            String result = injector.inject(basePrompt, pattern, HardwareTier.LOW_RESOURCE);

            assertTrue(result.contains("INSTRUCCION ESTRICTA"),
                "LOW_RESOURCE debe usar tono DIRECTIVO");
            assertTrue(result.contains(pattern),
                "Debe contener el patron");
            assertTrue(result.contains(basePrompt),
                "Debe contener el prompt base");
            assertTrue(result.contains("PROBLEMA A RESOLVER"),
                "Debe tener separador");
        }

        @Test
        @DisplayName("Inyecta patron con tono CONTEXTUAL para MEDIUM")
        void testInjectMedium() {
            String basePrompt = "Resuelve el problema";
            String pattern = "PATRON DE PRUEBA";

            String result = injector.inject(basePrompt, pattern, HardwareTier.MEDIUM);

            assertTrue(result.contains("CONTEXTO"),
                "MEDIUM debe usar tono CONTEXTUAL");
            assertTrue(result.contains(pattern));
            assertTrue(result.contains(basePrompt));
        }

        @Test
        @DisplayName("Inyecta patron con tono ARQUITECTONICO para HIGH_PERFORMANCE")
        void testInjectHighPerformance() {
            String basePrompt = "Resuelve el problema";
            String pattern = "PATRON DE PRUEBA";

            String result = injector.inject(basePrompt, pattern, HardwareTier.HIGH_PERFORMANCE);

            assertTrue(result.contains("TIP DE ARQUITECTURA"),
                "HIGH_PERFORMANCE debe usar tono ARQUITECTONICO");
            assertTrue(result.contains(pattern));
            assertTrue(result.contains(basePrompt));
        }

        @Test
        @DisplayName("Retorna prompt base si patron es null")
        void testInjectNullPattern() {
            String basePrompt = "Resuelve el problema";

            String result = injector.inject(basePrompt, null, HardwareTier.LOW_RESOURCE);

            assertEquals(basePrompt, result);
        }

        @Test
        @DisplayName("Retorna prompt base si patron esta vacio")
        void testInjectEmptyPattern() {
            String basePrompt = "Resuelve el problema";

            String result = injector.inject(basePrompt, "", HardwareTier.LOW_RESOURCE);

            assertEquals(basePrompt, result);
        }

        @Test
        @DisplayName("Retorna prompt base si patron es espacios")
        void testInjectBlankPattern() {
            String basePrompt = "Resuelve el problema";

            String result = injector.inject(basePrompt, "   ", HardwareTier.LOW_RESOURCE);

            assertEquals(basePrompt, result);
        }

        @Test
        @DisplayName("Usa MEDIUM como default si tier es null")
        void testInjectNullTier() {
            String basePrompt = "Resuelve el problema";
            String pattern = "PATRON DE PRUEBA";

            String result = injector.inject(basePrompt, pattern, null);

            assertTrue(result.contains("CONTEXTO"),
                "Tier null debe usar MEDIUM (CONTEXTUAL)");
        }

        @Test
        @DisplayName("Maneja prompt base null")
        void testInjectNullBasePrompt() {
            String pattern = "PATRON DE PRUEBA";

            String result = injector.inject(null, pattern, HardwareTier.LOW_RESOURCE);

            assertTrue(result.contains(pattern));
            assertTrue(result.contains("INSTRUCCION ESTRICTA"));
        }
    }

    @Nested
    @DisplayName("injectWithAutoDetection() - Auto-Deteccion")
    class AutoDetectionTests {
        @Test
        @DisplayName("Detecta BACKTRACKING e inyecta con tono correcto")
        void testAutoDetectBacktracking() {
            String basePrompt = "Implementa la solucion";
            String problemDescription = "dominoes chain backtracking";
            String modelName = "qwen2.5-coder:1.5b";

            String result = injector.injectWithAutoDetection(
                basePrompt, problemDescription, modelName);

            assertTrue(result.contains("INSTRUCCION ESTRICTA"),
                "1.5B debe usar tono DIRECTIVO");
            assertTrue(result.contains("BACKTRACKING"),
                "Debe detectar e inyectar patron BACKTRACKING");
            assertTrue(result.contains(basePrompt));
        }

        @Test
        @DisplayName("Detecta GRAPH_TRAVERSAL con modelo grande")
        void testAutoDetectGraphWithLargeModel() {
            String basePrompt = "Implementa la solucion";
            String problemDescription = "bfs dijkstra graph shortest path";
            String modelName = "qwen2.5-coder:32b";

            String result = injector.injectWithAutoDetection(
                basePrompt, problemDescription, modelName);

            assertTrue(result.contains("TIP DE ARQUITECTURA"),
                "32B debe usar tono ARQUITECTONICO para patterns STANDARD");
            assertTrue(result.contains("GRAFOS") || result.contains("BFS"),
                "Debe detectar patron GRAPH_TRAVERSAL");
        }

        @Test
        @DisplayName("Detecta DYNAMIC_PROGRAMMING con modelo medio")
        void testAutoDetectDPWithMediumModel() {
            String basePrompt = "Implementa la solucion";
            String problemDescription = "fibonacci memoization dynamic programming";
            String modelName = "qwen2.5-coder:7b";

            String result = injector.injectWithAutoDetection(
                basePrompt, problemDescription, modelName);

            assertTrue(result.contains("CONTEXTO"),
                "7B debe usar tono CONTEXTUAL");
            assertTrue(result.contains("DP") || result.contains("OPTIMIZACION"),
                "Debe detectar patron DP");
        }

        @Test
        @DisplayName("Retorna prompt base si no detecta patron")
        void testAutoDetectNoPattern() {
            String basePrompt = "Suma dos numeros";
            String problemDescription = "Calculate the sum of two numbers";
            String modelName = "qwen2.5-coder:1.5b";

            String result = injector.injectWithAutoDetection(
                basePrompt, problemDescription, modelName);

            assertEquals(basePrompt, result,
                "Sin patron detectado, retorna prompt original");
        }
    }

    @Nested
    @DisplayName("Escenarios Reales - Benchmark")
    class RealWorldTests {
        @Test
        @DisplayName("Escenario: dominoes con Rabbit 1.5B")
        void testDominoesWithRabbit() {
            String basePrompt = "def can_chain(dominoes):";
            String problemDescription = "dominoes chain backtracking";

            String result = injector.injectWithAutoDetection(
                basePrompt, problemDescription, "qwen2.5-coder:1.5b");

            assertTrue(result.contains("INSTRUCCION ESTRICTA"),
                "CRITICAL pattern debe forzar instrucciones estrictas");
            assertTrue(result.contains("BACKTRACKING"),
                "Debe incluir patron BACKTRACKING");
            assertTrue(result.contains("PROBLEMA A RESOLVER"),
                "Debe tener separador claro");
            assertTrue(result.contains(basePrompt),
                "Debe incluir el codigo base");
        }

        @Test
        @DisplayName("Escenario: dominoes con Turtle 32B - CRITICAL pattern fuerza LOW_RESOURCE")
        void testDominoesWithTurtle() {
            String basePrompt = "def can_chain(dominoes):";
            String problemDescription = "dominoes chain backtracking";

            String result = injector.injectWithAutoDetection(
                basePrompt, problemDescription, "qwen2.5-coder:32b");

            assertTrue(result.contains("INSTRUCCION ESTRICTA"),
                "CRITICAL pattern debe forzar LOW_RESOURCE incluso para 32B");
            assertTrue(result.contains("BACKTRACKING"));
        }

        @Test
        @DisplayName("Escenario: problema simple no inyecta nada")
        void testSimpleProblemNoInjection() {
            String basePrompt = "def add(a, b): return a + b";
            String problemDescription = "Add two numbers together";

            String result = injector.injectWithAutoDetection(
                basePrompt, problemDescription, "qwen2.5-coder:1.5b");

            assertEquals(basePrompt, result,
                "Problema simple no debe modificar el prompt");
            assertFalse(result.contains("INSTRUCCION"),
                "No debe tener prefijos de inyeccion");
        }

        @Test
        @DisplayName("Escenario: Context Handoff (CRITICAL override)")
        void testContextHandoff() {
            String problemDescription = "dominoes chain backtracking";

            String rabbitPrompt = injector.injectWithAutoDetection(
                "def solve(dominoes):", problemDescription, "qwen2.5-coder:1.5b");

            String turtlePrompt = injector.injectWithAutoDetection(
                "def solve(dominoes):", problemDescription, "qwen2.5-coder:32b");

            assertTrue(rabbitPrompt.contains("BACKTRACKING"));
            assertTrue(turtlePrompt.contains("BACKTRACKING"));

            assertTrue(rabbitPrompt.contains("INSTRUCCION ESTRICTA"));
            assertTrue(turtlePrompt.contains("INSTRUCCION ESTRICTA"),
                "CRITICAL pattern fuerza LOW_RESOURCE para Turtle");
        }

        @Test
        @DisplayName("Escenario: WRAPPER_PROXY detectado para paasio")
        void testPaasioWrapperDetection() {
            String basePrompt = "class MeteredFile:";
            String problemDescription = "wrapper proxy delegation byte count io socket file read write";

            String result = injector.injectWithAutoDetection(
                basePrompt, problemDescription, "qwen2.5-coder:32b");

            assertTrue(result.contains("INSTRUCCION ESTRICTA"),
                "WRAPPER_PROXY (CRITICAL) debe forzar LOW_RESOURCE");
            assertTrue(result.contains("WRAPPER") || result.contains("PROXY") || result.contains("COMPOSICION"),
                "Debe detectar patron WRAPPER_PROXY");
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {
        @Test
        @DisplayName("Constructor default crea AlgorithmPatterns")
        void testDefaultConstructor() {
            PromptInjector defaultInjector = new PromptInjector();

            String result = defaultInjector.inject("test", "pattern", HardwareTier.LOW_RESOURCE);
            assertTrue(result.contains("pattern"));
        }

        @Test
        @DisplayName("Constructor con AlgorithmPatterns inyectado")
        void testConstructorWithAlgorithmPatterns() {
            AlgorithmPatterns customPatterns = new AlgorithmPatterns();
            PromptInjector customInjector = new PromptInjector(customPatterns);

            String result = customInjector.inject("test", "pattern", HardwareTier.LOW_RESOURCE);
            assertTrue(result.contains("pattern"));
        }
    }
}
