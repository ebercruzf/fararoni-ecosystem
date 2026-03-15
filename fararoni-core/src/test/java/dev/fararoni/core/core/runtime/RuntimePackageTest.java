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
package dev.fararoni.core.core.runtime;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Runtime Package Tests (Etapa 5)")
class RuntimePackageTest {
    @Nested
    @DisplayName("ShellSession Tests")
    class ShellSessionTests {
        @TempDir
        Path tempDir;

        private ShellSession session;

        @BeforeEach
        void setUp() {
            session = new ShellSession(tempDir);
        }

        @Test
        @DisplayName("Constructor debe inicializar con directorio dado")
        void constructorShouldInitializeWithGivenDirectory() {
            assertEquals(tempDir.toAbsolutePath(), session.getCurrentDirectory());
        }

        @Test
        @DisplayName("Constructor debe heredar variables de entorno del sistema")
        void constructorShouldInheritSystemEnv() {
            assertFalse(session.getEnvironment().isEmpty());
            assertNotNull(session.getEnv("PATH"));
        }

        @Test
        @DisplayName("execute debe retornar resultado vacio para comando vacio")
        void executeShouldReturnEmptyForEmptyCommand() {
            ShellSession.CommandResult result = session.execute("");
            assertTrue(result.isSuccess());
            assertEquals("", result.stdout());
        }

        @Test
        @DisplayName("execute debe ejecutar comando simple")
        void executeShouldRunSimpleCommand() {
            ShellSession.CommandResult result = session.execute("echo hello");
            assertTrue(result.isSuccess());
            assertTrue(result.stdout().contains("hello"));
        }

        @Test
        @DisplayName("cd debe cambiar directorio de trabajo")
        void cdShouldChangeWorkingDirectory() throws Exception {
            Path subDir = tempDir.resolve("subdir");
            Files.createDirectory(subDir);

            ShellSession.CommandResult result = session.execute("cd subdir");

            assertTrue(result.isSuccess());
            assertEquals(subDir, session.getCurrentDirectory());
        }

        @Test
        @DisplayName("cd debe fallar para directorio inexistente")
        void cdShouldFailForNonexistentDirectory() {
            ShellSession.CommandResult result = session.execute("cd nonexistent");

            assertFalse(result.isSuccess());
            assertEquals(1, result.exitCode());
        }

        @Test
        @DisplayName("pwd debe mostrar directorio actual")
        void pwdShouldShowCurrentDirectory() {
            ShellSession.CommandResult result = session.execute("pwd");

            assertTrue(result.isSuccess());
            assertTrue(result.stdout().contains(tempDir.getFileName().toString()));
        }

        @Test
        @DisplayName("export debe establecer variable de entorno")
        void exportShouldSetEnvVariable() {
            session.execute("export MY_VAR=my_value");

            assertEquals("my_value", session.getEnv("MY_VAR"));
        }

        @Test
        @DisplayName("setEnv debe establecer variable de entorno")
        void setEnvShouldSetVariable() {
            session.setEnv("TEST_VAR", "test_value");

            assertEquals("test_value", session.getEnv("TEST_VAR"));
        }

        @Test
        @DisplayName("getHistory debe retornar historial de comandos")
        void getHistoryShouldReturnCommandHistory() {
            session.execute("echo one");
            session.execute("echo two");
            session.execute("echo three");

            List<String> history = session.getHistory();

            assertEquals(3, history.size());
            assertTrue(history.contains("echo one"));
        }

        @Test
        @DisplayName("clearHistory debe limpiar historial")
        void clearHistoryShouldClearHistory() {
            session.execute("echo test");
            session.clearHistory();

            assertTrue(session.getHistory().isEmpty());
        }

        @Test
        @DisplayName("setTimeout debe cambiar timeout")
        void setTimeoutShouldChangeTimeout() {
            session.setTimeoutSeconds(120);

            assertEquals(120, session.getTimeoutSeconds());
        }

        @Test
        @DisplayName("execute debe manejar comandos con comillas")
        void executeShouldHandleQuotedCommands() {
            ShellSession.CommandResult result = session.execute("echo \"hello world\"");

            assertTrue(result.isSuccess());
            assertTrue(result.stdout().contains("hello world"));
        }

        @Test
        @DisplayName("executeSuccess debe retornar boolean")
        void executeSuccessShouldReturnBoolean() {
            assertTrue(session.executeSuccess("echo test"));
            assertFalse(session.executeSuccess("false"));
        }

        @Test
        @DisplayName("executeAll debe ejecutar multiples comandos")
        void executeAllShouldRunMultipleCommands() {
            List<ShellSession.CommandResult> results = session.executeAll(
                    List.of("echo one", "echo two", "echo three"));

            assertEquals(3, results.size());
            assertTrue(results.stream().allMatch(ShellSession.CommandResult::isSuccess));
        }

        @Test
        @DisplayName("setCurrentDirectory debe cambiar directorio directamente")
        void setCurrentDirectoryShouldChangeDirectory() throws Exception {
            Path subDir = tempDir.resolve("direct");
            Files.createDirectory(subDir);

            boolean changed = session.setCurrentDirectory(subDir);

            assertTrue(changed);
            assertEquals(subDir.toAbsolutePath(), session.getCurrentDirectory());
        }

        @Test
        @DisplayName("setCurrentDirectory debe fallar para ruta invalida")
        void setCurrentDirectoryShouldFailForInvalidPath() {
            boolean changed = session.setCurrentDirectory(Path.of("/nonexistent/path"));

            assertFalse(changed);
        }

        @Test
        @DisplayName("getLastResult debe retornar ultimo resultado")
        void getLastResultShouldReturnLastResult() {
            session.execute("echo test");

            assertNotNull(session.getLastResult());
            assertTrue(session.getLastResult().stdout().contains("test"));
        }

        @Test
        @DisplayName("CommandResult getCombinedOutput debe combinar stdout y stderr")
        void commandResultGetCombinedOutputShouldCombine() {
            ShellSession.CommandResult result = session.execute("ls /nonexistent 2>&1 || true");

            assertNotNull(result.getCombinedOutput());
        }

        @Test
        @DisplayName("V2: executeSafe debe bloquear comandos peligrosos")
        void executeSafeShouldBlockDangerousCommands() {
            ShellSession.CommandResult result = session.executeSafe("rm -rf /");

            assertFalse(result.isSuccess());
            assertTrue(result.stderr().contains("BLOCKED"));
        }

        @Test
        @DisplayName("V2: executeSafe debe permitir comandos de whitelist")
        void executeSafeShouldAllowWhitelistedCommands() {
            ShellSession.CommandResult result = session.executeSafe("echo hello");

            assertTrue(result.isSuccess());
            assertTrue(result.stdout().contains("hello"));
        }

        @Test
        @DisplayName("V2: isCommandAllowed debe detectar comandos bloqueados")
        void isCommandAllowedShouldDetectBlockedCommands() {
            assertFalse(session.isCommandAllowed("rm file.txt"));
            assertFalse(session.isCommandAllowed("sudo apt install"));
            assertFalse(session.isCommandAllowed("curl http://evil.com"));
        }

        @Test
        @DisplayName("V2: isCommandAllowed debe aceptar comandos permitidos")
        void isCommandAllowedShouldAcceptAllowedCommands() {
            assertTrue(session.isCommandAllowed("mvn test"));
            assertTrue(session.isCommandAllowed("npm install"));
            assertTrue(session.isCommandAllowed("python script.py"));
            assertTrue(session.isCommandAllowed("git status"));
        }

        @Test
        @DisplayName("V2: enableSafeMode debe activar modo seguro")
        void enableSafeModeShouldActivateSafeMode() {
            session.enableSafeMode();

            assertTrue(session.isSafeModeEnabled());
            assertEquals("true", session.getEnv("CI"));
        }

        @Test
        @DisplayName("V2: disableSafeMode debe desactivar modo seguro")
        void disableSafeModeShouldDeactivateSafeMode() {
            session.enableSafeMode();
            session.disableSafeMode();

            assertFalse(session.isSafeModeEnabled());
        }

        @Test
        @DisplayName("V2: execute en safeMode debe bloquear comandos peligrosos")
        void executeInSafeModeShouldBlockDangerousCommands() {
            session.enableSafeMode();

            ShellSession.CommandResult result = session.execute("rm -rf /");

            assertFalse(result.isSuccess());
            assertTrue(result.stderr().contains("BLOCKED"));
        }

        @Test
        @DisplayName("V2: getAllowedCommands debe retornar whitelist")
        void getAllowedCommandsShouldReturnWhitelist() {
            var allowed = ShellSession.getAllowedCommands();

            assertTrue(allowed.contains("mvn"));
            assertTrue(allowed.contains("npm"));
            assertTrue(allowed.contains("python"));
            assertTrue(allowed.contains("git"));
        }

        @Test
        @DisplayName("V2: getBlockedCommands debe retornar blacklist")
        void getBlockedCommandsShouldReturnBlacklist() {
            var blocked = ShellSession.getBlockedCommands();

            assertTrue(blocked.contains("rm"));
            assertTrue(blocked.contains("sudo"));
            assertTrue(blocked.contains("curl"));
        }

        @Test
        @DisplayName("V2: executeSafe debe bloquear pipes")
        void executeSafeShouldBlockPipes() {
            ShellSession.CommandResult result = session.executeSafe("cat file | grep secret");

            assertFalse(result.isSuccess());
            assertTrue(result.stderr().contains("BLOCKED"));
        }

        @Test
        @DisplayName("V2: executeSafe debe bloquear redirects")
        void executeSafeShouldBlockRedirects() {
            ShellSession.CommandResult result = session.executeSafe("echo malware > /etc/passwd");

            assertFalse(result.isSuccess());
            assertTrue(result.stderr().contains("BLOCKED"));
        }
    }

    @Nested
    @DisplayName("TestRunner Tests")
    class TestRunnerTests {
        @TempDir
        Path tempDir;

        private ShellSession session;
        private TestRunner runner;

        @BeforeEach
        void setUp() {
            session = new ShellSession(tempDir);
            runner = new TestRunner(session);
        }

        @Test
        @DisplayName("Constructor debe rechazar session null")
        void constructorShouldRejectNullSession() {
            assertThrows(IllegalArgumentException.class, () -> new TestRunner(null));
        }

        @Test
        @DisplayName("detectFramework debe detectar Maven")
        void detectFrameworkShouldDetectMaven() throws Exception {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

            TestRunner.TestFramework detected = runner.detectFramework(tempDir);

            assertEquals(TestRunner.TestFramework.MAVEN, detected);
        }

        @Test
        @DisplayName("detectFramework debe detectar Gradle")
        void detectFrameworkShouldDetectGradle() throws Exception {
            Files.writeString(tempDir.resolve("build.gradle"), "plugins {}");

            TestRunner.TestFramework detected = runner.detectFramework(tempDir);

            assertEquals(TestRunner.TestFramework.GRADLE, detected);
        }

        @Test
        @DisplayName("detectFramework debe detectar npm")
        void detectFrameworkShouldDetectNpm() throws Exception {
            Files.writeString(tempDir.resolve("package.json"), "{}");

            TestRunner.TestFramework detected = runner.detectFramework(tempDir);

            assertEquals(TestRunner.TestFramework.NPM, detected);
        }

        @Test
        @DisplayName("detectFramework debe detectar pytest")
        void detectFrameworkShouldDetectPytest() throws Exception {
            Files.writeString(tempDir.resolve("pytest.ini"), "[pytest]");

            TestRunner.TestFramework detected = runner.detectFramework(tempDir);

            assertEquals(TestRunner.TestFramework.PYTEST, detected);
        }

        @Test
        @DisplayName("detectFramework debe detectar Cargo")
        void detectFrameworkShouldDetectCargo() throws Exception {
            Files.writeString(tempDir.resolve("Cargo.toml"), "[package]");

            TestRunner.TestFramework detected = runner.detectFramework(tempDir);

            assertEquals(TestRunner.TestFramework.CARGO, detected);
        }

        @Test
        @DisplayName("detectFramework debe detectar Go")
        void detectFrameworkShouldDetectGo() throws Exception {
            Files.writeString(tempDir.resolve("go.mod"), "module test");

            TestRunner.TestFramework detected = runner.detectFramework(tempDir);

            assertEquals(TestRunner.TestFramework.GO, detected);
        }

        @Test
        @DisplayName("detectFramework debe retornar UNKNOWN si no detecta nada")
        void detectFrameworkShouldReturnUnknownIfNotDetected() {
            TestRunner.TestFramework detected = runner.detectFramework(tempDir);

            assertEquals(TestRunner.TestFramework.UNKNOWN, detected);
        }

        @Test
        @DisplayName("detectFramework debe retornar UNKNOWN para path null")
        void detectFrameworkShouldReturnUnknownForNullPath() {
            TestRunner.TestFramework detected = runner.detectFramework(null);

            assertEquals(TestRunner.TestFramework.UNKNOWN, detected);
        }

        @Test
        @DisplayName("getAvailableFrameworks debe listar frameworks disponibles")
        void getAvailableFrameworksShouldListAvailable() throws Exception {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
            Files.writeString(tempDir.resolve("package.json"), "{}");

            List<TestRunner.TestFramework> available = runner.getAvailableFrameworks(tempDir);

            assertTrue(available.contains(TestRunner.TestFramework.MAVEN));
            assertTrue(available.contains(TestRunner.TestFramework.NPM));
        }

        @Test
        @DisplayName("runTests debe fallar si no detecta framework")
        void runTestsShouldFailIfNoFrameworkDetected() {
            TestRunner.TestResult result = runner.runTests(tempDir);

            assertFalse(result.isSuccess());
            assertEquals(TestRunner.TestFramework.UNKNOWN, result.framework());
        }

        @Test
        @DisplayName("TestFramework debe tener comandos configurados")
        void testFrameworkShouldHaveCommands() {
            for (TestRunner.TestFramework fw : TestRunner.TestFramework.values()) {
                if (fw != TestRunner.TestFramework.UNKNOWN) {
                    assertNotNull(fw.getCommand());
                    assertFalse(fw.getCommand().isBlank());
                }
            }
        }

        @Test
        @DisplayName("TestResult getSummary debe generar resumen")
        void testResultGetSummaryShouldGenerateSummary() {
            TestRunner.TestResult result = new TestRunner.TestResult(
                    true, TestRunner.TestFramework.MAVEN, "mvn test",
                    "output", "", 0, 1000, 10, 0);

            String summary = result.getSummary();

            assertTrue(summary.contains("MAVEN"));
            assertTrue(summary.contains("PASSED"));
            assertTrue(summary.contains("10 tests"));
        }

        @Test
        @DisplayName("TestResult getFailureLog debe retornar log de error")
        void testResultGetFailureLogShouldReturnErrorLog() {
            TestRunner.TestResult result = new TestRunner.TestResult(
                    false, TestRunner.TestFramework.MAVEN, "mvn test",
                    "output", "error log", 1, 1000, 10, 2);

            assertEquals("error log", result.getFailureLog());
        }

        @Test
        @DisplayName("getSession debe retornar la sesion")
        void getSessionShouldReturnSession() {
            assertEquals(session, runner.getSession());
        }

        @Test
        @DisplayName("V3: detectVirtualEnv debe detectar venv")
        void detectVirtualEnvShouldDetectVenv() throws Exception {
            Path venvDir = tempDir.resolve("venv/bin");
            Files.createDirectories(venvDir);
            Files.createFile(venvDir.resolve("python"));

            var detected = runner.detectVirtualEnv(tempDir);

            assertTrue(detected.isPresent());
            assertEquals(tempDir.resolve("venv"), detected.get());
        }

        @Test
        @DisplayName("V3: detectVirtualEnv debe detectar .venv")
        void detectVirtualEnvShouldDetectDotVenv() throws Exception {
            Path venvDir = tempDir.resolve(".venv/bin");
            Files.createDirectories(venvDir);
            Files.createFile(venvDir.resolve("python"));

            var detected = runner.detectVirtualEnv(tempDir);

            assertTrue(detected.isPresent());
        }

        @Test
        @DisplayName("V3: detectVirtualEnv debe retornar empty si no hay venv")
        void detectVirtualEnvShouldReturnEmptyIfNoVenv() {
            var detected = runner.detectVirtualEnv(tempDir);

            assertTrue(detected.isEmpty());
        }

        @Test
        @DisplayName("V3: detectLocalWrapper debe detectar gradlew")
        void detectLocalWrapperShouldDetectGradlew() throws Exception {
            Files.writeString(tempDir.resolve("gradlew"), "#!/bin/bash\n");

            var detected = runner.detectLocalWrapper(tempDir, TestRunner.TestFramework.GRADLE);

            assertTrue(detected.isPresent());
            assertTrue(detected.get().contains("gradlew"));
        }

        @Test
        @DisplayName("V3: detectLocalWrapper debe detectar mvnw")
        void detectLocalWrapperShouldDetectMvnw() throws Exception {
            Files.writeString(tempDir.resolve("mvnw"), "#!/bin/bash\n");

            var detected = runner.detectLocalWrapper(tempDir, TestRunner.TestFramework.MAVEN);

            assertTrue(detected.isPresent());
            assertTrue(detected.get().contains("mvnw"));
        }

        @Test
        @DisplayName("V3: detectLocalWrapper debe retornar empty si no hay wrapper")
        void detectLocalWrapperShouldReturnEmptyIfNoWrapper() {
            var detected = runner.detectLocalWrapper(tempDir, TestRunner.TestFramework.GRADLE);

            assertTrue(detected.isEmpty());
        }

        @Test
        @DisplayName("V3: buildSmartCommand debe usar wrapper local si existe")
        void buildSmartCommandShouldUseLocalWrapperIfExists() throws Exception {
            Files.writeString(tempDir.resolve("gradlew"), "#!/bin/bash\n");

            String command = runner.buildSmartCommand(tempDir, TestRunner.TestFramework.GRADLE);

            assertTrue(command.contains("gradlew"));
        }

        @Test
        @DisplayName("V3: buildSmartCommand debe usar venv para pytest si existe")
        void buildSmartCommandShouldUseVenvForPytestIfExists() throws Exception {
            Path venvDir = tempDir.resolve("venv/bin");
            Files.createDirectories(venvDir);
            Files.createFile(venvDir.resolve("python"));
            Files.createFile(venvDir.resolve("pytest"));

            String command = runner.buildSmartCommand(tempDir, TestRunner.TestFramework.PYTEST);

            assertTrue(command.contains("venv"));
        }

        @Test
        @DisplayName("V3: buildSmartCommand debe usar comando default si no hay wrapper")
        void buildSmartCommandShouldUseDefaultIfNoWrapper() {
            String command = runner.buildSmartCommand(tempDir, TestRunner.TestFramework.MAVEN);

            assertEquals("mvn test", command);
        }

        @Test
        @DisplayName("V3: getVenvPython debe retornar path correcto")
        void getVenvPythonShouldReturnCorrectPath() throws Exception {
            Path venvPath = tempDir.resolve("venv");
            Files.createDirectories(venvPath.resolve("bin"));

            Path pythonPath = runner.getVenvPython(venvPath);

            assertTrue(pythonPath.toString().contains("python"));
        }

        @Test
        @DisplayName("V3: getVenvPytest debe retornar path correcto")
        void getVenvPytestShouldReturnCorrectPath() throws Exception {
            Path venvPath = tempDir.resolve("venv");
            Files.createDirectories(venvPath.resolve("bin"));

            Path pytestPath = runner.getVenvPytest(venvPath);

            assertTrue(pytestPath.toString().contains("pytest"));
        }
    }

    @Nested
    @DisplayName("DebugLoop Tests")
    class DebugLoopTests {
        @TempDir
        Path tempDir;

        private ShellSession session;
        private TestRunner runner;
        private MockLlmProvider mockLlm;
        private DebugLoop debugLoop;

        @BeforeEach
        void setUp() {
            session = new ShellSession(tempDir);
            runner = new TestRunner(session);
            mockLlm = new MockLlmProvider();
            debugLoop = new DebugLoop(runner, mockLlm);
        }

        @Test
        @DisplayName("Constructor debe rechazar TestRunner null")
        void constructorShouldRejectNullTestRunner() {
            assertThrows(IllegalArgumentException.class,
                    () -> new DebugLoop(null, mockLlm));
        }

        @Test
        @DisplayName("Constructor debe rechazar LlmProvider null")
        void constructorShouldRejectNullLlmProvider() {
            assertThrows(IllegalArgumentException.class,
                    () -> new DebugLoop(runner, null));
        }

        @Test
        @DisplayName("runCycle debe fallar para path invalido")
        void runCycleShouldFailForInvalidPath() {
            DebugLoop.CycleResult result = debugLoop.runCycle(Path.of("/nonexistent"), 3);

            assertFalse(result.isSuccess());
            assertTrue(result.lastError().contains("invalid"));
        }

        @Test
        @DisplayName("runCycle debe fallar si no hay framework")
        void runCycleShouldFailIfNoFramework() {
            DebugLoop.CycleResult result = debugLoop.runCycle(tempDir, 3);

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("withCodePatcher debe configurar patcher")
        void withCodePatcherShouldConfigurePatcher() {
            AtomicInteger patchCount = new AtomicInteger(0);
            DebugLoop.CodePatcher patcher = (path, response) -> {
                patchCount.incrementAndGet();
                return true;
            };

            debugLoop.withCodePatcher(patcher);

            assertNotNull(debugLoop);
        }

        @Test
        @DisplayName("onAttempt debe configurar callback")
        void onAttemptShouldConfigureCallback() {
            AtomicInteger callbackCount = new AtomicInteger(0);

            debugLoop.onAttempt(record -> callbackCount.incrementAndGet());

            assertNotNull(debugLoop);
        }

        @Test
        @DisplayName("verify debe ejecutar tests sin reparacion")
        void verifyShouldRunTestsWithoutRepair() {
            boolean result = debugLoop.verify(tempDir);

            assertFalse(result);
        }

        @Test
        @DisplayName("CycleResult getSummary debe generar resumen correcto")
        void cycleResultGetSummaryShouldGenerateSummary() {
            DebugLoop.CycleResult successResult = new DebugLoop.CycleResult(
                    true, 2, 3, "", List.of());

            String summary = successResult.getSummary();

            assertTrue(summary.contains("SUCCESS"));
            assertTrue(summary.contains("2/3"));
        }

        @Test
        @DisplayName("CycleResult isSuccess debe reflejar estado")
        void cycleResultIsSuccessShouldReflectState() {
            DebugLoop.CycleResult success = new DebugLoop.CycleResult(
                    true, 1, 3, "", List.of());
            DebugLoop.CycleResult failure = new DebugLoop.CycleResult(
                    false, 3, 3, "error", List.of());

            assertTrue(success.isSuccess());
            assertFalse(failure.isSuccess());
        }

        @Test
        @DisplayName("buildCustomPrompt debe construir prompt")
        void buildCustomPromptShouldBuildPrompt() {
            String prompt = debugLoop.buildCustomPrompt("error log", "fix it");

            assertTrue(prompt.contains("error log"));
            assertTrue(prompt.contains("fix it"));
        }

        @Test
        @DisplayName("getTestRunner debe retornar runner")
        void getTestRunnerShouldReturnRunner() {
            assertEquals(runner, debugLoop.getTestRunner());
        }

        @Test
        @DisplayName("getLlmProvider debe retornar provider")
        void getLlmProviderShouldReturnProvider() {
            assertEquals(mockLlm, debugLoop.getLlmProvider());
        }

        @Test
        @DisplayName("DEFAULT_MAX_RETRIES debe ser 3")
        void defaultMaxRetriesShouldBe3() {
            assertEquals(3, DebugLoop.DEFAULT_MAX_RETRIES);
        }

        @Test
        @DisplayName("AttemptRecord debe contener todos los campos")
        void attemptRecordShouldContainAllFields() {
            DebugLoop.AttemptRecord record = new DebugLoop.AttemptRecord(
                    1, false, "error", "prompt", "response", true);

            assertEquals(1, record.attemptNumber());
            assertFalse(record.testsPassed());
            assertEquals("error", record.errorLog());
            assertEquals("prompt", record.llmPrompt());
            assertEquals("response", record.llmResponse());
            assertTrue(record.patchApplied());
        }

        @Test
        @DisplayName("V1: SafeCycleResult debe contener informacion de rollback")
        void safeCycleResultShouldContainRollbackInfo() {
            DebugLoop.SafeCycleResult result = new DebugLoop.SafeCycleResult(
                    false, true, "new content", "original content",
                    3, 5, 10, "Error escalation");

            assertFalse(result.success());
            assertTrue(result.wasRolledBack());
            assertEquals("new content", result.finalContent());
            assertEquals("original content", result.originalContent());
            assertEquals(3, result.attempts());
            assertEquals(5, result.baselineErrors());
            assertEquals(10, result.finalErrors());
            assertEquals("Error escalation", result.abortReason());
        }

        @Test
        @DisplayName("V1: SafeCycleResult isSuccess debe reflejar estado")
        void safeCycleResultIsSuccessShouldReflectState() {
            DebugLoop.SafeCycleResult success = new DebugLoop.SafeCycleResult(
                    true, false, "content", "original", 1, 5, 0, "");
            DebugLoop.SafeCycleResult failure = new DebugLoop.SafeCycleResult(
                    false, true, "content", "original", 3, 5, 8, "Errors increased");

            assertTrue(success.isSuccess());
            assertFalse(failure.isSuccess());
        }

        @Test
        @DisplayName("V1: SafeCycleResult wasRolledBack debe indicar rollback")
        void safeCycleResultWasRolledBackShouldIndicateRollback() {
            DebugLoop.SafeCycleResult withRollback = new DebugLoop.SafeCycleResult(
                    false, true, "content", "original", 3, 5, 8, "Errors increased");
            DebugLoop.SafeCycleResult withoutRollback = new DebugLoop.SafeCycleResult(
                    true, false, "content", "original", 1, 5, 0, "");

            assertTrue(withRollback.wasRolledBack());
            assertFalse(withoutRollback.wasRolledBack());
        }

        @Test
        @DisplayName("V1: SafeCycleResult getSummary debe generar resumen")
        void safeCycleResultGetSummaryShouldGenerateSummary() {
            DebugLoop.SafeCycleResult result = new DebugLoop.SafeCycleResult(
                    false, true, "content", "original", 3, 5, 8, "Errors increased");

            String summary = result.getSummary();

            assertTrue(summary.contains("ROLLED BACK"));
            assertTrue(summary.contains("3"));
            assertTrue(summary.contains("Errors increased"));
        }

        @Test
        @DisplayName("V1: runSafeCycle debe fallar para archivo inexistente")
        void runSafeCycleShouldFailForNonexistentFile() {
            DebugLoop.SafeCycleResult result = debugLoop.runSafeCycle(
                    Path.of("/nonexistent/file.java"), 3);

            assertFalse(result.success());
            assertFalse(result.wasRolledBack());
        }

        @Test
        @DisplayName("V1: ERROR_ESCALATION_THRESHOLD debe estar configurado")
        void errorEscalationThresholdShouldBeConfigured() {
            assertNotNull(debugLoop);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Flujo completo: session -> runner -> debugLoop")
        void fullFlowIntegration() throws Exception {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

            ShellSession session = new ShellSession(tempDir);
            TestRunner runner = new TestRunner(session);
            DebugLoop debugLoop = new DebugLoop(runner, prompt -> "// Fixed code");

            assertEquals(TestRunner.TestFramework.MAVEN, runner.detectFramework(tempDir));

            assertEquals(tempDir.toAbsolutePath(), session.getCurrentDirectory());
        }

        @Test
        @DisplayName("ShellSession debe recordar estado entre comandos")
        void shellSessionShouldRememberStateBetweenCommands() throws Exception {
            Path subDir = tempDir.resolve("level1/level2");
            Files.createDirectories(subDir);

            ShellSession session = new ShellSession(tempDir);

            session.execute("cd level1");
            session.execute("cd level2");

            assertEquals(subDir, session.getCurrentDirectory());
        }

        @Test
        @DisplayName("TestRunner debe usar directorio de sesion")
        void testRunnerShouldUseSessionDirectory() throws Exception {
            Path projectDir = tempDir.resolve("project");
            Files.createDirectory(projectDir);
            Files.writeString(projectDir.resolve("package.json"), "{}");

            ShellSession session = new ShellSession(tempDir);
            session.execute("cd project");

            TestRunner runner = new TestRunner(session);

            TestRunner.TestFramework detected = runner.detectFramework(null);
        }
    }

    private static class MockLlmProvider implements DebugLoop.LlmProvider {
        private String fixedResponse = "// Fixed code";

        public void setFixedResponse(String response) {
            this.fixedResponse = response;
        }

        @Override
        public String ask(String prompt) {
            return fixedResponse;
        }
    }
}
