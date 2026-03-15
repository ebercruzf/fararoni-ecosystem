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
package dev.fararoni.core.observability;

import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class AgentSpan implements AutoCloseable {
    public static final AgentSpan NOOP = new AgentSpan(null);

    public enum Status {
        OK,
        ERROR,
        TIMEOUT,
        CANCELLED
    }

    private final Timer timer;
    private final long startNanos;
    private Status status = Status.OK;
    private Throwable error;
    private String traceId;

    AgentSpan(Timer timer) {
        this.timer = timer;
        this.startNanos = System.nanoTime();
    }

    public AgentSpan setStatus(Status status) {
        this.status = status;
        return this;
    }

    public AgentSpan setError(Throwable error) {
        this.error = error;
        this.status = Status.ERROR;
        return this;
    }

    public AgentSpan setTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public Status getStatus() {
        return status;
    }

    public Throwable getError() {
        return error;
    }

    public String getTraceId() {
        return traceId;
    }

    public long getElapsedMs() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    public long getElapsedNanos() {
        return System.nanoTime() - startNanos;
    }

    @Override
    public void close() {
        if (timer != null) {
            long duration = System.nanoTime() - startNanos;
            timer.record(duration, TimeUnit.NANOSECONDS);
        }
    }

    public boolean isActive() {
        return timer != null;
    }
}
