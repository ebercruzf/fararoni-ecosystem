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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record CrawlResult(
    long filesProcessed,
    long filesSkipped,
    long filesFailed,
    long filesTimeout,
    List<CrawlError> errors,
    Duration duration
) {
    public static final CrawlResult EMPTY = new CrawlResult(0, 0, 0, 0, List.of(), Duration.ZERO);

    public long totalFiles() {
        return filesProcessed + filesSkipped + filesFailed + filesTimeout;
    }

    public double filesPerSecond() {
        if (duration.isZero()) {
            return 0.0;
        }
        return (filesProcessed + filesSkipped) / (duration.toMillis() / 1000.0);
    }

    public double successRate() {
        long total = totalFiles();
        if (total == 0) return 100.0;
        return ((filesProcessed + filesSkipped) * 100.0) / total;
    }

    public boolean hasErrors() {
        return filesFailed > 0 || filesTimeout > 0 || !errors.isEmpty();
    }

    public String toReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "[CRAWL COMPLETE] Processed: %d, Skipped: %d, Failed: %d, Timeout: %d%n",
            filesProcessed, filesSkipped, filesFailed, filesTimeout
        ));
        sb.append(String.format(
            "Duration: %.2fs, Rate: %.1f files/sec, Success: %.1f%%%n",
            duration.toMillis() / 1000.0, filesPerSecond(), successRate()
        ));

        if (!errors.isEmpty()) {
            sb.append("Errors:\n");
            for (CrawlError error : errors.subList(0, Math.min(10, errors.size()))) {
                sb.append("  - ").append(error.path()).append(": ").append(error.message()).append("\n");
            }
            if (errors.size() > 10) {
                sb.append("  ... and ").append(errors.size() - 10).append(" more errors\n");
            }
        }

        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AtomicLong processed = new AtomicLong(0);
        private final AtomicLong skipped = new AtomicLong(0);
        private final AtomicLong failed = new AtomicLong(0);
        private final AtomicLong timeout = new AtomicLong(0);
        private final Map<String, CrawlError> errors = new ConcurrentHashMap<>();
        private long startTime = System.currentTimeMillis();

        public Builder start() {
            this.startTime = System.currentTimeMillis();
            return this;
        }

        public Builder incrementProcessed() {
            processed.incrementAndGet();
            return this;
        }

        public Builder incrementSkipped() {
            skipped.incrementAndGet();
            return this;
        }

        public Builder incrementFailed() {
            failed.incrementAndGet();
            return this;
        }

        public Builder incrementTimeout() {
            timeout.incrementAndGet();
            return this;
        }

        public Builder addError(String path, String message, ErrorType type) {
            errors.put(path, new CrawlError(path, message, type, System.currentTimeMillis()));
            return this;
        }

        public long getProcessedCount() {
            return processed.get();
        }

        public long getSkippedCount() {
            return skipped.get();
        }

        public CrawlResult build() {
            long endTime = System.currentTimeMillis();
            return new CrawlResult(
                processed.get(),
                skipped.get(),
                failed.get(),
                timeout.get(),
                List.copyOf(errors.values()),
                Duration.ofMillis(endTime - startTime)
            );
        }
    }

    public enum ErrorType {
        IO_ERROR,
        PARSE_ERROR,
        TIMEOUT,
        FILE_TOO_LARGE,
        SYMLINK_CYCLE,
        OTHER
    }

    public record CrawlError(
        String path,
        String message,
        ErrorType type,
        long timestamp
    ) {}
}
