# Manual de Usuario: WhatsApp Baileys (Version Gratuita)

## Version: 1.0
## Audiencia: Desarrolladores, Usuarios de prueba, Proyectos personales

---

## Indice

1. [Introduccion](#1-introduccion)
2. [Requisitos Previos](#2-requisitos-previos)
3. [Instalar el Sidecar](#3-instalar-el-sidecar)
4. [Configurar Fararoni](#4-configurar-fararoni)
5. [Puesta en Marcha](#5-puesta-en-marcha)
6. [Escanear Codigo QR](#6-escanear-codigo-qr)
7. [Verificar Funcionamiento](#7-verificar-funcionamiento)
8. [Seguridad](#8-seguridad)
9. [Troubleshooting](#9-troubleshooting)
10. [Comandos Utiles](#10-comandos-utiles)

---

## 1. Introduccion

### Que es WhatsApp Baileys?

Baileys es una libreria **no oficial** de codigo abierto que permite conectar
a WhatsApp Web desde Node.js. Es completamente **GRATUITA** y no requiere
ninguna cuenta de negocio ni verificacion.

### Arquitectura

```
+----------------+     +-----------------+     +----------------+     +----------------+
|   Usuario      | --> |  WhatsApp Web   | --> |   Sidecar WA   | --> |   Fararoni     |
|   WhatsApp     |     |  (Baileys)      |     |   (:3000)      |     |   Gateway      |
+----------------+     +-----------------+     +----------------+     |   (:7071)      |
                                                                      +----------------+
                                                                             |
                                                                             v
                                                                      +----------------+
                                                                      |  Agentes LLM   |
                                                                      +----------------+
```

### Ventajas

| Caracteristica | Valor |
|----------------|-------|
| Costo | **GRATIS** |
| Requiere cuenta de negocio | NO |
| Setup | 15 minutos |
| Ideal para | Desarrollo, pruebas, uso personal |

### Limitaciones (Advertencia)

| Limitacion | Descripcion |
|------------|-------------|
| Riesgo de ban | Meta puede bloquear tu numero si detecta automatizacion |
| API no oficial | Baileys puede dejar de funcionar si Meta cambia algo |
| Sin soporte | No hay soporte oficial de Meta |
| Un dispositivo | Solo puede estar vinculado a un dispositivo a la vez |

> **IMPORTANTE:** Para produccion empresarial, usa [WhatsApp Enterprise](MANUAL-WHATSAPP-ENTERPRISE.md).

### Cuando usar Baileys?

- Desarrollo y pruebas locales
- Proyectos personales
- POCs (Proof of Concept)
- Cuando no puedes pagar WhatsApp Enterprise
- Bots de uso moderado (no spam)

---

## 2. Requisitos Previos

### 2.1 Software Necesario

- [ ] Node.js 20 o superior
- [ ] npm (viene con Node.js)
- [ ] Fararoni Core instalado
- [ ] Java 25+ con --enable-preview

### 2.2 WhatsApp

- [ ] Celular con WhatsApp instalado y funcionando
- [ ] Numero de telefono activo en WhatsApp
- [ ] El celular debe tener conexion a Internet

### 2.3 Puertos

| Puerto | Servicio | Estado Requerido |
|--------|----------|------------------|
| 7071 | Gateway REST | Abierto (local) |
| 3000 | Sidecar WhatsApp | Abierto (local) |

### Verificar Requisitos

```bash
# Verificar Node.js (debe ser 20+)
node --version

# Verificar npm
npm --version

# Verificar Java
java --version
```

### 2.4 Cifrado de Canales (Encryption Key)

Las credenciales de sesion de WhatsApp Baileys se almacenan cifradas en la base de datos local.

| Escenario | Key env var | DEV_MODE | Resultado |
|---|---|---|---|
| Produccion con key | `"abc123..."` | `false` | Usa la key del env |
| Produccion sin key | (vacia) | `false` | `LOG.severe` advierte, sin cifrado |
| Desarrollo con key | `"abc123..."` | `true` | Usa la key del env |
| Desarrollo sin key | (vacia) | `true` | Auto-genera key AES-256 persistente en `~/.fararoni/config/.dev-encryption-key` |

**Desarrollo local (no requiere configuracion extra):**
```bash
export FARARONI_DEV_MODE=true
# La key se genera automaticamente y persiste entre reinicios
```

> **Nota:** En desarrollo la key se guarda en `~/.fararoni/config/.dev-encryption-key`
> y se reutiliza en cada reinicio. En produccion debes proveer tu propia key
> y resguardarla — si la pierdes, los tokens cifrados no se pueden recuperar.

---

## 3. Instalar el Sidecar

### Paso 3.1: Navegar al Directorio

```bash
cd /ruta/a/fararoni/fararoni-sidecar-wa
```

### Paso 3.2: Instalar Dependencias

```bash
npm install
```

Esto instalara:
- `@whiskeysockets/baileys` - Libreria para WhatsApp Web
- `express` - Servidor HTTP para egress
- `axios` - Cliente HTTP para ingress
- `qrcode-terminal` - Muestra QR en terminal

### Paso 3.3: Verificar Instalacion

```bash
# Ver que node_modules existe
ls node_modules | head -5
```

---

## 4. Configurar Fararoni

### Paso 4.1: Verificar modules.yml

Ubicacion: `~/.fararoni/config/modules.yml`

Asegurate de que WhatsApp este habilitado:

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
    timeout_ms: 5000
    retry_count: 3
```

### Paso 4.2: Verificar Gateway REST

El Gateway REST debe estar habilitado:

```yaml
gateway:
  rest:
    enabled: true
    port: 7071
```

---

## 5. Puesta en Marcha

### 5.1 Orden de Inicio (IMPORTANTE)

Los servicios deben iniciarse en este orden:

```
1. Fararoni Core (Gateway)  <-- PRIMERO
2. Sidecar WhatsApp         <-- SEGUNDO
```

### 5.2 Terminal 1: Iniciar Fararoni Core

```bash
cd /ruta/a/fararoni/fararoni-core/target

java --enable-preview -jar fararoni-core-0.11.30.jar --server
```

Esperar hasta ver:
```
[MODULE-REGISTRY] Loaded module: OmniChannelGatewayModule
[INGRESS] RestIngressServer listening on port 7071
```

### 5.3 Terminal 2: Iniciar Sidecar WhatsApp

```bash
cd /ruta/a/fararoni/fararoni-sidecar-wa

npm start
```

La primera vez, veras un **codigo QR** en la terminal.

---

## 6. Escanear Codigo QR

### Paso 6.1: Ver el QR en Terminal

Cuando inicies el sidecar por primera vez, veras algo asi:

```
█████████████████████████████████████
█████████████████████████████████████
████ ▄▄▄▄▄ █▀█ █▄ ▀▄█▀▄█ ▄▄▄▄▄ ████
████ █   █ █▀▀▀█  ▄▄▀ ▄█ █   █ ████
████ █▄▄▄█ █▀ █▀▀▄▀▄▄  █ █▄▄▄█ ████
████▄▄▄▄▄▄▄█▄▀ ▀▄█ ▀▄█ █▄▄▄▄▄▄▄████
...
[INFO] Escanea el codigo QR con tu WhatsApp
```

### Paso 6.2: Abrir WhatsApp en el Celular

1. Abre WhatsApp en tu celular
2. Toca los **tres puntos** (menu) en la esquina superior derecha
3. Selecciona **"Dispositivos vinculados"**
4. Toca **"Vincular un dispositivo"**

### Paso 6.3: Escanear el QR

1. Apunta la camara del celular al QR en la terminal
2. Espera a que se complete la vinculacion

### Paso 6.4: Verificar Conexion Exitosa

En la terminal veras:

```
[INFO] Conexion establecida
[INFO] Sesion guardada en ./baileys_auth_info/
[INFO] WhatsApp conectado. Listo para recibir mensajes.
```

### La Sesion se Guarda

Despues de escanear el QR por primera vez, la sesion se guarda en:
```
fararoni-sidecar-wa/baileys_auth_info/
```

La proxima vez que inicies el sidecar, **NO necesitaras escanear de nuevo**.

### Cuando Expira la Sesion (IMPORTANTE)

La sesion de WhatsApp Web **NO es permanente**. Puede expirar o invalidarse por:

| Causa | Descripcion |
|-------|-------------|
| **Inactividad prolongada** | Si no usas el bot por varios dias, WhatsApp cierra la sesion |
| **Cierre manual** | Si vas a "Dispositivos vinculados" en tu celular y cierras la sesion |
| **Nuevo dispositivo** | Si vinculas otro dispositivo y excedes el limite de dispositivos |
| **Actualizacion de WhatsApp** | Ocasionalmente, actualizaciones de WhatsApp invalidan sesiones |
| **Cambio de red** | Cambios drasticos en la IP o ubicacion pueden disparar verificacion |

### Sintomas de Sesion Expirada

Cuando la sesion expira, veras errores como:

```
[ERROR] Connection Failure
[WARN] Sesion cerrada. Elimina baileys_auth_info y reinicia.
```

O el sidecar se queda intentando reconectar infinitamente.

### Como Re-autenticar

Cuando la sesion expire, **DEBES eliminar la carpeta de autenticacion** y escanear
el QR de nuevo:

```bash
cd fararoni-sidecar-wa && rm -rf baileys_auth_info && npm start
```

**Por que es necesario eliminar `baileys_auth_info`?**

La carpeta `baileys_auth_info/` contiene:
- Claves criptograficas de la sesion
- Tokens de autenticacion
- Credenciales de señal (Signal Protocol)

Cuando WhatsApp invalida la sesion en el servidor, estas credenciales locales
quedan **desincronizadas**. El cliente intenta usar credenciales viejas que el
servidor ya no acepta, causando un loop infinito de reconexion.

**Eliminar la carpeta fuerza una autenticacion limpia desde cero.**

### Automatizar Deteccion de Sesion Expirada

El sidecar detecta automaticamente cuando la sesion expira y muestra:

```
[WARN] [BAILEYS] Sesion cerrada. Elimina baileys_auth_info y reinicia.
```

Simplemente ejecuta:
```bash
cd fararoni-sidecar-wa && rm -rf baileys_auth_info && npm start
```

Y escanea el nuevo QR.

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
curl http://localhost:3000/health
```

Respuesta esperada:
```json
{"status": "connected", "phone": "+522291234567"}
```

### 7.3 Prueba End-to-End

1. Desde **otro telefono** (o un amigo), envia un mensaje a tu numero de WhatsApp
2. El mensaje debe ser procesado por Fararoni
3. Deberias recibir una respuesta automatica

### 7.4 Verificar Logs

**Terminal del Sidecar:**
```
[INGRESS] +522299876543 -> "Hola, necesito ayuda"
[EGRESS] +522299876543 <- "Hola! Soy Fararoni, en que puedo ayudarte?"
```

**Terminal de Fararoni:**
```
[OmniChannel] Processing message from whatsapp:+522299876543
[OmniChannel] Response sent to whatsapp:+522299876543
```

---

## 8. Seguridad

### 8.1 Filtro de Grupos

Por defecto, el sidecar **SOLO responde a chats privados** (1:1).
Los mensajes de **grupos son IGNORADOS** para evitar:

- Respuestas no deseadas en grupos familiares
- Spam accidental
- Consumo excesivo de tokens LLM

| Tipo de Chat | Procesado? |
|--------------|------------|
| Chat privado (1:1) | SI |
| Grupo | NO |
| Lista de difusion | NO |

### 8.2 Como Funciona el Filtro

En el codigo del sidecar (`index.js`):

```javascript
// Si el mensaje viene de un grupo, ignorarlo
if (remoteJid.endsWith('@g.us')) {
    console.log('[FILTRO] Ignorando mensaje de grupo:', remoteJid);
    return;
}
```

### 8.3 Habilitar Grupos Especificos (Opcional)

Si necesitas que el bot funcione en ciertos grupos:

```javascript
// Lista de grupos permitidos
const ALLOWED_GROUPS = [
    '123456789-1234567890@g.us',
    '987654321-0987654321@g.us'
];

// Modificar el filtro
if (remoteJid.endsWith('@g.us') && !ALLOWED_GROUPS.includes(remoteJid)) {
    return; // Ignorar grupos no autorizados
}
```

### 8.4 Riesgo de Ban

Meta puede detectar automatizacion y banear tu numero. Para minimizar riesgo:

| Practica | Riesgo |
|----------|--------|
| Responder a mensajes entrantes | Bajo |
| Enviar mensajes masivos (spam) | **MUY ALTO** |
| Respuestas muy rapidas (< 1 segundo) | Medio |
| Uso moderado (< 100 mensajes/dia) | Bajo |
| Uso intensivo (> 500 mensajes/dia) | Alto |

**Recomendaciones:**
- Usa Baileys solo para desarrollo y pruebas
- Para produccion, considera WhatsApp Enterprise
- No hagas spam
- No automatices mensajes salientes masivos

---

## 9. Troubleshooting

### El QR no aparece

**Verificar:**
```bash
# Eliminar sesion anterior y reintentar
rm -rf baileys_auth_info
npm start
```

### Error "Session closed" o "Connection Failure"

**Causa:** La sesion de WhatsApp expiro o fue invalidada.

Esto ocurre cuando:
- Pasaron varios dias sin usar el bot
- Cerraste la sesion desde "Dispositivos vinculados" en tu celular
- WhatsApp actualizo y revoco sesiones antiguas
- Hubo cambio de IP/ubicacion que disparo verificacion

**Solucion (comando completo):**
```bash
cd fararoni-sidecar-wa && rm -rf baileys_auth_info && npm start
```

Luego escanea el nuevo QR con tu celular.

> **Por que eliminar la carpeta?** Las credenciales locales quedaron
> desincronizadas con el servidor de WhatsApp. Eliminarlas fuerza una
> autenticacion limpia desde cero.

### Error "ECONNREFUSED"

```
Error: connect ECONNREFUSED 127.0.0.1:7071
```

**Causa:** El Gateway no esta corriendo.

**Solucion:**
```bash
java --enable-preview -jar fararoni-core-0.11.30.jar --server
```

### El bot no responde

**Verificar:**

1. **Gateway corriendo:**
   ```bash
   curl http://localhost:7071/gateway/v1/health
   ```

2. **Sidecar corriendo:**
   ```bash
   curl http://localhost:3000/health
   ```

3. **Sesion activa:**
   - Si el health dice "disconnected", elimina la sesion y escanea QR de nuevo

4. **Filtro de grupos:**
   - Si el mensaje viene de un grupo, sera ignorado

### WhatsApp pide vincular de nuevo

**Causas posibles:**
- Pasaron varios dias sin usar el bot
- Cerraste sesion desde el celular
- Vinculaste otro dispositivo

**Solucion:**
```bash
rm -rf baileys_auth_info
npm start
# Escanear QR de nuevo
```

### Error "Número baneado" o "Tu número está suspendido"

**Causa:** Meta detecto automatizacion y bloqueo el numero.

**Soluciones:**
1. Esperar 24-72 horas y reintentar
2. Apelar el ban desde WhatsApp
3. Usar otro numero
4. Considerar WhatsApp Enterprise (sin riesgo de ban)

### Los mensajes llegan pero no hay respuesta

**Verificar:**
1. Ver logs de Fararoni Core
2. Buscar errores en `OmniChannelRouter`
3. Verificar que la API key de OpenAI/Anthropic este configurada

---

## 10. Comandos Utiles

### Iniciar Todo (Script)

Puedes crear un script `start-whatsapp.sh`:

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
cd /ruta/a/fararoni/fararoni-sidecar-wa
npm start
```

### Health Checks

```bash
# Gateway
curl http://localhost:7071/gateway/v1/health

# Sidecar WhatsApp
curl http://localhost:3000/health

# Ver QR actual (si esta reconectando)
curl http://localhost:3000/qr
```

### Resetear Sesion

```bash
# Si tienes problemas de conexion
cd fararoni-sidecar-wa
rm -rf baileys_auth_info
npm start
```

### Ver Logs en Tiempo Real

```bash
# Logs de Fararoni (si escribe a archivo)
tail -f ~/.fararoni/logs/fararoni.log | grep -E "(OmniChannel|WHATSAPP)"
```

### Detener Servicios

```bash
# Detener Gateway
pkill -f "fararoni-core"

# Detener Sidecar (Ctrl+C si esta en foreground, o)
pkill -f "fararoni-sidecar-wa"
```

### Variables de Entorno

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `SIDECAR_PORT` | 3000 | Puerto del servidor HTTP |
| `GATEWAY_URL` | http://localhost:7071/gateway/v1/inbound | URL del Gateway |
| `AUTH_DIR` | ./baileys_auth_info | Directorio para sesion |
| `LOG_LEVEL` | info | Nivel de log |

---

## Anexo A: Checklist de Implementacion

```
REQUISITOS
[ ] Node.js 20+ instalado
[ ] npm instalado
[ ] Fararoni Core instalado
[ ] Java 25+ con --enable-preview
[ ] Celular con WhatsApp

INSTALACION
[ ] npm install ejecutado en fararoni-sidecar-wa
[ ] modules.yml tiene whatsapp.enabled: true
[ ] gateway.rest.enabled: true

PUESTA EN MARCHA
[ ] Gateway corriendo en puerto 7071
[ ] Sidecar corriendo en puerto 3000
[ ] Codigo QR visible en terminal

VINCULACION
[ ] QR escaneado desde WhatsApp
[ ] Mensaje "Conexion establecida" en terminal
[ ] Sesion guardada en baileys_auth_info/

PRUEBAS
[ ] Health check de Gateway OK
[ ] Health check de Sidecar OK
[ ] Mensaje enviado desde otro celular
[ ] Respuesta recibida del asistente
```

---

## Anexo B: Diferencias con Enterprise

| Aspecto | Baileys (Este manual) | Enterprise |
|---------|----------------------|------------|
| Costo | Gratis | Pago por mensaje |
| Riesgo de ban | Si | No |
| API | No oficial | Oficial de Meta |
| Setup | 15 min | 2-5 dias |
| Soporte | Comunidad | Meta oficial |
| Templates | No | Si |
| Mensajes masivos | No recomendado | Si |
| Ideal para | Desarrollo | Produccion |

Para migrar a Enterprise, consulta: [MANUAL-WHATSAPP-ENTERPRISE.md](MANUAL-WHATSAPP-ENTERPRISE.md)

---

**Autor:** Equipo Fararoni
**Version:** 1.0
