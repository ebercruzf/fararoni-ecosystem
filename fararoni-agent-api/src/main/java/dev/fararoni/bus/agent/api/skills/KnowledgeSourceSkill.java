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
package dev.fararoni.bus.agent.api.skills;

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.ToolSkill;
import dev.fararoni.bus.agent.api.governance.QuotaAwareSkill;
import dev.fararoni.bus.agent.api.security.AuditLog;

import java.util.List;
import java.util.Map;

/**
 * Contract for RAG (Retrieval-Augmented Generation) and knowledge base operations.
 *
 * <p>This interface defines how the AI agent can search, retrieve, and index
 * knowledge from various sources (documentation, code, databases). Essential
 * for building intelligent coding assistants with domain knowledge.</p>
 *
 * <h2>Architecture</h2>
 * <pre>
 * ┌────────────────────────────────────────────────────────────────┐
 * │                     Knowledge Sources                          │
 * │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
 * │  │  Code    │  │  Docs    │  │  APIs    │  │ Database │       │
 * │  │ (*.java) │  │ (*.md)   │  │(Swagger) │  │ (Schema) │       │
 * │  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘       │
 * │       │             │             │             │              │
 * │       └─────────────┴──────┬──────┴─────────────┘              │
 * │                            │                                   │
 * │                     ┌──────▼──────┐                            │
 * │                     │  Embeddings │                            │
 * │                     │   Index     │                            │
 * │                     └──────┬──────┘                            │
 * │                            │                                   │
 * │                     ┌──────▼──────┐                            │
 * │  Agent Query ──────►│  Semantic   │──────► Relevant Chunks    │
 * │                     │   Search    │                            │
 * │                     └─────────────┘                            │
 * └────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Search internal documentation for API usage</li>
 *   <li>Find similar code patterns in codebase</li>
 *   <li>Retrieve relevant context for code generation</li>
 *   <li>Query database schemas and relationships</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Search for relevant knowledge
 * FNLResult<List<KnowledgeChunk>> results = knowledgeSkill.search(
 *     "How to configure Spring Security with JWT?",
 *     10,  // top 10 results
 *     Map.of("source", "documentation")
 * );
 *
 * // Get chunks for RAG context
 * if (results.success()) {
 *     String context = results.data().stream()
 *         .map(KnowledgeChunk::content)
 *         .collect(Collectors.joining("\n\n"));
 *     // Use context in prompt to LLM
 * }
 *
 * // Index new documentation
 * knowledgeSkill.indexDocument("/docs/new-api.md", Map.of(
 *     "source", "documentation",
 *     "version", "2.0"
 * ));
 * }</pre>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see QuotaAwareSkill
 */
public interface KnowledgeSourceSkill extends ToolSkill, QuotaAwareSkill {

    // ==================== Search Operations ====================

    /**
     * Performs semantic search across the knowledge base.
     *
     * <p>Uses embedding similarity to find relevant content.</p>
     *
     * @param query the search query (natural language)
     * @param maxResults maximum number of results
     * @param filters optional metadata filters
     * @return result containing ranked knowledge chunks
     */
    @AgentAction(
        name = "search",
        description = "Semantic search for relevant knowledge chunks"
    )
    @AuditLog(severity = "INFO", category = "KNOWLEDGE_SEARCH")
    FNLResult<List<KnowledgeChunk>> search(
        String query,
        int maxResults,
        Map<String, String> filters
    );

    /**
     * Performs hybrid search (semantic + keyword).
     *
     * <p>Combines embedding similarity with traditional keyword matching
     * for better precision on technical queries.</p>
     *
     * @param query the search query
     * @param keywords explicit keywords to match
     * @param maxResults maximum number of results
     * @return result containing ranked chunks
     */
    @AgentAction(
        name = "hybrid_search",
        description = "Combined semantic and keyword search"
    )
    FNLResult<List<KnowledgeChunk>> hybridSearch(
        String query,
        List<String> keywords,
        int maxResults
    );

    /**
     * Searches for similar content to a given document.
     *
     * <p>Finds documents similar to a reference chunk or file.</p>
     *
     * @param referenceId the ID of the reference chunk
     * @param maxResults maximum number of results
     * @return result containing similar chunks
     */
    @AgentAction(
        name = "find_similar",
        description = "Finds documents similar to a reference"
    )
    FNLResult<List<KnowledgeChunk>> findSimilar(String referenceId, int maxResults);

    // ==================== Indexing Operations ====================

    /**
     * Indexes a document into the knowledge base.
     *
     * <p>Chunks the document, generates embeddings, and stores in index.</p>
     *
     * @param path file path to index
     * @param metadata metadata to attach to chunks
     * @return result containing number of chunks created
     */
    @AgentAction(
        name = "index_document",
        description = "Indexes a file into the knowledge base"
    )
    @AuditLog(severity = "INFO", category = "KNOWLEDGE_INDEX")
    FNLResult<IndexResult> indexDocument(String path, Map<String, String> metadata);

    /**
     * Indexes a directory of documents.
     *
     * @param path directory path
     * @param pattern file pattern (e.g., "*.md", "*.java")
     * @param metadata metadata to attach
     * @param recursive whether to process subdirectories
     * @return result containing indexing summary
     */
    @AgentAction(
        name = "index_directory",
        description = "Indexes all matching files in a directory"
    )
    @AuditLog(severity = "INFO", category = "KNOWLEDGE_INDEX")
    FNLResult<IndexResult> indexDirectory(
        String path,
        String pattern,
        Map<String, String> metadata,
        boolean recursive
    );

    /**
     * Indexes raw text content.
     *
     * @param content the text content to index
     * @param sourceId identifier for this content
     * @param metadata metadata to attach
     * @return result containing indexing info
     */
    @AgentAction(
        name = "index_text",
        description = "Indexes raw text into the knowledge base"
    )
    FNLResult<IndexResult> indexText(
        String content,
        String sourceId,
        Map<String, String> metadata
    );

    /**
     * Removes a document from the index.
     *
     * @param sourceId the source identifier to remove
     * @return result indicating success
     */
    @AgentAction(
        name = "remove_document",
        description = "Removes a document from the knowledge base"
    )
    @AuditLog(severity = "WARN", category = "KNOWLEDGE_DELETE")
    FNLResult<Integer> removeDocument(String sourceId);

    // ==================== Index Management ====================

    /**
     * Lists all indexed sources.
     *
     * @param filters optional metadata filters
     * @return result containing source list
     */
    @AgentAction(
        name = "list_sources",
        description = "Lists all indexed knowledge sources"
    )
    FNLResult<List<SourceInfo>> listSources(Map<String, String> filters);

    /**
     * Gets statistics about the knowledge base.
     *
     * @return result containing index statistics
     */
    @AgentAction(
        name = "index_stats",
        description = "Returns knowledge base statistics"
    )
    FNLResult<IndexStats> getIndexStats();

    /**
     * Clears the entire knowledge base.
     *
     * @return result indicating success
     */
    @AgentAction(
        name = "clear_index",
        description = "Clears all indexed knowledge (use with caution!)"
    )
    @AuditLog(severity = "CRITICAL", category = "KNOWLEDGE_CLEAR")
    FNLResult<Void> clearIndex();

    // ==================== Nested Types ====================

    /**
     * A chunk of knowledge retrieved from the index.
     *
     * @param id unique chunk identifier
     * @param content the text content
     * @param sourceId source document identifier
     * @param sourcePath original file path (if applicable)
     * @param score relevance score (0-1, higher is better)
     * @param startLine starting line in source (if applicable)
     * @param endLine ending line in source (if applicable)
     * @param metadata associated metadata
     */
    record KnowledgeChunk(
        String id,
        String content,
        String sourceId,
        String sourcePath,
        double score,
        int startLine,
        int endLine,
        Map<String, String> metadata
    ) {}

    /**
     * Result of an indexing operation.
     *
     * @param chunksCreated number of chunks created
     * @param chunksUpdated number of chunks updated
     * @param filesProcessed number of files processed
     * @param errors list of error messages (if any)
     */
    record IndexResult(
        int chunksCreated,
        int chunksUpdated,
        int filesProcessed,
        List<String> errors
    ) {
        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }
    }

    /**
     * Information about an indexed source.
     *
     * @param sourceId source identifier
     * @param path original file path
     * @param chunkCount number of chunks
     * @param indexedAt when it was indexed
     * @param metadata associated metadata
     */
    record SourceInfo(
        String sourceId,
        String path,
        int chunkCount,
        long indexedAt,
        Map<String, String> metadata
    ) {}

    /**
     * Knowledge base statistics.
     *
     * @param totalChunks total number of chunks
     * @param totalSources total number of sources
     * @param totalTokens estimated token count
     * @param indexSizeBytes size of index in bytes
     * @param embeddingDimension embedding vector dimension
     * @param lastUpdated last update timestamp
     */
    record IndexStats(
        int totalChunks,
        int totalSources,
        long totalTokens,
        long indexSizeBytes,
        int embeddingDimension,
        long lastUpdated
    ) {}
}
