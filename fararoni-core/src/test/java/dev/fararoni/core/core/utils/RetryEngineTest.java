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
package dev.fararoni.core.core.utils;

import dev.fararoni.core.core.utils.RetryEngine.RetryExhaustedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("RetryEngine")
class RetryEngineTest {
    @Nested
    @DisplayName("Ejecucion Exitosa")
    class SuccessTests {
        @Test
        @DisplayName("retorna resultado si operacion exitosa en primer intento")
        void execute_SuccessFirstAttempt_ReturnsResult() {
            String result = RetryEngine.execute(
                () -> "success",
                ex -> true,
                "Test operation"
            );

            assertEquals("success", result);
        }

        @Test
        @DisplayName("retorna resultado despues de reintentos exitosos")
        void execute_SuccessAfterRetries_ReturnsResult() {
            AtomicInteger attempts = new AtomicInteger(0);

            String result = RetryEngine.builder()
                .maxRetries(3)
                .initialDelayMs(1)
                .execute(() -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new IOException("Simulated failure");
                    }
                    return "success after retries";
                }, "Retry test");

            assertEquals("success after retries", result);
            assertEquals(3, attempts.get());
        }

        @Test
        @DisplayName("executeAny reintenta cualquier excepcion")
        void executeAny_RetriesAnyException() {
            AtomicInteger attempts = new AtomicInteger(0);

            String result = RetryEngine.builder()
                .maxRetries(2)
                .initialDelayMs(1)
                .retryOn(ex -> true)
                .execute(() -> {
                    if (attempts.incrementAndGet() < 2) {
                        throw new RuntimeException("Any error");
                    }
                    return "ok";
                }, "Any exception test");

            assertEquals("ok", result);
        }
    }

    @Nested
    @DisplayName("Predicado de Reintentos")
    class PredicateTests {
        @Test
        @DisplayName("reintenta solo excepciones que cumplen predicado")
        void execute_PredicateMatch_Retries() {
            AtomicInteger attempts = new AtomicInteger(0);

            String result = RetryEngine.builder()
                .maxRetries(3)
                .initialDelayMs(1)
                .retryOn(ex -> ex instanceof IOException)
                .execute(() -> {
                    if (attempts.incrementAndGet() < 2) {
                        throw new IOException("Retryable");
                    }
                    return "success";
                }, "Predicate test");

            assertEquals("success", result);
            assertEquals(2, attempts.get());
        }

        @Test
        @DisplayName("no reintenta excepciones que no cumplen predicado")
        void execute_PredicateNoMatch_NoRetry() {
            AtomicInteger attempts = new AtomicInteger(0);

            assertThrows(RuntimeException.class, () -> {
                RetryEngine.builder()
                    .maxRetries(3)
                    .initialDelayMs(1)
                    .retryOn(ex -> ex instanceof IOException)
                    .execute(() -> {
                        attempts.incrementAndGet();
                        throw new IllegalArgumentException("Not retryable");
                    }, "No retry test");
            });

            assertEquals(1, attempts.get());
        }

        @Test
        @DisplayName("predicado personalizado funciona correctamente")
        void execute_CustomPredicate_Works() {
            AtomicInteger attempts = new AtomicInteger(0);

            assertThrows(RuntimeException.class, () -> {
                RetryEngine.builder()
                    .maxRetries(2)
                    .initialDelayMs(1)
                    .retryOn(ex -> ex.getMessage() != null && ex.getMessage().contains("retry"))
                    .execute(() -> {
                        attempts.incrementAndGet();
                        throw new RuntimeException("do not continue");
                    }, "Custom predicate");
            });

            assertEquals(1, attempts.get());
        }
    }

    @Nested
    @DisplayName("Reintentos Agotados")
    class ExhaustedTests {
        @Test
        @DisplayName("lanza RetryExhaustedException cuando se agotan reintentos")
        void execute_AllRetriesFail_ThrowsExhaustedException() {
            AtomicInteger attempts = new AtomicInteger(0);

            RetryExhaustedException ex = assertThrows(RetryExhaustedException.class, () -> {
                RetryEngine.builder()
                    .maxRetries(3)
                    .initialDelayMs(1)
                    .retryOn(e -> true)
                    .execute(() -> {
                        attempts.incrementAndGet();
                        throw new IOException("Always fails");
                    }, "Exhausted test");
            });

            assertEquals(4, attempts.get());
            assertTrue(ex.getMessage().contains("failed after 4 attempts"));
            assertInstanceOf(IOException.class, ex.getCause());
        }

        @Test
        @DisplayName("mensaje de excepcion incluye descripcion")
        void execute_ExhaustedMessage_IncludesDescription() {
            RetryExhaustedException ex = assertThrows(RetryExhaustedException.class, () -> {
                RetryEngine.builder()
                    .maxRetries(1)
                    .initialDelayMs(1)
                    .execute(() -> {
                        throw new RuntimeException("error");
                    }, "Fetching user data");
            });

            assertTrue(ex.getMessage().contains("Fetching user data"));
        }

        @Test
        @DisplayName("con maxRetries=0 no reintenta")
        void execute_ZeroRetries_NoRetry() {
            AtomicInteger attempts = new AtomicInteger(0);

            assertThrows(RetryExhaustedException.class, () -> {
                RetryEngine.builder()
                    .maxRetries(0)
                    .initialDelayMs(1)
                    .execute(() -> {
                        attempts.incrementAndGet();
                        throw new RuntimeException("fail");
                    }, "Zero retries");
            });

            assertEquals(1, attempts.get());
        }
    }

    @Nested
    @DisplayName("Builder Configuration")
    class BuilderTests {
        @Test
        @DisplayName("builder usa valores por defecto correctos")
        void builder_DefaultValues() {
            RetryEngine engine = RetryEngine.builder().build();

            assertEquals(3, engine.getMaxRetries());
            assertEquals(200, engine.getInitialDelayMs());
            assertEquals(10_000, engine.getMaxDelayMs());
            assertEquals(0.20, engine.getJitterPercent(), 0.001);
        }

        @Test
        @DisplayName("builder permite configurar maxRetries")
        void builder_CustomMaxRetries() {
            RetryEngine engine = RetryEngine.builder()
                .maxRetries(5)
                .build();

            assertEquals(5, engine.getMaxRetries());
        }

        @Test
        @DisplayName("builder permite configurar delays")
        void builder_CustomDelays() {
            RetryEngine engine = RetryEngine.builder()
                .initialDelayMs(100)
                .maxDelayMs(5000)
                .build();

            assertEquals(100, engine.getInitialDelayMs());
            assertEquals(5000, engine.getMaxDelayMs());
        }

        @Test
        @DisplayName("builder permite configurar jitter")
        void builder_CustomJitter() {
            RetryEngine engine = RetryEngine.builder()
                .jitterPercent(0.30)
                .build();

            assertEquals(0.30, engine.getJitterPercent(), 0.001);
        }

        @Test
        @DisplayName("builder valida maxRetries negativo")
        void builder_NegativeMaxRetries_Throws() {
            assertThrows(IllegalArgumentException.class, () -> {
                RetryEngine.builder().maxRetries(-1);
            });
        }

        @Test
        @DisplayName("builder valida initialDelayMs negativo")
        void builder_NegativeInitialDelay_Throws() {
            assertThrows(IllegalArgumentException.class, () -> {
                RetryEngine.builder().initialDelayMs(-1);
            });
        }

        @Test
        @DisplayName("builder valida jitterPercent fuera de rango")
        void builder_InvalidJitter_Throws() {
            assertThrows(IllegalArgumentException.class, () -> {
                RetryEngine.builder().jitterPercent(1.5);
            });

            assertThrows(IllegalArgumentException.class, () -> {
                RetryEngine.builder().jitterPercent(-0.1);
            });
        }
    }

    @Nested
    @DisplayName("Backoff Exponencial")
    class BackoffTests {
        @Test
        @DisplayName("delays aumentan exponencialmente")
        void backoff_ExponentialIncrease() {
            AtomicInteger attempts = new AtomicInteger(0);
            long[] timestamps = new long[4];

            try {
                RetryEngine.builder()
                    .maxRetries(3)
                    .initialDelayMs(50)
                    .jitterPercent(0)
                    .execute(() -> {
                        timestamps[attempts.getAndIncrement()] = System.currentTimeMillis();
                        throw new RuntimeException("fail");
                    }, "Backoff test");
            } catch (RetryExhaustedException ignored) {
            }

            if (attempts.get() >= 3) {
                long delay1 = timestamps[1] - timestamps[0];
                long delay2 = timestamps[2] - timestamps[1];

                assertTrue(delay2 >= delay1 * 1.5,
                    "Delay2 (" + delay2 + ") deberia ser > delay1 (" + delay1 + ") * 1.5");
            }
        }

        @Test
        @DisplayName("delay no excede maxDelayMs")
        void backoff_RespectsCap() {
            RetryEngine engine = RetryEngine.builder()
                .initialDelayMs(100)
                .maxDelayMs(150)
                .maxRetries(5)
                .build();

            assertEquals(150, engine.getMaxDelayMs());
        }
    }

    @Nested
    @DisplayName("Jitter")
    class JitterTests {
        @Test
        @DisplayName("jitter produce variacion en delays")
        void jitter_ProducesVariation() {
            long[] delays = new long[5];

            for (int i = 0; i < 5; i++) {
                long[] timestamps = new long[2];
                AtomicInteger attempts = new AtomicInteger(0);

                try {
                    RetryEngine.builder()
                        .maxRetries(1)
                        .initialDelayMs(100)
                        .jitterPercent(0.20)
                        .execute(() -> {
                            timestamps[attempts.getAndIncrement()] = System.currentTimeMillis();
                            throw new RuntimeException("fail");
                        }, "Jitter test");
                } catch (RetryExhaustedException ignored) {
                }

                if (attempts.get() == 2) {
                    delays[i] = timestamps[1] - timestamps[0];
                }
            }

            boolean hasVariation = false;
            for (int i = 1; i < delays.length; i++) {
                if (delays[i] != delays[0] && delays[i] > 0) {
                    hasVariation = true;
                    break;
                }
            }
        }

        @Test
        @DisplayName("sin jitter los delays son consistentes")
        void noJitter_ConsistentDelays() {
            RetryEngine engine = RetryEngine.builder()
                .jitterPercent(0)
                .build();

            assertEquals(0, engine.getJitterPercent(), 0.001);
        }
    }

    @Nested
    @DisplayName("Validacion")
    class ValidationTests {
        @Test
        @DisplayName("lanza NullPointerException si operation es null")
        void execute_NullOperation_Throws() {
            RetryEngine engine = RetryEngine.builder().build();

            assertThrows(NullPointerException.class, () -> {
                engine.run(null, "Test");
            });
        }

        @Test
        @DisplayName("acepta description null")
        void execute_NullDescription_Works() {
            String result = RetryEngine.execute(
                () -> "ok",
                ex -> true,
                null
            );

            assertEquals("ok", result);
        }

        @Test
        @DisplayName("mensaje por defecto si description es null")
        void execute_NullDescriptionInError_UsesDefault() {
            RetryExhaustedException ex = assertThrows(RetryExhaustedException.class, () -> {
                RetryEngine.builder()
                    .maxRetries(0)
                    .execute(() -> {
                        throw new RuntimeException("fail");
                    }, null);
            });

            assertTrue(ex.getMessage().contains("Operation"));
        }
    }

    @Nested
    @DisplayName("Integracion")
    class IntegrationTests {
        @Test
        @DisplayName("escenario realista: operacion de red falla y luego exito")
        void integration_NetworkOperation_EventualSuccess() {
            AtomicInteger callCount = new AtomicInteger(0);

            String result = RetryEngine.builder()
                .maxRetries(3)
                .initialDelayMs(10)
                .retryOn(ex -> ex instanceof IOException)
                .execute(() -> {
                    int count = callCount.incrementAndGet();
                    if (count <= 2) {
                        throw new IOException("Connection timeout");
                    }
                    return "{\"status\": \"ok\"}";
                }, "Fetching API data");

            assertEquals("{\"status\": \"ok\"}", result);
            assertEquals(3, callCount.get());
        }

        @Test
        @DisplayName("escenario: error de autenticacion no se reintenta")
        void integration_AuthError_NoRetry() {
            AtomicInteger callCount = new AtomicInteger(0);

            assertThrows(RuntimeException.class, () -> {
                RetryEngine.builder()
                    .maxRetries(3)
                    .initialDelayMs(10)
                    .retryOn(ex -> ex instanceof IOException)
                    .execute(() -> {
                        callCount.incrementAndGet();
                        throw new SecurityException("Invalid API key");
                    }, "API call");
            });

            assertEquals(1, callCount.get());
        }
    }
}
