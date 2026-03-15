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
package dev.fararoni.core.context;

import dev.fararoni.core.config.ServiceRegistry;
import dev.fararoni.core.tokenizer.Tokenizer;
import dev.fararoni.core.model.Message;
import java.util.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class TokenBudgetAssembler {
    private final int maxContextWindow;
    private final Tokenizer tokenizer;
    private final int RESPONSE_BUFFER = 1024;

    public TokenBudgetAssembler(Tokenizer tokenizer, int maxContextWindow) {
        this.tokenizer = tokenizer;
        this.maxContextWindow = maxContextWindow;
    }

    public String assemblePrompt(
            String systemPrompt,
            List<String> loadedFiles,
            List<Message> chatHistory,
            String currentUserMsg) {
        int systemTokens = tokenizer.countTokens(systemPrompt);
        int queryTokens = tokenizer.countTokens(currentUserMsg);

        int availableBudget = maxContextWindow - systemTokens - queryTokens - RESPONSE_BUFFER;

        if (availableBudget < 0) {
            throw new ContextOverflowException(
                "Tu pregunta es demasiado larga para el modelo. " +
                "Tokens requeridos: " + (systemTokens + queryTokens) +
                ", disponibles: " + (maxContextWindow - RESPONSE_BUFFER)
            );
        }

        StringBuilder filesContext = new StringBuilder();
        int filesIncluded = 0;
        int filesTruncated = 0;

        for (String fileContent : loadedFiles) {
            int fileTokens = tokenizer.countTokens(fileContent);
            if (availableBudget - fileTokens > 0) {
                filesContext.append("--- File Content ---\n")
                           .append(fileContent)
                           .append("\n--- End File ---\n\n");
                availableBudget -= fileTokens;
                filesIncluded++;
            } else {
                filesTruncated++;
                System.err.println("[WARN] Archivo truncado por falta de espacio en contexto.");
            }
        }

        LinkedList<Message> historyToInclude = new LinkedList<>();
        int messagesIncluded = 0;

        for (int i = chatHistory.size() - 1; i >= 0; i--) {
            Message msg = chatHistory.get(i);
            int msgTokens = tokenizer.countTokens(msg.content());

            if (availableBudget - msgTokens > 0) {
                historyToInclude.addFirst(msg);
                availableBudget -= msgTokens;
                messagesIncluded++;
            } else {
                int messagesDropped = i + 1;
                if (messagesDropped > 0) {
                    System.err.println(
                        "[INFO] " + messagesDropped + " mensajes antiguos fueron " +
                        "excluidos del contexto para hacer espacio. " +
                        "Upgrade a Enterprise para memoria ilimitada con RAG."
                    );
                }
                break;
            }
        }

        return buildFinalPrompt(systemPrompt, filesContext.toString(), historyToInclude, currentUserMsg);
    }

    private String buildFinalPrompt(
            String systemPrompt,
            String filesContext,
            List<Message> history,
            String userQuery) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("<system>\n").append(systemPrompt).append("\n</system>\n\n");

        String sessionContext = ServiceRegistry.getSessionContextForPrompt();
        if (sessionContext != null && !sessionContext.isEmpty()) {
            prompt.append("<session_context>\n").append(sessionContext).append("</session_context>\n\n");
        }

        if (!filesContext.isEmpty()) {
            prompt.append("<loaded_files>\n").append(filesContext).append("</loaded_files>\n\n");
        }

        if (!history.isEmpty()) {
            prompt.append("<chat_history>\n");
            for (Message msg : history) {
                prompt.append(String.format("<%s>\n%s\n</%s>\n\n",
                    msg.role(), msg.content(), msg.role()));
            }
            prompt.append("</chat_history>\n\n");
        }

        prompt.append("<user_query>\n").append(userQuery).append("\n</user_query>");

        return prompt.toString();
    }
}
