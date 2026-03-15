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
package dev.fararoni.core.core.surgical;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("EditBlock Tests")
class EditBlockTest {
    @Nested
    @DisplayName("Constructor Validation Tests")
    class ConstructorValidationTests {
        @Test
        @DisplayName("Constructor valido crea instancia")
        void testValidConstructor() {
            EditBlock block = new EditBlock("id-1", "search", "replace", 10, 0, 0);

            assertEquals("id-1", block.id());
            assertEquals("search", block.search());
            assertEquals("replace", block.replace());
            assertEquals(10, block.estimatedLine());
        }

        @Test
        @DisplayName("Id null lanza excepcion")
        void testNullIdThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new EditBlock(null, "search", "replace", 10, 0, 0)
            );
        }

        @Test
        @DisplayName("Id vacio lanza excepcion")
        void testEmptyIdThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new EditBlock("  ", "search", "replace", 10, 0, 0)
            );
        }

        @Test
        @DisplayName("Search null lanza excepcion")
        void testNullSearchThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new EditBlock("id", null, "replace", 10, 0, 0)
            );
        }

        @Test
        @DisplayName("Search vacio lanza excepcion")
        void testEmptySearchThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new EditBlock("id", "", "replace", 10, 0, 0)
            );
        }

        @Test
        @DisplayName("Replace null lanza excepcion")
        void testNullReplaceThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new EditBlock("id", "search", null, 10, 0, 0)
            );
        }

        @Test
        @DisplayName("Replace vacio es valido (eliminacion)")
        void testEmptyReplaceIsValid() {
            EditBlock block = new EditBlock("id", "search", "", 10, 0, 0);
            assertTrue(block.isDeletion());
        }

        @Test
        @DisplayName("EstimatedLine negativo lanza excepcion")
        void testNegativeLineThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new EditBlock("id", "search", "replace", -1, 0, 0)
            );
        }

        @Test
        @DisplayName("EstimatedLine cero es valido")
        void testZeroLineIsValid() {
            EditBlock block = new EditBlock("id", "search", "replace", 0, 0, 0);
            assertEquals(0, block.estimatedLine());
        }
    }

    @Nested
    @DisplayName("SizeDelta Tests")
    class SizeDeltaTests {
        @Test
        @DisplayName("Delta positivo cuando replace es mas largo")
        void testPositiveDelta() {
            EditBlock block = new EditBlock("id", "abc", "abcdef", 10, 0, 0);
            assertEquals(3, block.sizeDelta());
        }

        @Test
        @DisplayName("Delta negativo cuando replace es mas corto")
        void testNegativeDelta() {
            EditBlock block = new EditBlock("id", "abcdef", "abc", 10, 0, 0);
            assertEquals(-3, block.sizeDelta());
        }

        @Test
        @DisplayName("Delta cero cuando mismo tamanio")
        void testZeroDelta() {
            EditBlock block = new EditBlock("id", "abc", "xyz", 10, 0, 0);
            assertEquals(0, block.sizeDelta());
        }
    }

    @Nested
    @DisplayName("Type Detection Tests")
    class TypeDetectionTests {
        @Test
        @DisplayName("isDeletion cuando replace esta vacio")
        void testIsDeletion() {
            EditBlock block = new EditBlock("id", "texto a eliminar", "", 10, 0, 0);
            assertTrue(block.isDeletion());
        }

        @Test
        @DisplayName("isInsertion cuando search es minimal")
        void testIsInsertion() {
            EditBlock block = new EditBlock("id", ";", "; // comentario", 10, 0, 0);
            assertTrue(block.isInsertion());
        }

        @Test
        @DisplayName("No es insercion cuando search es largo")
        void testNotInsertion() {
            EditBlock block = new EditBlock("id", "texto largo", "texto mas largo", 10, 0, 0);
            assertFalse(block.isInsertion());
        }
    }
}
