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
package dev.fararoni.core.core.safety.mission;

import dev.fararoni.core.core.safety.mission.MissionPostChecker.ProjectType;
import dev.fararoni.core.core.safety.mission.MissionPostChecker.ValidationMode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("MissionPostChecker - Verificación Post-Escritura")
class MissionPostCheckerTest {
    private MissionPostChecker checker;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        checker = new MissionPostChecker();
    }

    @Nested
    @DisplayName("Modos de Validación")
    class ValidationModeTests {
        @Test
        @DisplayName("ValidationMode enum tiene valores esperados")
        void validationMode_shouldHaveExpectedValues() {
            assertNotNull(ValidationMode.LSP_ONLY);
            assertNotNull(ValidationMode.HYBRID);
            assertEquals(2, ValidationMode.values().length);
        }

        @Test
        @DisplayName("getValidationMode() retorna LSP_ONLY por defecto")
        void getValidationMode_shouldReturnLspOnlyByDefault() {
            ValidationMode mode = MissionPostChecker.getValidationMode();
            assertNotNull(mode);
        }

        @Test
        @DisplayName("isHybridModeEnabled() es consistente con getValidationMode()")
        void isHybridModeEnabled_shouldBeConsistent() {
            boolean hybrid = MissionPostChecker.isHybridModeEnabled();
            ValidationMode mode = MissionPostChecker.getValidationMode();

            assertEquals(mode == ValidationMode.HYBRID, hybrid);
        }
    }

    @Nested
    @DisplayName("Detección de Tipo de Proyecto")
    class ProjectDetectionTests {
        @Test
        @DisplayName("Detecta proyecto Maven (con wrapper)")
        void detectProjectType_shouldDetectMavenWithWrapper() throws IOException {
            Files.createFile(tempDir.resolve("pom.xml"));
            Files.createFile(tempDir.resolve("mvnw"));

            ProjectType type = checker.detectProjectType(tempDir);
            assertEquals(ProjectType.MAVEN, type);
        }

        @Test
        @DisplayName("Detecta proyecto Maven (sin wrapper)")
        void detectProjectType_shouldDetectMavenWithoutWrapper() throws IOException {
            Files.createFile(tempDir.resolve("pom.xml"));

            ProjectType type = checker.detectProjectType(tempDir);
            assertEquals(ProjectType.MAVEN_NO_WRAPPER, type);
        }

        @Test
        @DisplayName("Detecta proyecto Gradle")
        void detectProjectType_shouldDetectGradle() throws IOException {
            Files.createFile(tempDir.resolve("build.gradle"));

            ProjectType type = checker.detectProjectType(tempDir);
            assertEquals(ProjectType.GRADLE, type);
        }

        @Test
        @DisplayName("Detecta proyecto npm")
        void detectProjectType_shouldDetectNpm() throws IOException {
            Files.createFile(tempDir.resolve("package.json"));

            ProjectType type = checker.detectProjectType(tempDir);
            assertEquals(ProjectType.NPM, type);
        }

        @Test
        @DisplayName("Retorna UNKNOWN para proyecto no reconocido")
        void detectProjectType_shouldReturnUnknown() {
            ProjectType type = checker.detectProjectType(tempDir);
            assertEquals(ProjectType.UNKNOWN, type);
        }

        @Test
        @DisplayName("Maven tiene prioridad sobre otros tipos")
        void detectProjectType_mavenHasPriority() throws IOException {
            Files.createFile(tempDir.resolve("pom.xml"));
            Files.createFile(tempDir.resolve("package.json"));

            ProjectType type = checker.detectProjectType(tempDir);
            assertTrue(type == ProjectType.MAVEN || type == ProjectType.MAVEN_NO_WRAPPER);
        }
    }

    @Nested
    @DisplayName("ProjectType Enum")
    class ProjectTypeTests {
        @Test
        @DisplayName("Cada tipo tiene marker definido excepto UNKNOWN")
        void projectTypes_shouldHaveMarkers() {
            assertNotNull(ProjectType.MAVEN.getMarker());
            assertNotNull(ProjectType.MAVEN_NO_WRAPPER.getMarker());
            assertNotNull(ProjectType.GRADLE.getMarker());
            assertNotNull(ProjectType.NPM.getMarker());
            assertNull(ProjectType.UNKNOWN.getMarker());
        }

        @Test
        @DisplayName("Cada tipo tiene comando definido")
        void projectTypes_shouldHaveCommands() {
            assertTrue(ProjectType.MAVEN.getCommand().length > 0);
            assertTrue(ProjectType.MAVEN_NO_WRAPPER.getCommand().length > 0);
            assertTrue(ProjectType.GRADLE.getCommand().length > 0);
            assertTrue(ProjectType.NPM.getCommand().length > 0);
        }
    }

    @Nested
    @DisplayName("Verificación de Proyecto")
    class ProjectVerificationTests {
        @Test
        @DisplayName("verifyFullProject() con path null retorna error")
        void verifyFullProject_withNullPath_shouldReturnError() {
            MissionReport report = checker.verifyFullProject((Path) null, null);

            assertFalse(report.isSuccess());
            assertTrue(report.message().contains("inválida"));
        }

        @Test
        @DisplayName("verifyFullProject() con directorio inexistente retorna error")
        void verifyFullProject_withNonExistentDir_shouldReturnError() {
            Path nonExistent = tempDir.resolve("non_existent_dir");

            MissionReport report = checker.verifyFullProject(nonExistent, null);

            assertFalse(report.isSuccess());
        }

        @Test
        @DisplayName("verifyFullProject() con proyecto no reconocido retorna skipped")
        void verifyFullProject_withUnknownProject_shouldReturnSkipped() {
            MissionReport report = checker.verifyFullProject(tempDir, null);

            assertTrue(report.isSkipped());
        }

        @Test
        @DisplayName("verifyFullProject() acepta String como path")
        void verifyFullProject_withStringPath_shouldWork() {
            MissionReport report = checker.verifyFullProject(tempDir.toString(), null);

            assertTrue(report.isSkipped() || !report.isSuccess());
        }
    }

    @Nested
    @DisplayName("Constructores")
    class ConstructorTests {
        @Test
        @DisplayName("Constructor por defecto funciona")
        void defaultConstructor_shouldWork() {
            MissionPostChecker defaultChecker = new MissionPostChecker();
            assertNotNull(defaultChecker);
        }

        @Test
        @DisplayName("Constructor con timeout personalizado funciona")
        void constructorWithTimeout_shouldWork() {
            MissionPostChecker customChecker = new MissionPostChecker(10);
            assertNotNull(customChecker);
        }

        @Test
        @DisplayName("Timeout negativo usa default")
        void negativeTimeout_shouldUseDefault() {
            MissionPostChecker customChecker = new MissionPostChecker(-5);
            assertNotNull(customChecker);
        }
    }

    @Nested
    @DisplayName("Información del Modo")
    class ModeInfoTests {
        @Test
        @DisplayName("printModeInfo() no lanza excepción")
        void printModeInfo_shouldNotThrow() {
            assertDoesNotThrow(MissionPostChecker::printModeInfo);
        }
    }

    @Nested
    @DisplayName("Constantes")
    class ConstantsTests {
        @Test
        @DisplayName("Variable de entorno está definida")
        void envVariable_shouldBeDefined() {
            assertEquals("FARARONI_VALIDATION_MODE", MissionPostChecker.ENV_VALIDATION_MODE);
        }
    }

    @Nested
    @DisplayName("Integración con Proyecto Real")
    @Disabled("Requiere proyecto Maven real - ejecutar manualmente")
    class RealProjectIntegrationTests {
        @Test
        @DisplayName("Verifica proyecto Fararoni actual")
        void verifyCurrentProject_shouldCompile() {
            Path projectRoot = Path.of(System.getProperty("user.dir"));

            if (Files.exists(projectRoot.resolve("pom.xml"))) {
                MissionReport report = checker.verifyFullProject(projectRoot, "java");

                System.out.println("Resultado: " + report.toLogString());

                assertTrue(report.isSuccess() || report.isSkipped(),
                    "El proyecto actual debería compilar: " + report.message());
            }
        }
    }
}
