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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("UnifiedBlockGrammar Tests")
class UnifiedBlockGrammarTest {
    @Nested
    @DisplayName("Parse Tests")
    class ParseTests {
        @Test
        @DisplayName("Parsea bloque unificado correctamente")
        void testParseValidBlock() {
            String input = """
                <<<<<<< SEARCH
                files.add(f);
                =======
                if(f!=null) files.add(clean(f));
                >>>>>>> REPLACE
                """;

            Optional<EditBlock> result = UnifiedBlockGrammar.parse(input);

            assertTrue(result.isPresent());
            assertEquals("files.add(f);", result.get().search());
            assertEquals("if(f!=null) files.add(clean(f));", result.get().replace());
        }

        @Test
        @DisplayName("Parsea NO_CHANGES_REQUIRED como empty")
        void testParseNoChanges() {
            Optional<EditBlock> result = UnifiedBlockGrammar.parse("NO_CHANGES_REQUIRED");

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Parsea bloque con codigo multilinea")
        void testParseMultilineBlock() {
            String input = """
                <<<<<<< SEARCH
                public void method() {
                    doSomething();
                }
                =======
                public void method() {
                    validateInput();
                    doSomething();
                    logResult();
                }
                >>>>>>> REPLACE
                """;

            Optional<EditBlock> result = UnifiedBlockGrammar.parse(input);

            assertTrue(result.isPresent());
            assertTrue(result.get().search().contains("doSomething()"));
            assertTrue(result.get().replace().contains("validateInput()"));
            assertTrue(result.get().replace().contains("logResult()"));
        }

        @Test
        @DisplayName("Lanza excepcion para output null")
        void testParseNullThrows() {
            assertThrows(UnifiedBlockGrammar.UnifiedBlockParseException.class, () ->
                UnifiedBlockGrammar.parse(null)
            );
        }

        @Test
        @DisplayName("Lanza excepcion para output vacio")
        void testParseEmptyThrows() {
            assertThrows(UnifiedBlockGrammar.UnifiedBlockParseException.class, () ->
                UnifiedBlockGrammar.parse("   ")
            );
        }

        @Test
        @DisplayName("Lanza excepcion para formato invalido")
        void testParseInvalidFormatThrows() {
            assertThrows(UnifiedBlockGrammar.UnifiedBlockParseException.class, () ->
                UnifiedBlockGrammar.parse("Aqui tienes el codigo modificado: ...")
            );
        }

        @Test
        @DisplayName("Lanza excepcion para bloque SEARCH vacio")
        void testParseEmptySearchThrows() {
            String input = """
                <<<<<<< SEARCH
                =======
                replacement
                >>>>>>> REPLACE
                """;

            assertThrows(UnifiedBlockGrammar.UnifiedBlockParseException.class, () ->
                UnifiedBlockGrammar.parse(input)
            );
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {
        @Test
        @DisplayName("isValid retorna true para bloque valido")
        void testIsValidTrue() {
            String input = """
                <<<<<<< SEARCH
                old code
                =======
                new code
                >>>>>>> REPLACE
                """;

            assertTrue(UnifiedBlockGrammar.isValid(input));
        }

        @Test
        @DisplayName("isValid retorna true para NO_CHANGES")
        void testIsValidNoChanges() {
            assertTrue(UnifiedBlockGrammar.isValid("NO_CHANGES_REQUIRED"));
        }

        @Test
        @DisplayName("isValid retorna false para null")
        void testIsValidNull() {
            assertFalse(UnifiedBlockGrammar.isValid(null));
        }

        @Test
        @DisplayName("isValid retorna false para texto libre")
        void testIsValidFreeText() {
            assertFalse(UnifiedBlockGrammar.isValid("Aqui esta el codigo modificado"));
        }
    }

    @Nested
    @DisplayName("Format Tests")
    class FormatTests {
        @Test
        @DisplayName("Formatea bloque correctamente")
        void testFormatBlock() {
            EditBlock block = new EditBlock("id", "search text", "replace text", 1, 0, 0);

            String formatted = UnifiedBlockGrammar.format(block);

            assertTrue(formatted.contains("<<<<<<< SEARCH"));
            assertTrue(formatted.contains("search text"));
            assertTrue(formatted.contains("======="));
            assertTrue(formatted.contains("replace text"));
            assertTrue(formatted.contains(">>>>>>> REPLACE"));
        }

        @Test
        @DisplayName("Formatea null como NO_CHANGES")
        void testFormatNull() {
            String formatted = UnifiedBlockGrammar.format(null);
            assertEquals("NO_CHANGES_REQUIRED", formatted);
        }

        @Test
        @DisplayName("Formatea lista de bloques")
        void testFormatMultiple() {
            List<EditBlock> blocks = List.of(
                new EditBlock("a", "s1", "r1", 1, 0, 0),
                new EditBlock("b", "s2", "r2", 2, 0, 0)
            );

            String formatted = UnifiedBlockGrammar.formatMultiple(blocks);

            assertTrue(formatted.contains("s1"));
            assertTrue(formatted.contains("r1"));
            assertTrue(formatted.contains("s2"));
            assertTrue(formatted.contains("r2"));
        }

        @Test
        @DisplayName("Formatea lista vacia como NO_CHANGES")
        void testFormatEmptyList() {
            String formatted = UnifiedBlockGrammar.formatMultiple(List.of());
            assertEquals("NO_CHANGES_REQUIRED", formatted);
        }
    }

    @Nested
    @DisplayName("Roundtrip Tests")
    class RoundtripTests {
        @Test
        @DisplayName("Format y parse son inversos")
        void testRoundtrip() {
            EditBlock original = new EditBlock("test", "original code", "modified code", 5, 0, 0);

            String formatted = UnifiedBlockGrammar.format(original);
            Optional<EditBlock> parsed = UnifiedBlockGrammar.parse(formatted);

            assertTrue(parsed.isPresent());
            assertEquals(original.search(), parsed.get().search());
            assertEquals(original.replace(), parsed.get().replace());
        }
    }

    @Nested
    @DisplayName("Grammar Constant Tests")
    class GrammarConstantTests {
        @Test
        @DisplayName("Gramatica GBNF no esta vacia")
        void testGrammarNotEmpty() {
            assertNotNull(UnifiedBlockGrammar.GBNF_GRAMMAR);
            assertFalse(UnifiedBlockGrammar.GBNF_GRAMMAR.isBlank());
        }

        @Test
        @DisplayName("Gramatica contiene reglas esperadas")
        void testGrammarContainsRules() {
            String grammar = UnifiedBlockGrammar.GBNF_GRAMMAR;

            assertTrue(grammar.contains("root"));
            assertTrue(grammar.contains("unified_block"));
            assertTrue(grammar.contains("no_changes"));
            assertTrue(grammar.contains("code_block"));
        }

        @Test
        @DisplayName("Delimitadores son correctos")
        void testDelimiters() {
            assertEquals("<<<<<<< SEARCH", UnifiedBlockGrammar.SEARCH_START);
            assertEquals("=======", UnifiedBlockGrammar.SEPARATOR);
            assertEquals(">>>>>>> REPLACE", UnifiedBlockGrammar.REPLACE_END);
            assertEquals("NO_CHANGES_REQUIRED", UnifiedBlockGrammar.NO_CHANGES);
        }
    }

    @Nested
    @DisplayName("Ciclo 8: Python Grammar Tests")
    class PythonGrammarTests {
        @Test
        @DisplayName("Gramatica Python no esta vacia")
        void testPythonGrammarNotEmpty() {
            assertNotNull(UnifiedBlockGrammar.PYTHON_GBNF_GRAMMAR);
            assertFalse(UnifiedBlockGrammar.PYTHON_GBNF_GRAMMAR.isBlank());
        }

        @Test
        @DisplayName("Gramatica Python contiene reglas Grado Militar")
        void testPythonGrammarContainsRules() {
            String grammar = UnifiedBlockGrammar.PYTHON_GBNF_GRAMMAR;

            assertTrue(grammar.contains("root"));
            assertTrue(grammar.contains("block"));
            assertTrue(grammar.contains("search_section"));
            assertTrue(grammar.contains("replace_section"));
            assertTrue(grammar.contains("content"));
            assertTrue(grammar.contains("line"));
            assertTrue(grammar.contains(UnifiedBlockGrammar.SEARCH_START));
            assertTrue(grammar.contains(UnifiedBlockGrammar.SEPARATOR));
            assertTrue(grammar.contains(UnifiedBlockGrammar.REPLACE_END));
        }

        @Test
        @DisplayName("Gramatica Python es diferente a la generica")
        void testPythonGrammarDifferentFromGeneric() {
            assertNotEquals(
                UnifiedBlockGrammar.GBNF_GRAMMAR,
                UnifiedBlockGrammar.PYTHON_GBNF_GRAMMAR
            );
        }
    }

    @Nested
    @DisplayName("Ciclo 8: Language Grammar Selector Tests")
    class LanguageGrammarSelectorTests {
        @Test
        @DisplayName("Python extension retorna gramatica Python")
        void testPythonExtensionReturnsGrammar() {
            assertEquals(
                UnifiedBlockGrammar.PYTHON_GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("py")
            );
        }

        @Test
        @DisplayName("Python con punto retorna gramatica Python")
        void testPythonWithDotReturnsGrammar() {
            assertEquals(
                UnifiedBlockGrammar.PYTHON_GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage(".py")
            );
        }

        @Test
        @DisplayName("Filename Python retorna gramatica Python")
        void testPythonFilenameReturnsGrammar() {
            assertEquals(
                UnifiedBlockGrammar.PYTHON_GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("main.py")
            );
        }

        @Test
        @DisplayName("pyw extension retorna gramatica Python")
        void testPywExtensionReturnsGrammar() {
            assertEquals(
                UnifiedBlockGrammar.PYTHON_GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("pyw")
            );
        }

        @Test
        @DisplayName("pyi (stub) extension retorna gramatica Python")
        void testPyiExtensionReturnsGrammar() {
            assertEquals(
                UnifiedBlockGrammar.PYTHON_GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("pyi")
            );
        }

        @Test
        @DisplayName("Java extension retorna gramatica generica")
        void testJavaExtensionReturnsGeneric() {
            assertEquals(
                UnifiedBlockGrammar.GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("java")
            );
        }

        @Test
        @DisplayName("JavaScript extension retorna gramatica generica")
        void testJsExtensionReturnsGeneric() {
            assertEquals(
                UnifiedBlockGrammar.GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("js")
            );
        }

        @Test
        @DisplayName("TypeScript extension retorna gramatica generica")
        void testTsExtensionReturnsGeneric() {
            assertEquals(
                UnifiedBlockGrammar.GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("ts")
            );
        }

        @Test
        @DisplayName("Go extension retorna gramatica generica")
        void testGoExtensionReturnsGeneric() {
            assertEquals(
                UnifiedBlockGrammar.GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("go")
            );
        }

        @Test
        @DisplayName("Rust extension retorna gramatica generica")
        void testRsExtensionReturnsGeneric() {
            assertEquals(
                UnifiedBlockGrammar.GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("rs")
            );
        }

        @Test
        @DisplayName("Extension desconocida retorna gramatica generica")
        void testUnknownExtensionReturnsGeneric() {
            assertEquals(
                UnifiedBlockGrammar.GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("xyz")
            );
        }

        @Test
        @DisplayName("Null extension retorna gramatica generica")
        void testNullExtensionReturnsGeneric() {
            assertEquals(
                UnifiedBlockGrammar.GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage(null)
            );
        }

        @Test
        @DisplayName("Extension vacia retorna gramatica generica")
        void testEmptyExtensionReturnsGeneric() {
            assertEquals(
                UnifiedBlockGrammar.GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("")
            );
        }

        @Test
        @DisplayName("Extension con mayusculas se normaliza")
        void testUppercaseExtensionNormalized() {
            assertEquals(
                UnifiedBlockGrammar.PYTHON_GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("PY")
            );
            assertEquals(
                UnifiedBlockGrammar.GBNF_GRAMMAR,
                UnifiedBlockGrammar.getGrammarForLanguage("JAVA")
            );
        }
    }

    @Nested
    @DisplayName("Ciclo 8: Indentation Sensitivity Tests")
    class IndentationSensitivityTests {
        @Test
        @DisplayName("Python es sensible a indentacion")
        void testPythonIsIndentSensitive() {
            assertTrue(UnifiedBlockGrammar.isIndentationSensitive("py"));
            assertTrue(UnifiedBlockGrammar.isIndentationSensitive(".py"));
            assertTrue(UnifiedBlockGrammar.isIndentationSensitive("pyw"));
            assertTrue(UnifiedBlockGrammar.isIndentationSensitive("pyi"));
        }

        @Test
        @DisplayName("YAML es sensible a indentacion")
        void testYamlIsIndentSensitive() {
            assertTrue(UnifiedBlockGrammar.isIndentationSensitive("yaml"));
            assertTrue(UnifiedBlockGrammar.isIndentationSensitive("yml"));
        }

        @Test
        @DisplayName("Haml/Slim/Pug son sensibles a indentacion")
        void testTemplateLanguagesIndentSensitive() {
            assertTrue(UnifiedBlockGrammar.isIndentationSensitive("haml"));
            assertTrue(UnifiedBlockGrammar.isIndentationSensitive("slim"));
            assertTrue(UnifiedBlockGrammar.isIndentationSensitive("pug"));
        }

        @Test
        @DisplayName("Java NO es sensible a indentacion")
        void testJavaNotIndentSensitive() {
            assertFalse(UnifiedBlockGrammar.isIndentationSensitive("java"));
        }

        @Test
        @DisplayName("JavaScript NO es sensible a indentacion")
        void testJsNotIndentSensitive() {
            assertFalse(UnifiedBlockGrammar.isIndentationSensitive("js"));
            assertFalse(UnifiedBlockGrammar.isIndentationSensitive("ts"));
        }

        @Test
        @DisplayName("Null retorna false")
        void testNullNotIndentSensitive() {
            assertFalse(UnifiedBlockGrammar.isIndentationSensitive(null));
        }

        @Test
        @DisplayName("Extension desconocida retorna false")
        void testUnknownNotIndentSensitive() {
            assertFalse(UnifiedBlockGrammar.isIndentationSensitive("xyz"));
        }
    }

    @Nested
    @DisplayName("Ciclo 8 Grado Militar: Parser Tolerante Tests")
    class TolerantParserTests {
        @Test
        @DisplayName("Parsea bloque con 6 caracteres < (tolerancia)")
        void testParseSixLessThanChars() {
            String input = """
                <<<<<< SEARCH
                old code
                =======
                new code
                >>>>>> REPLACE
                """;

            Optional<EditBlock> result = UnifiedBlockGrammar.parse(input);

            assertTrue(result.isPresent());
            assertEquals("old code", result.get().search());
            assertEquals("new code", result.get().replace());
        }

        @Test
        @DisplayName("Parsea bloque con 5 caracteres < (tolerancia minima)")
        void testParseFiveLessThanChars() {
            String input = """
                <<<<< SEARCH
                old code
                =====
                new code
                >>>>> REPLACE
                """;

            Optional<EditBlock> result = UnifiedBlockGrammar.parse(input);

            assertTrue(result.isPresent());
            assertEquals("old code", result.get().search());
            assertEquals("new code", result.get().replace());
        }

        @Test
        @DisplayName("Parsea bloque con espacios extra en delimitadores")
        void testParseWithExtraSpaces() {
            String input = """
                <<<<<<< SEARCH
                old code
                =======
                new code
                >>>>>>> REPLACE
                """;

            Optional<EditBlock> result = UnifiedBlockGrammar.parse(input);

            assertTrue(result.isPresent());
            assertEquals("old code", result.get().search());
        }

        @Test
        @DisplayName("Parsea bloque con SEARCH en minusculas (tolerancia)")
        void testParseLowercaseSearch() {
            String input = """
                <<<<<< search
                old code
                ======
                new code
                >>>>>> replace
                """;

            Optional<EditBlock> result = UnifiedBlockGrammar.parse(input);

            assertTrue(result.isPresent());
            assertEquals("old code", result.get().search());
        }

        @Test
        @DisplayName("Fallback a regex cuando parser tolerante falla")
        void testFallbackToRegex() {
            String input = """
                <<<<<<< SEARCH
                code here
                =======
                new code
                >>>>>>> REPLACE
                """;

            Optional<EditBlock> result = UnifiedBlockGrammar.parse(input);
            assertTrue(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Ciclo 8 Grado Militar: Indentation Detection Tests")
    class IndentationDetectionTests {
        @Test
        @DisplayName("Detecta indentacion con tabs")
        void testDetectTabs() {
            String content = """
                def hello():
                \tprint("hello")
                \tif True:
                \t\treturn
                """;

            assertEquals(
                UnifiedBlockGrammar.IndentationStyle.TABS,
                UnifiedBlockGrammar.detectIndentation(content)
            );
        }

        @Test
        @DisplayName("Detecta indentacion con 4 espacios")
        void testDetect4Spaces() {
            String content = """
                def hello():
                    print("hello")
                    if True:
                        return
                """;

            assertEquals(
                UnifiedBlockGrammar.IndentationStyle.SPACES_4,
                UnifiedBlockGrammar.detectIndentation(content)
            );
        }

        @Test
        @DisplayName("Detecta indentacion con 2 espacios")
        void testDetect2Spaces() {
            String content = """
                def hello():
                  print("hello")
                  if True:
                    return
                """;

            assertEquals(
                UnifiedBlockGrammar.IndentationStyle.SPACES_2,
                UnifiedBlockGrammar.detectIndentation(content)
            );
        }

        @Test
        @DisplayName("Retorna UNKNOWN para archivo sin indentacion")
        void testDetectNoIndentation() {
            String content = """
                x = 1
                y = 2
                z = 3
                """;

            assertEquals(
                UnifiedBlockGrammar.IndentationStyle.UNKNOWN,
                UnifiedBlockGrammar.detectIndentation(content)
            );
        }

        @Test
        @DisplayName("Retorna UNKNOWN para null")
        void testDetectNull() {
            assertEquals(
                UnifiedBlockGrammar.IndentationStyle.UNKNOWN,
                UnifiedBlockGrammar.detectIndentation(null)
            );
        }

        @Test
        @DisplayName("Retorna UNKNOWN para string vacio")
        void testDetectEmpty() {
            assertEquals(
                UnifiedBlockGrammar.IndentationStyle.UNKNOWN,
                UnifiedBlockGrammar.detectIndentation("")
            );
        }
    }

    @Nested
    @DisplayName("Ciclo 8 Grado Militar: Indentation Normalization Tests")
    class IndentationNormalizationTests {
        @Test
        @DisplayName("Normaliza tabs a 4 espacios")
        void testNormalizeTabsToSpaces() {
            String code = "\tprint('hello')\n\t\treturn";

            String normalized = UnifiedBlockGrammar.normalizeIndentation(
                code,
                UnifiedBlockGrammar.IndentationStyle.SPACES_4
            );

            assertTrue(normalized.startsWith("    print"));
            assertTrue(normalized.contains("        return"));
        }

        @Test
        @DisplayName("Normaliza 4 espacios a tabs")
        void testNormalizeSpacesToTabs() {
            String code = "    print('hello')\n        return";

            String normalized = UnifiedBlockGrammar.normalizeIndentation(
                code,
                UnifiedBlockGrammar.IndentationStyle.TABS
            );

            assertTrue(normalized.startsWith("\tprint"));
            assertTrue(normalized.contains("\t\treturn"));
        }

        @Test
        @DisplayName("Retorna codigo sin cambios si estilo es UNKNOWN")
        void testNormalizeUnknownReturnsOriginal() {
            String code = "    print('hello')";

            String normalized = UnifiedBlockGrammar.normalizeIndentation(
                code,
                UnifiedBlockGrammar.IndentationStyle.UNKNOWN
            );

            assertEquals(code, normalized);
        }

        @Test
        @DisplayName("Maneja null correctamente")
        void testNormalizeNull() {
            assertNull(UnifiedBlockGrammar.normalizeIndentation(
                null,
                UnifiedBlockGrammar.IndentationStyle.SPACES_4
            ));
        }

        @Test
        @DisplayName("Preserva ancho visual al normalizar (Grado Militar)")
        void testNormalizePreservesVisualWidth() {
            String code = "  print('hello')\n    return";

            String normalized = UnifiedBlockGrammar.normalizeIndentation(
                code,
                UnifiedBlockGrammar.IndentationStyle.SPACES_4
            );

            assertTrue(normalized.startsWith("  print"));
            assertTrue(normalized.contains("    return"));
        }
    }
}
