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

import dev.fararoni.core.core.download.NativeEngineDownloader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class NativeLoader {
    private static final Logger LOG = Logger.getLogger(NativeLoader.class.getName());

    private static final String VERSION = "v1";

    private static final String LIB_PATH_PROPERTY = "de.kherud.llama.lib.path";

    private static final String ENV_LIB_PATH = "FARARONI_LIB_PATH";

    private static volatile boolean loaded = false;

    private static Path extractedPath = null;

    private NativeLoader() {
        throw new AssertionError("NativeLoader is not instantiable");
    }

    public static synchronized void loadEmbeddedLibrary() {
        if (loaded) {
            LOG.fine("[NativeLoader] Already loaded, skipping");
            return;
        }

        try {
            String libName = getLibraryName();

            String manualPath = System.getenv(ENV_LIB_PATH);
            if (manualPath != null && !manualPath.isBlank()) {
                Path manualDir = Path.of(manualPath);
                Path manualLib = manualDir.resolve(libName);
                if (Files.isDirectory(manualDir) && isValidLibrary(manualLib)) {
                    configureLibraryPath(manualDir);
                    extractedPath = manualLib;
                    loaded = true;
                    LOG.info("[NativeLoader] Using manual path: " + manualDir);
                    return;
                } else {
                    LOG.warning("[NativeLoader] FARARONI_LIB_PATH invalid or library not found: " + manualPath);
                }
            }

            Path homeLib = Path.of(System.getProperty("user.home"), ".llm-fararoni", "lib");
            Path homeLibPath = homeLib.resolve(libName);
            if (Files.exists(homeLibPath) && isValidLibrary(homeLibPath)) {
                configureLibraryPath(homeLib);
                extractedPath = homeLibPath;
                loaded = true;
                LOG.info("[NativeLoader] Using downloaded library: " + homeLib);
                return;
            }

            Path extractDir = findOrCreateExtractDir();
            Path libPath = extractDir.resolve(libName);

            if (!Files.exists(libPath)) {
                extractLibrary(libName, libPath);
            }

            configureLibraryPath(extractDir);
            extractedPath = libPath;
            loaded = true;

            LOG.info("[NativeLoader] Library ready: " + libPath);
        } catch (Exception e) {
            String msg = "Native library not available. " +
                "Options:\n" +
                "  1. Install engine with: LocalLlmService.installEngine()\n" +
                "  2. Set " + ENV_LIB_PATH + " to directory containing " + getLibraryName() + "\n" +
                "  3. Use fararoni-enterprise (includes embedded library)";
            LOG.log(Level.SEVERE, msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static Path getExtractedPath() {
        return extractedPath;
    }

    public static boolean isNativeLibraryAvailable() {
        if (loaded) {
            return true;
        }

        String libName = getLibraryName();

        String manualPath = System.getenv(ENV_LIB_PATH);
        if (manualPath != null && !manualPath.isBlank()) {
            Path manualDir = Path.of(manualPath);
            Path libPath = manualDir.resolve(libName);
            if (Files.exists(libPath) && isValidLibrary(libPath)) {
                LOG.fine("[NativeLoader] Found in FARARONI_LIB_PATH: " + libPath);
                return true;
            }
        }

        Path homeLib = Path.of(System.getProperty("user.home"), ".llm-fararoni", "lib");
        Path homeLibPath = homeLib.resolve(libName);
        if (Files.exists(homeLibPath) && isValidLibrary(homeLibPath)) {
            LOG.fine("[NativeLoader] Found in ~/.llm-fararoni/lib/: " + homeLibPath);
            return true;
        }

        String resourcePath = "/native/" + libName;
        try (InputStream is = NativeLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.fine("[NativeLoader] Not found in embedded resources");
                return false;
            }
            boolean hasContent = is.read() != -1;
            if (hasContent) {
                LOG.fine("[NativeLoader] Found in embedded resources");
            }
            return hasContent;
        } catch (Exception e) {
            LOG.fine("[NativeLoader] Error checking embedded resources: " + e.getMessage());
            return false;
        }
    }

    private static boolean isValidLibrary(Path libPath) {
        try {
            long size = Files.size(libPath);
            return size >= 1_000_000L;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getLibraryName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return "libjllama.dylib";
        } else if (os.contains("win")) {
            return "jllama.dll";
        } else {
            return "libjllama.so";
        }
    }

    private static void configureLibraryPath(Path directory) {
        System.setProperty(LIB_PATH_PROPERTY, directory.toAbsolutePath().toString());
    }

    private static Path findOrCreateExtractDir() throws Exception {
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir", "/tmp"),
            "fararoni-native-" + VERSION);
        if (tryCreateDir(tmpDir)) {
            return tmpDir;
        }

        Path homeDir = Path.of(System.getProperty("user.home"),
            ".llm-fararoni", "lib");
        if (tryCreateDir(homeDir)) {
            return homeDir;
        }

        Path localDir = Path.of("native-lib");
        if (tryCreateDir(localDir)) {
            return localDir;
        }

        throw new RuntimeException(
            "Cannot create directory for native library extraction. " +
            "Tried: " + tmpDir + ", " + homeDir + ", " + localDir);
    }

    private static boolean tryCreateDir(Path dir) {
        try {
            Files.createDirectories(dir);
            Path testFile = dir.resolve(".write-test-" + System.currentTimeMillis());
            Files.writeString(testFile, "test");
            Files.delete(testFile);
            return true;
        } catch (Exception e) {
            LOG.fine("[NativeLoader] Cannot use directory " + dir + ": " + e.getMessage());
            return false;
        }
    }

    private static void extractLibrary(String libName, Path target) throws Exception {
        String resourcePath = "/native/" + libName;

        LOG.info("[NativeLoader] Extracting embedded library: " + resourcePath);

        try (InputStream is = NativeLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException(
                    "Native library not found in embedded resources: " + resourcePath + "\n" +
                    "This binary was not compiled with the native library embedded.\n" +
                    "Solutions:\n" +
                    "  1. Set " + ENV_LIB_PATH + " to a directory containing " + libName + "\n" +
                    "  2. Copy " + libName + " to ~/.llm-fararoni/lib/\n" +
                    "  3. Rebuild with: ./scripts/prepare_native_resource.sh && mvn -Pnative package");
            }

            Files.createDirectories(target.getParent());

            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);

            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
                target.toFile().setExecutable(true);
            }

            long size = Files.size(target);
            LOG.info("[NativeLoader] Extracted: " + target + " (" + (size / 1024 / 1024) + " MB)");
        }
    }
}
