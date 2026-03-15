# MANUAL: MCP Sidecar con S.A.T.I.

## FASE 80.1.14 - 80.1.15

**Fecha:** 2026-02-25
**Version:** 1.0.0
**Java:** 25 (Virtual Threads)

---

## Indice

1. [Introduccion](#1-introduccion)
2. [Jerarquia de Comunicacion](#2-jerarquia-de-comunicacion-grado-militar)
3. [Multiples Instancias](#3-multiples-instancias-sin-conflicto-de-puertos)
4. [Arquitectura SATI](#4-arquitectura-sati)
5. [Componentes del SDK](#5-componentes-del-sdk)
6. [Componentes del Kernel](#6-componentes-del-kernel)
7. [Instalacion y Compilacion](#7-instalacion-y-compilacion)
8. [Configuracion](#8-configuracion)
9. [Despliegue](#9-despliegue)
10. [Protocolo de Comunicacion](#10-protocolo-de-comunicacion)
11. [Algoritmo de Seleccion](#11-algoritmo-de-seleccion)
12. [Monitoreo](#12-monitoreo)
13. [Serie de Puertos](#13-serie-de-puertos)
14. [Dashboard de Emergencia](#14-dashboard-de-emergencia-satihealthmonitor)
15. [Prueba Reina: Resiliencia](#15-prueba-reina-resiliencia-sin-gateway)
16. [Troubleshooting](#16-troubleshooting)

---

## 1. Introduccion

### Que es S.A.T.I.?

**S.A.T.I. (Self-Aware Telemetry Intelligence)** es el sistema de enrutamiento inteligente de Fararoni que reemplaza el Round-Robin ciego de NATS con seleccion basada en merito.

### Problema que resuelve

NATS Queue Groups distribuyen mensajes de forma Round-Robin:
```
Peticion 1 -> Sidecar A (Raspberry Pi, lento)
Peticion 2 -> Sidecar B (Xeon Server, rapido)
Peticion 3 -> Sidecar A (lento de nuevo)
```

Con SATI, el Kernel **sabe** que Sidecar B es mas rapido y le envia el 90% del trafico.

### Principios

| Rol | Responsabilidad |
|-----|-----------------|
| **Sidecar (Sujeto)** | Mide su propia salud y reporta metricas |
| **Kernel (Juez)** | Recibe reportes y decide a quien enviar peticiones |

---

## 2. Jerarquia de Comunicacion (Grado Militar)

Esta es la clave de por que S.A.T.I. es de "Grado Militar":

**El Sidecar MCP se comunica directamente con el Bus (NATS) porque el Gateway
es una "puerta de entrada" para extraños, mientras que el Sidecar es un
"soldado interno".**

### 2.1 El Gateway (7071) es para "Civiles"

El Gateway HTTP es un protocolo **Sincrono y Fragil**.

- Se usa para que Discord (Node.js) o WhatsApp envien mensajes hacia adentro
- Ellos **no pueden** estar conectados permanentemente al bus del Kernel
- Si el Gateway recibe algo, tiene que esperar respuesta inmediata
- Si el Kernel esta ocupado, el Gateway se **bloquea**

### 2.2 El Bus (NATS) es para el "Ejercito" (Sidecars MCP)

Tu Sidecar MCP esta en **Java 25**. Al estar en tu red interna, no necesita
pedir permiso al Gateway. Se conecta **directamente** al Bus (NATS).

**Por que es mejor que el Sidecar hable directo con el Kernel por el Bus?**

| Ventaja | Descripcion |
|---------|-------------|
| **Asincronia Real** | El Kernel lanza una tarea y sigue trabajando. Cuando el Sidecar termina, suelta la respuesta en el Bus. No hay hilos bloqueados. |
| **Supervivencia** | Si el Gateway (7071) se cae por un ataque de trafico, el Sidecar MCP y el Kernel **siguen trabajando** porque su comunicacion por NATS es interna y privada. |
| **Prioridad 100** | El Gateway no entiende de prioridades. El Bus SI entiende que el Sidecar MCP tiene `priority: 100` y le entrega los mensajes antes que a nadie. |

### 2.3 Tabla de Flujos de Comunicacion

| Flujo | Camino de los Datos | Protocolo |
|-------|---------------------|-----------|
| **Entrada Externa** | Discord (Node) → Gateway (7071) → Bus → Kernel | HTTP → NATS |
| **Trabajo Interno** | Kernel ↔ Bus (NATS) ↔ Sidecar MCP (Java 25) | NATS Directo |

### 2.4 Por que el Sidecar MCP NO usa el Gateway?

Si el Sidecar de MCP usara el Gateway, estarias creando un **cuello de botella**:

```
CON GATEWAY (MALO - Cuello de botella):
  Kernel → Gateway → Sidecar → Gateway → Kernel
  4 saltos, doble de trabajo

SIN GATEWAY (BUENO - Directo):
  Kernel ↔ Bus (NATS) ↔ Sidecar MCP
  2 saltos, comunicacion directa
```

Al conectarse directo al Bus (NATS), el Sidecar de MCP se vuelve:
- **Invisible** para el mundo exterior (seguridad)
- **Omnipresente** para el Kernel (disponibilidad)

### 2.5 Diagrama de Arquitectura

```
                    MUNDO EXTERIOR
                         |
        +----------------v----------------+
        |      GATEWAY REST (7071)        |  <-- Para "Civiles"
        |   Discord, WhatsApp, Telegram   |      (HTTP Sincrono)
        +----------------+----------------+
                         |
                         v
    +--------------------+--------------------+
    |              BUS NATS (4222)            |  <-- Medula Espinal
    |         (Comunicacion Interna)          |      (Asincrono)
    +----+---------------+---------------+----+
         |               |               |
    +----v----+     +----v----+     +----v----+
    | Kernel  |     | Sidecar |     | Sidecar |  <-- "Ejercito"
    | Maestro |     | MCP #1  |     | MCP #2  |      (Java 25)
    +---------+     +---------+     +---------+
```

### 2.6 Por que el SATIRouter escucha el Bus y NO el Gateway?

Porque el Bus es el unico lugar donde podemos medir la **latencia real**
sin el ruido del trafico HTTP.

```java
// SATIRouter.java - Escucha el Bus (no HTTP)
bus.subscribe("sati.registry", String.class, envelope -> {
    processHeartbeat(envelope.payload());  // Latencia pura, sin HTTP
});
```

---

## 3. Multiples Instancias (Sin Conflicto de Puertos)

### 3.1 Por que NO hay conflicto de puertos?

El Sidecar MCP **NO expone un servidor HTTP**. Solo actua como **cliente NATS**.

```
SIDECAR TRADICIONAL (Node.js Discord):
  - ESCUCHA en puerto 3002 (servidor HTTP)
  - Si lanzas otro, CONFLICTO de puerto

SIDECAR MCP (Java 25):
  - NO ESCUCHA ningun puerto
  - Solo SE CONECTA a NATS (cliente)
  - Puedes lanzar 100 instancias sin conflicto
```

### 3.2 Diagrama de Multiples Sidecars

```
                    NATS SERVER (4222)
                           |
         +-----------------+-----------------+
         |                 |                 |
    +----v----+       +----v----+       +----v----+
    | Sidecar |       | Sidecar |       | Sidecar |
    | MCP #1  |       | MCP #2  |       | MCP #3  |
    | (cliente)|      | (cliente)|      | (cliente)|
    +---------+       +---------+       +---------+
         |                 |                 |
    +----v----+       +----v----+       +----v----+
    |MCP Srv  |       |MCP Srv  |       |MCP Srv  |
    |:8000    |       |:8001    |       |:8002    |
    +---------+       +---------+       +---------+
```

### 3.3 Queue Groups: Distribucion Automatica

NATS usa **Queue Groups** para distribuir peticiones automaticamente:

```java
// McpSidecarMain.java - Todos los Sidecars usan el mismo grupo
dispatcher.subscribe("fararoni.skills.mcp", "mcp-workers");
//                    ^-- Topico             ^-- Queue Group
```

Cuando llega una peticion a `fararoni.skills.mcp`:
- NATS elige **UN SOLO** Sidecar del grupo `mcp-workers`
- Los demas NO reciben el mensaje (evita duplicados)
- Si ese Sidecar esta ocupado, NATS elige otro

### 3.4 Comando para Lanzar Multiples Instancias (STDIO-TUNNEL)

```bash
# Terminal 1 - Sidecar MCP #1 (sandbox /tmp/sandbox1)
java --enable-preview \
  -Dmcp.exec.command="npx -y @modelcontextprotocol/server-filesystem /tmp/sandbox1" \
  -jar fararoni-sidecar-mcp-1.0.0.jar

# Terminal 2 - Sidecar MCP #2 (sandbox /tmp/sandbox2)
java --enable-preview \
  -Dmcp.exec.command="npx -y @modelcontextprotocol/server-filesystem /tmp/sandbox2" \
  -jar fararoni-sidecar-mcp-1.0.0.jar

# Terminal 3 - Sidecar MCP #3 (sandbox /tmp/sandbox3)
java --enable-preview \
  -Dmcp.exec.command="npx -y @modelcontextprotocol/server-filesystem /tmp/sandbox3" \
  -jar fararoni-sidecar-mcp-1.0.0.jar
```

**Nota:** En modo STDIO-TUNNEL no hay puertos HTTP. Cada Sidecar lanza su
propio proceso MCP como hijo. No hay conflictos de puertos.

### 3.5 Resumen de Puertos (STDIO-TUNNEL)

| Componente | Puerto | Tipo | Conflicto? |
|------------|--------|------|------------|
| NATS Server | 4222 | Servidor (1 instancia) | N/A |
| Gateway REST | 7071 | Servidor (1 instancia) | N/A |
| Sidecar MCP #1 | - | Cliente NATS + Proceso hijo | NO |
| Sidecar MCP #2 | - | Cliente NATS + Proceso hijo | NO |
| Sidecar MCP #N | - | Cliente NATS + Proceso hijo | NO |

**Ventaja STDIO-TUNNEL:** Los procesos MCP no abren puertos.
Son invisibles para `netstat`. Solo el Sidecar tiene acceso via pipes.

---

## 4. Arquitectura SATI

```
                    +-----------------------+
                    |      NATS Bus         |
                    |  fararoni.sati.registry
                    +----------+------------+
                               |
           +-------------------+-------------------+
           |                   |                   |
    +------v------+     +------v------+     +------v------+
    |  Sidecar A  |     |  Sidecar B  |     |  Sidecar C  |
    | MCP Bridge  |     | MCP Bridge  |     | MCP Bridge  |
    | lat: 5000us |     | lat: 2000us |     | lat: 50000us|
    +------+------+     +------+------+     +------+------+
           |                   |                   |
    +------v------+     +------v------+     +------v------+
    | MCP Server  |     | MCP Server  |     | MCP Server  |
    | (Node.js)   |     | (Node.js)   |     | (Node.js)   |
    +-------------+     +-------------+     +-------------+


                    +-----------------------+
                    |    SATI Router        |
                    |    (En el Kernel)     |
                    +-----------------------+
                    | registry: Map<ID, Node>
                    | getBestSidecar() -> B |
                    +-----------------------+
```

### Flujo de Datos

1. **Heartbeat (cada 5s):** Sidecar mide latencia y publica a NATS
2. **Registro:** SATIRouter recibe y actualiza tabla interna
3. **Peticion:** Usuario envia mensaje via Discord/CLI
4. **Seleccion:** Kernel consulta `getBestSidecar()`
5. **Ejecucion:** Mensaje va directamente al mejor Sidecar

---

## 5. Componentes del SDK (Modo STDIO-TUNNEL)

### 5.1 McpBridgeSidecar.java

**Modo STDIO-TUNNEL** - El Sidecar NO usa HTTP. Lanza el proceso MCP como hijo
y se comunica via tuberias (pipes) del sistema operativo.

**Ubicacion:** `fararoni-sidecar-mcp/src/main/java/dev/fararoni/enterprise/mcp/`

**Ventajas sobre HTTP:**

| Ventaja | Descripcion |
|---------|-------------|
| **Seguridad Total** | El servidor MCP no abre puertos. Invisible para atacantes. |
| **Latencia Zero** | No hay stack TCP/IP. Datos via kernel del SO. |
| **Soberania de Proceso** | Si MCP crashea, el Sidecar lo detecta y reinicia. |

**Metodos SATI:**

| Metodo | Descripcion |
|--------|-------------|
| `startOrRestart()` | Inicia o reinicia el proceso MCP hijo |
| `startWatchdog()` | Inicia monitor de Grado Militar (ping 500ms) |
| `sendAndReceive()` | Envia JSON-RPC y recibe respuesta via STDIO |
| `checkMcpHealth()` | Verifica si proceso esta vivo y responde |
| `getLastLatencyMicros()` | Ultima latencia medida |
| `getStatus()` | READY, RESTARTING, TIMEOUT, ERROR, OFFLINE |
| `getMode()` | Retorna "STDIO-TUNNEL" |
| `getProcessPid()` | PID del proceso MCP hijo |
| `getRestartCount()` | Numero de reinicios automaticos |

**Ejemplo de uso:**

```java
// STDIO-TUNNEL: El Sidecar lanza el proceso MCP directamente
McpBridgeSidecar bridge = new McpBridgeSidecar(
    "npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp/sandbox"
);

// Iniciar proceso y watchdog
bridge.startOrRestart();
bridge.startWatchdog();

// El watchdog monitorea cada 5s y auto-resucita si el proceso muere
System.out.printf("[MCP] Mode: %s, PID: %d, Status: %s%n",
    bridge.getMode(), bridge.getProcessPid(), bridge.getStatus());
```

### 5.2 McpSidecarMain.java

Orquestador del proceso con Virtual Threads (Java 25).

**Caracteristicas:**
- Modo STDIO-TUNNEL (sin HTTP)
- Conexion a NATS con reconexion infinita
- Heartbeat SATI cada 5 segundos con metricas de proceso
- Queue Group para distribucion Round-Robin
- Listener de SATI-Panic-Button para Hard Reset remoto
- Virtual Threads para escalabilidad masiva

**Topicos NATS:**

| Topico | Proposito |
|--------|-----------|
| `fararoni.sati.registry` | Heartbeats SATI con metricas |
| `fararoni.registry.updates` | Heartbeats legacy (compatibilidad) |
| `fararoni.skills.mcp` | Peticiones MCP (Queue Group) |
| `fararoni.sati.control.panic` | Ordenes de reinicio del Kernel |

**Heartbeat STDIO-TUNNEL (campos adicionales):**

```json
{
  "sidecar_id": "mcp-bridge-a1b2c3d4",
  "type": "MCP_BRIDGE",
  "mode": "STDIO-TUNNEL",
  "process_pid": 12345,
  "restart_count": 0,
  "status": "READY",
  "metrics": {
    "latency_us": 850,
    "active_requests": 0,
    "load_factor": 0.15
  }
}
```

---

## 6. Componentes del Kernel

### 6.1 SatiController.java (SATI-Panic-Button)

Controlador de Operaciones de Emergencia. El **Panic Button** de Grado Militar.

**Ubicacion:** `fararoni-core/src/main/java/dev/fararoni/core/core/sati/`

**Casos de uso:**

| Caso | Descripcion |
|------|-------------|
| **Limpieza de Estado Corrupto** | Hard Reset masivo cuando LLMs entran en bucle |
| **Hot-Swap de Configuracion** | Reiniciar procesos sin desconectar del bus |
| **Mitigacion de Ataques** | Desfibrilador para liberar recursos bloqueados |

**Comandos disponibles:**

```java
SatiController controller = new SatiController(bus);

// Hard reboot de TODOS los Sidecars (mata y resucita procesos MCP)
controller.broadcastPanic();

// Reinicio suave (espera peticiones en vuelo)
controller.broadcastSoftRestart();

// Solicita heartbeat inmediato de todos
controller.requestStatusReport();

// Reinicia un Sidecar especifico
controller.restartSidecar("mcp-bridge-a1b2c3d4");
```

**Integracion CLI:**

```java
// En el bucle de comandos del Kernel
if (controller.processCliCommand(input)) {
    // Comando SATI ejecutado
} else {
    // Otro comando...
}
```

**Ventaja sobre reiniciar JAR:**
El Sidecar NUNCA se desconecta de NATS. Solo mata el proceso hijo (MCP) y lo
resucita. RTO (Recovery Time Objective) cercano a cero.

---

### 6.2 SidecarNode.java

Record que representa un Sidecar en memoria.

**Ubicacion:** `fararoni-core/src/main/java/dev/fararoni/core/core/sati/`

**Campos:**

```java
public record SidecarNode(
    String id,           // "mcp-bridge-a1b2c3d4"
    String type,         // "MCP_BRIDGE"
    String target,       // "http://localhost:8000"
    int port,            // 7090
    int priority,        // 100 (dominante)
    long lastLatencyUs,  // 5000 (5ms)
    long lastSeen,       // timestamp
    String status,       // "READY"
    double loadFactor,   // 0.35
    int activeRequests,  // 2
    long totalRequests,  // 1500
    long totalErrors     // 3
)
```

**Metodos:**

| Metodo | Descripcion |
|--------|-------------|
| `isHealthy()` | READY + heartbeat reciente + latencia < 100ms |
| `isAvailable()` | Puede recibir peticiones (no OFFLINE) |
| `getScore()` | Calcula puntuacion para seleccion |

### 4.2 SATIRouter.java

Selector inteligente que mantiene el ranking de Sidecars.

**Metodos principales:**

```java
// Obtener el mejor Sidecar disponible
Optional<String> best = satiRouter.getBestSidecar();

// Obtener el mejor de un tipo especifico
Optional<String> mcpBest = satiRouter.getBestSidecarByType("MCP_BRIDGE");

// Listar todos disponibles (ordenados por score)
List<String> available = satiRouter.getAvailableSidecars();

// Imprimir estado del enjambre
satiRouter.printSwarmStatus();
```

**Ejemplo de integracion en el Kernel:**

```java
// Cuando llega una peticion MCP
Optional<String> target = satiRouter.getBestSidecar();

if (target.isPresent()) {
    String routingTopic = "fararoni.skills.mcp." + target.get();
    natsConn.publish(routingTopic, payload);
    LOG.info("SATI routing to: {}", target.get());
} else {
    // Fallback a Queue Group (Round-Robin)
    natsConn.publish("fararoni.skills.mcp", payload);
    LOG.warn("No SATI candidates, using Round-Robin");
}
```

---

## 7. Instalacion y Compilacion

### Prerequisitos

- Java 25 (con `--enable-preview`)
- Maven 3.9+
- NATS Server 2.10+

### Compilar MCP Sidecar

```bash
cd fararoni-sidecar-mcp
mvn clean package -DskipTests
```

**Resultado:** `target/fararoni-sidecar-mcp-1.0.0.jar` (Fat JAR ~3MB)

### Compilar Kernel (con SATI)

```bash
cd fararoni-core
mvn clean package -DskipTests -Dmaven.javadoc.skip=true
```

---

## 8. Configuracion

### Flags del Sidecar (Modo STDIO-TUNNEL)

| Flag | Default | Descripcion |
|------|---------|-------------|
| `-Dfararoni.nats.url` | `nats://localhost:4222` | URL del servidor NATS |
| `-Dmcp.exec.command` | `npx -y @modelcontextprotocol/server-filesystem /tmp/fararoni-sandbox` | Comando para lanzar servidor MCP |

**Nota:** El Sidecar ya NO usa `-Dmcp.server.url` ni `-Dsidecar.port`.
El proceso MCP se lanza como hijo y se comunica via STDIO (sin puertos).

### Constantes internas

| Constante | Valor | Descripcion |
|-----------|-------|-------------|
| `HEARTBEAT_INTERVAL_MS` | 5000 | Intervalo de heartbeat |
| `HEALTH_TIMEOUT` | 500ms | Timeout para health check |
| `STALE_THRESHOLD_MS` | 15000 | Tiempo sin heartbeat = muerto |
| `MAX_ACCEPTABLE_LATENCY_US` | 100000 | Latencia maxima aceptable (100ms) |

---

## 9. Despliegue

### Ejecutar un Sidecar individual (Modo STDIO-TUNNEL)

```bash
java --enable-preview \
  -Dfararoni.nats.url=nats://localhost:4222 \
  -Dmcp.exec.command="npx -y @modelcontextprotocol/server-filesystem /tmp/fararoni-sandbox" \
  -jar fararoni-sidecar-mcp-1.0.0.jar
```

**Nota:** El Sidecar lanza el proceso MCP como hijo y se comunica via STDIO.
No hay puertos HTTP. El servidor MCP es invisible para la red.

### Script de enjambre (spawn-swarm.sh)

```bash
# Desplegar 1 Kernel + 3 Sidecars MCP
./spawn-swarm.sh

# Desplegar con 5 Sidecars
./spawn-swarm.sh --sidecars 5

# Especificar URL de NATS
./spawn-swarm.sh --nats nats://192.168.1.100:4222

# Matar todos los procesos
./spawn-swarm.sh --kill
```

### Verificar estado

```bash
# Ver logs del Kernel
tail -f /tmp/fararoni-swarm/kernel-maestro.log

# Ver logs de Sidecar 0
tail -f /tmp/fararoni-swarm/sidecar-mcp-0.log

# Ver procesos activos
ps aux | grep fararoni
```

---

## 10. Protocolo de Comunicacion

### Mensaje Heartbeat SATI (Modo STDIO-TUNNEL)

```json
{
  "sidecar_id": "mcp-bridge-a1b2c3d4",
  "type": "MCP_BRIDGE",
  "mode": "STDIO-TUNNEL",
  "process_pid": 12345,
  "restart_count": 0,
  "priority": 100,
  "status": "READY",
  "capabilities": [
    {"name": "read_file", "description": "Lee archivos via MCP"},
    {"name": "write_file", "description": "Escribe archivos via MCP"},
    {"name": "list_directory", "description": "Lista directorios via MCP"}
  ],
  "metrics": {
    "latency_us": 5000,
    "health_check_us": 3200,
    "active_requests": 2,
    "total_requests": 1500,
    "total_errors": 3,
    "load_factor": 0.35,
    "system_load": 1.25,
    "vthreads_active": 42
  },
  "timestamp": 1740524400000
}
```

### Peticion MCP (JSON-RPC 2.0)

```json
{
  "jsonrpc": "2.0",
  "id": "uuid-here",
  "method": "tools/call",
  "params": {
    "name": "read_file",
    "arguments": {
      "path": "/tmp/fararoni-sandbox/demo.txt"
    }
  }
}
```

---

## 11. Algoritmo de Seleccion

### Formula de Score

```
Score = (Priority * 1,000,000) - LatencyUs - (LoadFactor * 100,000) - (ActiveRequests * 10,000)
```

### Ejemplo practico

| Sidecar | Priority | Latency | Load | Active | Score |
|---------|----------|---------|------|--------|-------|
| A | 100 | 5000us | 0.3 | 2 | 99,965,000 |
| B | 100 | 2000us | 0.5 | 1 | 99,938,000 |
| C | 50 | 1000us | 0.1 | 0 | 49,989,000 |

**Ganador:** Sidecar A (mayor prioridad + baja latencia)

### Criterios de salud

Un Sidecar es **saludable** si:
1. `status == "READY"`
2. `lastSeen` < 15 segundos
3. `latency` < 100,000 microsegundos (100ms)

---

## 12. Monitoreo

### Output de consola del Sidecar

```
====================================================
    FARARONI MCP BRIDGE SIDECAR (FASE 80.1.14)
====================================================
  Sidecar ID:   mcp-bridge-a1b2c3d4
  NATS URL:     nats://localhost:4222
  MCP Target:   http://localhost:8000
  Sidecar Port: 7090
====================================================
[NATS] Conexion establecida: NATS-SERVER-ID
[MCP] Suscrito a 'fararoni.skills.mcp' con QueueGroup 'mcp-workers'
[SATI] [OK] Heartbeat: latency=5000us, active=0, load=0.25
[SATI] [OK] Heartbeat: latency=4800us, active=1, load=0.30
```

### printSwarmStatus() del Kernel

```
========================================
  ESTADO DEL ENJAMBRE S.A.T.I.
========================================
  Total: 3 | Saludables: 2
----------------------------------------
  [OK] mcp-bridge-a1b2c3d4   | P:100 | L:  5000us | Load:0.30 | READY
  [OK] mcp-bridge-e5f6g7h8   | P:100 | L:  8000us | Load:0.45 | READY
  [!!] mcp-bridge-i9j0k1l2   | P:100 | L:150000us | Load:0.90 | DEGRADED
========================================
```

---

## 13. Serie de Puertos

### Serie 7000 (Core Infrastructure)

| Puerto | Servicio |
|--------|----------|
| 7070 | Kernel Maestro (Admin) |
| 7071 | Gateway REST |
| 7072-7079 | Reservado |

### Serie 7080 (OmniChannel Sidecars)

| Puerto | Sidecar |
|--------|---------|
| 7080 | Discord |
| 7081 | WhatsApp |
| 7082 | Telegram |
| 7084 | iMessage |

### Serie 7090 (MCP Sidecars)

| Puerto | Sidecar |
|--------|---------|
| 7090 | MCP Bridge 0 |
| 7091 | MCP Bridge 1 |
| 7092 | MCP Bridge 2 |
| 7093-7099 | Expansion |

### Serie 7270 (Kernels Obreros)

| Puerto | Servicio |
|--------|----------|
| 7270 | Kernel B (Worker) |
| 7271 | Kernel C (Worker) |
| 7272-7279 | Expansion |

### Serie 8000 (MCP Servers - Node.js)

| Puerto | Servidor |
|--------|----------|
| 8000 | MCP Server 0 (Filesystem) |
| 8001 | MCP Server 1 |
| 8002 | MCP Server 2 |

---

## 14. Dashboard de Emergencia (SatiHealthMonitor)

### Arquitectura de Soberania

```
+------------------+
|  Kernel (Java)   |  <-- Cerebro: orquesta todo
+--------+---------+
         |
+--------v---------+
| Sidecar (Java)   |  <-- Frontera: habla NATS + TCP/HTTP
+--------+---------+
         |
+--------v---------+
| Recurso Externo  |  <-- Caja Negra: MCP, DB, API, etc.
| (Cualquier lang) |
+------------------+
```

**Nota Critica:** El Kernel NUNCA toca el recurso externo directamente.
Solo toca al Sidecar de Java, quien detecta latencia y reporta degradacion.

### SatiHealthMonitor.java

Monitor de Grado Militar que reporta el estado del enjambre cada N segundos.

**Ubicacion:** `fararoni-core/src/main/java/dev/fararoni/core/core/diagnostics/`

**Uso basico:**

```java
// En el Kernel, despues de crear el SATIRouter
SATIRouter router = new SATIRouter(natsConn);

SatiHealthMonitor monitor = new SatiHealthMonitor(router);
monitor.startReporting(); // Reporte cada 30s por defecto
```

**Configuracion avanzada (Fluent API):**

```java
SatiHealthMonitor monitor = new SatiHealthMonitor(router)
    .withReportInterval(15)      // Reporte cada 15 segundos
    .withPruneInterval(120)      // Limpiar nodos obsoletos cada 2 minutos
    .withConsoleOutput(true);    // Imprimir a consola

monitor.startReporting();
```

**Metodos utiles:**

| Metodo | Descripcion |
|--------|-------------|
| `startReporting()` | Inicia el monitor periodico |
| `stop()` | Detiene el monitor |
| `isSwarmHealthy()` | true si hay al menos 1 Sidecar saludable |
| `getAverageLatency()` | Latencia promedio de Sidecars saludables |
| `getHealthReportString()` | Reporte en una linea |

### Output del Monitor

```
==================================================
  MONITOR DE SOBERANIA FARARONI S.A.T.I.
  19:45:30
==================================================
  Total: 3 | Saludables: 2 | Mejor: mcp-bridge-a1b2c3d4
--------------------------------------------------
  [OK] mcp-bridge-a1b2c3d4   | P:100 | L:  5000us | READY
  [OK] mcp-bridge-e5f6g7h8   | P:100 | L:  8000us | READY
  [!!] mcp-bridge-i9j0k1l2   | P:100 | L:150000us | DEGRADED
==================================================
```

### Alertas automaticas

Si no hay Sidecars saludables:

```
  [ALERTA] NO HAY SIDECARS SALUDABLES!
  El sistema operara en modo degradado.
```

Si no hay Sidecars registrados:

```
  [INFO] No hay Sidecars registrados.
  Ejecuta: ./spawn-swarm.sh --sidecars 3
```

---

## 15. Prueba Reina: Resiliencia sin Gateway

Esta prueba demuestra que tu arquitectura **S.A.T.I.** no es una simple API,
sino un **Ecosistema Resiliente**. Simula un escenario de "Guerra Tecnica":
el mundo exterior (Gateway) queda aislado, pero los organos internos
(Kernel y MCP) siguen operando.

### 15.1 Escenario de la Prueba

| Fase | Estado | Descripcion |
|------|--------|-------------|
| 1 | Inicial | Kernel y Sidecar MCP conectados a NATS |
| 2 | Accion | "Matamos" el Gateway (puerto 7071 deja de responder) |
| 3 | Objetivo | Kernel lanza tareas internas, Sidecar MCP las completa via NATS |

### 15.2 Por que esto demuestra Grado Militar?

```
ANTES DEL FALLO:
  Discord --> Gateway (7071) --> NATS --> Kernel --> NATS --> MCP
              [ONLINE]

DESPUES DEL FALLO:
  Discord --> Gateway (7071) --> X (BLOQUEADO)
              [OFFLINE]

  CLI Local --> NATS --> Kernel --> NATS --> MCP
                [SIGUE FUNCIONANDO]
```

**Conclusion:** El Bus (NATS) es la medula espinal. El Gateway es solo un
"adaptador de protocolo" para el mundo exterior.

### 15.3 Ejecucion de la Prueba

**Paso 1: Arrancar el enjambre**

```bash
./spawn-swarm.sh
```

**Paso 2: Verificacion inicial (debe funcionar)**

Envia un mensaje desde Discord. Ruta completa:
```
Discord -> Gateway (7071) -> NATS -> Kernel -> NATS -> MCP
```

**Paso 3: Simular fallo del Gateway**

Opcion A - Matar el proceso del Gateway:
```bash
# Encontrar el PID del Gateway
lsof -i :7071 | grep LISTEN

# Matar solo el Gateway (no el Kernel completo)
kill -9 <PID_GATEWAY>
```

Opcion B - Bloquear el puerto con firewall:
```bash
# macOS
sudo pfctl -e
echo "block in proto tcp from any to any port 7071" | sudo pfctl -f -

# Linux
sudo iptables -A INPUT -p tcp --dport 7071 -j DROP
```

**Paso 4: Prueba de Soberania (debe funcionar)**

Desde la consola local del Kernel (CLI que habla directo con NATS):
```bash
# El CLI usa NATS directamente, no el Gateway
java -jar fararoni-cli.jar "Lee el archivo de log del sandbox"
```

### 15.4 Output esperado en el Monitor SATI

```
========================================
  MONITOR DE SOBERANIA FARARONI S.A.T.I.
========================================
  Total: 3 | Saludables: 3
----------------------------------------
  [OK] mcp-bridge-0          | P:100 | L:   850us | Load:0.15 | READY
  [OK] mcp-bridge-1          | P:100 | L:   920us | Load:0.12 | READY
  [OK] mcp-bridge-2          | P:100 | L:  1100us | Load:0.18 | READY
========================================

[KERNEL] Gateway offline, pero proceso interno detectado.
[KERNEL] Dirigiendo peticion a: mcp-bridge-0 via NATS.
[MCP] Procesando en Virtual Thread: VirtualThread[#42]
[MCP-OUT] Respuesta obtenida en 4500us. Devolviendo al Bus...
```

### 15.5 Que demuestra esta prueba?

| Aspecto | Resultado |
|---------|-----------|
| **Aislamiento de Fallos** | DDoS al Gateway NO detiene procesos internos |
| **Independencia del Transporte** | El Gateway es solo un adaptador, la inteligencia esta en el Bus |
| **Recuperacion Silenciosa** | Al reiniciar el Gateway, vuelve a publicar al Bus que nunca dejo de funcionar |

### 15.6 Restaurar el Gateway

```bash
# Quitar bloqueo de firewall (macOS)
sudo pfctl -d

# Quitar bloqueo de firewall (Linux)
sudo iptables -D INPUT -p tcp --dport 7071 -j DROP

# O simplemente reiniciar el Kernel
./spawn-swarm.sh --kill
./spawn-swarm.sh
```

### 15.7 Conclusion para S.A.T.I.

Con esta prueba demuestras que:

1. **S.A.T.I. no depende de HTTP** - Funciona sin Gateway
2. **El Sidecar MCP es un recurso del sistema** - No una dependencia web
3. **Soberania significa:**
   - El sistema puede funcionar "ciego" (sin Gateway)
   - Pero NUNCA "descerebrado" (sin Bus NATS)

---

## 16. Troubleshooting

### Sidecar no aparece en el Registry

1. Verificar conexion a NATS:
   ```bash
   nats server ping --server=nats://localhost:4222
   ```

2. Verificar que el Sidecar esta publicando:
   ```bash
   nats sub "fararoni.sati.registry"
   ```

3. Revisar logs del Sidecar:
   ```bash
   tail -f /tmp/fararoni-swarm/sidecar-mcp-0.log | grep SATI
   ```

### Latencia muy alta (STDIO-TUNNEL)

1. Verificar que el proceso MCP hijo esta corriendo:
   ```bash
   ps aux | grep mcp-server-filesystem
   ```

2. Verificar que no hay puertos abiertos (modo STDIO-TUNNEL):
   ```bash
   lsof -i -P | grep mcp-server  # Debe estar vacio
   ```

3. Revisar carga del sistema:
   ```bash
   top -pid $(pgrep -f mcp-server)
   ```

### Kernel no selecciona el Sidecar correcto

1. Imprimir estado del enjambre:
   ```java
   satiRouter.printSwarmStatus();
   ```

2. Verificar que el Sidecar tenga `status: READY`
3. Verificar que la latencia sea < 100ms

### NATS no esta disponible

El Sidecar reintentara conexion indefinidamente:
```
[NATS] Evento: RECONNECTING
[WARN] Error en SATI Heartbeat: Connection closed
[NATS] Evento: CONNECTED
```

---

## 17. Guia de Pruebas FASE 80.1.15

### 17.1 Prerequisitos

Antes de iniciar la prueba, verificar:

```bash
# 1. Java 25 con preview features
java --version
# Debe mostrar: openjdk 25.x.x

# 2. Node.js y npx
node --version  # v25.x.x o superior
npx --version

# 3. NATS Server corriendo
pgrep -f nats-server || echo "NATS no esta corriendo"

# 4. JAR del Sidecar compilado
ls -la fararoni-sidecar-mcp/target/fararoni-sidecar-mcp-1.0.0.jar
```

### 17.2 Paso 1: Preparar el Sandbox

```bash
# Crear directorio sandbox
mkdir -p /tmp/fararoni-sandbox

# Crear archivo de prueba inicial
echo "Soberania de datos Fararoni activa" > /tmp/fararoni-sandbox/demo.txt

# Verificar
cat /tmp/fararoni-sandbox/demo.txt
```

### 17.3 Paso 2: Iniciar NATS (si no esta corriendo)

```bash
# Iniciar NATS Server en background
nats-server &

# Verificar que esta corriendo
pgrep -f nats-server
```

### 17.4 Paso 3: Lanzar el Sidecar STDIO-TUNNEL

```bash
cd fararoni-sidecar-mcp

java --enable-preview \
  -Dfararoni.nats.url=nats://localhost:4222 \
  -Dmcp.exec.command="npx -y @modelcontextprotocol/server-filesystem /tmp/fararoni-sandbox" \
  -jar target/fararoni-sidecar-mcp-1.0.0.jar
```

**Output esperado:**
```
====================================================
    FARARONI MCP BRIDGE SIDECAR (FASE 80.1.15)
    Modo: STDIO-TUNNEL (Sin HTTP)
====================================================
  Sidecar ID:   mcp-bridge-xxxxxxxx
  NATS URL:     nats://localhost:4222
  MCP Command:  npx -y @modelcontextprotocol/server-filesystem /tmp/fararoni-sandbox
====================================================
[NATS] Evento: CONNECTED
[NATS] Conexion establecida: NATS-SERVER-ID
[MCP-STDIO] Iniciando servidor MCP...
[MCP-STDIO] Proceso iniciado PID=XXXXX, Reinicios=1
[MCP] Suscrito a 'fararoni.skills.mcp' con QueueGroup 'mcp-workers'
[SATI] Escuchando ordenes de panico en 'fararoni.sati.control.panic'
====================================================
  MCP SIDECAR [mcp-bridge-xxxxxxxx] OPERATIVO
  Modo: STDIO-TUNNEL (Sin puertos HTTP)
  Java Version: 25
====================================================
[SATI] [OK] Heartbeat: mode=STDIO-TUNNEL, latency=XXXus, pid=XXXXX, restarts=1
```

### 17.5 Paso 4: Verificar Procesos (otra terminal)

```bash
# Verificar Sidecar Java
ps aux | grep fararoni-sidecar | grep -v grep

# Verificar MCP Server (proceso hijo)
ps aux | grep mcp-server-filesystem | grep -v grep

# Verificar que NO hay puertos HTTP abiertos
lsof -i -P | grep mcp-server
# Debe estar VACIO (modo STDIO-TUNNEL)
```

### 17.6 Paso 5: Verificar Heartbeats SATI

Si tienes el CLI de NATS instalado:

```bash
# Suscribirse al topico de heartbeats
nats sub "fararoni.sati.registry" --count=3
```

**Output esperado:**
```json
{
  "sidecar_id": "mcp-bridge-xxxxxxxx",
  "type": "MCP_BRIDGE",
  "mode": "STDIO-TUNNEL",
  "process_pid": 12345,
  "restart_count": 1,
  "status": "READY",
  "metrics": {
    "latency_us": 850,
    "active_requests": 0,
    "load_factor": 0.15
  }
}
```

### 17.7 Paso 6: Probar Peticion MCP (Leer Archivo)

Enviar peticion JSON-RPC via NATS:

```bash
# Instalar nats CLI si no esta disponible
brew install nats-io/nats-tools/nats

# Usando nats CLI (request-reply)
# NOTA: En macOS usar /private/tmp en lugar de /tmp (symlink)
nats request fararoni.skills.mcp \
  '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"read_file","arguments":{"path":"/private/tmp/fararoni-sandbox/demo.txt"}}}' \
  --timeout 10s
```

**Respuesta real (prueba 2026-02-25):**
```
20:46:33 Sending request on "fararoni.skills.mcp"
20:46:33 Received with rtt 4.157125ms
```
```json
{
  "result": {
    "content": [{"type": "text", "text": "Soberania de datos Fararoni activa\n"}],
    "structuredContent": {"content": "Soberania de datos Fararoni activa\n"}
  },
  "jsonrpc": "2.0",
  "id": 1
}
```

**RTT:** 4.15ms (via STDIO-TUNNEL, sin HTTP)

### 17.8 Paso 7: Probar Peticion MCP (Escribir Archivo)

```bash
nats request fararoni.skills.mcp \
  '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"write_file","arguments":{"path":"/private/tmp/fararoni-sandbox/test-sati.txt","content":"Archivo creado via STDIO-TUNNEL\nFASE 80.1.15\nFecha: 2026-02-25\nProtocolo S.A.T.I. validado"}}}' \
  --timeout 10s
```

**Respuesta real (prueba 2026-02-25):**
```
20:46:58 Sending request on "fararoni.skills.mcp"
20:46:58 Received with rtt 2.6735ms
```
```json
{
  "result": {
    "content": [{"type": "text", "text": "Successfully wrote to /private/tmp/fararoni-sandbox/test-sati.txt"}]
  },
  "jsonrpc": "2.0",
  "id": 2
}
```

**RTT:** 2.67ms (escritura via STDIO-TUNNEL)

**Verificar archivo creado:**
```bash
cat /tmp/fararoni-sandbox/test-sati.txt
```

**Output real:**
```
Archivo creado via STDIO-TUNNEL
FASE 80.1.15
Fecha: 2026-02-25
Protocolo S.A.T.I. validado
```

### 17.9 Nota sobre Paths en macOS

En macOS, `/tmp` es un symlink a `/private/tmp`. El servidor MCP valida
paths absolutos, por lo que debes usar `/private/tmp/...` en las peticiones.

---

## 18. Arquitectura Java (Capataz) + Node.js (Obrero)

### 18.1 Por que usamos Node.js en el MCP Server?

El MCP Server (`@modelcontextprotocol/server-filesystem`) es Node.js porque:

**1. El MCP Server es el "Especialista"**

Anthropic y la comunidad global lanzaron el estandar MCP principalmente en Node.js y Python.

- **Ventaja:** En lugar de perder meses programando conectores para Google Drive,
  Postgres o Filesystem en Java, "secuestramos" el servidor oficial que ya existe.
- **El Lenguaje no importa:** Como S.A.T.I. usa STDIO, al Sidecar Java no le
  importa si el PID es Node.js, C++ o COBOL. Solo importa JSON entrada/salida.

**2. Seguridad de "Privilegio Minimo" (Grado Militar)**

Esta es la parte mas critica:

- El **Sidecar Java** NO tiene permiso para escribir en `/tmp/fararoni-sandbox/`
- Solo el **Proceso Hijo (Node.js)** tiene ese permiso
- **Resultado:** Si un hacker compromete el Bus NATS, lo maximo que puede hacer
  es hablar con el Sidecar. No puede navegar el disco porque el Sidecar
  **no sabe escribir archivos**, solo pasa la nota al especialista (Node.js).

**3. Java como el "Capataz" (The Overseer)**

Usamos Node.js para el trabajo sucio (escribir archivos) porque es ligero y ya
esta hecho. Pero usamos **Java 25** para el Sidecar porque:

| Rol | Responsabilidad |
|-----|-----------------|
| Java (Capataz) | Monitorea si Node.js se vuelve loco (Watchdog) |
| Java (Capataz) | Mide microsegundos de latencia |
| Java (Capataz) | Mata a Node.js si recibes `panic` |
| Node.js (Obrero) | Tiene las herramientas (Filesystem) |

### 18.2 Diagrama de Roles

```
                    KERNEL FARARONI (Java 25)
                           |
                    Bus NATS (4222)
                           |
              +------------+------------+
              |                         |
    +---------v---------+     +---------v---------+
    |   SIDECAR JAVA    |     |   SIDECAR JAVA    |
    |    (Capataz)      |     |    (Capataz)      |
    |  - Watchdog       |     |  - Watchdog       |
    |  - Metricas       |     |  - Metricas       |
    |  - Panic Button   |     |  - Panic Button   |
    +--------+----------+     +--------+----------+
             |                         |
        STDIO Pipe                STDIO Pipe
             |                         |
    +--------v----------+     +--------v----------+
    |   MCP SERVER      |     |   MCP SERVER      |
    |    (Obrero)       |     |    (Obrero)       |
    |  - Node.js        |     |  - Node.js        |
    |  - Filesystem     |     |  - Filesystem     |
    +-------------------+     +-------------------+
```

### 18.3 La Gran Leccion de S.A.T.I.

**Java es el Gerente y Node.js es el Obrero.**

- El obrero (Node.js) tiene las herramientas (Filesystem)
- El gerente (Java) tiene el control, la radio (NATS) y el cronometro (Metricas)

**Potencia demostrada:** Puedes integrar cualquier tecnologia del mundo en tu
enjambre sin que el Kernel pierda su pureza Java.

---

## 19. Prueba de Watchdog: Sabotaje Controlado

### 19.1 Objetivo

Demostrar que el Protocolo S.A.T.I. es inmune al colapso de procesos externos.
El Sidecar Java (Capataz) debe detectar y resucitar al MCP Server (Obrero)
automaticamente.

### 19.2 El Plan de Sabotaje

1. **El Ataque:** Congelar el proceso MCP (Node.js) simulando un bloqueo
2. **La Deteccion:** El Watchdog detecta timeout de 500ms en el ping
3. **La Resurreccion:** Java ejecuta `destroyForcibly()` y levanta nuevo PID

### 19.3 Paso 1: Identificar PID del Obrero

```bash
ps aux | grep mcp-server-filesystem | grep -v grep
# Anotar el PID (ej: 4095)
```

### 19.4 Paso 2: Congelar el Proceso (Sabotaje)

```bash
# Enviar SIGSTOP para congelar el proceso (simula bloqueo)
kill -STOP <PID_MCP_SERVER>

# Ejemplo:
kill -STOP 4095
```

### 19.5 Paso 3: Observar la Consola del Sidecar

En la terminal del Sidecar veras algo como:

```
[WATCHDOG] Proceso no responde (500ms). Hard Reset...
[MCP-STDIO] Matando proceso existente PID=4095
[MCP-STDIO] Iniciando servidor MCP...
[MCP-STDIO] Proceso iniciado PID=5120, Reinicios=2
[SATI] [OK] Heartbeat: mode=STDIO-TUNNEL, latency=1150us, pid=5120, restarts=2
```

### 19.6 Paso 4: Verificar Resurreccion

```bash
# Verificar nuevo PID
ps aux | grep mcp-server-filesystem | grep -v grep
# El PID debe ser DIFERENTE al original (ej: 5120 en vez de 4095)

# Probar que el nuevo proceso funciona
nats request fararoni.skills.mcp \
  '{"jsonrpc":"2.0","id":99,"method":"tools/call","params":{"name":"read_file","arguments":{"path":"/private/tmp/fararoni-sandbox/demo.txt"}}}' \
  --timeout 10s
```

### 19.7 Por que esto es Revolucionario?

**Arquitectura Normal (sin S.A.T.I.):**
- El Kernel recibe error "Connection Timeout"
- El desarrollador tiene que entrar al servidor a reiniciar
- Sistema fuera de servicio hasta que alguien se da cuenta

**Fararoni S.A.T.I.:**
- Sistema **Autoreparable (Self-Healing)**
- La inteligencia de supervivencia esta en el Sidecar, no en el programador
- **El sistema se arregla solo mientras duermes**

### 19.8 Resultados Reales (Prueba 2026-02-25)

**Prueba ejecutada exitosamente:**

| Metrica | Antes del Sabotaje | Durante | Despues |
|---------|-------------------|---------|---------|
| PID MCP | 4095 | 4095 (congelado) | 9431 (nuevo) |
| Status | READY | TIMEOUT | READY |
| Reinicios | 1 | - | 2+ |
| Conexion NATS | Activa | Activa | Activa |

**Secuencia de eventos:**

```
1. PID Original: 4095 (MCP Server corriendo)
2. Sabotaje:     kill -STOP 4095 (proceso congelado)
3. Deteccion:    Watchdog detecta timeout 500ms
4. Ejecucion:    destroyForcibly() sobre PID 4095
5. Resurreccion: startOrRestart() lanza nuevo proceso
6. PID Nuevo:    9431 (MCP Server resucitado)
7. Verificacion: nats request -> RTT 3.51ms -> [OK]
```

**Evidencia de funcionamiento post-resurreccion:**

```bash
$ nats request fararoni.skills.mcp '{"method":"read_file"...}'
20:58:02 Received with rtt 3.516375ms
{"result":{"content":[{"text":"Soberania de datos Fararoni activa\n"}]}}
```

**Conclusion:** El sistema es **AUTOREPARABLE**. El Watchdog de Grado Militar
detecto el proceso congelado, lo elimino y resucito uno nuevo sin intervencion
humana. El Kernel nunca perdio conectividad con el Sidecar.

```bash
# Verificar symlink
ls -la /tmp
# lrwxr-xr-x  1 root  wheel  11 ... /tmp -> private/tmp
```

### 17.9 Paso 8: Probar Panic Button (Opcional)

Desde otra terminal, publicar orden de panico:

```bash
nats pub "fararoni.sati.control.panic" "HARD_REBOOT"
```

**En la terminal del Sidecar veras:**
```
[PANIC] Orden de HARD_REBOOT recibida del Kernel. Reiniciando tunel STDIO...
[MCP-STDIO] Matando proceso existente PID=12345
[MCP-STDIO] Iniciando servidor MCP...
[MCP-STDIO] Proceso iniciado PID=12346, Reinicios=2
[PANIC] Tunel restablecido tras orden de panico.
```

### 17.10 Paso 9: Detener la Prueba

```bash
# Encontrar PID del Sidecar
pgrep -f fararoni-sidecar

# Detener (el proceso MCP hijo muere automaticamente)
kill <PID_SIDECAR>

# Verificar que ambos murieron
ps aux | grep -E "fararoni-sidecar|mcp-server" | grep -v grep
# Debe estar vacio
```

---

### 17.11 Resultados de Prueba (2026-02-25)

**Estado:** EXITOSA

| Verificacion | Resultado |
|--------------|-----------|
| Sidecar lanza MCP como proceso hijo | [OK] PID: 4064 |
| MCP Server corriendo | [OK] PID: 4095 |
| MCP no abre puertos HTTP | [OK] Invisible para red |
| Comunicacion via pipes STDIO | [OK] |
| Ambos procesos estables | [OK] CPU < 1% |
| Sandbox accesible | [OK] /tmp/fararoni-sandbox/ |
| **Leer archivo via MCP** | [OK] RTT: 4.15ms |
| **Escribir archivo via MCP** | [OK] RTT: 2.67ms |

**Evidencia de Procesos:**
```
=== Estado STDIO-TUNNEL ===

Sidecar Java:       PID: 4064 | Corriendo
MCP Server (hijo):  PID: 4095 | Corriendo

Puertos abiertos:   NINGUNO (modo STDIO-TUNNEL)
Sandbox:            /tmp/fararoni-sandbox/
```

**Evidencia de Operaciones MCP:**
```
Paso 17.7 - Leer demo.txt:
  RTT: 4.157125ms
  Contenido: "Soberania de datos Fararoni activa"

Paso 17.8 - Escribir test-sati.txt:
  RTT: 2.6735ms
  Resultado: "Successfully wrote to /private/tmp/fararoni-sandbox/test-sati.txt"

Archivos en sandbox:
  demo.txt:      35 bytes (original)
  test-sati.txt: 90 bytes (creado via MCP STDIO-TUNNEL)
```

**Flujo Validado:**
```
nats request -> Sidecar Java (4064) -> STDIO pipe -> MCP Server (4095) -> Filesystem
     |                                                                        |
     +<-------------------------- Respuesta JSON-RPC -------------------------+
```

### 17.12 Componentes Verificados

| Componente | Estado |
|------------|--------|
| `McpBridgeSidecar.java` | STDIO-TUNNEL implementado |
| `McpSidecarMain.java` | Panic listener activo |
| `SatiController.java` | CREADO y compilado |
| `SATIRouter.java` | Selector inteligente |
| `SatiHealthMonitor.java` | Dashboard operativo |
| `SidecarNode.java` | Record del enjambre |

---

## Apendice A: Crear nuevo Sidecar con SATI

Cualquier Sidecar que use el SDK de Fararoni hereda la telemetria SATI automaticamente.

```java
public class MySidecar {
    public static void main(String[] args) throws Exception {
        // 1. Crear bridge en modo STDIO-TUNNEL
        McpBridgeSidecar bridge = new McpBridgeSidecar(
            "mi-comando", "--arg1", "--arg2"
        );

        // 2. Iniciar proceso y watchdog
        bridge.startOrRestart();
        bridge.startWatchdog();

        // 3. El SDK se encarga del resto:
        // - Conexion NATS
        // - Heartbeat SATI cada 5s
        // - Virtual Threads
        // - Queue Groups
        // - Listener de Panic Button
    }
}
```

**Tu ecosistema se vuelve autorreparable por defecto.**

---

## Apendice B: Especificacion del Protocolo

Ver documento completo: `PROTOCOLO-SATI-ESPECIFICACION.md`

---

*Documento generado para FASE 80.1.14 - 80.1.15*
*Fararoni Framework - Soberania Tecnologica*
