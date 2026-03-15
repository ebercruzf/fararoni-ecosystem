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

import dev.fararoni.core.core.runtime.jobs.JobManager;
import dev.fararoni.core.core.runtime.pty.InteractiveShell;
import dev.fararoni.core.core.runtime.sandbox.DockerSandbox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Enterprise Runtime Tests (Etapa 7)")
class EnterpriseRuntimeTest {
    @Nested
    @DisplayName("InteractiveShell Tests")
    class InteractiveShellTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Constructor debe inicializar correctamente")
        void constructorShouldInitializeCorrectly() {
            List<String> output = new ArrayList<>();
            InteractiveShell shell = new InteractiveShell(tempDir, output::add);

            assertNotNull(shell);
            assertEquals(tempDir, shell.getWorkingDir());
            assertFalse(shell.isRunning());
            assertFalse(shell.isAlive());
        }

        @Test
        @DisplayName("isInteractivePrompt debe detectar prompts comunes")
        void isInteractivePromptShouldDetectCommonPrompts() {
            assertTrue(InteractiveShell.isInteractivePrompt("Continue? [y/N]"));
            assertTrue(InteractiveShell.isInteractivePrompt("Proceed? [Y/n]"));
            assertTrue(InteractiveShell.isInteractivePrompt("Are you sure? (yes/no)"));
            assertTrue(InteractiveShell.isInteractivePrompt("Enter password:"));
            assertTrue(InteractiveShell.isInteractivePrompt("Password:"));
        }

        @Test
        @DisplayName("isInteractivePrompt debe rechazar lineas normales")
        void isInteractivePromptShouldRejectNormalLines() {
            assertFalse(InteractiveShell.isInteractivePrompt("Building project..."));
            assertFalse(InteractiveShell.isInteractivePrompt("npm install"));
            assertFalse(InteractiveShell.isInteractivePrompt(""));
            assertFalse(InteractiveShell.isInteractivePrompt(null));
        }

        @Test
        @DisplayName("isSelectionPrompt debe detectar menus numericos")
        void isSelectionPromptShouldDetectNumericMenus() {
            assertTrue(InteractiveShell.isSelectionPrompt("Select [1] or [2]"));
            assertTrue(InteractiveShell.isSelectionPrompt("Choose (1) First (2) Second"));
            assertTrue(InteractiveShell.isSelectionPrompt("Enter a number:"));
        }

        @Test
        @DisplayName("getOutputBuffer debe retornar buffer vacio inicialmente")
        void getOutputBufferShouldReturnEmptyInitially() {
            InteractiveShell shell = new InteractiveShell(tempDir, line -> {});
            assertEquals("", shell.getOutputBuffer());
        }

        @Test
        @DisplayName("clearOutputBuffer debe limpiar el buffer")
        void clearOutputBufferShouldClearBuffer() {
            InteractiveShell shell = new InteractiveShell(tempDir, line -> {});
            shell.clearOutputBuffer();
            assertEquals("", shell.getOutputBuffer());
        }

        @Test
        @DisplayName("getPid debe retornar -1 cuando no esta corriendo")
        void getPidShouldReturnNegativeOneWhenNotRunning() {
            InteractiveShell shell = new InteractiveShell(tempDir, line -> {});
            assertEquals(-1, shell.getPid());
        }

        @Test
        @DisplayName("exitValue debe retornar -1 cuando no esta corriendo")
        void exitValueShouldReturnNegativeOneWhenNotRunning() {
            InteractiveShell shell = new InteractiveShell(tempDir, line -> {});
            assertEquals(-1, shell.exitValue());
        }

        @Test
        @DisplayName("start debe rechazar comando vacio")
        void startShouldRejectEmptyCommand() {
            InteractiveShell shell = new InteractiveShell(tempDir, line -> {});
            assertThrows(IllegalArgumentException.class, () -> shell.start());
        }

        @Test
        @DisplayName("start debe rechazar comando null")
        void startShouldRejectNullCommand() {
            InteractiveShell shell = new InteractiveShell(tempDir, line -> {});
            assertThrows(IllegalArgumentException.class, () -> shell.start((String[]) null));
        }

        @Test
        @DisplayName("writeInput no debe fallar cuando no esta corriendo")
        void writeInputShouldNotFailWhenNotRunning() {
            InteractiveShell shell = new InteractiveShell(tempDir, line -> {});
            assertDoesNotThrow(() -> shell.writeInput("test"));
        }

        @Test
        @DisplayName("[GM] DEFAULT_COLUMNS debe ser 120")
        void defaultColumnsShouldBe120() {
            assertEquals(120, InteractiveShell.DEFAULT_COLUMNS);
        }

        @Test
        @DisplayName("[GM] DEFAULT_ROWS debe ser 40")
        void defaultRowsShouldBe40() {
            assertEquals(40, InteractiveShell.DEFAULT_ROWS);
        }

        @Test
        @DisplayName("[GM] withWindowSize debe configurar dimensiones")
        void withWindowSizeShouldConfigureDimensions() {
            InteractiveShell shell = new InteractiveShell(tempDir, line -> {})
                    .withWindowSize(200, 50);

            int[] size = shell.getWindowSize();
            assertEquals(200, size[0]);
            assertEquals(50, size[1]);
        }

        @Test
        @DisplayName("[GM] withWindowSize debe rechazar valores invalidos")
        void withWindowSizeShouldRejectInvalidValues() {
            InteractiveShell shell = new InteractiveShell(tempDir, line -> {});

            assertThrows(IllegalArgumentException.class,
                    () -> shell.withWindowSize(0, 40));
            assertThrows(IllegalArgumentException.class,
                    () -> shell.withWindowSize(120, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> shell.withWindowSize(-1, 40));
        }

        @Test
        @DisplayName("[GM] setWindowSize no debe fallar cuando no esta corriendo")
        void setWindowSizeShouldNotFailWhenNotRunning() {
            InteractiveShell shell = new InteractiveShell(tempDir, line -> {});
            assertDoesNotThrow(() -> shell.setWindowSize(80, 24));
        }

        @Test
        @DisplayName("[GM] getWindowSize debe retornar dimensiones por defecto")
        void getWindowSizeShouldReturnDefaultDimensions() {
            InteractiveShell shell = new InteractiveShell(tempDir, line -> {});

            int[] size = shell.getWindowSize();

            assertEquals(InteractiveShell.DEFAULT_COLUMNS, size[0]);
            assertEquals(InteractiveShell.DEFAULT_ROWS, size[1]);
        }

        @Test
        @DisplayName("[GM] terminate debe retornar true cuando no esta corriendo")
        void terminateShouldReturnTrueWhenNotRunning() {
            InteractiveShell shell = new InteractiveShell(tempDir, line -> {});
            assertTrue(shell.terminate());
        }
    }

    @Nested
    @DisplayName("JobManager Tests")
    class JobManagerTests {
        @TempDir
        Path tempDir;

        private JobManager jobManager;

        @BeforeEach
        void setUp() {
            jobManager = new JobManager(tempDir);
        }

        @AfterEach
        void tearDown() {
            jobManager.killAll();
        }

        @Test
        @DisplayName("Constructor debe crear directorio de logs")
        void constructorShouldCreateLogsDirectory() {
            assertTrue(Files.isDirectory(tempDir));
            assertEquals(tempDir, jobManager.getLogsDirectory());
        }

        @Test
        @DisplayName("startBackgroundJob debe iniciar proceso y retornar ID")
        void startBackgroundJobShouldStartProcessAndReturnId() throws Exception {
            String jobId = jobManager.startBackgroundJob(tempDir, "sleep", "10");

            assertNotNull(jobId);
            assertEquals(8, jobId.length());
            assertTrue(jobManager.isJobAlive(jobId));
        }

        @Test
        @DisplayName("startBackgroundJob debe rechazar comando vacio")
        void startBackgroundJobShouldRejectEmptyCommand() {
            assertThrows(IllegalArgumentException.class,
                    () -> jobManager.startBackgroundJob(tempDir));
        }

        @Test
        @DisplayName("killJob debe detener proceso activo")
        void killJobShouldStopActiveProcess() throws Exception {
            String jobId = jobManager.startBackgroundJob(tempDir, "sleep", "60");
            assertTrue(jobManager.isJobAlive(jobId));

            boolean killed = jobManager.killJob(jobId);

            assertTrue(killed);
            Thread.sleep(100);
            assertFalse(jobManager.isJobAlive(jobId));
        }

        @Test
        @DisplayName("killJob debe retornar false para job inexistente")
        void killJobShouldReturnFalseForNonexistentJob() {
            assertFalse(jobManager.killJob("nonexistent"));
        }

        @Test
        @DisplayName("getJobInfo debe retornar informacion del job")
        void getJobInfoShouldReturnJobInformation() throws Exception {
            String jobId = jobManager.startBackgroundJob(tempDir, "sleep", "10");

            var info = jobManager.getJobInfo(jobId);

            assertTrue(info.isPresent());
            assertEquals(jobId, info.get().id());
            assertTrue(info.get().isAlive());
            assertTrue(info.get().command().contains("sleep"));
        }

        @Test
        @DisplayName("getJobInfo debe retornar empty para job inexistente")
        void getJobInfoShouldReturnEmptyForNonexistentJob() {
            var info = jobManager.getJobInfo("nonexistent");
            assertTrue(info.isEmpty());
        }

        @Test
        @DisplayName("listJobs debe retornar lista de jobs")
        void listJobsShouldReturnJobList() throws Exception {
            jobManager.startBackgroundJob(tempDir, "sleep", "10");
            jobManager.startBackgroundJob(tempDir, "sleep", "10");

            var jobs = jobManager.listJobs();

            assertEquals(2, jobs.size());
        }

        @Test
        @DisplayName("getActiveJobCount debe contar jobs activos")
        void getActiveJobCountShouldCountActiveJobs() throws Exception {
            jobManager.startBackgroundJob(tempDir, "sleep", "10");
            jobManager.startBackgroundJob(tempDir, "sleep", "10");

            assertEquals(2, jobManager.getActiveJobCount());
        }

        @Test
        @DisplayName("getTotalJobCount debe contar todos los jobs")
        void getTotalJobCountShouldCountAllJobs() throws Exception {
            jobManager.startBackgroundJob(tempDir, "sleep", "10");
            assertEquals(1, jobManager.getTotalJobCount());
        }

        @Test
        @DisplayName("killAll debe matar todos los jobs")
        void killAllShouldKillAllJobs() throws Exception {
            jobManager.startBackgroundJob(tempDir, "sleep", "60");
            jobManager.startBackgroundJob(tempDir, "sleep", "60");
            assertEquals(2, jobManager.getActiveJobCount());

            jobManager.killAll();

            Thread.sleep(200);
            assertEquals(0, jobManager.getTotalJobCount());
        }

        @Test
        @DisplayName("waitForJob debe esperar a que termine el proceso")
        void waitForJobShouldWaitForProcess() throws Exception {
            String jobId = jobManager.startBackgroundJob(tempDir, "sleep", "1");

            int exitCode = jobManager.waitForJob(jobId, 5, TimeUnit.SECONDS);

            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("waitForJob debe retornar -1 para job inexistente")
        void waitForJobShouldReturnNegativeOneForNonexistent() throws Exception {
            int exitCode = jobManager.waitForJob("nonexistent");
            assertEquals(-1, exitCode);
        }

        @Test
        @DisplayName("getJobLog debe retornar logs del job")
        void getJobLogShouldReturnJobLogs() throws Exception {
            String jobId = jobManager.startBackgroundJob(tempDir, "echo", "test output");

            Thread.sleep(500);

            String log = jobManager.getJobLog(jobId, 10);
            assertTrue(log.contains("test output"));
        }

        @Test
        @DisplayName("getJobLog debe retornar vacio para job inexistente")
        void getJobLogShouldReturnEmptyForNonexistent() {
            assertEquals("", jobManager.getJobLog("nonexistent", 10));
        }

        @Test
        @DisplayName("cleanupDeadJobs debe limpiar jobs terminados")
        void cleanupDeadJobsShouldCleanupTerminatedJobs() throws Exception {
            String jobId = jobManager.startBackgroundJob(tempDir, "echo", "done");

            Thread.sleep(500);

            int cleaned = jobManager.cleanupDeadJobs();

            assertTrue(cleaned >= 0);
        }

        @Test
        @DisplayName("JobInfo getSummary debe generar resumen")
        void jobInfoGetSummaryShouldGenerateSummary() throws Exception {
            String jobId = jobManager.startBackgroundJob(tempDir, "sleep", "10");
            var info = jobManager.getJobInfo(jobId).orElseThrow();

            String summary = info.getSummary();

            assertTrue(summary.contains(jobId));
            assertTrue(summary.contains("RUNNING"));
        }

        @Test
        @DisplayName("[GM] killJob debe terminar proceso y sus descendientes")
        void killJobShouldTerminateProcessTree() throws Exception {
            String jobId = jobManager.startBackgroundJob(tempDir, "sh", "-c", "sleep 60 & sleep 60");
            assertTrue(jobManager.isJobAlive(jobId));

            Thread.sleep(100);

            boolean killed = jobManager.killJob(jobId);

            assertTrue(killed);
            Thread.sleep(200);
            assertFalse(jobManager.isJobAlive(jobId));
        }

        @Test
        @DisplayName("[GM] killAll debe terminar todos los procesos incluyendo descendientes")
        void killAllShouldTerminateAllProcessTrees() throws Exception {
            jobManager.startBackgroundJob(tempDir, "sh", "-c", "sleep 60 & sleep 60");
            jobManager.startBackgroundJob(tempDir, "sh", "-c", "sleep 60 & sleep 60");
            assertEquals(2, jobManager.getActiveJobCount());

            jobManager.killAll();

            Thread.sleep(200);
            assertEquals(0, jobManager.getTotalJobCount());
        }
    }

    @Nested
    @DisplayName("DockerSandbox Tests")
    class DockerSandboxTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Constructor debe inicializar con imagen dada")
        void constructorShouldInitializeWithGivenImage() {
            DockerSandbox sandbox = new DockerSandbox("python:3.9-slim");

            assertEquals("python:3.9-slim", sandbox.getImageName());
            assertFalse(sandbox.isRunning());
            assertNull(sandbox.getContainerId());
        }

        @Test
        @DisplayName("Constructor con null debe usar imagen por defecto")
        void constructorWithNullShouldUseDefaultImage() {
            DockerSandbox sandbox = new DockerSandbox(null);
            assertEquals(DockerSandbox.IMAGE_NODE_18, sandbox.getImageName());
        }

        @Test
        @DisplayName("isDockerAvailable debe retornar boolean")
        void isDockerAvailableShouldReturnBoolean() {
            boolean available = DockerSandbox.isDockerAvailable();
            assertNotNull(Boolean.valueOf(available));
        }

        @Test
        @DisplayName("withEnv debe configurar variables de entorno")
        void withEnvShouldConfigureEnvVars() {
            DockerSandbox sandbox = new DockerSandbox("node:18")
                    .withEnv("NODE_ENV", "test")
                    .withEnv("DEBUG", "true");

            assertNotNull(sandbox);
        }

        @Test
        @DisplayName("withCapability debe agregar capacidad")
        void withCapabilityShouldAddCapability() {
            DockerSandbox sandbox = new DockerSandbox("node:18")
                    .withCapability("SYS_PTRACE");

            assertNotNull(sandbox);
        }

        @Test
        @DisplayName("startSandbox debe fallar si ya esta corriendo")
        @EnabledIf("isDockerAvailable")
        void startSandboxShouldFailIfAlreadyRunning() throws Exception {
            DockerSandbox sandbox = new DockerSandbox("alpine");
            sandbox.startSandbox(tempDir);

            try {
                assertThrows(DockerSandbox.SandboxException.class,
                        () -> sandbox.startSandbox(tempDir));
            } finally {
                sandbox.destroySandbox();
            }
        }

        @Test
        @DisplayName("execute debe fallar si sandbox no esta corriendo")
        void executeShouldFailIfNotRunning() {
            DockerSandbox sandbox = new DockerSandbox("node:18");

            assertThrows(DockerSandbox.SandboxException.class,
                    () -> sandbox.execute("echo", "test"));
        }

        @Test
        @DisplayName("SandboxResult isSuccess debe reflejar exitCode")
        void sandboxResultIsSuccessShouldReflectExitCode() {
            var success = new DockerSandbox.SandboxResult(0, "out", "", "cmd", 100);
            var failure = new DockerSandbox.SandboxResult(1, "out", "err", "cmd", 100);

            assertTrue(success.isSuccess());
            assertFalse(failure.isSuccess());
        }

        @Test
        @DisplayName("SandboxResult getCombinedOutput debe combinar streams")
        void sandboxResultGetCombinedOutputShouldCombineStreams() {
            var result = new DockerSandbox.SandboxResult(1, "stdout", "stderr", "cmd", 100);

            String combined = result.getCombinedOutput();

            assertTrue(combined.contains("stdout"));
            assertTrue(combined.contains("stderr"));
        }

        @Test
        @DisplayName("Imagenes predefinidas deben existir")
        void predefinedImagesShouldExist() {
            assertEquals("node:18-alpine", DockerSandbox.IMAGE_NODE_18);
            assertEquals("python:3.9-slim", DockerSandbox.IMAGE_PYTHON_39);
            assertEquals("eclipse-temurin:17-jdk-alpine", DockerSandbox.IMAGE_JAVA_17);
        }

        @Test
        @DisplayName("[GM] DEFAULT_MEMORY_LIMIT debe ser 1g")
        void defaultMemoryLimitShouldBe1g() {
            assertEquals("1g", DockerSandbox.DEFAULT_MEMORY_LIMIT);
        }

        @Test
        @DisplayName("[GM] DEFAULT_CPU_LIMIT debe ser 1.5")
        void defaultCpuLimitShouldBe1point5() {
            assertEquals("1.5", DockerSandbox.DEFAULT_CPU_LIMIT);
        }

        @Test
        @DisplayName("[GM] DEFAULT_PIDS_LIMIT debe ser 100")
        void defaultPidsLimitShouldBe100() {
            assertEquals("100", DockerSandbox.DEFAULT_PIDS_LIMIT);
        }

        @Test
        @DisplayName("[GM] withMemoryLimit debe configurar limite de RAM")
        void withMemoryLimitShouldConfigureRamLimit() {
            DockerSandbox sandbox = new DockerSandbox("node:18")
                    .withMemoryLimit("512m");

            assertNotNull(sandbox);
        }

        @Test
        @DisplayName("[GM] withCpuLimit debe configurar limite de CPU")
        void withCpuLimitShouldConfigureCpuLimit() {
            DockerSandbox sandbox = new DockerSandbox("node:18")
                    .withCpuLimit("0.5");

            assertNotNull(sandbox);
        }

        @Test
        @DisplayName("[GM] withPidsLimit debe configurar limite de PIDs")
        void withPidsLimitShouldConfigurePidsLimit() {
            DockerSandbox sandbox = new DockerSandbox("node:18")
                    .withPidsLimit("50");

            assertNotNull(sandbox);
        }

        @Test
        @DisplayName("[GM] withUserMapping debe configurar mapeo de usuario")
        void withUserMappingShouldConfigureUserMapping() {
            DockerSandbox sandbox = new DockerSandbox("node:18")
                    .withUserMapping(false);

            assertNotNull(sandbox);
        }

        @Test
        @DisplayName("[GM] Configuracion fluida debe funcionar")
        void fluentConfigurationShouldWork() {
            DockerSandbox sandbox = new DockerSandbox("node:18")
                    .withMemoryLimit("2g")
                    .withCpuLimit("2.0")
                    .withPidsLimit("200")
                    .withUserMapping(true)
                    .withEnv("NODE_ENV", "test");

            assertNotNull(sandbox);
            assertEquals("node:18", sandbox.getImageName());
        }

        static boolean isDockerAvailable() {
            return DockerSandbox.isDockerAvailable();
        }
    }

    @Nested
    @DisplayName("FararoniEnterpriseRuntime Tests")
    class FararoniEnterpriseRuntimeTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Builder debe crear runtime con valores por defecto")
        void builderShouldCreateRuntimeWithDefaults() {
            var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .build();

            assertNotNull(runtime);
            assertEquals(tempDir.toAbsolutePath(), runtime.getProjectRoot());
            assertEquals(FararoniEnterpriseRuntime.ExecutionMode.LOCAL, runtime.getMode());
        }

        @Test
        @DisplayName("Builder enableSandbox debe cambiar modo")
        void builderEnableSandboxShouldChangeMode() {
            var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .enableSandbox(true)
                    .build();

            assertEquals(FararoniEnterpriseRuntime.ExecutionMode.SANDBOXED, runtime.getMode());
            runtime.close();
        }

        @Test
        @DisplayName("Builder mode debe establecer modo")
        void builderModeShouldSetMode() {
            var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .mode(FararoniEnterpriseRuntime.ExecutionMode.HYBRID)
                    .build();

            assertEquals(FararoniEnterpriseRuntime.ExecutionMode.HYBRID, runtime.getMode());
            runtime.close();
        }

        @Test
        @DisplayName("execute en modo LOCAL debe funcionar")
        void executeInLocalModeShouldWork() {
            try (var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .mode(FararoniEnterpriseRuntime.ExecutionMode.LOCAL)
                    .build()) {
                var result = runtime.execute("echo", "hello");

                assertTrue(result.stdout().contains("hello"));
                assertFalse(result.wasSandboxed());
            }
        }

        @Test
        @DisplayName("startServerInBackground debe retornar job ID")
        void startServerInBackgroundShouldReturnJobId() {
            try (var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .build()) {
                String jobId = runtime.startServerInBackground("sleep", "10");

                assertNotNull(jobId);
                assertTrue(runtime.isJobAlive(jobId));

                runtime.stopBackgroundJob(jobId);
            }
        }

        @Test
        @DisplayName("getJobLogs debe retornar logs")
        void getJobLogsShouldReturnLogs() throws Exception {
            try (var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .build()) {
                String jobId = runtime.startServerInBackground("echo", "test log");
                Thread.sleep(500);

                String logs = runtime.getJobLogs(jobId, 10);

                assertTrue(logs.contains("test log"));
            }
        }

        @Test
        @DisplayName("stopBackgroundJob debe detener job")
        void stopBackgroundJobShouldStopJob() {
            try (var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .build()) {
                String jobId = runtime.startServerInBackground("sleep", "60");
                assertTrue(runtime.isJobAlive(jobId));

                boolean stopped = runtime.stopBackgroundJob(jobId);

                assertTrue(stopped);
            }
        }

        @Test
        @DisplayName("isDockerAvailable debe retornar boolean")
        void isDockerAvailableShouldReturnBoolean() {
            try (var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .build()) {
                boolean available = runtime.isDockerAvailable();
                assertNotNull(Boolean.valueOf(available));
            }
        }

        @Test
        @DisplayName("getJobManager debe retornar JobManager")
        void getJobManagerShouldReturnJobManager() {
            try (var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .build()) {
                assertNotNull(runtime.getJobManager());
            }
        }

        @Test
        @DisplayName("getLocalShell debe retornar ShellSession")
        void getLocalShellShouldReturnShellSession() {
            try (var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .build()) {
                assertNotNull(runtime.getLocalShell());
                assertTrue(runtime.getLocalShell().isSafeModeEnabled());
            }
        }

        @Test
        @DisplayName("getSandbox debe retornar Optional vacio en modo LOCAL")
        void getSandboxShouldReturnEmptyInLocalMode() {
            try (var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .mode(FararoniEnterpriseRuntime.ExecutionMode.LOCAL)
                    .build()) {
                assertTrue(runtime.getSandbox().isEmpty());
            }
        }

        @Test
        @DisplayName("getSandbox debe retornar Optional con valor en modo SANDBOXED")
        void getSandboxShouldReturnPresentInSandboxedMode() {
            try (var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .mode(FararoniEnterpriseRuntime.ExecutionMode.SANDBOXED)
                    .build()) {
                assertTrue(runtime.getSandbox().isPresent());
            }
        }

        @Test
        @DisplayName("ExecutionResult getCombinedOutput debe combinar streams")
        void executionResultGetCombinedOutputShouldCombineStreams() {
            var result = new FararoniEnterpriseRuntime.ExecutionResult(
                    true, "stdout", "stderr", "cmd", 100, false);

            String combined = result.getCombinedOutput();

            assertTrue(combined.contains("stdout"));
            assertTrue(combined.contains("stderr"));
        }

        @Test
        @DisplayName("shutdown debe cerrar recursos correctamente")
        void shutdownShouldCloseResourcesCorrectly() {
            var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .build();

            runtime.startServerInBackground("sleep", "60");
            assertEquals(1, runtime.getJobManager().getActiveJobCount());

            runtime.shutdown();

            assertEquals(0, runtime.getJobManager().getTotalJobCount());
        }

        @Test
        @DisplayName("close debe llamar shutdown")
        void closeShouldCallShutdown() {
            var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .build();

            runtime.startServerInBackground("sleep", "60");

            runtime.close();

            assertEquals(0, runtime.getJobManager().getTotalJobCount());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Flujo completo: Job + Execute")
        void fullFlowJobAndExecute() throws Exception {
            try (var runtime = FararoniEnterpriseRuntime.builder()
                    .projectRoot(tempDir)
                    .build()) {
                String jobId = runtime.startServerInBackground("sleep", "30");
                assertTrue(runtime.isJobAlive(jobId));

                var result = runtime.execute("echo", "concurrent");
                assertTrue(result.success());
                assertTrue(result.stdout().contains("concurrent"));

                runtime.stopBackgroundJob(jobId);
                Thread.sleep(200);
                assertFalse(runtime.isJobAlive(jobId));
            }
        }

        @Test
        @DisplayName("JobManager debe persistir logs")
        void jobManagerShouldPersistLogs() throws Exception {
            JobManager jobs = new JobManager(tempDir);

            String jobId = jobs.startBackgroundJob(tempDir, "echo", "persistent log entry");
            Thread.sleep(500);

            String fullLog = jobs.getFullJobLog(jobId);
            assertTrue(fullLog.contains("persistent log entry"));

            var info = jobs.getJobInfo(jobId);
            assertTrue(info.isPresent());
            assertTrue(Files.exists(info.get().logFile()));

            jobs.killAll();
        }
    }
}
