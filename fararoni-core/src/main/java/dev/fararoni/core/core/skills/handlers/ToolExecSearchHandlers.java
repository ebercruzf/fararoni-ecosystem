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
package dev.fararoni.core.core.skills.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.core.core.index.ProjectKnowledgeBase;
import dev.fararoni.core.core.index.ProjectKnowledgeBase.ContextProfile;
import dev.fararoni.core.core.skills.ToolExecutionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ToolExecSearchHandlers {
    private static final Logger logger = Logger.getLogger(ToolExecSearchHandlers.class.getName());
    private final ObjectMapper mapper;
    private final Path workingDirectory;
    private final ProjectKnowledgeBase knowledgeBase;

    public ToolExecSearchHandlers(ObjectMapper mapper, Path workingDirectory, ProjectKnowledgeBase knowledgeBase) {
        this.mapper = mapper;
        this.workingDirectory = workingDirectory;
        this.knowledgeBase = knowledgeBase;
    }

    public ToolExecutionResult handleListFiles(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String dir = args.path("directoryPath").asText(".");

        logger.info("[QWEN-SEARCH] ListFiles solicitado para: " + dir);

        if (knowledgeBase != null && knowledgeBase.isAvailable()) {
            logger.info("[QWEN-SEARCH] Usando IndexStore para vista inteligente");

            try {
                knowledgeBase.refresh();
                String treeView = knowledgeBase.generateTreeView(dir);

                if (treeView != null && !treeView.isBlank()) {
                    return new ToolExecutionResult(true,
                        "=== ESTRUCTURA DEL PROYECTO (Indexada) ===\n" +
                        "Directorio: " + dir + "\n" +
                        "Fuente: IndexStore (optimizado para LLM)\n\n" +
                        treeView,
                        Optional.of(treeView), Optional.of(dir));
                }
            } catch (Exception e) {
                logger.warning("[QWEN-SEARCH] IndexStore falló, usando fallback: " + e.getMessage());
            }
        }

        logger.info("[QWEN-SEARCH] Usando fallback Files.walk");

        Path targetDir = workingDirectory != null
            ? workingDirectory.resolve(dir)
            : Path.of(dir);

        if (!Files.exists(targetDir)) {
            return new ToolExecutionResult(false,
                "Error: Directorio no existe: " + dir,
                Optional.empty(), Optional.of(dir));
        }

        StringBuilder tree = new StringBuilder();
        int maxDepth = 3;

        try (var stream = Files.walk(targetDir, maxDepth)) {
            stream.filter(p -> !p.toString().contains(".git"))
                  .filter(p -> !p.toString().contains("/target/"))
                  .filter(p -> !p.toString().contains("/node_modules/"))
                  .filter(p -> !p.toString().contains("/.idea/"))
                  .forEach(p -> {
                      int depth = targetDir.relativize(p).getNameCount();
                      String indent = "  ".repeat(Math.max(0, depth - 1));
                      String name = p.getFileName().toString();
                      if (Files.isDirectory(p)) {
                          tree.append(indent).append("[DIR] ").append(name).append("/\n");
                      } else {
                          tree.append(indent).append("  ").append(name).append("\n");
                      }
                  });
        }

        String result = tree.toString();
        if (result.isBlank()) {
            result = "(Directorio vacío o sin archivos visibles)";
        }

        return new ToolExecutionResult(true,
            "=== ESTRUCTURA DEL PROYECTO ===\n" +
            "Directorio: " + targetDir + "\n" +
            "Profundidad máxima: " + maxDepth + " niveles\n\n" +
            result,
            Optional.of(result), Optional.of(dir));
    }

    public ToolExecutionResult handleFileSearch(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String query = args.path("query").asText("");
        String fileType = args.path("fileType").asText("");

        if (query.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: FileSearch requiere 'query' (texto a buscar)",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-SEARCH] FileSearch: " + query + " (tipo: " + fileType + ")");

        Path searchDir = workingDirectory != null ? workingDirectory : Path.of(".");
        StringBuilder results = new StringBuilder();
        int matchCount = 0;
        int maxResults = 20;

        try (var stream = Files.walk(searchDir)) {
            var matchingFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains(".git"))
                .filter(p -> !p.toString().contains("/target/"))
                .filter(p -> !p.toString().contains("/node_modules/"))
                .filter(p -> fileType.isBlank() || p.toString().endsWith("." + fileType))
                .toList();

            for (Path file : matchingFiles) {
                if (matchCount >= maxResults) break;

                try {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size() && matchCount < maxResults; i++) {
                        String line = lines.get(i);
                        if (line.contains(query)) {
                            String relativePath = searchDir.relativize(file).toString();
                            results.append(relativePath)
                                   .append(":")
                                   .append(i + 1)
                                   .append(": ")
                                   .append(line.trim())
                                   .append("\n");
                            matchCount++;
                        }
                    }
                } catch (Exception e) {
                }
            }
        }

        if (matchCount == 0) {
            return new ToolExecutionResult(true,
                "No se encontraron resultados para: '" + query + "'\n" +
                (fileType.isBlank() ? "" : "Tipo de archivo: " + fileType),
                Optional.empty(), Optional.of(query));
        }

        String resultStr = results.toString();
        return new ToolExecutionResult(true,
            "=== RESULTADOS DE BÚSQUEDA ===\n" +
            "Query: " + query + "\n" +
            "Coincidencias: " + matchCount + (matchCount >= maxResults ? " (truncado)" : "") + "\n\n" +
            resultStr,
            Optional.of(resultStr), Optional.of(query));
    }

    public ToolExecutionResult handleGlobGet(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String pattern = args.path("pattern").asText("");

        if (pattern.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: GlobGet requiere 'pattern' (patrón glob)",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-SEARCH] GlobGet: " + pattern);

        Path searchDir = workingDirectory != null ? workingDirectory : Path.of(".");
        StringBuilder results = new StringBuilder();
        int matchCount = 0;
        int maxResults = 50;

        String regexPattern = pattern
            .replace(".", "\\.")
            .replace("**", ".*")
            .replace("*", "[^/]*")
            .replace("?", ".");

        try (var stream = Files.walk(searchDir)) {
            var matchingFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains(".git"))
                .filter(p -> !p.toString().contains("/target/"))
                .filter(p -> {
                    String relative = searchDir.relativize(p).toString();
                    return relative.matches(regexPattern);
                })
                .limit(maxResults)
                .toList();

            for (Path file : matchingFiles) {
                results.append(searchDir.relativize(file)).append("\n");
                matchCount++;
            }
        }

        if (matchCount == 0) {
            return new ToolExecutionResult(true,
                "No se encontraron archivos con patrón: " + pattern,
                Optional.empty(), Optional.of(pattern));
        }

        String resultStr = results.toString();
        return new ToolExecutionResult(true,
            "=== ARCHIVOS ENCONTRADOS ===\n" +
            "Patrón: " + pattern + "\n" +
            "Total: " + matchCount + (matchCount >= maxResults ? " (truncado)" : "") + "\n\n" +
            resultStr,
            Optional.of(resultStr), Optional.of(pattern));
    }

    public ToolExecutionResult handleDeepScan(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String directory = args.path("directory").asText(".");

        logger.info("[QWEN-SEARCH] DeepScan: Ejecutando analisis arquitectonico profundo (10 niveles)...");

        if (knowledgeBase != null && knowledgeBase.isAvailable()) {
            knowledgeBase.refresh();

            String deepMap = knowledgeBase.generateMap(
                ContextProfile.STRATEGIC
            );

            logger.info("[QWEN-SEARCH] DeepScan completado: " + deepMap.split("\n").length + " lineas");

            return new ToolExecutionResult(true,
                "=== MAPA ARQUITECTÓNICO COMPLETO (Strategic Mode: 10 niveles) ===\n\n" +
                deepMap,
                Optional.of(deepMap), Optional.of(directory));
        }

        logger.warning("[QWEN-SEARCH] DeepScan: IndexStore no disponible, usando fallback Files.walk");

        Path searchDir = workingDirectory != null ? workingDirectory.resolve(directory) : Path.of(directory);
        StringBuilder tree = new StringBuilder();
        tree.append("=== MAPA ARQUITECTÓNICO (Fallback Mode: Files.walk) ===\n\n");

        int[] itemCount = {0};
        int maxItems = 300;

        try (var stream = Files.walk(searchDir, 10)) {
            stream.filter(p -> !p.toString().contains(".git"))
                  .filter(p -> !p.toString().contains("/target/"))
                  .filter(p -> !p.toString().contains("node_modules"))
                  .filter(p -> !p.toString().contains("__pycache__"))
                  .sorted()
                  .limit(maxItems)
                  .forEach(p -> {
                      int depth = searchDir.relativize(p).getNameCount();
                      String indent = "  ".repeat(depth);
                      String icon = Files.isDirectory(p) ? "[DIR] " : "[FILE] ";
                      tree.append(indent).append(icon).append(p.getFileName()).append("\n");
                      itemCount[0]++;
                  });

            if (itemCount[0] >= maxItems) {
                tree.append("\n... [Truncado a ").append(maxItems).append(" items]\n");
            }

            tree.append("\n[Total: ").append(itemCount[0]).append(" items escaneados]");

            return new ToolExecutionResult(true, tree.toString(),
                Optional.of(tree.toString()), Optional.of(directory));
        } catch (IOException e) {
            return new ToolExecutionResult(false,
                "Error en DeepScan: " + e.getMessage(),
                Optional.empty(), Optional.of(directory));
        }
    }
}
