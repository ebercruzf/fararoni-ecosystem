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
package dev.fararoni.core.core.mission.engine;

import dev.fararoni.core.core.mission.engine.SovereignMissionEngine.FileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("FASE 61 - Inferencia Fragmentada (Streaming Mode)")
class SovereignMissionEngineFase61Test {
    private SovereignMissionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SovereignMissionEngine(null, null, null, null);
    }

    @Nested
    @DisplayName("FileRequest Record")
    class FileRequestTests {
        @Test
        @DisplayName("Crea FileRequest con todos los campos")
        void shouldCreateWithAllFields() {
            FileRequest req = new FileRequest(
                "gestor-alumnos/src/main/java/Alumno.java",
                "entity",
                "Domain entity for student"
            );

            assertEquals("gestor-alumnos/src/main/java/Alumno.java", req.path());
            assertEquals("entity", req.type());
            assertEquals("Domain entity for student", req.description());
        }

        @Test
        @DisplayName("fromMap() parsea Map correctamente")
        void fromMap_shouldParseMapCorrectly() {
            Map<String, Object> map = Map.of(
                "path", "proyecto/pom.xml",
                "type", "config",
                "description", "Maven configuration"
            );

            FileRequest req = FileRequest.fromMap(map);

            assertEquals("proyecto/pom.xml", req.path());
            assertEquals("config", req.type());
            assertEquals("Maven configuration", req.description());
        }

        @Test
        @DisplayName("fromMap() usa defaults para campos faltantes")
        void fromMap_shouldUseDefaultsForMissingFields() {
            Map<String, Object> map = Map.of("path", "src/Main.java");

            FileRequest req = FileRequest.fromMap(map);

            assertEquals("src/Main.java", req.path());
            assertEquals("unknown", req.type());
            assertEquals("", req.description());
        }

        @Test
        @DisplayName("fromMap() maneja Map vacío")
        void fromMap_shouldHandleEmptyMap() {
            Map<String, Object> map = Map.of();

            FileRequest req = FileRequest.fromMap(map);

            assertEquals("", req.path());
            assertEquals("unknown", req.type());
            assertEquals("", req.description());
        }

        @Test
        @DisplayName("equals() funciona correctamente")
        void equals_shouldWork() {
            FileRequest r1 = new FileRequest("path", "type", "desc");
            FileRequest r2 = new FileRequest("path", "type", "desc");
            FileRequest r3 = new FileRequest("other", "type", "desc");

            assertEquals(r1, r2);
            assertNotEquals(r1, r3);
        }
    }

    @Nested
    @DisplayName("parseManifest()")
    class ParseManifestTests {
        @Test
        @DisplayName("Parsea manifiesto JSON válido")
        void shouldParseValidManifest() {
            String json = """
                {
                  "projectName": "gestor-alumnos",
                  "language": "java",
                  "basePackage": "com.gestion.alumnos",
                  "files": [
                    {"path": "gestor-alumnos/pom.xml", "type": "config", "description": "Maven config"},
                    {"path": "gestor-alumnos/src/main/java/Alumno.java", "type": "entity", "description": "Student entity"}
                  ]
                }
                """;

            List<FileRequest> result = engine.parseManifest(json);

            assertEquals(2, result.size());
            assertEquals("gestor-alumnos/pom.xml", result.get(0).path());
            assertEquals("config", result.get(0).type());
            assertEquals("gestor-alumnos/src/main/java/Alumno.java", result.get(1).path());
            assertEquals("entity", result.get(1).type());
        }

        @Test
        @DisplayName("Parsea manifiesto con markdown wrapping")
        void shouldParseManifestWithMarkdownWrapping() {
            String json = """
                ```json
                {
                  "projectName": "test",
                  "files": [
                    {"path": "src/Main.java", "type": "main"}
                  ]
                }
                ```
                """;

            List<FileRequest> result = engine.parseManifest(json);

            assertEquals(1, result.size());
            assertEquals("src/Main.java", result.get(0).path());
        }

        @Test
        @DisplayName("Retorna lista vacía para JSON null")
        void shouldReturnEmptyForNull() {
            List<FileRequest> result = engine.parseManifest(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Retorna lista vacía para JSON vacío")
        void shouldReturnEmptyForBlank() {
            List<FileRequest> result = engine.parseManifest("   ");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Retorna lista vacía para JSON inválido")
        void shouldReturnEmptyForInvalidJson() {
            List<FileRequest> result = engine.parseManifest("not a json");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Retorna lista vacía si no hay campo 'files'")
        void shouldReturnEmptyIfNoFilesField() {
            String json = """
                {
                  "projectName": "test"
                }
                """;

            List<FileRequest> result = engine.parseManifest(json);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Filtra entradas con path vacío")
        void shouldFilterEmptyPaths() {
            String json = """
                {
                  "files": [
                    {"path": "valid/path.java", "type": "entity"},
                    {"path": "", "type": "invalid"},
                    {"path": "another/path.java", "type": "service"}
                  ]
                }
                """;

            List<FileRequest> result = engine.parseManifest(json);

            assertEquals(2, result.size());
            assertEquals("valid/path.java", result.get(0).path());
            assertEquals("another/path.java", result.get(1).path());
        }

        @Test
        @DisplayName("Maneja manifiesto grande con múltiples archivos")
        void shouldHandleLargeManifest() {
            StringBuilder json = new StringBuilder();
            json.append("{\"files\": [");
            for (int i = 0; i < 20; i++) {
                if (i > 0) json.append(",");
                json.append(String.format(
                    "{\"path\": \"src/File%d.java\", \"type\": \"class\", \"description\": \"Class %d\"}",
                    i, i
                ));
            }
            json.append("]}");

            List<FileRequest> result = engine.parseManifest(json.toString());

            assertEquals(20, result.size());
            assertEquals("src/File0.java", result.get(0).path());
            assertEquals("src/File19.java", result.get(19).path());
        }

        @Test
        @DisplayName("Ignora campos extra en el JSON")
        void shouldIgnoreExtraFields() {
            String json = """
                {
                  "projectName": "test",
                  "extraField": "ignored",
                  "nested": {"also": "ignored"},
                  "files": [
                    {"path": "src/Main.java", "type": "main", "extra": "ignored"}
                  ]
                }
                """;

            List<FileRequest> result = engine.parseManifest(json);

            assertEquals(1, result.size());
            assertEquals("src/Main.java", result.get(0).path());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {
        @Test
        @DisplayName("Maneja caracteres especiales en rutas")
        void shouldHandleSpecialCharsInPaths() {
            String json = """
                {
                  "files": [
                    {"path": "src/com/example/MiClase.java", "type": "class"},
                    {"path": "src/resources/mensajes_es.properties", "type": "resource"}
                  ]
                }
                """;

            List<FileRequest> result = engine.parseManifest(json);

            assertEquals(2, result.size());
            assertTrue(result.get(1).path().contains("_es"));
        }

        @Test
        @DisplayName("Maneja rutas con espacios (aunque no recomendado)")
        void shouldHandlePathsWithSpaces() {
            String json = """
                {
                  "files": [
                    {"path": "src/My Class.java", "type": "class"}
                  ]
                }
                """;

            List<FileRequest> result = engine.parseManifest(json);

            assertEquals(1, result.size());
            assertEquals("src/My Class.java", result.get(0).path());
        }

        @Test
        @DisplayName("Preserva orden de archivos del manifiesto")
        void shouldPreserveFileOrder() {
            String json = """
                {
                  "files": [
                    {"path": "a.java", "type": "a"},
                    {"path": "b.java", "type": "b"},
                    {"path": "c.java", "type": "c"}
                  ]
                }
                """;

            List<FileRequest> result = engine.parseManifest(json);

            assertEquals("a.java", result.get(0).path());
            assertEquals("b.java", result.get(1).path());
            assertEquals("c.java", result.get(2).path());
        }
    }

    @Nested
    @DisplayName("Compatibilidad Dual-Mode (BATCH vs STREAMING)")
    class DualModeCompatibilityTests {
        @Test
        @DisplayName("parseManifest no interfiere con formato >>>FILE:")
        void parseManifest_shouldNotInterfereWithBatchFormat() {
            String batchFormat = """
                >>>FILE: src/Main.java
                public class Main {}
                """;

            List<FileRequest> result = engine.parseManifest(batchFormat);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Manifiesto sin streamingConfig usa valores default")
        void manifestWithoutConfig_shouldUseDefaults() {
            String json = """
                {
                  "projectName": "legacy-project",
                  "files": [
                    {"path": "src/App.java"}
                  ]
                }
                """;

            List<FileRequest> result = engine.parseManifest(json);

            assertEquals(1, result.size());
            assertEquals("unknown", result.get(0).type());
            assertEquals("", result.get(0).description());
        }
    }
}
