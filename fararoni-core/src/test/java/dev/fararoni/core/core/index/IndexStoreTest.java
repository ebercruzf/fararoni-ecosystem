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
package dev.fararoni.core.core.index;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("IndexStore Tests")
class IndexStoreTest {
    private IndexStore store;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        store = IndexStore.inMemory();
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {
        @Test
        @DisplayName("In-memory store should be available")
        void inMemoryStoreShouldBeAvailable() {
            assertThat(store.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("File-based store should be available")
        void fileBasedStoreShouldBeAvailable() {
            Path dbPath = tempDir.resolve("test-index");
            try (IndexStore fileStore = new IndexStore(dbPath)) {
                assertThat(fileStore.isAvailable()).isTrue();
                assertThat(fileStore.getDbPath()).isEqualTo(dbPath);
            }
        }

        @Test
        @DisplayName("Stats should be empty initially")
        void statsShouldBeEmptyInitially() {
            IndexStore.IndexStats stats = store.getStats();
            assertThat(stats.totalFiles()).isZero();
            assertThat(stats.successFiles()).isZero();
            assertThat(stats.failedFiles()).isZero();
        }
    }

    @Nested
    @DisplayName("CRUD Operations")
    class CrudOperations {
        @Test
        @DisplayName("Should store and retrieve manifest")
        void shouldStoreAndRetrieveManifest() throws IOException {
            Path testFile = createTempFile("Test.java", "public class Test {}");
            String hash = ContentHasher.hashFile(testFile);
            FileManifest manifest = FileManifest.success(testFile, System.currentTimeMillis(), hash, "java");

            store.updateManifest(manifest);
            Optional<FileManifest> retrieved = store.getManifest(testFile);

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().path()).isEqualTo(testFile.toAbsolutePath());
            assertThat(retrieved.get().contentHash()).isEqualTo(hash);
            assertThat(retrieved.get().parseStatus()).isEqualTo(FileManifest.ParseStatus.SUCCESS);
            assertThat(retrieved.get().language()).isEqualTo("java");
        }

        @Test
        @DisplayName("Should return empty for non-existent file")
        void shouldReturnEmptyForNonExistentFile() {
            Path nonExistent = tempDir.resolve("nonexistent.java");
            Optional<FileManifest> result = store.getManifest(nonExistent);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should delete manifest")
        void shouldDeleteManifest() throws IOException {
            Path testFile = createTempFile("Delete.java", "class Delete {}");
            String hash = ContentHasher.hashFile(testFile);
            FileManifest manifest = FileManifest.success(testFile, System.currentTimeMillis(), hash, "java");
            store.updateManifest(manifest);

            boolean deleted = store.delete(testFile);

            assertThat(deleted).isTrue();
            assertThat(store.getManifest(testFile)).isEmpty();
        }

        @Test
        @DisplayName("Should update existing manifest")
        void shouldUpdateExistingManifest() throws IOException {
            Path testFile = createTempFile("Update.java", "class Update {}");
            String hash1 = ContentHasher.hash("content1");
            String hash2 = ContentHasher.hash("content2");

            FileManifest manifest1 = FileManifest.success(testFile, System.currentTimeMillis(), hash1, "java");
            store.updateManifest(manifest1);

            FileManifest manifest2 = FileManifest.success(testFile, System.currentTimeMillis(), hash2, "java");
            store.updateManifest(manifest2);

            Optional<FileManifest> retrieved = store.getManifest(testFile);
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().contentHash()).isEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("Hash Differential Tests")
    class HashDifferentialTests {
        @Test
        @DisplayName("needsReindexing should return true for new file")
        void needsReindexingShouldReturnTrueForNewFile() throws IOException {
            Path testFile = createTempFile("New.java", "class New {}");
            String hash = ContentHasher.hashFile(testFile);

            boolean result = store.needsReindexing(testFile, hash);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("needsReindexing should return false for unchanged file")
        void needsReindexingShouldReturnFalseForUnchangedFile() throws IOException {
            Path testFile = createTempFile("Unchanged.java", "class Unchanged {}");
            String hash = ContentHasher.hashFile(testFile);
            FileManifest manifest = FileManifest.success(testFile, System.currentTimeMillis(), hash, "java");
            store.updateManifest(manifest);

            boolean result = store.needsReindexing(testFile, hash);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("needsReindexing should return true for changed file")
        void needsReindexingShouldReturnTrueForChangedFile() throws IOException {
            Path testFile = createTempFile("Changed.java", "class Changed {}");
            String oldHash = ContentHasher.hash("old content");
            FileManifest manifest = FileManifest.success(testFile, System.currentTimeMillis(), oldHash, "java");
            store.updateManifest(manifest);

            String newHash = ContentHasher.hashFile(testFile);
            boolean result = store.needsReindexing(testFile, newHash);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("needsReindexing should return true when store unavailable")
        void needsReindexingShouldReturnTrueWhenStoreUnavailable() {
            store.close();
            Path testFile = tempDir.resolve("any.java");
            boolean result = store.needsReindexing(testFile, "somehash");
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Status Marking Tests")
    class StatusMarkingTests {
        @Test
        @DisplayName("Should mark file as unparseable")
        void shouldMarkFileAsUnparseable() throws IOException {
            Path testFile = createTempFile("Unparseable.java", "invalid { syntax");
            String hash = ContentHasher.hashFile(testFile);
            FileManifest manifest = FileManifest.success(testFile, System.currentTimeMillis(), hash, "java");
            store.updateManifest(manifest);

            store.markAsUnparseable(testFile, "Syntax error");

            Optional<FileManifest> retrieved = store.getManifest(testFile);
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().parseStatus()).isEqualTo(FileManifest.ParseStatus.UNPARSEABLE);
        }

        @Test
        @DisplayName("Should mark file as failed")
        void shouldMarkFileAsFailed() throws IOException {
            Path testFile = createTempFile("Failed.java", "class Failed {}");
            String hash = ContentHasher.hashFile(testFile);
            FileManifest manifest = FileManifest.success(testFile, System.currentTimeMillis(), hash, "java");
            store.updateManifest(manifest);

            store.markAsFailed(testFile, "Timeout");

            Optional<FileManifest> retrieved = store.getManifest(testFile);
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().parseStatus()).isEqualTo(FileManifest.ParseStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {
        @Test
        @DisplayName("Should count by status correctly")
        void shouldCountByStatusCorrectly() throws IOException {
            Path success1 = createTempFile("Success1.java", "class S1 {}");
            Path success2 = createTempFile("Success2.java", "class S2 {}");
            Path failed = createTempFile("Failed.java", "class F {}");

            store.updateManifest(FileManifest.success(success1, System.currentTimeMillis(),
                    ContentHasher.hashFile(success1), "java"));
            store.updateManifest(FileManifest.success(success2, System.currentTimeMillis(),
                    ContentHasher.hashFile(success2), "java"));
            store.updateManifest(FileManifest.failed(failed, System.currentTimeMillis(),
                    ContentHasher.hashFile(failed), "java"));

            IndexStore.IndexStats stats = store.getStats();

            assertThat(stats.totalFiles()).isEqualTo(3);
            assertThat(stats.successFiles()).isEqualTo(2);
            assertThat(stats.failedFiles()).isEqualTo(1);
            assertThat(stats.successRate()).isCloseTo(66.67, org.assertj.core.data.Offset.offset(0.1));
        }

        @Test
        @DisplayName("Stats report should be formatted correctly")
        void statsReportShouldBeFormattedCorrectly() throws IOException {
            Path testFile = createTempFile("Test.java", "class Test {}");
            store.updateManifest(FileManifest.success(testFile, System.currentTimeMillis(),
                    ContentHasher.hashFile(testFile), "java"));

            String report = store.getStats().toReport();

            assertThat(report).contains("Total: 1");
            assertThat(report).contains("Success: 1");
        }
    }

    @Nested
    @DisplayName("Pruning Tests")
    class PruningTests {
        @Test
        @DisplayName("Should prune deleted files")
        void shouldPruneDeletedFiles() throws IOException {
            Path testFile = createTempFile("ToDelete.java", "class ToDelete {}");
            store.updateManifest(FileManifest.success(testFile, System.currentTimeMillis(),
                    ContentHasher.hashFile(testFile), "java"));

            assertThat(store.getManifest(testFile)).isPresent();

            Files.delete(testFile);

            int pruned = store.pruneDeleted();

            assertThat(pruned).isEqualTo(1);
            assertThat(store.getManifest(testFile)).isEmpty();
        }

        @Test
        @DisplayName("Should not prune existing files")
        void shouldNotPruneExistingFiles() throws IOException {
            Path testFile = createTempFile("Existing.java", "class Existing {}");
            store.updateManifest(FileManifest.success(testFile, System.currentTimeMillis(),
                    ContentHasher.hashFile(testFile), "java"));

            int pruned = store.pruneDeleted();

            assertThat(pruned).isZero();
            assertThat(store.getManifest(testFile)).isPresent();
        }
    }

    @Nested
    @DisplayName("ProjectKnowledgeBase Interface Tests")
    class ProjectKnowledgeBaseTests {
        @Test
        @DisplayName("IndexStore should implement ProjectKnowledgeBase")
        void indexStoreShouldImplementInterface() {
            assertThat(store).isInstanceOf(ProjectKnowledgeBase.class);
        }

        @Test
        @DisplayName("refresh() should not throw when available")
        void refreshShouldNotThrowWhenAvailable() {
            assertThat(store.isAvailable()).isTrue();

            store.refresh();
        }

        @Test
        @DisplayName("refresh() should handle unavailable store gracefully")
        void refreshShouldHandleUnavailableStore() {
            store.close();
            assertThat(store.isAvailable()).isFalse();

            store.refresh();
        }

        @Test
        @DisplayName("generateTreeView() should return tree structure")
        void generateTreeViewShouldReturnTreeStructure() throws IOException {
            Path javaFile = createTempFile("src/main/java/User.java", "public class User {}");
            Path xmlFile = createTempFile("pom.xml", "<project/>");

            store.updateManifest(FileManifest.success(javaFile, System.currentTimeMillis(),
                    ContentHasher.hashFile(javaFile), "java"));
            store.updateManifest(FileManifest.success(xmlFile, System.currentTimeMillis(),
                    ContentHasher.hashFile(xmlFile), "xml"));

            String treeView = store.generateTreeView(tempDir.toString());

            assertThat(treeView).isNotNull();
            assertThat(treeView).isNotBlank();
            assertThat(treeView).contains("📁");
        }

        @Test
        @DisplayName("generateTreeView() should return error message when unavailable")
        void generateTreeViewShouldReturnErrorWhenUnavailable() {
            store.close();

            String result = store.generateTreeView(".");

            assertThat(result).contains("not available");
        }

        @Test
        @DisplayName("generateTreeView() with default path should work")
        void generateTreeViewDefaultPathShouldWork() throws IOException {
            Path testFile = createTempFile("Test.java", "class Test {}");
            store.updateManifest(FileManifest.success(testFile, System.currentTimeMillis(),
                    ContentHasher.hashFile(testFile), "java"));

            String treeView = store.generateTreeView();

            assertThat(treeView).isNotNull();
        }

        @Test
        @DisplayName("generateHighLevelMap() should return project structure")
        void generateHighLevelMapShouldReturnProjectStructure() throws IOException {
            Path javaFile = createTempFile("src/User.java", "class User {}");
            store.updateManifest(FileManifest.success(javaFile, System.currentTimeMillis(),
                    ContentHasher.hashFile(javaFile), "java"));

            String map = store.generateHighLevelMap();

            assertThat(map).isNotNull();
        }

        @Test
        @DisplayName("registerFile() should add file to index")
        void registerFileShouldAddFileToIndex() throws IOException {
            Path newFile = createTempFile("NewFile.java", "class NewFile {}");

            store.registerFile(newFile);

            Optional<FileManifest> manifest = store.getManifest(newFile);
            assertThat(manifest).isPresent();
        }

        @Test
        @DisplayName("registerFile() should handle null path gracefully")
        void registerFileShouldHandleNullPath() {
            store.registerFile(null);
        }

        @Test
        @DisplayName("registerFile() should update existing file")
        void registerFileShouldUpdateExistingFile() throws IOException {
            Path file = createTempFile("Existing.java", "class Existing {}");
            store.registerFile(file);
            String originalHash = store.getManifest(file).get().contentHash();

            Files.writeString(file, "class Existing { int x; }");

            store.registerFile(file);

            String newHash = store.getManifest(file).get().contentHash();
            assertThat(newHash).isNotEqualTo(originalHash);
        }

        @Test
        @DisplayName("isUnavailable() should return opposite of isAvailable()")
        void isUnavailableShouldReturnOpposite() {
            assertThat(store.isAvailable()).isTrue();
            assertThat(store.isUnavailable()).isFalse();

            store.close();
            assertThat(store.isAvailable()).isFalse();
            assertThat(store.isUnavailable()).isTrue();
        }
    }

    private Path createTempFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }
}
