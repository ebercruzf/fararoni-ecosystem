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
package dev.fararoni.core.core.ninja;

import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.bus.agent.api.ToolResponse;
import dev.fararoni.core.core.ninja.ExecutionModeSelector.ExecutionMode;
import dev.fararoni.core.core.ninja.ExecutionModeSelector.ExecutionParams;
import dev.fararoni.core.core.ninja.ExecutionModeSelector.SelectionContext;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Ninja Optimization Tests")
class NinjaOptimizationTest {
    @Nested
    @DisplayName("SpeculativeCache Tests")
    class SpeculativeCacheTests {
        @Test
        @DisplayName("builder() crea cache con valores por defecto")
        void builder_createsWithDefaults() {
            SpeculativeCache cache = SpeculativeCache.builder().build();
            assertNotNull(cache);
            assertEquals(0, cache.size());
        }

        @Test
        @DisplayName("createDefault() crea cache funcional")
        void createDefault_createsFunctionalCache() {
            SpeculativeCache cache = SpeculativeCache.createDefault();
            assertNotNull(cache);
        }

        @Test
        @DisplayName("put() y get() funcionan correctamente")
        void putGet_worksCorrectly() {
            SpeculativeCache cache = SpeculativeCache.builder()
                .maxSize(100)
                .ttl(Duration.ofMinutes(5))
                .build();

            ToolRequest request = ToolRequest.of("FILE", "read", Map.of("path", "test.txt"));
            ToolResponse response = ToolResponse.success("content");

            cache.put(request, response, Duration.ofMillis(10));

            Optional<ToolResponse> cached = cache.get(request);
            assertTrue(cached.isPresent());
            assertEquals("content", cached.get().result());
        }

        @Test
        @DisplayName("get() retorna empty para request no existente")
        void get_returnsEmptyForMissing() {
            SpeculativeCache cache = SpeculativeCache.createDefault();

            ToolRequest request = ToolRequest.of("FILE", "read", Map.of("path", "missing.txt"));

            Optional<ToolResponse> cached = cache.get(request);
            assertFalse(cached.isPresent());
        }

        @Test
        @DisplayName("contains() verifica existencia")
        void contains_checksExistence() {
            SpeculativeCache cache = SpeculativeCache.createDefault();

            ToolRequest request = ToolRequest.of("FILE", "read", Map.of("path", "test.txt"));
            ToolResponse response = ToolResponse.success("content");

            assertFalse(cache.contains(request));
            cache.put(request, response, Duration.ZERO);
            assertTrue(cache.contains(request));
        }

        @Test
        @DisplayName("invalidate() remueve entrada")
        void invalidate_removesEntry() {
            SpeculativeCache cache = SpeculativeCache.createDefault();

            ToolRequest request = ToolRequest.of("FILE", "read", Map.of("path", "test.txt"));
            cache.put(request, ToolResponse.success("content"), Duration.ZERO);

            assertTrue(cache.contains(request));
            cache.invalidate(request);
            assertFalse(cache.contains(request));
        }

        @Test
        @DisplayName("invalidateSkill() remueve todas las entradas del skill")
        void invalidateSkill_removesAllSkillEntries() {
            SpeculativeCache cache = SpeculativeCache.createDefault();

            cache.put(ToolRequest.of("FILE", "read", Map.of("path", "a.txt")),
                ToolResponse.success("a"), Duration.ZERO);
            cache.put(ToolRequest.of("FILE", "write", Map.of("path", "b.txt")),
                ToolResponse.success("b"), Duration.ZERO);
            cache.put(ToolRequest.of("GIT", "status", Map.of()),
                ToolResponse.success("clean"), Duration.ZERO);

            assertEquals(3, cache.size());
            cache.invalidateSkill("FILE");
            assertEquals(1, cache.size());
        }

        @Test
        @DisplayName("clear() limpia todo el cache")
        void clear_clearsAll() {
            SpeculativeCache cache = SpeculativeCache.createDefault();

            cache.put(ToolRequest.of("FILE", "read", Map.of("path", "a.txt")),
                ToolResponse.success("a"), Duration.ZERO);
            cache.put(ToolRequest.of("GIT", "status", Map.of()),
                ToolResponse.success("clean"), Duration.ZERO);

            assertEquals(2, cache.size());
            cache.clear();
            assertEquals(0, cache.size());
        }

        @Test
        @DisplayName("putSpeculative() almacena prediccion")
        void putSpeculative_storesPrediction() {
            SpeculativeCache cache = SpeculativeCache.createDefault();

            ToolRequest request = ToolRequest.of("FILE", "read", Map.of("path", "test.txt"));
            cache.putSpeculative(request, ToolResponse.success("predicted"));

            Optional<ToolResponse> cached = cache.get(request);
            assertTrue(cached.isPresent());
        }

        @Test
        @DisplayName("putSpeculative() no sobreescribe resultados reales")
        void putSpeculative_doesNotOverwriteReal() {
            SpeculativeCache cache = SpeculativeCache.createDefault();

            ToolRequest request = ToolRequest.of("FILE", "read", Map.of("path", "test.txt"));
            cache.put(request, ToolResponse.success("real"), Duration.ZERO);
            cache.putSpeculative(request, ToolResponse.success("speculative"));

            Optional<ToolResponse> cached = cache.get(request);
            assertTrue(cached.isPresent());
            assertEquals("real", cached.get().result());
        }

        @Test
        @DisplayName("getMetrics() retorna metricas")
        void getMetrics_returnsMetrics() {
            SpeculativeCache cache = SpeculativeCache.createDefault();

            cache.put(ToolRequest.of("FILE", "read", Map.of("path", "a.txt")),
                ToolResponse.success("a"), Duration.ZERO);
            cache.get(ToolRequest.of("FILE", "read", Map.of("path", "a.txt")));
            cache.get(ToolRequest.of("FILE", "read", Map.of("path", "b.txt")));

            Map<String, Object> metrics = cache.getMetrics();
            assertTrue(metrics.containsKey("size"));
            assertTrue(metrics.containsKey("hits"));
            assertTrue(metrics.containsKey("misses"));
            assertTrue(metrics.containsKey("hitRate"));
        }

        @Test
        @DisplayName("eviction funciona cuando se excede maxSize")
        void eviction_worksWhenOverMaxSize() {
            SpeculativeCache cache = SpeculativeCache.builder()
                .maxSize(3)
                .ttl(Duration.ofMinutes(5))
                .build();

            for (int i = 0; i < 5; i++) {
                cache.put(
                    ToolRequest.of("FILE", "read", Map.of("path", "file" + i + ".txt")),
                    ToolResponse.success("content" + i),
                    Duration.ZERO
                );
            }

            assertTrue(cache.size() <= 3);
        }
    }

    @Nested
    @DisplayName("ExecutionModeSelector Tests")
    class ExecutionModeSelectorTests {
        @Test
        @DisplayName("create() crea selector funcional")
        void create_createsFunctionalSelector() {
            ExecutionModeSelector selector = ExecutionModeSelector.create();
            assertNotNull(selector);
        }

        @Test
        @DisplayName("selectMode() retorna FAST para queries simples")
        void selectMode_returnsFastForSimple() {
            ExecutionModeSelector selector = ExecutionModeSelector.create();

            ExecutionMode mode = selector.selectMode("What is Java?");
            assertEquals(ExecutionMode.FAST, mode);
        }

        @Test
        @DisplayName("selectMode() retorna THOROUGH para tareas complejas")
        void selectMode_returnsThoroughForComplex() {
            ExecutionModeSelector selector = ExecutionModeSelector.create();

            ExecutionMode mode = selector.selectMode("Refactor the authentication system");
            assertEquals(ExecutionMode.THOROUGH, mode);
        }

        @Test
        @DisplayName("selectMode() retorna SPECULATIVE para batch")
        void selectMode_returnsSpeculativeForBatch() {
            ExecutionModeSelector selector = ExecutionModeSelector.create();

            ExecutionMode mode = selector.selectMode("Update all files in the project");
            assertEquals(ExecutionMode.SPECULATIVE, mode);
        }

        @Test
        @DisplayName("selectMode() con contexto high priority retorna THOROUGH")
        void selectMode_withHighPriority_returnsThorough() {
            ExecutionModeSelector selector = ExecutionModeSelector.create();

            SelectionContext context = SelectionContext.builder()
                .highPriority(true)
                .build();

            ExecutionMode mode = selector.selectMode("Simple task", context);
            assertEquals(ExecutionMode.THOROUGH, mode);
        }

        @Test
        @DisplayName("selectMode() con cache hit retorna FAST")
        void selectMode_withCacheHit_returnsFast() {
            ExecutionModeSelector selector = ExecutionModeSelector.create();

            SelectionContext context = SelectionContext.builder()
                .cacheHit(true)
                .build();

            ExecutionMode mode = selector.selectMode("Some task", context);
            assertEquals(ExecutionMode.FAST, mode);
        }

        @Test
        @DisplayName("selectMode() input null retorna FAST")
        void selectMode_nullInput_returnsFast() {
            ExecutionModeSelector selector = ExecutionModeSelector.create();

            ExecutionMode mode = selector.selectMode(null);
            assertEquals(ExecutionMode.FAST, mode);
        }

        @Test
        @DisplayName("selectMode() input blank retorna FAST")
        void selectMode_blankInput_returnsFast() {
            ExecutionModeSelector selector = ExecutionModeSelector.create();

            ExecutionMode mode = selector.selectMode("   ");
            assertEquals(ExecutionMode.FAST, mode);
        }

        @Test
        @DisplayName("getParamsForMode() retorna parametros correctos")
        void getParamsForMode_returnsCorrectParams() {
            ExecutionModeSelector selector = ExecutionModeSelector.create();

            ExecutionParams fastParams = selector.getParamsForMode(ExecutionMode.FAST);
            assertEquals(1, fastParams.maxReActTurns());
            assertFalse(fastParams.enableReflexion());

            ExecutionParams thoroughParams = selector.getParamsForMode(ExecutionMode.THOROUGH);
            assertEquals(5, thoroughParams.maxReActTurns());
            assertTrue(thoroughParams.enableReflexion());
            assertTrue(thoroughParams.enableSpeculation());
        }

        @Test
        @DisplayName("builder con override")
        void builder_withOverride() {
            ExecutionModeSelector selector = ExecutionModeSelector.builder()
                .override("urgent", ExecutionMode.THOROUGH)
                .build();

            ExecutionMode mode = selector.selectMode("This is urgent task");
            assertEquals(ExecutionMode.THOROUGH, mode);
        }

        @Test
        @DisplayName("SelectionContext.empty() crea contexto vacio")
        void selectionContext_empty() {
            SelectionContext context = SelectionContext.empty();
            assertFalse(context.isHighPriority());
            assertFalse(context.isCacheHit());
            assertFalse(context.hasMultipleTools());
        }
    }

    @Nested
    @DisplayName("NinjaDispatcher Builder Tests")
    class NinjaDispatcherBuilderTests {
        @Test
        @DisplayName("builder() requiere registry")
        void builder_requiresRegistry() {
            assertThrows(NullPointerException.class, () ->
                NinjaDispatcher.builder().build()
            );
        }

        @Test
        @DisplayName("builder con speculativeCache")
        void builder_withSpeculativeCache() {
            SpeculativeCache cache = SpeculativeCache.createDefault();

            assertNotNull(cache);
        }
    }

    @Nested
    @DisplayName("ExecutionParams Tests")
    class ExecutionParamsTests {
        @Test
        @DisplayName("ExecutionParams record es inmutable")
        void executionParams_isImmutable() {
            ExecutionParams params = new ExecutionParams(3, 30000, true, false);

            assertEquals(3, params.maxReActTurns());
            assertEquals(30000, params.timeoutMs());
            assertTrue(params.enableReflexion());
            assertFalse(params.enableSpeculation());
        }
    }
}
