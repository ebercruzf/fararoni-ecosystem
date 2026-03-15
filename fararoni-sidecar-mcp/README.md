

#  Fararoni S.A.T.I. (Sovereign Agnostic Transport Interface)


---

##  El Manifiesto de la Resiliencia

El mundo del software está atrapado en una **fragilidad sistémica**. Dependemos de microservicios pasivos que colapsan ante un hilo bloqueado, una latencia inesperada o un proceso zombie. Las arquitecturas actuales son reactivas; nosotros somos **soberanos**.

**S.A.T.I.** es el protocolo de orquestación del ecosistema **C-FARARONI** diseñado para blindar la comunicación entre el Kernel (Cerebro) y los Agentes (Músculo). No es solo un puente; es un **Supervisor Activo** que garantiza que el sistema siempre regrese a su estado de oro, sin importar cuán corrupto esté el proceso externo.

---

##  ¿Por qué S.A.T.I.? (La Magia Técnica)

S.A.T.I. resuelve los tres problemas en la orquestación moderna:

1. **Aislamiento de Grado Militar:** El **Sidecar (S.A.T.I.-D)** actúa como un capataz que envuelve procesos en Node.js, Python o C++. Si el "Obrero" falla, el "Gerente" (Java 25) lo detecta y lo resucita.
2. **Inmunidad al Deadlock:** Implementamos una arquitectura "Sentinel" que abandona el bloqueo de hilos tradicional en favor de **Hilos Virtuales de Java 25**. Un proceso externo congelado (`SIGSTOP`) nunca podrá secuestrar tu sistema.
3. **Selección por Mérito:** Olvida el balanceo de carga ciego. El **Capability Router** entrega la "chamba" al agente con mejor salud y latencia verificada en tiempo real.

---

##  Filosofía Open Source (Licencia Apache 2.0)

Hemos decidido liberar el Protocolo S.A.T.I. bajo la licencia **Apache 2.0**.

**¿Por qué?**

* **Para el CTO:** Seguridad legal y libertad de integración comercial.
* **Para el Arquitecto:** Transparencia total. No hay cajas negras; el código es auditable y resiliente por diseño.
* **Para la Comunidad:** Construir un ecosistema donde la inteligencia (AI Agents) sea gobernable y no una caja de Pandora.

---

##  Cómo Iniciar el Enjambre

### 1. Requisitos

* **Java 25** (Requerido para la gestión de Hilos Virtuales y concurrencia moderna).
* **Sovereign Event Bus** (NATS o implementación local).

### 2. Despliegue de un Nodo S.A.T.I.

Cualquier nodo debe respetar el contrato de salud:

```json
{
  "id": "mcp-agent-01",
  "priority": 110,
  "status": "READY",
  "metrics": { "latency_us": 1200 }
}

```

*[Ver especificación técnica completa en docs/SATI-SPEC.md]*

---

### **Autor**

**Eber Cruz**

*Arquitecto de Sistemas | Creador del Ecosistema Fararoni*

---
