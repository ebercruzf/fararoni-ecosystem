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
package dev.fararoni.core.agent;

import dev.fararoni.bus.agent.api.ToolRegistry;
import dev.fararoni.bus.agent.api.ToolSkill;
import dev.fararoni.bus.agent.api.ToolMetadata;
import dev.fararoni.core.agent.ActionParser.ActionResult;
import dev.fararoni.core.core.hooks.PostWriteHook;
import dev.fararoni.core.core.saga.SagaOrchestrator;
import dev.fararoni.core.service.FilesystemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ActionParser Self-Healing")
class ActionParserSagaTest {
    @TempDir
    Path tempDir;

    private List<String> outputMessages;
    private FilesystemService filesystemService;
    private SagaOrchestrator sagaOrchestrator;

    @BeforeEach
    void setUp() {
        outputMessages = new ArrayList<>();
        filesystemService = new FilesystemService(tempDir);
        sagaOrchestrator = new SagaOrchestrator(new MockToolRegistry());
    }

    @Nested
    @DisplayName("Without Self-Healing (Backward Compatibility)")
    class BackwardCompatibilityTests {
        @Test
        @DisplayName("Basic constructor works without saga")
        void basicConstructorWorks() {
            ActionParser parser = new ActionParser(
                filesystemService,
                outputMessages::add
            );

            parser.processLine(">>>FILE: test.txt");
            parser.processLine("Hello World");
            parser.processLine("<<<END_FILE");

            assertTrue(outputMessages.stream().anyMatch(m -> m.contains("guardado")));
            assertTrue(Files.exists(tempDir.resolve("test.txt")));
        }

        @Test
        @DisplayName("Constructor with null saga works")
        void nullSagaWorks() {
            ActionParser parser = new ActionParser(
                filesystemService,
                outputMessages::add,
                null,
                null,
                null
            );

            parser.processLine(">>>FILE: test2.txt");
            parser.processLine("Content");
            parser.processLine("<<<END_FILE");

            assertTrue(Files.exists(tempDir.resolve("test2.txt")));
        }
    }

    @Nested
    @DisplayName("With Self-Healing (Saga + Hooks)")
    class SelfHealingTests {
        @Test
        @DisplayName("File is saved when hook approves")
        void fileSavedWhenHookApproves() {
            PostWriteHook approveHook = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path file, String sagaId) {
                    return HookResult.ok();
                }

                @Override
                public String getName() {
                    return "Approver";
                }
            };

            ActionParser parser = new ActionParser(
                filesystemService,
                outputMessages::add,
                null,
                sagaOrchestrator,
                List.of(approveHook)
            );

            parser.processLine(">>>FILE: approved.java");
            parser.processLine("public class Approved {}");
            parser.processLine("<<<END_FILE");

            assertTrue(Files.exists(tempDir.resolve("approved.java")));
            assertTrue(outputMessages.stream().anyMatch(m -> m.contains("guardado")));

            assertEquals(0, sagaOrchestrator.getActiveSagaCount());
        }

        @Test
        @DisplayName("File is rolled back when hook rejects")
        void fileRolledBackWhenHookRejects() throws IOException {
            PostWriteHook rejectHook = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path file, String sagaId) {
                    return HookResult.rollback("Tests failed");
                }

                @Override
                public String getName() {
                    return "Rejector";
                }
            };

            ActionParser parser = new ActionParser(
                filesystemService,
                outputMessages::add,
                null,
                sagaOrchestrator,
                List.of(rejectHook)
            );

            parser.processLine(">>>FILE: rejected.java");
            parser.processLine("public class Rejected {}");
            parser.processLine("<<<END_FILE");

            assertTrue(outputMessages.stream().anyMatch(m -> m.contains("rollback")));
            assertTrue(outputMessages.stream().anyMatch(m -> m.contains("Tests failed")));

            assertEquals(0, sagaOrchestrator.getActiveSagaCount());
        }

        @Test
        @DisplayName("Warning is shown but file is saved")
        void warningShownButFileSaved() throws IOException {
            PostWriteHook warningHook = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path file, String sagaId) {
                    return HookResult.warning("Checkstyle issues");
                }

                @Override
                public String getName() {
                    return "Warner";
                }
            };

            ActionParser parser = new ActionParser(
                filesystemService,
                outputMessages::add,
                null,
                sagaOrchestrator,
                List.of(warningHook)
            );

            parser.processLine(">>>FILE: warning.java");
            parser.processLine("public class Warning {}");
            parser.processLine("<<<END_FILE");

            assertTrue(Files.exists(tempDir.resolve("warning.java")));

            assertTrue(outputMessages.stream().anyMatch(m -> m.contains("Checkstyle")));
        }

        @Test
        @DisplayName("Multiple hooks are executed in order")
        void multipleHooksExecutedInOrder() {
            List<String> hookOrder = new ArrayList<>();

            PostWriteHook hook1 = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path file, String sagaId) {
                    hookOrder.add("hook1");
                    return HookResult.ok();
                }

                @Override
                public String getName() {
                    return "Hook1";
                }
            };

            PostWriteHook hook2 = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path file, String sagaId) {
                    hookOrder.add("hook2");
                    return HookResult.ok();
                }

                @Override
                public String getName() {
                    return "Hook2";
                }
            };

            ActionParser parser = new ActionParser(
                filesystemService,
                outputMessages::add,
                null,
                sagaOrchestrator,
                List.of(hook1, hook2)
            );

            parser.processLine(">>>FILE: multi.java");
            parser.processLine("public class Multi {}");
            parser.processLine("<<<END_FILE");

            assertEquals(2, hookOrder.size());
            assertEquals("hook1", hookOrder.get(0));
            assertEquals("hook2", hookOrder.get(1));
        }

        @Test
        @DisplayName("Hook chain stops on rollback")
        void hookChainStopsOnRollback() {
            List<String> hookOrder = new ArrayList<>();

            PostWriteHook hook1 = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path file, String sagaId) {
                    hookOrder.add("hook1");
                    return HookResult.rollback("Stop here");
                }

                @Override
                public String getName() {
                    return "Stopper";
                }
            };

            PostWriteHook hook2 = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path file, String sagaId) {
                    hookOrder.add("hook2");
                    return HookResult.ok();
                }

                @Override
                public String getName() {
                    return "NeverReached";
                }
            };

            ActionParser parser = new ActionParser(
                filesystemService,
                outputMessages::add,
                null,
                sagaOrchestrator,
                List.of(hook1, hook2)
            );

            parser.processLine(">>>FILE: stop.java");
            parser.processLine("public class Stop {}");
            parser.processLine("<<<END_FILE");

            assertEquals(1, hookOrder.size());
            assertEquals("hook1", hookOrder.get(0));
        }
    }

    @Nested
    @DisplayName("Action Results")
    class ActionResultsTests {
        @Test
        @DisplayName("Success result when hook approves")
        void successResultWhenApproved() {
            PostWriteHook approveHook = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path file, String sagaId) {
                    return HookResult.ok();
                }

                @Override
                public String getName() {
                    return "Approver";
                }
            };

            ActionParser parser = new ActionParser(
                filesystemService,
                outputMessages::add,
                null,
                sagaOrchestrator,
                List.of(approveHook)
            );

            parser.processLine(">>>FILE: success.java");
            parser.processLine("public class Success {}");
            parser.processLine("<<<END_FILE");

            List<ActionResult> results = parser.getResults();
            assertEquals(1, results.size());
            assertTrue(results.get(0).success());
            assertEquals("success.java", results.get(0).path());
        }

        @Test
        @DisplayName("Failure result when hook rejects")
        void failureResultWhenRejected() {
            PostWriteHook rejectHook = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path file, String sagaId) {
                    return HookResult.rollback("Rejected");
                }

                @Override
                public String getName() {
                    return "Rejector";
                }
            };

            ActionParser parser = new ActionParser(
                filesystemService,
                outputMessages::add,
                null,
                sagaOrchestrator,
                List.of(rejectHook)
            );

            parser.processLine(">>>FILE: failure.java");
            parser.processLine("public class Failure {}");
            parser.processLine("<<<END_FILE");

            List<ActionResult> results = parser.getResults();
            assertEquals(1, results.size());
            assertFalse(results.get(0).success());
            assertTrue(results.get(0).message().contains("Rollback"));
        }
    }

    @Nested
    @DisplayName("Saga Statistics")
    class SagaStatisticsTests {
        @Test
        @DisplayName("Saga statistics are updated on success")
        void sagaStatsUpdatedOnSuccess() {
            PostWriteHook approveHook = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path file, String sagaId) {
                    return HookResult.ok();
                }

                @Override
                public String getName() {
                    return "Approver";
                }
            };

            ActionParser parser = new ActionParser(
                filesystemService,
                outputMessages::add,
                null,
                sagaOrchestrator,
                List.of(approveHook)
            );

            parser.processLine(">>>FILE: file1.java");
            parser.processLine("class A {}");
            parser.processLine("<<<END_FILE");

            parser.processLine(">>>FILE: file2.java");
            parser.processLine("class B {}");
            parser.processLine("<<<END_FILE");

            var stats = sagaOrchestrator.getStatistics();
            assertEquals(2, stats.get("totalSagas"));
            assertEquals(2, stats.get("successfulSagas"));
            assertEquals(0, stats.get("compensatedSagas"));
        }

        @Test
        @DisplayName("Saga statistics are updated on rollback")
        void sagaStatsUpdatedOnRollback() {
            PostWriteHook rejectHook = new PostWriteHook() {
                @Override
                public HookResult onFileWritten(Path file, String sagaId) {
                    return HookResult.rollback("Fail");
                }

                @Override
                public String getName() {
                    return "Rejector";
                }
            };

            ActionParser parser = new ActionParser(
                filesystemService,
                outputMessages::add,
                null,
                sagaOrchestrator,
                List.of(rejectHook)
            );

            parser.processLine(">>>FILE: bad.java");
            parser.processLine("class Bad {}");
            parser.processLine("<<<END_FILE");

            var stats = sagaOrchestrator.getStatistics();
            assertEquals(1, stats.get("totalSagas"));
            assertEquals(0, stats.get("successfulSagas"));
            assertEquals(1, stats.get("compensatedSagas"));
        }
    }

    static class MockToolRegistry implements ToolRegistry {
        @Override
        public void register(ToolSkill skill) {
        }

        @Override
        public boolean unregister(String skillName) {
            return false;
        }

        @Override
        public java.util.Optional<ToolSkill> findSkill(String name) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<java.lang.reflect.Method> findAction(String skillName, String actionName) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<ToolSkill> getAllSkills() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<String> getAllSkillNames() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<ToolMetadata> getAllActionMetadata() {
            return java.util.List.of();
        }

        @Override
        public String generateToolsJson() {
            return "{}";
        }

        @Override
        public String generateToolsSummary() {
            return "";
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean hasSkill(String skillName) {
            return false;
        }

        @Override
        public void shutdownAll() {
        }

        @Override
        public void clear() {
        }
    }
}
