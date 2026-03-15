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
package dev.fararoni.core.ui.renderers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("RichTableRenderer")
class RichTableRendererTest {
    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {
        @Test
        @DisplayName("crea renderer con ancho especificado")
        void constructor_WithWidth_SetsWidth() {
            RichTableRenderer renderer = new RichTableRenderer(100);
            assertEquals(100, renderer.getTerminalWidth());
        }

        @Test
        @DisplayName("crea renderer con ancho por defecto (80)")
        void constructor_Default_UsesDefaultWidth() {
            RichTableRenderer renderer = new RichTableRenderer();
            assertEquals(80, renderer.getTerminalWidth());
        }

        @Test
        @DisplayName("lanza excepcion si ancho es menor a 20")
        void constructor_TooSmall_ThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                new RichTableRenderer(19);
            });
        }
    }

    @Nested
    @DisplayName("headers")
    class HeadersTests {
        @Test
        @DisplayName("define headers correctamente")
        void headers_ValidHeaders_SetsHeaders() {
            RichTableRenderer renderer = new RichTableRenderer()
                .headers("A", "B", "C");

            assertEquals(3, renderer.getColumnCount());
        }

        @Test
        @DisplayName("lanza excepcion si no hay headers")
        void headers_Empty_ThrowsException() {
            RichTableRenderer renderer = new RichTableRenderer();
            assertThrows(IllegalArgumentException.class, () -> {
                renderer.headers();
            });
        }

        @Test
        @DisplayName("lanza excepcion si headers es null")
        void headers_Null_ThrowsException() {
            RichTableRenderer renderer = new RichTableRenderer();
            assertThrows(IllegalArgumentException.class, () -> {
                renderer.headers((String[]) null);
            });
        }
    }

    @Nested
    @DisplayName("row")
    class RowTests {
        @Test
        @DisplayName("agrega fila correctamente")
        void row_ValidRow_AddsRow() {
            RichTableRenderer renderer = new RichTableRenderer()
                .headers("A", "B")
                .row("1", "2");

            assertEquals(1, renderer.getRowCount());
        }

        @Test
        @DisplayName("lanza excepcion si no hay headers definidos")
        void row_NoHeaders_ThrowsException() {
            RichTableRenderer renderer = new RichTableRenderer();
            assertThrows(IllegalStateException.class, () -> {
                renderer.row("1", "2");
            });
        }

        @Test
        @DisplayName("lanza excepcion si numero de valores no coincide")
        void row_WrongCount_ThrowsException() {
            RichTableRenderer renderer = new RichTableRenderer()
                .headers("A", "B", "C");

            assertThrows(IllegalArgumentException.class, () -> {
                renderer.row("1", "2");
            });
        }

        @Test
        @DisplayName("convierte null a cadena vacia")
        void row_NullValue_ConvertsToEmpty() {
            RichTableRenderer renderer = new RichTableRenderer()
                .headers("A", "B")
                .row("1", null);

            String output = renderer.render();
            assertTrue(output.contains("1"));
        }
    }

    @Nested
    @DisplayName("render - Bordes Redondeados")
    class RenderBordersTests {
        @Test
        @DisplayName("usa esquinas redondeadas")
        void render_RoundedCorners() {
            RichTableRenderer renderer = new RichTableRenderer()
                .headers("Test")
                .row("data");

            String output = renderer.render();

            assertTrue(output.contains("╭"), "Esquina superior izquierda");
            assertTrue(output.contains("╮"), "Esquina superior derecha");
            assertTrue(output.contains("╰"), "Esquina inferior izquierda");
            assertTrue(output.contains("╯"), "Esquina inferior derecha");
        }

        @Test
        @DisplayName("usa intersecciones correctas")
        void render_CorrectIntersections() {
            RichTableRenderer renderer = new RichTableRenderer()
                .headers("A", "B")
                .row("1", "2");

            String output = renderer.render();

            assertTrue(output.contains("┬"), "T superior");
            assertTrue(output.contains("┴"), "T inferior");
            assertTrue(output.contains("├"), "T izquierda");
            assertTrue(output.contains("┤"), "T derecha");
            assertTrue(output.contains("┼"), "Cruz central");
        }
    }

    @Nested
    @DisplayName("render - Text Wrapping")
    class TextWrappingTests {
        @Test
        @DisplayName("divide texto largo en multiples lineas")
        void render_LongText_Wraps() {
            RichTableRenderer renderer = new RichTableRenderer(40)
                .headers("Columna")
                .row("Este es un texto muy largo que debe dividirse en multiples lineas");

            String output = renderer.render();
            String[] lines = output.split("\n");

            assertTrue(lines.length > 4, "Debe tener multiples lineas de datos");
        }

        @Test
        @DisplayName("texto corto no se divide")
        void render_ShortText_NoWrap() {
            RichTableRenderer renderer = new RichTableRenderer(80)
                .headers("Col")
                .row("Corto");

            String output = renderer.render();
            String[] lines = output.split("\n");
            assertEquals(5, lines.length);
        }
    }

    @Nested
    @DisplayName("stripAnsi / visualLength")
    class AnsiTests {
        @Test
        @DisplayName("stripAnsi elimina codigos de color")
        void stripAnsi_RemovesColorCodes() {
            String withAnsi = "\u001B[31mRojo\u001B[0m";
            String stripped = RichTableRenderer.stripAnsi(withAnsi);

            assertEquals("Rojo", stripped);
        }

        @Test
        @DisplayName("stripAnsi maneja texto sin ANSI")
        void stripAnsi_PlainText_Unchanged() {
            String plain = "Texto normal";
            assertEquals(plain, RichTableRenderer.stripAnsi(plain));
        }

        @Test
        @DisplayName("stripAnsi maneja null")
        void stripAnsi_Null_ReturnsEmpty() {
            assertEquals("", RichTableRenderer.stripAnsi(null));
        }

        @Test
        @DisplayName("visualLength ignora codigos ANSI")
        void visualLength_IgnoresAnsi() {
            String withAnsi = "\u001B[32mVerde\u001B[0m";

            assertEquals(5, RichTableRenderer.visualLength(withAnsi));
            assertEquals("Verde".length(), RichTableRenderer.visualLength(withAnsi));
        }

        @Test
        @DisplayName("visualLength de texto normal")
        void visualLength_PlainText() {
            assertEquals(4, RichTableRenderer.visualLength("Test"));
        }

        @Test
        @DisplayName("render con texto coloreado calcula anchos correctamente")
        void render_ColoredText_CorrectWidths() {
            String redText = "\u001B[31mRojo\u001B[0m";
            String greenText = "\u001B[32mVerde\u001B[0m";

            RichTableRenderer renderer = new RichTableRenderer(60)
                .headers("Color")
                .row(redText)
                .row(greenText);

            String output = renderer.render();
            String[] lines = output.split("\n");

            int firstLen = RichTableRenderer.visualLength(lines[0]);
            for (String line : lines) {
                assertEquals(firstLen, RichTableRenderer.visualLength(line),
                    "Linea con ancho diferente: " + line);
            }
        }
    }

    @Nested
    @DisplayName("Configuracion")
    class ConfigurationTests {
        @Test
        @DisplayName("withPadding configura padding")
        void withPadding_SetsPadding() {
            RichTableRenderer renderer = new RichTableRenderer()
                .headers("A")
                .withPadding(0)
                .row("x");

            String output = renderer.render();
            assertTrue(output.contains("│x│"));
        }

        @Test
        @DisplayName("withPadding negativo lanza excepcion")
        void withPadding_Negative_ThrowsException() {
            RichTableRenderer renderer = new RichTableRenderer();
            assertThrows(IllegalArgumentException.class, () -> {
                renderer.withPadding(-1);
            });
        }

        @Test
        @DisplayName("withRowSeparators false no muestra separadores")
        void withRowSeparators_False_NoSeparators() {
            RichTableRenderer renderer = new RichTableRenderer()
                .headers("A")
                .withRowSeparators(false)
                .row("1")
                .row("2")
                .row("3");

            String output = renderer.render();
            long separatorCount = output.chars().filter(c -> c == '├').count();
            assertEquals(1, separatorCount);
        }

        @Test
        @DisplayName("withRowSeparators true muestra separadores entre filas")
        void withRowSeparators_True_ShowsSeparators() {
            RichTableRenderer renderer = new RichTableRenderer()
                .headers("A")
                .withRowSeparators(true)
                .row("1")
                .row("2")
                .row("3");

            String output = renderer.render();
            long separatorCount = output.chars().filter(c -> c == '├').count();
            assertEquals(3, separatorCount);
        }
    }

    @Nested
    @DisplayName("Integracion")
    class IntegrationTests {
        @Test
        @DisplayName("render sin headers retorna vacio")
        void render_NoHeaders_ReturnsEmpty() {
            RichTableRenderer renderer = new RichTableRenderer();
            assertEquals("", renderer.render());
        }

        @Test
        @DisplayName("render solo headers sin filas")
        void render_OnlyHeaders_Works() {
            RichTableRenderer renderer = new RichTableRenderer()
                .headers("A", "B", "C");

            String output = renderer.render();

            assertTrue(output.contains("A"));
            assertTrue(output.contains("B"));
            assertTrue(output.contains("C"));
            assertFalse(output.contains("├"));
        }

        @Test
        @DisplayName("ejemplo del Javadoc funciona")
        void render_JavadocExample_Works() {
            RichTableRenderer renderer = new RichTableRenderer(80)
                .headers("MODULO", "ESTADO", "DETALLE")
                .row("Fararoni Core", "ACTIVO", "Sistema principal")
                .row("Enterprise", "INACTIVO", "Pendiente");

            String output = renderer.render();

            assertTrue(output.contains("MODULO"));
            assertTrue(output.contains("ESTADO"));
            assertTrue(output.contains("DETALLE"));
            assertTrue(output.contains("Fararoni Core"));
            assertTrue(output.contains("ACTIVO"));
            assertTrue(output.contains("Enterprise"));
        }

        @Test
        @DisplayName("encadenamiento fluido funciona")
        void fluentChaining_Works() {
            String output = new RichTableRenderer(60)
                .headers("X", "Y")
                .withPadding(1)
                .withRowSeparators(false)
                .row("a", "b")
                .row("c", "d")
                .render();

            assertTrue(output.contains("a"));
            assertTrue(output.contains("d"));
        }
    }
}
