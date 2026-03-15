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
package dev.fararoni.core.core.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Search Package - El Sabueso Tests")
class SearchPackageTest {
    @Nested
    @DisplayName("VectorUtils Tests")
    class VectorUtilsTests {
        @Test
        @DisplayName("Debe calcular similitud de coseno correctamente")
        void shouldCalculateCosineSimilarity() {
            float[] v1 = {1.0f, 0.0f, 0.0f};
            float[] v2 = {1.0f, 0.0f, 0.0f};

            double similarity = VectorUtils.cosineSimilarity(v1, v2);

            assertEquals(1.0, similarity, 0.0001, "Vectores identicos deben tener similitud 1.0");
        }

        @Test
        @DisplayName("Debe retornar 0 para vectores ortogonales")
        void shouldReturnZeroForOrthogonalVectors() {
            float[] v1 = {1.0f, 0.0f};
            float[] v2 = {0.0f, 1.0f};

            double similarity = VectorUtils.cosineSimilarity(v1, v2);

            assertEquals(0.0, similarity, 0.0001, "Vectores ortogonales deben tener similitud 0");
        }

        @Test
        @DisplayName("Debe manejar vectores null")
        void shouldHandleNullVectors() {
            assertEquals(0.0, VectorUtils.cosineSimilarity(null, new float[]{1.0f}));
            assertEquals(0.0, VectorUtils.cosineSimilarity(new float[]{1.0f}, null));
            assertEquals(0.0, VectorUtils.cosineSimilarity(null, null));
        }

        @Test
        @DisplayName("Debe manejar vectores de diferente longitud")
        void shouldHandleDifferentLengthVectors() {
            float[] v1 = {1.0f, 2.0f};
            float[] v2 = {1.0f, 2.0f, 3.0f};

            double similarity = VectorUtils.cosineSimilarity(v1, v2);

            assertEquals(0.0, similarity, "Vectores de diferente longitud deben retornar 0");
        }

        @Test
        @DisplayName("Debe normalizar vectores correctamente")
        void shouldNormalizeVectors() {
            float[] v = {3.0f, 4.0f};

            float[] normalized = VectorUtils.normalize(v);

            assertEquals(0.6f, normalized[0], 0.0001f);
            assertEquals(0.8f, normalized[1], 0.0001f);

            double norm = Math.sqrt(normalized[0] * normalized[0] + normalized[1] * normalized[1]);
            assertEquals(1.0, norm, 0.0001);
        }

        @Test
        @DisplayName("Debe calcular distancia euclidiana")
        void shouldCalculateEuclideanDistance() {
            float[] v1 = {0.0f, 0.0f};
            float[] v2 = {3.0f, 4.0f};

            double distance = VectorUtils.euclideanDistance(v1, v2);

            assertEquals(5.0, distance, 0.0001);
        }
    }

    @Nested
    @DisplayName("BM25Engine Tests")
    class BM25EngineTests {
        private BM25Engine bm25;

        @BeforeEach
        void setUp() {
            bm25 = new BM25Engine();
        }

        @Test
        @DisplayName("Debe indexar documentos correctamente")
        void shouldIndexDocuments() {
            bm25.index("doc1", "public class UserFactory creates users");
            bm25.index("doc2", "public class ProductService handles products");

            assertEquals(2, bm25.getDocumentCount());
        }

        @Test
        @DisplayName("Debe encontrar documentos con terminos exactos")
        void shouldFindDocumentsWithExactTerms() {
            bm25.index("UserFactory.java", "public class UserFactory creates users");
            bm25.index("ProductService.java", "public class ProductService handles products");

            Map<String, Double> results = bm25.search("UserFactory");

            assertTrue(results.containsKey("UserFactory.java"),
                "Debe encontrar documento con termino exacto");
            assertFalse(results.containsKey("ProductService.java"),
                "No debe encontrar documento sin el termino");
        }

        @Test
        @DisplayName("Debe rankear por relevancia")
        void shouldRankByRelevance() {
            bm25.index("doc1", "user user user management");
            bm25.index("doc2", "user management");
            bm25.index("doc3", "product service");

            Map<String, Double> results = bm25.search("user");

            assertTrue(results.containsKey("doc1"));
            assertTrue(results.containsKey("doc2"));
            assertFalse(results.containsKey("doc3"));

            assertTrue(results.get("doc1") > results.get("doc2"),
                "Documento con mas ocurrencias debe tener mayor score");
        }

        @Test
        @DisplayName("Debe retornar vacio para consulta sin coincidencias")
        void shouldReturnEmptyForNoMatches() {
            bm25.index("doc1", "hello world");

            Map<String, Double> results = bm25.search("xyz123");

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Debe manejar consulta null o vacia")
        void shouldHandleNullOrEmptyQuery() {
            bm25.index("doc1", "hello world");

            assertTrue(bm25.search(null).isEmpty());
            assertTrue(bm25.search("").isEmpty());
            assertTrue(bm25.search("   ").isEmpty());
        }

        @Test
        @DisplayName("Debe verificar existencia de terminos")
        void shouldCheckTermExistence() {
            bm25.index("doc1", "hello world");

            assertTrue(bm25.containsTerm("hello"));
            assertTrue(bm25.containsTerm("world"));
            assertFalse(bm25.containsTerm("goodbye"));
        }
    }

    @Nested
    @DisplayName("VectorEngine Tests")
    class VectorEngineTests {
        private VectorEngine vectorEngine;

        @BeforeEach
        void setUp() {
            vectorEngine = new VectorEngine();
        }

        @Test
        @DisplayName("Debe indexar embeddings correctamente")
        void shouldIndexEmbeddings() {
            vectorEngine.index("doc1", new float[]{0.1f, 0.2f, 0.3f});
            vectorEngine.index("doc2", new float[]{0.4f, 0.5f, 0.6f});

            assertEquals(2, vectorEngine.size());
            assertTrue(vectorEngine.contains("doc1"));
            assertTrue(vectorEngine.contains("doc2"));
        }

        @Test
        @DisplayName("Debe encontrar documentos similares")
        void shouldFindSimilarDocuments() {
            vectorEngine.index("doc1", new float[]{1.0f, 0.0f, 0.0f});
            vectorEngine.index("doc2", new float[]{0.0f, 1.0f, 0.0f});

            float[] query = {0.9f, 0.1f, 0.0f};
            Map<String, Double> results = vectorEngine.search(query);

            assertTrue(results.containsKey("doc1"), "Debe encontrar documento similar");
            assertTrue(results.get("doc1") > results.getOrDefault("doc2", 0.0),
                "doc1 debe ser mas relevante que doc2");
        }

        @Test
        @DisplayName("Debe respetar umbral de similitud")
        void shouldRespectSimilarityThreshold() {
            vectorEngine.setSimilarityThreshold(0.9);
            vectorEngine.index("doc1", new float[]{1.0f, 0.0f});

            float[] query = {0.7f, 0.7f};
            Map<String, Double> results = vectorEngine.search(query);

            assertTrue(results.isEmpty() || results.values().stream().allMatch(s -> s > 0.9),
                "Solo debe retornar documentos que superen el umbral");
        }

        @Test
        @DisplayName("Debe eliminar documentos")
        void shouldRemoveDocuments() {
            vectorEngine.index("doc1", new float[]{1.0f, 0.0f});

            boolean removed = vectorEngine.remove("doc1");

            assertTrue(removed);
            assertFalse(vectorEngine.contains("doc1"));
            assertEquals(0, vectorEngine.size());
        }

        @Test
        @DisplayName("Debe lanzar excepcion para embedding null")
        void shouldThrowForNullEmbedding() {
            assertThrows(IllegalArgumentException.class, () -> {
                vectorEngine.index("doc1", null);
            });
        }
    }

    @Nested
    @DisplayName("TheHound Tests")
    class TheHoundTests {
        private TheHound hound;

        private final TheHound.EmbeddingProvider mockProvider = text -> {
            if (text == null || text.isEmpty()) {
                return new float[0];
            }
            float[] embedding = new float[8];
            embedding[0] = text.length() / 100.0f;
            embedding[1] = text.contains("user") ? 1.0f : 0.0f;
            embedding[2] = text.contains("create") ? 1.0f : 0.0f;
            embedding[3] = text.contains("product") ? 1.0f : 0.0f;
            embedding[4] = text.contains("class") ? 0.5f : 0.0f;
            embedding[5] = text.contains("public") ? 0.3f : 0.0f;
            embedding[6] = (float) text.chars().filter(c -> c == ' ').count() / 10.0f;
            embedding[7] = text.toLowerCase().contains("factory") ? 0.8f : 0.0f;
            return embedding;
        };

        @BeforeEach
        void setUp() {
            hound = new TheHound(mockProvider);
        }

        @Test
        @DisplayName("Debe indexar documentos en ambos motores")
        void shouldIndexInBothEngines() {
            hound.learn("UserFactory.java", "public class UserFactory creates users");
            hound.learn("ProductService.java", "public class ProductService handles products");

            assertEquals(2, hound.getDocumentCount());
            assertEquals(2, hound.getBM25Engine().getDocumentCount());
            assertEquals(2, hound.getVectorEngine().size());
        }

        @Test
        @DisplayName("Debe encontrar documentos con busqueda hibrida")
        void shouldFindWithHybridSearch() {
            hound.learn("UserFactory.java", "public class UserFactory creates users");
            hound.learn("ProductService.java", "public class ProductService handles products");
            hound.learn("Utils.java", "public class Utils with helper methods");

            List<String> results = hound.hunt("UserFactory", 3);

            assertFalse(results.isEmpty(), "Debe encontrar al menos un resultado");
            assertEquals("UserFactory.java", results.get(0),
                "UserFactory.java debe ser el primer resultado");
        }

        @Test
        @DisplayName("Debe combinar resultados de ambos motores")
        void shouldCombineResultsFromBothEngines() {
            hound.learn("doc1", "user factory creation");
            hound.learn("doc2", "product service handler");
            hound.learn("doc3", "user management system");

            List<String> results = hound.hunt("user", 3);

            assertTrue(results.contains("doc1") || results.contains("doc3"),
                "Debe encontrar documentos relacionados con 'user'");
        }

        @Test
        @DisplayName("Debe soportar busqueda solo lexica")
        void shouldSupportLexicalOnlySearch() {
            hound.learn("UserFactory.java", "UserFactory class");
            hound.learn("Other.java", "other content");

            List<String> results = hound.huntLexical("UserFactory", 2);

            assertEquals(1, results.size());
            assertEquals("UserFactory.java", results.get(0));
        }

        @Test
        @DisplayName("Debe soportar busqueda solo semantica")
        void shouldSupportSemanticOnlySearch() {
            hound.learn("doc1", "user creation factory");
            hound.learn("doc2", "product handling service");

            List<String> results = hound.huntSemantic("user create", 2);

            assertFalse(results.isEmpty());
        }

        @Test
        @DisplayName("Debe limpiar indice con forget")
        void shouldClearIndexWithForget() {
            hound.learn("doc1", "content");

            hound.forget();

            assertEquals(0, hound.getDocumentCount());
        }

        @Test
        @DisplayName("Debe manejar consulta vacia")
        void shouldHandleEmptyQuery() {
            hound.learn("doc1", "content");

            assertTrue(hound.hunt(null, 5).isEmpty());
            assertTrue(hound.hunt("", 5).isEmpty());
            assertTrue(hound.hunt("   ", 5).isEmpty());
        }

        @Test
        @DisplayName("Debe lanzar excepcion sin provider")
        void shouldThrowWithoutProvider() {
            assertThrows(IllegalArgumentException.class, () -> {
                new TheHound(null);
            });
        }

        @Test
        @DisplayName("Debe retornar estadisticas")
        void shouldReturnStats() {
            hound.learn("doc1", "content one");
            hound.learn("doc2", "content two");

            String stats = hound.getStats();

            assertNotNull(stats);
            assertTrue(stats.contains("2"), "Stats debe mencionar 2 documentos");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        @Test
        @DisplayName("Debe funcionar el flujo completo de busqueda")
        void shouldWorkFullSearchFlow() {
            TheHound.EmbeddingProvider provider = text -> {
                float[] emb = new float[4];
                emb[0] = text.contains("user") ? 1.0f : 0.0f;
                emb[1] = text.contains("product") ? 1.0f : 0.0f;
                emb[2] = text.length() / 50.0f;
                emb[3] = 0.5f;
                return emb;
            };

            TheHound hound = new TheHound(provider);

            hound.learn("UserFactory.java",
                "public class UserFactory { public User createUser(String name) { } }");
            hound.learn("ProductService.java",
                "public class ProductService { public Product getProduct(int id) { } }");
            hound.learn("UserRepository.java",
                "public class UserRepository { public User findUser(int id) { } }");
            hound.learn("Utils.java",
                "public class Utils { public static String format(String s) { } }");

            List<String> exactResults = hound.hunt("UserFactory", 3);
            assertTrue(exactResults.contains("UserFactory.java"),
                "Debe encontrar por termino exacto");

            List<String> conceptResults = hound.hunt("user", 3);
            assertTrue(
                conceptResults.contains("UserFactory.java") ||
                conceptResults.contains("UserRepository.java"),
                "Debe encontrar documentos relacionados con 'user'"
            );
        }
    }
}
