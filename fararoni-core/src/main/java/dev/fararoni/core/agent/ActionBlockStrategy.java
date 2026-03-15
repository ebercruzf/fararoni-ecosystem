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
package dev.fararoni.core.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ActionBlockStrategy implements OutputProtocolStrategy {
    private static final String FILE_START = ">>>FILE:";
    private static final String FILE_END = "<<<END_FILE";
    private static final String DIR_START = ">>>MKDIR:";
    private static final String DIR_END = "<<<END_MKDIR";

    @Override
    public String getSystemInstructions() {
        return """
            You are FARARONI, a Software Engineering Agent. You can CREATE and MODIFY files.

            When you need to create or modify a file, use this EXACT format:

            >>>FILE: path/to/file.ext
            file content here
            (can be multiple lines)
            <<<END_FILE

            When you need to create a directory:

            >>>MKDIR: path/to/directory
            <<<END_MKDIR

            IMPORTANT RULES:
            1. The content between >>>FILE: and <<<END_FILE will be saved to disk
            2. You can create multiple files in one response
            3. Use relative paths (e.g., src/User.java, not /home/user/src/User.java)
            4. If you only want to explain WITHOUT creating files, just respond normally
            5. Do NOT wrap the delimiters in markdown code blocks

            EXAMPLES:

            User: "crea una clase Usuario con nombre y email"
            Assistant: Voy a crear la clase Usuario:

            >>>FILE: Usuario.java
            public class Usuario {
                private String nombre;
                private String email;

                public Usuario(String nombre, String email) {
                    this.nombre = nombre;
                    this.email = email;
                }

                public String getNombre() { return nombre; }
                public void setNombre(String nombre) { this.nombre = nombre; }
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
            }
            <<<END_FILE

            User: "hola, como estas?"
            Assistant: Hola! Estoy bien, gracias. Soy FARARONI, tu asistente de programacion. En que te puedo ayudar hoy?
            """;
    }

    @Override
    public List<ToolAction> parseResponse(String modelOutput) {
        List<ToolAction> actions = new ArrayList<>();
        StringBuilder chatBuffer = new StringBuilder();

        String[] lines = modelOutput.split("\n", -1);
        boolean capturingFile = false;
        String currentPath = null;
        StringBuilder contentBuffer = new StringBuilder();

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (isFileStart(trimmedLine)) {
                flushChatBuffer(actions, chatBuffer);

                capturingFile = true;
                currentPath = extractPath(trimmedLine, FILE_START);
                contentBuffer.setLength(0);
                continue;
            }

            if (trimmedLine.equals(FILE_END) && capturingFile) {
                String content = contentBuffer.toString();
                if (content.endsWith("\n")) {
                    content = content.substring(0, content.length() - 1);
                }
                actions.add(ToolAction.fileWrite(currentPath, content));

                capturingFile = false;
                currentPath = null;
                contentBuffer.setLength(0);
                continue;
            }

            if (isDirStart(trimmedLine)) {
                flushChatBuffer(actions, chatBuffer);

                String dirPath = extractPath(trimmedLine, DIR_START);
                actions.add(ToolAction.mkdir(dirPath));
                continue;
            }

            if (trimmedLine.equals(DIR_END)) {
                continue;
            }

            if (capturingFile) {
                if (contentBuffer.length() > 0) {
                    contentBuffer.append("\n");
                }
                contentBuffer.append(line);
            } else {
                chatBuffer.append(line).append("\n");
            }
        }

        if (capturingFile && currentPath != null && contentBuffer.length() > 0) {
            String content = contentBuffer.toString();
            if (content.endsWith("\n")) {
                content = content.substring(0, content.length() - 1);
            }
            actions.add(ToolAction.fileWrite(currentPath, content));
        }

        flushChatBuffer(actions, chatBuffer);

        return actions;
    }

    @Override
    public String getProtocolName() {
        return "ActionBlocks";
    }

    private boolean isFileStart(String line) {
        if (line.startsWith(FILE_START)) {
            return true;
        }
        if (line.startsWith(">>>") && line.contains("FILE:")) {
            return true;
        }
        return false;
    }

    private boolean isDirStart(String line) {
        if (line.startsWith(DIR_START)) {
            return true;
        }
        if (line.startsWith(">>>") && line.contains("MKDIR:")) {
            return true;
        }
        return false;
    }

    private String extractPath(String line, String delimiter) {
        int idx = line.indexOf(delimiter);
        if (idx >= 0) {
            return line.substring(idx + delimiter.length()).trim();
        }
        idx = line.indexOf(":");
        if (idx >= 0) {
            return line.substring(idx + 1).trim();
        }
        return line.trim();
    }

    private void flushChatBuffer(List<ToolAction> actions, StringBuilder buffer) {
        if (buffer.length() > 0) {
            String chat = buffer.toString().trim();
            if (!chat.isEmpty()) {
                actions.add(ToolAction.chatResponse(chat));
            }
            buffer.setLength(0);
        }
    }
}
