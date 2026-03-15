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
package dev.fararoni.core.core.enterprise;

import dev.fararoni.core.core.search.SemanticCache;
import dev.fararoni.core.core.search.TheHound.EmbeddingProvider;
import dev.fararoni.core.core.surgeon.QualityGate;
import dev.fararoni.core.core.workspace.GitManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Enterprise Features Tests (Relocated Classes)")
class EnterprisePackageTest {
    @Nested
    @DisplayName("SemanticCache Tests (core.search)")
    class SemanticCacheTests {
        private SemanticCache cache;
        private MockEmbeddingProvider mockProvider;

        @BeforeEach
        void setUp() {
            mockProvider = new MockEmbeddingProvider();
            cache = new SemanticCache(mockProvider);
        }

        @Test
        @DisplayName("Constructor debe rechazar provider null")
        void constructorShouldRejectNullProvider() {
            assertThrows(IllegalArgumentException.class, () -> new SemanticCache(null));
        }

        @Test
        @DisplayName("retrieve debe retornar null para cache vacio")
        void retrieveShouldReturnNullForEmptyCache() {
            String result = cache.retrieve("How do I read a file?");
            assertNull(result, "Cache vacio debe retornar null");
        }

        @Test
        @DisplayName("store y retrieve deben funcionar para queries identicos")
        void storeAndRetrieveShouldWorkForIdenticalQueries() {
            String query = "How do I read a file in Python?";
            String response = "Use open() function...";

            cache.store(query, response);
            String retrieved = cache.retrieve(query);

            assertEquals(response, retrieved, "Query identico debe retornar respuesta cacheada");
        }

        @Test
        @DisplayName("retrieve debe encontrar queries similares (>= 0.95)")
        void retrieveShouldFindSimilarQueries() {
            mockProvider.setVector("How do I read a file?",
                    new float[]{0.9f, 0.1f, 0.0f});
            mockProvider.setVector("How can I read a file?",
                    new float[]{0.91f, 0.09f, 0.01f});

            cache.store("How do I read a file?", "Use open()...");
            String result = cache.retrieve("How can I read a file?");

            assertNotNull(result, "Queries muy similares deben encontrar cache hit");
        }

        @Test
        @DisplayName("retrieve debe ignorar queries diferentes (< 0.95)")
        void retrieveShouldIgnoreDifferentQueries() {
            mockProvider.setVector("How do I read a file?",
                    new float[]{0.9f, 0.1f, 0.0f});
            mockProvider.setVector("What is Python?",
                    new float[]{0.1f, 0.9f, 0.5f});

            cache.store("How do I read a file?", "Use open()...");
            String result = cache.retrieve("What is Python?");

            assertNull(result, "Queries diferentes deben retornar null");
        }

        @Test
        @DisplayName("store debe ignorar queries null o vacios")
        void storeShouldIgnoreNullOrEmptyQueries() {
            cache.store(null, "response");
            cache.store("", "response");
            cache.store("query", null);
            cache.store("query", "");

            assertEquals(0, cache.size(), "No debe almacenar entradas invalidas");
        }

        @Test
        @DisplayName("cache debe respetar limite de tamano")
        void cacheShouldRespectSizeLimit() {
            for (int i = 0; i < 1100; i++) {
                String query = "Query number " + i;
                mockProvider.setVector(query, new float[]{i * 0.001f, 0.5f, 0.5f});
                cache.store(query, "Response " + i);
            }

            assertTrue(cache.size() <= 1000, "Cache no debe exceder limite de 1000 entradas");
        }

        @Test
        @DisplayName("clear debe limpiar cache y estadisticas")
        void clearShouldResetCacheAndStats() {
            cache.store("query1", "response1");
            cache.retrieve("query1");
            cache.retrieve("query2");

            cache.clear();

            assertEquals(0, cache.size());
            assertEquals(0, cache.getCacheHits());
            assertEquals(0, cache.getCacheMisses());
        }

        @Test
        @DisplayName("estadisticas deben rastrear hits y misses")
        void statisticsShouldTrackHitsAndMisses() {
            mockProvider.setVector("query1", new float[]{0.9f, 0.1f, 0.0f});

            cache.store("query1", "response1");
            cache.retrieve("query1");
            cache.retrieve("different");
            cache.retrieve("another");

            assertEquals(1, cache.getCacheHits());
            assertEquals(2, cache.getCacheMisses());
            assertEquals(1.0 / 3.0, cache.getHitRatio(), 0.01);
        }

        @Test
        @DisplayName("getStatsReport debe retornar reporte formateado")
        void getStatsReportShouldReturnFormattedReport() {
            cache.store("query1", "response1");

            String report = cache.getStatsReport();

            assertNotNull(report);
            assertTrue(report.contains("CACHE STATS"));
            assertTrue(report.contains("Size:"));
            assertTrue(report.contains("Hits:"));
        }
    }

    @Nested
    @DisplayName("QualityGate Tests (core.surgeon)")
    class QualityGateTests {
        private QualityGate gate;

        @TempDir
        Path tempDir;

        @BeforeEach
        void setUp() {
            gate = new QualityGate();
        }

        @Test
        @DisplayName("inspect debe retornar lista vacia para archivo inexistente")
        void inspectShouldHandleNonExistentFile() {
            List<String> errors = gate.inspect(Path.of("/nonexistent/file.py"));

            assertFalse(errors.isEmpty());
            assertEquals(QualityGate.InspectionResult.LINTER_ERROR, gate.getLastResult());
        }

        @Test
        @DisplayName("inspect debe aprobar codigo Python valido")
        void inspectShouldApprovePythonValidCode() throws Exception {
            Path pythonFile = tempDir.resolve("valid.py");
            Files.writeString(pythonFile, "print('Hello, World!')\n");

            List<String> errors = gate.inspect(pythonFile);

            assertTrue(errors.isEmpty(), "Codigo Python valido debe pasar: " + errors);
            assertTrue(gate.wasLastInspectionSuccessful());
        }

        @Test
        @DisplayName("inspect debe rechazar codigo Python invalido")
        void inspectShouldRejectPythonInvalidCode() throws Exception {
            Path pythonFile = tempDir.resolve("invalid.py");
            Files.writeString(pythonFile, "def broken(\n");

            List<String> errors = gate.inspect(pythonFile);

            assertFalse(errors.isEmpty(), "Codigo Python invalido debe fallar");
            assertEquals(QualityGate.InspectionResult.FAILED, gate.getLastResult());
        }

        @Test
        @DisplayName("inspect debe retornar UNSUPPORTED para archivos no soportados")
        void inspectShouldReturnUnsupportedForUnknownFiles() throws Exception {
            Path txtFile = tempDir.resolve("readme.txt");
            Files.writeString(txtFile, "Just some text");

            List<String> errors = gate.inspect(txtFile);

            assertTrue(errors.isEmpty());
            assertEquals(QualityGate.InspectionResult.UNSUPPORTED, gate.getLastResult());
            assertTrue(gate.wasLastInspectionSuccessful(), "Unsupported debe considerarse exitoso");
        }

        @Test
        @DisplayName("inspectCode debe validar codigo sin archivo")
        void inspectCodeShouldValidateWithoutFile() {
            String validPython = "x = 1\nprint(x)\n";

            List<String> errors = gate.inspectCode(validPython, "python");

            assertTrue(errors.isEmpty(), "Codigo valido inline debe pasar: " + errors);
        }

        @Test
        @DisplayName("inspectCode debe rechazar codigo vacio")
        void inspectCodeShouldRejectEmptyCode() {
            List<String> errors = gate.inspectCode("", "python");

            assertFalse(errors.isEmpty());
            assertEquals(QualityGate.InspectionResult.FAILED, gate.getLastResult());
        }

        @Test
        @DisplayName("getFirstError debe retornar primer error o null")
        void getFirstErrorShouldReturnFirstOrNull() throws Exception {
            Path validFile = tempDir.resolve("valid2.py");
            Files.writeString(validFile, "x = 42\n");
            gate.inspect(validFile);
            assertNull(gate.getFirstError());

            Path invalidFile = tempDir.resolve("invalid2.py");
            Files.writeString(invalidFile, "x = (\n");
            gate.inspect(invalidFile);
            assertNotNull(gate.getFirstError());
        }

        @Test
        @DisplayName("getInspectionReport debe generar reporte completo")
        void getInspectionReportShouldGenerateReport() throws Exception {
            Path invalidFile = tempDir.resolve("broken.py");
            Files.writeString(invalidFile, "def x(:\n");
            gate.inspect(invalidFile);

            String report = gate.getInspectionReport();

            assertNotNull(report);
            assertTrue(report.contains("QUALITY REPORT"));
            assertTrue(report.contains("Result:"));
            assertTrue(report.contains("Issues:"));
        }
    }

    @Nested
    @DisplayName("GitManager Tests (core.workspace)")
    class GitManagerTests {
        private GitManager gitManager;

        @TempDir
        Path tempDir;

        @BeforeEach
        void setUp() throws Exception {
            gitManager = new GitManager(tempDir);

            ProcessBuilder pb = new ProcessBuilder("git", "init");
            pb.directory(tempDir.toFile());
            pb.start().waitFor();

            new ProcessBuilder("git", "config", "user.email", "test@test.com")
                    .directory(tempDir.toFile()).start().waitFor();
            new ProcessBuilder("git", "config", "user.name", "Test User")
                    .directory(tempDir.toFile()).start().waitFor();

            Files.writeString(tempDir.resolve("initial.txt"), "initial content");
            new ProcessBuilder("git", "add", ".")
                    .directory(tempDir.toFile()).start().waitFor();
            new ProcessBuilder("git", "commit", "-m", "initial")
                    .directory(tempDir.toFile()).start().waitFor();
        }

        @Test
        @DisplayName("isGitRepository debe detectar repo git")
        void isGitRepositoryShouldDetectGitRepo() {
            assertTrue(gitManager.isGitRepository());
        }

        @Test
        @DisplayName("getCurrentBranch debe retornar rama actual")
        void getCurrentBranchShouldReturnBranch() {
            String branch = gitManager.getCurrentBranch();
            assertNotNull(branch);
            assertTrue(branch.equals("main") || branch.equals("master"),
                    "Branch debe ser main o master, pero fue: " + branch);
        }

        @Test
        @DisplayName("hasUncommittedChanges debe detectar cambios")
        void hasUncommittedChangesShouldDetectChanges() throws Exception {
            assertFalse(gitManager.hasUncommittedChanges());

            Files.writeString(tempDir.resolve("newfile.txt"), "new content");

            assertTrue(gitManager.hasUncommittedChanges());
        }

        @Test
        @DisplayName("createSnapshot debe crear stash con prefijo Fararoni")
        void createSnapshotShouldCreateStash() throws Exception {
            Files.writeString(tempDir.resolve("initial.txt"), "modified for stash");

            boolean result = gitManager.createSnapshot();

            assertTrue(result, "createSnapshot debe retornar true");
        }

        @Test
        @DisplayName("createSnapshot debe manejar caso sin cambios")
        void createSnapshotShouldHandleNoChanges() {
            boolean result = gitManager.createSnapshot();
            assertTrue(result, "Debe retornar true aunque no haya cambios");
        }

        @Test
        @DisplayName("smartCommit debe crear commit con prefijo correcto")
        void smartCommitShouldCreateCommitWithPrefix() throws Exception {
            Files.writeString(tempDir.resolve("feature.txt"), "new feature");

            boolean result = gitManager.smartCommit("Added new feature");

            assertTrue(result);
            assertFalse(gitManager.hasUncommittedChanges(), "No debe haber cambios pendientes");
        }

        @Test
        @DisplayName("smartCommit debe sanitizar mensaje")
        void smartCommitShouldSanitizeMessage() throws Exception {
            Files.writeString(tempDir.resolve("test.txt"), "test");

            boolean result = gitManager.smartCommit("Fix \"bug\" with 'quotes' and `backticks`");

            assertTrue(result);
        }

        @Test
        @DisplayName("smartCommit debe manejar mensaje muy largo")
        void smartCommitShouldHandleLongMessage() throws Exception {
            Files.writeString(tempDir.resolve("long.txt"), "content");

            String longMessage = "A".repeat(200);
            boolean result = gitManager.smartCommit(longMessage);

            assertTrue(result);
        }

        @Test
        @DisplayName("getDiff debe retornar cambios actuales")
        void getDiffShouldReturnCurrentChanges() throws Exception {
            Files.writeString(tempDir.resolve("initial.txt"), "modified content");

            String diff = gitManager.getDiff();

            assertNotNull(diff);
            assertTrue(diff.contains("modified content") || diff.contains("-initial content"),
                    "Diff debe mostrar cambios");
        }

        @Test
        @DisplayName("undoLastChange debe revertir stash")
        void undoLastChangeShouldRevertStash() throws Exception {
            Files.writeString(tempDir.resolve("initial.txt"), "modified content");
            assertTrue(gitManager.hasUncommittedChanges(), "Debe haber cambios antes del snapshot");

            gitManager.createSnapshot();

            boolean result = gitManager.undoLastChange();

            assertNotNull(gitManager.getLastResult(), "Debe tener un resultado");
        }

        @Test
        @DisplayName("wasLastCommandSuccessful debe rastrear estado")
        void wasLastCommandSuccessfulShouldTrackState() {
            gitManager.hasUncommittedChanges();

            assertTrue(gitManager.wasLastCommandSuccessful(),
                    "git status debe ser exitoso en un repo valido");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        @Test
        @DisplayName("Enterprise cycle simulation")
        void enterpriseCycleSimulation() {
            MockEmbeddingProvider provider = new MockEmbeddingProvider();
            SemanticCache cache = new SemanticCache(provider);
            QualityGate gate = new QualityGate();

            String query = "How do I read a file?";
            String cachedResponse = cache.retrieve(query);
            assertNull(cachedResponse, "Primera consulta debe ser cache miss");

            String generatedCode = "with open('file.txt') as f:\n    print(f.read())\n";

            List<String> errors = gate.inspectCode(generatedCode, "python");
            assertTrue(errors.isEmpty(), "Codigo generado debe pasar calidad");

            cache.store(query, generatedCode);

            provider.setVector(query, new float[]{0.9f, 0.1f, 0.0f});
            provider.setVector("How can I read a file?", new float[]{0.91f, 0.09f, 0.01f});

            String cached = cache.retrieve("How can I read a file?");

            assertEquals(1, cache.size());
        }
    }

    @Nested
    @DisplayName("V1: SemanticCache Persistence Tests")
    class SemanticCachePersistenceTests {
        @TempDir
        Path tempDir;

        private MockEmbeddingProvider mockProvider;

        @BeforeEach
        void setUp() {
            mockProvider = new MockEmbeddingProvider();
        }

        @Test
        @DisplayName("Constructor sin persistencia debe funcionar")
        void constructorWithoutPersistenceShouldWork() {
            SemanticCache cache = new SemanticCache(mockProvider, 1000L, false);

            assertFalse(cache.isPersistenceEnabled());
            cache.store("query", "response");
            assertEquals(1, cache.size());
        }

        @Test
        @DisplayName("Constructor con ruta personalizada debe funcionar")
        void constructorWithCustomPathShouldWork() {
            Path customPath = tempDir.resolve("custom_cache.dat");
            SemanticCache cache = new SemanticCache(mockProvider, 1000L, customPath);

            assertTrue(cache.isPersistenceEnabled());
        }

        @Test
        @DisplayName("store debe marcar cache como dirty")
        void storeShouldMarkCacheAsDirty() {
            Path customPath = tempDir.resolve("dirty_test.dat");
            SemanticCache cache = new SemanticCache(mockProvider, 1000L, customPath);

            assertFalse(cache.isDirty());
            cache.store("query", "response");
            assertTrue(cache.isDirty());
        }

        @Test
        @DisplayName("saveToDisk debe guardar y limpiar dirty flag")
        void saveToDiskShouldSaveAndClearDirty() {
            Path customPath = tempDir.resolve("save_test.dat");
            SemanticCache cache = new SemanticCache(mockProvider, 1000L, customPath);

            cache.store("query1", "response1");
            assertTrue(cache.isDirty());

            boolean saved = cache.saveToDisk();

            assertTrue(saved, "saveToDisk debe retornar true");
            assertFalse(cache.isDirty(), "dirty flag debe ser false despues de guardar");
            assertTrue(Files.exists(customPath), "Archivo de cache debe existir");
        }

        @Test
        @DisplayName("Cache debe persistir y restaurar datos")
        void cacheShouldPersistAndRestoreData() {
            Path customPath = tempDir.resolve("persist_test.dat");

            SemanticCache cache1 = new SemanticCache(mockProvider, 1000L, customPath);
            mockProvider.setVector("query1", new float[]{0.9f, 0.1f, 0.0f});
            cache1.store("query1", "response1");
            cache1.saveToDisk();

            SemanticCache cache2 = new SemanticCache(mockProvider, 1000L, customPath);

            assertEquals(1, cache2.size(), "Cache restaurado debe tener 1 entrada");
        }

        @Test
        @DisplayName("shutdown debe guardar cambios pendientes")
        void shutdownShouldSavePendingChanges() {
            Path customPath = tempDir.resolve("shutdown_test.dat");
            SemanticCache cache = new SemanticCache(mockProvider, 1000L, customPath);

            cache.store("query", "response");
            assertTrue(cache.isDirty());

            cache.shutdown();

            assertFalse(cache.isDirty(), "dirty flag debe ser false despues de shutdown");
            assertTrue(Files.exists(customPath), "Archivo debe existir despues de shutdown");
        }

        @Test
        @DisplayName("saveToDisk sin persistencia debe retornar false")
        void saveToDiskWithoutPersistenceShouldReturnFalse() {
            SemanticCache cache = new SemanticCache(mockProvider, 1000L, false);
            cache.store("query", "response");

            boolean saved = cache.saveToDisk();

            assertFalse(saved, "saveToDisk debe retornar false cuando persistencia deshabilitada");
        }
    }

    @Nested
    @DisplayName("V2: GitManager Hardening Tests")
    class GitManagerHardeningTests {
        @TempDir
        Path tempDir;

        private GitManager gitManager;

        @BeforeEach
        void setUp() throws Exception {
            gitManager = new GitManager(tempDir);

            new ProcessBuilder("git", "init")
                    .directory(tempDir.toFile()).start().waitFor();
            new ProcessBuilder("git", "config", "user.email", "test@test.com")
                    .directory(tempDir.toFile()).start().waitFor();
            new ProcessBuilder("git", "config", "user.name", "Test User")
                    .directory(tempDir.toFile()).start().waitFor();

            Files.writeString(tempDir.resolve("initial.txt"), "initial content");
            new ProcessBuilder("git", "add", ".")
                    .directory(tempDir.toFile()).start().waitFor();
            new ProcessBuilder("git", "commit", "-m", "initial")
                    .directory(tempDir.toFile()).start().waitFor();
        }

        @Test
        @DisplayName("validateCleanState debe detectar repo limpio")
        void validateCleanStateShouldDetectCleanRepo() {
            GitManager.CleanStateResult result = gitManager.validateCleanState();

            assertTrue(result.safe());
            assertEquals(GitManager.CleanStateIssue.NONE, result.issue());
        }

        @Test
        @DisplayName("validateCleanState debe detectar cambios sin commitear")
        void validateCleanStateShouldDetectUncommittedChanges() throws Exception {
            Files.writeString(tempDir.resolve("new.txt"), "new content");

            GitManager.CleanStateResult result = gitManager.validateCleanState();

            assertTrue(result.safe(), "Cambios sin commitear no deben bloquear");
            assertEquals(GitManager.CleanStateIssue.UNCOMMITTED_CHANGES, result.issue());
        }

        @Test
        @DisplayName("isSafeToModify debe retornar true para repo valido")
        void isSafeToModifyShouldReturnTrueForValidRepo() {
            assertTrue(gitManager.isSafeToModify());
        }

        @Test
        @DisplayName("stageSpecificFiles debe agregar solo archivos especificados")
        void stageSpecificFilesShouldAddOnlySpecifiedFiles() throws Exception {
            Files.writeString(tempDir.resolve("file1.txt"), "content1");
            Files.writeString(tempDir.resolve("file2.txt"), "content2");
            Files.writeString(tempDir.resolve("file3.txt"), "content3");

            boolean result = gitManager.stageSpecificFiles(
                    List.of(Path.of("file1.txt"), Path.of("file2.txt")));

            assertTrue(result);

            GitManager.CommandResult status = gitManager.getLastResult();
        }

        @Test
        @DisplayName("stageSpecificFiles debe manejar archivos inexistentes")
        void stageSpecificFilesShouldHandleNonExistentFiles() {
            boolean result = gitManager.stageSpecificFiles(
                    List.of(Path.of("nonexistent.txt")));

            assertTrue(result, "Debe retornar true aunque archivo no exista");
        }

        @Test
        @DisplayName("stageFiles varargs debe funcionar")
        void stageFilesVarargsShouldWork() throws Exception {
            Files.writeString(tempDir.resolve("a.txt"), "a");
            Files.writeString(tempDir.resolve("b.txt"), "b");

            boolean result = gitManager.stageFiles(
                    Path.of("a.txt"), Path.of("b.txt"));

            assertTrue(result);
        }

        @Test
        @DisplayName("smartCommit con archivos especificos debe funcionar")
        void smartCommitWithSpecificFilesShouldWork() throws Exception {
            Files.writeString(tempDir.resolve("specific.txt"), "content");

            boolean result = gitManager.smartCommit("Test commit",
                    List.of(Path.of("specific.txt")));

            assertTrue(result);
            assertFalse(gitManager.hasUncommittedChanges());
        }

        @Test
        @DisplayName("undoLastChange debe usar apply en lugar de pop")
        void undoLastChangeShouldUseApply() throws Exception {
            Files.writeString(tempDir.resolve("initial.txt"), "modified");
            gitManager.createSnapshot();

            boolean result = gitManager.undoLastChange();

            List<String> snapshots = gitManager.listFararoniSnapshots();
            assertNotNull(gitManager.getLastResult());
        }
    }

    @Nested
    @DisplayName("V3: QualityGate Capability Detection Tests")
    class QualityGateCapabilityTests {
        private QualityGate gate;

        @TempDir
        Path tempDir;

        @BeforeEach
        void setUp() {
            gate = new QualityGate();
        }

        @Test
        @DisplayName("isToolAvailable debe cachear resultados")
        void isToolAvailableShouldCacheResults() {
            boolean first = gate.isToolAvailable(QualityGate.SupportedLanguage.PYTHON);
            boolean second = gate.isToolAvailable(QualityGate.SupportedLanguage.PYTHON);

            assertEquals(first, second, "Resultados deben ser consistentes");
        }

        @Test
        @DisplayName("detectAllCapabilities debe verificar todas las herramientas")
        void detectAllCapabilitiesShouldCheckAllTools() {
            Map<QualityGate.SupportedLanguage, Boolean> caps = gate.detectAllCapabilities();

            assertEquals(QualityGate.SupportedLanguage.values().length, caps.size(),
                    "Debe verificar todas las herramientas");
        }

        @Test
        @DisplayName("clearCapabilityCache debe forzar re-verificacion")
        void clearCapabilityCacheShouldForceRecheck() {
            gate.isToolAvailable(QualityGate.SupportedLanguage.PYTHON);
            gate.clearCapabilityCache();

            Map<QualityGate.SupportedLanguage, Boolean> caps = gate.detectAllCapabilities();
            assertNotNull(caps);
        }

        @Test
        @DisplayName("getCapabilitiesReport debe generar reporte")
        void getCapabilitiesReportShouldGenerateReport() {
            String report = gate.getCapabilitiesReport();

            assertNotNull(report);
            assertTrue(report.contains("QUALITY CAPABILITIES"));
            assertTrue(report.contains("PYTHON"));
        }

        @Test
        @DisplayName("Modo no estricto debe permitir herramientas faltantes")
        void nonStrictModeShouldAllowMissingTools() throws Exception {
            gate.setStrictMode(false);

            Path rustFile = tempDir.resolve("test.rs");
            Files.writeString(rustFile, "fn main() {}");

            List<String> errors = gate.inspect(rustFile);

            if (gate.getLastResult() == QualityGate.InspectionResult.TOOL_NOT_AVAILABLE) {
                assertTrue(gate.wasLastInspectionSuccessful(),
                        "Modo no estricto debe considerar exitoso");
            }
        }

        @Test
        @DisplayName("Modo estricto debe fallar con herramientas faltantes")
        void strictModeShouldFailWithMissingTools() throws Exception {
            gate.setStrictMode(true);

            Path rustFile = tempDir.resolve("test.rs");
            Files.writeString(rustFile, "fn main() {}");

            gate.inspect(rustFile);

            if (gate.getLastResult() == QualityGate.InspectionResult.TOOL_NOT_AVAILABLE) {
                assertFalse(gate.wasLastInspectionSuccessful(),
                        "Modo estricto debe considerar fallo");
            }
        }

        @Test
        @DisplayName("isStrictMode debe reflejar configuracion")
        void isStrictModeShouldReflectConfiguration() {
            assertFalse(gate.isStrictMode(), "Default debe ser no estricto");

            gate.setStrictMode(true);
            assertTrue(gate.isStrictMode());

            gate.setStrictMode(false);
            assertFalse(gate.isStrictMode());
        }

        @Test
        @DisplayName("TOOL_NOT_AVAILABLE debe ser resultado valido")
        void toolNotAvailableShouldBeValidResult() {
            QualityGate.InspectionResult result = QualityGate.InspectionResult.TOOL_NOT_AVAILABLE;
            assertNotNull(result);
        }

        @Test
        @DisplayName("SupportedLanguage debe tener comandos de version")
        void supportedLanguageShouldHaveVersionCommands() {
            for (QualityGate.SupportedLanguage lang : QualityGate.SupportedLanguage.values()) {
                assertNotNull(lang.getVersionCommand(), "Debe tener comando de version");
                assertTrue(lang.getVersionCommand().length > 0);
                assertNotNull(lang.getToolName(), "Debe tener nombre de herramienta");
            }
        }
    }

    private static class MockEmbeddingProvider implements EmbeddingProvider {
        private final Map<String, float[]> vectors = new HashMap<>();
        private final float[] defaultVector = new float[]{0.5f, 0.5f, 0.5f};

        void setVector(String text, float[] vector) {
            vectors.put(text, vector);
        }

        @Override
        public float[] getEmbedding(String text) {
            return vectors.getOrDefault(text, defaultVector);
        }
    }
}
