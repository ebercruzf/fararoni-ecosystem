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
package dev.fararoni.core.core.session;

import dev.fararoni.core.core.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
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
@DisplayName("DataPurgeService Tests")
class DataPurgeServiceTest {
    @TempDir
    Path tempDir;

    private DataPurgeService service;

    @BeforeEach
    void setUp() {
        DataPurgeService.resetForTesting();
        WorkspaceManager.reset();
        service = DataPurgeService.getInstance();
        service.resetStats();
    }

    @AfterEach
    void tearDown() {
        DataPurgeService.resetForTesting();
        WorkspaceManager.reset();
    }

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {
        @Test
        @DisplayName("getInstance debe retornar la misma instancia")
        void getInstance_ShouldReturnSameInstance() {
            DataPurgeService s1 = DataPurgeService.getInstance();
            DataPurgeService s2 = DataPurgeService.getInstance();

            assertSame(s1, s2);
        }
    }

    @Nested
    @DisplayName("Standard Delete")
    class StandardDeleteTests {
        @Test
        @DisplayName("debe borrar archivo existente")
        void shouldDeleteExistingFile() throws IOException {
            Path file = tempDir.resolve("test-file.txt");
            Files.writeString(file, "Test content");
            assertTrue(Files.exists(file));

            var result = service.standardDelete(file);

            assertTrue(result.success());
            assertEquals(1, result.filesDeleted());
            assertTrue(result.bytesDeleted() > 0);
            assertFalse(Files.exists(file));
        }

        @Test
        @DisplayName("debe manejar archivo inexistente graciosamente")
        void shouldHandleNonExistentFile() {
            Path file = tempDir.resolve("non-existent.txt");

            var result = service.standardDelete(file);

            assertTrue(result.success());
            assertEquals(0, result.filesDeleted());
        }

        @Test
        @DisplayName("debe manejar path null")
        void shouldHandleNullPath() {
            var result = service.standardDelete(null);

            assertFalse(result.success());
            assertNotNull(result.errorMessage());
        }
    }

    @Nested
    @DisplayName("Secure Delete")
    class SecureDeleteTests {
        @Test
        @DisplayName("debe borrar archivo de forma segura")
        void shouldSecureDeleteFile() throws IOException {
            Path file = tempDir.resolve("secure-delete-test.txt");
            String content = "Sensitive data that should be securely deleted!";
            Files.writeString(file, content);
            long originalSize = Files.size(file);

            var result = service.secureDelete(file);

            assertTrue(result.success());
            assertEquals(1, result.filesDeleted());
            assertEquals(originalSize, result.bytesDeleted());
            assertFalse(Files.exists(file));
        }

        @Test
        @DisplayName("debe incrementar contador de borrados seguros")
        void shouldIncrementSecureDeleteCounter() throws IOException {
            Path file = tempDir.resolve("secure-count-test.txt");
            Files.writeString(file, "Test data");

            long beforeCount = service.getSecureDeletesPerformed();
            service.secureDelete(file);
            long afterCount = service.getSecureDeletesPerformed();

            assertEquals(beforeCount + 1, afterCount);
        }

        @Test
        @DisplayName("debe fallar para directorios")
        void shouldFailForDirectories() throws IOException {
            Path dir = tempDir.resolve("subdir");
            Files.createDirectory(dir);

            var result = service.secureDelete(dir);

            assertFalse(result.success());
            assertTrue(result.errorMessage().contains("directory"));
        }

        @Test
        @DisplayName("debe manejar archivo inexistente")
        void shouldHandleNonExistentFile() {
            Path file = tempDir.resolve("non-existent.txt");

            var result = service.secureDelete(file);

            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Directory Purge")
    class DirectoryPurgeTests {
        @Test
        @DisplayName("debe borrar directorio con archivos")
        void shouldPurgeDirectoryWithFiles() throws IOException {
            Path dir = tempDir.resolve("purge-test");
            Files.createDirectory(dir);
            Files.writeString(dir.resolve("file1.txt"), "Content 1");
            Files.writeString(dir.resolve("file2.txt"), "Content 2");
            Files.writeString(dir.resolve("file3.txt"), "Content 3");

            var result = service.purgeDirectory(dir, false);

            assertTrue(result.success());
            assertEquals(3, result.filesDeleted());
            assertFalse(Files.exists(dir));
        }

        @Test
        @DisplayName("debe borrar directorio anidado")
        void shouldPurgeNestedDirectory() throws IOException {
            Path dir = tempDir.resolve("nested-purge");
            Path subDir = dir.resolve("subdir");
            Path subSubDir = subDir.resolve("subsubdir");
            Files.createDirectories(subSubDir);
            Files.writeString(dir.resolve("root.txt"), "Root");
            Files.writeString(subDir.resolve("sub.txt"), "Sub");
            Files.writeString(subSubDir.resolve("deep.txt"), "Deep");

            var result = service.purgeDirectory(dir, false);

            assertTrue(result.success());
            assertEquals(3, result.filesDeleted());
            assertFalse(Files.exists(dir));
        }

        @Test
        @DisplayName("borrado seguro de directorio debe usar 3-pass")
        void securePurge_ShouldUse3Pass() throws IOException {
            Path dir = tempDir.resolve("secure-purge");
            Files.createDirectory(dir);
            Files.writeString(dir.resolve("sensitive.txt"), "Sensitive data");

            long beforeSecure = service.getSecureDeletesPerformed();
            service.purgeDirectory(dir, true);
            long afterSecure = service.getSecureDeletesPerformed();

            assertTrue(afterSecure > beforeSecure);
        }

        @Test
        @DisplayName("debe manejar directorio vacío")
        void shouldHandleEmptyDirectory() throws IOException {
            Path dir = tempDir.resolve("empty-dir");
            Files.createDirectory(dir);

            var result = service.purgeDirectory(dir, false);

            assertTrue(result.success());
            assertEquals(0, result.filesDeleted());
            assertFalse(Files.exists(dir));
        }

        @Test
        @DisplayName("debe manejar directorio inexistente")
        void shouldHandleNonExistentDirectory() {
            Path dir = tempDir.resolve("non-existent-dir");

            var result = service.purgeDirectory(dir, false);

            assertTrue(result.success());
            assertEquals(0, result.filesDeleted());
        }
    }

    @Nested
    @DisplayName("Selective Purge")
    class SelectivePurgeTests {
        @BeforeEach
        void setUpWorkspace() throws IOException {
            WorkspaceManager.reset();
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});

            Files.writeString(tempDir.resolve("memory.db"), "Memory DB content");
            Files.writeString(tempDir.resolve("interactions.db"), "Interactions content");
            Files.writeString(tempDir.resolve("cache.db"), "Cache content");
            Files.writeString(tempDir.resolve("audit.log"), "Audit log content");
            Files.writeString(tempDir.resolve("config.properties"), "Config content");
        }

        @Test
        @DisplayName("purgeSelective con historyOnly debe solo borrar interactions")
        void purgeSelective_HistoryOnly_ShouldOnlyDeleteInteractions() {
            var config = DataPurgeService.PurgeConfig.historyOnly();
            var result = service.purgeSelective(config);

            assertTrue(result.success());
            assertFalse(Files.exists(tempDir.resolve("interactions.db")));
            assertTrue(Files.exists(tempDir.resolve("memory.db")));
            assertTrue(Files.exists(tempDir.resolve("cache.db")));
        }

        @Test
        @DisplayName("purgeSelective con dataOnly debe mantener config")
        void purgeSelective_DataOnly_ShouldKeepConfig() {
            var config = DataPurgeService.PurgeConfig.dataOnly();
            var result = service.purgeSelective(config);

            assertTrue(result.success());
            assertTrue(Files.exists(tempDir.resolve("config.properties")));
        }

        @Test
        @DisplayName("purgeSelective con all debe borrar todo")
        void purgeSelective_All_ShouldDeleteEverything() {
            var config = DataPurgeService.PurgeConfig.all();
            var result = service.purgeSelective(config);

            assertTrue(result.success());
            assertFalse(Files.exists(tempDir.resolve("interactions.db")));
            assertFalse(Files.exists(tempDir.resolve("memory.db")));
            assertFalse(Files.exists(tempDir.resolve("cache.db")));
            assertFalse(Files.exists(tempDir.resolve("audit.log")));
            assertFalse(Files.exists(tempDir.resolve("config.properties")));
        }

        @Test
        @DisplayName("purgeSelective con builder debe permitir configuración custom")
        void purgeSelective_Builder_ShouldAllowCustomConfig() {
            var config = DataPurgeService.PurgeConfig.builder()
                    .purgeHistory()
                    .purgeCache()
                    .build();

            var result = service.purgeSelective(config);

            assertTrue(result.success());
            assertFalse(Files.exists(tempDir.resolve("interactions.db")));
            assertFalse(Files.exists(tempDir.resolve("cache.db")));
            assertTrue(Files.exists(tempDir.resolve("memory.db")));
            assertTrue(Files.exists(tempDir.resolve("config.properties")));
        }
    }

    @Nested
    @DisplayName("PurgeResult")
    class PurgeResultTests {
        @Test
        @DisplayName("success debe crear resultado exitoso")
        void success_ShouldCreateSuccessResult() {
            var result = DataPurgeService.PurgeResult.success(5, 1024);

            assertTrue(result.success());
            assertEquals(5, result.filesDeleted());
            assertEquals(1024, result.bytesDeleted());
            assertNull(result.errorMessage());
        }

        @Test
        @DisplayName("failure debe crear resultado de fallo")
        void failure_ShouldCreateFailureResult() {
            var result = DataPurgeService.PurgeResult.failure("Test error");

            assertFalse(result.success());
            assertEquals("Test error", result.errorMessage());
            assertEquals(0, result.filesDeleted());
        }

        @Test
        @DisplayName("getSummary debe formatear correctamente")
        void getSummary_ShouldFormatCorrectly() {
            var successResult = DataPurgeService.PurgeResult.success(10, 10 * 1024 * 1024);
            var failResult = DataPurgeService.PurgeResult.failure("Error message");

            String successSummary = successResult.getSummary();
            String failSummary = failResult.getSummary();

            assertTrue(successSummary.contains("10"));
            assertTrue(successSummary.contains("MB"));
            assertTrue(failSummary.contains("Error message"));
        }
    }

    @Nested
    @DisplayName("PurgeConfig")
    class PurgeConfigTests {
        @Test
        @DisplayName("all debe habilitar todas las opciones")
        void all_ShouldEnableAllOptions() {
            var config = DataPurgeService.PurgeConfig.all();

            assertTrue(config.purgeHistory());
            assertTrue(config.purgeMemory());
            assertTrue(config.purgeCache());
            assertTrue(config.purgeAuditLog());
            assertTrue(config.purgeConfig());
        }

        @Test
        @DisplayName("historyOnly debe solo habilitar history")
        void historyOnly_ShouldOnlyEnableHistory() {
            var config = DataPurgeService.PurgeConfig.historyOnly();

            assertTrue(config.purgeHistory());
            assertFalse(config.purgeMemory());
            assertFalse(config.purgeCache());
            assertFalse(config.purgeAuditLog());
            assertFalse(config.purgeConfig());
        }

        @Test
        @DisplayName("dataOnly debe excluir config")
        void dataOnly_ShouldExcludeConfig() {
            var config = DataPurgeService.PurgeConfig.dataOnly();

            assertTrue(config.purgeHistory());
            assertTrue(config.purgeMemory());
            assertTrue(config.purgeCache());
            assertTrue(config.purgeAuditLog());
            assertFalse(config.purgeConfig());
        }

        @Test
        @DisplayName("builder debe permitir configuración granular")
        void builder_ShouldAllowGranularConfig() {
            var config = DataPurgeService.PurgeConfig.builder()
                    .purgeHistory()
                    .purgeAuditLog()
                    .build();

            assertTrue(config.purgeHistory());
            assertFalse(config.purgeMemory());
            assertFalse(config.purgeCache());
            assertTrue(config.purgeAuditLog());
            assertFalse(config.purgeConfig());
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {
        @Test
        @DisplayName("debe acumular estadísticas correctamente")
        void shouldAccumulateStatsCorrectly() throws IOException {
            Path file1 = tempDir.resolve("stat-test-1.txt");
            Path file2 = tempDir.resolve("stat-test-2.txt");
            Files.writeString(file1, "Content 1");
            Files.writeString(file2, "Content 2");

            service.resetStats();
            service.standardDelete(file1);
            service.standardDelete(file2);

            assertEquals(2, service.getFilesDeleted());
            assertTrue(service.getBytesDeleted() > 0);
        }

        @Test
        @DisplayName("resetStats debe limpiar estadísticas")
        void resetStats_ShouldClearStats() throws IOException {
            Path file = tempDir.resolve("reset-test.txt");
            Files.writeString(file, "Test");
            service.standardDelete(file);

            assertTrue(service.getFilesDeleted() > 0);

            service.resetStats();

            assertEquals(0, service.getFilesDeleted());
            assertEquals(0, service.getBytesDeleted());
            assertEquals(0, service.getSecureDeletesPerformed());
        }

        @Test
        @DisplayName("getStatsSummary debe ser informativo")
        void getStatsSummary_ShouldBeInformative() throws IOException {
            Path file = tempDir.resolve("summary-test.txt");
            Files.writeString(file, "Test content for summary");
            service.secureDelete(file);

            String summary = service.getStatsSummary();

            assertNotNull(summary);
            assertTrue(summary.contains("files"));
            assertTrue(summary.contains("bytes") || summary.contains("MB"));
        }
    }
}
