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

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import dev.fararoni.core.core.indexing.model.SemanticUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SentinelVisitor Tests - 10 Pasos Documento 02.1")
class SentinelVisitorTest {
    private SentinelVisitor visitor;

    @BeforeEach
    void setUp() {
        visitor = new SentinelVisitor();
    }

    @Nested
    @DisplayName("Paso 1: Desacoplamiento Algoritmico")
    class DesacoplamientoTests {
        @Test
        @DisplayName("Visitor es clase separada de SentinelJavaParser")
        void testVisitorIsSeparateClass() {
            assertNotNull(visitor);
            assertTrue(visitor instanceof com.github.javaparser.ast.visitor.VoidVisitorAdapter);
        }
    }

    @Nested
    @DisplayName("Paso 2: Arboles Profundos (super.visit primero)")
    class ArbolesProfundosTests {
        @Test
        @DisplayName("Detecta metodos en clases anidadas")
        void testNestedClassMethods() {
            String code = """
                public class Outer {
                    public void outerMethod() {}

                    public class Inner {
                        public void innerMethod() {}

                        public class DeepInner {
                            public void deepMethod() {}
                        }
                    }
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);
            List<SemanticUnit> units = new ArrayList<>();
            cu.accept(visitor, units);

            List<SemanticUnit> methods = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .toList();

            assertEquals(3, methods.size());
            assertTrue(methods.stream().anyMatch(m -> m.signature().contains("outerMethod")));
            assertTrue(methods.stream().anyMatch(m -> m.signature().contains("innerMethod")));
            assertTrue(methods.stream().anyMatch(m -> m.signature().contains("deepMethod")));
        }

        @Test
        @DisplayName("Fully Qualified Signature para clases anidadas")
        void testFullyQualifiedSignature() {
            String code = """
                public class Outer {
                    public class Inner {
                        public void innerMethod() {}
                    }
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);
            List<SemanticUnit> units = new ArrayList<>();
            cu.accept(visitor, units);

            Optional<SemanticUnit> inner = units.stream()
                .filter(u -> u.signature().contains("innerMethod"))
                .findFirst();

            assertTrue(inner.isPresent());
            assertTrue(inner.get().signature().contains("Outer$Inner"));
        }
    }

    @Nested
    @DisplayName("Paso 4: Thread Safety (collector como arg)")
    class ThreadSafetyTests {
        @Test
        @DisplayName("Collector como parametro permite uso concurrente")
        void testCollectorAsParameter() {
            String code = """
                public class Example {
                    public void method1() {}
                    public void method2() {}
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);

            List<SemanticUnit> collector1 = new ArrayList<>();
            List<SemanticUnit> collector2 = new ArrayList<>();

            visitor.reset();
            cu.accept(visitor, collector1);

            visitor.reset();
            cu.accept(visitor, collector2);

            assertEquals(collector1.size(), collector2.size());
            assertNotSame(collector1, collector2);
        }
    }

    @Nested
    @DisplayName("Paso 5: Granularidad (detectar this.field)")
    class GranularidadTests {
        @Test
        @DisplayName("Detecta dependencias de campos con this.field")
        void testThisFieldDependencies() {
            String code = """
                public class Example {
                    private int counter;
                    private String name;

                    public void increment() {
                        this.counter++;
                    }

                    public String getName() {
                        return this.name;
                    }
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);
            List<SemanticUnit> units = new ArrayList<>();
            cu.accept(visitor, units);

            Optional<SemanticUnit> increment = units.stream()
                .filter(u -> u.signature().contains("increment"))
                .findFirst();

            Optional<SemanticUnit> getName = units.stream()
                .filter(u -> u.signature().contains("getName"))
                .findFirst();

            assertTrue(increment.isPresent());
            assertTrue(getName.isPresent());
            assertTrue(increment.get().usedFields().contains("counter"));
            assertTrue(getName.get().usedFields().contains("name"));
        }

        @Test
        @DisplayName("Detecta dependencias de campos sin this explicito")
        void testImplicitFieldDependencies() {
            String code = """
                public class Example {
                    private int value;

                    public int getValue() {
                        return value;
                    }
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);
            List<SemanticUnit> units = new ArrayList<>();
            cu.accept(visitor, units);

            Optional<SemanticUnit> getValue = units.stream()
                .filter(u -> u.signature().contains("getValue"))
                .findFirst();

            assertTrue(getValue.isPresent());
            assertTrue(getValue.get().usedFields().contains("value"));
        }
    }

    @Nested
    @DisplayName("Paso 7: Codigo Roto (null checks)")
    class CodigoRotoTests {
        @Test
        @DisplayName("Null collector no lanza excepcion")
        void testNullCollectorHandled() {
            String code = "public class Example { public void test() {} }";
            CompilationUnit cu = StaticJavaParser.parse(code);

            assertDoesNotThrow(() -> cu.accept(visitor, null));
        }
    }

    @Nested
    @DisplayName("Paso 9: Memoria (solo Strings)")
    class MemoriaTests {
        @Test
        @DisplayName("SemanticUnit contiene solo Strings, no nodos AST")
        void testSemanticUnitContainsOnlyStrings() {
            String code = """
                public class Example {
                    private int field;
                    public void method() {
                        int local = field;
                    }
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);
            List<SemanticUnit> units = new ArrayList<>();
            cu.accept(visitor, units);

            for (SemanticUnit unit : units) {
                assertNotNull(unit.type());
                assertTrue(unit.type() instanceof String);
                assertNotNull(unit.signature());
                assertTrue(unit.signature() instanceof String);
                assertNotNull(unit.content());
                assertTrue(unit.content() instanceof String);
            }
        }
    }

    @Nested
    @DisplayName("Tipos de Unidades Soportadas")
    class TiposUnidadesTests {
        @Test
        @DisplayName("Extrae metodos")
        void testExtractMethods() {
            String code = """
                public class Example {
                    public void method1() {}
                    private int method2(String arg) { return 0; }
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);
            List<SemanticUnit> units = new ArrayList<>();
            cu.accept(visitor, units);

            long methodCount = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .count();

            assertEquals(2, methodCount);
        }

        @Test
        @DisplayName("Extrae campos")
        void testExtractFields() {
            String code = """
                public class Example {
                    private int counter;
                    public String name;
                    private static final int MAX = 100;
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);
            List<SemanticUnit> units = new ArrayList<>();
            cu.accept(visitor, units);

            long fieldCount = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_FIELD))
                .count();

            assertEquals(3, fieldCount);
        }

        @Test
        @DisplayName("Extrae clases e interfaces")
        void testExtractClasses() {
            String code = """
                public class Example {
                    public interface Inner {}
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);
            List<SemanticUnit> units = new ArrayList<>();
            cu.accept(visitor, units);

            long classCount = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_CLASS))
                .count();

            assertEquals(2, classCount);
        }

        @Test
        @DisplayName("Extrae constructores")
        void testExtractConstructors() {
            String code = """
                public class Example {
                    public Example() {}
                    public Example(int value) {}
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);
            List<SemanticUnit> units = new ArrayList<>();
            cu.accept(visitor, units);

            long constructorCount = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_CONSTRUCTOR))
                .count();

            assertEquals(2, constructorCount);
        }

        @Test
        @DisplayName("Extrae lambdas significativas (>3 lineas)")
        void testExtractSignificantLambdas() {
            String code = """
                public class Example {
                    public void process() {
                        list.forEach(item -> {
                            process(item);
                        });
                    }
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);
            List<SemanticUnit> units = new ArrayList<>();
            cu.accept(visitor, units);

            long lambdaCount = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_LAMBDA))
                .count();

            assertEquals(1, lambdaCount);
        }

        @Test
        @DisplayName("Ignora lambdas pequenas (<3 lineas)")
        void testIgnoreSmallLambdas() {
            String code = """
                public class Example {
                    public void process() {
                        list.forEach(x -> process(x));
                    }
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);
            List<SemanticUnit> units = new ArrayList<>();
            cu.accept(visitor, units);

            long lambdaCount = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_LAMBDA))
                .count();

            assertEquals(0, lambdaCount);
        }
    }

    @Nested
    @DisplayName("Deteccion de @Override")
    class OverrideTests {
        @Test
        @DisplayName("Detecta metodos con @Override")
        void testDetectOverrideAnnotation() {
            String code = """
                public class Example {
                    @Override
                    public String toString() {
                        return "example";
                    }

                    public void normalMethod() {}
                }
                """;

            CompilationUnit cu = StaticJavaParser.parse(code);
            List<SemanticUnit> units = new ArrayList<>();
            cu.accept(visitor, units);

            Optional<SemanticUnit> toStringMethod = units.stream()
                .filter(u -> u.signature().contains("toString"))
                .findFirst();

            Optional<SemanticUnit> normalMethod = units.stream()
                .filter(u -> u.signature().contains("normalMethod"))
                .findFirst();

            assertTrue(toStringMethod.isPresent());
            assertTrue(normalMethod.isPresent());
            assertTrue(toStringMethod.get().isOverride());
            assertFalse(normalMethod.get().isOverride());
        }
    }

    @Nested
    @DisplayName("Utilidad: Reset")
    class ResetTests {
        @Test
        @DisplayName("Reset limpia estado interno")
        void testResetClearsState() {
            String code1 = """
                public class Class1 {
                    private int field1;
                    public void method1() { int x = field1; }
                }
                """;

            String code2 = """
                public class Class2 {
                    private String field2;
                    public void method2() { String s = field2; }
                }
                """;

            CompilationUnit cu1 = StaticJavaParser.parse(code1);
            CompilationUnit cu2 = StaticJavaParser.parse(code2);

            List<SemanticUnit> units1 = new ArrayList<>();
            cu1.accept(visitor, units1);

            visitor.reset();

            List<SemanticUnit> units2 = new ArrayList<>();
            cu2.accept(visitor, units2);

            Optional<SemanticUnit> method2 = units2.stream()
                .filter(u -> u.signature().contains("method2"))
                .findFirst();

            assertTrue(method2.isPresent());
            assertTrue(method2.get().usedFields().contains("field2"));
            assertFalse(method2.get().usedFields().contains("field1"));
        }
    }
}
