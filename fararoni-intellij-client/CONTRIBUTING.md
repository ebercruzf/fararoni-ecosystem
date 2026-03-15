# Contribuir a Fararoni Sentinel AI

Gracias por tu interes en contribuir. Esta guia explica como hacerlo.

## Requisitos

- **Java 21** (compilacion)
- **IntelliJ IDEA 2023.3+** (desarrollo del plugin)
- **Gradle 8.5+** (incluido via wrapper)

## Setup de Desarrollo

```bash
git clone https://github.com/fararoni/fararoni-intellij-client.git
cd fararoni-intellij-client

# Compilar
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew buildPlugin

# Ejecutar en sandbox de IntelliJ
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew runIde
```

## Flujo de Trabajo

1. Fork del repositorio
2. Crear branch desde `main`:
   ```bash
   git checkout -b feature/mi-funcionalidad
   ```
3. Hacer cambios y verificar:
   ```bash
   ./gradlew buildPlugin
   ./gradlew verifyPlugin
   ```
4. Commit con mensaje descriptivo:
   ```bash
   git commit -m "feat: descripcion breve del cambio"
   ```
5. Push y crear Pull Request

## Convenciones de Commits

Usamos [Conventional Commits](https://www.conventionalcommits.org/):

| Prefijo | Uso |
|---------|-----|
| `feat:` | Nueva funcionalidad |
| `fix:` | Correccion de bug |
| `refactor:` | Refactorizacion sin cambio funcional |
| `docs:` | Cambios en documentacion |
| `test:` | Agregar o modificar tests |
| `chore:` | Tareas de mantenimiento |

## Estilo de Codigo

- Java 21 features permitidos (records, sealed classes, pattern matching)
- Target de compilacion: Java 17 (compatibilidad IntelliJ)
- Javadoc obligatorio en clases publicas con `@author`, `@version`, `@since`
- Headers de licencia Apache 2.0 en todos los archivos `.java` (automatico via Spotless)
- Ejecutar `./gradlew spotlessApply` antes de hacer commit

## Reportar Issues

Al reportar un bug incluye:

- Version de IntelliJ IDEA
- Version de Java
- Sistema operativo
- Pasos para reproducir
- Logs relevantes (`Help > Show Log in Finder/Explorer`)

## Licencia

Al contribuir, aceptas que tus contribuciones se distribuyan bajo la [Licencia Apache 2.0](LICENSE).
