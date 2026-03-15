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

import dev.fararoni.core.core.reflexion.*;
import dev.fararoni.core.core.reflexion.critics.*;
import dev.fararoni.core.core.reflexion.memory.*;
import dev.fararoni.core.core.reflexion.testoutput.*;

import dev.fararoni.core.core.reflexion.Evaluation;
import dev.fararoni.core.core.reflexion.EvaluationContext;
import dev.fararoni.core.core.reflexion.FeedbackFormatter;
import dev.fararoni.core.core.reflexion.ReflexionEngine;
import dev.fararoni.core.core.reflexion.critics.DiffStrategyCritic;
import dev.fararoni.core.core.reflexion.critics.FailurePatternCritic;
import dev.fararoni.core.core.reflexion.critics.RetryMemoryCritic;
import dev.fararoni.core.core.reflexion.critics.TestOutputCritic;
import dev.fararoni.core.core.reflexion.memory.AttemptMemory;
import dev.fararoni.core.core.reflexion.memory.RetryAttempt;
import dev.fararoni.core.core.reflexion.testoutput.FailurePattern;
import dev.fararoni.core.core.reflexion.testoutput.FailurePatternMatcher;
import dev.fararoni.core.core.reflexion.testoutput.PytestOutputParser;
import dev.fararoni.core.core.reflexion.testoutput.TestFailure;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ReflexionEngine v2 Micro-Benchmark")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReflexionEngineV2Benchmark {
    private static final int ITERATIONS = 1000;
    private static final int WARMUP = 100;

    private static final Map<String, BenchmarkResult> results = new LinkedHashMap<>();

    private static final String PYTEST_OUTPUT_SINGLE_FAIL = """
        ============================= FAILURES =============================
        FAILED test_solution.py::test_add - AssertionError: assert 5 == 4
        ========================= 1 failed in 0.12s =========================
        """;

    private static final String PYTEST_OUTPUT_MULTIPLE_FAILS = """
        ============================= FAILURES =============================
        FAILED test_solution.py::test_add - AssertionError: assert 5 == 4
        FAILED test_solution.py::test_subtract - TypeError: unsupported operand type(s)
        FAILED test_solution.py::test_empty - AssertionError: assert [] == [1, 2, 3]
        ========================= 3 failed, 2 passed in 0.25s =========================
        """;

    private static final String PYTEST_OUTPUT_PASS = """
        ============================= test session starts ==============================
        platform darwin -- Python 3.9.6, pytest-8.4.2, pluggy-1.6.0
        ============================= 5 passed in 0.12s ==============================
        """;

    @AfterAll
    static void printResults() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("REFLEXION ENGINE V2 MICRO-BENCHMARK RESULTS");
        System.out.println("=".repeat(80));
        System.out.printf("%-40s %12s %12s %12s%n", "Component", "Avg (us)", "Min (us)", "Ops/sec");
        System.out.println("-".repeat(80));

        for (Map.Entry<String, BenchmarkResult> entry : results.entrySet()) {
            BenchmarkResult r = entry.getValue();
            System.out.printf("%-40s %,12.2f %,12.2f %,12.0f%n",
                entry.getKey(), r.avgMicros, r.minMicros, r.opsPerSec);
        }

        System.out.println("=".repeat(80));
        System.out.println("Iterations: " + ITERATIONS + " (warmup: " + WARMUP + ")");
        System.out.println("Target: All components < 100us for real-time feedback");
        System.out.println("=".repeat(80) + "\n");
    }

    @Test
    @Order(1)
    @DisplayName("PytestOutputParser - parse single failure")
    void benchmarkParserSingleFail() {
        PytestOutputParser parser = new PytestOutputParser();

        for (int i = 0; i < WARMUP; i++) {
            parser.parse(PYTEST_OUTPUT_SINGLE_FAIL);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            List<TestFailure> failures = parser.parse(PYTEST_OUTPUT_SINGLE_FAIL);
            times[i] = System.nanoTime() - start;
            assertEquals(1, failures.size());
        }

        results.put("PytestOutputParser (single fail)", analyze(times));
    }

    @Test
    @Order(2)
    @DisplayName("PytestOutputParser - parse multiple failures")
    void benchmarkParserMultipleFails() {
        PytestOutputParser parser = new PytestOutputParser();

        for (int i = 0; i < WARMUP; i++) {
            parser.parse(PYTEST_OUTPUT_MULTIPLE_FAILS);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            List<TestFailure> failures = parser.parse(PYTEST_OUTPUT_MULTIPLE_FAILS);
            times[i] = System.nanoTime() - start;
            assertEquals(3, failures.size());
        }

        results.put("PytestOutputParser (3 fails)", analyze(times));
    }

    @Test
    @Order(3)
    @DisplayName("FailurePatternMatcher - match pattern")
    void benchmarkPatternMatcher() {
        PytestOutputParser parser = new PytestOutputParser();
        List<TestFailure> failures = parser.parse(PYTEST_OUTPUT_SINGLE_FAIL);
        TestFailure failure = failures.get(0);

        for (int i = 0; i < WARMUP; i++) {
            FailurePatternMatcher.match(failure);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            FailurePattern pattern = FailurePatternMatcher.match(failure);
            times[i] = System.nanoTime() - start;
            assertNotNull(pattern);
        }

        results.put("FailurePatternMatcher match", analyze(times));
    }

    @Test
    @Order(4)
    @DisplayName("FailurePatternMatcher - match all patterns")
    void benchmarkPatternMatcherAll() {
        PytestOutputParser parser = new PytestOutputParser();
        List<TestFailure> failures = parser.parse(PYTEST_OUTPUT_MULTIPLE_FAILS);

        for (int i = 0; i < WARMUP; i++) {
            for (TestFailure f : failures) {
                FailurePatternMatcher.matchAll(f);
            }
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            for (TestFailure f : failures) {
                List<FailurePattern> patterns = FailurePatternMatcher.matchAll(f);
                assertFalse(patterns.isEmpty());
            }
            times[i] = System.nanoTime() - start;
        }

        results.put("FailurePatternMatcher matchAll (3)", analyze(times));
    }

    @Test
    @Order(5)
    @DisplayName("TestOutputCritic - evaluate")
    void benchmarkTestOutputCritic() {
        TestOutputCritic critic = new TestOutputCritic();
        EvaluationContext context = EvaluationContext.forTestRetry(
            "implement add", PYTEST_OUTPUT_SINGLE_FAIL, 1
        );
        String response = "def add(a, b): return a";

        for (int i = 0; i < WARMUP; i++) {
            critic.evaluate(response, context);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            Evaluation result = critic.evaluate(response, context);
            times[i] = System.nanoTime() - start;
            assertNotNull(result);
        }

        results.put("TestOutputCritic evaluate", analyze(times));
    }

    @Test
    @Order(6)
    @DisplayName("FailurePatternCritic - evaluate")
    void benchmarkFailurePatternCritic() {
        FailurePatternCritic critic = new FailurePatternCritic();
        EvaluationContext context = EvaluationContext.forTestRetry(
            "implement add", PYTEST_OUTPUT_MULTIPLE_FAILS, 1
        );
        String response = "def add(a, b): return a";

        for (int i = 0; i < WARMUP; i++) {
            critic.evaluate(response, context);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            Evaluation result = critic.evaluate(response, context);
            times[i] = System.nanoTime() - start;
            assertNotNull(result);
        }

        results.put("FailurePatternCritic evaluate", analyze(times));
    }

    @Test
    @Order(7)
    @DisplayName("DiffStrategyCritic - evaluate")
    void benchmarkDiffStrategyCritic() {
        DiffStrategyCritic critic = new DiffStrategyCritic();
        EvaluationContext context = EvaluationContext.forTestRetry(
            "implement add", PYTEST_OUTPUT_MULTIPLE_FAILS, 1
        );
        String response = "def add(a, b): return a";

        for (int i = 0; i < WARMUP; i++) {
            critic.evaluate(response, context);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            Evaluation result = critic.evaluate(response, context);
            times[i] = System.nanoTime() - start;
            assertNotNull(result);
        }

        results.put("DiffStrategyCritic evaluate", analyze(times));
    }

    @Test
    @Order(8)
    @DisplayName("RetryMemoryCritic - first attempt")
    void benchmarkRetryMemoryCriticFirst() {
        AttemptMemory memory = new AttemptMemory();
        RetryMemoryCritic critic = new RetryMemoryCritic(memory);
        String response = "def add(a, b): return a";

        for (int i = 0; i < WARMUP; i++) {
            memory.clearAll();
            EvaluationContext ctx = EvaluationContext.forTestRetry(
                "implement add " + i, PYTEST_OUTPUT_SINGLE_FAIL, 1
            );
            critic.evaluate(response, ctx);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            memory.clearAll();
            EvaluationContext ctx = EvaluationContext.forTestRetry(
                "implement add bench" + i, PYTEST_OUTPUT_SINGLE_FAIL, 1
            );
            long start = System.nanoTime();
            Evaluation result = critic.evaluate(response, ctx);
            times[i] = System.nanoTime() - start;
            assertInstanceOf(Evaluation.Pass.class, result);
        }

        results.put("RetryMemoryCritic (1st attempt)", analyze(times));
    }

    @Test
    @Order(9)
    @DisplayName("RetryMemoryCritic - detect repetition")
    void benchmarkRetryMemoryCriticRepetition() {
        AttemptMemory memory = new AttemptMemory();
        RetryMemoryCritic critic = new RetryMemoryCritic(memory);
        String response = "def add(a, b): return a";
        String exerciseId = "benchmark-exercise";

        EvaluationContext ctx1 = EvaluationContext.builder()
            .userPrompt("implement add")
            .metadata(EvaluationContext.KEY_TEST_OUTPUT, PYTEST_OUTPUT_SINGLE_FAIL)
            .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 1)
            .metadata(RetryMemoryCritic.KEY_EXERCISE_ID, exerciseId)
            .build();
        critic.evaluate(response, ctx1);

        for (int i = 0; i < WARMUP; i++) {
            EvaluationContext ctx2 = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, PYTEST_OUTPUT_SINGLE_FAIL)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 2)
                .metadata(RetryMemoryCritic.KEY_EXERCISE_ID, exerciseId)
                .build();
            critic.evaluate(response, ctx2);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            EvaluationContext ctx2 = EvaluationContext.builder()
                .userPrompt("implement add")
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, PYTEST_OUTPUT_SINGLE_FAIL)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, 2 + i)
                .metadata(RetryMemoryCritic.KEY_EXERCISE_ID, exerciseId)
                .build();
            long start = System.nanoTime();
            Evaluation result = critic.evaluate(response, ctx2);
            times[i] = System.nanoTime() - start;
            assertNotNull(result);
        }

        results.put("RetryMemoryCritic (detect repeat)", analyze(times));
    }

    @Test
    @Order(10)
    @DisplayName("ReflexionEngine v2 - forTestCorrection")
    void benchmarkFullEngine() {
        ReflexionEngine engine = ReflexionEngine.forTestCorrection();
        String response = "def add(a, b): return a";

        for (int i = 0; i < WARMUP; i++) {
            engine.clearMemory();
            EvaluationContext ctx = EvaluationContext.forTestRetry(
                "implement add " + i, PYTEST_OUTPUT_SINGLE_FAIL, 1
            );
            engine.reflect(response, ctx);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            EvaluationContext ctx = EvaluationContext.forTestRetry(
                "implement add bench" + i, PYTEST_OUTPUT_SINGLE_FAIL, 1
            );
            long start = System.nanoTime();
            ReflexionEngine.ReflexionResult result = engine.reflect(response, ctx);
            times[i] = System.nanoTime() - start;
            assertTrue(result.needsCorrection());
        }

        results.put("ReflexionEngine v2 (full)", analyze(times));
    }

    @Test
    @Order(11)
    @DisplayName("ReflexionEngine v2 - minimal")
    void benchmarkMinimalEngine() {
        ReflexionEngine engine = ReflexionEngine.minimal();
        String response = "def add(a, b): return a";

        for (int i = 0; i < WARMUP; i++) {
            EvaluationContext ctx = EvaluationContext.forTestRetry(
                "implement add", PYTEST_OUTPUT_SINGLE_FAIL, 1
            );
            engine.reflect(response, ctx);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            EvaluationContext ctx = EvaluationContext.forTestRetry(
                "implement add", PYTEST_OUTPUT_SINGLE_FAIL, 1
            );
            long start = System.nanoTime();
            ReflexionEngine.ReflexionResult result = engine.reflect(response, ctx);
            times[i] = System.nanoTime() - start;
            assertTrue(result.needsCorrection());
        }

        results.put("ReflexionEngine v2 (minimal)", analyze(times));
    }

    @Test
    @Order(12)
    @DisplayName("FeedbackFormatter - format")
    void benchmarkFeedbackFormatter() {
        FeedbackFormatter formatter = new FeedbackFormatter();
        List<Evaluation> evaluations = List.of(
            new Evaluation.Fail("TestOutputCritic", "Test failed",
                Optional.of("Expected 5 but got 4"), Optional.of("Check your math")),
            new Evaluation.Warning("RetryMemoryCritic",
                List.of("Repeated pattern"), List.of("Change strategy")),
            Evaluation.pass("OtherCritic", "All good")
        );

        for (int i = 0; i < WARMUP; i++) {
            formatter.format(evaluations);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            String feedback = formatter.format(evaluations);
            times[i] = System.nanoTime() - start;
            assertTrue(feedback.contains("CRITICAL") || feedback.contains("REQUIRED FIX"));
        }

        results.put("FeedbackFormatter format", analyze(times));
    }

    @Test
    @Order(13)
    @DisplayName("AttemptMemory - record/query")
    void benchmarkAttemptMemory() {
        AttemptMemory memory = new AttemptMemory();
        List<TestFailure> failures = List.of(
            TestFailure.of("test_add", "AssertionError", "assert 5 == 4")
        );

        for (int i = 1; i <= WARMUP; i++) {
            RetryAttempt attempt = RetryAttempt.of(i, "code" + i, failures);
            memory.recordAttempt("warmup", attempt);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 1; i <= ITERATIONS; i++) {
            RetryAttempt attempt = RetryAttempt.of(i, "code" + i, failures);
            long start = System.nanoTime();
            memory.recordAttempt("bench" + (i % 100), attempt);
            memory.isRepeatingCode("bench" + (i % 100));
            memory.getRepeatedPatterns("bench" + (i % 100), 2);
            times[i - 1] = System.nanoTime() - start;
        }

        results.put("AttemptMemory record/query", analyze(times));
    }

    @Test
    @Order(14)
    @DisplayName("E2E - Self-correction cycle")
    void benchmarkSelfCorrectionCycle() {
        ReflexionEngine engine = ReflexionEngine.forTestCorrection();
        FeedbackFormatter formatter = new FeedbackFormatter();

        String code = "def add(a, b): return a";

        for (int i = 0; i < WARMUP; i++) {
            engine.clearMemory();
            EvaluationContext ctx1 = EvaluationContext.forTestRetry(
                "implement add " + i, PYTEST_OUTPUT_SINGLE_FAIL, 1
            );
            engine.reflect(code, ctx1);
        }

        long[] times = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            engine.clearMemory();
            String exercisePrompt = "implement function " + i;

            long start = System.nanoTime();

            EvaluationContext ctx1 = EvaluationContext.forTestRetry(
                exercisePrompt, PYTEST_OUTPUT_SINGLE_FAIL, 1
            );
            ReflexionEngine.ReflexionResult result1 = engine.reflect(code, ctx1);

            if (result1.needsCorrection()) {
                String feedback = result1.getFormattedFeedback();
                assertFalse(feedback.isEmpty());

                EvaluationContext ctx2 = EvaluationContext.forTestRetry(
                    exercisePrompt, PYTEST_OUTPUT_SINGLE_FAIL, 2
                );
                ReflexionEngine.ReflexionResult result2 = engine.reflect(code, ctx2);
                assertNotNull(result2);
            }

            times[i] = System.nanoTime() - start;
        }

        results.put("E2E Self-correction (2 attempts)", analyze(times));
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
