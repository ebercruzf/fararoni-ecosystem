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
package dev.fararoni.core.core.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class EventBus {

    private final List<Consumer<Object>> globalListeners = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, List<Consumer<?>>> typedListeners = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final AtomicLong publishedEvents = new AtomicLong(0);
    private final AtomicLong deliveredEvents = new AtomicLong(0);
    private final AtomicLong failedDeliveries = new AtomicLong(0);
    private volatile boolean debugMode = false;

    public EventBus() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public EventBus(ExecutorService executor) {
        this.executor = executor;
    }

    public void subscribe(Consumer<Object> listener) {
        globalListeners.add(listener);
    }

    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        typedListeners
            .computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
            .add((Consumer<Object>) listener);
    }

    public void publish(Object event) {
        if (event == null) {
            return;
        }

        publishedEvents.incrementAndGet();

        if (debugMode) {
            System.out.println("[EventBus] Publishing: " + event.getClass().getSimpleName());
        }

        executor.submit(() -> deliverEvent(event));
    }

    public void publishSync(Object event) {
        if (event == null) {
            return;
        }

        publishedEvents.incrementAndGet();
        deliverEvent(event);
    }

    @SuppressWarnings("unchecked")
    private void deliverEvent(Object event) {
        for (Consumer<Object> listener : globalListeners) {
            deliverToListener(listener, event);
        }

        Class<?> eventClass = event.getClass();
        while (eventClass != null) {
            List<Consumer<?>> listeners = typedListeners.get(eventClass);
            if (listeners != null) {
                for (Consumer<?> listener : listeners) {
                    deliverToListener((Consumer<Object>) listener, event);
                }
            }

            for (Class<?> iface : eventClass.getInterfaces()) {
                List<Consumer<?>> ifaceListeners = typedListeners.get(iface);
                if (ifaceListeners != null) {
                    for (Consumer<?> listener : ifaceListeners) {
                        deliverToListener((Consumer<Object>) listener, event);
                    }
                }
            }

            eventClass = eventClass.getSuperclass();
        }
    }

    private void deliverToListener(Consumer<Object> listener, Object event) {
        try {
            listener.accept(event);
            deliveredEvents.incrementAndGet();
        } catch (Exception e) {
            failedDeliveries.incrementAndGet();
            System.err.println("[EventBus] Error en listener para " +
                event.getClass().getSimpleName() + ": " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
        }
    }

    public void unsubscribe(Consumer<Object> listener) {
        globalListeners.remove(listener);
    }

    public <T> void unsubscribe(Class<T> eventType, Consumer<T> listener) {
        List<Consumer<?>> listeners = typedListeners.get(eventType);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public void clearAll() {
        globalListeners.clear();
        typedListeners.clear();
    }

    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }

    public EventBusMetrics getMetrics() {
        return new EventBusMetrics(
            publishedEvents.get(),
            deliveredEvents.get(),
            failedDeliveries.get(),
            globalListeners.size(),
            typedListeners.values().stream().mapToInt(List::size).sum()
        );
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public record EventBusMetrics(
        long publishedEvents,
        long deliveredEvents,
        long failedDeliveries,
        int globalListenerCount,
        int typedListenerCount
    ) {
        public double getDeliverySuccessRate() {
            long total = deliveredEvents + failedDeliveries;
            return total > 0 ? (double) deliveredEvents / total : 1.0;
        }

        public String getFormattedStats() {
            return String.format(
                "EventBus Stats: published=%d, delivered=%d, failed=%d, listeners=%d (global=%d, typed=%d)",
                publishedEvents, deliveredEvents, failedDeliveries,
                globalListenerCount + typedListenerCount, globalListenerCount, typedListenerCount
            );
        }
    }
}
