/*
 * Copyright (c) 2026 Eber Cruz Fararoni. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * FASE 71 - Service Provider Interface for Fararoni Modules
 */

/**
 * Service Provider Interface (SPI) for Fararoni infrastructure modules.
 *
 * <p>This package defines the contracts for pluggable infrastructure components:</p>
 * <ul>
 *   <li>{@link dev.fararoni.bus.spi.FararoniModule} - Main SPI for modules</li>
 *   <li>{@link dev.fararoni.bus.spi.ModuleContext} - Dependency injection container</li>
 *   <li>{@link dev.fararoni.bus.spi.ModuleHealth} - Health status indicators</li>
 * </ul>
 *
 * <h2>Implementing a Module</h2>
 * <ol>
 *   <li>Create a class implementing {@code FararoniModule}</li>
 *   <li>Register it in {@code META-INF/services/dev.fararoni.bus.spi.FararoniModule}</li>
 *   <li>The ModuleRegistry will discover and load it via ServiceLoader</li>
 * </ol>
 *
 * <h2>Available Modules</h2>
 * <table border="1">
 *   <tr><th>Module</th><th>Package</th><th>Description</th></tr>
 *   <tr><td>Gateway REST</td><td>dev.fararoni.bus.gateway.rest</td><td>HTTP Ingress/Egress</td></tr>
 *   <tr><td>Voice Extension</td><td>fararoni-extension-voice</td><td>STT/TTS via Whisper</td></tr>
 *   <tr><td>Vision Extension</td><td>fararoni-extension-vision</td><td>Image processing</td></tr>
 * </table>
 *
 * @author Eber Cruz
 * @since 1.0.0
 */
package dev.fararoni.bus.spi;
