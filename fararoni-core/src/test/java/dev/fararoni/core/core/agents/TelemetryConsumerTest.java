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
package dev.fararoni.core.core.agents;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.protocol.AgentMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
class TelemetryConsumerTest {
    private Consumer<SovereignEnvelope<AgentMessage>> createTelemetryConsumer(PrintStream out) {
        return envelope -> {
            AgentMessage msg = envelope.payload();

            if (AgentMessage.TYPE_STATUS_UPDATE.equals(msg.type())) {
                String role = msg.senderRole();
                String status = msg.getContent("status", "UNKNOWN");
                String action = msg.naturalLanguageHint();

                String icon = switch(status) {
                    case "EXECUTING" -> "⧗";
                    case "COMPLETED" -> "✔";
                    case "FAILED" -> "✘";
                    case "PLANNING" -> "🧠";
                    case "PAUSED" -> "•";
                    default -> "?";
                };

                out.printf("\u001B[36m[%s]\u001B[0m %s %s%n", role, icon, action);
            }
        };
    }

    @ParameterizedTest(name = "Estado {0} debe mostrar icono {1}")
    @CsvSource({
        "EXECUTING, ⧗",
        "COMPLETED, ✔",
        "FAILED, ✘",
        "PLANNING, 🧠",
        "PAUSED, •",
        "UNKNOWN, ?"
    })
    @DisplayName("Cada estado debe tener su icono correcto")
    void status_shouldHaveCorrectIcon(String status, String expectedIcon) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);

        Consumer<SovereignEnvelope<AgentMessage>> consumer = createTelemetryConsumer(printStream);

        AgentMessage message = AgentMessage.statusUpdate("TEST_AGENT", status, "Test action");
        SovereignEnvelope<AgentMessage> envelope = SovereignEnvelope.create("system", "trace-1", message);

        consumer.accept(envelope);

        String output = outputStream.toString();
        assertTrue(output.contains(expectedIcon),
            "Output debe contener el icono '" + expectedIcon + "' para estado " + status);
    }

    @Test
    @DisplayName("Mensaje debe incluir rol entre corchetes")
    void output_shouldIncludeRoleInBrackets() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);

        Consumer<SovereignEnvelope<AgentMessage>> consumer = createTelemetryConsumer(printStream);

        AgentMessage message = AgentMessage.statusUpdate("NECROMANCER", "EXECUTING", "Procesando");
        SovereignEnvelope<AgentMessage> envelope = SovereignEnvelope.create("system", "trace-1", message);

        consumer.accept(envelope);

        String output = outputStream.toString();
        assertTrue(output.contains("[NECROMANCER]"), "Output debe incluir [NECROMANCER]");
    }

    @Test
    @DisplayName("Mensaje debe incluir acción descriptiva")
    void output_shouldIncludeAction() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);

        Consumer<SovereignEnvelope<AgentMessage>> consumer = createTelemetryConsumer(printStream);

        AgentMessage message = AgentMessage.statusUpdate("TEST", "EXECUTING", "Analizando cadáver");
        SovereignEnvelope<AgentMessage> envelope = SovereignEnvelope.create("system", "trace-1", message);

        consumer.accept(envelope);

        String output = outputStream.toString();
        assertTrue(output.contains("Analizando cadáver"), "Output debe incluir la acción");
    }

    @Test
    @DisplayName("Mensaje debe contener códigos de color ANSI")
    void output_shouldContainAnsiColorCodes() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);

        Consumer<SovereignEnvelope<AgentMessage>> consumer = createTelemetryConsumer(printStream);

        AgentMessage message = AgentMessage.statusUpdate("TEST", "EXECUTING", "Test");
        SovereignEnvelope<AgentMessage> envelope = SovereignEnvelope.create("system", "trace-1", message);

        consumer.accept(envelope);

        String output = outputStream.toString();
        assertTrue(output.contains("\u001B[36m"), "Output debe contener código ANSI cyan");
        assertTrue(output.contains("\u001B[0m"), "Output debe contener código ANSI reset");
    }

    @Test
    @DisplayName("Solo debe procesar mensajes TYPE_STATUS_UPDATE")
    void consumer_shouldOnlyProcessStatusUpdates() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);

        Consumer<SovereignEnvelope<AgentMessage>> consumer = createTelemetryConsumer(printStream);

        AgentMessage taskMessage = AgentMessage.taskAssignment("PM", java.util.Map.of(), "Do something");
        SovereignEnvelope<AgentMessage> envelope = SovereignEnvelope.create("system", "trace-1", taskMessage);

        consumer.accept(envelope);

        String output = outputStream.toString();
        assertTrue(output.isEmpty(), "No debe imprimir nada para mensajes que no son STATUS_UPDATE");
    }

    @Test
    @DisplayName("Formato completo: [ROL] icono acción")
    void output_shouldHaveCorrectFullFormat() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);

        Consumer<SovereignEnvelope<AgentMessage>> consumer = createTelemetryConsumer(printStream);

        AgentMessage message = AgentMessage.statusUpdate("NECROMANCER", "PLANNING", "Analizando error");
        SovereignEnvelope<AgentMessage> envelope = SovereignEnvelope.create("system", "trace-1", message);

        consumer.accept(envelope);

        String output = outputStream.toString();

        int roleIndex = output.indexOf("[NECROMANCER]");
        int iconIndex = output.indexOf("🧠");
        int actionIndex = output.indexOf("Analizando error");

        assertTrue(roleIndex >= 0 && iconIndex > roleIndex && actionIndex > iconIndex,
            "Formato debe ser: [ROL] icono acción. Output: " + output);
    }

    @Test
    @DisplayName("Cada mensaje debe terminar con newline")
    void output_shouldEndWithNewline() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);

        Consumer<SovereignEnvelope<AgentMessage>> consumer = createTelemetryConsumer(printStream);

        AgentMessage message = AgentMessage.statusUpdate("TEST", "EXECUTING", "Test");
        SovereignEnvelope<AgentMessage> envelope = SovereignEnvelope.create("system", "trace-1", message);

        consumer.accept(envelope);

        String output = outputStream.toString();
        assertTrue(output.endsWith(System.lineSeparator()), "Output debe terminar con newline");
    }
}
