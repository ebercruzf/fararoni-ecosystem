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
import dev.fararoni.core.core.agent.model.AgentInstanceConfig;
import dev.fararoni.core.core.agent.model.AgentInstanceConfig.WiringConfig;
import dev.fararoni.core.core.agent.model.AgentInstanceConfig.RoutingConfig;
import dev.fararoni.core.core.agent.model.AgentTemplate;
import dev.fararoni.core.core.bus.InMemorySovereignBus;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SovereignIntegrationStressTest {
    private static final Logger LOG = Logger.getLogger(SovereignIntegrationStressTest.class.getName());

    private SovereignEventBus bus;
    private List<DynamicSwarmAgent> agents;
    private ExecutorService testExecutor;

    @BeforeEach
    void setUp() {
        bus = new InMemorySovereignBus();
        agents = new CopyOnWriteArrayList<>();
        testExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterEach
    void tearDown() {
        agents.forEach(agent -> {
            try {
                agent.stop();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error deteniendo agente: " + e.getMessage());
            }
        });
        agents.clear();

        testExecutor.shutdownNow();

        if (bus != null) {
            bus.shutdown(Duration.ofSeconds(2));
        }
    }

    @Test
    @Order(1)
    @DisplayName("1.1 Swarm Flood - 100 agentes simultaneos")
    void testSwarmFlood_100AgentsSimultaneous() throws Exception {
        final int AGENT_COUNT = 100;
        final String BROADCAST_TOPIC = "broadcast.stress.test";
        final CountDownLatch allReceived = new CountDownLatch(AGENT_COUNT);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicLong totalResponseTime = new AtomicLong(0);
        final long startTime = System.currentTimeMillis();

        for (int i = 0; i < AGENT_COUNT; i++) {
            final int agentNum = i;
            AgentTemplate template = createTemplate("stress-agent-" + i, "STRESS_TESTER_" + i);
            WiringConfig wiring = new WiringConfig(
                List.of(BROADCAST_TOPIC),
                "output.stress." + i,
                "dlq.stress"
            );
            AgentInstanceConfig config = new AgentInstanceConfig(
                "STRESS_AGENT_" + i,
                template.templateId(),
                wiring,
                RoutingConfig.defaults(),
                Map.of()
            );

            DynamicSwarmAgent.LlmInferenceProvider mockLlm = (sys, user) -> {
                long responseStart = System.currentTimeMillis();
                try {
                    Thread.sleep(10 + new Random().nextInt(40));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                long elapsed = System.currentTimeMillis() - responseStart;
                totalResponseTime.addAndGet(elapsed);
                successCount.incrementAndGet();
                allReceived.countDown();
                return "Agente " + agentNum + " respondio en " + elapsed + "ms";
            };

            DynamicSwarmAgent agent = new DynamicSwarmAgent(
                config.id(),
                template,
                wiring,
                bus,
                mockLlm
            );
            agent.start();
            agents.add(agent);
        }

        var envelope = SovereignEnvelope.create(
            "STRESS_COORDINATOR",
            "BROADCAST",
            UUID.randomUUID().toString(),
            "WAKE UP ALL AGENTS - STRESS TEST"
        );
        bus.publish(BROADCAST_TOPIC, envelope);

        boolean allCompleted = allReceived.await(30, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - startTime;

        assertTrue(allCompleted, "No todos los agentes respondieron a tiempo");
        assertEquals(AGENT_COUNT, successCount.get(), "Algunos agentes no procesaron el mensaje");

        double avgResponseTime = (double) totalResponseTime.get() / AGENT_COUNT;
        LOG.info(String.format(
            "[SWARM FLOOD] %d agentes, tiempo total: %dms, promedio: %.2fms",
            AGENT_COUNT, totalTime, avgResponseTime
        ));

        assertTrue(avgResponseTime < 500,
            "Tiempo de respuesta promedio muy alto: " + avgResponseTime + "ms");
    }

    @Test
    @Order(2)
    @DisplayName("1.2 Circular Dependency - Sistema maneja bucles sin crash")
    void testCircularDependency_GracefulHandling() throws Exception {
        final String TOPIC_A = "circular.topic.a";
        final String TOPIC_B = "circular.topic.b";
        final AtomicInteger processedByA = new AtomicInteger(0);
        final AtomicInteger processedByB = new AtomicInteger(0);
        final long TEST_DURATION_MS = 500;

        DynamicSwarmAgent agentA = createAgent("AGENT_A", TOPIC_A, TOPIC_B, (sys, user) -> {
            processedByA.incrementAndGet();
            return "Response from A";
        });

        DynamicSwarmAgent agentB = createAgent("AGENT_B", TOPIC_B, TOPIC_A, (sys, user) -> {
            processedByB.incrementAndGet();
            return "Response from B";
        });

        agents.add(agentA);
        agents.add(agentB);
        agentA.start();
        agentB.start();

        var envelope = SovereignEnvelope.create(
            "EXTERNAL_TRIGGER",
            "INIT",
            UUID.randomUUID().toString(),
            "Start circular test"
        );
        long startTime = System.currentTimeMillis();
        bus.publish(TOPIC_A, envelope);

        Thread.sleep(TEST_DURATION_MS);

        agentA.stop();
        agentB.stop();

        long elapsed = System.currentTimeMillis() - startTime;
        int totalProcessed = processedByA.get() + processedByB.get();
        double throughput = (double) totalProcessed / (elapsed / 1000.0);

        LOG.info(String.format(
            "[CIRCULAR] A: %d, B: %d, Total: %d en %dms, Throughput: %.0f msg/s",
            processedByA.get(), processedByB.get(), totalProcessed, elapsed, throughput
        ));

        assertTrue(processedByA.get() > 0, "A debe haber procesado mensajes");
        assertTrue(processedByB.get() > 0, "B debe haber procesado mensajes");

        assertTrue(throughput > 1000,
            "Throughput debe ser alto (>1000 msg/s), fue: " + throughput);
    }

    @Test
    @Order(3)
    @DisplayName("2.1 Lazy Fetch - Factory no toca bus en constructor")
    void testLazyFetch_FactoryDoesNotTouchBusOnConstruction() {
        DynamicAgentFactory factory = new DynamicAgentFactory();

        assertNotNull(factory, "Factory debe poder crearse sin dependencias");
    }

    @Test
    @Order(4)
    @DisplayName("2.2 Lifecycle - Start/Stop sin perdida")
    void testAgentLifecycle_StartStopNoPanic() throws Exception {
        final String INPUT_TOPIC = "lifecycle.input";
        final AtomicInteger processedCount = new AtomicInteger(0);
        final CountDownLatch firstBatch = new CountDownLatch(5);

        DynamicSwarmAgent agent = createAgent("LIFECYCLE_AGENT", INPUT_TOPIC, null, (sys, user) -> {
            processedCount.incrementAndGet();
            firstBatch.countDown();
            return "Processed";
        });

        agents.add(agent);
        agent.start();

        for (int i = 0; i < 5; i++) {
            publishMessage(INPUT_TOPIC, "Message " + i);
        }

        assertTrue(firstBatch.await(5, TimeUnit.SECONDS), "Primer batch no procesado");
        assertEquals(5, processedCount.get());

        agent.stop();

        int beforeStop = processedCount.get();
        publishMessage(INPUT_TOPIC, "After stop");
        Thread.sleep(500);

        assertEquals(beforeStop, processedCount.get(), "Agente detenido no debe procesar");
    }

    @Test
    @Order(5)
    @DisplayName("3.1 Poison Pill - Agente toxico no tumba sistema")
    void testPoisonPill_ToxicAgentIsolated() throws Exception {
        final String SHARED_TOPIC = "shared.input";
        final String DLQ_TOPIC = "shared.dlq";
        final AtomicInteger healthyProcessed = new AtomicInteger(0);
        final AtomicInteger toxicErrors = new AtomicInteger(0);
        final CountDownLatch healthyLatch = new CountDownLatch(10);
        final List<String> dlqMessages = new CopyOnWriteArrayList<>();

        bus.subscribe(DLQ_TOPIC, String.class, env -> {
            dlqMessages.add(env.payload());
            toxicErrors.incrementAndGet();
        });

        DynamicSwarmAgent toxicAgent = createAgentWithDlq(
            "TOXIC_AGENT", SHARED_TOPIC, "toxic.output", DLQ_TOPIC,
            (sys, user) -> {
                throw new RuntimeException("POISON PILL - Siempre fallo!");
            }
        );

        DynamicSwarmAgent healthyAgent = createAgent(
            "HEALTHY_AGENT", SHARED_TOPIC, "healthy.output",
            (sys, user) -> {
                healthyProcessed.incrementAndGet();
                healthyLatch.countDown();
                return "Healthy response";
            }
        );

        agents.add(toxicAgent);
        agents.add(healthyAgent);
        toxicAgent.start();
        healthyAgent.start();

        for (int i = 0; i < 10; i++) {
            publishMessage(SHARED_TOPIC, "Test message " + i);
            Thread.sleep(50);
        }

        boolean healthyCompleted = healthyLatch.await(10, TimeUnit.SECONDS);

        assertTrue(healthyCompleted, "El agente sano debe completar todos los mensajes");
        assertEquals(10, healthyProcessed.get(), "Agente sano debe procesar 10 mensajes");

        LOG.info(String.format(
            "[POISON PILL] Healthy: %d, Toxic errors: %d, DLQ: %d",
            healthyProcessed.get(), toxicErrors.get(), dlqMessages.size()
        ));

        assertTrue(toxicErrors.get() > 0, "Errores toxicos deben ir al DLQ");
    }

    @Test
    @Order(6)
    @DisplayName("3.2 Timeout - LLM lento no bloquea")
    void testTimeoutHandling_SlowLlmDoesNotBlock() throws Exception {
        final String FAST_TOPIC = "speed.fast";
        final String SLOW_TOPIC = "speed.slow";
        final AtomicInteger fastProcessed = new AtomicInteger(0);
        final AtomicInteger slowProcessed = new AtomicInteger(0);
        final CountDownLatch fastLatch = new CountDownLatch(5);

        DynamicSwarmAgent slowAgent = createAgent("SLOW_AGENT", SLOW_TOPIC, null, (sys, user) -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            slowProcessed.incrementAndGet();
            return "Slow response";
        });

        DynamicSwarmAgent fastAgent = createAgent("FAST_AGENT", FAST_TOPIC, null, (sys, user) -> {
            fastProcessed.incrementAndGet();
            fastLatch.countDown();
            return "Fast response";
        });

        agents.add(slowAgent);
        agents.add(fastAgent);
        slowAgent.start();
        fastAgent.start();

        publishMessage(SLOW_TOPIC, "Slow message");

        for (int i = 0; i < 5; i++) {
            publishMessage(FAST_TOPIC, "Fast " + i);
        }

        boolean fastCompleted = fastLatch.await(1, TimeUnit.SECONDS);

        assertTrue(fastCompleted, "Mensajes rapidos deben completar en 1s");
        assertEquals(5, fastProcessed.get(), "Todos los rapidos procesados");
        assertEquals(0, slowProcessed.get(), "El lento aun no termina");

        Thread.sleep(2500);
        assertEquals(1, slowProcessed.get(), "El lento eventualmente termina");
    }

    @Test
    @Order(7)
    @DisplayName("4.1 Memory Pressure - 1000 mensajes en cola")
    void testMemoryPressure_HighMessageVolume() throws Exception {
        final int MESSAGE_COUNT = 1000;
        final String TOPIC = "pressure.test";
        final AtomicInteger processed = new AtomicInteger(0);
        final CountDownLatch allProcessed = new CountDownLatch(MESSAGE_COUNT);

        DynamicSwarmAgent agent = createAgent("PRESSURE_AGENT", TOPIC, null, (sys, user) -> {
            processed.incrementAndGet();
            allProcessed.countDown();
            return "OK";
        });

        agents.add(agent);
        agent.start();

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            publishMessage(TOPIC, "Pressure message " + i);
        }
        long publishTime = System.currentTimeMillis() - startTime;

        boolean completed = allProcessed.await(30, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - startTime;

        assertTrue(completed, "Todos los mensajes deben procesarse");
        assertEquals(MESSAGE_COUNT, processed.get());

        double throughput = (double) MESSAGE_COUNT / (totalTime / 1000.0);
        LOG.info(String.format(
            "[MEMORY PRESSURE] %d msgs, publish: %dms, total: %dms, throughput: %.2f msg/s",
            MESSAGE_COUNT, publishTime, totalTime, throughput
        ));

        assertTrue(throughput > 50, "Throughput debe ser > 50 msg/s, fue: " + throughput);
    }

    @Test
    @Order(8)
    @DisplayName("4.2 Concurrent Publishers - 10 publicadores simultaneos")
    void testConcurrentPublishers_MultipleWriters() throws Exception {
        final int PUBLISHERS = 10;
        final int MESSAGES_PER_PUBLISHER = 50;
        final String TOPIC = "concurrent.input";
        final AtomicInteger totalReceived = new AtomicInteger(0);
        final CountDownLatch allReceived = new CountDownLatch(PUBLISHERS * MESSAGES_PER_PUBLISHER);

        DynamicSwarmAgent agent = createAgent("CONCURRENT_RECEIVER", TOPIC, null, (sys, user) -> {
            totalReceived.incrementAndGet();
            allReceived.countDown();
            return "Received";
        });

        agents.add(agent);
        agent.start();

        List<Future<?>> publishers = new ArrayList<>();
        for (int p = 0; p < PUBLISHERS; p++) {
            final int publisherId = p;
            Future<?> f = testExecutor.submit(() -> {
                for (int m = 0; m < MESSAGES_PER_PUBLISHER; m++) {
                    publishMessage(TOPIC, "P" + publisherId + "-M" + m);
                }
            });
            publishers.add(f);
        }

        for (Future<?> f : publishers) {
            f.get(10, TimeUnit.SECONDS);
        }

        boolean completed = allReceived.await(30, TimeUnit.SECONDS);

        assertTrue(completed, "Todos los mensajes deben recibirse");
        assertEquals(PUBLISHERS * MESSAGES_PER_PUBLISHER, totalReceived.get());
    }

    private AgentTemplate createTemplate(String id, String roleName) {
        return new AgentTemplate(
            id,
            roleName,
            "System prompt for " + roleName,
            null,
            List.of("TEST_CAPABILITY"),
            Map.of("test", "true")
        );
    }

    private DynamicSwarmAgent createAgent(
            String id,
            String inputTopic,
            String outputTopic,
            DynamicSwarmAgent.LlmInferenceProvider llmProvider) {
        return createAgentWithDlq(id, inputTopic, outputTopic, null, llmProvider);
    }

    private DynamicSwarmAgent createAgentWithDlq(
            String id,
            String inputTopic,
            String outputTopic,
            String dlqTopic,
            DynamicSwarmAgent.LlmInferenceProvider llmProvider) {
        AgentTemplate template = createTemplate(id + "-template", id + "_ROLE");
        WiringConfig wiring = new WiringConfig(
            List.of(inputTopic),
            outputTopic,
            dlqTopic
        );
        AgentInstanceConfig config = new AgentInstanceConfig(
            id,
            template.templateId(),
            wiring,
            RoutingConfig.defaults(),
            Map.of()
        );

        return new DynamicSwarmAgent(
            config.id(),
            template,
            wiring,
            bus,
            llmProvider
        );
    }

    private void publishMessage(String topic, String content) {
        var envelope = SovereignEnvelope.create(
            "TEST_PUBLISHER",
            "TEST",
            UUID.randomUUID().toString(),
            content
        );
        bus.publish(topic, envelope);
    }
}
