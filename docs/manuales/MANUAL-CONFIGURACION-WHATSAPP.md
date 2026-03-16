# Manual de Configuración - Sidecar WhatsApp

## Version: 1.0

---

## 1. Requisitos Previos

### Software
- Node.js 18+
- npm o yarn
- Fararoni Core ejecutándose (puerto 7071)

### Hardware
- Teléfono con WhatsApp instalado
- Conexión a internet estable

---

## 2. Instalación

```bash
# 1. Navegar al directorio del sidecar
cd fararoni-sidecar-wa

# 2. Instalar dependencias
npm install

# 3. Copiar configuración de ejemplo
cp .env.example .env

# 4. Editar configuración
nano .env
```

---

## 3. Configuración (.env)

```env
# URL del Gateway REST de Fararoni
GATEWAY_URL=http://localhost:7071/gateway/v1/inbound

# Puerto del servidor HTTP para recibir respuestas
SIDECAR_PORT=3000

# Nivel de log (debug, info, warn, error)
LOG_LEVEL=info

# Prefijo para ignorar mensajes (opcional)
# IGNORE_PREFIX=!

# Modo de operación
# private_only = Solo responde en chats privados
# groups_only = Solo responde en grupos
# all = Responde en todos (CUIDADO)
CHAT_MODE=private_only
```

---

## 4. Primera Ejecución (Vincular WhatsApp)

```bash
# Ejecutar el sidecar
node sidecar.js
```

### Aparecerá un código QR en la terminal:

```
[SIDECAR] Escanea el código QR con WhatsApp:
[SIDECAR] WhatsApp > Dispositivos vinculados > Vincular dispositivo

█████████████████████████████████
█████████████████████████████████
████ ▄▄▄▄▄ █▀▀▄▀▄█▄█ ▄▄▄▄▄ ████
████ █   █ █▄ ▀▄▀ ▀█ █   █ ████
...
```

### Pasos para vincular:
1. Abrir WhatsApp en tu teléfono
2. Ir a **Configuración** > **Dispositivos vinculados**
3. Tocar **Vincular un dispositivo**
4. Escanear el código QR de la terminal

### Una vez vinculado:
```
[SIDECAR] ✅ WhatsApp conectado como: +52 1 229 XXX XXXX
[SIDECAR] 🚀 Servidor HTTP escuchando en puerto 3000
[SIDECAR] 📡 Listo para recibir y enviar mensajes
```

---

## 5. Arquitectura de Flujo

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Usuario       │     │   Sidecar WA    │     │  Fararoni Core  │
│   WhatsApp      │     │   (Node.js)     │     │   (Java)        │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         │  Mensaje entrante     │                       │
         │──────────────────────>│                       │
         │                       │  POST /gateway/v1/inbound
         │                       │──────────────────────>│
         │                       │                       │
         │                       │                       │ Procesa con LLM
         │                       │                       │
         │                       │  POST /send           │
         │                       │<──────────────────────│
         │  Mensaje de respuesta │                       │
         │<──────────────────────│                       │
         │                       │                       │
```

---

## 6. Endpoints del Sidecar

| Método | Path | Descripción |
|--------|------|-------------|
| POST | `/send` | Enviar mensaje (usado por Fararoni) |
| GET | `/health` | Health check |
| GET | `/status` | Estado de conexión WhatsApp |

### Ejemplo POST /send
```json
{
  "to": "5212291234567@s.whatsapp.net",
  "message": "Hola, soy Fararoni"
}
```

---

## 7. Configuración en Fararoni Core

### Archivo: `~/.fararoni/config/modules.yml`

```yaml
gateway:
  rest:
    enabled: true
    port: 7071

channels:
  whatsapp:
    enabled: true
    egress_url: "http://localhost:3000/send"
    # Filtros opcionales
    allowed_numbers: []  # Vacío = todos permitidos
    blocked_numbers: []
```

---

## 8. Comandos Útiles

```bash
# Iniciar Fararoni Core
cd fararoni-core/target
java --enable-preview -jar fararoni-core-1.0.0.jar --server

# Iniciar Sidecar WhatsApp (otra terminal)
cd fararoni-sidecar-wa
node sidecar.js

# Ver logs en tiempo real
tail -f ~/.fararoni/logs/fararoni.log

# Reiniciar sesión WhatsApp (borrar credenciales)
rm -rf fararoni-sidecar-wa/baileys_auth_info/
node sidecar.js  # Escaneará QR de nuevo
```

---

## 9. Troubleshooting

### Problema: No aparece código QR
```bash
# Limpiar cache de node
rm -rf node_modules
npm install
```

### Problema: Desconexión frecuente
- Verificar conexión a internet
- No usar WhatsApp Web en el navegador al mismo tiempo
- Revisar si el teléfono tiene batería suficiente

### Problema: Mensajes no llegan a Fararoni
```bash
# Verificar que Fararoni esté corriendo
curl http://localhost:7071/gateway/v1/health

# Verificar conectividad
curl -X POST http://localhost:7071/gateway/v1/inbound \
  -H "Content-Type: application/json" \
  -d '{"type":"text","content":"test","sender":"test@test.com"}'
```

### Problema: Robot no responde
1. Verificar logs del sidecar
2. Verificar logs de Fararoni
3. Confirmar que OmniChannelRouter está activo

---

## 10. Seguridad

### Credenciales de WhatsApp
- **NUNCA** subir `baileys_auth_info/` a git
- Ya está en `.gitignore`
- Contiene tokens de sesión sensibles

### Números bloqueados
```yaml
# En modules.yml
channels:
  whatsapp:
    blocked_numbers:
      - "5212291234567"  # Bloquear número específico
```

---

## 11. Pendientes / Issues Conocidos

### ✅ RESUELTO: Robot ya NO responde en grupos (FASE 71.4)

**Problema original:** Cuando alguien enviaba mensaje en un grupo donde está el número vinculado, el robot respondía en el grupo.

**Solución aplicada:** Filtro en `index.js` línea 230-234

```javascript
// FASE 71.4: Ignorar mensajes de grupos
if (msg.key.remoteJid.endsWith('@g.us')) {
  logger.debug(`[INGRESS] Ignorando mensaje de grupo: ${msg.key.remoteJid}`);
  return;
}
```

**Tipos de JID en WhatsApp:**
| Sufijo | Tipo | Comportamiento actual |
|--------|------|----------------------|
| `@s.whatsapp.net` | Chat privado (1:1) | ✅ Responde |
| `@g.us` | Grupo | ❌ Ignora |
| `status@broadcast` | Estados | ❌ Ignora |

---

## 12. Habilitar Respuestas en Grupos (Futuro)

Si en el futuro necesitas que el robot responda en grupos, modifica `index.js`:

### Opción A: Habilitar TODOS los grupos
```javascript
// Comentar o eliminar estas líneas (230-234):
// if (msg.key.remoteJid.endsWith('@g.us')) {
//   logger.debug(`[INGRESS] Ignorando mensaje de grupo: ${msg.key.remoteJid}`);
//   return;
// }
```

### Opción B: Habilitar grupos ESPECÍFICOS (whitelist)
```javascript
// Reemplazar el filtro con whitelist:
const ALLOWED_GROUPS = [
  '5212291234567-1234567890@g.us',  // ID del grupo permitido
  '5212299876543-0987654321@g.us'
];

if (msg.key.remoteJid.endsWith('@g.us')) {
  if (!ALLOWED_GROUPS.includes(msg.key.remoteJid)) {
    logger.debug(`[INGRESS] Grupo no autorizado: ${msg.key.remoteJid}`);
    return;
  }
  logger.info(`[INGRESS] Grupo autorizado: ${msg.key.remoteJid}`);
}
```

### Opción C: Variable de entorno
```javascript
// En CONFIG agregar:
ALLOW_GROUPS: process.env.ALLOW_GROUPS === 'true',

// En el filtro:
if (msg.key.remoteJid.endsWith('@g.us') && !CONFIG.ALLOW_GROUPS) {
  logger.debug(`[INGRESS] Grupos deshabilitados`);
  return;
}
```

### Cómo obtener el ID de un grupo
1. Agregar log temporal: `console.log('JID:', msg.key.remoteJid)`
2. Enviar mensaje en el grupo
3. Ver el JID en los logs (formato: `TELEFONO-TIMESTAMP@g.us`)

---

**Autor:** Eber Cruz
**Version:** 1.0
