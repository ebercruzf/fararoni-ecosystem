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
package dev.fararoni.core.core.context;

import dev.fararoni.core.core.search.TheHound;
import dev.fararoni.core.core.workspace.WorkspaceInsight;
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
@DisplayName("ContextManager - El Cerebro Tests")
class ContextManagerTest {
    private static final TheHound.EmbeddingProvider DUMMY_PROVIDER = text -> new float[384];

    private ContextManager contextManager;

    @BeforeEach
    void setUp() {
        contextManager = new ContextManager(DUMMY_PROVIDER);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {
        @Test
        @DisplayName("Debe crear instancia con provider valido")
        void shouldCreateWithValidProvider() {
            ContextManager ctx = new ContextManager(DUMMY_PROVIDER);

            assertNotNull(ctx);
            assertFalse(ctx.isIndexed());
            assertEquals(0, ctx.getIndexedFileCount());
        }

        @Test
        @DisplayName("Debe lanzar exception si provider es null")
        void shouldThrowIfProviderIsNull() {
            assertThrows(NullPointerException.class, () -> {
                new ContextManager((TheHound.EmbeddingProvider) null);
            });
        }

        @Test
        @DisplayName("Debe poder crear con componentes personalizados")
        void shouldCreateWithCustomComponents() {
            var insight = new WorkspaceInsight();
            var hound = new TheHound(DUMMY_PROVIDER);

            ContextManager ctx = new ContextManager(insight, hound);

            assertNotNull(ctx);
            assertSame(insight, ctx.getInsight());
            assertSame(hound, ctx.getHound());
        }
    }

    @Nested
    @DisplayName("Indexacion Tests")
    class IndexacionTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Debe indexar proyecto Python")
        void shouldIndexPythonProject() throws IOException {
            Files.writeString(tempDir.resolve("main.py"), "def hello(): pass");
            Files.writeString(tempDir.resolve("utils.py"), "def helper(): pass");

            contextManager.indexProject(tempDir);

            assertTrue(contextManager.isIndexed());
            assertEquals(2, contextManager.getIndexedFileCount());
            assertEquals(tempDir, contextManager.getIndexedProjectPath());
        }

        @Test
        @DisplayName("Debe indexar proyecto Java")
        void shouldIndexJavaProject() throws IOException {
            Files.createDirectories(tempDir.resolve("src/main/java"));
            Files.writeString(tempDir.resolve("src/main/java/Main.java"),
                "public class Main { }");
            Files.writeString(tempDir.resolve("pom.xml"),
                "<project></project>");

            contextManager.indexProject(tempDir);

            assertTrue(contextManager.isIndexed());
            assertTrue(contextManager.getIndexedFileCount() >= 2);
        }

        @Test
        @DisplayName("No debe re-indexar el mismo proyecto")
        void shouldNotReindexSameProject() throws IOException {
            Files.writeString(tempDir.resolve("main.py"), "def hello(): pass");
            contextManager.indexProject(tempDir);
            int firstCount = contextManager.getIndexedFileCount();

            Files.writeString(tempDir.resolve("extra.py"), "def extra(): pass");
            contextManager.indexProject(tempDir);

            assertEquals(firstCount, contextManager.getIndexedFileCount());
        }

        @Test
        @DisplayName("forceReindex debe re-indexar")
        void forceReindexShouldReindex() throws IOException {
            Files.writeString(tempDir.resolve("main.py"), "def hello(): pass");
            contextManager.indexProject(tempDir);

            Files.writeString(tempDir.resolve("extra.py"), "def extra(): pass");
            contextManager.forceReindex(tempDir);

            assertEquals(2, contextManager.getIndexedFileCount());
        }

        @Test
        @DisplayName("Debe ignorar archivos binarios")
        void shouldIgnoreBinaryFiles() throws IOException {
            Files.writeString(tempDir.resolve("main.py"), "def hello(): pass");
            Files.write(tempDir.resolve("image.png"), new byte[100]);
            Files.write(tempDir.resolve("data.bin"), new byte[100]);

            contextManager.indexProject(tempDir);

            assertEquals(1, contextManager.getIndexedFileCount());
        }

        @Test
        @DisplayName("Debe indexar archivos de configuracion")
        void shouldIndexConfigFiles() throws IOException {
            Files.writeString(tempDir.resolve("config.json"), "{\"key\": \"value\"}");
            Files.writeString(tempDir.resolve("settings.yaml"), "key: value");
            Files.writeString(tempDir.resolve("README.md"), "# Readme");

            contextManager.indexProject(tempDir);

            assertEquals(3, contextManager.getIndexedFileCount());
        }
    }

    @Nested
    @DisplayName("Build Context Payload Tests")
    class BuildContextPayloadTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Debe generar payload con conciencia situacional")
        void shouldGeneratePayloadWithSituationalAwareness() throws IOException {
            Files.writeString(tempDir.resolve("main.py"), "def calculate_total(): pass");
            contextManager.indexProject(tempDir);

            String payload = contextManager.buildContextPayload(tempDir, "error en calculate");

            assertTrue(payload.contains("CONCIENCIA SITUACIONAL"));
            assertTrue(payload.contains("Proyecto Detectado"));
            assertTrue(payload.contains("ESTRUCTURA DE ARCHIVOS"));
        }

        @Test
        @DisplayName("Debe incluir archivos relevantes via RAG")
        void shouldIncludeRelevantFilesViaRAG() throws IOException {
            Files.writeString(tempDir.resolve("calculator.py"),
                "def calculate_total(items):\n    return sum(i.price for i in items)");
            Files.writeString(tempDir.resolve("models.py"),
                "class Item:\n    def __init__(self, price): self.price = price");
            contextManager.indexProject(tempDir);

            String payload = contextManager.buildContextPayload(tempDir, "calculate_total");

            assertTrue(payload.contains("ARCHIVOS RELEVANTES (RAG)"));
            assertTrue(payload.contains("calculator.py") || payload.contains("models.py"));
        }

        @Test
        @DisplayName("Payload debe contener contenido de archivos")
        void payloadShouldContainFileContent() throws IOException {
            String expectedContent = "def my_unique_function(): return 42";
            Files.writeString(tempDir.resolve("unique.py"), expectedContent);
            contextManager.indexProject(tempDir);

            String payload = contextManager.buildContextPayload(tempDir, "my_unique_function");

            assertTrue(payload.contains("my_unique_function"));
            assertTrue(payload.contains("ARCHIVO:"));
        }

        @Test
        @DisplayName("Debe truncar archivos muy largos")
        void shouldTruncateLongFiles() throws IOException {
            StringBuilder longContent = new StringBuilder();
            for (int i = 0; i < 500; i++) {
                longContent.append("def function_").append(i).append("(): pass\n");
            }
            Files.writeString(tempDir.resolve("long.py"), longContent.toString());
            contextManager.indexProject(tempDir);

            String payload = contextManager.buildContextPayload(tempDir, "function_0");

            assertTrue(payload.contains("[TRUNCADO]"));
        }

        @Test
        @DisplayName("Debe funcionar sin indexar (solo conciencia situacional)")
        void shouldWorkWithoutIndex() throws IOException {
            Files.writeString(tempDir.resolve("main.py"), "def hello(): pass");

            String payload = contextManager.buildContextPayload(tempDir, "hello");

            assertTrue(payload.contains("CONCIENCIA SITUACIONAL"));
            assertFalse(payload.contains("ARCHIVOS RELEVANTES"));
        }
    }

    @Nested
    @DisplayName("Utility Methods Tests")
    class UtilityMethodsTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("getStats debe retornar informacion formateada")
        void getStatsShouldReturnFormattedInfo() throws IOException {
            Files.writeString(tempDir.resolve("main.py"), "def hello(): pass");
            contextManager.indexProject(tempDir);

            String stats = contextManager.getStats();

            assertTrue(stats.contains("ContextManager Stats"));
            assertTrue(stats.contains("Indexed: true"));
            assertTrue(stats.contains("Files: 1"));
        }

        @Test
        @DisplayName("clear debe limpiar el indice")
        void clearShouldResetIndex() throws IOException {
            Files.writeString(tempDir.resolve("main.py"), "def hello(): pass");
            contextManager.indexProject(tempDir);
            assertTrue(contextManager.isIndexed());

            contextManager.clear();

            assertFalse(contextManager.isIndexed());
            assertEquals(0, contextManager.getIndexedFileCount());
            assertNull(contextManager.getIndexedProjectPath());
        }

        @Test
        @DisplayName("Debe exponer componentes internos")
        void shouldExposeInternalComponents() {
            assertNotNull(contextManager.getInsight());
            assertNotNull(contextManager.getHound());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Flujo completo: indexar + buscar + generar payload")
        void fullFlowShouldWork() throws IOException {
            Files.writeString(tempDir.resolve("requirements.txt"), "pytest");
            Files.writeString(tempDir.resolve("main.py"),
                "from calculator import add\n\nprint(add(1, 2))");
            Files.writeString(tempDir.resolve("calculator.py"),
                "def add(a, b):\n    return a + b\n\ndef subtract(a, b):\n    return a - b");
            Files.writeString(tempDir.resolve("test_calculator.py"),
                "from calculator import add, subtract\n\n" +
                "def test_add():\n    assert add(1, 2) == 3");

            contextManager.indexProject(tempDir);
            String payload = contextManager.buildContextPayload(
                tempDir,
                "TypeError: add() missing 1 required positional argument"
            );

            assertTrue(contextManager.isIndexed());
            assertTrue(contextManager.getIndexedFileCount() >= 3);
            assertTrue(payload.contains("CONCIENCIA SITUACIONAL"));
            assertTrue(payload.contains("Python"));
            assertTrue(payload.contains("ARCHIVOS RELEVANTES"));
        }

        @Test
        @DisplayName("Debe manejar proyecto multilenguaje")
        void shouldHandleMultiLanguageProject() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
            Files.createDirectories(tempDir.resolve("src/main/java"));
            Files.writeString(tempDir.resolve("src/main/java/Main.java"),
                "public class Main { public static void main(String[] args) {} }");
            Files.createDirectories(tempDir.resolve("scripts"));
            Files.writeString(tempDir.resolve("scripts/helper.py"),
                "def run_build(): pass");

            contextManager.indexProject(tempDir);
            String payload = contextManager.buildContextPayload(tempDir, "Main class");

            assertTrue(contextManager.getIndexedFileCount() >= 2);
            assertTrue(payload.contains("Java"));
        }
    }
}
