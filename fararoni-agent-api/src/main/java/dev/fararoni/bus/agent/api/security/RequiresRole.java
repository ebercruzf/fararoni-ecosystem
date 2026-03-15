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
 * Role-Based Access Control (RBAC) annotation for FNL tools.
 *
 * <p>Marks a method that requires specific roles to execute. The FNL runtime
 * will verify roles BEFORE invoking the method, providing a security firewall
 * that MCP (Model Context Protocol) does not have.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @AgentAction(name = "delete_user", description = "Deletes a user from the system")
 * @RequiresRole({"ADMIN", "DBA"})
 * public FNLResult<Void> deleteUser(String userId) {
 *     // Only executed if caller has ADMIN or DBA role
 * }
 * }</pre>
 *
 * <h2>Enterprise Value</h2>
 * <ul>
 *   <li>Banks cannot trust AI to "decide" not to delete users</li>
 *   <li>This provides a hard firewall in code</li>
 *   <li>Roles are verified at runtime before method invocation</li>
 * </ul>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see AuditLog
 * @see RateLimit
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresRole {

    /**
     * The roles required to execute this method.
     *
     * <p>Example: {"ADMIN", "DBA", "AUDITOR"}</p>
     *
     * @return array of role names
     */
    String[] value();

    /**
     * If true, the caller needs ANY of the roles (OR logic).
     * If false (default), the caller needs ALL roles (AND logic).
     *
     * @return true for OR logic, false for AND logic
     */
    boolean any() default true;

    /**
     * Optional message to return when access is denied.
     *
     * @return custom denial message
     */
    String message() default "Access denied: insufficient roles";
}
