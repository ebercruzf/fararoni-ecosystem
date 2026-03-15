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

import dev.fararoni.core.core.hooks.PostWriteHook.HookResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("PostWriteHook")
class PostWriteHookTest {
    @Nested
    @DisplayName("HookResult")
    class HookResultTests {
        @Test
        @DisplayName("ok() creates successful result without rollback")
        void okCreatesSuccessfulResult() {
            HookResult result = HookResult.ok();

            assertTrue(result.success());
            assertFalse(result.shouldRollback());
            assertNull(result.message());
        }

        @Test
        @DisplayName("rollback() creates result that triggers rollback")
        void rollbackCreatesRollbackResult() {
            String reason = "Tests failed: 3 assertions failed";
            HookResult result = HookResult.rollback(reason);

            assertFalse(result.success());
            assertTrue(result.shouldRollback());
            assertEquals(reason, result.message());
        }

        @Test
        @DisplayName("warning() creates successful result with message")
        void warningCreatesSuccessWithMessage() {
            String msg = "Checkstyle warnings detected";
            HookResult result = HookResult.warning(msg);

            assertTrue(result.success());
            assertFalse(result.shouldRollback());
            assertEquals(msg, result.message());
        }

        @Test
        @DisplayName("error() creates failed result without rollback")
        void errorCreatesFailedWithoutRollback() {
            String msg = "Could not execute validation";
            HookResult result = HookResult.error(msg);

            assertFalse(result.success());
            assertFalse(result.shouldRollback());
            assertEquals(msg, result.message());
        }
    }

    @Nested
    @DisplayName("Custom Hook Implementation")
    class CustomHookTests {
        @Test
        @DisplayName("Hook can approve file changes")
        void hookCanApproveChanges() {
            PostWriteHook approveAllHook = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path writtenFile, String sagaId) {
                    return HookResult.ok();
                }

                @Override
                public String getName() {
                    return "ApproveAll";
                }
            };

            HookResult result = approveAllHook.onFileWritten(
                Path.of("/tmp/test.java"), "saga-123"
            );

            assertTrue(result.success());
            assertFalse(result.shouldRollback());
            assertEquals("ApproveAll", approveAllHook.getName());
        }

        @Test
        @DisplayName("Hook can reject file changes")
        void hookCanRejectChanges() {
            PostWriteHook rejectAllHook = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path writtenFile, String sagaId) {
                    return HookResult.rollback("Rejected: " + writtenFile.getFileName());
                }

                @Override
                public String getName() {
                    return "RejectAll";
                }
            };

            HookResult result = rejectAllHook.onFileWritten(
                Path.of("/tmp/bad.java"), "saga-456"
            );

            assertFalse(result.success());
            assertTrue(result.shouldRollback());
            assertTrue(result.message().contains("bad.java"));
        }

        @Test
        @DisplayName("Hook receives correct parameters")
        void hookReceivesCorrectParameters() {
            final Path[] capturedPath = new Path[1];
            final String[] capturedSagaId = new String[1];

            PostWriteHook capturingHook = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path writtenFile, String sagaId) {
                    capturedPath[0] = writtenFile;
                    capturedSagaId[0] = sagaId;
                    return HookResult.ok();
                }

                @Override
                public String getName() {
                    return "Capturing";
                }
            };

            Path expectedPath = Path.of("/src/main/java/App.java");
            String expectedSagaId = "saga-test-789";

            capturingHook.onFileWritten(expectedPath, expectedSagaId);

            assertEquals(expectedPath, capturedPath[0]);
            assertEquals(expectedSagaId, capturedSagaId[0]);
        }
    }

    @Nested
    @DisplayName("File Type Filtering Hook")
    class FileTypeFilteringTests {
        PostWriteHook javaOnlyHook = new PostWriteHook() {
            @Override
            public HookResult onFileWritten(Path writtenFile, String sagaId) {
                String fileName = writtenFile.getFileName().toString();
                if (fileName.endsWith(".java")) {
                    return HookResult.rollback("Java files need review");
                }
                return HookResult.ok();
            }

            @Override
            public String getName() {
                return "JavaOnly";
            }
        };

        @Test
        @DisplayName("Hook can filter by file extension - Java file")
        void hookFiltersJavaFiles() {
            HookResult result = javaOnlyHook.onFileWritten(
                Path.of("/src/App.java"), "saga-1"
            );

            assertTrue(result.shouldRollback());
        }

        @Test
        @DisplayName("Hook can filter by file extension - non-Java file")
        void hookAllowsNonJavaFiles() {
            HookResult result = javaOnlyHook.onFileWritten(
                Path.of("/config/app.properties"), "saga-2"
            );

            assertTrue(result.success());
            assertFalse(result.shouldRollback());
        }
    }
}
