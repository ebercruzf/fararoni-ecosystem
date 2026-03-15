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
package dev.fararoni.core.core.services;

import dev.fararoni.core.config.ConfigPriorityResolver;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class EmailTransportService {
    private static final Logger logger = Logger.getLogger(EmailTransportService.class.getName());

    private final String smtpHost;
    private final int smtpPort;
    private final String imapHost;
    private final int imapPort;
    private final String username;
    private final String password;
    private final String senderEmail;
    private final String senderName;

    public record EmailSummary(
        String from,
        String subject,
        String date,
        String snippet,
        String messageId,
        boolean read
    ) {}

    public record EmailDetail(
        String from,
        String to,
        String subject,
        String date,
        String body,
        List<String> attachmentNames,
        String messageId
    ) {}

    public EmailTransportService() {
        ConfigPriorityResolver resolver = new ConfigPriorityResolver();

        this.smtpHost = resolver.resolve("mail-host", null);
        String portStr = resolver.resolve("mail-port", null);
        this.smtpPort = portStr != null ? Integer.parseInt(portStr) : 587;

        String imapHostResolved = resolver.resolve("mail-imap-host", null);
        this.imapHost = imapHostResolved != null ? imapHostResolved : this.smtpHost;
        String imapPortStr = resolver.resolve("mail-imap-port", null);
        this.imapPort = imapPortStr != null ? Integer.parseInt(imapPortStr) : 993;

        this.username = resolver.resolve("mail-username", null);
        this.password = resolver.resolve("mail-password", null);

        String sender = resolver.resolve("mail-sender", null);
        this.senderEmail = sender != null ? sender : this.username;
        this.senderName = resolver.resolve("mail-sender-name", null);
    }

    public boolean isConfigured() {
        return smtpHost != null && username != null && password != null;
    }

    public List<EmailSummary> fetchEmails(String folder, int limit, boolean unreadOnly) throws MessagingException {
        List<EmailSummary> results = new ArrayList<>();

        try (Store store = connectImap()) {
            Folder mailFolder = store.getFolder(folder != null ? folder : "INBOX");
            mailFolder.open(Folder.READ_ONLY);

            Message[] messages;
            if (unreadOnly) {
                messages = mailFolder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            } else {
                messages = mailFolder.getMessages();
            }

            int start = Math.max(0, messages.length - limit);
            for (int i = messages.length - 1; i >= start; i--) {
                Message msg = messages[i];
                try {
                    String from = extractFrom(msg);
                    String subject = msg.getSubject() != null ? msg.getSubject() : "(sin asunto)";
                    String date = msg.getSentDate() != null
                        ? msg.getSentDate().toInstant().toString()
                        : Instant.now().toString();
                    String snippet = extractSnippet(msg, 150);
                    String messageId = msg instanceof MimeMessage mime ? mime.getMessageID() : "";
                    boolean read = msg.isSet(Flags.Flag.SEEN);

                    results.add(new EmailSummary(from, subject, date, snippet, messageId, read));
                } catch (Exception e) {
                    logger.warning("[EMAIL_FETCH] Error procesando mensaje: " + e.getMessage());
                }
            }

            mailFolder.close(false);
        }

        return results;
    }

    public EmailDetail readEmail(String messageId) throws MessagingException, IOException {
        try (Store store = connectImap()) {
            Folder mailFolder = store.getFolder("INBOX");
            mailFolder.open(Folder.READ_ONLY);

            Message[] messages = mailFolder.getMessages();

            for (int i = messages.length - 1; i >= 0; i--) {
                Message msg = messages[i];
                if (msg instanceof MimeMessage mime) {
                    String msgId = mime.getMessageID();
                    if (messageId.equals(msgId)) {
                        EmailDetail detail = buildEmailDetail(mime);
                        mailFolder.close(false);
                        return detail;
                    }
                }
            }

            mailFolder.close(false);
        }

        return null;
    }

    public boolean sendEmail(String to, String subject, String body) throws MessagingException, java.io.UnsupportedEncodingException {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.connectiontimeout", "15000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        MimeMessage message = new MimeMessage(session);

        if (senderName != null && !senderName.isBlank()) {
            message.setFrom(new InternetAddress(senderEmail, senderName, "UTF-8"));
        } else {
            message.setFrom(new InternetAddress(senderEmail));
        }

        message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        message.setSubject(subject, "UTF-8");
        message.setText(body, "UTF-8");
        message.setSentDate(java.util.Date.from(Instant.now()));

        Transport.send(message);

        logger.info("[EMAIL_SEND] Correo enviado a: " + to);
        return true;
    }

    private Store connectImap() throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", String.valueOf(imapPort));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.timeout", "15000");
        props.put("mail.imaps.connectiontimeout", "15000");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect(imapHost, username, password);
        return store;
    }

    private EmailDetail buildEmailDetail(MimeMessage mime) throws MessagingException, IOException {
        String from = extractFrom(mime);

        Address[] toAddresses = mime.getRecipients(Message.RecipientType.TO);
        String to = toAddresses != null && toAddresses.length > 0
            ? toAddresses[0].toString() : "";

        String subject = mime.getSubject() != null ? mime.getSubject() : "(sin asunto)";
        String date = mime.getSentDate() != null
            ? mime.getSentDate().toInstant().toString()
            : Instant.now().toString();

        String body = extractContent(mime);
        List<String> attachments = extractAttachmentNames(mime);
        String messageId = mime.getMessageID();

        return new EmailDetail(from, to, subject, date, body, attachments, messageId);
    }

    private String extractFrom(Message msg) throws MessagingException {
        Address[] fromAddresses = msg.getFrom();
        if (fromAddresses != null && fromAddresses.length > 0) {
            Address from = fromAddresses[0];
            if (from instanceof InternetAddress ia) {
                return ia.getAddress();
            }
            return from.toString();
        }
        return "unknown";
    }

    private String extractSnippet(Message msg, int maxLen) {
        try {
            String content = extractContent(msg);
            if (content.length() > maxLen) {
                return content.substring(0, maxLen) + "...";
            }
            return content;
        } catch (Exception e) {
            return "(no se pudo leer el contenido)";
        }
    }

    private String extractContent(Message msg) throws MessagingException, IOException {
        Object content = msg.getContent();

        if (content instanceof String text) {
            return text;
        } else if (content instanceof Multipart multipart) {
            return extractTextFromMultipart(multipart);
        }

        return content.toString();
    }

    private String extractTextFromMultipart(Multipart multipart) throws MessagingException, IOException {
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            String contentType = part.getContentType().toLowerCase();

            if (contentType.contains("text/plain")) {
                text.append(part.getContent().toString());
            } else if (contentType.contains("text/html") && text.isEmpty()) {
                String html = part.getContent().toString();
                text.append(stripHtml(html));
            } else if (part.getContent() instanceof Multipart nested) {
                text.append(extractTextFromMultipart(nested));
            }
        }

        return text.toString();
    }

    private List<String> extractAttachmentNames(MimeMessage mime) throws MessagingException, IOException {
        List<String> names = new ArrayList<>();
        if (mime.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) mime.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                    String fileName = part.getFileName();
                    names.add(fileName != null ? fileName : "adjunto-" + i);
                }
            }
        }
        return names;
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]*>", "")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&amp;", "&")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
}
