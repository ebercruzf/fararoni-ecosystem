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
package dev.fararoni.core.core.lifecycle;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("GracefulShutdownService Tests")
class GracefulShutdownServiceTest {
    private GracefulShutdownService service;

    @BeforeEach
    void setUp() {
        service = GracefulShutdownService.getInstance();
        service.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        service.resetForTesting();
    }

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {
        @Test
        @DisplayName("getInstance debe retornar la misma instancia")
        void getInstance_ShouldReturnSameInstance() {
            GracefulShutdownService instance1 = GracefulShutdownService.getInstance();
            GracefulShutdownService instance2 = GracefulShutdownService.getInstance();

            assertSame(instance1, instance2, "Debe retornar la misma instancia singleton");
        }
    }

    @Nested
    @DisplayName("Resource Registration")
    class RegistrationTests {
        @Test
        @DisplayName("register debe incrementar contador de recursos")
        void register_ShouldIncrementCount() {
            assertEquals(0, service.getRegisteredCount());

            service.register("Resource1", () -> {});
            assertEquals(1, service.getRegisteredCount());

            service.register("Resource2", () -> {});
            assertEquals(2, service.getRegisteredCount());
        }

        @Test
        @DisplayName("register null resource debe ser ignorado")
        void register_NullResource_ShouldBeIgnored() {
            service.register("NullResource", (AutoCloseable) null);
            assertEquals(0, service.getRegisteredCount());
        }

        @Test
        @DisplayName("unregister debe remover recurso")
        void unregister_ShouldRemoveResource() {
            AutoCloseable resource = () -> {};
            service.register("TestResource", resource);
            assertEquals(1, service.getRegisteredCount());

            boolean removed = service.unregister(resource);

            assertTrue(removed);
            assertEquals(0, service.getRegisteredCount());
        }

        @Test
        @DisplayName("unregister recurso inexistente debe retornar false")
        void unregister_NonExistent_ShouldReturnFalse() {
            AutoCloseable resource = () -> {};

            boolean removed = service.unregister(resource);

            assertFalse(removed);
        }
    }

    @Nested
    @DisplayName("Shutdown Execution")
    class ShutdownTests {
        @Test
        @DisplayName("shutdown debe cerrar todos los recursos")
        void shutdown_ShouldCloseAllResources() {
            AtomicInteger closedCount = new AtomicInteger(0);

            service.register("R1", closedCount::incrementAndGet);
            service.register("R2", closedCount::incrementAndGet);
            service.register("R3", closedCount::incrementAndGet);

            GracefulShutdownService.ShutdownResult result = service.shutdown();

            assertEquals(3, closedCount.get());
            assertEquals(3, result.successCount());
            assertEquals(0, result.failureCount());
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("shutdown debe cerrar en orden LIFO")
        void shutdown_ShouldCloseInLifoOrder() {
            List<String> closeOrder = new ArrayList<>();

            service.register("First", () -> closeOrder.add("First"));
            service.register("Second", () -> closeOrder.add("Second"));
            service.register("Third", () -> closeOrder.add("Third"));

            service.shutdown();

            assertEquals(List.of("Third", "Second", "First"), closeOrder);
        }

        @Test
        @DisplayName("shutdown debe continuar si un recurso falla")
        void shutdown_ShouldContinueOnFailure() {
            AtomicInteger closedCount = new AtomicInteger(0);

            service.register("R1", closedCount::incrementAndGet);
            service.register("R2-Failing", () -> { throw new RuntimeException("Simulated failure"); });
            service.register("R3", closedCount::incrementAndGet);

            GracefulShutdownService.ShutdownResult result = service.shutdown();

            assertEquals(2, closedCount.get(), "Otros recursos deben cerrarse aunque uno falle");
            assertEquals(2, result.successCount());
            assertEquals(1, result.failureCount());
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("shutdown doble debe retornar wasAlreadyRunning")
        void shutdown_Twice_ShouldReturnWasAlreadyRunning() {
            service.register("R1", () -> {});

            GracefulShutdownService.ShutdownResult first = service.shutdown();
            GracefulShutdownService.ShutdownResult second = service.shutdown();

            assertFalse(first.wasAlreadyRunning());
            assertTrue(second.wasAlreadyRunning());
        }

        @Test
        @DisplayName("shutdown sin recursos debe funcionar")
        void shutdown_NoResources_ShouldSucceed() {
            GracefulShutdownService.ShutdownResult result = service.shutdown();

            assertEquals(0, result.successCount());
            assertEquals(0, result.failureCount());
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("State Management")
    class StateTests {
        @Test
        @DisplayName("isShutdownStarted debe ser false inicialmente")
        void isShutdownStarted_Initially_ShouldBeFalse() {
            assertFalse(service.isShutdownStarted());
        }

        @Test
        @DisplayName("isShutdownStarted debe ser true despues de shutdown")
        void isShutdownStarted_AfterShutdown_ShouldBeTrue() {
            service.shutdown();

            assertTrue(service.isShutdownStarted());
        }

        @Test
        @DisplayName("isShutdownCompleted debe ser true despues de shutdown")
        void isShutdownCompleted_AfterShutdown_ShouldBeTrue() {
            assertFalse(service.isShutdownCompleted());

            service.shutdown();

            assertTrue(service.isShutdownCompleted());
        }

        @Test
        @DisplayName("register despues de shutdown debe lanzar excepcion")
        void register_AfterShutdown_ShouldThrow() {
            service.shutdown();

            assertThrows(IllegalStateException.class, () ->
                service.register("LateResource", () -> {})
            );
        }
    }

    @Nested
    @DisplayName("ShutdownResult")
    class ShutdownResultTests {
        @Test
        @DisplayName("totalCount debe sumar success y failure")
        void totalCount_ShouldSumSuccessAndFailure() {
            var result = new GracefulShutdownService.ShutdownResult(5, 2, 100, false);

            assertEquals(7, result.totalCount());
        }

        @Test
        @DisplayName("isSuccess debe ser true solo con 0 failures")
        void isSuccess_ShouldBeTrueOnlyWithZeroFailures() {
            var success = new GracefulShutdownService.ShutdownResult(5, 0, 100, false);
            var failure = new GracefulShutdownService.ShutdownResult(5, 1, 100, false);
            var alreadyRunning = new GracefulShutdownService.ShutdownResult(0, 0, 0, true);

            assertTrue(success.isSuccess());
            assertFalse(failure.isSuccess());
            assertFalse(alreadyRunning.isSuccess());
        }
    }

    @Nested
    @DisplayName("Closeable Support")
    class CloseableTests {
        @Test
        @DisplayName("register Closeable debe funcionar igual que AutoCloseable")
        void register_Closeable_ShouldWork() {
            AtomicBoolean closed = new AtomicBoolean(false);
            java.io.Closeable closeable = () -> closed.set(true);

            service.register("CloseableResource", closeable);
            service.shutdown();

            assertTrue(closed.get());
        }
    }

    @Nested
    @DisplayName("Performance")
    class PerformanceTests {
        @Test
        @DisplayName("shutdown debe reportar tiempo transcurrido")
        void shutdown_ShouldReportElapsedTime() {
            service.register("SlowResource", () -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            GracefulShutdownService.ShutdownResult result = service.shutdown();

            assertTrue(result.elapsedMs() >= 40,
                "Tiempo debe ser al menos 40ms, actual: " + result.elapsedMs());
        }
    }
}
