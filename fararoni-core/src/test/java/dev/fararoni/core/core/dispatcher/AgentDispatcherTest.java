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

import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.bus.agent.api.ToolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("AgentDispatcher Tests")
class AgentDispatcherTest {
    private ToolRegistryImpl registry;
    private AgentDispatcher dispatcher;
    private TestSkill testSkill;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistryImpl();
        testSkill = new TestSkill();
        registry.register(testSkill);
        dispatcher = new AgentDispatcher(registry);
    }

    @Test
    @DisplayName("Should execute simple echo action")
    void shouldExecuteSimpleEchoAction() {
        ToolRequest request = ToolRequest.of("TEST", "echo", Map.of("message", "Hello World"));

        ToolResponse response = dispatcher.executeSingle(request);

        assertThat(response.success()).isTrue();
        assertThat(response.result()).isEqualTo("Echo: Hello World");
        assertThat(response.executionTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(testSkill.getInvocationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should execute add action with numeric params")
    void shouldExecuteAddActionWithNumericParams() {
        ToolRequest request = ToolRequest.of("TEST", "add", Map.of("a", 5, "b", 3));

        ToolResponse response = dispatcher.executeSingle(request);

        assertThat(response.success()).isTrue();
        assertThat(response.result()).isEqualTo("8");
    }

    @Test
    @DisplayName("Should execute action with no params")
    void shouldExecuteActionWithNoParams() {
        ToolRequest request = ToolRequest.of("TEST", "get_time");

        ToolResponse response = dispatcher.executeSingle(request);

        assertThat(response.success()).isTrue();
        assertThat(response.result()).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle optional parameter")
    void shouldHandleOptionalParameter() {
        ToolRequest request1 = ToolRequest.of("TEST", "greet", Map.of("name", "Alice"));

        ToolResponse response1 = dispatcher.executeSingle(request1);

        assertThat(response1.success()).isTrue();
        assertThat(response1.result()).isEqualTo("Hello, Alice!");

        ToolRequest request2 = ToolRequest.of("TEST", "greet",
            Map.of("name", "Alice", "title", "Dr."));

        ToolResponse response2 = dispatcher.executeSingle(request2);

        assertThat(response2.success()).isTrue();
        assertThat(response2.result()).isEqualTo("Hello, Dr. Alice!");
    }

    @Test
    @DisplayName("Should return error for non-existent skill")
    void shouldReturnErrorForNonExistentSkill() {
        ToolRequest request = ToolRequest.of("NONEXISTENT", "action", Map.of());

        ToolResponse response = dispatcher.executeSingle(request);

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("not found");
    }

    @Test
    @DisplayName("Should return error for non-existent action")
    void shouldReturnErrorForNonExistentAction() {
        ToolRequest request = ToolRequest.of("TEST", "nonexistent", Map.of());

        ToolResponse response = dispatcher.executeSingle(request);

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("not found");
    }

    @Test
    @DisplayName("Should return error for missing required parameter")
    void shouldReturnErrorForMissingRequiredParameter() {
        ToolRequest request = ToolRequest.of("TEST", "echo", Map.of());

        ToolResponse response = dispatcher.executeSingle(request);

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("message");
    }

    @Test
    @DisplayName("Should handle action that throws exception")
    void shouldHandleActionThatThrowsException() {
        ToolRequest request = ToolRequest.of("TEST", "fail", Map.of());

        ToolResponse response = dispatcher.executeSingle(request);

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("always fails");
    }

    @Test
    @DisplayName("Should return error when skill is unavailable")
    void shouldReturnErrorWhenSkillIsUnavailable() {
        testSkill.setAvailable(false);
        ToolRequest request = ToolRequest.of("TEST", "echo", Map.of("message", "test"));

        ToolResponse response = dispatcher.executeSingle(request);

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("no esta disponible");
    }

    @Test
    @DisplayName("Should execute batch of requests in parallel")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void shouldExecuteBatchOfRequestsInParallel() {
        List<ToolRequest> requests = List.of(
            ToolRequest.of("TEST", "echo", Map.of("message", "One")),
            ToolRequest.of("TEST", "echo", Map.of("message", "Two")),
            ToolRequest.of("TEST", "echo", Map.of("message", "Three"))
        );

        List<ToolResponse> responses = dispatcher.executeBatch(requests);

        assertThat(responses).hasSize(3);
        assertThat(responses).allMatch(ToolResponse::success);
        assertThat(responses.get(0).result()).isEqualTo("Echo: One");
        assertThat(responses.get(1).result()).isEqualTo("Echo: Two");
        assertThat(responses.get(2).result()).isEqualTo("Echo: Three");
    }

    @Test
    @DisplayName("Should return empty list for empty batch")
    void shouldReturnEmptyListForEmptyBatch() {
        List<ToolResponse> responses = dispatcher.executeBatch(List.of());

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("Should handle single item batch")
    void shouldHandleSingleItemBatch() {
        List<ToolRequest> requests = List.of(
            ToolRequest.of("TEST", "echo", Map.of("message", "Single"))
        );

        List<ToolResponse> responses = dispatcher.executeBatch(requests);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().success()).isTrue();
    }

    @Test
    @DisplayName("Should execute many requests concurrently with Virtual Threads")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void shouldExecuteManyRequestsConcurrentlyWithVirtualThreads() {
        int count = 100;
        List<ToolRequest> requests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            requests.add(ToolRequest.of("TEST", "echo", Map.of("message", "Request " + i)));
        }

        long startTime = System.currentTimeMillis();
        List<ToolResponse> responses = dispatcher.executeBatch(requests);
        long elapsed = System.currentTimeMillis() - startTime;

        assertThat(responses).hasSize(count);
        assertThat(responses).allMatch(ToolResponse::success);
        assertThat(testSkill.getInvocationCount()).isEqualTo(count);

        System.out.println("Executed " + count + " requests in " + elapsed + "ms");
    }

    @Test
    @DisplayName("Should convert string numbers to int parameters")
    void shouldConvertStringNumbersToIntParameters() {
        ToolRequest request = ToolRequest.of("TEST", "add", Map.of("a", "10", "b", "20"));

        ToolResponse response = dispatcher.executeSingle(request);

        assertThat(response.success()).isTrue();
        assertThat(response.result()).isEqualTo("30");
    }

    @Test
    @DisplayName("Should check skill availability")
    void shouldCheckSkillAvailability() {
        assertThat(dispatcher.isSkillAvailable("TEST")).isTrue();
        assertThat(dispatcher.isSkillAvailable("NONEXISTENT")).isFalse();

        testSkill.setAvailable(false);
        assertThat(dispatcher.isSkillAvailable("TEST")).isFalse();
    }

    @Test
    @DisplayName("Should get active execution count")
    void shouldGetActiveExecutionCount() {
        assertThat(dispatcher.getActiveExecutions()).isZero();
    }

    @Test
    @DisplayName("Should throw on null request")
    void shouldThrowOnNullRequest() {
        assertThatThrownBy(() -> dispatcher.executeSingle(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should throw on null batch")
    void shouldThrowOnNullBatch() {
        assertThatThrownBy(() -> dispatcher.executeBatch(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should include request ID in response")
    void shouldIncludeRequestIdInResponse() {
        ToolRequest request = ToolRequest.of("TEST", "echo", Map.of("message", "test"));

        ToolResponse response = dispatcher.executeSingle(request);

        assertThat(response.requestId()).isEqualTo(request.requestId());
    }

    @Test
    @DisplayName("Should handle mixed success and failure in batch")
    void shouldHandleMixedSuccessAndFailureInBatch() {
        List<ToolRequest> requests = List.of(
            ToolRequest.of("TEST", "echo", Map.of("message", "Good")),
            ToolRequest.of("TEST", "fail", Map.of()),
            ToolRequest.of("TEST", "echo", Map.of("message", "Also Good"))
        );

        List<ToolResponse> responses = dispatcher.executeBatch(requests);

        assertThat(responses).hasSize(3);
        assertThat(responses.get(0).success()).isTrue();
        assertThat(responses.get(1).success()).isFalse();
        assertThat(responses.get(2).success()).isTrue();
    }
}
