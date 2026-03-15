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
package dev.fararoni.core.core.index;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record CrawlConfig(
    Duration timeout,
    long maxFileSize,
    int maxDepth,
    Set<Pattern> excludePatterns,
    Set<String> includedExtensions
) {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(200);

    public static final long DEFAULT_MAX_FILE_SIZE = 1024 * 1024;

    public static final int DEFAULT_MAX_DEPTH = Integer.MAX_VALUE;

    public static final Set<String> DEFAULT_EXTENSIONS = Set.of(
        "java", "py", "js", "ts", "tsx", "jsx",
        "go", "rs", "rb", "kt", "scala",
        "c", "cpp", "h", "hpp",
        "html", "vue", "svelte",
        "xml", "json", "yaml", "yml", "toml",
        "md", "txt", "sh", "bash"
    );

    public static final Set<Pattern> DEFAULT_EXCLUDE_PATTERNS = Set.of(
        Pattern.compile(".*/node_modules/.*"),
        Pattern.compile(".*/\\.git/.*"),
        Pattern.compile(".*/target/.*"),
        Pattern.compile(".*/build/.*"),
        Pattern.compile(".*/dist/.*"),
        Pattern.compile(".*/\\.idea/.*"),
        Pattern.compile(".*/\\.vscode/.*"),
        Pattern.compile(".*/\\.gradle/.*"),
        Pattern.compile(".*/\\.mvn/.*"),
        Pattern.compile(".*/__pycache__/.*"),
        Pattern.compile(".*/\\.cache/.*"),
        Pattern.compile(".*/vendor/.*"),
        Pattern.compile(".*/bin/.*"),
        Pattern.compile(".*/obj/.*")
    );

    public static final CrawlConfig DEFAULT = new CrawlConfig(
        DEFAULT_TIMEOUT,
        DEFAULT_MAX_FILE_SIZE,
        DEFAULT_MAX_DEPTH,
        DEFAULT_EXCLUDE_PATTERNS,
        DEFAULT_EXTENSIONS
    );

    public CrawlConfig {
        if (timeout == null) {
            timeout = DEFAULT_TIMEOUT;
        }
        if (maxFileSize <= 0) {
            maxFileSize = DEFAULT_MAX_FILE_SIZE;
        }
        if (maxDepth <= 0) {
            maxDepth = DEFAULT_MAX_DEPTH;
        }
        if (excludePatterns == null) {
            excludePatterns = DEFAULT_EXCLUDE_PATTERNS;
        }
        if (includedExtensions == null) {
            includedExtensions = DEFAULT_EXTENSIONS;
        }
    }

    public boolean shouldExclude(String path) {
        if (path == null) {
            return true;
        }

        for (Pattern pattern : excludePatterns) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    public boolean isExtensionIncluded(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        return includedExtensions.contains(extension.toLowerCase());
    }

    public boolean exceedsMaxSize(long size) {
        return size > maxFileSize;
    }

    public long timeoutMillis() {
        return timeout.toMillis();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Duration timeout = DEFAULT_TIMEOUT;
        private long maxFileSize = DEFAULT_MAX_FILE_SIZE;
        private int maxDepth = DEFAULT_MAX_DEPTH;
        private Set<Pattern> excludePatterns = DEFAULT_EXCLUDE_PATTERNS;
        private Set<String> includedExtensions = DEFAULT_EXTENSIONS;

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder timeoutMillis(long millis) {
            this.timeout = Duration.ofMillis(millis);
            return this;
        }

        public Builder maxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder excludePatterns(Set<Pattern> patterns) {
            this.excludePatterns = patterns;
            return this;
        }

        public Builder addExcludePattern(String pattern) {
            java.util.HashSet<Pattern> newPatterns = new java.util.HashSet<>(this.excludePatterns);
            newPatterns.add(Pattern.compile(pattern));
            this.excludePatterns = Set.copyOf(newPatterns);
            return this;
        }

        public Builder includedExtensions(Set<String> extensions) {
            this.includedExtensions = extensions;
            return this;
        }

        public CrawlConfig build() {
            return new CrawlConfig(timeout, maxFileSize, maxDepth, excludePatterns, includedExtensions);
        }
    }
}
