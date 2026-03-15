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
package dev.fararoni.bus.agent.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting annotation for FNL tools.
 *
 * <p>Prevents accidental Denial-of-Service (DoS) from an AI agent in an
 * infinite loop or malicious prompt injection. Protects both the system
 * and the budget (API costs).</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @AgentAction(name = "call_external_api", description = "Calls expensive external API")
 * @RateLimit(calls = 10, period = 1, unit = TimeUnit.MINUTES)
 * public FNLResult<String> callExternalApi(String query) {
 *     // Max 10 calls per minute
 * }
 * }</pre>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>When limit is exceeded, method returns error without executing</li>
 *   <li>Counter resets after the period expires</li>
 *   <li>Rate limiting is per-method, not per-skill</li>
 * </ul>
 *
 * <h2>Enterprise Value</h2>
 * <ul>
 *   <li>Prevents runaway AI agents from exhausting resources</li>
 *   <li>Protects against surprise AWS/OpenAI bills</li>
 *   <li>Defense-in-depth against prompt injection attacks</li>
 * </ul>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see RequiresRole
 * @see AuditLog
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimit {

    /**
     * Maximum number of calls allowed within the period.
     *
     * @return max calls
     */
    int calls();

    /**
     * The duration of the rate limit window.
     *
     * @return period duration
     */
    long period();

    /**
     * Time unit for the period.
     *
     * @return time unit (SECONDS, MINUTES, HOURS, etc.)
     */
    TimeUnit unit();

    /**
     * Strategy when limit is exceeded.
     *
     * <ul>
     *   <li>REJECT: Immediately return error (default)</li>
     *   <li>QUEUE: Queue the request for later execution</li>
     *   <li>THROTTLE: Slow down execution with delays</li>
     * </ul>
     *
     * @return overflow strategy
     */
    Strategy strategy() default Strategy.REJECT;

    /**
     * Optional message when rate limit is exceeded.
     *
     * @return custom error message
     */
    String message() default "Rate limit exceeded. Please try again later.";

    /**
     * Rate limit overflow strategies.
     */
    enum Strategy {
        /** Reject the request immediately with an error */
        REJECT,
        /** Queue the request for later execution */
        QUEUE,
        /** Slow down execution with artificial delays */
        THROTTLE
    }
}
