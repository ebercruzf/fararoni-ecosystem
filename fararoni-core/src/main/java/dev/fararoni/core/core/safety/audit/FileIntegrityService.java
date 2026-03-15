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
package dev.fararoni.core.core.safety.audit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class FileIntegrityService {
    private static final Logger LOG = Logger.getLogger(FileIntegrityService.class.getName());

    private static final int BUFFER_SIZE = 8192;

    private static final String ALGORITHM = "SHA-256";

    private static final HexFormat HEX = HexFormat.of();

    public String calculateHash(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            throw new IOException("El archivo no existe: " + path);
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);

            try (InputStream is = Files.newInputStream(path)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            byte[] hashBytes = digest.digest();
            return HEX.formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            LOG.log(Level.SEVERE, "Algoritmo SHA-256 no disponible", e);
            throw new RuntimeException("SHA-256 no disponible en este JDK", e);
        }
    }

    public String calculateHash(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("El contenido no puede ser null");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(content);
            return HEX.formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            LOG.log(Level.SEVERE, "Algoritmo SHA-256 no disponible", e);
            throw new RuntimeException("SHA-256 no disponible en este JDK", e);
        }
    }

    public String calculateHash(String content) {
        if (content == null) {
            throw new IllegalArgumentException("El contenido no puede ser null");
        }
        return calculateHash(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public boolean verifyHash(Path path, String expectedHash) throws IOException {
        String actualHash = calculateHash(path);
        return actualHash.equalsIgnoreCase(expectedHash);
    }

    public static String formatAuditEntry(Path path, String hash) {
        return String.format("[AUDIT] %s | SHA-256: %s...%s",
            path.getFileName(),
            hash.substring(0, 8),
            hash.substring(56));
    }
}
