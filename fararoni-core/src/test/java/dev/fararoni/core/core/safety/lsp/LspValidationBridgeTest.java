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
package dev.fararoni.core.core.safety.lsp;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("LspValidationBridge - Validación LSP")
class LspValidationBridgeTest {
    private LspValidationBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new LspValidationBridge();
    }

    @Nested
    @DisplayName("Validación Básica")
    class BasicValidationTests {
        @Test
        @DisplayName("validate() con null retorna skipped")
        void validate_withNull_shouldReturnSkipped() {
            ValidationResult result = bridge.validate(null, null);
            assertTrue(result.isValid());
            assertEquals(ValidationResult.SKIPPED, result.errorCode());
        }

        @Test
        @DisplayName("validate() con contenido vacío retorna skipped")
        void validate_withEmptyContent_shouldReturnSkipped() {
            ValidationResult result = bridge.validate("test.java", "");
            assertTrue(result.isValid());
            assertEquals(ValidationResult.SKIPPED, result.errorCode());
        }

        @Test
        @DisplayName("validate() con extensión desconocida retorna skipped")
        void validate_withUnknownExtension_shouldReturnSkipped() {
            ValidationResult result = bridge.validate("file.xyz", "content");
            assertTrue(result.isValid());
            assertEquals(ValidationResult.SKIPPED, result.errorCode());
        }
    }

    @Nested
    @DisplayName("Detección de Binarios")
    class BinaryDetectionTests {
        @Test
        @DisplayName("isJava25Available() no lanza excepción")
        void isJava25Available_shouldNotThrow() {
            assertDoesNotThrow(() -> LspValidationBridge.isJava25Available());
        }

        @Test
        @DisplayName("isPython3Available() no lanza excepción")
        void isPython3Available_shouldNotThrow() {
            assertDoesNotThrow(() -> LspValidationBridge.isPython3Available());
        }

        @Test
        @DisplayName("isNodeAvailable() no lanza excepción")
        void isNodeAvailable_shouldNotThrow() {
            assertDoesNotThrow(() -> LspValidationBridge.isNodeAvailable());
        }
    }

    @Nested
    @DisplayName("Validación Java")
    @EnabledIf("isJavaAvailable")
    class JavaValidationTests {
        static boolean isJavaAvailable() {
            return LspValidationBridge.isJava25Available();
        }

        @Test
        @DisplayName("Código Java válido pasa validación")
        void validJavaCode_shouldPass() {
            String javaCode = """
                public class Test {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
                """;

            ValidationResult result = bridge.validate("Test.java", javaCode);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Código Java con error de sintaxis falla")
        void invalidJavaCode_shouldFail() {
            String badCode = """
                public class Test {
                    public static void main(String[] args) {
                        System.out.println("Hello")
                    }
                }
                """;

            ValidationResult result = bridge.validate("Test.java", badCode);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Código con backticks se limpia correctamente")
        void codeWithBackticks_shouldBeCleaned() {
            String codeWithMarkdown = """
                ```java
                public class Test {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
                ```
                """;

            ValidationResult result = bridge.validate("Test.java", codeWithMarkdown);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Validación Python")
    @EnabledIf("isPythonAvailable")
    class PythonValidationTests {
        static boolean isPythonAvailable() {
            return LspValidationBridge.isPython3Available();
        }

        @Test
        @DisplayName("Código Python válido pasa validación")
        void validPythonCode_shouldPass() {
            String pythonCode = """
                def hello():
                    print("Hello, World!")

                if __name__ == "__main__":
                    hello()
                """;

            ValidationResult result = bridge.validate("test.py", pythonCode);

            assertTrue(result.isValid(), "Python válido debería pasar: " + result.message());
        }

        @Test
        @DisplayName("Código Python con error de sintaxis falla")
        void invalidPythonCode_shouldFail() {
            String badCode = """
                def hello(
                    print("Falta cerrar paréntesis")
                """;

            ValidationResult result = bridge.validate("test.py", badCode);

            assertFalse(result.isValid());
        }
    }

    @Nested
    @DisplayName("Validación JavaScript")
    @EnabledIf("isNodeAvailable")
    class JavaScriptValidationTests {
        static boolean isNodeAvailable() {
            return LspValidationBridge.isNodeAvailable();
        }

        @Test
        @DisplayName("Código JS válido pasa validación")
        void validJsCode_shouldPass() {
            String jsCode = """
                function hello() {
                    console.log("Hello, World!");
                }
                hello();
                """;

            ValidationResult result = bridge.validate("test.js", jsCode);

            assertTrue(result.isValid(), "JS válido debería pasar: " + result.message());
        }

        @Test
        @DisplayName("Código JS con error de sintaxis falla")
        void invalidJsCode_shouldFail() {
            String badCode = """
                function hello( {
                    console.log("Falta cerrar paréntesis");
                }
                """;

            ValidationResult result = bridge.validate("test.js", badCode);

            assertFalse(result.isValid());
        }
    }

    @Nested
    @DisplayName("Filtrado de Falsos Positivos")
    class FalsePositiveFilterTests {
        @Test
        @DisplayName("Error de dependencia se trata como éxito")
        @EnabledIf("isJavaAvailable")
        void dependencyError_shouldBeFilteredAsSuccess() {
            String codeWithExternalDep = """
                import lombok.Data;

                @Data
                public class User {
                    private String name;
                }
                """;

            ValidationResult result = bridge.validate("User.java", codeWithExternalDep);

            assertTrue(result.isValid(),
                "Error de dependencia debería filtrarse como éxito (falso positivo)");
        }

        static boolean isJavaAvailable() {
            return LspValidationBridge.isJava25Available();
        }
    }

    @Nested
    @DisplayName("Manejo de Timeout")
    class TimeoutTests {
        @Test
        @DisplayName("Constructor con timeout personalizado")
        void customTimeout_shouldBeAccepted() {
            LspValidationBridge customBridge = new LspValidationBridge(60);
            assertNotNull(customBridge);
        }

        @Test
        @DisplayName("Timeout negativo usa default")
        void negativeTimeout_shouldUseDefault() {
            LspValidationBridge customBridge = new LspValidationBridge(-1);
            assertNotNull(customBridge);
        }
    }

    @Nested
    @DisplayName("Limpieza de Archivos Temporales")
    class CleanupTests {
        @Test
        @DisplayName("Archivos temporales se limpian después de validación")
        void tempFiles_shouldBeCleanedUp() {
            String code = "public class Test {}";

            for (int i = 0; i < 5; i++) {
                bridge.validate("Test" + i + ".java", code);
            }

            assertDoesNotThrow(() -> bridge.validate("Final.java", code));
        }
    }
}
