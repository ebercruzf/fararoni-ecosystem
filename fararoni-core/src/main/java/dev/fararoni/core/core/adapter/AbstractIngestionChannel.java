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
package dev.fararoni.core.core.adapter;

import dev.fararoni.bus.agent.api.io.IncomingMessage;
import dev.fararoni.bus.agent.api.io.IngestionChannel;
import dev.fararoni.core.core.event.EventBus;
import dev.fararoni.core.core.event.types.IncomingMessageEvent;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class AbstractIngestionChannel implements IngestionChannel {
    private final String name;
    private final String type;
    private final EventBus eventBus;

    private final CopyOnWriteArrayList<Consumer<IncomingMessage>> messageHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Throwable>> errorHandlers = new CopyOnWriteArrayList<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final AtomicLong messagesFailed = new AtomicLong(0);
    private final AtomicLong lastMessageAt = new AtomicLong(0);
    private volatile long startedAt = 0;

    protected AbstractIngestionChannel(String name, String type, EventBus eventBus) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.eventBus = eventBus;
    }

    protected AbstractIngestionChannel(String name, String type) {
        this(name, type, null);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public final void start() {
        if (running.compareAndSet(false, true)) {
            startedAt = System.currentTimeMillis();
            try {
                doStart();
            } catch (Exception e) {
                running.set(false);
                startedAt = 0;
                dispatchError(e);
                throw new IllegalStateException("Failed to start channel: " + name, e);
            }
        } else {
            throw new IllegalStateException("Channel already running: " + name);
        }
    }

    @Override
    public final void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                doStop();
            } catch (Exception e) {
                dispatchError(e);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isHealthy() {
        if (!running.get()) {
            return false;
        }
        try {
            return checkHealth();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onMessage(Consumer<IncomingMessage> handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        messageHandlers.add(handler);
    }

    @Override
    public void onError(Consumer<Throwable> errorHandler) {
        Objects.requireNonNull(errorHandler, "errorHandler must not be null");
        errorHandlers.add(errorHandler);
    }

    @Override
    public void close() {
        stop();
        messageHandlers.clear();
        errorHandlers.clear();
    }

    @Override
    public ChannelStats getStats() {
        return new ChannelStats(
            messagesReceived.get(),
            messagesProcessed.get(),
            messagesFailed.get(),
            lastMessageAt.get(),
            running.get() ? System.currentTimeMillis() - startedAt : 0
        );
    }

    protected void dispatchMessage(IncomingMessage message) {
        if (message == null) {
            return;
        }

        messagesReceived.incrementAndGet();
        lastMessageAt.set(System.currentTimeMillis());

        try {
            for (Consumer<IncomingMessage> handler : messageHandlers) {
                try {
                    handler.accept(message);
                } catch (Exception e) {
                    dispatchError(e);
                }
            }

            if (eventBus != null) {
                eventBus.publish(new IncomingMessageEvent(message, name));
            }

            messagesProcessed.incrementAndGet();
        } catch (Exception e) {
            messagesFailed.incrementAndGet();
            dispatchError(e);
        }
    }

    protected void dispatchError(Throwable error) {
        if (error == null) {
            return;
        }

        for (Consumer<Throwable> handler : errorHandlers) {
            try {
                handler.accept(error);
            } catch (Exception e) {
                System.err.println("[" + name + "] Error in error handler: " + e.getMessage());
            }
        }
    }

    protected abstract void doStart() throws Exception;

    protected abstract void doStop() throws Exception;

    protected abstract boolean checkHealth();

    protected EventBus getEventBus() {
        return eventBus;
    }
}
