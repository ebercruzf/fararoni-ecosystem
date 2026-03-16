# Fararoni — Guia de Inicio Rapido

**Version: 1.0.0 | Java 25**

> **Fararoni** es un orquestador de agentes de IA soberano (Sovereign AI Agent Orchestrator)
> que opera modelos de lenguaje locales para asistir en tareas de desarrollo de software.
> Funciona como CLI interactivo, servidor REST para plugins IDE, o gateway multicanal
> para WhatsApp, Telegram, Discord e iMessage.

---

## Indice

1. [Introduccion y Arquitectura](#1-introduccion-y-arquitectura)
2. [Justificacion: Por que modelos locales](#2-justificacion-por-que-modelos-locales)
3. [Requisitos del Sistema](#3-requisitos-del-sistema)
4. [Guia de Modelos por Hardware](#4-guia-de-modelos-por-hardware)
5. [Instalacion](#5-instalacion)
6. [Levantar Fararoni Core CLI (Modo Interactivo)](#6-levantar-fararoni-core-cli-modo-interactivo)
7. [Levantar Fararoni Core Server (Modo Servidor)](#7-levantar-fararoni-core-server-modo-servidor)
8. [Levantar OmniChannel Gateway + Sidecars](#8-levantar-omnichannel-gateway--sidecars)
9. [Seguridad Zero-Trust (3 Llaves)](#9-seguridad-zero-trust-3-llaves)
10. [Gestion de Invitados](#10-gestion-de-invitados)
11. [Configuracion (modules.yml)](#11-configuracion-modulesyml)
12. [Comandos Esenciales](#12-comandos-esenciales)
13. [Verificacion y Troubleshooting](#13-verificacion-y-troubleshooting)
14. [Siguiente Paso](#14-siguiente-paso)

---

## 1. Introduccion y Arquitectura

Fararoni se compone de los siguientes modulos:

```
+===========================================================================+
|                    FARARONI — Sovereign AI Agent Orchestrator              |
+===========================================================================+
|                                                                           |
|  +---------------------------+                                           |
|  | Fararoni Core             |                                           |
|  | (fararoni-core)           |                                           |
|  |                           |                                           |
|  |  CLI Interactivo          |                                           |
|  |  Servidor REST :7070      |                                           |
|  |  Rabbit (modelo rapido)   |                                           |
|  |  Turtle (modelo experto)  |                                           |
|  +---------------------------+                                           |
|               |                                  |                        |
|  +---------------------------+     +----------------------------------+   |
|  | SovereignEventBus         |     | Fararoni Enterprise Transport    |   |
|  | (InMemorySovereignBus)    |     | (NatsSovereignBus)               |   |
|  |                           |     |                                  |   |
|  | Bus de eventos embebido   |     | NATS JetStream (distribuido)     |   |
|  | Zero-config, listo al     |     | Reemplaza InMemory via SPI      |   |
|  | iniciar Core              |     | Solo si se necesita multi-nodo   |   |
|  +---------------------------+     +----------------------------------+   |
|               |                                                           |
|  +--------------------------------------------------------------------+  |
|  | OmniChannel Gateway (fararoni-gateway-rest)              :7071     |  |
|  |                                                                    |  |
|  |  RestIngressServer  ←  Recibe mensajes de Sidecars                |  |
|  |  HttpEgressDispatcher → Envia respuestas a Sidecars               |  |
|  |  OmniChannelRouter    → Rutea al Core para procesamiento          |  |
|  +--------------------------------------------------------------------+  |
|               |                                                           |
|  +------------+----------+-----------+-----------+                        |
|  | Sidecar    | Sidecar  | Sidecar   | Sidecar   |                       |
|  | WhatsApp   | Telegram | Discord   | iMessage  |                       |
|  | :3000      | :3001    | :3002     | :3003     |                       |
|  | (Baileys)  | (Node)   | (Node)    | (BlueBub) |                       |
|  +------------+----------+-----------+-----------+                        |
|                                                                           |
|                                                                           |
|  +--------------------------------------------------------------------+  |
|  | FNL — Fararoni Native Link (fararoni-agent-api)                    |  |
|  | Contratos y protocolos de comunicacion LLM-Sistema                 |  |
|  +--------------------------------------------------------------------+  |
|                                                                           |
+===========================================================================+
```

### Catalogo de Componentes

| Componente | Nombre Formal | Modulo Maven | Descripcion |
|-----------|---------------|-------------|-------------|
| Core CLI | **Fararoni Core** | `fararoni-core` | Motor principal: CLI, servidor REST, agentes |
| Bus de Eventos | **SovereignEventBus** | Embebido en Core | Bus in-memory, zero-config, se levanta con Core |
| Bus Enterprise | **NatsSovereignBus** | `fararoni-enterprise-transport` | Bus distribuido via NATS JetStream (opcional) |
| Gateway | **OmniChannel Gateway** | `fararoni-gateway-rest` | HTTP ingress/egress para sidecars |
| API de Agentes | **FNL (Fararoni Native Link)** | `fararoni-agent-api` | Contratos para comunicacion LLM-Sistema |
| MCP Bridge | **Fararoni MCP Bridge** | `fararoni-sidecar-mcp` | Model Context Protocol (experimental) |

### Modelos LLM: Rabbit y Turtle

Fararoni usa una arquitectura de **cerebro dual**:

| Rol | Nombre | Tamano Tipico | Proposito |
|-----|--------|--------------|-----------|
| **Rabbit** (Conejo) | Modelo rapido | 1.5B — 7B | Chat casual, planificacion rapida, tareas simples |
| **Turtle** (Tortuga) | Modelo experto | 14B — 72B | Analisis profundo, tool calling, generacion de codigo |

El Rabbit responde en milisegundos. Cuando la tarea es compleja, Fararoni escala
automaticamente al Turtle. Ambos son configurables en caliente via `/reconfig`.

---

## 2. Justificacion: Por que modelos locales

### Ventajas

- **Privacidad total**: tu codigo nunca sale de tu maquina
- **Costo $0**: solo electricidad, sin suscripciones ni tokens
- **Sin internet**: funciona offline (despues de descargar modelos)
- **Sin limites de rate**: sin HTTP 429, sin cuotas
- **Latencia minima**: respuestas en milisegundos para el Rabbit
- **Personalizable**: puedes fine-tunear modelos para tu stack

### Hibrido: Local + Cloud

Fararoni soporta activar **Claude (Anthropic)** como Turtle en caliente via `/reconfigt`.
Esto permite usar un modelo local barato para chat casual (Rabbit) y Claude
para tareas complejas, optimizando costo vs calidad.

---

## 3. Requisitos del Sistema

### Software

| Requisito | Version | Obligatorio |
|-----------|---------|-------------|
| **Java (JDK)** | 25+ (GraalVM o OpenJDK) | SI |
| **Maven** | 3.9.0+ | SI (para compilar) |
| **Ollama** | Latest | SI (servidor LLM local) |
| **Node.js** | 22+ | Solo si compilas sidecars desde fuente |
| **Git** | 2.x | Recomendado |

**CRITICO**: El flag `--enable-preview` es **OBLIGATORIO** para Java 25.
Fararoni usa JEP 480 (StructuredTaskScope) y JEP 481 (ScopedValues).

### Hardware Minimo

| Recurso | Minimo | Recomendado |
|---------|--------|-------------|
| **RAM** | 8 GB | 16 GB+ |
| **Disco** | 10 GB (modelos) | SSD NVMe |
| **CPU** | Cualquier moderno | Apple Silicon M1+ |

---

## 4. Guia de Modelos por Hardware

### Equipo con 8 GB de RAM — Solo Rabbit (un modelo)

Con 8 GB de RAM el espacio es limitado. Se recomienda usar **un solo modelo pequeno**
que funcione como Rabbit y Turtle a la vez:

```bash
# Descargar UN solo modelo (3.4 GB)
ollama pull qwen2.5-coder:7b
```

| Modelo | VRAM/RAM | Velocidad | Capacidad |
|--------|----------|-----------|-----------|
| `qwen2.5-coder:7b` | ~4.5 GB | Rapida | Buena para codigo, tool calling basico |
| `qwen3.5:4b` | ~3.4 GB | Muy rapida | Multimodal (texto + imagenes) |
| `qwen2.5-coder:1.5b` | ~1.2 GB | Instantanea | Basica, solo chat casual |

**Configuracion recomendada para 8 GB:**
```bash
# Un solo modelo como Rabbit Y Turtle
export LLM_SERVER_URL=http://localhost:11434
export LLM_MODEL_NAME=qwen2.5-coder:7b

java --enable-preview -jar fararoni-core-1.0.0.jar
# En /reconfig: usar el mismo modelo para ambos roles
```

### Equipo con 16 GB de RAM — Rabbit + Turtle (dos modelos)

Con 16 GB puedes tener **dos modelos** simultaneos: uno rapido y uno grande.

```bash
# Descargar dos modelos
ollama pull qwen2.5-coder:1.5b    # Rabbit (1.2 GB)
ollama pull qwen2.5-coder:32b     # Turtle (20 GB, se pagina a disco)
# O mejor:
ollama pull qwen3.5:9b            # Turtle (6.6 GB, multimodal)
```

| Configuracion | Rabbit | Turtle | RAM usada |
|---------------|--------|--------|-----------|
| **Optima** | `qwen2.5-coder:1.5b` (1.2 GB) | `qwen3.5:9b` (6.6 GB) | ~8 GB |
| **Balanceada** | `qwen2.5-coder:7b` (4.5 GB) | `qwen3.5:9b` (6.6 GB) | ~11 GB |
| **Maxima potencia** | `qwen2.5-coder:1.5b` (1.2 GB) | `qwen2.5-coder:32b` (20 GB) | Pagina a disco |

**Configuracion recomendada para 16 GB:**
```bash
export LLM_SERVER_URL=http://localhost:11434
export LLM_MODEL_NAME=qwen2.5-coder:7b          # Rabbit
export FARARONI_TURTLE_MODEL=qwen3.5:9b          # Turtle

java --enable-preview -jar fararoni-core-1.0.0.jar
```

### Equipo con 32 GB+ de RAM — Configuracion Premium

```bash
ollama pull qwen2.5-coder:7b      # Rabbit
ollama pull qwen3.5:35b           # Turtle (24 GB, multimodal + vision)
```

`qwen3.5:35b` es multimodal (acepta imagenes), soporta tool calling,
thinking mode, y tiene 256K de contexto.

---

## 5. Instalacion

### Opcion A: Instalador (recomendado, zero dependencies)

Descarga el instalador desde [GitHub Releases](https://github.com/ebercruzf/fararoni-ecosystem/releases):

| Plataforma | Archivo |
|------------|---------|
| macOS (arm64) | `Fararoni-Installer-macos-arm64.dmg` |
| Linux (x64) | `fararoni-v1.0.0-linux-x64.tar.gz` |
| Windows (x64) | `fararoni-v1.0.0-windows-x64.zip` |

El instalador incluye core + 4 sidecars como binarios nativos. No requiere Node.js.

### Opcion B: Homebrew (macOS / Linux)

```bash
brew tap ebercruzf/fararoni
brew install fararoni
```

### Opcion C: Compilar desde fuente

#### Paso 1: Clonar el repositorio

```bash
git clone https://github.com/ebercruzf/fararoni-ecosystem.git
cd fararoni-ecosystem
```

#### Paso 2: Configurar Java 25

```bash
# macOS con SDKMAN
sdk install java 25-graalce
sdk use java 25-graalce

# O con brew
brew install --cask graalvm-jdk

# Verificar
java --version
# Debe mostrar: java 25.x.x
```

#### Paso 3: Compilar

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
mvn clean package -DskipTests -Dmaven.javadoc.skip=true
```

Resultado: `fararoni-core/target/fararoni-core-1.0.0.jar`

#### Paso 4: Instalar Ollama y modelos

```bash
# Instalar Ollama
brew install ollama        # macOS
# o: curl -fsSL https://ollama.com/install.sh | sh   # Linux

# Iniciar Ollama
ollama serve

# Descargar modelo (en otra terminal)
ollama pull qwen2.5-coder:7b
```

#### Paso 5 (opcional): Crear launcher

```bash
cat > /usr/local/bin/fararoni << 'EOF'
#!/bin/bash
JAVA_HOME=$(/usr/libexec/java_home -v 25)
JAR_PATH="$HOME/fararoni/fararoni-core/target/fararoni-core-1.0.0.jar"
exec "$JAVA_HOME/bin/java" --enable-preview -jar "$JAR_PATH" "$@"
EOF
chmod +x /usr/local/bin/fararoni
```

---

## 6. Levantar Fararoni Core CLI (Modo Interactivo)

El modo CLI es una terminal interactiva tipo Claude Code para trabajar con tu proyecto.

### Iniciar

```bash
# Con launcher instalado
fararoni

# Con JAR directamente
java --enable-preview -jar fararoni-core-1.0.0.jar

# Con directorio de trabajo especifico
java --enable-preview -jar fararoni-core-1.0.0.jar --cwd /ruta/a/tu/proyecto
```

### Que se levanta

```
Fararoni Core CLI
    ├── InteractiveShell (terminal JLine)
    ├── FararoniCore (orquestador)
    ├── SovereignEventBus (InMemorySovereignBus — embebido, zero-config)
    ├── Rabbit Client (modelo rapido)
    ├── Turtle Client (modelo experto)
    ├── ToolExecutor + ToolRegistry (33 herramientas)
    └── Recon del proyecto (detecta pom.xml, package.json, etc.)
```

El **SovereignEventBus** (InMemorySovereignBus) se levanta automaticamente
dentro de Fararoni Core. **No hay que levantar nada adicional** para modo CLI.

### Modo Headless (one-shot)

Para scripts y CI/CD:

```bash
java --enable-preview -jar fararoni-core-1.0.0.jar "Crea un archivo README.md"
```

### Argumentos CLI disponibles

| Argumento | Descripcion |
|-----------|-------------|
| `--cwd <ruta>` | Directorio de trabajo |
| `--server` | Modo servidor REST (ver seccion 7) |
| `--port <numero>` | Puerto custom para servidor (default: 7070) |
| `--version` | Mostrar version |
| `--help` | Mostrar ayuda |
| `--debug` | Modo debug |
| `"texto"` | Modo headless (one-shot) |

---

## 7. Levantar Fararoni Core Server (Modo Servidor)

El modo servidor expone una API REST + WebSocket para que plugins IDE
(IntelliJ, VS Code) o aplicaciones web se comuniquen con Fararoni.

### Iniciar

```bash
# Puerto default 7070
java --enable-preview -jar fararoni-core-1.0.0.jar --server

# Puerto custom
java --enable-preview -jar fararoni-core-1.0.0.jar --server --port 8080
```

### Que se levanta

```
Fararoni Core Server (:7070)
    ├── Servidor HTTP (Javalin)
    ├── FararoniCore (orquestador)
    ├── SovereignEventBus (InMemorySovereignBus — embebido)
    ├── OmniChannel Gateway (:7071) ← SE LEVANTA AUTOMATICAMENTE
    │   ├── RestIngressServer (recibe de sidecars)
    │   ├── HttpEgressDispatcher (envia a sidecars)
    │   └── OmniChannelRouter (rutea al Core)
    ├── SessionManager (sesiones multi-usuario)
    └── WebSocket /ws/events (live feed)
```

**IMPORTANTE**: El **OmniChannel Gateway** (puerto 7071) se levanta
**automaticamente** cuando Fararoni Core arranca en modo `--server`.
No hay que levantarlo por separado. Es un modulo que se carga via SPI
(`OmniChannelGatewayModule` implementa `FararoniModule`).

### Endpoints REST

| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| `POST` | `/api/task` | Ejecutar mision asincrona |
| `GET` | `/api/status` | Estado del servidor |
| `GET` | `/api/session/{id}` | Estado de sesion |
| `DELETE` | `/api/session/{id}` | Cerrar sesion |
| `GET` | `/health` | Health check |
| `WS` | `/ws/events?userId=X` | Live feed WebSocket |

### Verificar que esta corriendo

```bash
# Health check
curl http://localhost:7070/health

# Status
curl http://localhost:7070/api/status

# Gateway health
curl http://localhost:7071/health
```

---

## 8. Levantar OmniChannel Gateway + Sidecars

Para recibir mensajes de WhatsApp, Telegram, Discord o iMessage necesitas:

1. **Fararoni Core Server** corriendo con `--server` (levanta el Gateway automaticamente)
2. **Sidecar** del canal que quieras usar (binarios nativos SEA, incluidos en la instalacion)

### Flujo de Comunicacion

```
Usuario (WhatsApp/Telegram/etc.)
        |
        v
Sidecar (binario nativo, puerto 3000-3003)
        |
        v  POST /gateway/v1/inbound
OmniChannel Gateway (:7071)
        |
        v
SovereignEventBus
        |
        v
Fararoni Core (procesa con LLM)
        |
        v  POST /send
SovereignEventBus → HttpEgressDispatcher
        |
        v
Sidecar → Usuario
```

### 8.1 Sidecar WhatsApp (Baileys)

Puerto: **3000** | Binario: `sidecar-whatsapp`

```bash
# Terminal 1: Fararoni Core Server (incluye Gateway)
java --enable-preview -jar fararoni-core-1.0.0.jar --server

# Terminal 2: Sidecar WhatsApp (binario nativo, sin Node.js)
./bin/sidecar-whatsapp
```

Al iniciar mostrara un **codigo QR** en la terminal.
Escanealo con WhatsApp en tu telefono (Dispositivos vinculados > Vincular dispositivo).

Configuracion en `~/.fararoni/config/modules.yml`:
```yaml
channels:
  whatsapp:
    enabled: true
    trust_level: UNTRUSTED_EXTERNAL
    egress_url: "http://localhost:3000/send"
    capabilities:
      - text
      - audio
      - image
```

### 8.2 Sidecar Telegram

Puerto: **3001** | Binario: `sidecar-telegram`

```bash
# Prerequisito: Crear bot en @BotFather y obtener token
# 1. Abre Telegram, busca @BotFather
# 2. Envia /newbot, sigue instrucciones
# 3. Copia el token

# Terminal 2: Sidecar Telegram (binario nativo)
export TELEGRAM_TOKEN="tu_token_de_botfather"
./bin/sidecar-telegram
```

Configuracion:
```yaml
channels:
  telegram:
    enabled: true
    trust_level: SECURE_ENCRYPTED
    egress_url: "http://localhost:3001/send"
    capabilities:
      - text
```

### 8.3 Sidecar Discord

Puerto: **3002** | Binario: `sidecar-discord`

```bash
# Prerequisito: Crear bot en Discord Developer Portal
# 1. https://discord.com/developers/applications
# 2. New Application > Bot > Copy Token
# 3. Habilitar "Message Content Intent" en Bot settings
# 4. Invitar bot a tu servidor con permisos de lectura/escritura

# Terminal 2: Sidecar Discord (binario nativo)
export DISCORD_TOKEN="tu_token_de_bot"
./bin/sidecar-discord
```

Configuracion:
```yaml
channels:
  discord:
    enabled: true
    trust_level: SECURE_ENCRYPTED
    egress_url: "http://localhost:3002/send"
    capabilities:
      - text
```

### 8.4 Sidecar iMessage (solo macOS)

Puerto: **3003** | Binario: `sidecar-imessage`

```bash
# Prerequisito: BlueBubbles Server corriendo en tu Mac
# 1. Descargar BlueBubbles de https://bluebubbles.app
# 2. Configurar webhook apuntando a http://localhost:3003/webhook
# 3. Obtener password del servidor

# Terminal 2: Sidecar iMessage (binario nativo)
export BLUEBUBBLES_PASSWORD="tu_password"
./bin/sidecar-imessage
```

Configuracion:
```yaml
channels:
  imessage:
    enabled: true
    trust_level: SECURE_ENCRYPTED
    egress_url: "http://localhost:3003/send"
    capabilities:
      - text
```

### Resumen de Puertos

```
Puerto    Servicio                          Protocolo
------    --------                          ---------
7070      Fararoni Core Server              HTTP/WS
7071      OmniChannel Gateway              HTTP
3000      Sidecar WhatsApp (Baileys)       HTTP
3001      Sidecar Telegram                  HTTP
3002      Sidecar Discord                   HTTP
3003      Sidecar iMessage (BlueBubbles)   HTTP
11434     Ollama (servidor LLM)             HTTP
```

---

## 9. Seguridad Zero-Trust (3 Llaves)

Fararoni protege el acceso desde canales externos con un sistema de 3 niveles.
**El CLI local NO requiere autenticacion** — solo los canales remotos (WhatsApp,
Telegram, Discord, iMessage).

### Las 3 Llaves

| Llave | Mecanismo | Proposito |
|-------|-----------|-----------|
| **Llave 1 — TOTP** | Google Authenticator (6 digitos) | Identidad del dueno. Sesion de 4 horas |
| **Llave 2 — Sandbox** | Carpeta de confianza | Archivos fuera requieren `sudo` |
| **Llave 3 — BCrypt** | Master Password | Elevacion temporal de privilegios (15 min) |

### Configuracion Inicial

La seguridad se configura automaticamente en el **wizard de primera ejecucion**.
Si lo omitiste, ejecuta `/security-setup` desde el CLI.

El wizard genera:
1. Un secreto TOTP y muestra un **QR en la terminal** (escanear con Google Authenticator)
2. Solicita un **Master Password** (min 8 caracteres, hasheado con BCrypt)
3. Define la **carpeta de confianza** (default: `~/Documents/Proyectos`)

### Archivos de Seguridad

```
~/.fararoni/
  secret.bin           # Secreto TOTP (permisos rw-------)
  master.bin           # Hash BCrypt del Master Password (rw-------)
  guests.txt           # IDs de invitados autorizados
  audit/
    security.log       # Log de auditoria (LOGIN, LOGOUT, SUDO, GUEST)
  config/
    security.yml       # allowed_paths, duraciones, flags
```

### Flujo de Acceso desde Canal Externo

```
1. Escribes a Fararoni por Telegram/Discord/WhatsApp
2. El "Cadenero" (FaraSecurityInterceptor) te intercepta
3. Te pide codigo TOTP de 6 digitos
4. Lo verificas con Google Authenticator
5. ACCESO CONCEDIDO — sesion de 4 horas
6. Puedes chatear normalmente con el LLM
```

### Comandos de Seguridad (con sesion TOTP activa)

| Comando | Accion |
|---------|--------|
| `salir` / `logout` / `cerrar` | Cierra tu sesion TOTP |
| `sudo <password>` | Eleva privilegios 15 min (desactiva sandbox) |

### Canales Internos vs Externos

| Tipo | Canales | TOTP |
|------|---------|------|
| **Interno** | CLI, IntelliJ, TERMINAL_DEV | NO requiere |
| **Externo** | WhatsApp, Telegram, Discord, iMessage, Slack, Web | SI requiere |

---

## 10. Gestion de Invitados

Los invitados son usuarios autorizados por el dueno para acceder a Fararoni
desde canales externos. Operan en **modo solo-lectura**: pueden chatear con
el LLM pero **no ejecutan herramientas ni comandos de gestion**.

### Autorizar un Invitado

Desde un canal externo (con sesion TOTP activa):

```
fara autorizar discord:1474437923876245506
fara autorizar telegram:123456789
fara autorizar whatsapp:5215512345678
```

El formato es `canal:id_usuario`. El ID depende de cada plataforma:
- **Discord**: ID numerico del usuario (click derecho > Copiar ID de usuario)
- **Telegram**: ID numerico (usar @userinfobot para obtenerlo)
- **WhatsApp**: Numero con codigo de pais sin `+` (ej: `5215512345678`)

### Revocar Acceso

```
fara revocar discord:1474437923876245506
fara revocar telegram:123456789
```

### Ver Lista de Invitados

```
fara lista-invitados
```

### Gestion Manual

Tambien puedes editar directamente el archivo `~/.fararoni/guests.txt`
(un ID por linea):

```
discord:1474437923876245506
telegram:987654321
whatsapp:5215512345678
```

### Permisos de Invitados vs Dueno

| Capacidad | Dueno (TOTP) | Invitado |
|-----------|:------------:|:--------:|
| Chatear con el LLM | SI | SI |
| Ejecutar herramientas (tools) | SI | NO |
| Comandos de gestion (`fara autorizar`, `sudo`) | SI | NO |
| Cerrar sesion (`salir`, `logout`) | SI | NO |

### Auditoria

Todas las acciones de invitados se registran en `~/.fararoni/audit/security.log`:

```
2026-03-08T15:30:00 GUEST_ACCESS discord:1474437923876245506 — chat
2026-03-08T15:31:00 GUEST_AUTHORIZED telegram:987654321 — by owner
2026-03-08T16:00:00 GUEST_REVOKED telegram:987654321 — by owner
```

---

## 11. Configuracion (modules.yml)

Archivo: `~/.fararoni/config/modules.yml`

Se crea automaticamente en la primera ejecucion, o puedes crearlo manualmente:

```bash
mkdir -p ~/.fararoni/config
```

### Ejemplo minimo (solo CLI):

```yaml
# Solo necesitas esto para usar Fararoni como CLI
# El resto se autodetecta
```

No se necesita `modules.yml` para modo CLI. Fararoni autodetecta Ollama.

### Ejemplo completo (servidor + sidecars):

```yaml
gateway:
  rest:
    enabled: true
    port: 7071
    limits:
      max_payload_mb: 20
      rate_limit_capacity: 100
      rate_limit_refill: 10

channels:
  whatsapp:
    enabled: true
    trust_level: UNTRUSTED_EXTERNAL
    egress_url: "http://localhost:3000/send"
    capabilities: [text, audio, image]
    timeout_ms: 5000
    retry_count: 3

  telegram:
    enabled: true
    trust_level: SECURE_ENCRYPTED
    egress_url: "http://localhost:3001/send"
    capabilities: [text]

  discord:
    enabled: true
    trust_level: SECURE_ENCRYPTED
    egress_url: "http://localhost:3002/send"
    capabilities: [text]

  imessage:
    enabled: true
    trust_level: SECURE_ENCRYPTED
    egress_url: "http://localhost:3003/send"
    capabilities: [text]

```

### Variables de Entorno (override de config)

| Variable | Descripcion | Default |
|----------|-------------|---------|
| `LLM_SERVER_URL` | URL del servidor LLM | `http://localhost:8000` |
| `LLM_MODEL_NAME` | Modelo por defecto (Rabbit) | `qwen-coder` |
| `LLM_API_KEY` | API Key (si el servidor lo requiere) | - |
| `FARARONI_OLLAMA_URL` | URL de Ollama | `http://localhost:11434` |
| `FARARONI_TURTLE_MODEL` | Modelo Turtle (experto) | `qwen2.5-coder:32b` |
| `FARARONI_RABBIT_MODEL` | Modelo Rabbit (rapido) | `qwen2.5-coder:1.5b` |
| `FARARONI_SHOW_REASONING` | Mostrar thinking del modelo | `false` |
| `ANTHROPIC_API_KEY` | API key de Claude (opcional) | - |

---

## 12. Comandos Esenciales

### Comandos del Sistema

| Comando | Descripcion |
|---------|-------------|
| `/help` | Ver todos los comandos disponibles |
| `/status` | Estado del sistema (modelo, memoria, conexion) |
| `/reconfig` | Reconfigurar modelo Rabbit en caliente |
| `/reconfigt` | Reconfigurar modelo Turtle en caliente |
| `/vision <imagen>` | Analizar una imagen con vision multimodal |
| `/exit` | Salir |

### Comandos de Proyecto

| Comando | Descripcion |
|---------|-------------|
| `/load <archivo>` | Cargar archivo al contexto |
| `/unload` | Descargar contexto |
| `/context` | Ver contexto actual |
| `/git` | Operaciones git |

### Lenguaje Natural

Fararoni entiende instrucciones en espanol:

```
> hola analiza el proyecto para ver si hay errores
> crea un endpoint REST para gestionar usuarios
> usa start_mission para crear el microservicio de pagos
> sube los cambios a git
```

---

## 13. Verificacion y Troubleshooting

### Verificar que todo funciona

```bash
# 1. Ollama corriendo?
curl http://localhost:11434/api/tags
# Debe listar modelos disponibles

# 2. Fararoni CLI funciona?
java --enable-preview -jar fararoni-core-1.0.0.jar --version
# Debe mostrar: Fararoni Core v1.0.0

# 3. Gateway corriendo? (solo modo --server)
curl http://localhost:7071/health

# 4. Sidecar WhatsApp corriendo?
curl http://localhost:3000/health
```

### Errores Comunes

| Error | Causa | Solucion |
|-------|-------|----------|
| `Unknown VM option 'StructuredTaskScope'` | Falta `--enable-preview` | Agregar `--enable-preview` al comando java |
| `Connection refused :11434` | Ollama no esta corriendo | Ejecutar `ollama serve` |
| `Error: 500` del modelo | Modelo no cargado o sin VRAM | Verificar `ollama list` y RAM disponible |
| `pom.xml not found` | Fararoni no encuentra proyecto | Usar `--cwd` o navegar al directorio del proyecto |
| QR de WhatsApp no aparece | Sidecar no arranca | Verificar que `sidecar-whatsapp` tenga permisos de ejecucion |

### Logs

```bash
# Trace log de Fararoni (routing, tools, errores)
tail -f /tmp/fararoni-trace.log

# Logs de Java
# Se imprimen en stderr del proceso
```

---

## 14. Siguiente Paso

- **Manual de Usuario**: `docs/MANUAL_USUARIO.md` — comandos avanzados, casos de uso
- **Guia de Compilacion**: `docs/GUIA-COMPILACION-EMPAQUETADO.md` — build detallado
- **Guia de Instalacion**: `docs/GUIA-INSTALACION-RAPIDA.md` — instalacion detallada

### Proximos manuales (por canal):

- Manual de Usuario WhatsApp — como interactuar via WhatsApp
- Manual de Usuario Telegram — como interactuar via Telegram

---

*Fararoni v1.0.0 — Sovereign AI Agent Orchestrator*
*Licencia: Apache 2.0*
*Autor: Eber Cruz*
