# C-FARARONI — Sovereign AI Agent Orchestrator

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

**Eber Cruz**
