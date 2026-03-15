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
package dev.fararoni.core.core.swarm.infra;

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.core.core.persistence.SovereignJournal;
import dev.fararoni.core.core.swarm.HiveMind;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SovereignBridgeBus implements SwarmTransport {
    private static final Logger LOG = Logger.getLogger(SovereignBridgeBus.class.getName());

    private final SovereignEventBus sovereignBus;

    private final SovereignJournal journal;

    private final Map<String, BlockingQueue<SwarmMessage>> mailboxes = new ConcurrentHashMap<>();

    private final Set<String> registeredAgents = ConcurrentHashMap.newKeySet();

    private final List<Consumer<SwarmMessage>> globalListeners = new CopyOnWriteArrayList<>();

    private final Queue<SwarmMessage> messageLog = new ConcurrentLinkedQueue<>();

    private static final int MAX_LOG_SIZE = 10_000;

    private volatile String currentMissionId;

    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong persistenceFailures = new AtomicLong(0);
    private final AtomicLong deliveryFailures = new AtomicLong(0);

    public SovereignBridgeBus(SovereignEventBus sovereignBus, SovereignJournal journal) {
        this.sovereignBus = Objects.requireNonNull(sovereignBus, "sovereignBus is required");
        this.journal = journal;

        LOG.info("[BRIDGE] SovereignBridgeBus inicializado" +
            (journal != null ? " con persistencia" : " sin persistencia"));
    }

    public SovereignBridgeBus(SovereignEventBus sovereignBus) {
        this(sovereignBus, null);
    }

    @Override
    public void register(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            LOG.warning("[BRIDGE] Intento de registrar agentId null/vacío");
            return;
        }

        if (registeredAgents.add(agentId)) {
            mailboxes.put(agentId, new LinkedBlockingQueue<>());

            String topic = ProtocolTranslator.toTopic(agentId);
            sovereignBus.subscribe(topic, SwarmMessage.class, envelope -> {
                SwarmMessage msg = ProtocolTranslator.fromSovereign(envelope);
                deliverToMailbox(agentId, msg);
            });

            LOG.info("[BRIDGE] Agente registrado: " + agentId + " → " + topic);
        }
    }

    @Override
    public void unregister(String agentId) {
        if (registeredAgents.remove(agentId)) {
            BlockingQueue<SwarmMessage> removed = mailboxes.remove(agentId);
            if (removed != null && !removed.isEmpty()) {
                LOG.warning("[BRIDGE] Agente desregistrado " + agentId +
                    " con " + removed.size() + " mensajes pendientes");
            } else {
                LOG.info("[BRIDGE] Agente desregistrado: " + agentId);
            }
        }
    }

    @Override
    public boolean isRegistered(String agentId) {
        return registeredAgents.contains(agentId);
    }

    @Override
    public Set<String> getRegisteredAgents() {
        return Collections.unmodifiableSet(new HashSet<>(registeredAgents));
    }

    @Override
    public void send(SwarmMessage msg) {
        if (msg == null) {
            LOG.warning("[BRIDGE] Intento de enviar mensaje null");
            return;
        }

        try {
            addToLog(msg);

            notifyGlobalListeners(msg);

            SovereignEnvelope<SwarmMessage> envelope = ProtocolTranslator.toSovereign(msg);

            String topic = ProtocolTranslator.toTopic(msg.receiverId());

            sovereignBus.publish(topic, envelope).join();

            messagesSent.incrementAndGet();
            LOG.fine(() -> String.format("[BRIDGE] %s → %s [%s]",
                msg.senderId(), msg.receiverId(), msg.type()));
        } catch (Exception e) {
            deliveryFailures.incrementAndGet();
            LOG.severe("[BRIDGE] Error enviando mensaje: " + e.getMessage());
            throw new RuntimeException("Bridge send failure", e);
        }
    }

    @Override
    public void broadcast(String senderId, String type, String content) {
        LOG.info(() -> String.format("[BRIDGE] BROADCAST %s → %d agentes: %s",
            type, registeredAgents.size(), registeredAgents));

        for (String receiverId : new HashSet<>(registeredAgents)) {
            if (!receiverId.equals(senderId)) {
                try {
                    SwarmMessage msg = SwarmMessage.builder()
                        .from(senderId)
                        .to(receiverId)
                        .type(type)
                        .content(content)
                        .build();
                    send(msg);
                } catch (Exception e) {
                    LOG.warning("[BRIDGE] Error en broadcast a " + receiverId + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public boolean sendAndAwait(SwarmMessage msg, long timeout, TimeUnit unit)
            throws InterruptedException {
        send(msg);
        return true;
    }

    @Override
    public SwarmMessage receive(String agentId, long timeout, TimeUnit unit)
            throws InterruptedException {
        BlockingQueue<SwarmMessage> mailbox = mailboxes.get(agentId);
        if (mailbox == null) {
            LOG.warning("[BRIDGE] receive() para agente no registrado: " + agentId);
            return null;
        }

        SwarmMessage msg = mailbox.poll(timeout, unit);
        if (msg != null) {
            messagesReceived.incrementAndGet();
        }
        return msg;
    }

    @Override
    public SwarmMessage tryReceive(String agentId) {
        BlockingQueue<SwarmMessage> mailbox = mailboxes.get(agentId);
        if (mailbox == null) return null;

        SwarmMessage msg = mailbox.poll();
        if (msg != null) {
            messagesReceived.incrementAndGet();
        }
        return msg;
    }

    @Override
    public int pendingCount(String agentId) {
        BlockingQueue<SwarmMessage> mailbox = mailboxes.get(agentId);
        return mailbox != null ? mailbox.size() : 0;
    }

    private void deliverToMailbox(String agentId, SwarmMessage msg) {
        BlockingQueue<SwarmMessage> mailbox = mailboxes.get(agentId);
        if (mailbox != null) {
            mailbox.offer(msg);
            notifyGlobalListeners(msg);
            LOG.fine(() -> "[BRIDGE] Mensaje entregado a mailbox: " + agentId);
        } else {
            LOG.warning("[BRIDGE] Mailbox no encontrado para: " + agentId);
        }
    }

    @Override
    public void subscribeAll(Consumer<SwarmMessage> listener) {
        if (listener != null) {
            globalListeners.add(listener);
            LOG.fine("[BRIDGE] Listener global agregado. Total: " + globalListeners.size());
        }
    }

    @Override
    public boolean unsubscribe(Consumer<SwarmMessage> listener) {
        boolean removed = globalListeners.remove(listener);
        if (removed) {
            LOG.fine("[BRIDGE] Listener global eliminado. Total: " + globalListeners.size());
        }
        return removed;
    }

    @Override
    public void clearAllListeners() {
        globalListeners.clear();
        LOG.info("[BRIDGE] Todos los listeners globales eliminados");
    }

    private void notifyGlobalListeners(SwarmMessage msg) {
        for (Consumer<SwarmMessage> listener : globalListeners) {
            try {
                listener.accept(msg);
            } catch (Exception e) {
                LOG.warning("[BRIDGE] Error en listener global: " + e.getMessage());
            }
        }
    }

    @Override
    public void setCurrentMissionId(String missionId) {
        this.currentMissionId = missionId;
        LOG.fine("[BRIDGE] MissionId establecido: " + missionId);
    }

    @Override
    public String getCurrentMissionId() {
        return currentMissionId;
    }

    @Override
    public boolean clearMissionJournal() {
        if (journal != null) {
            journal.cleanup();
            LOG.info("[BRIDGE] Journal de misión limpiado");
            return true;
        }
        return false;
    }

    @Override
    public int replayHistory(String missionId) {
        LOG.warning("[BRIDGE] replayHistory llamado - recovery manejado por OutboxDispatcher");
        return 0;
    }

    @Override
    public void dumpLogs(String missionId) {
        LOG.info("═══════════════════════════════════════════════════════════════");
        LOG.info("[BRIDGE] BLACK BOX DUMP - Misión: " + missionId);
        LOG.info("[BRIDGE] Total mensajes logueados: " + messageLog.size());
        LOG.info("───────────────────────────────────────────────────────────────");

        messageLog.forEach(msg -> {
            String content = msg.content();
            String truncated = content.length() > 80
                ? content.substring(0, 80) + "..."
                : content;
            LOG.info(String.format("[BRIDGE]   %s → %s [%s]: %s",
                msg.senderId(), msg.receiverId(), msg.type(), truncated));
        });

        LOG.info("═══════════════════════════════════════════════════════════════");
    }

    @Override
    public BusMetrics getMetrics() {
        return new BusMetrics(
            messagesSent.get(),
            messagesReceived.get(),
            persistenceFailures.get(),
            deliveryFailures.get(),
            registeredAgents.size(),
            messageLog.size()
        );
    }

    @Override
    public void resetMetrics() {
        messagesSent.set(0);
        messagesReceived.set(0);
        persistenceFailures.set(0);
        deliveryFailures.set(0);
    }

    @Override
    public Queue<SwarmMessage> getMessageLog() {
        return new ConcurrentLinkedQueue<>(messageLog);
    }

    @Override
    public void clearLog() {
        messageLog.clear();
    }

    @Override
    public void reset() {
        mailboxes.clear();
        registeredAgents.clear();
        globalListeners.clear();
        messageLog.clear();
        currentMissionId = null;
        resetMetrics();
        LOG.warning("[BRIDGE] Bus reseteado completamente");
    }

    private void addToLog(SwarmMessage msg) {
        messageLog.offer(msg);
        while (messageLog.size() > MAX_LOG_SIZE) {
            messageLog.poll();
        }
    }
}
