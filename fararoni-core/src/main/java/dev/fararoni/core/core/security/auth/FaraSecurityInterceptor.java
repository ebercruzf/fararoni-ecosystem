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

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FaraSecurityInterceptor implements ISecurityInterceptor {
    private static final Logger LOG = Logger.getLogger(FaraSecurityInterceptor.class.getName());
    private static final DateTimeFormatter AUDIT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern NL_ADD_GUEST = Pattern.compile(
        "(?i)(?:fara\\s+)?(?:agrega|añade|autoriza|invita|permite|da acceso|registra).*" +
        "(?:invitado|guest|numero|número|contacto|usuario).*?" +
        "([+]?[\\d\\s()-]{7,20}|\\S+@\\S+|\\w+[:#]\\S+)");
    private static final Pattern NL_REMOVE_GUEST = Pattern.compile(
        "(?i)(?:fara\\s+)?(?:quita|elimina|remueve|revoca|bloquea|borra).*" +
        "(?:invitado|guest|numero|número|contacto|usuario).*?" +
        "([+]?[\\d\\s()-]{7,20}|\\S+@\\S+|\\w+[:#]\\S+)");
    private static final Pattern NL_LIST_GUESTS = Pattern.compile(
        "(?i)(?:fara\\s+)?(?:lista|muestra|dame|ver|quienes son|cuantos).*(?:invitados|guests)");

    private final FaraSecurityVault vault;
    private final IGuestManager guestManager;

    private final BiFunction<String, Boolean, String> chatHandler;

    private final Function<String, String> chatReadOnlyHandler;

    public FaraSecurityInterceptor(
            FaraSecurityVault vault,
            IGuestManager guestManager,
            BiFunction<String, Boolean, String> chatHandler,
            Function<String, String> chatReadOnlyHandler) {
        this.vault = vault;
        this.guestManager = guestManager;
        this.chatHandler = chatHandler;
        this.chatReadOnlyHandler = chatReadOnlyHandler;
        LOG.info("[SECURITY-INTERCEPTOR] Cadenero inicializado");
    }

    @Override
    public String processSecureRequest(String channelId, String message) {
        String cleanMessage = message.trim();
        String lowerMessage = cleanMessage.toLowerCase();

        if (isLogoutCommand(lowerMessage)) {
            return handleLogout(channelId);
        }

        if (guestManager.isGuest(channelId)) {
            return handleGuestMessage(channelId, cleanMessage);
        }

        if (!vault.isSessionValid(channelId)) {
            return handleUnauthenticated(channelId, cleanMessage);
        }

        String gestionResult = handleGestionCommands(channelId, lowerMessage, cleanMessage);
        if (gestionResult != null) {
            return gestionResult;
        }

        if (lowerMessage.startsWith(SecurityConstants.SUDO_PREFIX)) {
            return handleSudo(channelId, cleanMessage);
        }

        boolean isAdmin = vault.isAdminSessionValid(channelId);
        return chatHandler.apply(cleanMessage, isAdmin);
    }

    @Override
    public String processSecureRequest(String channel, String senderId, String message) {
        String channelId = channel + ":" + senderId;
        return processSecureRequest(channelId, message);
    }

    private String handleLogout(String channelId) {
        if (vault.isSessionValid(channelId)) {
            vault.invalidateSession(channelId);
            auditLog("LOGOUT", channelId, "MANUAL");
            return "Sesion cerrada correctamente. Fararoni esta bloqueado.\n" +
                   "Ingresa tu codigo de 6 digitos para volver a acceder.";
        }
        return "Fararoni ya se encuentra bloqueado.";
    }

    private String handleUnauthenticated(String channelId, String message) {
        if (message.matches(SecurityConstants.TOTP_PATTERN)) {
            int code = Integer.parseInt(message);
            if (vault.validateAndLogin(channelId, code)) {
                return "ACCESO CONCEDIDO.\n\n" +
                       "Sesion activa por 4 horas.\n" +
                       "Escribe 'salir' en cualquier momento para bloquear el acceso.";
            } else {
                return "Codigo incorrecto. Verifica tu app de autenticacion e intenta de nuevo.";
            }
        }

        return "Acceso Restringido.\n\n" +
               "Ingresa el codigo de 6 digitos de tu App de Autenticacion " +
               "para interactuar con Fararoni.";
    }

    private String handleGuestMessage(String channelId, String message) {
        LOG.fine("[SECURITY-INTERCEPTOR] Guest access: " + channelId);
        auditLog("GUEST_CHAT", channelId, "message_len=" + message.length());
        return chatReadOnlyHandler.apply(message);
    }

    private String handleGestionCommands(String channelId, String lowerMessage, String originalMessage) {
        if (lowerMessage.startsWith(SecurityConstants.CMD_AUTHORIZE)) {
            String targetId = originalMessage.substring(SecurityConstants.CMD_AUTHORIZE.length()).trim();
            return handleAuthorize(channelId, targetId);
        }

        if (lowerMessage.startsWith(SecurityConstants.CMD_REVOKE)) {
            String targetId = originalMessage.substring(SecurityConstants.CMD_REVOKE.length()).trim();
            return handleRevoke(channelId, targetId);
        }

        if (lowerMessage.equals(SecurityConstants.CMD_LIST_GUESTS)) {
            return handleListGuests();
        }

        Matcher addMatcher = NL_ADD_GUEST.matcher(originalMessage);
        if (addMatcher.find()) {
            String extracted = addMatcher.group(1).replaceAll("[\\s()-]", "").trim();
            LOG.info("[SECURITY-NL] Detectada intencion de agregar invitado: " + extracted);
            return handleAuthorizeNL(channelId, extracted, originalMessage);
        }

        Matcher removeMatcher = NL_REMOVE_GUEST.matcher(originalMessage);
        if (removeMatcher.find()) {
            String extracted = removeMatcher.group(1).replaceAll("[\\s()-]", "").trim();
            LOG.info("[SECURITY-NL] Detectada intencion de revocar invitado: " + extracted);
            return handleRevoke(channelId, extracted);
        }

        if (NL_LIST_GUESTS.matcher(originalMessage).find()) {
            LOG.info("[SECURITY-NL] Detectada intencion de listar invitados");
            return handleListGuests();
        }

        return null;
    }

    private String handleAuthorize(String channelId, String targetId) {
        if (!isValidGuestFormat(targetId)) {
            return "Formato invalido. Usa: fara autorizar canal:identificador\n" +
                   "Ejemplos: whatsapp:521XXXXXXXXX, telegram:123456, discord:usuario#1234";
        }
        guestManager.addGuest(targetId);
        auditLog("GUEST_ADD", channelId, "target=" + targetId);
        return "Autorizado. El ID '" + targetId + "' ahora puede usar Fararoni en Modo Invitado (Solo Chat).";
    }

    private String handleAuthorizeNL(String channelId, String extracted, String originalMessage) {
        if (extracted.contains(":") || extracted.contains("#")) {
            return handleAuthorize(channelId, extracted);
        }

        String inferredChannel = channelId.contains(":") ?
            channelId.substring(0, channelId.indexOf(":")).toLowerCase() : "unknown";

        String targetId = inferredChannel + ":" + extracted;
        LOG.info("[SECURITY-NL] Invitado clasificado como " + targetId + " (acceso: todos los canales)");
        guestManager.addGuest(targetId);
        auditLog("GUEST_ADD", channelId, "target=" + targetId);
        return "Autorizado. '" + extracted +
               "' ahora puede usar Fararoni en Modo Invitado (Solo Chat) desde cualquier canal.";
    }

    private String handleRevoke(String channelId, String targetId) {
        if (guestManager.removeGuest(targetId)) {
            auditLog("GUEST_REMOVE", channelId, "target=" + targetId);
            return "Acceso revocado para: " + targetId;
        }
        return "El ID '" + targetId + "' no estaba en la lista de invitados.";
    }

    private String handleListGuests() {
        Set<String> guests = guestManager.getGuestList();
        if (guests.isEmpty()) {
            return "No hay invitados autorizados actualmente.";
        }
        StringBuilder sb = new StringBuilder("INVITADOS AUTORIZADOS (Solo Chat):\n");
        guests.forEach(id -> sb.append("  - ").append(id).append("\n"));
        return sb.toString();
    }

    private String handleSudo(String channelId, String originalMessage) {
        String password = originalMessage.substring(SecurityConstants.SUDO_PREFIX.length()).trim();
        if (password.isEmpty()) {
            return "Uso: sudo <tu_master_password>\n" +
                   "Esto activa el Modo Administrador por 15 minutos.";
        }
        if (vault.elevatePrivileges(channelId, password)) {
            return "MODO ADMINISTRADOR ACTIVADO (15 min).\n" +
                   "El Sandbox ha sido desactivado temporalmente.\n" +
                   "Puedes acceder a archivos fuera de la carpeta de confianza.";
        }
        auditLog("SUDO_FAILED", channelId, "password incorrecto");
        return "Password Maestro incorrecto. Intento registrado.";
    }

    private boolean isLogoutCommand(String lowerMessage) {
        for (String cmd : SecurityConstants.LOGOUT_COMMANDS) {
            if (lowerMessage.equals(cmd)) return true;
        }
        return false;
    }

    private boolean isValidGuestFormat(String id) {
        return id.contains(":") && id.split(":").length == 2 && id.length() > 3;
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
            LOG.warning("[SECURITY-INTERCEPTOR] Error escribiendo audit log: " + e.getMessage());
        }
    }
}
