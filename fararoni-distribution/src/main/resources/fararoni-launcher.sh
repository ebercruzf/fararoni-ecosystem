#!/bin/zsh
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
#  FARARONI LAUNCHER — Orquestador de Core + Sidecars (binarios nativos)
#
#  Inicia fararoni-core en foreground y los sidecars como procesos background.
#  Maneja SIGINT/SIGTERM para shutdown limpio de todos los procesos.
#
#  Uso:
#    ./fararoni-launcher.sh                   # Inicia core + todos los sidecars
#    ./fararoni-launcher.sh --no-sidecars     # Solo core
#    ./fararoni-launcher.sh --sidecars-only   # Solo sidecars (background)
#
# =============================================================================

DIR="$(cd "$(dirname "$0")" && pwd)"
LOGS_DIR="$DIR/logs"
PIDS=()

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

mkdir -p "$LOGS_DIR"

# =============================================================================
# SHUTDOWN HANDLER
# =============================================================================
shutdown() {
    echo ""
    echo -e "${YELLOW}[LAUNCHER]${NC} Deteniendo procesos..."
    for pid in "${PIDS[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null
            echo -e "${YELLOW}[LAUNCHER]${NC} Detenido PID $pid"
        fi
    done
    wait 2>/dev/null
    echo -e "${GREEN}[LAUNCHER]${NC} Shutdown completado."
    exit 0
}

trap shutdown SIGINT SIGTERM EXIT

# =============================================================================
# PARSEAR ARGUMENTOS
# =============================================================================
START_CORE=true
START_SIDECARS=true

for arg in "$@"; do
    case $arg in
        --no-sidecars)    START_SIDECARS=false ;;
        --sidecars-only)  START_CORE=false ;;
        --help|-h)
            echo "Fararoni Launcher"
            echo ""
            echo "  ./fararoni-launcher.sh                 Inicia core + sidecars"
            echo "  ./fararoni-launcher.sh --no-sidecars   Solo core"
            echo "  ./fararoni-launcher.sh --sidecars-only Solo sidecars"
            exit 0
            ;;
    esac
done

# =============================================================================
# SIDECARS
# =============================================================================
SIDECAR_BINS=(sidecar-telegram sidecar-whatsapp sidecar-discord sidecar-imessage)
SIDECAR_NAMES=(Telegram WhatsApp Discord iMessage)

if [[ "$START_SIDECARS" == true ]]; then
    echo -e "${CYAN}${BOLD}[LAUNCHER]${NC} Iniciando sidecars..."

    for i in {1..${#SIDECAR_BINS[@]}}; do
        local_bin="$DIR/bin/${SIDECAR_BINS[$i]}"
        local_name="${SIDECAR_NAMES[$i]}"

        if [[ -x "$local_bin" ]]; then
            "$local_bin" >> "$LOGS_DIR/${SIDECAR_BINS[$i]}-out.log" 2>> "$LOGS_DIR/${SIDECAR_BINS[$i]}-err.log" &
            PIDS+=($!)
            echo -e "${GREEN}[OK]${NC} $local_name iniciado (PID $!)"
        else
            echo -e "${YELLOW}[WARN]${NC} $local_name no encontrado: $local_bin"
        fi
    done

    echo ""
fi

# =============================================================================
# CORE
# =============================================================================
if [[ "$START_CORE" == true ]]; then
    CORE_BIN="$DIR/bin/fararoni-core"

    if [[ ! -x "$CORE_BIN" ]]; then
        echo -e "${RED}[ERROR]${NC} Core no encontrado: $CORE_BIN"
        exit 1
    fi

    echo -e "${CYAN}${BOLD}[LAUNCHER]${NC} Iniciando fararoni-core --server..."
    "$CORE_BIN" --server
else
    echo -e "${CYAN}[LAUNCHER]${NC} Core no iniciado (--sidecars-only)."
    echo -e "${CYAN}[LAUNCHER]${NC} Sidecars en background. Ctrl+C para detener."
    wait
fi
