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
package dev.fararoni.core.core.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("RequestSanitizer - Aislamiento de Intención")
class RequestSanitizerTest {
    @Nested
    @DisplayName("extractSolicitud() - Casos Básicos")
    class ExtractSolicitudBasicTests {
        @Test
        @DisplayName("Debe extraer solicitud antes del separador ':'")
        void shouldExtractBeforeColon() {
            String prompt = "agrega atributo fechaTermino: /path/to/File.java";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertEquals("agrega atributo fechatermino", result);
        }

        @Test
        @DisplayName("Debe convertir a minúsculas")
        void shouldConvertToLowercase() {
            String prompt = "AGREGA ATRIBUTO: /path/File.java";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertEquals("agrega atributo", result);
        }

        @Test
        @DisplayName("Debe normalizar espacios múltiples")
        void shouldNormalizeMultipleSpaces() {
            String prompt = "agrega   atributo    nuevo: /path/File.java";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertEquals("agrega atributo nuevo", result);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Debe retornar vacío para inputs nulos/vacíos/blancos")
        void shouldReturnEmptyForNullOrBlank(String input) {
            String result = RequestSanitizer.extractSolicitud(input);
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("extractSolicitud() - Protocolos y Paths")
    class ExtractSolicitudProtocolTests {
        @Test
        @DisplayName("No debe confundir ':' de URL con separador")
        void shouldNotConfuseUrlColonWithSeparator() {
            String prompt = "descarga archivo de http://example.com/file.txt";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertTrue(result.contains("descarga"),
                "Debe contener la solicitud principal");
        }

        @Test
        @DisplayName("No debe confundir ':' de protocolo s3:// con separador")
        void shouldNotConfuseS3ColonWithSeparator() {
            String prompt = "lee archivo: s3://bucket/file.txt";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertEquals("lee archivo", result);
        }

        @Test
        @DisplayName("No debe confundir ':' de unidad Windows con separador")
        void shouldNotConfuseWindowsDriveWithSeparator() {
            String prompt = "lee el archivo: C:\\Users\\file.java";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertEquals("lee el archivo", result);
        }

        @Test
        @DisplayName("Debe manejar protocolo file://")
        void shouldHandleFileProtocol() {
            String prompt = "abre: file:///home/user/file.txt";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertEquals("abre", result);
        }
    }

    @Nested
    @DisplayName("extractSolicitud() - Múltiples Líneas")
    class ExtractSolicitudMultilineTests {
        @Test
        @DisplayName("Debe tomar solo la primera línea")
        void shouldTakeOnlyFirstLine() {
            String prompt = "agrega atributo\nesta es otra línea\ny otra más";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertEquals("agrega atributo", result);
        }

        @Test
        @DisplayName("Debe manejar saltos de línea Windows (CRLF)")
        void shouldHandleWindowsLineEndings() {
            String prompt = "agrega método\r\notra línea";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertEquals("agrega método", result);
        }

        @Test
        @DisplayName("Debe manejar primera línea vacía")
        void shouldHandleEmptyFirstLine() {
            String prompt = "\nagrega atributo";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("extractSolicitud() - Sin Separador")
    class ExtractSolicitudNoSeparatorTests {
        @Test
        @DisplayName("Debe retornar todo si no hay separador ni path")
        void shouldReturnAllIfNoSeparatorOrPath() {
            String prompt = "explica este código";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertEquals("explica este código", result);
        }

        @Test
        @DisplayName("Debe cortar antes del path /")
        void shouldCutBeforeSlashPath() {
            String prompt = "modifica el archivo /src/main/File.java";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertTrue(result.contains("modifica"),
                "Debe contener la solicitud antes del path");
            assertFalse(result.contains("/src"),
                "No debe contener el path");
        }

        @Test
        @DisplayName("Debe cortar antes del path \\ (Windows)")
        void shouldCutBeforeBackslashPath() {
            String prompt = "lee archivo \\Users\\file.java";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertTrue(result.contains("lee"),
                "Debe contener la solicitud");
        }
    }

    @Nested
    @DisplayName("extractSolicitud() - Límite de Longitud")
    class ExtractSolicitudLengthTests {
        @Test
        @DisplayName("Debe limitar a 200 caracteres máximo")
        void shouldLimitTo200Characters() {
            String longPrompt = "a".repeat(300) + ": /path/file.java";
            String result = RequestSanitizer.extractSolicitud(longPrompt);

            assertTrue(result.length() <= 200,
                "Resultado debe ser <= 200 caracteres");
        }
    }

    @Nested
    @DisplayName("extractPath()")
    class ExtractPathTests {
        @Test
        @DisplayName("Debe extraer path Unix después del separador")
        void shouldExtractUnixPath() {
            String prompt = "agrega: /src/main/java/File.java";
            String result = RequestSanitizer.extractPath(prompt);

            assertEquals("/src/main/java/File.java", result);
        }

        @Test
        @DisplayName("Debe extraer path Windows después del separador")
        void shouldExtractWindowsPath() {
            String prompt = "lee: C:\\Users\\file.java";
            String result = RequestSanitizer.extractPath(prompt);

            assertEquals("C:\\Users\\file.java", result);
        }

        @Test
        @DisplayName("Debe retornar vacío si no hay path")
        void shouldReturnEmptyIfNoPath() {
            String prompt = "explica este código";
            String result = RequestSanitizer.extractPath(prompt);

            assertEquals("", result);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Debe retornar vacío para inputs nulos/vacíos")
        void shouldReturnEmptyForNullOrEmpty(String input) {
            String result = RequestSanitizer.extractPath(input);
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("containsPath()")
    class ContainsPathTests {
        @ParameterizedTest
        @ValueSource(strings = {
            "/path/to/file",
            "C:\\Users\\file",
            "archivo.java",
            "script.py",
            "component.tsx",
            "module.js"
        })
        @DisplayName("Debe detectar paths y extensiones de archivo")
        void shouldDetectPathsAndExtensions(String input) {
            assertTrue(RequestSanitizer.containsPath(input),
                "Debe detectar path en: " + input);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "hola como estas",
            "explica este concepto",
            "que es java"
        })
        @DisplayName("Debe retornar false para texto sin paths")
        void shouldReturnFalseForTextWithoutPaths(String input) {
            assertFalse(RequestSanitizer.containsPath(input),
                "No debe detectar path en: " + input);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Debe retornar false para inputs nulos/vacíos")
        void shouldReturnFalseForNullOrEmpty(String input) {
            assertFalse(RequestSanitizer.containsPath(input));
        }
    }

    @Nested
    @DisplayName("Casos Reales del Bug Original")
    class RealBugCasesTests {
        @Test
        @DisplayName("Bug Original: prompt largo con path debe extraer solo solicitud")
        void originalBugCase() {
            String prompt = "agrega atributo fechaTermino: servicio-gestion-alumnos/src/main/java/com/microservicio/gestionalumnos/model/CreditoBancario.java";

            String solicitud = RequestSanitizer.extractSolicitud(prompt);

            assertTrue(solicitud.length() < 50,
                "Solicitud debe ser mucho más corta que el prompt original");
            assertTrue(solicitud.contains("agrega"),
                "Debe contener la intención principal");
            assertTrue(solicitud.contains("atributo"),
                "Debe contener el tipo de operación");
            assertFalse(solicitud.contains("servicio-gestion"),
                "No debe contener el nombre del proyecto");
        }

        @ParameterizedTest
        @CsvSource({
            "'agrega atributo: /path/File.java', 'agrega atributo'",
            "'lee archivo: C:\\Users\\file.java', 'lee archivo'",
            "'modifica método: src/main/App.java', 'modifica método'",
            "'start_mission: crear microservicio', 'start_mission'"
        })
        @DisplayName("Casos de routing frecuentes")
        void frequentRoutingCases(String input, String expected) {
            String result = RequestSanitizer.extractSolicitud(input);
            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("Seguridad y Edge Cases")
    class SecurityAndEdgeCasesTests {
        @Test
        @DisplayName("Debe manejar caracteres de control")
        void shouldHandleControlCharacters() {
            String prompt = "agrega\u0000atributo: /path/file.java";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertFalse(result.contains("\u0000"),
                "No debe contener caracteres de control");
        }

        @Test
        @DisplayName("Debe manejar caracteres Unicode")
        void shouldHandleUnicodeCharacters() {
            String prompt = "añade método: /path/Archivo.java";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertTrue(result.contains("añade") || result.contains("anade"),
                "Debe manejar caracteres acentuados");
        }

        @Test
        @DisplayName("Debe manejar múltiples ':' en el prompt")
        void shouldHandleMultipleColons() {
            String prompt = "configura: key1:value1 key2:value2";
            String result = RequestSanitizer.extractSolicitud(prompt);

            assertEquals("configura", result);
        }
    }
}
