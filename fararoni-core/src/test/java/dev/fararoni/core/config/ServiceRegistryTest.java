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
package dev.fararoni.core.config;

import dev.fararoni.core.context.ContextManager;
import dev.fararoni.core.context.BasicContextManager;
import dev.fararoni.core.model.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@DisplayName("ServiceRegistry - Polymorphic Override Tests")
class ServiceRegistryTest {
    @BeforeEach
    void setUp() {
        ServiceRegistry.reset();
    }

    @Test
    @DisplayName("Debe descubrir al menos una implementación de ContextManager")
    void shouldDiscoverContextManager() {
        ContextManager contextManager = ServiceRegistry.getContextManager();

        assertNotNull(contextManager, "ServiceRegistry debe retornar un ContextManager");
    }

    @Test
    @DisplayName("Debe retornar BasicContextManager cuando solo Core está disponible")
    void shouldReturnBasicContextManagerInCoreOnly() {
        ContextManager contextManager = ServiceRegistry.getContextManager();

        assertInstanceOf(BasicContextManager.class, contextManager,
            "En Core solo, debe retornar BasicContextManager");
        assertEquals("CORE (Manual Loading + Token Budgeting)", contextManager.getStrategyName(),
            "El nombre de estrategia debe ser el correcto");
        assertEquals(0, contextManager.getPriority(),
            "La prioridad de BasicContextManager debe ser 0");
    }

    @Test
    @DisplayName("Debe retornar el mismo ContextManager en llamadas subsecuentes (singleton)")
    void shouldReturnSameInstanceOnSubsequentCalls() {
        ContextManager first = ServiceRegistry.getContextManager();
        ContextManager second = ServiceRegistry.getContextManager();

        assertSame(first, second, "ServiceRegistry debe retornar la misma instancia");
    }

    @Test
    @DisplayName("Debe permitir inyección manual de ContextManager para testing")
    void shouldAllowManualInjectionForTesting() {
        ContextManager mockManager = new ContextManager() {
            @Override
            public String assemblePrompt(String systemPrompt, java.util.List<String> userFiles,
                                        java.util.List<Message> history,
                                        String currentQuery) {
                return "MOCK";
            }

            @Override
            public String getStrategyName() {
                return "MOCK";
            }

            @Override
            public int getPriority() {
                return 999;
            }
        };

        ServiceRegistry.setContextManager(mockManager);
        ContextManager retrieved = ServiceRegistry.getContextManager();

        assertSame(mockManager, retrieved, "Debe retornar el ContextManager inyectado manualmente");
        assertEquals("MOCK", retrieved.getStrategyName());
    }

    @Test
    @DisplayName("Reset debe permitir redescubrimiento de servicios")
    void shouldAllowRediscoveryAfterReset() {
        ContextManager first = ServiceRegistry.getContextManager();

        ServiceRegistry.reset();
        ContextManager second = ServiceRegistry.getContextManager();

        assertNotSame(first, second, "Después de reset debe crear nueva instancia");
        assertInstanceOf(BasicContextManager.class, second,
            "Después de reset debe redescubrir BasicContextManager");
    }

    @Test
    @DisplayName("ContextManager retornado debe tener métodos funcionales")
    void contextManagerShouldHaveFunctionalMethods() {
        ContextManager mockManager = new ContextManager() {
            @Override
            public String assemblePrompt(String systemPrompt, java.util.List<String> userFiles,
                                        java.util.List<Message> history,
                                        String currentQuery) {
                return systemPrompt + "\nUser: " + currentQuery;
            }

            @Override
            public String getStrategyName() {
                return "TEST_MOCK";
            }

            @Override
            public int getPriority() {
                return 999;
            }
        };

        ServiceRegistry.setContextManager(mockManager);
        ContextManager retrieved = ServiceRegistry.getContextManager();
        String prompt = retrieved.assemblePrompt(
            "You are a helpful assistant",
            java.util.List.of(),
            java.util.List.of(),
            "Hello"
        );

        assertNotNull(prompt, "assemblePrompt no debe retornar null");
        assertTrue(prompt.contains("You are a helpful assistant"),
            "El prompt debe contener el system prompt");
    }
}
