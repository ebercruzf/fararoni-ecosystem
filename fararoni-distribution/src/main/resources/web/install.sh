#!/bin/bash
# =============================================================================
# Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
# Licensed under the Apache License, Version 2.0
# =============================================================================
#
# Fararoni Suite — Bootstrap Installer (macOS / Linux)
#
# Uso:
#   curl -fsSL https://fararoni.com/install.sh | bash
#
# Que hace:
#   1. Detecta SO y arquitectura
#   2. Descarga el bundle (tar.gz)
#   3. Verifica integridad (SHA-256)
#   4. Extrae en ~/Fararoni
#   5. Ejecuta el wizard de instalacion
#
# =============================================================================
set -e

VERSION="1.0.0"
BASE_URL="https://fararoni.com/releases/v${VERSION}"
INSTALL_DIR="$HOME/Fararoni"

# --- Colores ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

echo ""
echo -e "${CYAN}${BOLD}"
echo "  =================================================================="
echo "       Fararoni Suite v${VERSION} — Instalador Rapido"
echo "  =================================================================="
echo -e "${NC}"

# --- Detectar SO ---
OS="$(uname -s)"
ARCH="$(uname -m)"

case "$OS" in
    Darwin)
        PLATFORM="macos"
        case "$ARCH" in
            arm64)  ARCH_LABEL="Apple Silicon (arm64)" ;;
            x86_64) ARCH_LABEL="Intel (x86_64)" ;;
            *)      ARCH_LABEL="$ARCH" ;;
        esac
        ;;
    Linux)
        PLATFORM="linux"
        case "$ARCH" in
            x86_64)  ARCH_LABEL="x86_64" ;;
            aarch64) ARCH_LABEL="ARM64 (aarch64)" ;;
            *)       ARCH_LABEL="$ARCH" ;;
        esac
        ;;
    *)
        echo -e "${RED}Sistema no soportado: $OS${NC}"
        echo "Usa Windows PowerShell: iwr -useb https://fararoni.com/install.ps1 | iex"
        exit 1
        ;;
esac

echo -e "${GREEN}[1/4]${NC} Sistema detectado: $OS $ARCH_LABEL"

# --- Verificar herramientas ---
if command -v curl &>/dev/null; then
    DOWNLOADER="curl"
elif command -v wget &>/dev/null; then
    DOWNLOADER="wget"
else
    echo -e "${RED}Se requiere curl o wget. Instala uno e intenta de nuevo.${NC}"
    exit 1
fi

# --- Descargar ---
ARCHIVE="fararoni-v${VERSION}.tar.gz"
URL="${BASE_URL}/${ARCHIVE}"
CHECKSUM_URL="${BASE_URL}/fararoni-v${VERSION}-checksums.txt"
TMP_DIR=$(mktemp -d)
TMP_FILE="$TMP_DIR/$ARCHIVE"
TMP_CHECKSUM="$TMP_DIR/checksums.txt"

echo -e "${GREEN}[2/4]${NC} Descargando ${ARCHIVE} (~150 MB)..."

if [[ "$DOWNLOADER" == "curl" ]]; then
    curl -fSL --progress-bar "$URL" -o "$TMP_FILE"
    curl -fsSL "$CHECKSUM_URL" -o "$TMP_CHECKSUM" 2>/dev/null || true
else
    wget -q --show-progress "$URL" -O "$TMP_FILE"
    wget -q "$CHECKSUM_URL" -O "$TMP_CHECKSUM" 2>/dev/null || true
fi

# --- Verificar integridad ---
echo -e "${GREEN}[3/4]${NC} Verificando integridad (SHA-256)..."

VERIFY_OK=false

if [[ -f "$TMP_CHECKSUM" ]]; then
    EXPECTED_HASH=$(grep "$ARCHIVE" "$TMP_CHECKSUM" 2>/dev/null | awk '{print $1}')

    if [[ -n "$EXPECTED_HASH" ]]; then
        if command -v sha256sum &>/dev/null; then
            ACTUAL_HASH=$(sha256sum "$TMP_FILE" | awk '{print $1}')
        elif command -v shasum &>/dev/null; then
            ACTUAL_HASH=$(shasum -a 256 "$TMP_FILE" | awk '{print $1}')
        fi

        if [[ "$EXPECTED_HASH" == "$ACTUAL_HASH" ]]; then
            echo -e "  ${GREEN}SHA-256 verificado correctamente.${NC}"
            VERIFY_OK=true
        else
            echo -e "${RED}ERROR: Hash no coincide. Descarga corrupta.${NC}"
            echo "  Esperado: $EXPECTED_HASH"
            echo "  Actual:   $ACTUAL_HASH"
            rm -rf "$TMP_DIR"
            exit 1
        fi
    fi
fi

if [[ "$VERIFY_OK" == false ]]; then
    echo -e "  ${YELLOW}Checksums no disponibles. Continuando sin verificacion.${NC}"
fi

# --- Extraer e instalar ---
echo -e "${GREEN}[4/4]${NC} Instalando en $INSTALL_DIR..."

if [[ -d "$INSTALL_DIR" ]]; then
    echo -e "  ${YELLOW}Directorio existente encontrado. Respaldando...${NC}"
    BACKUP="${INSTALL_DIR}.bak-$(date +%Y%m%d%H%M%S)"
    mv "$INSTALL_DIR" "$BACKUP"
    echo -e "  Respaldo en: $BACKUP"
fi

mkdir -p "$INSTALL_DIR"
tar -xzf "$TMP_FILE" -C "$INSTALL_DIR" --strip-components=1

rm -rf "$TMP_DIR"

# Permisos
chmod +x "$INSTALL_DIR/bin/fararoni-core" 2>/dev/null || true
chmod +x "$INSTALL_DIR/bin/fararoni.sh" 2>/dev/null || true
chmod +x "$INSTALL_DIR/installer.sh" 2>/dev/null || true

echo ""
echo -e "${GREEN}${BOLD}  Descarga completada. Iniciando wizard de instalacion...${NC}"
echo ""

# --- Ejecutar wizard ---
cd "$INSTALL_DIR"
exec ./installer.sh
