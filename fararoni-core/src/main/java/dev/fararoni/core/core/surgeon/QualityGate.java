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
package dev.fararoni.core.core.surgeon;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class QualityGate {
    private static final Logger LOG = Logger.getLogger(QualityGate.class.getName());

    private static final int LINTER_TIMEOUT_SECONDS = 30;

    private static final int CAPABILITY_CHECK_TIMEOUT_SECONDS = 5;

    public enum InspectionResult {
        PASSED,
        FAILED,
        UNSUPPORTED,
        LINTER_ERROR,
        TOOL_NOT_AVAILABLE
    }

    public enum SupportedLanguage {
        PYTHON("python3", "--version"),
        JAVA("javac", "-version"),
        JAVASCRIPT("node", "--version"),
        TYPESCRIPT("npx", "tsc", "--version"),
        GO("go", "version"),
        RUST("rustc", "--version"),
        RUBY("ruby", "--version");

        private final String[] versionCommand;

        SupportedLanguage(String... versionCommand) {
            this.versionCommand = versionCommand;
        }

        public String[] getVersionCommand() {
            return versionCommand;
        }

        public String getToolName() {
            return versionCommand[0];
        }
    }

    private InspectionResult lastResult = null;

    private List<String> lastErrors = new ArrayList<>();

    private final Map<SupportedLanguage, Boolean> capabilityCache = new EnumMap<>(SupportedLanguage.class);

    private boolean strictMode = false;

    public boolean isToolAvailable(SupportedLanguage language) {
        if (capabilityCache.containsKey(language)) {
            return capabilityCache.get(language);
        }

        boolean available = checkToolAvailability(language);
        capabilityCache.put(language, available);

        if (available) {
            LOG.info("[QUALITY] Tool available: " + language.getToolName());
        } else {
            LOG.warning("[QUALITY] Tool NOT available: " + language.getToolName());
        }

        return available;
    }

    private boolean checkToolAvailability(SupportedLanguage language) {
        try {
            ProcessBuilder pb = new ProcessBuilder(language.getVersionCommand());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                }
            }

            boolean finished = process.waitFor(CAPABILITY_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<SupportedLanguage, Boolean> detectAllCapabilities() {
        for (SupportedLanguage lang : SupportedLanguage.values()) {
            isToolAvailable(lang);
        }
        return new EnumMap<>(capabilityCache);
    }

    private SupportedLanguage getLanguageForFile(String fileName) {
        if (fileName.endsWith(".py")) return SupportedLanguage.PYTHON;
        if (fileName.endsWith(".java")) return SupportedLanguage.JAVA;
        if (fileName.endsWith(".js")) return SupportedLanguage.JAVASCRIPT;
        if (fileName.endsWith(".ts")) return SupportedLanguage.TYPESCRIPT;
        if (fileName.endsWith(".go")) return SupportedLanguage.GO;
        if (fileName.endsWith(".rs")) return SupportedLanguage.RUST;
        if (fileName.endsWith(".rb")) return SupportedLanguage.RUBY;
        return null;
    }

    public void clearCapabilityCache() {
        capabilityCache.clear();
        LOG.info("[QUALITY] Capability cache cleared");
    }

    public void setStrictMode(boolean strict) {
        this.strictMode = strict;
        LOG.info("[QUALITY] Strict mode: " + strict);
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public String getCapabilitiesReport() {
        detectAllCapabilities();

        StringBuilder sb = new StringBuilder();
        sb.append("[QUALITY CAPABILITIES]\n");

        for (SupportedLanguage lang : SupportedLanguage.values()) {
            Boolean available = capabilityCache.get(lang);
            String status = available != null && available ? "AVAILABLE" : "NOT FOUND";
            sb.append(String.format("  %-12s (%s): %s%n",
                    lang.name(), lang.getToolName(), status));
        }

        return sb.toString();
    }

    public List<String> inspect(Path filePath) {
        lastErrors = new ArrayList<>();
        lastResult = InspectionResult.PASSED;

        if (filePath == null || !Files.exists(filePath)) {
            lastErrors.add("File does not exist: " + filePath);
            lastResult = InspectionResult.LINTER_ERROR;
            return lastErrors;
        }

        String fileName = filePath.getFileName().toString().toLowerCase();

        SupportedLanguage language = getLanguageForFile(fileName);

        if (language == null) {
            lastResult = InspectionResult.UNSUPPORTED;
            LOG.fine("[QUALITY] Unsupported file type: " + fileName);
            return lastErrors;
        }

        if (!isToolAvailable(language)) {
            lastErrors.add("Tool not available: " + language.getToolName() +
                    ". Install it to enable " + language.name() + " inspection.");
            lastResult = InspectionResult.TOOL_NOT_AVAILABLE;

            if (strictMode) {
                LOG.warning("[QUALITY] Strict mode: failing due to missing tool: " + language.getToolName());
                return lastErrors;
            } else {
                LOG.info("[QUALITY] Graceful degradation: skipping inspection for " + fileName);
                lastErrors.clear();
                return lastErrors;
            }
        }

        try {
            ProcessBuilder pb = createLinterProcess(fileName, filePath);

            if (pb == null) {
                lastResult = InspectionResult.UNSUPPORTED;
                LOG.fine("[QUALITY] Unsupported file type: " + fileName);
                return lastErrors;
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lastErrors.add(line);
                }
            }

            boolean finished = process.waitFor(LINTER_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                lastErrors.add("Linter timeout after " + LINTER_TIMEOUT_SECONDS + " seconds");
                lastResult = InspectionResult.LINTER_ERROR;
                return lastErrors;
            }

            int exitCode = process.exitValue();

            if (exitCode == 0) {
                lastErrors.clear();
                lastResult = InspectionResult.PASSED;
                LOG.info("[QUALITY] Inspection PASSED: " + filePath);
            } else {
                lastResult = InspectionResult.FAILED;
                LOG.warning("[QUALITY] Inspection FAILED: " + filePath +
                        " (" + lastErrors.size() + " issues)");
            }
        } catch (Exception e) {
            lastErrors.add("Error running linter: " + e.getMessage());
            lastResult = InspectionResult.LINTER_ERROR;
            LOG.severe("[QUALITY] Linter error: " + e.getMessage());
        }

        return lastErrors;
    }

    public List<String> inspectCode(String code, String language) {
        if (code == null || code.isBlank()) {
            lastErrors = List.of("Empty code");
            lastResult = InspectionResult.FAILED;
            return lastErrors;
        }

        try {
            String extension = getExtensionForLanguage(language);
            Path tempFile = Files.createTempFile("fararoni_inspect_", extension);
            Files.writeString(tempFile, code);

            try {
                return inspect(tempFile);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            lastErrors = List.of("Error creating temp file: " + e.getMessage());
            lastResult = InspectionResult.LINTER_ERROR;
            return lastErrors;
        }
    }

    private ProcessBuilder createLinterProcess(String fileName, Path filePath) {
        String absolutePath = filePath.toAbsolutePath().toString();

        if (fileName.endsWith(".py")) {
            return new ProcessBuilder("python3", "-m", "py_compile", absolutePath);
        } else if (fileName.endsWith(".java")) {
            return new ProcessBuilder("javac", "-d", "/tmp", absolutePath);
        } else if (fileName.endsWith(".js")) {
            return new ProcessBuilder("node", "--check", absolutePath);
        } else if (fileName.endsWith(".ts")) {
            return new ProcessBuilder("npx", "tsc", "--noEmit", absolutePath);
        } else if (fileName.endsWith(".go")) {
            return new ProcessBuilder("go", "vet", absolutePath);
        } else if (fileName.endsWith(".rs")) {
            return new ProcessBuilder("rustc", "--emit=metadata", absolutePath);
        } else if (fileName.endsWith(".rb")) {
            return new ProcessBuilder("ruby", "-c", absolutePath);
        }

        return null;
    }

    private String getExtensionForLanguage(String language) {
        if (language == null) return ".txt";

        return switch (language.toLowerCase()) {
            case "python", "py" -> ".py";
            case "java" -> ".java";
            case "javascript", "js" -> ".js";
            case "typescript", "ts" -> ".ts";
            case "go", "golang" -> ".go";
            case "rust", "rs" -> ".rs";
            case "ruby", "rb" -> ".rb";
            default -> ".txt";
        };
    }

    public InspectionResult getLastResult() {
        return lastResult;
    }

    public List<String> getLastErrors() {
        return new ArrayList<>(lastErrors);
    }

    public boolean wasLastInspectionSuccessful() {
        if (lastResult == InspectionResult.PASSED ||
            lastResult == InspectionResult.UNSUPPORTED) {
            return true;
        }

        if (lastResult == InspectionResult.TOOL_NOT_AVAILABLE && !strictMode) {
            return true;
        }

        return false;
    }

    public String getFirstError() {
        return lastErrors.isEmpty() ? null : lastErrors.get(0);
    }

    public String getInspectionReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("[QUALITY REPORT]\n");
        sb.append("Result: ").append(lastResult).append("\n");
        sb.append("Issues: ").append(lastErrors.size()).append("\n");

        if (!lastErrors.isEmpty()) {
            sb.append("Details:\n");
            int limit = Math.min(lastErrors.size(), 10);
            for (int i = 0; i < limit; i++) {
                sb.append("  ").append(i + 1).append(". ").append(lastErrors.get(i)).append("\n");
            }
            if (lastErrors.size() > 10) {
                sb.append("  ... and ").append(lastErrors.size() - 10).append(" more\n");
            }
        }

        return sb.toString();
    }
}
