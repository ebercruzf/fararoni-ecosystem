# Manual de Usuario: WhatsApp Enterprise (Meta Business API)

## Version: 1.0
## Audiencia: Administradores de sistemas, DevOps, Clientes Enterprise

---

## Indice

1. [Introduccion](#1-introduccion)
2. [Requisitos Previos](#2-requisitos-previos)
3. [Configuracion en Meta Business](#3-configuracion-en-meta-business)
4. [Configuracion en Fararoni](#4-configuracion-en-fararoni)
5. [Verificacion del Webhook](#5-verificacion-del-webhook)
6. [Pruebas de Conectividad](#6-pruebas-de-conectividad)
7. [Troubleshooting](#7-troubleshooting)
8. [Costos y Facturacion](#8-costos-y-facturacion)

---

## 1. Introduccion

### Que es WhatsApp Enterprise?

WhatsApp Enterprise (Meta Business API) es la API OFICIAL de Meta/Facebook para
integrar WhatsApp en aplicaciones empresariales. A diferencia de soluciones no
oficiales como Baileys, esta API:

- Tiene soporte oficial de Meta
- Garantiza 99.9% de disponibilidad (SLA)
- No tiene riesgo de ban por automatizacion
- Permite enviar mensajes masivos con templates aprobados

### Diferencia con Baileys (Sidecar)

| Caracteristica | Baileys | Meta Enterprise |
|----------------|---------|-----------------|
| Costo | Gratis | Pago por mensaje |
| Estabilidad | Variable | 99.9% SLA |
| Riesgo de ban | Si | No |
| Setup | Escanear QR | Verificar negocio |
| Ideal para | Desarrollo | Produccion |

### Cuando usar Meta Enterprise?

- Tienes un negocio legalmente constituido
- Necesitas atencion al cliente 24/7
- Quieres enviar notificaciones/campanas masivas
- El costo por mensaje es viable para tu negocio

---

## 2. Requisitos Previos

### 2.1 Requisitos de Negocio

- [ ] Empresa legalmente constituida
- [ ] Sitio web activo de la empresa
- [ ] Politica de privacidad publicada
- [ ] Numero de telefono dedicado (no usado en WhatsApp personal)

### 2.2 Requisitos Tecnicos

- [ ] Servidor con IP publica
- [ ] Certificado SSL valido (HTTPS obligatorio)
- [ ] Puerto 7071 accesible desde Internet
- [ ] Fararoni Core instalado y funcionando
- [ ] Java 25+ con --enable-preview

### 2.3 Cuentas Necesarias

- [ ] Cuenta de Facebook personal (del administrador)
- [ ] Cuenta de Meta for Developers
- [ ] WhatsApp Business Account (se crea durante el proceso)

### 2.4 Cifrado de Canales (Encryption Key)

Los tokens y credenciales de canales se almacenan cifrados en la base de datos local.
La clave de cifrado se configura con la variable de entorno `FARARONI_CHANNELS_ENCRYPTION_KEY`.

| Escenario | Key env var | DEV_MODE | Resultado |
|---|---|---|---|
| Produccion con key | `"abc123..."` | `false` | Usa la key del env |
| Produccion sin key | (vacia) | `false` | `LOG.severe` advierte, sin cifrado |
| Desarrollo con key | `"abc123..."` | `true` | Usa la key del env |
| Desarrollo sin key | (vacia) | `true` | Auto-genera key AES-256 persistente en `~/.fararoni/config/.dev-encryption-key` |

**Produccion (recomendado para WhatsApp Enterprise):**
```bash
export FARARONI_CHANNELS_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export FARARONI_ENV=production
java --enable-preview -jar fararoni-core-1.0.0.jar --server
```

**Desarrollo local:**
```bash
export FARARONI_DEV_MODE=true
java --enable-preview -jar fararoni-core-1.0.0.jar --server
# La key se genera automaticamente y persiste entre reinicios
```

> **Nota:** En desarrollo la key se guarda en `~/.fararoni/config/.dev-encryption-key`
> y se reutiliza en cada reinicio. En produccion debes proveer tu propia key
> y resguardarla — si la pierdes, los tokens cifrados no se pueden recuperar.

---

## 3. Configuracion en Meta Business

### Paso 3.1: Crear App en Meta for Developers

1. Ir a https://developers.facebook.com
2. Click en "My Apps" -> "Create App"
3. Seleccionar tipo: "Business"
4. Nombre: "Fararoni WhatsApp" (o el nombre de tu empresa)
5. Click "Create App"

### Paso 3.2: Agregar WhatsApp a la App

1. En el Dashboard de la App, buscar "WhatsApp"
2. Click "Set Up" en el producto WhatsApp
3. Meta te guiara para crear un WhatsApp Business Account

### Paso 3.3: Verificar tu Negocio

1. Ir a Meta Business Suite -> Settings -> Business Info
2. Completar informacion de la empresa:
   - Nombre legal
   - Direccion
   - Sitio web
   - Documentos de verificacion (RFC, acta constitutiva, etc.)
3. Esperar aprobacion (1-5 dias habiles)

### Paso 3.4: Registrar Numero de Telefono

1. En WhatsApp -> API Setup
2. Click "Add Phone Number"
3. Ingresar el numero dedicado para WhatsApp Business
4. Verificar via SMS o llamada
5. Guardar el **Phone Number ID** (ej: `123456789012345`)

### Paso 3.5: Generar Token de Acceso Permanente

1. En WhatsApp -> API Setup -> "Generate Access Token"
2. Para produccion, crear un System User:
   - Business Settings -> Users -> System Users
   - Click "Add" -> Nombre: "Fararoni API"
   - Asignar permisos: `whatsapp_business_messaging`, `whatsapp_business_management`
   - Generar Token
3. Guardar el **Access Token** (ej: `EAAGm0PX4ZCps...`)

> IMPORTANTE: El token de System User no expira. El token de usuario normal expira en 60 dias.

### Paso 3.6: Configurar Webhook

1. En WhatsApp -> Configuration -> Webhook
2. Callback URL: `https://tu-servidor.com:7071/gateway/v1/meta/webhook`
3. Verify Token: Crear un string secreto (ej: `mi_token_secreto_123`)
4. Click "Verify and Save"

Si la verificacion falla, asegurate de que:
- El servidor Fararoni este corriendo
- El puerto 7071 sea accesible desde Internet
- El certificado SSL sea valido

### Paso 3.7: Suscribirse a Eventos

1. En la seccion "Webhook fields"
2. Suscribirse a: `messages` (obligatorio)
3. Opcional: `message_template_status_update`

---

## 4. Configuracion en Fararoni

### Paso 4.1: Configurar Variables de Entorno

Editar tu archivo de inicio (`.bashrc`, `.zshrc`, o servicio systemd):

```bash
# WhatsApp Enterprise - Meta Business API
export META_VERIFY_TOKEN="mi_token_secreto_123"
export META_ACCESS_TOKEN="EAAGm0PX4ZCps..."
export META_PHONE_ID="123456789012345"
```

Recargar el entorno:
```bash
source ~/.bashrc  # o ~/.zshrc
```

### Paso 4.2: Editar modules.yml

Ubicacion: `~/.fararoni/config/modules.yml`

```yaml
gateway:
  rest:
    enabled: true
    port: 7071

  # ACTIVAR Meta Enterprise
  meta:
    enabled: true  # <-- Cambiar a true
    verify_token: "env:META_VERIFY_TOKEN"
    access_token: "env:META_ACCESS_TOKEN"
    phone_id: "env:META_PHONE_ID"
    api_version: "v18.0"

channels:
  # Desactivar Baileys si solo usaras Enterprise
  whatsapp:
    enabled: false  # <-- Opcional: desactivar Baileys

  # Activar canal Enterprise
  whatsapp_meta:
    enabled: true  # <-- Cambiar a true
    trust_level: SECURE_ENTERPRISE
    egress_adapter: "meta_enterprise"
    capabilities:
      - text
      - template
    timeout_ms: 10000
    retry_count: 3
```

### Paso 4.3: Reiniciar Fararoni

```bash
# Detener si esta corriendo
pkill -f fararoni-core

# Iniciar con la nueva configuracion
cd fararoni-core/target
java --enable-preview -jar fararoni-core-0.11.30.jar --server
```

### Paso 4.4: Verificar Logs de Inicio

Buscar estas lineas en los logs:

```
[MODULE-REGISTRY] Loaded module: OmniChannelGatewayModule
[INGRESS] RestIngressServer listening on port 7071
[META-ENTERPRISE] Meta webhook endpoint registered: /gateway/v1/meta/webhook
[META-EGRESS] Initialized for phone: 123456789012345
```

---

## 5. Verificacion del Webhook

### 5.1 Verificacion Automatica

Cuando configuras el webhook en Meta, este envia una peticion GET:

```
GET /gateway/v1/meta/webhook?hub.mode=subscribe&hub.challenge=1234567890&hub.verify_token=mi_token_secreto_123
```

Fararoni debe responder con el numero del `hub.challenge`:
```
1234567890
```

### 5.2 Verificacion Manual

Puedes probar manualmente con curl:

```bash
curl "https://tu-servidor.com:7071/gateway/v1/meta/webhook?hub.mode=subscribe&hub.challenge=test123&hub.verify_token=mi_token_secreto_123"
```

Respuesta esperada:
```
test123
```

---

## 6. Pruebas de Conectividad

### 6.1 Enviar Mensaje de Prueba (desde Meta)

1. En Meta for Developers -> WhatsApp -> API Setup
2. En "Send and receive messages", hay un numero de prueba de Meta
3. Agregar tu numero personal como destinatario de prueba
4. Enviar un mensaje de prueba desde la consola de Meta

### 6.2 Recibir Mensaje en Fararoni

1. Desde tu WhatsApp personal, envia un mensaje al numero Enterprise
2. Verificar en logs de Fararoni:

```
[META-WEBHOOK] Received message from: 5212291234567
[OmniChannel] Processing message: "Hola"
[OmniChannel] Response sent to: 5212291234567
```

### 6.3 Verificar Respuesta

El robot de Fararoni debe responder automaticamente al mensaje.

---

## 7. Troubleshooting

### Error: "Webhook verification failed"

**Causa**: El verify_token no coincide o el servidor no responde.

**Solucion**:
1. Verificar que META_VERIFY_TOKEN coincida exactamente
2. Verificar que el puerto 7071 sea accesible: `curl http://localhost:7071/gateway/v1/health`
3. Verificar certificado SSL: `curl https://tu-servidor.com:7071/gateway/v1/health`

### Error: "Invalid OAuth access token"

**Causa**: Token expirado o incorrecto.

**Solucion**:
1. Regenerar token en Meta for Developers
2. Actualizar META_ACCESS_TOKEN
3. Reiniciar Fararoni

### Error: "Phone number not registered"

**Causa**: El Phone ID no corresponde al numero verificado.

**Solucion**:
1. Ir a Meta for Developers -> WhatsApp -> Phone Numbers
2. Copiar el Phone Number ID correcto
3. Actualizar META_PHONE_ID

### Los mensajes no llegan a Fararoni

**Verificar**:
1. El webhook esta suscrito a `messages`
2. El firewall permite trafico al puerto 7071
3. El servidor tiene certificado SSL valido
4. Revisar logs: `tail -f ~/.fararoni/logs/fararoni.log | grep META`

### El robot no responde

**Verificar**:
1. El mensaje llega al bus: buscar `agency.input.whatsapp` en logs
2. El OmniChannelRouter procesa: buscar `[OmniChannel]` en logs
3. El egress funciona: buscar `[META-EGRESS]` en logs

---

## 8. Costos y Facturacion

### 8.1 Modelo de Precios de Meta (2026)

Meta cobra por **conversacion**, no por mensaje individual:

| Tipo de Conversacion | Costo Aproximado (MXN) |
|---------------------|------------------------|
| Marketing | $0.80 - $1.50 |
| Utility | $0.40 - $0.80 |
| Authentication | $0.30 - $0.60 |
| Service (iniciada por usuario) | $0.15 - $0.30 |

> Los precios varian por pais y volumen. Consulta https://business.whatsapp.com/products/platform-pricing

### 8.2 Ventana de 24 horas

- Si el usuario inicia la conversacion, tienes 24 horas para responder GRATIS
- Despues de 24 horas, solo puedes enviar templates aprobados (con costo)

### 8.3 Templates (Mensajes Masivos)

Para enviar el primer mensaje a un usuario (sin que el haya escrito primero):

1. Crear template en Meta Business -> WhatsApp -> Message Templates
2. Esperar aprobacion (1-24 horas)
3. Usar el template desde Fararoni:

```java
metaAdapter.sendTemplate("5212291234567", "welcome_message", "es");
```

### 8.4 Facturacion

- Meta factura mensualmente
- Se requiere metodo de pago (tarjeta de credito)
- Puedes configurar alertas de gasto en Meta Business Suite

---

## Anexo A: Checklist de Implementacion

```
PRE-REQUISITOS
[ ] Empresa legalmente constituida
[ ] Sitio web con politica de privacidad
[ ] Numero de telefono dedicado
[ ] Servidor con HTTPS y puerto 7071 abierto

META BUSINESS
[ ] App creada en Meta for Developers
[ ] WhatsApp Business Account creado
[ ] Negocio verificado
[ ] Numero de telefono registrado
[ ] Token de System User generado
[ ] Webhook configurado y verificado
[ ] Suscripcion a evento "messages"

FARARONI
[ ] Variables de entorno configuradas
[ ] modules.yml actualizado
[ ] gateway.meta.enabled: true
[ ] channels.whatsapp_meta.enabled: true
[ ] Fararoni reiniciado
[ ] Logs confirman inicializacion

PRUEBAS
[ ] Webhook verification exitosa
[ ] Mensaje de prueba recibido
[ ] Respuesta enviada correctamente
```

---

## Anexo B: Comandos Utiles

```bash
# Ver logs en tiempo real
tail -f ~/.fararoni/logs/fararoni.log | grep -E "(META|WHATSAPP)"

# Verificar health del gateway
curl http://localhost:7071/gateway/v1/health

# Verificar webhook externamente
curl "https://tu-servidor.com:7071/gateway/v1/meta/webhook?hub.mode=subscribe&hub.challenge=test&hub.verify_token=TU_TOKEN"

# Ver variables de entorno
echo $META_ACCESS_TOKEN | head -c 20
echo $META_PHONE_ID
echo $META_VERIFY_TOKEN
```

---

## Soporte

- **Documentacion Meta**: https://developers.facebook.com/docs/whatsapp
- **WhatsApp Business Help**: https://business.whatsapp.com/support

---

**Autor:** Equipo Fararoni
**Version:** 1.0
