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
package dev.fararoni.core.core.skills.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.core.core.services.EmailTransportService;
import dev.fararoni.core.core.services.EmailTransportService.EmailDetail;
import dev.fararoni.core.core.services.EmailTransportService.EmailSummary;
import dev.fararoni.core.core.skills.ToolExecutionResult;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ToolExecEmailHandlers {
    private static final Logger logger = Logger.getLogger(ToolExecEmailHandlers.class.getName());
    private final ObjectMapper mapper;

    public ToolExecEmailHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private EmailTransportService freshEmailService() {
        return new EmailTransportService();
    }

    public ToolExecutionResult handleEmailFetch(String jsonArgs) throws Exception {
        EmailTransportService emailService = freshEmailService();
        if (!emailService.isConfigured()) {
            return notConfiguredError();
        }

        JsonNode args = mapper.readTree(jsonArgs);

        String folder = args.has("folder") ? args.get("folder").asText() : "INBOX";
        int limit = args.has("limit") ? args.get("limit").asInt() : 10;
        boolean unreadOnly = !args.has("unread_only") || args.get("unread_only").asBoolean();

        logger.info("[EMAIL_FETCH] Folder: " + folder + ", limit: " + limit + ", unreadOnly: " + unreadOnly);

        try {
            List<EmailSummary> emails = emailService.fetchEmails(folder, limit, unreadOnly);

            if (emails.isEmpty()) {
                String msg = unreadOnly
                    ? "No hay correos sin leer en " + folder + "."
                    : "No hay correos en " + folder + ".";
                return new ToolExecutionResult(true, msg, Optional.empty(), Optional.empty());
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %d CORREOS EN %s ===\n\n", emails.size(), folder));

            for (int i = 0; i < emails.size(); i++) {
                EmailSummary e = emails.get(i);
                sb.append(String.format(
                    "%d. %s %s\n   De: %s\n   Fecha: %s\n   %s\n   Message-ID: %s\n\n",
                    i + 1,
                    e.read() ? "[LEIDO]" : "[NUEVO]",
                    e.subject(),
                    e.from(),
                    e.date(),
                    e.snippet(),
                    e.messageId()
                ));
            }

            logger.info("[EMAIL_FETCH] OK - " + emails.size() + " correos encontrados");

            return new ToolExecutionResult(true, sb.toString(),
                Optional.of(sb.toString()), Optional.of(folder));
        } catch (Exception e) {
            logger.warning("[EMAIL_FETCH] Error: " + e.getMessage());
            return new ToolExecutionResult(false,
                "Error al leer correos de " + folder + ": " + e.getMessage() +
                ". Verifica las credenciales con '/config show'.",
                Optional.empty(), Optional.of(folder));
        }
    }

    public ToolExecutionResult handleEmailSend(String jsonArgs) throws Exception {
        EmailTransportService emailService = freshEmailService();
        if (!emailService.isConfigured()) {
            return notConfiguredError();
        }

        JsonNode args = mapper.readTree(jsonArgs);

        if (!args.has("to")) {
            return new ToolExecutionResult(false,
                "Error: email_send requiere parametro 'to'",
                Optional.empty(), Optional.empty());
        }
        if (!args.has("subject")) {
            return new ToolExecutionResult(false,
                "Error: email_send requiere parametro 'subject'",
                Optional.empty(), Optional.empty());
        }
        if (!args.has("body")) {
            return new ToolExecutionResult(false,
                "Error: email_send requiere parametro 'body'",
                Optional.empty(), Optional.empty());
        }

        String to = args.get("to").asText();
        String subject = args.get("subject").asText();
        String body = args.get("body").asText();

        logger.info("[EMAIL_SEND] Enviando a: " + to + " | Asunto: " + subject);

        try {
            boolean sent = emailService.sendEmail(to, subject, body);

            if (sent) {
                String result = String.format(
                    "=== CORREO ENVIADO ===\n" +
                    "Para: %s\n" +
                    "Asunto: %s\n" +
                    "Estado: Enviado exitosamente",
                    to, subject
                );
                return new ToolExecutionResult(true, result, Optional.of(result), Optional.of(to));
            } else {
                return new ToolExecutionResult(false,
                    "Error: No se pudo enviar el correo a " + to,
                    Optional.empty(), Optional.of(to));
            }
        } catch (Exception e) {
            logger.warning("[EMAIL_SEND] Error: " + e.getMessage());
            return new ToolExecutionResult(false,
                "Error enviando correo a '" + to + "': " + e.getMessage() +
                ". Verifica las credenciales SMTP con '/config show'.",
                Optional.empty(), Optional.of(to));
        }
    }

    public ToolExecutionResult handleEmailRead(String jsonArgs) throws Exception {
        EmailTransportService emailService = freshEmailService();
        if (!emailService.isConfigured()) {
            return notConfiguredError();
        }

        JsonNode args = mapper.readTree(jsonArgs);

        if (!args.has("message_id")) {
            return new ToolExecutionResult(false,
                "Error: email_read requiere parametro 'message_id'. " +
                "Usa email_fetch primero para obtener el Message-ID del correo.",
                Optional.empty(), Optional.empty());
        }

        String messageId = args.get("message_id").asText();

        logger.info("[EMAIL_READ] Leyendo mensaje: " + messageId);

        try {
            EmailDetail detail = emailService.readEmail(messageId);

            if (detail == null) {
                return new ToolExecutionResult(false,
                    "No se encontro el correo con Message-ID: " + messageId +
                    ". El correo puede haber sido eliminado o el ID es incorrecto.",
                    Optional.empty(), Optional.of(messageId));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== CORREO COMPLETO ===\n");
            sb.append("De: ").append(detail.from()).append("\n");
            sb.append("Para: ").append(detail.to()).append("\n");
            sb.append("Asunto: ").append(detail.subject()).append("\n");
            sb.append("Fecha: ").append(detail.date()).append("\n");

            if (!detail.attachmentNames().isEmpty()) {
                sb.append("Adjuntos: ").append(String.join(", ", detail.attachmentNames())).append("\n");
            }

            sb.append("\n=== CONTENIDO ===\n");
            sb.append(detail.body());

            logger.info("[EMAIL_READ] OK - Mensaje leido: " + detail.subject());

            return new ToolExecutionResult(true, sb.toString(),
                Optional.of(detail.body()), Optional.of(messageId));
        } catch (Exception e) {
            logger.warning("[EMAIL_READ] Error: " + e.getMessage());
            return new ToolExecutionResult(false,
                "Error leyendo correo '" + messageId + "': " + e.getMessage(),
                Optional.empty(), Optional.of(messageId));
        }
    }

    private ToolExecutionResult notConfiguredError() {
        return new ToolExecutionResult(false,
            "Error: Credenciales de email no configuradas. " +
            "Configura con:\n" +
            "  /config set mail-host smtp.gmail.com\n" +
            "  /config set mail-port 587\n" +
            "  /config set mail-username tu@gmail.com\n" +
            "  /config set mail-password tu-app-password\n" +
            "  /config set mail-imap-host imap.gmail.com\n" +
            "  /config set mail-imap-port 993\n" +
            "O via variables de entorno: MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD, etc.",
            Optional.empty(), Optional.empty());
    }
}
