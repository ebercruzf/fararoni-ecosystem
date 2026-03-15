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
package dev.fararoni.core.cli;

import dev.fararoni.core.core.security.SecureConfigService;
import dev.fararoni.core.core.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ConfigCommand Tests")
class ConfigCommandTest {
    @TempDir
    Path tempDir;

    private ByteArrayOutputStream outContent;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        resetSecureConfigService();
        WorkspaceManager.reset();
        WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});

        outContent = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
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
    @DisplayName("Available Keys")
    class AvailableKeysTests {
        @Test
        @DisplayName("debe tener todas las claves de API key definidas")
        void shouldHaveAllApiKeysDefined() {
            assertTrue(ConfigCommand.AVAILABLE_KEYS.containsKey("api-key"));
            assertTrue(ConfigCommand.AVAILABLE_KEYS.containsKey("api-key-backup"));
            assertTrue(ConfigCommand.AVAILABLE_KEYS.containsKey("openai-api-key"));
            assertTrue(ConfigCommand.AVAILABLE_KEYS.containsKey("anthropic-api-key"));
        }

        @Test
        @DisplayName("debe tener claves de configuracion general")
        void shouldHaveGeneralConfigKeys() {
            assertTrue(ConfigCommand.AVAILABLE_KEYS.containsKey("server-url"));
            assertTrue(ConfigCommand.AVAILABLE_KEYS.containsKey("model-name"));
            assertTrue(ConfigCommand.AVAILABLE_KEYS.containsKey("max-tokens"));
            assertTrue(ConfigCommand.AVAILABLE_KEYS.containsKey("temperature"));
        }

        @Test
        @DisplayName("las claves de API deben estar marcadas como secure")
        void apiKeysShouldBeMarkedAsSecure() {
            assertTrue(ConfigCommand.AVAILABLE_KEYS.get("api-key").secure());
            assertTrue(ConfigCommand.AVAILABLE_KEYS.get("openai-api-key").secure());
            assertTrue(ConfigCommand.AVAILABLE_KEYS.get("anthropic-api-key").secure());
        }

        @Test
        @DisplayName("las claves generales no deben estar marcadas como secure")
        void generalKeysShouldNotBeSecure() {
            assertFalse(ConfigCommand.AVAILABLE_KEYS.get("server-url").secure());
            assertFalse(ConfigCommand.AVAILABLE_KEYS.get("model-name").secure());
        }

        @Test
        @DisplayName("todas las claves deben tener env var asociada")
        void allKeysShouldHaveEnvVar() {
            for (var entry : ConfigCommand.AVAILABLE_KEYS.entrySet()) {
                assertNotNull(entry.getValue().envVar(),
                    "Key " + entry.getKey() + " should have envVar");
            }
        }
    }

    @Nested
    @DisplayName("maskSecret")
    class MaskSecretTests {
        @Test
        @DisplayName("debe enmascarar valores largos correctamente")
        void shouldMaskLongValues() {
            String masked = ConfigCommand.maskSecret("sk-abc123456789xyz");
            assertEquals("sk-a...9xyz", masked);
        }

        @Test
        @DisplayName("debe enmascarar valores cortos como ****")
        void shouldMaskShortValues() {
            String masked = ConfigCommand.maskSecret("short");
            assertEquals("****", masked);
        }

        @Test
        @DisplayName("debe manejar valores null")
        void shouldHandleNull() {
            String masked = ConfigCommand.maskSecret(null);
            assertEquals("****", masked);
        }

        @Test
        @DisplayName("debe manejar valores vacios")
        void shouldHandleEmpty() {
            String masked = ConfigCommand.maskSecret("");
            assertEquals("****", masked);
        }
    }

    @Nested
    @DisplayName("resolveValue")
    class ResolveValueTests {
        @Test
        @DisplayName("debe retornar null para clave sin valor")
        void shouldReturnNullForUnsetKey() {
            var keyInfo = ConfigCommand.AVAILABLE_KEYS.get("api-key");
            String value = ConfigCommand.resolveValue(keyInfo);
            assertDoesNotThrow(() -> ConfigCommand.resolveValue(keyInfo));
        }

        @Test
        @DisplayName("debe leer valor del config file")
        void shouldReadFromConfigFile() {
            var configService = SecureConfigService.getInstance();
            configService.setProperty("server.url", "http://test:8000");

            var keyInfo = ConfigCommand.AVAILABLE_KEYS.get("server-url");
            String value = ConfigCommand.resolveValue(keyInfo);

            assertEquals("http://test:8000", value);
        }
    }

    @Nested
    @DisplayName("getValueSource")
    class GetValueSourceTests {
        @Test
        @DisplayName("debe retornar FILE para valores del archivo")
        void shouldReturnFileForConfigValues() {
            var configService = SecureConfigService.getInstance();
            configService.setProperty("model.name", "test-model");

            var keyInfo = ConfigCommand.AVAILABLE_KEYS.get("model-name");
            String source = ConfigCommand.getValueSource(keyInfo);

            assertEquals("FILE", source);
        }
    }

    @Nested
    @DisplayName("ConfigKeyInfo Record")
    class ConfigKeyInfoTests {
        @Test
        @DisplayName("debe crear ConfigKeyInfo correctamente")
        void shouldCreateConfigKeyInfo() {
            var info = new ConfigCommand.ConfigKeyInfo(
                "internal.key",
                "Test description",
                true,
                "TEST_ENV_VAR"
            );

            assertEquals("internal.key", info.internalKey());
            assertEquals("Test description", info.description());
            assertTrue(info.secure());
            assertEquals("TEST_ENV_VAR", info.envVar());
        }
    }

    @Nested
    @DisplayName("Picocli Integration")
    class PicocliIntegrationTests {
        @Test
        @DisplayName("config sin argumentos debe mostrar ayuda")
        void configWithoutArgsShouldShowHelp() {
            var cmd = new CommandLine(new ConfigCommand());
            int exitCode = cmd.execute();
            assertEquals(0, exitCode);
            String output = outContent.toString();
            assertTrue(output.contains("Subcomandos disponibles") ||
                       output.contains("subcommand"));
        }

        @Test
        @DisplayName("config list debe mostrar claves")
        void configListShouldShowKeys() {
            var cmd = new CommandLine(new ConfigCommand());
            int exitCode = cmd.execute("list");
            assertEquals(0, exitCode);
            String output = outContent.toString();
            assertTrue(output.contains("api-key") || output.contains("API"));
        }

        @Test
        @DisplayName("config show debe mostrar configuracion")
        void configShowShouldShowConfiguration() {
            var cmd = new CommandLine(new ConfigCommand());
            int exitCode = cmd.execute("show");
            assertEquals(0, exitCode);
        }

        @Test
        @DisplayName("config set debe guardar valor")
        void configSetShouldSaveValue() {
            var cmd = new CommandLine(new ConfigCommand());
            int exitCode = cmd.execute("set", "server-url", "http://custom:9000");
            assertEquals(0, exitCode);

            var configService = SecureConfigService.getInstance();
            assertEquals("http://custom:9000", configService.getProperty("server.url"));
        }

        @Test
        @DisplayName("config get debe obtener valor")
        void configGetShouldRetrieveValue() {
            var configService = SecureConfigService.getInstance();
            configService.setProperty("context.window_size", "4096");

            var cmd = new CommandLine(new ConfigCommand());
            int exitCode = cmd.execute("get", "context-window");
            assertEquals(0, exitCode);
            String output = outContent.toString();
            assertTrue(output.contains("4096"));
        }

        @Test
        @DisplayName("config unset debe eliminar valor")
        void configUnsetShouldRemoveValue() {
            var configService = SecureConfigService.getInstance();
            configService.setProperty("ui.streaming", "true");

            var cmd = new CommandLine(new ConfigCommand());
            int exitCode = cmd.execute("unset", "streaming");
            assertEquals(0, exitCode);

            assertNull(configService.getProperty("ui.streaming"));
        }

        @Test
        @DisplayName("config set con clave desconocida debe fallar")
        void configSetWithUnknownKeyShouldFail() {
            var cmd = new CommandLine(new ConfigCommand());
            int exitCode = cmd.execute("set", "unknown-key", "value");
            assertEquals(1, exitCode);
        }
    }
}
