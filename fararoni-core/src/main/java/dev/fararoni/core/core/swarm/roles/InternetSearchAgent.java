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
package dev.fararoni.core.core.swarm.roles;

import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.skills.WebSearchSkill;
import dev.fararoni.core.core.skills.impl.SovereignSearchSkill;
import dev.fararoni.core.core.swarm.base.SwarmAgent;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class InternetSearchAgent extends SwarmAgent {
    private static final Logger LOG = Logger.getLogger(InternetSearchAgent.class.getName());

    public static final String TYPE_SEARCH_REQUEST = "SEARCH_REQUEST";

    public static final String TYPE_SEARCH_RESULT = "SEARCH_RESULT";

    private static final Persona SCOUT_PERSONA = Persona.builder("SCOUT")
        .name("Oficial de Reconocimiento")
        .description("""
            Eres un agente de reconocimiento tactico especializado en busquedas
            rapidas de informacion en Internet. Tu trabajo es:
            1. Buscar informacion actualizada en la web
            2. Sintetizar resultados de forma concisa
            3. Citar fuentes cuando sea posible
            4. Responder de forma directa y factual

            NO generes codigo. NO hagas analisis profundos.
            Solo busca y reporta hechos.""")
        .expertise("web-search", "information-retrieval", "synthesis")
        .allowedTools("web_search", "url_scrape")
        .style(Persona.CommunicationStyle.BALANCED)
        .priorityCritics(Critic.CriticCategory.QUALITY)
        .build();

    private final WebSearchSkill searchSkill;
    private int searchesPerformed = 0;

    public InternetSearchAgent(WebSearchSkill searchSkill) {
        super("SCOUT", SCOUT_PERSONA);
        this.searchSkill = searchSkill;
    }

    public InternetSearchAgent() {
        this(new SovereignSearchSkill());
    }

    @Override
    protected void processMessage(SwarmMessage msg) {
        LOG.fine(() -> "[SCOUT] Mensaje recibido: " + msg.type());

        switch (msg.type()) {
            case TYPE_SEARCH_REQUEST -> handleSearchRequest(msg);
            default -> LOG.fine(() -> "[SCOUT] Mensaje ignorado: " + msg.type());
        }
    }

    private void handleSearchRequest(SwarmMessage msg) {
        String query = msg.content();
        LOG.info(() -> "[SCOUT] Iniciando busqueda: " + query);
        System.out.println("[SCOUT] Buscando en Internet: " + query);

        try {
            String rawResults = searchSkill.search(query);
            searchesPerformed++;

            String synthesizedResponse = think("""
                Tienes estos resultados de busqueda en Internet:

                %s

                Basandote en estos resultados, responde a la pregunta del usuario
                de forma DIRECTA y CONCISA.

                Pregunta original: "%s"

                Instrucciones:
                - Responde en maximo 3-4 oraciones
                - Cita las fuentes si son relevantes
                - Si no hay resultados utiles, dilo claramente
                - NO inventes informacion que no este en los resultados
                """.formatted(rawResults, query));

            String recipient = msg.senderId() != null ? msg.senderId() : "USER";
            sendTo(recipient, TYPE_SEARCH_RESULT, synthesizedResponse);

            LOG.info(() -> "[SCOUT] Busqueda completada. Enviado a " + recipient);
        } catch (Exception e) {
            LOG.warning(() -> "[SCOUT] Error en busqueda: " + e.getMessage());
            String errorMsg = "Error durante la busqueda: " + e.getMessage();
            sendTo(msg.senderId() != null ? msg.senderId() : "USER",
                   SwarmMessage.TYPE_ERROR, errorMsg);
        }
    }

    public String searchDirect(String query) {
        LOG.info(() -> "[SCOUT] Busqueda directa: " + query);
        System.out.println("[SCOUT] Busqueda directa en Internet...");

        String rawResults = searchSkill.search(query);
        searchesPerformed++;

        return think("""
            Resultados de busqueda:
            %s

            Pregunta: "%s"

            Responde de forma directa y concisa. Cita fuentes si aplica.
            """.formatted(rawResults, query));
    }

    public int getSearchesPerformed() {
        return searchesPerformed;
    }

    public String getSearchProvider() {
        return searchSkill.getProviderName();
    }
}
