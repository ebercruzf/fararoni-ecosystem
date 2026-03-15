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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HexFormat;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class HardwareIdGenerator {
    private static final Logger log = LoggerFactory.getLogger(HardwareIdGenerator.class);

    private static volatile String cachedHardwareId;
    private static final Object LOCK = new Object();

    private static final String SALT = "FARARONI-HWID-v1.0";

    private HardwareIdGenerator() {
    }

    public static String generateHardwareId() {
        if (cachedHardwareId != null) {
            return cachedHardwareId;
        }

        synchronized (LOCK) {
            if (cachedHardwareId != null) {
                return cachedHardwareId;
            }

            cachedHardwareId = computeHardwareId();
            log.debug("[HardwareIdGenerator] Generated hardware ID: {}...",
                    cachedHardwareId.substring(0, 16));
            return cachedHardwareId;
        }
    }

    private static String computeHardwareId() {
        StringBuilder components = new StringBuilder();

        String mac = getMacAddress();
        components.append("MAC:").append(mac != null ? mac : "UNKNOWN").append("|");

        String hostname = getHostname();
        components.append("HOST:").append(hostname != null ? hostname : "UNKNOWN").append("|");

        String username = getUsername();
        components.append("USER:").append(username != null ? username : "UNKNOWN").append("|");

        components.append("SALT:").append(SALT);

        return sha256(components.toString());
    }

    private static String getMacAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }

                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    return HexFormat.of().formatHex(mac);
                }
            }
        } catch (Exception e) {
            log.warn("[HardwareIdGenerator] Could not get MAC address: {}", e.getMessage());
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            StringBuilder sb = new StringBuilder();
            while (interfaces.hasMoreElements()) {
                sb.append(interfaces.nextElement().getName());
            }
            if (!sb.isEmpty()) {
                return sha256(sb.toString()).substring(0, 12);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            String hostname = System.getenv("HOSTNAME");
            if (hostname == null || hostname.isEmpty()) {
                hostname = System.getenv("COMPUTERNAME");
            }
            return hostname;
        }
    }

    private static String getUsername() {
        String username = System.getProperty("user.name");
        if (username == null || username.isEmpty()) {
            username = System.getenv("USER");
        }
        if (username == null || username.isEmpty()) {
            username = System.getenv("USERNAME");
        }
        return username;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new SecurityException("SHA-256 not available", e);
        }
    }

    public static String getHardwareIdSummary() {
        String id = generateHardwareId();
        return id.substring(0, 16) + "...";
    }

    public static boolean verifyHardwareId(String savedId) {
        if (savedId == null || savedId.isEmpty()) {
            return false;
        }
        return generateHardwareId().equals(savedId);
    }

    static void clearCache() {
        synchronized (LOCK) {
            cachedHardwareId = null;
        }
    }
}
