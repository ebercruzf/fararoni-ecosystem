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
package dev.fararoni.core.cli;

import org.jline.reader.Completer;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.regex.Pattern;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class JLineConfig {
    private static final Logger LOG = Logger.getLogger(JLineConfig.class.getName());

    private static final Path FARARONI_DIR =
        Paths.get(System.getProperty("user.home"), ".fararoni");

    private static final Path HISTORY_FILE = FARARONI_DIR.resolve("history");

    private static final int MAX_HISTORY_SIZE = 1000;

    private JLineConfig() {}

    public static Terminal buildTerminal() throws IOException {
        try {
            return TerminalBuilder.builder()
                .system(true)
                .name("fararoni")
                .build();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo crear terminal sistema, usando dumb", e);
            return TerminalBuilder.builder()
                .dumb(true)
                .name("fararoni-dumb")
                .build();
        }
    }

    public static LineReader buildLineReader(Terminal terminal, Completer completer) {
        ensureConfigDirectory();

        LineReaderBuilder builder = LineReaderBuilder.builder()
            .terminal(terminal)
            .history(new DefaultHistory())
            .variable(LineReader.HISTORY_FILE, HISTORY_FILE)
            .variable(LineReader.HISTORY_SIZE, MAX_HISTORY_SIZE)
            .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
            .option(LineReader.Option.HISTORY_IGNORE_SPACE, true)
            .option(LineReader.Option.AUTO_FRESH_LINE, true)
            .highlighter(createPremiumHighlighter());

        if (completer != null) {
            builder.completer(completer);
        }

        LineReader lineReader = builder.build();

        LOG.fine("[JLineConfig] LineReader creado con historial en: " + HISTORY_FILE);

        return lineReader;
    }

    private static void ensureConfigDirectory() {
        try {
            if (!Files.exists(FARARONI_DIR)) {
                Files.createDirectories(FARARONI_DIR);
                LOG.info("[JLineConfig] Directorio creado: " + FARARONI_DIR);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo crear directorio de configuracion", e);
        }
    }

    private static Highlighter createPremiumHighlighter() {
        final AttributedStyle inputStyle = AttributedStyle.DEFAULT
            .bold()
            .foreground(AttributedStyle.WHITE);

        return new Highlighter() {
            @Override
            public AttributedString highlight(LineReader reader, String buffer) {
                return new AttributedString(buffer, inputStyle);
            }

            @Override
            public void setErrorPattern(Pattern errorPattern) {
            }

            @Override
            public void setErrorIndex(int errorIndex) {
            }
        };
    }

    public static Path getHistoryFile() {
        return HISTORY_FILE;
    }

    public static Path getConfigDirectory() {
        return FARARONI_DIR;
    }
}
