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
package dev.fararoni.core.core.mission.events;

import java.time.Instant;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record FileWriteErrorEvent(
    String originalEventId,
    String agentId,
    String missionId,
    String targetPath,
    String errorCode,
    String errorMessage,
    boolean recoverable,
    Instant timestamp
) {
    public static final String TOPIC = "safety.file.error";

    public static final String ERR_IRONCLAD_TRUNCATION = "ERR_IRONCLAD_TRUNCATION";
    public static final String ERR_IRONCLAD_LAZY = "ERR_IRONCLAD_LAZY";
    public static final String ERR_AST_SYNTAX = "ERR_AST_SYNTAX";
    public static final String ERR_CONCURRENCY = "ERR_CONCURRENCY";
    public static final String ERR_IO = "ERR_IO";
    public static final String ERR_PATH_FORBIDDEN = "ERR_PATH_FORBIDDEN";
    public static final String ERR_FATAL = "ERR_FATAL";
    public static final String ERR_OVERSEER_POLICY = "ERR_OVERSEER_POLICY";

    public FileWriteErrorEvent {
        Objects.requireNonNull(originalEventId, "originalEventId no puede ser null");
        Objects.requireNonNull(agentId, "agentId no puede ser null");
        Objects.requireNonNull(targetPath, "targetPath no puede ser null");
        Objects.requireNonNull(errorCode, "errorCode no puede ser null");
        Objects.requireNonNull(errorMessage, "errorMessage no puede ser null");
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
    }

    public static FileWriteErrorEvent from(
            FileWriteIntentEvent intent,
            String errorCode,
            String errorMessage,
            boolean recoverable) {
        return new FileWriteErrorEvent(
            intent.eventId(),
            intent.agentId(),
            intent.missionId(),
            intent.targetPath(),
            errorCode,
            errorMessage,
            recoverable,
            Instant.now()
        );
    }

    public static FileWriteErrorEvent ironcladTruncation(
            FileWriteIntentEvent intent,
            String message) {
        return from(intent, ERR_IRONCLAD_TRUNCATION, message, false);
    }

    public static FileWriteErrorEvent ironcladLazy(
            FileWriteIntentEvent intent,
            String message) {
        return from(intent, ERR_IRONCLAD_LAZY, message, true);
    }

    public static FileWriteErrorEvent astSyntax(
            FileWriteIntentEvent intent,
            String message) {
        return from(intent, ERR_AST_SYNTAX, message, true);
    }

    public static FileWriteErrorEvent concurrency(
            FileWriteIntentEvent intent,
            String message) {
        return from(intent, ERR_CONCURRENCY, message, true);
    }

    public static FileWriteErrorEvent io(
            FileWriteIntentEvent intent,
            String message) {
        return from(intent, ERR_IO, message, true);
    }

    public static FileWriteErrorEvent fatal(
            FileWriteIntentEvent intent,
            String message) {
        return from(intent, ERR_FATAL, message, false);
    }

    public static FileWriteErrorEvent overseerPolicy(
            FileWriteIntentEvent intent,
            String message) {
        return from(intent, ERR_OVERSEER_POLICY, message, true);
    }
}
