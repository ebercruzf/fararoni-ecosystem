/*
 * Copyright 2026 Eber Cruz Fararoni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package dev.fararoni.enterprise.mcp;

import io.nats.client.*;
import java.util.concurrent.Executors;
import java.util.Map;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MCP SIDECAR MAIN - STDIO-TUNNEL (Java 25)
 *
 * Orquestador del proceso Sidecar que:
 * 1. Conecta al Bus NATS Soberano
 * 2. Lanza el servidor MCP como proceso hijo (via STDIO, sin HTTP)
 * 3. Registra capacidades via Auto-Discovery (Heartbeat SATI)
 * 4. Procesa peticiones MCP usando Virtual Threads
 * 5. Soporta Queue Groups para Round-Robin entre instancias
 * 6. Escucha el SATI-Panic-Button para Hard Reset
 *
 * Modo STDIO-TUNNEL:
 * - El servidor MCP NO abre puertos
 * - Comunicacion via tuberias del SO (latencia zero)
 * - Invisible para atacantes externos
 *
 * Uso:
 *   java --enable-preview \
 *     -Dfararoni.nats.url=nats://localhost:4222 \
 *     -Dmcp.exec.command="npx -y @modelcontextprotocol/server-filesystem /tmp/sandbox" \
 *     -jar fararoni-sidecar-mcp-1.0.0.jar
 *
 * @since FASE 80.1.15
 */
public class McpSidecarMain {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String QUEUE_GROUP = "mcp-workers";
    private static final String SKILLS_TOPIC = "fararoni.skills.mcp";
    private static final String REGISTRY_TOPIC = "fararoni.registry.updates";
    private static final String SATI_REGISTRY_TOPIC = "fararoni.sati.registry";
    private static final String PANIC_TOPIC = "fararoni.sati.control.panic";
    private static final int HEARTBEAT_INTERVAL_MS = 5_000;

    public static void main(String[] args) throws Exception {
        // 1. Cargar Configuracion
        String natsUrl = System.getProperty("fararoni.nats.url", "nats://localhost:4222");
        String mcpCommand = System.getProperty("mcp.exec.command",
            "npx -y @modelcontextprotocol/server-filesystem /tmp/fararoni-sandbox");
        String sidecarId = "mcp-bridge-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        // Parsear el comando (split por espacios, respetando comillas)
        String[] commandParts = parseCommand(mcpCommand);

        printBanner(sidecarId, natsUrl, mcpCommand);

        // 2. Conexion al Bus Soberano NATS
        Options options = new Options.Builder()
            .server(natsUrl)
            .maxReconnects(-1)
            .reconnectWait(java.time.Duration.ofSeconds(2))
            .connectionListener((conn, type) -> {
                System.out.println("[NATS] Evento: " + type);
            })
            .build();

        Connection natsConn = Nats.connect(options);
        System.out.println("[NATS] Conexion establecida: " + natsConn.getServerInfo().getServerId());

        // 3. Ejecutor de Hilos Virtuales (Java 25)
        var executor = Executors.newVirtualThreadPerTaskExecutor();

        // 4. Instanciar el Bridge MCP en modo STDIO-TUNNEL
        McpBridgeSidecar bridge = new McpBridgeSidecar(commandParts);

        // 5. Iniciar el proceso MCP
        System.out.println("[MCP-STDIO] Iniciando servidor MCP...");
        bridge.startOrRestart();

        // 6. Iniciar Watchdog de Grado Militar
        bridge.startWatchdog();

        // 7. AUTO-DISCOVERY con SATI: Heartbeat periodico con metricas
        executor.submit(() -> runSatiHeartbeatLoop(natsConn, bridge, sidecarId));

        // 8. Suscripcion al topico de Skills con Queue Group
        Dispatcher dispatcher = natsConn.createDispatcher(msg -> {
            executor.submit(() -> {
                try {
                    String response = bridge.processMcpRequest(new String(msg.getData()));
                    if (msg.getReplyTo() != null) {
                        natsConn.publish(msg.getReplyTo(), response.getBytes());
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR] Fallo en Bridge: " + e.getMessage());
                }
            });
        });
        dispatcher.subscribe(SKILLS_TOPIC, QUEUE_GROUP);
        System.out.println("[MCP] Suscrito a '" + SKILLS_TOPIC + "' con QueueGroup '" + QUEUE_GROUP + "'");

        // 9. SATI-Panic-Button: Escuchar ordenes de reinicio masivo
        natsConn.createDispatcher(msg -> {
            String command = new String(msg.getData());
            if ("HARD_REBOOT".equals(command)) {
                System.err.println("[PANIC] Orden de HARD_REBOOT recibida del Kernel. Reiniciando tunel STDIO...");
                try {
                    bridge.startOrRestart();
                    System.out.println("[PANIC] Tunel restablecido tras orden de panico.");
                } catch (Exception e) {
                    System.err.println("[PANIC] Error en reinicio forzado: " + e.getMessage());
                }
            }
        }).subscribe(PANIC_TOPIC);
        System.out.println("[SATI] Escuchando ordenes de panico en '" + PANIC_TOPIC + "'");

        System.out.println("====================================================");
        System.out.println("  MCP SIDECAR [" + sidecarId + "] OPERATIVO");
        System.out.println("  Modo: STDIO-TUNNEL (Sin puertos HTTP)");
        System.out.println("  Java Version: " + System.getProperty("java.version"));
        System.out.println("====================================================");

        // Mantener vivo el proceso
        Thread.currentThread().join();
    }

    /**
     * Loop de Heartbeat SATI con metricas de modo STDIO-TUNNEL.
     */
    private static void runSatiHeartbeatLoop(Connection natsConn, McpBridgeSidecar bridge, String sidecarId) {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                boolean isHealthy = bridge.checkMcpHealth();
                double systemLoad = osBean.getSystemLoadAverage();
                if (systemLoad < 0) systemLoad = 0.0;

                Map<String, Object> satiHeartbeat = Map.of(
                    "sidecar_id", sidecarId,
                    "type", "MCP_BRIDGE",
                    "mode", bridge.getMode(), // "STDIO-TUNNEL"
                    "priority", 100,
                    "status", bridge.getStatus(),
                    "process_pid", bridge.getProcessPid(),
                    "restart_count", bridge.getRestartCount(),
                    "capabilities", java.util.List.of(
                        Map.of("name", "read_file", "description", "Lee archivos via MCP"),
                        Map.of("name", "write_file", "description", "Escribe archivos via MCP"),
                        Map.of("name", "list_directory", "description", "Lista directorios via MCP")
                    ),
                    "metrics", Map.of(
                        "latency_us", bridge.getLastLatencyMicros(),
                        "active_requests", bridge.getActiveRequests(),
                        "total_requests", bridge.getTotalRequests(),
                        "total_errors", bridge.getTotalErrors(),
                        "load_factor", bridge.getLoadFactor(),
                        "system_load", systemLoad
                    ),
                    "timestamp", System.currentTimeMillis()
                );

                byte[] payload = mapper.writeValueAsBytes(satiHeartbeat);
                natsConn.publish(REGISTRY_TOPIC, payload);
                natsConn.publish(SATI_REGISTRY_TOPIC, payload);

                String statusIcon = isHealthy ? "[OK]" : "[!!]";
                System.out.printf("[SATI] %s Heartbeat: mode=%s, latency=%dus, pid=%d, restarts=%d%n",
                    statusIcon,
                    bridge.getMode(),
                    bridge.getLastLatencyMicros(),
                    bridge.getProcessPid(),
                    bridge.getRestartCount()
                );

                Thread.sleep(HEARTBEAT_INTERVAL_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[WARN] Error en SATI Heartbeat: " + e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Parsea un comando string en array de argumentos.
     * Respeta comillas para argumentos con espacios.
     */
    private static String[] parseCommand(String command) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : command.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts.toArray(new String[0]);
    }

    private static void printBanner(String sidecarId, String natsUrl, String mcpCommand) {
        System.out.println("====================================================");
        System.out.println("    FARARONI MCP BRIDGE SIDECAR (FASE 80.1.15)");
        System.out.println("    Modo: STDIO-TUNNEL (Sin HTTP)");
        System.out.println("====================================================");
        System.out.println("  Sidecar ID:   " + sidecarId);
        System.out.println("  NATS URL:     " + natsUrl);
        System.out.println("  MCP Command:  " + mcpCommand);
        System.out.println("====================================================");
    }
}
