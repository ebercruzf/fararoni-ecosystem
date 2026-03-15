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

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class FileSecurityFilter {
    private static final Set<String> DEFAULT_SENSITIVE_EXTENSIONS = Set.of(
        ".pem", ".key", ".crt", ".cer", ".p12", ".pfx", ".jks", ".keystore",
        ".env", ".envrc",
        ".secret", ".token",
        ".aws",
        ".gpg", ".pgp", ".asc"
    );

    private static final Set<String> DEFAULT_SENSITIVE_FILENAMES = Set.of(
        ".env", ".env.local", ".env.development", ".env.production",
        ".envrc",
        "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519",
        "id_rsa.pub", "id_dsa.pub", "id_ecdsa.pub", "id_ed25519.pub",
        "known_hosts", "authorized_keys",
        "credentials", "config",
        "service-account.json", "application_default_credentials.json",
        "kubeconfig",
        "docker-compose.override.yml",
        "secrets", "secrets.yaml", "secrets.yml", "secrets.json",
        "credentials.json", "credentials.yaml", "credentials.yml",
        ".htpasswd", ".htaccess",
        "shadow", "passwd"
    );

    private static final Set<String> DEFAULT_SENSITIVE_PATTERNS = Set.of(
        "api_key", "apikey", "api-key",
        "secret_key", "secretkey", "secret-key",
        "private_key", "privatekey", "private-key",
        "access_token", "accesstoken", "access-token",
        "auth_token", "authtoken", "auth-token",
        "password", "passwd"
    );

    private static final Set<String> DEFAULT_SENSITIVE_DIRECTORIES = Set.of(
        ".ssh", ".gnupg", ".aws", ".gcp", ".azure",
        ".kube", ".docker"
    );

    private final Set<String> sensitiveExtensions;
    private final Set<String> sensitiveFilenames;
    private final Set<String> sensitivePatterns;
    private final Set<String> sensitiveDirectories;

    private FileSecurityFilter(
            Set<String> extensions,
            Set<String> filenames,
            Set<String> patterns,
            Set<String> directories) {
        this.sensitiveExtensions = Set.copyOf(extensions);
        this.sensitiveFilenames = Set.copyOf(filenames);
        this.sensitivePatterns = Set.copyOf(patterns);
        this.sensitiveDirectories = Set.copyOf(directories);
    }

    public static FileSecurityFilter standard() {
        return new FileSecurityFilter(
            DEFAULT_SENSITIVE_EXTENSIONS,
            DEFAULT_SENSITIVE_FILENAMES,
            DEFAULT_SENSITIVE_PATTERNS,
            DEFAULT_SENSITIVE_DIRECTORIES
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isSensitive(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }

        String normalizedPath = normalizePath(filename);
        String name = extractFilename(normalizedPath);
        String extension = extractExtension(name);
        String nameWithoutExt = removeExtension(name);

        if (isInSensitiveDirectory(normalizedPath)) {
            return true;
        }

        if (extension != null && sensitiveExtensions.contains(extension.toLowerCase())) {
            return true;
        }

        if (sensitiveFilenames.contains(name.toLowerCase())) {
            return true;
        }

        String lowerName = nameWithoutExt.toLowerCase();
        for (String pattern : sensitivePatterns) {
            if (lowerName.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    public boolean isSensitive(Path path) {
        if (path == null) {
            return false;
        }
        return isSensitive(path.toString());
    }

    public String getSensitivityReason(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }

        String normalizedPath = normalizePath(filename);
        String name = extractFilename(normalizedPath);
        String extension = extractExtension(name);
        String nameWithoutExt = removeExtension(name);

        for (String dir : sensitiveDirectories) {
            if (normalizedPath.contains("/" + dir + "/") || normalizedPath.startsWith(dir + "/")) {
                return "Located in sensitive directory: " + dir;
            }
        }

        if (extension != null && sensitiveExtensions.contains(extension.toLowerCase())) {
            return "Sensitive file extension: " + extension;
        }

        if (sensitiveFilenames.contains(name.toLowerCase())) {
            return "Sensitive filename: " + name;
        }

        String lowerName = nameWithoutExt.toLowerCase();
        for (String pattern : sensitivePatterns) {
            if (lowerName.contains(pattern)) {
                return "Contains sensitive pattern: " + pattern;
            }
        }

        return null;
    }

    private String normalizePath(String path) {
        return path.replace('\\', '/');
    }

    private String extractFilename(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot);
        }
        return null;
    }

    private String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }

    private boolean isInSensitiveDirectory(String path) {
        for (String dir : sensitiveDirectories) {
            if (path.contains("/" + dir + "/") || path.startsWith(dir + "/")) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getSensitiveExtensions() {
        return sensitiveExtensions;
    }

    public Set<String> getSensitiveFilenames() {
        return sensitiveFilenames;
    }

    public Set<String> getSensitivePatterns() {
        return sensitivePatterns;
    }

    public Set<String> getSensitiveDirectories() {
        return sensitiveDirectories;
    }

    public static class Builder {
        private final Set<String> extensions = new HashSet<>(DEFAULT_SENSITIVE_EXTENSIONS);
        private final Set<String> filenames = new HashSet<>(DEFAULT_SENSITIVE_FILENAMES);
        private final Set<String> patterns = new HashSet<>(DEFAULT_SENSITIVE_PATTERNS);
        private final Set<String> directories = new HashSet<>(DEFAULT_SENSITIVE_DIRECTORIES);

        public Builder addExtension(String extension) {
            if (extension != null && !extension.isBlank()) {
                extensions.add(extension.toLowerCase());
            }
            return this;
        }

        public Builder addFilename(String filename) {
            if (filename != null && !filename.isBlank()) {
                filenames.add(filename.toLowerCase());
            }
            return this;
        }

        public Builder addPattern(String pattern) {
            if (pattern != null && !pattern.isBlank()) {
                patterns.add(pattern.toLowerCase());
            }
            return this;
        }

        public Builder addDirectory(String directory) {
            if (directory != null && !directory.isBlank()) {
                directories.add(directory);
            }
            return this;
        }

        public Builder clearExtensions() {
            extensions.clear();
            return this;
        }

        public Builder clearFilenames() {
            filenames.clear();
            return this;
        }

        public FileSecurityFilter build() {
            return new FileSecurityFilter(extensions, filenames, patterns, directories);
        }
    }
}
