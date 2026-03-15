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
package dev.fararoni.core.core.indexing;

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
@DisplayName("HtmlScriptExtractor Tests - Paso 7: Multi-Layer Parsing")
class HtmlScriptExtractorTest {
    private HtmlScriptExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new HtmlScriptExtractor();
    }

    @Nested
    @DisplayName("Extraccion de Scripts JavaScript")
    class JavaScriptExtractionTests {
        @Test
        @DisplayName("Extrae script inline simple")
        void testExtractSimpleScript() {
            String html = """
                <!DOCTYPE html>
                <html>
                <body>
                <script>
                function hello() {
                    console.log("Hello");
                }
                </script>
                </body>
                </html>
                """;

            List<HtmlScriptExtractor.ScriptBlock> scripts = extractor.extract(html);

            assertEquals(1, scripts.size());
            assertTrue(scripts.get(0).content().contains("function hello()"));
            assertEquals(HtmlScriptExtractor.ScriptType.JAVASCRIPT, scripts.get(0).type());
        }

        @Test
        @DisplayName("Extrae multiples scripts")
        void testExtractMultipleScripts() {
            String html = """
                <html>
                <body>
                <script>
                function first() {}
                </script>
                <script>
                function second() {}
                </script>
                </body>
                </html>
                """;

            List<HtmlScriptExtractor.ScriptBlock> scripts = extractor.extract(html);

            assertEquals(2, scripts.size());
            assertTrue(scripts.get(0).content().contains("first"));
            assertTrue(scripts.get(1).content().contains("second"));
        }

        @Test
        @DisplayName("Ignora scripts externos (con src)")
        void testIgnoresExternalScripts() {
            String html = """
                <html>
                <body>
                <script src="external.js"></script>
                <script>
                function internal() {}
                </script>
                </body>
                </html>
                """;

            List<HtmlScriptExtractor.ScriptBlock> scripts = extractor.extract(html);

            assertEquals(1, scripts.size());
            assertTrue(scripts.get(0).content().contains("internal"));
        }

        @Test
        @DisplayName("Detecta script type=module")
        void testDetectsModuleType() {
            String html = """
                <script type="module">
                import { something } from './module.js';
                </script>
                """;

            List<HtmlScriptExtractor.ScriptBlock> scripts = extractor.extract(html);

            assertEquals(1, scripts.size());
            assertEquals(HtmlScriptExtractor.ScriptType.MODULE, scripts.get(0).type());
            assertEquals("js", scripts.get(0).getExtension());
        }
    }

    @Nested
    @DisplayName("Extraccion de Scripts TypeScript")
    class TypeScriptExtractionTests {
        @Test
        @DisplayName("Detecta script lang=ts (Vue)")
        void testDetectsVueTypeScript() {
            String html = """
                <script lang="ts">
                interface User {
                    name: string;
                }
                function greet(user: User): void {
                    console.log(user.name);
                }
                </script>
                """;

            List<HtmlScriptExtractor.ScriptBlock> scripts = extractor.extract(html);

            assertEquals(1, scripts.size());
            assertEquals(HtmlScriptExtractor.ScriptType.TYPESCRIPT, scripts.get(0).type());
            assertEquals("ts", scripts.get(0).getExtension());
            assertTrue(scripts.get(0).isTypeScript());
        }

        @Test
        @DisplayName("Detecta script lang=typescript")
        void testDetectsFullTypeScriptLang() {
            String html = """
                <script lang="typescript">
                const x: number = 42;
                </script>
                """;

            List<HtmlScriptExtractor.ScriptBlock> scripts = extractor.extract(html);

            assertEquals(1, scripts.size());
            assertTrue(scripts.get(0).isTypeScript());
        }
    }

    @Nested
    @DisplayName("Casos Edge")
    class EdgeCasesTests {
        @Test
        @DisplayName("Maneja HTML sin scripts")
        void testNoScripts() {
            String html = """
                <html>
                <body>
                <h1>No scripts here</h1>
                </body>
                </html>
                """;

            List<HtmlScriptExtractor.ScriptBlock> scripts = extractor.extract(html);

            assertTrue(scripts.isEmpty());
        }

        @Test
        @DisplayName("Maneja scripts vacios")
        void testEmptyScripts() {
            String html = """
                <script></script>
                <script>   </script>
                """;

            List<HtmlScriptExtractor.ScriptBlock> scripts = extractor.extract(html);

            assertTrue(scripts.isEmpty());
        }

        @Test
        @DisplayName("Maneja null y vacio")
        void testNullAndEmpty() {
            assertTrue(extractor.extract(null).isEmpty());
            assertTrue(extractor.extract("").isEmpty());
            assertTrue(extractor.extract("   ").isEmpty());
        }

        @Test
        @DisplayName("Case insensitive para tags")
        void testCaseInsensitive() {
            String html = """
                <SCRIPT>
                function test() {}
                </SCRIPT>
                """;

            List<HtmlScriptExtractor.ScriptBlock> scripts = extractor.extract(html);

            assertEquals(1, scripts.size());
        }
    }

    @Nested
    @DisplayName("Utilidades")
    class UtilityMethodsTests {
        @Test
        @DisplayName("hasScripts detecta scripts")
        void testHasScripts() {
            assertTrue(extractor.hasScripts("<script>x</script>"));
            assertFalse(extractor.hasScripts("<html><body></body></html>"));
            assertFalse(extractor.hasScripts(null));
        }

        @Test
        @DisplayName("isHtmlLikeExtension detecta extensiones HTML")
        void testIsHtmlLikeExtension() {
            assertTrue(extractor.isHtmlLikeExtension("html"));
            assertTrue(extractor.isHtmlLikeExtension("htm"));
            assertTrue(extractor.isHtmlLikeExtension("vue"));
            assertTrue(extractor.isHtmlLikeExtension("svelte"));

            assertFalse(extractor.isHtmlLikeExtension("js"));
            assertFalse(extractor.isHtmlLikeExtension("java"));
            assertFalse(extractor.isHtmlLikeExtension(null));
        }

        @Test
        @DisplayName("ScriptBlock calcula tokenEstimate")
        void testTokenEstimate() {
            String html = """
                <script>
                function longFunction() {
                    const a = 1;
                    const b = 2;
                    return a + b;
                }
                </script>
                """;

            List<HtmlScriptExtractor.ScriptBlock> scripts = extractor.extract(html);

            assertTrue(scripts.get(0).tokenEstimate() > 0);
        }
    }
}
