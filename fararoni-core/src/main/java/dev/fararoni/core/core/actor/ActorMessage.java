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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Mensaje inmutable entre actores.
 *
 * @param id          ID unico del mensaje
 * @param type        tipo del mensaje (para pattern matching en el actor)
 * @param payload     contenido
 * @param sender      ID del actor emisor
 * @param replyTo     future para patron ask (null para tell)
 * @param timestamp   cuando se creo
 *
 * @author Eber Cruz
 * @version 1.2.0
 * @since 1.2.0
 */
public record ActorMessage(
    String id,
    String type,
    Object payload,
    String sender,
    CompletableFuture<Object> replyTo,
    Instant timestamp
) {
    /** tell: fire-and-forget */
    public static ActorMessage tell(String type, Object payload, String sender) {
        return new ActorMessage(UUID.randomUUID().toString(), type, payload, sender, null, Instant.now());
    }

    /** ask: espera respuesta */
    public static ActorMessage ask(String type, Object payload, String sender) {
        return new ActorMessage(UUID.randomUUID().toString(), type, payload, sender,
            new CompletableFuture<>(), Instant.now());
    }

    public boolean isAsk() { return replyTo != null; }

    public void reply(Object response) {
        if (replyTo != null) replyTo.complete(response);
    }

    public void replyError(Throwable error) {
        if (replyTo != null) replyTo.completeExceptionally(error);
    }
}
