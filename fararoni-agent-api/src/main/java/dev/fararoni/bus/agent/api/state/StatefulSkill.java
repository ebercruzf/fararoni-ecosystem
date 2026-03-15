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
package dev.fararoni.bus.agent.api.state;

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.ToolSkill;

import java.util.List;

/**
 * Interface for skills that maintain persistent sessions/connections.
 *
 * <p>Implement this interface when your skill needs to maintain state across
 * multiple calls (database connections, SSH sessions, authenticated APIs).
 * This is a key differentiator vs MCP which is stateless.</p>
 *
 * <h2>Why Stateful?</h2>
 * <p>MCP reconnects on every call which causes:</p>
 * <ul>
 *   <li>100 DB queries = 100 TCP handshakes + 100 authentications</li>
 *   <li>SSH commands require new connection each time</li>
 *   <li>API rate limits hit faster due to repeated auth</li>
 * </ul>
 *
 * <p>FNL maintains sessions:</p>
 * <ul>
 *   <li>100 DB queries = 1 connection, reused 100 times</li>
 *   <li>SSH session stays open for multiple commands</li>
 *   <li>API token cached and reused</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public class DatabaseSkillImpl implements DatabaseSkill, StatefulSkill {
 *
 *     private final Map<String, Connection> connections = new ConcurrentHashMap<>();
 *
 *     @Override
 *     public FNLResult<SessionHandle> openSession(String connectionString) {
 *         try {
 *             Connection conn = DriverManager.getConnection(connectionString);
 *             String sessionId = UUID.randomUUID().toString();
 *             connections.put(sessionId, conn);
 *
 *             return FNLResult.success(SessionHandle.withMetadata(sessionId,
 *                 Map.of("url", connectionString, "catalog", conn.getCatalog())));
 *         } catch (SQLException e) {
 *             return FNLResult.failure("Connection failed: " + e.getMessage());
 *         }
 *     }
 *
 *     @Override
 *     public FNLResult<Void> closeSession(String sessionId) {
 *         Connection conn = connections.remove(sessionId);
 *         if (conn != null) {
 *             conn.close();
 *             return FNLResult.success(null);
 *         }
 *         return FNLResult.failure("Session not found: " + sessionId);
 *     }
 *
 *     // Use session for queries
 *     @AgentAction(name = "query", description = "Execute SQL query")
 *     public FNLResult<ResultSet> query(String sessionId, String sql) {
 *         Connection conn = connections.get(sessionId);
 *         if (conn == null) {
 *             return FNLResult.failure("Invalid session");
 *         }
 *         // Execute query using existing connection (fast!)
 *         return FNLResult.success(conn.createStatement().executeQuery(sql));
 *     }
 * }
 * }</pre>
 *
 * <h2>Performance Comparison</h2>
 * <table border="1">
 *   <caption>Stateless vs Stateful performance comparison</caption>
 *   <tr><th>Operation</th><th>MCP (Stateless)</th><th>FNL (Stateful)</th></tr>
 *   <tr><td>100 DB queries</td><td>~200s (reconnect each)</td><td>~2s (1 connection)</td></tr>
 *   <tr><td>50 SSH commands</td><td>~100s (new session)</td><td>~5s (persistent)</td></tr>
 *   <tr><td>API burst (100 calls)</td><td>Rate limited</td><td>Token reused</td></tr>
 * </table>
 *
 * <h2>Session Lifecycle</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │  openSession() ──► SessionHandle ──► use session ──► close │
 * │       │                                    │                │
 * │       │         ping() keeps alive         │                │
 * │       │         ◄──────────────────────────┤                │
 * │       │                                    │                │
 * │       └────── timeout auto-closes ─────────┘                │
 * └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see SessionHandle
 */
public interface StatefulSkill extends ToolSkill {

    /**
     * Opens a new session/connection.
     *
     * <p>The returned {@link SessionHandle} contains the session ID and metadata.
     * Use this ID for subsequent operations that require the session.</p>
     *
     * @param connectionString connection parameters (URL, credentials, options)
     * @return result containing the session handle
     */
    @AgentAction(
        name = "open_session",
        description = "Opens a persistent session/connection for efficient multi-step operations"
    )
    FNLResult<SessionHandle> openSession(String connectionString);

    /**
     * Closes an existing session.
     *
     * <p>Releases all resources associated with the session. After closing,
     * the session ID is no longer valid.</p>
     *
     * @param sessionId the session to close
     * @return result indicating success or failure
     */
    @AgentAction(
        name = "close_session",
        description = "Closes a session and releases associated resources"
    )
    FNLResult<Void> closeSession(String sessionId);

    /**
     * Keeps a session alive (heartbeat).
     *
     * <p>Call this periodically for long-running operations to prevent
     * automatic timeout. Updates the session's lastActiveAt timestamp.</p>
     *
     * @param sessionId the session to keep alive
     * @return result with updated session handle
     */
    @AgentAction(
        name = "ping_session",
        description = "Sends heartbeat to keep session alive"
    )
    default FNLResult<SessionHandle> pingSession(String sessionId) {
        // Default implementation returns failure - override in implementations
        return FNLResult.failure("Ping not implemented for this skill");
    }

    /**
     * Lists all active sessions for this skill.
     *
     * <p>Useful for debugging and resource management.</p>
     *
     * @return list of active session handles
     */
    default FNLResult<List<SessionHandle>> listSessions() {
        return FNLResult.success(List.of());
    }

    /**
     * Checks if a session is still valid.
     *
     * @param sessionId the session to check
     * @return true if session exists and hasn't expired
     */
    default boolean isSessionValid(String sessionId) {
        return false;
    }

    /**
     * Gets the maximum session idle time before auto-close.
     *
     * @return idle timeout in milliseconds, or -1 for no timeout
     */
    default long getSessionTimeoutMs() {
        return 300_000; // 5 minutes default
    }

    /**
     * Gets the maximum number of concurrent sessions allowed.
     *
     * @return max sessions, or -1 for unlimited
     */
    default int getMaxConcurrentSessions() {
        return 10;
    }

    /**
     * Closes all active sessions.
     *
     * <p>Called during shutdown or cleanup. Implementations should
     * close all sessions gracefully.</p>
     *
     * @return number of sessions closed
     */
    default FNLResult<Integer> closeAllSessions() {
        return FNLResult.success(0);
    }
}
