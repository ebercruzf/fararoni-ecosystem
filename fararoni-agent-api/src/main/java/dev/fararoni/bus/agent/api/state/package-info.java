/*
 * Copyright (c) 2026 Eber Cruz Fararoni. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * FNL - Fararoni Native Link v2.0
 * Stateful Session Management Package
 */

/**
 * Stateful session management for FNL skills.
 *
 * <p>This package provides the contracts for maintaining persistent
 * sessions/connections across multiple AI agent calls. Unlike MCP
 * which is stateless, FNL supports:</p>
 *
 * <ul>
 *   <li>Persistent database connections</li>
 *   <li>SSH session reuse</li>
 *   <li>API token caching</li>
 *   <li>WebSocket connections</li>
 * </ul>
 *
 * <h2>Key Components</h2>
 * <dl>
 *   <dt>{@link dev.fararoni.bus.agent.api.state.StatefulSkill}</dt>
 *   <dd>Interface for skills that maintain sessions</dd>
 *   <dt>{@link dev.fararoni.bus.agent.api.state.SessionHandle}</dt>
 *   <dd>Token representing an active session</dd>
 * </dl>
 *
 * <h2>Performance Impact</h2>
 * <pre>
 * Operation          | MCP (Stateless)  | FNL (Stateful)
 * -------------------|------------------|----------------
 * 100 DB queries     | ~200s            | ~2s
 * 50 SSH commands    | ~100s            | ~5s
 * API burst (100)    | Rate limited     | Success
 * </pre>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 */
package dev.fararoni.bus.agent.api.state;
