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
package dev.fararoni.core.core.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class OSPathUtils {
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    private static final boolean IS_WINDOWS = OS_NAME.contains("windows");

    private static final boolean IS_MAC = OS_NAME.contains("mac") || OS_NAME.contains("darwin");

    private static final boolean IS_LINUX = OS_NAME.contains("linux") || OS_NAME.contains("nix") || OS_NAME.contains("nux");

    private OSPathUtils() {
    }

    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    public static boolean isMac() {
        return IS_MAC;
    }

    public static boolean isLinux() {
        return IS_LINUX;
    }

    public static String getOsType() {
        if (IS_WINDOWS) return "windows";
        if (IS_MAC) return "mac";
        if (IS_LINUX) return "linux";
        return "unknown";
    }

    public static String normalize(String path) {
        if (path == null) return null;
        return path.replace('\\', '/');
    }

    public static String normalize(Path path) {
        if (path == null) return null;
        return normalize(path.toString());
    }

    public static String toNativePath(String normalizedPath) {
        if (normalizedPath == null) return null;
        if (IS_WINDOWS) {
            return normalizedPath.replace('/', '\\');
        }
        return normalizedPath;
    }

    public static Path toPath(String pathString) {
        if (pathString == null) return null;
        return Paths.get(normalize(pathString));
    }

    public static String getRelativeToSrc(String fullPath) {
        if (fullPath == null) return null;

        String normalized = normalize(fullPath);

        String[] srcMarkers = {"src/main/java/", "src/test/java/", "src/main/resources/", "src/test/resources/"};

        for (String marker : srcMarkers) {
            int idx = normalized.indexOf(marker);
            if (idx >= 0) {
                return normalized.substring(idx);
            }
        }

        int srcIdx = normalized.indexOf("src/");
        if (srcIdx >= 0) {
            return normalized.substring(srcIdx);
        }

        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            return normalized.substring(lastSlash + 1);
        }

        return normalized;
    }

    public static String getFileName(String path) {
        if (path == null) return null;

        String normalized = normalize(path);
        int lastSlash = normalized.lastIndexOf('/');

        if (lastSlash >= 0 && lastSlash < normalized.length() - 1) {
            return normalized.substring(lastSlash + 1);
        }

        return normalized;
    }

    public static String getExtension(String path) {
        if (path == null) return "";

        String fileName = getFileName(path);
        int dot = fileName.lastIndexOf('.');

        if (dot > 0 && dot < fileName.length() - 1) {
            return fileName.substring(dot + 1);
        }

        return "";
    }

    public static boolean pathEquals(String path1, String path2) {
        if (path1 == null && path2 == null) return true;
        if (path1 == null || path2 == null) return false;

        String norm1 = normalize(path1);
        String norm2 = normalize(path2);

        if (IS_WINDOWS) {
            return norm1.equalsIgnoreCase(norm2);
        }

        return norm1.equals(norm2);
    }

    public static String getMavenWrapper() {
        return IS_WINDOWS ? "mvnw.cmd" : "./mvnw";
    }

    public static String getGradleWrapper() {
        return IS_WINDOWS ? "gradlew.bat" : "./gradlew";
    }

    public static String getPythonCommand() {
        return IS_WINDOWS ? "python" : "python3";
    }

    public static Path getTempDirectory() {
        return Paths.get(System.getProperty("java.io.tmpdir"));
    }

    public static Path getUserHome() {
        return Paths.get(System.getProperty("user.home"));
    }

    public static boolean isValidLength(String path) {
        if (path == null) return true;

        int maxLength = IS_WINDOWS ? 260 : 4096;

        return path.length() <= maxLength;
    }

    public static boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return false;

        if (IS_WINDOWS) {
            return !fileName.matches(".*[<>:\"/\\\\|?*].*") &&
                   !fileName.matches("^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$");
        }

        return !fileName.contains("/") && !fileName.contains("\0");
    }

    public static void printOsInfo() {
        System.out.println("=== OS Path Utils ===");
        System.out.println("OS Name: " + OS_NAME);
        System.out.println("OS Type: " + getOsType());
        System.out.println("Is Windows: " + IS_WINDOWS);
        System.out.println("Is Mac: " + IS_MAC);
        System.out.println("Is Linux: " + IS_LINUX);
        System.out.println("Temp Dir: " + getTempDirectory());
        System.out.println("User Home: " + getUserHome());
        System.out.println("Maven Wrapper: " + getMavenWrapper());
        System.out.println("Gradle Wrapper: " + getGradleWrapper());
        System.out.println("====================");
    }
}
