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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("FileSecurityFilter Tests - Paso 9: Seguridad Enterprise")
class FileSecurityFilterTest {
    private FileSecurityFilter filter;

    @BeforeEach
    void setUp() {
        filter = FileSecurityFilter.standard();
    }

    @Nested
    @DisplayName("Extensiones Sensibles")
    class SensitiveExtensionsTests {
        @Test
        @DisplayName("Detecta archivos .env como sensibles")
        void testEnvFileIsSensitive() {
            assertTrue(filter.isSensitive(".env"), ".env deberia ser sensible");
            assertTrue(filter.isSensitive("config/.env"), "config/.env deberia ser sensible");
            assertTrue(filter.isSensitive(".env.local"), ".env.local deberia ser sensible");
        }

        @Test
        @DisplayName("Detecta archivos .pem como sensibles")
        void testPemFileIsSensitive() {
            assertTrue(filter.isSensitive("certificate.pem"));
            assertTrue(filter.isSensitive("keys/private.pem"));
        }

        @Test
        @DisplayName("Detecta archivos .key como sensibles")
        void testKeyFileIsSensitive() {
            assertTrue(filter.isSensitive("private.key"));
            assertTrue(filter.isSensitive("ssl/server.key"));
        }

        @Test
        @DisplayName("Detecta archivos .jks como sensibles")
        void testJksFileIsSensitive() {
            assertTrue(filter.isSensitive("keystore.jks"));
        }
    }

    @Nested
    @DisplayName("Nombres Sensibles")
    class SensitiveFilenamesTests {
        @Test
        @DisplayName("Detecta archivos SSH como sensibles")
        void testSshFilesAreSensitive() {
            assertTrue(filter.isSensitive("id_rsa"));
            assertTrue(filter.isSensitive("id_ed25519"));
            assertTrue(filter.isSensitive("authorized_keys"));
        }

        @Test
        @DisplayName("Detecta credentials.json como sensible")
        void testCredentialsJsonIsSensitive() {
            assertTrue(filter.isSensitive("credentials.json"));
            assertTrue(filter.isSensitive("config/credentials.json"));
        }

        @Test
        @DisplayName("Detecta secrets.yaml como sensible")
        void testSecretsYamlIsSensitive() {
            assertTrue(filter.isSensitive("secrets.yaml"));
            assertTrue(filter.isSensitive("secrets.yml"));
        }
    }

    @Nested
    @DisplayName("Patrones Sensibles")
    class SensitivePatternsTests {
        @Test
        @DisplayName("Detecta archivos con 'api_key' en el nombre")
        void testApiKeyPatternIsSensitive() {
            assertTrue(filter.isSensitive("api_key.txt"));
            assertTrue(filter.isSensitive("my_api_key_file.json"));
        }

        @Test
        @DisplayName("Detecta archivos con 'password' en el nombre")
        void testPasswordPatternIsSensitive() {
            assertTrue(filter.isSensitive("password.txt"));
            assertTrue(filter.isSensitive("user_password.json"));
        }

        @Test
        @DisplayName("Detecta archivos con 'secret_key' en el nombre")
        void testSecretKeyPatternIsSensitive() {
            assertTrue(filter.isSensitive("secret_key.txt"));
        }
    }

    @Nested
    @DisplayName("Directorios Sensibles")
    class SensitiveDirectoriesTests {
        @Test
        @DisplayName("Detecta archivos en .ssh como sensibles")
        void testSshDirectoryIsSensitive() {
            assertTrue(filter.isSensitive(".ssh/config"));
            assertTrue(filter.isSensitive(".ssh/id_rsa"));
        }

        @Test
        @DisplayName("Detecta archivos en .aws como sensibles")
        void testAwsDirectoryIsSensitive() {
            assertTrue(filter.isSensitive(".aws/credentials"));
            assertTrue(filter.isSensitive(".aws/config"));
        }

        @Test
        @DisplayName("Detecta archivos en .kube como sensibles")
        void testKubeDirectoryIsSensitive() {
            assertTrue(filter.isSensitive(".kube/config"));
        }
    }

    @Nested
    @DisplayName("Archivos No Sensibles")
    class NonSensitiveFilesTests {
        @Test
        @DisplayName("Archivos de codigo normales no son sensibles")
        void testCodeFilesAreNotSensitive() {
            assertFalse(filter.isSensitive("Main.java"));
            assertFalse(filter.isSensitive("app.py"));
            assertFalse(filter.isSensitive("index.js"));
            assertFalse(filter.isSensitive("src/utils.ts"));
        }

        @Test
        @DisplayName("Archivos de configuracion normales no son sensibles")
        void testConfigFilesAreNotSensitive() {
            assertFalse(filter.isSensitive("pom.xml"));
            assertFalse(filter.isSensitive("package.json"));
            assertFalse(filter.isSensitive("tsconfig.json"));
        }

        @Test
        @DisplayName("Archivos null o vacios retornan false")
        void testNullAndEmptyAreNotSensitive() {
            assertFalse(filter.isSensitive((String) null));
            assertFalse(filter.isSensitive(""));
            assertFalse(filter.isSensitive("   "));
        }
    }

    @Nested
    @DisplayName("API Adicional")
    class AdditionalApiTests {
        @Test
        @DisplayName("getSensitivityReason retorna razon para archivos sensibles")
        void testGetSensitivityReason() {
            String reason = filter.getSensitivityReason(".env");
            assertNotNull(reason, "getSensitivityReason(.env) deberia retornar razon");

            String pemReason = filter.getSensitivityReason("certificate.pem");
            assertNotNull(pemReason, "getSensitivityReason(certificate.pem) deberia retornar razon");
        }

        @Test
        @DisplayName("getSensitivityReason retorna null para archivos normales")
        void testGetSensitivityReasonReturnsNull() {
            assertNull(filter.getSensitivityReason("Main.java"));
        }

        @Test
        @DisplayName("isSensitive acepta Path")
        void testIsSensitiveWithPath() {
            Path pemPath = Path.of("keys/private.pem");
            assertTrue(filter.isSensitive(pemPath), "private.pem deberia ser sensible");

            Path javaPath = Path.of("src/Main.java");
            assertFalse(filter.isSensitive(javaPath), "Main.java no deberia ser sensible");
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {
        @Test
        @DisplayName("Builder permite agregar extensiones custom")
        void testBuilderAddExtension() {
            FileSecurityFilter custom = FileSecurityFilter.builder()
                .addExtension(".mysecret")
                .build();

            assertTrue(custom.isSensitive("config.mysecret"));
        }

        @Test
        @DisplayName("Builder permite agregar filenames custom")
        void testBuilderAddFilename() {
            FileSecurityFilter custom = FileSecurityFilter.builder()
                .addFilename("my-super-secret.txt")
                .build();

            assertTrue(custom.isSensitive("my-super-secret.txt"));
        }

        @Test
        @DisplayName("Builder permite agregar patrones custom")
        void testBuilderAddPattern() {
            FileSecurityFilter custom = FileSecurityFilter.builder()
                .addPattern("supersecret")
                .build();

            assertTrue(custom.isSensitive("my_supersecret_file.txt"));
        }
    }
}
