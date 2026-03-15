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

import dev.fararoni.core.client.OpenAiStreamParser;

import java.io.PrintStream;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ThoughtRenderer {
    private static final String THOUGHT_STYLE = "\033[3;90m";

    private static final String RESET_STYLE = "\033[0m";

    private static final String THOUGHT_START = "\033[3;90m[Pensando...] ";

    private boolean inThoughtMode = false;

    private boolean thoughtPrefixShown = false;

    private final PrintStream out;

    public ThoughtRenderer() {
        this(System.out);
    }

    public ThoughtRenderer(PrintStream out) {
        this.out = out;
    }

    public void render(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }

        if (token.startsWith(OpenAiStreamParser.THOUGHT_PREFIX)) {
            String thought = token.substring(OpenAiStreamParser.THOUGHT_PREFIX.length());

            if (!thoughtPrefixShown) {
                out.print("\n" + THOUGHT_START);
                thoughtPrefixShown = true;
                inThoughtMode = true;
            }

            out.print(thought);
        } else {
            if (inThoughtMode) {
                out.print(RESET_STYLE + "\n\n");
                inThoughtMode = false;
            }

            out.print(token);
        }
    }

    public void finish() {
        if (inThoughtMode) {
            out.print(RESET_STYLE + "\n");
            inThoughtMode = false;
        }
        thoughtPrefixShown = false;
    }

    public void reset() {
        inThoughtMode = false;
        thoughtPrefixShown = false;
    }

    public boolean isInThoughtMode() {
        return inThoughtMode;
    }

    public static String format(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }

        if (token.startsWith(OpenAiStreamParser.THOUGHT_PREFIX)) {
            String thought = token.substring(OpenAiStreamParser.THOUGHT_PREFIX.length());
            return THOUGHT_STYLE + thought + RESET_STYLE;
        }

        return token;
    }

    public static boolean isThoughtToken(String token) {
        return token != null && token.startsWith(OpenAiStreamParser.THOUGHT_PREFIX);
    }
}
