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
package dev.fararoni.core.router;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eber Cruz
 */
@DisplayName("BasicRouterService")
class BasicRouterServiceTest {
    private BasicRouterService router;

    @BeforeEach
    void setUp() {
        router = new BasicRouterService();
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {
        @Test
        @DisplayName("should be available after creation")
        void shouldBeAvailableAfterCreation() {
            assertThat(router.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("should not be LLM based")
        void shouldNotBeLlmBased() {
            assertThat(router.isLlmBased()).isFalse();
        }

        @Test
        @DisplayName("should have correct name")
        void shouldHaveCorrectName() {
            assertThat(router.getName()).isEqualTo("BasicRouter");
        }

        @Test
        @DisplayName("warmup should complete without error")
        void warmupShouldCompleteWithoutError() {
            router.warmup();
            assertThat(router.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("shutdown should mark router as unavailable")
        void shutdownShouldMarkRouterAsUnavailable() {
            router.shutdown();
            assertThat(router.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should provide stats")
        void shouldProvideStats() {
            router.route("test");
            RouterService.RouterStats stats = router.getStats();

            assertThat(stats).isNotNull();
            assertThat(stats.totalRequests()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Git Routing")
    class GitRoutingTests {
        @ParameterizedTest
        @DisplayName("should detect push commands")
        @ValueSource(strings = {
            "push",
            "sube los cambios",
            "sube cambios",
            "subir los cambios",
            "publica los cambios"
        })
        void shouldDetectPushCommands(String input) {
            RoutingResult result = router.route(input);

            assertThat(result.tool()).isEqualTo(Tool.GIT);
            assertThat(result.action()).isEqualTo("push");
            assertThat(result.fromFallback()).isTrue();
        }

        @ParameterizedTest
        @DisplayName("should detect pull commands")
        @ValueSource(strings = {
            "pull",
            "baja los cambios",
            "bajar cambios",
            "descarga los cambios",
            "actualiza los cambios"
        })
        void shouldDetectPullCommands(String input) {
            RoutingResult result = router.route(input);

            assertThat(result.tool()).isEqualTo(Tool.GIT);
            assertThat(result.action()).isEqualTo("pull");
        }

        @ParameterizedTest
        @DisplayName("should detect status commands")
        @ValueSource(strings = {
            "status",
            "git status",
            "estado"
        })
        void shouldDetectStatusCommands(String input) {
            RoutingResult result = router.route(input);

            assertThat(result.tool()).isEqualTo(Tool.GIT);
            assertThat(result.action()).isEqualTo("status");
        }

        @ParameterizedTest
        @DisplayName("should detect diff commands")
        @ValueSource(strings = {
            "diff",
            "diferencias",
            "que cambio",
            "que cambió"
        })
        void shouldDetectDiffCommands(String input) {
            RoutingResult result = router.route(input);

            assertThat(result.tool()).isEqualTo(Tool.GIT);
            assertThat(result.action()).isEqualTo("diff");
        }

        @Test
        @DisplayName("should detect commit with message")
        void shouldDetectCommitWithMessage() {
            RoutingResult result = router.route("commitea con mensaje fix bug");

            assertThat(result.tool()).isEqualTo(Tool.GIT);
            assertThat(result.action()).isEqualTo("commit");
            assertThat(result.hasParameter("message")).isTrue();
            assertThat(result.<String>getParameter("message")).contains("fix bug");
        }

        @Test
        @DisplayName("should detect commit without message")
        void shouldDetectCommitWithoutMessage() {
            RoutingResult result = router.route("commit");

            assertThat(result.tool()).isEqualTo(Tool.GIT);
        }

        @Test
        @DisplayName("should detect branch with name")
        void shouldDetectBranchWithName() {
            RoutingResult result = router.route("rama feature/login");

            assertThat(result.tool()).isEqualTo(Tool.GIT);
            assertThat(result.action()).isEqualTo("branch");
            assertThat(result.getParameter("branch", "")).contains("feature/login");
        }
    }

    @Nested
    @DisplayName("File Routing")
    class FileRoutingTests {
        @ParameterizedTest
        @DisplayName("should detect load file commands")
        @CsvSource({
            "carga pom.xml, pom.xml",
            "cargar el archivo Main.java, Main.java",
            "load src/App.java, src/App.java",
            "lee README.md, README.md",
            "abre config.yaml, config.yaml"
        })
        void shouldDetectLoadFileCommands(String input, String expectedFile) {
            RoutingResult result = router.route(input);

            assertThat(result.tool()).isEqualTo(Tool.FILE);
            assertThat(result.action()).isEqualTo("load");
            assertThat(result.<String>getParameter("file")).isEqualTo(expectedFile);
        }

        @ParameterizedTest
        @DisplayName("should detect unload file commands")
        @CsvSource({
            "cierra pom.xml, pom.xml",
            "cierra Main.java, Main.java",
            "unload App.java, App.java",
            "close config.yaml, config.yaml"
        })
        void shouldDetectUnloadFileCommands(String input, String expectedFile) {
            RoutingResult result = router.route(input);

            assertThat(result.tool()).isEqualTo(Tool.FILE);
            assertThat(result.action()).isEqualTo("unload");
            assertThat(result.<String>getParameter("file")).isEqualTo(expectedFile);
        }

        @Test
        @DisplayName("should detect search command")
        void shouldDetectSearchCommand() {
            RoutingResult result = router.route("busca getUserById");

            assertThat(result.tool()).isEqualTo(Tool.FILE);
            assertThat(result.action()).isEqualTo("search");
            assertThat(result.hasParameter("query")).isTrue();
        }
    }

    @Nested
    @DisplayName("Config Routing")
    class ConfigRoutingTests {
        @Test
        @DisplayName("should detect show config command")
        void shouldDetectShowConfigCommand() {
            RoutingResult result = router.route("muestra la configuracion");

            assertThat(result.tool()).isEqualTo(Tool.CONFIG);
            assertThat(result.action()).isEqualTo("show");
        }

        @ParameterizedTest
        @DisplayName("should detect config show variants")
        @ValueSource(strings = {
            "muestra config",
            "mostrar configuracion",
            "show config",
            "ver settings"
        })
        void shouldDetectConfigShowVariants(String input) {
            RoutingResult result = router.route(input);

            assertThat(result.tool()).isEqualTo(Tool.CONFIG);
            assertThat(result.action()).isEqualTo("show");
        }

        @Test
        @DisplayName("should detect config set command")
        void shouldDetectConfigSetCommand() {
            RoutingResult result = router.route("configura mi api key");

            assertThat(result.tool()).isEqualTo(Tool.CONFIG);
            assertThat(result.action()).isEqualTo("set");
        }
    }

    @Nested
    @DisplayName("Feature Routing")
    class FeatureRoutingTests {
        @Test
        @DisplayName("should detect plan feature command")
        void shouldDetectPlanFeatureCommand() {
            RoutingResult result = router.route("planifica una feature: login con OAuth");

            assertThat(result.tool()).isEqualTo(Tool.FEATURE);
            assertThat(result.action()).isEqualTo("plan");
            assertThat(result.hasParameter("description")).isTrue();
        }

        @Test
        @DisplayName("should detect execute feature command")
        void shouldDetectExecuteFeatureCommand() {
            RoutingResult result = router.route("ejecuta el plan de autenticacion");

            assertThat(result.tool()).isEqualTo(Tool.FEATURE);
            assertThat(result.action()).isEqualTo("execute");
        }
    }

    @Nested
    @DisplayName("Chat/Default Routing")
    class ChatRoutingTests {
        @ParameterizedTest
        @DisplayName("should default to CHAT for questions")
        @ValueSource(strings = {
            "como funciona el patron Strategy?",
            "que es dependency injection?",
            "explica los principios SOLID",
            "ayuda con este error",
            "dime como resolver este problema"
        })
        void shouldDefaultToChatForQuestions(String input) {
            RoutingResult result = router.route(input);

            assertThat(result.tool()).isEqualTo(Tool.CHAT);
            assertThat(result.action()).isEqualTo("message");
            assertThat(result.confidence()).isGreaterThanOrEqualTo(0.9);
        }

        @Test
        @DisplayName("should return CHAT for empty input")
        void shouldReturnChatForEmptyInput() {
            RoutingResult result = router.route("");

            assertThat(result.tool()).isEqualTo(Tool.CHAT);
        }

        @Test
        @DisplayName("should return CHAT for null input")
        void shouldReturnChatForNullInput() {
            RoutingResult result = router.route(null);

            assertThat(result.tool()).isEqualTo(Tool.CHAT);
        }

        @Test
        @DisplayName("should return CHAT for whitespace input")
        void shouldReturnChatForWhitespaceInput() {
            RoutingResult result = router.route("   ");

            assertThat(result.tool()).isEqualTo(Tool.CHAT);
        }
    }

    @Nested
    @DisplayName("RoutingResult Properties")
    class RoutingResultPropertiesTests {
        @Test
        @DisplayName("should mark fallback results correctly")
        void shouldMarkFallbackResultsCorrectly() {
            RoutingResult result = router.route("push");

            assertThat(result.fromFallback()).isTrue();
            assertThat(result.confidence()).isEqualTo(0.6);
        }

        @Test
        @DisplayName("should include latency in results")
        void shouldIncludeLatencyInResults() {
            RoutingResult result = router.route("carga pom.xml");

            assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should provide JSON representation")
        void shouldProvideJsonRepresentation() {
            RoutingResult result = router.route("push");

            String json = result.toJson();

            assertThat(json).contains("\"tool\":\"git\"");
            assertThat(json).contains("\"action\":\"push\"");
            assertThat(json).contains("\"confidence\":");
        }

        @Test
        @DisplayName("should provide log string representation")
        void shouldProvideLogStringRepresentation() {
            RoutingResult result = router.route("carga pom.xml");

            String logString = result.toLogString();

            assertThat(logString).contains("tool:file");
            assertThat(logString).contains("action:load");
            assertThat(logString).contains("confidence:");
            assertThat(logString).contains("latency:");
        }
    }

    @Nested
    @DisplayName("Tool Enum")
    class ToolEnumTests {
        @Test
        @DisplayName("should find tool by id")
        void shouldFindToolById() {
            assertThat(Tool.fromId("git")).isEqualTo(Tool.GIT);
            assertThat(Tool.fromId("file")).isEqualTo(Tool.FILE);
            assertThat(Tool.fromId("config")).isEqualTo(Tool.CONFIG);
            assertThat(Tool.fromId("chat")).isEqualTo(Tool.CHAT);
        }

        @Test
        @DisplayName("should return UNKNOWN for invalid id")
        void shouldReturnUnknownForInvalidId() {
            assertThat(Tool.fromId("invalid")).isEqualTo(Tool.UNKNOWN);
            assertThat(Tool.fromId(null)).isEqualTo(Tool.UNKNOWN);
            assertThat(Tool.fromId("")).isEqualTo(Tool.UNKNOWN);
        }

        @Test
        @DisplayName("should detect keywords correctly")
        void shouldDetectKeywordsCorrectly() {
            assertThat(Tool.GIT.matchesKeywords("sube los cambios")).isTrue();
            assertThat(Tool.FILE.matchesKeywords("carga el archivo")).isTrue();
            assertThat(Tool.CONFIG.matchesKeywords("configura la api")).isTrue();
        }

        @Test
        @DisplayName("should validate actions")
        void shouldValidateActions() {
            assertThat(Tool.GIT.isValidAction("commit")).isTrue();
            assertThat(Tool.GIT.isValidAction("push")).isTrue();
            assertThat(Tool.GIT.isValidAction("invalid")).isFalse();
        }

        @Test
        @DisplayName("should generate prompt text")
        void shouldGeneratePromptText() {
            String promptText = Tool.toPromptText();

            assertThat(promptText).contains("git:");
            assertThat(promptText).contains("file:");
            assertThat(promptText).contains("config:");
            assertThat(promptText).contains("chat:");
            assertThat(promptText).doesNotContain("unknown:");
        }
    }
}
