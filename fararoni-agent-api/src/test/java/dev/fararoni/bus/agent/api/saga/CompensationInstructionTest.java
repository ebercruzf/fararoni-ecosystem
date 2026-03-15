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
package dev.fararoni.bus.agent.api.saga;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CompensationInstruction record.
 */
@DisplayName("CompensationInstruction")
class CompensationInstructionTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("of() creates instruction with parameters")
        void ofCreatesInstructionWithParams() {
            Map<String, Object> params = Map.of("path", "/tmp/test.txt", "backup", "/tmp/backup.txt");
            CompensationInstruction instruction = CompensationInstruction.of("FileSkill", "restore", params);

            assertEquals("FileSkill", instruction.skillName());
            assertEquals("restore", instruction.method());
            assertEquals("/tmp/test.txt", instruction.params().get("path"));
            assertEquals("/tmp/backup.txt", instruction.params().get("backup"));
        }

        @Test
        @DisplayName("of() handles empty params")
        void ofHandlesEmptyParams() {
            CompensationInstruction instruction = CompensationInstruction.of("TestSkill", "cleanup", Map.of());

            assertNotNull(instruction.params());
            assertTrue(instruction.params().isEmpty());
        }

        @Test
        @DisplayName("of() handles null params by creating empty map")
        void ofHandlesNullParams() {
            CompensationInstruction instruction = CompensationInstruction.of("TestSkill", "cleanup", null);

            assertNotNull(instruction.params());
            assertTrue(instruction.params().isEmpty());
        }
    }

    @Nested
    @DisplayName("Parameter Access")
    class ParameterAccess {

        @Test
        @DisplayName("getParam() returns correct value")
        void getParamReturnsCorrectValue() {
            CompensationInstruction instruction = CompensationInstruction.of(
                "FileSkill", "restore", Map.of("path", "/test", "size", 1024L)
            );

            assertEquals("/test", instruction.<String>getParam("path"));
            assertEquals(1024L, instruction.<Long>getParam("size"));
        }

        @Test
        @DisplayName("getParam() returns null for missing key")
        void getParamReturnsNullForMissingKey() {
            CompensationInstruction instruction = CompensationInstruction.of(
                "FileSkill", "restore", Map.of("path", "/test")
            );

            assertNull(instruction.getParam("nonexistent"));
        }

        @Test
        @DisplayName("getParam() with default returns value when present")
        void getParamWithDefaultReturnsValueWhenPresent() {
            CompensationInstruction instruction = CompensationInstruction.of(
                "FileSkill", "restore", Map.of("path", "/test")
            );

            assertEquals("/test", instruction.getParam("path", "/default"));
        }

        @Test
        @DisplayName("getParam() with default returns default when missing")
        void getParamWithDefaultReturnsDefaultWhenMissing() {
            CompensationInstruction instruction = CompensationInstruction.of(
                "FileSkill", "restore", Map.of()
            );

            assertEquals("/default", instruction.getParam("path", "/default"));
        }
    }

    @Nested
    @DisplayName("Helper Methods")
    class HelperMethods {

        @Test
        @DisplayName("hasParam() returns true for existing key")
        void hasParamReturnsTrueForExistingKey() {
            CompensationInstruction instruction = CompensationInstruction.of(
                "FileSkill", "restore", Map.of("path", "/test")
            );

            assertTrue(instruction.hasParam("path"));
        }

        @Test
        @DisplayName("hasParam() returns false for missing key")
        void hasParamReturnsFalseForMissingKey() {
            CompensationInstruction instruction = CompensationInstruction.of(
                "FileSkill", "restore", Map.of()
            );

            assertFalse(instruction.hasParam("path"));
        }

        @Test
        @DisplayName("describe() returns human-readable description")
        void describeReturnsHumanReadableDescription() {
            CompensationInstruction instruction = CompensationInstruction.of(
                "FileSkill", "restore", Map.of("path", "/test.txt")
            );

            String description = instruction.describe();

            assertTrue(description.contains("FileSkill"));
            assertTrue(description.contains("restore"));
            assertTrue(description.contains("path"));
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("params map is immutable")
        void paramsMapIsImmutable() {
            Map<String, Object> params = new HashMap<>();
            params.put("path", "/test");

            CompensationInstruction instruction = CompensationInstruction.of(
                "FileSkill", "restore", params
            );

            // Original map modification should not affect instruction
            params.put("path", "/modified");
            assertEquals("/test", instruction.getParam("path"));

            // Returned map should be immutable
            assertThrows(UnsupportedOperationException.class, () ->
                instruction.params().put("new", "value")
            );
        }
    }

    @Nested
    @DisplayName("Serialization")
    class Serialization {

        @Test
        @DisplayName("instruction is serializable")
        void instructionIsSerializable() throws IOException, ClassNotFoundException {
            CompensationInstruction original = CompensationInstruction.of(
                "FileSkill", "delete", Map.of("path", "/tmp/test.txt")
            );

            // Serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(original);
            }

            // Deserialize
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            CompensationInstruction deserialized;
            try (ObjectInputStream ois = new ObjectInputStream(bais)) {
                deserialized = (CompensationInstruction) ois.readObject();
            }

            assertEquals(original.skillName(), deserialized.skillName());
            assertEquals(original.method(), deserialized.method());
            assertEquals(original.params(), deserialized.params());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("equal instructions are equal")
        void equalInstructionsAreEqual() {
            CompensationInstruction a = CompensationInstruction.of(
                "FileSkill", "delete", Map.of("path", "/test")
            );
            CompensationInstruction b = CompensationInstruction.of(
                "FileSkill", "delete", Map.of("path", "/test")
            );

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("different instructions are not equal")
        void differentInstructionsAreNotEqual() {
            CompensationInstruction a = CompensationInstruction.of(
                "FileSkill", "delete", Map.of("path", "/test1")
            );
            CompensationInstruction b = CompensationInstruction.of(
                "FileSkill", "delete", Map.of("path", "/test2")
            );

            assertNotEquals(a, b);
        }
    }
}
