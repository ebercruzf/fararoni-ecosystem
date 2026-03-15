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
package dev.fararoni.core.core.agent.model;

import java.util.List;
import java.util.Map;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record AgentInstanceConfig(
    String id,
    String templateRef,
    WiringConfig wiring,
    RoutingConfig routing,
    Map<String, String> variables
) {
    public AgentInstanceConfig {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id no puede ser null o vacio");
        }
        if (templateRef == null || templateRef.isBlank()) {
            throw new IllegalArgumentException("templateRef no puede ser null o vacio");
        }
        if (wiring == null) {
            wiring = WiringConfig.defaults();
        }
        if (routing == null) {
            routing = RoutingConfig.defaults();
        }
        variables = variables != null ? Map.copyOf(variables) : Map.of();
    }

    public record WiringConfig(
        List<String> inputTopics,
        String outputTopic,
        String deadLetterTopic
    ) {
        public WiringConfig {
            inputTopics = inputTopics != null ? List.copyOf(inputTopics) : List.of();
            if (outputTopic == null || outputTopic.isBlank()) {
                outputTopic = "sys.output.default";
            }
            if (deadLetterTopic == null || deadLetterTopic.isBlank()) {
                deadLetterTopic = "sys.dlq.main";
            }
        }

        public static WiringConfig defaults() {
            return new WiringConfig(List.of(), "sys.output.default", "sys.dlq.main");
        }

        public static WiringConfig singleInput(String inputTopic, String outputTopic) {
            return new WiringConfig(List.of(inputTopic), outputTopic, "sys.dlq.main");
        }
    }

    public record RoutingConfig(
        int priority,
        int maxConcurrent,
        long timeoutMs
    ) {
        public static final int DEFAULT_PRIORITY = 50;
        public static final int DEFAULT_MAX_CONCURRENT = 3;
        public static final long DEFAULT_TIMEOUT_MS = 30_000;

        public RoutingConfig {
            if (priority < 0) priority = DEFAULT_PRIORITY;
            if (maxConcurrent < 1) maxConcurrent = DEFAULT_MAX_CONCURRENT;
            if (timeoutMs < 1000) timeoutMs = DEFAULT_TIMEOUT_MS;
        }

        public static RoutingConfig defaults() {
            return new RoutingConfig(DEFAULT_PRIORITY, DEFAULT_MAX_CONCURRENT, DEFAULT_TIMEOUT_MS);
        }

        public static RoutingConfig highPriority() {
            return new RoutingConfig(100, DEFAULT_MAX_CONCURRENT, DEFAULT_TIMEOUT_MS);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String templateRef;
        private WiringConfig wiring;
        private RoutingConfig routing;
        private Map<String, String> variables = Map.of();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder templateRef(String templateRef) {
            this.templateRef = templateRef;
            return this;
        }

        public Builder wiring(WiringConfig wiring) {
            this.wiring = wiring;
            return this;
        }

        public Builder routing(RoutingConfig routing) {
            this.routing = routing;
            return this;
        }

        public Builder variables(Map<String, String> variables) {
            this.variables = variables;
            return this;
        }

        public AgentInstanceConfig build() {
            return new AgentInstanceConfig(id, templateRef, wiring, routing, variables);
        }
    }
}
