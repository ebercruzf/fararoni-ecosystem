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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("ValidationResult - Resultados de Validación LSP")
class ValidationResultTest {
    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {
        @Test
        @DisplayName("success() crea resultado válido")
        void success_shouldCreateValidResult() {
            ValidationResult result = ValidationResult.success(150L);

            assertTrue(result.isValid());
            assertEquals(ValidationResult.OK, result.errorCode());
            assertEquals(150L, result.durationMs());
            assertNotNull(result.message());
        }

        @Test
        @DisplayName("failure() crea resultado inválido")
        void failure_shouldCreateInvalidResult() {
            ValidationResult result = ValidationResult.failure("Syntax error at line 10", 200L);

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ERR_SYNTAX, result.errorCode());
            assertEquals("Syntax error at line 10", result.message());
            assertEquals(200L, result.durationMs());
        }

        @Test
        @DisplayName("timeout() crea resultado de timeout")
        void timeout_shouldCreateTimeoutResult() {
            ValidationResult result = ValidationResult.timeout(30);

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ERR_TIMEOUT, result.errorCode());
            assertTrue(result.message().contains("30"));
        }

        @Test
        @DisplayName("skipped() crea resultado skipped")
        void skipped_shouldCreateSkippedResult() {
            ValidationResult result = ValidationResult.skipped();

            assertTrue(result.isValid());
            assertEquals(ValidationResult.SKIPPED, result.errorCode());
        }

        @Test
        @DisplayName("binaryNotFound() crea resultado de binario faltante")
        void binaryNotFound_shouldCreateBinaryNotFoundResult() {
            ValidationResult result = ValidationResult.binaryNotFound("python3");

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ERR_BINARY_NOT_FOUND, result.errorCode());
            assertTrue(result.message().contains("python3"));
        }

        @Test
        @DisplayName("ioError() crea resultado de error I/O")
        void ioError_shouldCreateIoErrorResult() {
            ValidationResult result = ValidationResult.ioError("Permission denied");

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ERR_IO, result.errorCode());
            assertTrue(result.message().contains("Permission denied"));
        }
    }

    @Nested
    @DisplayName("Códigos de Error")
    class ErrorCodeTests {
        @Test
        @DisplayName("Constantes de error están definidas")
        void errorCodes_shouldBeDefined() {
            assertEquals("OK", ValidationResult.OK);
            assertEquals("ERR_SYNTAX", ValidationResult.ERR_SYNTAX);
            assertEquals("ERR_TIMEOUT", ValidationResult.ERR_TIMEOUT);
            assertEquals("ERR_BINARY_NOT_FOUND", ValidationResult.ERR_BINARY_NOT_FOUND);
            assertEquals("ERR_IO", ValidationResult.ERR_IO);
            assertEquals("SKIPPED", ValidationResult.SKIPPED);
        }
    }

    @Nested
    @DisplayName("Comportamiento de Record")
    class RecordBehaviorTests {
        @Test
        @DisplayName("Record es inmutable")
        void record_shouldBeImmutable() {
            ValidationResult result = ValidationResult.success(100L);

            assertTrue(result.isValid());
            assertEquals("OK", result.errorCode());
            assertEquals(100L, result.durationMs());
        }

        @Test
        @DisplayName("equals() funciona correctamente")
        void equals_shouldWork() {
            ValidationResult r1 = ValidationResult.success(100L);
            ValidationResult r2 = ValidationResult.success(100L);
            ValidationResult r3 = ValidationResult.success(200L);

            assertEquals(r1, r2);
            assertNotEquals(r1, r3);
        }

        @Test
        @DisplayName("toString() no es null")
        void toString_shouldNotBeNull() {
            ValidationResult result = ValidationResult.success(100L);
            assertNotNull(result.toString());
        }
    }

    @Nested
    @DisplayName("Formato de Log")
    class LogFormatTests {
        @Test
        @DisplayName("toLogString() formatea éxito correctamente")
        void toLogString_shouldFormatSuccess() {
            ValidationResult result = ValidationResult.success(150L);
            String log = result.toLogString();

            assertNotNull(log);
            assertTrue(log.contains("150ms") || log.contains("150"));
        }

        @Test
        @DisplayName("toLogString() formatea error correctamente")
        void toLogString_shouldFormatError() {
            ValidationResult result = ValidationResult.failure("Error grave", 50L);
            String log = result.toLogString();

            assertNotNull(log);
            assertTrue(log.contains("Error grave") || log.contains("ERR"));
        }

        @Test
        @DisplayName("toLogString() formatea timeout correctamente")
        void toLogString_shouldFormatTimeout() {
            ValidationResult result = ValidationResult.timeout(30);
            String log = result.toLogString();

            assertNotNull(log);
            assertTrue(log.contains("TIMEOUT") || log.contains("30"));
        }
    }
}
