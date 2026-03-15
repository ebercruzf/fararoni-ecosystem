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

import dev.fararoni.core.ui.CliTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class RichTableRenderer {
    private static final char CORNER_TOP_LEFT = '╭';

    private static final char CORNER_TOP_RIGHT = '╮';

    private static final char CORNER_BOTTOM_LEFT = '╰';

    private static final char CORNER_BOTTOM_RIGHT = '╯';

    private static final char LINE_HORIZONTAL = '─';

    private static final char LINE_VERTICAL = '│';

    private static final char T_TOP = '┬';

    private static final char T_BOTTOM = '┴';

    private static final char T_LEFT = '├';

    private static final char T_RIGHT = '┤';

    private static final char CROSS = '┼';

    private static final int DEFAULT_TERMINAL_WIDTH = 80;

    private static final int MIN_COLUMN_WIDTH = 5;

    private static final int DEFAULT_PADDING = 2;

    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[;\\d]*m");

    private final int terminalWidth;

    private List<String> headers;

    private final List<List<String>> rows;

    private int padding;

    private boolean showRowSeparators;

    public RichTableRenderer(int terminalWidth) {
        if (terminalWidth < 20) {
            throw new IllegalArgumentException(
                "terminalWidth debe ser al menos 20, recibido: " + terminalWidth);
        }
        this.terminalWidth = terminalWidth;
        this.headers = new ArrayList<>();
        this.rows = new ArrayList<>();
        this.padding = DEFAULT_PADDING;
        this.showRowSeparators = true;
    }

    public RichTableRenderer() {
        this(DEFAULT_TERMINAL_WIDTH);
    }

    public RichTableRenderer headers(String... headers) {
        if (headers == null || headers.length == 0) {
            throw new IllegalArgumentException("Se requiere al menos un header");
        }
        this.headers = new ArrayList<>(Arrays.asList(headers));
        return this;
    }

    public RichTableRenderer row(String... values) {
        if (headers.isEmpty()) {
            throw new IllegalStateException("Debes definir headers antes de agregar filas");
        }
        if (values.length != headers.size()) {
            throw new IllegalArgumentException(
                String.format("Se esperaban %d valores, recibidos %d", headers.size(), values.length));
        }

        List<String> row = new ArrayList<>(values.length);
        for (String value : values) {
            row.add(value != null ? value : "");
        }
        rows.add(row);
        return this;
    }

    public RichTableRenderer withPadding(int padding) {
        if (padding < 0) {
            throw new IllegalArgumentException("padding no puede ser negativo");
        }
        this.padding = padding;
        return this;
    }

    public RichTableRenderer withRowSeparators(boolean show) {
        this.showRowSeparators = show;
        return this;
    }

    public String render() {
        if (headers.isEmpty()) {
            return "";
        }

        int[] columnWidths = calculateColumnWidths();
        StringBuilder sb = new StringBuilder();

        appendHorizontalLine(sb, columnWidths, CORNER_TOP_LEFT, T_TOP, CORNER_TOP_RIGHT);

        appendWrappedRow(sb, headers, columnWidths);

        if (!rows.isEmpty()) {
            appendHorizontalLine(sb, columnWidths, T_LEFT, CROSS, T_RIGHT);
        }

        for (int i = 0; i < rows.size(); i++) {
            appendWrappedRow(sb, rows.get(i), columnWidths);

            if (showRowSeparators && i < rows.size() - 1) {
                appendHorizontalLine(sb, columnWidths, T_LEFT, CROSS, T_RIGHT);
            }
        }

        appendHorizontalLine(sb, columnWidths, CORNER_BOTTOM_LEFT, T_BOTTOM, CORNER_BOTTOM_RIGHT);

        return sb.toString();
    }

    private int[] calculateColumnWidths() {
        int numColumns = headers.size();
        int[] widths = new int[numColumns];

        int borderSpace = numColumns + 1;
        int paddingSpace = 2 * padding * numColumns;

        int availableWidth = terminalWidth - borderSpace - paddingSpace;

        int[] naturalWidths = new int[numColumns];
        int totalNatural = 0;

        for (int i = 0; i < numColumns; i++) {
            int maxWidth = visualLength(headers.get(i));
            for (List<String> row : rows) {
                maxWidth = Math.max(maxWidth, visualLength(row.get(i)));
            }
            naturalWidths[i] = maxWidth;
            totalNatural += maxWidth;
        }

        if (totalNatural <= availableWidth) {
            return naturalWidths;
        }

        for (int i = 0; i < numColumns; i++) {
            double proportion = (double) naturalWidths[i] / totalNatural;
            widths[i] = Math.max(MIN_COLUMN_WIDTH, (int) (availableWidth * proportion));
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
            sb.append(i < widths.length - 1 ? middle : right);
        }
        sb.append('\n');
    }

    private void appendWrappedRow(StringBuilder sb, List<String> values, int[] widths) {
        List<List<String>> wrappedCells = new ArrayList<>();
        int maxLines = 1;

        for (int i = 0; i < values.size(); i++) {
            List<String> lines = wrapText(values.get(i), widths[i]);
            wrappedCells.add(lines);
            maxLines = Math.max(maxLines, lines.size());
        }

        for (int line = 0; line < maxLines; line++) {
            sb.append(LINE_VERTICAL);
            for (int col = 0; col < wrappedCells.size(); col++) {
                List<String> cellLines = wrappedCells.get(col);
                String content = line < cellLines.size() ? cellLines.get(line) : "";

                sb.append(" ".repeat(padding));

                sb.append(content);
                int visualLen = visualLength(content);
                sb.append(" ".repeat(Math.max(0, widths[col] - visualLen)));

                sb.append(" ".repeat(padding));

                sb.append(LINE_VERTICAL);
            }
            sb.append('\n');
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        if (visualLength(text) <= maxWidth) {
            lines.add(text);
            return lines;
        }

        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        int currentLength = 0;

        for (String word : words) {
            int wordLength = visualLength(word);

            if (wordLength > maxWidth) {
                if (currentLength > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                    currentLength = 0;
                }
                lines.addAll(splitLongWord(word, maxWidth));
                continue;
            }

            int spaceNeeded = currentLength > 0 ? wordLength + 1 : wordLength;
            if (currentLength + spaceNeeded <= maxWidth) {
                if (currentLength > 0) {
                    currentLine.append(' ');
                    currentLength++;
                }
                currentLine.append(word);
                currentLength += wordLength;
            } else {
                if (currentLength > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
                currentLength = wordLength;
            }
        }

        if (currentLength > 0) {
            lines.add(currentLine.toString());
        }

        return lines.isEmpty() ? List.of("") : lines;
    }

    private List<String> splitLongWord(String word, int maxWidth) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < word.length()) {
            int end = Math.min(start + maxWidth, word.length());
            pieces.add(word.substring(start, end));
            start = end;
        }
        return pieces;
    }

    public static String stripAnsi(String text) {
        if (text == null) {
            return "";
        }
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }

    public static int visualLength(String text) {
        return stripAnsi(text).length();
    }

    public int getTerminalWidth() {
        return terminalWidth;
    }

    public int getColumnCount() {
        return headers.size();
    }

    public int getRowCount() {
        return rows.size();
    }
}
