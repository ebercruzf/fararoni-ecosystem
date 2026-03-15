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

import java.nio.file.Path;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record FileManifest(
    Path path,
    long lastModified,
    String contentHash,
    ParseStatus parseStatus,
    String language,
    long indexedAt
) {
    public enum ParseStatus {
        SUCCESS,
        FAILED,
        UNPARSEABLE,
        PENDING
    }

    public static FileManifest pending(Path path, long lastModified, String contentHash, String language) {
        return new FileManifest(
            path,
            lastModified,
            contentHash,
            ParseStatus.PENDING,
            language,
            System.currentTimeMillis()
        );
    }

    public static FileManifest success(Path path, long lastModified, String contentHash, String language) {
        return new FileManifest(
            path,
            lastModified,
            contentHash,
            ParseStatus.SUCCESS,
            language,
            System.currentTimeMillis()
        );
    }

    public static FileManifest failed(Path path, long lastModified, String contentHash, String language) {
        return new FileManifest(
            path,
            lastModified,
            contentHash,
            ParseStatus.FAILED,
            language,
            System.currentTimeMillis()
        );
    }

    public static FileManifest unparseable(Path path, long lastModified, String contentHash) {
        return new FileManifest(
            path,
            lastModified,
            contentHash,
            ParseStatus.UNPARSEABLE,
            null,
            System.currentTimeMillis()
        );
    }

    public boolean isSuccess() {
        return parseStatus == ParseStatus.SUCCESS;
    }

    public boolean hasChanged(String newHash) {
        return !contentHash.equals(newHash);
    }

    public FileManifest withStatus(ParseStatus newStatus) {
        return new FileManifest(
            path,
            lastModified,
            contentHash,
            newStatus,
            language,
            System.currentTimeMillis()
        );
    }

    public String getExtension() {
        String filename = path.getFileName().toString();
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }
}
