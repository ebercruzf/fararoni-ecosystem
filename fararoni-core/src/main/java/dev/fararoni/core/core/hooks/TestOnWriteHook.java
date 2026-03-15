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
package dev.fararoni.core.core.hooks;

import dev.fararoni.core.core.commands.TestCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class TestOnWriteHook implements PostWriteHook {
    private static final Logger LOG = Logger.getLogger(TestOnWriteHook.class.getName());

    private static final Set<String> CODE_EXTENSIONS = Set.of(
        ".java", ".kt", ".scala",
        ".js", ".ts", ".jsx", ".tsx",
        ".py",
        ".go",
        ".rs",
        ".rb",
        ".cs"
    );

    private static final Set<String> BUILD_FILES = Set.of(
        "pom.xml", "build.gradle", "build.gradle.kts",
        "package.json", "Cargo.toml", "go.mod",
        "pyproject.toml", "setup.py"
    );

    private final TestCommand testCommand;

    public TestOnWriteHook(TestCommand testCommand) {
        this.testCommand = testCommand;
    }

    @Override
    public HookResult onFileWritten(Path writtenFile, String sagaId) {
        String fileName = writtenFile.getFileName().toString();

        if (!isCodeFile(fileName)) {
            LOG.fine(() -> "[RegressionGuard] Skipping non-code file: " + fileName);
            return HookResult.ok();
        }

        LOG.info(() -> "[RegressionGuard] Validating code change: " + fileName + " (saga: " + sagaId + ")");

        Path projectRoot = findProjectRoot(writtenFile);
        if (projectRoot == null) {
            LOG.warning(() -> "[RegressionGuard] Could not find project root for: " + writtenFile);
            return HookResult.warning("Could not find project root - skipping test validation");
        }

        try {
            TestCommand.TestResult result = testCommand.executeQuick(projectRoot);

            if (result.success()) {
                LOG.info(() -> String.format("[RegressionGuard] Tests PASSED in %dms", result.durationMs()));
                return HookResult.ok();
            } else {
                String errorMsg = "Regression detected: " + result.errorSummary();
                LOG.warning(() -> "[RegressionGuard] " + errorMsg);
                return HookResult.rollback(errorMsg);
            }
        } catch (Exception e) {
            LOG.warning(() -> "[RegressionGuard] Test execution error: " + e.getMessage());
            return HookResult.warning("Test execution failed: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "RegressionGuard";
    }

    private boolean isCodeFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return CODE_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }

    private Path findProjectRoot(Path startPath) {
        Path current = startPath.toAbsolutePath();

        while (current != null) {
            for (String buildFile : BUILD_FILES) {
                if (Files.exists(current.resolve(buildFile))) {
                    return current;
                }
            }

            if (Files.exists(current.resolve(".git"))) {
                return current;
            }

            current = current.getParent();
        }

        return null;
    }
}
