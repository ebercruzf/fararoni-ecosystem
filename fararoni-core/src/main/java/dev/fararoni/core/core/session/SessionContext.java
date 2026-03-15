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
package dev.fararoni.core.core.session;

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record SessionContext(
    String requestId,
    String userId,
    RequestOrigin origin,
    CompletableFuture<String> futureResponse,
    Instant createdAt,
    Map<String, String> metadata
) {
    public static SessionContext forCli(String userId) {
        return new SessionContext(
            UUID.randomUUID().toString(),
            userId,
            RequestOrigin.CLI_LOCAL,
            null,
            Instant.now(),
            Map.of()
        );
    }

    public static SessionContext forCli(String userId, Map<String, String> metadata) {
        return new SessionContext(
            UUID.randomUUID().toString(),
            userId,
            RequestOrigin.CLI_LOCAL,
            null,
            Instant.now(),
            metadata
        );
    }

    public static SessionContext forWeb(String userId, CompletableFuture<String> futureResponse) {
        return new SessionContext(
            UUID.randomUUID().toString(),
            userId,
            RequestOrigin.WEB_REMOTE,
            futureResponse,
            Instant.now(),
            Map.of()
        );
    }

    public static SessionContext forWeb(
        String userId,
        CompletableFuture<String> futureResponse,
        Map<String, String> metadata
    ) {
        return new SessionContext(
            UUID.randomUUID().toString(),
            userId,
            RequestOrigin.WEB_REMOTE,
            futureResponse,
            Instant.now(),
            metadata
        );
    }

    public static SessionContext forChannel(
        String userId,
        String channelId,
        CompletableFuture<String> futureResponse
    ) {
        return new SessionContext(
            UUID.randomUUID().toString(),
            userId,
            RequestOrigin.WEB_REMOTE,
            futureResponse,
            Instant.now(),
            Map.of(
                "X-Origin-Channel", channelId,
                "X-Reply-Channel-Id", channelId
            )
        );
    }

    public boolean isLocal() {
        return origin.isLocal();
    }

    public boolean isRemote() {
        return origin.isRemote();
    }

    public boolean hasAsyncResponse() {
        return futureResponse != null;
    }

    public void complete(String result) {
        if (futureResponse != null) {
            futureResponse.complete(result);
        }
    }

    public void completeExceptionally(Throwable error) {
        if (futureResponse != null) {
            futureResponse.completeExceptionally(error);
        }
    }

    public String getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    public String getOriginChannel() {
        return getMetadata("X-Origin-Channel");
    }

    public boolean shouldBlockByKillSwitch(boolean remoteDisabled) {
        return isRemote() && remoteDisabled;
    }

    public static final String KILL_SWITCH_MESSAGE =
        "⛔ MI CEREBRO ESTÁ EN MODO LOCAL. NO MOLESTAR.";
}
