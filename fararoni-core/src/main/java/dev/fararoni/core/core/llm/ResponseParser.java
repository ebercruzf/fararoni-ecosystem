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
package dev.fararoni.core.core.llm;

import dev.fararoni.core.core.surgical.EditBlock;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ResponseParser {
    public List<EditBlock> parseJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return List.of();
        }

        String jsonContent = extractJsonArray(rawResponse);

        if (jsonContent == null) {
            throw new ResponseParseException("No se encontro array JSON en la respuesta: " + rawResponse);
        }

        return parseJsonArray(jsonContent);
    }

    private String extractJsonArray(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');

        if (start == -1 || end == -1 || end <= start) {
            return null;
        }

        return response.substring(start, end + 1);
    }

    private List<EditBlock> parseJsonArray(String json) {
        throw new UnsupportedOperationException(
            "Parseo JSON pendiente de implementacion con Jackson/Gson. JSON: " + json
        );
    }

    public static class ResponseParseException extends RuntimeException {
        public ResponseParseException(String message) {
            super(message);
        }

        public ResponseParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
