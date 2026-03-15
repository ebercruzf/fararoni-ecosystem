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
package dev.fararoni.core.core.skills.forensic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class NetworkProbeSkill {
    private static final Logger LOG = Logger.getLogger(NetworkProbeSkill.class.getName());

    private final HttpClient client;

    public NetworkProbeSkill() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public String probeGet(String url) {
        StringBuilder report = new StringBuilder();
        report.append("[PROBE] GET: ").append(url).append("\n");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            report.append("STATUS: ").append(response.statusCode()).append("\n");
            report.append("BODY_PREVIEW: ").append(truncate(response.body(), 200)).append("\n");

            report.append(inferProtocol(response));
        } catch (Exception e) {
            report.append("CONNECTION_FAILED: ").append(e.getMessage()).append("\n");
            report.append("DEDUCCIÓN: El servidor no responde o no existe.\n");
        }

        return report.toString();
    }

    public String probePost(String url, String jsonPayload) {
        StringBuilder report = new StringBuilder();
        report.append("[PROBE] POST: ").append(url).append("\n");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(10))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            report.append("STATUS: ").append(response.statusCode()).append("\n");
            report.append("BODY_PREVIEW: ").append(truncate(response.body(), 300)).append("\n");

            switch (response.statusCode()) {
                case 200 -> report.append("DEDUCCIÓN: Endpoint funcional. Payload aceptado.\n");
                case 400 -> report.append("DEDUCCIÓN: Bad Request. Posible formato de payload incorrecto.\n");
                case 401 -> report.append("DEDUCCIÓN: Requiere autenticación (API Key faltante).\n");
                case 404 -> {
                    report.append("DEDUCCIÓN: Endpoint no encontrado.\n");
                    if (url.contains("/v1/")) {
                        report.append("  → ¿Es un servidor Ollama? Prueba /api/chat en lugar de /v1/chat/completions\n");
                    }
                }
                case 500 -> report.append("DEDUCCIÓN: Error interno del servidor. Revisa logs del servidor.\n");
                default -> report.append("DEDUCCIÓN: Respuesta inesperada.\n");
            }
        } catch (Exception e) {
            report.append("CONNECTION_FAILED: ").append(e.getMessage()).append("\n");
        }

        return report.toString();
    }

    public String detectLlmServerType(String baseUrl) {
        StringBuilder report = new StringBuilder();
        report.append("[DETECT] DETECTANDO TIPO DE SERVIDOR LLM: ").append(baseUrl).append("\n");

        String rootProbe = probeGet(baseUrl);
        if (rootProbe.contains("Ollama is running")) {
            report.append("[OK] DETECTADO: OLLAMA NATIVO\n");
            report.append("   → Usar endpoint: /api/chat\n");
            return report.toString();
        }

        String modelsProbe = probeGet(baseUrl + "/v1/models");
        if (modelsProbe.contains("STATUS: 200")) {
            report.append("[OK] DETECTADO: OPENAI-COMPATIBLE (vLLM/LiteLLM/Ollama-OpenAI)\n");
            report.append("   → Usar endpoint: /v1/chat/completions\n");
            return report.toString();
        }

        String tagsProbe = probeGet(baseUrl + "/api/tags");
        if (tagsProbe.contains("STATUS: 200") && tagsProbe.contains("models")) {
            report.append("[OK] DETECTADO: OLLAMA NATIVO\n");
            report.append("   → Usar endpoint: /api/chat\n");
            return report.toString();
        }

        report.append("[!] NO DETECTADO: Servidor desconocido o apagado\n");
        return report.toString();
    }

    public boolean fileExists(String path) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ls", "-la", path);
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public String checkPort(String host, int port) {
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), 3000);
            socket.close();
            return "[OK] Puerto " + port + " en " + host + " esta ABIERTO";
        } catch (Exception e) {
            return "[X] Puerto " + port + " en " + host + " esta CERRADO o inalcanzable: " + e.getMessage();
        }
    }

    private String inferProtocol(HttpResponse<String> response) {
        StringBuilder inference = new StringBuilder();

        String body = response.body().toLowerCase();

        if (body.contains("ollama is running")) {
            inference.append("PROTOCOLO DETECTADO: OLLAMA_NATIVE\n");
            inference.append("  → Endpoint correcto: /api/chat\n");
        } else if (body.contains("openai") || body.contains("gpt")) {
            inference.append("PROTOCOLO DETECTADO: OPENAI_COMPATIBLE\n");
            inference.append("  → Endpoint correcto: /v1/chat/completions\n");
        } else if (response.statusCode() == 404 && body.contains("not found")) {
            inference.append("INFERENCIA: Endpoint no existe en este servidor.\n");
        }

        return inference.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
