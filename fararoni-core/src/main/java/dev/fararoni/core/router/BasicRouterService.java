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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class BasicRouterService implements RouterService {
    private static final Pattern COMMIT_PATTERN = Pattern.compile(
        "(?i)(commit|commitea|comitea)\\s+(?:con\\s+)?(?:mensaje\\s+|message\\s+|msg\\s+)?(.+)$"
    );

    private static final Pattern PUSH_PATTERN = Pattern.compile(
        "(?i)(push|sube|subir|publica)\\s*(los)?\\s*(cambios|changes)?"
    );

    private static final Pattern PULL_PATTERN = Pattern.compile(
        "(?i)(pull|baja|bajar|actualiza)\\s*(los)?\\s*(cambios|changes)?|descarga\\s+(los\\s+)?cambios"
    );

    private static final Pattern STATUS_PATTERN = Pattern.compile(
        "(?i)(status|estado|git\\s+status)"
    );

    private static final Pattern DIFF_PATTERN = Pattern.compile(
        "(?i)(diff|diferencias|cambios|que\\s+cambi[oó])"
    );

    private static final Pattern BRANCH_PATTERN = Pattern.compile(
        "(?i)(branch|rama|ramas|branches)\\s*([\\w\\-\\/]+)?"
    );

    private static final Pattern LOAD_FILE_PATTERN = Pattern.compile(
        "(?i)(carga|cargar|load|lee|leer|abre|abrir|read|open)\\s+(?:el\\s+)?(?:archivo\\s+)?([\\w\\.\\-\\/]+)"
    );

    private static final Pattern UNLOAD_FILE_PATTERN = Pattern.compile(
        "(?i)(unload|cierra|cerrar|close|quita|quitar)\\s+(?:el\\s+)?(?:archivo\\s+)?([\\w\\.\\-\\/]+)"
    );

    private static final Pattern SEARCH_FILE_PATTERN = Pattern.compile(
        "(?i)(busca|buscar|search|encuentra|find)\\s+(?:en\\s+)?(?:archivos?\\s+)?[\"']?([^\"']+)[\"']?"
    );

    private static final Pattern CONFIG_SET_PATTERN = Pattern.compile(
        "(?i)(configura|configurar|set|establece)\\s+(?:mi\\s+)?(?:la\\s+)?(?:api\\s*key|clave|key|config)\\s*([\\w]+)?\\s*(?:=|a)?\\s*(.+)?"
    );

    private static final Pattern CONFIG_SHOW_PATTERN = Pattern.compile(
        "(?i)(muestra|mostrar|show|ver|lista|listar)\\s+(?:la\\s+)?(?:mi\\s+)?(config|configuracion|settings?)"
    );

    private static final Pattern FEATURE_PLAN_PATTERN = Pattern.compile(
        "(?i)(planifica|planificar|plan|diseña|diseñar)\\s+(?:una?\\s+)?(?:feature|funcionalidad|tarea)?\\s*:?\\s*(.+)?"
    );

    private static final Pattern FEATURE_EXECUTE_PATTERN = Pattern.compile(
        "(?i)(ejecuta|ejecutar|execute|implementa|implementar|implement)\\s+(?:el\\s+)?(?:plan|feature)?\\s*(.+)?"
    );

    private volatile boolean available = true;
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRoutes = new AtomicLong(0);

    public BasicRouterService() {
    }

    @Override
    public RoutingResult route(String userInput) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();

        if (userInput == null || userInput.isBlank()) {
            return RoutingResult.chat(0);
        }

        String input = userInput.trim();

        try {
            RoutingResult gitResult = tryGitRouting(input, startTime);
            if (gitResult != null) {
                successfulRoutes.incrementAndGet();
                return gitResult;
            }

            RoutingResult fileResult = tryFileRouting(input, startTime);
            if (fileResult != null) {
                successfulRoutes.incrementAndGet();
                return fileResult;
            }

            RoutingResult configResult = tryConfigRouting(input, startTime);
            if (configResult != null) {
                successfulRoutes.incrementAndGet();
                return configResult;
            }

            RoutingResult featureResult = tryFeatureRouting(input, startTime);
            if (featureResult != null) {
                successfulRoutes.incrementAndGet();
                return featureResult;
            }

            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.chat(latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.unknown(latency);
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void warmup() {
    }

    @Override
    public void shutdown() {
        available = false;
    }

    @Override
    public String getName() {
        return "BasicRouter";
    }

    @Override
    public boolean isLlmBased() {
        return false;
    }

    @Override
    public RouterStats getStats() {
        return new RouterStats(
            totalRequests.get(),
            successfulRoutes.get(),
            totalRequests.get(),
            0.5,
            1.0,
            0,
            0
        );
    }

    private RoutingResult tryGitRouting(String input, long startTime) {
        Matcher commitMatcher = COMMIT_PATTERN.matcher(input);
        if (commitMatcher.find()) {
            String message = commitMatcher.group(2);
            Map<String, Object> params = new HashMap<>();
            if (message != null && !message.isBlank()) {
                params.put("message", message.trim());
            }
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.of(Tool.GIT, "commit", params, 0.75, latency);
        }

        if (PUSH_PATTERN.matcher(input).find()) {
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.fallback(Tool.GIT, "push", latency);
        }

        if (PULL_PATTERN.matcher(input).find()) {
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.fallback(Tool.GIT, "pull", latency);
        }

        if (STATUS_PATTERN.matcher(input).find()) {
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.fallback(Tool.GIT, "status", latency);
        }

        if (DIFF_PATTERN.matcher(input).find()) {
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.fallback(Tool.GIT, "diff", latency);
        }

        Matcher branchMatcher = BRANCH_PATTERN.matcher(input);
        if (branchMatcher.find()) {
            String branchName = branchMatcher.group(2);
            Map<String, Object> params = new HashMap<>();
            if (branchName != null && !branchName.isBlank()) {
                params.put("branch", branchName.trim());
            }
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.of(Tool.GIT, "branch", params, 0.65, latency);
        }

        if (Tool.GIT.matchesKeywords(input)) {
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.fallback(Tool.GIT, latency);
        }

        return null;
    }

    private RoutingResult tryFileRouting(String input, long startTime) {
        Matcher unloadMatcher = UNLOAD_FILE_PATTERN.matcher(input);
        if (unloadMatcher.find()) {
            String filename = unloadMatcher.group(2);
            Map<String, Object> params = new HashMap<>();
            if (filename != null) {
                params.put("file", filename.trim());
            }
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.of(Tool.FILE, "unload", params, 0.7, latency);
        }

        Matcher loadMatcher = LOAD_FILE_PATTERN.matcher(input);
        if (loadMatcher.find()) {
            String filename = loadMatcher.group(2);
            Map<String, Object> params = new HashMap<>();
            if (filename != null) {
                params.put("file", filename.trim());
            }
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.of(Tool.FILE, "load", params, 0.7, latency);
        }

        Matcher searchMatcher = SEARCH_FILE_PATTERN.matcher(input);
        if (searchMatcher.find()) {
            String query = searchMatcher.group(2);
            Map<String, Object> params = new HashMap<>();
            if (query != null) {
                params.put("query", query.trim());
            }
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.of(Tool.FILE, "search", params, 0.65, latency);
        }

        if (Tool.FILE.matchesKeywords(input)) {
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.fallback(Tool.FILE, latency);
        }

        return null;
    }

    private RoutingResult tryConfigRouting(String input, long startTime) {
        Matcher setMatcher = CONFIG_SET_PATTERN.matcher(input);
        if (setMatcher.find()) {
            Map<String, Object> params = new HashMap<>();
            String key = setMatcher.group(2);
            String value = setMatcher.group(3);
            if (key != null) params.put("key", key.trim());
            if (value != null) params.put("value", value.trim());
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.of(Tool.CONFIG, "set", params, 0.7, latency);
        }

        if (CONFIG_SHOW_PATTERN.matcher(input).find()) {
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.fallback(Tool.CONFIG, "show", latency);
        }

        if (Tool.CONFIG.matchesKeywords(input)) {
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.fallback(Tool.CONFIG, latency);
        }

        return null;
    }

    private RoutingResult tryFeatureRouting(String input, long startTime) {
        Matcher executeMatcher = FEATURE_EXECUTE_PATTERN.matcher(input);
        if (executeMatcher.find()) {
            String planRef = executeMatcher.group(2);
            Map<String, Object> params = new HashMap<>();
            if (planRef != null) {
                params.put("plan", planRef.trim());
            }
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.of(Tool.FEATURE, "execute", params, 0.65, latency);
        }

        Matcher planMatcher = FEATURE_PLAN_PATTERN.matcher(input);
        if (planMatcher.find()) {
            String description = planMatcher.group(2);
            Map<String, Object> params = new HashMap<>();
            if (description != null) {
                params.put("description", description.trim());
            }
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.of(Tool.FEATURE, "plan", params, 0.65, latency);
        }

        if (Tool.FEATURE.matchesKeywords(input)) {
            long latency = System.currentTimeMillis() - startTime;
            return RoutingResult.fallback(Tool.FEATURE, latency);
        }

        return null;
    }
}
