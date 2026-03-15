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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("MultiFileParser Tests")
class MultiFileParserTest {
    @Nested
    @DisplayName("isMultiFile() Detection Tests")
    class IsMultiFileTests {
        @Test
        @DisplayName("Should detect multi-file content with >>>FILE: marker")
        void shouldDetectMultiFileContent() {
            String content = """
                >>>FILE: src/User.java
                public class User {}
                """;

            assertThat(MultiFileParser.isMultiFile(content)).isTrue();
        }

        @Test
        @DisplayName("Should return false for single file content")
        void shouldReturnFalseForSingleFileContent() {
            String content = "public class User { private String name; }";

            assertThat(MultiFileParser.isMultiFile(content)).isFalse();
        }

        @Test
        @DisplayName("Should return false for null content")
        void shouldReturnFalseForNullContent() {
            assertThat(MultiFileParser.isMultiFile(null)).isFalse();
        }

        @Test
        @DisplayName("Should return false for empty content")
        void shouldReturnFalseForEmptyContent() {
            assertThat(MultiFileParser.isMultiFile("")).isFalse();
            assertThat(MultiFileParser.isMultiFile("   ")).isFalse();
        }

        @Test
        @DisplayName("Should detect marker case-sensitive")
        void shouldDetectMarkerCaseSensitive() {
            assertThat(MultiFileParser.isMultiFile(">>>FILE: test.java")).isTrue();
            assertThat(MultiFileParser.isMultiFile(">>>file: test.java")).isFalse();
            assertThat(MultiFileParser.isMultiFile(">>>File: test.java")).isFalse();
        }
    }

    @Nested
    @DisplayName("parse() Parsing Tests")
    class ParseTests {
        @Test
        @DisplayName("Should parse single file correctly")
        void shouldParseSingleFile() {
            String content = """
                >>>FILE: src/User.java
                public class User {
                    private String name;
                }
                """;

            Map<String, String> result = MultiFileParser.parse(content);

            assertThat(result).hasSize(1);
            assertThat(result).containsKey("src/User.java");
            assertThat(result.get("src/User.java")).contains("public class User");
            assertThat(result.get("src/User.java")).contains("private String name");
        }

        @Test
        @DisplayName("Should parse multiple files correctly")
        void shouldParseMultipleFiles() {
            String content = """
                >>>FILE: src/models/User.java
                public class User {
                    private Long id;
                }

                >>>FILE: src/services/UserService.java
                public class UserService {
                    public User findById(Long id) { return null; }
                }

                >>>FILE: pom.xml
                <project>
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """;

            Map<String, String> result = MultiFileParser.parse(content);

            assertThat(result).hasSize(3);
            assertThat(result).containsKeys(
                "src/models/User.java",
                "src/services/UserService.java",
                "pom.xml"
            );

            assertThat(result.get("src/models/User.java")).contains("private Long id");
            assertThat(result.get("src/services/UserService.java")).contains("findById");
            assertThat(result.get("pom.xml")).contains("<modelVersion>");
        }

        @Test
        @DisplayName("Should preserve order of files")
        void shouldPreserveOrderOfFiles() {
            String content = """
                >>>FILE: first.java
                >>>FILE: second.java
                >>>FILE: third.java
                """;

            Map<String, String> result = MultiFileParser.parse(content);

            String[] keys = result.keySet().toArray(new String[0]);
            assertThat(keys[0]).isEqualTo("first.java");
            assertThat(keys[1]).isEqualTo("second.java");
            assertThat(keys[2]).isEqualTo("third.java");
        }

        @Test
        @DisplayName("Should return empty map for null content")
        void shouldReturnEmptyMapForNullContent() {
            Map<String, String> result = MultiFileParser.parse(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty map for empty content")
        void shouldReturnEmptyMapForEmptyContent() {
            Map<String, String> result = MultiFileParser.parse("");
            assertThat(result).isEmpty();

            result = MultiFileParser.parse("   ");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty map for content without markers")
        void shouldReturnEmptyMapForContentWithoutMarkers() {
            String content = "public class User { }";
            Map<String, String> result = MultiFileParser.parse(content);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle paths with spaces in filename")
        void shouldHandlePathsWithSpaces() {
            String content = """
                >>>FILE: src/my file.java
                public class MyFile {}
                """;

            Map<String, String> result = MultiFileParser.parse(content);

            assertThat(result).containsKey("src/my file.java");
        }

        @Test
        @DisplayName("Should handle paths with deep nesting")
        void shouldHandleDeepNesting() {
            String content = """
                >>>FILE: src/main/java/com/example/model/User.java
                public class User {}
                """;

            Map<String, String> result = MultiFileParser.parse(content);

            assertThat(result).containsKey("src/main/java/com/example/model/User.java");
        }

        @Test
        @DisplayName("Should trim content but preserve internal formatting")
        void shouldTrimContentButPreserveInternalFormatting() {
            String content = """
                >>>FILE: Test.java

                public class Test {
                    private String field;
                }

                """;

            Map<String, String> result = MultiFileParser.parse(content);
            String fileContent = result.get("Test.java");

            assertThat(fileContent).contains("    // Indented comment");
            assertThat(fileContent).contains("    private String field");
        }

        @Test
        @DisplayName("Should handle empty file content")
        void shouldHandleEmptyFileContent() {
            String content = """
                >>>FILE: empty.java
                >>>FILE: notempty.java
                content
                """;

            Map<String, String> result = MultiFileParser.parse(content);

            assertThat(result.get("notempty.java").trim()).isEqualTo("content");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        @Test
        @DisplayName("Should handle marker at start without leading newline")
        void shouldHandleMarkerAtStart() {
            String content = ">>>FILE: first.java\ncode";

            Map<String, String> result = MultiFileParser.parse(content);

            assertThat(result).containsKey("first.java");
        }

        @Test
        @DisplayName("Should handle Windows line endings")
        void shouldHandleWindowsLineEndings() {
            String content = ">>>FILE: test.java\r\npublic class Test {}\r\n";

            Map<String, String> result = MultiFileParser.parse(content);

            assertThat(result).containsKey("test.java");
            assertThat(result.get("test.java")).contains("public class Test");
        }

        @Test
        @DisplayName("Should handle mixed line endings")
        void shouldHandleMixedLineEndings() {
            String content = ">>>FILE: a.java\ncode\r\n>>>FILE: b.java\r\nmore";

            Map<String, String> result = MultiFileParser.parse(content);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should handle Python files")
        void shouldHandlePythonFiles() {
            String content = """
                >>>FILE: main.py
                def hello():
                    print("Hello")

                >>>FILE: utils.py
                def helper():
                    pass
                """;

            Map<String, String> result = MultiFileParser.parse(content);

            assertThat(result).hasSize(2);
            assertThat(result.get("main.py")).contains("def hello()");
            assertThat(result.get("utils.py")).contains("def helper()");
        }

        @Test
        @DisplayName("Should handle XML/HTML files")
        void shouldHandleXmlFiles() {
            String content = """
                >>>FILE: pom.xml
                <?xml version="1.0"?>
                <project>
                    <groupId>com.example</groupId>
                </project>
                """;

            Map<String, String> result = MultiFileParser.parse(content);

            assertThat(result.get("pom.xml")).contains("<?xml version");
            assertThat(result.get("pom.xml")).contains("<groupId>");
        }

        @Test
        @DisplayName("Should handle JSON files")
        void shouldHandleJsonFiles() {
            String content = """
                >>>FILE: package.json
                {
                    "name": "my-app",
                    "version": "1.0.0"
                }
                """;

            Map<String, String> result = MultiFileParser.parse(content);

            assertThat(result.get("package.json")).contains("\"name\": \"my-app\"");
        }
    }
}
