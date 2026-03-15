# PROTOCOLO FARARONI S.A.T.I.

## Ficha Tecnica Oficial

**Nombre:** Sovereign Agnostic Transport Interface (S.A.T.I.)

**Version:** 1.0 (Especificacion 2026)

**Naturaleza:** Protocolo de Transporte y Orquestacion de Capacidades

**Dependencia:** Requiere un bus de eventos (ej. Sovereign Event Bus / NATS)

**Fecha:** 2026-02-25

---

## 1. Definicion Conceptual

S.A.T.I. es un estandar de comunicacion asincrona diseñado para blindar la
ejecucion de herramientas externas (MCP, APIs, Scripts) mediante el uso de
**Sidecars Centinelas**.

Su objetivo es garantizar que el Kernel nunca pierda el control, sin importar
el estado de salud del recurso externo.

**S.A.T.I. no es solo codigo; es la "Ley" de como deben comportarse los Sidecars.**

---

## 2. Los 3 Pilares de un Nodo S.A.T.I.

Cualquier implementacion (Java, Node.js, Python, Go) debe cumplir con:

### Pilar 1: Salud (Heartbeat)

El nodo debe emitir un latido cada 5-10 segundos al topico
`fararoni.sati.registry` con metricas de latencia real y modo de transporte.

```
Topico: fararoni.sati.registry
Intervalo: 5000ms (recomendado)
Contenido: Metricas de salud + modo de transporte
```

### Pilar 2: Resiliencia (Watchdog)

El nodo debe monitorear el recurso que envuelve. Si el recurso no responde
en < 500ms, el nodo debe tener la autonomia para reiniciarlo (Auto-Resurreccion).

```
Timeout: 500ms (ping)
Accion: destroyForcibly() + restart()
Intervalo de monitoreo: 5000ms
```

### Pilar 3: Obediencia (Control)

El nodo debe escuchar el topico de emergencia `fararoni.sati.control.panic`
y ejecutar un reinicio forzado inmediato al recibir la senal `HARD_REBOOT`.

```
Topico: fararoni.sati.control.panic
Comandos: HARD_REBOOT, SOFT_RESTART, STATUS_REPORT
Accion: Reinicio inmediato del recurso envuelto
```

---

## 3. Modos de Transporte

### 3.1 STDIO-TUNNEL (Recomendado)

El Sidecar lanza el recurso como proceso hijo y se comunica via tuberias (pipes)
del sistema operativo. El recurso NO abre puertos.

| Aspecto | Valor |
|---------|-------|
| Seguridad | MAXIMA - Invisible para la red |
| Latencia | MINIMA - Sin stack TCP/IP |
| Complejidad | BAJA - ProcessBuilder nativo |

### 3.2 REST-BRIDGE (Legacy)

El Sidecar se conecta a un recurso HTTP existente. El recurso expone un puerto.

| Aspecto | Valor |
|---------|-------|
| Seguridad | MEDIA - Puerto expuesto |
| Latencia | NORMAL - Stack HTTP |
| Complejidad | MEDIA - Requiere servidor HTTP |

---

## 4. Especificacion del Mensaje de Estado (JSON)

Para que el Kernel reconozca un nodo como "Soberano", el mensaje debe
seguir este esquema:

```json
{
  "sidecar_id": "string (UUID corto)",
  "type": "string (MCP_BRIDGE, DISCORD, etc.)",
  "mode": "STDIO-TUNNEL | REST-BRIDGE",
  "priority": "int (0-100, donde 100 es dominante)",
  "status": "READY | DEGRADED | RESTARTING | TIMEOUT | OFFLINE",
  "process_pid": "long (solo STDIO-TUNNEL)",
  "restart_count": "int (numero de auto-resurrecciones)",
  "capabilities": [
    {"name": "string", "description": "string"}
  ],
  "metrics": {
    "latency_us": "long (microsegundos)",
    "active_requests": "int",
    "total_requests": "long",
    "total_errors": "long",
    "load_factor": "double (0.0-1.0)",
    "system_load": "double"
  },
  "timestamp": "long (epoch millis)"
}
```

### Ejemplo Completo (STDIO-TUNNEL)

```json
{
  "sidecar_id": "mcp-bridge-a1b2c3d4",
  "type": "MCP_BRIDGE",
  "mode": "STDIO-TUNNEL",
  "priority": 100,
  "status": "READY",
  "process_pid": 12345,
  "restart_count": 0,
  "capabilities": [
    {"name": "read_file", "description": "Lee archivos via MCP"},
    {"name": "write_file", "description": "Escribe archivos via MCP"},
    {"name": "list_directory", "description": "Lista directorios via MCP"}
  ],
  "metrics": {
    "latency_us": 850,
    "active_requests": 0,
    "total_requests": 1500,
    "total_errors": 3,
    "load_factor": 0.15,
    "system_load": 1.25
  },
  "timestamp": 1740524400000
}
```

---

## 5. Topicos NATS

| Topico | Proposito | Direccion |
|--------|-----------|-----------|
| `fararoni.sati.registry` | Heartbeats SATI | Sidecar -> Kernel |
| `fararoni.registry.updates` | Heartbeats legacy | Sidecar -> Kernel |
| `fararoni.skills.mcp` | Peticiones MCP (Queue Group) | Kernel -> Sidecar |
| `fararoni.sati.control.panic` | Ordenes de emergencia | Kernel -> Sidecar |

---

## 6. Algoritmo de Seleccion

El Kernel usa el siguiente algoritmo para seleccionar el mejor Sidecar:

```
Score = (Priority * 1,000,000) - LatencyUs - (LoadFactor * 100,000) - (ActiveRequests * 10,000)
```

### Criterios de Salud

Un Sidecar es **saludable** si:

1. `status == "READY"`
2. `lastSeen < 15 segundos`
3. `latency < 100,000 microsegundos (100ms)`

---

## 7. Ventajas de la Abstraccion

| Ventaja | Descripcion |
|---------|-------------|
| **Agnostico al Lenguaje** | Java 25 (Grado Militar), Node.js (agilidad), Python (ML), Go (eficiencia) |
| **Soberania de Datos** | Datos viajan encapsulados dentro del bus privado, no por red abierta |
| **Escalabilidad** | Multiples Sidecars en Queue Group, sin conflicto de puertos |
| **Resiliencia** | Auto-resurreccion + Panic Button para recuperacion masiva |

---

## 8. Implementaciones de Referencia

### 8.1 Java 25 (Grado Militar)

**Ubicacion:** `fararoni-sidecar-mcp/`

**Caracteristicas:**
- Virtual Threads (Project Loom)
- STDIO-TUNNEL nativo
- Watchdog con Auto-Resurreccion
- Fat JAR (~3MB)

**Comando:**
```bash
java --enable-preview \
  -Dmcp.exec.command="npx -y @modelcontextprotocol/server-filesystem /tmp/sandbox" \
  -jar fararoni-sidecar-mcp-1.0.0.jar
```

### 8.2 Node.js (Esqueleto)

```javascript
// sati-sidecar.js - Esqueleto S.A.T.I. en Node.js
const { connect } = require('nats');

class SatiSidecar {
  constructor(id) {
    this.id = id;
    this.status = 'STARTING';
    this.mode = 'STDIO-TUNNEL';
  }

  async start() {
    this.nc = await connect({ servers: 'nats://localhost:4222' });

    // Pilar 1: Heartbeat
    setInterval(() => this.sendHeartbeat(), 5000);

    // Pilar 3: Panic listener
    const sub = this.nc.subscribe('fararoni.sati.control.panic');
    for await (const msg of sub) {
      if (msg.data.toString() === 'HARD_REBOOT') {
        await this.restart();
      }
    }
  }

  sendHeartbeat() {
    const heartbeat = {
      sidecar_id: this.id,
      mode: this.mode,
      status: this.status,
      metrics: { latency_us: this.lastLatency }
    };
    this.nc.publish('fararoni.sati.registry', JSON.stringify(heartbeat));
  }

  // Pilar 2: Watchdog (implementar segun recurso)
  async restart() { /* ... */ }
}
```

---

## 9. Pruebas de Validacion FASE 80.1.15

### 9.1 Prueba STDIO-TUNNEL (2026-02-25)

**Estado:** EXITOSA

**Configuracion:**
```bash
java --enable-preview \
  -Dfararoni.nats.url=nats://localhost:4222 \
  -Dmcp.exec.command="npx -y @modelcontextprotocol/server-filesystem /tmp/fararoni-sandbox" \
  -jar fararoni-sidecar-mcp-1.0.0.jar
```

**Resultados:**

| Verificacion | Resultado |
|--------------|-----------|
| Sidecar lanza MCP como proceso hijo | [OK] PID: 4064 |
| MCP Server corriendo | [OK] PID: 4095 |
| MCP no abre puertos HTTP | [OK] Invisible para red |
| Comunicacion via pipes STDIO | [OK] Latencia zero |
| Ambos procesos estables | [OK] CPU < 1% |

**Evidencia:**

```
=== Estado STDIO-TUNNEL ===

Sidecar Java:       PID: 4064 | Corriendo
MCP Server (hijo):  PID: 4095 | Corriendo

Puertos abiertos:   NINGUNO (modo STDIO-TUNNEL)
Sandbox:            /tmp/fararoni-sandbox/demo.txt
```

### 9.2 Componentes Implementados

| Componente | Ubicacion | Estado |
|------------|-----------|--------|
| `McpBridgeSidecar.java` | fararoni-sidecar-mcp | STDIO-TUNNEL |
| `McpSidecarMain.java` | fararoni-sidecar-mcp | Panic listener |
| `SatiController.java` | fararoni-core/sati | CREADO |
| `SATIRouter.java` | fararoni-core/sati | Selector inteligente |
| `SatiHealthMonitor.java` | fararoni-core/diagnostics | Dashboard |
| `SidecarNode.java` | fararoni-core/sati | Record enjambre |

---

## 10. Comandos de Control (SatiController)

Disponibles en el CLI del Kernel:

| Comando | Accion |
|---------|--------|
| `panic` | Hard reboot de TODOS los Sidecars |
| `soft-restart` | Reinicio suave (espera peticiones) |
| `status-report` | Solicita heartbeat inmediato |
| `restart <id>` | Reinicia un Sidecar especifico |

---

## 11. Conclusion

S.A.T.I. es una **Constitucion Tecnica** que define como deben comportarse
los Sidecars en el ecosistema Fararoni.

**Cualquier programador puede crear un Sidecar en cualquier lenguaje,
siempre y cuando respete los 3 Pilares:**

1. Hablar por el Bus (Heartbeat)
2. Monitorear su recurso (Watchdog)
3. Obedecer al Kernel (Panic Button)

---

*Documento generado para FASE 80.1.15*
*Fararoni Framework - Soberania Tecnologica*
