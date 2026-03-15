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

import dev.fararoni.core.core.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("SessionModeManager Tests")
class SessionModeManagerTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        SessionModeManager.reset();
        WorkspaceManager.reset();
    }

    @AfterEach
    void tearDown() {
        SessionModeManager.reset();
        WorkspaceManager.reset();
    }

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {
        @Test
        @DisplayName("getInstance debe retornar la misma instancia")
        void getInstance_ShouldReturnSameInstance() {
            SessionModeManager sm1 = SessionModeManager.getInstance();
            SessionModeManager sm2 = SessionModeManager.getInstance();

            assertSame(sm1, sm2);
        }

        @Test
        @DisplayName("initialize debe retornar instancia y marcar como inicializado")
        void initialize_ShouldReturnInstanceAndMarkInitialized() {
            assertFalse(SessionModeManager.isInitialized());

            SessionModeManager sm = SessionModeManager.initialize(new String[]{});

            assertTrue(SessionModeManager.isInitialized());
            assertNotNull(sm);
        }

        @Test
        @DisplayName("initialize dos veces debe lanzar excepción")
        void initialize_Twice_ShouldThrowException() {
            SessionModeManager.initialize(new String[]{});

            assertThrows(IllegalStateException.class, () ->
                    SessionModeManager.initialize(new String[]{}));
        }

        @Test
        @DisplayName("reset debe permitir reinicialización")
        void reset_ShouldAllowReinitialization() {
            SessionModeManager.initialize(new String[]{});
            SessionModeManager.reset();

            assertFalse(SessionModeManager.isInitialized());

            assertDoesNotThrow(() -> SessionModeManager.initialize(new String[]{}));
        }
    }

    @Nested
    @DisplayName("Session Modes")
    class SessionModeTests {
        @Test
        @DisplayName("sin argumentos debe ser modo PERSISTENT")
        void noArgs_ShouldBePersistentMode() {
            SessionModeManager sm = SessionModeManager.initialize(new String[]{});

            assertEquals(SessionModeManager.SessionMode.PERSISTENT, sm.getMode());
            assertTrue(sm.isPersistent());
            assertFalse(sm.isIncognito());
            assertTrue(sm.persistsToDisk());
        }

        @Test
        @DisplayName("--incognito debe activar modo INCOGNITO")
        void incognitoArg_ShouldActivateIncognitoMode() {
            SessionModeManager sm = SessionModeManager.initialize(new String[]{"--incognito"});

            assertEquals(SessionModeManager.SessionMode.INCOGNITO, sm.getMode());
            assertTrue(sm.isIncognito());
            assertFalse(sm.isPersistent());
            assertFalse(sm.persistsToDisk());
        }

        @Test
        @DisplayName("--incognito entre otros argumentos debe funcionar")
        void incognitoArg_WithOtherArgs_ShouldWork() {
            SessionModeManager sm = SessionModeManager.initialize(
                    new String[]{"--url", "http://localhost", "--incognito", "--model", "test"});

            assertTrue(sm.isIncognito());
        }

        @Test
        @DisplayName("--purge debe marcar purge como solicitado")
        void purgeArg_ShouldMarkPurgeRequested() {
            SessionModeManager sm = SessionModeManager.initialize(new String[]{"--purge"});

            assertTrue(sm.isPurgeRequested());
            assertTrue(sm.isPersistent());
        }

        @Test
        @DisplayName("--incognito y --purge juntos deben funcionar")
        void incognitoAndPurge_ShouldWorkTogether() {
            SessionModeManager sm = SessionModeManager.initialize(
                    new String[]{"--incognito", "--purge"});

            assertTrue(sm.isIncognito());
            assertTrue(sm.isPurgeRequested());
        }
    }

    @Nested
    @DisplayName("Database URL Methods")
    class DatabaseUrlTests {
        @Test
        @DisplayName("modo PERSISTENT debe retornar URLs de archivo")
        void persistentMode_ShouldReturnFileUrls() {
            WorkspaceManager.reset();
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});

            SessionModeManager sm = SessionModeManager.initialize(new String[]{});

            String memoryUrl = sm.getMemoryDbUrl();
            String interactionsUrl = sm.getInteractionsDbUrl();
            String cacheUrl = sm.getCacheDbUrl();

            assertTrue(memoryUrl.startsWith("jdbc:sqlite:"));
            assertTrue(memoryUrl.contains("memory.db"));
            assertFalse(memoryUrl.equals("jdbc:sqlite::memory:"));

            assertTrue(interactionsUrl.contains("interactions.db"));
            assertTrue(cacheUrl.contains("cache.db"));
        }

        @Test
        @DisplayName("modo INCOGNITO debe retornar URLs en memoria")
        void incognitoMode_ShouldReturnMemoryUrls() {
            SessionModeManager sm = SessionModeManager.initialize(new String[]{"--incognito"});

            assertEquals("jdbc:sqlite::memory:", sm.getMemoryDbUrl());
            assertEquals("jdbc:sqlite::memory:", sm.getInteractionsDbUrl());
            assertEquals("jdbc:sqlite::memory:", sm.getCacheDbUrl());
        }

        @Test
        @DisplayName("modo INCOGNITO debe retornar null para audit log path")
        void incognitoMode_ShouldReturnNullForAuditLog() {
            SessionModeManager sm = SessionModeManager.initialize(new String[]{"--incognito"});

            assertNull(sm.getAuditLogPath());
            assertNull(sm.getConfigPath());
        }

        @Test
        @DisplayName("modo PERSISTENT debe retornar paths para audit log")
        void persistentMode_ShouldReturnPathsForAuditLog() {
            WorkspaceManager.reset();
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});

            SessionModeManager sm = SessionModeManager.initialize(new String[]{});

            assertNotNull(sm.getAuditLogPath());
            assertNotNull(sm.getConfigPath());
        }
    }

    @Nested
    @DisplayName("Info and Display")
    class InfoDisplayTests {
        @Test
        @DisplayName("getInfoString debe contener información del modo")
        void getInfoString_ShouldContainModeInfo() {
            SessionModeManager sm = SessionModeManager.initialize(new String[]{"--incognito"});

            String info = sm.getInfoString();

            assertNotNull(info);
            assertTrue(info.contains("Incognito") || info.contains("INCOGNITO"));
        }

        @Test
        @DisplayName("getBannerMessage debe ser null para PERSISTENT")
        void getBannerMessage_ShouldBeNullForPersistent() {
            SessionModeManager sm = SessionModeManager.initialize(new String[]{});

            assertNull(sm.getBannerMessage());
        }

        @Test
        @DisplayName("getBannerMessage debe retornar mensaje para INCOGNITO")
        void getBannerMessage_ShouldReturnMessageForIncognito() {
            SessionModeManager sm = SessionModeManager.initialize(new String[]{"--incognito"});

            String banner = sm.getBannerMessage();

            assertNotNull(banner);
            assertTrue(banner.contains("memoria") || banner.contains("INCÓGNITA"));
        }

        @Test
        @DisplayName("getModeSource debe indicar fuente")
        void getModeSource_ShouldIndicateSource() {
            SessionModeManager sm = SessionModeManager.initialize(new String[]{"--incognito"});

            String source = sm.getModeSource();

            assertNotNull(source);
            assertTrue(source.contains("--incognito") || source.contains("argument"));
        }

        @Test
        @DisplayName("toString debe ser informativo")
        void toString_ShouldBeInformative() {
            SessionModeManager sm = SessionModeManager.initialize(new String[]{"--incognito"});

            String str = sm.toString();

            assertNotNull(str);
            assertTrue(str.contains("INCOGNITO"));
        }
    }

    @Nested
    @DisplayName("SessionMode Enum")
    class SessionModeEnumTests {
        @Test
        @DisplayName("PERSISTENT debe persistir a disco")
        void persistent_ShouldPersistToDisk() {
            assertTrue(SessionModeManager.SessionMode.PERSISTENT.persistsToDisk());
        }

        @Test
        @DisplayName("INCOGNITO no debe persistir a disco")
        void incognito_ShouldNotPersistToDisk() {
            assertFalse(SessionModeManager.SessionMode.INCOGNITO.persistsToDisk());
        }

        @Test
        @DisplayName("CORPORATE_NO_PERSIST no debe persistir a disco")
        void corporateNoPersist_ShouldNotPersistToDisk() {
            assertFalse(SessionModeManager.SessionMode.CORPORATE_NO_PERSIST.persistsToDisk());
        }

        @Test
        @DisplayName("todos los modos deben tener displayName e icono")
        void allModes_ShouldHaveDisplayNameAndIcon() {
            for (SessionModeManager.SessionMode mode : SessionModeManager.SessionMode.values()) {
                assertNotNull(mode.getDisplayName());
                assertNotNull(mode.getIcon());
                assertFalse(mode.getDisplayName().isEmpty());
                assertFalse(mode.getIcon().isEmpty());
            }
        }
    }

    @Nested
    @DisplayName("Auto-initialization")
    class AutoInitTests {
        @Test
        @DisplayName("getInstance sin initialize debe auto-inicializar en modo PERSISTENT")
        void getInstance_WithoutInitialize_ShouldAutoInitAsPersistent() {
            SessionModeManager sm = SessionModeManager.getInstance();

            assertNotNull(sm);
            assertTrue(SessionModeManager.isInitialized());
            assertTrue(sm.isPersistent());
        }
    }
}
