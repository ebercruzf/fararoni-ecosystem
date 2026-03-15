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
package dev.fararoni.core.core.llm.providers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
class OllamaProviderUnicodeTest {
    @Test
    @DisplayName("Decodifica \\u003e a > en tokens de streaming")
    void testDecodeUnicodeGreaterThan() throws Exception {
        OllamaProvider provider = new OllamaProvider("http://localhost:11434");

        String jsonLine = "{\"message\":{\"content\":\"\\u003e\\u003e\\u003eFILE: test.java\"}}";

        String token = invokeExtractStreamingToken(provider, jsonLine);

        assertNotNull(token, "Token no debe ser null");
        assertTrue(token.startsWith(">>>"),
            "Token debe comenzar con >>> pero fue: " + token);
        assertTrue(token.contains("FILE:"),
            "Token debe contener FILE: pero fue: " + token);
        assertEquals(">>>FILE: test.java", token,
            "Token completo debe ser >>>FILE: test.java");
    }

    @Test
    @DisplayName("Decodifica \\u003c a < en tokens")
    void testDecodeUnicodeLessThan() throws Exception {
        OllamaProvider provider = new OllamaProvider("http://localhost:11434");

        String jsonLine = "{\"message\":{\"content\":\"\\u003cxml\\u003e\"}}";

        String token = invokeExtractStreamingToken(provider, jsonLine);

        assertEquals("<xml>", token, "Debe decodificar < y >");
    }

    @Test
    @DisplayName("Mantiene texto normal sin escapes")
    void testNormalTextWithoutEscapes() throws Exception {
        OllamaProvider provider = new OllamaProvider("http://localhost:11434");

        String jsonLine = "{\"message\":{\"content\":\"Hello World\"}}";

        String token = invokeExtractStreamingToken(provider, jsonLine);

        assertEquals("Hello World", token);
    }

    @Test
    @DisplayName("Combina Unicode escapes con texto normal")
    void testMixedUnicodeAndNormal() throws Exception {
        OllamaProvider provider = new OllamaProvider("http://localhost:11434");

        String jsonLine = "{\"message\":{\"content\":\"\\u003e\\u003e\\u003eFILE: src/Main.java\\npackage com;\"}}";

        String token = invokeExtractStreamingToken(provider, jsonLine);

        assertTrue(token.startsWith(">>>FILE:"),
            "Debe comenzar con >>>FILE: pero fue: " + token);
        assertTrue(token.contains("src/Main.java"),
            "Debe contener la ruta del archivo");
    }

    @Test
    @DisplayName("Decodifica multiples escapes Unicode consecutivos")
    void testMultipleConsecutiveUnicodeEscapes() throws Exception {
        OllamaProvider provider = new OllamaProvider("http://localhost:11434");

        String jsonLine = "{\"message\":{\"content\":\"\\u0048\\u0065\\u006c\\u006c\\u006f\"}}";

        String token = invokeExtractStreamingToken(provider, jsonLine);

        assertEquals("Hello", token, "Debe decodificar H-e-l-l-o");
    }

    @Test
    @DisplayName("Maneja escapes estandar junto con Unicode")
    void testStandardEscapesWithUnicode() throws Exception {
        OllamaProvider provider = new OllamaProvider("http://localhost:11434");

        String jsonLine = "{\"message\":{\"content\":\"line1\\nline2\\u003e\"}}";

        String token = invokeExtractStreamingToken(provider, jsonLine);

        assertTrue(token.contains("\n"), "Debe contener newline");
        assertTrue(token.endsWith(">"), "Debe terminar con >");
        assertEquals("line1\nline2>", token);
    }

    @Test
    @DisplayName("Fallback para Unicode invalido")
    void testInvalidUnicodeEscapeFallback() throws Exception {
        OllamaProvider provider = new OllamaProvider("http://localhost:11434");

        String jsonLine = "{\"message\":{\"content\":\"test" + "\\u" + "GGGGend\"}}";

        String token = invokeExtractStreamingToken(provider, jsonLine);

        assertNotNull(token);
    }

    private String invokeExtractStreamingToken(OllamaProvider provider, String jsonLine)
            throws Exception {
        Method method = OllamaProvider.class.getDeclaredMethod(
            "extractStreamingToken", String.class);
        method.setAccessible(true);
        return (String) method.invoke(provider, jsonLine);
    }
}
