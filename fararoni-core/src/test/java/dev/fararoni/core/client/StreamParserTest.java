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
package dev.fararoni.core.client;

import dev.fararoni.bus.agent.api.client.StreamParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("StreamParser - Parseo de Streaming LLM")
class StreamParserTest {
    @Nested
    @DisplayName("OpenAiStreamParser - Formato OpenAI/vLLM")
    class OpenAiStreamParserTests {
        private final OpenAiStreamParser parser = new OpenAiStreamParser();

        @Nested
        @DisplayName("parseChunk()")
        class ParseChunkTests {
            @Test
            @DisplayName("Parsea chunk de chat completion correctamente")
            void parseChunk_withChatCompletion_extractsContent() {
                String chunk = """
                    {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1694268190,"model":"gpt-4","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
                    """;

                Optional<String> result = parser.parseChunk(chunk);

                assertTrue(result.isPresent());
                assertEquals("Hello", result.get());
            }

            @Test
            @DisplayName("Parsea chunk con contenido multilinea")
            void parseChunk_withMultilineContent_extractsContent() {
                String chunk = """
                    {"choices":[{"delta":{"content":"Line1\\nLine2"}}]}
                    """;

                Optional<String> result = parser.parseChunk(chunk);

                assertTrue(result.isPresent());
                assertTrue(result.get().contains("Line1"));
            }

            @Test
            @DisplayName("Retorna empty para chunk sin contenido")
            void parseChunk_withEmptyDelta_returnsEmpty() {
                String chunk = """
                    {"choices":[{"delta":{}}]}
                    """;

                Optional<String> result = parser.parseChunk(chunk);

                assertTrue(result.isEmpty());
            }

            @Test
            @DisplayName("Retorna empty para marcador [DONE]")
            void parseChunk_withDoneMarker_returnsEmpty() {
                Optional<String> result = parser.parseChunk("[DONE]");

                assertTrue(result.isEmpty());
            }

            @ParameterizedTest(name = "parseChunk(\"{0}\") retorna empty")
            @NullAndEmptySource
            @ValueSource(strings = {"   ", "\t", "\n"})
            void parseChunk_withNullOrBlank_returnsEmpty(String input) {
                Optional<String> result = parser.parseChunk(input);

                assertTrue(result.isEmpty());
            }

            @Test
            @DisplayName("Maneja JSON invalido sin excepción")
            void parseChunk_withInvalidJson_returnsEmpty() {
                Optional<String> result = parser.parseChunk("{invalid json}");

                assertTrue(result.isEmpty());
            }
        }

        @Nested
        @DisplayName("isEndOfStream()")
        class IsEndOfStreamTests {
            @Test
            @DisplayName("[DONE] indica fin de stream")
            void isEndOfStream_withDone_returnsTrue() {
                assertTrue(parser.isEndOfStream("[DONE]"));
            }

            @Test
            @DisplayName("[DONE] con espacios indica fin de stream")
            void isEndOfStream_withDoneAndWhitespace_returnsTrue() {
                assertTrue(parser.isEndOfStream("  [DONE]  "));
            }

            @Test
            @DisplayName("Chunk normal no indica fin de stream")
            void isEndOfStream_withNormalChunk_returnsFalse() {
                String chunk = """
                    {"choices":[{"delta":{"content":"Hello"}}]}
                    """;

                assertFalse(parser.isEndOfStream(chunk));
            }

            @Test
            @DisplayName("null no indica fin de stream")
            void isEndOfStream_withNull_returnsFalse() {
                assertFalse(parser.isEndOfStream(null));
            }
        }

        @Nested
        @DisplayName("Propiedades del parser")
        class ParserPropertiesTests {
            @Test
            @DisplayName("Provider name es 'openai'")
            void getProviderName_returnsOpenAi() {
                assertEquals("openai", parser.getProviderName());
            }

            @Test
            @DisplayName("Soporta chat mode")
            void supportsChatMode_returnsTrue() {
                assertTrue(parser.supportsChatMode());
            }
        }
    }

    @Nested
    @DisplayName("OllamaStreamParser - Formato Ollama Nativo")
    class OllamaStreamParserTests {
        private final OllamaStreamParser parser = new OllamaStreamParser();

        @Nested
        @DisplayName("parseChunk()")
        class ParseChunkTests {
            @Test
            @DisplayName("Parsea chunk de chat API correctamente")
            void parseChunk_withChatApi_extractsContent() {
                String chunk = """
                    {"model":"llama2","created_at":"2023-08-04T19:22:45.499127Z","message":{"role":"assistant","content":"Hello"},"done":false}
                    """;

                Optional<String> result = parser.parseChunk(chunk);

                assertTrue(result.isPresent());
                assertEquals("Hello", result.get());
            }

            @Test
            @DisplayName("Parsea chunk final con contenido")
            void parseChunk_withDoneTrue_stillExtractsContent() {
                String chunk = """
                    {"model":"llama2","message":{"content":"Final"},"done":true}
                    """;

                Optional<String> result = parser.parseChunk(chunk);

                assertTrue(result.isPresent());
                assertEquals("Final", result.get());
            }

            @Test
            @DisplayName("Retorna empty para chunk sin contenido")
            void parseChunk_withEmptyContent_returnsEmpty() {
                String chunk = """
                    {"message":{"content":""},"done":false}
                    """;

                Optional<String> result = parser.parseChunk(chunk);

                assertTrue(result.isEmpty());
            }

            @ParameterizedTest(name = "parseChunk(\"{0}\") retorna empty")
            @NullAndEmptySource
            @ValueSource(strings = {"   ", "\t"})
            void parseChunk_withNullOrBlank_returnsEmpty(String input) {
                Optional<String> result = parser.parseChunk(input);

                assertTrue(result.isEmpty());
            }

            @Test
            @DisplayName("Maneja JSON invalido sin excepción")
            void parseChunk_withInvalidJson_returnsEmpty() {
                Optional<String> result = parser.parseChunk("{broken");

                assertTrue(result.isEmpty());
            }
        }

        @Nested
        @DisplayName("isEndOfStream()")
        class IsEndOfStreamTests {
            @Test
            @DisplayName("done:true indica fin de stream")
            void isEndOfStream_withDoneTrue_returnsTrue() {
                String chunk = """
                    {"model":"llama2","message":{"content":""},"done":true}
                    """;

                assertTrue(parser.isEndOfStream(chunk));
            }

            @Test
            @DisplayName("done:false no indica fin de stream")
            void isEndOfStream_withDoneFalse_returnsFalse() {
                String chunk = """
                    {"message":{"content":"Hello"},"done":false}
                    """;

                assertFalse(parser.isEndOfStream(chunk));
            }

            @Test
            @DisplayName("Chunk sin campo done no indica fin de stream")
            void isEndOfStream_withoutDoneField_returnsFalse() {
                String chunk = """
                    {"message":{"content":"Hello"}}
                    """;

                assertFalse(parser.isEndOfStream(chunk));
            }

            @Test
            @DisplayName("null no indica fin de stream")
            void isEndOfStream_withNull_returnsFalse() {
                assertFalse(parser.isEndOfStream(null));
            }

            @Test
            @DisplayName("string vacío no indica fin de stream")
            void isEndOfStream_withEmpty_returnsFalse() {
                assertFalse(parser.isEndOfStream(""));
            }
        }

        @Nested
        @DisplayName("Propiedades del parser")
        class ParserPropertiesTests {
            @Test
            @DisplayName("Provider name es 'ollama'")
            void getProviderName_returnsOllama() {
                assertEquals("ollama", parser.getProviderName());
            }

            @Test
            @DisplayName("Soporta chat mode")
            void supportsChatMode_returnsTrue() {
                assertTrue(parser.supportsChatMode());
            }
        }
    }

    @Nested
    @DisplayName("Interoperabilidad")
    class InteroperabilityTests {
        @Test
        @DisplayName("Ambos parsers implementan StreamParser interface")
        void bothParsers_implementStreamParser() {
            StreamParser openAiParser = new OpenAiStreamParser();
            StreamParser ollamaParser = new OllamaStreamParser();

            assertNotNull(openAiParser.getProviderName());
            assertNotNull(ollamaParser.getProviderName());
            assertNotEquals(openAiParser.getProviderName(), ollamaParser.getProviderName());
        }

        @Test
        @DisplayName("Parsers tienen nombres de proveedor distintos")
        void parsers_haveDistinctProviderNames() {
            assertEquals("openai", new OpenAiStreamParser().getProviderName());
            assertEquals("ollama", new OllamaStreamParser().getProviderName());
        }

        @Test
        @DisplayName("OpenAI parser no detecta formato Ollama como fin de stream")
        void openAiParser_doesNotDetectOllamaFormat() {
            OpenAiStreamParser parser = new OpenAiStreamParser();
            String ollamaEnd = """
                {"done":true}
                """;

            assertFalse(parser.isEndOfStream(ollamaEnd));
        }

        @Test
        @DisplayName("Ollama parser no detecta [DONE] como fin de stream")
        void ollamaParser_doesNotDetectOpenAiFormat() {
            OllamaStreamParser parser = new OllamaStreamParser();

            assertFalse(parser.isEndOfStream("[DONE]"));
        }
    }
}
