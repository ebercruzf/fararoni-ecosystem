# Fararoni Suite — Guia de Instalacion

## macOS (DMG)

**Archivo**: `fararoni-distribution/target/Fararoni-Installer.dmg`

Al abrir el DMG se muestran 3 elementos:

| Elemento | Descripcion |
|----------|-------------|
| **Fararoni Suite/** | Carpeta con el bundle completo (core + 4 sidecars + config) |
| **Instalar Fararoni.command** | Doble-clic para ejecutar el wizard de instalacion |
| **Applications** | Symlink — arrastra "Fararoni Suite" aqui para instalar en /Applications |

### Pasos

1. Abrir `Fararoni-Installer.dmg`
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

**Archivo**: `fararoni-distribution/target/fararoni-v1.0.0.zip`

### Pasos

1. Descomprimir `fararoni-v1.0.0.zip`
2. Doble-clic en **"Instalar Fararoni.bat"** (wizard PowerShell)
3. O ejecutar manualmente: `powershell -File installer.ps1`
4. Configurar credenciales en `%USERPROFILE%\.fararoni\config\`

> **Nota**: Los sidecars SEA actuales son binarios arm64 macOS. Para Windows se necesita
> recompilar con `build-sidecars.sh` en una maquina Windows (o CI con Node.js para Windows).

---

## Linux

**Archivo**: `fararoni-distribution/target/fararoni-v1.0.0.tar.gz`

### Pasos

1. `tar xzf fararoni-v1.0.0.tar.gz`
2. `cd fararoni-v1.0.0 && chmod +x installer.sh && ./installer.sh`
3. Configurar credenciales en `~/.fararoni/config/`
4. Los sidecars se registran como servicios systemd

> **Nota**: Los sidecars SEA actuales son binarios arm64 macOS. Para Linux se necesita
> recompilar con `build-sidecars.sh` en una maquina Linux (o CI con Node.js para Linux).

---

## Generacion Cross-Platform (CI/CD)

Para generar instaladores nativos en las 3 plataformas, se necesita un pipeline CI:

```
GitHub Actions Matrix:
  - macos-latest (arm64)  → DMG + ZIP
  - ubuntu-latest (x64)   → TAR.GZ + .deb (opcional)
  - windows-latest (x64)  → ZIP + MSI (opcional)
```

Cada plataforma ejecuta:
1. `./build-sidecars.sh` → genera binarios SEA nativos para ese OS
2. `mvn package` → empaqueta en ZIP/TAR.GZ
3. macOS: `./create-dmg.sh` → genera DMG
4. Linux: `dpkg-deb` o `rpmbuild` (opcional)
5. Windows: WiX Toolset o Inno Setup (opcional)

Los sidecars SEA son **platform-specific** porque embeben el binario de Node.js del OS donde se compilan.
