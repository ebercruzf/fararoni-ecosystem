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

import dev.fararoni.core.core.indexing.GitignoreFilter;
import dev.fararoni.core.core.services.CodeAnalysisService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SafeCrawler Tests")
class SafeCrawlerTest {
    private SafeCrawler crawler;
    private IndexStore indexStore;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        indexStore = IndexStore.inMemory();
        crawler = new SafeCrawler(
            indexStore,
            new CodeAnalysisService(),
            GitignoreFilter.withDefaults(),
            CrawlConfig.DEFAULT
        );
    }

    @AfterEach
    void tearDown() {
        if (crawler != null) {
            crawler.close();
        }
    }

    @Nested
    @DisplayName("Basic Crawling Tests")
    class BasicCrawlingTests {
        @Test
        @DisplayName("Should crawl empty directory")
        void shouldCrawlEmptyDirectory() {
            CrawlResult result = crawler.crawl(tempDir);

            assertThat(result).isNotNull();
            assertThat(result.totalFiles()).isZero();
            assertThat(result.hasErrors()).isFalse();
        }

        @Test
        @DisplayName("Should crawl directory with Java files")
        void shouldCrawlDirectoryWithJavaFiles() throws IOException {
            createFile("Test.java", "public class Test { public void test() {} }");
            createFile("Another.java", "public class Another { }");

            CrawlResult result = crawler.crawl(tempDir);

            assertThat(result.filesProcessed()).isEqualTo(2);
            assertThat(result.filesSkipped()).isZero();
            assertThat(result.filesFailed()).isZero();
        }

        @Test
        @DisplayName("Should crawl nested directories")
        void shouldCrawlNestedDirectories() throws IOException {
            Path subdir = tempDir.resolve("src/main/java");
            Files.createDirectories(subdir);
            Files.writeString(subdir.resolve("Main.java"), "public class Main {}");

            CrawlResult result = crawler.crawl(tempDir);

            assertThat(result.filesProcessed()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return empty result for non-existent path")
        void shouldReturnEmptyResultForNonExistentPath() {
            CrawlResult result = crawler.crawl(tempDir.resolve("nonexistent"));
            assertThat(result).isEqualTo(CrawlResult.EMPTY);
        }

        @Test
        @DisplayName("Should return empty result for null path")
        void shouldReturnEmptyResultForNullPath() {
            CrawlResult result = crawler.crawl(null);
            assertThat(result).isEqualTo(CrawlResult.EMPTY);
        }
    }

    @Nested
    @DisplayName("Incremental Indexing Tests")
    class IncrementalIndexingTests {
        @Test
        @DisplayName("Should skip already indexed files")
        void shouldSkipAlreadyIndexedFiles() throws IOException {
            createFile("Indexed.java", "public class Indexed {}");

            CrawlResult result1 = crawler.crawl(tempDir);
            assertThat(result1.filesProcessed()).isEqualTo(1);

            CrawlResult result2 = crawler.crawl(tempDir);

            assertThat(result2.filesSkipped()).isEqualTo(1);
            assertThat(result2.filesProcessed()).isZero();
        }

        @Test
        @DisplayName("Should reindex modified files")
        void shouldReindexModifiedFiles() throws IOException, InterruptedException {
            Path testFile = createFile("Modified.java", "public class Modified {}");

            CrawlResult result1 = crawler.crawl(tempDir);
            assertThat(result1.filesProcessed()).isEqualTo(1);

            Thread.sleep(100);
            Files.writeString(testFile, "public class Modified { void newMethod() {} }");

            CrawlResult result2 = crawler.crawl(tempDir);

            assertThat(result2.filesProcessed()).isEqualTo(1);
            assertThat(result2.filesSkipped()).isZero();
        }
    }

    @Nested
    @DisplayName("File Filtering Tests")
    class FileFilteringTests {
        @Test
        @DisplayName("Should skip unsupported extensions")
        void shouldSkipUnsupportedExtensions() throws IOException {
            createFile("data.bin", "binary data");
            createFile("image.png", "fake image");
            createFile("Test.java", "class Test {}");

            CrawlResult result = crawler.crawl(tempDir);

            assertThat(result.filesProcessed()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should process supported extensions")
        void shouldProcessSupportedExtensions() throws IOException {
            createFile("test.py", "def hello(): pass");
            createFile("app.js", "function hello() {}");
            createFile("Main.java", "class Main {}");

            CrawlResult result = crawler.crawl(tempDir);

            assertThat(result.filesProcessed()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("Should skip files larger than maxSize")
        void shouldSkipFilesLargerThanMaxSize() throws IOException {
            CrawlConfig smallConfig = CrawlConfig.builder()
                .maxFileSize(100)
                .build();

            SafeCrawler smallCrawler = new SafeCrawler(
                IndexStore.inMemory(),
                new CodeAnalysisService(),
                GitignoreFilter.withDefaults(),
                smallConfig
            );

            try {
                createFile("Large.java", "x".repeat(200));

                CrawlResult result = smallCrawler.crawl(tempDir);

                assertThat(result.filesSkipped()).isEqualTo(1);
                assertThat(result.filesProcessed()).isZero();
            } finally {
                smallCrawler.close();
            }
        }
    }

    @Nested
    @DisplayName("Gitignore Integration Tests")
    class GitignoreIntegrationTests {
        @Test
        @DisplayName("Should skip node_modules directory")
        void shouldSkipNodeModulesDirectory() throws IOException {
            Path nodeModules = tempDir.resolve("node_modules");
            Files.createDirectories(nodeModules);
            Files.writeString(nodeModules.resolve("package.js"), "module.exports = {}");
            createFile("app.js", "console.log('app')");

            CrawlResult result = crawler.crawl(tempDir);

            assertThat(result.filesProcessed()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should skip target directory")
        void shouldSkipTargetDirectory() throws IOException {
            Path target = tempDir.resolve("target/classes");
            Files.createDirectories(target);
            Files.writeString(target.resolve("Main.class"), "bytecode");
            createFile("Main.java", "class Main {}");

            CrawlResult result = crawler.crawl(tempDir);

            assertThat(result.filesProcessed()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Timeout Handling Tests")
    class TimeoutHandlingTests {
        @Test
        @DisplayName("Should complete within reasonable time")
        void shouldCompleteWithinReasonableTime() throws IOException {
            for (int i = 0; i < 10; i++) {
                createFile("Test" + i + ".java", "public class Test" + i + " {}");
            }

            long start = System.currentTimeMillis();
            CrawlResult result = crawler.crawl(tempDir);
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isLessThan(5000);
            assertThat(result.filesProcessed()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Process Single File Tests")
    class ProcessSingleFileTests {
        @Test
        @DisplayName("Should process single file")
        void shouldProcessSingleFile() throws IOException {
            Path testFile = createFile("Single.java", "public class Single {}");

            boolean result = crawler.processFile(testFile);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false for non-existent file")
        void shouldReturnFalseForNonExistentFile() {
            Path nonExistent = tempDir.resolve("nonexistent.java");

            boolean result = crawler.processFile(nonExistent);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false for directory")
        void shouldReturnFalseForDirectory() {
            boolean result = crawler.processFile(tempDir);
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {
        @Test
        @DisplayName("Should track stats correctly")
        void shouldTrackStatsCorrectly() throws IOException {
            createFile("Test1.java", "class Test1 {}");
            createFile("Test2.java", "class Test2 {}");

            crawler.crawl(tempDir);

            IndexStore.IndexStats stats = crawler.getStats();
            assertThat(stats.totalFiles()).isEqualTo(2);
            assertThat(stats.successFiles()).isEqualTo(2);
        }

        @Test
        @DisplayName("CrawlResult report should be formatted")
        void crawlResultReportShouldBeFormatted() throws IOException {
            createFile("Test.java", "class Test {}");

            CrawlResult result = crawler.crawl(tempDir);
            String report = result.toReport();

            assertThat(report).contains("CRAWL COMPLETE");
            assertThat(report).contains("Processed:");
            assertThat(report).contains("Duration:");
        }
    }

    @Nested
    @DisplayName("Config Tests")
    class ConfigTests {
        @Test
        @DisplayName("Should respect maxDepth config")
        void shouldRespectMaxDepthConfig() throws IOException {
            CrawlConfig shallowConfig = CrawlConfig.builder()
                .maxDepth(1)
                .build();

            SafeCrawler shallowCrawler = new SafeCrawler(
                IndexStore.inMemory(),
                new CodeAnalysisService(),
                GitignoreFilter.withDefaults(),
                shallowConfig
            );

            try {
                createFile("root.java", "class Root {}");
                Path deep = tempDir.resolve("level1/level2");
                Files.createDirectories(deep);
                Files.writeString(deep.resolve("Deep.java"), "class Deep {}");

                CrawlResult result = shallowCrawler.crawl(tempDir);

                assertThat(result.filesProcessed()).isEqualTo(1);
            } finally {
                shallowCrawler.close();
            }
        }

        @Test
        @DisplayName("Default config should have sensible defaults")
        void defaultConfigShouldHaveSensibleDefaults() {
            CrawlConfig config = CrawlConfig.DEFAULT;

            assertThat(config.timeout()).isEqualTo(Duration.ofMillis(200));
            assertThat(config.maxFileSize()).isEqualTo(1024 * 1024);
            assertThat(config.maxDepth()).isEqualTo(Integer.MAX_VALUE);
            assertThat(config.includedExtensions()).contains("java", "py", "js", "ts");
        }
    }

    private Path createFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }
}
