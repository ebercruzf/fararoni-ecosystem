/*
 * Copyright (c) 2026 Eber Cruz Fararoni. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * FNL - Fararoni Native Link v2.0
 */

/**
 * Security annotations for FNL Enterprise capabilities.
 *
 * <p>This package provides declarative security annotations that the FNL
 * runtime uses to enforce access control, audit logging, and rate limiting
 * BEFORE method invocation.</p>
 *
 * <h2>Annotations</h2>
 * <ul>
 *   <li>{@link dev.fararoni.bus.agent.api.security.RequiresRole} - RBAC access control</li>
 *   <li>{@link dev.fararoni.bus.agent.api.security.AuditLog} - Forensic audit logging</li>
 *   <li>{@link dev.fararoni.bus.agent.api.security.RateLimit} - DoS protection</li>
 * </ul>
 *
 * <h2>Why FNL Security is Superior to MCP</h2>
 * <table border="1">
 *   <caption>Security features comparison</caption>
 *   <tr><th>Feature</th><th>MCP</th><th>FNL</th></tr>
 *   <tr><td>Access Control</td><td>None</td><td>@RequiresRole</td></tr>
 *   <tr><td>Audit Trail</td><td>Manual</td><td>@AuditLog</td></tr>
 *   <tr><td>Rate Limiting</td><td>None</td><td>@RateLimit</td></tr>
 *   <tr><td>Input Validation</td><td>None</td><td>@Sanitized</td></tr>
 * </table>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 */
package dev.fararoni.bus.agent.api.security;
