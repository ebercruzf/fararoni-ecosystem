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
package dev.fararoni.core.core.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record SessionContextState(
    String sessionId,
    long createdAtEpochMs,
    long lastUpdateEpochMs,
    List<FileModification> recentFiles,
    ErrorContext lastError,
    int totalInteractions,
    List<CodeSnippet> codeSnippets
) {
    public static final int MAX_RECENT_FILES = 5;

    public static final int MAX_CODE_SNIPPETS = 10;

    public static SessionContextState createNew() {
        long now = System.currentTimeMillis();
        return new SessionContextState(
            UUID.randomUUID().toString(),
            now,
            now,
            new ArrayList<>(),
            null,
            0,
            new ArrayList<>()
        );
    }

    public Instant createdAt() {
        return Instant.ofEpochMilli(createdAtEpochMs);
    }

    public Instant lastUpdate() {
        return Instant.ofEpochMilli(lastUpdateEpochMs);
    }

    public SessionContextState withFileModification(FileModification modification) {
        List<FileModification> newFiles = new ArrayList<>(recentFiles);

        newFiles.removeIf(f -> f.filepath().equals(modification.filepath()));

        newFiles.add(0, modification);

        if (newFiles.size() > MAX_RECENT_FILES) {
            newFiles = newFiles.subList(0, MAX_RECENT_FILES);
        }

        return new SessionContextState(
            sessionId,
            createdAtEpochMs,
            System.currentTimeMillis(),
            newFiles,
            lastError,
            totalInteractions,
            codeSnippets
        );
    }

    public SessionContextState withCodeSnippet(CodeSnippet snippet) {
        List<CodeSnippet> newSnippets = new ArrayList<>(
            codeSnippets != null ? codeSnippets : new ArrayList<>()
        );

        newSnippets.add(0, snippet);

        if (newSnippets.size() > MAX_CODE_SNIPPETS) {
            newSnippets = newSnippets.subList(0, MAX_CODE_SNIPPETS);
        }

        return new SessionContextState(
            sessionId,
            createdAtEpochMs,
            System.currentTimeMillis(),
            recentFiles,
            lastError,
            totalInteractions,
            newSnippets
        );
    }

    public SessionContextState withError(ErrorContext error) {
        return new SessionContextState(
            sessionId,
            createdAtEpochMs,
            System.currentTimeMillis(),
            recentFiles,
            error,
            totalInteractions,
            codeSnippets
        );
    }

    public SessionContextState withErrorCleared() {
        return new SessionContextState(
            sessionId,
            createdAtEpochMs,
            System.currentTimeMillis(),
            recentFiles,
            null,
            totalInteractions,
            codeSnippets
        );
    }

    public SessionContextState withInteractionIncremented() {
        return new SessionContextState(
            sessionId,
            createdAtEpochMs,
            System.currentTimeMillis(),
            recentFiles,
            lastError,
            totalInteractions + 1,
            codeSnippets
        );
    }

    public boolean hasRecentError() {
        return lastError != null && lastError.isRecent();
    }

    public boolean hasCodeSnippets() {
        return codeSnippets != null && !codeSnippets.isEmpty();
    }

    public String toPromptContext() {
        StringBuilder sb = new StringBuilder();
        boolean hasContent = false;

        if (hasCodeSnippets()) {
            sb.append("CODIGO GENERADO ANTERIORMENTE (preservado en memoria):\n");
            for (CodeSnippet snippet : codeSnippets) {
                sb.append(snippet.toStablePrompt()).append("\n\n");
            }
            hasContent = true;
        }

        if (hasRecentError()) {
            sb.append(lastError.toStablePrompt());
            sb.append("\n");
            hasContent = true;
        }

        if (!recentFiles.isEmpty()) {
            sb.append("ARCHIVOS EN FOCO (modificados recientemente):\n");
            int count = 1;
            for (FileModification file : recentFiles) {
                sb.append(count++).append(". ").append(file.toStablePrompt()).append("\n");
            }
            hasContent = true;
        }

        return hasContent ? sb.toString() : null;
    }
}
