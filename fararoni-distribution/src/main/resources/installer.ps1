# =============================================================================
#
# Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# =============================================================================
#
#  FARARONI SUITE v1.0.0 - Instalador Profesional (Windows)
#
#  Este script se ejecuta DENTRO del bundle distribuible:
#    fararoni-v1.0.0\installer.ps1
#
#  Arquitectura Hibrida:
#    Core (GraalVM Native)  -> Servicio de Windows (SCM)
#    Sidecars (Node.js)     -> PM2 Process Manager
#
#  Uso:
#    .\installer.ps1                  # Wizard interactivo
#    .\installer.ps1 -Silent          # Sin prompts (instala todo)
#    .\installer.ps1 -Uninstall       # Desinstalar servicios
#    .\installer.ps1 -Help            # Ayuda
#
# =============================================================================

param(
    [switch]$Silent,
    [switch]$Uninstall,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

# =============================================================================
# CONFIGURACION
# =============================================================================
$Version     = "1.0.0"
$AppName     = "fararoni"
$ServiceName = "FararoniCore"
$InstallDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$UserName    = [Environment]::UserName

# Componentes
$Binary = Join-Path $InstallDir "bin\fararoni-core.exe"

$Sidecars = @(
    @{ Name = "Telegram";  Dir = "fararoni-sidecar-telegram";  Port = 3001; EnvKey = "TELEGRAM_TOKEN";      Doc = "https://core.telegram.org/bots#how-do-i-create-a-bot"; Desc = "Token de BotFather para Telegram" },
    @{ Name = "WhatsApp";  Dir = "fararoni-sidecar-whatsapp";  Port = 3000; EnvKey = "";                    Doc = "Escanea QR al iniciar (Baileys)";                      Desc = "Sesion QR (automatica al iniciar)" },
    @{ Name = "Discord";   Dir = "fararoni-sidecar-discord";   Port = 3002; EnvKey = "DISCORD_TOKEN";       Doc = "https://discord.com/developers/applications";          Desc = "Token del Bot en Developer Portal" },
    @{ Name = "iMessage";  Dir = "fararoni-sidecar-imessage";  Port = 3003; EnvKey = "BLUEBUBBLES_PASSWORD"; Doc = "Requiere BlueBubbles Server";                         Desc = "Password del servidor BlueBubbles" }
)

$EnabledServices = @()

# =============================================================================
# UTILIDADES
# =============================================================================
function Write-Info    { param($Msg) Write-Host "[INFO] $Msg" -ForegroundColor Blue }
function Write-Ok      { param($Msg) Write-Host "[OK] $Msg" -ForegroundColor Green }
function Write-Warn    { param($Msg) Write-Host "[WARN] $Msg" -ForegroundColor Yellow }
function Write-Err     { param($Msg) Write-Host "[ERROR] $Msg" -ForegroundColor Red }
function Write-Step    { param($N, $Total, $Msg) Write-Host "`n[$N/$Total] $Msg" -ForegroundColor Cyan }

function Show-Banner {
    Write-Host ""
    Write-Host "  ==================================================================" -ForegroundColor Cyan
    Write-Host ""                                                                     -ForegroundColor Cyan
    Write-Host "   FARARONI   FARARONI   FARARONI   FARARONI   FARARONI   FARARONI"   -ForegroundColor Cyan
    Write-Host ""                                                                     -ForegroundColor Cyan
    Write-Host "          Suite Installer v$Version - GraalVM Native + PM2"           -ForegroundColor Cyan
    Write-Host "                        Windows Edition"                               -ForegroundColor Cyan
    Write-Host ""                                                                     -ForegroundColor Cyan
    Write-Host "  ==================================================================" -ForegroundColor Cyan
    Write-Host ""
}

function Show-Help {
    Write-Host "FARARONI Suite Installer v$Version (Windows)" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Uso: .\installer.ps1 [OPCIONES]"
    Write-Host ""
    Write-Host "  -Silent       Instalacion sin prompts (todo por defecto)"
    Write-Host "  -Uninstall    Desinstalar servicios del sistema"
    Write-Host "  -Help         Mostrar esta ayuda"
    Write-Host ""
    Write-Host "Estructura del bundle:"
    Write-Host "  bin\fararoni-core.exe          Binario nativo GraalVM (x64/arm64)"
    Write-Host "  lib\                           JARs Enterprise (gateway, transport)"
    Write-Host "  modules\fararoni-sidecar-*\    Sidecars Node.js"
    Write-Host "  config\global.env              Configuracion centralizada"
    Write-Host "  ecosystem.config.js            Orquestador PM2"
    Write-Host ""
}

function Test-Admin {
    $identity  = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

# =============================================================================
# AYUDA
# =============================================================================
if ($Help) { Show-Help; exit 0 }

# =============================================================================
# DESINSTALAR
# =============================================================================
if ($Uninstall) {
    Show-Banner
    Write-Info "Desinstalando servicios de Fararoni..."

    # PM2
    if (Get-Command pm2 -ErrorAction SilentlyContinue) {
        pm2 stop ecosystem.config.js 2>$null
        pm2 delete ecosystem.config.js 2>$null
        pm2 save 2>$null
        Write-Ok "Sidecars PM2 detenidos"
    }

    # Servicio de Windows
    $svc = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if ($svc) {
        if (-not (Test-Admin)) {
            Write-Err "Se requiere ejecutar como Administrador para desinstalar el servicio."
            Write-Info "Clic derecho en PowerShell -> 'Ejecutar como administrador'"
            exit 1
        }
        Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
        sc.exe delete $ServiceName | Out-Null
        Write-Ok "Servicio Windows '$ServiceName' removido"
    }

    # PATH
    $binDir = Join-Path $InstallDir "bin"
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    if ($userPath -like "*$binDir*") {
        $newPath = ($userPath -split ";" | Where-Object { $_ -ne $binDir }) -join ";"
        [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
        Write-Ok "Removido de PATH: $binDir"
    }

    Write-Host ""
    Write-Ok "Desinstalacion completada."
    exit 0
}

# =============================================================================
# PASO 1: VERIFICAR REQUISITOS
# =============================================================================
Show-Banner
Write-Step 1 5 "Verificando requisitos..."

# OS
$arch = if ([Environment]::Is64BitOperatingSystem) { "x64" } else { "x86" }
Write-Ok "Sistema: Windows $([Environment]::OSVersion.Version) ($arch)"

# Binario nativo
if (-not (Test-Path $Binary)) {
    Write-Err "Binario nativo no encontrado: $Binary"
    Write-Info "Asegurate de tener el binario compilado para Windows ($arch)."
    Write-Host ""
    Write-Host "Presiona cualquier tecla para salir..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}
$binarySize = "{0:N1} MB" -f ((Get-Item $Binary).Length / 1MB)
Write-Ok "Core nativo: fararoni-core.exe ($binarySize)"

# Node.js
$hasNode = $false
if (Get-Command node -ErrorAction SilentlyContinue) {
    $hasNode = $true
    $nodeVersion = node --version
    Write-Ok "Node.js: $nodeVersion"
} else {
    Write-Warn "Node.js no detectado (necesario para sidecars)"
}

# Privilegios de administrador
$isAdmin = Test-Admin
if ($isAdmin) {
    Write-Ok "Privilegios: Administrador"
} else {
    Write-Warn "Sin privilegios de Administrador (el servicio no se registrara)"
}

# =============================================================================
# PASO 2: INSTALAR CORE COMO SERVICIO DE WINDOWS
# =============================================================================
Write-Step 2 5 "Registrando Core como Servicio de Windows..."

$logsDir = Join-Path $InstallDir "logs"
if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir | Out-Null }

if (-not $Silent) {
    Write-Host "  El Core se ejecutara como servicio de Windows (auto-start, auto-restart)." -ForegroundColor DarkGray
    $choice = Read-Host "  Registrar como servicio? (s/n)"
} else {
    $choice = "s"
}

if ($choice -eq "s" -or $choice -eq "S") {
    if (-not $isAdmin) {
        Write-Warn "Se requieren privilegios de Administrador para crear servicios."
        Write-Info "Clic derecho en 'Instalar Fararoni.bat' -> 'Ejecutar como administrador'"
        Write-Info "Puedes ejecutar manualmente: .\bin\fararoni-core.exe --server"
    } else {
        # Verificar si el servicio ya existe
        $existingSvc = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
        if ($existingSvc) {
            Write-Info "Servicio existente encontrado. Actualizando..."
            Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
            sc.exe delete $ServiceName | Out-Null
            Start-Sleep -Seconds 2
        }

        # Crear servicio nativo de Windows
        $svcParams = @{
            Name           = $ServiceName
            BinaryPathName = "`"$Binary`" --server"
            DisplayName    = "Fararoni Core Service v$Version"
            Description    = "Arquitectura C-FARARONI - Servicio Core (GraalVM Native)"
            StartupType    = "Automatic"
        }
        New-Service @svcParams | Out-Null

        Write-Ok "Servicio '$ServiceName' registrado (Inicio automatico)"
        Write-Info "Iniciar: Start-Service $ServiceName"
        Write-Info "Ver en: services.msc"
    }
} else {
    Write-Info "Servicio no registrado. Ejecuta manualmente: .\bin\fararoni-core.exe --server"
}

# =============================================================================
# PASO 3: AGREGAR AL PATH
# =============================================================================
Write-Step 3 5 "Configurando comando global 'fararoni'..."

$binDir = Join-Path $InstallDir "bin"
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")

if ($userPath -notlike "*$binDir*") {
    [Environment]::SetEnvironmentVariable("Path", "$userPath;$binDir", "User")
    $env:Path = "$env:Path;$binDir"
    Write-Ok "Agregado al PATH de usuario: $binDir"
    Write-Info "Reinicia la terminal para usar 'fararoni' directamente."
} else {
    Write-Ok "Ya esta en PATH: $binDir"
}

# =============================================================================
# PASO 4: CONFIGURAR SIDECARS (PM2)
# =============================================================================
Write-Step 4 5 "Configurando Sidecars (Node.js + PM2)..."

if (-not $hasNode) {
    Write-Warn "Node.js no disponible. Saltando sidecars."
    Write-Info "Instala Node.js desde https://nodejs.org y re-ejecuta: .\installer.ps1"
} else {
    # Instalar PM2 si no existe
    if (-not (Get-Command pm2 -ErrorAction SilentlyContinue)) {
        Write-Info "Instalando PM2..."
        npm install -g pm2 2>$null
    }

    Write-Host ""
    Write-Host "  Canales disponibles:" -ForegroundColor White

    foreach ($sidecar in $Sidecars) {
        $sidecarDir = Join-Path $InstallDir "modules\$($sidecar.Dir)"

        if (-not (Test-Path $sidecarDir)) {
            Write-Host "    $($sidecar.Name) (Puerto $($sidecar.Port)) - No disponible" -ForegroundColor DarkGray
            continue
        }

        if ($Silent) {
            $activate = "s"
        } else {
            $activate = Read-Host "    Activar $($sidecar.Name) (Puerto $($sidecar.Port))? (s/n)"
        }

        if ($activate -eq "s" -or $activate -eq "S") {
            # Instalar dependencias
            $nodeModules = Join-Path $sidecarDir "node_modules"
            if (-not (Test-Path $nodeModules)) {
                Write-Info "Instalando dependencias de $($sidecar.Name)..."
                Push-Location $sidecarDir
                npm install --production 2>$null
                Pop-Location
            }

            # Validar credenciales
            if ($sidecar.EnvKey -ne "") {
                $envFile = Join-Path $sidecarDir ".env"
                $currentVal = ""

                if (Test-Path $envFile) {
                    $match = Select-String -Path $envFile -Pattern "^$($sidecar.EnvKey)=" -ErrorAction SilentlyContinue
                    if ($match) {
                        $currentVal = $match.Line.Split("=", 2)[1]
                    }
                }

                if ([string]::IsNullOrWhiteSpace($currentVal) -or $currentVal -match "tu_|aqui") {
                    if (-not $Silent) {
                        Write-Host ""
                        Write-Host "    Credencial requerida: $($sidecar.Desc)" -ForegroundColor Yellow
                        Write-Host "    Documentacion: $($sidecar.Doc)" -ForegroundColor DarkGray
                        Write-Host "      1) Configurar ahora"
                        Write-Host "      2) Configurar despues (no arranca)"
                        Write-Host "      3) Arrancar sin credencial"

                        $opt = Read-Host "    Opcion [1-3]"
                        switch ($opt) {
                            "1" {
                                $newVal = Read-Host "    Ingresa $($sidecar.EnvKey)"
                                $envExample = Join-Path $sidecarDir ".env.example"
                                if (-not (Test-Path $envFile) -and (Test-Path $envExample)) {
                                    Copy-Item $envExample $envFile
                                } elseif (-not (Test-Path $envFile)) {
                                    New-Item -ItemType File -Path $envFile | Out-Null
                                }

                                $content = Get-Content $envFile -Raw -ErrorAction SilentlyContinue
                                if ($content -match "^$($sidecar.EnvKey)=") {
                                    $content = $content -replace "(?m)^$($sidecar.EnvKey)=.*", "$($sidecar.EnvKey)=$newVal"
                                    Set-Content -Path $envFile -Value $content -NoNewline
                                } else {
                                    Add-Content -Path $envFile -Value "$($sidecar.EnvKey)=$newVal"
                                }
                                Write-Ok "$($sidecar.EnvKey) configurado"
                            }
                            "2" {
                                Write-Info "$($sidecar.Name) saltado."
                                continue
                            }
                            "3" {
                                Write-Warn "$($sidecar.Name) arrancara sin credencial."
                            }
                            default { continue }
                        }
                    } else {
                        Write-Warn "$($sidecar.Name): falta $($sidecar.EnvKey)"
                    }
                } else {
                    Write-Ok "$($sidecar.Name): credenciales detectadas"
                }
            }

            $EnabledServices += $sidecar.Dir
        }
    }

    # Arrancar PM2
    if ($EnabledServices.Count -gt 0) {
        Write-Host ""
        Write-Info "Iniciando sidecars seleccionados..."
        Push-Location $InstallDir
        $only = $EnabledServices -join ","
        pm2 start ecosystem.config.js --only $only 2>$null
        pm2 save 2>$null
        Pop-Location
        Write-Ok "Sidecars activos en PM2"

        # Registrar PM2 como servicio de Windows
        if ($isAdmin) {
            Write-Info "Configurando PM2 para inicio automatico..."
            $pm2Startup = Get-Command pm2-startup -ErrorAction SilentlyContinue
            if (-not $pm2Startup) {
                npm install -g pm2-windows-startup 2>$null
            }
            pm2-startup install 2>$null
            Write-Ok "PM2 registrado como servicio de Windows"
        }
    }
}

# =============================================================================
# PASO 5: RESUMEN
# =============================================================================
Write-Step 5 5 "Instalacion completada"

Write-Host ""
Write-Host "  ==================================================================" -ForegroundColor Green
Write-Host "              FARARONI SUITE v$Version - OPERATIVO"                   -ForegroundColor Green
Write-Host "  ==================================================================" -ForegroundColor Green

Write-Host ""
Write-Host "  CORE (GraalVM Native):" -ForegroundColor Cyan
Write-Host "    Binario:     $Binary"
Write-Host "    Comando:     fararoni --server"
if ($isAdmin) {
    Write-Host "    Servicio:    Start-Service $ServiceName"
    Write-Host "    Monitor:     services.msc"
}
Write-Host "    Logs:        Get-Content $logsDir\core.log -Wait -Tail 50"

Write-Host ""
Write-Host "  SIDECARS (PM2):" -ForegroundColor Cyan
if ($EnabledServices.Count -gt 0) {
    Write-Host "    Panel:       pm2 monit"
    Write-Host "    Estado:      pm2 status"
    Write-Host "    Logs:        pm2 logs"
    Write-Host ""
    Write-Host "    Logs individuales:" -ForegroundColor DarkGray
    foreach ($svc in $EnabledServices) {
        $friendly = $svc -replace "fararoni-sidecar-", ""
        Write-Host "      ${friendly}:  $logsDir\$friendly-out.log"
    }
} else {
    Write-Host "    No se activaron sidecars." -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "  COMANDOS RAPIDOS:" -ForegroundColor Cyan
Write-Host "    fararoni --version        Verificar"
Write-Host "    fararoni                  CLI interactivo"
Write-Host "    fararoni --server         Servidor (REST + Gateway)"
Write-Host "    pm2 monit                 Monitor sidecars"

Write-Host ""
Write-Host "  ==================================================================" -ForegroundColor Green
Write-Host "    Fararoni v$Version instalado. Sistema operativo."                 -ForegroundColor Green
Write-Host "  ==================================================================" -ForegroundColor Green
Write-Host ""

Write-Host "Presiona cualquier tecla para cerrar..." -ForegroundColor DarkGray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
