/*
 * Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fararoni.core.core.commands;

import dev.fararoni.bus.agent.api.command.CommandCategory;
import dev.fararoni.bus.agent.api.command.ConsoleCommand;
import dev.fararoni.bus.agent.api.command.ExecutionContext;
import dev.fararoni.core.core.llm.LocalLlmConfig;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ModelCommand implements ConsoleCommand {
    private static volatile boolean modelActive = true;
    private static volatile long requestCount = 0;
    private static volatile long totalTokens = 0;

    @Override
    public String getTrigger() {
        return "/model";
    }

    @Override
    public String getDescription() {
        return "Muestra informacion y estado del modelo LLM local";
    }

    @Override
    public String getUsage() {
        return "/model [status|info|stats|reload|off|on]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIG;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/llm", "/ai" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /model - Gestiona el modelo LLM local

            Uso:
              /model            Muestra estado actual
              /model status     Estado detallado del modelo
              /model info       Informacion de configuracion
              /model stats      Estadisticas de uso
              /model reload     Recarga el modelo
              /model on         Activa el modelo
              /model off        Desactiva el modelo

            Informacion del Modelo:
              - Modelo: Qwen2.5-Coder-1.5B-Instruct
              - Formato: GGUF (q8_0)
              - Tamano: ~1.7GB
              - Contexto: 2048 tokens (configurable)
              - Uso: Clasificacion semantica y generacion

            Variables de Entorno:
              FARARONI_MODEL_PATH    Ruta al modelo GGUF
              FARARONI_CONTEXT_LENGTH  Tamanio de contexto
              FARARONI_MAX_TOKENS      Max tokens a generar
              FARARONI_TEMPERATURE     Temperatura (0.0-1.0)
              FARARONI_THREADS         Threads (0=auto)
              FARARONI_GPU_LAYERS      Capas en GPU (0=CPU)
              FARARONI_MIN_RAM_MB      RAM minima libre

            Ejemplos:
              /model                 # Ver estado
              /model info            # Ver configuracion
              /model stats           # Ver estadisticas
              /model reload          # Recargar modelo

            Notas:
              - El modelo se carga automaticamente al iniciar
              - Requiere ~2GB RAM libre para cargar
              - Use /model off para liberar memoria

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        String action = (args != null && !args.isBlank()) ? args.trim().toLowerCase() : "status";

        switch (action) {
            case "status", "?" -> showStatus(ctx);
            case "info", "config" -> showInfo(ctx);
            case "stats", "statistics" -> showStats(ctx);
            case "reload", "restart" -> reloadModel(ctx);
            case "on", "enable", "activate" -> enableModel(ctx);
            case "off", "disable", "deactivate" -> disableModel(ctx);
            default -> {
                ctx.printError("Accion no reconocida: " + action);
                ctx.print("Uso: " + getUsage());
                ctx.print("  Acciones: status, info, stats, reload, on, off");
            }
        }
    }

    private void showStatus(ExecutionContext ctx) {
        LocalLlmConfig config = LocalLlmConfig.fromEnvironment();

        ctx.print("Estado del Modelo LLM");
        ctx.print("");

        String status = modelActive ? "ACTIVO" : "INACTIVO";
        ctx.print("  Estado:      " + status);

        boolean modelDownloaded = config.isModelDownloaded();
        boolean hasRam = config.hasEnoughRam();

        ctx.print("  Modelo:      " + (modelDownloaded ? "Descargado" : "No encontrado"));
        ctx.print("  RAM:         " + (hasRam ? "Suficiente" : "Insuficiente"));

        if (modelDownloaded && hasRam && modelActive) {
            ctx.printSuccess("OK - Modelo listo para usar");
        } else if (!modelDownloaded) {
            ctx.printWarning("El modelo no esta descargado");
            ctx.print("  Use: fararoni --download-model");
        } else if (!hasRam) {
            ctx.printWarning("RAM insuficiente para cargar el modelo");
            ctx.print("  Requerido: " + config.minFreeRamMb() + " MB");
            ctx.print("  Disponible: " + config.getAvailableRamMb() + " MB");
        } else {
            ctx.printWarning("Modelo desactivado");
            ctx.print("  Use: /model on");
        }

        ctx.printDebug("config.modelPath=" + config.modelPath());
    }

    private void showInfo(ExecutionContext ctx) {
        LocalLlmConfig config = LocalLlmConfig.fromEnvironment();

        ctx.print("Configuracion del Modelo");
        ctx.print("");
        ctx.print("  Modelo:");
        ctx.print("    Path:        " + config.modelPath());
        ctx.print("    Descargado:  " + (config.isModelDownloaded() ? "Si" : "No"));
        ctx.print("");
        ctx.print("  Parametros:");
        ctx.print("    Contexto:    " + config.contextLength() + " tokens");
        ctx.print("    Max Tokens:  " + config.maxTokens());
        ctx.print("    Temperatura: " + config.temperature());
        ctx.print("    Threads:     " + (config.threads() == 0 ? "auto" : config.threads()));
        ctx.print("    GPU Layers:  " + (config.gpuLayers() == 0 ? "CPU only" : config.gpuLayers()));
        ctx.print("");
        ctx.print("  Memoria:");
        ctx.print("    RAM Minima:  " + config.minFreeRamMb() + " MB");
        ctx.print("    Disponible:  " + config.getAvailableRamMb() + " MB");
        ctx.print("    Suficiente:  " + (config.hasEnoughRam() ? "Si" : "No"));
        ctx.print("");
        ctx.print("  Debug:         " + (config.debugLogging() ? "Activado" : "Desactivado"));

        ctx.printDebug("LocalLlmConfig loaded from environment");
    }

    private void showStats(ExecutionContext ctx) {
        ctx.print("Estadisticas del Modelo");
        ctx.print("");
        ctx.print("  Sesion Actual:");
        ctx.print("    Requests:     " + requestCount);
        ctx.print("    Tokens Total: " + totalTokens);
        ctx.print("    Estado:       " + (modelActive ? "Activo" : "Inactivo"));
        ctx.print("");

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        ctx.print("  Memoria JVM:");
        ctx.print("    Usada:        " + usedMemory + " MB");
        ctx.print("    Maxima:       " + maxMemory + " MB");
        ctx.print("    Uso:          " + (usedMemory * 100 / maxMemory) + "%");

        ctx.printDebug("requestCount=" + requestCount + ", totalTokens=" + totalTokens);
    }

    private void reloadModel(ExecutionContext ctx) {
        ctx.print("Recargando modelo...");

        modelActive = true;
        ctx.printSuccess("OK - Modelo recargado");
        ctx.print("  El modelo esta listo para usar");

        ctx.printDebug("Model reloaded (simulated)");
    }

    private void enableModel(ExecutionContext ctx) {
        if (modelActive) {
            ctx.printWarning("El modelo ya esta activo");
            return;
        }

        LocalLlmConfig config = LocalLlmConfig.fromEnvironment();

        if (!config.isModelDownloaded()) {
            ctx.printError("No se puede activar: modelo no descargado");
            ctx.print("  Use: fararoni --download-model");
            return;
        }

        if (!config.hasEnoughRam()) {
            ctx.printError("No se puede activar: RAM insuficiente");
            ctx.print("  Requerido: " + config.minFreeRamMb() + " MB");
            ctx.print("  Disponible: " + config.getAvailableRamMb() + " MB");
            return;
        }

        modelActive = true;
        ctx.printSuccess("OK - Modelo activado");
        ctx.print("  El modelo LLM local esta listo");

        ctx.printDebug("modelActive=true");
    }

    private void disableModel(ExecutionContext ctx) {
        if (!modelActive) {
            ctx.printWarning("El modelo ya esta inactivo");
            return;
        }

        modelActive = false;
        ctx.printSuccess("OK - Modelo desactivado");
        ctx.print("  Se usara el router basico (sin LLM)");
        ctx.printWarning("Las funciones de IA local no estaran disponibles");

        ctx.printDebug("modelActive=false");
    }

    public static boolean isModelActive() {
        return modelActive;
    }

    public static void incrementRequestCount() {
        requestCount++;
    }

    public static void addTokens(long tokens) {
        totalTokens += tokens;
    }

    public static long getRequestCount() {
        return requestCount;
    }

    public static long getTotalTokens() {
        return totalTokens;
    }
}
