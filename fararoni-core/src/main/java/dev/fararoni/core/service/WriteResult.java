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
package dev.fararoni.core.service;

import java.nio.file.Path;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record WriteResult(
    boolean success,
    Path path,
    long bytesWritten,
    String errorMessage
) {
    public static WriteResult success(Path path, long bytes) {
        return new WriteResult(true, path, bytes, null);
    }

    public static WriteResult error(String message) {
        return new WriteResult(false, null, 0, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isError() {
        return !success;
    }

    @Override
    public String toString() {
        if (success) {
            return "WriteResult[OK: " + path + " (" + bytesWritten + " bytes)]";
        } else {
            return "WriteResult[ERROR: " + errorMessage + "]";
        }
    }
}
