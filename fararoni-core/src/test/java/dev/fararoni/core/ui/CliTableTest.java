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
package dev.fararoni.core.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("CliTable")
class CliTableTest {
    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {
        @Test
        @DisplayName("crea tabla con un solo header")
        void constructor_SingleHeader_CreatesTable() {
            CliTable table = new CliTable("Nombre");

            assertEquals(1, table.getColumnCount());
            assertEquals(0, table.getRowCount());
            assertFalse(table.hasRows());
        }

        @Test
        @DisplayName("crea tabla con multiples headers")
        void constructor_MultipleHeaders_CreatesTable() {
            CliTable table = new CliTable("Nombre", "Edad", "Ciudad");

            assertEquals(3, table.getColumnCount());
            assertEquals(List.of("Nombre", "Edad", "Ciudad"), table.getHeaders());
        }

        @Test
        @DisplayName("lanza NullPointerException si headers es null")
        void constructor_NullHeaders_ThrowsNPE() {
            assertThrows(NullPointerException.class, () -> {
                new CliTable((String[]) null);
            });
        }

        @Test
        @DisplayName("lanza IllegalArgumentException si no hay headers")
        void constructor_EmptyHeaders_ThrowsIAE() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                new CliTable();
            });
            assertTrue(ex.getMessage().contains("al menos un header"));
        }

        @Test
        @DisplayName("lanza NullPointerException si algun header es null")
        void constructor_NullHeaderValue_ThrowsNPE() {
            NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                new CliTable("Nombre", null, "Ciudad");
            });
            assertTrue(ex.getMessage().contains("posicion 1"));
        }
    }

    @Nested
    @DisplayName("addRow")
    class AddRowTests {
        @Test
        @DisplayName("agrega fila correctamente")
        void addRow_ValidRow_AddsRow() {
            CliTable table = new CliTable("Nombre", "Edad");
            table.addRow("Juan", "25");

            assertEquals(1, table.getRowCount());
            assertTrue(table.hasRows());
        }

        @Test
        @DisplayName("soporta encadenamiento fluido")
        void addRow_FluentChaining_Works() {
            CliTable table = new CliTable("A", "B")
                .addRow("1", "2")
                .addRow("3", "4")
                .addRow("5", "6");

            assertEquals(3, table.getRowCount());
        }

        @Test
        @DisplayName("lanza IllegalArgumentException si numero de valores no coincide")
        void addRow_WrongColumnCount_ThrowsIAE() {
            CliTable table = new CliTable("A", "B", "C");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                table.addRow("1", "2");
            });
            assertTrue(ex.getMessage().contains("3 valores"));
            assertTrue(ex.getMessage().contains("2"));
        }

        @Test
        @DisplayName("convierte valores null a cadena vacia")
        void addRow_NullValue_ConvertsToEmptyString() {
            CliTable table = new CliTable("Nombre", "Edad");
            table.addRow("Juan", null);

            String rendered = table.render();
            assertTrue(rendered.contains("Juan"));
        }
    }

    @Nested
    @DisplayName("withPadding")
    class PaddingTests {
        @Test
        @DisplayName("soporta padding cero")
        void withPadding_Zero_NoPadding() {
            CliTable table = new CliTable("A").withPadding(0);
            table.addRow("x");

            String rendered = table.render();
            assertTrue(rendered.contains("│x│"), "Debe tener contenido sin espacios");
        }

        @Test
        @DisplayName("soporta padding personalizado")
        void withPadding_Custom_AppliesPadding() {
            CliTable table = new CliTable("A").withPadding(3);
            table.addRow("x");

            String rendered = table.render();
            assertTrue(rendered.contains("│   x   │"), "Debe tener 3 espacios a cada lado");
        }

        @Test
        @DisplayName("lanza IllegalArgumentException si padding es negativo")
        void withPadding_Negative_ThrowsIAE() {
            CliTable table = new CliTable("A");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                table.withPadding(-1);
            });
            assertTrue(ex.getMessage().contains("negativo"));
        }

        @Test
        @DisplayName("permite encadenamiento fluido")
        void withPadding_FluentChaining_Works() {
            CliTable table = new CliTable("A", "B")
                .withPadding(2)
                .addRow("x", "y");

            assertEquals(1, table.getRowCount());
        }
    }

    @Nested
    @DisplayName("render - Estructura")
    class RenderStructureTests {
        @Test
        @DisplayName("renderiza tabla solo con headers (sin filas)")
        void render_OnlyHeaders_MinimalStructure() {
            CliTable table = new CliTable("Nombre", "Edad");

            String rendered = table.render();
            String[] lines = rendered.split("\n");

            assertEquals(3, lines.length);
            assertTrue(rendered.contains("Nombre"));
            assertTrue(rendered.contains("Edad"));
            assertFalse(rendered.contains("├"), "No debe tener separador sin filas");
        }

        @Test
        @DisplayName("renderiza tabla con una fila")
        void render_OneRow_IncludesSeparator() {
            CliTable table = new CliTable("Nombre", "Edad");
            table.addRow("Juan", "25");

            String rendered = table.render();

            assertTrue(rendered.contains("├"), "Debe tener separador con filas");
            assertTrue(rendered.contains("Juan"));
            assertTrue(rendered.contains("25"));
        }

        @Test
        @DisplayName("renderiza tabla con multiples filas")
        void render_MultipleRows_CorrectLineCount() {
            CliTable table = new CliTable("ID", "Nombre");
            table.addRow("1", "Uno")
                 .addRow("2", "Dos")
                 .addRow("3", "Tres");

            String rendered = table.render();
            String[] lines = rendered.split("\n");

            assertEquals(7, lines.length);
        }
    }

    @Nested
    @DisplayName("render - Bordes Unicode")
    class RenderUnicodeTests {
        @Test
        @DisplayName("usa esquinas correctas")
        void render_CorrectCorners() {
            CliTable table = new CliTable("X");
            String rendered = table.render();

            assertTrue(rendered.contains("┌"), "Esquina superior izquierda");
            assertTrue(rendered.contains("┐"), "Esquina superior derecha");
            assertTrue(rendered.contains("└"), "Esquina inferior izquierda");
            assertTrue(rendered.contains("┘"), "Esquina inferior derecha");
        }

        @Test
        @DisplayName("usa intersecciones T correctas")
        void render_CorrectTIntersections() {
            CliTable table = new CliTable("A", "B", "C");
            table.addRow("1", "2", "3");

            String rendered = table.render();

            assertTrue(rendered.contains("┬"), "T superior entre columnas");
            assertTrue(rendered.contains("┴"), "T inferior entre columnas");
            assertTrue(rendered.contains("┼"), "Cruz en separador");
            assertTrue(rendered.contains("├"), "T izquierda en separador");
            assertTrue(rendered.contains("┤"), "T derecha en separador");
        }

        @Test
        @DisplayName("usa lineas horizontales y verticales")
        void render_CorrectLines() {
            CliTable table = new CliTable("Test");
            String rendered = table.render();

            assertTrue(rendered.contains("─"), "Linea horizontal");
            assertTrue(rendered.contains("│"), "Linea vertical");
        }
    }

    @Nested
    @DisplayName("render - Calculo de Anchos")
    class RenderWidthTests {
        @Test
        @DisplayName("todas las lineas tienen el mismo ancho")
        void render_ConsistentLineWidth() {
            CliTable table = new CliTable("A", "Columna Larga");
            table.addRow("x", "Texto muy largo para probar");

            String rendered = table.render();
            String[] lines = rendered.split("\n");

            int expectedWidth = lines[0].length();
            for (int i = 1; i < lines.length; i++) {
                assertEquals(expectedWidth, lines[i].length(),
                    "Linea " + i + " tiene ancho diferente");
            }
        }

        @Test
        @DisplayName("ajusta ancho a datos mas largos que headers")
        void render_DataWiderThanHeader_Expands() {
            CliTable table = new CliTable("A");
            table.addRow("Texto largo");

            String rendered = table.render();
            assertTrue(rendered.contains("│ Texto largo │"));
        }

        @Test
        @DisplayName("ajusta ancho a headers mas largos que datos")
        void render_HeaderWiderThanData_Expands() {
            CliTable table = new CliTable("Header Largo");
            table.addRow("x");

            String rendered = table.render();
            String[] lines = rendered.split("\n");

            String dataRow = lines[3];
            assertTrue(dataRow.contains("x"));
            assertEquals(lines[1].length(), dataRow.length());
        }
    }

    @Nested
    @DisplayName("render - Alineacion")
    class RenderAlignmentTests {
        @Test
        @DisplayName("alinea contenido a la izquierda")
        void render_LeftAligned() {
            CliTable table = new CliTable("Nombre");
            table.addRow("AB");

            String rendered = table.render();
            assertTrue(rendered.contains("│ AB"));
        }

        @Test
        @DisplayName("maneja cadenas vacias correctamente")
        void render_EmptyStrings_Padded() {
            CliTable table = new CliTable("A", "B");
            table.addRow("", "valor");

            String rendered = table.render();
            assertTrue(rendered.contains("│"));
            assertTrue(rendered.contains("valor"));
        }
    }

    @Nested
    @DisplayName("Integracion")
    class IntegrationTests {
        @Test
        @DisplayName("renderiza ejemplo del Javadoc correctamente")
        void render_JavadocExample_Works() {
            CliTable table = new CliTable("Componente", "Ubicacion", "Esfuerzo");
            table.addRow("TaskTreeModel.java", "fararoni-agent-api/ui/model/", "Bajo");
            table.addRow("LiveProgressRenderer.java", "fararoni-core/ui/renderers/", "Alto");

            String rendered = table.render();

            assertTrue(rendered.contains("Componente"));
            assertTrue(rendered.contains("Ubicacion"));
            assertTrue(rendered.contains("Esfuerzo"));
            assertTrue(rendered.contains("TaskTreeModel.java"));
            assertTrue(rendered.contains("LiveProgressRenderer.java"));
            assertTrue(rendered.contains("Bajo"));
            assertTrue(rendered.contains("Alto"));

            String[] lines = rendered.split("\n");
            assertEquals(6, lines.length);
        }

        @Test
        @DisplayName("soporta caracteres Unicode en contenido")
        void render_UnicodeContent_Works() {
            CliTable table = new CliTable("Simbolo", "Descripcion");
            table.addRow("→", "Flecha derecha");
            table.addRow("✔", "Check");
            table.addRow("⚠", "Advertencia");

            String rendered = table.render();

            assertTrue(rendered.contains("→"));
            assertTrue(rendered.contains("✔"));
            assertTrue(rendered.contains("⚠"));
        }

        @Test
        @DisplayName("getHeaders retorna copia inmutable")
        void getHeaders_ReturnsImmutableCopy() {
            CliTable table = new CliTable("A", "B");
            List<String> headers = table.getHeaders();

            assertThrows(UnsupportedOperationException.class, () -> {
                headers.add("C");
            });
        }
    }
}
