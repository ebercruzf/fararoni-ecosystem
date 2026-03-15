/*
 * Copyright (c) 2026 Eber Cruz Fararoni. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * FNL - Fararoni Native Link v2.0
 * Enterprise Governance Package
 */

/**
 * Enterprise governance contracts for cost control and resource management.
 *
 * <p>This package provides interfaces for implementing cost estimation,
 * budget control, and quota management. Essential for preventing runaway
 * AI agents from generating surprise bills.</p>
 *
 * <h2>Why Governance?</h2>
 * <p>AI agents can generate costs rapidly:</p>
 * <ul>
 *   <li>LLM API calls ($0.01-$0.10 per call)</li>
 *   <li>Cloud compute resources</li>
 *   <li>External API quotas</li>
 *   <li>Storage operations</li>
 * </ul>
 *
 * <h2>Key Components</h2>
 * <dl>
 *   <dt>{@link dev.fararoni.bus.agent.api.governance.QuotaAwareSkill}</dt>
 *   <dd>Interface for skills with cost estimation and budget control</dd>
 * </dl>
 *
 * <h2>Budget Flow</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────────┐
 * │  Agent wants to call skill.action()                      │
 * │           │                                              │
 * │           ▼                                              │
 * │  ┌─────────────────────┐                                 │
 * │  │ estimateCost()      │ ◄── How much will this cost?    │
 * │  └─────────────────────┘                                 │
 * │           │                                              │
 * │           ▼                                              │
 * │  ┌─────────────────────┐                                 │
 * │  │ hasBudget(cost)?    │ ◄── Can we afford it?           │
 * │  └─────────────────────┘                                 │
 * │      │           │                                       │
 * │    Yes           No                                      │
 * │      │           │                                       │
 * │      ▼           ▼                                       │
 * │  Execute      Reject with                                │
 * │  action()     "Budget exceeded"                          │
 * │      │                                                   │
 * │      ▼                                                   │
 * │  recordCost() ◄── Track actual usage                     │
 * └──────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Enterprise Value</h2>
 * <ul>
 *   <li>Prevent surprise cloud bills</li>
 *   <li>Enforce departmental budgets</li>
 *   <li>Audit resource consumption</li>
 *   <li>Implement tiered pricing (free/pro/enterprise)</li>
 * </ul>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 */
package dev.fararoni.bus.agent.api.governance;
