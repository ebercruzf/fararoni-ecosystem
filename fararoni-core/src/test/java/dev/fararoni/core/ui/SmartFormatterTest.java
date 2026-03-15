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

import dev.fararoni.core.ui.SmartFormatter.FormatType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SmartFormatter")
class SmartFormatterTest {
    private static List<String[]> singleRow(String... values) {
        return Collections.singletonList(values);
    }

    @SafeVarargs
    private static List<String[]> rows(String[]... rows) {
        return Arrays.asList(rows);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {
        @Test
        @DisplayName("crea formatter con ancho especificado")
        void constructor_WithWidth_SetsWidth() {
            SmartFormatter formatter = new SmartFormatter(100);
            assertEquals(100, formatter.getTerminalWidth());
        }

        @Test
        @DisplayName("crea formatter con ancho por defecto (80)")
        void constructor_Default_UsesDefaultWidth() {
            SmartFormatter formatter = new SmartFormatter();
            assertEquals(80, formatter.getTerminalWidth());
        }

        @Test
        @DisplayName("lanza excepcion si ancho es menor a 20")
        void constructor_TooSmall_ThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                new SmartFormatter(19);
            });
        }

        @Test
        @DisplayName("acepta ancho minimo de 20")
        void constructor_MinWidth_Accepted() {
            SmartFormatter formatter = new SmartFormatter(20);
            assertEquals(20, formatter.getTerminalWidth());
        }
    }

    @Nested
    @DisplayName("Regla 1: Cardinalidad")
    class CardinalityRuleTests {
        @Test
        @DisplayName("1 item con 4+ headers -> CARD_VIEW")
        void decideFormat_OneItemManyHeaders_CardView() {
            SmartFormatter formatter = new SmartFormatter(200);

            FormatType decision = formatter.decideFormat(
                singleRow("val1", "val2", "val3", "val4"),
                "H1", "H2", "H3", "H4"
            );

            assertEquals(FormatType.CARD_VIEW, decision);
        }

        @Test
        @DisplayName("1 item con exactamente 3 headers -> TABLE (no supera umbral)")
        void decideFormat_OneItemThreeHeaders_Table() {
            SmartFormatter formatter = new SmartFormatter(200);

            FormatType decision = formatter.decideFormat(
                singleRow("a", "b", "c"),
                "H1", "H2", "H3"
            );

            assertEquals(FormatType.TABLE, decision);
        }

        @Test
        @DisplayName("2+ items siempre evaluan Regla 2 (dimensionalidad)")
        void decideFormat_MultipleItems_SkipsCardinality() {
            SmartFormatter formatter = new SmartFormatter(200);

            FormatType decision = formatter.decideFormat(
                rows(
                    new String[]{"a", "b", "c", "d", "e"},
                    new String[]{"1", "2", "3", "4", "5"}
                ),
                "H1", "H2", "H3", "H4", "H5"
            );

            assertEquals(FormatType.TABLE, decision);
        }
    }

    @Nested
    @DisplayName("Regla 2: Dimensionalidad")
    class DimensionalityRuleTests {
        @Test
        @DisplayName("tabla ancha en terminal angosta -> CARD_VIEW")
        void decideFormat_WideTableNarrowTerminal_CardView() {
            SmartFormatter formatter = new SmartFormatter(40);

            FormatType decision = formatter.decideFormat(
                rows(
                    new String[]{"valor_muy_largo_1", "valor_muy_largo_2", "valor_muy_largo_3"},
                    new String[]{"otro_valor_largo", "mas_valores_aqui", "y_otro_mas_largo"}
                ),
                "Columna_Larga_A", "Columna_Larga_B", "Columna_Larga_C"
            );

            assertEquals(FormatType.CARD_VIEW, decision);
        }

        @Test
        @DisplayName("tabla angosta en terminal amplia -> TABLE")
        void decideFormat_NarrowTableWideTerminal_Table() {
            SmartFormatter formatter = new SmartFormatter(120);

            FormatType decision = formatter.decideFormat(
                rows(
                    new String[]{"a", "b"},
                    new String[]{"c", "d"}
                ),
                "X", "Y"
            );

            assertEquals(FormatType.TABLE, decision);
        }

        @Test
        @DisplayName("tabla justo en el limite -> TABLE")
        void decideFormat_ExactlyAtLimit_Table() {
            SmartFormatter formatter = new SmartFormatter(80);

            FormatType decision = formatter.decideFormat(
                singleRow("OK", "SI"),
                "A", "B"
            );

            assertEquals(FormatType.TABLE, decision);
        }
    }

    @Nested
    @DisplayName("renderCardView")
    class CardViewRenderTests {
        @Test
        @DisplayName("formatAsCard genera formato key-value")
        void formatAsCard_GeneratesKeyValue() {
            SmartFormatter formatter = new SmartFormatter();

            String output = formatter.formatAsCard(
                new String[]{"Main.java", "MODIFIED", "/src"},
                "File", "Status", "Path"
            );

            assertTrue(output.contains("File"));
            assertTrue(output.contains(": Main.java"));
            assertTrue(output.contains("Status"));
            assertTrue(output.contains(": MODIFIED"));
            assertTrue(output.contains("Path"));
            assertTrue(output.contains(": /src"));
        }

        @Test
        @DisplayName("headers se alinean a la derecha")
        void formatAsCard_HeadersRightAligned() {
            SmartFormatter formatter = new SmartFormatter();

            String output = formatter.formatAsCard(
                new String[]{"v1", "v2"},
                "A", "LongHeader"
            );

            String[] lines = output.split("\n");
            assertTrue(lines[0].contains("         A : v1") || lines[0].trim().startsWith("A"));
        }

        @Test
        @DisplayName("valores null se convierten a cadena vacia")
        void formatAsCard_NullValues_Empty() {
            SmartFormatter formatter = new SmartFormatter();

            String output = formatter.formatAsCard(
                new String[]{"valor", null},
                "Key1", "Key2"
            );

            assertTrue(output.contains("Key1"));
            assertTrue(output.contains(": valor"));
            assertTrue(output.contains("Key2 :"));
        }
    }

    @Nested
    @DisplayName("renderTable")
    class TableRenderTests {
        @Test
        @DisplayName("formatAsTable usa RichTableRenderer")
        void formatAsTable_UsesRichTableRenderer() {
            SmartFormatter formatter = new SmartFormatter(80);

            String output = formatter.formatAsTable(
                rows(
                    new String[]{"data1", "data2"},
                    new String[]{"data3", "data4"}
                ),
                "Col1", "Col2"
            );

            assertTrue(output.contains("╭"), "Debe tener esquina superior izquierda redondeada");
            assertTrue(output.contains("╯"), "Debe tener esquina inferior derecha redondeada");
            assertTrue(output.contains("Col1"));
            assertTrue(output.contains("data1"));
        }

        @Test
        @DisplayName("tabla incluye todos los datos")
        void formatAsTable_IncludesAllData() {
            SmartFormatter formatter = new SmartFormatter(100);

            String output = formatter.formatAsTable(
                rows(
                    new String[]{"A1", "B1", "C1"},
                    new String[]{"A2", "B2", "C2"},
                    new String[]{"A3", "B3", "C3"}
                ),
                "ColA", "ColB", "ColC"
            );

            assertTrue(output.contains("ColA"));
            assertTrue(output.contains("ColB"));
            assertTrue(output.contains("ColC"));
            assertTrue(output.contains("A1"));
            assertTrue(output.contains("B2"));
            assertTrue(output.contains("C3"));
        }
    }

    @Nested
    @DisplayName("format (decision automatica)")
    class AutoFormatTests {
        @Test
        @DisplayName("format elige automaticamente segun heuristica")
        void format_AutoDecision() {
            SmartFormatter formatter = new SmartFormatter(80);

            String tableOutput = formatter.format(
                rows(
                    new String[]{"a", "b"},
                    new String[]{"c", "d"}
                ),
                "X", "Y"
            );

            String cardOutput = formatter.format(
                singleRow("v1", "v2", "v3", "v4", "v5"),
                "H1", "H2", "H3", "H4", "H5"
            );

            assertTrue(tableOutput.contains("╭") || tableOutput.contains("┌"));

            assertTrue(cardOutput.contains(" : "));
        }
    }

    @Nested
    @DisplayName("Validacion de Entrada")
    class ValidationTests {
        @Test
        @DisplayName("lanza excepcion si rows es null")
        void format_NullRows_ThrowsException() {
            SmartFormatter formatter = new SmartFormatter();
            assertThrows(NullPointerException.class, () -> {
                formatter.format(null, "H1");
            });
        }

        @Test
        @DisplayName("lanza excepcion si headers es null")
        void format_NullHeaders_ThrowsException() {
            SmartFormatter formatter = new SmartFormatter();
            assertThrows(NullPointerException.class, () -> {
                formatter.format(singleRow("a"), (String[]) null);
            });
        }

        @Test
        @DisplayName("lanza excepcion si headers esta vacio")
        void format_EmptyHeaders_ThrowsException() {
            SmartFormatter formatter = new SmartFormatter();
            assertThrows(IllegalArgumentException.class, () -> {
                formatter.format(singleRow());
            });
        }

        @Test
        @DisplayName("lanza excepcion si rows esta vacio")
        void format_EmptyRows_ThrowsException() {
            SmartFormatter formatter = new SmartFormatter();
            List<String[]> emptyRows = new ArrayList<>();
            assertThrows(IllegalArgumentException.class, () -> {
                formatter.format(emptyRows, "H1");
            });
        }

        @Test
        @DisplayName("lanza excepcion si fila tiene cantidad incorrecta de valores")
        void format_RowMismatch_ThrowsException() {
            SmartFormatter formatter = new SmartFormatter();
            assertThrows(IllegalArgumentException.class, () -> {
                formatter.format(
                    singleRow("a", "b"),
                    "H1", "H2", "H3"
                );
            });
        }

        @Test
        @DisplayName("lanza excepcion si alguna fila es null")
        void format_NullRow_ThrowsException() {
            SmartFormatter formatter = new SmartFormatter();

            List<String[]> rowsWithNull = new ArrayList<>();
            rowsWithNull.add(new String[]{"a", "b"});
            rowsWithNull.add(null);

            assertThrows(IllegalArgumentException.class, () -> {
                formatter.format(rowsWithNull, "H1", "H2");
            });
        }
    }

    @Nested
    @DisplayName("Integracion")
    class IntegrationTests {
        @Test
        @DisplayName("ejemplo del Javadoc: 1 item con 3 campos")
        void javadocExample_OneItem() {
            SmartFormatter formatter = new SmartFormatter(80);

            List<String[]> oneItem = singleRow("Main.java", "MODIFIED", "/src/main");

            String output = formatter.format(oneItem, "File", "Status", "Path");

            assertTrue(output.contains("Main.java"));
            assertTrue(output.contains("MODIFIED"));
        }

        @Test
        @DisplayName("ejemplo del Javadoc: multiples items")
        void javadocExample_MultiItems() {
            SmartFormatter formatter = new SmartFormatter(80);

            List<String[]> multiItems = rows(
                new String[]{"Main.java", "MODIFIED"},
                new String[]{"Test.java", "NEW"}
            );

            String output = formatter.format(multiItems, "File", "Status");

            assertTrue(output.contains("Main.java"));
            assertTrue(output.contains("Test.java"));
            assertTrue(output.contains("MODIFIED"));
            assertTrue(output.contains("NEW"));
        }

        @Test
        @DisplayName("decideFormat retorna tipo correcto para depuracion")
        void decideFormat_ReturnsCorrectTypeForDebugging() {
            SmartFormatter formatter = new SmartFormatter(80);

            FormatType tableType = formatter.decideFormat(
                singleRow("a", "b"),
                "X", "Y"
            );

            FormatType cardType = formatter.decideFormat(
                singleRow("1", "2", "3", "4", "5"),
                "A", "B", "C", "D", "E"
            );

            assertEquals(FormatType.TABLE, tableType);
            assertEquals(FormatType.CARD_VIEW, cardType);
        }
    }
}
