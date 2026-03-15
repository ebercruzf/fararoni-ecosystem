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
package dev.fararoni.core.core.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("UserNotifier - Interface de Notificaciones")
class UserNotifierTest {
    @Nested
    @DisplayName("stderrFallback()")
    class StderrFallbackTests {
        @Test
        @DisplayName("Debe escribir a stderr")
        void shouldWriteToStderr() {
            ByteArrayOutputStream errContent = new ByteArrayOutputStream();
            PrintStream originalErr = System.err;
            System.setErr(new PrintStream(errContent));

            try {
                UserNotifier notifier = UserNotifier.stderrFallback();
                notifier.notify("Test message");

                String output = errContent.toString();
                assertTrue(output.contains("Test message"),
                    "El mensaje debe aparecer en stderr");
            } finally {
                System.setErr(originalErr);
            }
        }

        @Test
        @DisplayName("Debe retornar instancia no-null")
        void shouldReturnNonNullInstance() {
            UserNotifier notifier = UserNotifier.stderrFallback();
            assertNotNull(notifier, "stderrFallback() no debe retornar null");
        }

        @Test
        @DisplayName("Debe ser thread-safe para uso concurrente")
        void shouldBeThreadSafe() throws InterruptedException {
            UserNotifier notifier = UserNotifier.stderrFallback();
            AtomicInteger successCount = new AtomicInteger(0);
            int threadCount = 10;

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> {
                    try {
                        notifier.notify("Message from thread " + idx);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                    }
                });
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join();

            assertEquals(threadCount, successCount.get(),
                "Todas las notificaciones deben completarse");
        }
    }

    @Nested
    @DisplayName("silent()")
    class SilentTests {
        @Test
        @DisplayName("Debe no hacer nada (no-op)")
        void shouldDoNothing() {
            UserNotifier notifier = UserNotifier.silent();

            assertDoesNotThrow(() -> notifier.notify("Ignored message"));
            assertDoesNotThrow(() -> notifier.notify("Another ignored", true));
        }

        @Test
        @DisplayName("Debe no escribir a ningún stream")
        void shouldNotWriteAnywhere() {
            ByteArrayOutputStream outContent = new ByteArrayOutputStream();
            ByteArrayOutputStream errContent = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;

            System.setOut(new PrintStream(outContent));
            System.setErr(new PrintStream(errContent));

            try {
                UserNotifier notifier = UserNotifier.silent();
                notifier.notify("This should not appear");

                assertEquals(0, outContent.size(), "stdout debe estar vacío");
                assertEquals(0, errContent.size(), "stderr debe estar vacío");
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }
    }

    @Nested
    @DisplayName("Contrato de Interface")
    class ContractTests {
        @Test
        @DisplayName("Debe ser @FunctionalInterface (lambda compatible)")
        void shouldBeFunctionalInterface() {
            List<String> captured = new ArrayList<>();

            UserNotifier lambdaNotifier = captured::add;

            lambdaNotifier.notify("Lambda test");

            assertEquals(1, captured.size());
            assertEquals("Lambda test", captured.get(0));
        }

        @Test
        @DisplayName("notify(msg, urgent) debe delegar a notify(msg) por defecto")
        void notifyWithUrgentShouldDelegateByDefault() {
            AtomicBoolean called = new AtomicBoolean(false);

            UserNotifier notifier = msg -> called.set(true);
            notifier.notify("Test", true);

            assertTrue(called.get(),
                "notify(msg, urgent) debe llamar a notify(msg)");
        }

        @Test
        @DisplayName("notify(msg, urgent=false) debe funcionar igual")
        void notifyWithUrgentFalseShouldWork() {
            List<String> captured = new ArrayList<>();

            UserNotifier notifier = captured::add;
            notifier.notify("Non-urgent", false);

            assertEquals(1, captured.size());
            assertEquals("Non-urgent", captured.get(0));
        }
    }

    @Nested
    @DisplayName("Casos Edge")
    class EdgeCaseTests {
        @Test
        @DisplayName("Debe manejar mensaje null sin crash")
        void shouldHandleNullMessage() {
            UserNotifier notifier = UserNotifier.stderrFallback();

            assertDoesNotThrow(() -> notifier.notify(null));
        }

        @Test
        @DisplayName("Debe manejar mensaje vacío")
        void shouldHandleEmptyMessage() {
            List<String> captured = new ArrayList<>();
            UserNotifier notifier = captured::add;

            notifier.notify("");

            assertEquals(1, captured.size());
            assertEquals("", captured.get(0));
        }

        @Test
        @DisplayName("Debe manejar mensajes con caracteres especiales")
        void shouldHandleSpecialCharacters() {
            List<String> captured = new ArrayList<>();
            UserNotifier notifier = captured::add;

            String specialMsg = "Test\n\twith\rspecial\0chars";
            notifier.notify(specialMsg);

            assertEquals(specialMsg, captured.get(0));
        }

        @Test
        @DisplayName("Debe manejar mensajes muy largos")
        void shouldHandleLongMessages() {
            List<String> captured = new ArrayList<>();
            UserNotifier notifier = captured::add;

            String longMsg = "A".repeat(100_000);
            notifier.notify(longMsg);

            assertEquals(longMsg, captured.get(0));
        }
    }

    @Nested
    @DisplayName("Composición de Notifiers")
    class CompositionTests {
        @Test
        @DisplayName("Debe poder componer múltiples notifiers")
        void shouldComposeMultipleNotifiers() {
            List<String> log1 = new ArrayList<>();
            List<String> log2 = new ArrayList<>();

            UserNotifier notifier1 = log1::add;
            UserNotifier notifier2 = log2::add;

            UserNotifier composite = msg -> {
                notifier1.notify(msg);
                notifier2.notify(msg);
            };

            composite.notify("Composite message");

            assertEquals(1, log1.size());
            assertEquals(1, log2.size());
            assertEquals("Composite message", log1.get(0));
            assertEquals("Composite message", log2.get(0));
        }

        @Test
        @DisplayName("Debe poder crear notifier condicional")
        void shouldCreateConditionalNotifier() {
            List<String> captured = new ArrayList<>();
            AtomicBoolean enabled = new AtomicBoolean(false);

            UserNotifier conditional = msg -> {
                if (enabled.get()) {
                    captured.add(msg);
                }
            };

            conditional.notify("Should be ignored");
            assertEquals(0, captured.size());

            enabled.set(true);
            conditional.notify("Should be captured");
            assertEquals(1, captured.size());
        }
    }
}
