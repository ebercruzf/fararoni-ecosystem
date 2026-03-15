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

import com.github.javaparser.ParserConfiguration;
import dev.fararoni.core.core.indexing.model.SemanticUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SentinelJavaParser - El Ojo v2.0")
class SentinelJavaParserTest {
    private SentinelJavaParser parser;

    @BeforeEach
    void setUp() {
        parser = new SentinelJavaParser();
    }

    @Nested
    @DisplayName("Extraccion de Unidades Semanticas")
    class SemanticUnitExtraction {
        @Test
        @DisplayName("Debe extraer clase, campo y metodo de codigo simple")
        void shouldExtractClassFieldAndMethod() {
            String code = """
                public class Calculator {
                    private int value;

                    public int add(int x) {
                        return value + x;
                    }
                }
                """;

            List<SemanticUnit> units = parser.parse(code);

            assertThat(units).isNotEmpty();
            assertThat(units).anyMatch(u -> u.type().equals(SemanticUnit.TYPE_CLASS));
            assertThat(units).anyMatch(u -> u.type().equals(SemanticUnit.TYPE_FIELD));
            assertThat(units).anyMatch(u -> u.type().equals(SemanticUnit.TYPE_METHOD));
        }

        @Test
        @DisplayName("Debe extraer imports")
        void shouldExtractImports() {
            String code = """
                import java.util.List;
                import java.util.Map;

                public class Service {
                }
                """;

            List<SemanticUnit> units = parser.parse(code);

            assertThat(units)
                .anyMatch(u -> u.type().equals(SemanticUnit.TYPE_IMPORT));
        }

        @Test
        @DisplayName("Debe extraer constructor")
        void shouldExtractConstructor() {
            String code = """
                public class Service {
                    private final String name;

                    public Service(String name) {
                        this.name = name;
                    }
                }
                """;

            List<SemanticUnit> units = parser.parse(code);

            assertThat(units)
                .anyMatch(u -> u.type().equals(SemanticUnit.TYPE_CONSTRUCTOR));
        }
    }

    @Nested
    @DisplayName("Precaucion 5: Deteccion de Dependencias")
    class DependencyDetection {
        @Test
        @DisplayName("Debe detectar dependencias this.field")
        void shouldDetectThisFieldDependencies() {
            String code = """
                public class Calculator {
                    private int accumulator;

                    public void add(int x) {
                        this.accumulator += x;
                    }
                }
                """;

            List<SemanticUnit> units = parser.parse(code);

            SemanticUnit method = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .findFirst()
                .orElseThrow();

            assertThat(method.usedFields()).contains("accumulator");
        }

        @Test
        @DisplayName("Debe detectar dependencias sin this. explicito")
        void shouldDetectImplicitFieldDependencies() {
            String code = """
                public class Calculator {
                    private int value;

                    public int getValue() {
                        return value;
                    }
                }
                """;

            List<SemanticUnit> units = parser.parse(code);

            SemanticUnit method = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .findFirst()
                .orElseThrow();

            assertThat(method.usedFields()).contains("value");
        }
    }

    @Nested
    @DisplayName("Precaucion 7: Fail-Safe")
    class FailSafe {
        @Test
        @DisplayName("Debe retornar lista vacia para codigo null")
        void shouldReturnEmptyForNull() {
            List<SemanticUnit> units = parser.parse((String) null);
            assertThat(units).isEmpty();
        }

        @Test
        @DisplayName("Debe retornar lista vacia para codigo en blanco")
        void shouldReturnEmptyForBlank() {
            List<SemanticUnit> units = parser.parse("   ");
            assertThat(units).isEmpty();
        }

        @Test
        @DisplayName("Debe retornar lista vacia para codigo con sintaxis invalida")
        void shouldReturnEmptyForInvalidSyntax() {
            String invalidCode = "public class { broken syntax }}}";
            List<SemanticUnit> units = parser.parse(invalidCode);
            assertThat(units).isEmpty();
        }
    }

    @Nested
    @DisplayName("Validacion de Sintaxis")
    class SyntaxValidation {
        @Test
        @DisplayName("isValidSyntax debe retornar true para codigo valido")
        void shouldReturnTrueForValidCode() {
            String code = "public class Valid {}";
            assertThat(parser.isValidSyntax(code)).isTrue();
        }

        @Test
        @DisplayName("isValidSyntax debe retornar false para codigo invalido")
        void shouldReturnFalseForInvalidCode() {
            String code = "public class { broken }}}";
            assertThat(parser.isValidSyntax(code)).isFalse();
        }

        @Test
        @DisplayName("isValidSyntax debe retornar false para null")
        void shouldReturnFalseForNull() {
            assertThat(parser.isValidSyntax(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Informacion de Lineas")
    class LineInformation {
        @Test
        @DisplayName("Debe capturar lineas de inicio y fin correctas")
        void shouldCaptureCorrectLines() {
            String code = """
                public class Test {
                    public void method() {
                    }
                }
                """;

            List<SemanticUnit> units = parser.parse(code);

            SemanticUnit method = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .findFirst()
                .orElseThrow();

            assertThat(method.startLine()).isGreaterThan(0);
            assertThat(method.endLine()).isGreaterThanOrEqualTo(method.startLine());
        }
    }

    @Nested
    @DisplayName("Estimacion de Tokens")
    class TokenEstimation {
        @Test
        @DisplayName("tokenEstimate debe estimar basado en longitud de contenido")
        void shouldEstimateTokensFromContent() {
            String code = """
                public class Test {
                    public void longMethod() {
                    }
                }
                """;

            List<SemanticUnit> units = parser.parse(code);

            SemanticUnit method = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .findFirst()
                .orElseThrow();

            int expectedEstimate = method.content().length() / 4;
            assertThat(method.tokenEstimate()).isEqualTo(expectedEstimate);
        }
    }

    @Nested
    @DisplayName("Fully Qualified Scoping - Clases Anidadas")
    class FullyQualifiedScoping {
        @Test
        @DisplayName("Debe generar firma FQ para metodo en clase anidada")
        void shouldGenerateFullyQualifiedSignatureForNestedClass() {
            String code = """
                public class Outer {
                    public class Inner {
                        public void process() {
                        }
                    }
                }
                """;

            List<SemanticUnit> units = parser.parse(code);

            SemanticUnit method = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .findFirst()
                .orElseThrow();

            assertThat(method.signature()).contains("Outer");
            assertThat(method.signature()).contains("Inner");
            assertThat(method.signature()).contains("#");
            assertThat(method.signature()).contains("process");
        }

        @Test
        @DisplayName("Debe generar firma FQ para clases doblemente anidadas")
        void shouldGenerateFullyQualifiedSignatureForDoubleNestedClass() {
            String code = """
                public class Level1 {
                    public class Level2 {
                        public class Level3 {
                            public void deepMethod() {
                            }
                        }
                    }
                }
                """;

            List<SemanticUnit> units = parser.parse(code);

            SemanticUnit method = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .findFirst()
                .orElseThrow();

            assertThat(method.signature()).contains("Level1$Level2$Level3");
            assertThat(method.signature()).contains("deepMethod");
        }

        @Test
        @DisplayName("Metodo en clase simple no tiene prefijo $")
        void shouldNotHaveDollarForSimpleClass() {
            String code = """
                public class Simple {
                    public void method() {
                    }
                }
                """;

            List<SemanticUnit> units = parser.parse(code);

            SemanticUnit method = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .findFirst()
                .orElseThrow();

            assertThat(method.signature()).startsWith("Simple#");
            assertThat(method.signature()).doesNotContain("$");
        }

        @Test
        @DisplayName("Clase estatica anidada tambien usa formato FQ")
        void shouldHandleStaticNestedClass() {
            String code = """
                public class Container {
                    public static class StaticNested {
                        public void staticMethod() {
                        }
                    }
                }
                """;

            List<SemanticUnit> units = parser.parse(code);

            SemanticUnit method = units.stream()
                .filter(u -> u.type().equals(SemanticUnit.TYPE_METHOD))
                .findFirst()
                .orElseThrow();

            assertThat(method.signature()).contains("Container$StaticNested");
            assertThat(method.signature()).contains("staticMethod");
        }
    }

    @Nested
    @DisplayName("Java Version Configuration")
    class JavaVersionConfiguration {
        @Test
        @DisplayName("Constructor por defecto usa Java 21")
        void shouldDefaultToJava21() {
            SentinelJavaParser defaultParser = new SentinelJavaParser();
            assertThat(defaultParser.getLanguageLevel())
                .isEqualTo(ParserConfiguration.LanguageLevel.JAVA_21);
        }

        @Test
        @DisplayName("Factory method forJava8 configura correctamente")
        void shouldCreateJava8Parser() {
            SentinelJavaParser java8Parser = SentinelJavaParser.forJava8();
            assertThat(java8Parser.getLanguageLevel())
                .isEqualTo(ParserConfiguration.LanguageLevel.JAVA_8);
        }

        @Test
        @DisplayName("Factory method forJava11 configura correctamente")
        void shouldCreateJava11Parser() {
            SentinelJavaParser java11Parser = SentinelJavaParser.forJava11();
            assertThat(java11Parser.getLanguageLevel())
                .isEqualTo(ParserConfiguration.LanguageLevel.JAVA_11);
        }

        @Test
        @DisplayName("Factory method forJava17 configura correctamente")
        void shouldCreateJava17Parser() {
            SentinelJavaParser java17Parser = SentinelJavaParser.forJava17();
            assertThat(java17Parser.getLanguageLevel())
                .isEqualTo(ParserConfiguration.LanguageLevel.JAVA_17);
        }

        @Test
        @DisplayName("Factory method forJava21 configura correctamente")
        void shouldCreateJava21Parser() {
            SentinelJavaParser java21Parser = SentinelJavaParser.forJava21();
            assertThat(java21Parser.getLanguageLevel())
                .isEqualTo(ParserConfiguration.LanguageLevel.JAVA_21);
        }

        @Test
        @DisplayName("Constructor con LanguageLevel null usa Java 21")
        void shouldDefaultToJava21WhenNull() {
            SentinelJavaParser nullLevelParser = new SentinelJavaParser((ParserConfiguration.LanguageLevel) null);
            assertThat(nullLevelParser.getLanguageLevel())
                .isEqualTo(ParserConfiguration.LanguageLevel.JAVA_21);
        }

        @Test
        @DisplayName("Parser Java 8 puede parsear codigo legacy")
        void shouldParseJava8Code() {
            SentinelJavaParser java8Parser = SentinelJavaParser.forJava8();
            String java8Code = """
                public class LegacyService {
                    public void process() {
                        java.util.List<String> list = new java.util.ArrayList<>();
                    }
                }
                """;

            List<SemanticUnit> units = java8Parser.parse(java8Code);
            assertThat(units).isNotEmpty();
            assertThat(units).anyMatch(u -> u.type().equals(SemanticUnit.TYPE_METHOD));
        }

        @Test
        @DisplayName("Parser Java 21 puede parsear records")
        void shouldParseJava21Records() {
            SentinelJavaParser java21Parser = SentinelJavaParser.forJava21();
            String java21Code = """
                public record Point(int x, int y) {
                    public int sum() {
                        return x + y;
                    }
                }
                """;

            List<SemanticUnit> units = java21Parser.parse(java21Code);
            assertThat(units).isNotEmpty();
            assertThat(units).anyMatch(u -> u.type().equals(SemanticUnit.TYPE_METHOD));
        }
    }
}
