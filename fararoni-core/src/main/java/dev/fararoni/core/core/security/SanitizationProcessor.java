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
package dev.fararoni.core.core.security;

import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.Sanitized;
import dev.fararoni.bus.agent.api.Sanitized.SanitizationStrategy;

import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SanitizationProcessor {
    private static final Logger LOG = Logger.getLogger(SanitizationProcessor.class.getName());

    private static final Pattern SHELL_DANGEROUS = Pattern.compile(
        "[;|&`$(){}\\[\\]<>\\\\!#*?~]|\\$\\(|\\)\\)|&&|\\|\\||>>|<<");

    private static final Pattern SQL_DANGEROUS = Pattern.compile(
        "(--|/\\*|\\*/|;\\s*$|'\\s*or\\s*'|\"\\s*or\\s*\"|union\\s+select|drop\\s+table)",
        Pattern.CASE_INSENSITIVE);

    private static final Map<Character, String> HTML_ESCAPES = Map.of(
        '<', "&lt;",
        '>', "&gt;",
        '&', "&amp;",
        '"', "&quot;",
        '\'', "&#x27;"
    );

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");

    private static final Pattern PATH_TRAVERSAL = Pattern.compile(
        "\\.\\.[\\\\/]|^[\\\\/]|^[a-zA-Z]:[\\\\/]");

    private Path workspaceRoot;

    public SanitizationProcessor() {
        this.workspaceRoot = null;
    }

    public SanitizationProcessor(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public FNLResult<Object[]> sanitizeArguments(Parameter[] parameters, Object[] arguments) {
        if (parameters.length != arguments.length) {
            return FNLResult.failure("Parameter count mismatch");
        }

        Object[] sanitized = new Object[arguments.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Object arg = arguments[i];

            Sanitized annotation = param.getAnnotation(Sanitized.class);

            if (annotation == null || arg == null) {
                sanitized[i] = arg;
                continue;
            }

            if (!(arg instanceof String strArg)) {
                sanitized[i] = arg;
                continue;
            }

            FNLResult<String> result = sanitize(strArg, annotation);
            if (!result.success()) {
                return FNLResult.failure(String.format(
                    "Sanitization failed for parameter '%s': %s",
                    param.getName(), result.error()));
            }

            sanitized[i] = result.data();
        }

        return FNLResult.success(sanitized);
    }

    public FNLResult<String> sanitize(String input, Sanitized annotation) {
        if (input == null) {
            return FNLResult.success(null);
        }

        SanitizationStrategy strategy = annotation.strategy();
        int maxLength = annotation.maxLength();
        boolean trim = annotation.trim();
        boolean rejectOnViolation = annotation.rejectOnViolation();

        String result = input;

        if (trim) {
            result = result.trim();
        }

        if (result.length() > maxLength) {
            if (rejectOnViolation) {
                return FNLResult.failure("Input exceeds maximum length of " + maxLength);
            }
            result = result.substring(0, maxLength);
        }

        try {
            result = switch (strategy) {
                case DEFAULT -> sanitizeDefault(result, rejectOnViolation);
                case SHELL -> sanitizeShell(result, rejectOnViolation);
                case SQL -> sanitizeSql(result, rejectOnViolation);
                case PATH -> sanitizePath(result, rejectOnViolation);
                case HTML -> sanitizeHtml(result);
                case NONE -> result;
            };
        } catch (SecurityException e) {
            return FNLResult.failure(e.getMessage());
        }

        return FNLResult.success(result);
    }

    public FNLResult<String> sanitize(String input, SanitizationStrategy strategy) {
        if (input == null) {
            return FNLResult.success(null);
        }

        try {
            String result = switch (strategy) {
                case DEFAULT -> sanitizeDefault(input, false);
                case SHELL -> sanitizeShell(input, false);
                case SQL -> sanitizeSql(input, false);
                case PATH -> sanitizePath(input, false);
                case HTML -> sanitizeHtml(input);
                case NONE -> input;
            };
            return FNLResult.success(result);
        } catch (SecurityException e) {
            return FNLResult.failure(e.getMessage());
        }
    }

    private String sanitizeDefault(String input, boolean reject) {
        String result = CONTROL_CHARS.matcher(input).replaceAll("");

        result = result.replaceAll("\\s+", " ");

        return result;
    }

    private String sanitizeShell(String input, boolean reject) {
        if (SHELL_DANGEROUS.matcher(input).find()) {
            if (reject) {
                throw new SecurityException("Shell metacharacters detected in input");
            }
            LOG.warning(() -> "[SANITIZE] Removed shell metacharacters from input");
        }

        String result = SHELL_DANGEROUS.matcher(input).replaceAll("");

        return sanitizeDefault(result, reject);
    }

    private String sanitizeSql(String input, boolean reject) {
        if (SQL_DANGEROUS.matcher(input).find()) {
            if (reject) {
                throw new SecurityException("SQL injection pattern detected in input");
            }
            LOG.warning(() -> "[SANITIZE] SQL injection pattern detected in input");
        }

        String result = input.replace("'", "''");

        result = result.replace("\\", "\\\\");

        return sanitizeDefault(result, reject);
    }

    private String sanitizePath(String input, boolean reject) {
        String sanitized = input;

        if (PATH_TRAVERSAL.matcher(sanitized).find()) {
            if (reject) {
                throw new SecurityException("Path traversal attempt detected: " + sanitized);
            }
            final String blockedPath = sanitized;
            LOG.warning(() -> "[SANITIZE] Path traversal blocked: " + blockedPath);

            sanitized = sanitized.replaceAll("\\.\\.", "");
            sanitized = sanitized.replaceAll("^[\\\\/]+", "");
        }

        String result = sanitized.replace("\\", "/");

        result = result.replaceAll("/+", "/");

        if (workspaceRoot != null) {
            try {
                Path resolved = workspaceRoot.resolve(result).normalize();
                if (!resolved.startsWith(workspaceRoot)) {
                    if (reject) {
                        throw new SecurityException("Path escapes workspace: " + result);
                    }
                    final String escapedPath = result;
                    LOG.warning(() -> "[SANITIZE] Path escapes workspace: " + escapedPath);
                    return "";
                }
            } catch (Exception e) {
                if (reject) {
                    throw new SecurityException("Invalid path: " + result);
                }
                return "";
            }
        }

        return sanitizeDefault(result, reject);
    }

    private String sanitizeHtml(String input) {
        StringBuilder result = new StringBuilder(input.length() + 16);

        for (char c : input.toCharArray()) {
            String escaped = HTML_ESCAPES.get(c);
            if (escaped != null) {
                result.append(escaped);
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    public void setWorkspaceRoot(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public boolean containsShellMetacharacters(String input) {
        return input != null && SHELL_DANGEROUS.matcher(input).find();
    }

    public boolean containsSqlInjection(String input) {
        return input != null && SQL_DANGEROUS.matcher(input).find();
    }

    public boolean containsPathTraversal(String input) {
        return input != null && PATH_TRAVERSAL.matcher(input).find();
    }
}
