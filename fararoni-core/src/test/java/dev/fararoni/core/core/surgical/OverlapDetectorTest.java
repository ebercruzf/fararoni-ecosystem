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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("OverlapDetector Tests")
class OverlapDetectorTest {
    private OverlapDetector detector;

    @BeforeEach
    void setUp() {
        detector = new OverlapDetector();
    }

    @Nested
    @DisplayName("Valid Cases - No Overlap")
    class ValidCasesTests {
        @Test
        @DisplayName("Lista null lanza NullPointerException")
        void testNullListThrows() {
            assertThrows(NullPointerException.class, () -> detector.validate(null));
        }

        @Test
        @DisplayName("Lista vacia no lanza excepcion")
        void testEmptyListPasses() {
            assertDoesNotThrow(() -> detector.validate(List.of()));
        }

        @Test
        @DisplayName("Un solo bloque no lanza excepcion")
        void testSingleBlockPasses() {
            EditBlock single = new EditBlock("edit-1", "search", "replace", 10, 0, 0);
            assertDoesNotThrow(() -> detector.validate(List.of(single)));
        }

        @Test
        @DisplayName("Bloques en lineas diferentes pasan validacion")
        void testDifferentLinesPasses() {
            List<EditBlock> edits = List.of(
                new EditBlock("edit-1", "texto uno", "reemplazo uno", 10, 0, 0),
                new EditBlock("edit-2", "texto dos", "reemplazo dos", 20, 0, 0),
                new EditBlock("edit-3", "texto tres", "reemplazo tres", 30, 0, 0)
            );

            assertDoesNotThrow(() -> detector.validate(edits));
        }
    }

    @Nested
    @DisplayName("Critical Collision Detection")
    class CriticalCollisionTests {
        @Test
        @DisplayName("Detecta colision en misma linea")
        void testSameLineCollision() {
            List<EditBlock> edits = List.of(
                new EditBlock("edit-1", "texto uno", "reemplazo uno", 10, 0, 0),
                new EditBlock("edit-2", "texto dos", "reemplazo dos", 10, 0, 0)
            );

            OverlapConflictException ex = assertThrows(
                OverlapConflictException.class,
                () -> detector.validate(edits)
            );

            assertEquals("COLLISION", ex.getType());
        }

        @Test
        @DisplayName("Detecta colision por mismo texto de busqueda en lineas contiguas")
        void testSameSearchTextCollision() {
            List<EditBlock> edits = List.of(
                new EditBlock("edit-1", "texto duplicado", "reemplazo A", 10, 0, 0),
                new EditBlock("edit-2", "texto duplicado", "reemplazo B", 10, 0, 0)
            );

            OverlapConflictException ex = assertThrows(
                OverlapConflictException.class,
                () -> detector.validate(edits)
            );

            assertTrue("COLLISION".equals(ex.getType()) || "REDUNDANT".equals(ex.getType()));
        }
    }

    @Nested
    @DisplayName("Redundant Conflict Detection")
    class RedundantConflictTests {
        @Test
        @DisplayName("Detecta bloque contenido en otro")
        void testSubsetDetection() {
            List<EditBlock> edits = List.of(
                new EditBlock("edit-1", "texto completo largo", "reemplazo", 10, 0, 0),
                new EditBlock("edit-2", "completo", "otro", 10, 0, 0)
            );

            OverlapConflictException ex = assertThrows(
                OverlapConflictException.class,
                () -> detector.validate(edits)
            );

            assertEquals("REDUNDANT", ex.getType());
        }

        @Test
        @DisplayName("Detecta bloque que contiene a otro")
        void testSupersetDetection() {
            List<EditBlock> edits = List.of(
                new EditBlock("edit-1", "abc", "x", 10, 0, 0),
                new EditBlock("edit-2", "abcdef", "y", 10, 0, 0)
            );

            OverlapConflictException ex = assertThrows(
                OverlapConflictException.class,
                () -> detector.validate(edits)
            );

            assertEquals("REDUNDANT", ex.getType());
        }
    }

    @Nested
    @DisplayName("Escenario 4: Auto-Fix (Superset Wins)")
    class AutoFixTests {
        @Test
        @DisplayName("optimizeRedundancies elimina bloque pequeno contenido en grande")
        void testScenario4_RedundantOverlap_AutoFixed_Silent() {
            EditBlock bigBlock = new EditBlock("Big", "line1;\nline2;\nline3;", "REPLACED_ALL", 10, 0, 0);
            EditBlock smallBlock = new EditBlock("Small", "line2;", "REPLACED_SMALL", 11, 0, 0);

            List<EditBlock> rawPlan = List.of(bigBlock, smallBlock);

            List<EditBlock> cleanPlan = detector.optimizeRedundancies(rawPlan);

            assertEquals(1, cleanPlan.size(), "Debe quedar solo 1 bloque (Superset Wins)");
            assertEquals("Big", cleanPlan.get(0).id(), "Debe sobrevivir el bloque mayor");

            assertDoesNotThrow(() -> detector.validateCollisions(cleanPlan),
                "Plan saneado no debe tener colisiones");
        }

        @Test
        @DisplayName("optimizeRedundancies es agnostico al orden de entrada")
        void testAutoFix_OrderAgnostic() {
            EditBlock smallBlock = new EditBlock("Small", "line2;", "REPLACED_SMALL", 11, 0, 0);
            EditBlock bigBlock = new EditBlock("Big", "line1;\nline2;\nline3;", "REPLACED_ALL", 10, 0, 0);

            List<EditBlock> rawPlan = List.of(smallBlock, bigBlock);

            List<EditBlock> cleanPlan = detector.optimizeRedundancies(rawPlan);

            assertEquals(1, cleanPlan.size(), "Debe quedar solo 1 bloque");
            assertEquals("Big", cleanPlan.get(0).id(), "Debe sobrevivir el mayor independiente del orden");
        }

        @Test
        @DisplayName("optimizeRedundancies preserva bloques no redundantes")
        void testAutoFix_PreservesNonRedundant() {
            EditBlock block1 = new EditBlock("A", "codigo uno", "nuevo uno", 10, 0, 0);
            EditBlock block2 = new EditBlock("B", "codigo dos", "nuevo dos", 20, 0, 0);
            EditBlock block3 = new EditBlock("C", "codigo tres", "nuevo tres", 30, 0, 0);

            List<EditBlock> rawPlan = List.of(block1, block2, block3);

            List<EditBlock> cleanPlan = detector.optimizeRedundancies(rawPlan);

            assertEquals(3, cleanPlan.size(), "Todos los bloques deben preservarse");
        }

        @Test
        @DisplayName("optimizeRedundancies maneja lista null")
        void testAutoFix_NullList() {
            List<EditBlock> cleanPlan = detector.optimizeRedundancies(null);
            assertTrue(cleanPlan.isEmpty(), "Lista null debe devolver lista vacia");
        }

        @Test
        @DisplayName("optimizeRedundancies maneja lista con un elemento")
        void testAutoFix_SingleElement() {
            EditBlock single = new EditBlock("Single", "codigo", "nuevo", 10, 0, 0);
            List<EditBlock> rawPlan = List.of(single);

            List<EditBlock> cleanPlan = detector.optimizeRedundancies(rawPlan);

            assertEquals(1, cleanPlan.size(), "Un solo elemento debe permanecer");
            assertEquals("Single", cleanPlan.get(0).id());
        }

        @Test
        @DisplayName("optimizeRedundancies elimina duplicados exactos")
        void testAutoFix_ExactDuplicates() {
            EditBlock block1 = new EditBlock("Dup1", "mismo codigo", "reemplazo A", 10, 0, 0);
            EditBlock block2 = new EditBlock("Dup2", "mismo codigo", "reemplazo B", 10, 0, 0);

            List<EditBlock> rawPlan = List.of(block1, block2);

            List<EditBlock> cleanPlan = detector.optimizeRedundancies(rawPlan);

            assertEquals(1, cleanPlan.size(), "Duplicados exactos deben reducirse a uno");
        }

        @Test
        @DisplayName("validateCollisions detecta colision REAL (no redundancia)")
        void testValidateCollisions_RealCollision() {
            EditBlock block1 = new EditBlock("A", "linea1\nlinea2", "nuevo1", 10, 0, 0);
            EditBlock block2 = new EditBlock("B", "linea2\nlinea3", "nuevo2", 11, 0, 0);

            List<EditBlock> plan = List.of(block1, block2);

            List<EditBlock> optimized = detector.optimizeRedundancies(plan);
            assertEquals(2, optimized.size(), "No debe eliminar bloques que no son subconjuntos");

            assertThrows(OverlapConflictException.class,
                () -> detector.validateCollisions(optimized),
                "Debe detectar colision real (interseccion parcial)");
        }
    }
}
