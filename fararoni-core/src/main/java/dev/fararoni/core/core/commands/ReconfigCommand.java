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
import dev.fararoni.core.FararoniCore;
import dev.fararoni.core.core.bootstrap.LlmProviderDiscovery;
import dev.fararoni.core.config.ConfigPriorityResolver;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.constants.AppDefaults.RabbitPower;
import dev.fararoni.core.core.routing.EnterpriseRouter;
import dev.fararoni.core.core.security.SecureConfigService;

import dev.fararoni.core.core.llm.LocalLlmConfig;
import dev.fararoni.core.core.utils.NativeLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ReconfigCommand implements ConsoleCommand {
    @Override
    public String getTrigger() {
        return "/reconfig";
    }

    @Override
    public String getDescription() {
        return "Reconfigura la conexión al servidor LLM (Wizard on Demand)";
    }

    @Override
    public String getUsage() {
        return "/reconfig [scan|reset]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIG;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/reconnect", "/llm" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /reconfig - Reconfigura la conexión al servidor LLM

            Uso:
              /reconfig         Ejecuta wizard interactivo de configuración
              /reconfig scan    Solo escanea proveedores sin cambiar config
              /reconfig reset   Restaura configuración por defecto
              /reconfig swap    Hot-Swap: Cambia el motor Rabbit en caliente

            Este comando te permite:
              • Re-escanear proveedores LLM disponibles (Ollama, vLLM, LM Studio)
              • Cambiar de servidor sin reiniciar Fararoni
              • Ver qué modelos están disponibles en cada proveedor
              • Seleccionar manualmente un modelo específico
              • [NUEVO] Hot-Swap: Cambiar entre motor nativo GGUF y Ollama

            Ejemplos:
              /reconfig         # Wizard completo
              /reconfig scan    # Solo ver qué hay disponible
              /reconfig reset   # Volver a http:
              /reconfig swap    # Cambiar motor Rabbit (GGUF <-> Ollama)

            Hot-Swap (Grado Militar):
              El comando 'swap' permite cambiar el motor del Rabbit en tiempo real:
              • NATIVE: Motor GGUF local (rápido, funciona offline)
              • REMOTE: Ollama con modelo configurable (flexible)

              El sistema valida la conexión Y ejecuta una prueba de inferencia
              real antes de aplicar el cambio. Si falla, el sistema queda intacto.

            Notas:
              - Los cambios aplican inmediatamente (Hot-Swap)
              - La nueva configuración se guarda para próximas sesiones
              - Si no hay proveedores, te mostrará sugerencias de instalación

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        System.out.println();
        System.out.println("SOLICITUD DE RECONFIGURACION RECIBIDA");
        System.out.println("=========================================");

        String subcommand = args != null ? args.trim().toLowerCase() : "";

        switch (subcommand) {
            case "scan" -> executeScan();
            case "reset" -> executeReset();
            case "swap" -> executeSwap(ctx);
            default -> executeWizard(ctx);
        }
    }

    private void executeScan() {
        System.out.println("\nESCANEO DE PROVEEDORES LLM");
        System.out.println("─────────────────────────────────────");

        LlmProviderDiscovery discovery = new LlmProviderDiscovery();
        ConfigPriorityResolver resolver = new ConfigPriorityResolver();

        String currentUrl = resolver.resolveServerUrl(null);
        String currentModel = resolver.resolveModelName(null);

        System.out.println("Configuración actual:");
        System.out.println("  URL:    " + currentUrl);
        System.out.println("  Modelo: " + currentModel);
        System.out.println();

        LlmProviderDiscovery.DiscoveryResult result =
            discovery.discoverBestProvider(currentUrl, currentModel, null);

        System.out.println();
        if (result.success()) {
            System.out.println("[OK] Proveedor detectado: " + result.serverUrl());
            System.out.println("   Modelo recomendado: " + result.modelName());
        } else {
            System.out.println("[ERROR] " + result.message());
        }
        System.out.println();
    }

    private void executeReset() {
        System.out.println("\nRESTAURANDO CONFIGURACION ORIGINAL");
        System.out.println("─────────────────────────────────────────");

        try {
            SecureConfigService configService = SecureConfigService.getInstance();

            String originalUrl = configService.getProperty("first-run.server.url");
            String originalModel = configService.getProperty("first-run.model.name");
            String originalMode = configService.getProperty("first-run.mode");

            if (originalUrl == null || originalUrl.isBlank()) {
                originalUrl = AppDefaults.DEFAULT_OLLAMA_URL;
                System.out.println("[INFO] Sin configuracion original guardada. Usando default de Ollama.");
            }
            if (originalModel == null || originalModel.isBlank()) {
                originalModel = AppDefaults.DEFAULT_RABBIT_MODEL;
            }
            if (originalMode == null || originalMode.isBlank()) {
                originalMode = "hybrid";
            }

            configService.setProperty("server.url", originalUrl);
            configService.setProperty("model.name", originalModel);
            configService.setProperty("mode", originalMode);

            String providerHint = originalUrl.contains(":11434") ? "(Ollama)" : "(vLLM/Otro)";

            System.out.println("[OK] Configuracion restaurada a valores ORIGINALES:");
            System.out.println("   URL:    " + originalUrl + " " + providerHint);
            System.out.println("   Modelo: " + originalModel);
            System.out.println("   Modo:   " + originalMode);
            System.out.println();
            System.out.println("[WARN] Nota: Los cambios aplicaran en la proxima operacion LLM.");
            System.out.println("          Escribe cualquier mensaje para probar la conexión.");
        } catch (Exception e) {
            System.out.println("[ERROR] Error restaurando configuracion: " + e.getMessage());
        }
        System.out.println();
    }

    private void executeWizard(ExecutionContext ctx) {
        System.out.println("\nWIZARD DE CONFIGURACION LLM");
        System.out.println("─────────────────────────────────────");

        LlmProviderDiscovery discovery = new LlmProviderDiscovery();
        ConfigPriorityResolver resolver = new ConfigPriorityResolver();

        String currentUrl = resolver.resolveServerUrl(null);
        String currentModel = resolver.resolveModelName(null);

        System.out.println("Configuracion actual:");
        System.out.println("  URL:    " + currentUrl);
        System.out.println("  Modelo: " + currentModel);
        System.out.println();

        System.out.println("Buscando proveedores disponibles...");
        LlmProviderDiscovery.DiscoveryResult result =
            discovery.discoverBestProvider(null, null, null);

        System.out.println();

        if (result.success()) {
            System.out.println("┌─────────────────────────────────────────────┐");
            System.out.println("│  PROVEEDOR ENCONTRADO                       │");
            System.out.println("├─────────────────────────────────────────────┤");
            System.out.println("│  URL:    " + padRight(result.serverUrl(), 32) + " │");
            System.out.println("│  Modelo: " + padRight(result.modelName(), 32) + " │");
            System.out.println("└─────────────────────────────────────────────┘");
            System.out.println();

            FararoniCore core = getFararoniCore(ctx);
            boolean nativeAvailable = NativeLoader.isNativeLibraryAvailable() &&
                                      LocalLlmConfig.fromEnvironment().isModelDownloaded();

            System.out.println("Selecciona una opcion:");
            System.out.println();
            System.out.println("  [1] Mantener configuracion actual");
            System.out.println("      No hacer cambios, conservar: " + currentModel);
            System.out.println();
            System.out.println("  [2] Usar modelo auto-detectado");
            System.out.println("      Aplicar: " + result.modelName());
            System.out.println();
            System.out.println("  [3] Elegir otro modelo de la lista");
            System.out.println("      Sustituir temporalmente el modelo Rabbit (1.5B)");
            System.out.println("      por un modelo mas grande para tareas complejas");
            System.out.println();
            if (nativeAvailable) {
                System.out.println("  [4] Restaurar GGUF nativo (" + AppDefaults.DEFAULT_RABBIT_MODEL + ")");
                System.out.println("      Motor local, sin internet, mas rapido");
                System.out.println();
            }

            System.out.print("Tu opcion [1-" + (nativeAvailable ? "4" : "3") + "]: ");
            String input = readLine(ctx);

            switch (input.trim()) {
                case "1", "" -> {
                    System.out.println("\n[OK] Configuracion sin cambios.");
                }
                case "2" -> {
                    applyConfigurationWithHotSwap(ctx, result.serverUrl(), result.modelName());
                }
                case "3" -> {
                    showModelSelection(ctx, result, currentModel);
                }
                case "4" -> {
                    if (nativeAvailable && core != null) {
                        applyNativeGgufRestore(core);
                    } else {
                        System.out.println("\n[ERROR] Opcion no valida. No se aplicaron cambios.");
                    }
                }
                default -> {
                    System.out.println("\n[ERROR] Opcion no valida. No se aplicaron cambios.");
                }
            }
        } else {
            System.out.println("[ERROR] No se encontraron proveedores LLM activos.");
            System.out.println();
            System.out.println("[TIP] Opciones:");
            System.out.println("   1. Instala Ollama: brew install ollama");
            System.out.println("   2. Descarga un modelo: ollama pull qwen2.5-coder:7b");
            System.out.println("   3. Inicia el servidor: ollama serve");
            System.out.println("   4. Ejecuta /reconfig nuevamente");
        }
        System.out.println();
    }

    private void applyConfigurationWithHotSwap(ExecutionContext ctx, String serverUrl, String modelName) {
        System.out.println("Aplicando configuracion...");

        FararoniCore core = getFararoniCore(ctx);

        if (core != null) {
            try {
                System.out.println("   1. Validando conexion...");
                System.out.println("   2. Ejecutando prueba de inferencia...");
                System.out.println("   3. Aplicando Hot-Swap...");

                long start = System.currentTimeMillis();
                core.hotSwapRabbitClient(serverUrl, modelName);
                long elapsed = System.currentTimeMillis() - start;

                SecureConfigService configService = SecureConfigService.getInstance();
                configService.setProperty("server.url", serverUrl);
                configService.setProperty("model.name", modelName);

                System.out.println();
                System.out.println("[OK] Hot-Swap completado en " + elapsed + "ms");
                System.out.println("     URL:    " + serverUrl);
                System.out.println("     Modelo: " + modelName);
                System.out.println("     Modo:   " + core.getCurrentRabbitMode());
                System.out.println();
                System.out.println("[NOTA] El cambio ya esta activo. El footer se actualizo.");
            } catch (Exception e) {
                System.out.println();
                System.out.println("[ERROR] Hot-Swap fallido: " + e.getMessage());
                System.out.println("        El sistema mantiene su configuracion anterior.");
            }
        } else {
            try {
                SecureConfigService configService = SecureConfigService.getInstance();
                configService.setProperty("server.url", serverUrl);
                configService.setProperty("model.name", modelName);

                System.out.println();
                System.out.println("[OK] Configuracion guardada:");
                System.out.println("     URL:    " + serverUrl);
                System.out.println("     Modelo: " + modelName);
                System.out.println();
                System.out.println("[NOTA] Los cambios aplicaran en la proxima operacion LLM.");
            } catch (Exception e) {
                System.out.println("[ERROR] Error guardando configuracion: " + e.getMessage());
            }
        }
    }

    private void applyNativeGgufRestore(FararoniCore core) {
        System.out.println("Aplicando configuracion...");
        try {
            System.out.println("   1. Validando motor nativo...");
            System.out.println("   2. Ejecutando prueba de inferencia...");
            System.out.println("   3. Aplicando switch a NATIVE...");

            long start = System.currentTimeMillis();
            core.restoreNativeRabbit();
            long elapsed = System.currentTimeMillis() - start;

            System.out.println();
            System.out.println("[OK] Hot-Swap completado en " + elapsed + "ms");
            System.out.println("     Modelo: " + AppDefaults.DEFAULT_RABBIT_MODEL);
            System.out.println("     Modo:   " + core.getCurrentRabbitMode());
            System.out.println();
            System.out.println("[NOTA] El cambio ya esta activo. El footer se actualizo.");
        } catch (Exception e) {
            System.out.println();
            System.out.println("[ERROR] Restauracion GGUF fallida: " + e.getMessage());
            System.out.println("        El sistema mantiene su configuracion anterior.");
        }
    }

    private FararoniCore getFararoniCore(ExecutionContext ctx) {
        if (ctx == null) return null;
        Object coreObj = ctx.getCore();
        if (coreObj instanceof FararoniCore) {
            return (FararoniCore) coreObj;
        }
        return null;
    }

    private void showModelSelection(ExecutionContext ctx, LlmProviderDiscovery.DiscoveryResult result, String currentModel) {
        java.util.List<String> models = result.availableModels();

        if (models == null || models.isEmpty()) {
            System.out.println("\n[X] No hay modelos alternativos disponibles.");
            return;
        }

        FararoniCore core = getFararoniCore(ctx);
        String realRabbitModel = (core != null) ? core.getRabbitModelName() : currentModel;
        boolean nativeAvailable = NativeLoader.isNativeLibraryAvailable() &&
                                  LocalLlmConfig.fromEnvironment().isModelDownloaded();

        System.out.println();
        System.out.println("SUSTITUCION TEMPORAL DEL MODELO RABBIT");
        System.out.println("───────────────────────────────────────");
        System.out.println();
        System.out.println("El modelo Rabbit (1.5B) es rapido pero limitado.");
        System.out.println("Selecciona un modelo mas grande para tareas complejas");
        System.out.println("como analisis de codigo, refactoring o arquitectura.");
        System.out.println();
        System.out.println("Servidor: " + result.serverUrl());
        System.out.println("Actual:   " + realRabbitModel);
        System.out.println();
        System.out.println("MODELOS DISPONIBLES:");

        int offset = 0;
        if (nativeAvailable) {
            String nativeMarker = (core.getCurrentRabbitMode() == FararoniCore.RabbitMode.NATIVE) ? " [actual]" : " [GGUF]";
            System.out.println("  [1] " + AppDefaults.DEFAULT_RABBIT_MODEL + nativeMarker + " (Nativo - offline)");
            offset = 1;
        }

        for (int i = 0; i < models.size(); i++) {
            String modelo = models.get(i);
            String marker = "";
            if (modelo.equals(realRabbitModel) && (core == null || core.getCurrentRabbitMode() != FararoniCore.RabbitMode.NATIVE)) {
                marker = " [actual]";
            } else if (modelo.equals(result.modelName())) {
                marker = " [recomendado]";
            }
            System.out.println("  [" + (i + 1 + offset) + "] " + modelo + marker);
        }
        System.out.println();
        System.out.println("  [0] Cancelar - mantener " + realRabbitModel);
        System.out.println();

        int maxOption = models.size() + offset;
        System.out.print("Selecciona modelo [0-" + maxOption + "]: ");
        String choice = readLine(ctx);

        if (choice.isEmpty() || "0".equals(choice)) {
            System.out.println("\n[OK] Configuracion sin cambios. Se mantiene: " + realRabbitModel);
            return;
        }

        try {
            int selected = Integer.parseInt(choice);
            if (selected < 0 || selected > maxOption) {
                System.out.println("\n[ERROR] Opcion no valida.");
                return;
            }

            if (nativeAvailable && selected == 1) {
                if (core.getCurrentRabbitMode() == FararoniCore.RabbitMode.NATIVE) {
                    System.out.println("\n[OK] El motor nativo ya esta activo. Sin cambios.");
                    return;
                }
                applyNativeGgufRestore(core);
                return;
            }

            int index = selected - 1 - offset;
            if (index < 0 || index >= models.size()) {
                System.out.println("\n[ERROR] Opcion no valida.");
                return;
            }

            String selectedModel = models.get(index);

            if (selectedModel.equals(realRabbitModel)) {
                System.out.println("\n[OK] El modelo seleccionado ya esta activo. Sin cambios.");
                return;
            }

            applyConfigurationWithHotSwap(ctx, result.serverUrl(), selectedModel);
        } catch (NumberFormatException e) {
            System.out.println("\n[ERROR] Entrada no valida. Usa un numero.");
        }
    }

    private void executeSwap(ExecutionContext ctx) {
        System.out.println("\nPROTOCOLO DE HOT-SWAP (Cambio de Motor en Caliente)");
        System.out.println("─────────────────────────────────────────────────────────");

        Object coreObj = ctx.getCore();
        if (coreObj == null || !(coreObj instanceof FararoniCore)) {
            System.out.println("[ERROR] Error: FararoniCore no disponible en este contexto.");
            System.out.println("   Este comando solo funciona dentro de una sesión interactiva.");
            return;
        }
        FararoniCore core = (FararoniCore) coreObj;

        System.out.println("\nEstado Actual:");
        System.out.println("   Modo:   " + core.getCurrentRabbitMode());
        System.out.println("   Modelo: " + core.getRabbitModelName());
        System.out.println();

        LlmProviderDiscovery discovery = new LlmProviderDiscovery();
        LlmProviderDiscovery.DiscoveryResult ollamaResult =
            discovery.discoverBestProvider("http://localhost:11434", null, null);

        if (ollamaResult.success()) {
            System.out.println("Ollama detectado: " + ollamaResult.modelName());
        } else {
            System.out.println("[WARN] Ollama no detectado en localhost:11434");
        }
        System.out.println();

        System.out.println("Selecciona el nuevo modo:");
        System.out.println();
        System.out.println("  [1] NATIVE (GGUF Local)");
        System.out.println("      └─ Motor nativo, sin internet, más rápido");
        System.out.println();
        System.out.println("  [2] REMOTE (Ollama/vLLM)");
        System.out.println("      └─ Servidor externo, modelo configurable");
        System.out.println();
        System.out.println("  [0] Cancelar");
        System.out.println();

        System.out.print("Tu orden [0-2] > ");
        String choice = readLine(ctx);

        if ("0".equals(choice) || choice.isEmpty()) {
            System.out.println("\nOperación cancelada.");
            return;
        }

        try {
            switch (choice) {
                case "1" -> {
                    System.out.println("\nRestaurando motor nativo GGUF...");
                    long start = System.currentTimeMillis();
                    core.restoreNativeRabbit();
                    long elapsed = System.currentTimeMillis() - start;
                    System.out.println("\n[OK] Motor nativo restaurado (" + elapsed + "ms)");
                    System.out.println("   Modo actual: " + core.getCurrentRabbitMode());
                }
                case "2" -> {
                    executeRemoteSwap(ctx, core, ollamaResult);
                }
                default -> {
                    System.out.println("\n[ERROR] Opcion no valida.");
                }
            }
        } catch (Exception e) {
            System.out.println("\nOPERACION FALLIDA");
            System.out.println("   Causa: " + e.getMessage());
            System.out.println("   Estado: El sistema mantiene su configuración anterior.");
        }

        System.out.println();
    }

    private void executeRemoteSwap(ExecutionContext ctx, FararoniCore core, LlmProviderDiscovery.DiscoveryResult detected) {
        System.out.println("\nCONFIGURACION DE PROVEEDOR REMOTO");
        System.out.println("────────────────────────────────────");

        String defaultUrl = detected.success() ? detected.serverUrl() : "http://localhost:11434";
        System.out.print("URL del servidor [" + defaultUrl + "]: ");
        String url = readLine(ctx);
        if (url.isEmpty()) url = defaultUrl;

        String defaultModel = detected.success() ? detected.modelName() : "qwen2.5-coder:7b";
        System.out.print("Nombre del modelo [" + defaultModel + "]: ");
        String model = readLine(ctx);
        if (model.isEmpty()) model = defaultModel;

        if (model.contains("32b") || model.contains("70b")) {
            System.out.println();
            System.out.println("[WARN] ADVERTENCIA: Has seleccionado un modelo grande.");
            System.out.println("   Esto hara que TODAS las operaciones (incluyendo chat casual)");
            System.out.println("   usen el modelo grande, lo cual puede ser mas lento.");
            System.out.print("\n¿Continuar? [s/N]: ");
            String confirm = readLine(ctx).toLowerCase();
            if (!confirm.equals("s") && !confirm.equals("si") && !confirm.equals("y")) {
                System.out.println("\nOperación cancelada.");
                return;
            }
        }

        RabbitPower inferredPower = RabbitPower.fromModelName(model);
        showRabbitPowerInfo(inferredPower);

        System.out.println("\nEjecutando Hot-Swap...");
        System.out.println("   1. Creando cliente candidato...");
        System.out.println("   2. Validando conexión de red...");
        System.out.println("   3. Ejecutando prueba de fuego (inferencia real)...");
        System.out.println("   4. Persistiendo configuración...");
        System.out.println("   5. Intercambio atómico...");
        System.out.println("   6. Actualizando Router Context-Aware...");

        long start = System.currentTimeMillis();
        core.hotSwapRabbitClient(url, model);

        EnterpriseRouter router = core.getRouter();
        if (router != null) {
            router.setRabbitPowerFromModel(model);
        }

        long elapsed = System.currentTimeMillis() - start;

        System.out.println("\n[OK] HOT-SWAP EXITOSO (" + elapsed + "ms)");
        System.out.println("   Enlace: " + url);
        System.out.println("   Modelo: " + model);
        System.out.println("   Modo:   " + core.getCurrentRabbitMode());
        System.out.println("   Potencia: " + inferredPower.displayName);
        System.out.println("   Contexto Máx: " + inferredPower.maxSafeContextTokens + " tokens");
    }

    private void showRabbitPowerInfo(RabbitPower power) {
        System.out.println();
        System.out.println("┌── TRADE-OFFS DE HARDWARE ─────────────────────────┐");
        System.out.println("│                                                                 │");
        System.out.println("│  Modelo más grande = Más inteligente PERO menos memoria libre   │");
        System.out.println("│                                                                 │");
        System.out.println("├─────────────────────────────────────────────────────────────────┤");
        System.out.println("│  Nivel     │ Inteligencia │ RAM Libre │ Contexto Máx │ Uso     │");
        System.out.println("├─────────────────────────────────────────────────────────────────┤");

        for (RabbitPower p : RabbitPower.values()) {
            String marker = p == power ? " ◀" : "  ";
            String intel = switch (p) {
                case WEAK_1B -> "Basica    ";
                case BALANCED_7B -> "Media     ";
                case TITAN_30B -> "Alta      ";
            };
            String ram = switch (p) {
                case WEAK_1B -> "Alta  ";
                case BALANCED_7B -> "Media ";
                case TITAN_30B -> "Baja  ";
            };
            System.out.printf("│  %-9s │ %s │ %s │ %,6d tokens │%s│%n",
                p.displayName, intel, ram, p.maxSafeContextTokens, marker);
        }

        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        if (power == RabbitPower.TITAN_30B) {
            System.out.println();
            System.out.println("[WARN] MODO TITAN ACTIVADO:");
            System.out.println("   • El Rabbit absorberá lógica compleja (código, refactoring)");
            System.out.println("   • Tareas de ARQUITECTURA (análisis profundo) irán a la Tortuga");
            System.out.println("   • Esto protege contra Out-Of-Memory en contextos grandes");
        }
    }

    private String readLine(ExecutionContext ctx) {
        if (ctx != null && ctx.supportsInteractiveInput()) {
            String line = ctx.readLine();
            return line != null ? line.trim() : "";
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = reader.readLine();
            return line != null ? line.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String padRight(String s, int n) {
        if (s == null) s = "";
        return String.format("%-" + n + "s", s.length() > n ? s.substring(0, n) : s);
    }
}
