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

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record ValidationResult(
    boolean isValid,
    String message,
    String errorCode,
    long durationMs
) {
    public static final String OK = "OK";

    public static final String ERR_SYNTAX = "ERR_SYNTAX";

    public static final String ERR_TIMEOUT = "ERR_TIMEOUT";

    public static final String ERR_BINARY_NOT_FOUND = "ERR_BINARY_NOT_FOUND";

    public static final String ERR_IO = "ERR_IO";

    public static final String SKIPPED = "SKIPPED";

    public static ValidationResult success(long durationMs) {
        return new ValidationResult(true, "Validación exitosa", OK, durationMs);
    }

    public static ValidationResult success() {
        return success(0);
    }

    public static ValidationResult failure(String message, long durationMs) {
        return new ValidationResult(false, message, ERR_SYNTAX, durationMs);
    }

    public static ValidationResult failure(String message, String errorCode, long durationMs) {
        return new ValidationResult(false, message, errorCode, durationMs);
    }

    public static ValidationResult timeout(int timeoutSeconds) {
        return new ValidationResult(
            false,
            "Timeout después de " + timeoutSeconds + " segundos",
            ERR_TIMEOUT,
            timeoutSeconds * 1000L
        );
    }

    public static ValidationResult skipped() {
        return new ValidationResult(true, "Validación omitida (lenguaje no soportado)", SKIPPED, 0);
    }

    public static ValidationResult binaryNotFound(String binaryName) {
        return new ValidationResult(
            false,
            "Binario no encontrado: " + binaryName + ". Verifique que esté instalado y en el PATH.",
            ERR_BINARY_NOT_FOUND,
            0
        );
    }

    public static ValidationResult ioError(String ioMessage) {
        return new ValidationResult(false, "Error de I/O: " + ioMessage, ERR_IO, 0);
    }

    public boolean isSkipped() {
        return SKIPPED.equals(errorCode);
    }

    public boolean isTimeout() {
        return ERR_TIMEOUT.equals(errorCode);
    }

    public String toLogString() {
        if (isValid) {
            return String.format("[LSP] [OK] %s (%dms)", message, durationMs);
        } else {
            return String.format("[LSP] [FAIL] [%s] %s (%dms)", errorCode, message, durationMs);
        }
    }
}
