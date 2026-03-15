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
package dev.fararoni.core.ui.header;

import dev.fararoni.core.ui.JLineAgentUI;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class FararoniHeaderRenderer {
    private final Terminal terminal;

    public FararoniHeaderRenderer(Terminal terminal) {
        this.terminal = terminal;
    }

    public void render() {
        int width = Math.max(terminal.getWidth(), 90);

        AttributedStyle styleBright = AttributedStyle.DEFAULT.foreground(0xEE, 0xEE, 0xEE).bold();
        AttributedStyle styleSilver = AttributedStyle.DEFAULT.foreground(0xAA, 0xAA, 0xAA);
        AttributedStyle styleShadow = AttributedStyle.DEFAULT.foreground(0x66, 0x66, 0x66);
        AttributedStyle styleBorder = AttributedStyle.DEFAULT.foreground(0x44, 0x44, 0x44);
        AttributedStyle styleLabel  = AttributedStyle.DEFAULT.foreground(0x88, 0x88, 0x88);

        String[] logoLines = {
            "                       ",
            "      ╭════╗     ╔════╮      ",
            "     ╭╯    ╚╗   ╔╝    ╰╮     ",
            "     ║      ╚═══╝      ║     ",
            "     ╰╗     ╔═══╗     ╔╯     ",
            "      ╚════╭╯   ╰╮════╝      ",
            "                       "
        };

        String[][] infoLines = {
            {"IDENTITY", ""},
            {"User:", "noreply@fararoni.dev"},
            {"Role:", "Staff Architect"},
            {"", ""},
            {"SYSTEM", ""},
            {"Model:", "FARARONI-Prime-v1"},
            {"Core:",  "Metallic Nexus"},
            {"", ""},
            {"STATUS", ""},
            {"Mem:",   "4.2GB / 16GB"},
            {"Path:",  "~/dev/fararoni"}
        };

        printBorderLine("╭", "─", "─ FARARONI CLI v1.0.0 (Sovereign Infinity) ", "╮", width, styleBorder);

        for (int i = 0; i < logoLines.length; i++) {
            AttributedStringBuilder sb = new AttributedStringBuilder();
            sb.append("│ ", styleBorder);

            String line = logoLines[i];

            if (line.contains("═══╝") || line.contains("╔═══")) {
                 sb.append(line, styleShadow);
            } else if (line.contains("════")) {
                sb.append(line, styleBright);
            } else {
                sb.append(line, styleSilver);
            }

            sb.append(" │  ", styleBorder);

            if (i < infoLines.length) {
                String[] data = infoLines[i];
                if (data[1].isEmpty() && !data[0].isEmpty()) {
                    sb.append(String.format("%-25s", "── " + data[0] + " ──"), styleLabel);
                } else if (!data[0].isEmpty()) {
                    sb.append(String.format("%-8s", data[0]), styleLabel);
                    sb.append(String.format("%-20s", data[1]), styleBright);
                }
            } else if (i == logoLines.length - 1) {
                sb.append("Fararoni", styleBright);
            }

            int padding = width - 72;
            if (padding > 0) sb.append(" ".repeat(padding));
            sb.append("│", styleBorder);
            System.out.println(sb.toAnsi());
        }

        printBorderLine("╰", "─", "", "╯", width, styleBorder);
    }

    private void printBorderLine(String left, String fill, String title, String right,
                                  int width, AttributedStyle style) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.append(left, style);
        sb.append(title, style);
        int fillLen = width - 2 - title.length();
        if (fillLen > 0) sb.append(fill.repeat(fillLen), style);
        sb.append(right, style);
        System.out.println(sb.toAnsi());
    }
}
