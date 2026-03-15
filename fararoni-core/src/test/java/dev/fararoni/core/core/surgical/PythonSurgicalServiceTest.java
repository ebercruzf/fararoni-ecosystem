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
package dev.fararoni.core.core.surgical;

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
class PythonSurgicalServiceTest {
    private PythonSurgicalService service;

    @BeforeEach
    void setUp() {
        service = new PythonSurgicalService();
    }

    @Nested
    @DisplayName("Validacion de Sintaxis Python (Tree-sitter)")
    class SyntaxValidationTests {
        @Test
        @DisplayName("Codigo Python valido pasa validacion")
        void testValidPythonPasses() {
            String validPython = """
                def hello(name):
                    print(f"Hello, {name}!")
                    return True

                class Calculator:
                    def add(self, a, b):
                        return a + b
                """;

            var result = service.validatePythonSyntax(validPython);

            assertTrue(result.isValid(), "Codigo valido debe pasar validacion");
            assertNull(result.getErrorMessage());
            assertTrue(result.nodeCount() > 0, "Debe tener nodos en el AST");
        }

        @Test
        @DisplayName("Codigo Python con error de sintaxis falla validacion")
        void testInvalidPythonFails() {
            String invalidPython = """
                def broken(:
                    print("missing parameter")
                """;

            var result = service.validatePythonSyntax(invalidPython);

            assertFalse(result.isValid(), "Codigo invalido debe fallar validacion");
        }

        @Test
        @DisplayName("Indentacion incorrecta falla validacion")
        void testBadIndentationFails() {
            String badIndent = """
                def test():
                print("no indentation")
                """;

            var result = service.validatePythonSyntax(badIndent);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Codigo vacio retorna invalido")
        void testEmptyCodeInvalid() {
            var result = service.validatePythonSyntax("");
            assertFalse(result.isValid());

            var resultNull = service.validatePythonSyntax(null);
            assertFalse(resultNull.isValid());
        }

        @Test
        @DisplayName("Decoradores son validos")
        void testDecoratorsValid() {
            String withDecorators = """
                @staticmethod
                def static_method():
                    pass

                @property
                def my_property(self):
                    return self._value

                @decorator_with_args(arg1, arg2)
                def decorated():
                    pass
                """;

            var result = service.validatePythonSyntax(withDecorators);
            assertTrue(result.isValid(), "Decoradores deben ser validos");
        }

        @Test
        @DisplayName("Type hints son validos")
        void testTypeHintsValid() {
            String withTypeHints = """
                def greet(name: str, times: int = 1) -> str:
                    return name * times

                def process(data: list[dict[str, int]]) -> None:
                    pass
                """;

            var result = service.validatePythonSyntax(withTypeHints);
            assertTrue(result.isValid(), "Type hints deben ser validos");
        }

        @Test
        @DisplayName("Async/await son validos")
        void testAsyncAwaitValid() {
            String asyncCode = """
                async def fetch_data(url: str) -> dict:
                    async with aiohttp.ClientSession() as session:
                        async for item in stream:
                            await process(item)
                    return {}
                """;

            var result = service.validatePythonSyntax(asyncCode);
            assertTrue(result.isValid(), "Async/await debe ser valido");
        }
    }

    @Nested
    @DisplayName("Cirugia Basica")
    class BasicSurgeryTests {
        @Test
        @DisplayName("Cirugia simple con 4 espacios")
        void testSimpleSurgerySpaces4() {
            String source = """
                def calculate(x):
                    result = x * 2
                    return result
                """;

            EditBlock edit = new EditBlock(
                "edit-1",
                "result = x * 2",
                "result = x * 3  # Modified",
                2, 0, 0
            );

            var result = service.executeSurgery(source, List.of(edit));

            assertTrue(!result.logs().isEmpty());
            assertTrue(result.content().contains("x * 3"));
            assertTrue(result.content().contains("# Modified"));
            assertEquals(UnifiedBlockGrammar.IndentationStyle.SPACES_4, result.detectedStyle());
            assertTrue(result.syntaxValidation().isValid());
        }

        @Test
        @DisplayName("Cirugia preserva indentacion anidada")
        void testNestedIndentation() {
            String source = """
                class MyClass:
                    def method(self):
                        if True:
                            value = 1
                        return value
                """;

            EditBlock edit = new EditBlock(
                "edit-1",
                "value = 1",
                "value = calculate()\n            extra = 2",
                4, 0, 0
            );

            var result = service.executeSurgery(source, List.of(edit));

            assertTrue(!result.logs().isEmpty());
            assertTrue(result.syntaxValidation().isValid(),
                "El resultado debe ser sintacticamente valido");
        }

        @Test
        @DisplayName("Source null lanza excepcion")
        void testNullSourceThrows() {
            assertThrows(PythonSurgicalService.PythonSurgicalException.class, () ->
                service.executeSurgery(null, List.of())
            );
        }

        @Test
        @DisplayName("Source vacio lanza excepcion")
        void testEmptySourceThrows() {
            assertThrows(PythonSurgicalService.PythonSurgicalException.class, () ->
                service.executeSurgery("", List.of())
            );
        }

        @Test
        @DisplayName("Edits null retorna noChanges")
        void testNullEditsReturnsNoChanges() {
            String source = "def test(): pass\n";
            var result = service.executeSurgery(source, null);

            assertTrue(result.logs().isEmpty());
            assertEquals(source, result.content());
        }

        @Test
        @DisplayName("Edits vacio retorna noChanges")
        void testEmptyEditsReturnsNoChanges() {
            String source = "def test(): pass\n";
            var result = service.executeSurgery(source, List.of());

            assertTrue(result.logs().isEmpty());
            assertEquals(source, result.content());
        }
    }

    @Nested
    @DisplayName("Deteccion de Estilo de Indentacion")
    class StyleDetectionTests {
        @Test
        @DisplayName("Detecta estilo SPACES_4")
        void testDetectsSpaces4() {
            String source = """
                def test():
                    line1 = 1
                    line2 = 2
                    if True:
                        nested = 3
                """;

            var result = service.executeSurgery(source, List.of());
            assertEquals(UnifiedBlockGrammar.IndentationStyle.SPACES_4, result.detectedStyle());
        }

        @Test
        @DisplayName("Detecta estilo SPACES_2")
        void testDetectsSpaces2() {
            String source = """
                def test():
                  line1 = 1
                  line2 = 2
                  if True:
                    nested = 3
                """;

            var result = service.executeSurgery(source, List.of());
            assertEquals(UnifiedBlockGrammar.IndentationStyle.SPACES_2, result.detectedStyle());
        }

        @Test
        @DisplayName("Detecta estilo TABS")
        void testDetectsTabs() {
            String source = "def test():\n\tline1 = 1\n\tline2 = 2\n";

            var result = service.executeSurgery(source, List.of());
            assertEquals(UnifiedBlockGrammar.IndentationStyle.TABS, result.detectedStyle());
        }
    }

    @Nested
    @DisplayName("Aplicacion desde Output LLM")
    class LlmOutputTests {
        @Test
        @DisplayName("Aplica cirugia desde formato SEARCH/REPLACE")
        void testApplyFromLlmOutput() {
            String source = """
                def greet(name):
                    message = "Hello"
                    return message
                """;

            String llmOutput = """
                <<<<<<< SEARCH
                message = "Hello"
                =======
                message = f"Hello, {name}!"
                >>>>>>> REPLACE
                """;

            var result = service.applySurgeryFromLlmOutput(source, llmOutput);

            assertTrue(!result.logs().isEmpty());
            assertTrue(result.content().contains("f\"Hello, {name}!\""));
            assertTrue(result.syntaxValidation().isValid());
        }

        @Test
        @DisplayName("NO_CHANGES_REQUIRED retorna sin cambios")
        void testNoChangesRequired() {
            String source = "def test(): pass\n";
            String llmOutput = "NO_CHANGES_REQUIRED";

            var result = service.applySurgeryFromLlmOutput(source, llmOutput);

            assertTrue(result.logs().isEmpty());
            assertEquals(source, result.content());
        }

        @Test
        @DisplayName("Formato tolerante (6 caracteres) funciona")
        void testTolerantFormat6Chars() {
            String source = "x = 1\n";

            String llmOutput = """
                <<<<<< SEARCH
                x = 1
                ======
                x = 2
                >>>>>> REPLACE
                """;

            var result = service.applySurgeryFromLlmOutput(source, llmOutput);

            assertTrue(!result.logs().isEmpty());
            assertTrue(result.content().contains("x = 2"));
        }

        @Test
        @DisplayName("Formato tolerante (5 caracteres) funciona")
        void testTolerantFormat5Chars() {
            String source = "y = 1\n";

            String llmOutput = """
                <<<<< search
                y = 1
                =====
                y = 3
                >>>>> replace
                """;

            var result = service.applySurgeryFromLlmOutput(source, llmOutput);

            assertTrue(!result.logs().isEmpty());
            assertTrue(result.content().contains("y = 3"));
        }
    }

    @Nested
    @DisplayName("Manejo de Errores")
    class ErrorHandlingTests {
        @Test
        @DisplayName("SEARCH no encontrado lanza excepcion")
        void testSearchNotFoundThrows() {
            String source = "x = 1\n";

            EditBlock edit = new EditBlock(
                "edit-1",
                "no_existe = 999",
                "replacement",
                1, 0, 0
            );

            assertThrows(PythonSurgicalService.PythonSurgicalException.class, () ->
                service.executeSurgery(source, List.of(edit))
            );
        }

        @Test
        @DisplayName("Cirugia que corrompe sintaxis lanza excepcion")
        void testCorruptingSurgeryThrows() {
            String source = """
                def test():
                    return 1
                """;

            EditBlock edit = new EditBlock(
                "edit-1",
                "def test():\n    return 1",
                "def broken(\n    syntax error here",
                1, 0, 0
            );

            assertThrows(PythonSurgicalService.PythonSurgicalException.class, () ->
                service.executeSurgery(source, List.of(edit))
            );
        }
    }

    @Nested
    @DisplayName("Multiples Ediciones")
    class MultipleEditsTests {
        @Test
        @DisplayName("Dos ediciones ordenadas bottom-up")
        void testTwoEditsBottomUp() {
            String source = """
                def func_a():
                    return "a"

                def func_b():
                    return "b"
                """;

            EditBlock edit1 = new EditBlock(
                "edit-a", "return \"a\"", "return \"A\"", 2, 0, 0
            );

            EditBlock edit2 = new EditBlock(
                "edit-b", "return \"b\"", "return \"B\"", 5, 0, 0
            );

            var result = service.executeSurgery(source, List.of(edit1, edit2));

            assertTrue(!result.logs().isEmpty());
            assertEquals(2, result.logs().size());
            assertTrue(result.content().contains("return \"A\""));
            assertTrue(result.content().contains("return \"B\""));
            assertTrue(result.syntaxValidation().isValid());
        }

        @Test
        @DisplayName("Ediciones con delta de tamanio")
        void testEditsWithSizeDelta() {
            String source = """
                a = 1
                b = 2
                c = 3
                """;

            EditBlock edit = new EditBlock(
                "edit-1",
                "b = 2",
                "b = 2\nb_extra = 20\nb_more = 200",
                2, 0, 0
            );

            var result = service.executeSurgery(source, List.of(edit));

            assertTrue(!result.logs().isEmpty());
            assertTrue(result.totalDelta() > 0, "Delta debe ser positivo al agregar lineas");
            assertTrue(result.content().contains("b_extra"));
            assertTrue(result.content().contains("b_more"));
            assertTrue(result.syntaxValidation().isValid());
        }
    }

    @Nested
    @DisplayName("Normalizacion de Indentacion")
    class IndentationNormalizationTests {
        @Test
        @DisplayName("Normaliza tabs a espacios")
        void testNormalizesTabsToSpaces() {
            String source = """
                def test():
                    value = 1
                    return value
                """;

            EditBlock edit = new EditBlock(
                "edit-1",
                "value = 1",
                "value = compute()\n\tif value:\n\t\treturn value",
                2, 0, 0
            );

            var result = service.executeSurgery(source, List.of(edit));

            assertTrue(!result.logs().isEmpty());
            assertTrue(result.syntaxValidation().isValid(),
                "El resultado normalizado debe ser sintacticamente valido");
        }

        @Test
        @DisplayName("LLM sin indentacion se alinea al contexto")
        void testZeroIndentAligns() {
            String source = """
                class MyClass:
                    def method(self):
                        existing = 1
                """;

            EditBlock edit = new EditBlock(
                "edit-1",
                "existing = 1",
                "new_value = compute()\nif new_value:\n    process(new_value)",
                3, 0, 0
            );

            var result = service.executeSurgery(source, List.of(edit));

            assertTrue(!result.logs().isEmpty());
            assertTrue(result.syntaxValidation().isValid());
        }
    }

    @Nested
    @DisplayName("PythonSurgeryResult")
    class ResultTests {
        @Test
        @DisplayName("noChanges preserva source y estilo")
        void testNoChangesPreservesSource() {
            String source = """
                def test():
                    return 42
                """;

            var result = PythonSurgicalService.PythonSurgeryResult.noChanges(source);

            assertEquals(source, result.content());
            assertTrue(result.logs().isEmpty());
            assertEquals(0, result.totalDelta());
            assertTrue(result.logs().isEmpty());
            assertEquals(UnifiedBlockGrammar.IndentationStyle.SPACES_4, result.detectedStyle());
        }
    }
}
