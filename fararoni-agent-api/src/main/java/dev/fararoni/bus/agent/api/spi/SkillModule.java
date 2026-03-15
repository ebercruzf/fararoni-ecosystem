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
package dev.fararoni.bus.agent.api.spi;

import dev.fararoni.bus.agent.api.ToolSkill;

import java.util.List;

/**
 * Service Provider Interface (SPI) for modules that provide Skills.
 *
 * <p>This interface enables a polymorphic plugin architecture where:</p>
 * <ul>
 *   <li>Core module provides basic skills (FileSystem, Terminal, Git)</li>
 *   <li>Enterprise module provides advanced skills (RAG, Analysis, Database)</li>
 *   <li>Third-party modules can add custom skills</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <pre>
 * +------------------+     +------------------+     +------------------+
 * |  fararoni-core   |     | fararoni-enter.  |     |  third-party     |
 * +------------------+     +------------------+     +------------------+
 * | CoreSkillModule  |     | EnterpriseSkill  |     | CustomSkillModule|
 * | priority=10      |     | Module           |     | priority=5       |
 * |                  |     | priority=100     |     |                  |
 * +--------+---------+     +--------+---------+     +--------+---------+
 *          |                        |                        |
 *          +------------------------+------------------------+
 *                                   |
 *                                   v
 *                     +---------------------------+
 *                     |     ServiceLoader         |
 *                     | load(SkillModule.class)   |
 *                     +---------------------------+
 *                                   |
 *                                   v
 *                     +---------------------------+
 *                     |     SkillRegistry         |
 *                     | (ordered by priority)     |
 *                     +---------------------------+
 * </pre>
 *
 * <h2>Registration</h2>
 * <p>To register a module, create:</p>
 * <pre>
 * META-INF/services/dev.fararoni.bus.agent.api.spi.SkillModule
 * </pre>
 * <p>With content:</p>
 * <pre>
 * com.yourcompany.YourSkillModule
 * </pre>
 *
 * <h2>Priority System</h2>
 * <p>Modules with higher priority are loaded first and their skills
 * take precedence in case of name conflicts:</p>
 * <ul>
 *   <li>0-10: Third-party plugins</li>
 *   <li>10-50: Core modules</li>
 *   <li>100: Enterprise modules</li>
 * </ul>
 *
 * <h2>GraalVM Native Image</h2>
 * <p>SPI is natively supported by GraalVM. Ensure resource-config.json includes:</p>
 * <pre>
 * {"pattern": "\\QMETA-INF/services/dev.fararoni.bus.agent.api.spi.SkillModule\\E"}
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * public class MySkillModule implements SkillModule {
 *
 *     @Override
 *     public List<ToolSkill> provideSkills() {
 *         return List.of(
 *             new MyCustomSkill(),
 *             new AnotherSkill()
 *         );
 *     }
 *
 *     @Override
 *     public String getModuleName() {
 *         return "My Custom Skills";
 *     }
 *
 *     @Override
 *     public String getVersion() {
 *         return "1.0.0";
 *     }
 *
 *     @Override
 *     public int priority() {
 *         return 5; // Low priority
 *     }
 * }
 * }</pre>
 *
 * @author Eber Cruz
 * @since 1.0.0 (Phase 9)
 * @see java.util.ServiceLoader
 */
public interface SkillModule {

    /**
     * Provides the list of skills that this module contributes.
     *
     * <p>Skills are instantiated and ready to use. The module is responsible
     * for any initialization required before returning the skills.</p>
     *
     * <p>This method is called after {@link #initialize()}.</p>
     *
     * @return list of ToolSkill instances (not null, may be empty)
     */
    List<ToolSkill> provideSkills();

    /**
     * Returns the descriptive name of this module.
     *
     * <p>This name is used in logging and diagnostics.</p>
     *
     * @return module name (e.g., "FARARONI Core", "FARARONI Enterprise")
     */
    String getModuleName();

    /**
     * Returns the version of this module.
     *
     * <p>Recommended format: Semantic Versioning (MAJOR.MINOR.PATCH)</p>
     *
     * @return version string (e.g., "2.0.0", "1.5.3-beta")
     */
    String getVersion();

    /**
     * Returns the loading priority of this module.
     *
     * <p>Modules with higher priority are loaded first. When multiple modules
     * provide skills with the same name, the higher priority module wins.</p>
     *
     * <p>Recommended values:</p>
     * <ul>
     *   <li>0-10: Third-party plugins</li>
     *   <li>10-50: Core modules</li>
     *   <li>100: Enterprise modules</li>
     * </ul>
     *
     * @return priority value (higher = more priority)
     */
    default int priority() {
        return 0;
    }

    /**
     * Initializes the module before skills are requested.
     *
     * <p>This method is called once before {@link #provideSkills()}.
     * Use it to:</p>
     * <ul>
     *   <li>Validate required dependencies</li>
     *   <li>Load configuration</li>
     *   <li>Initialize shared resources</li>
     *   <li>Register hooks or listeners</li>
     * </ul>
     *
     * <p>If initialization fails, throw an exception to prevent
     * the module from being loaded.</p>
     *
     * @throws IllegalStateException if initialization fails
     */
    default void initialize() {
        // Default: no-op
    }

    /**
     * Indicates whether this module is enabled.
     *
     * <p>Disabled modules are skipped during loading. Use this for:</p>
     * <ul>
     *   <li>Environment-based feature flags</li>
     *   <li>License validation</li>
     *   <li>Configuration-based enabling</li>
     * </ul>
     *
     * @return true if the module should be loaded, false to skip
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Returns an optional description of this module.
     *
     * <p>Shown in help messages and system information.</p>
     *
     * @return description or null
     */
    default String getDescription() {
        return null;
    }

    /**
     * Shuts down the module and releases resources.
     *
     * <p>Called when the application is shutting down. Use this to:</p>
     * <ul>
     *   <li>Close connections</li>
     *   <li>Release file handles</li>
     *   <li>Stop background threads</li>
     * </ul>
     */
    default void shutdown() {
        // Default: no-op
    }
}
