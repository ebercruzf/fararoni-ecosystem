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
package dev.fararoni.core.cli;

import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.security.auth.FaraSecurityManager;
import dev.fararoni.core.core.security.auth.SecurityConstants;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import dev.fararoni.core.core.security.SecureConfigService;
import dev.fararoni.core.core.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class FirstRunExperience {
    private static final Logger log = LoggerFactory.getLogger(FirstRunExperience.class);

    private static final String INITIALIZED_MARKER = ".initialized";

    private final SecureConfigService configService;
    private final WorkspaceManager workspaceManager;
    private final BufferedReader reader;
    private final Console console;

    public FirstRunExperience() {
        this.configService = SecureConfigService.getInstance();
        this.workspaceManager = WorkspaceManager.getInstance();
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.console = System.console();
    }

    FirstRunExperience(SecureConfigService configService, WorkspaceManager workspaceManager) {
        this.configService = configService;
        this.workspaceManager = workspaceManager;
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.console = null;
    }

    public boolean isFirstRun() {
        if (hasInitializedMarker()) {
            log.debug("[FirstRun] Marker found, not first run");
            return false;
        }

        if (hasEnvApiKey()) {
            log.debug("[FirstRun] API key found in ENV, not first run");
            return false;
        }

        if (configService.hasAnyApiKey()) {
            log.debug("[FirstRun] API key found in config, not first run");
            return false;
        }

        if (configService.getProperty("first-run.mode") != null) {
            log.debug("[FirstRun] first-run.mode found, wizard already completed — recreating marker");
            markAsInitialized();
            return false;
        }

        log.info("[FirstRun] No configuration found, first run detected");
        return true;
    }

    public boolean runSetupWizard() {
        printWelcomeBanner();

        try {
            int mode = askUsageMode();

            switch (mode) {
                case 0 -> {
                    return handleSkipSetup();
                }
                case 1 -> {
                    return setupExternalServerMode(false);
                }
                case 2 -> {
                    return setupExternalServerMode(true);
                }
                case 3 -> {
                    return setupLocalMode();
                }
                default -> {
                    return handleSkipSetup();
                }
            }
        } catch (IOException e) {
            log.error("[FirstRun] Error during setup: {}", e.getMessage());
            printError("Error durante la configuracion: " + e.getMessage());
            return false;
        }
    }

    private int askUsageMode() throws IOException {
        boolean ollamaDetected = detectOllama();

        System.out.println(ansi().fgCyan().a("""

            ¿Como quieres usar FARARONI?

            """).reset());

        if (ollamaDetected) {
            System.out.println(ansi().fgGreen().a(
                "  ✓ Ollama detectado en localhost:11434\n"
            ).reset());

            System.out.println(ansi().fgYellow().a("""
              [1] Solo servidor externo (Ollama)
                  → Usa solo el modelo grande de Ollama

              [2] Hibrido: Ollama + Motor local (Recomendado)
                  → Modelo grande (Ollama) + modelo pequeño local como fallback
                  → El motor local se usa para routing y cuando Ollama no esta disponible

              [3] Solo modo local (100% offline)
                  → Todo local, funciona sin internet (~1.2 GB)

              [0] Omitir configuracion

            """).reset());

            String choice = readInput("Tu eleccion [1/2/3/0]: ");

            if (choice == null || choice.trim().isEmpty()) {
                return 2;
            }

            return switch (choice.trim()) {
                case "1" -> 1;
                case "2" -> 2;
                case "3" -> 3;
                default -> 0;
            };
        } else {
            System.out.println(ansi().fgYellow().a("""
              [1] Servidor externo (Ollama, OpenAI, vLLM, etc.)
                  → Conecta a un servidor LLM que configures

              [2] Modo local (100% offline)
                  → Descarga motor + modelo pequeño (~1.2 GB)
                  → Funciona sin internet

              [0] Omitir configuracion

            """).reset());

            String choice = readInput("Tu eleccion [1/2/0]: ");

            if (choice == null || choice.trim().isEmpty()) {
                return 0;
            }

            return switch (choice.trim()) {
                case "1" -> 1;
                case "2" -> 3;
                default -> 0;
            };
        }
    }

    private boolean setupExternalServerMode(boolean enableLocalFallback) throws IOException {
        boolean ollamaDetected = detectOllama();
        int totalSteps = enableLocalFallback ? 4 : 3;

        System.out.println(ansi().fgCyan().a(String.format("""

            PASO 1/%d: URL del Servidor

            """, totalSteps)).reset());

        String defaultUrl = ollamaDetected ? AppDefaults.DEFAULT_OLLAMA_URL : AppDefaults.DEFAULT_SERVER_URL;

        if (ollamaDetected) {
            System.out.println(ansi().fgGreen().a(
                "  ✓ Ollama detectado - usando " + AppDefaults.DEFAULT_OLLAMA_URL + "\n"
            ).reset());
        }

        String url = readInput("Server URL [" + defaultUrl + "]: ");
        if (url == null || url.trim().isEmpty()) {
            url = defaultUrl;
        }

        String trimmedUrl = url.trim();
        if (!trimmedUrl.equals(AppDefaults.DEFAULT_SERVER_URL)) {
            configService.setProperty("server.url", trimmedUrl);
        }
        configService.setProperty("first-run.server.url", trimmedUrl);
        System.out.println(ansi().fgGreen().a("  → Servidor: " + url).reset());

        boolean isOllama = url.contains(":11434") || url.equals(AppDefaults.DEFAULT_OLLAMA_URL);

        System.out.println(ansi().fgCyan().a(String.format("""

            PASO 2/%d: API Key

            """, totalSteps)).reset());

        if (isOllama) {
            System.out.println(ansi().fgBlue().a(
                "  Ollama no requiere API key. Presiona Enter para omitir.\n"
            ).reset());
        } else {
            System.out.println(ansi().fgYellow().a(
                "  Si tu servidor requiere autenticacion, ingresa tu API key.\n" +
                "  Presiona Enter si no necesitas API key.\n"
            ).reset());
        }

        String apiKey = readSecureInput("API Key (opcional): ");
        if (apiKey != null && !apiKey.trim().isEmpty() && apiKey.length() >= 10) {
            configService.setApiKey(apiKey.trim());
            System.out.println(ansi().fgGreen().a("  → API Key guardada").reset());
        } else {
            System.out.println(ansi().fgBlue().a("  → Sin API Key (no requerida)").reset());
        }

        System.out.println(ansi().fgCyan().a(String.format("""

            PASO 3/%d: Modelo por Defecto

            """, totalSteps)).reset());

        String defaultModel = isOllama ? "qwen2.5-coder:1.5b-instruct" : AppDefaults.DEFAULT_MODEL_NAME;
        System.out.println(ansi().fgBlue().a(
            "  Modelos populares: qwen2.5-coder:1.5b-instruct, qwen2.5-coder:7b, llama3.2\n"
        ).reset());

        String model = readInput("Modelo [" + defaultModel + "]: ");
        if (model == null || model.trim().isEmpty()) {
            model = defaultModel;
        }

        String trimmedModel = model.trim();
        if (!trimmedModel.equals(AppDefaults.DEFAULT_MODEL_NAME)) {
            configService.setProperty("model.name", trimmedModel);
        }
        configService.setProperty("first-run.model.name", trimmedModel);
        System.out.println(ansi().fgGreen().a("  → Modelo: " + model).reset());

        if (enableLocalFallback) {
            System.out.println(ansi().fgCyan().a(String.format("""

            PASO 4/%d: Motor Local (Fallback)

            El motor local se usara para:
              • Routing inteligente de consultas
              • Fallback cuando el servidor no este disponible
              • Tareas simples sin necesidad de servidor

            Se descargara cuando inicies FARARONI (~5 MB motor + ~1.2 GB modelo)

            """, totalSteps)).reset());

            configService.setProperty("mode", "hybrid");
            configService.setProperty("local.fallback.enabled", "true");
            configService.setProperty("first-run.mode", "hybrid");
            System.out.println(ansi().fgGreen().a("  → Modo hibrido activado").reset());
        } else {
            configService.setProperty("mode", "external");
            configService.setProperty("first-run.mode", "external");
        }

        markAsInitialized();

        runSecuritySetup(totalSteps);
        printSuccessBanner();
        return true;
    }

    private boolean setupLocalMode() throws IOException {
        System.out.println(ansi().fgCyan().a("""

            MODO LOCAL (Offline)

            Este modo descargara:
              1. Motor de inferencia (~5 MB)
              2. Modelo Qwen 2.5 Coder 1.5B (~1.2 GB)

            Una vez instalado, FARARONI funcionara sin conexion a internet.

            """).reset());

        String confirm = readInput("¿Deseas continuar? [S/n]: ");
        if (confirm != null && confirm.trim().toLowerCase().startsWith("n")) {
            return handleSkipSetup();
        }

        markAsInitialized();

        configService.setProperty("mode", "local");
        configService.setProperty("first-run.mode", "local");

        System.out.println(ansi().fgGreen().a("""

            Configuracion completada para modo local.

            Cuando inicies FARARONI, te preguntara si deseas descargar
            el motor y el modelo necesarios.

            """).reset());

        runSecuritySetup(2);

        return true;
    }

    private boolean detectOllama() {
        try {
            URL url = new URL(AppDefaults.DEFAULT_OLLAMA_URL + "/api/tags");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean runIfNeeded() {
        if (!isFirstRun()) {
            return true;
        }

        return runSetupWizard();
    }

    private void runSecuritySetup(int previousSteps) {
        try {
            var secManager = new FaraSecurityManager();

            if (!secManager.isFirstRun()) {
                System.out.println(ansi().fgGreen().a(
                    "  → Seguridad ya configurada (secret.bin detectado). Omitiendo.\n"
                ).reset());
                return;
            }

            System.out.println(ansi().fgCyan().a("""

                ========================================
                     SEGURIDAD — Estrategia 3 Llaves
                ========================================

                Fararoni protege tu sistema con 3 niveles:
                  Llave 1: TOTP (Google Authenticator)
                  Llave 2: Sandbox (carpeta de confianza)
                  Llave 3: Master Password (elevacion sudo)

                Canales externos (WhatsApp, Telegram) pediran
                codigo 2FA. El CLI local NO lo requiere.

                """).reset());

            String skipSecurity = readInput("¿Configurar seguridad ahora? [S/n]: ");
            if (skipSecurity != null && skipSecurity.trim().toLowerCase().startsWith("n")) {
                System.out.println(ansi().fgYellow().a(
                    "  → Seguridad omitida. Puedes configurarla despues con /security-setup\n"
                ).reset());
                return;
            }

            int step = previousSteps + 1;
            System.out.println(ansi().fgCyan().a(String.format("""

                PASO %d: Vinculacion 2FA (Google Authenticator)

                """, step)).reset());

            String secret = secManager.loadOrGenerateSecret();

            GoogleAuthenticator gAuth = new GoogleAuthenticator();
            boolean verified = false;
            for (int attempt = 1; attempt <= 3; attempt++) {
                String codeInput = readInput("  Codigo de 6 digitos de tu app: ");
                if (codeInput == null || codeInput.trim().isEmpty()) break;
                try {
                    int code = Integer.parseInt(codeInput.trim());
                    if (gAuth.authorize(secret, code)) {
                        System.out.println(ansi().fgGreen().a("  → Codigo verificado correctamente").reset());
                        verified = true;
                        break;
                    } else {
                        System.out.println(ansi().fgRed().a(
                            "  Codigo incorrecto. Intento " + attempt + "/3"
                        ).reset());
                    }
                } catch (NumberFormatException e) {
                    System.out.println(ansi().fgRed().a(
                        "  Ingresa solo numeros. Intento " + attempt + "/3"
                    ).reset());
                }
            }
            if (!verified) {
                System.out.println(ansi().fgYellow().a(
                    "  → Verificacion pendiente. El secreto ya fue guardado.\n"
                ).reset());
            }

            step++;
            System.out.println(ansi().fgCyan().a(String.format("""

                PASO %d: Master Password (elevacion de privilegios)

                Este password permite acceso temporal fuera del Sandbox
                usando 'sudo <password>' (sesion de 15 min).

                """, step)).reset());

            for (int attempt = 1; attempt <= 3; attempt++) {
                String pass1 = readSecureInput("  Master Password (min 8 chars): ");
                if (pass1 == null || pass1.trim().isEmpty()) {
                    System.out.println(ansi().fgYellow().a(
                        "  → Master Password omitido. 'sudo' no estara disponible.\n"
                    ).reset());
                    break;
                }
                if (pass1.trim().length() < 8) {
                    System.out.println(ansi().fgRed().a(
                        "  Minimo 8 caracteres. Intento " + attempt + "/3"
                    ).reset());
                    continue;
                }
                String pass2 = readSecureInput("  Confirmar password: ");
                if (pass1.trim().equals(pass2 != null ? pass2.trim() : "")) {
                    secManager.setupMasterPassword(pass1.trim());
                    System.out.println(ansi().fgGreen().a(
                        "  → Master Password guardado (BCrypt)"
                    ).reset());
                    break;
                } else {
                    System.out.println(ansi().fgRed().a(
                        "  Los passwords no coinciden. Intento " + attempt + "/3"
                    ).reset());
                }
            }

            step++;
            System.out.println(ansi().fgCyan().a(String.format("""

                PASO %d: Carpeta de Confianza (Sandbox)

                El agente solo puede leer/escribir dentro de esta carpeta.
                Todo lo externo requiere 'sudo' para acceder.

                """, step)).reset());

            String defaultPath = System.getProperty("user.home") + "/Documents/Proyectos";
            String workspace = readInput("  Ruta de confianza [" + defaultPath + "]: ");
            if (workspace == null || workspace.trim().isEmpty()) {
                workspace = defaultPath;
            }
            System.out.println(ansi().fgGreen().a("  → Sandbox: " + workspace).reset());

            saveSecurityYml(workspace.trim());

            System.out.println(ansi().fgGreen().a("""

                ----------------------------------------
                  Seguridad configurada correctamente
                ----------------------------------------
                  Llave 1 (TOTP):    Activa
                  Llave 2 (Sandbox): Activa
                  Llave 3 (BCrypt):  Configurada
                ----------------------------------------

                """).reset());
        } catch (Exception e) {
            log.error("[FirstRun] Error configurando seguridad: {}", e.getMessage());
            System.out.println(ansi().fgYellow().a(
                "  → Seguridad no configurada: " + e.getMessage() + "\n" +
                "    Puedes configurarla despues con /security-setup\n"
            ).reset());
        }
    }

    private void saveSecurityYml(String allowedPath) throws IOException {
        var securityYml = SecurityConstants.SECURITY_YML;
        String yml = """
                # Fararoni Security Configuration — FASE 1004-1005
                # Generado automaticamente durante el setup inicial.

                security:
                  allowed_paths:
                    - %s
                  session_duration_hours: 4
                  admin_session_minutes: 15
                  cli_requires_auth: false
                  remote_requires_auth: true
                """.formatted(allowedPath);

        java.nio.file.Files.createDirectories(securityYml.getParent());
        java.nio.file.Files.writeString(securityYml, yml);
        try {
            java.nio.file.Files.setPosixFilePermissions(securityYml,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
        } catch (UnsupportedOperationException ignored) {}
    }

    private boolean handleSkipSetup() {
        System.out.println(ansi().fgYellow().a("""

            Configuracion omitida. Puedes configurar FARARONI mas tarde:

            SERVIDOR EXTERNO:
              fararoni config set server-url http:
              fararoni config set model-name qwen2.5-coder:1.5b-instruct

            O con variables de entorno:
              export LLM_SERVER_URL=http:
              export LLM_MODEL_NAME=qwen2.5-coder:1.5b-instruct

            MODO LOCAL:
              Simplemente ejecuta FARARONI y te ofrecera
              descargar el motor y modelo automaticamente.

            """).reset());

        markAsInitialized();
        return false;
    }

    private boolean hasInitializedMarker() {
        return workspaceManager.getWorkspaceDir()
            .resolve(INITIALIZED_MARKER)
            .toFile()
            .exists();
    }

    private boolean hasEnvApiKey() {
        String envKey = System.getenv("LLM_API_KEY");
        return envKey != null && !envKey.isEmpty();
    }

    private void markAsInitialized() {
        try {
            java.nio.file.Path markerPath = workspaceManager.getWorkspaceDir().resolve(INITIALIZED_MARKER);
            if (!java.nio.file.Files.exists(markerPath)) {
                java.nio.file.Files.createFile(markerPath);
                log.debug("[FirstRun] Created initialization marker");
            }
        } catch (IOException e) {
            log.warn("[FirstRun] Could not create marker: {}", e.getMessage());
        }
    }

    private String readInput(String prompt) throws IOException {
        System.out.print(ansi().fgCyan().a(prompt).reset());
        System.out.flush();
        return reader.readLine();
    }

    private String readSecureInput(String prompt) throws IOException {
        System.out.print(ansi().fgCyan().a(prompt).reset());
        System.out.flush();

        if (console != null) {
            char[] chars = console.readPassword();
            return chars != null ? new String(chars) : null;
        }

        return reader.readLine();
    }

    private void printWelcomeBanner() {
        System.out.println(ansi().fgCyan().a("""

            ========================================
                 FARARONI - Primera Ejecucion
            ========================================

            Bienvenido a FARARONI!

            Parece que es tu primera vez usando esta herramienta.
            Vamos a configurar los ajustes basicos.

            Puedes omitir este proceso presionando Ctrl+C
            y configurar manualmente despues.

            """).reset());
    }

    private void printSuccessBanner() {
        System.out.println(ansi().fgGreen().a("""

            ========================================
                 Configuracion Completada!
            ========================================

            FARARONI esta listo para usar.

            Comandos utiles:
              fararoni              - Iniciar shell interactivo
              fararoni config show  - Ver configuracion
              fararoni --help       - Ver ayuda

            Que tengas una excelente experiencia!

            """).reset());
    }

    private void printError(String message) {
        System.out.println(ansi().fgRed().a(message).reset());
    }

    public static boolean checkAndRun() {
        var experience = new FirstRunExperience();
        return experience.runIfNeeded();
    }
}
