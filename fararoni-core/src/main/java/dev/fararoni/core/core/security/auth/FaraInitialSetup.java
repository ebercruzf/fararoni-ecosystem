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
package dev.fararoni.core.core.security.auth;

import com.warrenstrange.googleauth.GoogleAuthenticator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Scanner;
import java.util.logging.Logger;

public class FaraInitialSetup {
    private static final Logger LOG = Logger.getLogger(FaraInitialSetup.class.getName());

    private final FaraSecurityManager securityManager;

    public FaraInitialSetup(FaraSecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    public String runFirstTimeSetup() throws IOException {
        Scanner scanner = new Scanner(System.in);

        printBanner();

        System.out.println("  [1/3] VINCULACION CON APP DE AUTENTICACION (2FA)");
        System.out.println("  " + "-".repeat(50));
        System.out.println();

        String secret = securityManager.loadOrGenerateSecret();

        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        boolean verified = false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("  Ingresa el codigo de 6 digitos que ves en tu app: ");
            String codeInput = scanner.nextLine().trim();
            try {
                int code = Integer.parseInt(codeInput);
                if (gAuth.authorize(secret, code)) {
                    System.out.println("  [OK] Codigo verificado correctamente.");
                    System.out.println();
                    verified = true;
                    break;
                } else {
                    System.out.println("  [ERROR] Codigo incorrecto. Intento " + attempt + "/3");
                }
            } catch (NumberFormatException e) {
                System.out.println("  [ERROR] Ingresa solo numeros. Intento " + attempt + "/3");
            }
        }

        if (!verified) {
            System.out.println("  [WARN] No se pudo verificar el codigo. El secreto ya fue guardado.");
            System.out.println("         Podras verificarlo al conectar por WhatsApp/Telegram.");
            System.out.println();
        }

        System.out.println("  [2/3] MASTER PASSWORD (para elevacion de privilegios)");
        System.out.println("  " + "-".repeat(50));
        System.out.println("  Este password te permite acceder a archivos fuera del Sandbox");
        System.out.println("  usando el comando 'sudo <password>' (sesion de 15 min).");
        System.out.println();

        String password = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("  Define tu Master Password (min 8 caracteres): ");
            String pass1 = scanner.nextLine().trim();
            if (pass1.length() < 8) {
                System.out.println("  [ERROR] Minimo 8 caracteres. Intento " + attempt + "/3");
                continue;
            }
            System.out.print("  Confirma tu Master Password: ");
            String pass2 = scanner.nextLine().trim();
            if (!pass1.equals(pass2)) {
                System.out.println("  [ERROR] Los passwords no coinciden. Intento " + attempt + "/3");
                continue;
            }
            password = pass1;
            break;
        }

        if (password != null) {
            securityManager.setupMasterPassword(password);
            System.out.println("  [OK] Master Password guardado con cifrado BCrypt.");
        } else {
            System.out.println("  [WARN] Master Password no configurado. 'sudo' no estara disponible.");
        }
        System.out.println();

        System.out.println("  [3/3] CARPETA DE CONFIANZA (Sandbox)");
        System.out.println("  " + "-".repeat(50));
        System.out.println("  Define la carpeta donde el agente puede leer/escribir archivos.");
        System.out.println("  Todo lo que este fuera requiere 'sudo' para acceder.");
        System.out.println();

        String defaultPath = System.getProperty("user.home") + "/Documents/Proyectos";
        System.out.print("  Ruta de confianza [" + defaultPath + "]: ");
        String workspace = scanner.nextLine().trim();
        if (workspace.isEmpty()) {
            workspace = defaultPath;
        }

        Path workspacePath = Paths.get(workspace);
        if (!Files.isDirectory(workspacePath)) {
            System.out.println("  [WARN] La ruta no existe. Se usara cuando la crees.");
        }

        saveSecurityYml(workspace);

        printSummary(secret, password != null, workspace);

        return secret;
    }

    private void saveSecurityYml(String allowedPath) throws IOException {
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

        Files.createDirectories(SecurityConstants.SECURITY_YML.getParent());
        Files.writeString(SecurityConstants.SECURITY_YML, yml);
        try {
            Files.setPosixFilePermissions(SecurityConstants.SECURITY_YML,
                    PosixFilePermissions.fromString(SecurityConstants.POSIX_OWNER_GROUP_READ));
        } catch (UnsupportedOperationException ignored) {
        }
        LOG.info("[SETUP] security.yml guardado en: " + SecurityConstants.SECURITY_YML);
    }

    private void printBanner() {
        System.out.println();
        System.out.println("=".repeat(65));
        System.out.println("   FARARONI — CONFIGURACION DE SEGURIDAD (Primera Ejecucion)");
        System.out.println("   Estrategia de 3 Llaves: TOTP + Sandbox + BCrypt Sudo");
        System.out.println("=".repeat(65));
        System.out.println();
    }

    private void printSummary(String secret, boolean hasPassword, String workspace) {
        System.out.println();
        System.out.println("=".repeat(65));
        System.out.println("   CONFIGURACION COMPLETADA");
        System.out.println("=".repeat(65));
        System.out.println();
        System.out.println("   Llave 1 (TOTP):     Activa — Google Authenticator vinculado");
        System.out.println("   Llave 2 (Sandbox):  Activa — " + workspace);
        System.out.println("   Llave 3 (BCrypt):   " + (hasPassword ? "Activa" : "No configurada"));
        System.out.println();
        System.out.println("   Archivos creados:");
        System.out.println("     - " + SecurityConstants.SECRET_PATH);
        if (hasPassword) {
            System.out.println("     - " + SecurityConstants.MASTER_PATH);
        }
        System.out.println("     - " + SecurityConstants.SECURITY_YML);
        System.out.println();
        System.out.println("   El CLI local NO requiere 2FA (ya estas en la maquina).");
        System.out.println("   WhatsApp, Telegram y canales externos SI piden codigo TOTP.");
        System.out.println();
        System.out.println("=".repeat(65));
        System.out.println();
    }
}
