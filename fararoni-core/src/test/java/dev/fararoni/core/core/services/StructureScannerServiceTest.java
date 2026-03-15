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
package dev.fararoni.core.core.services;

import dev.fararoni.core.core.services.StructureScannerService.DirectoryScanResult;
import dev.fararoni.core.core.services.StructureScannerService.SkeletonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("StructureScannerService")
class StructureScannerServiceTest {
    private StructureScannerService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new StructureScannerService();
    }

    @Nested
    @DisplayName("Extensiones Soportadas")
    class SupportedExtensionsTests {
        @Test
        @DisplayName("soporta extensiones Java")
        void isSupported_Java_ReturnsTrue() {
            assertTrue(service.isSupported(".java"));
        }

        @Test
        @DisplayName("soporta extensiones Python")
        void isSupported_Python_ReturnsTrue() {
            assertTrue(service.isSupported(".py"));
        }

        @Test
        @DisplayName("soporta extensiones JavaScript/TypeScript")
        void isSupported_JsTs_ReturnsTrue() {
            assertTrue(service.isSupported(".js"));
            assertTrue(service.isSupported(".jsx"));
            assertTrue(service.isSupported(".ts"));
            assertTrue(service.isSupported(".tsx"));
        }

        @Test
        @DisplayName("soporta extensiones Go")
        void isSupported_Go_ReturnsTrue() {
            assertTrue(service.isSupported(".go"));
        }

        @Test
        @DisplayName("soporta extensiones Rust")
        void isSupported_Rust_ReturnsTrue() {
            assertTrue(service.isSupported(".rs"));
        }

        @Test
        @DisplayName("soporta extensiones Kotlin")
        void isSupported_Kotlin_ReturnsTrue() {
            assertTrue(service.isSupported(".kt"));
            assertTrue(service.isSupported(".kts"));
        }

        @Test
        @DisplayName("no soporta extensiones desconocidas")
        void isSupported_Unknown_ReturnsFalse() {
            assertFalse(service.isSupported(".xyz"));
            assertFalse(service.isSupported(".txt"));
            assertFalse(service.isSupported(".md"));
        }

        @Test
        @DisplayName("getSupportedExtensions retorna lista no vacia")
        void getSupportedExtensions_ReturnsNonEmpty() {
            List<String> extensions = service.getSupportedExtensions();
            assertNotNull(extensions);
            assertFalse(extensions.isEmpty());
            assertTrue(extensions.contains(".java"));
            assertTrue(extensions.contains(".py"));
        }
    }

    @Nested
    @DisplayName("Skeleton Java")
    class JavaSkeletonTests {
        @Test
        @DisplayName("extrae clase publica")
        void generateSkeleton_JavaClass_ExtractsClass() throws IOException {
            Path javaFile = tempDir.resolve("MyClass.java");
            Files.writeString(javaFile, """
                package com.example;

                public class MyClass {
                    private String name;
                }
                """);

            SkeletonResult result = service.generateSkeleton(javaFile);

            assertTrue(result.skeleton().contains("public class MyClass"));
            assertEquals("Java", result.language());
            assertTrue(result.signatureCount() >= 1);
        }

        @Test
        @DisplayName("extrae interface")
        void generateSkeleton_JavaInterface_ExtractsInterface() throws IOException {
            Path javaFile = tempDir.resolve("MyInterface.java");
            Files.writeString(javaFile, """
                public interface MyInterface {
                    void doSomething();
                }
                """);

            SkeletonResult result = service.generateSkeleton(javaFile);

            assertTrue(result.skeleton().contains("public interface MyInterface"));
        }

        @Test
        @DisplayName("extrae metodos publicos")
        void generateSkeleton_JavaMethods_ExtractsMethods() throws IOException {
            Path javaFile = tempDir.resolve("Service.java");
            Files.writeString(javaFile, """
                public class Service {
                    public void process(String data) {
                    }

                    protected String transform(int value) {
                        return String.valueOf(value);
                    }

                    private void internal() {
                    }
                }
                """);

            SkeletonResult result = service.generateSkeleton(javaFile);

            assertTrue(result.skeleton().contains("public class Service"));
            assertTrue(result.skeleton().contains("public void process"));
            assertTrue(result.skeleton().contains("protected String transform"));
        }

        @Test
        @DisplayName("extrae record")
        void generateSkeleton_JavaRecord_ExtractsRecord() throws IOException {
            Path javaFile = tempDir.resolve("MyRecord.java");
            Files.writeString(javaFile, """
                public record MyRecord(String name, int age) {
                }
                """);

            SkeletonResult result = service.generateSkeleton(javaFile);

            assertTrue(result.skeleton().contains("public record MyRecord"));
        }

        @Test
        @DisplayName("ignora comentarios de bloque")
        void generateSkeleton_JavaBlockComment_IgnoresComment() throws IOException {
            Path javaFile = tempDir.resolve("Commented.java");
            Files.writeString(javaFile, """
                public class RealClass {
                }
                """);

            SkeletonResult result = service.generateSkeleton(javaFile);

            assertTrue(result.skeleton().contains("public class RealClass"));
            assertFalse(result.skeleton().contains("FakeClass"));
        }

        @Test
        @DisplayName("ignora imports")
        void generateSkeleton_JavaImports_IgnoresImports() throws IOException {
            Path javaFile = tempDir.resolve("WithImports.java");
            Files.writeString(javaFile, """
                package com.example;

                import java.util.List;
                import java.util.Map;

                public class WithImports {
                }
                """);

            SkeletonResult result = service.generateSkeleton(javaFile);

            assertFalse(result.skeleton().contains("import"));
            assertFalse(result.skeleton().contains("package"));
        }
    }

    @Nested
    @DisplayName("Skeleton Python")
    class PythonSkeletonTests {
        @Test
        @DisplayName("extrae clase Python")
        void generateSkeleton_PythonClass_ExtractsClass() throws IOException {
            Path pyFile = tempDir.resolve("my_module.py");
            Files.writeString(pyFile, """
                class MyClass:
                    def __init__(self):
                        pass
                """);

            SkeletonResult result = service.generateSkeleton(pyFile);

            assertTrue(result.skeleton().contains("class MyClass"));
            assertEquals("Python", result.language());
        }

        @Test
        @DisplayName("extrae funciones Python")
        void generateSkeleton_PythonFunctions_ExtractsFunctions() throws IOException {
            Path pyFile = tempDir.resolve("utils.py");
            Files.writeString(pyFile, """
                def process_data(data):
                    return data

                async def fetch_data(url):
                    return await get(url)
                """);

            SkeletonResult result = service.generateSkeleton(pyFile);

            assertTrue(result.skeleton().contains("def process_data"));
            assertTrue(result.skeleton().contains("async def fetch_data"));
        }

        @Test
        @DisplayName("ignora comentarios Python")
        void generateSkeleton_PythonComments_IgnoresComments() throws IOException {
            Path pyFile = tempDir.resolve("commented.py");
            Files.writeString(pyFile, """
                # This is a comment
                # def fake_function():

                def real_function():
                    pass
                """);

            SkeletonResult result = service.generateSkeleton(pyFile);

            assertTrue(result.skeleton().contains("def real_function"));
            assertFalse(result.skeleton().contains("fake_function"));
        }
    }

    @Nested
    @DisplayName("Skeleton JavaScript/TypeScript")
    class JsTsSkeletonTests {
        @Test
        @DisplayName("extrae clase JS")
        void generateSkeleton_JsClass_ExtractsClass() throws IOException {
            Path jsFile = tempDir.resolve("component.js");
            Files.writeString(jsFile, """
                export class MyComponent {
                    constructor() {}
                }
                """);

            SkeletonResult result = service.generateSkeleton(jsFile);

            assertTrue(result.skeleton().contains("export class MyComponent"));
            assertEquals("JavaScript/TypeScript", result.language());
        }

        @Test
        @DisplayName("extrae funciones JS")
        void generateSkeleton_JsFunctions_ExtractsFunctions() throws IOException {
            Path jsFile = tempDir.resolve("utils.js");
            Files.writeString(jsFile, """
                export function processData(data) {
                    return data;
                }

                export async function fetchData(url) {
                    return await fetch(url);
                }
                """);

            SkeletonResult result = service.generateSkeleton(jsFile);

            assertTrue(result.skeleton().contains("export function processData"));
            assertTrue(result.skeleton().contains("export async function fetchData"));
        }

        @Test
        @DisplayName("extrae arrow functions")
        void generateSkeleton_JsArrowFunctions_ExtractsArrowFunctions() throws IOException {
            Path jsFile = tempDir.resolve("arrows.js");
            Files.writeString(jsFile, """
                export const handler = (event) => {
                    return event.data;
                };

                const internalFn = async (data) => {
                    return data;
                };
                """);

            SkeletonResult result = service.generateSkeleton(jsFile);

            assertTrue(result.skeleton().contains("export const handler"));
        }

        @Test
        @DisplayName("extrae interface TypeScript")
        void generateSkeleton_TsInterface_ExtractsInterface() throws IOException {
            Path tsFile = tempDir.resolve("types.ts");
            Files.writeString(tsFile, """
                export interface User {
                    id: number;
                    name: string;
                }

                export type Config = {
                    debug: boolean;
                };
                """);

            SkeletonResult result = service.generateSkeleton(tsFile);

            assertTrue(result.skeleton().contains("export interface User"));
        }
    }

    @Nested
    @DisplayName("Skeleton Go")
    class GoSkeletonTests {
        @Test
        @DisplayName("extrae funciones Go")
        void generateSkeleton_GoFunc_ExtractsFunc() throws IOException {
            Path goFile = tempDir.resolve("main.go");
            Files.writeString(goFile, """
                package main

                func ProcessData(data string) string {
                    return data
                }

                func (s *Server) Start() error {
                    return nil
                }
                """);

            SkeletonResult result = service.generateSkeleton(goFile);

            assertTrue(result.skeleton().contains("func ProcessData"));
            assertTrue(result.skeleton().contains("func (s *Server) Start"));
            assertEquals("Go", result.language());
        }

        @Test
        @DisplayName("extrae structs Go")
        void generateSkeleton_GoStruct_ExtractsStruct() throws IOException {
            Path goFile = tempDir.resolve("types.go");
            Files.writeString(goFile, """
                package types

                type User struct {
                    ID   int
                    Name string
                }

                type Handler interface {
                    Handle() error
                }
                """);

            SkeletonResult result = service.generateSkeleton(goFile);

            assertTrue(result.skeleton().contains("type User struct"));
            assertTrue(result.skeleton().contains("type Handler interface"));
        }
    }

    @Nested
    @DisplayName("Skeleton Rust")
    class RustSkeletonTests {
        @Test
        @DisplayName("extrae funciones Rust")
        void generateSkeleton_RustFn_ExtractsFn() throws IOException {
            Path rsFile = tempDir.resolve("lib.rs");
            Files.writeString(rsFile, """
                pub fn process_data(data: &str) -> String {
                    data.to_string()
                }

                fn internal_fn() {
                }

                pub async fn fetch_data(url: &str) -> Result<String, Error> {
                    Ok(String::new())
                }
                """);

            SkeletonResult result = service.generateSkeleton(rsFile);

            assertTrue(result.skeleton().contains("pub fn process_data"));
            assertTrue(result.skeleton().contains("fn internal_fn"));
            assertTrue(result.skeleton().contains("pub async fn fetch_data"));
            assertEquals("Rust", result.language());
        }

        @Test
        @DisplayName("extrae structs y traits Rust")
        void generateSkeleton_RustStructTrait_ExtractsStructTrait() throws IOException {
            Path rsFile = tempDir.resolve("types.rs");
            Files.writeString(rsFile, """
                pub struct User {
                    id: u32,
                    name: String,
                }

                pub trait Handler {
                    fn handle(&self) -> Result<(), Error>;
                }

                impl Handler for User {
                    fn handle(&self) -> Result<(), Error> {
                        Ok(())
                    }
                }
                """);

            SkeletonResult result = service.generateSkeleton(rsFile);

            assertTrue(result.skeleton().contains("pub struct User"));
            assertTrue(result.skeleton().contains("pub trait Handler"));
            assertTrue(result.skeleton().contains("impl Handler for User"));
        }
    }

    @Nested
    @DisplayName("Scan de Directorio")
    class DirectoryScanTests {
        @Test
        @DisplayName("escanea directorio con multiples archivos")
        void scanDirectory_MultipleFiles_ScansAll() throws IOException {
            Files.writeString(tempDir.resolve("Main.java"), """
                public class Main {
                    public static void main(String[] args) {}
                }
                """);

            Files.writeString(tempDir.resolve("utils.py"), """
                def helper():
                    pass
                """);

            DirectoryScanResult result = service.scanDirectory(tempDir);

            assertEquals(2, result.fileCount());
            assertTrue(result.totalSignatures() >= 2);
            assertFalse(result.truncated());
        }

        @Test
        @DisplayName("ignora directorios de build")
        void scanDirectory_IgnoresBuildDirs_SkipsTarget() throws IOException {
            Path targetDir = tempDir.resolve("target");
            Files.createDirectories(targetDir);
            Files.writeString(targetDir.resolve("Generated.java"), """
                public class Generated {}
                """);

            Files.writeString(tempDir.resolve("Source.java"), """
                public class Source {}
                """);

            DirectoryScanResult result = service.scanDirectory(tempDir);

            assertEquals(1, result.fileCount());
            assertTrue(result.results().stream()
                .anyMatch(r -> r.skeleton().contains("Source")));
            assertFalse(result.results().stream()
                .anyMatch(r -> r.skeleton().contains("Generated")));
        }

        @Test
        @DisplayName("respeta limite de profundidad")
        void scanDirectory_MaxDepth_RespectsLimit() throws IOException {
            Path level1 = tempDir.resolve("l1");
            Path level2 = level1.resolve("l2");
            Path level3 = level2.resolve("l3");
            Path level4 = level3.resolve("l4");
            Path level5 = level4.resolve("l5");
            Path level6 = level5.resolve("l6");

            Files.createDirectories(level6);
            Files.writeString(level1.resolve("L1.java"), "public class L1 {}");
            Files.writeString(level6.resolve("L6.java"), "public class L6 {}");

            DirectoryScanResult result = service.scanDirectory(tempDir, 5, 50000);

            assertTrue(result.results().stream()
                .anyMatch(r -> r.skeleton().contains("L1")));
        }

        @Test
        @DisplayName("formatForContext incluye header")
        void formatForContext_IncludesHeader() throws IOException {
            Files.writeString(tempDir.resolve("Test.java"), """
                public class Test {}
                """);

            DirectoryScanResult result = service.scanDirectory(tempDir);
            String formatted = service.formatForContext(result);

            assertTrue(formatted.contains(">>> SKELETON MAP:"));
            assertTrue(formatted.contains("Files scanned:"));
            assertTrue(formatted.contains("Total chars:"));
        }

        @Test
        @DisplayName("directorio vacio retorna resultado vacio")
        void scanDirectory_EmptyDir_ReturnsEmpty() throws IOException {
            DirectoryScanResult result = service.scanDirectory(tempDir);

            assertEquals(0, result.fileCount());
            assertEquals(0, result.totalSignatures());
            assertFalse(result.truncated());
        }
    }

    @Nested
    @DisplayName("Lenguaje No Soportado")
    class UnsupportedLanguageTests {
        @Test
        @DisplayName("retorna mensaje para extension desconocida")
        void generateSkeleton_UnknownExtension_ReturnsMessage() throws IOException {
            Path unknownFile = tempDir.resolve("data.xyz");
            Files.writeString(unknownFile, "some content");

            SkeletonResult result = service.generateSkeleton(unknownFile);

            assertTrue(result.skeleton().contains("Lenguaje no soportado"));
            assertEquals("unknown", result.language());
            assertEquals(0, result.signatureCount());
        }
    }

    @Nested
    @DisplayName("Validacion de Input")
    class ValidationTests {
        @Test
        @DisplayName("lanza NullPointerException si path es null")
        void generateSkeleton_NullPath_ThrowsNPE() {
            assertThrows(NullPointerException.class, () -> {
                service.generateSkeleton(null);
            });
        }

        @Test
        @DisplayName("lanza NullPointerException si rootPath es null en scanDirectory")
        void scanDirectory_NullPath_ThrowsNPE() {
            assertThrows(NullPointerException.class, () -> {
                service.scanDirectory(null);
            });
        }
    }
}
