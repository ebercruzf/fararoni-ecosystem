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
package dev.fararoni.core.router;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public enum Tool {
    GIT(
        "git",
        "Git version control operations",
        List.of("commit", "push", "pull", "status", "diff", "branch", "checkout", "merge", "log", "stash"),
        List.of("git", "commit", "push", "pull", "sube", "subir", "baja", "bajar", "rama", "branch",
                "status", "diff", "cambios", "merge", "fusiona", "checkout", "stash", "guardar")
    ),

    FILE(
        "file",
        "File operations - load, unload, search files in context",
        List.of("load", "unload", "search", "list", "read", "open", "close"),
        List.of("archivo", "file", "cargar", "load", "unload", "descargar", "leer", "read",
                "abrir", "open", "cerrar", "close", "buscar", "search", "listar", "list")
    ),

    CONFIG(
        "config",
        "Configuration management - API keys, settings",
        List.of("set", "get", "show", "delete", "list", "reset"),
        List.of("config", "configurar", "configuracion", "setting", "api", "key", "clave",
                "set", "get", "mostrar", "show", "borrar", "delete", "resetear", "reset")
    ),

    FEATURE(
        "feature",
        "Feature planning and execution - complex multi-step tasks",
        List.of("plan", "execute", "analyze", "implement"),
        List.of("feature", "funcionalidad", "planificar", "plan", "ejecutar", "execute",
                "implementar", "implement", "analizar", "analyze", "tarea", "task")
    ),

    SYSTEM(
        "system",
        "Operating system commands - date, whoami, hostname, etc",
        List.of("exec", "run", "date", "whoami", "hostname", "pwd", "uname", "uptime"),
        List.of("fecha", "date", "hora", "time", "usuario", "user", "whoami",
                "hostname", "maquina", "sistema", "system", "donde estoy", "pwd", "uname")
    ),

    CHAT(
        "chat",
        "General conversation, questions, and explanations",
        List.of("message", "explain", "help", "ask"),
        List.of("como", "que", "por que", "explica", "ayuda", "help", "dime", "cuentame",
                "describe", "ejemplo", "example", "tutorial", "guia", "guide")
    ),

    UNKNOWN(
        "unknown",
        "Could not determine the appropriate tool",
        List.of(),
        List.of()
    );

    private final String id;
    private final String description;
    private final List<String> actions;
    private final List<String> keywords;

    private static final Map<String, Tool> BY_ID = Arrays.stream(values())
        .collect(Collectors.toMap(Tool::getId, t -> t));

    Tool(String id, String description, List<String> actions, List<String> keywords) {
        this.id = id;
        this.description = description;
        this.actions = List.copyOf(actions);
        this.keywords = List.copyOf(keywords);
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getActions() {
        return actions;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public boolean isValidAction(String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        return actions.stream()
            .anyMatch(a -> a.equalsIgnoreCase(action.trim()));
    }

    public boolean matchesKeywords(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lowerText = text.toLowerCase();
        return keywords.stream()
            .anyMatch(kw -> lowerText.contains(kw.toLowerCase()));
    }

    public static Tool fromId(String id) {
        if (id == null || id.isBlank()) {
            return UNKNOWN;
        }
        return BY_ID.getOrDefault(id.toLowerCase().trim(), UNKNOWN);
    }

    public static Tool detectFromKeywords(String text) {
        if (text == null || text.isBlank()) {
            return CHAT;
        }

        for (Tool tool : List.of(GIT, FILE, CONFIG, FEATURE)) {
            if (tool.matchesKeywords(text)) {
                return tool;
            }
        }

        return CHAT;
    }

    public static String toPromptText() {
        StringBuilder sb = new StringBuilder("Tools:\n");
        for (Tool tool : values()) {
            if (tool == UNKNOWN) continue;
            sb.append("- ").append(tool.id).append(": ");
            sb.append(String.join(", ", tool.actions));
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return id;
    }
}
