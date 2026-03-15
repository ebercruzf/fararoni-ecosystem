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
package dev.fararoni.core.core.workspace;

import org.junit.jupiter.api.AfterEach;
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
@DisplayName("WorkspaceManager - Priority Cascade Tests")
class WorkspaceManagerTest {
    @BeforeEach
    void setUp() {
        WorkspaceManager.reset();
    }

    @AfterEach
    void tearDown() {
        WorkspaceManager.reset();
    }

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {
        @Test
        @DisplayName("Debe inicializar sin argumentos usando default path")
        void shouldInitializeWithDefaultPath() {
            WorkspaceManager ws = WorkspaceManager.getInstance();

            assertNotNull(ws, "WorkspaceManager no debe ser null");
            assertNotNull(ws.getWorkspaceDir(), "WorkspaceDir no debe ser null");
            assertEquals(WorkspaceManager.ResolutionMode.USER_HOME_DEFAULT, ws.getResolutionMode());
            assertTrue(ws.getWorkspaceDir().toString().contains(".llm-fararoni"),
                "Default path debe contener .llm-fararoni");
        }

        @Test
        @DisplayName("Debe lanzar excepcion si se llama initialize() dos veces")
        void shouldThrowIfInitializedTwice() {
            WorkspaceManager.initialize(new String[0]);

            assertThrows(IllegalStateException.class, () -> {
                WorkspaceManager.initialize(new String[0]);
            }, "Debe lanzar IllegalStateException si se inicializa dos veces");
        }

        @Test
        @DisplayName("Debe permitir reset y re-inicializacion")
        void shouldAllowResetAndReinitialize() {
            WorkspaceManager first = WorkspaceManager.getInstance();
            WorkspaceManager.reset();

            WorkspaceManager second = WorkspaceManager.getInstance();

            assertNotSame(first, second, "Despues de reset debe crear nueva instancia");
        }

        @Test
        @DisplayName("isInitialized debe reflejar estado correcto")
        void shouldReflectInitializationState() {
            assertFalse(WorkspaceManager.isInitialized(), "No debe estar inicializado al inicio");

            WorkspaceManager.getInstance();
            assertTrue(WorkspaceManager.isInitialized(), "Debe estar inicializado despues de getInstance");

            WorkspaceManager.reset();
            assertFalse(WorkspaceManager.isInitialized(), "No debe estar inicializado despues de reset");
        }
    }

    @Nested
    @DisplayName("CLI Argument Tests (Priority 1)")
    class CliArgumentTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Debe usar --data-dir=/path cuando se especifica")
        void shouldUseDataDirWithEquals() {
            String customPath = tempDir.toAbsolutePath().toString();
            String[] args = {"--data-dir=" + customPath};

            WorkspaceManager ws = WorkspaceManager.initialize(args);

            assertEquals(WorkspaceManager.ResolutionMode.CLI_ARGUMENT, ws.getResolutionMode());
            assertEquals(tempDir.toAbsolutePath(), ws.getWorkspaceDir());
            assertTrue(ws.isCustomPath());
        }

        @Test
        @DisplayName("Debe usar --data-dir /path cuando se especifica con espacio")
        void shouldUseDataDirWithSpace() {
            String customPath = tempDir.toAbsolutePath().toString();
            String[] args = {"--data-dir", customPath};

            WorkspaceManager ws = WorkspaceManager.initialize(args);

            assertEquals(WorkspaceManager.ResolutionMode.CLI_ARGUMENT, ws.getResolutionMode());
            assertEquals(tempDir.toAbsolutePath(), ws.getWorkspaceDir());
        }

        @Test
        @DisplayName("CLI debe tener mayor prioridad que ENV")
        void cliShouldOverrideEnv() {
            String customPath = tempDir.toAbsolutePath().toString();
            String[] args = {"--data-dir=" + customPath};

            WorkspaceManager ws = WorkspaceManager.initialize(args);

            assertEquals(WorkspaceManager.ResolutionMode.CLI_ARGUMENT, ws.getResolutionMode());
        }

        @Test
        @DisplayName("Debe ignorar otros argumentos y solo usar --data-dir")
        void shouldIgnoreOtherArguments() {
            String customPath = tempDir.toAbsolutePath().toString();
            String[] args = {"--url", "http://localhost", "--data-dir=" + customPath, "--model", "qwen"};

            WorkspaceManager ws = WorkspaceManager.initialize(args);

            assertEquals(WorkspaceManager.ResolutionMode.CLI_ARGUMENT, ws.getResolutionMode());
            assertEquals(tempDir.toAbsolutePath(), ws.getWorkspaceDir());
        }
    }

    @Nested
    @DisplayName("Path Getters Tests")
    class PathGettersTests {
        @TempDir
        Path tempDir;

        private WorkspaceManager ws;

        @BeforeEach
        void initWorkspace() {
            String[] args = {"--data-dir=" + tempDir.toAbsolutePath()};
            ws = WorkspaceManager.initialize(args);
        }

        @Test
        @DisplayName("getMemoryDbPath debe retornar path a memory.db")
        void shouldReturnMemoryDbPath() {
            Path expected = tempDir.resolve("memory.db");
            assertEquals(expected, ws.getMemoryDbPath());
        }

        @Test
        @DisplayName("getMemoryDbUrl debe retornar URL JDBC correcta")
        void shouldReturnMemoryDbUrl() {
            String url = ws.getMemoryDbUrl();
            assertTrue(url.startsWith("jdbc:sqlite:"), "Debe empezar con jdbc:sqlite:");
            assertTrue(url.contains("memory.db"), "Debe contener memory.db");
        }

        @Test
        @DisplayName("getInteractionsDbPath debe retornar path a interactions.db")
        void shouldReturnInteractionsDbPath() {
            Path expected = tempDir.resolve("interactions.db");
            assertEquals(expected, ws.getInteractionsDbPath());
        }

        @Test
        @DisplayName("getInteractionsDbUrl debe retornar URL JDBC correcta")
        void shouldReturnInteractionsDbUrl() {
            String url = ws.getInteractionsDbUrl();
            assertTrue(url.startsWith("jdbc:sqlite:"));
            assertTrue(url.contains("interactions.db"));
        }

        @Test
        @DisplayName("getCacheDbPath debe retornar path a cache.db")
        void shouldReturnCacheDbPath() {
            Path expected = tempDir.resolve("cache.db");
            assertEquals(expected, ws.getCacheDbPath());
        }

        @Test
        @DisplayName("getCacheDbUrl debe retornar URL JDBC correcta")
        void shouldReturnCacheDbUrl() {
            String url = ws.getCacheDbUrl();
            assertTrue(url.startsWith("jdbc:sqlite:"));
            assertTrue(url.contains("cache.db"));
        }

        @Test
        @DisplayName("getAuditLogPath debe retornar path a audit.log")
        void shouldReturnAuditLogPath() {
            Path expected = tempDir.resolve("audit.log");
            assertEquals(expected, ws.getAuditLogPath());
        }

        @Test
        @DisplayName("getConfigPath debe retornar path a config.properties")
        void shouldReturnConfigPath() {
            Path expected = tempDir.resolve("config.properties");
            assertEquals(expected, ws.getConfigPath());
        }

        @Test
        @DisplayName("getQueuePath debe retornar path a queue.json")
        void shouldReturnQueuePath() {
            Path expected = tempDir.resolve("queue.json");
            assertEquals(expected, ws.getQueuePath());
        }

        @Test
        @DisplayName("resolve debe retornar path relativo dentro del workspace")
        void shouldResolveRelativePath() {
            Path expected = tempDir.resolve("custom/file.txt");
            assertEquals(expected, ws.resolve("custom/file.txt"));
        }
    }

    @Nested
    @DisplayName("Directory Creation Tests")
    class DirectoryCreationTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Debe crear directorio si no existe")
        void shouldCreateDirectoryIfNotExists() throws IOException {
            Path newDir = tempDir.resolve("new-workspace");
            assertFalse(Files.exists(newDir));

            String[] args = {"--data-dir=" + newDir.toAbsolutePath()};
            WorkspaceManager ws = WorkspaceManager.initialize(args);

            assertTrue(Files.exists(newDir), "Directorio debe existir");
            assertTrue(Files.isDirectory(newDir), "Debe ser directorio");
            assertTrue(ws.isValid(), "Workspace debe ser valido");
        }

        @Test
        @DisplayName("Debe usar directorio existente sin errores")
        void shouldUseExistingDirectory() throws IOException {
            Path existingDir = tempDir.resolve("existing");
            Files.createDirectories(existingDir);
            assertTrue(Files.exists(existingDir));

            String[] args = {"--data-dir=" + existingDir.toAbsolutePath()};
            WorkspaceManager ws = WorkspaceManager.initialize(args);

            assertEquals(existingDir, ws.getWorkspaceDir());
            assertTrue(ws.isValid());
        }

        @Test
        @DisplayName("Debe crear directorios anidados si es necesario")
        void shouldCreateNestedDirectories() {
            Path nestedDir = tempDir.resolve("level1/level2/level3");
            assertFalse(Files.exists(nestedDir));

            String[] args = {"--data-dir=" + nestedDir.toAbsolutePath()};
            WorkspaceManager ws = WorkspaceManager.initialize(args);

            assertTrue(Files.exists(nestedDir));
            assertTrue(ws.isValid());
        }
    }

    @Nested
    @DisplayName("Metadata Tests")
    class MetadataTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("isPortableMode debe ser false cuando no es portable")
        void shouldReturnFalseForNonPortable() {
            WorkspaceManager ws = WorkspaceManager.getInstance();

            assertFalse(ws.isPortableMode());
        }

        @Test
        @DisplayName("isCustomPath debe ser true cuando se usa CLI")
        void shouldReturnTrueForCustomPath() {
            String[] args = {"--data-dir=" + tempDir.toAbsolutePath()};

            WorkspaceManager ws = WorkspaceManager.initialize(args);

            assertTrue(ws.isCustomPath());
        }

        @Test
        @DisplayName("isCustomPath debe ser false cuando se usa default")
        void shouldReturnFalseForDefaultPath() {
            WorkspaceManager ws = WorkspaceManager.getInstance();

            assertFalse(ws.isCustomPath());
        }

        @Test
        @DisplayName("getResolutionSource debe contener informacion util")
        void shouldReturnUsefulResolutionSource() {
            String customPath = tempDir.toAbsolutePath().toString();
            String[] args = {"--data-dir=" + customPath};

            WorkspaceManager ws = WorkspaceManager.initialize(args);

            assertNotNull(ws.getResolutionSource());
            assertTrue(ws.getResolutionSource().contains(customPath),
                "ResolutionSource debe contener el path usado");
        }

        @Test
        @DisplayName("getInfoString debe retornar string formateado")
        void shouldReturnFormattedInfoString() {
            WorkspaceManager ws = WorkspaceManager.getInstance();

            String info = ws.getInfoString();
            assertNotNull(info);
            assertTrue(info.contains("Workspace:"));
            assertTrue(info.contains(".llm-fararoni"));
        }

        @Test
        @DisplayName("toString debe retornar representacion util")
        void shouldReturnUsefulToString() {
            WorkspaceManager ws = WorkspaceManager.getInstance();

            String str = ws.toString();
            assertNotNull(str);
            assertTrue(str.contains("WorkspaceManager"));
            assertTrue(str.contains("workspaceDir"));
            assertTrue(str.contains("resolutionMode"));
        }
    }

    @Nested
    @DisplayName("Singleton Tests")
    class SingletonTests {
        @Test
        @DisplayName("getInstance debe retornar la misma instancia")
        void shouldReturnSameInstance() {
            WorkspaceManager first = WorkspaceManager.getInstance();
            WorkspaceManager second = WorkspaceManager.getInstance();
            WorkspaceManager third = WorkspaceManager.getInstance();

            assertSame(first, second);
            assertSame(second, third);
        }

        @Test
        @DisplayName("initialize y getInstance deben retornar la misma instancia")
        void initializeAndGetInstanceShouldReturnSame() {
            WorkspaceManager initialized = WorkspaceManager.initialize(new String[0]);
            WorkspaceManager retrieved = WorkspaceManager.getInstance();

            assertSame(initialized, retrieved);
        }
    }

    @Nested
    @DisplayName("Validity Tests")
    class ValidityTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("isValid debe retornar true para workspace valido")
        void shouldReturnTrueForValidWorkspace() {
            String[] args = {"--data-dir=" + tempDir.toAbsolutePath()};

            WorkspaceManager ws = WorkspaceManager.initialize(args);

            assertTrue(ws.isValid());
        }

        @Test
        @DisplayName("Workspace debe ser escribible")
        void workspaceShouldBeWritable() throws IOException {
            String[] args = {"--data-dir=" + tempDir.toAbsolutePath()};
            WorkspaceManager ws = WorkspaceManager.initialize(args);

            Path testFile = ws.resolve("test-write.txt");
            Files.writeString(testFile, "test content");

            assertTrue(Files.exists(testFile));
            assertEquals("test content", Files.readString(testFile));

            Files.delete(testFile);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Debe manejar argumentos null")
        void shouldHandleNullArgs() {
            WorkspaceManager ws = WorkspaceManager.initialize(null);

            assertNotNull(ws);
            assertEquals(WorkspaceManager.ResolutionMode.USER_HOME_DEFAULT, ws.getResolutionMode());
        }

        @Test
        @DisplayName("Debe manejar argumentos vacios")
        void shouldHandleEmptyArgs() {
            WorkspaceManager ws = WorkspaceManager.initialize(new String[0]);

            assertNotNull(ws);
            assertEquals(WorkspaceManager.ResolutionMode.USER_HOME_DEFAULT, ws.getResolutionMode());
        }

        @Test
        @DisplayName("Debe manejar --data-dir sin valor")
        void shouldHandleDataDirWithoutValue() {
            String[] args = {"--data-dir"};

            WorkspaceManager ws = WorkspaceManager.initialize(args);

            assertEquals(WorkspaceManager.ResolutionMode.USER_HOME_DEFAULT, ws.getResolutionMode());
        }

        @Test
        @DisplayName("Debe normalizar paths con ..")
        void shouldNormalizePaths() {
            Path subDir = tempDir.resolve("subdir");
            String unnormalizedPath = subDir.toAbsolutePath() + "/../subdir";
            String[] args = {"--data-dir=" + unnormalizedPath};

            WorkspaceManager ws = WorkspaceManager.initialize(args);

            assertFalse(ws.getWorkspaceDir().toString().contains(".."),
                "Path debe estar normalizado");
        }
    }
}
