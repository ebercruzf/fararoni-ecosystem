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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SurgeryReport Tests")
class SurgeryReportTest {
    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {
        @Test
        @DisplayName("Constructor valido crea instancia")
        void testValidConstructor() {
            List<ChangeLog> changes = List.of(new ChangeLog("block-1", 10, 10, 5));
            SurgeryReport report = new SurgeryReport("content", changes, 5);

            assertEquals("content", report.content());
            assertEquals(1, report.changes().size());
            assertEquals(5, report.totalDelta());
        }

        @Test
        @DisplayName("Content null lanza excepcion")
        void testNullContentThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new SurgeryReport(null, List.of(), 0)
            );
        }

        @Test
        @DisplayName("Changes null se convierte a lista vacia")
        void testNullChangesBecomesEmpty() {
            SurgeryReport report = new SurgeryReport("content", null, 0);
            assertNotNull(report.changes());
            assertTrue(report.changes().isEmpty());
        }

        @Test
        @DisplayName("Lista de cambios es inmutable")
        void testChangesListIsImmutable() {
            List<ChangeLog> mutableList = new ArrayList<>();
            mutableList.add(new ChangeLog("block-1", 10, 10, 5));

            SurgeryReport report = new SurgeryReport("content", mutableList, 5);

            mutableList.clear();
            assertEquals(1, report.changes().size());

            assertThrows(UnsupportedOperationException.class, () ->
                report.changes().add(new ChangeLog("block-2", 20, 20, 3))
            );
        }
    }

    @Nested
    @DisplayName("Status Methods Tests")
    class StatusMethodsTests {
        @Test
        @DisplayName("isSuccessful cuando content no esta vacio")
        void testIsSuccessful() {
            SurgeryReport report = new SurgeryReport("content", List.of(), 0);
            assertTrue(report.isSuccessful());
        }

        @Test
        @DisplayName("No isSuccessful cuando content esta vacio")
        void testNotSuccessfulWhenBlank() {
            SurgeryReport report = new SurgeryReport("   ", List.of(), 0);
            assertFalse(report.isSuccessful());
        }

        @Test
        @DisplayName("hasChanges cuando hay cambios")
        void testHasChanges() {
            List<ChangeLog> changes = List.of(new ChangeLog("block-1", 10, 10, 5));
            SurgeryReport report = new SurgeryReport("content", changes, 5);
            assertTrue(report.hasChanges());
        }

        @Test
        @DisplayName("No hasChanges cuando lista vacia")
        void testNoChanges() {
            SurgeryReport report = new SurgeryReport("content", List.of(), 0);
            assertFalse(report.hasChanges());
        }

        @Test
        @DisplayName("changeCount retorna numero correcto")
        void testChangeCount() {
            List<ChangeLog> changes = List.of(
                new ChangeLog("block-1", 10, 10, 5),
                new ChangeLog("block-2", 20, 21, 3)
            );
            SurgeryReport report = new SurgeryReport("content", changes, 8);
            assertEquals(2, report.changeCount());
        }
    }

    @Nested
    @DisplayName("Delta Methods Tests")
    class DeltaMethodsTests {
        @Test
        @DisplayName("didGrow cuando delta positivo")
        void testDidGrow() {
            SurgeryReport report = new SurgeryReport("content", List.of(), 10);
            assertTrue(report.didGrow());
            assertFalse(report.didShrink());
        }

        @Test
        @DisplayName("didShrink cuando delta negativo")
        void testDidShrink() {
            SurgeryReport report = new SurgeryReport("content", List.of(), -5);
            assertTrue(report.didShrink());
            assertFalse(report.didGrow());
        }

        @Test
        @DisplayName("Ni grow ni shrink cuando delta cero")
        void testNeitherGrowNorShrink() {
            SurgeryReport report = new SurgeryReport("content", List.of(), 0);
            assertFalse(report.didGrow());
            assertFalse(report.didShrink());
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {
        @Test
        @DisplayName("noChanges crea report sin cambios")
        void testNoChangesFactory() {
            String original = "codigo original";
            SurgeryReport report = SurgeryReport.noChanges(original);

            assertEquals(original, report.content());
            assertFalse(report.hasChanges());
            assertEquals(0, report.changeCount());
            assertEquals(0, report.totalDelta());
            assertTrue(report.isSuccessful());
        }
    }
}
