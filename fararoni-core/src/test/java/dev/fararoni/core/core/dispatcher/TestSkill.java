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
package dev.fararoni.core.core.dispatcher;

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.ToolParameter;
import dev.fararoni.bus.agent.api.ToolSkill;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class TestSkill implements ToolSkill {
    private final AtomicInteger initCount = new AtomicInteger(0);
    private final AtomicInteger shutdownCount = new AtomicInteger(0);
    private final AtomicInteger invocationCount = new AtomicInteger(0);
    private boolean available = true;

    @Override
    public String getSkillName() {
        return "TEST";
    }

    @Override
    public String getDescription() {
        return "Test skill for unit testing";
    }

    @Override
    public void initialize() {
        initCount.incrementAndGet();
    }

    @Override
    public void shutdown() {
        shutdownCount.incrementAndGet();
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getInitCount() {
        return initCount.get();
    }

    public int getShutdownCount() {
        return shutdownCount.get();
    }

    public int getInvocationCount() {
        return invocationCount.get();
    }

    @AgentAction(name = "echo", description = "Echoes back the input message")
    public String echo(
        @ToolParameter(name = "message", description = "Message to echo") String message
    ) {
        invocationCount.incrementAndGet();
        return "Echo: " + message;
    }

    @AgentAction(name = "add", description = "Adds two numbers")
    public int add(
        @ToolParameter(name = "a", description = "First number") int a,
        @ToolParameter(name = "b", description = "Second number") int b
    ) {
        invocationCount.incrementAndGet();
        return a + b;
    }

    @AgentAction(name = "get_time", description = "Returns current time")
    public String getTime() {
        invocationCount.incrementAndGet();
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @AgentAction(name = "greet", description = "Greets with optional title")
    public String greet(
        @ToolParameter(name = "name", description = "Name to greet") String name,
        @ToolParameter(name = "title", description = "Optional title", required = false, defaultValue = "")
        String title
    ) {
        invocationCount.incrementAndGet();
        if (title != null && !title.isEmpty()) {
            return "Hello, " + title + " " + name + "!";
        }
        return "Hello, " + name + "!";
    }

    @AgentAction(name = "fail", description = "Always throws an exception")
    public String fail() {
        invocationCount.incrementAndGet();
        throw new RuntimeException("This action always fails");
    }

    @AgentAction(name = "slow", description = "Simulates a slow operation", timeoutMs = 100)
    public String slow(
        @ToolParameter(name = "delay_ms", description = "Delay in milliseconds") int delayMs
    ) throws InterruptedException {
        invocationCount.incrementAndGet();
        Thread.sleep(delayMs);
        return "Completed after " + delayMs + "ms";
    }

    @AgentAction(name = "secure", description = "Requires confirmation", requiresConfirmation = true)
    public String secure(
        @ToolParameter(name = "data", description = "Sensitive data") String data
    ) {
        invocationCount.incrementAndGet();
        return "Processed: " + data;
    }
}
