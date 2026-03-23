# Fararoni Ecosystem & CLI

🌍 *Read this in other languages: [English](README.md) | [Español](README.es.md)*

---

Multi-agent AI system that orchestrates local and cloud LLMs through an agentic DEFCON architecture. Zero-dependency distribution with GraalVM native binaries and Node.js SEA sidecars.

## Quick Install

```bash
curl -fsSL https://fararoni.dev/install.sh | bash
```

Windows:
```powershell
iwr -useb https://fararoni.dev/install.ps1 | iex
```

## Architecture

![Omnichannel Architecture](docs/OMNICHANNEL-ARCHITECTURE-WHITEPAPER-v1.svg)

```
fararoni-ecosystem/
├── fararoni-agent-api/          API publica de agentes
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

## DEFCON Levels

| Level | Agents | Use Case |
|-------|--------|----------|
| 5 | COMMANDER > BLUEPRINT > BUILDER > OPERATOR | Simple tasks |
| 3 | +INTEL, +SENTINEL | Development |
| 1 | +STRATEGIST (VETO power) | Enterprise |

## Build from Source

Prerequisites: Java 25+, Maven 3.9+

```bash
# Core (JAR)
mvn clean package -DskipTests -Dmaven.javadoc.skip=true

# Core (GraalVM native binary)
mvn -Pnative package -pl fararoni-core -DskipTests

# Sidecars (Node.js SEA binaries)
cd fararoni-distribution && ./build-sidecars.sh

# Full bundle (ZIP + TAR.GZ)
mvn package -DskipTests -pl fararoni-distribution

# macOS DMG
mvn install -Pdmg -pl fararoni-distribution -DskipTests
```

## Ports

| Port | Service |
|------|---------|
| 7070 | Core REST Server |
| 7071 | Gateway REST |
| 3000 | Sidecar WhatsApp |
| 3001 | Sidecar Telegram |
| 3002 | Sidecar Discord |
| 3003 | Sidecar iMessage |

## License

Licensed under the [Apache License 2.0](LICENSE).

See [CONTRIBUTING.md](CONTRIBUTING.md) for the Contributor License Agreement.

## Author

**Eber Cruz Fararoni** — Project Founder & Lead Architect

See [CONTRIBUTORS.md](CONTRIBUTORS.md) for the full contributor roster.

---

## The Fararoni Vision: A Sovereign Journey

Fararoni is more than a high-performance orchestrator; it is a collaborative environment designed for those who value **technical independence and architectural excellence**.

We believe that true innovation happens when expertise is respected. Here, the **Sidecar Architecture** ensures that every contributor maintains sovereignty over their own modules. Whether you are optimizing a core routine in **Java**, crafting a sophisticated interface in **Angular**, or integrating physical hardware, you have a place here.

> *"Engineering is not just about code; it's about the legacy we build together."*

## Governance & Sovereignty

To maintain our standard of excellence and protect the collective effort, Fararoni operates under a professional framework that ensures order and long-term sovereignty for the project and its contributors.

* **Sidecar Architects:** You own your specialized modules. We provide the foundation; you provide the vision.
* **Domain Experts:** If you have a vision involving sensors or industrial devices but lack the software infrastructure, we are open to exploring how Fararoni can bridge that gap.

---

### How to Join

If you have the willingness and the time to transcend the conventional, explore our **[CONTRIBUTING.md](CONTRIBUTING.md)**. We don't just seek code; we seek the spirit of collaboration that makes technology truly human.

**Maintained by [Eber Cruz Fararoni](https://fararoni.dev)**
