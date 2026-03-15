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
package dev.fararoni.bus.agent.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Input sanitization annotation for FNL tool parameters.
 *
 * <p>Marks a parameter that MUST be sanitized by the FNL runtime before
 * the method is invoked. This provides declarative security against
 * injection attacks (shell, SQL, path traversal, XSS).</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @AgentAction(name = "execute_command", description = "Executes a shell command")
 * public FNLResult<String> executeCommand(
 *     @Sanitized(strategy = SanitizationStrategy.SHELL) String command
 * ) {
 *     // 'command' is guaranteed to be safe (no ; && | ` $() etc.)
 * }
 *
 * @AgentAction(name = "read_file", description = "Reads a file")
 * public FNLResult<String> readFile(
 *     @Sanitized(strategy = SanitizationStrategy.PATH) String path
 * ) {
 *     // 'path' is guaranteed to have no ../ or absolute paths outside workspace
 * }
 * }</pre>
 *
 * <h2>Sanitization Strategies</h2>
 * <ul>
 *   <li><b>DEFAULT</b>: Basic character escaping</li>
 *   <li><b>SHELL</b>: Removes shell metacharacters (; &amp;&amp; | ` $() etc.)</li>
 *   <li><b>SQL</b>: Escapes SQL injection patterns</li>
 *   <li><b>PATH</b>: Prevents path traversal (../) and absolute paths</li>
 *   <li><b>HTML</b>: Escapes HTML/XSS characters</li>
 *   <li><b>NONE</b>: No sanitization (use with caution)</li>
 * </ul>
 *
 * <h2>Enterprise Value</h2>
 * <ul>
 *   <li>Declarative security in the contract, not implementation</li>
 *   <li>Impossible to forget sanitization - compiler enforces it</li>
 *   <li>Defense against prompt injection attacks</li>
 * </ul>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see SanitizationStrategy
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Sanitized {

    /**
     * The sanitization strategy to apply.
     *
     * @return strategy enum value
     */
    SanitizationStrategy strategy() default SanitizationStrategy.DEFAULT;

    /**
     * Maximum allowed length for the input.
     * Values longer than this are truncated.
     * Default: 10000 characters.
     *
     * @return max length
     */
    int maxLength() default 10000;

    /**
     * Whether to trim whitespace from input.
     *
     * @return true to trim
     */
    boolean trim() default true;

    /**
     * Whether to reject (throw error) or clean (remove bad chars) on violation.
     *
     * @return true to reject with error, false to clean silently
     */
    boolean rejectOnViolation() default false;

    /**
     * Sanitization strategies available in FNL.
     */
    enum SanitizationStrategy {
        /**
         * Basic sanitization: trim, normalize whitespace, remove control chars.
         */
        DEFAULT,

        /**
         * Shell command sanitization: removes ; &amp;&amp; || | ` $() {} etc.
         * Use for any input that will be passed to shell execution.
         */
        SHELL,

        /**
         * SQL sanitization: escapes ' " ; -- /* etc.
         * Use for any input that will be used in SQL queries.
         */
        SQL,

        /**
         * Path sanitization: removes ../ and validates against workspace.
         * Use for any input that represents a file path.
         */
        PATH,

        /**
         * HTML/XSS sanitization: escapes &lt; &gt; &amp; " ' etc.
         * Use for any input that will be rendered in HTML.
         */
        HTML,

        /**
         * No sanitization. Use only when you have custom validation.
         */
        NONE
    }
}
