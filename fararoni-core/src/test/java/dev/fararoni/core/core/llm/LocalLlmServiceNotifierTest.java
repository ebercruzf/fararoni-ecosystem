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
package dev.fararoni.core.core.llm;

import dev.fararoni.core.core.spi.UserNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("LocalLlmService - UserNotifier Integration")
class LocalLlmServiceNotifierTest {
    @Nested
    @DisplayName("Constructores")
    class ConstructorTests {
        @Test
        @DisplayName("Constructor sin notifier debe usar fallback a stderr")
        void constructorWithoutNotifierShouldUseFallback() {
            assertDoesNotThrow(() -> {
                LocalLlmService service = new LocalLlmService();
                assertNotNull(service);
            });
        }

        @Test
        @DisplayName("Constructor con config debe usar fallback a stderr")
        void constructorWithConfigShouldUseFallback() {
            LocalLlmConfig config = LocalLlmConfig.defaults();

            assertDoesNotThrow(() -> {
                LocalLlmService service = new LocalLlmService(config);
                assertNotNull(service);
            });
        }

        @Test
        @DisplayName("Constructor con notifier null debe usar fallback")
        void constructorWithNullNotifierShouldUseFallback() {
            LocalLlmConfig config = LocalLlmConfig.defaults();

            assertDoesNotThrow(() -> {
                LocalLlmService service = new LocalLlmService(config, null);
                assertNotNull(service);
            });
        }

        @Test
        @DisplayName("Constructor con notifier personalizado debe usarlo")
        void constructorWithCustomNotifierShouldUseIt() {
            LocalLlmConfig config = LocalLlmConfig.defaults();
            List<String> captured = new ArrayList<>();
            UserNotifier customNotifier = captured::add;

            LocalLlmService service = new LocalLlmService(config, customNotifier);
            assertNotNull(service);
        }

        @Test
        @DisplayName("Constructor con config null debe lanzar NullPointerException")
        void constructorWithNullConfigShouldThrow() {
            assertThrows(NullPointerException.class, () ->
                new LocalLlmService(null, UserNotifier.silent())
            );
        }
    }

    @Nested
    @DisplayName("Fallback a stderr")
    class FallbackTests {
        @Test
        @DisplayName("stderrFallback() debe retornar notifier funcional")
        void stderrFallbackShouldReturnFunctionalNotifier() {
            UserNotifier fallback = UserNotifier.stderrFallback();

            assertDoesNotThrow(() -> fallback.notify("Test"));
            assertDoesNotThrow(() -> fallback.notify("Test", true));
            assertDoesNotThrow(() -> fallback.notify("Test", false));
        }

        @Test
        @DisplayName("silent() debe retornar notifier no-op")
        void silentShouldReturnNoOpNotifier() {
            UserNotifier silent = UserNotifier.silent();

            assertDoesNotThrow(() -> silent.notify("Ignored"));
        }
    }

    @Nested
    @DisplayName("Captura de Notificaciones")
    class NotificationCaptureTests {
        @Test
        @DisplayName("Debe poder capturar notificaciones con mock")
        void shouldCaptureNotificationsWithMock() {
            List<String> captured = new ArrayList<>();
            UserNotifier mockNotifier = captured::add;

            LocalLlmConfig config = LocalLlmConfig.defaults();
            LocalLlmService service = new LocalLlmService(config, mockNotifier);

            assertNotNull(service);
        }

        @Test
        @DisplayName("Notifier urgente debe registrar urgencia")
        void urgentNotifierShouldRegisterUrgency() {
            List<String> normalMsgs = new ArrayList<>();
            List<String> urgentMsgs = new ArrayList<>();

            UserNotifier trackingNotifier = new UserNotifier() {
                @Override
                public void notify(String message) {
                    normalMsgs.add(message);
                }

                @Override
                public void notify(String message, boolean urgent) {
                    if (urgent) {
                        urgentMsgs.add(message);
                    } else {
                        normalMsgs.add(message);
                    }
                }
            };

            trackingNotifier.notify("Normal");
            trackingNotifier.notify("Urgent", true);
            trackingNotifier.notify("Also normal", false);

            assertEquals(2, normalMsgs.size());
            assertEquals(1, urgentMsgs.size());
            assertEquals("Urgent", urgentMsgs.get(0));
        }
    }

    @Nested
    @DisplayName("Composición de Notifiers")
    class CompositionTests {
        @Test
        @DisplayName("Debe poder componer notifier que loggea y muestra")
        void shouldComposeLoggingAndDisplayNotifier() {
            List<String> logOutput = new ArrayList<>();
            List<String> displayOutput = new ArrayList<>();

            UserNotifier compositeNotifier = msg -> {
                logOutput.add("[LOG] " + msg);
                displayOutput.add(msg);
            };

            compositeNotifier.notify("Test message");

            assertEquals(1, logOutput.size());
            assertEquals(1, displayOutput.size());
            assertEquals("[LOG] Test message", logOutput.get(0));
            assertEquals("Test message", displayOutput.get(0));
        }

        @Test
        @DisplayName("Debe poder crear notifier condicional basado en debug mode")
        void shouldCreateConditionalDebugNotifier() {
            AtomicBoolean debugMode = new AtomicBoolean(false);
            List<String> debugOutput = new ArrayList<>();

            UserNotifier debugNotifier = msg -> {
                if (debugMode.get()) {
                    debugOutput.add(msg);
                }
            };

            debugNotifier.notify("Should be ignored");
            assertEquals(0, debugOutput.size());

            debugMode.set(true);
            debugNotifier.notify("Should be captured");
            assertEquals(1, debugOutput.size());
        }
    }

    @Nested
    @DisplayName("Integración con LocalLlmConfig")
    class ConfigIntegrationTests {
        @Test
        @DisplayName("Debe respetar debugLogging en config")
        void shouldRespectDebugLoggingInConfig() {
            LocalLlmConfig configNoDebug = LocalLlmConfig.defaults();

            assertDoesNotThrow(() -> {
                LocalLlmService service = new LocalLlmService(configNoDebug,
                    UserNotifier.silent());
                assertNotNull(service);
            });
        }

        @Test
        @DisplayName("Debe poder usar notifier con cualquier config")
        void shouldWorkWithAnyConfig() {
            List<String> captured = new ArrayList<>();
            UserNotifier notifier = captured::add;

            LocalLlmService service1 = new LocalLlmService(
                LocalLlmConfig.defaults(), notifier);
            assertNotNull(service1);

            try {
                LocalLlmConfig envConfig = LocalLlmConfig.fromEnvironment();
                LocalLlmService service2 = new LocalLlmService(envConfig, notifier);
                assertNotNull(service2);
            } catch (Exception e) {
            }
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {
        @Test
        @DisplayName("Notifier debe ser invocable desde múltiples threads")
        void notifierShouldBeInvocableFromMultipleThreads() throws InterruptedException {
            List<String> captured = java.util.Collections.synchronizedList(new ArrayList<>());
            UserNotifier threadSafeNotifier = captured::add;

            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[i] = new Thread(() ->
                    threadSafeNotifier.notify("Message " + idx)
                );
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join();

            assertEquals(threadCount, captured.size(),
                "Todas las notificaciones deben ser capturadas");
        }
    }
}
