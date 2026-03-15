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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ContentHasher {
    private static final int BUFFER_SIZE = 8192;

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private static final ThreadLocal<MessageDigest> DIGEST_HOLDER = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    });

    private ContentHasher() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String hash(String content) {
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }

        MessageDigest digest = DIGEST_HOLDER.get();
        digest.reset();

        byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return HEX_FORMAT.formatHex(hashBytes);
    }

    public static String hash(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Bytes cannot be null");
        }

        MessageDigest digest = DIGEST_HOLDER.get();
        digest.reset();

        byte[] hashBytes = digest.digest(bytes);
        return HEX_FORMAT.formatHex(hashBytes);
    }

    public static String hashFile(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }

        MessageDigest digest = DIGEST_HOLDER.get();
        digest.reset();

        byte[] buffer = new byte[BUFFER_SIZE];

        try (InputStream is = Files.newInputStream(file)) {
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return HEX_FORMAT.formatHex(digest.digest());
    }

    public static String hashStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }

        MessageDigest digest = DIGEST_HOLDER.get();
        digest.reset();

        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }

        return HEX_FORMAT.formatHex(digest.digest());
    }

    public static boolean equals(String hash1, String hash2) {
        if (hash1 == null || hash2 == null) {
            return hash1 == hash2;
        }

        if (hash1.length() != hash2.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < hash1.length(); i++) {
            result |= hash1.charAt(i) ^ hash2.charAt(i);
        }
        return result == 0;
    }

    public static boolean isValidHash(String hash) {
        if (hash == null || hash.length() != 64) {
            return false;
        }

        for (char c : hash.toCharArray()) {
            if (!isHexDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') ||
               (c >= 'a' && c <= 'f') ||
               (c >= 'A' && c <= 'F');
    }
}
