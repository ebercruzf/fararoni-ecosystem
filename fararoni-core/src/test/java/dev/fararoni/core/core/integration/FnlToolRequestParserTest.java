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
package dev.fararoni.core.core.integration;

import dev.fararoni.bus.agent.api.ToolRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("FnlToolRequestParser Tests")
class FnlToolRequestParserTest {
    private FnlToolRequestParser parser;

    @BeforeEach
    void setUp() {
        parser = new FnlToolRequestParser();
    }

    @Nested
    @DisplayName("parse() method")
    class ParseTests {
        @Test
        @DisplayName("Should parse clean JSON")
        void shouldParseCleanJson() {
            String json = """
                {"tool":"SYSTEM","action":"exec","params":{"command":"date"}}
                """;

            Optional<ToolRequest> result = parser.parse(json);

            assertThat(result).isPresent();
            assertThat(result.get().toolName()).isEqualTo("SYSTEM");
            assertThat(result.get().action()).isEqualTo("exec");
            assertThat(result.get().params()).containsEntry("command", "date");
        }

        @Test
        @DisplayName("Should parse JSON with formatted whitespace")
        void shouldParseFormattedJson() {
            String json = """
                {
                    "tool": "DATETIME",
                    "action": "now",
                    "params": {
                        "format": "yyyy-MM-dd"
                    }
                }
                """;

            Optional<ToolRequest> result = parser.parse(json);

            assertThat(result).isPresent();
            assertThat(result.get().toolName()).isEqualTo("DATETIME");
            assertThat(result.get().action()).isEqualTo("now");
            assertThat(result.get().params()).containsEntry("format", "yyyy-MM-dd");
        }

        @Test
        @DisplayName("Should parse JSON without params")
        void shouldParseJsonWithoutParams() {
            String json = """
                {"tool":"DATETIME","action":"today"}
                """;

            Optional<ToolRequest> result = parser.parse(json);

            assertThat(result).isPresent();
            assertThat(result.get().toolName()).isEqualTo("DATETIME");
            assertThat(result.get().action()).isEqualTo("today");
            assertThat(result.get().params()).isEmpty();
        }

        @Test
        @DisplayName("Should parse JSON with numeric params")
        void shouldParseJsonWithNumericParams() {
            String json = """
                {"tool":"DATETIME","action":"add","params":{"amount":5,"unit":"days"}}
                """;

            Optional<ToolRequest> result = parser.parse(json);

            assertThat(result).isPresent();
            assertThat(result.get().params()).containsEntry("amount", 5);
            assertThat(result.get().params()).containsEntry("unit", "days");
        }

        @Test
        @DisplayName("Should normalize tool name to uppercase")
        void shouldNormalizeToolNameToUppercase() {
            String json = """
                {"tool":"system","action":"exec"}
                """;

            Optional<ToolRequest> result = parser.parse(json);

            assertThat(result).isPresent();
            assertThat(result.get().toolName()).isEqualTo("SYSTEM");
        }

        @Test
        @DisplayName("Should return empty for null input")
        void shouldReturnEmptyForNullInput() {
            Optional<ToolRequest> result = parser.parse(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for blank input")
        void shouldReturnEmptyForBlankInput() {
            Optional<ToolRequest> result = parser.parse("   ");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for non-JSON input")
        void shouldReturnEmptyForNonJsonInput() {
            Optional<ToolRequest> result = parser.parse("Hello world");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for JSON without tool")
        void shouldReturnEmptyForJsonWithoutTool() {
            String json = """
                {"action":"exec","params":{}}
                """;

            Optional<ToolRequest> result = parser.parse(json);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for JSON without action")
        void shouldReturnEmptyForJsonWithoutAction() {
            String json = """
                {"tool":"SYSTEM","params":{}}
                """;

            Optional<ToolRequest> result = parser.parse(json);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractJson() method")
    class ExtractJsonTests {
        @Test
        @DisplayName("Should extract JSON from mixed content")
        void shouldExtractJsonFromMixedContent() {
            String response = """
                Here is the command:
                {"tool":"SYSTEM","action":"exec","params":{"command":"date"}}
                This will get the current date.
                """;

            String json = parser.extractJson(response);

            assertThat(json).isNotNull();
            assertThat(json).startsWith("{");
            assertThat(json).endsWith("}");
            assertThat(json).contains("\"tool\"");
        }

        @Test
        @DisplayName("Should extract JSON from markdown code block")
        void shouldExtractJsonFromMarkdownCodeBlock() {
            String response = """
                Here is the JSON:
                ```json
                {"tool":"SYSTEM","action":"exec"}
                ```
                """;

            String json = parser.extractJson(response);

            assertThat(json).isNotNull();
            assertThat(json).contains("\"tool\"");
        }

        @Test
        @DisplayName("Should extract JSON from generic code block")
        void shouldExtractJsonFromGenericCodeBlock() {
            String response = """
                ```
                {"tool":"FILE","action":"read_file","params":{"path":"test.txt"}}
                ```
                """;

            String json = parser.extractJson(response);

            assertThat(json).isNotNull();
            assertThat(json).contains("\"tool\":\"FILE\"");
        }

        @Test
        @DisplayName("Should return null for no JSON content")
        void shouldReturnNullForNoJsonContent() {
            String json = parser.extractJson("This is just plain text.");

            assertThat(json).isNull();
        }
    }

    @Nested
    @DisplayName("repairTruncatedJson() method")
    class RepairJsonTests {
        @Test
        @DisplayName("Should repair JSON missing one closing brace")
        void shouldRepairJsonMissingOneClosingBrace() {
            String truncated = """
                {"tool":"SYSTEM","action":"exec","params":{"command":"date"}
                """;

            String repaired = parser.repairTruncatedJson(truncated);

            assertThat(repaired).endsWith("}");

            Optional<ToolRequest> result = parser.parse(repaired);
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should repair JSON missing two closing braces")
        void shouldRepairJsonMissingTwoClosingBraces() {
            String truncated = """
                {"tool":"SYSTEM","action":"exec","params":{"command":"date"
                """;

            String repaired = parser.repairTruncatedJson(truncated);

            long openBraces = repaired.chars().filter(c -> c == '{').count();
            long closeBraces = repaired.chars().filter(c -> c == '}').count();

            assertThat(closeBraces).isEqualTo(openBraces);
        }

        @Test
        @DisplayName("Should not modify valid JSON")
        void shouldNotModifyValidJson() {
            String valid = """
                {"tool":"SYSTEM","action":"exec"}
                """;

            String result = parser.repairTruncatedJson(valid);

            assertThat(result).isEqualTo(valid);
        }

        @Test
        @DisplayName("Should handle braces inside strings")
        void shouldHandleBracesInsideStrings() {
            String json = """
                {"tool":"SYSTEM","action":"exec","params":{"message":"Hello {world}"}}
                """;

            String result = parser.repairTruncatedJson(json);

            assertThat(result).isEqualTo(json);
        }
    }

    @Nested
    @DisplayName("containsToolRequest() method")
    class ContainsToolRequestTests {
        @Test
        @DisplayName("Should return true for valid tool request")
        void shouldReturnTrueForValidToolRequest() {
            String response = """
                {"tool":"SYSTEM","action":"exec"}
                """;

            assertThat(parser.containsToolRequest(response)).isTrue();
        }

        @Test
        @DisplayName("Should return true for mixed content with tool request")
        void shouldReturnTrueForMixedContentWithToolRequest() {
            String response = """
                Here is the result: {"tool":"FILE","action":"list_files"}
                """;

            assertThat(parser.containsToolRequest(response)).isTrue();
        }

        @Test
        @DisplayName("Should return false for plain text")
        void shouldReturnFalseForPlainText() {
            assertThat(parser.containsToolRequest("Hello world")).isFalse();
        }

        @Test
        @DisplayName("Should return false for JSON without tool")
        void shouldReturnFalseForJsonWithoutTool() {
            assertThat(parser.containsToolRequest("{\"action\":\"exec\"}")).isFalse();
        }

        @Test
        @DisplayName("Should return false for null")
        void shouldReturnFalseForNull() {
            assertThat(parser.containsToolRequest(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Real LLM Response Patterns")
    class RealLlmPatternTests {
        @Test
        @DisplayName("Should parse Qwen-style response")
        void shouldParseQwenStyleResponse() {
            String response = """
                Para obtener la fecha actual, ejecutare el comando:
                {"tool":"system","action":"exec","params":{"command":"date"}}
                """;

            Optional<ToolRequest> result = parser.parse(response);

            assertThat(result).isPresent();
            assertThat(result.get().toolName()).isEqualTo("SYSTEM");
            assertThat(result.get().action()).isEqualTo("exec");
        }

        @Test
        @DisplayName("Should parse response with command outside params")
        void shouldParseResponseWithCommandOutsideParams() {
            String response = """
                {"tool":"system","action":"exec","command":"date"}
                """;

            Optional<ToolRequest> result = parser.parse(response);

            assertThat(result).isPresent();
            assertThat(result.get().params()).containsKey("command");
        }

        @Test
        @DisplayName("Should handle git push command")
        void shouldHandleGitPushCommand() {
            String response = """
                {"tool":"git","action":"push"}
                """;

            Optional<ToolRequest> result = parser.parse(response);

            assertThat(result).isPresent();
            assertThat(result.get().toolName()).isEqualTo("GIT");
            assertThat(result.get().action()).isEqualTo("push");
        }

        @Test
        @DisplayName("Should handle file operation")
        void shouldHandleFileOperation() {
            String response = """
                {"tool":"FILE","action":"read_file","params":{"path":"src/main/java/App.java","max_lines":100}}
                """;

            Optional<ToolRequest> result = parser.parse(response);

            assertThat(result).isPresent();
            assertThat(result.get().toolName()).isEqualTo("FILE");
            assertThat(result.get().action()).isEqualTo("read_file");
            assertThat(result.get().params()).containsEntry("path", "src/main/java/App.java");
            assertThat(result.get().params()).containsEntry("max_lines", 100);
        }
    }
}
