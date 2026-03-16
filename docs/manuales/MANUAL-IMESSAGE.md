# Manual de Usuario - Sidecar iMessage

## Fararoni OmniChannel Gateway

**Version:** 1.0

---

## Indice

1. [Introduccion](#1-introduccion)
2. [Requisitos Previos](#2-requisitos-previos)
3. [Instalacion de BlueBubbles](#3-instalacion-de-bluebubbles)
4. [Configuracion del Sidecar](#4-configuracion-del-sidecar)
5. [Configuracion del Webhook](#5-configuracion-del-webhook)
6. [Ejecucion](#6-ejecucion)
7. [Verificacion](#7-verificacion)
8. [Restriccion de Usuarios](#8-restriccion-de-usuarios)
9. [Troubleshooting](#9-troubleshooting)
10. [Produccion con PM2](#10-produccion-con-pm2)

---

## 1. Introduccion

El sidecar de iMessage permite que Fararoni se comunique a traves de la red de mensajeria de Apple (iMessage y SMS). Utiliza BlueBubbles Server como puente para acceder a las APIs nativas de macOS.

### Diferencia Fundamental con Otros Canales

A diferencia de Discord o Telegram, **Apple no tiene un portal de desarrolladores para crear "Bots" publicos de iMessage**. Apple es un ecosistema cerrado.

Es por eso que esta integracion es tan especial: tu bot Fararoni va a "poseer" tu propia cuenta de iMessage (o una cuenta de Apple dedicada que configures en tu Mac) a traves del puente que es BlueBubbles. Para el mundo exterior, parecera que tu mismo estas respondiendo instantaneamente desde tu Mac.

| Aspecto | Discord/Telegram | iMessage |
|---------|------------------|----------|
| Portal de desarrolladores | Si (crear bot publico) | No existe |
| Identidad del bot | Bot con nombre propio | Tu propia cuenta de iMessage |
| Registro | Token/API Key en portal | Tu Mac ES el "registro" |
| Apariencia al usuario | "Bot XYZ dice..." | "Tu nombre dice..." (burbuja azul) |

### Arquitectura

```
Usuario iOS/Mac               Tu Mac (BlueBubbles)              Fararoni
      |                              |                              |
      | <-- iMessage (E2E) -->       |                              |
      |                              |                              |
                    BlueBubbles Server (:1234)                      |
                              |                                     |
                              | <-- HTTP (local) -->                |
                              |                                     |
                    Sidecar iMessage (:3003)                        |
                              |                                     |
                              | <-- HTTP (Gateway) -->              |
                              |                                     |
                                              Gateway REST (:7071) --> Agentes LLM
```

### Caracteristicas

- Mensajes de burbuja azul (iMessage)
- Soporte para SMS (si tu Mac los recibe)
- Cifrado end-to-end nativo de Apple
- Sin riesgo de ban (usa APIs oficiales de macOS)
- Responde a 1:1 y grupos de iMessage

### Limitaciones

- **Solo macOS**: Requiere una Mac con iMessage configurado
- **Mac siempre encendida**: BlueBubbles debe estar corriendo
- **Solo texto**: Audio/imagenes en futuras versiones

---

## 2. Requisitos Previos

### Hardware

- Mac con macOS 10.14 (Mojave) o superior
- Cuenta de iCloud con iMessage activado

### Software

- Node.js 18 o superior
- npm (viene con Node.js)
- BlueBubbles Server (app nativa)
- Fararoni Gateway corriendo

### Verificar Node.js

```bash
node --version  # Debe ser v18+
npm --version
```

Si no tienes Node.js:
```bash
# macOS con Homebrew
brew install node

# O descarga desde https://nodejs.org/
```

---

## 3. Instalacion de BlueBubbles

BlueBubbles es una aplicacion de codigo abierto que expone iMessage como API REST.

### Paso 1: Descargar BlueBubbles

**Opcion A: Homebrew (recomendado)**

```bash
brew install --cask bluebubbles
```

**Opcion B: Descarga manual**

1. Ve a https://bluebubbles.app/
2. Descarga "BlueBubbles Server" para Mac
3. Arrastra a /Applications

### Paso 2: Abrir y Configurar

1. Abre BlueBubbles Server
2. Concede permisos de accesibilidad si lo pide
3. Inicia sesion con tu cuenta de iCloud si es necesario
4. Espera a que muestre "Connected"

### Paso 3: Configurar API

1. En BlueBubbles, ve a la pestana **API & Webhooks**
2. Anota estos valores:
   - **Server Password**: Lo usaras en `BLUEBUBBLES_PASSWORD`
   - **Local Port**: Por defecto `1234`

### Paso 4: Verificar Conexion

En terminal, prueba la conexion:

```bash
# Reemplaza TU_PASSWORD con el Server Password
curl "http://localhost:1234/api/v1/server/info?password=TU_PASSWORD"
```

Respuesta esperada (exito):
```json
{
  "status": 200,
  "data": {
    "server_version": "1.9.3",
    ...
  }
}
```

---

## 4. Configuracion del Sidecar

### Paso 1: Clonar/Crear el Sidecar

```bash
cd /ruta/a/tus/proyectos/fararoni
cd fararoni-sidecar-imessage
```

### Paso 2: Instalar Dependencias

```bash
npm install
```

### Paso 3: Crear Archivo .env

```bash
cp .env.example .env
nano .env
```

### Paso 4: Configurar Variables

```bash
# =============================================================================
# FARARONI SIDECAR - iMESSAGE (via BlueBubbles)
# =============================================================================

# Password del servidor BlueBubbles (REQUERIDO)
# Obtenerlo en: BlueBubbles Server -> API & Webhooks -> Server Password
BLUEBUBBLES_PASSWORD=aqui_tu_password_de_bluebubbles

# URL del servidor BlueBubbles
# Por defecto: http://localhost:1234
BLUEBUBBLES_URL=http://localhost:1234

# URL del Gateway REST de Fararoni
# Por defecto: http://localhost:7071/gateway/v1/inbound
GATEWAY_URL=http://localhost:7071/gateway/v1/inbound

# Puerto del servidor HTTP para Ingress/Egress
# Por defecto: 3003
SIDECAR_PORT=3003

# Lista de numeros de telefono permitidos (separados por coma)
# Dejar vacio para permitir todos
# Ejemplo: ALLOWED_SENDERS=+521234567890,+529876543210
ALLOWED_SENDERS=

# Modo debug (muestra logs adicionales)
DEBUG=false
```

### Tabla de Variables

| Variable | Requerido | Default | Descripcion |
|----------|-----------|---------|-------------|
| `BLUEBUBBLES_PASSWORD` | Si | - | Password de BlueBubbles API |
| `BLUEBUBBLES_URL` | No | `http://localhost:1234` | URL de BlueBubbles |
| `GATEWAY_URL` | No | `http://localhost:7071/gateway/v1/inbound` | URL del Gateway |
| `SIDECAR_PORT` | No | `3003` | Puerto del sidecar |
| `ALLOWED_SENDERS` | No | (vacio = todos) | Allowlist de numeros |
| `DEBUG` | No | `false` | Logs detallados |

---

## 5. Configuracion del Webhook

BlueBubbles necesita saber donde enviar los mensajes entrantes.

### Paso 1: Abrir Configuracion de Webhooks

1. Abre BlueBubbles Server en tu Mac
2. Ve a la pestana **API & Webhooks**
3. Busca la seccion **Webhooks**

### Paso 2: Agregar Webhook

1. Click en "Add Webhook" o similar
2. Configura:
   - **URL**: `http://localhost:3003/webhook`
   - **Events**: Selecciona solo `New Messages`

### Paso 3: Guardar

1. Guarda la configuracion
2. BlueBubbles enviara eventos a tu sidecar

### Diagrama de Flujo

```
Mensaje entrante de iMessage
           |
           v
    BlueBubbles Server
           |
           | POST /webhook
           v
    Sidecar iMessage (:3003)
           |
           | POST /gateway/v1/inbound
           v
    Gateway REST (:7071)
           |
           v
    Agentes Fararoni
           |
           | Respuesta
           v
    Gateway REST (:7071)
           |
           | POST /send
           v
    Sidecar iMessage (:3003)
           |
           | POST /api/v1/message/text
           v
    BlueBubbles Server
           |
           v
    iMessage al usuario
```

---

## 6. Ejecucion

### Orden de Inicio

1. **Primero**: Fararoni Server (Gateway)
2. **Segundo**: BlueBubbles Server (en tu Mac)
3. **Tercero**: Sidecar iMessage

### Iniciar Fararoni

```bash
cd /ruta/a/fararoni
./start-server.sh
# O: java --enable-preview -jar fararoni-core.jar --server
```

### Iniciar BlueBubbles

1. Abre la aplicacion BlueBubbles Server
2. Espera a que muestre "Connected"

### Iniciar Sidecar

```bash
cd fararoni-sidecar-imessage

# Modo normal
npm start

# Modo debug (logs detallados)
npm run dev
# O: DEBUG=true npm start
```

### Salida Esperada

```
============================================================
  FARARONI SIDECAR - iMESSAGE (FASE 71.7)
  via BlueBubbles Server
============================================================

[INFO]  12:00:00 [STARTUP] Verificando conexion con BlueBubbles...
[INFO]  12:00:01 [STARTUP] BlueBubbles Server OK: v1.9.3
[INFO]  12:00:01 [HTTP] Servidor en puerto 3003
[INFO]  12:00:01 [GATEWAY] Enviando a: http://localhost:7071/gateway/v1/inbound
[INFO]  12:00:01 [BLUEBUBBLES] Conectado a: http://localhost:1234
[INFO]  12:00:01 [READY] Sidecar listo para recibir mensajes de iMessage

------------------------------------------------------------

  Asegurate de configurar el Webhook en BlueBubbles:
    URL: http://localhost:3003/webhook
    Eventos: New Messages

------------------------------------------------------------
```

---

## 7. Verificacion y Prueba de Fuego

### 7.1 Health Checks

Antes de probar con mensajes reales, verifica que todos los componentes esten corriendo:

```bash
# 1. Verificar Sidecar
curl http://localhost:3003/health

# 2. Verificar Gateway
curl http://localhost:7071/gateway/v1/health

# 3. Verificar BlueBubbles (reemplaza TU_PASSWORD)
curl "http://localhost:1234/api/v1/server/info?password=TU_PASSWORD"
```

Respuesta esperada del sidecar:
```json
{
  "status": "healthy",
  "service": "fararoni-sidecar-imessage",
  "channel": "imessage",
  "blueBubbles": {
    "url": "http://localhost:1234",
    "status": "healthy"
  }
}
```

### 7.2 La Prueba de Fuego

Dado que el bot esta anclado a **tu propia cuenta de iMessage**, la forma de probarlo es que el mensaje venga desde "afuera" hacia ti. No puedes enviarte un mensaje a ti mismo como en Discord.

#### Opcion A: Un amigo o familiar

Pidele a alguien que tenga un iPhone que te envie un iMessage (burbuja azul) a tu numero de telefono o a tu correo de iCloud.

Mensaje de prueba sugerido:
```
Hola, dime un dato curioso
```

#### Opcion B: Otro dispositivo tuyo

Si tienes un iPhone o iPad con una cuenta de iCloud **diferente** (o un numero diferente), enviate un iMessage a la cuenta principal que esta en tu Mac.

#### Opcion C: Pedir a alguien que te envie un SMS

Si alguien te envia un SMS (burbuja verde), tambien sera capturado por BlueBubbles si tu Mac esta configurada para recibir SMS.

### 7.3 Telemetria: Lo que veras en cada pantalla

Cuando alguien te envie un mensaje, esto es lo que ocurre en cada componente:

#### 1. Tu Mac (App Mensajes)

Tu app nativa de Mensajes de macOS recibira el mensaje azul de forma normal, como siempre.

#### 2. BlueBubbles Server

BlueBubbles notara el mensaje y hara un POST automatico al puerto 3003 de tu Sidecar.

#### 3. Terminal del Sidecar (Node.js)

Veras en la terminal:
```
[INFO]  12:01:00 [INGRESS] Juan Perez (iMsg) -> "Hola, dime un dato curioso" (HTTP 200)
```

#### 4. Terminal de Fararoni (Java)

Veras al enrutador atrapar el mensaje, el LLM procesara la respuesta, y el EgressDispatcher lo lanzara de vuelta.

#### 5. El Regreso

El Sidecar imprimira:
```
[INFO]  12:01:05 [EGRESS] iMessage;-;+521234567890 <- "Aqui tienes un dato curioso..."
```

Y tu Mac enviara fisicamente la respuesta azul a traves de tu cuenta.

### 7.4 Resultado Final

El usuario que te escribio recibira una respuesta de la Inteligencia Artificial directamente en su iPhone, en formato de **burbuja azul nativa**. Para ellos, parecera que tu mismo respondiste instantaneamente.

### 7.5 Estado Detallado (Debug)

```bash
curl http://localhost:3003/status
```

Muestra configuracion completa y estado de BlueBubbles.

### 7.6 Probar Envio Manual (Egress)

Para probar que el egress funciona sin esperar respuesta del LLM, puedes hacer un POST directo al sidecar:

```bash
curl -X POST http://localhost:3003/send \
  -H "Content-Type: application/json" \
  -d '{
    "recipient": "iMessage;-;+521234567890",
    "message": "Mensaje de prueba desde curl"
  }'
```

Reemplaza `+521234567890` con el numero de telefono real al que quieres enviar.

---

## 8. Restriccion de Usuarios

Por defecto, el sidecar responde a **todos** los mensajes entrantes.

### Activar Allowlist

Edita `.env`:

```bash
# Solo estos numeros pueden usar el bot
ALLOWED_SENDERS=+521234567890,+529876543210
```

### Formato de Numeros

- Usa formato internacional: `+521234567890`
- O emails de Apple ID: `usuario@icloud.com`
- Separados por coma, sin espacios

### Verificar Allowlist

Con `DEBUG=true`, veras:

```
[DEBUG] [FILTRO] Remitente +1999888777 no esta en ALLOWED_SENDERS
```

---

## 9. Troubleshooting

### Error: "BLUEBUBBLES_PASSWORD no configurado"

**Causa:** No definiste el password en `.env`

**Solucion:**
1. Abre BlueBubbles -> API & Webhooks
2. Copia el "Server Password"
3. Agrega a `.env`: `BLUEBUBBLES_PASSWORD=tu_password`

---

### Error: "BlueBubbles no disponible"

**Causa:** BlueBubbles Server no esta corriendo o el puerto es incorrecto.

**Solucion:**
1. Abre la app BlueBubbles Server en tu Mac
2. Verifica que muestre "Connected"
3. Verifica el puerto en API & Webhooks
4. Si es diferente de 1234, actualiza `BLUEBUBBLES_URL`

---

### Error: "Password de BlueBubbles incorrecto"

**Causa:** El password en `.env` no coincide.

**Solucion:**
1. Abre BlueBubbles -> API & Webhooks
2. Verifica el "Server Password"
3. Actualiza `.env` con el valor correcto

---

### No recibo mensajes

**Checklist:**
1. BlueBubbles esta corriendo? (App abierta en Mac)
2. Webhook configurado?
   - URL: `http://localhost:3003/webhook`
   - Eventos: `New Messages`
3. Sidecar esta corriendo?
   ```bash
   curl http://localhost:3003/health
   ```
4. Gateway esta corriendo?
   ```bash
   curl http://localhost:7071/gateway/v1/health
   ```

---

### No envio respuestas

**Checklist:**
1. Verifica logs del sidecar (usa `DEBUG=true`)
2. Verifica que el Gateway recibio la respuesta
3. BlueBubbles puede enviar?
   ```bash
   # Prueba manual
   curl -X POST "http://localhost:1234/api/v1/message/text?password=TU_PASSWORD" \
     -H "Content-Type: application/json" \
     -d '{"chatGuid":"iMessage;-;+521234567890","text":"Test","method":"apple-script"}'
   ```

---

### Mensajes duplicados

**Causa:** Webhook configurado multiples veces en BlueBubbles.

**Solucion:**
1. Abre BlueBubbles -> API & Webhooks
2. Elimina webhooks duplicados
3. Deja solo uno apuntando a `http://localhost:3003/webhook`

---

### Mensaje: "Gateway no disponible"

**Causa:** Fararoni no esta corriendo o el puerto es incorrecto.

**Solucion:**
1. Verifica que Fararoni este corriendo
2. Verifica la URL en `GATEWAY_URL`
3. Prueba: `curl http://localhost:7071/gateway/v1/health`

---

## 10. Produccion con PM2

Para mantener el sidecar corriendo 24/7.

### Instalar PM2

```bash
npm install -g pm2
```

### Iniciar con PM2

```bash
cd fararoni-sidecar-imessage
pm2 start index.js --name imessage-bot
```

### Comandos Utiles

```bash
# Ver estado
pm2 status

# Ver logs
pm2 logs imessage-bot

# Reiniciar
pm2 restart imessage-bot

# Detener
pm2 stop imessage-bot

# Eliminar
pm2 delete imessage-bot
```

### Auto-inicio al Reiniciar Mac

```bash
pm2 save
pm2 startup
```

Sigue las instrucciones que muestra PM2.

---

## Apendice A: Diferencias iMessage vs SMS

| Aspecto | iMessage | SMS |
|---------|----------|-----|
| Color burbuja | Azul | Verde |
| Cifrado | End-to-end | No |
| Requiere | Internet + Apple ID | Operador celular |
| Costo | Gratis | Depende del plan |

El sidecar maneja ambos transparentemente. Puedes ver el tipo en metadata:
```json
{
  "metadata": {
    "service": "iMessage"  // o "SMS"
  }
}
```

---

## Apendice B: Identificadores de Chat

BlueBubbles usa `chatGuid` para identificar conversaciones:

| Tipo | Formato | Ejemplo |
|------|---------|---------|
| iMessage 1:1 | `iMessage;-;+numero` | `iMessage;-;+521234567890` |
| iMessage email | `iMessage;-;email` | `iMessage;-;user@icloud.com` |
| SMS | `SMS;-;+numero` | `SMS;-;+521234567890` |
| Grupo | `iMessage;+;chat123` | `iMessage;+;chat123456789` |

El sidecar maneja esto automaticamente.

---

## Apendice C: Puertos del Ecosistema

| Puerto | Servicio | Descripcion |
|--------|----------|-------------|
| 7070 | CLI Server | API REST interna |
| 7071 | Gateway REST | Ingress/Egress Sidecars |
| 3000 | WhatsApp | Sidecar Baileys |
| 3001 | Telegram | Sidecar Telegraf |
| 3002 | Discord | Sidecar discord.js |
| 3003 | iMessage | Sidecar BlueBubbles |
| 1234 | BlueBubbles | API REST nativa |

---

## Contacto y Soporte

- **FAQ General**: `FAQ-SIDECARS-OMNICHANNEL.md`
- **BlueBubbles**: https://bluebubbles.app/

---

*Manual para FASE 71.7 - Fararoni Sidecar iMessage*
