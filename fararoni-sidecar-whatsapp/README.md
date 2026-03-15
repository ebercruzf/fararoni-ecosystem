# Fararoni WhatsApp Sidecar

FASE 71 - Microservicio Node.js para comunicacion con WhatsApp via Baileys (Open Source).

## Arquitectura

```
WhatsApp <-> Baileys <-> Sidecar (3000) <-> Gateway Java (7071) <-> Agents
```

**IMPORTANTE**: El Gateway escucha en puerto **7071** (`/gateway/v1/inbound`),
NO en puerto 7070 (`/api/v1/ingress`) que es el CLI Server.

## Requisitos

- Node.js 20+
- npm

## Instalacion

```bash
cd fararoni-sidecar-wa
npm install
```

## Ejecucion

**JavaScript (recomendado):**
```bash
npm start
```

**TypeScript:**
```bash
npm run start:ts
```

Al iniciar:
1. Aparecera un **QR Code** en la terminal
2. Abre WhatsApp en tu celular
3. Ve a **Configuracion > Dispositivos Vinculados > Vincular Dispositivo**
4. Escanea el QR

La sesion se guarda en `./baileys_auth_info/` y no se pedira QR nuevamente.

## Endpoints

| Metodo | Path | Descripcion |
|--------|------|-------------|
| POST | `/send` | Recibe respuestas del Gateway y las envia a WhatsApp |
| GET | `/health` | Health check |
| GET | `/qr` | Devuelve QR actual (si esta conectando) |

## Variables de Entorno

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `SIDECAR_PORT` | 3000 | Puerto del servidor HTTP |
| `GATEWAY_URL` | http://localhost:7071/gateway/v1/inbound | URL del Gateway Java |
| `FARARONI_GATEWAY_TOKEN` | (vacio) | Token de autenticacion |
| `AUTH_DIR` | ./baileys_auth_info | Directorio para sesion |
| `LOG_LEVEL` | info | Nivel de log |

## Flujo de Mensajes

### Ingress (WhatsApp -> Fararoni)

1. Usuario envia mensaje por WhatsApp
2. Baileys recibe el evento `messages.upsert`
3. Sidecar construye `UniversalMessage`
4. POST a `http://localhost:7071/gateway/v1/inbound`
5. Gateway Java lo inyecta en el bus
6. OmniChannelRouter lo procesa

### Egress (Fararoni -> WhatsApp)

1. Agente genera respuesta
2. Se publica a `agency.output.main`
3. HttpEgressDispatcher hace POST a `http://localhost:3000/send`
4. Sidecar envia via `sock.sendMessage()`
5. Usuario recibe en WhatsApp

## Formato UniversalMessage

```json
{
  "messageId": "wamid.HBgLNTIyMjkxMjM0NTY3...",
  "channelId": "whatsapp",
  "senderId": "+522291234567",
  "conversationId": "+522291234567@s.whatsapp.net",
  "type": "TEXT",
  "textContent": "Hola, necesito ayuda",
  "mediaContent": null,
  "mimeType": null,
  "metadata": {
    "profileName": "Juan Perez"
  },
  "timestamp": "2026-02-19T12:00:00Z"
}
```

## Tipos de Mensaje Soportados

| Tipo | textContent | mediaContent | Descripcion |
|------|-------------|--------------|-------------|
| TEXT | Texto | null | Mensajes de texto |
| AUDIO | null | Base64 | Notas de voz |
| IMAGE | Caption | (futuro) | Imagenes |

## Troubleshooting

### "Gateway no disponible (ECONNREFUSED)"

El servidor Java no esta corriendo. Inicia con:
```bash
java --enable-preview -jar fararoni-core-1.0.0.jar --server
```

### "Sesion cerrada"

Elimina la sesion y vuelve a escanear QR:
```bash
rm -rf baileys_auth_info
npm start
```

### WhatsApp pide vincular de nuevo

Normal despues de mucho tiempo inactivo o si usaste otro dispositivo.
Simplemente escanea el nuevo QR.
