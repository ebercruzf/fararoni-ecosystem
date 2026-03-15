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
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.security.SecureConfigService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * @author Eber Cruz
 * @version 1.0.0 (FASE 92.1)
 */
public class ReconfigTurtleCommand implements ConsoleCommand {
    @Override
    public String getTrigger() {
        return "/reconfigt";
    }

    @Override
    public String getDescription() {
        return "Reconfigura el modelo Turtle (experto) en caliente";
    }

    @Override
    public String getUsage() {
        return "/reconfigt";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIG;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/turtle" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /reconfigt - Reconfigura el modelo Turtle (experto) en caliente

            Uso:
              /reconfigt         Wizard interactivo para cambiar el modelo grande

            Este comando te permite:
              • Cambiar el modelo experto (Turtle) sin reiniciar
              • Usar Ollama/vLLM remoto como modelo grande
              • Usar Claude (Anthropic API) como modelo grande
              • Desactivar Claude y volver al modelo Ollama anterior

            El Rabbit (modelo rapido) NO se modifica con este comando.
            Usa /reconfig swap para cambiar el Rabbit.

            Ejemplos:
              /reconfigt         # Wizard para cambiar Turtle
              /turtle            # Alias del mismo comando

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        System.out.println();
        System.out.println("HOT-SWAP TURTLE (Cambio de Modelo Experto)");
        System.out.println("═══════════════════════════════════════════════");

        Object coreObj = ctx.getCore();
        if (coreObj == null || !(coreObj instanceof FararoniCore)) {
            System.out.println("[ERROR] FararoniCore no disponible en este contexto.");
            System.out.println("   Este comando solo funciona dentro de una sesion interactiva.");
            return;
        }
        FararoniCore core = (FararoniCore) coreObj;

        System.out.println();
        System.out.println("Estado Actual:");
        System.out.println("   Turtle:  " + core.getTurtleModelName());
        if (core.isClaudeActive()) {
            System.out.println("   Claude:  ACTIVO (" + core.getClaudeModelName() + ")");
        } else {
            System.out.println("   Claude:  INACTIVO");
        }
        System.out.println("   Rabbit:  " + core.getRabbitModelName() + " (no se modifica aqui)");
        System.out.println();

        LlmProviderDiscovery discovery = new LlmProviderDiscovery();
        LlmProviderDiscovery.DiscoveryResult ollamaResult =
            discovery.discoverBestProvider("http://localhost:11434", null, null);

        if (ollamaResult.success()) {
            System.out.println("[OK] Ollama detectado: " + ollamaResult.serverUrl());
        } else {
            System.out.println("[INFO] Ollama no detectado en localhost:11434");
        }
        System.out.println();

        System.out.println("Selecciona el nuevo modo para Turtle:");
        System.out.println();
        System.out.println("  [1] REMOTE (Ollama/vLLM)");
        System.out.println("      └─ Servidor externo, modelo grande configurable");
        System.out.println();
        System.out.println("  [2] CLAUDE (Anthropic API)");
        System.out.println("      └─ Cloud, modelo Claude (Sonnet/Opus/Haiku)");
        System.out.println();
        if (core.isClaudeActive()) {
            System.out.println("  [3] DESACTIVAR Claude");
            System.out.println("      └─ Volver al modelo Ollama anterior");
            System.out.println();
        }
        System.out.println("  [0] Cancelar");
        System.out.println();

        System.out.print("Tu orden > ");
        String choice = readLine(ctx);

        if ("0".equals(choice) || choice.isEmpty()) {
            System.out.println("\nOperacion cancelada.");
            return;
        }

        try {
            switch (choice) {
                case "1" -> executeRemoteTurtleSwap(ctx, core, ollamaResult);
                case "2" -> executeClaudeSwap(ctx, core);
                case "3" -> {
                    if (core.isClaudeActive()) {
                        core.deactivateClaudeAsTurtle();
                        System.out.println("\n[OK] Claude DESACTIVADO. Turtle vuelve a: " + core.getTurtleModelName());
                    } else {
                        System.out.println("\n[INFO] Claude no estaba activo.");
                    }
                }
                default -> System.out.println("\n[ERROR] Opcion no valida.");
            }
        } catch (Exception e) {
            System.out.println("\nOPERACION FALLIDA");
            System.out.println("   Causa: " + e.getMessage());
            System.out.println("   Estado: El sistema mantiene su configuracion anterior.");
        }

        System.out.println();
    }

    private void executeRemoteTurtleSwap(ExecutionContext ctx, FararoniCore core,
                                          LlmProviderDiscovery.DiscoveryResult detected) {
        System.out.println("\nSELECCION DE MODELO TURTLE (Ollama/vLLM)");
        System.out.println("───────────────────────────────────────────");

        System.out.println();
        System.out.println("Buscando modelos disponibles...");
        LlmProviderDiscovery discovery = new LlmProviderDiscovery();
        LlmProviderDiscovery.DiscoveryResult result =
            discovery.discoverBestProvider(null, null, null);

        if (!result.success() || result.availableModels() == null || result.availableModels().isEmpty()) {
            System.out.println("[ERROR] No se encontraron modelos en Ollama.");
            System.out.println("   Verifica que Ollama este corriendo: ollama serve");
            return;
        }

        java.util.List<String> models = result.availableModels();
        String currentTurtle = core.getTurtleModelName();

        System.out.println();
        System.out.println("Servidor: " + result.serverUrl());
        System.out.println("Actual:   " + currentTurtle);
        System.out.println();
        System.out.println("MODELOS DISPONIBLES:");

        for (int i = 0; i < models.size(); i++) {
            String modelo = models.get(i);
            String marker = "";
            if (modelo.equals(currentTurtle)) {
                marker = " [actual]";
            } else if (modelo.equals(result.modelName())) {
                marker = " [recomendado]";
            }
            System.out.println("  [" + (i + 1) + "] " + modelo + marker);
        }
        System.out.println();
        System.out.println("  [0] Cancelar - mantener " + currentTurtle);
        System.out.println();

        System.out.print("Selecciona modelo [0-" + models.size() + "]: ");
        System.out.flush();
        String choice = readLine(ctx);

        if (choice.isEmpty() || "0".equals(choice)) {
            System.out.println("\n[OK] Sin cambios. Se mantiene: " + currentTurtle);
            return;
        }

        try {
            int index = Integer.parseInt(choice) - 1;
            if (index < 0 || index >= models.size()) {
                System.out.println("\n[ERROR] Opcion fuera de rango.");
                return;
            }

            String selectedModel = models.get(index);

            if (selectedModel.equals(currentTurtle) && !core.isClaudeActive()) {
                System.out.println("\n[OK] El modelo seleccionado ya esta activo. Sin cambios.");
                return;
            }

            if (core.isClaudeActive()) {
                core.deactivateClaudeAsTurtle();
                System.out.println("\n[INFO] Claude desactivado. Turtle vuelve a Ollama.");
            }

            System.out.println("\nEjecutando Hot-Swap de Turtle...");
            System.out.println("   1. Creando cliente candidato...");
            System.out.println("   2. Validando conexion de red...");
            System.out.println("   3. Ejecutando prueba de fuego (inferencia real)...");
            System.out.println("   4. Persistiendo configuracion...");
            System.out.println("   5. Intercambio atomico...");

            long start = System.currentTimeMillis();
            core.hotSwapTurtleClient(result.serverUrl(), selectedModel);
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("\n[OK] HOT-SWAP TURTLE EXITOSO (" + elapsed + "ms)");
            System.out.println("   Enlace: " + result.serverUrl());
            System.out.println("   Modelo: " + selectedModel);
        } catch (NumberFormatException e) {
            System.out.println("\n[ERROR] Entrada no valida. Usa un numero.");
        }
    }

    private void executeClaudeSwap(ExecutionContext ctx, FararoniCore core) {
        System.out.println("\nCONFIGURACION DE CLAUDE COMO TURTLE");
        System.out.println("──────────────────────────────────────");

        String apiKey = System.getenv(AppDefaults.ENV_CLAUDE_API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("ANTHROPIC_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = SecureConfigService.getInstance().getSecureProperty("claude.api.key");
        }

        if (apiKey != null && !apiKey.isBlank()) {
            String maskedKey = apiKey.substring(0, Math.min(8, apiKey.length())) + "..." +
                apiKey.substring(Math.max(0, apiKey.length() - 4));
            System.out.println("[OK] API Key detectada: " + maskedKey);
            System.out.print("¿Usar esta key? [S/n]: ");
            String confirm = readLine(ctx).toLowerCase();
            if (confirm.equals("n") || confirm.equals("no")) {
                apiKey = null;
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.out.print("Ingresa tu API Key de Anthropic: ");
            apiKey = readLine(ctx);
            if (apiKey.isBlank()) {
                System.out.println("\n[ERROR] API Key es requerida.");
                return;
            }
        }

        System.out.println();
        System.out.println("Selecciona modelo Claude:");
        System.out.println();
        System.out.println("  [1] claude-sonnet-4-6      (Recomendado - rapido y capaz)");
        System.out.println("  [2] claude-opus-4-6        (Maximo poder, mas lento)");
        System.out.println("  [3] claude-haiku-4-5-20251001  (Economico, rapido)");
        System.out.println("  [0] Escribir nombre manualmente");
        System.out.println();
        System.out.print("Tu opcion [1-3] o [0]: ");
        String modelChoice = readLine(ctx);

        String model = switch (modelChoice) {
            case "1", "" -> "claude-sonnet-4-6";
            case "2" -> "claude-opus-4-6";
            case "3" -> "claude-haiku-4-5-20251001";
            case "0" -> {
                System.out.print("Nombre del modelo: ");
                String manual = readLine(ctx);
                yield manual.isBlank() ? "claude-sonnet-4-6" : manual;
            }
            default -> {
                System.out.println("[WARN] Opcion no reconocida, usando Sonnet.");
                yield "claude-sonnet-4-6";
            }
        };

        System.out.println("\nValidando conexion con Claude API...");
        try {
            boolean pingOk = testClaudePing(apiKey, model);
            if (!pingOk) {
                System.out.println("[ERROR] Claude API no respondio correctamente.");
                System.out.println("   Verifica tu API key y modelo.");
                return;
            }
            System.out.println("[OK] Claude API respondio correctamente.");
        } catch (Exception e) {
            System.out.println("[ERROR] Error conectando a Claude: " + e.getMessage());
            return;
        }

        core.activateClaudeAsTurtle(apiKey, model);

        System.out.println();
        System.out.println("[OK] CLAUDE ACTIVADO COMO TURTLE");
        System.out.println("   Modelo: " + model);
        System.out.println("   Rabbit: " + core.getRabbitModelName() + " (sin cambios)");
        System.out.println();
        System.out.println("   Las tareas complejas ahora las atiende Claude.");
        System.out.println("   Chat casual sigue con el Rabbit local (ahorro de tokens).");
        System.out.println();
        System.out.println("   Para desactivar: /reconfigt → opcion [3]");
    }

    private boolean testClaudePing(String apiKey, String model) throws Exception {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

        String body = """
            {"model":"%s","max_tokens":5,"messages":[{"role":"user","content":"ping"}]}
            """.formatted(model).trim();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(AppDefaults.DEFAULT_CLAUDE_BASE_URL + "/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", AppDefaults.ANTHROPIC_API_VERSION)
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return true;
        }

        System.out.println("   HTTP " + response.statusCode() + ": " + response.body());
        return false;
    }

    private String readLine(ExecutionContext ctx) {
        System.out.flush();
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
}
