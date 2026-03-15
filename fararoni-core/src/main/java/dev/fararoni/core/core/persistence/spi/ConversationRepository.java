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
package dev.fararoni.core.core.persistence.spi;

import dev.fararoni.core.model.Message;
import dev.fararoni.core.core.persistence.SqliteConversationRepository;

import java.util.List;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public interface ConversationRepository {
    void saveMessage(String sessionId, Message message);

    List<Message> getHistory(String sessionId, int limit);

    void clear(String sessionId);

    void trimByTokens(String sessionId, int maxTokens);

    default boolean isAvailable() {
        return true;
    }

    default int countMessages(String sessionId) {
        return getHistory(sessionId, Integer.MAX_VALUE).size();
    }
}
