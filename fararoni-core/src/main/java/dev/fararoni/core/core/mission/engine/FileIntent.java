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
package dev.fararoni.core.core.mission.engine;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public record FileIntent(
    String path,
    String content,
    int retryCount,
    Status status
) {
    public enum Status {
        PENDING,
        SUCCESS,
        FAILED,
        SKIPPED
    }

    public static FileIntent pending(String path, String content) {
        return new FileIntent(path, content, 0, Status.PENDING);
    }

    public FileIntent withRetry() {
        return new FileIntent(path, content, retryCount + 1, status);
    }

    public FileIntent asSuccess() {
        return new FileIntent(path, content, retryCount, Status.SUCCESS);
    }

    public FileIntent asFailed() {
        return new FileIntent(path, content, retryCount, Status.FAILED);
    }

    public FileIntent asSkipped() {
        return new FileIntent(path, content, retryCount, Status.SKIPPED);
    }
}
