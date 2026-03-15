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
import dev.fararoni.core.core.persistence.SovereignJournal;
import dev.fararoni.core.core.swarm.HiveMind;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public interface SwarmTransport {
    void register(String agentId);

    void unregister(String agentId);

    boolean isRegistered(String agentId);

    Set<String> getRegisteredAgents();

    void send(SwarmMessage msg);

    void broadcast(String senderId, String type, String content);

    default boolean sendAndAwait(SwarmMessage msg, long timeout, TimeUnit unit)
            throws InterruptedException {
        send(msg);
        return true;
    }

    SwarmMessage receive(String agentId, long timeout, TimeUnit unit)
            throws InterruptedException;

    SwarmMessage tryReceive(String agentId);

    int pendingCount(String agentId);

    void subscribeAll(Consumer<SwarmMessage> listener);

    boolean unsubscribe(Consumer<SwarmMessage> listener);

    default void clearAllListeners() {
    }

    void setCurrentMissionId(String missionId);

    String getCurrentMissionId();

    boolean clearMissionJournal();

    int replayHistory(String missionId);

    void dumpLogs(String missionId);

    default void connectJournal(JournalManager journal) {
    }

    BusMetrics getMetrics();

    void resetMetrics();

    default Queue<SwarmMessage> getMessageLog() {
        return new java.util.LinkedList<>();
    }

    default void clearLog() {
    }

    default void setPersistenceHook(Consumer<SwarmMessage> hook) {
    }

    void reset();

    record BusMetrics(
        long messagesSent,
        long messagesReceived,
        long persistenceFailures,
        long deliveryFailures,
        int registeredAgents,
        int logSize
    ) {
        public double deliverySuccessRate() {
            long total = messagesSent + deliveryFailures;
            return total > 0 ? (double) messagesSent / total : 1.0;
        }
    }
}
