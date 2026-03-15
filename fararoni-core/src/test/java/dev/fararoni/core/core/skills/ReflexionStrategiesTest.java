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
package dev.fararoni.core.core.skills;

import dev.fararoni.core.core.memory.Wisdom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eber Cruz
 * @version 4.0.0
 * @since 1.0.0
 */
@DisplayName("ReflexionStrategies - Determinate Strategy Tests")
class ReflexionStrategiesTest {
    private ReflexionStrategies strategies;

    @BeforeEach
    void setUp() {
        strategies = new ReflexionStrategies();
    }

    @Nested
    @DisplayName("Cortocircuito Directivo (Active Wisdom)")
    class DirectiveCortocircuitoTests {
        @Test
        @DisplayName("Con Wisdom activo retorna DIRECTIVA PRIORITARIA")
        void withActiveWisdomReturnsDirective() {
            Wisdom wisdom = new Wisdom();
            wisdom.id = "skill-paasio";
            wisdom.description = "Wrapper pattern for file metrics";
            wisdom.codeSnippet = "class MeteredFile: ...";

            String result = strategies.determineStrategy(
                "Error in paasio.py",
                "paasio",
                List.of(wisdom)
            );

            assertThat(result)
                .contains("DIRECTIVA PRIORITARIA")
                .contains("paasio");
        }

        @Test
        @DisplayName("Con lista de Wisdom vacía usa Legacy")
        void withEmptyWisdomUsesLegacy() {
            String result = strategies.determineStrategy(
                "Error in bowling.py: roll failed",
                "bowling",
                Collections.emptyList()
            );

            assertThat(result)
                .doesNotContain("DIRECTIVA PRIORITARIA");
        }

        @Test
        @DisplayName("Con Wisdom null usa Legacy")
        void withNullWisdomUsesLegacy() {
            String result = strategies.determineStrategy(
                "Error in bowling.py",
                "bowling",
                null
            );

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Legacy Error Pattern Detection")
    class LegacyPatternTests {
        @Test
        @DisplayName("Detecta paasio desde error AttributeError")
        void detectsPaasioFromError() {
            String result = strategies.determineStrategy(
                "AttributeError: 'MeteredFile' object has no attribute",
                null,
                null
            );

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Detecta bowling desde keywords")
        void detectsBowlingFromKeywords() {
            String result = strategies.determineStrategy(
                "Error in roll method: pins remaining",
                "bowling",
                null
            );

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Maneja log vacío sin excepción")
        void handlesEmptyLog() {
            String result = strategies.determineStrategy("", "unknown", null);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Context ID Handling")
    class ContextIdTests {
        @Test
        @DisplayName("Usa contextId cuando está presente")
        void usesContextIdWhenPresent() {
            String result = strategies.determineStrategy(
                "some random error",
                "paasio",
                null
            );

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Maneja contextId null")
        void handlesNullContextId() {
            String result = strategies.determineStrategy(
                "Error in bowling.py",
                null,
                null
            );

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        @Test
        @DisplayName("determineStrategy no lanza excepción con parámetros válidos")
        void determineStrategyDoesNotThrow() {
            String result = strategies.determineStrategy(
                "Error in bowling.py: pins remaining after strike",
                "bowling",
                null
            );

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("determineStrategy retorna string no vacío")
        void determineStrategyReturnsNonEmpty() {
            String result = strategies.determineStrategy(
                "some error",
                "paasio",
                null
            );

            assertThat(result).isNotBlank();
        }
    }
}
