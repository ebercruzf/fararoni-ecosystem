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
package dev.fararoni.core.core.adapter;

import dev.fararoni.bus.agent.api.io.IncomingMessage;
import dev.fararoni.core.core.event.EventBus;

import jakarta.mail.*;
import jakarta.mail.event.MessageCountAdapter;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ImapIngestionChannel extends AbstractIngestionChannel {
    private final ImapConfig config;
    private final AtomicBoolean shouldRun = new AtomicBoolean(false);
    private ExecutorService idleExecutor;
    private Store store;
    private Folder inbox;

    public ImapIngestionChannel(String name, ImapConfig config, EventBus eventBus) {
        super(name, "email", eventBus);
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public ImapIngestionChannel(String name, ImapConfig config) {
        this(name, config, null);
    }

    @Override
    protected void doStart() throws Exception {
        shouldRun.set(true);

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", config.host());
        props.put("mail.imaps.port", String.valueOf(config.port()));
        props.put("mail.imaps.ssl.enable", String.valueOf(config.useSsl()));
        props.put("mail.imaps.timeout", "30000");
        props.put("mail.imaps.connectiontimeout", "30000");

        Session session = Session.getInstance(props);

        store = session.getStore("imaps");
        store.connect(config.host(), config.username(), config.password());

        inbox = store.getFolder(config.folder());
        inbox.open(Folder.READ_WRITE);

        inbox.addMessageCountListener(new MessageCountAdapter() {
            @Override
            public void messagesAdded(MessageCountEvent event) {
                for (Message message : event.getMessages()) {
                    try {
                        processMessage(message);
                    } catch (Exception e) {
                        dispatchError(e);
                    }
                }
            }
        });

        idleExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "imap-idle-" + getName());
            t.setDaemon(true);
            return t;
        });

        idleExecutor.submit(this::idleLoop);

        System.out.printf("[%s] IMAP channel started - %s@%s:%d/%s%n",
            getName(), config.username(), config.host(), config.port(), config.folder());
    }

    @Override
    protected void doStop() throws Exception {
        shouldRun.set(false);

        if (idleExecutor != null) {
            idleExecutor.shutdownNow();
            idleExecutor = null;
        }

        if (inbox != null && inbox.isOpen()) {
            inbox.close(false);
            inbox = null;
        }

        if (store != null && store.isConnected()) {
            store.close();
            store = null;
        }

        System.out.printf("[%s] IMAP channel stopped%n", getName());
    }

    @Override
    protected boolean checkHealth() {
        return store != null && store.isConnected() &&
               inbox != null && inbox.isOpen();
    }

    private void idleLoop() {
        while (shouldRun.get()) {
            try {
                if (inbox instanceof org.eclipse.angus.mail.imap.IMAPFolder imapFolder) {
                    imapFolder.idle();
                } else {
                    Thread.sleep(30000);
                    inbox.getMessageCount();
                }
            } catch (MessagingException e) {
                if (shouldRun.get()) {
                    dispatchError(e);
                    try {
                        Thread.sleep(5000);
                        reconnect();
                    } catch (Exception re) {
                        dispatchError(re);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void reconnect() throws MessagingException {
        if (inbox != null && inbox.isOpen()) {
            inbox.close(false);
        }
        if (store != null && store.isConnected()) {
            store.close();
        }

        store.connect(config.host(), config.username(), config.password());
        inbox = store.getFolder(config.folder());
        inbox.open(Folder.READ_WRITE);

        System.out.printf("[%s] IMAP reconnected%n", getName());
    }

    private void processMessage(Message message) throws MessagingException, IOException {
        MimeMessage mime = (MimeMessage) message;

        String from = extractFrom(mime);
        String subject = mime.getSubject() != null ? mime.getSubject() : "(sin asunto)";
        String content = extractContent(mime);
        String messageId = mime.getMessageID();

        Map<String, String> metadata = new HashMap<>();
        metadata.put("subject", subject);
        metadata.put("from", from);
        metadata.put("messageId", messageId != null ? messageId : "");

        Address[] toAddresses = mime.getRecipients(Message.RecipientType.TO);
        if (toAddresses != null && toAddresses.length > 0) {
            metadata.put("to", toAddresses[0].toString());
        }

        if (mime.getReceivedDate() != null) {
            metadata.put("receivedDate", mime.getReceivedDate().toInstant().toString());
        }

        metadata.put("hasAttachments", String.valueOf(hasAttachments(mime)));

        IncomingMessage incomingMessage = new IncomingMessage(
            "email",
            getName(),
            from,
            content,
            metadata,
            Instant.now()
        );

        dispatchMessage(incomingMessage);

        message.setFlag(Flags.Flag.SEEN, true);
    }

    private String extractFrom(MimeMessage mime) throws MessagingException {
        Address[] fromAddresses = mime.getFrom();
        if (fromAddresses != null && fromAddresses.length > 0) {
            Address from = fromAddresses[0];
            if (from instanceof InternetAddress ia) {
                return ia.getAddress();
            }
            return from.toString();
        }
        return "unknown";
    }

    private String extractContent(MimeMessage mime) throws MessagingException, IOException {
        Object content = mime.getContent();

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

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]*>", "")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&amp;", "&")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    private boolean hasAttachments(MimeMessage mime) throws MessagingException, IOException {
        if (mime.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) mime.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                    return true;
                }
            }
        }
        return false;
    }

    public record ImapConfig(
        String host,
        int port,
        String username,
        String password,
        String folder,
        boolean useSsl
    ) {
        public ImapConfig {
            Objects.requireNonNull(host, "host must not be null");
            Objects.requireNonNull(username, "username must not be null");
            Objects.requireNonNull(password, "password must not be null");
            if (port <= 0) port = 993;
            if (folder == null || folder.isBlank()) folder = "INBOX";
        }

        public static ImapConfig gmail(String email, String appPassword) {
            return new ImapConfig(
                "imap.gmail.com",
                993,
                email,
                appPassword,
                "INBOX",
                true
            );
        }

        public static ImapConfig outlook(String email, String password) {
            return new ImapConfig(
                "outlook.office365.com",
                993,
                email,
                password,
                "INBOX",
                true
            );
        }
    }
}
