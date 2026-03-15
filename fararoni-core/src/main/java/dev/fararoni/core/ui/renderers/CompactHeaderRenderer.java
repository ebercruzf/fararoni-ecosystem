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

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class CompactHeaderRenderer {
    private static final String LINE_CHAR = "─";

    private static final int DEFAULT_WIDTH = 80;

    private final Terminal terminal;

    public CompactHeaderRenderer(Terminal terminal) {
        this.terminal = Objects.requireNonNull(terminal, "terminal no puede ser null");
    }

    public void printCompactHeader(String text) {
        int width = getTerminalWidth();

        AttributedStringBuilder sb = new AttributedStringBuilder();

        AttributedStyle lineStyle = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.BRIGHT + AttributedStyle.BLACK);

        AttributedStyle textStyle = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.WHITE)
            .faint();

        if (text == null || text.isEmpty()) {
            sb.style(lineStyle);
            sb.append(LINE_CHAR.repeat(width));
        } else {
            int textLen = text.length() + 2;
            int sideLen = (width - textLen) / 2;

            sb.style(lineStyle);
            if (sideLen > 0) {
                sb.append(LINE_CHAR.repeat(sideLen));
            }

            sb.style(textStyle);
            sb.append(" ").append(text).append(" ");

            sb.style(lineStyle);
            int rightLen = width - textLen - sideLen;
            if (rightLen > 0) {
                sb.append(LINE_CHAR.repeat(rightLen));
            }
        }

        terminal.writer().println(sb.toAnsi(terminal));
        terminal.flush();
    }

    public void printSeparator() {
        printCompactHeader(null);
    }

    public void printDefaultCompactHeader() {
        printCompactHeader("Conversation compacted");
    }

    private int getTerminalWidth() {
        int width = terminal.getWidth();
        return width > 0 ? width : DEFAULT_WIDTH;
    }
}
