# Manual de Usuario: Telegram Bot

## Version: 1.0
## Audiencia: Desarrolladores, Administradores de sistemas

---

## Indice

1. [Introduccion](#1-introduccion)
2. [Requisitos Previos](#2-requisitos-previos)
3. [Crear Bot en Telegram](#3-crear-bot-en-telegram)
4. [Instalar el Sidecar](#4-instalar-el-sidecar)
5. [Configurar Fararoni](#5-configurar-fararoni)
6. [Puesta en Marcha](#6-puesta-en-marcha)
7. [Verificar Funcionamiento](#7-verificar-funcionamiento)
8. [Seguridad](#8-seguridad)
9. [Troubleshooting](#9-troubleshooting)
10. [Comandos Utiles](#10-comandos-utiles)

---

## 1. Introduccion

### Que es el Canal de Telegram?

El canal de Telegram permite conectar un bot de Telegram con Fararoni, permitiendo
que los usuarios interactuen con tu asistente IA a traves de la aplicacion Telegram.

### Arquitectura

```
+----------------+     +-----------------+     +----------------+     +----------------+
|   Usuario      | --> |  Telegram API   | --> |   Sidecar TG   | --> |   Fararoni     |
|   Telegram     |     |  (Bot Father)   |     |   (:3001)      |     |   Gateway      |
+----------------+     +-----------------+     +----------------+     |   (:7071)      |
                                                                      +----------------+
                                                                             |
                                                                             v
                                                                      +----------------+
                                                                      |  Agentes LLM   |
                                                                      +----------------+
```

### Ventajas de Telegram

| Caracteristica | Valor |
|----------------|-------|
| Costo | **GRATIS** (sin limites) |
| API | Oficial y estable |
| Riesgo de ban | Ninguno |
| Cifrado | End-to-end |
| Velocidad | Muy rapida |
| Setup | 5 minutos |

### Comparativa con WhatsApp

| Aspecto | Telegram | WhatsApp Baileys | WhatsApp Enterprise |
|---------|----------|------------------|---------------------|
| Costo | Gratis | Gratis | Pago por mensaje |
| Riesgo de ban | No | Si | No |
| API | Oficial | No oficial | Oficial |
| Setup | 5 min | 15 min | 2-5 dias |
| Usuarios | 900M+ | 2B+ | 2B+ |

---

## 2. Requisitos Previos

### 2.1 Software Necesario

- [ ] Node.js 18 o superior
- [ ] npm (viene con Node.js)
- [ ] Fararoni Core instalado
- [ ] Java 25+ con --enable-preview

### 2.2 Cuentas

- [ ] Cuenta de Telegram (en tu celular o escritorio)

### 2.3 Puertos

| Puerto | Servicio | Estado Requerido |
|--------|----------|------------------|
| 7071 | Gateway REST | Abierto (local) |
| 3001 | Sidecar Telegram | Abierto (local) |

### Verificar Requisitos

```bash
# Verificar Node.js (debe ser 18+)
node --version

# Verificar npm
npm --version

# Verificar Java
java --version
```

### 2.4 Cifrado de Canales (Encryption Key)

Los tokens del bot de Telegram se almacenan cifrados en la base de datos local.

| Escenario | Key env var | DEV_MODE | Resultado |
|---|---|---|---|
| Produccion con key | `"abc123..."` | `false` | Usa la key del env |
| Produccion sin key | (vacia) | `false` | `LOG.severe` advierte, sin cifrado |
| Desarrollo con key | `"abc123..."` | `true` | Usa la key del env |
| Desarrollo sin key | (vacia) | `true` | Auto-genera key AES-256 persistente en `~/.fararoni/config/.dev-encryption-key` |

**Produccion:**
```bash
export FARARONI_CHANNELS_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export FARARONI_ENV=production
```

**Desarrollo local (no requiere configuracion extra):**
```bash
export FARARONI_DEV_MODE=true
# La key se genera automaticamente y persiste entre reinicios
```

> **Nota:** En desarrollo la key se guarda en `~/.fararoni/config/.dev-encryption-key`
> y se reutiliza en cada reinicio. En produccion debes proveer tu propia key
> y resguardarla — si la pierdes, los tokens cifrados no se pueden recuperar.

---

## 3. Crear Bot en Telegram

### Paso 3.1: Abrir BotFather

1. Abre Telegram en tu celular o escritorio
2. En el buscador, escribe **@BotFather**
3. Selecciona el usuario con la **marca de verificacion azul**
4. Inicia una conversacion con el (click en "Start" si es la primera vez)

### Paso 3.2: Crear Nuevo Bot

Envia el comando:

```
/newbot
```

BotFather te preguntara:

```
Alright, a new bot. How are we going to call it?
Please choose a name for your bot.
```

**Responde con el nombre visible** (ej: `Fararoni Asistente`).
Este es el nombre que veran los usuarios.

Luego BotFather te preguntara:

```
Good. Now let's choose a username for your bot.
It must end in `bot`. Like this, for example: TetrisBot or tetris_bot.
```

**Responde con el username** (ej: `mi_asistente_bot`).

> **IMPORTANTE:** El username DEBE terminar en `bot` y debe ser unico en todo Telegram.

### Paso 3.3: Obtener el Token

Si todo salio bien, BotFather te dara un mensaje como este:

```
Done! Congratulations on your new bot. You will find it at t.me/mi_asistente_bot.

Use this token to access the HTTP API:
1234567890:ABCdefGHIjklMNOpqrSTUvwxYZ

Keep your token secure and store it safely, it can be used by anyone
to control your bot.
```

**COPIA Y GUARDA ESTE TOKEN** - Lo necesitaras para iniciar el Sidecar.

El token tiene el formato: `NUMERO:LETRAS_Y_NUMEROS`

Ejemplo de formato (no uses este, es solo ejemplo):
```
1234567890:ABCdefGHIjklMNOpqrSTUvwxYZ
```

> **IMPORTANTE:** El token es secreto. NUNCA lo compartas publicamente ni lo subas a git.

### Paso 3.4: Ya Tienes un Bot? Recuperar Token

Si ya creaste un bot antes y olvidaste el token:

1. En @BotFather envia:
   ```
   /mybots
   ```

2. Selecciona tu bot de la lista

3. Click en **API Token**

4. BotFather te mostrara el token actual

### Paso 3.5: Regenerar Token (Si Fue Comprometido)

Si crees que alguien mas tiene tu token:

1. En @BotFather envia:
   ```
   /revoke
   ```

2. Selecciona tu bot

3. BotFather generara un **nuevo token** (el anterior dejara de funcionar)

### Paso 3.6: Configurar Privacidad del Bot (Opcional)

Por defecto, los bots pueden leer todos los mensajes de grupos.
Para bots de IA, es mejor dejarlo asi para control total.

Si quieres cambiar la privacidad:
1. En BotFather, envia `/setprivacy`
2. Selecciona tu bot
3. Elige `Enable` o `Disable`

---

## 4. Instalar el Sidecar

### Paso 4.1: Navegar al Directorio

```bash
cd /ruta/a/fararoni/fararoni-sidecar-tg
```

### Paso 4.2: Instalar Dependencias

```bash
npm install
```

Esto instalara:
- `telegraf` - Libreria para bots de Telegram
- `express` - Servidor HTTP para egress
- `axios` - Cliente HTTP para ingress
- `dotenv` - Carga de variables de entorno

### Paso 4.3: Verificar Instalacion

```bash
# Ver que node_modules existe
ls node_modules | wc -l
# Debe mostrar un numero mayor a 0
```

---

## 5. Configurar Fararoni

### Paso 5.1: Verificar modules.yml

Ubicacion: `~/.fararoni/config/modules.yml`

Asegurate de que Telegram este habilitado:

```yaml
channels:
  telegram:
    enabled: true
    trust_level: SECURE_ENCRYPTED
    egress_url: "http://localhost:3001/send"
    capabilities:
      - text
    timeout_ms: 5000
    retry_count: 3
```

### Paso 5.2: Verificar Gateway REST

El Gateway REST debe estar habilitado en `modules.yml`:

```yaml
gateway:
  rest:
    enabled: true
    port: 7071
```

---

## 6. Puesta en Marcha

### 6.1 Resumen Rapido

```
Terminal 1: java --enable-preview -jar fararoni-core-0.11.30.jar --server
Terminal 2: export TELEGRAM_TOKEN="tu_token" && npm start
Telegram:   Buscar tu bot y enviar mensaje
```

### 6.2 Orden de Inicio (IMPORTANTE)

Los servicios deben iniciarse en este orden:

```
1. Fararoni Core (Gateway)  <-- PRIMERO (puerto 7071)
2. Sidecar Telegram         <-- SEGUNDO (puerto 3001)
```

> **Por que este orden?** El Sidecar necesita conectarse al Gateway. Si el Gateway no esta corriendo, el Sidecar fallara con error `ECONNREFUSED`.

### 6.3 Terminal 1: Iniciar Fararoni Core

Abre una terminal y ejecuta:

```bash
cd fararoni-core/target

java --enable-preview -jar fararoni-core-0.11.30.jar --server
```

**Esperar hasta ver estos mensajes:**

```
[MODULE-REGISTRY] Loaded module: OmniChannelGatewayModule
[INGRESS] RestIngressServer listening on port 7071
[READY] Fararoni Core listo
```

**NO CIERRES ESTA TERMINAL** - Dejala corriendo.

### 6.4 Terminal 2: Iniciar Sidecar Telegram

Abre **otra terminal** y ejecuta:

```bash
cd fararoni-sidecar-tg

# Configurar el token (reemplaza con TU token de @BotFather)
export TELEGRAM_TOKEN="1234567890:ABCdefGHIjklMNOpqrSTUvwxYZ"

# Iniciar el sidecar
npm start
```

**Esperar hasta ver estos mensajes:**

```
[INFO]  [HTTP] Servidor Egress en puerto 3001
[INFO]  [TELEGRAM] Bot conectado: @tu_bot_username
[INFO]  [READY] Sidecar listo para recibir mensajes
```

**NO CIERRES ESTA TERMINAL** - Dejala corriendo.

### 6.5 Probar el Bot

1. Abre **Telegram** en tu celular o escritorio
2. Busca tu bot: `@tu_bot_username`
3. Click en **Start** o envia `/start`
4. Escribe un mensaje: `Hola, que puedes hacer?`
5. Deberias recibir una respuesta del asistente Fararoni

### 6.6 Alternativa: Usar Archivo .env

En lugar de exportar la variable cada vez, puedes crear un archivo `.env`:

```bash
cd fararoni-sidecar-tg

# Crear archivo de configuracion
cp .env.example .env

# Editar con tu token
nano .env
```

Contenido del archivo `.env` (reemplaza con TU token):

```bash
TELEGRAM_TOKEN=1234567890:ABCdefGHIjklMNOpqrSTUvwxYZ
SIDECAR_PORT=3001
GATEWAY_URL=http://localhost:7071/gateway/v1/inbound
DEBUG=false
```

Ahora puedes iniciar sin exportar:

```bash
npm start
```

> **IMPORTANTE:** El archivo `.env` ya esta en `.gitignore`, asi que no se subira a git.

---

## 7. Verificar Funcionamiento

### 7.1 Health Check del Gateway

```bash
curl http://localhost:7071/gateway/v1/health
```

Respuesta esperada:
```json
{"status": "healthy", "module": "gateway-rest-omnichannel"}
```

### 7.2 Health Check del Sidecar

```bash
curl http://localhost:3001/health
```

Respuesta esperada:
```json
{
  "status": "healthy",
  "service": "fararoni-sidecar-tg",
  "channel": "telegram",
  "port": 3001,
  "bot": {
    "id": 1234567890,
    "username": "tu_bot_username"
  }
}
```

### 7.3 Prueba End-to-End

1. Abre Telegram
2. Busca tu bot: `@tu_bot_username`
3. Inicia una conversacion (click en "Start" o envia `/start`)
4. Envia un mensaje: `Hola, que puedes hacer?`
5. Deberias recibir una respuesta del asistente Fararoni

### 7.4 Verificar Logs

**Terminal del Sidecar:**
```
[INFO]  [INGRESS] 123456789 -> "Hola, que puedes hacer?" (HTTP 202)
[INFO]  [EGRESS] 123456789 <- "Hola! Soy Fararoni, tu asistente..."
```

**Terminal de Fararoni:**
```
[OmniChannel] Processing message from telegram:123456789
[OmniChannel] Response sent to telegram:123456789
```

---

## 8. Seguridad

### 8.1 Filtro de Grupos

Por defecto, el sidecar **SOLO responde a chats privados** (1:1).
Los mensajes de grupos, supergrupos y canales son **IGNORADOS**.

| Tipo de Chat | Procesado? |
|--------------|------------|
| Privado (1:1) | SI |
| Grupo | NO |
| Supergrupo | NO |
| Canal | NO |

Esto evita que el bot:
- Responda en grupos donde no deberia
- Sea agregado a grupos spam
- Genere respuestas no deseadas

### 8.2 Verificar Filtro en Logs

Cuando alguien intenta usar el bot en un grupo:

```
[DEBUG] [FILTRO] Ignorando mensaje de group: -987654321
```

### 8.3 Habilitar Grupos Especificos (Opcional)

Si necesitas que el bot funcione en ciertos grupos:

1. Edita `fararoni-sidecar-tg/index.js`
2. Busca la seccion del filtro:

```javascript
// Lista de grupos permitidos
const ALLOWED_GROUPS = ['-123456789', '-987654321'];

// Modificar el filtro
if (chat.type !== 'private' && !ALLOWED_GROUPS.includes(chat.id.toString())) {
    logger.debug(`[FILTRO] Ignorando mensaje de ${chat.type}: ${chat.id}`);
    return;
}
```

### 8.4 Proteger el Token

- **NUNCA** subas el token a git
- Usa variables de entorno o archivo `.env`
- El archivo `.env` debe estar en `.gitignore`

Si crees que tu token fue comprometido:
1. Abre @BotFather
2. Envia `/revoke`
3. Selecciona tu bot
4. Obtendras un nuevo token

### 8.5 Control de Acceso de Usuarios

**Por defecto, cualquier persona que encuentre tu bot puede usarlo.**

Tienes dos opciones:

#### Opcion A: Bot Publico (Comportamiento por defecto)

- Cualquiera puede chatear con tu bot
- Util si quieres ofrecer un servicio publico
- No requiere configuracion adicional

#### Opcion B: Restringir a Usuarios Especificos

Si quieres que **solo tu** (o ciertas personas) puedan usar el bot:

**Paso 1: Obtener tu User ID de Telegram**

El User ID es un numero, NO es tu username (@usuario).

Para obtenerlo:
1. Abre Telegram
2. Busca el bot **@userinfobot**
3. Enviale cualquier mensaje
4. Te respondera con tu ID:
   ```
   Id: 123456789
   First: Tu Nombre
   Lang: es
   ```
5. Guarda ese numero (ej: `123456789`)

**Paso 2: Configurar Lista de Usuarios Permitidos**

Edita el archivo `fararoni-sidecar-tg/index.js` y agrega al inicio:

```javascript
// =============================================================================
// CONFIGURACION DE USUARIOS PERMITIDOS
// =============================================================================
// Dejar vacio [] para permitir a todos (bot publico)
// Agregar IDs para restringir acceso (bot privado)
const ALLOWED_USERS = [
    '123456789',    // Tu User ID
    '987654321',    // Otro usuario permitido
];
// =============================================================================
```

**Paso 3: Agregar el Filtro**

Busca la seccion donde se procesan los mensajes y agrega:

```javascript
// Filtro de usuarios (si la lista no esta vacia)
if (ALLOWED_USERS.length > 0 && !ALLOWED_USERS.includes(senderId.toString())) {
    logger.debug(`[FILTRO] Usuario no autorizado: ${senderId}`);
    return; // Ignorar usuarios no autorizados
}
```

**Paso 4: Reiniciar el Sidecar**

```bash
# Ctrl+C para detener
npm start
```

**Resultado:**
- Usuarios en la lista: Reciben respuestas normalmente
- Usuarios NO en la lista: Sus mensajes son ignorados silenciosamente

> **TIP:** Para agregar mas usuarios, solo agrega sus IDs a la lista `ALLOWED_USERS`.

---

## 9. Troubleshooting

### El bot no responde

**Verificar:**

1. **Gateway corriendo:**
   ```bash
   curl http://localhost:7071/gateway/v1/health
   ```

2. **Sidecar corriendo:**
   ```bash
   curl http://localhost:3001/health
   ```

3. **Token correcto:**
   - Si ves `401 Unauthorized` en logs, el token es invalido
   - Regenera el token en @BotFather con `/token`

4. **Logs del sidecar:**
   - El mensaje debe aparecer como `[INGRESS]`
   - Si no aparece, el bot no esta recibiendo mensajes

### Error "ECONNREFUSED"

```
Error: connect ECONNREFUSED 127.0.0.1:7071
```

**Causa:** El Gateway no esta corriendo.

**Solucion:**
```bash
java --enable-preview -jar fararoni-core-0.11.30.jar --server
```

### Error "401 Unauthorized"

**Causa:** Token de Telegram invalido o expirado.

**Solucion:**
1. Abre @BotFather
2. Envia `/token`
3. Selecciona tu bot
4. Copia el nuevo token
5. Actualiza la variable de entorno o `.env`
6. Reinicia el sidecar

### Error "409 Conflict: terminated by other getUpdates"

**Causa:** Otro proceso esta usando el mismo bot.

**Solucion:**
1. Cierra cualquier otra instancia del sidecar
2. Si usas webhooks en otro servicio, desactivalos
3. Reinicia el sidecar

### El bot responde en grupos (no deberia)

**Causa:** El filtro de seguridad no esta activo.

**Solucion:**
1. Verifica que `index.js` tenga el filtro:
   ```javascript
   if (chat.type !== 'private') {
       return;
   }
   ```
2. Reinicia el sidecar

### Mensajes llegan pero no hay respuesta

**Causa:** El agente LLM no esta procesando.

**Verificar:**
1. Ver logs de Fararoni Core
2. Buscar errores en `OmniChannelRouter`
3. Verificar que la API key de OpenAI/Anthropic este configurada

---

## 10. Comandos Utiles

### Iniciar Todo (Script)

Puedes crear un script `start-telegram.sh`:

```bash
#!/bin/bash

# Iniciar Gateway en background
cd /ruta/a/fararoni/fararoni-core/target
java --enable-preview -jar fararoni-core-0.11.30.jar --server &
GATEWAY_PID=$!
echo "Gateway PID: $GATEWAY_PID"

# Esperar a que el Gateway este listo
sleep 5

# Iniciar Sidecar
cd /ruta/a/fararoni/fararoni-sidecar-tg
npm start
```

### Health Checks

```bash
# Gateway
curl http://localhost:7071/gateway/v1/health

# Sidecar Telegram
curl http://localhost:3001/health

# Estado detallado del sidecar
curl http://localhost:3001/status
```

### Ver Logs en Tiempo Real

```bash
# Logs de Fararoni (si escribe a archivo)
tail -f ~/.fararoni/logs/fararoni.log | grep -E "(OmniChannel|TELEGRAM)"

# Logs del sidecar (stdout)
# Solo si lo ejecutas con npm start
```

### Detener Servicios

```bash
# Detener Gateway
pkill -f "fararoni-core"

# Detener Sidecar (Ctrl+C si esta en foreground, o)
pkill -f "fararoni-sidecar-tg"
```

### Regenerar Token

En @BotFather:
```
/token
@tu_bot_username
```

### Cambiar Nombre del Bot

En @BotFather:
```
/setname
@tu_bot_username
Nuevo Nombre del Bot
```

### Cambiar Descripcion del Bot

En @BotFather:
```
/setdescription
@tu_bot_username
Esta es la descripcion que veran los usuarios.
```

---

## Anexo A: Checklist de Implementacion

```
REQUISITOS
[ ] Node.js 18+ instalado
[ ] npm instalado
[ ] Fararoni Core instalado
[ ] Java 25+ con --enable-preview

TELEGRAM
[ ] Cuenta de Telegram activa
[ ] Bot creado en @BotFather
[ ] Token guardado de forma segura

INSTALACION
[ ] npm install ejecutado en fararoni-sidecar-tg
[ ] Archivo .env creado con TELEGRAM_TOKEN
[ ] modules.yml tiene telegram.enabled: true

PUESTA EN MARCHA
[ ] Gateway corriendo en puerto 7071
[ ] Sidecar corriendo en puerto 3001
[ ] Health check de ambos servicios OK

PRUEBAS
[ ] Mensaje enviado desde Telegram
[ ] Respuesta recibida del asistente
[ ] Mensaje en grupo ignorado (si aplica)
```

---

## Anexo B: Configuracion Avanzada

### Cambiar Puerto del Sidecar

```bash
export SIDECAR_PORT=3002
npm start
```

Y actualizar `modules.yml`:
```yaml
channels:
  telegram:
    egress_url: "http://localhost:3002/send"
```

### Usar Proxy HTTP

Si tu servidor requiere proxy para salir a Internet:

```bash
export HTTP_PROXY=http://proxy.empresa.com:8080
export HTTPS_PROXY=http://proxy.empresa.com:8080
npm start
```

### Multiples Bots

Para tener varios bots de Telegram:

1. Crear cada bot en @BotFather
2. Crear multiples instancias del sidecar en diferentes puertos
3. Cada instancia con su propio `TELEGRAM_TOKEN`
4. Configurar cada canal en `modules.yml`:

```yaml
channels:
  telegram_ventas:
    enabled: true
    egress_url: "http://localhost:3001/send"

  telegram_soporte:
    enabled: true
    egress_url: "http://localhost:3002/send"
```

---

**Autor:** Equipo Fararoni
**Version:** 1.0
