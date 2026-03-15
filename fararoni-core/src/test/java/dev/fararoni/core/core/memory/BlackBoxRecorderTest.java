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
package dev.fararoni.core.core.memory;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("BlackBoxRecorder Tests")
class BlackBoxRecorderTest {
    @TempDir
    Path tempDir;

    private BlackBoxRecorder recorder;
    private Path trainingDir;
    private Path regressionDir;

    @BeforeEach
    void setUp() {
        trainingDir = tempDir.resolve("training");
        regressionDir = tempDir.resolve("regression");
        recorder = new BlackBoxRecorder(trainingDir, regressionDir);
    }

    @Nested
    @DisplayName("Training Samples (Stream A)")
    class TrainingSamplesTests {
        @Test
        @DisplayName("recordTrainingSample should write valid JSONL")
        void shouldWriteValidJsonl() throws IOException {
            boolean result = recorder.recordTrainingSample(
                "Arregla el bug en App.java",
                "public class App { /* fixed */ }"
            );

            assertTrue(result);
            Path trainingFile = trainingDir.resolve("training_data.jsonl");
            assertTrue(Files.exists(trainingFile));

            String content = Files.readString(trainingFile);
            assertTrue(content.contains("\"input\""));
            assertTrue(content.contains("\"output\""));
            assertTrue(content.contains("\"timestamp\""));
            assertTrue(content.contains("Arregla el bug"));
        }

        @Test
        @DisplayName("recordTrainingSample with metadata should include language and taskType")
        void shouldIncludeMetadata() throws IOException {
            boolean result = recorder.recordTrainingSample(
                "Fix syntax error",
                "System.out.println(\"Hello\");",
                "java",
                "fix"
            );

            assertTrue(result);
            String content = Files.readString(trainingDir.resolve("training_data.jsonl"));
            assertTrue(content.contains("\"language\":\"java\""));
            assertTrue(content.contains("\"task_type\":\"fix\""));
        }

        @Test
        @DisplayName("should reject null or empty input")
        void shouldRejectNullOrEmptyInput() {
            assertFalse(recorder.recordTrainingSample(null, "output"));
            assertFalse(recorder.recordTrainingSample("", "output"));
            assertFalse(recorder.recordTrainingSample("   ", "output"));
        }

        @Test
        @DisplayName("should reject null or empty output")
        void shouldRejectNullOrEmptyOutput() {
            assertFalse(recorder.recordTrainingSample("input", null));
            assertFalse(recorder.recordTrainingSample("input", ""));
            assertFalse(recorder.recordTrainingSample("input", "   "));
        }

        @Test
        @DisplayName("should append multiple samples to same file")
        void shouldAppendMultipleSamples() throws IOException {
            recorder.recordTrainingSample("input1", "output1");
            recorder.recordTrainingSample("input2", "output2");
            recorder.recordTrainingSample("input3", "output3");

            List<String> lines = Files.readAllLines(trainingDir.resolve("training_data.jsonl"));
            assertEquals(3, lines.size());
            assertTrue(lines.get(0).contains("input1"));
            assertTrue(lines.get(1).contains("input2"));
            assertTrue(lines.get(2).contains("input3"));
        }
    }

    @Nested
    @DisplayName("PII Filtering")
    class PIIFilteringTests {
        @Test
        @DisplayName("should block OpenAI API keys")
        void shouldBlockOpenAIKeys() {
            String inputWithKey = "Use this key: sk-abc123def456ghi789jkl012mno345pqr678";
            assertFalse(recorder.recordTrainingSample(inputWithKey, "output"));

            var stats = recorder.getStats();
            assertEquals(1, stats.piiBlocked());
        }

        @Test
        @DisplayName("should block Anthropic API keys")
        void shouldBlockAnthropicKeys() {
            String inputWithKey = "api_key = sk-ant-api03-abcdefghijklmnopqrstuvwxyz123456";
            assertFalse(recorder.recordTrainingSample(inputWithKey, "output"));
        }

        @Test
        @DisplayName("should block AWS access keys")
        void shouldBlockAWSKeys() {
            String inputWithKey = "aws_key = AKIAIOSFODNN7EXAMPLE";
            assertFalse(recorder.recordTrainingSample(inputWithKey, "output"));
        }

        @Test
        @DisplayName("should block GitHub tokens")
        void shouldBlockGitHubTokens() {
            String inputWithToken = "token: ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
            assertFalse(recorder.recordTrainingSample(inputWithToken, "output"));
        }

        @Test
        @DisplayName("should block password patterns")
        void shouldBlockPasswords() {
            String inputWithPassword = "password = mySecretPass123";
            assertFalse(recorder.recordTrainingSample(inputWithPassword, "output"));
        }

        @Test
        @DisplayName("should block PII in output as well")
        void shouldBlockPIIInOutput() {
            String outputWithKey = "Here is your key: sk-abc123def456ghi789jkl012mno345pqr678";
            assertFalse(recorder.recordTrainingSample("safe input", outputWithKey));
        }

        @Test
        @DisplayName("containsPII should detect patterns correctly")
        void containsPIIShouldWork() {
            assertTrue(recorder.containsPII("sk-1234567890abcdefghijklmnopqrstuv"));
            assertTrue(recorder.containsPII("password: secret123"));
            assertFalse(recorder.containsPII("This is safe code"));
            assertFalse(recorder.containsPII("public static void main()"));
        }
    }

    @Nested
    @DisplayName("Regression Tests (Stream B)")
    class RegressionTestsTests {
        @Test
        @DisplayName("generateRegressionTest should create JSON file")
        void shouldCreateJsonFile() throws IOException {
            Path testPath = recorder.generateRegressionTest(
                "App.java",
                "public class App { syntax error",
                "SUCCESS"
            );

            assertNotNull(testPath);
            assertTrue(Files.exists(testPath));
            assertTrue(testPath.getFileName().toString().startsWith("test_"));
            assertTrue(testPath.getFileName().toString().endsWith(".json"));

            String content = Files.readString(testPath);
            assertTrue(content.contains("\"target_file\":\"App.java\""));
            assertTrue(content.contains("\"expected_pattern\":\"SUCCESS\""));
            assertTrue(content.contains("\"initial_content\""));
        }

        @Test
        @DisplayName("should reject empty targetFile")
        void shouldRejectEmptyTargetFile() {
            assertNull(recorder.generateRegressionTest("", "code", "SUCCESS"));
            assertNull(recorder.generateRegressionTest(null, "code", "SUCCESS"));
        }

        @Test
        @DisplayName("should reject empty brokenCode")
        void shouldRejectEmptyBrokenCode() {
            assertNull(recorder.generateRegressionTest("App.java", "", "SUCCESS"));
            assertNull(recorder.generateRegressionTest("App.java", null, "SUCCESS"));
        }

        @Test
        @DisplayName("should block PII in regression tests")
        void shouldBlockPIIInTests() {
            Path result = recorder.generateRegressionTest(
                "App.java",
                "String key = \"sk-abc123def456ghi789jkl012mno345pqr678\";",
                "SUCCESS"
            );
            assertNull(result);
        }

        @Test
        @DisplayName("should generate unique test files")
        void shouldGenerateUniqueFiles() throws InterruptedException {
            Path test1 = recorder.generateRegressionTest("A.java", "code1", "OK");
            Thread.sleep(5);
            Path test2 = recorder.generateRegressionTest("B.java", "code2", "OK");

            assertNotNull(test1);
            assertNotNull(test2);
            assertNotEquals(test1, test2);
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {
        @Test
        @DisplayName("should handle concurrent writes safely")
        void shouldHandleConcurrentWrites() throws Exception {
            int threadCount = 10;
            int samplesPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int s = 0; s < samplesPerThread; s++) {
                            recorder.recordTrainingSample(
                                "Thread " + threadId + " Sample " + s,
                                "Output " + threadId + "-" + s
                            );
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            Path trainingFile = trainingDir.resolve("training_data.jsonl");
            List<String> lines = Files.readAllLines(trainingFile);

            assertEquals(threadCount * samplesPerThread, lines.size());

            for (String line : lines) {
                assertTrue(line.startsWith("{"));
                assertTrue(line.endsWith("}"));
                assertTrue(line.contains("\"input\""));
            }
        }
    }

    @Nested
    @DisplayName("Size Limits")
    class SizeLimitsTests {
        @Test
        @DisplayName("should reject samples exceeding size limit")
        void shouldRejectOversizedSamples() {
            String largeInput = "x".repeat(30_000);
            String largeOutput = "y".repeat(30_000);

            boolean result = recorder.recordTrainingSample(largeInput, largeOutput);

            assertFalse(result);
            assertFalse(Files.exists(trainingDir.resolve("training_data.jsonl")));
        }

        @Test
        @DisplayName("should accept samples within size limit")
        void shouldAcceptNormalSizedSamples() {
            String normalInput = "x".repeat(1000);
            String normalOutput = "y".repeat(1000);

            boolean result = recorder.recordTrainingSample(normalInput, normalOutput);

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatsTests {
        @Test
        @DisplayName("getStats should track training samples correctly")
        void shouldTrackTrainingSamples() {
            assertEquals(0, recorder.getStats().trainingSamples());

            recorder.recordTrainingSample("input1", "output1");
            assertEquals(1, recorder.getStats().trainingSamples());

            recorder.recordTrainingSample("input2", "output2");
            assertEquals(2, recorder.getStats().trainingSamples());
        }

        @Test
        @DisplayName("getStats should track regression tests correctly")
        void shouldTrackRegressionTests() {
            assertEquals(0, recorder.getStats().regressionTests());

            recorder.generateRegressionTest("A.java", "code1", "OK");
            assertEquals(1, recorder.getStats().regressionTests());

            recorder.generateRegressionTest("B.java", "code2", "OK");
            assertEquals(2, recorder.getStats().regressionTests());
        }

        @Test
        @DisplayName("getStats should track PII blocked correctly")
        void shouldTrackPIIBlocked() {
            assertEquals(0, recorder.getStats().piiBlocked());

            recorder.recordTrainingSample("sk-abcdef1234567890abcdef1234567890", "output");
            assertEquals(1, recorder.getStats().piiBlocked());

            recorder.recordTrainingSample("input", "password: mysecret");
            assertEquals(2, recorder.getStats().piiBlocked());
        }
    }
}
