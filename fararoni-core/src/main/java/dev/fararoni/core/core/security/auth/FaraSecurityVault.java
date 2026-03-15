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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class FaraSecurityVault implements ISecurityVault {
    private static final Logger LOG = Logger.getLogger(FaraSecurityVault.class.getName());
    private static final DateTimeFormatter AUDIT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();
    private final String masterSecret;
    private final String hashedMasterPassword;

    private final ConcurrentHashMap<String, Long> activeSessions = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> adminSessions = new ConcurrentHashMap<>();

    public FaraSecurityVault(String masterSecret, String hashedMasterPassword) {
        this.masterSecret = masterSecret;
        this.hashedMasterPassword = hashedMasterPassword;
        LOG.info("[SECURITY-VAULT] Vault inicializado. TOTP=activo, BCrypt=" +
                (hashedMasterPassword != null ? "activo" : "no-configurado"));
    }

    @Override
    public boolean isSessionValid(String channelId) {
        Long expiry = activeSessions.get(channelId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            activeSessions.remove(channelId);
            auditLog("SESSION_EXPIRED", channelId, "Sesion TOTP expirada");
            return false;
        }
        return true;
    }

    @Override
    public boolean validateAndLogin(String channelId, int totpCode) {
        boolean valid = gAuth.authorize(masterSecret, totpCode);
        if (valid) {
            long expiry = System.currentTimeMillis() + SecurityConstants.SESSION_DURATION_MS;
            activeSessions.put(channelId, expiry);
            auditLog("LOGIN", channelId, "SUCCESS");
            LOG.info("[SECURITY-VAULT] Sesion TOTP creada para: " + channelId);
        } else {
            auditLog("LOGIN", channelId, "FAILED — codigo incorrecto");
            LOG.warning("[SECURITY-VAULT] Intento de login fallido para: " + channelId);
        }
        return valid;
    }

    @Override
    public void invalidateSession(String channelId) {
        activeSessions.remove(channelId);
        adminSessions.remove(channelId);
        auditLog("LOGOUT", channelId, "MANUAL");
        LOG.info("[SECURITY-VAULT] Sesion terminada manualmente para: " + channelId);
    }

    @Override
    public boolean isAdminSessionValid(String channelId) {
        Long expiry = adminSessions.get(channelId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            adminSessions.remove(channelId);
            auditLog("SUDO_EXPIRED", channelId, "Sesion admin expirada (15 min)");
            return false;
        }
        return true;
    }

    @Override
    public boolean elevatePrivileges(String channelId, String rawPassword) {
        if (hashedMasterPassword == null) {
            LOG.warning("[SECURITY-VAULT] Master Password no configurado. Sudo no disponible.");
            return false;
        }
        boolean valid = org.mindrot.jbcrypt.BCrypt.checkpw(rawPassword, hashedMasterPassword);
        if (valid) {
            long expiry = System.currentTimeMillis() + SecurityConstants.ADMIN_DURATION_MS;
            adminSessions.put(channelId, expiry);
            auditLog("SUDO", channelId, "SUCCESS duration=15min");
            LOG.info("[SECURITY-VAULT] Privilegios elevados para: " + channelId);
        } else {
            auditLog("SUDO", channelId, "FAILED — password incorrecto");
            LOG.warning("[SECURITY-VAULT] Intento de sudo fallido para: " + channelId);
        }
        return valid;
    }

    private void auditLog(String action, String channelId, String detail) {
        try {
            Files.createDirectories(SecurityConstants.AUDIT_DIR);
            String entry = String.format("[%s] [%s] channelId=%s %s%n",
                    LocalDateTime.now().format(AUDIT_FMT), action, channelId, detail);
            Files.writeString(SecurityConstants.AUDIT_LOG, entry,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warning("[SECURITY-VAULT] Error escribiendo audit log: " + e.getMessage());
        }
    }
}
