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
package dev.fararoni.bus.agent.api.bus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para BusOverloadException.
 */
@DisplayName("BusOverloadException")
class BusOverloadExceptionTest {

    @Test
    @DisplayName("Constructor almacena mensaje y valores correctamente")
    void constructorAlmacenaValores() {
        var ex = new BusOverloadException("Bus sobrecargado", 9500, 10000);

        assertEquals("Bus sobrecargado", ex.getMessage());
        assertEquals(9500, ex.getInFlightCount());
        assertEquals(10000, ex.getMaxCapacity());
    }

    @Test
    @DisplayName("getUtilizationPercent() calcula porcentaje correcto")
    void getUtilizationPercent() {
        var ex = new BusOverloadException("Test", 5000, 10000);
        assertEquals(50.0, ex.getUtilizationPercent(), 0.01);

        var ex2 = new BusOverloadException("Test", 9000, 10000);
        assertEquals(90.0, ex2.getUtilizationPercent(), 0.01);

        var ex3 = new BusOverloadException("Test", 10000, 10000);
        assertEquals(100.0, ex3.getUtilizationPercent(), 0.01);
    }

    @Test
    @DisplayName("getUtilizationPercent() retorna -1 para capacidad cero")
    void getUtilizationPercentCapacidadCero() {
        var ex = new BusOverloadException("Test", 100, 0);
        assertEquals(-1.0, ex.getUtilizationPercent(), 0.01);
    }

    @Test
    @DisplayName("getSuggestedRetryMs() retorna valores basados en utilizacion")
    void getSuggestedRetryMs() {
        var ex99 = new BusOverloadException("Test", 9900, 10000);
        assertEquals(5000, ex99.getSuggestedRetryMs());

        var ex95 = new BusOverloadException("Test", 9500, 10000);
        assertEquals(2000, ex95.getSuggestedRetryMs());

        var ex90 = new BusOverloadException("Test", 9000, 10000);
        assertEquals(1000, ex90.getSuggestedRetryMs());

        var ex50 = new BusOverloadException("Test", 5000, 10000);
        assertEquals(500, ex50.getSuggestedRetryMs());
    }

    @Test
    @DisplayName("Es RuntimeException")
    void esRuntimeException() {
        var ex = new BusOverloadException("Test", 0, 0);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    @DisplayName("Puede ser lanzada y capturada")
    void puedeSerLanzadaYCapturada() {
        assertThrows(BusOverloadException.class, () -> {
            throw new BusOverloadException("Backpressure activo", 9999, 10000);
        });
    }
}
