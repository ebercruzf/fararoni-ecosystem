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
package dev.fararoni.core.gateway;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.bus.InMemorySovereignBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
class OutputDispatcherTest {
    private SovereignEventBus bus;
    private OutputDispatcher dispatcher;
    private ByteArrayOutputStream outputCapture;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        bus = new InMemorySovereignBus();
        dispatcher = new OutputDispatcher(bus);

        originalOut = System.out;
        outputCapture = new ByteArrayOutputStream();
        dispatcher.withCliOutput(new PrintStream(outputCapture));
    }

    @Test
    void constructor_createsSuccessfully() {
        assertNotNull(dispatcher);
        assertFalse(dispatcher.isActive());
    }

    @Test
    void activate_setsActiveTrue() {
        dispatcher.activate();

        assertTrue(dispatcher.isActive());
    }

    @Test
    void deactivate_setsActiveFalse() {
        dispatcher.activate();

        dispatcher.deactivate();

        assertFalse(dispatcher.isActive());
    }

    @Test
    void dispatch_withCliProtocol_sendsToStdout() {
        dispatcher.activate();
        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "testUser", "CLI", null, "Hello from CLI"
        ).withHeader("origin_protocol", "CLI");

        dispatcher.dispatch(envelope);

        String output = outputCapture.toString();
        assertTrue(output.contains("Hello from CLI"));
    }

    @Test
    void dispatch_withUnknownProtocol_sendsToDefaultOutput() {
        dispatcher.activate();
        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "testUser", "UNKNOWN", null, "Unknown message"
        ).withHeader("origin_protocol", "UNKNOWN_PROTOCOL");

        dispatcher.dispatch(envelope);

        String output = outputCapture.toString();
        assertTrue(output.contains("Unknown message"));
    }

    @Test
    void dispatch_withNoProtocol_treatsAsUnknown() {
        dispatcher.activate();
        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "testUser", "TEST", null, "No protocol message"
        );

        dispatcher.dispatch(envelope);

        String output = outputCapture.toString();
        assertTrue(output.contains("No protocol message"));
    }

    @Test
    void dispatch_whenNotActive_doesNothing() {
        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "testUser", "CLI", null, "Should not appear"
        ).withHeader("origin_protocol", "CLI");

        dispatcher.dispatch(envelope);

        String output = outputCapture.toString();
        assertTrue(output.isEmpty());
    }

    @Test
    void withTtsHandler_receivesVoiceMessages() {
        List<String> ttsMessages = new ArrayList<>();
        dispatcher.withTtsHandler(ttsMessages::add);
        dispatcher.activate();

        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "testUser", "VOICE", null, "Hello voice"
        ).withHeader("origin_protocol", "VOICE");

        dispatcher.dispatch(envelope);

        assertEquals(1, ttsMessages.size());
        assertEquals("Hello voice", ttsMessages.get(0));
    }

    @Test
    void withCustomHandler_overridesDefaultBehavior() {
        List<SovereignEnvelope<String>> customMessages = new ArrayList<>();
        dispatcher.withCustomHandler(
            OutputDispatcher.OriginProtocol.CLI,
            customMessages::add
        );
        dispatcher.activate();

        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "testUser", "CLI", null, "Custom handled"
        ).withHeader("origin_protocol", "CLI");

        dispatcher.dispatch(envelope);

        assertEquals(1, customMessages.size());
        String output = outputCapture.toString();
        assertFalse(output.contains("Custom handled"));
    }

    @Test
    void getDispatchStats_tracksMessagesByProtocol() {
        dispatcher.activate();

        dispatcher.dispatch(createEnvelopeWithProtocol("CLI"));
        dispatcher.dispatch(createEnvelopeWithProtocol("CLI"));
        dispatcher.dispatch(createEnvelopeWithProtocol("VOICE"));

        Map<OutputDispatcher.OriginProtocol, Long> stats = dispatcher.getDispatchStats();
        assertEquals(2L, stats.get(OutputDispatcher.OriginProtocol.CLI));
    }

    @Test
    void getTotalDispatched_countsAllMessages() {
        dispatcher.activate();

        dispatcher.dispatch(createEnvelopeWithProtocol("CLI"));
        dispatcher.dispatch(createEnvelopeWithProtocol("CLI"));
        dispatcher.dispatch(createEnvelopeWithProtocol("UNKNOWN"));

        assertTrue(dispatcher.getTotalDispatched() >= 3);
    }

    @Test
    void close_deactivatesDispatcher() {
        dispatcher.activate();

        dispatcher.close();

        assertFalse(dispatcher.isActive());
    }

    private SovereignEnvelope<String> createEnvelopeWithProtocol(String protocol) {
        return SovereignEnvelope.create(
            "testUser", protocol, null, "Test message for " + protocol
        ).withHeader("origin_protocol", protocol);
    }
}
