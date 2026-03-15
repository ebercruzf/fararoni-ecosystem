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

import dev.fararoni.bus.agent.api.ToolMetadata.ParameterMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ToolMetadata and ParameterMetadata records.
 */
@DisplayName("ToolMetadata Tests")
class ToolMetadataTest {

    @Test
    @DisplayName("Should create metadata with all fields")
    void shouldCreateMetadataWithAllFields() {
        // Given
        List<ParameterMetadata> params = List.of(
            ParameterMetadata.required("path", "File path"),
            ParameterMetadata.optional("encoding", "File encoding", "UTF-8")
        );

        // When
        ToolMetadata metadata = new ToolMetadata(
            "write_file",
            "Writes content to a file",
            "FILE",
            "filesystem",
            params,
            true,
            5000
        );

        // Then
        assertThat(metadata.name()).isEqualTo("write_file");
        assertThat(metadata.description()).isEqualTo("Writes content to a file");
        assertThat(metadata.skillName()).isEqualTo("FILE");
        assertThat(metadata.category()).isEqualTo("filesystem");
        assertThat(metadata.parameters()).hasSize(2);
        assertThat(metadata.requiresConfirmation()).isTrue();
        assertThat(metadata.timeoutMs()).isEqualTo(5000);
    }

    @Test
    @DisplayName("Should throw on null action name")
    void shouldThrowOnNullActionName() {
        assertThatThrownBy(() -> new ToolMetadata(null, "desc", "SKILL", "", List.of(), false, 1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Action name");
    }

    @Test
    @DisplayName("Should throw on blank action name")
    void shouldThrowOnBlankActionName() {
        assertThatThrownBy(() -> new ToolMetadata("  ", "desc", "SKILL", "", List.of(), false, 1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Action name");
    }

    @Test
    @DisplayName("Should throw on null skill name")
    void shouldThrowOnNullSkillName() {
        assertThatThrownBy(() -> new ToolMetadata("action", "desc", null, "", List.of(), false, 1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Skill name");
    }

    @Test
    @DisplayName("Should handle null parameters")
    void shouldHandleNullParameters() {
        // When
        ToolMetadata metadata = new ToolMetadata("action", "desc", "SKILL", "", null, false, 1000);

        // Then
        assertThat(metadata.parameters()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Should create simplified metadata")
    void shouldCreateSimplifiedMetadata() {
        // When
        ToolMetadata metadata = ToolMetadata.of("get_date", "Gets current date", "DATETIME");

        // Then
        assertThat(metadata.name()).isEqualTo("get_date");
        assertThat(metadata.description()).isEqualTo("Gets current date");
        assertThat(metadata.skillName()).isEqualTo("DATETIME");
        assertThat(metadata.parameters()).isEmpty();
        assertThat(metadata.requiresConfirmation()).isFalse();
        assertThat(metadata.timeoutMs()).isEqualTo(30000);
    }

    @Test
    @DisplayName("Should get full qualified name")
    void shouldGetFullQualifiedName() {
        // Given
        ToolMetadata metadata = ToolMetadata.of("status", "Gets git status", "GIT");

        // Then
        assertThat(metadata.getFullName()).isEqualTo("GIT.status");
    }

    @Test
    @DisplayName("Should check for parameters")
    void shouldCheckForParameters() {
        // Given
        ToolMetadata withParams = new ToolMetadata("write", "Write", "FILE", "",
            List.of(ParameterMetadata.required("path", "Path")), false, 1000);
        ToolMetadata noParams = ToolMetadata.of("now", "Now", "TIME");

        // Then
        assertThat(withParams.hasParameters()).isTrue();
        assertThat(noParams.hasParameters()).isFalse();
    }

    @Test
    @DisplayName("Should count required parameters")
    void shouldCountRequiredParameters() {
        // Given
        List<ParameterMetadata> params = List.of(
            ParameterMetadata.required("path", "Path"),
            ParameterMetadata.required("content", "Content"),
            ParameterMetadata.optional("mode", "Mode", "write")
        );
        ToolMetadata metadata = new ToolMetadata("write", "Write", "FILE", "", params, false, 1000);

        // Then
        assertThat(metadata.getRequiredParameterCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should create required parameter metadata")
    void shouldCreateRequiredParameterMetadata() {
        // When
        ParameterMetadata param = ParameterMetadata.required("path", "File path");

        // Then
        assertThat(param.name()).isEqualTo("path");
        assertThat(param.description()).isEqualTo("File path");
        assertThat(param.required()).isTrue();
        assertThat(param.type()).isEqualTo("string");
    }

    @Test
    @DisplayName("Should create optional parameter metadata")
    void shouldCreateOptionalParameterMetadata() {
        // When
        ParameterMetadata param = ParameterMetadata.optional("limit", "Max items", "10");

        // Then
        assertThat(param.name()).isEqualTo("limit");
        assertThat(param.description()).isEqualTo("Max items");
        assertThat(param.required()).isFalse();
        assertThat(param.defaultValue()).isEqualTo("10");
    }

    @Test
    @DisplayName("Should convert Java types to JSON types")
    void shouldConvertJavaTypesToJsonTypes() {
        assertThat(ParameterMetadata.javaTypeToJsonType(String.class)).isEqualTo("string");
        assertThat(ParameterMetadata.javaTypeToJsonType(int.class)).isEqualTo("integer");
        assertThat(ParameterMetadata.javaTypeToJsonType(Integer.class)).isEqualTo("integer");
        assertThat(ParameterMetadata.javaTypeToJsonType(long.class)).isEqualTo("integer");
        assertThat(ParameterMetadata.javaTypeToJsonType(double.class)).isEqualTo("number");
        assertThat(ParameterMetadata.javaTypeToJsonType(boolean.class)).isEqualTo("boolean");
        assertThat(ParameterMetadata.javaTypeToJsonType(List.class)).isEqualTo("array");
        assertThat(ParameterMetadata.javaTypeToJsonType(Object.class)).isEqualTo("object");
    }

    @Test
    @DisplayName("Should throw on null parameter name")
    void shouldThrowOnNullParameterName() {
        assertThatThrownBy(() -> new ParameterMetadata(null, "string", "desc", true, "", "", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Parameter name");
    }
}
