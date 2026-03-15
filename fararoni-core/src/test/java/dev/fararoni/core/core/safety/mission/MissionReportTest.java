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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("MissionReport - Resultados de Compilación Post-Misión")
class MissionReportTest {
    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {
        @Test
        @DisplayName("success() crea reporte exitoso")
        void success_shouldCreateSuccessReport() {
            MissionReport report = MissionReport.success("maven", 5000L);

            assertTrue(report.isSuccess());
            assertEquals("maven", report.techStack());
            assertEquals(5000L, report.durationMs());
            assertEquals("", report.buildOutput());
            assertFalse(report.isSkipped());
        }

        @Test
        @DisplayName("failure() crea reporte fallido con errores")
        void failure_shouldCreateFailureReport() {
            String errorLog = "[ERROR] UserService.java:[15,5] cannot find symbol";
            MissionReport report = MissionReport.failure(errorLog, "maven", 3000L);

            assertFalse(report.isSuccess());
            assertEquals("maven", report.techStack());
            assertEquals(errorLog, report.buildOutput());
            assertEquals(3000L, report.durationMs());
        }

        @Test
        @DisplayName("timeout() crea reporte de timeout")
        void timeout_shouldCreateTimeoutReport() {
            MissionReport report = MissionReport.timeout("gradle", 5);

            assertFalse(report.isSuccess());
            assertEquals("gradle", report.techStack());
            assertTrue(report.message().contains("5"));
            assertEquals("BUILD TIMEOUT", report.buildOutput());
        }

        @Test
        @DisplayName("skipped() crea reporte skipped")
        void skipped_shouldCreateSkippedReport() {
            MissionReport report = MissionReport.skipped();

            assertTrue(report.isSuccess());
            assertTrue(report.isSkipped());
            assertEquals("unknown", report.techStack());
        }

        @Test
        @DisplayName("infrastructureError() crea reporte de error de infra")
        void infrastructureError_shouldCreateInfraErrorReport() {
            MissionReport report = MissionReport.infrastructureError("Docker not available");

            assertFalse(report.isSuccess());
            assertTrue(report.message().contains("Docker not available"));
            assertEquals("unknown", report.techStack());
        }
    }

    @Nested
    @DisplayName("Métodos de Utilidad")
    class UtilityMethodTests {
        @Test
        @DisplayName("isSkipped() detecta reportes skipped")
        void isSkipped_shouldDetectSkippedReports() {
            assertTrue(MissionReport.skipped().isSkipped());
            assertFalse(MissionReport.success("maven", 1000L).isSkipped());
            assertFalse(MissionReport.failure("error", "maven", 500L).isSkipped());
        }

        @Test
        @DisplayName("toLogString() formatea éxito")
        void toLogString_shouldFormatSuccess() {
            MissionReport report = MissionReport.success("maven", 2500L);
            String log = report.toLogString();

            assertNotNull(log);
            assertTrue(log.contains("maven"));
            assertTrue(log.contains("2500"));
        }

        @Test
        @DisplayName("toLogString() formatea fallo")
        void toLogString_shouldFormatFailure() {
            MissionReport report = MissionReport.failure("compile error", "gradle", 1000L);
            String log = report.toLogString();

            assertNotNull(log);
            assertTrue(log.contains("gradle"));
        }

        @Test
        @DisplayName("getFirstLines() extrae primeras líneas")
        void getFirstLines_shouldExtractFirstLines() {
            String multiLine = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
            MissionReport report = MissionReport.failure(multiLine, "maven", 100L);

            String firstTwo = report.getFirstLines(2);
            assertTrue(firstTwo.contains("Line 1"));
            assertTrue(firstTwo.contains("Line 2"));
            assertFalse(firstTwo.contains("Line 3") && !firstTwo.contains("más"));
        }

        @Test
        @DisplayName("getFirstLines() maneja output vacío")
        void getFirstLines_shouldHandleEmptyOutput() {
            MissionReport report = MissionReport.success("maven", 100L);
            assertEquals("", report.getFirstLines(5));
        }

        @Test
        @DisplayName("getFirstLines() maneja output corto")
        void getFirstLines_shouldHandleShortOutput() {
            MissionReport report = MissionReport.failure("Single line", "maven", 100L);
            assertEquals("Single line", report.getFirstLines(10));
        }
    }

    @Nested
    @DisplayName("Comportamiento de Record")
    class RecordBehaviorTests {
        @Test
        @DisplayName("Campos son accesibles")
        void fields_shouldBeAccessible() {
            MissionReport report = new MissionReport(
                true,
                "Test message",
                "Build output",
                "maven",
                1500L
            );

            assertTrue(report.isSuccess());
            assertEquals("Test message", report.message());
            assertEquals("Build output", report.buildOutput());
            assertEquals("maven", report.techStack());
            assertEquals(1500L, report.durationMs());
        }

        @Test
        @DisplayName("equals() funciona correctamente")
        void equals_shouldWork() {
            MissionReport r1 = MissionReport.success("maven", 100L);
            MissionReport r2 = MissionReport.success("maven", 100L);
            MissionReport r3 = MissionReport.success("gradle", 100L);

            assertEquals(r1, r2);
            assertNotEquals(r1, r3);
        }
    }
}
