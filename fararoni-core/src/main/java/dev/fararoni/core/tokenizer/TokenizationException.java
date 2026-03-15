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
package dev.fararoni.core.tokenizer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class TokenizationException extends RuntimeException {
    private final String operation;
    private final String inputText;

    public TokenizationException(String message) {
        super(message);
        this.operation = null;
        this.inputText = null;
    }

    public TokenizationException(String message, Throwable cause) {
        super(message, cause);
        this.operation = null;
        this.inputText = null;
    }

    public TokenizationException(String operation, String inputText, String message) {
        super("Error en %s: %s".formatted(operation, message));
        this.operation = operation;
        this.inputText = inputText;
    }

    public TokenizationException(String operation, String inputText, String message, Throwable cause) {
        super("Error en %s: %s".formatted(operation, message), cause);
        this.operation = operation;
        this.inputText = inputText;
    }

    public String getOperation() { return operation; }
    public String getInputText() { return inputText; }

    public static TokenizationException encode(String text, Throwable cause) {
        return new TokenizationException("encode", text, "Fallo al tokenizar texto", cause);
    }

    public static TokenizationException decode(String tokenIds, Throwable cause) {
        return new TokenizationException("decode", tokenIds, "Fallo al detokenizar", cause);
    }

    public static TokenizationException networkError(String operation, Throwable cause) {
        return new TokenizationException(operation, null, "Error de red", cause);
    }
}
