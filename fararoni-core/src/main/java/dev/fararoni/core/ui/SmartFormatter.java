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

import dev.fararoni.core.ui.renderers.RichTableRenderer;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SmartFormatter {
    private static final int DEFAULT_TERMINAL_WIDTH = 80;

    private static final int HEADER_THRESHOLD_FOR_CARD = 3;

    private static final int MIN_COLUMN_OVERHEAD = 4;

    private static final String CARD_SEPARATOR = " : ";

    public enum FormatType {
        TABLE,
        CARD_VIEW
    }

    private final int terminalWidth;

    public SmartFormatter(int terminalWidth) {
        if (terminalWidth < 20) {
            throw new IllegalArgumentException(
                "terminalWidth debe ser al menos 20, recibido: " + terminalWidth);
        }
        this.terminalWidth = terminalWidth;
    }

    public SmartFormatter() {
        this(DEFAULT_TERMINAL_WIDTH);
    }

    public String format(List<String[]> rows, String... headers) {
        validateInput(rows, headers);

        FormatType formatType = decideFormat(rows, headers);

        return switch (formatType) {
            case CARD_VIEW -> renderCardView(rows, headers);
            case TABLE -> renderTable(rows, headers);
        };
    }

    public String formatAsCard(String[] values, String... headers) {
        List<String[]> singleRow = Collections.singletonList(values);
        validateInput(singleRow, headers);
        return renderCardView(singleRow, headers);
    }

    public String formatAsTable(List<String[]> rows, String... headers) {
        validateInput(rows, headers);
        return renderTable(rows, headers);
    }

    public FormatType decideFormat(List<String[]> rows, String... headers) {
        if (rows.size() == 1 && headers.length > HEADER_THRESHOLD_FOR_CARD) {
            return FormatType.CARD_VIEW;
        }

        int estimatedWidth = estimateTableWidth(rows, headers);
        if (estimatedWidth > terminalWidth) {
            return FormatType.CARD_VIEW;
        }

        return FormatType.TABLE;
    }

    private String renderCardView(List<String[]> rows, String[] headers) {
        StringBuilder sb = new StringBuilder();

        int maxHeaderWidth = 0;
        for (String header : headers) {
            maxHeaderWidth = Math.max(maxHeaderWidth, header.length());
        }

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            String[] row = rows.get(rowIdx);

            if (rowIdx > 0) {
                sb.append("\n");
            }

            for (int i = 0; i < headers.length; i++) {
                String header = headers[i];
                String value = i < row.length ? (row[i] != null ? row[i] : "") : "";

                sb.append(padLeft(header, maxHeaderWidth));
                sb.append(CARD_SEPARATOR);
                sb.append(value);
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    private String renderTable(List<String[]> rows, String[] headers) {
        RichTableRenderer renderer = new RichTableRenderer(terminalWidth)
            .headers(headers)
            .withRowSeparators(false);

        for (String[] row : rows) {
            renderer.row(row);
        }

        return renderer.render();
    }

    private int estimateTableWidth(List<String[]> rows, String[] headers) {
        int totalWidth = 0;

        for (int col = 0; col < headers.length; col++) {
            int maxWidth = headers[col].length();

            for (String[] row : rows) {
                if (col < row.length && row[col] != null) {
                    maxWidth = Math.max(maxWidth, row[col].length());
                }
            }

            totalWidth += maxWidth + MIN_COLUMN_OVERHEAD;
        }

        totalWidth += 1;

        return totalWidth;
    }

    private void validateInput(List<String[]> rows, String[] headers) {
        Objects.requireNonNull(rows, "rows no puede ser null");
        Objects.requireNonNull(headers, "headers no puede ser null");

        if (headers.length == 0) {
            throw new IllegalArgumentException("Se requiere al menos un header");
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una fila de datos");
        }

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null) {
                throw new IllegalArgumentException("Fila " + i + " es null");
            }
            if (row.length != headers.length) {
                throw new IllegalArgumentException(
                    String.format("Fila %d tiene %d valores, se esperaban %d",
                                  i, row.length, headers.length));
            }
        }
    }

    private static String padLeft(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        return " ".repeat(width - text.length()) + text;
    }

    public int getTerminalWidth() {
        return terminalWidth;
    }
}
