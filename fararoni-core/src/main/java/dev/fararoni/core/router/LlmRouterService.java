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

import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.core.core.integration.FnlIntegrationService;
import dev.fararoni.core.core.llm.LocalLlmConfig;
import dev.fararoni.core.core.llm.LocalLlmService;
import dev.fararoni.core.core.skills.SkillProvider;
import dev.fararoni.core.core.utils.NativeSilencer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class LlmRouterService implements RouterService {
    private static final Logger LOG = Logger.getLogger(LlmRouterService.class.getName());

    private static final Pattern TOOL_PATTERN = Pattern.compile(
        "\"tool\"\\s*:\\s*\"([^\"]+)\""
    );
    private static final Pattern ACTION_PATTERN = Pattern.compile(
        "\"action\"\\s*:\\s*\"([^\"]+)\""
    );
    private static final Pattern COMMAND_PATTERN = Pattern.compile(
        "\"command\"\\s*:\\s*\"([^\"]+)\""
    );

    private static final String SYSTEM_INSTRUCTIONS = """
        <|im_start|>system
        You are Fararoni, an intelligent CLI assistant that can CREATE FILES.

        Available Tools:
        - system: exec (for date, time, whoami, pwd, uname, hostname)
        - git: push, pull, status, diff, commit, branch, log
        - file: load, unload, search, list
        - config: set, get, show, delete

        FILE CREATION FORMAT (CRITICAL - FOLLOW EXACTLY):
        When user asks to "crea", "escribe", "genera", or "create" a file:

        STEP 1: Extract the filename from the request (e.g., "Usuario.java")
        STEP 2: Use this EXACT format - NO EXCEPTIONS:

        >>>FILE: filename.ext
        [file content here]
        <<<END_FILE

        RULES FOR FILENAME:
        - Filename MUST have an extension (.java, .py, .js, etc.)
        - Filename MUST NOT contain spaces or special characters
        - Filename MUST NOT be the user's description
        - If user says "crea Usuario.java", filename is "Usuario.java"
        - If user says "crea una clase Usuario", filename is "Usuario.java"
        - IF USER SPECIFIES A DIRECTORY/PATH, USE THE FULL PATH:
          Example: "crea Usuario.java en /path/to/dir" -> "/path/to/dir/Usuario.java"

        FILE EDITING FORMAT (FOR MODIFYING EXISTING FILES):
        When user asks to "edita", "modifica", "agrega a", "añade a" an EXISTING file:

        >>>PATCH: filename.ext
        <<<SEARCH>>>
        [exact text to find in file]
        <<<REPLACE>>>
        [new text to replace with]
        <<<END_PATCH

        PATCH RULES:
        - Use >>>PATCH: when EDITING existing files (add, modify, remove content)
        - Use >>>FILE: when CREATING new files
        - SEARCH text must EXACTLY match what exists in the file
        - REPLACE text is the new content to put in its place

        RULES:

        1. DATE/TIME/USER QUESTIONS:
           Use system tool. Example: {"tool":"system","action":"exec","params":{"command":"date"}}

        2. GIT/FILE/CONFIG TASKS:
           Use JSON. Example: {"tool":"git","action":"push"}

        3. CREATE/WRITE FILE:
           Use >>>FILE: format.

        4. EDIT/MODIFY EXISTING FILE:
           Use >>>PATCH: format for surgical edits.

        5. GREETINGS/CHAT:
           Reply in plain text. NO JSON.

        EXAMPLES:

        User: hola
        Assistant: Hola! En que te ayudo?

        User: que fecha es hoy
        Assistant: {"tool":"system","action":"exec","params":{"command":"date"}}

        User: quien soy
        Assistant: {"tool":"system","action":"exec","params":{"command":"whoami"}}

        User: sube cambios
        Assistant: {"tool":"git","action":"push"}

        User: carga pom.xml
        Assistant: {"tool":"file","action":"load","params":{"file":"pom.xml"}}

        User: crea Usuario.java
        Assistant: >>>FILE: Usuario.java
        public class Usuario {
            private String nombre;

            public Usuario(String nombre) {
                this.nombre = nombre;
            }

            public String getNombre() {
                return nombre;
            }

            public void setNombre(String nombre) {
                this.nombre = nombre;
            }
        }
        <<<END_FILE

        User: escribe Hola.java con un main que imprima hola mundo
        Assistant: >>>FILE: Hola.java
        public class Hola {
            public static void main(String[] args) {
                System.out.println("Hola Mundo");
            }
        }
        <<<END_FILE

        User: crea App.java en /home/user/proyecto
        Assistant: >>>FILE: /home/user/proyecto/App.java
        public class App {
            public static void main(String[] args) {
                System.out.println("App started");
            }
        }
        <<<END_FILE

        User: agrega un metodo getEdad() a Usuario.java
        Assistant: >>>PATCH: Usuario.java
        <<<SEARCH>>>
            public void setNombre(String nombre) {
                this.nombre = nombre;
            }
        }
        <<<REPLACE>>>
            public void setNombre(String nombre) {
                this.nombre = nombre;
            }

            public int getEdad() {
                return this.edad;
            }
        }
        <<<END_PATCH

        User: crea User.java y UserTest.java
        Assistant: >>>FILE: User.java
        public class User {
            private String name;
            public User(String name) { this.name = name; }
            public String getName() { return name; }
        }
        <<<END_FILE
        >>>FILE: UserTest.java
        import org.junit.Test;
        public class UserTest {
            @Test
            public void testUser() {
                User u = new User("Test");
                assert u.getName().equals("Test");
            }
        }
        <<<END_FILE
        <|im_end|>
        """;

    private static final String USER_TEMPLATE = """
        <|im_start|>user
        %s<|im_end|>
        <|im_start|>assistant
        """;

    private final LocalLlmConfig config;
    private volatile LocalLlmService llmService;
    private final BasicRouterService fallbackRouter;
    private final FnlIntegrationService fnlService;

    private volatile boolean active = false;
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong llmRoutes = new AtomicLong(0);

    private volatile String activeContext = null;

    public LlmRouterService(LocalLlmConfig config) {
        this.config = config;
        this.fallbackRouter = new BasicRouterService();
        this.fnlService = SkillProvider.getInstance();

        initializeLlm();

        LOG.info("[LlmRouter] FNL initialized with skills: " +
                 String.join(", ", fnlService.getSkillNames()));
    }

    public LlmRouterService() {
        this(LocalLlmConfig.fromEnvironment());
    }

    @Override
    public RoutingResult route(String userInput) {
        requestCount.incrementAndGet();
        long inicio = System.currentTimeMillis();

        if (!isModelReady()) {
            return fallbackRouter.route(userInput);
        }

        try {
            String prompt = buildPrompt(userInput);
            String response = llmService.generate(prompt);

            if (response == null || response.isBlank()) {
                return fallbackRouter.route(userInput);
            }

            response = cleanResponse(response);

            RoutingResult result = parseResponse(response, userInput, inicio);
            llmRoutes.incrementAndGet();

            return result;
        } catch (Exception e) {
            LOG.warning("[LlmRouter] Error: " + e.getMessage());
            return fallbackRouter.route(userInput);
        }
    }

    private String buildPrompt(String userInput) {
        String sanitized = userInput
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .trim();

        String contextBlock = "";
        if (activeContext != null && !activeContext.isBlank()) {
            String ctx = activeContext.length() > 1500
                ? activeContext.substring(0, 1497) + "..."
                : activeContext;
            contextBlock = String.format("""
                <|im_start|>system
                LOADED FILES CONTEXT (use this to answer user questions):
                %s
                <|im_end|>
                """, ctx);
        }

        return SYSTEM_INSTRUCTIONS + contextBlock + String.format(USER_TEMPLATE, sanitized);
    }

    private String cleanResponse(String response) {
        return response
            .replace("<|im_end|>", "")
            .replace("<|im_start|>", "")
            .trim();
    }

    private RoutingResult parseResponse(String response, String userInput, long inicio) {
        long latency = System.currentTimeMillis() - inicio;

        Optional<ToolRequest> fnlRequest = fnlService.parseToolRequest(response);

        if (fnlRequest.isPresent()) {
            ToolRequest req = fnlRequest.get();
            String toolId = req.toolName().toLowerCase();
            String action = req.action();

            Tool tool = Tool.fromId(toolId);
            Map<String, Object> params = new HashMap<>(req.params());

            if (tool != Tool.CHAT) {
                params.put("fromLocalModel", true);
                params.put("localResponse", response);
                params.put("fnlToolRequest", req);
            }

            LOG.fine("[LlmRouter] FNL parsed: " + toolId + ":" + action);
            return new RoutingResult(tool, action, params, 0.9, false, latency);
        }

        String json = extractJson(response);
        if (json != null) {
            String toolId = extractValue(TOOL_PATTERN, json);
            String action = extractValue(ACTION_PATTERN, json);

            if (toolId != null && action != null) {
                Tool tool = Tool.fromId(toolId);
                Map<String, Object> params = new HashMap<>();

                if ("system".equals(toolId)) {
                    String command = extractValue(COMMAND_PATTERN, json);
                    if (command != null) {
                        params.put("command", command);
                    }
                }

                if (tool != Tool.CHAT) {
                    params.put("fromLocalModel", true);
                    params.put("localResponse", response);
                }

                LOG.fine("[LlmRouter] Regex fallback parsed: " + toolId + ":" + action);
                return new RoutingResult(tool, action, params, 0.85, false, latency);
            }
        }

        Map<String, Object> chatParams = new HashMap<>();
        chatParams.put("fromLocalModel", true);
        chatParams.put("localResponse", response);

        return new RoutingResult(Tool.CHAT, "response", chatParams, 0.95, false, latency);
    }

    private String extractJson(String response) {
        int start = response.indexOf("{");
        if (start < 0) return null;

        int end = response.lastIndexOf("}");
        if (end <= start) {
            String partial = response.substring(start);
            return repairJson(partial);
        }

        return response.substring(start, end + 1);
    }

    private String repairJson(String json) {
        int open = 0, close = 0;
        boolean inStr = false;
        char prev = 0;

        for (char c : json.toCharArray()) {
            if (c == '"' && prev != '\\') inStr = !inStr;
            if (!inStr) {
                if (c == '{') open++;
                if (c == '}') close++;
            }
            prev = c;
        }

        StringBuilder sb = new StringBuilder(json);
        for (int i = 0; i < open - close; i++) {
            sb.append("}");
        }
        return sb.toString();
    }

    private String extractValue(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private boolean isModelReady() {
        if (llmService == null) {
            initializeLlm();
        }
        return llmService != null && active;
    }

    private synchronized void initializeLlm() {
        if (llmService != null) return;

        if (!config.isModelDownloaded()) {
            LOG.fine("[LlmRouter] Modelo no disponible: " + config.modelPath());
            return;
        }

        if (!config.hasEnoughRam()) {
            LOG.fine("[LlmRouter] RAM insuficiente");
            return;
        }

        NativeSilencer.silencePermanently();

        java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            @Override public void write(int b) {}
        }));

        try {
            llmService = new LocalLlmService(config);
            llmService.warmup();
            active = true;
            LOG.fine("[LlmRouter] Inicializado con modelo local");
        } catch (Exception e) {
            LOG.warning("[LlmRouter] Error inicializando: " + e.getMessage());
            llmService = null;
            active = false;
        } finally {
            System.setOut(originalOut);
        }
    }

    @Override
    public boolean isAvailable() {
        if (!config.isModelDownloaded() || !config.hasEnoughRam()) {
            return false;
        }

        return llmService != null && active && llmService.isNativeAvailable();
    }

    @Override
    public void warmup() {
        initializeLlm();
    }

    @Override
    public void shutdown() {
        active = false;
        if (llmService != null) {
            try {
                llmService.close();
            } catch (Exception e) {
                LOG.warning("[LlmRouter] Error cerrando: " + e.getMessage());
            }
            llmService = null;
        }
    }

    @Override
    public String getName() {
        return "LlmRouter";
    }

    @Override
    public boolean isLlmBased() {
        return true;
    }

    @Override
    public RouterStats getStats() {
        return new RouterStats(
            requestCount.get(),
            llmRoutes.get(),
            requestCount.get() - llmRoutes.get(),
            0, 0, 0, 0
        );
    }

    public FnlIntegrationService getFnlService() {
        return fnlService;
    }

    @Override
    public void setActiveContext(String context) {
        this.activeContext = context;
        if (context != null) {
            LOG.fine("[LlmRouter] FIX 9.6: activeContext set (" + context.length() + " chars)");
        } else {
            LOG.fine("[LlmRouter] FIX 9.6: activeContext cleared");
        }
    }
}
