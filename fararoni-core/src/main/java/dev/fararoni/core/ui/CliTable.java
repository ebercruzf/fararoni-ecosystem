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

import dev.fararoni.core.ui.renderers.LiveProgressRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class CliTable {
    private static final char CORNER_TOP_LEFT = '┌';
    private static final char CORNER_TOP_RIGHT = '┐';
    private static final char CORNER_BOTTOM_LEFT = '└';
    private static final char CORNER_BOTTOM_RIGHT = '┘';
    private static final char LINE_HORIZONTAL = '─';
    private static final char LINE_VERTICAL = '│';
    private static final char T_TOP = '┬';
    private static final char T_BOTTOM = '┴';
    private static final char T_LEFT = '├';
    private static final char T_RIGHT = '┤';
    private static final char CROSS = '┼';
    private static final int DEFAULT_PADDING = 1;

    private final List<String> headers;
    private final List<List<String>> rows;
    private int padding;

    public CliTable(String... headers) {
        Objects.requireNonNull(headers, "headers no puede ser null");
        if (headers.length == 0) {
            throw new IllegalArgumentException("Se requiere al menos un header");
        }
        for (int i = 0; i < headers.length; i++) {
            Objects.requireNonNull(headers[i],
                "Header en posicion " + i + " no puede ser null");
        }

        this.headers = new ArrayList<>(Arrays.asList(headers));
        this.rows = new ArrayList<>();
        this.padding = DEFAULT_PADDING;
    }

    public CliTable addRow(String... values) {
        if (values.length != headers.size()) {
            throw new IllegalArgumentException(
                String.format("Se esperaban %d valores pero se recibieron %d. " +
                              "La tabla tiene %d columnas: %s",
                              headers.size(), values.length, headers.size(), headers));
        }

        List<String> row = new ArrayList<>(values.length);
        for (String value : values) {
            row.add(value != null ? value : "");
        }
        rows.add(row);
        return this;
    }

    public CliTable withPadding(int padding) {
        if (padding < 0) {
            throw new IllegalArgumentException(
                "padding no puede ser negativo, recibido: " + padding);
        }
        this.padding = padding;
        return this;
    }

    public String render() {
        int[] columnWidths = calculateColumnWidths();
        StringBuilder sb = new StringBuilder();

        appendHorizontalLine(sb, columnWidths, CORNER_TOP_LEFT, T_TOP, CORNER_TOP_RIGHT);

        appendDataRow(sb, headers, columnWidths);

        if (!rows.isEmpty()) {
            appendHorizontalLine(sb, columnWidths, T_LEFT, CROSS, T_RIGHT);

            for (List<String> row : rows) {
                appendDataRow(sb, row, columnWidths);
            }
        }

        appendHorizontalLine(sb, columnWidths, CORNER_BOTTOM_LEFT, T_BOTTOM, CORNER_BOTTOM_RIGHT);

        return sb.toString();
    }

    private int[] calculateColumnWidths() {
        int numColumns = headers.size();
        int[] widths = new int[numColumns];

        for (int i = 0; i < numColumns; i++) {
            widths[i] = headers.get(i).length();
        }

        for (List<String> row : rows) {
            for (int i = 0; i < numColumns; i++) {
                int cellWidth = row.get(i).length();
                if (cellWidth > widths[i]) {
                    widths[i] = cellWidth;
                }
            }
        }

        return widths;
    }

    private void appendHorizontalLine(StringBuilder sb, int[] widths,
                                      char left, char middle, char right) {
        sb.append(left);
        for (int i = 0; i < widths.length; i++) {
            int totalWidth = widths[i] + (padding * 2);
            for (int j = 0; j < totalWidth; j++) {
                sb.append(LINE_HORIZONTAL);
            }

            if (i < widths.length - 1) {
                sb.append(middle);
            }
        }
        sb.append(right).append('\n');
    }

    private void appendDataRow(StringBuilder sb, List<String> values, int[] widths) {
        sb.append(LINE_VERTICAL);
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            String paddingSpaces = " ".repeat(padding);

            sb.append(paddingSpaces);

            sb.append(value);

            int spacesToFill = widths[i] - value.length();
            for (int j = 0; j < spacesToFill; j++) {
                sb.append(' ');
            }

            sb.append(paddingSpaces);

            sb.append(LINE_VERTICAL);
        }
        sb.append('\n');
    }

    public int getColumnCount() {
        return headers.size();
    }

    public int getRowCount() {
        return rows.size();
    }

    public boolean hasRows() {
        return !rows.isEmpty();
    }

    public List<String> getHeaders() {
        return List.copyOf(headers);
    }
}
