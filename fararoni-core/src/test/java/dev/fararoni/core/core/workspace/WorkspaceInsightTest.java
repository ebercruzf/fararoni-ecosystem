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
@DisplayName("WorkspaceInsight - Ojo de Dios Tests")
class WorkspaceInsightTest {
    private WorkspaceInsight insight;

    @BeforeEach
    void setUp() {
        insight = new WorkspaceInsight();
    }

    @Nested
    @DisplayName("Project Type Detection Tests")
    class ProjectTypeDetectionTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Debe detectar proyecto Python por requirements.txt")
        void shouldDetectPythonByRequirements() throws IOException {
            Files.createFile(tempDir.resolve("requirements.txt"));
            Files.createFile(tempDir.resolve("main.py"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.PYTHON, type);
        }

        @Test
        @DisplayName("Debe detectar proyecto Python por pyproject.toml")
        void shouldDetectPythonByPyproject() throws IOException {
            Files.createFile(tempDir.resolve("pyproject.toml"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.PYTHON, type);
        }

        @Test
        @DisplayName("Debe detectar proyecto Python por extension .py")
        void shouldDetectPythonByExtension() throws IOException {
            Files.createFile(tempDir.resolve("bowling.py"));
            Files.createFile(tempDir.resolve("bowling_test.py"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.PYTHON, type);
        }

        @Test
        @DisplayName("Debe detectar proyecto Java Maven por pom.xml")
        void shouldDetectJavaMaven() throws IOException {
            Files.createFile(tempDir.resolve("pom.xml"));
            Files.createDirectories(tempDir.resolve("src/main/java"));
            Files.createFile(tempDir.resolve("src/main/java/Main.java"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.JAVA_MAVEN, type);
        }

        @Test
        @DisplayName("Debe detectar proyecto Java Gradle por build.gradle")
        void shouldDetectJavaGradle() throws IOException {
            Files.createFile(tempDir.resolve("build.gradle"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.JAVA_GRADLE, type);
        }

        @Test
        @DisplayName("Debe detectar proyecto Java Gradle Kotlin por build.gradle.kts")
        void shouldDetectJavaGradleKotlin() throws IOException {
            Files.createFile(tempDir.resolve("build.gradle.kts"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.JAVA_GRADLE, type);
        }

        @Test
        @DisplayName("Debe detectar proyecto Angular por angular.json")
        void shouldDetectAngular() throws IOException {
            Files.createFile(tempDir.resolve("angular.json"));
            Files.createFile(tempDir.resolve("package.json"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.ANGULAR, type);
        }

        @Test
        @DisplayName("Debe detectar proyecto Node.js por package.json")
        void shouldDetectNodeJs() throws IOException {
            Files.createFile(tempDir.resolve("package.json"));
            Files.createFile(tempDir.resolve("index.js"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.NODE_JS, type);
        }

        @Test
        @DisplayName("Debe detectar proyecto Go por go.mod")
        void shouldDetectGo() throws IOException {
            Files.createFile(tempDir.resolve("go.mod"));
            Files.createFile(tempDir.resolve("main.go"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.GO, type);
        }

        @Test
        @DisplayName("Debe detectar proyecto Rust por Cargo.toml")
        void shouldDetectRust() throws IOException {
            Files.createFile(tempDir.resolve("Cargo.toml"));
            Files.createDirectories(tempDir.resolve("src"));
            Files.createFile(tempDir.resolve("src/main.rs"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.RUST, type);
        }

        @Test
        @DisplayName("Debe detectar proyecto Ruby por Gemfile")
        void shouldDetectRuby() throws IOException {
            Files.createFile(tempDir.resolve("Gemfile"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.RUBY, type);
        }

        @Test
        @DisplayName("Debe detectar proyecto C/C++ por CMakeLists.txt")
        void shouldDetectCppByCMake() throws IOException {
            Files.createFile(tempDir.resolve("CMakeLists.txt"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.C_CPP, type);
        }

        @Test
        @DisplayName("Debe detectar proyecto C/C++ por Makefile")
        void shouldDetectCppByMakefile() throws IOException {
            Files.createFile(tempDir.resolve("Makefile"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.C_CPP, type);
        }

        @Test
        @DisplayName("Debe retornar UNKNOWN para directorio vacio")
        void shouldReturnUnknownForEmptyDir() {
            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.UNKNOWN, type);
        }

        @Test
        @DisplayName("Angular debe tener prioridad sobre Node.js")
        void angularShouldHavePriorityOverNodeJs() throws IOException {
            Files.createFile(tempDir.resolve("angular.json"));
            Files.createFile(tempDir.resolve("package.json"));
            Files.createFile(tempDir.resolve("tsconfig.json"));

            WorkspaceInsight.ProjectType type = insight.detectType(tempDir);

            assertEquals(WorkspaceInsight.ProjectType.ANGULAR, type,
                "Angular debe tener prioridad sobre Node.js");
        }
    }

    @Nested
    @DisplayName("Noise Filtering Tests")
    class NoiseFilteringTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Debe ignorar directorio node_modules")
        void shouldIgnoreNodeModules() throws IOException {
            Files.createDirectories(tempDir.resolve("node_modules/some-package"));
            Files.createFile(tempDir.resolve("node_modules/some-package/index.js"));
            Files.createFile(tempDir.resolve("package.json"));
            Files.createFile(tempDir.resolve("index.js"));

            WorkspaceInsight.ScanResult result = insight.scan(tempDir);

            boolean hasNodeModules = result.files().stream()
                .anyMatch(p -> p.toString().contains("node_modules"));
            assertFalse(hasNodeModules, "No debe incluir archivos de node_modules");
        }

        @Test
        @DisplayName("Debe ignorar directorio .git")
        void shouldIgnoreGitDir() throws IOException {
            Files.createDirectories(tempDir.resolve(".git/objects"));
            Files.createFile(tempDir.resolve(".git/HEAD"));
            Files.createFile(tempDir.resolve("main.py"));

            WorkspaceInsight.ScanResult result = insight.scan(tempDir);

            boolean hasGit = result.files().stream()
                .anyMatch(p -> p.toString().contains(".git"));
            assertFalse(hasGit, "No debe incluir archivos de .git");
        }

        @Test
        @DisplayName("Debe ignorar directorio __pycache__")
        void shouldIgnorePycache() throws IOException {
            Files.createDirectories(tempDir.resolve("__pycache__"));
            Files.createFile(tempDir.resolve("__pycache__/main.cpython-39.pyc"));
            Files.createFile(tempDir.resolve("main.py"));

            WorkspaceInsight.ScanResult result = insight.scan(tempDir);

            boolean hasPycache = result.files().stream()
                .anyMatch(p -> p.toString().contains("__pycache__"));
            assertFalse(hasPycache, "No debe incluir archivos de __pycache__");
        }

        @Test
        @DisplayName("Debe ignorar directorio target")
        void shouldIgnoreTarget() throws IOException {
            Files.createDirectories(tempDir.resolve("target/classes"));
            Files.createFile(tempDir.resolve("target/classes/Main.class"));
            Files.createFile(tempDir.resolve("pom.xml"));
            Files.createDirectories(tempDir.resolve("src/main/java"));
            Files.createFile(tempDir.resolve("src/main/java/Main.java"));

            WorkspaceInsight.ScanResult result = insight.scan(tempDir);

            boolean hasTarget = result.files().stream()
                .anyMatch(p -> p.toString().contains("/target/"));
            assertFalse(hasTarget, "No debe incluir archivos de target");
        }

        @Test
        @DisplayName("Debe ignorar archivos .class")
        void shouldIgnoreClassFiles() throws IOException {
            Files.createFile(tempDir.resolve("Main.java"));
            Files.createFile(tempDir.resolve("Main.class"));

            WorkspaceInsight.ScanResult result = insight.scan(tempDir);

            boolean hasClass = result.files().stream()
                .anyMatch(p -> p.toString().endsWith(".class"));
            assertFalse(hasClass, "No debe incluir archivos .class");
        }

        @Test
        @DisplayName("Debe ignorar archivos de imagen")
        void shouldIgnoreImageFiles() throws IOException {
            Files.createFile(tempDir.resolve("main.py"));
            Files.createFile(tempDir.resolve("logo.png"));
            Files.createFile(tempDir.resolve("banner.jpg"));

            WorkspaceInsight.ScanResult result = insight.scan(tempDir);

            boolean hasImages = result.files().stream()
                .anyMatch(p -> p.toString().endsWith(".png") || p.toString().endsWith(".jpg"));
            assertFalse(hasImages, "No debe incluir archivos de imagen");
        }

        @Test
        @DisplayName("Debe ignorar lockfiles")
        void shouldIgnoreLockfiles() throws IOException {
            Files.createFile(tempDir.resolve("package.json"));
            Files.createFile(tempDir.resolve("package-lock.json"));
            Files.createFile(tempDir.resolve("yarn.lock"));

            WorkspaceInsight.ScanResult result = insight.scan(tempDir);

            boolean hasLockfiles = result.files().stream()
                .anyMatch(p -> {
                    String name = p.getFileName().toString();
                    return name.contains("-lock.json") || name.endsWith(".lock");
                });
            assertFalse(hasLockfiles, "No debe incluir lockfiles");
        }
    }

    @Nested
    @DisplayName("Report Generation Tests")
    class ReportGenerationTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Debe generar reporte con formato correcto")
        void shouldGenerateFormattedReport() throws IOException {
            Files.createFile(tempDir.resolve("bowling.py"));
            Files.createFile(tempDir.resolve("bowling_test.py"));
            Files.createFile(tempDir.resolve("README.md"));

            String report = insight.scanAndReport(tempDir);

            assertTrue(report.contains("CONCIENCIA SITUACIONAL"),
                "Debe contener encabezado");
            assertTrue(report.contains("Proyecto Detectado: Python"),
                "Debe indicar tipo de proyecto");
            assertTrue(report.contains("ESTRUCTURA DE ARCHIVOS"),
                "Debe tener seccion de estructura");
            assertTrue(report.contains("bowling.py"),
                "Debe listar archivos del proyecto");
        }

        @Test
        @DisplayName("Debe mostrar cantidad de archivos")
        void shouldShowFileCount() throws IOException {
            Files.createFile(tempDir.resolve("file1.py"));
            Files.createFile(tempDir.resolve("file2.py"));
            Files.createFile(tempDir.resolve("file3.py"));

            String report = insight.scanAndReport(tempDir);

            assertTrue(report.contains("Archivos encontrados: 3"),
                "Debe mostrar cantidad correcta de archivos");
        }

        @Test
        @DisplayName("Debe mostrar rutas relativas en el arbol")
        void shouldShowRelativePaths() throws IOException {
            Files.createDirectories(tempDir.resolve("src"));
            Files.createFile(tempDir.resolve("src/main.py"));

            String report = insight.scanAndReport(tempDir);

            assertTrue(report.contains("src/main.py") || report.contains("src\\main.py"),
                "Debe mostrar ruta relativa");
        }

        @Test
        @DisplayName("ScanResult debe indicar exito correctamente")
        void scanResultShouldIndicateSuccess() throws IOException {
            Files.createFile(tempDir.resolve("main.py"));

            WorkspaceInsight.ScanResult result = insight.scan(tempDir);

            assertTrue(result.isSuccess(), "Debe indicar exito");
            assertNull(result.error(), "Error debe ser null");
            assertEquals(1, result.fileCount(), "Debe tener 1 archivo");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Debe manejar directorio vacio")
        void shouldHandleEmptyDirectory() {
            String report = insight.scanAndReport(tempDir);

            assertTrue(report.contains("Proyecto Detectado: Generico"),
                "Debe detectar como generico");
            assertTrue(report.contains("Archivos encontrados: 0"),
                "Debe mostrar 0 archivos");
        }

        @Test
        @DisplayName("Debe lanzar NullPointerException para path null")
        void shouldThrowForNullPath() {
            assertThrows(NullPointerException.class, () -> {
                insight.scanAndReport(null);
            });
        }

        @Test
        @DisplayName("Debe manejar directorio inexistente")
        void shouldHandleNonExistentDirectory() {
            Path nonExistent = tempDir.resolve("does-not-exist");

            WorkspaceInsight.ScanResult result = insight.scan(nonExistent);

            assertEquals(WorkspaceInsight.ProjectType.UNKNOWN, result.projectType());
            assertEquals(0, result.fileCount());
        }

        @Test
        @DisplayName("Debe manejar estructura profunda")
        void shouldHandleDeepStructure() throws IOException {
            Path deep = tempDir;
            for (int i = 0; i < 5; i++) {
                deep = deep.resolve("level" + i);
            }
            Files.createDirectories(deep);
            Files.createFile(deep.resolve("main.py"));

            WorkspaceInsight.ScanResult result = insight.scan(tempDir);

            assertTrue(result.fileCount() >= 1,
                "Debe encontrar archivo en estructura profunda");
        }
    }

    @Nested
    @DisplayName("ProjectType Label Tests")
    class ProjectTypeLabelTests {
        @Test
        @DisplayName("Todos los ProjectTypes deben tener labels")
        void allProjectTypesShouldHaveLabels() {
            for (WorkspaceInsight.ProjectType type : WorkspaceInsight.ProjectType.values()) {
                assertNotNull(type.getLabel(),
                    "ProjectType " + type.name() + " debe tener label");
                assertFalse(type.getLabel().isEmpty(),
                    "ProjectType " + type.name() + " label no debe ser vacio");
            }
        }

        @Test
        @DisplayName("Labels deben ser legibles")
        void labelsShouldBeReadable() {
            assertEquals("Python", WorkspaceInsight.ProjectType.PYTHON.getLabel());
            assertEquals("Java (Maven)", WorkspaceInsight.ProjectType.JAVA_MAVEN.getLabel());
            assertEquals("Java (Gradle)", WorkspaceInsight.ProjectType.JAVA_GRADLE.getLabel());
            assertEquals("Angular", WorkspaceInsight.ProjectType.ANGULAR.getLabel());
            assertEquals("Node.js", WorkspaceInsight.ProjectType.NODE_JS.getLabel());
            assertEquals("Go", WorkspaceInsight.ProjectType.GO.getLabel());
            assertEquals("Rust", WorkspaceInsight.ProjectType.RUST.getLabel());
        }
    }

    @Nested
    @DisplayName("Monorepo Detection Tests (Vulnerability 4 Fix)")
    class MonorepoDetectionTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Debe detectar multiples tipos en monorepo Java+Angular")
        void shouldDetectJavaAngularMonorepo() throws IOException {
            Files.createFile(tempDir.resolve("pom.xml"));
            Files.createFile(tempDir.resolve("angular.json"));
            Files.createFile(tempDir.resolve("package.json"));

            String identity = insight.detectProjectIdentity(tempDir);

            assertTrue(identity.contains("Java (Maven)"),
                "Debe detectar Java Maven");
            assertTrue(identity.contains("Angular"),
                "Debe detectar Angular");
            assertTrue(identity.contains("+"),
                "Debe combinar con +");
        }

        @Test
        @DisplayName("Debe detectar multiples tipos en monorepo Python+Node")
        void shouldDetectPythonNodeMonorepo() throws IOException {
            Files.createFile(tempDir.resolve("requirements.txt"));
            Files.createFile(tempDir.resolve("package.json"));
            Files.createFile(tempDir.resolve("main.py"));
            Files.createFile(tempDir.resolve("index.js"));

            var types = insight.detectAllTypes(tempDir);

            assertTrue(types.contains(WorkspaceInsight.ProjectType.PYTHON),
                "Debe detectar Python");
            assertTrue(types.contains(WorkspaceInsight.ProjectType.NODE_JS),
                "Debe detectar Node.js");
        }

        @Test
        @DisplayName("Debe retornar EnumSet con todos los tipos presentes")
        void shouldReturnEnumSetWithAllTypes() throws IOException {
            Files.createFile(tempDir.resolve("go.mod"));
            Files.createFile(tempDir.resolve("Cargo.toml"));

            var types = insight.detectAllTypes(tempDir);

            assertEquals(2, types.size(), "Debe detectar exactamente 2 tipos");
            assertTrue(types.contains(WorkspaceInsight.ProjectType.GO));
            assertTrue(types.contains(WorkspaceInsight.ProjectType.RUST));
        }

        @Test
        @DisplayName("detectProjectIdentity debe retornar cadena formateada")
        void identityShouldReturnFormattedString() throws IOException {
            Files.createFile(tempDir.resolve("pom.xml"));

            String identity = insight.detectProjectIdentity(tempDir);

            assertEquals("Java (Maven)", identity);
        }
    }

    @Nested
    @DisplayName("Token Budgeting Tests (Vulnerability 3 Fix)")
    class TokenBudgetingTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Debe generar resumen estrategico para proyectos masivos")
        void shouldGenerateStrategicSummaryForMassiveProjects() throws IOException {
            Files.createFile(tempDir.resolve("pom.xml"));
            Files.createDirectories(tempDir.resolve("src/main/java"));
            Files.createDirectories(tempDir.resolve("src/test/java"));

            for (int i = 0; i < 350; i++) {
                String dir = i % 2 == 0 ? "src/main/java" : "src/test/java";
                Files.createFile(tempDir.resolve(dir + "/File" + i + ".java"));
            }

            String report = insight.scanAndReport(tempDir);

            assertTrue(report.contains("ESTRUCTURA MASIVA"),
                "Debe indicar estructura masiva");
            assertTrue(report.contains("Resumen estrategico"),
                "Debe mostrar resumen estrategico");
            assertTrue(report.contains("ARCHIVOS DE CONFIGURACION"),
                "Debe mostrar seccion de configuracion");
            assertTrue(report.contains("DIRECTORIOS PRINCIPALES"),
                "Debe mostrar seccion de directorios");
        }

        @Test
        @DisplayName("Proyectos pequenos deben mostrar arbol completo")
        void smallProjectsShouldShowFullTree() throws IOException {
            Files.createFile(tempDir.resolve("main.py"));
            Files.createFile(tempDir.resolve("utils.py"));
            Files.createFile(tempDir.resolve("test_main.py"));

            String report = insight.scanAndReport(tempDir);

            assertFalse(report.contains("ESTRUCTURA MASIVA"),
                "No debe ser estructura masiva");
            assertTrue(report.contains("- main.py"),
                "Debe mostrar archivos individuales");
        }
    }

    @Nested
    @DisplayName("Robust I/O Tests (Vulnerability 1 Fix)")
    class RobustIOTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("getLastScanWarnings debe retornar lista de warnings")
        void shouldTrackIOWarnings() throws IOException {
            Files.createFile(tempDir.resolve("main.py"));

            insight.scan(tempDir);
            var warnings = insight.getLastScanWarnings();

            assertNotNull(warnings, "Warnings no debe ser null");
            assertTrue(warnings.isEmpty(),
                "No debe haber warnings en escaneo normal");
        }

        @Test
        @DisplayName("Debe continuar escaneo aunque haya directorios ignorados")
        void shouldContinueScanningWithIgnoredDirs() throws IOException {
            Files.createDirectories(tempDir.resolve("node_modules/pkg"));
            Files.createFile(tempDir.resolve("node_modules/pkg/index.js"));
            Files.createDirectories(tempDir.resolve("src"));
            Files.createFile(tempDir.resolve("src/main.js"));
            Files.createFile(tempDir.resolve("package.json"));

            var result = insight.scan(tempDir);

            assertTrue(result.isSuccess());
            assertEquals(2, result.fileCount());
        }

        @Test
        @DisplayName("Debe ignorar directorios ocultos excepto configuracion especifica")
        void shouldIgnoreHiddenDirectories() throws IOException {
            Files.createDirectories(tempDir.resolve(".hidden"));
            Files.createFile(tempDir.resolve(".hidden/secret.txt"));
            Files.createFile(tempDir.resolve("visible.txt"));

            var result = insight.scan(tempDir);

            assertEquals(1, result.fileCount(),
                "Solo debe encontrar visible.txt");
        }
    }
}
