# Fararoni — Guia del Modo Servidor

**Version: 1.0.0**

> Esta guia explica como iniciar Fararoni en modo servidor para conectar
> plugins IDE (IntelliJ), sidecars de mensajeria (WhatsApp, Telegram, Discord, iMessage)
> y aplicaciones externas via REST/WebSocket.

---

## Requisitos

- Fararoni instalado y configurado (ver `GUIA-INSTALACION-RAPIDA.md`)
- Ollama corriendo (`ollama serve`)

---

## Paso 1 — Iniciar el servidor

Navega a tu carpeta de confianza (la que configuraste en el Sandbox) y ejecuta:

```bash
cd ~/Documents/Proyectos
fararoni --server
```

### Que aparece al iniciar

Primero el banner del modo servidor:

```
╔═══════════════════════════════════════════════════════════════╗
║           FARARONI SERVER - Modo IDE Plugin                   ║
╚═══════════════════════════════════════════════════════════════╝

[SERVER] Inicializando componentes...
```

Luego Fararoni carga todos los subsistemas (los mismos que en modo CLI):

```
[FARARONI] Enlace primario estable.
[FARARONI]  Persistencia Blindada ACTIVA (Outbox Pattern: sovereign_bus.db)
[FARARONI]  Repositorio de canales inicializado
[FARARONI]  MessagingChannelManager iniciado
[FARARONI]  Security Auth ACTIVO (TOTP + Sandbox + BCrypt)
[FARARONI]  GalvanicAgent iniciado en Bus Soberano
[FARARONI]  QuartermasterAgent iniciado en Bus Soberano
[FARARONI]  AgentTemplateManager iniciado (10 agentes activos, LLM inyectado)
[FARARONI]  SovereignMissionEngine activo
[RECOVERY] Servicio de Recuperacion de Desastres iniciado
[SAFETY] FileSystemIntentListener activo (Write + Restore)
[TOOL-REGISTRY] Herramientas base: 36+
 Dynamic Tool Prompting activado
```

Finalmente, los componentes exclusivos del modo servidor:

```
[SERVER] ✓ HyperNativeKernel inicializado (via FararoniCore)
[SERVER] ✓ SessionManager configurado (Multi-Tenant)
[SERVER] ✓ Autenticacion: DESACTIVADA (desarrollo)
```

### Panel de servidor activo

Cuando todo esta listo, aparece el panel con los endpoints disponibles:

```
╔═══════════════════════════════════════════════════════════════╗
║  [ACTIVE] SERVIDOR ACTIVO                                     ║
╠═══════════════════════════════════════════════════════════════╣
║  URL Base:    http://localhost:7070                           ║
║                                                               ║
║  Endpoints REST:                                              ║
║    POST   /api/chat           - Chat sincronico (IDE)         ║
║    POST   /api/task           - Ejecutar mision (async)       ║
║    GET    /api/status         - Estado del servidor           ║
║    GET    /api/session/{id}   - Estado de sesion              ║
║    DELETE /api/session/{id}   - Cerrar sesion                 ║
║    GET    /health             - Health check                  ║
║                                                               ║
║  WebSocket:                                                   ║
║    WS     /ws/events?userId=X - Live feed de eventos          ║
║                                                               ║
║  Presiona CTRL+C para detener el servidor                     ║
╚═══════════════════════════════════════════════════════════════╝
```

El servidor queda corriendo en primer plano. **No cierres esta terminal** —
dejala abierta mientras uses Fararoni en modo servidor.

> **Nota**: Es posible que veas lineas `[DEBUG]` como
> `Entrando a CountDownLatch.await() - servidor deberia mantenerse vivo`.
> Esto es normal — significa que el servidor esta activo y esperando conexiones.
>
> **El OmniChannel Gateway (puerto 7071)** se levanta automaticamente junto con
> el servidor. En los logs aparece:
> ```
> [MODULE-REGISTRY] Found module: gateway-rest-omnichannel
> [MODULE-REGISTRY] Loaded 1/1 modules
> [MODULE-REGISTRY] Started: gateway-rest-omnichannel
> ```

---

## Paso 2 — Verificar que funciona

Abre una **terminal nueva** y ejecuta:

```bash
# Health check del Core Server
curl http://localhost:7070/health
# {"status":"healthy"}

# Status del servidor
curl http://localhost:7070/api/status

# Health check del Gateway
curl http://localhost:7071/gateway/v1/health
# {"status":"healthy","port":7071,"activeClients":0,"throttleRate":0.0000}
```

---

## Paso 3 — Que se levanta automaticamente

Al iniciar con `--server`, Fararoni levanta dos servicios:

| Servicio | Puerto | Descripcion |
|----------|--------|-------------|
| **Core Server** | 7070 | API REST + WebSocket para plugins IDE y aplicaciones |
| **OmniChannel Gateway** | 7071 | HTTP ingress/egress para sidecars de mensajeria |

El Gateway se levanta automaticamente — no hay que iniciarlo por separado.

---

## Paso 4 — Conectar Sidecars de Mensajeria

Con el servidor corriendo, abre una **terminal nueva** para cada canal que quieras activar.
Los sidecars son binarios nativos — no requieren Node.js ni npm.

### WhatsApp

```bash
# Terminal 2
~/Fararoni/bin/sidecar-whatsapp
```

Al iniciar aparece un **codigo QR** en la terminal. Escanealo con WhatsApp:
1. Abre WhatsApp en tu telefono
2. Ve a **Configuracion → Dispositivos vinculados → Vincular dispositivo**
3. Escanea el QR

La sesion se guarda localmente — no vuelve a pedir QR en futuros inicios.

Logs esperados:
```
============================================================
  FARARONI WhatsApp Sidecar
============================================================
[INFO]  13:28:40 [HTTP] Servidor escuchando en puerto 3000
[INFO]  13:28:40 [BAILEYS] Iniciando conexion...
[INFO]  13:28:41 [BAILEYS] QR Code generado - Escanea con WhatsApp
```

### Telegram

Requiere un token de **@BotFather**:
1. Abre Telegram y busca `@BotFather`
2. Envia `/newbot` y sigue las instrucciones
3. Copia el token que te da

```bash
# Terminal 3
export TELEGRAM_TOKEN="tu_token_de_botfather"
~/Fararoni/bin/sidecar-telegram
```

Logs esperados:
```
============================================================
  FARARONI SIDECAR - TELEGRAM
============================================================
[INFO]  13:18:39 [HTTP] Servidor Egress en puerto 3001
```

### Discord

Requiere un token del **Developer Portal** de Discord:
1. Ve a https://discord.com/developers/applications
2. Crea una aplicacion (o usa una existente)
3. En la seccion **Bot**, copia el token

```bash
# Terminal 4
export DISCORD_TOKEN="tu_token_del_developer_portal"
~/Fararoni/bin/sidecar-discord
```

Logs esperados:
```
============================================================
  FARARONI SIDECAR - DISCORD
============================================================
[INFO]  13:41:38 [HTTP] Servidor Egress en puerto 3002
[INFO]  13:41:39 [DISCORD] Bot conectado: tu-bot#0000
[INFO]  13:41:39 [READY] Sidecar listo para recibir mensajes
```

### iMessage (solo macOS)

Requiere **BlueBubbles** corriendo en tu Mac:

```bash
# Terminal 5
export BLUEBUBBLES_PASSWORD="tu_password"
~/Fararoni/bin/sidecar-imessage
```

---

## Paso 5 — Verificar sidecars

En otra terminal, verifica que cada sidecar responda:

```bash
# WhatsApp (puerto 3000)
curl http://localhost:3000/health

# Telegram (puerto 3001)
curl http://localhost:3001/health

# Discord (puerto 3002)
curl http://localhost:3002/health

# iMessage (puerto 3003)
curl http://localhost:3003/health
```

Cada health check devuelve el estado de conexion del canal, por ejemplo:
```json
{"status":"healthy","connection":"connected","channelId":"telegram","port":3001}
```

---

## Flujo de comunicacion

```
Usuario (WhatsApp/Telegram/Discord/iMessage)
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
Fararoni Core (:7070) — procesa con LLM
        |
        v  POST /send
SovereignEventBus → HttpEgressDispatcher
        |
        v
Sidecar → Usuario
```

### Ejemplo real verificado (Telegram)

```
Usuario Telegram → "Hola"
        |
        v
Sidecar Telegram (:3001) → POST /gateway/v1/inbound
        |
        v
OmniChannel Gateway (:7071)
        |
        v
SovereignEventBus
        |
        v
Fararoni Core (:7070) → LLM
        |
        v
"¡Hola! ¿En que puedo ayudarte hoy?" → Telegram
```

Logs del sidecar durante el flujo:

```
[INFO]  13:18:39 [HTTP] Servidor Egress en puerto 3001
[INFO]  13:18:40 [INGRESS] 1568104683 -> "Hola" (HTTP 202)
[INFO]  13:18:48 [EGRESS] 1568104683 <- "¡Hola! ¿En que puedo ayudarte hoy?"
```

Los tres servicios funcionan en conjunto:
- **Core** (`:7070`) — procesa el mensaje con el LLM
- **Gateway** (`:7071`) — enruta ingress/egress entre sidecars y core
- **Sidecar Telegram** (`:3001`) — recibe y envia mensajes a Telegram

---

## Endpoints REST (referencia)

### Chat sincronico (para plugins IDE)

```bash
curl -X POST http://localhost:7070/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "analiza este proyecto", "userId": "ide-user"}'
```

### Ejecutar mision asincrona

```bash
curl -X POST http://localhost:7070/api/task \
  -H "Content-Type: application/json" \
  -d '{"task": "crea un microservicio de pagos", "userId": "user1"}'
```

### Estado del servidor

```bash
curl http://localhost:7070/api/status
```

### WebSocket (live feed)

Conectar a `ws://localhost:7070/ws/events?userId=tu-usuario` para recibir
eventos en tiempo real (respuestas del LLM, progreso de misiones, etc.).

---

## Resumen de puertos

| Puerto | Servicio | Protocolo |
|--------|----------|-----------|
| 7070 | Fararoni Core Server | HTTP/WS |
| 7071 | OmniChannel Gateway | HTTP |
| 3000 | Sidecar WhatsApp | HTTP |
| 3001 | Sidecar Telegram | HTTP |
| 3002 | Sidecar Discord | HTTP |
| 3003 | Sidecar iMessage | HTTP |
| 11434 | Ollama | HTTP |

---

## Detener el servidor

Presiona **Ctrl+C** en la terminal donde corre el servidor.
Esto detiene el Core y el Gateway.

Para detener los sidecars, presiona Ctrl+C en cada terminal donde corren,
o si los registraste como LaunchAgents:

```bash
launchctl unload ~/Library/LaunchAgents/com.fararoni.sidecar-*.plist
launchctl unload ~/Library/LaunchAgents/com.fararoni.core.plist
```

---

## Problemas comunes

| Problema | Solucion |
|----------|----------|
| `Connection refused :7070` | El servidor no esta corriendo. Ejecuta `fararoni --server` |
| `Connection refused :7071` | El Gateway no se levanto. Verifica logs del servidor |
| `Connection refused :11434` | Ollama no esta corriendo. Ejecuta `ollama serve` |
| Sidecar no conecta al Gateway | Verifica que el servidor este corriendo en puerto 7071 |
| Puerto 7070 ya en uso | Usa otro puerto: `fararoni --server --port 8080` |

---

*Fararoni v1.0.0 — Sovereign AI Agent Orchestrator*
*Licencia: Apache 2.0*
*Autor: Eber Cruz*
