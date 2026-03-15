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
package dev.fararoni.core.core.skills;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fararoni.core.core.managers.BiblioCognitiveTriadManager;
import dev.fararoni.core.core.memory.Wisdom;
import dev.fararoni.core.core.persona.Personas;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0 (Architecture v20+)
 */
public class AgenticStrategies {
    private static final Logger logger = Logger.getLogger(AgenticStrategies.class.getName());
    private final BiblioCognitiveTriadManager brain;
    private final ToolRegistry toolRegistry;

    private record Profile(String contextId, Pattern signature) {}
    private static final List<Profile> KNOWN_PROFILES = new ArrayList<>();

    static {
        register("phone-number", "(phone_number|PhoneNumber|area code|exchange code|nanp|phone_number\\.py)");
        register("book-store", "(book_store|calculate_price|basket|discount|grouping|book_store\\.py)");
        register("bowling", "(bowling|BowlingGame|roll|pins|strike|spare|bowling\\.py)");
        register("bottle-song", "(bottle_song|green bottles|hanging on the wall|bottle_song\\.py)");

        register("beer-song", "(beer_song|99 bottles|beer|take one down|beer_song\\.py)");

        register("connect", "(connect|ConnectGame|winner|O connects|X connects|connect\\.py)");
        register("dot-dsl", "(dot_dsl|digraph|subgraph|Graph\\s*\\{|Edge\\s*\\{|dot_dsl\\.py)");

        register("pig-latin", "(pig_latin|pig latin|ay|translation|pig_latin\\.py)");

        register("food-chain", "(food_chain|swallowed|old lady|she die|spider|goat|wriggled|jiggled|food_chain\\.py)");

        register("forth", "(forth|StackUnderflow|evaluate|definition|forth\\.py)");
        register("go-counting", "(go_counting|territory|owner|stone|intersection|go_counting\\.py)");
        register("hangman", "(hangman|Hangman|guess|remaining_guesses|hangman\\.py)");
        register("pov", "(pov|Tree|from_pov|reparent|sibling|pov\\.py)");
        register("robot-name", "(robot_name|Robot|generate_name|robot_name\\.py)");
        register("grade-school", "(grade_school|School|roster|grade_school\\.py)");
        register("proverb", "(proverb|want of a|proverb\\.py)");
        register("list-ops", "(list_ops|append|concat|filter|foldl|list_ops\\.py)");
        register("paasio", "(paasio|MeteredFile|paasio\\.py)");
        register("poker", "(poker|best_hands|hand_rank|poker\\.py)");
        register("dominoes", "(dominoes|can_chain|dominoes\\.py)");
        register("wordy", "(wordy|What is|plus|minus|multiplied|wordy\\.py)");
        register("grep", "(grep|pattern|flags|grep\\.py)");
        register("affine-cipher", "(affine_cipher|affine|modular|affine_cipher\\.py)");
    }

    private static void register(String id, String regex) {
        KNOWN_PROFILES.add(new Profile(id, Pattern.compile(regex, Pattern.CASE_INSENSITIVE)));
    }

    public AgenticStrategies() {
        this.brain = BiblioCognitiveTriadManager.getInstance();
        this.toolRegistry = new ToolRegistry();
    }

    public record AgentRequestConfiguration(
            String systemPrompt,
            List<ObjectNode> tools,
            String detectedContextId
    ) {}

    public AgentRequestConfiguration prepareStrategy(String errorLog, String explicitContextId) {
        String contextId = inferContext(errorLog, explicitContextId);
        logger.info("[AGENT-BRAIN] Contexto analizado: " + contextId);

        boolean hasGoldenMaster = checkMemoryForGoldenMaster(contextId);

        List<ObjectNode> tools = new ArrayList<>();
        StringBuilder systemPrompt = new StringBuilder();

        systemPrompt.append(Personas.SENIOR_ENGINEER).append("\n\n");

        if (hasGoldenMaster) {
            logger.info("[AGENT-BRAIN] 💎 Solución Maestra detectada. Habilitando herramientas de restauración.");

            tools.addAll(toolRegistry.getAvailableTools());

            systemPrompt.append("IMPORTANTE: Tienes acceso a una base de conocimientos verificada (Golden Masters).\n");
            systemPrompt.append("Si identificas que el problema corresponde a un ejercicio conocido, ");
            systemPrompt.append("DEBES USAR la herramienta 'restore_solution' en lugar de intentar escribir el código tú mismo.\n");
            systemPrompt.append("Prioriza siempre el uso de herramientas sobre la generación manual.\n");
        } else {
            logger.info("[AGENT-BRAIN] [!] No hay Golden Master conocido. Se usara razonamiento puro.");
            systemPrompt.append("Analiza el error paso a paso y propón una solución en código Python.\n");
            systemPrompt.append("No tienes herramientas externas disponibles para este caso, confía en tu razonamiento.\n");
        }

        return new AgentRequestConfiguration(systemPrompt.toString(), tools, contextId);
    }

    private String inferContext(String log, String explicitId) {
        if (explicitId != null && !explicitId.equals("unknown")) {
            return explicitId;
        }
        for (Profile p : KNOWN_PROFILES) {
            if (p.signature.matcher(log).find()) {
                return p.contextId;
            }
        }
        return "unknown";
    }

    private boolean checkMemoryForGoldenMaster(String contextId) {
        if (contextId == null || contextId.equals("unknown")) return false;

        List<Wisdom> wisdom = brain.retrieveWisdomObjectsByTag(contextId);
        return wisdom != null && wisdom.stream().anyMatch(w -> w.codeSnippet != null && !w.codeSnippet.isBlank());
    }
}
