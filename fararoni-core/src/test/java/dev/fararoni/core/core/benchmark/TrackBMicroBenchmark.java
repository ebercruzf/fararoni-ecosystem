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
package dev.fararoni.core.core.benchmark;

import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.bus.agent.api.ToolResponse;
import dev.fararoni.core.core.engine.ReflexionEngine;
import dev.fararoni.core.core.engine.ReflexionResult;
import dev.fararoni.core.core.memory.GraphRAGService;
import dev.fararoni.core.core.ninja.ExecutionModeSelector;
import dev.fararoni.core.core.ninja.ExecutionModeSelector.ExecutionMode;
import dev.fararoni.core.core.ninja.SpeculativeCache;
import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.core.persona.PersonaRegistry;
import dev.fararoni.core.core.persona.Personas;
import dev.fararoni.core.core.prompt.PromptBuilder;
import dev.fararoni.core.core.reflexion.EvaluationContext;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Track B Micro-Benchmark")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TrackBMicroBenchmark {
    private static final int ITERATIONS = 1000;
    private static final int WARMUP = 100;

    private static final Map<String, BenchmarkResult> results = new LinkedHashMap<>();

    @AfterAll
    static void printResults() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("TRACK B MICRO-BENCHMARK RESULTS");
        System.out.println("=".repeat(70));
        System.out.printf("%-30s %12s %12s %12s%n", "Component", "Avg (us)", "Min (us)", "Ops/sec");
        System.out.println("-".repeat(70));

        for (Map.Entry<String, BenchmarkResult> entry : results.entrySet()) {
            BenchmarkResult r = entry.getValue();
            System.out.printf("%-30s %,12.2f %,12.2f %,12.0f%n",
                entry.getKey(), r.avgMicros, r.minMicros, r.opsPerSec);
        }

        System.out.println("=".repeat(70));
        System.out.println("Iterations: " + ITERATIONS + " (warmup: " + WARMUP + ")");
        System.out.println("=".repeat(70) + "\n");
    }

    @Test
    @Order(1)
    @DisplayName("SpeculativeCache - put/get")
    void benchmarkSpeculativeCache() {
        SpeculativeCache cache = SpeculativeCache.builder()
            .maxSize(10000)
            .ttl(Duration.ofMinutes(5))
            .build();

        for (int i = 0; i < WARMUP; i++) {
            ToolRequest req = ToolRequest.of("FILE", "read", Map.of("path", "file" + i + ".txt"));
            cache.put(req, ToolResponse.success("content" + i), Duration.ZERO);
            cache.get(req);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            ToolRequest req = ToolRequest.of("FILE", "read", Map.of("path", "bench" + i + ".txt"));
            long start = System.nanoTime();
            cache.put(req, ToolResponse.success("content" + i), Duration.ZERO);
            cache.get(req);
            times[i] = System.nanoTime() - start;
        }

        results.put("SpeculativeCache put/get", analyze(times));
        assertTrue(cache.size() > 0);
    }

    @Test
    @Order(2)
    @DisplayName("SpeculativeCache - hit rate")
    void benchmarkCacheHitRate() {
        SpeculativeCache cache = SpeculativeCache.createDefault();

        for (int i = 0; i < 100; i++) {
            ToolRequest req = ToolRequest.of("FILE", "read", Map.of("path", "file" + i + ".txt"));
            cache.put(req, ToolResponse.success("content" + i), Duration.ZERO);
        }

        long[] times = new long[ITERATIONS];
        Random rand = new Random(42);
        for (int i = 0; i < ITERATIONS; i++) {
            int idx = rand.nextInt(125);
            ToolRequest req = ToolRequest.of("FILE", "read", Map.of("path", "file" + idx + ".txt"));
            long start = System.nanoTime();
            cache.get(req);
            times[i] = System.nanoTime() - start;
        }

        results.put("SpeculativeCache hit-test", analyze(times));
        assertTrue(cache.getHitRate() > 0.5);
    }

    @Test
    @Order(3)
    @DisplayName("ExecutionModeSelector - mode selection")
    void benchmarkModeSelector() {
        ExecutionModeSelector selector = ExecutionModeSelector.create();
        String[] queries = {
            "What is Java?",
            "Explain polymorphism",
            "Refactor the authentication system completely",
            "Update all files in the project",
            "Fix the bug"
        };

        for (int i = 0; i < WARMUP; i++) {
            selector.selectMode(queries[i % queries.length]);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            String query = queries[i % queries.length];
            long start = System.nanoTime();
            ExecutionMode mode = selector.selectMode(query);
            times[i] = System.nanoTime() - start;
            assertNotNull(mode);
        }

        results.put("ExecutionModeSelector select", analyze(times));
    }

    @Test
    @Order(4)
    @DisplayName("ReflexionEngine - evaluate")
    void benchmarkReflexionEngine() {
        ReflexionEngine engine = ReflexionEngine.builder().build();
        String response = """
            Here is the implementation:
            ```java
            public class Calculator {
                public int add(int a, int b) {
                    return a + b;
                }
            }
            ```
            This is a simple calculator class.
            """;
        EvaluationContext context = EvaluationContext.builder()
            .userPrompt("Create a calculator")
            .build();

        for (int i = 0; i < WARMUP; i++) {
            engine.reflect(response, context);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            ReflexionResult result = engine.reflect(response, context);
            times[i] = System.nanoTime() - start;
            assertNotNull(result);
        }

        results.put("ReflexionEngine evaluate", analyze(times));
    }

    @Test
    @Order(5)
    @DisplayName("PersonaRegistry - persona selection")
    void benchmarkPersonaRegistry() {
        PersonaRegistry registry = PersonaRegistry.getInstance();
        String[] tasks = {
            "Review this code for security vulnerabilities",
            "Design a microservices architecture",
            "Write unit tests for the calculator",
            "Deploy to Kubernetes",
            "Analyze the requirements"
        };

        for (int i = 0; i < WARMUP; i++) {
            registry.selectFor(tasks[i % tasks.length]);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            String task = tasks[i % tasks.length];
            long start = System.nanoTime();
            Persona persona = registry.selectFor(task);
            times[i] = System.nanoTime() - start;
            assertNotNull(persona);
        }

        results.put("PersonaRegistry selectFor", analyze(times));
    }

    @Test
    @Order(6)
    @DisplayName("PromptBuilder - build")
    void benchmarkPromptBuilder() {
        Persona persona = Personas.DEVELOPER;

        for (int i = 0; i < WARMUP; i++) {
            PromptBuilder.create()
                .systemPrompt("You are a helpful assistant")
                .withPersona(persona)
                .userQuery("Hello")
                .build();
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            String prompt = PromptBuilder.create()
                .systemPrompt("You are a helpful assistant named {{name}}")
                .variable("name", "Fararoni")
                .withPersona(persona)
                .addConstraint("Be concise")
                .addConstraint("Use code examples")
                .userQuery("Explain inheritance in Java")
                .build();
            times[i] = System.nanoTime() - start;
            assertNotNull(prompt);
            assertTrue(prompt.length() > 100);
        }

        results.put("PromptBuilder build", analyze(times));
    }

    @Test
    @Order(7)
    @DisplayName("GraphRAGService - addFact/retrieve")
    void benchmarkGraphRAG() {
        GraphRAGService rag = GraphRAGService.create();

        for (int i = 0; i < WARMUP; i++) {
            rag.addFact("entity" + i, "relates_to", "entity" + (i + 1), 1.0);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            rag.addFact("bench" + i, "depends_on", "bench" + (i + 1), 0.8);
            rag.retrieveFacts("bench" + i, 5);
            times[i] = System.nanoTime() - start;
        }

        results.put("GraphRAGService add/retrieve", analyze(times));
    }

    @Test
    @Order(8)
    @DisplayName("GraphRAGService - context retrieval")
    void benchmarkContextRetrieval() {
        GraphRAGService rag = GraphRAGService.create();

        String[] files = {"Main.java", "Service.java", "Repository.java", "Controller.java"};
        for (String file : files) {
            rag.addMessage("user", "Working on " + file, "session1");
            rag.addFact(file, "contains", "class " + file.replace(".java", ""), 1.0);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            String context = rag.retrieveContext("session1", 5);
            times[i] = System.nanoTime() - start;
            assertNotNull(context);
        }

        results.put("GraphRAGService context", analyze(times));
    }

    @Test
    @Order(9)
    @DisplayName("Combined - Full pipeline simulation")
    void benchmarkCombinedPipeline() {
        ExecutionModeSelector modeSelector = ExecutionModeSelector.create();
        PersonaRegistry personaRegistry = PersonaRegistry.getInstance();
        ReflexionEngine reflexionEngine = ReflexionEngine.builder().build();
        SpeculativeCache cache = SpeculativeCache.createDefault();
        GraphRAGService rag = GraphRAGService.create();

        String task = "Implement a REST API endpoint for user authentication";
        String response = "Here is the implementation with proper validation...";

        EvaluationContext evalContext = EvaluationContext.builder()
            .userPrompt(task)
            .build();

        for (int i = 0; i < WARMUP; i++) {
            modeSelector.selectMode(task);
            personaRegistry.selectFor(task);
            reflexionEngine.reflect(response, evalContext);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();

            ExecutionMode mode = modeSelector.selectMode(task);

            Persona persona = personaRegistry.selectFor(task);

            String prompt = PromptBuilder.create()
                .systemPrompt("You are a {{role}} assistant")
                .variable("role", persona.id())
                .withPersona(persona)
                .userQuery(task)
                .build();

            ToolRequest cacheReq = ToolRequest.of("LLM", "generate", Map.of("prompt", String.valueOf(prompt.hashCode())));
            cache.get(cacheReq);

            rag.addMessage("user", task, "bench-session");

            ReflexionResult result = reflexionEngine.reflect(response, evalContext);

            times[i] = System.nanoTime() - start;

            assertNotNull(mode);
            assertNotNull(persona);
            assertNotNull(result);
        }

        results.put("Combined Pipeline", analyze(times));
    }

    private BenchmarkResult analyze(long[] times) {
        Arrays.sort(times);
        long sum = 0;
        for (long t : times) sum += t;
        double avgNanos = (double) sum / times.length;
        double minNanos = times[0];
        double avgMicros = avgNanos / 1000.0;
        double minMicros = minNanos / 1000.0;
        double opsPerSec = 1_000_000_000.0 / avgNanos;
        return new BenchmarkResult(avgMicros, minMicros, opsPerSec);
    }

    private record BenchmarkResult(double avgMicros, double minMicros, double opsPerSec) {}
}
