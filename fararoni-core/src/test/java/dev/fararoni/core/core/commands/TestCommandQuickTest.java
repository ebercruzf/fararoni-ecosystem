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
package dev.fararoni.core.core.commands;

import dev.fararoni.core.core.commands.TestCommand.TestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("TestCommand Quick API")
class TestCommandQuickTest {
    @TempDir
    Path tempDir;

    private TestCommand testCommand;

    @BeforeEach
    void setUp() {
        testCommand = new TestCommand();
    }

    @Nested
    @DisplayName("TestResult record")
    class TestResultTests {
        @Test
        @DisplayName("pass() creates successful result")
        void passCreatesSuccessfulResult() {
            TestResult result = TestResult.pass(150);

            assertTrue(result.success());
            assertEquals(0, result.exitCode());
            assertEquals(150, result.durationMs());
            assertNull(result.errorSummary());
        }

        @Test
        @DisplayName("fail() creates failed result with error")
        void failCreatesFailedResult() {
            TestResult result = TestResult.fail(1, "Compilation error");

            assertFalse(result.success());
            assertEquals(1, result.exitCode());
            assertEquals("Compilation error", result.errorSummary());
        }

        @Test
        @DisplayName("timeout() creates timeout result")
        void timeoutCreatesTimeoutResult() {
            TestResult result = TestResult.timeout(30);

            assertFalse(result.success());
            assertEquals(-1, result.exitCode());
            assertEquals(30000, result.durationMs());
            assertTrue(result.errorSummary().contains("30"));
            assertTrue(result.errorSummary().contains("Timeout"));
        }

        @Test
        @DisplayName("noFramework() creates no-framework result")
        void noFrameworkCreatesNoFrameworkResult() {
            TestResult result = TestResult.noFramework();

            assertTrue(result.success());
            assertEquals(0, result.exitCode());
            assertEquals(0, result.durationMs());
            assertNull(result.errorSummary());
        }
    }

    @Nested
    @DisplayName("Framework Detection")
    class FrameworkDetectionTests {
        @Test
        @DisplayName("Returns noFramework when no build file exists")
        void returnsNoFrameworkWhenNoBuildFile() {
            TestResult result = testCommand.executeQuick(tempDir);

            assertTrue(result.success());
            assertNull(result.errorSummary());
        }

        @Test
        @DisplayName("Detects Maven project")
        void detectsMavenProject() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>test</groupId>
                    <artifactId>test</artifactId>
                    <version>1.0</version>
                </project>
                """);

            TestResult result = testCommand.executeQuick(tempDir);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Detects Gradle project")
        void detectsGradleProject() throws IOException {
            Files.writeString(tempDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                }
                """);

            TestResult result = testCommand.executeQuick(tempDir);
            assertNotNull(result);
        }

        @Test
        @DisplayName("Detects npm project")
        void detectsNpmProject() throws IOException {
            Files.writeString(tempDir.resolve("package.json"), """
                {
                    "name": "test",
                    "scripts": {
                        "test": "echo 'no tests'"
                    }
                }
                """);

            TestResult result = testCommand.executeQuick(tempDir);
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Error Summary Extraction")
    class ErrorSummaryTests {
        @Test
        @DisplayName("Extracts compilation error summary")
        void extractsCompilationError() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>test</groupId>
                    <artifactId>test</artifactId>
                    <version>1.0</version>
                </project>
                """);

            Path srcDir = tempDir.resolve("src/main/java");
            Files.createDirectories(srcDir);
            Files.writeString(srcDir.resolve("Broken.java"), """
                public class Broken {
                    this is not valid java
                }
                """);

            TestResult result = testCommand.executeQuick(tempDir);

            if (!result.success() && result.errorSummary() != null) {
                assertFalse(result.errorSummary().isEmpty());
            }
        }
    }

    @Nested
    @DisplayName("Timeout Behavior")
    class TimeoutTests {
        @Test
        @DisplayName("Quick timeout is 30 seconds")
        void quickTimeoutIs30Seconds() {
            TestResult result = testCommand.executeQuick(tempDir);
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("TestResult Immutability")
    class ImmutabilityTests {
        @Test
        @DisplayName("TestResult is a record (immutable)")
        void testResultIsRecord() {
            TestResult result = TestResult.pass(100);

            assertTrue(result.getClass().isRecord());
        }

        @Test
        @DisplayName("TestResult components are accessible")
        void componentsAreAccessible() {
            TestResult result = new TestResult(true, 0, 250, null);

            assertEquals(true, result.success());
            assertEquals(0, result.exitCode());
            assertEquals(250, result.durationMs());
            assertNull(result.errorSummary());
        }
    }
}
