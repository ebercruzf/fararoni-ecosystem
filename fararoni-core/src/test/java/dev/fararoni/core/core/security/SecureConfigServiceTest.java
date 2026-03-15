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
package dev.fararoni.core.core.security;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("SecureConfigService Tests")
class SecureConfigServiceTest {
    @TempDir
    Path tempDir;

    private SecureConfigService service;
    private Path configFile;

    @BeforeEach
    void setUp() {
        configFile = tempDir.resolve("secure-config.properties");
        service = new SecureConfigService(configFile);
    }

    @AfterEach
    void tearDown() {
        SecureConfigService.resetForTesting();
        HardwareIdGenerator.clearCache();
    }

    @Nested
    @DisplayName("API Key Storage")
    class ApiKeyTests {
        @Test
        @DisplayName("setApiKey/getApiKey debe encriptar y desencriptar correctamente")
        void setGetApiKey_ShouldEncryptAndDecrypt() {
            String apiKey = "sk-proj-abc123xyz789-test-key";

            service.setApiKey(apiKey);
            String retrieved = service.getApiKey();

            assertEquals(apiKey, retrieved);
        }

        @Test
        @DisplayName("API key en archivo debe estar encriptada")
        void apiKey_InFile_ShouldBeEncrypted() throws Exception {
            String apiKey = "sk-proj-abc123xyz789-test-key";
            service.setApiKey(apiKey);

            List<String> lines = Files.readAllLines(configFile);
            String content = String.join("\n", lines);

            assertFalse(content.contains(apiKey),
                    "API key no debe estar en texto plano en el archivo");

            assertTrue(content.contains("ENC:") || content.contains("ENC\\:"),
                    "Debe usar prefijo ENC: para valores encriptados, content: " + content);
        }

        @Test
        @DisplayName("setBackupApiKey/getBackupApiKey debe funcionar")
        void setGetBackupApiKey_ShouldWork() {
            String backupKey = "sk-backup-key-123";

            service.setBackupApiKey(backupKey);

            assertEquals(backupKey, service.getBackupApiKey());
        }

        @Test
        @DisplayName("setOpenAiApiKey/getOpenAiApiKey debe funcionar")
        void setGetOpenAiApiKey_ShouldWork() {
            String openaiKey = "sk-openai-test-key";

            service.setOpenAiApiKey(openaiKey);

            assertEquals(openaiKey, service.getOpenAiApiKey());
        }

        @Test
        @DisplayName("setAnthropicApiKey/getAnthropicApiKey debe funcionar")
        void setGetAnthropicApiKey_ShouldWork() {
            String anthropicKey = "sk-ant-test-key";

            service.setAnthropicApiKey(anthropicKey);

            assertEquals(anthropicKey, service.getAnthropicApiKey());
        }

        @Test
        @DisplayName("getApiKey sin configurar debe retornar null")
        void getApiKey_WhenNotSet_ShouldReturnNull() {
            assertNull(service.getApiKey());
        }
    }

    @Nested
    @DisplayName("Generic Properties")
    class GenericPropertyTests {
        @Test
        @DisplayName("setSecureProperty/getSecureProperty con clave custom")
        void setGetSecureProperty_WithCustomKey_ShouldWork() {
            service.setSecureProperty("custom.secret", "my-secret-value");

            assertEquals("my-secret-value", service.getSecureProperty("custom.secret"));
        }

        @Test
        @DisplayName("setProperty/getProperty para valores no sensibles")
        void setGetProperty_ForNonSensitive_ShouldNotEncrypt() throws Exception {
            service.setProperty("app.version", "1.0.0");

            List<String> lines = Files.readAllLines(configFile);
            String content = String.join("\n", lines);

            assertTrue(content.contains("1.0.0"),
                    "Valores no sensibles deben guardarse en texto plano");
            assertFalse(content.contains("ENC:") && content.contains("app.version=ENC:"),
                    "No debe encriptar propiedades normales");
        }

        @Test
        @DisplayName("hasProperty debe detectar propiedades existentes")
        void hasProperty_ShouldDetectExisting() {
            assertFalse(service.hasProperty("test.key"));

            service.setProperty("test.key", "value");

            assertTrue(service.hasProperty("test.key"));
        }

        @Test
        @DisplayName("removeProperty debe eliminar la propiedad")
        void removeProperty_ShouldRemove() {
            service.setProperty("to.remove", "value");
            assertTrue(service.hasProperty("to.remove"));

            service.removeProperty("to.remove");

            assertFalse(service.hasProperty("to.remove"));
        }

        @Test
        @DisplayName("setSecureProperty con null debe eliminar la propiedad")
        void setSecureProperty_WithNull_ShouldRemove() {
            service.setApiKey("test-key");
            assertNotNull(service.getApiKey());

            service.setApiKey(null);

            assertNull(service.getApiKey());
        }

        @Test
        @DisplayName("setSecureProperty con clave null debe lanzar excepción")
        void setSecureProperty_WithNullKey_ShouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                    service.setSecureProperty(null, "value"));
        }
    }

    @Nested
    @DisplayName("File Persistence")
    class PersistenceTests {
        @Test
        @DisplayName("configuración debe persistir entre instancias")
        void config_ShouldPersistBetweenInstances() {
            service.setApiKey("persistent-key");
            service.setProperty("app.name", "TestApp");

            SecureConfigService service2 = new SecureConfigService(configFile);

            assertEquals("persistent-key", service2.getApiKey());
            assertEquals("TestApp", service2.getProperty("app.name"));
        }

        @Test
        @DisplayName("hot-reload debe detectar cambios externos")
        void hotReload_ShouldDetectExternalChanges() throws Exception {
            service.setProperty("external.prop", "original");

            Thread.sleep(100);

            List<String> lines = Files.readAllLines(configFile);
            StringBuilder modified = new StringBuilder();
            for (String line : lines) {
                if (line.contains("external.prop")) {
                    modified.append("external.prop=modified\n");
                } else {
                    modified.append(line).append("\n");
                }
            }
            Files.writeString(configFile, modified.toString());

            Thread.sleep(100);
            assertEquals("modified", service.getProperty("external.prop"));
        }

        @Test
        @DisplayName("getConfigFilePath debe retornar path correcto")
        void getConfigFilePath_ShouldReturnCorrectPath() {
            assertEquals(configFile, service.getConfigFilePath());
        }
    }

    @Nested
    @DisplayName("Hardware Binding")
    class HardwareBindingTests {
        @Test
        @DisplayName("hardware ID debe guardarse automáticamente")
        void hardwareId_ShouldBeSavedAutomatically() {
            service.setProperty("test", "value");

            assertTrue(service.hasProperty(SecureConfigService.KEY_HARDWARE_ID));
        }

        @Test
        @DisplayName("hardware ID guardado debe coincidir con actual")
        void savedHardwareId_ShouldMatchCurrent() {
            service.setProperty("test", "value");

            String savedHwid = service.getProperty(SecureConfigService.KEY_HARDWARE_ID);
            String currentHwid = HardwareIdGenerator.generateHardwareId();

            assertEquals(currentHwid, savedHwid);
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityTests {
        @Test
        @DisplayName("hasAnyApiKey debe detectar cualquier API key")
        void hasAnyApiKey_ShouldDetectAnyKey() {
            assertFalse(service.hasAnyApiKey());

            service.setOpenAiApiKey("test-openai");

            assertTrue(service.hasAnyApiKey());
        }

        @Test
        @DisplayName("getBestAvailableApiKey debe retornar en orden de prioridad")
        void getBestAvailableApiKey_ShouldReturnInPriorityOrder() {
            assertNull(service.getBestAvailableApiKey());

            service.setAnthropicApiKey("anthropic-key");
            assertEquals("anthropic-key", service.getBestAvailableApiKey());

            service.setOpenAiApiKey("openai-key");
            assertEquals("openai-key", service.getBestAvailableApiKey());

            service.setBackupApiKey("backup-key");
            assertEquals("backup-key", service.getBestAvailableApiKey());

            service.setApiKey("main-key");
            assertEquals("main-key", service.getBestAvailableApiKey());
        }
    }

    @Nested
    @DisplayName("Migration Support")
    class MigrationTests {
        @Test
        @DisplayName("debe poder leer valores no encriptados (migración)")
        void shouldReadUnencryptedValues_ForMigration() throws Exception {
            Files.writeString(configFile, "api.key=sk-plain-text-key-for-migration\n");

            SecureConfigService service2 = new SecureConfigService(configFile);
            String apiKey = service2.getApiKey();

            assertEquals("sk-plain-text-key-for-migration", apiKey);
        }
    }
}
