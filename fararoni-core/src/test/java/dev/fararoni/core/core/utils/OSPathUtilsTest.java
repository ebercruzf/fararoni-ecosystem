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
package dev.fararoni.core.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("OSPathUtils - Normalización de Paths Multiplataforma")
class OSPathUtilsTest {
    @Nested
    @DisplayName("Detección de Sistema Operativo")
    class OsDetectionTests {
        @Test
        @DisplayName("getOsType() retorna valor válido")
        void getOsType_shouldReturnValidValue() {
            String osType = OSPathUtils.getOsType();
            assertNotNull(osType);
            assertTrue(
                osType.equals("windows") || osType.equals("mac") ||
                osType.equals("linux") || osType.equals("unknown"),
                "OS type debe ser windows, mac, linux o unknown"
            );
        }

        @Test
        @DisplayName("Solo un método de OS retorna true")
        void onlyOneOsMethod_shouldReturnTrue() {
            int trueCount = 0;
            if (OSPathUtils.isWindows()) trueCount++;
            if (OSPathUtils.isMac()) trueCount++;
            if (OSPathUtils.isLinux()) trueCount++;

            assertTrue(trueCount <= 1, "Solo un método de OS debería retornar true");
        }
    }

    @Nested
    @DisplayName("Normalización de Paths")
    class NormalizationTests {
        @Test
        @DisplayName("normalize() convierte backslashes a forward slashes")
        void normalize_shouldConvertBackslashes() {
            String windowsPath = "C:\\Users\\code\\file.java";
            String normalized = OSPathUtils.normalize(windowsPath);
            assertEquals("C:/Users/code/file.java", normalized);
        }

        @Test
        @DisplayName("normalize() mantiene forward slashes")
        void normalize_shouldKeepForwardSlashes() {
            String unixPath = "/home/user/code/file.java";
            String normalized = OSPathUtils.normalize(unixPath);
            assertEquals("/home/user/code/file.java", normalized);
        }

        @Test
        @DisplayName("normalize() maneja null")
        void normalize_shouldHandleNull() {
            assertNull(OSPathUtils.normalize((String) null));
            assertNull(OSPathUtils.normalize((Path) null));
        }

        @Test
        @DisplayName("normalize() maneja paths mixtos")
        void normalize_shouldHandleMixedPaths() {
            String mixed = "C:\\Users/code\\src/file.java";
            String normalized = OSPathUtils.normalize(mixed);
            assertEquals("C:/Users/code/src/file.java", normalized);
        }

        @Test
        @DisplayName("toNativePath() convierte a separador nativo")
        void toNativePath_shouldConvertToNative() {
            String normalized = "C:/Users/code/file.java";
            String native_ = OSPathUtils.toNativePath(normalized);

            if (OSPathUtils.isWindows()) {
                assertEquals("C:\\Users\\code\\file.java", native_);
            } else {
                assertEquals(normalized, native_);
            }
        }
    }

    @Nested
    @DisplayName("Extracción de Paths Relativos")
    class RelativePathTests {
        @Test
        @DisplayName("getRelativeToSrc() extrae desde src/main/java")
        void getRelativeToSrc_shouldExtractFromMainJava() {
            String fullPath = "/home/user/project/src/main/java/com/example/User.java";
            String relative = OSPathUtils.getRelativeToSrc(fullPath);
            assertEquals("src/main/java/com/example/User.java", relative);
        }

        @Test
        @DisplayName("getRelativeToSrc() extrae desde src/test/java")
        void getRelativeToSrc_shouldExtractFromTestJava() {
            String fullPath = "/home/user/project/src/test/java/com/example/UserTest.java";
            String relative = OSPathUtils.getRelativeToSrc(fullPath);
            assertEquals("src/test/java/com/example/UserTest.java", relative);
        }

        @Test
        @DisplayName("getRelativeToSrc() retorna nombre si no encuentra src")
        void getRelativeToSrc_shouldReturnFilenameIfNoSrc() {
            String fullPath = "/tmp/random/file.java";
            String relative = OSPathUtils.getRelativeToSrc(fullPath);
            assertEquals("file.java", relative);
        }

        @Test
        @DisplayName("getFileName() extrae nombre del archivo")
        void getFileName_shouldExtractFilename() {
            assertEquals("User.java", OSPathUtils.getFileName("/home/code/User.java"));
            assertEquals("User.java", OSPathUtils.getFileName("C:/code/User.java"));
            assertEquals("User.java", OSPathUtils.getFileName("User.java"));
        }

        @Test
        @DisplayName("getExtension() extrae extensión")
        void getExtension_shouldExtractExtension() {
            assertEquals("java", OSPathUtils.getExtension("User.java"));
            assertEquals("ts", OSPathUtils.getExtension("/path/to/file.ts"));
            assertEquals("", OSPathUtils.getExtension("Makefile"));
            assertEquals("", OSPathUtils.getExtension(null));
        }
    }

    @Nested
    @DisplayName("Comparación de Paths")
    class ComparisonTests {
        @Test
        @DisplayName("pathEquals() compara paths normalizados")
        void pathEquals_shouldCompareNormalizedPaths() {
            assertTrue(OSPathUtils.pathEquals(
                "/home/user/file.java",
                "/home/user/file.java"
            ));

            assertTrue(OSPathUtils.pathEquals(
                "C:\\Users\\file.java",
                "C:/Users/file.java"
            ));
        }

        @Test
        @DisplayName("pathEquals() maneja nulls")
        void pathEquals_shouldHandleNulls() {
            assertTrue(OSPathUtils.pathEquals(null, null));
            assertFalse(OSPathUtils.pathEquals("/path", null));
            assertFalse(OSPathUtils.pathEquals(null, "/path"));
        }
    }

    @Nested
    @DisplayName("Comandos del Sistema")
    class SystemCommandTests {
        @Test
        @DisplayName("getMavenWrapper() retorna comando correcto")
        void getMavenWrapper_shouldReturnCorrectCommand() {
            String wrapper = OSPathUtils.getMavenWrapper();
            if (OSPathUtils.isWindows()) {
                assertEquals("mvnw.cmd", wrapper);
            } else {
                assertEquals("./mvnw", wrapper);
            }
        }

        @Test
        @DisplayName("getGradleWrapper() retorna comando correcto")
        void getGradleWrapper_shouldReturnCorrectCommand() {
            String wrapper = OSPathUtils.getGradleWrapper();
            if (OSPathUtils.isWindows()) {
                assertEquals("gradlew.bat", wrapper);
            } else {
                assertEquals("./gradlew", wrapper);
            }
        }

        @Test
        @DisplayName("getPythonCommand() retorna comando correcto")
        void getPythonCommand_shouldReturnCorrectCommand() {
            String python = OSPathUtils.getPythonCommand();
            if (OSPathUtils.isWindows()) {
                assertEquals("python", python);
            } else {
                assertEquals("python3", python);
            }
        }

        @Test
        @DisplayName("getTempDirectory() no es null")
        void getTempDirectory_shouldNotBeNull() {
            assertNotNull(OSPathUtils.getTempDirectory());
        }

        @Test
        @DisplayName("getUserHome() no es null")
        void getUserHome_shouldNotBeNull() {
            assertNotNull(OSPathUtils.getUserHome());
        }
    }

    @Nested
    @DisplayName("Validación de Paths")
    class ValidationTests {
        @Test
        @DisplayName("isValidLength() acepta paths cortos")
        void isValidLength_shouldAcceptShortPaths() {
            assertTrue(OSPathUtils.isValidLength("/home/user/file.java"));
            assertTrue(OSPathUtils.isValidLength(null));
        }

        @Test
        @DisplayName("isValidFileName() rechaza caracteres inválidos en Windows")
        void isValidFileName_shouldRejectInvalidCharsOnWindows() {
            if (OSPathUtils.isWindows()) {
                assertFalse(OSPathUtils.isValidFileName("file<name>.txt"));
                assertFalse(OSPathUtils.isValidFileName("file:name.txt"));
                assertFalse(OSPathUtils.isValidFileName("CON.txt"));
            }
        }

        @Test
        @DisplayName("isValidFileName() acepta nombres válidos")
        void isValidFileName_shouldAcceptValidNames() {
            assertTrue(OSPathUtils.isValidFileName("User.java"));
            assertTrue(OSPathUtils.isValidFileName("test-file_123.txt"));
            assertTrue(OSPathUtils.isValidFileName("README.md"));
        }

        @Test
        @DisplayName("isValidFileName() rechaza null y vacío")
        void isValidFileName_shouldRejectNullAndEmpty() {
            assertFalse(OSPathUtils.isValidFileName(null));
            assertFalse(OSPathUtils.isValidFileName(""));
            assertFalse(OSPathUtils.isValidFileName("   "));
        }
    }
}
