/*
 * Copyright (c) 2026 Eber Cruz Fararoni. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * FNL - Fararoni Native Link v2.0
 * Saga Pattern Package
 */

/**
 * Saga pattern implementation for distributed transaction compensation.
 *
 * <p>This package provides the contracts for implementing the Saga pattern,
 * enabling automatic rollback of multi-step AI agent operations. Unlike
 * traditional 2PC (Two-Phase Commit), Saga executes operations immediately
 * and stores compensation instructions for rollback.</p>
 *
 * <h2>Saga vs 2PC</h2>
 * <pre>
 * 2PC (Traditional):
 * ┌──────────┐    ┌──────────┐    ┌──────────┐
 * │ Prepare  │───►│  Commit  │───►│  Done    │
 * └──────────┘    └──────────┘    └──────────┘
 *      │               │
 *      └─── Rollback ──┘  (blocks resources during prepare)
 *
 * Saga (FNL):
 * ┌──────────┐    ┌──────────┐    ┌──────────┐
 * │ Execute  │───►│ Execute  │───►│ Execute  │
 * │ + Store  │    │ + Store  │    │ + Store  │
 * │  Undo    │    │  Undo    │    │  Undo    │
 * └──────────┘    └──────────┘    └──────────┘
 *      │               │               │
 *      │      If step 3 fails:         │
 *      │◄──── Compensate 2 ◄───────────┘
 *      │◄──── Compensate 1
 * </pre>
 *
 * <h2>Key Components</h2>
 * <dl>
 *   <dt>{@link dev.fararoni.bus.agent.api.saga.SagaCapableSkill}</dt>
 *   <dd>Interface for skills that support compensation</dd>
 *   <dt>{@link dev.fararoni.bus.agent.api.saga.CompensationInstruction}</dt>
 *   <dd>Instructions for undoing an operation</dd>
 * </dl>
 *
 * <h2>Enterprise Value</h2>
 * <ul>
 *   <li>Automatic rollback when AI agent fails mid-operation</li>
 *   <li>No resource blocking (unlike 2PC)</li>
 *   <li>Audit trail of all compensations</li>
 *   <li>Works across distributed systems</li>
 * </ul>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 */
package dev.fararoni.bus.agent.api.saga;
