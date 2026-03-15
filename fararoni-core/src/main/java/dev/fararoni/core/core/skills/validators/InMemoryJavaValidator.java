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
package dev.fararoni.core.core.skills.validators;

import javax.tools.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class InMemoryJavaValidator {
    private static final Logger LOG = Logger.getLogger(InMemoryJavaValidator.class.getName());

    private InMemoryJavaValidator() {
    }

    public static String validate(String filepath, String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            LOG.fine("[AST Guard] JavaCompiler no disponible (¿JRE en vez de JDK?). Saltando validación.");
            return null;
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String className = extractClassName(filepath);

        JavaFileObject memoryFile = new StringJavaFileObject(className, content);

        JavaCompiler.CompilationTask task = compiler.getTask(
            null,
            null,
            diagnostics,
            List.of("-proc:none", "-Xlint:none", "--enable-preview", "--release", "25"),
            null,
            List.of(memoryFile)
        );

        boolean success = task.call();

        if (success) {
            LOG.fine("[AST Guard] Código Java válido: " + filepath);
            return null;
        }

        String errors = diagnostics.getDiagnostics().stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
            .map(d -> String.format("Línea %d: %s",
                d.getLineNumber(),
                d.getMessage(null)))
            .collect(Collectors.joining("\n"));

        if (errors.isBlank()) {
            return "Error de compilación (sin detalles)";
        }

        LOG.fine("[AST Guard] Errores en código Java:\n" + errors);
        return errors;
    }

    private static String extractClassName(String filepath) {
        if (filepath == null || filepath.isBlank()) {
            return "UnnamedClass";
        }

        String filename = Path.of(filepath).getFileName().toString();

        if (filename.endsWith(".java")) {
            return filename.substring(0, filename.length() - 5);
        }

        return filename;
    }

    private static class StringJavaFileObject extends SimpleJavaFileObject {
        private final String code;

        protected StringJavaFileObject(String className, String code) {
            super(
                URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                Kind.SOURCE
            );
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
