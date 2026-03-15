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
package dev.fararoni.core.model;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record Message(String role, String content) {
    public Message {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("El rol no puede estar vacío");
        }
        if (content == null) {
            throw new IllegalArgumentException("El contenido no puede ser nulo");
        }
        role = role.trim().toLowerCase();
        if (!role.matches("system|user|assistant|tool")) {
            throw new IllegalArgumentException("Rol inválido: " + role + ". Debe ser: system, user, assistant, tool");
        }
    }

    public static Message system(String content) {
        return new Message("system", content);
    }

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }

    public static Message tool(String content) {
        return new Message("tool", content);
    }

    public static Message systemContinuation(String content) {
        return new Message("system", "[CONTINUACIÓN] " + content);
    }

    public static Message userContinuation(String content) {
        return new Message("user", "[CONTINUACIÓN] " + content);
    }

    public boolean isSystem() { return "system".equals(role); }
    public boolean isUser() { return "user".equals(role); }
    public boolean isAssistant() { return "assistant".equals(role); }
    public boolean isTool() { return "tool".equals(role); }
    public boolean isContinuation() { return content.startsWith("[CONTINUACIÓN]"); }

    public String getCleanContent() {
        return isContinuation()
            ? content.substring("[CONTINUACIÓN] ".length()).trim()
            : content;
    }

    public static Message userWithImage(String text, String imageContent) {
        return new Message("user", text + "\n[Image Content: " + imageContent + "]");
    }
}
