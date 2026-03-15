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
package dev.fararoni.core.core.hooks;

import dev.fararoni.core.core.commands.TestCommand;
import dev.fararoni.core.core.commands.TestCommand.TestResult;
import dev.fararoni.core.core.hooks.PostWriteHook.HookResult;
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
@DisplayName("TestOnWriteHook (RegressionGuard)")
class TestOnWriteHookTest {
    @TempDir
    Path tempDir;

    private TestOnWriteHook hook;
    private MockTestCommand mockTestCommand;

    @BeforeEach
    void setUp() {
        mockTestCommand = new MockTestCommand();
        hook = new TestOnWriteHook(mockTestCommand);
    }

    @Nested
    @DisplayName("getName()")
    class GetNameTests {
        @Test
        @DisplayName("Returns 'RegressionGuard'")
        void returnsCorrectName() {
            assertEquals("RegressionGuard", hook.getName());
        }
    }

    @Nested
    @DisplayName("File Type Filtering")
    class FileTypeFilteringTests {
        @Test
        @DisplayName("Ignores .txt files")
        void ignoresTxtFiles() {
            Path txtFile = tempDir.resolve("readme.txt");
            HookResult result = hook.onFileWritten(txtFile, "saga-1");

            assertTrue(result.success());
            assertFalse(result.shouldRollback());
            assertFalse(mockTestCommand.wasExecuted());
        }

        @Test
        @DisplayName("Ignores .md files")
        void ignoresMdFiles() {
            Path mdFile = tempDir.resolve("README.md");
            HookResult result = hook.onFileWritten(mdFile, "saga-2");

            assertTrue(result.success());
            assertFalse(mockTestCommand.wasExecuted());
        }

        @Test
        @DisplayName("Ignores .json files")
        void ignoresJsonFiles() {
            Path jsonFile = tempDir.resolve("config.json");
            HookResult result = hook.onFileWritten(jsonFile, "saga-3");

            assertTrue(result.success());
            assertFalse(mockTestCommand.wasExecuted());
        }

        @Test
        @DisplayName("Ignores .xml files")
        void ignoresXmlFiles() {
            Path xmlFile = tempDir.resolve("pom.xml");
            HookResult result = hook.onFileWritten(xmlFile, "saga-4");

            assertTrue(result.success());
            assertFalse(mockTestCommand.wasExecuted());
        }
    }

    @Nested
    @DisplayName("Code File Detection")
    class CodeFileDetectionTests {
        @Test
        @DisplayName("Validates .java files")
        void validatesJavaFiles() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
            Path javaFile = tempDir.resolve("App.java");
            Files.writeString(javaFile, "public class App {}");

            mockTestCommand.setResult(TestResult.pass(100));

            HookResult result = hook.onFileWritten(javaFile, "saga-java");

            assertTrue(mockTestCommand.wasExecuted());
        }

        @Test
        @DisplayName("Validates .js files")
        void validatesJsFiles() throws IOException {
            Files.writeString(tempDir.resolve("package.json"), "{}");
            Path jsFile = tempDir.resolve("app.js");
            Files.writeString(jsFile, "console.log('hello');");

            mockTestCommand.setResult(TestResult.pass(50));

            HookResult result = hook.onFileWritten(jsFile, "saga-js");

            assertTrue(mockTestCommand.wasExecuted());
        }

        @Test
        @DisplayName("Validates .ts files")
        void validatesTsFiles() throws IOException {
            Files.writeString(tempDir.resolve("package.json"), "{}");
            Path tsFile = tempDir.resolve("app.ts");
            Files.writeString(tsFile, "const x: number = 1;");

            mockTestCommand.setResult(TestResult.pass(50));

            hook.onFileWritten(tsFile, "saga-ts");

            assertTrue(mockTestCommand.wasExecuted());
        }

        @Test
        @DisplayName("Validates .py files")
        void validatesPyFiles() throws IOException {
            Files.writeString(tempDir.resolve("setup.py"), "");
            Path pyFile = tempDir.resolve("app.py");
            Files.writeString(pyFile, "print('hello')");

            mockTestCommand.setResult(TestResult.pass(30));

            hook.onFileWritten(pyFile, "saga-py");

            assertTrue(mockTestCommand.wasExecuted());
        }

        @Test
        @DisplayName("Validates .go files")
        void validatesGoFiles() throws IOException {
            Files.writeString(tempDir.resolve("go.mod"), "module test");
            Path goFile = tempDir.resolve("main.go");
            Files.writeString(goFile, "package main");

            mockTestCommand.setResult(TestResult.pass(80));

            hook.onFileWritten(goFile, "saga-go");

            assertTrue(mockTestCommand.wasExecuted());
        }

        @Test
        @DisplayName("Validates .kt files")
        void validatesKtFiles() throws IOException {
            Files.writeString(tempDir.resolve("build.gradle"), "");
            Path ktFile = tempDir.resolve("App.kt");
            Files.writeString(ktFile, "fun main() {}");

            mockTestCommand.setResult(TestResult.pass(60));

            hook.onFileWritten(ktFile, "saga-kt");

            assertTrue(mockTestCommand.wasExecuted());
        }
    }

    @Nested
    @DisplayName("Test Execution Results")
    class TestExecutionResultsTests {
        @BeforeEach
        void setUpProject() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
        }

        @Test
        @DisplayName("Returns ok() when tests pass")
        void returnsOkWhenTestsPass() throws IOException {
            Path javaFile = tempDir.resolve("Calculator.java");
            Files.writeString(javaFile, "public class Calculator {}");

            mockTestCommand.setResult(TestResult.pass(150));

            HookResult result = hook.onFileWritten(javaFile, "saga-pass");

            assertTrue(result.success());
            assertFalse(result.shouldRollback());
        }

        @Test
        @DisplayName("Returns rollback() when tests fail")
        void returnsRollbackWhenTestsFail() throws IOException {
            Path javaFile = tempDir.resolve("BrokenCode.java");
            Files.writeString(javaFile, "public class BrokenCode {}");

            mockTestCommand.setResult(TestResult.fail(1, "Assertion failed"));

            HookResult result = hook.onFileWritten(javaFile, "saga-fail");

            assertFalse(result.success());
            assertTrue(result.shouldRollback());
            assertTrue(result.message().contains("Regression detected"));
        }

        @Test
        @DisplayName("Returns rollback() when tests timeout")
        void returnsRollbackWhenTestsTimeout() throws IOException {
            Path javaFile = tempDir.resolve("SlowCode.java");
            Files.writeString(javaFile, "public class SlowCode {}");

            mockTestCommand.setResult(TestResult.timeout(30));

            HookResult result = hook.onFileWritten(javaFile, "saga-timeout");

            assertFalse(result.success());
            assertTrue(result.shouldRollback());
        }

        @Test
        @DisplayName("Returns ok() when no framework detected")
        void returnsOkWhenNoFramework() throws IOException {
            Files.deleteIfExists(tempDir.resolve("pom.xml"));

            Path javaFile = tempDir.resolve("Orphan.java");
            Files.writeString(javaFile, "public class Orphan {}");

            mockTestCommand.setResult(TestResult.noFramework());

            HookResult result = hook.onFileWritten(javaFile, "saga-no-fw");

            assertTrue(result.success());
            assertFalse(result.shouldRollback());
        }
    }

    @Nested
    @DisplayName("Project Root Detection")
    class ProjectRootDetectionTests {
        @Test
        @DisplayName("Finds project root with pom.xml")
        void findsProjectRootWithPomXml() throws IOException {
            Path projectRoot = tempDir.resolve("my-project");
            Files.createDirectories(projectRoot);
            Files.writeString(projectRoot.resolve("pom.xml"), "<project></project>");

            Path srcDir = projectRoot.resolve("src/main/java");
            Files.createDirectories(srcDir);
            Path javaFile = srcDir.resolve("App.java");
            Files.writeString(javaFile, "public class App {}");

            mockTestCommand.setResult(TestResult.pass(100));

            hook.onFileWritten(javaFile, "saga-nested");

            assertTrue(mockTestCommand.wasExecuted());
            assertEquals(projectRoot, mockTestCommand.getLastProjectRoot());
        }

        @Test
        @DisplayName("Finds project root with build.gradle")
        void findsProjectRootWithBuildGradle() throws IOException {
            Path projectRoot = tempDir.resolve("gradle-project");
            Files.createDirectories(projectRoot);
            Files.writeString(projectRoot.resolve("build.gradle"), "plugins {}");

            Path srcDir = projectRoot.resolve("src/main/kotlin");
            Files.createDirectories(srcDir);
            Path ktFile = srcDir.resolve("App.kt");
            Files.writeString(ktFile, "fun main() {}");

            mockTestCommand.setResult(TestResult.pass(80));

            hook.onFileWritten(ktFile, "saga-gradle");

            assertEquals(projectRoot, mockTestCommand.getLastProjectRoot());
        }

        @Test
        @DisplayName("Returns warning when project root not found")
        void returnsWarningWhenNoProjectRoot() throws IOException {
            Path orphanFile = tempDir.resolve("orphan/deep/nested/Orphan.java");
            Files.createDirectories(orphanFile.getParent());
            Files.writeString(orphanFile, "public class Orphan {}");

            HookResult result = hook.onFileWritten(orphanFile, "saga-orphan");

            assertNotNull(result);
        }
    }

    static class MockTestCommand extends TestCommand {
        private TestResult resultToReturn = TestResult.pass(0);
        private boolean executed = false;
        private Path lastProjectRoot = null;

        void setResult(TestResult result) {
            this.resultToReturn = result;
        }

        boolean wasExecuted() {
            return executed;
        }

        Path getLastProjectRoot() {
            return lastProjectRoot;
        }

        @Override
        public TestResult executeQuick(Path workDir) {
            this.executed = true;
            this.lastProjectRoot = workDir;
            return resultToReturn;
        }
    }
}
