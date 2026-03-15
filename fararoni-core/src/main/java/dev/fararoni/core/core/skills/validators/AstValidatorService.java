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
package dev.fararoni.core.core.skills.validators;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class AstValidatorService {
    private static final Logger LOG = Logger.getLogger(AstValidatorService.class.getName());

    private static final int PYTHON_TIMEOUT_SECONDS = 5;

    public String validate(String filename, String content) {
        if (filename == null || content == null || content.isBlank()) {
            return null;
        }

        String lowerFilename = filename.toLowerCase();

        try {
            if (lowerFilename.endsWith(".py")) {
                return validatePythonSyntax(content);
            } else if (lowerFilename.endsWith(".java")) {
                return validateJavaMinimal(content);
            } else if (lowerFilename.endsWith(".js")) {
                return validateJavaScriptSyntax(filename, content);
            } else if (lowerFilename.endsWith(".ts")) {
                return validateTypeScriptSyntax(filename, content);
            } else if (lowerFilename.endsWith(".json")) {
                return validateJsonSyntax(content);
            }
            return null;
        } catch (Exception e) {
            LOG.fine(() -> "[AST Guard] Error durante validación: " + e.getMessage());
            return null;
        }
    }

    private String validatePythonSyntax(String content) {
        try {
            Path tempFile = Files.createTempFile("fararoni_validate_", ".py");
            Files.writeString(tempFile, content);

            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "python3", "-m", "py_compile", tempFile.toString());
                pb.redirectErrorStream(true);
                Process process = pb.start();

                boolean finished = process.waitFor(PYTHON_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return null;
                }

                if (process.exitValue() != 0) {
                    String output = new String(process.getInputStream().readAllBytes());
                    String[] lines = output.split("\n");
                    for (String line : lines) {
                        if (line.contains("SyntaxError") || line.contains("IndentationError")) {
                            return line.trim();
                        }
                    }
                    return "Error de sintaxis Python";
                }

                return null;
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            LOG.fine(() -> "[AST Guard] Error validando Python: " + e.getMessage());
            return null;
        }
    }

    private String validateJavaMinimal(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String trimmed = content.trim();

        if (trimmed.startsWith("#") || trimmed.startsWith("```")) {
            return "El contenido parece ser Markdown, no Java";
        }

        boolean looksLikeJava = trimmed.contains("class ")
            || trimmed.contains("interface ")
            || trimmed.contains("enum ")
            || trimmed.contains("record ")
            || trimmed.contains("package ")
            || trimmed.contains("import ")
            || trimmed.contains("public ")
            || trimmed.contains("private ")
            || trimmed.contains("@");

        if (!looksLikeJava) {
            if (trimmed.length() < 50) {
                return null;
            }
            return "El contenido no parece ser código Java válido";
        }

        return null;
    }

    private String validateJavaSyntax(String content) {
        int braces = 0;
        int parens = 0;
        int brackets = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"' && !inChar) {
                inString = !inString;
                continue;
            }
            if (c == '\'' && !inString) {
                inChar = !inChar;
                continue;
            }

            if (inString || inChar) {
                continue;
            }

            switch (c) {
                case '{' -> braces++;
                case '}' -> braces--;
                case '(' -> parens++;
                case ')' -> parens--;
                case '[' -> brackets++;
                case ']' -> brackets--;
            }

            if (braces < 0) return "Llave de cierre } sin abrir";
            if (parens < 0) return "Paréntesis ) sin abrir";
            if (brackets < 0) return "Corchete ] sin abrir";
        }

        if (braces != 0) return "Llaves desbalanceadas: " + Math.abs(braces) + " sin cerrar";
        if (parens != 0) return "Paréntesis desbalanceados: " + Math.abs(parens) + " sin cerrar";
        if (brackets != 0) return "Corchetes desbalanceados: " + Math.abs(brackets) + " sin cerrar";

        return null;
    }

    private String validateJsSyntax(String content) {
        return validateJavaSyntax(content);
    }

    private String validateJsonSyntax(String content) {
        try {
            String stripped = content.strip();
            if (stripped.isEmpty()) {
                return "JSON vacío";
            }

            char first = stripped.charAt(0);
            char last = stripped.charAt(stripped.length() - 1);

            if (first == '{' && last != '}') {
                return "JSON objeto no cerrado";
            }
            if (first == '[' && last != ']') {
                return "JSON array no cerrado";
            }
            if (first != '{' && first != '[') {
                return "JSON debe comenzar con { o [";
            }

            return validateJavaSyntax(content);
        } catch (Exception e) {
            return "Error validando JSON: " + e.getMessage();
        }
    }

    private static final int NODE_TIMEOUT_SECONDS = 10;

    private String validateJavaScriptSyntax(String filepath, String content) {
        Path tempFile = null;
        try {
            tempFile = createSecureTempFile(".js");
            Files.writeString(tempFile, content);

            ProcessBuilder pb = new ProcessBuilder("node", "--check", tempFile.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(NODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                LOG.fine("[AST Guard] Node.js timeout, asumiendo válido: " + filepath);
                return null;
            }

            if (process.exitValue() == 0) {
                return null;
            }

            String output = new String(process.getInputStream().readAllBytes()).trim();
            return "Error de sintaxis JS: " + output.replace(tempFile.toString(), "[SCRIPT]");
        } catch (Exception e) {
            LOG.fine(() -> "[AST Guard] Node.js no disponible, fallback a balanceo: " + e.getMessage());
            return validateJavaSyntax(content);
        } finally {
            destroyEphemeralFile(tempFile);
        }
    }

    private String validateTypeScriptSyntax(String filepath, String content) {
        Path tempFile = null;
        try {
            tempFile = createSecureTempFile(".ts");
            Files.writeString(tempFile, content);

            ProcessBuilder pb = new ProcessBuilder("npx", "tsc", "--noEmit", tempFile.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(NODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                LOG.fine("[AST Guard] TypeScript timeout, asumiendo válido: " + filepath);
                return null;
            }

            if (process.exitValue() == 0) {
                return null;
            }

            String output = new String(process.getInputStream().readAllBytes()).trim();
            return "Error de sintaxis TS: " + output.replace(tempFile.toString(), "[SCRIPT]");
        } catch (Exception e) {
            LOG.fine(() -> "[AST Guard] TypeScript no disponible, fallback a balanceo: " + e.getMessage());
            return validateJavaSyntax(content);
        } finally {
            destroyEphemeralFile(tempFile);
        }
    }

    private Path createSecureTempFile(String suffix) throws Exception {
        String prefix = "fararoni_ast_";

        boolean supportsPosix = FileSystems.getDefault()
            .supportedFileAttributeViews().contains("posix");

        if (supportsPosix) {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
            FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(permissions);
            return Files.createTempFile(prefix, suffix, attrs);
        } else {
            return Files.createTempFile(prefix, suffix);
        }
    }

    private void destroyEphemeralFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception e) {
                path.toFile().deleteOnExit();
                LOG.fine("[AST Guard] Archivo temporal se borrará al cerrar: " + path.getFileName());
            }
        }
    }
}
