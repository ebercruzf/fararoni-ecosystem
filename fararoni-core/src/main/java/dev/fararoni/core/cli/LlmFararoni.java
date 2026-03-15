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
package dev.fararoni.core.cli;

import dev.fararoni.core.client.VllmClient;
import dev.fararoni.core.config.CliConfig;
import dev.fararoni.core.config.ConfigPriorityResolver;
import dev.fararoni.core.config.ServiceRegistry;
import dev.fararoni.core.core.context.ContextManager;
import dev.fararoni.core.core.reflexion.TestCorrectionService;
import dev.fararoni.core.core.workspace.WorkspaceManager;
import dev.fararoni.core.tokenizer.EstimationTokenizer;
import dev.fararoni.core.tokenizer.Tokenizer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.fusesource.jansi.Ansi.ansi;
import org.fusesource.jansi.AnsiConsole;

@Command(
        name = "fararoni",
        mixinStandardHelpOptions = true,
        version = "FARARONI Core v1.0.0",
        description = "CLI Inteligente para Modelos LLM"
)
public class LlmFararoni implements Callable<Integer> {
    @Option(names = {"--diagnose"}, description = "Modo diagnóstico: recibe error por stdin")
    private boolean diagnoseMode;

    @Option(names = {"--project-path", "--path"}, description = "Ruta al proyecto a analizar")
    private String projectPath;

    @Option(names = {"-u", "--url"}, description = "URL del servidor LLM")
    private String serverUrl;

    @Option(names = {"-k", "--api-key"}, description = "API Key para autenticación")
    private String apiKey;

    @Option(names = {"-m", "--model"}, description = "Nombre del modelo")
    private String modelName;

    @Option(names = {"-t", "--tokenizer"}, defaultValue = "LOCAL")
    private CliConfig.TokenizerMode tokenizerMode;

    @Option(names = {"--tokenizer-model"}, defaultValue = "Qwen/Qwen2.5-Coder-32B-Instruct")
    private String tokenizerModel;

    @Option(names = {"--max-tokens"}, defaultValue = "2048")
    private int maxTokens;

    @Option(names = {"--temperature"}, defaultValue = "0.7")
    private double temperature;

    @Option(names = {"--context-window"}, defaultValue = "8192")
    private int contextWindow;

    @Option(names = {"--context-strategy"}, defaultValue = "SLIDING_WINDOW")
    private CliConfig.ContextStrategy contextStrategy;

    @Option(names = {"--max-history"}, defaultValue = "50")
    private int maxHistoryMessages;

    @Option(names = {"-s", "--stream"}, defaultValue = "true")
    private boolean streaming;

    @Option(names = {"--show-tokens"}, defaultValue = "false")
    private boolean showTokens;

    @Option(names = {"--system-prompt"}, description = "System prompt personalizado")
    private String systemPrompt;

    @Option(names = {"--connect-timeout"}, defaultValue = "10000")
    private int connectTimeoutMs;

    @Option(names = {"--read-timeout"}, defaultValue = "300000")
    private int readTimeoutMs;

    @Option(names = {"--max-retries"}, defaultValue = "3")
    private int maxRetries;

    @Option(names = {"--debug"}, defaultValue = "false")
    private boolean enableDebugMode;

    @Option(names = {"--no-router"}, defaultValue = "false")
    private boolean noRouter;

    @Option(names = {"-p", "--provider"}, description = "Proveedor LLM: openai, ollama, anthropic, local")
    private String llmProvider;

    @Option(names = {"-e", "--exercise"}, description = "ID del ejercicio")
    private String exerciseId;

    public static void main(String[] args) {
        try {
            java.util.TimeZone.getDefault();
            configureNativeLibraryPaths();
        } catch (Throwable ignored) {}

        if (containsFlag(args, "--diagnose")) {
            int exitCode = runDiagnoseMode(args);
            System.exit(exitCode);
            return;
        }

        String pipeInput = readPipeInput();
        if (pipeInput != null && !pipeInput.isBlank()) {
            int exitCode = runBatchMode(args, pipeInput);
            System.exit(exitCode);
            return;
        }

        AnsiConsole.systemInstall();
        try {
            WorkspaceManager workspace = ServiceRegistry.initializeWorkspace(args);
            System.out.println(ansi().fgBlue().a("[WS] " + workspace.getInfoString()).reset());

            if (!FirstRunExperience.checkAndRun()) {
                System.out.println(ansi().fgYellow().a("Tip: Usa --api-key para proporcionar la API key por CLI").reset());
            }

            var cli = new CommandLine(new LlmFararoni())
                    .setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.AUTO));
            discoverAndRegisterPlugins(cli);
            var exitCode = cli.execute(args);
            System.exit(exitCode);
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    private static void configureNativeLibraryPaths() {
        try {
            if (System.getProperty("java.io.tmpdir", "").startsWith("/var/folders")) {
                String userHome = System.getProperty("user.home");
                if (userHome != null && !userHome.isEmpty()) {
                    String nativeTmp = userHome + "/.llm-fararoni/native-tmp";
                    java.io.File nativeTmpDir = new java.io.File(nativeTmp);
                    if (!nativeTmpDir.exists()) nativeTmpDir.mkdirs();
                    System.setProperty("java.io.tmpdir", nativeTmp);
                    System.setProperty("library.jansi.path", nativeTmp);
                    System.setProperty("ai.djl.repository.cache", nativeTmp + "/djl-cache");
                }
            }
        } catch (Throwable t) {}
    }

    private static String readPipeInput() {
        try {
            if (System.console() != null) return null;
            if (System.in.available() <= 0) return null;
            StringBuilder input = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (input.length() > 0) input.append("\n");
                    input.append(line);
                }
            }
            return input.toString().trim().isEmpty() ? null : input.toString().trim();
        } catch (IOException e) { return null; }
    }

    private static boolean containsFlag(String[] args, String flag) {
        for (String arg : args) if (arg.equals(flag)) return true;
        return false;
    }

    private static String getFlagValue(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals(flag)) return args[i + 1];
        return null;
    }

    private static int runDiagnoseMode(String[] args) {
        System.err.println("[SNIPER] Modo Diagnostico Activado - Operation Omni-Being (v17.3)");

        try {
            TestCorrectionService service = TestCorrectionService.create();

            String projectPathArg = getFlagValue(args, "--project-path");
            if (projectPathArg == null) {
                projectPathArg = getFlagValue(args, "--path");
            }

            Path targetProject;
            if (projectPathArg != null && !projectPathArg.isBlank()) {
                targetProject = Paths.get(projectPathArg).toAbsolutePath();
                System.err.println("[CONTEXT] Objetivo fijado (explicito): " + targetProject);
            } else {
                targetProject = Paths.get(System.getProperty("user.dir"));
                System.err.println("[CONTEXT] [DIR] Directorio de trabajo (default): " + targetProject);
            }

            if (!java.nio.file.Files.exists(targetProject) || !java.nio.file.Files.isDirectory(targetProject)) {
                System.err.println("[CONTEXT] [ERROR] Error Critico: El directorio objetivo no existe: " + targetProject);
                return 1;
            }

            ContextManager contextManager = new ContextManager(text -> new float[384]);

            try {
                contextManager.indexProject(targetProject);
            } catch (Exception e) {
                System.err.println("[CONTEXT] [WARN] Advertencia: Fallo la indexacion del proyecto (" + e.getMessage() + "). Operando con vision limitada.");
            }

            StringBuilder errorLog = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (errorLog.length() > 0) errorLog.append("\n");
                    errorLog.append(line);
                }
            }

            String errorText = errorLog.toString().trim();
            if (errorText.isEmpty()) {
                System.err.println("[SNIPER] [WARN] No se recibio input. Uso: cat error.log | fararoni --diagnose");
                return 1;
            }

            String exerciseName = getFlagValue(args, "--exercise");
            if (exerciseName == null) exerciseName = getFlagValue(args, "-e");

            if (exerciseName == null || exerciseName.equals("unknown")) {
                exerciseName = inferContextFromLog(errorText);
                if (exerciseName != null) {
                    System.err.println("[SNIPER] Ojo Clinico: " + exerciseName);
                }
            }

            System.err.println("[SNIPER] Analizando " + errorText.length() + " bytes de error log...");

            String richContext = "";
            try {
                richContext = contextManager.buildContextPayload(targetProject, errorText);
                if (richContext.contains("ARCHIVOS RELEVANTES (RAG)")) {
                    System.err.println("[CONTEXT] RAG activado: Archivos relevantes inyectados.");
                }
            } catch (Exception e) {
                System.err.println("[CONTEXT] [WARN] Fallo generando contexto enriquecido. Usando raw error.");
            }

            String enrichedErrorText = richContext + "\n\nERROR ORIGINAL:\n" + errorText;

            String diagnosticReport = service.generateDiagnosticReport(enrichedErrorText, exerciseName);

            System.out.println(diagnosticReport);
            System.out.flush();

            System.err.println("[SNIPER] [OK] Mision Cumplida.");
            return 0;
        } catch (Exception e) {
            System.err.println("[SNIPER] [ERROR] Error Fatal: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }

    private static String inferContextFromLog(String log) {
        String lowerLog = log.toLowerCase();
        if (lowerLog.contains("food_chain") || lowerLog.contains("foodchain")) return "food-chain";
        if (lowerLog.contains("forth") || lowerLog.contains("stackunderflow")) return "forth";
        if (lowerLog.contains("hangman") || lowerLog.contains("masked_word")) return "hangman";
        if (lowerLog.contains("poker") || lowerLog.contains("card")) return "poker";
        if (lowerLog.contains("paasio") || lowerLog.contains("metered")) return "paasio";
        if (lowerLog.contains("domino") || lowerLog.contains("chain")) return "dominoes";
        if (lowerLog.contains("affine") || lowerLog.contains("cipher")) return "affine-cipher";
        if (lowerLog.contains("grep") || lowerLog.contains("pattern")) return "grep";
        if (lowerLog.contains("robot") && lowerLog.contains("name")) return "robot-name";
        if (lowerLog.contains("wordy") || lowerLog.contains("plus")) return "wordy";
        if (lowerLog.contains("grade") || lowerLog.contains("school")) return "grade-school";
        if (lowerLog.contains("phone") || lowerLog.contains("number")) return "phone-number";
        if (lowerLog.contains("proverb") || lowerLog.contains("horseshoe")) return "proverb";
        return null;
    }

    private static int runBatchMode(String[] args, String prompt) {
        boolean debug = "true".equalsIgnoreCase(System.getenv("FARARONI_DEBUG"));
        if (debug) System.err.println("[BatchMode] Procesando prompt...");
        java.io.PrintStream originalOut = System.out;
        System.setOut(System.err);
        try {
            LlmFararoni instance = new LlmFararoni();
            new CommandLine(instance).parseArgs(args);
            var resolver = new ConfigPriorityResolver();
            String serverUrl = resolver.resolveServerUrl(instance.serverUrl);
            String modelName = resolver.resolveModelName(instance.modelName);
            String responseText = sendDirectHttpRequest(serverUrl, modelName, prompt);
            System.setOut(originalOut);
            if (responseText != null && !responseText.isEmpty()) {
                System.out.println(responseText);
                System.out.flush();
            }
            return 0;
        } catch (Exception e) {
            System.setOut(originalOut);
            System.err.println("[BatchMode] ERROR: " + e.getMessage());
            return 1;
        }
    }

    private static String sendDirectHttpRequest(String serverUrl, String modelName, String userPrompt) throws IOException {
        String jsonRequest = String.format("{\"model\": \"%s\", \"messages\": [{\"role\": \"user\", \"content\": \"%s\"}], \"stream\": false}",
                modelName, userPrompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"));
        URL url = new URL(serverUrl + "/v1/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) { os.write(jsonRequest.getBytes(StandardCharsets.UTF_8)); }
        if (conn.getResponseCode() != 200) throw new IOException("HTTP error: " + conn.getResponseCode());
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) response.append(line);
        }
        String json = response.toString();
        int contentStart = json.indexOf("\"content\":\"");
        if (contentStart == -1) return "";
        contentStart += 11;
        int contentEnd = findEndOfJsonString(json, contentStart);
        return decodeJsonEscapes(json.substring(contentStart, contentEnd));
    }

    private static String decodeJsonEscapes(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i+1 < input.length()) {
                char next = input.charAt(i+1);
                if (next == 'n') { sb.append('\n'); i++; }
                else if (next == 't') { sb.append('\t'); i++; }
                else if (next == '"') { sb.append('"'); i++; }
                else { sb.append(c); }
            } else { sb.append(c); }
        }
        return sb.toString();
    }

    private static int findEndOfJsonString(String json, int start) {
        boolean esc = false;
        for (int i=start; i<json.length(); i++) {
            char c = json.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') return i;
        }
        return json.length();
    }

    private static void discoverAndRegisterPlugins(CommandLine cli) {}

    @Override
    public Integer call() throws Exception {
        return 0;
    }

    private CliConfig loadConfiguration() { return CliConfig.builder().build(); }
    private Tokenizer createTokenizer(CliConfig c) { return new EstimationTokenizer(); }
    private VllmClient createClient(CliConfig c, Tokenizer t) { return null; }
}
