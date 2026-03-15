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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SystemPromptBuilder {
    private static final Locale LOCALE_ES = new Locale("es", "ES");

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", LOCALE_ES);

    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("HH:mm");

    public String buildSystemPrompt(String channel) {
        String fechaActual = getCurrentDate();
        String horaActual = getCurrentTime();
        String canalHint = getChannelHint(channel);

        return """
            Eres un asistente inteligente %s.

            CONTEXTO TEMPORAL:
            - Fecha actual: %s
            - Hora actual: %s

            REGLAS DE COMPORTAMIENTO:
            1. Responde de forma breve y natural (max 2-3 oraciones para preguntas simples).
            2. Manten un tono amigable pero profesional.
            3. Si no tienes informacion suficiente, pregunta para clarificar.
            4. Nunca inventes fechas, horas o datos que no conozcas con certeza.
            5. Si te preguntan la fecha u hora, usa los valores del CONTEXTO TEMPORAL.
            """.formatted(canalHint, fechaActual, horaActual);
    }

    public String buildMinimalPrompt(String channel) {
        String fechaActual = getCurrentDate();
        String canalHint = getChannelHint(channel);

        return """
            Asistente %s. Fecha: %s.
            Responde breve y natural.
            """.formatted(canalHint, fechaActual);
    }

    public String buildDebugPrompt() {
        return """
            Eres un asistente de desarrollo en modo terminal.

            CONTEXTO:
            - Fecha: %s
            - Hora: %s
            - Modo: DESARROLLO

            Puedes incluir detalles tecnicos en tus respuestas.
            """.formatted(getCurrentDate(), getCurrentTime());
    }

    public String getCurrentDate() {
        return LocalDate.now().format(DATE_FORMAT);
    }

    public String getCurrentTime() {
        return LocalTime.now().format(TIME_FORMAT);
    }

    private String getChannelHint(String channel) {
        if (channel == null || channel.isBlank()) {
            return "";
        }

        return switch (channel.toUpperCase()) {
            case "WHATSAPP", "WHA" -> "comunicandote via WhatsApp";
            case "TELEGRAM", "TEL" -> "comunicandote via Telegram";
            case "TERMINAL", "CLI", "TERMINAL_DEV" -> "en modo terminal de desarrollo";
            case "SLACK" -> "en un canal de Slack";
            case "DISCORD" -> "en un servidor de Discord";
            case "WEB", "HTTP" -> "via interfaz web";
            default -> "via mensajeria";
        };
    }

    private static volatile SystemPromptBuilder instance;

    public static SystemPromptBuilder getInstance() {
        if (instance == null) {
            synchronized (SystemPromptBuilder.class) {
                if (instance == null) {
                    instance = new SystemPromptBuilder();
                }
            }
        }
        return instance;
    }
}
