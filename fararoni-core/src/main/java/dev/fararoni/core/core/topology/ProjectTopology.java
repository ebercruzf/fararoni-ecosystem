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
package dev.fararoni.core.core.topology;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record ProjectTopology(
    Path root,
    BuildSystem buildSystem,
    Optional<Path> sourceRoot,
    List<String> packages,
    boolean isEmpty,
    boolean hasGit,
    Optional<String> projectName
) {

    public ProjectTopology {
        if (root == null) {
            throw new IllegalArgumentException("root no puede ser null");
        }
        if (buildSystem == null) {
            buildSystem = BuildSystem.UNKNOWN;
        }
        if (sourceRoot == null) {
            sourceRoot = Optional.empty();
        }
        if (packages == null) {
            packages = List.of();
        }
        if (projectName == null) {
            projectName = Optional.empty();
        }
    }

    public static ProjectTopology empty(Path root) {
        return new ProjectTopology(
            root,
            BuildSystem.UNKNOWN,
            Optional.empty(),
            List.of(),
            true,
            false,
            Optional.empty()
        );
    }

    public String toContextString() {
        StringBuilder sb = new StringBuilder();

        if (isEmpty) {
            sb.append("Directorio vacio. Puedes crear un proyecto nuevo.\n");
            return sb.toString();
        }

        sb.append("Proyecto ").append(buildSystem.getDisplayName());

        projectName.ifPresent(name -> sb.append(" (").append(name).append(")"));

        sb.append(".\n");

        sourceRoot.ifPresent(sr ->
            sb.append("Source root: ").append(sr).append("\n")
        );

        if (!packages.isEmpty()) {
            sb.append("Paquetes existentes: ")
              .append(String.join(", ", packages))
              .append("\n");
        }

        if (hasGit) {
            sb.append("Git: inicializado\n");
        }

        return sb.toString();
    }

    public String toContextJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"buildSystem\": \"").append(buildSystem.name()).append("\",\n");
        sb.append("  \"isEmpty\": ").append(isEmpty).append(",\n");
        sb.append("  \"hasGit\": ").append(hasGit).append(",\n");

        sourceRoot.ifPresent(sr ->
            sb.append("  \"sourceRoot\": \"").append(sr).append("\",\n")
        );

        projectName.ifPresent(name ->
            sb.append("  \"projectName\": \"").append(name).append("\",\n")
        );

        sb.append("  \"packages\": [");
        if (!packages.isEmpty()) {
            sb.append("\n");
            for (int i = 0; i < packages.size(); i++) {
                sb.append("    \"").append(packages.get(i)).append("\"");
                if (i < packages.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  ");
        }
        sb.append("]\n");

        sb.append("}");
        return sb.toString();
    }

    public Optional<String> suggestPackage() {
        return Optional.ofNullable(detectRootPackage());
    }

    public String detectRootPackage() {
        if (packages == null || packages.isEmpty()) {
            return null;
        }

        return packages.stream()
                .filter(p -> !p.toLowerCase().contains("example"))
                .filter(p -> !p.toLowerCase().contains("test"))
                .filter(p -> p.split("\\.").length >= 2)
                .max((a, b) -> {
                    int lenDiff = a.length() - b.length();
                    if (lenDiff != 0) return lenDiff;
                    return b.compareTo(a);
                })
                .orElse(packages.stream()
                        .filter(p -> !p.toLowerCase().contains("example"))
                        .findFirst()
                        .orElse(packages.get(0)));
    }

    public boolean isKnownProject() {
        return buildSystem.isKnown();
    }

    public String getArchitecturalRules() {
        if (isEmpty) {
            return generateStandaloneRules();
        }

        return switch (buildSystem) {
            case MAVEN, GRADLE -> generateJavaRules();
            case NPM -> generateJsRules();
            case PIP -> generatePythonRules();
            case GO -> generateGoRules();
            case CARGO -> generateRustRules();
            case DOTNET -> generateDotNetRules();
            default -> generateGenericRules();
        };
    }

    private String generateStandaloneRules() {
        return """
            [PROTOCOLO MODO AUTÓNOMO]
            1. Situación: Directorio vacío o sin estructura de proyecto.
            2. Para JAVA simple: Crea la clase directamente sin 'package'.
            3. Para proyectos completos: Sugiere crear estructura src/main/java/...
            4. Para WEB: Crea index.html en la raíz.
            5. PRIORIDAD: Funcionalidad inmediata con mínima configuración.
            """;
    }

    private String generateJavaRules() {
        String rootPkg = detectRootPackage();

        if (rootPkg == null || rootPkg.isEmpty()) {
            return """
                [PROTOCOLO JAVA - INICIO]
                1. No se detectó paquete raíz (proyecto nuevo).
                2. Sugerencia: Crea estructura src/main/java/com/empresa/
                3. PROHIBIDO: Usar 'com.example' como paquete.
                """;
        }

        String pkgPath = rootPkg.replace('.', '/');
        return """
            [PROTOCOLO JAVA - ESTRICTO]
            1. PAQUETE OBLIGATORIO: %s
            2. RUTA FÍSICA: src/main/java/%s/NombreClase.java
            3. STATEMENT: package %s;
            4. PROHIBIDO: Usar 'com.example' - ESTÁ BLOQUEADO.
            5. Para sub-paquetes (si aplica): .model, .service, .controller
            """.formatted(rootPkg, pkgPath, rootPkg);
    }

    private String generateJsRules() {
        return """
            [PROTOCOLO JS/TS]
            1. Raíz de código: src/
            2. Componentes: PascalCase (UserProfile.tsx)
            3. Utilidades: camelCase (formatDate.ts)
            4. NO uses paquetes estilo Java (com.example).
            """;
    }

    private String generatePythonRules() {
        return """
            [PROTOCOLO PYTHON]
            1. Usa snake_case para módulos y funciones.
            2. Crea __init__.py en cada directorio de paquete.
            3. Estructura típica: src/nombre_proyecto/...
            """;
    }

    private String generateGoRules() {
        return """
            [PROTOCOLO GO]
            1. Archivos en la raíz del módulo o subdirectorios.
            2. Naming: snake_case para archivos, lowercase para paquetes.
            3. Estructura: cmd/, pkg/, internal/ según convención.
            """;
    }

    private String generateRustRules() {
        return """
            [PROTOCOLO RUST]
            1. Código en src/
            2. Módulos: snake_case (user_service.rs)
            3. Usa mod.rs o el nombre del módulo directamente.
            """;
    }

    private String generateDotNetRules() {
        return """
            [PROTOCOLO DOTNET]
            1. Namespaces: PascalCase (NombreProyecto.Models)
            2. Archivos: PascalCase.cs (UserService.cs)
            3. Estructura por carpetas: Models/, Services/, Controllers/
            """;
    }

    private String generateGenericRules() {
        return """
            [PROTOCOLO GENÉRICO]
            1. Analiza la estructura existente y síguela.
            2. No inventes carpetas nuevas sin necesidad.
            3. Mantén consistencia con los archivos existentes.
            """;
    }
}
