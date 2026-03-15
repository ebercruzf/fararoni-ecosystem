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
package dev.fararoni.core.core.managers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.core.core.memory.Wisdom;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class BiblioCognitiveTriadManager {

    private static final Logger LOG = Logger.getLogger(BiblioCognitiveTriadManager.class.getName());
    private static BiblioCognitiveTriadManager instance;

    private JsonNode memoryRoot;
    private final ObjectMapper mapper;

    public BiblioCognitiveTriadManager() {
        this.mapper = new ObjectMapper();
        loadMemoryBank();
    }

    public static synchronized BiblioCognitiveTriadManager getInstance() {
        if (instance == null) {
            instance = new BiblioCognitiveTriadManager();
        }
        return instance;
    }

    public void loadMemoryBank() {
        try (InputStream is = getClass().getResourceAsStream("/memory/cognitive_memory.json")) {
            if (is == null) {
                LOG.severe("FATAL: No se encontró /memory/cognitive_memory.json en resources.");
                return;
            }
            this.memoryRoot = mapper.readTree(is);
            LOG.info("[CognitiveTriad] Memoria cargada. Neuronas activas.");
        } catch (Exception e) {
            LOG.severe("Error cargando memoria cognitiva: " + e.getMessage());
        }
    }

    public List<Wisdom> retrieveWisdomObjectsByTag(String exerciseName) {
        List<Wisdom> surgicalWisdom = new ArrayList<>();

        if (memoryRoot == null) {
            LOG.warning("MemoryRoot es null. Intentando recargar...");
            loadMemoryBank();
            if (memoryRoot == null) return surgicalWisdom;
        }

        String normalizedTag = exerciseName.toLowerCase().trim();

        JsonNode constitutionNode = memoryRoot.get("constitution");
        if (constitutionNode != null && constitutionNode.isArray()) {
            for (JsonNode rule : constitutionNode) {
                String scope = rule.path("scope").asText("UNKNOWN");
                String id = rule.path("id").asText("");

                boolean isGlobal = "GLOBAL".equalsIgnoreCase(scope);
                boolean isSpecific = id.toLowerCase().contains(normalizedTag);

                if (isGlobal || isSpecific) {
                    Wisdom w = new Wisdom();
                    w.id = id;
                    w.scope = scope;
                    w.description = rule.path("statement").asText();
                    w.tags.add(isGlobal ? "GLOBAL" : normalizedTag);
                    surgicalWisdom.add(w);
                }
            }
        }

        JsonNode skillNode = memoryRoot.get("skill_library");
        if (skillNode != null && skillNode.isArray()) {
            for (JsonNode skill : skillNode) {
                JsonNode tagsNode = skill.get("tags");

                if (containsTag(tagsNode, normalizedTag)) {
                    List<String> extractedTags = new ArrayList<>();
                    if (tagsNode != null) {
                        tagsNode.forEach(t -> extractedTags.add(t.asText()));
                    }

                    Wisdom w = new Wisdom();
                    w.id = skill.path("id").asText();
                    w.scope = skill.path("scope").asText();
                    w.description = skill.path("description").asText();
                    w.codeSnippet = skill.path("codeSnippet").asText();
                    w.tags = extractedTags;
                    surgicalWisdom.add(w);

                    LOG.info("[Neural Link] Skill Activado: " + skill.path("id").asText() + " para " + exerciseName);
                }
            }
        }

        return surgicalWisdom;
    }

    public Set<String> getAllAvailableTags() {
        Set<String> allTags = new HashSet<>();

        if (memoryRoot == null) {
            LOG.warning("MemoryRoot es null al obtener tags.");
            loadMemoryBank();
            if (memoryRoot == null) return allTags;
        }

        JsonNode skillNode = memoryRoot.get("skill_library");
        if (skillNode != null && skillNode.isArray()) {
            for (JsonNode skill : skillNode) {
                JsonNode tagsNode = skill.get("tags");
                if (tagsNode != null && tagsNode.isArray()) {
                    for (JsonNode tag : tagsNode) {
                        String tagText = tag.asText().toLowerCase().trim();
                        if (!tagText.isEmpty()) {
                            allTags.add(tagText);
                        }
                    }
                }
            }
        }

        LOG.info("[ToolRegistry] " + allTags.size() + " tags disponibles para Function Calling.");
        return allTags;
    }

    @Deprecated
    public String retrieveWisdom(String intent, String language) {
        if (memoryRoot == null) return "";

        StringBuilder wisdom = new StringBuilder();
        wisdom.append("\n📘 --- MEMORIA COGNITIVA ACTIVADA (" + intent.toUpperCase() + ") ---\n");

        List<Wisdom> objects = retrieveWisdomObjectsByTag(intent);

        for (Wisdom w : objects) {
            wisdom.append("• [").append(w.id).append("] ").append(w.description).append("\n");
            if (w.codeSnippet != null && !w.codeSnippet.isBlank()) {
                wisdom.append("  (Código disponible)\n");
            }
        }
        return wisdom.toString();
    }

    private boolean containsTag(JsonNode tagsNode, String searchTag) {
        if (tagsNode == null || !tagsNode.isArray()) return false;
        for (JsonNode tag : tagsNode) {
            if (tag.asText().equalsIgnoreCase(searchTag)) return true;
        }
        return false;
    }
}