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
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.logging.Logger;

public class FaraSecurityManager implements ISecuritySetup {
    private static final Logger LOG = Logger.getLogger(FaraSecurityManager.class.getName());
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    @Override
    public boolean isFirstRun() {
        return !Files.exists(SecurityConstants.SECRET_PATH);
    }

    @Override
    public String loadOrGenerateSecret() throws IOException {
        if (Files.exists(SecurityConstants.SECRET_PATH)) {
            LOG.info("[SECURITY] Cargando secreto TOTP existente...");
            return Files.readString(SecurityConstants.SECRET_PATH).trim();
        }

        LOG.warning("[SECURITY] Primera ejecucion detectada. Generando Identidad Maestra...");

        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();

        persistSecureFile(SecurityConstants.SECRET_PATH, secret);

        displayQrSetup(key);

        return secret;
    }

    @Override
    public void setupMasterPassword(String rawPassword) throws IOException {
        String hash = org.mindrot.jbcrypt.BCrypt.hashpw(rawPassword,
                org.mindrot.jbcrypt.BCrypt.gensalt(SecurityConstants.BCRYPT_SALT_ROUNDS));
        persistSecureFile(SecurityConstants.MASTER_PATH, hash);
        LOG.info("[SECURITY] Master Password hasheado y guardado con BCrypt (salt=" +
                SecurityConstants.BCRYPT_SALT_ROUNDS + ")");
    }

    @Override
    public void runFirstTimeSetup() throws IOException {
        throw new UnsupportedOperationException(
                "Use FaraInitialSetup.runFirstTimeSetup() para el wizard interactivo");
    }

    public String loadMasterPasswordHash() {
        try {
            if (Files.exists(SecurityConstants.MASTER_PATH)) {
                return Files.readString(SecurityConstants.MASTER_PATH).trim();
            }
        } catch (IOException e) {
            LOG.severe("[SECURITY] Error cargando master.bin: " + e.getMessage());
        }
        return null;
    }

    void displayQrSetup(GoogleAuthenticatorKey key) {
        String account = System.getProperty("user.name", "usuario");
        String otpAuthUrl = String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                SecurityConstants.TOTP_ISSUER, account, key.getKey(), SecurityConstants.TOTP_ISSUER);

        System.out.println();
        System.out.println("=".repeat(65));
        System.out.println("  CONFIGURACION DE SEGURIDAD — 2FA (Google Authenticator)");
        System.out.println("=".repeat(65));
        System.out.println();
        System.out.println("  1. Abre Google Authenticator (o Authy) en tu celular");
        System.out.println("  2. Escanea este codigo directamente desde tu terminal:");

        new ConsoleQrRenderer().printQrToConsole(otpAuthUrl);

        System.out.println("     CLAVE MANUAL: " + key.getKey());
        System.out.println();
        System.out.println("  3. Fararoni te pedira el codigo de 6 digitos al conectar");
        System.out.println("     por WhatsApp, Telegram o cualquier canal externo.");
        System.out.println();
        System.out.println("=".repeat(65));
        System.out.println();
    }

    private void persistSecureFile(java.nio.file.Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        try {
            Files.setPosixFilePermissions(path,
                    PosixFilePermissions.fromString(SecurityConstants.POSIX_OWNER_ONLY));
        } catch (UnsupportedOperationException ignored) {
        }
    }
}
