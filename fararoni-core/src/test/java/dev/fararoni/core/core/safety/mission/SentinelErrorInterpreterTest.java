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

import dev.fararoni.core.core.safety.mission.SentinelErrorInterpreter.CompilationError;
import dev.fararoni.core.core.safety.mission.SentinelErrorInterpreter.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SentinelErrorInterpreter - Diagnóstico de Errores de Compilación")
class SentinelErrorInterpreterTest {
    private SentinelErrorInterpreter interpreter;

    @BeforeEach
    void setUp() {
        interpreter = new SentinelErrorInterpreter();
    }

    @Nested
    @DisplayName("Parsing de Errores")
    class ParsingTests {
        @Test
        @DisplayName("parseErrors() maneja input null")
        void parseErrors_shouldHandleNull() {
            List<CompilationError> errors = interpreter.parseErrors(null);
            assertNotNull(errors);
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("parseErrors() maneja input vacío")
        void parseErrors_shouldHandleEmpty() {
            List<CompilationError> errors = interpreter.parseErrors("");
            assertNotNull(errors);
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("parseErrors() detecta errores de Maven")
        void parseErrors_shouldDetectMavenErrors() {
            String mavenOutput = """
                [ERROR] /project/src/main/java/User.java:[15,5] cannot find symbol
                [ERROR]   symbol:   class Lombok
                [ERROR]   location: class com.example.User
                """;

            List<CompilationError> errors = interpreter.parseErrors(mavenOutput);

            assertFalse(errors.isEmpty());
            assertEquals(1, errors.size());
            assertEquals(15, errors.get(0).line());
        }

        @Test
        @DisplayName("parseErrors() detecta errores de javac simple")
        void parseErrors_shouldDetectJavacErrors() {
            String javacOutput = """
                User.java:10: error: package lombok does not exist
                import lombok.Data;
                             ^
                """;

            List<CompilationError> errors = interpreter.parseErrors(javacOutput);

            assertFalse(errors.isEmpty());
            assertEquals(1, errors.size());
            assertEquals(10, errors.get(0).line());
        }
    }

    @Nested
    @DisplayName("Clasificación de Errores")
    class ClassificationTests {
        @Test
        @DisplayName("Detecta MISSING_IMPORT")
        void shouldDetectMissingImport() {
            String output = "[ERROR] User.java:[5,1] package lombok does not exist";
            List<CompilationError> errors = interpreter.parseErrors(output);

            assertFalse(errors.isEmpty());
            assertEquals(ErrorType.MISSING_IMPORT, errors.get(0).type());
        }

        @Test
        @DisplayName("Detecta SYMBOL_NOT_FOUND")
        void shouldDetectSymbolNotFound() {
            String output = "[ERROR] Service.java:[20,10] cannot find symbol";
            List<CompilationError> errors = interpreter.parseErrors(output);

            assertFalse(errors.isEmpty());
            assertEquals(ErrorType.SYMBOL_NOT_FOUND, errors.get(0).type());
        }

        @Test
        @DisplayName("Detecta TYPE_MISMATCH")
        void shouldDetectTypeMismatch() {
            String output = "[ERROR] Main.java:[30,5] incompatible types: String cannot be converted to int";
            List<CompilationError> errors = interpreter.parseErrors(output);

            assertFalse(errors.isEmpty());
            assertEquals(ErrorType.TYPE_MISMATCH, errors.get(0).type());
        }

        @Test
        @DisplayName("Detecta SYNTAX_ERROR")
        void shouldDetectSyntaxError() {
            String output = "[ERROR] Test.java:[1,1] illegal character: '`'";
            List<CompilationError> errors = interpreter.parseErrors(output);

            assertFalse(errors.isEmpty());
            assertEquals(ErrorType.SYNTAX_ERROR, errors.get(0).type());
        }
    }

    @Nested
    @DisplayName("CompilationError Record")
    class CompilationErrorTests {
        @Test
        @DisplayName("toAgentPrompt() genera prompt estructurado")
        void toAgentPrompt_shouldGenerateStructuredPrompt() {
            CompilationError error = new CompilationError(
                ErrorType.MISSING_IMPORT,
                "User.java",
                15,
                5,
                "package lombok does not exist",
                "Paquete faltante: lombok"
            );

            String prompt = error.toAgentPrompt();

            assertNotNull(prompt);
            assertTrue(prompt.contains("User.java"));
            assertTrue(prompt.contains("15"));
            assertTrue(prompt.contains("MISSING_IMPORT"));
        }

        @Test
        @DisplayName("toShortRef() genera referencia corta")
        void toShortRef_shouldGenerateShortRef() {
            CompilationError error = new CompilationError(
                ErrorType.SYNTAX_ERROR,
                "src/main/java/User.java",
                42,
                10,
                "error message",
                null
            );

            assertEquals("src/main/java/User.java:42", error.toShortRef());
        }
    }

    @Nested
    @DisplayName("ErrorType Enum")
    class ErrorTypeTests {
        @Test
        @DisplayName("Todos los tipos tienen acción sugerida")
        void allTypes_shouldHaveSuggestedAction() {
            for (ErrorType type : ErrorType.values()) {
                assertNotNull(type.getSuggestedAction());
                assertFalse(type.getSuggestedAction().isBlank());
            }
        }

        @Test
        @DisplayName("Tipos esperados están definidos")
        void expectedTypes_shouldBeDefined() {
            assertNotNull(ErrorType.MISSING_IMPORT);
            assertNotNull(ErrorType.SYMBOL_NOT_FOUND);
            assertNotNull(ErrorType.TYPE_MISMATCH);
            assertNotNull(ErrorType.SYNTAX_ERROR);
            assertNotNull(ErrorType.DUPLICATE_CLASS);
            assertNotNull(ErrorType.ACCESS_ERROR);
            assertNotNull(ErrorType.VERSION_ERROR);
            assertNotNull(ErrorType.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("Generación de Prompts")
    class PromptGenerationTests {
        @Test
        @DisplayName("generateAgentPrompt() con lista vacía")
        void generateAgentPrompt_withEmptyList_shouldReturnMessage() {
            String prompt = interpreter.generateAgentPrompt(List.of());
            assertNotNull(prompt);
            assertTrue(prompt.contains("No se detectaron"));
        }

        @Test
        @DisplayName("generateAgentPrompt() con errores agrupa por tipo")
        void generateAgentPrompt_shouldGroupByType() {
            List<CompilationError> errors = List.of(
                new CompilationError(ErrorType.MISSING_IMPORT, "A.java", 1, 0, "msg1", null),
                new CompilationError(ErrorType.MISSING_IMPORT, "B.java", 2, 0, "msg2", null),
                new CompilationError(ErrorType.SYNTAX_ERROR, "C.java", 3, 0, "msg3", null)
            );

            String prompt = interpreter.generateAgentPrompt(errors);

            assertNotNull(prompt);
            assertTrue(prompt.contains("3"));
            assertTrue(prompt.contains("MISSING_IMPORT"));
            assertTrue(prompt.contains("SYNTAX_ERROR"));
        }

        @Test
        @DisplayName("generateSummary() genera resumen conciso")
        void generateSummary_shouldGenerateConciseSummary() {
            List<CompilationError> errors = List.of(
                new CompilationError(ErrorType.MISSING_IMPORT, "A.java", 1, 0, "msg", null),
                new CompilationError(ErrorType.MISSING_IMPORT, "B.java", 2, 0, "msg", null)
            );

            String summary = interpreter.generateSummary(errors);

            assertNotNull(summary);
            assertTrue(summary.contains("2"));
        }

        @Test
        @DisplayName("generateSummary() con lista vacía")
        void generateSummary_withEmptyList_shouldReturnZero() {
            String summary = interpreter.generateSummary(List.of());
            assertEquals("0 errors", summary);
        }
    }

    @Nested
    @DisplayName("Detección de Errores Lombok")
    class LombokDetectionTests {
        @Test
        @DisplayName("isLombokRelatedError() detecta 'package lombok does not exist'")
        void isLombokRelatedError_shouldDetectLombokPackage() {
            assertTrue(interpreter.isLombokRelatedError("package lombok does not exist"));
            assertTrue(interpreter.isLombokRelatedError("package lombok.extern does not exist"));
            assertTrue(interpreter.isLombokRelatedError("package lombok.experimental does not exist"));
        }

        @Test
        @DisplayName("isLombokRelatedError() detecta anotaciones Lombok")
        void isLombokRelatedError_shouldDetectAnnotations() {
            assertTrue(interpreter.isLombokRelatedError("cannot find symbol: @Data"));
            assertTrue(interpreter.isLombokRelatedError("cannot find symbol: @Getter"));
            assertTrue(interpreter.isLombokRelatedError("cannot find symbol: @Builder"));
        }

        @Test
        @DisplayName("isLombokRelatedError() retorna false para errores no-Lombok")
        void isLombokRelatedError_shouldReturnFalseForOthers() {
            assertFalse(interpreter.isLombokRelatedError("package javax.inject does not exist"));
            assertFalse(interpreter.isLombokRelatedError("cannot find symbol: UserRepository"));
            assertFalse(interpreter.isLombokRelatedError(null));
            assertFalse(interpreter.isLombokRelatedError(""));
        }

        @Test
        @DisplayName("hasLombokErrors() detecta errores Lombok en lista")
        void hasLombokErrors_shouldDetectInList() {
            List<CompilationError> errors = List.of(
                new CompilationError(ErrorType.MISSING_IMPORT, "User.java", 1, 0,
                    "package lombok does not exist", null)
            );

            assertTrue(interpreter.hasLombokErrors(errors));
        }

        @Test
        @DisplayName("hasLombokErrors() retorna false para lista sin Lombok")
        void hasLombokErrors_shouldReturnFalseForNonLombok() {
            List<CompilationError> errors = List.of(
                new CompilationError(ErrorType.MISSING_IMPORT, "User.java", 1, 0,
                    "package javax.inject does not exist", null)
            );

            assertFalse(interpreter.hasLombokErrors(errors));
        }

        @Test
        @DisplayName("generateLombokReplacementSuggestion() retorna sugerencia válida")
        void generateLombokReplacementSuggestion_shouldReturnValidSuggestion() {
            String suggestion = interpreter.generateLombokReplacementSuggestion(null);

            assertNotNull(suggestion);
            assertTrue(suggestion.contains("Java Record"));
            assertTrue(suggestion.contains("NUNCA uses Lombok"));
        }

        @Test
        @DisplayName("generateAgentPrompt() incluye advertencia Lombok cuando aplica")
        void generateAgentPrompt_shouldIncludeLombokWarning() {
            List<CompilationError> errors = List.of(
                new CompilationError(ErrorType.MISSING_IMPORT, "User.java", 1, 0,
                    "package lombok does not exist", null)
            );

            String prompt = interpreter.generateAgentPrompt(errors);

            assertTrue(prompt.contains("Java Record"), "Debería sugerir Java Records");
            assertTrue(prompt.contains("NUNCA uses Lombok"), "Debería advertir sobre Lombok");
        }

        @Test
        @DisplayName("Contexto de error Lombok incluye advertencia")
        void parseErrors_shouldIncludeLombokWarningInContext() {
            String mavenOutput = "[ERROR] /project/src/main/java/User.java:[5,1] package lombok does not exist";

            List<CompilationError> errors = interpreter.parseErrors(mavenOutput);

            assertFalse(errors.isEmpty());
            assertEquals(ErrorType.MISSING_IMPORT, errors.get(0).type());
            assertNotNull(errors.get(0).context());
            assertTrue(errors.get(0).context().contains("LOMBOK") ||
                      errors.get(0).context().contains("Records"),
                "Contexto debería mencionar Lombok o Records");
        }
    }

    @Nested
    @DisplayName("Escenarios Reales de Compilación")
    class RealWorldScenarios {
        @Test
        @DisplayName("Maven build típico con múltiples errores")
        void typicalMavenBuild_withMultipleErrors() {
            String mavenOutput = """
                [INFO] Compiling 15 source files...
                [ERROR] /project/src/main/java/com/example/UserService.java:[23,15] package javax.inject does not exist
                [ERROR] /project/src/main/java/com/example/UserService.java:[45,10] cannot find symbol
                [ERROR]   symbol:   class UserRepository
                [ERROR]   location: class com.example.UserService
                [INFO] BUILD FAILURE
                [INFO] Total time: 5.234 s
                """;

            List<CompilationError> errors = interpreter.parseErrors(mavenOutput);

            assertEquals(2, errors.size());

            assertEquals(ErrorType.MISSING_IMPORT, errors.get(0).type());
            assertEquals(23, errors.get(0).line());

            assertEquals(ErrorType.SYMBOL_NOT_FOUND, errors.get(1).type());
            assertEquals(45, errors.get(1).line());
        }

        @Test
        @DisplayName("Output sin errores de compilación")
        void outputWithoutCompilationErrors() {
            String successOutput = """
                [INFO] Compiling 15 source files...
                [INFO] BUILD SUCCESS
                [INFO] Total time: 3.456 s
                """;

            List<CompilationError> errors = interpreter.parseErrors(successOutput);

            assertTrue(errors.isEmpty());
        }
    }
}
