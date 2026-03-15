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
@DisplayName("DataMigration Tests")
class DataMigrationTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        WorkspaceManager.reset();
    }

    @AfterEach
    void tearDown() {
        WorkspaceManager.reset();
    }

    @Nested
    @DisplayName("Single File Migration")
    class SingleFileMigrationTests {
        @Test
        @DisplayName("debe migrar archivo existente a destino vacio")
        void shouldMigrateExistingFileToEmptyTarget() throws IOException {
            Path source = tempDir.resolve("source.db");
            Path target = tempDir.resolve("target.db");
            Files.writeString(source, "test data");

            var result = DataMigration.migrateFile(source, target);

            assertTrue(result.isSuccess());
            assertEquals(DataMigration.MigrationStatus.SUCCESS, result.status());
            assertFalse(Files.exists(source), "Source should be moved");
            assertTrue(Files.exists(target), "Target should exist");
            assertEquals("test data", Files.readString(target));
        }

        @Test
        @DisplayName("debe omitir si origen no existe")
        void shouldSkipIfSourceDoesNotExist() {
            Path source = tempDir.resolve("non-existent.db");
            Path target = tempDir.resolve("target.db");

            var result = DataMigration.migrateFile(source, target);

            assertEquals(DataMigration.MigrationStatus.SKIPPED, result.status());
            assertTrue(result.message().contains("Source does not exist"));
        }

        @Test
        @DisplayName("debe omitir si destino ya existe")
        void shouldSkipIfTargetAlreadyExists() throws IOException {
            Path source = tempDir.resolve("source.db");
            Path target = tempDir.resolve("target.db");
            Files.writeString(source, "source data");
            Files.writeString(target, "existing target data");

            var result = DataMigration.migrateFile(source, target);

            assertEquals(DataMigration.MigrationStatus.SKIPPED, result.status());
            assertTrue(result.message().contains("Target already exists"));
            assertTrue(Files.exists(source), "Source should remain");
            assertEquals("existing target data", Files.readString(target), "Target should not be overwritten");
        }

        @Test
        @DisplayName("debe manejar paths null")
        void shouldHandleNullPaths() {
            var result1 = DataMigration.migrateFile(null, tempDir.resolve("target.db"));
            var result2 = DataMigration.migrateFile(tempDir.resolve("source.db"), null);

            assertEquals(DataMigration.MigrationStatus.FAILED, result1.status());
            assertEquals(DataMigration.MigrationStatus.FAILED, result2.status());
        }

        @Test
        @DisplayName("debe migrar archivos WAL asociados")
        void shouldMigrateAssociatedWalFiles() throws IOException {
            Path source = tempDir.resolve("source.db");
            Path sourceWal = tempDir.resolve("source.db-wal");
            Path sourceShm = tempDir.resolve("source.db-shm");
            Path target = tempDir.resolve("target.db");
            Path targetWal = tempDir.resolve("target.db-wal");
            Path targetShm = tempDir.resolve("target.db-shm");

            Files.writeString(source, "main db");
            Files.writeString(sourceWal, "wal data");
            Files.writeString(sourceShm, "shm data");

            var result = DataMigration.migrateFile(source, target);

            assertTrue(result.isSuccess());
            assertFalse(Files.exists(source));
            assertFalse(Files.exists(sourceWal));
            assertFalse(Files.exists(sourceShm));
            assertTrue(Files.exists(target));
            assertTrue(Files.exists(targetWal));
            assertTrue(Files.exists(targetShm));
        }
    }

    @Nested
    @DisplayName("Full Migration")
    class FullMigrationTests {
        @Test
        @DisplayName("migrateIfNeeded debe retornar resultado sin errores cuando no hay archivos")
        void migrateIfNeeded_ShouldReturnEmptyResult_WhenNoFiles() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.resolve("workspace")});

            var result = DataMigration.migrateIfNeeded();

            assertFalse(result.hasMigrations());
            assertFalse(result.hasErrors());
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("hasPendingMigrations debe retornar false cuando no hay archivos pendientes")
        void hasPendingMigrations_ShouldReturnFalse_WhenNoFiles() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.resolve("workspace")});

            boolean hasPending = DataMigration.hasPendingMigrations();

            assertFalse(hasPending);
        }
    }

    @Nested
    @DisplayName("MigrationEntry")
    class MigrationEntryTests {
        @Test
        @DisplayName("success debe crear entrada exitosa")
        void success_ShouldCreateSuccessEntry() {
            Path source = tempDir.resolve("source.db");
            Path target = tempDir.resolve("target.db");

            var entry = DataMigration.MigrationEntry.success(source, target);

            assertTrue(entry.isSuccess());
            assertEquals(DataMigration.MigrationStatus.SUCCESS, entry.status());
            assertEquals(source, entry.source());
            assertEquals(target, entry.target());
        }

        @Test
        @DisplayName("skipped debe crear entrada omitida")
        void skipped_ShouldCreateSkippedEntry() {
            Path source = tempDir.resolve("source.db");
            Path target = tempDir.resolve("target.db");

            var entry = DataMigration.MigrationEntry.skipped(source, target, "Test reason");

            assertFalse(entry.isSuccess());
            assertEquals(DataMigration.MigrationStatus.SKIPPED, entry.status());
            assertEquals("Test reason", entry.message());
        }

        @Test
        @DisplayName("failed debe crear entrada fallida")
        void failed_ShouldCreateFailedEntry() {
            Path source = tempDir.resolve("source.db");
            Path target = tempDir.resolve("target.db");

            var entry = DataMigration.MigrationEntry.failed(source, target, "Error message");

            assertFalse(entry.isSuccess());
            assertEquals(DataMigration.MigrationStatus.FAILED, entry.status());
            assertEquals("Error message", entry.message());
        }
    }

    @Nested
    @DisplayName("MigrationResult")
    class MigrationResultTests {
        @Test
        @DisplayName("debe reportar correctamente migraciones exitosas")
        void shouldReportSuccessfulMigrations() {
            var migrations = java.util.List.of(
                    DataMigration.MigrationEntry.success(
                            tempDir.resolve("a.db"),
                            tempDir.resolve("b.db")
                    )
            );

            var result = new DataMigration.MigrationResult(
                    migrations,
                    java.util.List.of(),
                    java.util.List.of()
            );

            assertTrue(result.hasMigrations());
            assertFalse(result.hasErrors());
            assertTrue(result.isSuccess());
            assertEquals(1, result.migratedCount());
        }

        @Test
        @DisplayName("debe reportar correctamente errores")
        void shouldReportErrors() {
            var failed = java.util.List.of(
                    DataMigration.MigrationEntry.failed(
                            tempDir.resolve("a.db"),
                            tempDir.resolve("b.db"),
                            "IO Error"
                    )
            );

            var result = new DataMigration.MigrationResult(
                    java.util.List.of(),
                    java.util.List.of(),
                    failed
            );

            assertFalse(result.hasMigrations());
            assertTrue(result.hasErrors());
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("getSummary debe formatear correctamente")
        void getSummary_ShouldFormatCorrectly() {
            var migrations = java.util.List.of(
                    DataMigration.MigrationEntry.success(
                            tempDir.resolve("source.db"),
                            tempDir.resolve("target.db")
                    )
            );

            var result = new DataMigration.MigrationResult(
                    migrations,
                    java.util.List.of(),
                    java.util.List.of()
            );

            String summary = result.getSummary();

            assertNotNull(summary);
            assertTrue(summary.contains("Migrated 1 file"));
            assertTrue(summary.contains("source.db"));
        }

        @Test
        @DisplayName("getShortSummary debe ser conciso")
        void getShortSummary_ShouldBeConcise() {
            var result1 = new DataMigration.MigrationResult(
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of()
            );

            var result2 = new DataMigration.MigrationResult(
                    java.util.List.of(
                            DataMigration.MigrationEntry.success(
                                    tempDir.resolve("a.db"),
                                    tempDir.resolve("b.db")
                            )
                    ),
                    java.util.List.of(),
                    java.util.List.of()
            );

            assertEquals("No migration needed", result1.getShortSummary());
            assertTrue(result2.getShortSummary().contains("Migrated 1"));
        }

        @Test
        @DisplayName("hasSkipped debe detectar archivos omitidos")
        void hasSkipped_ShouldDetectSkippedFiles() {
            var skipped = java.util.List.of(
                    DataMigration.MigrationEntry.skipped(
                            tempDir.resolve("a.db"),
                            tempDir.resolve("b.db"),
                            "Already exists"
                    )
            );

            var result = new DataMigration.MigrationResult(
                    java.util.List.of(),
                    skipped,
                    java.util.List.of()
            );

            assertTrue(result.hasSkipped());
            assertFalse(result.hasMigrations());
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("MigrationStatus Enum")
    class MigrationStatusTests {
        @Test
        @DisplayName("debe tener todos los estados esperados")
        void shouldHaveAllExpectedStatuses() {
            assertEquals(3, DataMigration.MigrationStatus.values().length);
            assertNotNull(DataMigration.MigrationStatus.SUCCESS);
            assertNotNull(DataMigration.MigrationStatus.SKIPPED);
            assertNotNull(DataMigration.MigrationStatus.FAILED);
        }
    }
}
