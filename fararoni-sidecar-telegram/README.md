# Fararoni Sidecar - Telegram

FASE 71.5 - Sidecar para conectar Telegram Bot API con Fararoni Gateway.

## Descripcion

Este sidecar actua como un "Dumb Pipe" (tubo tonto) que traduce mensajes
entre la API de Telegram y el Gateway REST de Fararoni. No contiene logica
de negocio ni procesamiento de IA.

```
Usuario Telegram <-> Sidecar (:3001) <-> Gateway (:7071) <-> Fararoni Core
```

## Seguridad

Por defecto, el sidecar **SOLO responde a chats privados** (1:1).
Los mensajes de grupos, supergrupos y canales son ignorados.

| Tipo de Chat | Procesado? |
|--------------|------------|
| Privado | SI |
| Grupo | NO |
| Supergrupo | NO |
| Canal | NO |

## Requisitos

- Node.js 18 o superior
- Bot de Telegram (crear en @BotFather)
- Fararoni Gateway corriendo en puerto 7071

## Instalacion

```bash
# 1. Entrar al directorio
cd fararoni-sidecar-tg

# 2. Instalar dependencias
npm install

# 3. Configurar token
export TELEGRAM_TOKEN="tu_token_de_botfather"

# 4. Iniciar
npm start
```

## Crear Bot en Telegram

1. Abre Telegram en tu celular o escritorio
2. Busca el usuario **@BotFather**
3. Envia el comando `/newbot`
4. Sigue las instrucciones:
   - Nombre del bot: `Fararoni Bot`
   - Username: `fararoni_dev_bot` (debe terminar en `bot`)
5. Copia el token HTTP API que te da BotFather

## Configuracion

### Variables de Entorno

| Variable | Requerido | Default | Descripcion |
|----------|-----------|---------|-------------|
| `TELEGRAM_TOKEN` | SI | - | Token de @BotFather |
| `SIDECAR_PORT` | NO | 3001 | Puerto del servidor HTTP |
| `GATEWAY_URL` | NO | http://localhost:7071/gateway/v1/inbound | URL del Gateway |
| `DEBUG` | NO | false | Habilitar logs de debug |

### Archivo .env (Opcional)

```bash
cp .env.example .env
# Editar .env con tus valores
```

## Endpoints

| Metodo | Path | Descripcion |
|--------|------|-------------|
| POST | `/send` | Recibe mensajes del Gateway para enviar a Telegram |
| GET | `/health` | Health check del servicio |
| GET | `/status` | Estado detallado (para debugging) |

### POST /send

Payload esperado:

```json
{
  "recipient": "123456789",
  "message": "Hola desde Fararoni!",
  "type": "TEXT"
}
```

Respuesta exitosa:

```json
{
  "status": "sent",
  "recipient": "123456789",
  "timestamp": "2026-02-19T22:00:00.000Z"
}
```

### GET /health

```json
{
  "status": "healthy",
  "service": "fararoni-sidecar-tg",
  "channel": "telegram",
  "port": 3001,
  "bot": {
    "id": 1234567890,
    "username": "fararoni_dev_bot"
  }
}
```

## Flujo de Mensajes

### Ingress (Telegram -> Fararoni)

```
1. Usuario envia mensaje en Telegram
2. Telegraf recibe el evento 'text'
3. Filtro: Si no es chat privado -> ignorar
4. Construir UniversalMessage
5. POST a Gateway (/gateway/v1/inbound)
6. Gateway publica en bus interno
7. Fararoni Core procesa el mensaje
```

### Egress (Fararoni -> Telegram)

```
1. Agente genera respuesta
2. Gateway llama POST /send
3. Sidecar recibe el request
4. bot.telegram.sendMessage()
5. Usuario recibe respuesta en Telegram
```

## Logs

```
[INFO]  16:49:32 [HTTP] Servidor Egress en puerto 3001
[INFO]  16:49:33 [TELEGRAM] Bot conectado: @fararoni_dev_bot
[INFO]  16:49:33 [READY] Sidecar listo para recibir mensajes
[INFO]  16:50:01 [INGRESS] 123456789 -> "Hola Fararoni" (HTTP 202)
[INFO]  16:50:02 [EGRESS] 123456789 <- "Hola! Como puedo ayudarte?"
[DEBUG] 16:50:15 [FILTRO] Ignorando mensaje de group: -987654321
```

## Troubleshooting

### El bot no responde

1. Verificar que el token sea correcto
2. Verificar que el Gateway este corriendo: `curl http://localhost:7071/gateway/v1/health`
3. Verificar logs del sidecar

### Error "401 Unauthorized"

Token de Telegram invalido. Regenerar en @BotFather con `/token`.

### Error "ECONNREFUSED"

El Gateway no esta corriendo. Iniciar Fararoni Core primero.

### El bot responde en grupos

Verificar que el filtro `chat.type !== 'private'` este activo en index.js.

## Habilitar Grupos (Opcional)

Si en el futuro necesitas que el bot responda en grupos especificos:

```javascript
// En index.js, modificar el filtro:
const ALLOWED_GROUPS = ['-123456789', '-987654321'];

if (chat.type !== 'private' && !ALLOWED_GROUPS.includes(chat.id.toString())) {
    return; // Ignorar grupos no autorizados
}
```

## Arquitectura

Este sidecar es parte de la arquitectura OmniChannel de Fararoni:

```
+------------------+     +------------------+     +------------------+
|  Sidecar WA      |     |  Sidecar TG      |     |  Sidecar X       |
|  Puerto 3000     |     |  Puerto 3001     |     |  Puerto 300X     |
+--------+---------+     +--------+---------+     +--------+---------+
         |                        |                        |
         +------------------------+------------------------+
                                  |
                                  v
                    +---------------------------+
                    |  Gateway REST (:7071)     |
                    |  RestIngressServer        |
                    |  HttpEgressDispatcher     |
                    +---------------------------+
                                  |
                                  v
                    +---------------------------+
                    |  Fararoni Core            |
                    |  OmniChannelRouter        |
                    |  Agentes LLM              |
                    +---------------------------+
```

## Licencia

Apache 2.0

---

**FASE:** 71.5
**Autor:** Equipo Fararoni
**Fecha:** 2026-02-19
