# Fararoni Suite — Guia de Instalacion

## Descargas

Todos los instaladores se generan automaticamente via GitHub Actions para las 3 plataformas.
Descarga la version mas reciente desde:

https://github.com/ebercruzf/fararoni-ecosystem/releases

| Plataforma | Archivo |
|------------|---------|
| macOS (arm64) | `Fararoni-Installer-macos-arm64.dmg` |
| Linux (x64) | `fararoni-v1.0.0-linux-x64.tar.gz` |
| Windows (x64) | `fararoni-v1.0.0-windows-x64.zip` |

### Homebrew (macOS / Linux)

```bash
brew tap ebercruzf/fararoni
brew install fararoni
```

---

## macOS (DMG)

Al abrir el DMG se muestran 3 elementos:

| Elemento | Descripcion |
|----------|-------------|
| **Fararoni Suite/** | Carpeta con el bundle completo (core + 4 sidecars + config) |
| **Instalar Fararoni.command** | Doble-clic para ejecutar el wizard de instalacion |
| **Applications** | Symlink — arrastra "Fararoni Suite" aqui para instalar en /Applications |

### Pasos

1. Abrir `Fararoni-Installer-macos-arm64.dmg`
2. Opcion A: Doble-clic en **"Instalar Fararoni.command"** (wizard interactivo)
3. Opcion B: Arrastrar **"Fararoni Suite"** a **Applications** (instalacion manual)
4. Configurar credenciales en `~/.fararoni/config/`

### Contenido de Fararoni Suite/

```
bin/
  fararoni-core          # Core Java (GraalVM native o JAR wrapper)
  fararoni.sh            # Script de arranque
  sidecar-telegram       # Sidecar Telegram (SEA binary, 122MB)
  sidecar-whatsapp       # Sidecar WhatsApp (SEA binary, 124MB)
  sidecar-discord        # Sidecar Discord (SEA binary, 123MB)
  sidecar-imessage       # Sidecar iMessage (SEA binary, 121MB)
lib/
  fararoni-gateway-rest-1.0.0.jar
  fararoni-enterprise-transport-1.0.0.jar
fararoni-launcher.sh     # Orquestador: inicia core + sidecars
installer.sh             # Wizard de instalacion (macOS/Linux)
ecosystem.config.js      # Config PM2 (legacy, backward compat)
global.env               # Variables de entorno globales
Fararoni.icns            # Icono de la aplicacion
LICENSE                  # Apache 2.0
```

---

## Windows

### Pasos

1. Descargar `fararoni-v1.0.0-windows-x64.zip` desde [Releases](https://github.com/ebercruzf/fararoni-ecosystem/releases)
2. Descomprimir el ZIP
3. Doble-clic en **"Instalar Fararoni.bat"** (wizard PowerShell)
4. O ejecutar manualmente: `powershell -File installer.ps1`
5. Configurar credenciales en `%USERPROFILE%\.fararoni\config\`

---

## Linux

### Pasos

1. Descargar `fararoni-v1.0.0-linux-x64.tar.gz` desde [Releases](https://github.com/ebercruzf/fararoni-ecosystem/releases)
2. `tar xzf fararoni-v1.0.0-linux-x64.tar.gz`
3. `cd fararoni-v1.0.0 && chmod +x installer.sh && ./installer.sh`
4. Configurar credenciales en `~/.fararoni/config/`
5. Los sidecars se registran como servicios systemd

---

## Build desde fuente

Los binarios de cada plataforma se compilan automaticamente via GitHub Actions
al crear un tag `v*`. El workflow (`.github/workflows/release.yml`) ejecuta:

1. **Build sidecars SEA** — Cada runner (macOS/Linux/Windows) compila los 4 sidecars como binarios nativos usando Node.js SEA (esbuild + postject). Los binarios son platform-specific porque embeben el Node.js del OS donde se compilan.
2. **Maven package** — Compila core + gateway + enterprise-transport y empaqueta todo en ZIP/TAR.GZ via maven-assembly-plugin.
3. **Create DMG** (solo macOS) — Genera el instalador DMG con icono y layout personalizado.
4. **Publish Release** — Sube todos los artefactos como GitHub Release.

### Build local (macOS)

```bash
cd fararoni-distribution
./build-sidecars.sh                    # Compila 4 sidecars SEA
cd .. && mvn package -DskipTests       # Maven package
cd fararoni-distribution && ./create-dmg.sh   # Genera DMG
```
