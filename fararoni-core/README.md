# Fararoni Core

Modulo principal de implementacion de Fararoni - El agente de codigo con IA.

## Contenido del Modulo

```
fararoni-core/
├── src/main/java/dev/fararoni/
│   ├── agent/
│   │   └── ActionParser.java          ← Parser streaming + Self-Healing
│   │
│   └── core/
│       ├── commands/                   ← Comandos de consola (/test, /add, etc.)
│       ├── hooks/                      ← Sistema de hooks reactivos
│       ├── saga/                       ← Patron Saga para rollback
│       ├── skills/                     ← Implementaciones de skills
│       └── services/                   ← Servicios del core
│
└── src/test/java/                      ← Tests unitarios
```

## Componentes Principales

### 1. ActionParser

Parser streaming para respuestas del LLM con integracion Self-Healing.

```java
// Uso basico
ActionParser parser = new ActionParser(filesystemService, outputCallback);

// Con Self-Healing
ActionParser parser = new ActionParser(
    filesystemService,
    outputCallback,
    gitService,
    sagaOrchestrator,
    List.of(new TestOnWriteHook(testCommand))
);
```

### 2. SagaOrchestrator

Coordinador de transacciones con rollback automatico.

```java
String sagaId = orchestrator.beginSaga();
// ... operaciones ...
orchestrator.registerCompensation(sagaId, undoInstruction);
// Si algo falla:
orchestrator.compensate(sagaId);
// Si todo OK:
orchestrator.commitSaga(sagaId);
```

### 3. PostWriteHook

Interface para validacion reactiva de archivos escritos.

```java
public class MyHook implements PostWriteHook {
    @Override
    public HookResult onFileWritten(Path file, String sagaId) {
        // Validacion
        return HookResult.ok(); // o rollback(reason)
    }
}
```

### 4. TestOnWriteHook

Implementacion que ejecuta tests automaticamente y solicita rollback si fallan.

```java
TestOnWriteHook regressionGuard = new TestOnWriteHook(testCommand);
// Se activa cuando se escribe cualquier archivo de codigo
// Ejecuta tests con timeout de 30 segundos
// Si los tests fallan, retorna HookResult.rollback()
```

### 5. TestCommand

Comando `/test` con modo Quick para integracion.

```java
// Modo consola (5 min timeout)
testCommand.execute(args, context);

// Modo quick (30s timeout, sin output)
TestResult result = testCommand.executeQuick(projectRoot);
```

### 6. StreamParser (Vendor-Agnostic)

Interface para parseo de streams SSE de diferentes proveedores LLM.

```java
// Interface en agent-api
public interface StreamParser {
    String extractContent(String sseData);
    boolean isEndOfStream(String sseData);
    Optional<String> extractError(String sseData);
}

// Implementaciones en core
StreamParser openAi = new OpenAiStreamParser();  // OpenAI, vLLM, Azure
StreamParser ollama = new OllamaStreamParser();  // Ollama nativo

// Inyeccion en cliente
VllmClient client = VllmClient.builder()
    .streamParser(openAi)
    .build();
```

**Proveedores soportados:**
- `OpenAiStreamParser`: OpenAI, vLLM, Azure OpenAI, cualquier endpoint compatible
- `OllamaStreamParser`: Ollama nativo con formato JSON diferente

### 7. IngestionChannel (Canales de Entrada)

Adaptadores para recibir mensajes de diferentes fuentes externas.

```java
// Interface base
public interface IngestionChannel {
    void start();
    void stop();
    boolean isRunning();
    String getChannelType();
}

// Clase base con EventBus integration
public abstract class AbstractIngestionChannel implements IngestionChannel {
    protected void publishMessage(IncomingMessage message) {
        eventBus.publish(new IncomingMessageEvent(message));
    }
}
```

**Adaptadores disponibles:**

| Adaptador | Uso | Configuracion |
|-----------|-----|---------------|
| `WebhookIngestionChannel` | HTTP webhooks genericos | `port`, `path` |
| `ImapIngestionChannel` | Email IMAP IDLE | `host`, `user`, `password`, `folder` |
| `JiraWebhookChannel` | Jira webhooks | `port`, `secret` |
| `SlackIngestionChannel` | Slack Events API | `port`, `signingSecret` |

```java
// Ejemplo: Webhook generico
WebhookIngestionChannel webhook = WebhookIngestionChannel.builder()
    .eventBus(eventBus)
    .port(8080)
    .path("/api/webhook")
    .build();
webhook.start();

// Ejemplo: Email IMAP
ImapIngestionChannel imap = ImapIngestionChannel.builder()
    .eventBus(eventBus)
    .host("imap.gmail.com")
    .port(993)
    .username("user@gmail.com")
    .password("app-password")
    .folder("INBOX")
    .build();
imap.start();

// Ejemplo: Slack
SlackIngestionChannel slack = SlackIngestionChannel.builder()
    .eventBus(eventBus)
    .port(3000)
    .signingSecret("your-slack-signing-secret")
    .build();
slack.start();
```

Todos los adaptadores publican `IncomingMessageEvent` al EventBus:

## Self-Healing Flow

```
LLM genera codigo
       │
       ▼
ActionParser detecta <<<END_FILE
       │
       ├─1─▶ beginSaga()
       ├─2─▶ writeFile()
       ├─3─▶ registerCompensation()
       ├─4─▶ TestOnWriteHook.onFileWritten()
       │         │
       │         └─▶ executeQuick() → TestResult
       │
       ├── Si PASS: commitSaga() → Archivo guardado
       └── Si FAIL: compensate() → Archivo restaurado
```

## Herramientas Autonomas del LLM (Desactivadas por Seguridad)

Fararoni incluye 3 herramientas que permiten al LLM ejecutar acciones peligrosas de forma autonoma (sin que el usuario lo solicite). Estas herramientas estan **comentadas intencionalmente** por seguridad.

### Diferencia entre Comandos y Herramientas

| Concepto | Quien lo invoca | Ejemplo | Estado |
|----------|-----------------|---------|--------|
| **Comando** (`/run`, `/git`) | El **usuario** escribe en la consola | `/run mvn test` | Activo |
| **Herramienta** (`shell_execute`, `git_action`) | El **LLM decide solo** ejecutarlo | LLM decide correr `rm -rf` | Bloqueado |

### Herramientas Comentadas

| Herramienta | Proposito | Riesgo |
|-------------|-----------|--------|
| `defineShellCommandTool()` | El LLM ejecuta comandos shell autonomamente | Podria ejecutar comandos destructivos |
| `defineGitActionTool()` | El LLM hace git commit/push autonomamente | Podria hacer push sin autorizacion |
| `defineExecuteCodeTool()` | El LLM ejecuta codigo arbitrario | Podria correr codigo malicioso |

### Como Activarlas (Bajo Tu Propio Riesgo)

**Archivo:** `src/main/java/dev/fararoni/core/core/skills/ToolRegistry.java`

Busca la seccion marcada como `[SEGURIDAD] HERRAMIENTAS PELIGROSAS` (alrededor de la linea 1068):

```java
// =====================================================================
// [SEGURIDAD] HERRAMIENTAS PELIGROSAS - COMENTADAS INTENCIONALMENTE
// Estas herramientas SOLO estan disponibles manualmente via /run.
// El LLM NO puede invocarlas automaticamente por seguridad.
// ACTIVAR: Descomentar las lineas de abajo bajo tu propio riesgo.
// =====================================================================
// tools.add(defineShellCommandTool()); // COMENTADO: Seguridad - Solo /run
// tools.add(defineGitActionTool());    // COMENTADO: Seguridad - Solo /run
// tools.add(defineExecuteCodeTool());  // COMENTADO: Seguridad - Redirect
```

Para activar, descomenta la linea deseada quitando `//` al inicio:

```java
tools.add(defineShellCommandTool()); // ACTIVADO: El LLM puede ejecutar shell
// tools.add(defineGitActionTool());    // Sigue comentado
// tools.add(defineExecuteCodeTool());  // Sigue comentado
```

Luego recompila:

```bash
mvn clean package -DskipTests -Dmaven.javadoc.skip=true
```

> **Advertencia:** Al activar estas herramientas, el LLM podra ejecutar comandos en tu sistema sin pedirte confirmacion. Solo activalas en entornos controlados o de prueba.

---

## Dependencias

```xml
<dependency>
    <groupId>dev.fararoni</groupId>
    <artifactId>fararoni-agent-api</artifactId>
</dependency>
```

## Documentacion

- [Arquitectura Self-Healing](../docs/architecture/SELF-HEALING-ARCHITECTURE.md)
- [Manual de Usuario](../docs/user-guide/SELF-HEALING-USER-GUIDE.md)

## Compilacion

```bash
mvn compile -pl fararoni-core -am
```

## Tests

```bash
mvn test -pl fararoni-core
```

---

*Version: 2.0.0*
*Modulo: fararoni-core (Open Source)*
