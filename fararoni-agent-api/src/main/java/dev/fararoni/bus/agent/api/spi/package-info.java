/*
 * Copyright (c) 2026 Eber Cruz Fararoni. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * FNL - Fararoni Native Link v2.0
 */

/**
 * Service Provider Interface (SPI) contracts for FNL plugin architecture.
 *
 * <p>This package defines the contracts that enable modular, polymorphic
 * skill loading using Java's {@link java.util.ServiceLoader} mechanism.</p>
 *
 * <h2>Key Interface</h2>
 * <ul>
 *   <li>{@link dev.fararoni.bus.agent.api.spi.SkillModule} - Contract for skill providers</li>
 * </ul>
 *
 * <h2>Architecture Overview</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    POLYMORPHIC SKILL LOADING                    │
 * ├─────────────────────────────────────────────────────────────────┤
 * │                                                                 │
 * │  1. Application starts                                          │
 * │           │                                                     │
 * │           v                                                     │
 * │  2. ServiceLoader.load(SkillModule.class)                       │
 * │           │                                                     │
 * │           v                                                     │
 * │  3. Scan META-INF/services/ in all JARs                         │
 * │           │                                                     │
 * │     ┌─────┴─────────────────────┐                               │
 * │     │                           │                               │
 * │     v                           v                               │
 * │  ┌──────────────┐    ┌────────────────────┐                     │
 * │  │ CoreSkill    │    │ EnterpriseSkill    │                     │
 * │  │ Module       │    │ Module             │                     │
 * │  │ priority=10  │    │ priority=100       │                     │
 * │  └──────────────┘    └────────────────────┘                     │
 * │           │                    │                                │
 * │           v                    v                                │
 * │  4. Sort by priority (descending)                               │
 * │           │                                                     │
 * │           v                                                     │
 * │  5. Call initialize() on each module                            │
 * │           │                                                     │
 * │           v                                                     │
 * │  6. Call provideSkills() and register each skill                │
 * │           │                                                     │
 * │           v                                                     │
 * │  7. Skills ready for agent use                                  │
 * │                                                                 │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Registration</h2>
 * <p>To register a SkillModule implementation:</p>
 * <ol>
 *   <li>Create a class implementing {@link dev.fararoni.bus.agent.api.spi.SkillModule}</li>
 *   <li>Create file: {@code META-INF/services/dev.fararoni.bus.agent.api.spi.SkillModule}</li>
 *   <li>Add fully qualified class name to the file</li>
 * </ol>
 *
 * <h2>GraalVM Native Image</h2>
 * <p>For native compilation, add to resource-config.json:</p>
 * <pre>
 * {
 *   "resources": {
 *     "includes": [{
 *       "pattern": "\\QMETA-INF/services/dev.fararoni.bus.agent.api.spi.SkillModule\\E"
 *     }]
 *   }
 * }
 * </pre>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contracts - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0 (Phase 9)
 * @see dev.fararoni.bus.agent.api.spi.SkillModule
 * @see java.util.ServiceLoader
 */
package dev.fararoni.bus.agent.api.spi;
