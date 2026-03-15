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

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ToolRequest record.
 */
@DisplayName("ToolRequest Tests")
class ToolRequestTest {

    @Test
    @DisplayName("Should create request with all fields")
    void shouldCreateRequestWithAllFields() {
        // Given
        Map<String, Object> params = Map.of("path", "test.txt", "content", "Hello");

        // When
        ToolRequest request = ToolRequest.of("FILE", "write_file", params);

        // Then
        assertThat(request.toolName()).isEqualTo("FILE");
        assertThat(request.action()).isEqualTo("write_file");
        assertThat(request.params()).containsEntry("path", "test.txt");
        assertThat(request.params()).containsEntry("content", "Hello");
        assertThat(request.requestId()).isNotNull().isNotEmpty();
        assertThat(request.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("Should normalize tool name to uppercase")
    void shouldNormalizeToolNameToUppercase() {
        // When
        ToolRequest request = ToolRequest.of("file", "read_file", Map.of());

        // Then
        assertThat(request.toolName()).isEqualTo("FILE");
    }

    @Test
    @DisplayName("Should create immutable params copy")
    void shouldCreateImmutableParamsCopy() {
        // Given
        Map<String, Object> originalParams = new java.util.HashMap<>();
        originalParams.put("key", "value");

        // When
        ToolRequest request = ToolRequest.of("TEST", "action", originalParams);

        // Then
        assertThatThrownBy(() -> request.params().put("new", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should handle null params")
    void shouldHandleNullParams() {
        // When
        ToolRequest request = new ToolRequest("FILE", "read", null, null, null);

        // Then
        assertThat(request.params()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Should throw on null toolName")
    void shouldThrowOnNullToolName() {
        assertThatThrownBy(() -> ToolRequest.of(null, "action", Map.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("toolName");
    }

    @Test
    @DisplayName("Should throw on null action")
    void shouldThrowOnNullAction() {
        assertThatThrownBy(() -> ToolRequest.of("FILE", null, Map.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("action");
    }

    @Test
    @DisplayName("Should get param as string")
    void shouldGetParamAsString() {
        // Given
        ToolRequest request = ToolRequest.of("FILE", "write", Map.of("path", "test.txt"));

        // When/Then
        assertThat(request.getParamAsString("path")).isEqualTo("test.txt");
        assertThat(request.getParamAsString("nonexistent")).isNull();
    }

    @Test
    @DisplayName("Should check param presence")
    void shouldCheckParamPresence() {
        // Given
        ToolRequest request = ToolRequest.of("FILE", "write", Map.of("path", "test.txt"));

        // When/Then
        assertThat(request.hasParam("path")).isTrue();
        assertThat(request.hasParam("content")).isFalse();
    }

    @Test
    @DisplayName("Should generate log string")
    void shouldGenerateLogString() {
        // Given
        ToolRequest request = ToolRequest.of("FILE", "write_file", Map.of("path", "test.txt"));

        // When
        String logString = request.toLogString();

        // Then
        assertThat(logString)
            .contains("FILE")
            .contains("write_file")
            .contains("path");
    }

    @Test
    @DisplayName("Should create request without params")
    void shouldCreateRequestWithoutParams() {
        // When
        ToolRequest request = ToolRequest.of("DATETIME", "now");

        // Then
        assertThat(request.toolName()).isEqualTo("DATETIME");
        assertThat(request.action()).isEqualTo("now");
        assertThat(request.params()).isEmpty();
    }
}
