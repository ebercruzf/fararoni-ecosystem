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
package dev.fararoni.core.core.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("GraphRAG System - Sistema de Memoria")
class GraphRAGTest {
    @Nested
    @DisplayName("Edge")
    class EdgeTests {
        @Test
        @DisplayName("of() crea edge simple")
        void of_createsSimpleEdge() {
            Edge edge = Edge.of("source", "relation", "target");

            assertEquals("source", edge.source());
            assertEquals("relation", edge.relation());
            assertEquals("target", edge.target());
            assertEquals(1.0, edge.weight());
            assertNotNull(edge.id());
        }

        @Test
        @DisplayName("of() crea edge con peso")
        void of_createsEdgeWithWeight() {
            Edge edge = Edge.of("a", "rel", "b", 0.5);

            assertEquals(0.5, edge.weight());
        }

        @Test
        @DisplayName("builder() crea edge con metadatos")
        void builder_createsEdgeWithMetadata() {
            Edge edge = Edge.builder("src", "rel", "tgt")
                .weight(0.8)
                .metadata("key1", "value1")
                .metadata("key2", 123)
                .build();

            assertEquals(0.8, edge.weight());
            assertEquals("value1", edge.getMetadataString("key1"));
            assertEquals(123, edge.getMetadata("key2", Integer.class));
        }

        @Test
        @DisplayName("connectsTo() verifica conexion")
        void connectsTo_checksConnection() {
            Edge edge = Edge.of("a", "rel", "b");

            assertTrue(edge.connectsTo("a"));
            assertTrue(edge.connectsTo("b"));
            assertFalse(edge.connectsTo("c"));
        }

        @Test
        @DisplayName("getOpposite() retorna nodo opuesto")
        void getOpposite_returnsOppositeNode() {
            Edge edge = Edge.of("a", "rel", "b");

            assertEquals("b", edge.getOpposite("a"));
            assertEquals("a", edge.getOpposite("b"));
            assertNull(edge.getOpposite("c"));
        }

        @Test
        @DisplayName("isRelation() verifica tipo de relacion")
        void isRelation_checksRelationType() {
            Edge edge = Edge.of("a", "imports", "b");

            assertTrue(edge.isRelation("imports"));
            assertTrue(edge.isRelation("IMPORTS"));
            assertFalse(edge.isRelation("extends"));
        }

        @Test
        @DisplayName("isBidirectional() identifica relaciones simetricas")
        void isBidirectional_identifiesSymmetricRelations() {
            assertTrue(Edge.of("a", "relates_to", "b").isBidirectional());
            assertTrue(Edge.of("a", "similar_to", "b").isBidirectional());
            assertFalse(Edge.of("a", "imports", "b").isBidirectional());
        }

        @Test
        @DisplayName("withAccess() actualiza acceso")
        void withAccess_updatesAccess() {
            Edge original = Edge.of("a", "rel", "b");
            Edge accessed = original.withAccess();

            assertNotSame(original, accessed);
            assertEquals(1, accessed.accessCount());
            assertTrue(accessed.lastAccessed().isAfter(original.createdAt()) ||
                       accessed.lastAccessed().equals(original.lastAccessed()));
        }

        @Test
        @DisplayName("withWeight() actualiza peso")
        void withWeight_updatesWeight() {
            Edge original = Edge.of("a", "rel", "b", 0.5);
            Edge updated = original.withWeight(0.9);

            assertEquals(0.5, original.weight());
            assertEquals(0.9, updated.weight());
        }

        @Test
        @DisplayName("toTriplet() genera formato triplet")
        void toTriplet_generatesTripletFormat() {
            Edge edge = Edge.of("user:1", "asked", "agent");
            assertEquals("user:1 --asked--> agent", edge.toTriplet());
        }

        @Test
        @DisplayName("toNaturalLanguage() genera texto natural")
        void toNaturalLanguage_generatesNaturalText() {
            Edge edge = Edge.of("file:App.java", "contains_class", "class:App");
            assertEquals("file:App.java contains class class:App", edge.toNaturalLanguage());
        }

        @Test
        @DisplayName("Constructor normaliza peso fuera de rango")
        void constructor_normalizesOutOfRangeWeight() {
            Edge low = Edge.of("a", "rel", "b", -0.5);
            Edge high = Edge.of("a", "rel", "b", 1.5);

            assertEquals(0.0, low.weight());
            assertEquals(1.0, high.weight());
        }

        @Test
        @DisplayName("Constructor valida campos obligatorios")
        void constructor_validatesRequiredFields() {
            assertThrows(NullPointerException.class, () -> Edge.of(null, "rel", "b"));
            assertThrows(NullPointerException.class, () -> Edge.of("a", null, "b"));
            assertThrows(NullPointerException.class, () -> Edge.of("a", "rel", null));
        }
    }

    @Nested
    @DisplayName("InMemoryGraph")
    class InMemoryGraphTests {
        private InMemoryGraph graph;

        @BeforeEach
        void setUp() {
            graph = new InMemoryGraph();
        }

        @Test
        @DisplayName("addEdge() agrega edge correctamente")
        void addEdge_addsEdgeCorrectly() {
            Edge edge = Edge.of("a", "rel", "b");
            assertTrue(graph.addEdge(edge));
            assertEquals(1, graph.size());
        }

        @Test
        @DisplayName("addEdge() retorna false si ya existe")
        void addEdge_returnsFalseIfExists() {
            Edge edge = Edge.of("a", "rel", "b");
            assertTrue(graph.addEdge(edge));
            assertFalse(graph.addEdge(edge));
            assertEquals(1, graph.size());
        }

        @Test
        @DisplayName("removeEdge() remueve edge")
        void removeEdge_removesEdge() {
            Edge edge = Edge.of("a", "rel", "b");
            graph.addEdge(edge);

            Edge removed = graph.removeEdge(edge.id());
            assertNotNull(removed);
            assertEquals(0, graph.size());
        }

        @Test
        @DisplayName("getEdge() obtiene edge por ID")
        void getEdge_getsEdgeById() {
            Edge edge = Edge.of("a", "rel", "b");
            graph.addEdge(edge);

            assertTrue(graph.getEdge(edge.id()).isPresent());
            assertFalse(graph.getEdge("nonexistent").isPresent());
        }

        @Test
        @DisplayName("getOutgoing() obtiene edges salientes")
        void getOutgoing_getsOutgoingEdges() {
            graph.addEdge(Edge.of("a", "r1", "b"));
            graph.addEdge(Edge.of("a", "r2", "c"));
            graph.addEdge(Edge.of("b", "r3", "c"));

            List<Edge> outgoing = graph.getOutgoing("a");
            assertEquals(2, outgoing.size());
        }

        @Test
        @DisplayName("getIncoming() obtiene edges entrantes")
        void getIncoming_getsIncomingEdges() {
            graph.addEdge(Edge.of("a", "r1", "c"));
            graph.addEdge(Edge.of("b", "r2", "c"));
            graph.addEdge(Edge.of("a", "r3", "b"));

            List<Edge> incoming = graph.getIncoming("c");
            assertEquals(2, incoming.size());
        }

        @Test
        @DisplayName("getConnected() obtiene todos los edges conectados")
        void getConnected_getsAllConnectedEdges() {
            graph.addEdge(Edge.of("a", "r1", "b"));
            graph.addEdge(Edge.of("c", "r2", "a"));
            graph.addEdge(Edge.of("x", "r3", "y"));

            List<Edge> connected = graph.getConnected("a");
            assertEquals(2, connected.size());
        }

        @Test
        @DisplayName("findByRelation() busca por tipo de relacion")
        void findByRelation_searchesByRelationType() {
            graph.addEdge(Edge.of("a", "imports", "b"));
            graph.addEdge(Edge.of("c", "imports", "d"));
            graph.addEdge(Edge.of("e", "extends", "f"));

            List<Edge> imports = graph.findByRelation("imports");
            assertEquals(2, imports.size());
        }

        @Test
        @DisplayName("findBetween() busca edges entre dos nodos")
        void findBetween_findsEdgesBetweenNodes() {
            graph.addEdge(Edge.of("a", "r1", "b"));
            graph.addEdge(Edge.of("a", "r2", "b"));
            graph.addEdge(Edge.of("a", "r3", "c"));

            List<Edge> between = graph.findBetween("a", "b");
            assertEquals(2, between.size());
        }

        @Test
        @DisplayName("getNeighbors() obtiene vecinos a profundidad 1")
        void getNeighbors_getsNeighborsAtDepth1() {
            graph.addEdge(Edge.of("a", "r1", "b"));
            graph.addEdge(Edge.of("a", "r2", "c"));
            graph.addEdge(Edge.of("b", "r3", "d"));

            Set<String> neighbors = graph.getNeighbors("a");
            assertEquals(2, neighbors.size());
            assertTrue(neighbors.contains("b"));
            assertTrue(neighbors.contains("c"));
            assertFalse(neighbors.contains("d"));
        }

        @Test
        @DisplayName("getNeighbors() obtiene vecinos a profundidad 2")
        void getNeighbors_getsNeighborsAtDepth2() {
            graph.addEdge(Edge.of("a", "r1", "b"));
            graph.addEdge(Edge.of("b", "r2", "c"));
            graph.addEdge(Edge.of("c", "r3", "d"));

            Set<String> neighbors = graph.getNeighbors("a", 2);
            assertEquals(2, neighbors.size());
            assertTrue(neighbors.contains("b"));
            assertTrue(neighbors.contains("c"));
            assertFalse(neighbors.contains("d"));
        }

        @Test
        @DisplayName("findPath() encuentra camino mas corto")
        void findPath_findsShortestPath() {
            graph.addEdge(Edge.of("a", "r1", "b"));
            graph.addEdge(Edge.of("b", "r2", "c"));
            graph.addEdge(Edge.of("c", "r3", "d"));
            graph.addEdge(Edge.of("a", "r4", "d"));

            List<Edge> path = graph.findPath("a", "d");
            assertEquals(1, path.size());
            assertEquals("r4", path.get(0).relation());
        }

        @Test
        @DisplayName("findPath() retorna lista vacia si no hay camino")
        void findPath_returnsEmptyIfNoPath() {
            graph.addEdge(Edge.of("a", "r1", "b"));
            graph.addEdge(Edge.of("x", "r2", "y"));

            List<Edge> path = graph.findPath("a", "y");
            assertTrue(path.isEmpty());
        }

        @Test
        @DisplayName("removeNode() remueve todos los edges del nodo")
        void removeNode_removesAllNodeEdges() {
            graph.addEdge(Edge.of("a", "r1", "b"));
            graph.addEdge(Edge.of("c", "r2", "a"));
            graph.addEdge(Edge.of("x", "r3", "y"));

            int removed = graph.removeNode("a");
            assertEquals(2, removed);
            assertEquals(1, graph.size());
        }

        @Test
        @DisplayName("evictOlderThan() evicta edges antiguos")
        void evictOlderThan_evictsOldEdges() throws InterruptedException {
            graph.addEdge(Edge.of("a", "r1", "b"));
            Thread.sleep(50);
            graph.addEdge(Edge.of("c", "r2", "d"));

            int evicted = graph.evictOlderThan(0);
            assertEquals(2, evicted);
        }

        @Test
        @DisplayName("evictBelowWeight() evicta edges de bajo peso")
        void evictBelowWeight_evictsLowWeightEdges() {
            graph.addEdge(Edge.of("a", "r1", "b", 0.9));
            graph.addEdge(Edge.of("c", "r2", "d", 0.3));
            graph.addEdge(Edge.of("e", "r3", "f", 0.5));

            int evicted = graph.evictBelowWeight(0.5);
            assertEquals(1, evicted);
            assertEquals(2, graph.size());
        }

        @Test
        @DisplayName("getStats() retorna estadisticas correctas")
        void getStats_returnsCorrectStats() {
            graph.addEdge(Edge.of("a", "r1", "b"));
            graph.addEdge(Edge.of("a", "r2", "c"));
            graph.addEdge(Edge.of("b", "r1", "d"));

            Map<String, Object> stats = graph.getStats();
            assertEquals(3, stats.get("edges"));
            assertEquals(4, stats.get("nodes"));
            assertEquals(2, stats.get("relations"));
        }

        @Test
        @DisplayName("clear() limpia el grafo")
        void clear_clearsGraph() {
            graph.addEdge(Edge.of("a", "r1", "b"));
            graph.addEdge(Edge.of("c", "r2", "d"));

            graph.clear();
            assertTrue(graph.isEmpty());
            assertEquals(0, graph.size());
        }
    }

    @Nested
    @DisplayName("GraphRAGService")
    class GraphRAGServiceTests {
        private GraphRAGService rag;

        @BeforeEach
        void setUp() {
            rag = GraphRAGService.create();
        }

        @Test
        @DisplayName("addFact() agrega hecho simple")
        void addFact_addsSimpleFact() {
            String id = rag.addFact("user:1", "asked", "question about Java");

            assertNotNull(id);
            assertTrue(rag.getGraph().size() > 0);
        }

        @Test
        @DisplayName("addFact() agrega hecho con peso")
        void addFact_addsFactWithWeight() {
            rag.addFact("file:App.java", "contains", "class:App", 0.9);

            List<Edge> edges = rag.getGraph().findByRelation("contains");
            assertEquals(1, edges.size());
            assertEquals(0.9, edges.get(0).weight());
        }

        @Test
        @DisplayName("removeFact() remueve hecho")
        void removeFact_removesFact() {
            String id = rag.addFact("a", "rel", "b");
            assertTrue(rag.removeFact(id));
            assertEquals(0, rag.getGraph().size());
        }

        @Test
        @DisplayName("addMessage() agrega mensaje y extrae entidades")
        void addMessage_addsMessageAndExtractsEntities() {
            rag.addMessage("user", "Create a class named User in file User.java", "session1");

            assertTrue(rag.getGraph().size() > 0);
        }

        @Test
        @DisplayName("getConversationHistory() retorna historial")
        void getConversationHistory_returnsHistory() {
            rag.addMessage("user", "Hello", "s1");
            rag.addMessage("assistant", "Hi!", "s1");

            var history = rag.getConversationHistory("s1");
            assertEquals(2, history.size());
            assertEquals("user", history.get(0).role());
            assertEquals("assistant", history.get(1).role());
        }

        @Test
        @DisplayName("clearSession() limpia sesion")
        void clearSession_clearsSession() {
            rag.addMessage("user", "Hello", "s1");
            rag.clearSession("s1");

            assertTrue(rag.getConversationHistory("s1").isEmpty());
        }

        @Test
        @DisplayName("retrieveContext() recupera contexto relevante")
        void retrieveContext_retrievesRelevantContext() {
            rag.addFact("file:App.java", "contains", "class:App");
            rag.addFact("class:App", "extends", "class:BaseClass");
            rag.addFact("file:Utils.java", "contains", "class:Utils");

            String context = rag.retrieveContext("Tell me about App.java");

            assertTrue(context.contains("Relevant Context"));
            assertTrue(context.toLowerCase().contains("app"));
        }

        @Test
        @DisplayName("retrieveFacts() retorna triplets")
        void retrieveFacts_returnsTriplets() {
            rag.addFact("file:App.java", "contains", "class:App");
            rag.addFact("class:App", "uses", "class:Utils");

            List<String[]> facts = rag.retrieveFacts("App", 10);

            assertTrue(facts.size() >= 1);
            assertEquals(3, facts.get(0).length);
        }

        @Test
        @DisplayName("retrieveRelatedEntities() retorna entidades relacionadas")
        void retrieveRelatedEntities_returnsRelatedEntities() {
            rag.addFact("file:App.java", "contains", "class:App");
            rag.addFact("class:App", "uses", "class:Utils");

            Set<String> related = rag.retrieveRelatedEntities("App", 2);

            assertTrue(related.size() > 0);
        }

        @Test
        @DisplayName("extractEntities() extrae entidades de texto")
        void extractEntities_extractsEntitiesFromText() {
            Set<String> entities = rag.extractEntities(
                "Check the class User in file User.java and the method getName"
            );

            assertTrue(entities.stream().anyMatch(e -> e.contains("User.java")));
        }

        @Test
        @DisplayName("getStats() retorna estadisticas")
        void getStats_returnsStats() {
            rag.addFact("a", "rel", "b");
            rag.addMessage("user", "Hello", "s1");

            Map<String, Object> stats = rag.getStats();
            assertTrue(stats.containsKey("edges"));
            assertTrue(stats.containsKey("sessions"));
            assertTrue(stats.containsKey("totalMessages"));
        }

        @Test
        @DisplayName("runMaintenance() ejecuta limpieza")
        void runMaintenance_runsCleanup() {
            rag.addFact("a", "rel", "b", 0.1);
            rag.addFact("c", "rel", "d", 0.9);

            int evicted = rag.runMaintenance(86400, 0.5);
            assertTrue(evicted > 0);
        }

        @Test
        @DisplayName("clear() limpia todo")
        void clear_clearsEverything() {
            rag.addFact("a", "rel", "b");
            rag.addMessage("user", "Hello", "s1");

            rag.clear();

            assertEquals(0, rag.getGraph().size());
            assertTrue(rag.getConversationHistory("s1").isEmpty());
        }

        @Test
        @DisplayName("Builder configura correctamente")
        void builder_configuresCorrectly() {
            GraphRAGService custom = GraphRAGService.builder()
                .graphCapacity(5000)
                .maxContextItems(10)
                .maxContextDepth(3)
                .build();

            assertNotNull(custom);
            assertEquals(5000, custom.getGraph().getCapacity());
        }
    }
}
