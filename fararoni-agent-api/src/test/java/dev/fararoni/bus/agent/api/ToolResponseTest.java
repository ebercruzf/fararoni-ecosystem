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
package dev.fararoni.bus.agent.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ToolResponse record.
 */
@DisplayName("ToolResponse Tests")
class ToolResponseTest {

    @Test
    @DisplayName("Should create success response")
    void shouldCreateSuccessResponse() {
        // When
        ToolResponse response = ToolResponse.success("File created");

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.result()).isEqualTo("File created");
        assertThat(response.errorMessage()).isNull();
        assertThat(response.isError()).isFalse();
    }

    @Test
    @DisplayName("Should create success response with metadata")
    void shouldCreateSuccessResponseWithMetadata() {
        // When
        ToolResponse response = ToolResponse.success("Done", "req-123", 50);

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.result()).isEqualTo("Done");
        assertThat(response.requestId()).isEqualTo("req-123");
        assertThat(response.executionTimeMs()).isEqualTo(50);
        assertThat(response.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should create error response")
    void shouldCreateErrorResponse() {
        // When
        ToolResponse response = ToolResponse.error("Permission denied");

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.result()).isNull();
        assertThat(response.errorMessage()).isEqualTo("Permission denied");
        assertThat(response.isError()).isTrue();
    }

    @Test
    @DisplayName("Should create error response with metadata")
    void shouldCreateErrorResponseWithMetadata() {
        // When
        ToolResponse response = ToolResponse.error("Failed", "req-456", 100);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).isEqualTo("Failed");
        assertThat(response.requestId()).isEqualTo("req-456");
        assertThat(response.executionTimeMs()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should create error response from exception")
    void shouldCreateErrorResponseFromException() {
        // Given
        Exception exception = new IllegalArgumentException("Invalid path");

        // When
        ToolResponse response = ToolResponse.fromException(exception);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).isEqualTo("Invalid path");
    }

    @Test
    @DisplayName("Should handle exception with null message")
    void shouldHandleExceptionWithNullMessage() {
        // Given
        Exception exception = new NullPointerException();

        // When
        ToolResponse response = ToolResponse.fromException(exception);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).isEqualTo("NullPointerException");
    }

    @Test
    @DisplayName("Should get message based on status")
    void shouldGetMessageBasedOnStatus() {
        // Given
        ToolResponse success = ToolResponse.success("All good");
        ToolResponse error = ToolResponse.error("Something wrong");

        // Then
        assertThat(success.getMessage()).isEqualTo("All good");
        assertThat(error.getMessage()).isEqualTo("Something wrong");
    }

    @Test
    @DisplayName("Should create response with request ID")
    void shouldCreateResponseWithRequestId() {
        // Given
        ToolResponse original = ToolResponse.success("Done");

        // When
        ToolResponse withId = original.withRequestId("req-789");

        // Then
        assertThat(withId.requestId()).isEqualTo("req-789");
        assertThat(withId.result()).isEqualTo("Done");
        assertThat(withId.success()).isTrue();
    }

    @Test
    @DisplayName("Should create response with execution time")
    void shouldCreateResponseWithExecutionTime() {
        // Given
        ToolResponse original = ToolResponse.success("Done");

        // When
        ToolResponse withTime = original.withExecutionTime(42);

        // Then
        assertThat(withTime.executionTimeMs()).isEqualTo(42);
        assertThat(withTime.result()).isEqualTo("Done");
    }

    @Test
    @DisplayName("Should generate log string for success")
    void shouldGenerateLogStringForSuccess() {
        // Given
        ToolResponse response = ToolResponse.success("Created file", "abc123", 15);

        // When
        String logString = response.toLogString();

        // Then
        assertThat(logString)
            .contains("abc123")
            .contains("SUCCESS")
            .contains("15ms")
            .contains("Created file");
    }

    @Test
    @DisplayName("Should generate log string for error")
    void shouldGenerateLogStringForError() {
        // Given
        ToolResponse response = ToolResponse.error("Access denied", "xyz789", 5);

        // When
        String logString = response.toLogString();

        // Then
        assertThat(logString)
            .contains("xyz789")
            .contains("ERROR")
            .contains("5ms")
            .contains("Access denied");
    }
}
