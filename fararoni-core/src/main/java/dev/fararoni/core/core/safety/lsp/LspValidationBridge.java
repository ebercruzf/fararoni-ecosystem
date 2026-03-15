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
package dev.fararoni.core.core.safety.lsp;

import dev.fararoni.core.core.utils.MultiFileParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class LspValidationBridge {
    private static final Logger LOG = Logger.getLogger(LspValidationBridge.class.getName());

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private static final String TEMP_PREFIX = "fararoni_lsp_";

    private final Path tempDirectory;

    private final int timeoutSeconds;

    public LspValidationBridge() {
        this(null, DEFAULT_TIMEOUT_SECONDS);
    }

    public LspValidationBridge(int timeoutSeconds) {
        this(null, timeoutSeconds);
    }

    public LspValidationBridge(Path tempDirectory, int timeoutSeconds) {
        this.tempDirectory = tempDirectory;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }

    public ValidationResult validate(String filename, String content) {
        if (filename == null || content == null || content.isBlank()) {
            return ValidationResult.skipped();
        }

        long startTime = System.currentTimeMillis();
        Path tempFile = null;

        try {
            String cleanContent = MultiFileParser.cleanCode(content);

            List<String> command = getValidationCommand(filename);
            if (command.isEmpty()) {
                LOG.fine("[LSP] Lenguaje no soportado: " + filename);
                return ValidationResult.skipped();
            }

            tempFile = createTempValidationFile(filename, cleanContent);
            final String tempFilePath = tempFile.toString();

            command = command.stream()
                .map(arg -> arg.equals("__FILE__") ? tempFilePath : arg)
                .toList();

            LOG.info("[LSP] Validando: " + filename + " con comando: " + String.join(" ", command));

            ValidationResult result = executeValidationProcess(command, startTime);

            if (!result.isValid() && filename.toLowerCase().endsWith(".java")) {
                result = filterDependencyErrors(result, startTime);
            }

            LOG.info(result.toLogString());
            return result;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[LSP] Error de I/O: " + e.getMessage(), e);
            return ValidationResult.ioError(e.getMessage());
        } finally {
            cleanup(tempFile);
        }
    }

    private List<String> getValidationCommand(String filename) {
        String ext = getExtension(filename).toLowerCase();

        return switch (ext) {
            case "java" -> List.of(
                "java",
                "--source", "25",
                "--enable-preview",
                "__FILE__"
            );
            case "py" -> List.of(
                "python3", "-m", "py_compile", "__FILE__"
            );
            case "ts" -> List.of(
                "npx", "tsc",
                "--noEmit",
                "--target", "esnext",
                "--moduleResolution", "node",
                "__FILE__"
            );
            case "js" -> List.of(
                "node", "--check", "__FILE__"
            );
            default -> Collections.emptyList();
        };
    }

    private ValidationResult executeValidationProcess(List<String> command, long startTime) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            Thread outputReader = Thread.ofVirtual().start(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        if (output.length() > 10000) {
                            output.append("... [truncado]");
                            break;
                        }
                    }
                } catch (IOException e) {
                }
            });

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            outputReader.join(1000);

            long duration = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                return ValidationResult.timeout(timeoutSeconds);
            }

            if (process.exitValue() == 0) {
                return ValidationResult.success(duration);
            }

            String errorOutput = output.toString().trim();
            return ValidationResult.failure(
                sanitizeErrorMessage(errorOutput),
                duration
            );
        } catch (IOException e) {
            String binary = command.isEmpty() ? "unknown" : command.get(0);
            LOG.warning("[LSP] Binario no encontrado: " + binary);
            return ValidationResult.binaryNotFound(binary);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ValidationResult.failure("Validación interrumpida", 0);
        }
    }

    private ValidationResult filterDependencyErrors(ValidationResult originalResult, long startTime) {
        String message = originalResult.message();
        if (message == null) return originalResult;

        boolean isDependencyError =
            message.contains("package") && message.contains("does not exist") ||
            message.contains("cannot find symbol") ||
            message.contains("cannot access") ||
            message.contains("class file has wrong version") ||
            message.contains("requires") && message.contains("module");

        if (isDependencyError) {
            LOG.info("[LSP] Falso positivo por dependencias detectado, permitiendo archivo");
            long duration = System.currentTimeMillis() - startTime;
            return ValidationResult.success(duration);
        }

        return originalResult;
    }

    private Path createTempValidationFile(String originalName, String content) throws IOException {
        String ext = "." + getExtension(originalName);

        Path tempFile;
        if (tempDirectory != null) {
            Files.createDirectories(tempDirectory);
            tempFile = Files.createTempFile(tempDirectory, TEMP_PREFIX, ext);
        } else {
            tempFile = Files.createTempFile(TEMP_PREFIX, ext);
        }

        Files.writeString(tempFile, content, StandardCharsets.UTF_8);

        LOG.fine("[LSP] Archivo temporal creado: " + tempFile);
        return tempFile;
    }

    private void cleanup(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
                LOG.fine("[LSP] Archivo temporal eliminado: " + file.getFileName());
            } catch (IOException e) {
                file.toFile().deleteOnExit();
                LOG.fine("[LSP] Archivo temporal se borrará al cerrar: " + file.getFileName());
            }
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1) : "";
    }

    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Error desconocido";
        }

        String sanitized = errorMessage
            .replaceAll("/tmp/fararoni_lsp_[^\\s:]+", "[ARCHIVO]")
            .replaceAll("\\\\temp\\\\fararoni_lsp_[^\\s:]+", "[ARCHIVO]");

        if (sanitized.length() > 500) {
            sanitized = sanitized.substring(0, 500) + "... [truncado]";
        }

        return sanitized.trim();
    }

    public static boolean isJava25Available() {
        try {
            Process p = new ProcessBuilder("java", "--version")
                .redirectErrorStream(true)
                .start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) return false;

            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return output.contains(" 25") || output.contains(" 26") || output.contains(" 27");
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPython3Available() {
        return isBinaryAvailable("python3", "--version");
    }

    public static boolean isNodeAvailable() {
        return isBinaryAvailable("node", "--version");
    }

    private static boolean isBinaryAvailable(String binary, String... args) {
        try {
            List<String> cmd = new java.util.ArrayList<>();
            cmd.add(binary);
            cmd.addAll(List.of(args));

            Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
