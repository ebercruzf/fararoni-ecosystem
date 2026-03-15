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
package dev.fararoni.core.core.gateway.routing;

import org.junit.jupiter.api.*;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SystemPromptBuilder Tests")
class SystemPromptBuilderTest {
    private SystemPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SystemPromptBuilder();
    }

    @Nested
    @DisplayName("Fecha y Hora Dinamica")
    class DateTimeTests {
        @Test
        @DisplayName("getCurrentDate debe retornar fecha actual en espanol")
        void shouldReturnCurrentDateInSpanish() {
            String date = builder.getCurrentDate();

            assertNotNull(date);
            assertFalse(date.isEmpty());

            String currentYear = String.valueOf(LocalDate.now().getYear());
            assertTrue(date.contains(currentYear),
                "Fecha debe contener el ano actual: " + date);

            assertTrue(date.contains(" de "),
                "Fecha debe estar en espanol: " + date);
        }

        @Test
        @DisplayName("getCurrentTime debe retornar hora en formato HH:mm")
        void shouldReturnCurrentTime() {
            String time = builder.getCurrentTime();

            assertNotNull(time);
            assertTrue(time.matches("\\d{2}:\\d{2}"),
                "Hora debe tener formato HH:mm: " + time);
        }

        @Test
        @DisplayName("buildSystemPrompt debe incluir fecha actual")
        void shouldIncludeCurrentDate() {
            String prompt = builder.buildSystemPrompt("WHATSAPP");

            assertTrue(prompt.contains("Fecha actual:"),
                "Prompt debe contener etiqueta de fecha");

            String currentYear = String.valueOf(LocalDate.now().getYear());
            assertTrue(prompt.contains(currentYear),
                "Prompt debe contener el ano actual");
        }

        @Test
        @DisplayName("buildSystemPrompt debe incluir hora actual")
        void shouldIncludeCurrentTime() {
            String prompt = builder.buildSystemPrompt("WHATSAPP");

            assertTrue(prompt.contains("Hora actual:"),
                "Prompt debe contener etiqueta de hora");
        }
    }

    @Nested
    @DisplayName("Personalizacion por Canal")
    class ChannelCustomizationTests {
        @Test
        @DisplayName("WHATSAPP debe incluir hint de WhatsApp")
        void shouldCustomizeForWhatsApp() {
            String prompt = builder.buildSystemPrompt("WHATSAPP");
            assertTrue(prompt.toLowerCase().contains("whatsapp"),
                "Prompt debe mencionar WhatsApp");
        }

        @Test
        @DisplayName("WHA (abreviado) debe funcionar igual que WHATSAPP")
        void shouldHandleAbbreviatedWhatsApp() {
            String prompt = builder.buildSystemPrompt("WHA");
            assertTrue(prompt.toLowerCase().contains("whatsapp"),
                "Prompt debe mencionar WhatsApp para WHA");
        }

        @Test
        @DisplayName("TELEGRAM debe incluir hint de Telegram")
        void shouldCustomizeForTelegram() {
            String prompt = builder.buildSystemPrompt("TELEGRAM");
            assertTrue(prompt.toLowerCase().contains("telegram"),
                "Prompt debe mencionar Telegram");
        }

        @Test
        @DisplayName("TEL (abreviado) debe funcionar igual que TELEGRAM")
        void shouldHandleAbbreviatedTelegram() {
            String prompt = builder.buildSystemPrompt("TEL");
            assertTrue(prompt.toLowerCase().contains("telegram"),
                "Prompt debe mencionar Telegram para TEL");
        }

        @Test
        @DisplayName("TERMINAL debe incluir hint de terminal")
        void shouldCustomizeForTerminal() {
            String prompt = builder.buildSystemPrompt("TERMINAL");
            assertTrue(prompt.toLowerCase().contains("terminal"),
                "Prompt debe mencionar terminal");
        }

        @Test
        @DisplayName("TERMINAL_DEV debe funcionar igual que TERMINAL")
        void shouldHandleTerminalDev() {
            String prompt = builder.buildSystemPrompt("TERMINAL_DEV");
            assertTrue(prompt.toLowerCase().contains("terminal"),
                "Prompt debe mencionar terminal para TERMINAL_DEV");
        }

        @Test
        @DisplayName("SLACK debe incluir hint de Slack")
        void shouldCustomizeForSlack() {
            String prompt = builder.buildSystemPrompt("SLACK");
            assertTrue(prompt.toLowerCase().contains("slack"),
                "Prompt debe mencionar Slack");
        }

        @Test
        @DisplayName("DISCORD debe incluir hint de Discord")
        void shouldCustomizeForDiscord() {
            String prompt = builder.buildSystemPrompt("DISCORD");
            assertTrue(prompt.toLowerCase().contains("discord"),
                "Prompt debe mencionar Discord");
        }

        @Test
        @DisplayName("Canal desconocido debe usar hint generico")
        void shouldUseGenericHintForUnknownChannel() {
            String prompt = builder.buildSystemPrompt("UNKNOWN_CHANNEL");
            assertTrue(prompt.toLowerCase().contains("mensajeria"),
                "Prompt debe usar hint generico para canal desconocido");
        }

        @Test
        @DisplayName("Canal null debe manejarse gracefully")
        void shouldHandleNullChannel() {
            assertDoesNotThrow(() -> builder.buildSystemPrompt(null));
            String prompt = builder.buildSystemPrompt(null);
            assertNotNull(prompt);
            assertFalse(prompt.isEmpty());
        }

        @Test
        @DisplayName("Canal vacio debe manejarse gracefully")
        void shouldHandleEmptyChannel() {
            assertDoesNotThrow(() -> builder.buildSystemPrompt(""));
            String prompt = builder.buildSystemPrompt("");
            assertNotNull(prompt);
            assertFalse(prompt.isEmpty());
        }
    }

    @Nested
    @DisplayName("Reglas de Comportamiento")
    class BehaviorRulesTests {
        @Test
        @DisplayName("Prompt debe incluir regla de brevedad")
        void shouldIncludeBrevityRule() {
            String prompt = builder.buildSystemPrompt("WHATSAPP");
            assertTrue(prompt.toLowerCase().contains("breve") ||
                       prompt.toLowerCase().contains("2-3 oraciones"),
                "Prompt debe incluir regla de brevedad");
        }

        @Test
        @DisplayName("Prompt debe incluir regla anti-alucinacion")
        void shouldIncludeAntiHallucinationRule() {
            String prompt = builder.buildSystemPrompt("WHATSAPP");
            assertTrue(prompt.toLowerCase().contains("nunca inventes") ||
                       prompt.toLowerCase().contains("no inventes"),
                "Prompt debe incluir regla anti-alucinacion");
        }

        @Test
        @DisplayName("Prompt debe incluir instruccion de usar contexto temporal")
        void shouldIncludeTemporalContextInstruction() {
            String prompt = builder.buildSystemPrompt("WHATSAPP");
            assertTrue(prompt.contains("CONTEXTO TEMPORAL"),
                "Prompt debe incluir seccion de contexto temporal");
        }
    }

    @Nested
    @DisplayName("Prompts Alternativos")
    class AlternativePromptsTests {
        @Test
        @DisplayName("buildMinimalPrompt debe ser mas corto")
        void minimalPromptShouldBeShorter() {
            String fullPrompt = builder.buildSystemPrompt("WHATSAPP");
            String minimalPrompt = builder.buildMinimalPrompt("WHATSAPP");

            assertTrue(minimalPrompt.length() < fullPrompt.length(),
                "Prompt minimal debe ser mas corto que el completo");
        }

        @Test
        @DisplayName("buildMinimalPrompt debe incluir fecha")
        void minimalPromptShouldIncludeDate() {
            String prompt = builder.buildMinimalPrompt("WHATSAPP");
            String currentYear = String.valueOf(LocalDate.now().getYear());
            assertTrue(prompt.contains(currentYear),
                "Prompt minimal debe incluir fecha");
        }

        @Test
        @DisplayName("buildDebugPrompt debe indicar modo desarrollo")
        void debugPromptShouldIndicateDevelopmentMode() {
            String prompt = builder.buildDebugPrompt();
            assertTrue(prompt.contains("DESARROLLO") || prompt.contains("desarrollo"),
                "Debug prompt debe indicar modo desarrollo");
        }

        @Test
        @DisplayName("buildDebugPrompt debe incluir fecha y hora")
        void debugPromptShouldIncludeDateAndTime() {
            String prompt = builder.buildDebugPrompt();
            String currentYear = String.valueOf(LocalDate.now().getYear());
            assertTrue(prompt.contains(currentYear),
                "Debug prompt debe incluir fecha");
        }
    }

    @Nested
    @DisplayName("Singleton")
    class SingletonTests {
        @Test
        @DisplayName("getInstance debe retornar misma instancia")
        void shouldReturnSameInstance() {
            SystemPromptBuilder instance1 = SystemPromptBuilder.getInstance();
            SystemPromptBuilder instance2 = SystemPromptBuilder.getInstance();

            assertSame(instance1, instance2,
                "getInstance debe retornar la misma instancia");
        }

        @Test
        @DisplayName("Singleton debe ser funcional")
        void singletonShouldBeFunctional() {
            SystemPromptBuilder instance = SystemPromptBuilder.getInstance();
            String prompt = instance.buildSystemPrompt("WHATSAPP");

            assertNotNull(prompt);
            assertFalse(prompt.isEmpty());
        }
    }

    @Nested
    @DisplayName("Formato de Fecha")
    class DateFormatTests {
        @Test
        @DisplayName("Fecha debe incluir dia de la semana")
        void shouldIncludeDayOfWeek() {
            String date = builder.getCurrentDate();

            boolean hasDayOfWeek = date.contains("lunes") ||
                                   date.contains("martes") ||
                                   date.contains("miercoles") || date.contains("miércoles") ||
                                   date.contains("jueves") ||
                                   date.contains("viernes") ||
                                   date.contains("sabado") || date.contains("sábado") ||
                                   date.contains("domingo");

            assertTrue(hasDayOfWeek,
                "Fecha debe incluir dia de la semana en espanol: " + date);
        }

        @Test
        @DisplayName("Fecha debe incluir mes en espanol")
        void shouldIncludeMonthInSpanish() {
            String date = builder.getCurrentDate();

            boolean hasMonth = date.contains("enero") ||
                               date.contains("febrero") ||
                               date.contains("marzo") ||
                               date.contains("abril") ||
                               date.contains("mayo") ||
                               date.contains("junio") ||
                               date.contains("julio") ||
                               date.contains("agosto") ||
                               date.contains("septiembre") ||
                               date.contains("octubre") ||
                               date.contains("noviembre") ||
                               date.contains("diciembre");

            assertTrue(hasMonth,
                "Fecha debe incluir mes en espanol: " + date);
        }
    }
}
