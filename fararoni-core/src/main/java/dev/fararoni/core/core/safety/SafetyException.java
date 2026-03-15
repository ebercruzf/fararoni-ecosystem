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
package dev.fararoni.core.core.safety;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SafetyException extends RuntimeException {
    private final SafetyErrorCode errorCode;

    public SafetyException(String message) {
        super(message);
        this.errorCode = SafetyErrorCode.GENERIC;
    }

    public SafetyException(String message, SafetyErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public SafetyException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = SafetyErrorCode.GENERIC;
    }

    public SafetyException(String message, SafetyErrorCode errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public SafetyErrorCode getErrorCode() {
        return errorCode;
    }

    public enum SafetyErrorCode {
        GENERIC,
        DIRECTORY_NOT_EMPTY,
        FILE_NOT_FOUND,
        BACKUP_CORRUPTED,
        ROLLBACK_FAILED,
        ATOMIC_WRITE_FAILED,
        PERMISSION_DENIED,
        PATH_NOT_ALLOWED,
        DESTRUCTIVE_WRITE,
        COMPLIANCE_ERROR
    }
}
