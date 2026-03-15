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

import dev.fararoni.core.core.persistence.JournalManager;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class MessageBus implements SwarmTransport {
    private static final Logger LOG = Logger.getLogger(MessageBus.class.getName());

    private static final MessageBus LEGACY_INSTANCE = new MessageBus();

    private final Map<String, TransferQueue<SwarmMessage>> mailboxes = new ConcurrentHashMap<>();
    private final Queue<SwarmMessage> messageLog = new ConcurrentLinkedQueue<>();

    private Consumer<SwarmMessage> persistenceHook = msg -> {};
    private Consumer<SwarmMessage> messageInterceptor = msg -> {};
    private Consumer<SwarmMessage> outputHook = msg -> {};

    private final List<Consumer<SwarmMessage>> globalListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private Function<String, List<SwarmMessage>> historyProvider = id -> List.of();

    private long messagesReplayed = 0;
    private long messagesSent = 0;
    private long messagesReceived = 0;
    private long persistenceFailures = 0;
    private long deliveryFailures = 0;

    private boolean persistenceEnabled = true;
    private int maxLogSize = 10000;

    private JournalManager journal;
    private volatile String currentMissionId;

    public MessageBus() {
    }

    public MessageBus(JournalManager journal) {
        this();
        connectJournal(journal);
    }

    @Deprecated
    public static MessageBus getInstance() {
        return LEGACY_INSTANCE;
    }

    public void setOutputHook(Consumer<SwarmMessage> hook) {
        this.outputHook = hook != null ? hook : msg -> {};
    }

    @Override
    public void register(String agentId) {
        mailboxes.putIfAbsent(agentId, new LinkedTransferQueue<>());
        LOG.info(() -> "[MessageBus] Registered agent: " + agentId);
    }

    @Override
    public void unregister(String agentId) {
        TransferQueue<SwarmMessage> removed = mailboxes.remove(agentId);
        if (removed != null && !removed.isEmpty()) {
            LOG.warning(() -> String.format(
                "[MessageBus] Unregistered agent %s with %d pending messages",
                agentId, removed.size()));
        }
    }

    @Override
    public boolean isRegistered(String agentId) {
        return mailboxes.containsKey(agentId);
    }

    @Override
    public Set<String> getRegisteredAgents() {
        return Set.copyOf(mailboxes.keySet());
    }

    @Override
    public void send(SwarmMessage msg) {
        if (persistenceEnabled) {
            try {
                persistenceHook.accept(msg);
            } catch (Exception e) {
                persistenceFailures++;
                LOG.severe(() -> String.format(
                    "[MessageBus] CRITICAL: Persistence failed for message %s. Aborting send.",
                    msg.id()));
                throw new RuntimeException("Bus Persistence Failure", e);
            }
        }

        addToLog(msg);

        messageInterceptor.accept(msg);

        TransferQueue<SwarmMessage> queue = mailboxes.get(msg.receiverId());
        if (queue != null) {
            queue.offer(msg);
            messagesSent++;
            LOG.fine(() -> String.format("[MessageBus] %s → %s: %s",
                msg.senderId(), msg.receiverId(), msg.type()));

            outputHook.accept(msg);

            notifyGlobalListeners(msg);
        } else {
            deliveryFailures++;
            LOG.warning(() -> String.format(
                "[MessageBus] Recipient not registered: %s (from %s)",
                msg.receiverId(), msg.senderId()));
        }
    }

    @Override
    public void broadcast(String senderId, String type, String content) {
        Set<String> agents = getRegisteredAgents();
        LOG.info(() -> String.format("[MessageBus] BROADCAST %s -> %d agentes: %s",
            type, agents.size(), agents));

        for (String receiverId : agents) {
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
                    LOG.warning(() -> String.format(
                        "[WARN] [BROADCAST] Error enviando a %s: %s", receiverId, e.getMessage()));
                }
            }
        }
    }

    @Override
    public boolean sendAndAwait(SwarmMessage msg, long timeout, TimeUnit unit)
            throws InterruptedException {
        if (persistenceEnabled) {
            persistenceHook.accept(msg);
        }
        addToLog(msg);

        TransferQueue<SwarmMessage> queue = mailboxes.get(msg.receiverId());
        if (queue != null) {
            boolean transferred = queue.tryTransfer(msg, timeout, unit);
            if (transferred) messagesSent++;
            return transferred;
        }
        return false;
    }

    @Override
    public SwarmMessage receive(String agentId, long timeout, TimeUnit unit)
            throws InterruptedException {
        TransferQueue<SwarmMessage> queue = mailboxes.get(agentId);
        if (queue == null) {
            LOG.warning(() -> "[MessageBus] No mailbox for: " + agentId);
            return null;
        }

        SwarmMessage msg = queue.poll(timeout, unit);
        if (msg != null) {
            messagesReceived++;
        }
        return msg;
    }

    @Override
    public SwarmMessage tryReceive(String agentId) {
        TransferQueue<SwarmMessage> queue = mailboxes.get(agentId);
        if (queue == null) return null;

        SwarmMessage msg = queue.poll();
        if (msg != null) messagesReceived++;
        return msg;
    }

    @Override
    public int pendingCount(String agentId) {
        TransferQueue<SwarmMessage> queue = mailboxes.get(agentId);
        return queue != null ? queue.size() : 0;
    }

    public void setPersistenceHook(Consumer<SwarmMessage> hook) {
        this.persistenceHook = hook != null ? hook : msg -> {};
    }

    public void setMessageInterceptor(Consumer<SwarmMessage> interceptor) {
        this.messageInterceptor = interceptor != null ? interceptor : msg -> {};
    }

    public void setPersistenceEnabled(boolean enabled) {
        this.persistenceEnabled = enabled;
    }

    @Override
    public void subscribeAll(Consumer<SwarmMessage> listener) {
        if (listener != null) {
            globalListeners.add(listener);
            LOG.fine(() -> "[BUS] Listener global agregado. Total: " + globalListeners.size());
        }
    }

    @Override
    public boolean unsubscribe(Consumer<SwarmMessage> listener) {
        boolean removed = globalListeners.remove(listener);
        if (removed) {
            LOG.fine(() -> "[BUS] Listener global eliminado. Total: " + globalListeners.size());
        }
        return removed;
    }

    @Override
    public void clearAllListeners() {
        globalListeners.clear();
        LOG.info("[BUS] Todos los listeners globales eliminados");
    }

    private void notifyGlobalListeners(SwarmMessage msg) {
        for (Consumer<SwarmMessage> listener : globalListeners) {
            try {
                listener.accept(msg);
            } catch (Exception e) {
                LOG.warning(() -> "[BUS] Error en listener global: " + e.getMessage());
            }
        }
    }

    public void connectJournal(JournalManager journal) {
        this.journal = journal;

        this.persistenceHook = msg -> {
            if (currentMissionId != null && journal != null) {
                journal.append(currentMissionId, msg);
            }
        };

        this.historyProvider = missionId -> {
            if (journal != null) {
                return journal.replay(missionId);
            }
            return List.of();
        };

        LOG.info(() -> "[BUS] Journal conectado: " + journal.getJournalDir());
    }

    @Override
    public void setCurrentMissionId(String missionId) {
        this.currentMissionId = missionId;
        LOG.fine(() -> "[BUS] MissionId establecido: " + missionId);
    }

    @Override
    public String getCurrentMissionId() {
        return currentMissionId;
    }

    public JournalManager getJournal() {
        return journal;
    }

    @Override
    public boolean clearMissionJournal() {
        if (journal != null && currentMissionId != null) {
            return journal.deleteJournal(currentMissionId);
        }
        return false;
    }

    public void setHistoryProvider(Function<String, List<SwarmMessage>> provider) {
        this.historyProvider = provider != null ? provider : id -> List.of();
    }

    @Override
    public int replayHistory(String missionId) {
        List<SwarmMessage> history = historyProvider.apply(missionId);

        if (history.isEmpty()) {
            LOG.info(() -> "[BUS] No hay historial para rehidratar: " + missionId);
            return 0;
        }

        LOG.info(() -> String.format(
            "[BUS] Rehidratando %d mensajes para misión %s...",
            history.size(), missionId));

        int replayed = 0;
        for (SwarmMessage msg : history) {
            TransferQueue<SwarmMessage> queue = mailboxes.get(msg.receiverId());
            if (queue != null) {
                queue.offer(msg);
                replayed++;
                LOG.fine(() -> String.format(
                    "[BUS] Rehidratado: %s → %s: %s",
                    msg.senderId(), msg.receiverId(), msg.type()));
            } else {
                LOG.warning(() -> String.format(
                    "[BUS] No se pudo rehidratar mensaje para %s (no registrado)",
                    msg.receiverId()));
            }
        }

        messagesReplayed += replayed;
        final int finalReplayed = replayed;
        LOG.info(() -> String.format(
            "[BUS] Rehidratación completada: %d/%d mensajes",
            finalReplayed, history.size()));

        return replayed;
    }

    public List<SwarmMessage> getMessagesForMission(String missionId) {
        return messageLog.stream()
            .filter(msg -> {
                String msgMission = msg.getMetadata("missionId");
                return missionId.equals(msgMission);
            })
            .toList();
    }

    @Override
    public void dumpLogs(String missionId) {
        LOG.info(() -> "[BLACK BOX] === Dumping logs for mission: " + missionId + " ===");
        messageLog.stream()
            .filter(msg -> {
                String msgMission = msg.getMetadata("missionId");
                return missionId.equals(msgMission) || msgMission == null;
            })
            .forEach(msg -> LOG.info(() -> String.format(
                "  [%s] %s → %s: %s (%s)",
                msg.timestamp(), msg.senderId(), msg.receiverId(),
                msg.type(), truncate(msg.content(), 50))));
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
    public SwarmTransport.BusMetrics getMetrics() {
        return new SwarmTransport.BusMetrics(
            messagesSent,
            messagesReceived,
            persistenceFailures,
            deliveryFailures,
            mailboxes.size(),
            messageLog.size()
        );
    }

    @Override
    public void resetMetrics() {
        messagesSent = 0;
        messagesReceived = 0;
        persistenceFailures = 0;
        deliveryFailures = 0;
    }

    @Override
    public void reset() {
        mailboxes.clear();
        messageLog.clear();
        resetMetrics();
        LOG.warning("[MessageBus] Bus reset completely");
    }

    private void addToLog(SwarmMessage msg) {
        messageLog.offer(msg);
        while (messageLog.size() > maxLogSize) {
            messageLog.poll();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
