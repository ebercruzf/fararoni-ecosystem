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
package dev.fararoni.core.core.indexing;

import dev.fararoni.core.core.indexing.model.SemanticUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("MultiLanguageSurgicalParser Tests - Tree-sitter Poliglota")
class MultiLanguageSurgicalParserTest {
    private MultiLanguageSurgicalParser parser;

    @BeforeEach
    void setUp() {
        parser = new MultiLanguageSurgicalParser();
    }

    @Nested
    @DisplayName("Python (.py)")
    class PythonTests {
        @Test
        @DisplayName("Extrae funciones Python")
        void testExtractPythonFunctions() {
            String code = """
                def hello(name):
                    print(f"Hello, {name}")

                def goodbye(name):
                    print(f"Goodbye, {name}")
                """;

            List<SemanticUnit> units = parser.parse(code, "py");

            long methodCount = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .count();

            assertEquals(2, methodCount);
            assertTrue(units.stream().anyMatch(u -> u.signature().contains("hello")));
            assertTrue(units.stream().anyMatch(u -> u.signature().contains("goodbye")));
        }

        @Test
        @DisplayName("Extrae funciones async Python")
        void testExtractAsyncPythonFunctions() {
            String code = """
                async def fetch_data(url):
                    response = await aiohttp.get(url)
                    return response.json()
                """;

            List<SemanticUnit> units = parser.parse(code, "py");

            assertTrue(units.stream().anyMatch(u ->
                u.signature().contains("async") && u.signature().contains("fetch_data")));
        }

        @Test
        @DisplayName("Extrae clases Python")
        void testExtractPythonClasses() {
            String code = """
                class Person:
                    def __init__(self, name):
                        self.name = name

                    def greet(self):
                        print(f"Hello, I'm {self.name}")
                """;

            List<SemanticUnit> units = parser.parse(code, "py");

            long classCount = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_CLASS))
                .count();

            assertEquals(1, classCount);
            assertTrue(units.stream().anyMatch(u -> u.signature().contains("Person")));
        }

        @Test
        @DisplayName("Paso 8: Extrae type hints Python")
        void testExtractPythonTypeHints() {
            String code = """
                def calculate(x: int, y: int) -> int:
                    return x + y
                """;

            List<SemanticUnit> units = parser.parse(code, "py");

            Optional<SemanticUnit> method = units.stream()
                .filter(u -> u.signature().contains("calculate"))
                .findFirst();

            assertTrue(method.isPresent());
            assertTrue(method.get().content().contains("-> int"));
        }
    }

    @Nested
    @DisplayName("JavaScript (.js)")
    class JavaScriptTests {
        @Test
        @DisplayName("Extrae funciones JavaScript")
        void testExtractJsFunctions() {
            String code = """
                function add(a, b) {
                    return a + b;
                }

                function subtract(a, b) {
                    return a - b;
                }
                """;

            List<SemanticUnit> units = parser.parse(code, "js");

            long methodCount = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .count();

            assertEquals(2, methodCount);
        }

        @Test
        @DisplayName("Extrae arrow functions JavaScript")
        void testExtractArrowFunctions() {
            String code = """
                const multiply = (a, b) => {
                    return a * b;
                };

                const greet = name => `Hello, ${name}`;
                """;

            List<SemanticUnit> units = parser.parse(code, "js");

            assertTrue(units.stream().anyMatch(u -> u.type().equals(SemanticUnit.TYPE_METHOD)));
        }

        @Test
        @DisplayName("Extrae clases JavaScript")
        void testExtractJsClasses() {
            String code = """
                class Calculator {
                    constructor(value) {
                        this.value = value;
                    }

                    add(n) {
                        return this.value + n;
                    }
                }
                """;

            List<SemanticUnit> units = parser.parse(code, "js");

            assertTrue(units.stream().anyMatch(u ->
                u.type().equals(SemanticUnit.TYPE_CLASS) && u.signature().contains("Calculator")));
        }
    }

    @Nested
    @DisplayName("TypeScript (.ts)")
    class TypeScriptTests {
        @Test
        @DisplayName("Extrae interfaces TypeScript")
        void testExtractTsInterfaces() {
            String code = """
                interface User {
                    id: number;
                    name: string;
                    email: string;
                }
                """;

            List<SemanticUnit> units = parser.parse(code, "ts");

            assertTrue(units.stream().anyMatch(u ->
                u.signature().contains("interface") && u.signature().contains("User")));
        }

        @Test
        @DisplayName("Extrae type aliases TypeScript")
        void testExtractTsTypeAliases() {
            String code = """
                type Status = 'pending' | 'active' | 'completed';

                type UserWithStatus = User & { status: Status };
                """;

            List<SemanticUnit> units = parser.parse(code, "ts");

            assertTrue(units.stream().anyMatch(u -> u.signature().contains("Status")));
        }

        @Test
        @DisplayName("Extrae enums TypeScript")
        void testExtractTsEnums() {
            String code = """
                enum Color {
                    Red = 'red',
                    Green = 'green',
                    Blue = 'blue'
                }
                """;

            List<SemanticUnit> units = parser.parse(code, "ts");

            assertTrue(units.stream().anyMatch(u ->
                u.signature().contains("enum") && u.signature().contains("Color")));
        }

        @Test
        @DisplayName("Paso 5: Extrae funciones de archivo TSX")
        void testExtractTsxFunctions() {
            String code = """
                function MyComponent({ name }: { name: string }) {
                    return <div>Hello, {name}</div>;
                }

                const App = () => {
                    return <MyComponent name="World" />;
                };
                """;

            List<SemanticUnit> units = parser.parse(code, "tsx");

            assertTrue(units.stream().anyMatch(u -> u.signature().contains("MyComponent")));
        }
    }

    @Nested
    @DisplayName("Go (.go)")
    class GoTests {
        @Test
        @DisplayName("Extrae funciones Go")
        void testExtractGoFunctions() {
            String code = """
                func add(a, b int) int {
                    return a + b
                }

                func subtract(a, b int) int {
                    return a - b
                }
                """;

            List<SemanticUnit> units = parser.parse(code, "go");

            long methodCount = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .count();

            assertEquals(2, methodCount);
        }

        @Test
        @DisplayName("Extrae structs Go")
        void testExtractGoStructs() {
            String code = """
                type Person struct {
                    Name string
                    Age  int
                }
                """;

            List<SemanticUnit> units = parser.parse(code, "go");

            assertTrue(units.stream().anyMatch(u ->
                u.type().equals(SemanticUnit.TYPE_CLASS) && u.signature().contains("Person")));
        }
    }

    @Nested
    @DisplayName("Rust (.rs)")
    class RustTests {
        @Test
        @DisplayName("Extrae funciones Rust")
        void testExtractRustFunctions() {
            String code = """
                fn add(a: i32, b: i32) -> i32 {
                    a + b
                }

                fn multiply(a: i32, b: i32) -> i32 {
                    a * b
                }
                """;

            List<SemanticUnit> units = parser.parse(code, "rs");

            long methodCount = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .count();

            assertEquals(2, methodCount);
        }

        @Test
        @DisplayName("Extrae structs Rust")
        void testExtractRustStructs() {
            String code = """
                struct Point {
                    x: f64,
                    y: f64,
                }
                """;

            List<SemanticUnit> units = parser.parse(code, "rs");

            assertTrue(units.stream().anyMatch(u ->
                u.signature().contains("struct") && u.signature().contains("Point")));
        }

        @Test
        @DisplayName("Extrae enums Rust")
        void testExtractRustEnums() {
            String code = """
                enum Message {
                    Quit,
                    Move { x: i32, y: i32 },
                    Write(String),
                }
                """;

            List<SemanticUnit> units = parser.parse(code, "rs");

            assertTrue(units.stream().anyMatch(u ->
                u.signature().contains("enum") && u.signature().contains("Message")));
        }
    }

    @Nested
    @DisplayName("Ruby (.rb)")
    class RubyTests {
        @Test
        @DisplayName("Extrae metodos Ruby")
        void testExtractRubyMethods() {
            String code = """
                def greet(name)
                  puts "Hello, #{name}"
                end

                def farewell(name)
                  puts "Goodbye, #{name}"
                end
                """;

            List<SemanticUnit> units = parser.parse(code, "rb");

            long methodCount = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .count();

            assertEquals(2, methodCount);
        }

        @Test
        @DisplayName("Extrae clases Ruby")
        void testExtractRubyClasses() {
            String code = """
                class Person
                  def initialize(name)
                    @name = name
                  end

                  def greet
                    puts "Hello, I'm #{@name}"
                  end
                end
                """;

            List<SemanticUnit> units = parser.parse(code, "rb");

            assertTrue(units.stream().anyMatch(u ->
                u.type().equals(SemanticUnit.TYPE_CLASS) && u.signature().contains("Person")));
        }

        @Test
        @DisplayName("Extrae modulos Ruby")
        void testExtractRubyModules() {
            String code = """
                module Greetable
                  def greet
                    puts "Hello!"
                  end
                end
                """;

            List<SemanticUnit> units = parser.parse(code, "rb");

            assertTrue(units.stream().anyMatch(u ->
                u.signature().contains("module") && u.signature().contains("Greetable")));
        }
    }

    @Nested
    @DisplayName("Soporte y Diagnostico")
    class SupportTests {
        @Test
        @DisplayName("isSupported retorna true para lenguajes soportados")
        void testIsSupported() {
            assertTrue(parser.isSupported("py"));
            assertTrue(parser.isSupported("js"));
            assertTrue(parser.isSupported("ts"));
            assertTrue(parser.isSupported("tsx"));
            assertTrue(parser.isSupported("jsx"));
            assertTrue(parser.isSupported("go"));
            assertTrue(parser.isSupported("rs"));
            assertTrue(parser.isSupported("rb"));
        }

        @Test
        @DisplayName("isSupported retorna false para lenguajes no soportados")
        void testIsNotSupported() {
            assertFalse(parser.isSupported("java"));
            assertFalse(parser.isSupported("cpp"));
            assertFalse(parser.isSupported("unknown"));
        }

        @Test
        @DisplayName("getSupportedExtensions retorna todas las extensiones")
        void testGetSupportedExtensions() {
            var extensions = parser.getSupportedExtensions();

            assertTrue(extensions.contains("py"));
            assertTrue(extensions.contains("js"));
            assertTrue(extensions.contains("ts"));
            assertTrue(extensions.contains("go"));
            assertTrue(extensions.contains("rs"));
            assertTrue(extensions.contains("rb"));
        }

        @Test
        @DisplayName("parse lanza excepcion para lenguaje no soportado")
        void testParseUnsupportedThrows() {
            assertThrows(UnsupportedOperationException.class, () -> {
                parser.parse("public class Test {}", "java");
            });
        }

        @Test
        @DisplayName("parse retorna lista vacia para contenido null")
        void testParseNullContent() {
            List<SemanticUnit> units = parser.parse(null, "py");
            assertTrue(units.isEmpty());
        }

        @Test
        @DisplayName("parse retorna lista vacia para contenido vacio")
        void testParseEmptyContent() {
            List<SemanticUnit> units = parser.parse("", "py");
            assertTrue(units.isEmpty());
        }

        @Test
        @DisplayName("diagnose retorna informacion correcta")
        void testDiagnose() {
            String code = """
                def hello():
                    pass

                class World:
                    pass
                """;

            var diagnostic = parser.diagnose(code, "py");

            assertTrue(diagnostic.success());
            assertEquals("py", diagnostic.extension());
            assertTrue(diagnostic.totalUnits() >= 2);
            assertTrue(diagnostic.methods() >= 1);
            assertTrue(diagnostic.classes() >= 1);
            assertNull(diagnostic.errorMessage());
        }
    }

    @Nested
    @DisplayName("Paso 10: Unificacion con SemanticUnit")
    class UnificationTests {
        @Test
        @DisplayName("SemanticUnit tiene todos los campos requeridos")
        void testSemanticUnitFields() {
            String code = """
                def example():
                    pass
                """;

            List<SemanticUnit> units = parser.parse(code, "py");
            SemanticUnit unit = units.get(0);

            assertNotNull(unit.type());
            assertNotNull(unit.signature());
            assertNotNull(unit.content());
            assertTrue(unit.startLine() > 0);
            assertTrue(unit.endLine() >= unit.startLine());
            assertNotNull(unit.usedFields());
        }

        @Test
        @DisplayName("SemanticUnit calcula tokenEstimate correctamente")
        void testTokenEstimate() {
            String code = """
                def long_function():
                    # This is a longer function with more content
                    x = 1
                    y = 2
                    z = x + y
                    return z
                """;

            List<SemanticUnit> units = parser.parse(code, "py");
            SemanticUnit unit = units.get(0);

            assertTrue(unit.tokenEstimate() > 0);
            assertEquals(unit.content().length() / 4, unit.tokenEstimate());
        }
    }
}
