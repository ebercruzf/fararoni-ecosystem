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
package dev.fararoni.core.core.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class DebugLoop {
    private static final Logger LOG = Logger.getLogger(DebugLoop.class.getName());

    private static final int MAX_ERROR_LOG_LENGTH = 3000;

    public static final int DEFAULT_MAX_RETRIES = 3;

    private static final double ERROR_ESCALATION_THRESHOLD = 1.5;

    private static final Pattern ERROR_PATTERN = Pattern.compile(
            ".*(error|fail|exception|caused by|assert).*", Pattern.CASE_INSENSITIVE);

    @FunctionalInterface
    public interface LlmProvider {
        String ask(String prompt);
    }

    @FunctionalInterface
    public interface CodePatcher {
        boolean apply(Path projectPath, String llmResponse);
    }

    public record CycleResult(
            boolean success,
            int attempts,
            int maxAttempts,
            String lastError,
            List<AttemptRecord> history
    ) {
        public boolean isSuccess() {
            return success;
        }

        public String getSummary() {
            if (success) {
                return String.format("SUCCESS after %d/%d attempts", attempts, maxAttempts);
            }
            return String.format("FAILED after %d/%d attempts. Last error: %s",
                    attempts, maxAttempts, truncate(lastError, 200));
        }

        private String truncate(String s, int max) {
            if (s == null) return "";
            return s.length() > max ? s.substring(0, max) + "..." : s;
        }
    }

    public record AttemptRecord(
            int attemptNumber,
            boolean testsPassed,
            String errorLog,
            String llmPrompt,
            String llmResponse,
            boolean patchApplied
    ) {}

    private final TestRunner testRunner;
    private final LlmProvider llmProvider;
    private CodePatcher codePatcher;
    private Consumer<AttemptRecord> onAttempt;
    private CycleResult lastResult;

    public DebugLoop(TestRunner testRunner, LlmProvider llmProvider) {
        if (testRunner == null) {
            throw new IllegalArgumentException("TestRunner cannot be null");
        }
        if (llmProvider == null) {
            throw new IllegalArgumentException("LlmProvider cannot be null");
        }
        this.testRunner = testRunner;
        this.llmProvider = llmProvider;
        this.codePatcher = null;
    }

    public DebugLoop withCodePatcher(CodePatcher patcher) {
        this.codePatcher = patcher;
        return this;
    }

    public DebugLoop onAttempt(Consumer<AttemptRecord> callback) {
        this.onAttempt = callback;
        return this;
    }

    public CycleResult runCycle(Path projectPath, int maxRetries) {
        if (projectPath == null || !Files.isDirectory(projectPath)) {
            lastResult = new CycleResult(false, 0, maxRetries,
                    "Project path is invalid: " + projectPath, List.of());
            return lastResult;
        }

        int actualMax = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;
        List<AttemptRecord> history = new ArrayList<>();

        LOG.info("[DEBUG-LOOP] Starting verification cycle at: " + projectPath);

        for (int attempt = 1; attempt <= actualMax; attempt++) {
            LOG.info(String.format("[DEBUG-LOOP] Attempt %d/%d", attempt, actualMax));

            TestRunner.TestResult testResult = testRunner.runTests(projectPath);

            if (testResult.isSuccess()) {
                LOG.info("[DEBUG-LOOP] Tests PASSED on attempt " + attempt);
                AttemptRecord record = new AttemptRecord(attempt, true, "", "", "", false);
                history.add(record);
                notifyAttempt(record);

                lastResult = new CycleResult(true, attempt, actualMax, "", history);
                return lastResult;
            }

            LOG.warning("[DEBUG-LOOP] Tests FAILED. Analyzing errors...");

            String errorLog = extractErrorLog(testResult);
            String prompt = buildFixPrompt(projectPath, errorLog, attempt, actualMax);

            LOG.info("[DEBUG-LOOP] Asking LLM for fix...");
            String llmResponse;
            try {
                llmResponse = llmProvider.ask(prompt);
            } catch (Exception e) {
                LOG.severe("[DEBUG-LOOP] LLM error: " + e.getMessage());
                llmResponse = "";
            }

            boolean patchApplied = false;
            if (codePatcher != null && !llmResponse.isBlank()) {
                LOG.info("[DEBUG-LOOP] Applying fix...");
                try {
                    patchApplied = codePatcher.apply(projectPath, llmResponse);
                } catch (Exception e) {
                    LOG.warning("[DEBUG-LOOP] Patch error: " + e.getMessage());
                }
            }

            AttemptRecord record = new AttemptRecord(
                    attempt, false, errorLog, prompt, llmResponse, patchApplied);
            history.add(record);
            notifyAttempt(record);

            if (!patchApplied && codePatcher != null) {
                LOG.warning("[DEBUG-LOOP] Patch could not be applied. Continuing...");
            }
        }

        String lastError = history.isEmpty() ? "Unknown error" :
                history.get(history.size() - 1).errorLog();
        LOG.severe("[DEBUG-LOOP] Max attempts reached. Tests still failing.");

        lastResult = new CycleResult(false, actualMax, actualMax, lastError, history);
        return lastResult;
    }

    public CycleResult runCycle(Path projectPath) {
        return runCycle(projectPath, DEFAULT_MAX_RETRIES);
    }

    public boolean verify(Path projectPath) {
        TestRunner.TestResult result = testRunner.runTests(projectPath);
        return result.isSuccess();
    }

    public record SafeCycleResult(
            boolean success,
            boolean wasRolledBack,
            String finalContent,
            String originalContent,
            int attempts,
            int baselineErrors,
            int finalErrors,
            String abortReason
    ) {
        public boolean isSuccess() {
            return success;
        }

        public boolean wasRolledBack() {
            return wasRolledBack;
        }

        public String getSummary() {
            if (success) {
                return String.format("SUCCESS after %d attempts", attempts);
            }
            if (wasRolledBack) {
                return String.format("ROLLED BACK after %d attempts. Reason: %s", attempts, abortReason);
            }
            return String.format("FAILED after %d attempts. Errors: %d -> %d", attempts, baselineErrors, finalErrors);
        }
    }

    public SafeCycleResult runSafeCycle(Path targetFile, int maxRetries) {
        if (targetFile == null || !Files.exists(targetFile)) {
            return new SafeCycleResult(false, false, "", "", 0, 0, 0,
                    "Target file does not exist: " + targetFile);
        }

        String originalContent;
        try {
            originalContent = Files.readString(targetFile);
        } catch (IOException e) {
            return new SafeCycleResult(false, false, "", "", 0, 0, 0,
                    "Cannot read file: " + e.getMessage());
        }

        Path projectRoot = targetFile.getParent();
        String currentContent = originalContent;
        int actualMax = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;

        LOG.info("[DEBUG-LOOP] Starting SAFE cycle with rollback protection");

        TestRunner.TestResult initialResult = testRunner.runTests(projectRoot);
        int baselineErrors = countErrorLines(initialResult.getFailureLog());

        if (initialResult.isSuccess()) {
            LOG.info("[DEBUG-LOOP] Original code already passes tests!");
            return new SafeCycleResult(true, false, originalContent, originalContent,
                    0, baselineErrors, 0, null);
        }

        LOG.info("[DEBUG-LOOP] Baseline errors: " + baselineErrors);

        for (int attempt = 1; attempt <= actualMax; attempt++) {
            LOG.info(String.format("[DEBUG-LOOP] Safe attempt %d/%d", attempt, actualMax));

            String errorLog = extractErrorLog(initialResult);

            String prompt = buildSafeFixPrompt(currentContent, errorLog, attempt, actualMax);
            String llmResponse;
            try {
                llmResponse = llmProvider.ask(prompt);
            } catch (Exception e) {
                LOG.severe("[DEBUG-LOOP] LLM error: " + e.getMessage());
                continue;
            }

            String proposedFix = parseCodeFromResponse(llmResponse, currentContent);

            try {
                Files.writeString(targetFile, proposedFix);
            } catch (IOException e) {
                LOG.severe("[DEBUG-LOOP] I/O error writing fix. Aborting.");
                restoreOriginal(targetFile, originalContent);
                return new SafeCycleResult(false, true, originalContent, originalContent,
                        attempt, baselineErrors, baselineErrors, "I/O error: " + e.getMessage());
            }

            TestRunner.TestResult newResult = testRunner.runTests(projectRoot);

            if (newResult.isSuccess()) {
                LOG.info("[DEBUG-LOOP] SUCCESS! Tests passed on attempt " + attempt);
                return new SafeCycleResult(true, false, proposedFix, originalContent,
                        attempt, baselineErrors, 0, null);
            }

            int newErrors = countErrorLines(newResult.getFailureLog());
            LOG.info("[DEBUG-LOOP] Error count: " + baselineErrors + " -> " + newErrors);

            if (newErrors > baselineErrors * ERROR_ESCALATION_THRESHOLD) {
                LOG.warning("[DEBUG-LOOP] ERROR ESCALATION DETECTED! Rolling back.");
                restoreOriginal(targetFile, originalContent);
                return new SafeCycleResult(false, true, originalContent, originalContent,
                        attempt, baselineErrors, newErrors,
                        "Error escalation: " + baselineErrors + " -> " + newErrors);
            }

            currentContent = proposedFix;
            initialResult = newResult;
        }

        LOG.severe("[DEBUG-LOOP] Max attempts exhausted. ROLLING BACK to original.");
        restoreOriginal(targetFile, originalContent);

        int finalErrors = countErrorLines(initialResult.getFailureLog());
        return new SafeCycleResult(false, true, originalContent, originalContent,
                actualMax, baselineErrors, finalErrors, "Max attempts exhausted");
    }

    private void restoreOriginal(Path file, String originalContent) {
        try {
            Files.writeString(file, originalContent);
            LOG.info("[DEBUG-LOOP] Restored original content");
        } catch (IOException e) {
            LOG.severe("[DEBUG-LOOP] CRITICAL: Failed to restore original! " + e.getMessage());
        }
    }

    private int countErrorLines(String log) {
        if (log == null || log.isBlank()) return 0;
        return (int) log.lines()
                .filter(line -> ERROR_PATTERN.matcher(line).matches())
                .count();
    }

    private String buildSafeFixPrompt(String code, String errors, int attempt, int maxAttempts) {
        String safeErrors = errors.length() > 1500
                ? errors.substring(0, 1500) + "\n...[TRUNCATED]"
                : errors;

        StringBuilder sb = new StringBuilder();
        sb.append("# FIX_REQUEST (Attempt ").append(attempt).append("/").append(maxAttempts).append(")\n\n");
        sb.append("## CURRENT CODE:\n```\n").append(code).append("\n```\n\n");
        sb.append("## ERRORS:\n```\n").append(safeErrors).append("\n```\n\n");
        sb.append("## INSTRUCTION:\n");
        sb.append("Return the FULL fixed code. No markdown code blocks, just the raw code.\n");
        sb.append("Focus on fixing the specific error. Do not add unnecessary changes.\n");

        if (attempt > 1) {
            sb.append("\nNOTE: Previous attempts failed. Try a DIFFERENT approach.\n");
        }

        return sb.toString();
    }

    private String parseCodeFromResponse(String response, String fallback) {
        if (response == null || response.isBlank()) {
            return fallback;
        }

        if (response.contains("```")) {
            int start = response.indexOf("```");
            int end = response.lastIndexOf("```");
            if (end > start) {
                String content = response.substring(start + 3, end);
                if (content.startsWith("java") || content.startsWith("python") ||
                    content.startsWith("javascript") || content.startsWith("typescript")) {
                    int newline = content.indexOf('\n');
                    if (newline > 0) {
                        content = content.substring(newline + 1);
                    }
                }
                return content.trim();
            }
        }

        if (response.length() < 20) {
            return fallback;
        }

        return response.trim();
    }

    private String extractErrorLog(TestRunner.TestResult result) {
        String errorLog = result.errorLog();
        if (errorLog.isBlank()) {
            errorLog = result.output();
        }

        if (errorLog.length() > MAX_ERROR_LOG_LENGTH) {
            errorLog = errorLog.substring(0, MAX_ERROR_LOG_LENGTH) + "\n...[TRUNCATED]";
        }

        return errorLog;
    }

    private String buildFixPrompt(Path projectPath, String errorLog, int attempt, int maxAttempts) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Test Failure Analysis - Attempt ").append(attempt).append("/").append(maxAttempts);
        sb.append("\n\n");

        sb.append("The automated tests failed with the following error log:\n\n");
        sb.append("```\n").append(errorLog).append("\n```\n\n");

        sb.append("## Project Information\n");
        sb.append("- Path: ").append(projectPath).append("\n");
        sb.append("- Test Framework: ").append(testRunner.getLastResult().framework()).append("\n\n");

        sb.append("## Instructions\n");
        sb.append("1. Analyze the error log carefully\n");
        sb.append("2. Identify the root cause of the failure\n");
        sb.append("3. Provide a fix using SEARCH/REPLACE blocks:\n\n");

        sb.append("```\n");
        sb.append("<<<<<<< SEARCH\n");
        sb.append("// The exact code to find\n");
        sb.append("=======\n");
        sb.append("// The corrected code\n");
        sb.append(">>>>>>> REPLACE\n");
        sb.append("```\n\n");

        sb.append("If multiple files need changes, include the file path before each block:\n\n");
        sb.append("```\n");
        sb.append("FILE: src/main/java/Example.java\n");
        sb.append("<<<<<<< SEARCH\n");
        sb.append("...\n");
        sb.append("=======\n");
        sb.append("...\n");
        sb.append(">>>>>>> REPLACE\n");
        sb.append("```\n\n");

        if (attempt > 1) {
            sb.append("NOTE: Previous fix attempts have failed. ");
            sb.append("Please try a different approach.\n");
        }

        return sb.toString();
    }

    public String buildCustomPrompt(String errorLog, String customInstructions) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Test Failure - Fix Request\n\n");
        sb.append("## Error Log\n```\n").append(errorLog).append("\n```\n\n");
        sb.append("## Instructions\n").append(customInstructions);
        return sb.toString();
    }

    private void notifyAttempt(AttemptRecord record) {
        if (onAttempt != null) {
            try {
                onAttempt.accept(record);
            } catch (Exception e) {
                LOG.warning("[DEBUG-LOOP] Callback error: " + e.getMessage());
            }
        }
    }

    public CycleResult getLastResult() {
        return lastResult;
    }

    public TestRunner getTestRunner() {
        return testRunner;
    }

    public LlmProvider getLlmProvider() {
        return llmProvider;
    }
}
