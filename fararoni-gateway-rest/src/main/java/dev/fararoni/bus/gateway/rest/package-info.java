/*
 * Copyright (c) 2026 Eber Cruz Fararoni. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * OmniChannel Gateway REST Module
 */

/**
 * OmniChannel HTTP Gateway for Fararoni.
 *
 * <p>This module provides a channel-agnostic HTTP gateway for communication
 * with external Sidecars (WhatsApp, Telegram, Slack, Discord, IoT devices).</p>
 *
 * <h2>Package Structure</h2>
 * <pre>
 * dev.fararoni.bus.gateway.rest
 * +-- OmniChannelGatewayModule.java  (Main entry point, FararoniModule impl)
 * +-- ingress/
 * |   +-- RestIngressServer.java     (HTTP server for inbound messages)
 * +-- egress/
 * |   +-- HttpEgressDispatcher.java  (HTTP client for outbound messages)
 * +-- dto/
 * |   +-- UniversalMessage.java      (Channel-agnostic message format)
 * |   +-- ChannelEndpoint.java       (Sidecar endpoint configuration)
 * +-- security/
 *     +-- RateLimiter.java           (Token Bucket rate limiting)
 * </pre>
 *
 * <h2>Architecture</h2>
 * <pre>
 *                          +------------------+
 *                          | External Sidecars|
 *                          | (Node.js/Python) |
 *                          +--------+---------+
 *                                   |
 *                          HTTP POST/Webhook
 *                                   |
 *                                   v
 * +----------------------------------------------------------------+
 * |                  fararoni-gateway-rest (Port 7071)              |
 * |                                                                 |
 * |  +-------------------+              +----------------------+    |
 * |  | RestIngressServer |              | HttpEgressDispatcher |    |
 * |  | POST /gateway/v1/ |              | HTTP POST to Sidecars|    |
 * |  |      inbound      |              |                      |    |
 * |  +---------+---------+              +----------+-----------+    |
 * |            |                                   ^                |
 * |            v                                   |                |
 * |  +-------------------+              +----------+-----------+    |
 * |  | SovereignEventBus |              | agency.output.main   |    |
 * |  | agency.input.*    +------------->+                      |    |
 * |  +-------------------+              +----------------------+    |
 * +----------------------------------------------------------------+
 *                                   |
 *                                   v
 *                          OmniChannelRouter (fararoni-core)
 *                                   |
 *                                   v
 *                            Agents (LLM)
 * </pre>
 *
 * <h2>Configuration</h2>
 * <p>Configure in ~/.fararoni/config/modules.yml:</p>
 * <pre>
 * gateway:
 *   rest:
 *     enabled: true
 *     port: 7071
 *     security:
 *       api_token: "env:FARARONI_GATEWAY_TOKEN"
 *
 * channels:
 *   whatsapp:
 *     enabled: true
 *     egress_url: "http://localhost:3000/send"
 * </pre>
 *
 * <h2>Quick Start</h2>
 * <ol>
 *   <li>Add fararoni-gateway-rest to classpath</li>
 *   <li>Configure modules.yml</li>
 *   <li>Set FARARONI_GATEWAY_TOKEN environment variable</li>
 *   <li>Module is auto-discovered via ServiceLoader</li>
 * </ol>
 *
 * @author Eber Cruz
 * @since 1.0.0
 */
package dev.fararoni.bus.gateway.rest;
