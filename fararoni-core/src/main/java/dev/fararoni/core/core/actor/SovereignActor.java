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
package dev.fararoni.core.core.actor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * M4-01: Actor base con mailbox y Virtual Thread.
 *
 * <h2>Punto Flaco #1: Routing Engine = SPOF</h2>
 * <p>Cada actor tiene su propia cola de mensajes (mailbox) y corre en un
 * Virtual Thread dedicado. No hay Single Point of Failure — si un actor
 * crashea, el supervisor lo reinicia sin afectar a los demas.</p>
 *
 * <h2>Patron</h2>
 * <ul>
 *   <li>Mailbox: {@code LinkedBlockingQueue} unbounded</li>
 *   <li>Thread: Virtual Thread de Java 25 (~0 costo)</li>
 *   <li>Supervision: onError() permite restart/stop/escalate</li>
 *   <li>Tell: fire-and-forget (asincrono)</li>
 *   <li>Ask: request/reply con CompletableFuture</li>
 * </ul>
 *
 * @author Eber Cruz
 * @version 1.2.0
 * @since 1.2.0
 */
public abstract class SovereignActor {

    private static final Logger log = Logger.getLogger(SovereignActor.class.getName());

    private final String actorId;
    private final BlockingQueue<ActorMessage> mailbox = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread actorThread;
    private int messagesProcessed = 0;
    private int errors = 0;

    protected SovereignActor(String actorId) {
        this.actorId = actorId;
    }

    /**
     * Procesa un mensaje. Implementar en subclases.
     *
     * @param message mensaje a procesar
     */
    protected abstract void onMessage(ActorMessage message);

    /**
     * Callback de error. Override para custom supervision.
     * Default: log + continuar.
     */
    protected void onError(ActorMessage message, Exception error) {
        log.warning("[ACTOR:" + actorId + "] Error processing " + message.type() +
            ": " + error.getMessage());
        errors++;
    }

    /**
     * Arranca el actor en un Virtual Thread.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) return;

        actorThread = Thread.ofVirtual().name("actor-" + actorId).start(() -> {
            log.info("[ACTOR:" + actorId + "] Started");
            while (running.get()) {
                try {
                    ActorMessage msg = mailbox.poll(1, TimeUnit.SECONDS);
                    if (msg != null) {
                        try {
                            onMessage(msg);
                            messagesProcessed++;
                        } catch (Exception e) {
                            onError(msg, e);
                            if (msg.isAsk()) msg.replyError(e);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.info("[ACTOR:" + actorId + "] Stopped (processed: " + messagesProcessed + ", errors: " + errors + ")");
        });
    }

    /**
     * Detiene el actor.
     */
    public void stop() {
        running.set(false);
        if (actorThread != null) actorThread.interrupt();
    }

    /**
     * Tell: envía mensaje fire-and-forget.
     */
    public void tell(ActorMessage message) {
        mailbox.offer(message);
    }

    /**
     * Tell shortcut.
     */
    public void tell(String type, Object payload, String sender) {
        tell(ActorMessage.tell(type, payload, sender));
    }

    /**
     * Ask: envía mensaje y espera respuesta.
     */
    public java.util.concurrent.CompletableFuture<Object> ask(String type, Object payload, String sender) {
        var msg = ActorMessage.ask(type, payload, sender);
        mailbox.offer(msg);
        return msg.replyTo();
    }

    public String getActorId() { return actorId; }
    public boolean isRunning() { return running.get(); }
    public int getMessagesProcessed() { return messagesProcessed; }
    public int getErrors() { return errors; }
    public int getMailboxSize() { return mailbox.size(); }
}
