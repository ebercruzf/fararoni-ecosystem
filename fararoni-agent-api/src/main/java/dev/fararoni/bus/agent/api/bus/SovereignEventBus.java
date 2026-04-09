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
package dev.fararoni.bus.agent.api.bus;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public interface SovereignEventBus {

    <T> CompletableFuture<Void> publish(String topic, SovereignEnvelope<T> envelope);

    <T, R> CompletableFuture<R> request(
        String topic,
        SovereignEnvelope<T> envelope,
        Class<R> responseType,
        Duration timeout
    );

    <T> java.util.concurrent.Flow.Subscription subscribe(String topic, Class<T> payloadType, Consumer<SovereignEnvelope<T>> consumer);

    long getInFlightCount();

    boolean isHealthy();

    default void shutdown(Duration timeout) {
    }

    default int getPriority() {
        return 0;
    }

    default boolean isAvailable() {
        return true;
    }

    default boolean hasPendingMessages() {
        return false;
    }
}
