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
package dev.fararoni.core.core.llm.streaming;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class StreamingFileParser {
    private static final Logger LOG = Logger.getLogger(StreamingFileParser.class.getName());
    private static final String FILE_MARKER = ">>>FILE:";

    private enum State {
        WAITING_FOR_FILE,
        READING_PATH,
        READING_CONTENT
    }

    private State state = State.WAITING_FOR_FILE;
    private final StringBuilder pathBuffer = new StringBuilder();
    private String currentFilePath = null;

    private final Consumer<String> onFileStart;
    private final BiConsumer<String, String> onChunk;
    private final Consumer<String> onFileEnd;

    public StreamingFileParser(
            Consumer<String> onFileStart,
            BiConsumer<String, String> onChunk,
            Consumer<String> onFileEnd) {
        this.onFileStart = onFileStart;
        this.onChunk = onChunk;
        this.onFileEnd = onFileEnd;
    }

    public void onToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }

        switch (state) {
            case WAITING_FOR_FILE -> processWaitingForFile(token);
            case READING_PATH -> processReadingPath(token);
            case READING_CONTENT -> processReadingContent(token);
        }
    }

    public void flush() {
        if (currentFilePath != null && state == State.READING_CONTENT) {
            LOG.fine("[PARSER] Flush: cerrando archivo " + currentFilePath);
            onFileEnd.accept(currentFilePath);
            currentFilePath = null;
        }
        state = State.WAITING_FOR_FILE;
        pathBuffer.setLength(0);
    }

    private void processWaitingForFile(String token) {
        if (token.contains(FILE_MARKER)) {
            int markerStart = token.indexOf(FILE_MARKER);
            String afterMarker = token.substring(markerStart + FILE_MARKER.length());

            if (afterMarker.contains("\n")) {
                int newlinePos = afterMarker.indexOf("\n");
                String path = afterMarker.substring(0, newlinePos).trim();
                startNewFile(path);

                String content = afterMarker.substring(newlinePos + 1);
                if (!content.isEmpty()) {
                    onChunk.accept(currentFilePath, content);
                }
                state = State.READING_CONTENT;
            } else {
                pathBuffer.append(afterMarker);
                state = State.READING_PATH;
            }
        }
    }

    private void processReadingPath(String token) {
        if (token.contains("\n")) {
            int newlinePos = token.indexOf("\n");
            pathBuffer.append(token.substring(0, newlinePos));
            String path = pathBuffer.toString().trim();
            pathBuffer.setLength(0);

            startNewFile(path);

            String content = token.substring(newlinePos + 1);
            if (!content.isEmpty()) {
                onChunk.accept(currentFilePath, content);
            }
            state = State.READING_CONTENT;
        } else {
            pathBuffer.append(token);
        }
    }

    private void processReadingContent(String token) {
        if (token.contains(FILE_MARKER)) {
            int markerStart = token.indexOf(FILE_MARKER);

            String contentBefore = token.substring(0, markerStart);
            if (!contentBefore.isEmpty()) {
                onChunk.accept(currentFilePath, contentBefore);
            }

            onFileEnd.accept(currentFilePath);

            String afterMarker = token.substring(markerStart + FILE_MARKER.length());
            if (afterMarker.contains("\n")) {
                int newlinePos = afterMarker.indexOf("\n");
                String path = afterMarker.substring(0, newlinePos).trim();
                startNewFile(path);

                String content = afterMarker.substring(newlinePos + 1);
                if (!content.isEmpty()) {
                    onChunk.accept(currentFilePath, content);
                }
            } else {
                pathBuffer.append(afterMarker);
                state = State.READING_PATH;
            }
        } else {
            onChunk.accept(currentFilePath, token);
        }
    }

    private void startNewFile(String path) {
        LOG.info("[PARSER] Nuevo archivo detectado: " + path);
        currentFilePath = path;
        onFileStart.accept(path);
        state = State.READING_CONTENT;
    }

    public String getCurrentFilePath() {
        return currentFilePath;
    }

    public State getState() {
        return state;
    }
}
