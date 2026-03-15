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
package dev.fararoni.core.core.agent.dynamic;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.security.HmacMessageSigner;
import dev.fararoni.core.core.agent.model.AgentInstanceConfig;
import dev.fararoni.core.core.agent.model.AgentTemplate;
import dev.fararoni.core.core.bus.InMemorySovereignBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
class DoppelgangerAgentTest {
    private SovereignEventBus bus;
    private DoppelgangerAgent agent;
    private AgentTemplate template;
    private AgentInstanceConfig config;

    @BeforeEach
    void setUp() {
        bus = new InMemorySovereignBus();

        template = new AgentTemplate(
            "test-template-v1",
            "TESTER",
            "Eres un agente de pruebas. Responde en JSON.",
            null,
            List.of("TEST_CAPABILITY"),
            Map.of("version", "1.0")
        );

        config = new AgentInstanceConfig(
            "test-agent-1",
            "test-template-v1",
            new AgentInstanceConfig.WiringConfig(
                List.of("input.test"),
                "output.test",
                "dlq.test"
            ),
            AgentInstanceConfig.RoutingConfig.defaults(),
            Map.of("env", "test")
        );
    }

    @AfterEach
    void tearDown() {
        if (agent != null) {
            agent.close();
        }
    }

    @Test
    void activate_subscribesToInputTopics() {
        agent = new DoppelgangerAgent(config, template, bus, LlmInferenceProvider.echo());

        agent.activate();

        assertTrue(agent.isActive());
    }

    @Test
    void activate_twice_noError() {
        agent = new DoppelgangerAgent(config, template, bus, LlmInferenceProvider.echo());
        agent.activate();

        agent.activate();
        assertTrue(agent.isActive());
    }

    @Test
    void deactivate_cancelsSubscriptions() {
        agent = new DoppelgangerAgent(config, template, bus, LlmInferenceProvider.echo());
        agent.activate();
        assertTrue(agent.isActive());

        agent.deactivate();

        assertFalse(agent.isActive());
    }

    @Test
    void close_deactivatesAgent() {
        agent = new DoppelgangerAgent(config, template, bus, LlmInferenceProvider.echo());
        agent.activate();

        agent.close();

        assertFalse(agent.isActive());
    }

    @Test
    void processSecureMessage_validMessage_publishesResponse() throws InterruptedException {
        String responseJson = "{\"status\": \"ok\"}";
        agent = new DoppelgangerAgent(config, template, bus,
            LlmInferenceProvider.mock(responseJson));
        agent.activate();

        List<SovereignEnvelope<String>> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        bus.subscribe("output.test", String.class, envelope -> {
            received.add(envelope);
            latch.countDown();
        });

        SovereignEnvelope<String> input = SovereignEnvelope.create(
            "sender-1", "test message"
        );
        bus.publish("input.test", input);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Deberia recibir respuesta");
        assertEquals(1, received.size());
        assertEquals(responseJson, received.get(0).payload());
        assertEquals(1, agent.getMessagesProcessed());
    }

    @Test
    void processSecureMessage_withSignature_verifiesAndProcesses() throws InterruptedException {
        String responseJson = "{\"verified\": true}";
        agent = new DoppelgangerAgent(config, template, bus,
            LlmInferenceProvider.mock(responseJson));
        agent.activate();

        CountDownLatch latch = new CountDownLatch(1);
        bus.subscribe("output.test", String.class, envelope -> latch.countDown());

        String payload = "signed message";
        SovereignEnvelope<String> base = SovereignEnvelope.create("sender-1", payload);
        String signature = HmacMessageSigner.sign(payload, base.timestamp().toEpochMilli());
        SovereignEnvelope<String> signed = base.withSignature(signature);

        bus.publish("input.test", signed);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, agent.getMessagesProcessed());
    }

    @Test
    void processSecureMessage_invalidSignature_goesToDlq() throws InterruptedException {
        agent = new DoppelgangerAgent(config, template, bus, LlmInferenceProvider.echo());
        agent.activate();

        List<SovereignEnvelope<String>> dlqMessages = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        bus.subscribe("dlq.test", String.class, envelope -> {
            dlqMessages.add(envelope);
            latch.countDown();
        });

        SovereignEnvelope<String> badSig = SovereignEnvelope.create("sender-1", "payload")
            .withSignature("invalid-signature");
        bus.publish("input.test", badSig);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Deberia ir a DLQ");
        assertEquals(1, dlqMessages.size());
        assertTrue(dlqMessages.get(0).headers().get("dlq.reason").contains("HMAC"));
    }

    @Test
    void processSecureMessage_maxHopsExceeded_goesToDlq() throws InterruptedException {
        agent = new DoppelgangerAgent(config, template, bus, LlmInferenceProvider.echo());
        agent.activate();

        List<SovereignEnvelope<String>> dlqMessages = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        bus.subscribe("dlq.test", String.class, envelope -> {
            dlqMessages.add(envelope);
            latch.countDown();
        });

        SovereignEnvelope<String> base = SovereignEnvelope.create("sender-1", "payload");
        SovereignEnvelope<String> highHops = base;

        for (int i = 0; i < SovereignEnvelope.MAX_HOP_COUNT - 1; i++) {
            highHops = highHops.incrementHop();
        }

        assertFalse(highHops.isMaxHopsExceeded(), "Aun no deberia exceder limite");
        assertEquals(SovereignEnvelope.MAX_HOP_COUNT - 1, highHops.hopCount());

        bus.publish("input.test", highHops);

        Thread.sleep(500);

        assertFalse(latch.await(1, TimeUnit.SECONDS),
            "Mensaje valido no deberia ir a DLQ");
    }

    @Test
    void processSecureMessage_withSchema_validatesOutput() throws InterruptedException {
        AgentTemplate schemaTemplate = new AgentTemplate(
            "schema-template",
            "VALIDATOR",
            "Responde JSON",
            "{\"type\": \"object\"}",
            List.of(),
            Map.of()
        );

        AgentInstanceConfig schemaConfig = new AgentInstanceConfig(
            "schema-agent",
            "schema-template",
            new AgentInstanceConfig.WiringConfig(
                List.of("input.schema"),
                "output.schema",
                "dlq.schema"
            ),
            null, null
        );

        agent = new DoppelgangerAgent(schemaConfig, schemaTemplate, bus,
            LlmInferenceProvider.mock("{\"valid\": true}"));
        agent.activate();

        CountDownLatch latch = new CountDownLatch(1);
        bus.subscribe("output.schema", String.class, envelope -> latch.countDown());

        bus.publish("input.schema", SovereignEnvelope.create("sender", "test"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void processSecureMessage_invalidSchema_goesToDlq() throws InterruptedException {
        AgentTemplate schemaTemplate = new AgentTemplate(
            "schema-template",
            "VALIDATOR",
            "Responde JSON",
            "{\"type\": \"object\"}",
            List.of(),
            Map.of()
        );

        AgentInstanceConfig schemaConfig = new AgentInstanceConfig(
            "schema-agent",
            "schema-template",
            new AgentInstanceConfig.WiringConfig(
                List.of("input.schema2"),
                "output.schema2",
                "dlq.schema2"
            ),
            null, null
        );

        agent = new DoppelgangerAgent(schemaConfig, schemaTemplate, bus,
            LlmInferenceProvider.mock("esto no es JSON"));
        agent.activate();

        List<SovereignEnvelope<String>> dlq = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        bus.subscribe("dlq.schema2", String.class, envelope -> {
            dlq.add(envelope);
            latch.countDown();
        });

        bus.publish("input.schema2", SovereignEnvelope.create("sender", "test"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(dlq.get(0).headers().get("dlq.reason").contains("Schema"));
    }

    @Test
    void getters_returnCorrectValues() {
        agent = new DoppelgangerAgent(config, template, bus, LlmInferenceProvider.echo());

        assertEquals("test-agent-1", agent.getId());
        assertEquals("test-template-v1", agent.getTemplateId());
        assertFalse(agent.isActive());
        assertEquals(0, agent.getMessagesProcessed());
        assertEquals(0, agent.getErrorsCount());
        assertSame(config, agent.getConfig());
        assertSame(template, agent.getTemplate());
    }

    @Test
    void processSecureMessage_includesVariablesInPrompt() throws InterruptedException {
        String[] capturedPrompt = new String[1];
        LlmInferenceProvider capturingProvider = (systemPrompt, userMessage) -> {
            capturedPrompt[0] = userMessage;
            return "{\"captured\": true}";
        };

        AgentInstanceConfig configWithVars = new AgentInstanceConfig(
            "vars-agent",
            "test-template-v1",
            new AgentInstanceConfig.WiringConfig(
                List.of("input.vars"),
                "output.vars",
                "dlq.vars"
            ),
            null,
            Map.of("region", "LATAM", "env", "prod")
        );

        agent = new DoppelgangerAgent(configWithVars, template, bus, capturingProvider);
        agent.activate();

        CountDownLatch latch = new CountDownLatch(1);
        bus.subscribe("output.vars", String.class, envelope -> latch.countDown());

        bus.publish("input.vars", SovereignEnvelope.create("sender", "user message"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(capturedPrompt[0]);
        assertTrue(capturedPrompt[0].contains("region"));
        assertTrue(capturedPrompt[0].contains("LATAM"));
    }
}
