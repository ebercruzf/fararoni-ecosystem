/*
 * Copyright (c) 2026 Eber Cruz Fararoni. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * FNL - Fararoni Native Link v2.0
 * Domain Skill Contracts
 */

/**
 * Domain-specific skill contracts for FNL.
 *
 * <p>This package contains interfaces defining the contracts for specific
 * capabilities that AI agents can use. Each skill represents a cohesive
 * domain of functionality.</p>
 *
 * <h2>Available Skills</h2>
 * <table border="1">
 *   <caption>Skills available in FNL</caption>
 *   <tr>
 *     <th>Skill</th>
 *     <th>Description</th>
 *     <th>Capabilities</th>
 *   </tr>
 *   <tr>
 *     <td>{@link dev.fararoni.bus.agent.api.skills.FileSystemSkill}</td>
 *     <td>File operations</td>
 *     <td>Saga + Streaming</td>
 *   </tr>
 *   <tr>
 *     <td>{@link dev.fararoni.bus.agent.api.skills.TerminalSkill}</td>
 *     <td>Shell execution</td>
 *     <td>Sandboxed commands</td>
 *   </tr>
 *   <tr>
 *     <td>{@link dev.fararoni.bus.agent.api.skills.GitSkill}</td>
 *     <td>Version control</td>
 *     <td>Saga compensation</td>
 *   </tr>
 *   <tr>
 *     <td>{@link dev.fararoni.bus.agent.api.skills.ProjectAnalysisSkill}</td>
 *     <td>Code analysis</td>
 *     <td>Symbols, metrics</td>
 *   </tr>
 *   <tr>
 *     <td>{@link dev.fararoni.bus.agent.api.skills.KnowledgeSourceSkill}</td>
 *     <td>RAG/Knowledge base</td>
 *     <td>Semantic search</td>
 *   </tr>
 *   <tr>
 *     <td>{@link dev.fararoni.bus.agent.api.skills.DatabaseSkill}</td>
 *     <td>Database operations</td>
 *     <td>Stateful + Saga</td>
 *   </tr>
 *   <tr>
 *     <td>{@link dev.fararoni.bus.agent.api.skills.BrowserSkill}</td>
 *     <td>Web automation</td>
 *     <td>Stateful sessions</td>
 *   </tr>
 * </table>
 *
 * <h2>Skill Composition</h2>
 * <p>Skills can implement multiple capability interfaces:</p>
 * <pre>
 *                     ┌─────────────┐
 *                     │  ToolSkill  │
 *                     │   (base)    │
 *                     └──────┬──────┘
 *                            │
 *         ┌──────────────────┼──────────────────┐
 *         │                  │                  │
 *   ┌─────▼─────┐    ┌───────▼───────┐  ┌───────▼───────┐
 *   │  Saga     │    │   Stateful    │  │  Streamable   │
 *   │ Capable   │    │    Skill      │  │    Skill      │
 *   └─────┬─────┘    └───────┬───────┘  └───────┬───────┘
 *         │                  │                  │
 *         │     ┌────────────┴────────────┐     │
 *         │     │                         │     │
 *   ┌─────▼─────▼───┐            ┌────────▼─────▼───┐
 *   │ FileSystem    │            │   Database       │
 *   │ Skill         │            │   Skill          │
 *   │ (Saga+Stream) │            │ (Saga+Stateful)  │
 *   └───────────────┘            └──────────────────┘
 * </pre>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 */
package dev.fararoni.bus.agent.api.skills;
