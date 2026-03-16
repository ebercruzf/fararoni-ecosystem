# Manual de Usuario: Discord Bot

## Version: 1.0
## Audiencia: Desarrolladores, Administradores de sistemas

---

## Indice

1. [Introduccion](#1-introduccion)
2. [Requisitos Previos](#2-requisitos-previos)
3. [Crear Bot en Discord](#3-crear-bot-en-discord)
4. [Instalar el Sidecar](#4-instalar-el-sidecar)
5. [Configurar Fararoni](#5-configurar-fararoni)
6. [Puesta en Marcha](#6-puesta-en-marcha)
7. [Verificar Funcionamiento](#7-verificar-funcionamiento)
8. [Modos de Operacion](#8-modos-de-operacion)
9. [Seguridad](#9-seguridad)
10. [Troubleshooting](#10-troubleshooting)
11. [Comandos Utiles](#11-comandos-utiles)

---

## 1. Introduccion

### Que es el Canal de Discord?

El canal de Discord permite conectar un bot de Discord con Fararoni, permitiendo
que los usuarios interactuen con tu asistente IA a traves de la aplicacion Discord,
tanto en mensajes directos (DMs) como en servidores.

### Arquitectura

```
+----------------+     +-----------------+     +----------------+     +----------------+
|   Usuario      | --> |   Discord API   | --> |  Sidecar DC    | --> |   Fararoni     |
|   Discord      |     |   (Bot Gateway) |     |   (:3002)      |     |   Gateway      |
+----------------+     +-----------------+     +----------------+     |   (:7071)      |
                                                                      +----------------+
                                                                             |
                                                                             v
                                                                      +----------------+
                                                                      |  Agentes LLM   |
                                                                      +----------------+
```

### Ventajas de Discord

| Caracteristica | Valor |
|----------------|-------|
| Costo | **GRATIS** |
| API | Oficial y estable |
| Riesgo de ban | Ninguno |
| Rate limits | Generosos |
| Velocidad | Muy rapida |
| Setup | 10 minutos |
| Comunidad | Ideal para gaming/dev |

### Comparativa con Otros Canales

| Aspecto | Discord | Telegram | WhatsApp Baileys |
|---------|---------|----------|------------------|
| Costo | Gratis | Gratis | Gratis |
| Riesgo de ban | No | No | Si |
| API | Oficial | Oficial | No oficial |
| Setup | 10 min | 5 min | 15 min |
| Servidores/Grupos | Si (con mencion) | Solo DMs | Solo DMs |
| Rich embeds | Si | No | No |

---

## 2. Requisitos Previos

### 2.1 Software Necesario

- [ ] Node.js 18 o superior
- [ ] npm (viene con Node.js)
- [ ] Fararoni Core instalado
- [ ] Java 25+ con --enable-preview

### 2.2 Cuentas

- [ ] Cuenta de Discord
- [ ] Acceso al Discord Developer Portal

### 2.3 Puertos

| Puerto | Servicio | Estado Requerido |
|--------|----------|------------------|
| 7071 | Gateway REST | Abierto (local) |
| 3002 | Sidecar Discord | Abierto (local) |

### Verificar Requisitos

```bash
# Verificar Node.js (debe ser 18+)
node --version

# Verificar npm
npm --version

# Verificar Java
java --version

# Verificar que Fararoni este corriendo
curl http://localhost:7071/gateway/v1/health
```

---

## 3. Crear Bot en Discord

### Paso 3.1: Acceder al Developer Portal

1. Abre tu navegador
2. Ve a: **https://discord.com/developers/applications**
3. Inicia sesion con tu cuenta de Discord
4. Click en **"New Application"** (boton azul, esquina superior derecha)

### Paso 3.2: Crear la Aplicacion

1. Ingresa un nombre para tu aplicacion (ej: `Fararoni Bot`)
2. Acepta los terminos de servicio
3. Click en **"Create"**

### Paso 3.3: Crear el Bot

1. En el menu lateral izquierdo, click en **"Bot"**
2. Click en **"Add Bot"**
3. Confirma clickeando **"Yes, do it!"**

### Paso 3.4: Configurar Intents (MUY IMPORTANTE)

En la misma pagina de Bot, baja hasta **"Privileged Gateway Intents"** y activa:

- [x] **MESSAGE CONTENT INTENT** (OBLIGATORIO)
- [x] PRESENCE INTENT (opcional)
- [x] SERVER MEMBERS INTENT (opcional)

> **CRITICO:** Sin "Message Content Intent", el bot recibira mensajes VACIOS.
> El bot no podra leer el contenido de los mensajes sin esta opcion activada.

Click en **"Save Changes"** al final de la pagina.

### Paso 3.5: Obtener el Token

1. En la seccion **"Bot"**, busca **"Token"**
2. Click en **"Reset Token"** (si es la primera vez) o **"Copy"**
3. **GUARDA ESTE TOKEN** en un lugar seguro

```
Ejemplo de formato de token:
TU_BOT_TOKEN_AQUI
```

> **ADVERTENCIA:** Nunca compartas tu token. Si lo expones accidentalmente,
> ve al Developer Portal y regeneralo inmediatamente con "Reset Token".

### Paso 3.6: Invitar el Bot a tu Servidor

1. En el menu lateral, click en **"OAuth2"** -> **"URL Generator"**
2. En **"SCOPES"**, selecciona:
   - [x] `bot`
3. En **"BOT PERMISSIONS"**, selecciona:
   - [x] Send Messages
   - [x] Read Message History
   - [x] View Channels
4. Copia la **URL generada** al final de la pagina
5. Abre esa URL en tu navegador
6. Selecciona el servidor donde quieres agregar el bot
7. Click en **"Authorize"**
8. Completa el captcha si aparece

---

## 4. Instalar el Sidecar

### Paso 4.1: Ubicar el Directorio

El sidecar de Discord esta en:

```bash
cd fararoni-sidecar-discord
```

### Paso 4.2: Instalar Dependencias

```bash
npm install
```

Esto instalara:
- `discord.js` - Libreria oficial de Discord
- `express` - Servidor HTTP para egress
- `axios` - Cliente HTTP para conectar con Gateway
- `dotenv` - Manejo de variables de entorno

### Paso 4.3: Crear Archivo de Configuracion

```bash
# Copiar el archivo de ejemplo
cp .env.example .env

# Editar con tu editor favorito
nano .env
# o
code .env
```

### Paso 4.4: Configurar Variables

Edita el archivo `.env`:

```bash
# Token del bot de Discord (REQUERIDO)
DISCORD_TOKEN=tu_token_del_developer_portal

# URL del Gateway REST de Fararoni
GATEWAY_URL=http://localhost:7071/gateway/v1/inbound

# Puerto del servidor HTTP para Egress
SIDECAR_PORT=3002

# Permitir mensajes en servidores (requiere mencion del bot)
ALLOW_GUILDS=false

# Lista de IDs de usuarios permitidos (separados por coma)
# Dejar vacio para permitir todos
ALLOWED_USERS=

# Modo debug
DEBUG=false
```

---

## 5. Configurar Fararoni

### Paso 5.1: Verificar Gateway

El Gateway REST debe tener configurado el canal Discord.

Verifica en `~/.fararoni/config/modules.yml`:

```yaml
gateway:
  rest:
    enabled: true
    port: 7071

channels:
  # ... otros canales ...

  discord:
    enabled: true
    egress_url: "http://localhost:3002/send"
```

### Paso 5.2: Reiniciar Fararoni (si modificaste config)

```bash
# Si Fararoni esta corriendo, el Hot Reload deberia detectar cambios
# Si no, reinicia el servidor
./start-server.sh
```

---

## 6. Puesta en Marcha

### Prerequisitos

Antes de iniciar, asegurate de haber completado:

- [x] Crear bot en Discord Developer Portal (Seccion 3)
- [x] Activar **MESSAGE CONTENT INTENT** (Paso 3.4)
- [x] Obtener y guardar el token en `.env` (Paso 3.5)
- [x] Instalar dependencias con `npm install` (Seccion 4)

### Paso 6.1: Iniciar Fararoni Gateway (si no esta corriendo)

```bash
# Terminal 1: Fararoni Core
cd /ruta/a/fararoni
./start-server.sh
```

> **Nota:** El Gateway debe estar corriendo en el puerto configurado en tu `.env`
> (por defecto 7071). Verifica con: `curl http://localhost:7071/gateway/v1/health`

### Paso 6.2: Iniciar el Sidecar Discord

```bash
# Terminal 2: Sidecar Discord
cd fararoni-sidecar-discord
npm start
```

### Salida Esperada

```
============================================================
  FARARONI SIDECAR - DISCORD (FASE 71.6)
============================================================

[INFO]  12:34:56 [HTTP] Servidor Egress en puerto 3002
[INFO]  12:34:57 [DISCORD] Bot conectado: FararoniBot#1234
[INFO]  12:34:57 [DISCORD] Servidores: 2
[INFO]  12:34:57 [GATEWAY] Enviando a: http://localhost:7071/gateway/v1/inbound
[INFO]  12:34:57 [SEGURIDAD] Solo DMs (mensajes directos)
[INFO]  12:34:57 [READY] Sidecar listo para recibir mensajes

------------------------------------------------------------
```

### Modo Debug

Para ver logs detallados:

```bash
npm run dev
# o
DEBUG=true npm start
```

---

## 7. Verificar Funcionamiento

### 7.1 Preparacion: Invitar el Bot a un Servidor

> **IMPORTANTE:** No puedes buscar un bot nuevo en la barra de busqueda de Discord
> si no compartes un servidor con el. Discord bloquea esto por seguridad.
> Primero debes invitar el bot a un servidor.

#### Paso 7.1.1: Crear Servidor de Pruebas (si no tienes uno)

1. Abre Discord
2. En la barra izquierda, click en el boton **`+`** (Add a Server)
3. Selecciona **"Create My Own"** -> **"For me and my friends"**
4. Ponle un nombre (ej: `Laboratorio Fararoni`)
5. Click en **"Create"**

#### Paso 7.1.2: Generar Link de Invitacion del Bot

1. Ve a https://discord.com/developers/applications
2. Selecciona tu aplicacion
3. Click en **"OAuth2"** en el menu lateral
4. Baja hasta **"OAuth2 URL Generator"**
5. En **SCOPES**, marca:
   - [x] `bot`
6. En **BOT PERMISSIONS** (aparece abajo), marca:
   - [x] Send Messages
   - [x] Read Message History
   - [x] View Channels
7. Copia la **URL generada** al final de la pagina

#### Paso 7.1.3: Invitar el Bot al Servidor

1. Pega la URL copiada en tu navegador
2. Selecciona tu servidor de pruebas (ej: `Laboratorio Fararoni`)
3. Click en **"Authorize"**
4. Completa el captcha si aparece
5. Veras el mensaje "Authorized" - el bot ya esta en tu servidor

### 7.2 Test de Mensaje Directo (DM)

Ahora que el bot esta en tu servidor, puedes enviarle un DM.

> **IMPORTANTE:** No escribas en el canal del servidor. Debes abrir una
> conversacion privada (DM) con el bot.

#### Paso 7.2.1: Mostrar el Panel de Miembros

1. Entra a tu servidor de pruebas (click en el icono del servidor en la barra izquierda)
2. Mira la **esquina superior derecha** de Discord
3. Busca el icono con **dos siluetas de personas**
4. Si no ves el panel de miembros a la derecha, click en ese icono para desplegarlo

#### Paso 7.2.2: Encontrar el Bot

1. En el panel derecho (Lista de Miembros), busca tu bot
2. Tendra una etiqueta que dice **"BOT"** junto a su nombre

#### Paso 7.2.3: Abrir Mensaje Directo

**Opcion A - Click Derecho:**
1. Click derecho sobre el nombre del bot
2. En el menu contextual, selecciona **"Message"** (o "Mensaje")

**Opcion B - Click Normal:**
1. Click normal (izquierdo) sobre el nombre del bot
2. Se abre su tarjeta de perfil
3. Abajo veras un recuadro: **"Enviar mensaje a @TuBot"**
4. Click en ese recuadro

#### Paso 7.2.4: Enviar el Mensaje

1. Discord te llevara automaticamente a la seccion de **Mensajes Directos**
   (icono azul de Discord arriba a la izquierda)
2. Tendras un chat privado abierto con tu bot
3. Escribe: `Hola`
4. Presiona **Enter**

#### Paso 7.2.5: Verificar en la Terminal

Al momento de enviar el mensaje, revisa la terminal donde corre el sidecar.

**Deberas ver:**
```
[INFO]  15:45:xx [INGRESS] TuUsuario (DM) -> "Hola" (HTTP 202)
```

**Y cuando el bot responda:**
```
[INFO]  15:45:xx [EGRESS] 123456789 <- "Hola! En que puedo ayudarte?"
```

> **Si no ves ningun log:** Verifica que el bot tenga "MESSAGE CONTENT INTENT"
> activado en el Developer Portal (ver Troubleshooting).

### 7.3 Entendiendo los DMs en Discord

#### La Regla del "Primer Contacto"

Discord **no permite** enviar mensajes privados a un usuario o bot con el que
no compartes al menos un servidor. Es una medida anti-spam inquebrantable.

Por lo tanto, **siempre necesitas usar el servidor como puente temporal** para
hacer el primer contacto:

1. Invitas al bot a tu servidor de pruebas
2. Click derecho en su nombre -> "Message" -> Envias "Hola"
3. El canal DM queda creado **para siempre**

#### La Bandeja de Mensajes Directos

Una vez que enviaste el primer mensaje, el canal DM queda creado permanentemente.

**A partir de ese momento:**

1. Ve a la esquina **superior izquierda** de Discord
2. Click en el **icono morado/azul de Discord** (Home)
3. Veras tu bot en la lista de **Mensajes Directos**
4. Ya no necesitas volver al servidor - chatea desde ahi directamente

```
┌─────────────────────────────────────────────────────────────┐
│  [Discord Icon]  Mensajes Directos                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  🤖 MiBot                    Hola! En que puedo ayudarte?  │
│  👤 Amigo1                   nos vemos manana              │
│  👤 Amigo2                   ok perfecto                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Ventajas:**
- Funciona desde celular o computadora
- El chat persiste aunque expulses al bot del servidor
- Experiencia identica a chatear con un amigo

#### Como lo Maneja el Sidecar

El sidecar detecta automaticamente si el mensaje viene de un servidor o un DM:

```javascript
conversationId: msg.guildId || 'dm'
```

- **Mensaje en servidor:** `msg.guildId` = ID del servidor
- **Mensaje en DM:** `msg.guildId` = null, se usa `'dm'`

El `senderId` es el ID del canal de chat. Cuando el LLM genera la respuesta,
el bot sabe exactamente donde responder (en tu ventana privada, no en un canal publico).

### 7.5 Test de Health Check

```bash
curl http://localhost:3002/health
```

Respuesta esperada:

```json
{
  "status": "healthy",
  "service": "fararoni-sidecar-discord",
  "channel": "discord",
  "port": 3002,
  "gatewayUrl": "http://localhost:7071/gateway/v1/inbound",
  "uptime": 123,
  "timestamp": "2026-02-20T12:34:56.789Z",
  "bot": {
    "id": "123456789012345678",
    "username": "FararoniBot",
    "tag": "FararoniBot#1234"
  }
}
```

### 7.6 Test de Estado Detallado

```bash
curl http://localhost:3002/status
```

---

## 8. Modos de Operacion

### 8.1 Modo DM (Por Defecto)

El bot **SOLO** responde a mensajes directos.

```bash
# .env
ALLOW_GUILDS=false
```

**Comportamiento:**
- Responde a cualquier DM
- Ignora todos los mensajes en servidores

### 8.2 Modo DM + Servidores (con Mencion)

El bot responde a DMs y a mensajes en servidores **solo si lo mencionan**.

```bash
# .env
ALLOW_GUILDS=true
```

**Comportamiento:**
- Responde a cualquier DM
- En servidores, solo responde si escribes `@FararoniBot hola`
- Ignora mensajes en servidores que no lo mencionen

### 8.3 Modo Restringido (Allowlist)

Solo ciertos usuarios pueden usar el bot.

```bash
# .env
ALLOWED_USERS=123456789012345678,987654321098765432
```

**Como obtener tu User ID:**
1. En Discord, ve a Configuracion -> Avanzado
2. Activa "Modo desarrollador"
3. Click derecho en cualquier usuario -> "Copiar ID"

---

## 9. Seguridad

### 9.1 Proteccion del Token

**NUNCA:**
- Subas el token a GitHub
- Compartas el token en chats
- Incluyas el token en codigo fuente

**SIEMPRE:**
- Usa archivos `.env` (estan en `.gitignore`)
- Regenera el token si lo expones
- Usa variables de entorno en produccion

### 9.2 Filtros de Seguridad Implementados

| Filtro | Descripcion |
|--------|-------------|
| Anti-bot | Ignora mensajes de otros bots |
| Anti-loop | Ignora sus propios mensajes |
| Guild filter | Solo responde en servidores si lo mencionan |
| Allowlist | Restringe usuarios autorizados |

### 9.3 Regenerar Token Comprometido

1. Ve a https://discord.com/developers/applications
2. Selecciona tu aplicacion
3. Ve a "Bot"
4. Click en "Reset Token"
5. Actualiza tu archivo `.env`
6. Reinicia el sidecar

---

## 10. Troubleshooting

### Error: "TOKEN_INVALID" o "invalid token"

**Causa:** El token es incorrecto o fue regenerado.

**Solucion:**
1. Ve al Developer Portal
2. Copia el token correcto
3. Actualiza `.env`
4. Reinicia el sidecar

### Error: "Used disallowed intents" o "Intents"

**Causa:** No activaste los "Privileged Gateway Intents" en el Developer Portal.

**Solucion:**

1. Ve a https://discord.com/developers/applications
2. Selecciona tu aplicacion
3. Click en **"Bot"** en el menu lateral
4. Baja hasta **"Privileged Gateway Intents"**
5. Activa estas opciones:

```
Privileged Gateway Intents
─────────────────────────────────────
[x] PRESENCE INTENT          (opcional)
[x] SERVER MEMBERS INTENT    (opcional)
[x] MESSAGE CONTENT INTENT   <-- OBLIGATORIO
─────────────────────────────────────
                      [Save Changes]
```

6. Click en **"Save Changes"**
7. Reinicia el sidecar:
   ```bash
   npm run dev
   ```

> **IMPORTANTE:** Sin "MESSAGE CONTENT INTENT", el bot no puede leer
> el contenido de los mensajes. Esta es la causa mas comun de errores.

### El bot recibe mensajes vacios

**Causa:** Message Content Intent desactivado.

**Solucion:** Igual que el error anterior.

### Error: "ECONNREFUSED" al Gateway

**Causa:** Fararoni Gateway no esta corriendo o la URL es incorrecta.

**Solucion:**
```bash
# 1. Verificar que el Gateway este activo (usa el puerto que configuraste)
curl http://localhost:7071/gateway/v1/health

# 2. Si no responde, iniciar Fararoni
./start-server.sh

# 3. Verificar la URL en tu .env
cat .env | grep GATEWAY_URL
```

> **Nota:** El puerto 7071 es el valor por defecto. Si configuraste otro puerto
> en `~/.fararoni/config/modules.yml`, usa ese valor en `GATEWAY_URL`.

### El bot no responde en servidores

**Causa:** `ALLOW_GUILDS=false` o no lo estas mencionando.

**Solucion:**
1. Cambia `ALLOW_GUILDS=true` en `.env`
2. Reinicia el sidecar
3. Menciona al bot: `@FararoniBot hola`

### Error: Puerto 3002 ocupado (EADDRINUSE)

**Causa:** Otro proceso usa el puerto.

**Solucion:**
```bash
# Ver que proceso usa el puerto
lsof -i :3002

# Matar el proceso
kill -9 <PID>

# O cambiar el puerto en .env
SIDECAR_PORT=3003
```

> **IMPORTANTE:** Si cambias el puerto del sidecar, tambien debes actualizar
> el `egress_url` en `~/.fararoni/config/modules.yml`:
> ```yaml
> channels:
>   discord:
>     egress_url: "http://localhost:3003/send"  # <- Nuevo puerto
> ```

---

## 11. Comandos Utiles

### Iniciar el Sidecar

```bash
# Normal
npm start

# Con debug
npm run dev

# Con variables inline
DISCORD_TOKEN=xxx GATEWAY_URL=http://localhost:7071/gateway/v1/inbound node index.js
```

### Verificar Estado

```bash
# Health check
curl http://localhost:3002/health

# Estado detallado
curl http://localhost:3002/status
```

### Logs del Sidecar

```bash
# Ver logs en tiempo real
npm start 2>&1 | tee discord.log

# Con timestamps
npm start 2>&1 | ts '[%Y-%m-%d %H:%M:%S]'
```

### Procesos

```bash
# Ver procesos del sidecar
ps aux | grep "node index.js"

# Matar proceso
pkill -f "fararoni-sidecar-discord"
```

---

## Apendice A: Estructura del Proyecto

```
fararoni-sidecar-discord/
├── index.js          # Codigo principal del sidecar
├── package.json      # Dependencias y scripts
├── .env.example      # Plantilla de configuracion
├── .env              # Configuracion local (no subir a git)
└── node_modules/     # Dependencias instaladas
```

---

## Apendice B: Flujo de Mensajes

```
1. Usuario envia "Hola" por Discord DM
2. Discord API notifica al bot (WebSocket)
3. Sidecar recibe evento messageCreate
4. Filtros de seguridad validan el mensaje
5. Sidecar envia POST a Gateway (:7071/gateway/v1/inbound)
6. Gateway publica en SovereignEventBus
7. OmniChannelRouter enruta al agente
8. Agente genera respuesta con LLM
9. Gateway envia POST al Sidecar (:3002/send)
10. Sidecar envia respuesta a Discord API
11. Usuario ve la respuesta en Discord
```

---

## Apendice C: Esquema UniversalMessage

El sidecar envia mensajes al Gateway con este formato:

```json
{
  "messageId": "dc-1234567890",
  "channelId": "discord",
  "senderId": "1234567890123456789",
  "conversationId": "dm",
  "type": "TEXT",
  "textContent": "Hola, como estas?",
  "mediaContent": null,
  "mimeType": null,
  "metadata": {
    "profileName": "Usuario123",
    "globalName": "Usuario",
    "discriminator": "1234",
    "userId": "9876543210987654321",
    "isDM": true,
    "guildName": null
  },
  "timestamp": "2026-02-20T12:34:56.789Z"
}
```

---

## Contacto y Soporte

- **Documentacion**: `docs/` en el repositorio

---

*Documento generado para FASE 71.6 - Fararoni Sidecar Discord*
