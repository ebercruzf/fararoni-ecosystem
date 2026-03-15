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
package dev.fararoni.core.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class BiblioCognitiveMemoryService {
    private static final Logger LOG = Logger.getLogger(BiblioCognitiveMemoryService.class.getName());
    private JsonNode memoryBank;

    public BiblioCognitiveMemoryService() {
        loadMemory();
    }

    private void loadMemory() {
        try (InputStream is = getClass().getResourceAsStream("/memory/cognitive_memory.json")) {
            if (is == null) {
                LOG.severe(" NO SE ENCONTRÓ cognitive_memory.json en resources!");
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            this.memoryBank = mapper.readTree(is);
            LOG.info(" Memoria Cognitiva cargada correctamente.");
        } catch (Exception e) {
            LOG.severe(" Error cargando Memoria Cognitiva: " + e.getMessage());
        }
    }

    public String recallPrinciple(String principleId) {
        if (memoryBank == null) return "";
        for (JsonNode node : memoryBank.get("principles")) {
            if (node.get("id").asText().equals(principleId)) {
                return "PRINCIPIO ACTIVO: " + node.get("statement").asText();
            }
        }
        return "";
    }

    public String recallSkill(String skillId) {
        if (memoryBank == null) return "";
        for (JsonNode node : memoryBank.get("skill_library")) {
            if (node.get("id").asText().equals(skillId)) {
                return "HABILIDAD APRENDIDA (" + node.get("description").asText() + "):\n" +
                        node.get("blueprint").asText();
            }
        }
        return "";
    }
}
