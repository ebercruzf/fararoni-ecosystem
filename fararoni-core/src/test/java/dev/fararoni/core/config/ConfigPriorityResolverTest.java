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
package dev.fararoni.core.config;

import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.security.SecureConfigService;
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
@DisplayName("ConfigPriorityResolver Tests")
class ConfigPriorityResolverTest {
    @TempDir
    Path tempDir;

    private ConfigPriorityResolver resolver;

    @BeforeEach
    void setUp() {
        resetSecureConfigService();
        WorkspaceManager.reset();
        WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
        resolver = new ConfigPriorityResolver();
    }

    @AfterEach
    void tearDown() {
        resetSecureConfigService();
        WorkspaceManager.reset();
    }

    private void resetSecureConfigService() {
        try {
            var field = SecureConfigService.class.getDeclaredField("instance");
            field.setAccessible(true);
            synchronized (SecureConfigService.class) {
                field.set(null, null);
            }
        } catch (Exception e) {
        }
    }

    @Nested
    @DisplayName("CLI Priority")
    class CliPriorityTests {
        @Test
        @DisplayName("CLI debe tener prioridad sobre todo")
        void cliShouldHaveHighestPriority() {
            var configService = SecureConfigService.getInstance();
            configService.setProperty("server.url", "http://file:8000");

            String resolved = resolver.resolve("server-url", "http://cli:9000");

            assertEquals("http://cli:9000", resolved);
        }

        @Test
        @DisplayName("CLI vacio debe caer a siguiente nivel")
        void emptyCliShouldFallThrough() {
            var configService = SecureConfigService.getInstance();
            configService.setProperty("server.url", "http://file:8000");

            String resolved = resolver.resolve("server-url", "");

            assertEquals("http://file:8000", resolved);
        }

        @Test
        @DisplayName("CLI null debe caer a siguiente nivel")
        void nullCliShouldFallThrough() {
            var configService = SecureConfigService.getInstance();
            configService.setProperty("server.url", "http://file:8000");

            String resolved = resolver.resolve("server-url", null);

            assertEquals("http://file:8000", resolved);
        }
    }

    @Nested
    @DisplayName("File Priority")
    class FilePriorityTests {
        @Test
        @DisplayName("debe leer valor del archivo de configuracion")
        void shouldReadFromConfigFile() {
            var configService = SecureConfigService.getInstance();
            configService.setProperty("model.name", "file-model");

            String resolved = resolver.resolve("model-name", null);

            assertEquals("file-model", resolved);
        }

        @Test
        @DisplayName("debe retornar null si no hay valor")
        void shouldReturnNullIfNoValue() {
            String resolved = resolver.resolve("model-name", null);

            assertNull(resolved);
        }
    }

    @Nested
    @DisplayName("resolveWithSource")
    class ResolveWithSourceTests {
        @Test
        @DisplayName("debe indicar fuente CLI")
        void shouldIndicateCliSource() {
            var result = resolver.resolveWithSource("server-url", "http://cli:8000");

            assertTrue(result.isPresent());
            assertEquals(ConfigPriorityResolver.Source.CLI, result.source());
            assertEquals("CLI", result.getSourceDescription());
        }

        @Test
        @DisplayName("debe indicar fuente FILE")
        void shouldIndicateFileSource() {
            var configService = SecureConfigService.getInstance();
            configService.setProperty("server.url", "http://file:8000");

            var result = resolver.resolveWithSource("server-url", null);

            assertTrue(result.isPresent());
            assertEquals(ConfigPriorityResolver.Source.FILE, result.source());
            assertEquals("FILE", result.getSourceDescription());
        }

        @Test
        @DisplayName("debe indicar NONE si no hay valor")
        void shouldIndicateNoneIfNoValue() {
            var result = resolver.resolveWithSource("model-name", null);

            assertFalse(result.isPresent());
            assertEquals(ConfigPriorityResolver.Source.NONE, result.source());
        }
    }

    @Nested
    @DisplayName("Specific Resolvers")
    class SpecificResolversTests {
        @Test
        @DisplayName("resolveApiKey debe resolver API key")
        void resolveApiKeyShouldWork() {
            var configService = SecureConfigService.getInstance();
            configService.setApiKey("file-api-key");

            String apiKey = resolver.resolveApiKey(null);

            assertEquals("file-api-key", apiKey);
        }

        @Test
        @DisplayName("resolveServerUrl debe usar default si no hay valor")
        void resolveServerUrlShouldUseDefault() {
            String url = resolver.resolveServerUrl(null);

            assertEquals(AppDefaults.DEFAULT_SERVER_URL, url);
        }

        @Test
        @DisplayName("resolveModelName debe usar default si no hay valor")
        void resolveModelNameShouldUseDefault() {
            String model = resolver.resolveModelName(null);

            assertEquals(AppDefaults.DEFAULT_MODEL_NAME, model);
        }
    }

    @Nested
    @DisplayName("hasValue")
    class HasValueTests {
        @Test
        @DisplayName("debe retornar false para clave sin valor")
        void shouldReturnFalseForMissingValue() {
            assertFalse(resolver.hasValue("model-name"));
        }

        @Test
        @DisplayName("debe retornar true para clave con valor en file")
        void shouldReturnTrueForFileValue() {
            var configService = SecureConfigService.getInstance();
            configService.setProperty("model.name", "test");

            assertTrue(resolver.hasValue("model-name"));
        }
    }

    @Nested
    @DisplayName("ResolvedValue")
    class ResolvedValueTests {
        @Test
        @DisplayName("empty() debe crear valor vacio")
        void emptyShouldCreateEmptyValue() {
            var empty = ConfigPriorityResolver.ResolvedValue.empty();

            assertFalse(empty.isPresent());
            assertNull(empty.value());
            assertEquals(ConfigPriorityResolver.Source.NONE, empty.source());
        }

        @Test
        @DisplayName("toOptional debe funcionar correctamente")
        void toOptionalShouldWork() {
            var present = new ConfigPriorityResolver.ResolvedValue("value", ConfigPriorityResolver.Source.CLI);
            var absent = ConfigPriorityResolver.ResolvedValue.empty();

            assertTrue(present.toOptional().isPresent());
            assertTrue(absent.toOptional().isEmpty());
        }

        @Test
        @DisplayName("getSourceDescription debe formatear ENV correctamente")
        void getSourceDescriptionShouldFormatEnv() {
            var result = new ConfigPriorityResolver.ResolvedValue(
                "value",
                ConfigPriorityResolver.Source.ENVIRONMENT,
                "MY_ENV_VAR"
            );

            assertEquals("ENV:MY_ENV_VAR", result.getSourceDescription());
        }
    }

    @Nested
    @DisplayName("Source Enum")
    class SourceEnumTests {
        @Test
        @DisplayName("debe tener todos los valores esperados")
        void shouldHaveAllExpectedValues() {
            assertEquals(4, ConfigPriorityResolver.Source.values().length);
            assertNotNull(ConfigPriorityResolver.Source.CLI);
            assertNotNull(ConfigPriorityResolver.Source.ENVIRONMENT);
            assertNotNull(ConfigPriorityResolver.Source.FILE);
            assertNotNull(ConfigPriorityResolver.Source.NONE);
        }
    }
}
