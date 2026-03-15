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
package dev.fararoni.bus.spi;

/**
 * Service Provider Interface (SPI) for Fararoni infrastructure modules.
 *
 * <p>This interface enables a plugin architecture for infrastructure components
 * that extend Fararoni's capabilities without modifying the core.</p>
 *
 * <h2>Difference from SkillModule</h2>
 * <table border="1">
 *   <tr><th>Aspect</th><th>SkillModule</th><th>FararoniModule</th></tr>
 *   <tr><td>Purpose</td><td>Provide tools to agents</td><td>Infrastructure services</td></tr>
 *   <tr><td>Examples</td><td>fs_write, git, db_query</td><td>REST Gateway, Voice, Vision</td></tr>
 *   <tr><td>Lifecycle</td><td>Stateless tools</td><td>Long-running services</td></tr>
 *   <tr><td>Network</td><td>No ports</td><td>May open ports</td></tr>
 * </table>
 *
 * <h2>Architecture</h2>
 * <pre>
 * +------------------------+     +------------------------+
 * | fararoni-gateway-rest  |     | fararoni-extension-*   |
 * +------------------------+     +------------------------+
 * | OmniChannelGateway     |     | VoiceExtension         |
 * | Module                 |     | Module                 |
 * | priority=50            |     | priority=40            |
 * +----------+-------------+     +----------+-------------+
 *            |                              |
 *            +------------------------------+
 *                           |
 *                           v
 *            +------------------------------+
 *            |       ServiceLoader          |
 *            | load(FararoniModule.class)   |
 *            +------------------------------+
 *                           |
 *                           v
 *            +------------------------------+
 *            |       ModuleRegistry         |
 *            | (ordered by priority)        |
 *            +------------------------------+
 * </pre>
 *
 * <h2>Registration</h2>
 * <p>To register a module, create:</p>
 * <pre>
 * META-INF/services/dev.fararoni.bus.spi.FararoniModule
 * </pre>
 * <p>With content:</p>
 * <pre>
 * dev.fararoni.bus.gateway.rest.OmniChannelGatewayModule
 * </pre>
 *
 * <h2>Priority System</h2>
 * <ul>
 *   <li>0-20: Third-party plugins</li>
 *   <li>30-50: Core infrastructure (Gateway, Voice)</li>
 *   <li>60-80: Enterprise infrastructure</li>
 *   <li>90-100: Critical system services</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <pre>
 * 1. ServiceLoader discovers module
 * 2. ModuleRegistry calls prerequisitesMet() - skip if false
 * 3. ModuleRegistry calls initialize(context) - inject dependencies
 * 4. ModuleRegistry calls start() - begin accepting work
 * 5. ... module runs ...
 * 6. ModuleRegistry calls stop() - graceful shutdown
 * </pre>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see ModuleContext
 * @see ModuleHealth
 */
public interface FararoniModule {

    /**
     * Returns the unique identifier for this module.
     *
     * <p>Convention: lowercase-kebab-case, e.g., "gateway-rest-omnichannel"</p>
     *
     * <p>This ID is used for:</p>
     * <ul>
     *   <li>Logging and diagnostics</li>
     *   <li>Configuration keys in modules.yml</li>
     *   <li>Health check endpoints</li>
     * </ul>
     *
     * @return unique module identifier (not null, not empty)
     */
    String getModuleId();

    /**
     * Returns the loading priority of this module.
     *
     * <p>Modules with higher priority are loaded first. This matters when
     * modules have dependencies on each other.</p>
     *
     * <p>Recommended values:</p>
     * <ul>
     *   <li>0-20: Third-party plugins</li>
     *   <li>30-50: Core infrastructure (Gateway: 50, Voice: 40)</li>
     *   <li>60-80: Enterprise infrastructure</li>
     *   <li>90-100: Critical system services</li>
     * </ul>
     *
     * @return priority value (higher = loaded first)
     */
    default int priority() {
        return 50;
    }

    /**
     * Checks if all prerequisites for this module are met.
     *
     * <p>Called before initialize(). If this returns false, the module
     * is skipped silently (no error). Use this for:</p>
     * <ul>
     *   <li>Checking required configuration exists</li>
     *   <li>Validating port availability</li>
     *   <li>Verifying native libraries are present</li>
     * </ul>
     *
     * @param context module context with configuration access
     * @return true if module can be initialized, false to skip
     */
    default boolean prerequisitesMet(ModuleContext context) {
        return true;
    }

    /**
     * Initializes the module with its context.
     *
     * <p>Called once before start(). Use this to:</p>
     * <ul>
     *   <li>Store reference to SovereignEventBus</li>
     *   <li>Load configuration from modules.yml</li>
     *   <li>Create internal components (but don't start them)</li>
     * </ul>
     *
     * <p>If initialization fails, throw an exception to prevent
     * the module from being started.</p>
     *
     * @param context provides bus, config, and other dependencies
     * @throws IllegalStateException if initialization fails
     */
    void initialize(ModuleContext context);

    /**
     * Starts the module.
     *
     * <p>Called after initialize(). This is where the module should:</p>
     * <ul>
     *   <li>Open network ports</li>
     *   <li>Subscribe to bus topics</li>
     *   <li>Start background threads</li>
     *   <li>Begin accepting work</li>
     * </ul>
     *
     * <p>This method should return quickly. Long-running work should
     * be done in separate threads.</p>
     */
    void start();

    /**
     * Stops the module gracefully.
     *
     * <p>Called during shutdown. The module should:</p>
     * <ul>
     *   <li>Stop accepting new work</li>
     *   <li>Complete in-flight requests (with timeout)</li>
     *   <li>Close network ports</li>
     *   <li>Release resources</li>
     * </ul>
     *
     * <p>This method should complete within a reasonable time (e.g., 5 seconds).
     * The system may force-kill modules that take too long.</p>
     */
    void stop();

    /**
     * Returns the current health status of this module.
     *
     * <p>Called periodically by health check endpoints. Should be fast
     * and non-blocking.</p>
     *
     * @return current health status
     */
    default ModuleHealth getHealth() {
        return ModuleHealth.UNKNOWN;
    }

    /**
     * Indicates whether this module is enabled.
     *
     * <p>Disabled modules are skipped during loading. Use this for:</p>
     * <ul>
     *   <li>Feature flags</li>
     *   <li>License validation</li>
     *   <li>Environment-based enabling (dev vs prod)</li>
     * </ul>
     *
     * @return true if the module should be loaded, false to skip
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Returns a human-readable description of this module.
     *
     * <p>Shown in logs and system information.</p>
     *
     * @return description or null
     */
    default String getDescription() {
        return null;
    }
}
