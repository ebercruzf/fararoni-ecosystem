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
package dev.fararoni.core.ui;

import dev.fararoni.core.core.spi.UserNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("OutputCoordinator implements UserNotifier")
class OutputCoordinatorNotifierTest {
    @Nested
    @DisplayName("Implementación de UserNotifier")
    class ImplementationTests {
        @Test
        @DisplayName("Debe implementar UserNotifier")
        void shouldImplementUserNotifier() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            assertTrue(coordinator instanceof UserNotifier,
                "OutputCoordinator debe implementar UserNotifier");
        }

        @Test
        @DisplayName("Debe poder asignarse a variable UserNotifier")
        void shouldBeAssignableToUserNotifier() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            UserNotifier notifier = coordinator;
            assertNotNull(notifier);
        }
    }

    @Nested
    @DisplayName("notify(String message)")
    class NotifyTests {
        @Test
        @DisplayName("notify() no debe lanzar excepción")
        void notifyShouldNotThrowException() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            assertDoesNotThrow(() -> coordinator.notify("Test notification"));
        }

        @Test
        @DisplayName("Debe manejar mensajes null sin crash")
        void shouldHandleNullMessage() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            assertDoesNotThrow(() -> coordinator.notify(null));
        }

        @Test
        @DisplayName("Debe manejar mensajes vacíos")
        void shouldHandleEmptyMessage() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            assertDoesNotThrow(() -> coordinator.notify(""));
        }

        @Test
        @DisplayName("Debe manejar mensajes con caracteres especiales")
        void shouldHandleSpecialCharacters() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            assertDoesNotThrow(() ->
                coordinator.notify("Test\n\twith\rspecial\0chars"));
        }
    }

    @Nested
    @DisplayName("notify(String message, boolean urgent)")
    class NotifyUrgentTests {
        @Test
        @DisplayName("urgent=true no debe lanzar excepción")
        void urgentTrueShouldNotThrow() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            assertDoesNotThrow(() ->
                coordinator.notify("Urgent message", true));
        }

        @Test
        @DisplayName("urgent=false no debe lanzar excepción")
        void urgentFalseShouldNotThrow() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            assertDoesNotThrow(() ->
                coordinator.notify("Normal message", false));
        }

        @Test
        @DisplayName("Debe manejar múltiples llamadas urgentes")
        void shouldHandleMultipleUrgentCalls() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            for (int i = 0; i < 10; i++) {
                final int idx = i;
                assertDoesNotThrow(() ->
                    coordinator.notify("Message " + idx, true));
            }
        }
    }

    @Nested
    @DisplayName("Integración con Status Line")
    class StatusLineIntegrationTests {
        @Test
        @DisplayName("notify debe funcionar cuando status line está inactiva")
        void shouldWorkWhenStatusLineInactive() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            assertDoesNotThrow(() ->
                coordinator.notify("Message without status line"));
        }

        @Test
        @DisplayName("notify debe funcionar cuando status line está activa")
        void shouldWorkWhenStatusLineActive() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            coordinator.activateStatusLine("Status: Active");

            try {
                assertDoesNotThrow(() ->
                    coordinator.notify("Message with status line"));
            } finally {
                coordinator.deactivateStatusLine();
            }
        }

        @Test
        @DisplayName("notify debe funcionar después de desactivar status line")
        void shouldWorkAfterDeactivatingStatusLine() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            coordinator.activateStatusLine("Status");
            coordinator.deactivateStatusLine();

            assertDoesNotThrow(() ->
                coordinator.notify("Message after deactivation"));
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {
        @Test
        @DisplayName("notify debe ser thread-safe para uso concurrente")
        void notifyShouldBeThreadSafe() throws InterruptedException {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            java.util.concurrent.atomic.AtomicInteger successCount =
                new java.util.concurrent.atomic.AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> {
                    try {
                        coordinator.notify("Thread " + idx + " message");
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                    }
                });
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join();

            assertEquals(threadCount, successCount.get(),
                "Todas las notificaciones deben completarse sin excepción");
        }

        @Test
        @DisplayName("notify con status line activa debe ser thread-safe")
        void notifyWithActiveStatusLineShouldBeThreadSafe() throws InterruptedException {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            coordinator.activateStatusLine("Active");

            try {
                int threadCount = 5;
                Thread[] threads = new Thread[threadCount];
                java.util.concurrent.atomic.AtomicInteger successCount =
                    new java.util.concurrent.atomic.AtomicInteger(0);

                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    threads[i] = new Thread(() -> {
                        try {
                            coordinator.notify("Thread " + idx, true);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                        }
                    });
                }

                for (Thread t : threads) t.start();
                for (Thread t : threads) t.join();

                assertEquals(threadCount, successCount.get());
            } finally {
                coordinator.deactivateStatusLine();
            }
        }
    }

    @Nested
    @DisplayName("Modos de Display")
    class DisplayModeTests {
        @Test
        @DisplayName("Debe funcionar en modo COMPATIBLE")
        void shouldWorkInCompatibleMode() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

            assertDoesNotThrow(() -> coordinator.notify("Compatible mode"));
        }

        @Test
        @DisplayName("Debe funcionar en modo RICH")
        void shouldWorkInRichMode() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.RICH);

            assertDoesNotThrow(() -> coordinator.notify("Rich mode"));
        }

        @Test
        @DisplayName("Debe funcionar en modo SIMPLE")
        void shouldWorkInSimpleMode() {
            OutputCoordinator coordinator = new OutputCoordinator(
                TerminalCapabilityDetector.DisplayMode.SIMPLE);

            assertDoesNotThrow(() -> coordinator.notify("Simple mode"));
        }
    }

    @Nested
    @DisplayName("Verificación de Output (Captura Directa)")
    class OutputVerificationTests {
        @Test
        @DisplayName("printUrgent debe escribir a stdout capturado")
        void printUrgentShouldWriteToCapturedStdout() {
            ByteArrayOutputStream capture = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            System.setOut(new PrintStream(capture));

            try {
                OutputCoordinator coordinator = new OutputCoordinator(
                    TerminalCapabilityDetector.DisplayMode.COMPATIBLE);

                coordinator.printUrgent("Captured message");

                String output = capture.toString();
                assertTrue(output.contains("Captured message"),
                    "printUrgent debe escribir al stdout capturado");
            } finally {
                System.setOut(originalOut);
            }
        }

        @Test
        @DisplayName("notify debe producir output igual a printUrgent")
        void notifyShouldProduceSameOutputAsPrintUrgent() {
            ByteArrayOutputStream capture1 = new ByteArrayOutputStream();
            ByteArrayOutputStream capture2 = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;

            try {
                System.setOut(new PrintStream(capture1));
                OutputCoordinator coord1 = new OutputCoordinator(
                    TerminalCapabilityDetector.DisplayMode.COMPATIBLE);
                coord1.notify("Test message");
                String output1 = capture1.toString();

                System.setOut(new PrintStream(capture2));
                OutputCoordinator coord2 = new OutputCoordinator(
                    TerminalCapabilityDetector.DisplayMode.COMPATIBLE);
                coord2.printUrgent("Test message");
                String output2 = capture2.toString();

                assertEquals(output1, output2,
                    "notify() y printUrgent() deben producir el mismo output");
            } finally {
                System.setOut(originalOut);
            }
        }
    }
}
