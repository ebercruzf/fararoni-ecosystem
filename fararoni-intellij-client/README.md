# Fararoni Sentinel AI - IntelliJ Plugin

Plugin de IntelliJ IDEA que conecta tu IDE con el nucleo Fararoni (HiveMind) para autocompletado inteligente y reparacion quirurgica de codigo.

```
    ╔═══════════════════════════════════════════════════════════╗
    ║                  FARARONI SENTINEL AI                      ║
    ║              Inteligencia Tactica para Java               ║
    ╚═══════════════════════════════════════════════════════════╝
```

> **Aviso:** Esta es una version en desarrollo y **no se considera estable**. Actualmente el plugin solo permite validar funcionalidades basicas a traves del chat integrado. Las demas features (autocompletado, sugerencias proactivas, reparacion quirurgica) estan en fase experimental y pueden no funcionar correctamente.

---

## Tabla de Contenidos

1. [Descripcion General](#descripcion-general)
2. [Caracteristicas](#caracteristicas)
3. [Requisitos del Sistema](#requisitos-del-sistema)
4. [Instalacion](#instalacion)
5. [Configuracion](#configuracion)
6. [Manual de Uso](#manual-de-uso)
7. [Arquitectura](#arquitectura)
8. [Desarrollo](#desarrollo)
9. [API Reference](#api-reference)
10. [Solucion de Problemas](#solucion-de-problemas)
11. [Contribuir](#contribuir)

---

## Descripcion General

**Fararoni Sentinel AI** es un plugin para IntelliJ IDEA que proporciona asistencia de programacion potenciada por IA, conectandose a un servidor local (Fararoni Core) que ejecuta un enjambre de agentes especializados.

### Filosofia

- **Fail-Silent**: Si el servidor no responde, el IDE sigue funcionando sin interrupciones
- **Contexto Local**: Todo el procesamiento ocurre en tu maquina, sin enviar codigo a la nube
- **Integracion Nativa**: Usa las APIs oficiales de IntelliJ para una experiencia fluida

---

## Caracteristicas

### Autocompletado Inteligente (Ctrl+Space)

Obtiene sugerencias de codigo basadas en el contexto actual:

```
┌─────────────────────────────────────────────────────────────┐
│  public void processOrder(Order order) {                   │
│      if (order.isValid()) {                                 │
│          █                                                  │
│      }                                                      │
│  }                                                          │
├─────────────────────────────────────────────────────────────┤
│  ► order.process();           [Fararoni AI]                │
│    order.validate();                                        │
│    order.save();                                            │
└─────────────────────────────────────────────────────────────┘
```

- Analiza hasta 1000 caracteres de contexto previo
- Limpia automaticamente markdown de las respuestas
- Icono distintivo para identificar sugerencias de Fararoni

### Reparacion Quirurgica (Alt+Enter)

Analiza y corrige bloques de codigo con errores:

```
┌─────────────────────────────────────────────────────────────┐
│  public void calculate(int x) {                             │
│      return x * 2;  // ERROR: void method returns value     │
│  }                  ▲                                       │
│                     └── Cursor aqui                         │
├─────────────────────────────────────────────────────────────┤
│  💡 Fararoni: Analizar y Arreglar Bloque                   │
│     Quick fix...                                            │
│     Suppress warning...                                     │
└─────────────────────────────────────────────────────────────┘
```

- Ejecuta en background para no bloquear la UI
- Aplica cambios con WriteCommandAction (Undo disponible)
- Muestra dialogo informativo si no hay cambios propuestos

---

## Requisitos del Sistema

| Componente | Version Minima | Recomendado |
|------------|----------------|-------------|
| IntelliJ IDEA | 2023.3 | 2024.1+ |
| Java Runtime | 17 | 21 |
| Fararoni Core | 1.0 | Ultima |
| RAM disponible | 512 MB | 1 GB |
| Puerto | 7070 (libre) | - |

### Compatibilidad de Versiones

```
IntelliJ IDEA:
  ├── Community Edition (IC) ✓
  ├── Ultimate Edition (IU) ✓
  └── Versiones: 2023.3 - 2024.3

Plataformas:
  ├── macOS (arm64, x86_64) ✓
  ├── Linux (x86_64) ✓
  └── Windows (x86_64) ✓
```

---

## Instalacion

### Metodo 1: Desde Archivo ZIP (Recomendado)

1. **Descargar** el archivo `fararoni-intellij-client-1.0-SNAPSHOT.zip` de `build/distributions/`

2. **Instalar en IntelliJ**:
   ```
   Settings/Preferences
     → Plugins
       → ⚙️ (icono engranaje)
         → Install Plugin from Disk...
           → Seleccionar el archivo .zip
   ```

3. **Reiniciar** IntelliJ IDEA

### Metodo 2: Desde Codigo Fuente

```bash
# Clonar repositorio
git clone <repo-url>
cd fararoni-intellij-client

# Compilar (requiere Java 21)
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew buildPlugin

# El ZIP estara en:
# build/distributions/fararoni-intellij-client-1.0-SNAPSHOT.zip
```

### Metodo 3: Modo Desarrollo (Sandbox)

```bash
# Ejecuta una instancia de IntelliJ con el plugin instalado
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew runIde
```

---

## Configuracion

### 1. Iniciar Fararoni Core

El plugin requiere que el servidor Fararoni Core este ejecutandose en `localhost:7070`.

```bash
# Desde el directorio fararoni-core
cd ../fararoni-core

# Opcion A: Con Maven
mvn exec:java -Dexec.mainClass="dev.fararoni.core.FararoniMain"

# Opcion B: Con JAR empaquetado
java -jar target/fararoni-core-1.0-SNAPSHOT.jar
```

### 2. Verificar Conexion

```bash
# Test rapido de conectividad
curl -X POST http://localhost:7070/api/task \
  -H "Content-Type: application/json" \
  -d '{"intent":"PING","context":{"content":"test"}}'
```

Respuesta esperada:
```json
{"status":"OK","message":"Fararoni Core operativo"}
```

### 3. Configuracion del Plugin (Opcional)

Actualmente el plugin usa valores por defecto. Configuraciones futuras:

| Parametro | Valor Default | Descripcion |
|-----------|---------------|-------------|
| `api.url` | `http://localhost:7070/api/task` | Endpoint del Core |
| `timeout.connect` | `2s` | Timeout de conexion |
| `timeout.read` | `30s` | Timeout de lectura |
| `context.maxChars` | `1000` | Contexto maximo enviado |

---

## Manual de Uso

### Autocompletado con Fararoni

1. **Posiciona el cursor** donde necesitas codigo
2. **Presiona `Ctrl+Space`** (o `Cmd+Space` en macOS)
3. **Busca el icono** de Fararoni en las sugerencias
4. **Selecciona** la sugerencia para insertarla

```
Flujo de Autocompletado:

  [Editor]                          [Fararoni Core]
     │                                    │
     │  Ctrl+Space                        │
     ├───────────────────────────────────►│
     │  {intent: "AUTOCOMPLETE",          │
     │   context: {content: "..."}}       │
     │                                    │
     │◄───────────────────────────────────┤
     │  Sugerencia de codigo              │
     │                                    │
     ▼                                    ▼
  [Menu de Completado]
```

### Reparacion de Codigo

1. **Posiciona el cursor** sobre el codigo con error
2. **Presiona `Alt+Enter`** para abrir el menu de intenciones
3. **Selecciona** "Fararoni: Analizar y Arreglar Bloque"
4. **Espera** a que el agente procese (maximo 30s)
5. **Revisa** el cambio aplicado (Ctrl+Z para deshacer)

```
Flujo de Reparacion:

  [Editor]                [Background Thread]        [Fararoni Core]
     │                           │                         │
     │  Alt+Enter                │                         │
     │  Seleccionar Fix          │                         │
     ├──────────────────────────►│                         │
     │                           │  POST /api/task         │
     │                           ├────────────────────────►│
     │                           │  {intent: "SURGICAL_FIX"│
     │                           │   context: {...}}       │
     │                           │                         │
     │                           │◄────────────────────────┤
     │                           │  Codigo corregido       │
     │  WriteCommandAction       │                         │
     │◄──────────────────────────┤                         │
     │  Reemplazar texto         │                         │
     ▼                           ▼                         ▼
```

### Atajos de Teclado

| Accion | Windows/Linux | macOS |
|--------|---------------|-------|
| Autocompletado | `Ctrl+Space` | `Cmd+Space` |
| Intenciones | `Alt+Enter` | `Option+Enter` |
| Deshacer | `Ctrl+Z` | `Cmd+Z` |

---

## Arquitectura

### Diagrama de Componentes

```
┌─────────────────────────────────────────────────────────────────┐
│                     INTELLIJ IDEA                               │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                 Fararoni Sentinel AI Plugin                │  │
│  │  ┌─────────────────┐  ┌─────────────────────────────────┐ │  │
│  │  │   MyIcons.java  │  │         features/               │ │  │
│  │  │   (Recursos)    │  │  ┌───────────────────────────┐  │ │  │
│  │  └─────────────────┘  │  │ FararoniCompletionContrib │  │ │  │
│  │                       │  │ (CompletionContributor)   │  │ │  │
│  │  ┌─────────────────┐  │  └───────────────────────────┘  │ │  │
│  │  │ FararoniClient  │  │  ┌───────────────────────────┐  │ │  │
│  │  │ (HTTP Client)   │◄─┼──│ FixWithFararoniAction     │  │ │  │
│  │  │                 │  │  │ (IntentionAction)         │  │ │  │
│  │  └────────┬────────┘  │  └───────────────────────────┘  │ │  │
│  │           │           └─────────────────────────────────┘ │  │
│  └───────────┼───────────────────────────────────────────────┘  │
│              │                                                   │
└──────────────┼───────────────────────────────────────────────────┘
               │ HTTP POST
               │ localhost:7070/api/task
               ▼
┌─────────────────────────────────────────────────────────────────┐
│                     FARARONI CORE                               │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    HiveMind (Swarm)                       │  │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐      │  │
│  │  │   PM    │  │  DEV    │  │   QA    │  │ CRITIC  │      │  │
│  │  │  Agent  │  │  Agent  │  │  Agent  │  │  Agent  │      │  │
│  │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘      │  │
│  │       └────────────┴────────────┴────────────┘            │  │
│  │                        │                                  │  │
│  │              ┌─────────▼─────────┐                        │  │
│  │              │  LLM Backend      │                        │  │
│  │              │  (Ollama/OpenAI)  │                        │  │
│  │              └───────────────────┘                        │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Estructura del Proyecto

```
fararoni-intellij-client/
├── build.gradle.kts              # Configuracion Gradle con IntelliJ SDK
├── settings.gradle.kts           # Nombre del proyecto
├── gradlew                       # Wrapper de Gradle
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── src/
│   └── main/
│       ├── java/
│       │   └── dev/fararoni/
│       │       ├── client/
│       │       │   └── FararoniClient.java      # Cliente HTTP
│       │       └── features/
│       │           ├── MyIcons.java             # Iconos
│       │           ├── FararoniCompletionContributor.java
│       │           └── FixWithFararoniAction.java
│       └── resources/
│           └── META-INF/
│               └── plugin.xml    # Manifiesto del plugin
└── build/
    └── distributions/
        └── fararoni-intellij-client-1.0-SNAPSHOT.zip
```

### Componentes Principales

#### FararoniClient.java

Cliente HTTP que maneja la comunicacion con el Core:

```java
// Configuracion de timeouts
OkHttpClient client = new OkHttpClient.Builder()
    .connectTimeout(2, TimeUnit.SECONDS)   // Fail-fast
    .readTimeout(30, TimeUnit.SECONDS)     // Tiempo para pensar
    .build();

// Formato de Request
{
  "intent": "AUTOCOMPLETE" | "SURGICAL_FIX",
  "context": {
    "content": "<codigo>",
    "ide": "IntelliJ"
  }
}
```

#### FararoniCompletionContributor.java

Contribuidor de autocompletado registrado en `plugin.xml`:

```xml
<completion.contributor
    language="JAVA"
    implementationClass="...FararoniCompletionContributor"/>
```

#### FixWithFararoniAction.java

Accion de intencion disponible via Alt+Enter:

```xml
<intentionAction>
    <className>...FixWithFararoniAction</className>
    <category>Fararoni AI</category>
</intentionAction>
```

---

## Desarrollo

### Prerequisitos

- **Java 21** (para compilar)
- **IntelliJ IDEA** (para desarrollo del plugin)
- **Gradle 8.5+** (incluido via wrapper)

### Setup de Desarrollo

```bash
# 1. Clonar el proyecto
git clone <repo>
cd fararoni-intellij-client

# 2. Importar en IntelliJ IDEA
#    File → Open → Seleccionar directorio
#    Aceptar "Load Gradle Project"

# 3. Esperar descarga del IntelliJ SDK (~800MB primera vez)

# 4. Ejecutar en modo sandbox
./gradlew runIde
```

### Comandos Gradle

| Comando | Descripcion |
|---------|-------------|
| `./gradlew buildPlugin` | Compila y empaqueta el plugin |
| `./gradlew runIde` | Ejecuta IntelliJ sandbox con el plugin |
| `./gradlew verifyPlugin` | Verifica compatibilidad del plugin |
| `./gradlew clean` | Limpia artefactos de build |
| `./gradlew dependencies` | Lista dependencias |

### Estructura de Dependencias

```
fararoni-intellij-client
├── org.jetbrains.intellij (plugin SDK)
├── com.squareup.okhttp3:okhttp:4.12.0
├── com.google.code.gson:gson:2.10.1
└── org.junit.jupiter:junit-jupiter-api:5.10.0 (test)
```

---

## API Reference

### Endpoint Principal

```
POST http://localhost:7070/api/task
Content-Type: application/json
```

### Intents Soportados

#### AUTOCOMPLETE

Solicita sugerencias de completado de codigo.

**Request:**
```json
{
  "intent": "AUTOCOMPLETE",
  "context": {
    "content": "public void process() {\n    List<String> items = ",
    "ide": "IntelliJ"
  }
}
```

**Response:**
```json
{
  "solution": "new ArrayList<>();\nfor (String item : source) {\n    items.add(item.trim());\n}"
}
```

#### SURGICAL_FIX

Solicita reparacion de un fragmento de codigo.

**Request:**
```json
{
  "intent": "SURGICAL_FIX",
  "context": {
    "content": "public void getValue() { return 42; }",
    "ide": "IntelliJ"
  }
}
```

**Response:**
```json
{
  "solution": "public int getValue() { return 42; }"
}
```

---

## Solucion de Problemas

### El autocompletado no muestra sugerencias de Fararoni

1. **Verificar que el Core esta corriendo:**
   ```bash
   curl http://localhost:7070/api/health
   ```

2. **Verificar logs de IntelliJ:**
   ```
   Help → Diagnostic Tools → Show Log in Finder/Explorer
   ```

3. **Verificar que el plugin esta activo:**
   ```
   Settings → Plugins → Installed → Buscar "Fararoni"
   ```

### Error "Connection refused"

El servidor Fararoni Core no esta ejecutandose o esta en otro puerto.

```bash
# Verificar puerto 7070
lsof -i :7070

# Iniciar el Core si no esta corriendo
cd fararoni-core
mvn exec:java -Dexec.mainClass="dev.fararoni.core.FararoniMain"
```

### La reparacion no aplica cambios

1. El agente puede no haber encontrado problemas
2. El codigo seleccionado puede ser muy pequeno para contexto
3. Timeout excedido (>30s)

**Solucion:** Seleccionar un bloque de codigo mas grande o verificar logs.

### Error de compilacion con Java 25

El plugin requiere Java 21 para compilar. Usar toolchain:

```bash
# macOS
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew buildPlugin

# Linux
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew buildPlugin

# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-21
gradlew.bat buildPlugin
```

### El plugin no aparece en IntelliJ

1. Verificar version de IntelliJ (minimo 2023.3)
2. Verificar que el ZIP se instalo correctamente
3. Reiniciar IntelliJ despues de instalar

---

## Contribuir

### Reportar Issues

Usar el repositorio de GitHub para reportar bugs o solicitar features.

### Pull Requests

1. Fork del repositorio
2. Crear branch: `git checkout -b feature/nueva-funcionalidad`
3. Commit: `git commit -m "feat: descripcion"`
4. Push: `git push origin feature/nueva-funcionalidad`
5. Crear Pull Request

### Estilo de Codigo

- Java 21 features permitidos
- Javadoc para metodos publicos
- Tests unitarios para nuevas funcionalidades

---

## Licencia

Copyright (c) 2026 Eber Cruz Fararoni

---

## Contacto

- **Web:** https://fararoni.dev

---

```
    ╔═══════════════════════════════════════════════════════════╗
    ║           "Codigo Limpio, Mente Tactica"                  ║
    ║                    - Fararoni AI -                        ║
    ╚═══════════════════════════════════════════════════════════╝
```
