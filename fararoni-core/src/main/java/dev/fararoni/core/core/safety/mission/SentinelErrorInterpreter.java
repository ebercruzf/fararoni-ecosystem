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
package dev.fararoni.core.core.safety.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SentinelErrorInterpreter {
    private static final Logger LOG = Logger.getLogger(SentinelErrorInterpreter.class.getName());

    private static final Pattern JAVA_ERROR_PATTERN = Pattern.compile(
        "\\[ERROR\\]\\s+([^:]+):\\[(\\d+),(\\d+)\\]\\s+(.+)"
    );

    private static final Pattern JAVA_ERROR_SIMPLE = Pattern.compile(
        "([^:]+\\.java):(\\d+):\\s+error:\\s+(.+)"
    );

    private static final Pattern MISSING_PACKAGE = Pattern.compile(
        "package\\s+([\\w.]+)\\s+does not exist"
    );

    private static final Pattern SYMBOL_NOT_FOUND = Pattern.compile(
        "cannot find symbol.*symbol:\\s+(\\w+)\\s+(\\w+)"
    );

    private static final Pattern TYPE_MISMATCH = Pattern.compile(
        "incompatible types:.*found:\\s+(\\S+).*required:\\s+(\\S+)"
    );

    private static final List<String> LOMBOK_PACKAGES = List.of(
        "lombok",
        "lombok.extern",
        "lombok.experimental"
    );

    private static final List<String> LOMBOK_ANNOTATIONS = List.of(
        "@Data", "@Getter", "@Setter", "@Builder",
        "@AllArgsConstructor", "@NoArgsConstructor",
        "@RequiredArgsConstructor", "@Value", "@ToString",
        "@EqualsAndHashCode", "@Slf4j", "@Log"
    );

    public enum ErrorType {
        MISSING_IMPORT("Agregar import o verificar dependencia en pom.xml"),

        SYMBOL_NOT_FOUND("Verificar nombre de clase, método o variable"),

        TYPE_MISMATCH("Verificar tipos de datos y castings"),

        SYNTAX_ERROR("Revisar sintaxis del código"),

        DUPLICATE_CLASS("Renombrar clase o eliminar duplicado"),

        ACCESS_ERROR("Verificar modificadores de acceso"),

        VERSION_ERROR("Verificar versión de Java en pom.xml"),

        UNKNOWN("Revisar manualmente el error");

        private final String suggestedAction;

        ErrorType(String suggestedAction) {
            this.suggestedAction = suggestedAction;
        }

        public String getSuggestedAction() {
            return suggestedAction;
        }
    }

    public record CompilationError(
        ErrorType type,
        String file,
        int line,
        int column,
        String message,
        String context
    ) {
        public String toAgentPrompt() {
            return String.format("""
                **Error en `%s` línea %d:**
                - Tipo: %s
                - Mensaje: %s
                - Acción sugerida: %s
                %s""",
                file,
                line,
                type.name(),
                message,
                type.getSuggestedAction(),
                context != null && !context.isBlank() ? "- Contexto: " + context : ""
            );
        }

        public String toShortRef() {
            return file + ":" + line;
        }
    }

    public List<CompilationError> parseErrors(String buildOutput) {
        List<CompilationError> errors = new ArrayList<>();

        if (buildOutput == null || buildOutput.isBlank()) {
            return errors;
        }

        String[] lines = buildOutput.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            Matcher mavenMatcher = JAVA_ERROR_PATTERN.matcher(line);
            if (mavenMatcher.find()) {
                CompilationError error = parseJavaError(
                    mavenMatcher.group(1),
                    mavenMatcher.group(2),
                    mavenMatcher.group(3),
                    mavenMatcher.group(4),
                    lines, i
                );
                errors.add(error);
                continue;
            }

            Matcher javacMatcher = JAVA_ERROR_SIMPLE.matcher(line);
            if (javacMatcher.find()) {
                CompilationError error = parseJavaError(
                    javacMatcher.group(1),
                    javacMatcher.group(2),
                    "0",
                    javacMatcher.group(3),
                    lines, i
                );
                errors.add(error);
            }
        }

        LOG.info("Parsed " + errors.size() + " compilation errors");
        return errors;
    }

    private CompilationError parseJavaError(String file, String lineStr, String columnStr,
                                            String message, String[] allLines, int currentIdx) {
        int line = parseIntSafe(lineStr);
        int column = parseIntSafe(columnStr);

        ErrorType type = classifyError(message);

        String context = extractContext(message, type, allLines, currentIdx);

        String normalizedFile = normalizeFilePath(file);

        return new CompilationError(type, normalizedFile, line, column, message, context);
    }

    private ErrorType classifyError(String message) {
        if (message == null) return ErrorType.UNKNOWN;

        String lower = message.toLowerCase();

        if (lower.contains("package") && lower.contains("does not exist")) {
            return ErrorType.MISSING_IMPORT;
        }
        if (lower.contains("cannot find symbol")) {
            return ErrorType.SYMBOL_NOT_FOUND;
        }
        if (lower.contains("incompatible types")) {
            return ErrorType.TYPE_MISMATCH;
        }
        if (lower.contains("illegal character") || lower.contains("illegal start")) {
            return ErrorType.SYNTAX_ERROR;
        }
        if (lower.contains("duplicate class")) {
            return ErrorType.DUPLICATE_CLASS;
        }
        if (lower.contains("is not public") || lower.contains("has private access")) {
            return ErrorType.ACCESS_ERROR;
        }
        if (lower.contains("class file has wrong version") || lower.contains("unsupported class file")) {
            return ErrorType.VERSION_ERROR;
        }

        return ErrorType.UNKNOWN;
    }

    private String extractContext(String message, ErrorType type, String[] lines, int idx) {
        switch (type) {
            case MISSING_IMPORT -> {
                Matcher m = MISSING_PACKAGE.matcher(message);
                if (m.find()) {
                    String pkg = m.group(1);
                    if (LOMBOK_PACKAGES.stream().anyMatch(pkg::startsWith)) {
                        return "[WARN] LOMBOK DETECTADO: Reemplazar con Java Records";
                    }
                    return "Paquete faltante: " + pkg;
                }
            }
            case SYMBOL_NOT_FOUND -> {
                Matcher m = SYMBOL_NOT_FOUND.matcher(message);
                if (m.find()) {
                    return "Símbolo: " + m.group(1) + " " + m.group(2);
                }
            }
            case TYPE_MISMATCH -> {
                Matcher m = TYPE_MISMATCH.matcher(message);
                if (m.find()) {
                    return "Esperado: " + m.group(2) + ", Encontrado: " + m.group(1);
                }
            }
            default -> {
                if (idx + 1 < lines.length) {
                    String next = lines[idx + 1].trim();
                    if (!next.startsWith("[") && next.length() < 100) {
                        return next;
                    }
                }
            }
        }
        return null;
    }

    private String normalizeFilePath(String path) {
        if (path == null) return "unknown";

        int srcIdx = path.indexOf("src/main/java/");
        if (srcIdx >= 0) {
            return path.substring(srcIdx);
        }
        srcIdx = path.indexOf("src/test/java/");
        if (srcIdx >= 0) {
            return path.substring(srcIdx);
        }

        srcIdx = path.indexOf("src\\main\\java\\");
        if (srcIdx >= 0) {
            return path.substring(srcIdx).replace('\\', '/');
        }

        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            return path.substring(lastSlash + 1);
        }

        return path;
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String generateAgentPrompt(List<CompilationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "No se detectaron errores de compilación.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## ERRORES DE COMPILACIÓN DETECTADOS\n\n");
        sb.append("Se encontraron **").append(errors.size()).append("** errores:\n\n");

        var byType = errors.stream()
            .collect(java.util.stream.Collectors.groupingBy(CompilationError::type));

        for (var entry : byType.entrySet()) {
            sb.append("### ").append(entry.getKey().name()).append("\n");
            sb.append("_Acción: ").append(entry.getKey().getSuggestedAction()).append("_\n\n");

            for (CompilationError error : entry.getValue()) {
                sb.append("- **").append(error.toShortRef()).append("**: ");
                sb.append(error.message()).append("\n");
                if (error.context() != null && !error.context().isBlank()) {
                    sb.append("  - ").append(error.context()).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("### INSTRUCCIONES\n");
        sb.append("1. Corrige cada error en el orden listado\n");
        sb.append("2. Usa `fs_write` para escribir los archivos corregidos\n");
        sb.append("3. NO inventes dependencias - verifica pom.xml primero\n");

        if (hasLombokErrors(errors)) {
            sb.append("\n").append(generateLombokReplacementSuggestion(null));
        }

        return sb.toString();
    }

    public String generateSummary(List<CompilationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "0 errors";
        }

        var counts = errors.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                CompilationError::type,
                java.util.stream.Collectors.counting()
            ));

        return errors.size() + " errors: " + counts;
    }

    public boolean isLombokRelatedError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return false;
        }

        String lower = errorMessage.toLowerCase();

        for (String pkg : LOMBOK_PACKAGES) {
            if (lower.contains("package " + pkg) && lower.contains("does not exist")) {
                return true;
            }
        }

        for (String annotation : LOMBOK_ANNOTATIONS) {
            if (errorMessage.contains(annotation) || errorMessage.contains(annotation.substring(1))) {
                return true;
            }
        }

        return false;
    }

    public String generateLombokReplacementSuggestion(String errorMessage) {
        return """
            [ERROR] Uso de Lombok detectado (dependencia no disponible)

            [OK] SOLUCION - Reemplazar con Java Records o POJOs manuales:

            ANTES (Lombok - NO USAR):
            ```java
            @Data
            @AllArgsConstructor
            public class User {
                private String name;
                private int age;
            }
            ```

            DESPUES (Java Record - USAR ESTO):
            ```java
            public record User(String name, int age) {}
            ```

            O si necesitas mutabilidad (POJO manual):
            ```java
            public class User {
                private String name;
                private int age;

                public User(String name, int age) {
                    this.name = name;
                    this.age = age;
                }

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public int getAge() { return age; }
                public void setAge(int age) { this.age = age; }
            }
            ```

            [WARN] IMPORTANTE: NUNCA uses Lombok en este proyecto.
            """;
    }

    public boolean hasLombokErrors(List<CompilationError> errors) {
        if (errors == null) return false;
        return errors.stream()
            .anyMatch(e -> isLombokRelatedError(e.message()) ||
                          (e.context() != null && isLombokRelatedError(e.context())));
    }
}
