/*
 * Copyright (C) 2026 Eber Cruz Fararoni
 *
 * FASE 80.1.5 + 80.1.7 - Sidecar Bridge Skills
 *
 * Este paquete contiene las clases para conectar sidecars externos
 * con el Kernel de forma agnostica.
 *
 * Componentes principales:
 * - DynamicSkill: Interface para skills con health check dinamico
 * - SidecarBridgeSkill: Clase base para implementar bridges a sidecars
 * - SkillWatcher: Monitor que refresca disponibilidad cada 5 segundos
 * - McpProxySkill: Proxy para comunicacion con Sidecar MCP externo (FASE 80.1.7)
 * - CapabilityManager: Gestor de handshakes y registro dinamico de capacidades (FASE 80.1.7)
 */
package dev.fararoni.core.core.skills.bridge;
