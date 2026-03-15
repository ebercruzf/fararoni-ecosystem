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
package dev.fararoni.core.core.gateway;

import dev.fararoni.core.core.orchestrator.SovereignOrchestrator;

import java.util.Map;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public interface RichInputAdapter {
    enum ContentType {
        TEXT,

        IMAGE,

        AUDIO,

        VIDEO,

        DOCUMENT,

        LOCATION,

        CONTACT,

        STICKER,

        REACTION
    }

    void onTextReceived(String userId, String text, Map<String, String> metadata);

    void onRichContentReceived(
        String userId,
        ContentType contentType,
        Object content,
        Map<String, String> metadata
    );

    void sendResponse(String userId, String response);

    void sendRichResponse(String userId, ContentType type, Object content);

    String getChannelId();

    String getOriginProtocol();

    default boolean supportsContentType(ContentType type) {
        return type == ContentType.TEXT;
    }

    default boolean supportsRichContent() {
        return false;
    }
}
