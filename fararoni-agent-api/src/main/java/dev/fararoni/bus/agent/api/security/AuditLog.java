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

/**
 * Audit logging annotation for FNL tools.
 *
 * <p>Marks a method as a critical operation that must be recorded in an
 * immutable audit log. Essential for Banks, Fintech, and compliance-heavy
 * industries.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @AgentAction(name = "transfer_funds", description = "Transfers money between accounts")
 * @AuditLog(severity = "CRITICAL", captureInputs = true)
 * @RequiresRole("BANKER")
 * public FNLResult<TransferReceipt> transferFunds(String from, String to, BigDecimal amount) {
 *     // This operation will be fully logged with inputs
 * }
 * }</pre>
 *
 * <h2>Log Entry Format</h2>
 * <pre>
 * [AUDIT] 2026-01-10T10:30:00Z | CRITICAL | transfer_funds | user=john |
 *         inputs={from=ACC001, to=ACC002, amount=1000.00} | result=SUCCESS
 * </pre>
 *
 * <h2>Enterprise Value</h2>
 * <ul>
 *   <li>Forensic traceability for regulatory compliance</li>
 *   <li>Immutable record of AI agent actions</li>
 *   <li>SOX, PCI-DSS, GDPR audit requirements</li>
 * </ul>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see RequiresRole
 * @see RateLimit
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditLog {

    /**
     * Severity level for the audit entry.
     *
     * <p>Suggested values: "INFO", "WARN", "CRITICAL"</p>
     *
     * @return severity level string
     */
    String severity() default "INFO";

    /**
     * Whether to capture and log the method input parameters.
     *
     * <p>Set to false for methods with sensitive data (passwords, tokens)
     * that should not be logged.</p>
     *
     * @return true to capture inputs, false to omit
     */
    boolean captureInputs() default true;

    /**
     * Whether to capture and log the method return value.
     *
     * @return true to capture output, false to omit
     */
    boolean captureOutput() default false;

    /**
     * Optional category for grouping audit entries.
     *
     * <p>Example: "FINANCIAL", "USER_MANAGEMENT", "DATA_ACCESS"</p>
     *
     * @return category string
     */
    String category() default "GENERAL";
}
