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
package dev.fararoni.bus.agent.api.telemetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para SovereignMetrics.
 * Valida: singleton, counters, gauges, concurrencia.
 */
@DisplayName("SovereignMetrics")
class SovereignMetricsTest {

    @BeforeEach
    void limpiarMetricas() {
        // Reset metricas antes de cada test
        SovereignMetrics.INSTANCE.reset();
    }

    @Nested
    @DisplayName("Singleton")
    class Singleton {

        @Test
        @DisplayName("INSTANCE no es null")
        void instanceNoEsNull() {
            assertNotNull(SovereignMetrics.INSTANCE);
        }

        @Test
        @DisplayName("INSTANCE es siempre la misma referencia")
        void instanceEsMismaReferencia() {
            assertSame(SovereignMetrics.INSTANCE, SovereignMetrics.INSTANCE);
        }
    }

    @Nested
    @DisplayName("Counters")
    class Counters {

        @Test
        @DisplayName("increment() incrementa contador en 1")
        void incrementBasico() {
            SovereignMetrics.INSTANCE.increment("test.counter");

            var snapshot = SovereignMetrics.INSTANCE.snapshot();
            assertEquals(1L, snapshot.get("test.counter"));
        }

        @Test
        @DisplayName("Multiples increments acumulan")
        void multiplesIncrements() {
            SovereignMetrics.INSTANCE.increment("test.counter");
            SovereignMetrics.INSTANCE.increment("test.counter");
            SovereignMetrics.INSTANCE.increment("test.counter");

            var snapshot = SovereignMetrics.INSTANCE.snapshot();
            assertEquals(3L, snapshot.get("test.counter"));
        }

        @Test
        @DisplayName("Counters diferentes son independientes")
        void countersDiferentesIndependientes() {
            SovereignMetrics.INSTANCE.increment("counter.a");
            SovereignMetrics.INSTANCE.increment("counter.a");
            SovereignMetrics.INSTANCE.increment("counter.b");

            var snapshot = SovereignMetrics.INSTANCE.snapshot();
            assertEquals(2L, snapshot.get("counter.a"));
            assertEquals(1L, snapshot.get("counter.b"));
        }

        @Test
        @DisplayName("Counter inexistente retorna 0 en snapshot")
        void counterInexistente() {
            var snapshot = SovereignMetrics.INSTANCE.snapshot();
            assertNull(snapshot.get("no.existe"));
        }
    }

    @Nested
    @DisplayName("Gauges")
    class Gauges {

        @Test
        @DisplayName("gauge() incrementa valor positivo")
        void gaugePositivo() {
            SovereignMetrics.INSTANCE.gauge("test.gauge", 5);

            assertEquals(5L, SovereignMetrics.INSTANCE.getGauge("test.gauge"));
        }

        @Test
        @DisplayName("gauge() decrementa valor negativo")
        void gaugeNegativo() {
            SovereignMetrics.INSTANCE.gauge("test.gauge", 10);
            SovereignMetrics.INSTANCE.gauge("test.gauge", -3);

            assertEquals(7L, SovereignMetrics.INSTANCE.getGauge("test.gauge"));
        }

        @Test
        @DisplayName("gauge() puede llegar a negativo")
        void gaugePuedeSerNegativo() {
            SovereignMetrics.INSTANCE.gauge("test.gauge", -5);

            assertEquals(-5L, SovereignMetrics.INSTANCE.getGauge("test.gauge"));
        }

        @Test
        @DisplayName("Gauges son independientes de counters")
        void gaugesIndependientesDeCounters() {
            SovereignMetrics.INSTANCE.increment("metric");
            SovereignMetrics.INSTANCE.gauge("metric.gauge", 100);

            assertEquals(1L, SovereignMetrics.INSTANCE.getCount("metric"));
            assertEquals(100L, SovereignMetrics.INSTANCE.getGauge("metric.gauge"));
        }

        @Test
        @DisplayName("setGauge() establece valor absoluto")
        void setGaugeEstableceValorAbsoluto() {
            SovereignMetrics.INSTANCE.gauge("abs.gauge", 50);
            SovereignMetrics.INSTANCE.setGauge("abs.gauge", 10);

            assertEquals(10L, SovereignMetrics.INSTANCE.getGauge("abs.gauge"));
        }
    }

    @Nested
    @DisplayName("Snapshot")
    class Snapshot {

        @Test
        @DisplayName("snapshot() retorna copia inmutable de estado")
        void snapshotRetornaCopia() {
            SovereignMetrics.INSTANCE.increment("test");
            var snapshot1 = SovereignMetrics.INSTANCE.snapshot();

            SovereignMetrics.INSTANCE.increment("test");
            var snapshot2 = SovereignMetrics.INSTANCE.snapshot();

            // snapshot1 no debe cambiar
            assertEquals(1L, snapshot1.get("test"));
            assertEquals(2L, snapshot2.get("test"));
        }

        @Test
        @DisplayName("snapshot() incluye todos los contadores y gauges")
        void snapshotIncluyeTodo() {
            SovereignMetrics.INSTANCE.increment("counter.a");
            SovereignMetrics.INSTANCE.increment("counter.b");
            SovereignMetrics.INSTANCE.gauge("gauge.x", 50);

            var snapshot = SovereignMetrics.INSTANCE.snapshot();

            assertTrue(snapshot.containsKey("counter.a"));
            assertTrue(snapshot.containsKey("counter.b"));
            // Gauges tienen prefijo "gauge." en snapshot()
            assertTrue(snapshot.containsKey("gauge.gauge.x"));
        }

        @Test
        @DisplayName("snapshotCounters() solo retorna counters")
        void snapshotCountersSoloCounters() {
            SovereignMetrics.INSTANCE.increment("my.counter");
            SovereignMetrics.INSTANCE.gauge("my.gauge", 10);

            var snapshot = SovereignMetrics.INSTANCE.snapshotCounters();

            assertTrue(snapshot.containsKey("my.counter"));
            assertFalse(snapshot.containsKey("my.gauge"));
        }

        @Test
        @DisplayName("snapshotGauges() solo retorna gauges")
        void snapshotGaugesSoloGauges() {
            SovereignMetrics.INSTANCE.increment("my.counter");
            SovereignMetrics.INSTANCE.gauge("my.gauge", 10);

            var snapshot = SovereignMetrics.INSTANCE.snapshotGauges();

            assertFalse(snapshot.containsKey("my.counter"));
            assertTrue(snapshot.containsKey("my.gauge"));
        }
    }

    @Nested
    @DisplayName("Reset")
    class Reset {

        @Test
        @DisplayName("reset() limpia todas las metricas")
        void resetLimpiaTodo() {
            SovereignMetrics.INSTANCE.increment("counter");
            SovereignMetrics.INSTANCE.gauge("gauge", 100);

            SovereignMetrics.INSTANCE.reset();

            var snapshot = SovereignMetrics.INSTANCE.snapshot();
            assertTrue(snapshot.isEmpty());
        }
    }

    @Nested
    @DisplayName("Concurrencia")
    class Concurrencia {

        @Test
        @DisplayName("increment() es thread-safe")
        void incrementThreadSafe() throws InterruptedException {
            int threads = 10;
            int incrementsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        SovereignMetrics.INSTANCE.increment("concurrent.counter");
                    }
                    latch.countDown();
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            var snapshot = SovereignMetrics.INSTANCE.snapshot();
            assertEquals((long) threads * incrementsPerThread, snapshot.get("concurrent.counter"));
        }

        @Test
        @DisplayName("gauge() es thread-safe")
        void gaugeThreadSafe() throws InterruptedException {
            int threads = 10;
            int operationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            // Cada thread incrementa y decrementa el mismo gauge
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < operationsPerThread; j++) {
                        SovereignMetrics.INSTANCE.gauge("concurrent.gauge", 1);
                        SovereignMetrics.INSTANCE.gauge("concurrent.gauge", -1);
                    }
                    latch.countDown();
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            // Debe ser 0 porque cada +1 tiene un -1
            assertEquals(0L, SovereignMetrics.INSTANCE.getGauge("concurrent.gauge"));
        }
    }

    @Nested
    @DisplayName("Metricas Tipicas del Bus")
    class MetricasBus {

        @Test
        @DisplayName("Simula metricas de mensajeria")
        void simulaMetricasMensajeria() {
            // Simular 100 mensajes publicados
            for (int i = 0; i < 100; i++) {
                SovereignMetrics.INSTANCE.increment("bus.messages.published");
            }

            // Simular 95 exitos, 5 fallos
            for (int i = 0; i < 95; i++) {
                SovereignMetrics.INSTANCE.increment("bus.messages.success");
            }
            for (int i = 0; i < 5; i++) {
                SovereignMetrics.INSTANCE.increment("bus.messages.failed");
            }

            // Simular inflight gauge
            SovereignMetrics.INSTANCE.gauge("bus.inflight", 50);
            SovereignMetrics.INSTANCE.gauge("bus.inflight", -30);

            // Verificar counters
            assertEquals(100L, SovereignMetrics.INSTANCE.getCount("bus.messages.published"));
            assertEquals(95L, SovereignMetrics.INSTANCE.getCount("bus.messages.success"));
            assertEquals(5L, SovereignMetrics.INSTANCE.getCount("bus.messages.failed"));

            // Verificar gauge
            assertEquals(20L, SovereignMetrics.INSTANCE.getGauge("bus.inflight"));
        }
    }
}
