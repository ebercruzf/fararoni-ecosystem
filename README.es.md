# Ecosistema Fararoni y CLI

🌍 *Leer en otros idiomas: [English](README.md) | [Español](README.es.md)*

---

Sistema multi-agente de IA que orquesta LLMs locales y en la nube a través de una arquitectura agéntica DEFCON. Distribución sin dependencias con binarios nativos GraalVM y sidecars Node.js SEA.

## Instalación Rápida

```bash
curl -fsSL https://fararoni.dev/install.sh | bash
```

Windows:
```powershell
iwr -useb https://fararoni.dev/install.ps1 | iex
```

## Arquitectura

![Arquitectura Omnicanal](docs/OMNICHANNEL-ARCHITECTURE-WHITEPAPER-v1.svg)

```
fararoni-ecosystem/
├── fararoni-agent-api/          API pública de agentes
├── fararoni-core/               Motor principal — CLI + Server
├── fararoni-gateway-rest/       Gateway HTTP Ingress/Egress
├── fararoni-enterprise-transport/  NATS JetStream transport
├── fararoni-distribution/       Bundle, instaladores, DMG builder
├── fararoni-intellij-client/    Plugin IntelliJ (Sentinel AI)
├── fararoni-sidecar-telegram/   Sidecar Telegram (Node.js SEA)
├── fararoni-sidecar-whatsapp/   Sidecar WhatsApp — Baileys (Node.js SEA)
├── fararoni-sidecar-discord/    Sidecar Discord (Node.js SEA)
├── fararoni-sidecar-imessage/   Sidecar iMessage — BlueBubbles (Node.js SEA)
├── fararoni-sidecar-mcp/        Sidecar MCP (Model Context Protocol)
└── pom.xml                      Maven reactor (parent POM)
```

## Niveles DEFCON

| Nivel | Agentes | Caso de Uso |
|-------|---------|-------------|
| 5 | COMMANDER > BLUEPRINT > BUILDER > OPERATOR | Tareas simples |
| 3 | +INTEL, +SENTINEL | Desarrollo |
| 1 | +STRATEGIST (poder de VETO) | Empresarial |

## Compilar desde Código Fuente

Requisitos: Java 25+, Maven 3.9+

```bash
# Core (JAR)
mvn clean package -DskipTests -Dmaven.javadoc.skip=true

# Core (binario nativo GraalVM)
mvn -Pnative package -pl fararoni-core -DskipTests

# Sidecars (binarios Node.js SEA)
cd fararoni-distribution && ./build-sidecars.sh

# Bundle completo (ZIP + TAR.GZ)
mvn package -DskipTests -pl fararoni-distribution

# macOS DMG
mvn install -Pdmg -pl fararoni-distribution -DskipTests
```

## Puertos

| Puerto | Servicio |
|--------|----------|
| 7070 | Core REST Server |
| 7071 | Gateway REST |
| 3000 | Sidecar WhatsApp |
| 3001 | Sidecar Telegram |
| 3002 | Sidecar Discord |
| 3003 | Sidecar iMessage |

## Licencia

Licenciado bajo la [Apache License 2.0](LICENSE).

Consulta [CONTRIBUTING.md](CONTRIBUTING.md) para el Acuerdo de Licencia de Contribuidor.

## Autor

**Eber Cruz Fararoni** — Project Founder & Lead Architect

Consulta [CONTRIBUTORS.md](CONTRIBUTORS.md) para el roster completo de contribuidores.

---

## La Visión Fararoni: Un Camino Soberano

Fararoni es más que un orquestador de alto rendimiento; es un entorno colaborativo diseñado para quienes valoran la **independencia técnica y la excelencia arquitectónica**.

Creemos que la verdadera innovación ocurre cuando se respeta la maestría. Aquí, la **Arquitectura Sidecar** garantiza que cada colaborador mantenga la soberanía sobre sus propios módulos. Ya sea que estés optimizando una rutina del núcleo en **Java**, diseñando una interfaz sofisticada en **Angular** o integrando hardware físico, tienes un lugar aquí.

> *"La ingeniería no se trata solo de código; se trata del legado que construimos juntos".*

## Gobernanza y Soberanía

Para mantener nuestro estándar de excelencia y proteger el esfuerzo colectivo, Fararoni opera bajo un marco profesional que garantiza el orden y la soberanía a largo plazo tanto para el proyecto como para sus colaboradores.

* **Arquitectos de Sidecar:** Eres dueño de tus módulos especializados. Nosotros ponemos la base; tú pones la visión.
* **Expertos de Dominio:** Si tienes una visión que involucre sensores o dispositivos industriales, pero careces de la infraestructura de software, estamos abiertos a explorar cómo Fararoni puede cerrar esa brecha.

---

### Cómo unirte

Si tienes la disposición y el tiempo para trascender lo convencional, explora nuestro **[CONTRIBUTING.md](CONTRIBUTING.md)**. No solo buscamos código; buscamos el espíritu de colaboración que hace que la tecnología sea verdaderamente humana.

**Mantenido por [Eber Cruz Fararoni](https://fararoni.dev)**
