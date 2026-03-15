# =============================================================================
# Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
# Licensed under the Apache License, Version 2.0
# =============================================================================
#
# Fararoni Suite — Bootstrap Installer (Windows)
#
# Uso:
#   iwr -useb https://fararoni.com/install.ps1 | iex
#
# Que hace:
#   1. Detecta arquitectura y permisos
#   2. Descarga el bundle (ZIP)
#   3. Verifica integridad (SHA-256)
#   4. Extrae en ~\Fararoni
#   5. Ejecuta el wizard de instalacion
#
# =============================================================================
$ErrorActionPreference = "Stop"

$Version    = "1.0.0"
$BaseUrl    = "https://fararoni.com/releases/v$Version"
$InstallDir = "$env:USERPROFILE\Fararoni"

Write-Host ""
Write-Host "  ==================================================================" -ForegroundColor Cyan
Write-Host "       Fararoni Suite v$Version - Instalador Rapido (Windows)"       -ForegroundColor Cyan
Write-Host "  ==================================================================" -ForegroundColor Cyan
Write-Host ""

# --- Verificar Admin ---
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator
)

if (-not $isAdmin) {
    Write-Host "[WARN] No tienes permisos de Administrador." -ForegroundColor Yellow
    Write-Host "       El servicio de Windows no se podra registrar." -ForegroundColor Yellow
    Write-Host "       Recomendacion: Ejecuta PowerShell como Administrador." -ForegroundColor Yellow
    Write-Host ""
}

# --- Detectar arquitectura ---
$arch = if ([Environment]::Is64BitOperatingSystem) { "x64" } else { "x86" }
$winVer = [Environment]::OSVersion.Version
Write-Host "[1/4] Sistema detectado: Windows $winVer ($arch)" -ForegroundColor Green

# --- Descargar ---
$Archive      = "fararoni-v$Version.zip"
$Url          = "$BaseUrl/$Archive"
$ChecksumUrl  = "$BaseUrl/fararoni-v$Version-checksums.txt"
$TmpDir       = Join-Path $env:TEMP "fararoni-install-$(Get-Random)"
$ZipPath      = Join-Path $TmpDir $Archive

if (Test-Path $TmpDir) { Remove-Item -Recurse -Force $TmpDir }
New-Item -ItemType Directory -Path $TmpDir | Out-Null

Write-Host "[2/4] Descargando $Archive (~150 MB)..." -ForegroundColor Green

try {
    # Usar BITS para descarga con progreso, fallback a Invoke-WebRequest
    $bitsAvailable = Get-Command Start-BitsTransfer -ErrorAction SilentlyContinue
    if ($bitsAvailable) {
        Start-BitsTransfer -Source $Url -Destination $ZipPath -DisplayName "Descargando Fararoni"
    } else {
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $Url -OutFile $ZipPath -UseBasicParsing
        $ProgressPreference = 'Continue'
    }
} catch {
    Write-Host "ERROR: No se pudo descargar el archivo." -ForegroundColor Red
    Write-Host "  URL: $Url" -ForegroundColor Red
    Write-Host "  Verifica tu conexion a internet." -ForegroundColor Red
    Remove-Item -Recurse -Force $TmpDir -ErrorAction SilentlyContinue
    exit 1
}

# Descargar checksums (no fatal si falla)
$checksumContent = $null
try {
    $checksumContent = (Invoke-WebRequest -Uri $ChecksumUrl -UseBasicParsing -ErrorAction SilentlyContinue).Content
} catch { }

# --- Verificar integridad ---
Write-Host "[3/4] Verificando integridad (SHA-256)..." -ForegroundColor Green

$verifyOk = $false

if ($checksumContent) {
    $expectedLine = $checksumContent -split "`n" | Where-Object { $_ -like "*$Archive*" } | Select-Object -First 1
    if ($expectedLine) {
        $expectedHash = ($expectedLine -split "\s+")[0].Trim().ToLower()
        $actualHash = (Get-FileHash -Path $ZipPath -Algorithm SHA256).Hash.ToLower()

        if ($expectedHash -eq $actualHash) {
            Write-Host "  SHA-256 verificado correctamente." -ForegroundColor Green
            $verifyOk = $true
        } else {
            Write-Host "ERROR: Hash no coincide. Descarga corrupta." -ForegroundColor Red
            Write-Host "  Esperado: $expectedHash"
            Write-Host "  Actual:   $actualHash"
            Remove-Item -Recurse -Force $TmpDir
            exit 1
        }
    }
}

if (-not $verifyOk) {
    Write-Host "  Checksums no disponibles. Continuando sin verificacion." -ForegroundColor Yellow
}

# --- Extraer ---
Write-Host "[4/4] Instalando en $InstallDir..." -ForegroundColor Green

if (Test-Path $InstallDir) {
    $timestamp = Get-Date -Format "yyyyMMddHHmmss"
    $backup = "${InstallDir}.bak-$timestamp"
    Write-Host "  Directorio existente encontrado. Respaldando a: $backup" -ForegroundColor Yellow
    Move-Item -Path $InstallDir -Destination $backup
}

# Extraer ZIP
Expand-Archive -Path $ZipPath -DestinationPath $TmpDir -Force

# Mover contenido (quitar directorio raiz del ZIP: fararoni-v1.0.0/)
$extracted = Get-ChildItem -Path $TmpDir -Directory | Where-Object { $_.Name -like "fararoni-v*" } | Select-Object -First 1

if (-not $extracted) {
    Write-Host "ERROR: Contenido del ZIP no reconocido." -ForegroundColor Red
    Remove-Item -Recurse -Force $TmpDir
    exit 1
}

Move-Item -Path $extracted.FullName -Destination $InstallDir

# Limpiar temporales
Remove-Item -Recurse -Force $TmpDir -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "  Descarga completada. Iniciando wizard de instalacion..." -ForegroundColor Green
Write-Host ""

# --- Ejecutar wizard ---
Set-Location $InstallDir

$installerPath = Join-Path $InstallDir "installer.ps1"
if (Test-Path $installerPath) {
    & $installerPath
} else {
    Write-Host "ERROR: No se encontro installer.ps1 en $InstallDir" -ForegroundColor Red
    Write-Host "  Contenido del directorio:"
    Get-ChildItem $InstallDir | ForEach-Object { Write-Host "    $($_.Name)" }
    exit 1
}
