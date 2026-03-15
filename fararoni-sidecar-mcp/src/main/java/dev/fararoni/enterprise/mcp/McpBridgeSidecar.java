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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP BRIDGE SIDECAR - STDIO-TUNNEL (Grado Militar)
 *
 * Este Sidecar NO usa HTTP. Lanza el proceso MCP (ej: npx server-filesystem)
 * y se comunica con el via tuberias (pipes) de memoria del sistema operativo.
 *
 * Ventajas sobre HTTP:
 * 1. SEGURIDAD TOTAL: El servidor MCP no abre puertos. Solo el Sidecar tiene el handle.
 * 2. LATENCIA ZERO: No hay stack TCP/IP. Datos pasan via kernel del SO.
 * 3. SOBERANIA DE PROCESO: Si el MCP crashea, el Sidecar lo detecta y reinicia automaticamente.
 *
 * Incluye Watchdog de Grado Militar que:
 * - Detecta procesos "zombies" (vivos pero congelados)
 * - Auto-resucita el proceso en milisegundos
 * - Reporta latencia real al SATIRouter
 *
 * FASE 80.1.19: Isolated Sentinel Pattern
 * - Inmune a SIGSTOP (procesos congelados)
 * - ReentrantLock con tryLock() evita deadlock
 * - hardReset() independiente del lock de comunicacion
 *
 * @since FASE 80.1.15
 * @updated FASE 80.1.19 (Resiliente a Congelamiento)
 */
public class McpBridgeSidecar {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(McpBridgeSidecar.class);

    private final String[] command;
    private volatile Process mcpProcess;
    private volatile BufferedWriter writer;
    private volatile BufferedReader reader;
    private volatile BufferedReader errorReader;

    // === ISOLATED SENTINEL PATTERN (FASE 80.1.19) ===
    // Lock inteligente: permite intentar entrar sin quedarse atrapado
    private final ReentrantLock tunnelLock = new ReentrantLock();
    // Lock separado para operaciones de lifecycle (startOrRestart)
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    // Timeout para obtener el lock del tunel (ms)
    private static final long TUNNEL_LOCK_TIMEOUT_MS = 200;

    // === SATI TELEMETRY ===
    private final AtomicLong lastLatencyMicros = new AtomicLong(-1);
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicInteger restartCount = new AtomicInteger(0);
    private volatile String lastStatus = "STARTING";

    // Identificador de modo para el SATIRouter
    private static final String CONNECTION_MODE = "STDIO-TUNNEL";

    // Timeout para health check (500ms)
    private static final long PING_TIMEOUT_MS = 500;

    /**
     * Crea un Sidecar que lanzara el comando especificado como proceso hijo.
     *
     * @param command Comando a ejecutar (ej: "npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp/sandbox")
     */
    public McpBridgeSidecar(String... command) {
        this.command = command;
    }

    // =========================================================================
    // PROCESS LIFECYCLE
    // =========================================================================

    /**
     * Inicia o reinicia el proceso MCP.
     * Si ya existe un proceso, lo mata primero.
     * Usa lifecycleLock separado del tunnelLock.
     */
    public void startOrRestart() throws IOException {
        lifecycleLock.lock();
        try {
            if (mcpProcess != null && mcpProcess.isAlive()) {
                LOG.info("[MCP-STDIO] Matando proceso existente PID={}", mcpProcess.pid());
                mcpProcess.destroyForcibly();
                try {
                    mcpProcess.waitFor(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            LOG.info("[MCP-STDIO] Iniciando proceso: {}", String.join(" ", command));
            lastStatus = "STARTING";

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false); // Mantener stderr separado para diagnostico

            this.mcpProcess = pb.start();

            // Crear tuneles de comunicacion
            this.writer = new BufferedWriter(
                new OutputStreamWriter(mcpProcess.getOutputStream(), StandardCharsets.UTF_8));
            this.reader = new BufferedReader(
                new InputStreamReader(mcpProcess.getInputStream(), StandardCharsets.UTF_8));
            this.errorReader = new BufferedReader(
                new InputStreamReader(mcpProcess.getErrorStream(), StandardCharsets.UTF_8));

            restartCount.incrementAndGet();
            lastStatus = "READY";

            LOG.info("[MCP-STDIO] Proceso iniciado PID={}, Reinicios={}", mcpProcess.pid(), restartCount.get());

            // Iniciar lector de stderr en hilo virtual (para capturar errores del MCP)
            startErrorReader();
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * HARD RESET: Reinicio forzado inmune a deadlock (FASE 80.1.19)
     *
     * Esta operacion NO depende del tunnelLock, por lo que puede ejecutarse
     * incluso cuando un hilo esta bloqueado en I/O por un proceso congelado (SIGSTOP).
     *
     * Flujo:
     * 1. Mata el proceso sin esperar (destroyForcibly)
     * 2. Si hay un hilo atrapado en el tunnelLock, el I/O fallara cuando el proceso muera
     * 3. Reinicia el proceso con nuevos streams
     */
    public void hardReset() {
        LOG.warn("[SATI-RESET] Ejecutando Hard Reset del tunel...");

        // 1. Matar el proceso inmediatamente (no espera, no usa locks de comunicacion)
        if (mcpProcess != null) {
            long oldPid = mcpProcess.pid();
            mcpProcess.destroyForcibly();
            LOG.info("[SATI-RESET] Proceso {} destruido forzosamente", oldPid);
        }

        // 2. Log si hay hilos atrapados en el lock de comunicacion
        if (tunnelLock.isLocked()) {
            LOG.warn("[SATI-RESET] tunnelLock estaba bloqueado (I/O zombie detectado). El hilo atrapado fallara cuando intente leer/escribir.");
        }

        // 3. Reiniciar el proceso (usa su propio lock)
        try {
            startOrRestart();
        } catch (IOException e) {
            LOG.error("[SATI-RESET] Error reiniciando proceso: {}", e.getMessage());
            lastStatus = "RESTART_FAILED";
        }
    }

    /**
     * Lee stderr del proceso MCP para capturar errores y advertencias.
     */
    private void startErrorReader() {
        Thread.ofVirtual().name("MCP-STDERR-Reader").start(() -> {
            try {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    LOG.warn("[MCP-STDERR] {}", line);
                }
            } catch (IOException e) {
                // Proceso cerrado, normal
            }
        });
    }

    // =========================================================================
    // WATCHDOG (Auto-Resurreccion) - FASE 80.1.19 MEJORADO
    // =========================================================================

    /**
     * Inicia el Watchdog de Grado Militar.
     * Monitorea el proceso cada 5 segundos y lo resucita si muere o se congela.
     *
     * FASE 80.1.19: Usa hardReset() que es inmune a deadlock por SIGSTOP.
     */
    public void startWatchdog() {
        var scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("SATI-Watchdog-").factory()
        );

        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. Verificar si el proceso esta vivo fisicamente
                if (mcpProcess == null || !mcpProcess.isAlive()) {
                    LOG.error("[WATCHDOG] Proceso muerto detectado. Ejecutando Hard Reset...");
                    lastStatus = "RESTARTING";
                    hardReset();
                    return;
                }

                // 2. Verificar si el proceso responde logicamente (ping con timeout)
                long start = System.nanoTime();
                CompletableFuture<String> pingTask = CompletableFuture.supplyAsync(() -> {
                    try {
                        return sendAndReceive("{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":0}");
                    } catch (IOException e) {
                        return null;
                    }
                }, Executors.newVirtualThreadPerTaskExecutor());

                String response = pingTask.get(PING_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                if (response == null) {
                    throw new TimeoutException("Respuesta nula");
                }

                // Actualizar latencia
                lastLatencyMicros.set((System.nanoTime() - start) / 1000);
                lastStatus = "READY";

            } catch (TimeoutException e) {
                LOG.warn("[WATCHDOG] Proceso no responde ({}ms). Hard Reset...", PING_TIMEOUT_MS);
                lastStatus = "TIMEOUT";
                totalErrors.incrementAndGet();
                // FASE 80.1.19: Usar hardReset() en lugar de startOrRestart()
                hardReset();
            } catch (Exception e) {
                LOG.error("[WATCHDOG] Error inesperado: {}", e.getMessage());
                hardReset();
            }
        }, 5, 5, TimeUnit.SECONDS);

        LOG.info("[WATCHDOG] Iniciado. Monitoreo cada 5 segundos.");
    }

    // =========================================================================
    // COMMUNICATION (STDIO) - FASE 80.1.19 CON TRYLOCK
    // =========================================================================

    /**
     * Envia una peticion JSON-RPC al proceso hijo y lee la respuesta.
     *
     * FASE 80.1.19: Usa tryLock() con timeout en lugar de synchronized.
     * Si el proceso esta congelado (SIGSTOP), este metodo fallara rapido
     * en lugar de bloquearse indefinidamente.
     *
     * @param jsonRequest Peticion JSON-RPC
     * @return Respuesta del servidor MCP
     * @throws IOException Si hay error de comunicacion o timeout de lock
     */
    public String sendAndReceive(String jsonRequest) throws IOException {
        activeRequests.incrementAndGet();
        totalRequests.incrementAndGet();
        long startNanos = System.nanoTime();

        try {
            // FASE 80.1.19: Intentar obtener el lock con timeout
            // Si el proceso esta congelado, otro hilo puede estar atrapado en write/read
            if (!tunnelLock.tryLock(TUNNEL_LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                totalErrors.incrementAndGet();
                lastStatus = "TUNNEL_BLOCKED";
                throw new IOException("Tunel bloqueado: El proceso hijo no acepta datos (posible congelamiento). Lock timeout: " + TUNNEL_LOCK_TIMEOUT_MS + "ms");
            }

            try {
                // 1. Enviar al STDIN del servidor MCP
                writer.write(jsonRequest);
                writer.newLine();
                writer.flush();

                // 2. Leer del STDOUT del servidor MCP
                String response = reader.readLine();

                // Actualizar latencia
                lastLatencyMicros.set((System.nanoTime() - startNanos) / 1000);

                if (response == null) {
                    totalErrors.incrementAndGet();
                    lastStatus = "ERROR";
                    throw new IOException("Proceso MCP cerro el stream de salida");
                }

                return response;

            } finally {
                tunnelLock.unlock();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            totalErrors.incrementAndGet();
            throw new IOException("Comunicacion interrumpida mientras esperaba lock del tunel");
        } catch (IOException e) {
            totalErrors.incrementAndGet();
            lastStatus = "ERROR";
            throw e;
        } finally {
            activeRequests.decrementAndGet();
        }
    }

    /**
     * Procesa una peticion MCP recibida del Bus Soberano.
     * Wrapper para compatibilidad con el Main.
     */
    public String processMcpRequest(String rawInput) {
        try {
            LOG.debug("[MCP-IN] Peticion: {}", truncate(rawInput, 200));
            String response = sendAndReceive(rawInput);
            LOG.debug("[MCP-OUT] Respuesta en {}us", lastLatencyMicros.get());
            return response;
        } catch (IOException e) {
            return buildErrorResponse("STDIO_ERROR", e.getMessage());
        }
    }

    // =========================================================================
    // SATI TELEMETRY
    // =========================================================================

    /**
     * Verifica si el proceso MCP esta vivo.
     */
    public boolean isAlive() {
        return mcpProcess != null && mcpProcess.isAlive();
    }

    /**
     * Verifica la salud del proceso (para SATI heartbeat).
     * Retorna true si el proceso esta vivo y respondio al ultimo ping.
     */
    public boolean checkMcpHealth() {
        if (!isAlive()) {
            lastStatus = "OFFLINE";
            return false;
        }
        return "READY".equals(lastStatus);
    }

    public long getLastLatencyMicros() {
        return lastLatencyMicros.get();
    }

    public String getStatus() {
        return lastStatus;
    }

    public String getMode() {
        return CONNECTION_MODE;
    }

    public int getActiveRequests() {
        return activeRequests.get();
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getTotalErrors() {
        return totalErrors.get();
    }

    public int getRestartCount() {
        return restartCount.get();
    }

    public double getLoadFactor() {
        int active = activeRequests.get();
        double requestLoad = Math.min(active / 10.0, 1.0);
        long latency = lastLatencyMicros.get();
        double latencyLoad = latency > 0 ? Math.min(latency / 100_000.0, 1.0) : 0.5;
        return (requestLoad + latencyLoad) / 2.0;
    }

    /**
     * Obtiene el PID del proceso MCP.
     */
    public long getProcessPid() {
        return mcpProcess != null ? mcpProcess.pid() : -1;
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private String buildErrorResponse(String code, String message) {
        return String.format(
            "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}",
            code, escapeJson(message)
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
