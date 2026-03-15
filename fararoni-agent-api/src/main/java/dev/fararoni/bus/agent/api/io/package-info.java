/*
 * Copyright (c) 2026 Eber Cruz Fararoni. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * FNL - Fararoni Native Link v2.0
 * High-Performance I/O Package
 */

/**
 * High-performance binary streaming and event ingestion for FNL.
 *
 * <p>This package provides contracts for:</p>
 * <ul>
 *   <li>Efficient handling of large binary data without Base64 overhead</li>
 *   <li>Vendor-agnostic event ingestion from external systems (Track A)</li>
 * </ul>
 *
 * <h2>Binary Streaming</h2>
 * <p>MCP encodes everything as Base64 in JSON:</p>
 * <ul>
 *   <li>100MB file = 133MB in JSON (33% overhead)</li>
 *   <li>Entire file must fit in memory</li>
 *   <li>1GB file = Out of Memory error</li>
 * </ul>
 *
 * <p>FNL streams binary data directly:</p>
 * <ul>
 *   <li>100MB file = 8KB buffer (constant memory)</li>
 *   <li>10GB file = still 8KB buffer</li>
 *   <li>Zero-copy transfer possible</li>
 * </ul>
 *
 * <h2>Event Ingestion (Track A)</h2>
 * <p>Contracts for receiving events from external systems:</p>
 * <ul>
 *   <li>Email (IMAP IDLE)</li>
 *   <li>Jira webhooks</li>
 *   <li>Slack Events API</li>
 *   <li>Generic HTTP webhooks</li>
 * </ul>
 *
 * <h2>Key Components</h2>
 * <dl>
 *   <dt>{@link dev.fararoni.bus.agent.api.io.StreamableSkill}</dt>
 *   <dd>Interface for skills that support binary streaming</dd>
 *   <dt>{@link dev.fararoni.bus.agent.api.io.BinaryStreamHandle}</dt>
 *   <dd>Wrapper for efficient stream handling</dd>
 *   <dt>{@link dev.fararoni.bus.agent.api.io.IngestionChannel}</dt>
 *   <dd>Port for receiving external events (Track A)</dd>
 *   <dt>{@link dev.fararoni.bus.agent.api.io.IncomingMessage}</dt>
 *   <dd>Vendor-agnostic message DTO (Track A)</dd>
 * </dl>
 *
 * <h2>Memory Comparison</h2>
 * <pre>
 * File Size  | MCP Memory      | FNL Memory
 * -----------|-----------------|------------
 * 100MB      | ~200MB (Base64) | 8KB
 * 1GB        | ~2GB (Base64)   | 8KB
 * 10GB       | OOM Error       | 8KB
 * </pre>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Reading large log files</li>
 *   <li>Processing PDF documents</li>
 *   <li>Streaming database exports</li>
 *   <li>Handling image/video files</li>
 *   <li>Network stream processing</li>
 * </ul>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 */
package dev.fararoni.bus.agent.api.io;
