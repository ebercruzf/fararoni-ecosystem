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
#  FARARONI SUITE v1.0.0 - Instalador Profesional
#
#  Este script se ejecuta DENTRO del bundle distribuible:
#    fararoni-v1.0.0/installer.sh
#
#  Arquitectura Zero-Dependencies:
#    Core (GraalVM Native)  → Servicio del sistema (systemd/launchd)
#    Sidecars (Node.js SEA) → Servicios del sistema (LaunchAgent/systemd)
#
#  Uso:
#    ./installer.sh                  # Wizard interactivo
#    ./installer.sh --silent         # Sin prompts (instala todo)
#    ./installer.sh --uninstall      # Desinstalar servicios
#    ./installer.sh --help           # Ayuda
#
# =============================================================================

set -e

# =============================================================================
# CONFIGURACION
# =============================================================================
VERSION="1.0.0"
APP_NAME="fararoni"
INSTALL_DIR="$(cd "$(dirname "$0")" && pwd)"
USER_NAME="$(whoami)"
OS_TYPE="$(uname)"

# Componentes
BINARY="$INSTALL_DIR/bin/fararoni-core"
SIDECARS=("fararoni-sidecar-telegram" "fararoni-sidecar-whatsapp" "fararoni-sidecar-discord" "fararoni-sidecar-imessage")
SIDECAR_NAMES=("Telegram" "WhatsApp" "Discord" "iMessage")
SIDECAR_PORTS=(3001 3000 3002 3003)
SIDECAR_ENV_KEYS=("TELEGRAM_TOKEN" "" "DISCORD_TOKEN" "BLUEBUBBLES_PASSWORD")
SIDECAR_DOC_URLS=(
    "https://core.telegram.org/bots#how-do-i-create-a-bot"
    "Escanea QR al iniciar (Baileys)"
    "https://discord.com/developers/applications"
    "Requiere BlueBubbles Server en macOS"
)
SIDECAR_ENV_DESC=(
    "Token de BotFather para Telegram"
    "Sesion QR (automatica al iniciar)"
    "Token del Bot en Developer Portal"
    "Password del servidor BlueBubbles"
)

# Flags
SILENT_MODE=false
UNINSTALL=false
ENABLED_SERVICES=""

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'
DIM='\033[2m'

# =============================================================================
# UTILIDADES
# =============================================================================
log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[OK]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()    { echo -e "\n${CYAN}[$1/$2]${NC} ${BOLD}$3${NC}"; }

print_banner() {
    echo ""
    echo -e "${CYAN}${BOLD}"
    echo "  =================================================================="
    echo ""
    echo "   ███████╗ █████╗ ██████╗  █████╗ ██████╗  ██████╗ ███╗   ██╗██╗ "
    echo "   ██╔════╝██╔══██╗██╔══██╗██╔══██╗██╔══██╗██╔═══██╗████╗  ██║██║ "
    echo "   █████╗  ███████║██████╔╝███████║██████╔╝██║   ██║██╔██╗ ██║██║ "
    echo "   ██╔══╝  ██╔══██║██╔══██╗██╔══██║██╔══██╗██║   ██║██║╚██╗██║██║ "
    echo "   ██║     ██║  ██║██║  ██║██║  ██║██║  ██║╚██████╔╝██║ ╚████║██║ "
    echo "   ╚═╝     ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═══╝  "
    echo ""
    echo "          Suite Installer v${VERSION} — Zero Dependencies"
    echo ""
    echo "  =================================================================="
    echo -e "${NC}"
}

show_help() {
    echo "FARARONI Suite Installer v${VERSION}"
    echo ""
    echo "Uso: ./installer.sh [OPCIONES]"
    echo ""
    echo "  --silent       Instalacion sin prompts (todo por defecto)"
    echo "  --uninstall    Desinstalar servicios del sistema"
    echo "  --help         Mostrar esta ayuda"
    echo ""
    echo "Estructura del bundle:"
    echo "  bin/fararoni-core              Binario nativo GraalVM (arm64/amd64)"
    echo "  bin/sidecar-*                  Sidecars nativos (Node.js SEA)"
    echo "  lib/                           JARs Enterprise (gateway, transport)"
    echo "  config/global.env              Configuracion centralizada"
    echo "  fararoni-launcher.sh           Orquestador de procesos"
    echo ""
}

# =============================================================================
# PARSEAR ARGUMENTOS
# =============================================================================
for arg in "$@"; do
    case $arg in
        --silent|-s)     SILENT_MODE=true ;;
        --uninstall)     UNINSTALL=true ;;
        --help|-h)       show_help; exit 0 ;;
        *)               log_error "Opcion desconocida: $arg"; exit 1 ;;
    esac
done

# =============================================================================
# DESINSTALAR
# =============================================================================
uninstall() {
    print_banner
    log_info "Desinstalando servicios de Fararoni..."

    # PM2 (legacy — backward compat)
    if command -v pm2 &> /dev/null; then
        pm2 stop ecosystem.config.js 2>/dev/null || true
        pm2 delete ecosystem.config.js 2>/dev/null || true
        pm2 save 2>/dev/null || true
        log_success "Sidecars PM2 detenidos (legacy)"
    fi

    # LaunchAgents (macOS) — core + sidecars
    for plist_name in com.fararoni.core com.fararoni.sidecar-telegram com.fararoni.sidecar-whatsapp com.fararoni.sidecar-discord com.fararoni.sidecar-imessage; do
        local plist="$HOME/Library/LaunchAgents/${plist_name}.plist"
        if [[ -f "$plist" ]]; then
            launchctl unload "$plist" 2>/dev/null || true
            rm -f "$plist"
            log_success "LaunchAgent removido: $plist_name"
        fi
    done

    # Systemd (Linux) — core + sidecars
    for svc_name in "${APP_NAME}-core" "${APP_NAME}-sidecar-telegram" "${APP_NAME}-sidecar-whatsapp" "${APP_NAME}-sidecar-discord" "${APP_NAME}-sidecar-imessage"; do
        local service="/etc/systemd/system/${svc_name}.service"
        if [[ -f "$service" ]]; then
            sudo systemctl stop "$svc_name" 2>/dev/null || true
            sudo systemctl disable "$svc_name" 2>/dev/null || true
            sudo rm -f "$service"
            log_success "Systemd service removido: $svc_name"
        fi
    done
    sudo systemctl daemon-reload 2>/dev/null || true

    # Symlinks
    for target in "/usr/local/bin/fararoni" "/usr/local/bin/fararoni-core" "$HOME/bin/fararoni" "$HOME/bin/fararoni-core"; do
        if [[ -f "$target" || -L "$target" ]]; then
            if [[ -w "$(dirname "$target")" ]]; then rm -f "$target"; else sudo rm -f "$target"; fi
            log_success "Removido: $target"
        fi
    done

    echo ""
    log_success "Desinstalacion completada."
    exit 0
}

[[ "$UNINSTALL" == true ]] && uninstall

# =============================================================================
# PASO 1: VERIFICAR REQUISITOS
# =============================================================================
print_banner
log_step 1 5 "Verificando requisitos..."

# OS
case "$OS_TYPE" in
    Darwin) OS_NAME="macOS"; ARCH="$(uname -m)" ;;
    Linux)  OS_NAME="Linux"; ARCH="$(uname -m)" ;;
    *)      log_error "Sistema no soportado: $OS_TYPE"; exit 1 ;;
esac
log_success "Sistema: $OS_NAME ($ARCH)"

# Binario nativo
if [[ ! -x "$BINARY" ]]; then
    log_error "Binario nativo no encontrado: $BINARY"
    log_info "Este bundle puede no ser compatible con tu arquitectura ($ARCH)."
    exit 1
fi
BINARY_SIZE=$(ls -lh "$BINARY" | awk '{print $5}')
log_success "Core nativo: fararoni-core ($BINARY_SIZE)"

# Sidecars (binarios SEA)
SIDECAR_BIN_DIR="$INSTALL_DIR/bin"
HAS_SIDECARS=false
if [[ -x "$SIDECAR_BIN_DIR/sidecar-telegram" || -x "$SIDECAR_BIN_DIR/sidecar-whatsapp" || -x "$SIDECAR_BIN_DIR/sidecar-discord" || -x "$SIDECAR_BIN_DIR/sidecar-imessage" ]]; then
    HAS_SIDECARS=true
    log_success "Sidecars nativos detectados en bin/"
else
    log_warning "Sidecars nativos no encontrados en bin/"
fi

# =============================================================================
# PASO 2: INSTALAR CORE COMO SERVICIO
# =============================================================================
log_step 2 5 "Registrando Core como servicio del sistema..."

mkdir -p "$INSTALL_DIR/logs"

if [[ "$SILENT_MODE" == false ]]; then
    echo -e "  ${DIM}El Core se ejecutara como servicio de fondo (auto-start, auto-restart).${NC}"
    read "choice?  Registrar como servicio? (s/n): "
else
    choice="s"
fi

if [[ "$choice" == "s" || "$choice" == "S" ]]; then
    if [[ "$OS_TYPE" == "Darwin" ]]; then
        # --- macOS: LaunchAgent ---
        PLIST_DIR="$HOME/Library/LaunchAgents"
        PLIST_PATH="$PLIST_DIR/com.fararoni.core.plist"
        mkdir -p "$PLIST_DIR"

        cat > "$PLIST_PATH" << PLISTEOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.fararoni.core</string>
    <key>ProgramArguments</key>
    <array>
        <string>${BINARY}</string>
        <string>--server</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>WorkingDirectory</key>
    <string>${INSTALL_DIR}</string>
    <key>StandardOutPath</key>
    <string>${INSTALL_DIR}/logs/core.log</string>
    <key>StandardErrorPath</key>
    <string>${INSTALL_DIR}/logs/core-err.log</string>
    <key>ThrottleInterval</key>
    <integer>5</integer>
</dict>
</plist>
PLISTEOF

        log_success "LaunchAgent creado: $PLIST_PATH"
        log_info "Cargar: launchctl load $PLIST_PATH"

    elif [[ "$OS_TYPE" == "Linux" ]]; then
        # --- Linux: Systemd ---
        SERVICE_PATH="/etc/systemd/system/${APP_NAME}-core.service"

        cat << SVCEOF | sudo tee "$SERVICE_PATH" > /dev/null
[Unit]
Description=Fararoni Core Service v${VERSION} (Native GraalVM)
After=network.target

[Service]
User=${USER_NAME}
WorkingDirectory=${INSTALL_DIR}
ExecStart=${BINARY} --server
Restart=always
RestartSec=5
StandardOutput=append:${INSTALL_DIR}/logs/core.log
StandardError=append:${INSTALL_DIR}/logs/core-err.log
MemoryMax=512M
CPUWeight=100

[Install]
WantedBy=multi-user.target
SVCEOF

        sudo systemctl daemon-reload
        sudo systemctl enable "${APP_NAME}-core"
        log_success "Systemd service creado: $SERVICE_PATH"
        log_info "Iniciar: sudo systemctl start ${APP_NAME}-core"
    fi
else
    log_info "Servicio no registrado. Ejecuta manualmente: ./bin/fararoni-core --server"
fi

# =============================================================================
# PASO 3: INSTALAR COMANDO GLOBAL
# =============================================================================
log_step 3 5 "Instalando comando global 'fararoni'..."

if [[ -w "/usr/local/bin" ]]; then
    BIN_DIR="/usr/local/bin"
    NEED_SUDO=false
elif sudo -n true 2>/dev/null; then
    BIN_DIR="/usr/local/bin"
    NEED_SUDO=true
else
    BIN_DIR="$HOME/bin"
    NEED_SUDO=false
    mkdir -p "$BIN_DIR"
fi

FARARONI_CMD="$BIN_DIR/fararoni"

if [[ "$NEED_SUDO" == true ]]; then
    sudo ln -sf "$BINARY" "$FARARONI_CMD"
else
    ln -sf "$BINARY" "$FARARONI_CMD"
fi

log_success "Symlink: $FARARONI_CMD -> $BINARY"

if [[ ":$PATH:" != *":$BIN_DIR:"* ]]; then
    log_warning "$BIN_DIR no esta en tu PATH."
    echo -e "  ${YELLOW}Agrega a ~/.zshrc:${NC}  export PATH=\"$BIN_DIR:\$PATH\""
fi

# =============================================================================
# PASO 4: CONFIGURAR SIDECARS (Binarios Nativos SEA)
# =============================================================================
log_step 4 5 "Configurando Sidecars (binarios nativos)..."

# Mapeo: nombre_binario -> indice en arrays
SIDECAR_BINS=("sidecar-telegram" "sidecar-whatsapp" "sidecar-discord" "sidecar-imessage")

if [[ "$HAS_SIDECARS" == false ]]; then
    log_warning "Sidecars no disponibles en bin/. Saltando."
else
    echo ""
    echo -e "  ${BOLD}Canales disponibles:${NC}"
    echo ""

    for i in {0..3}; do
        local_bin="${SIDECAR_BINS[$((i+1))]}"
        local_sidecar="${SIDECARS[$((i+1))]}"
        local_name="${SIDECAR_NAMES[$((i+1))]}"
        local_port="${SIDECAR_PORTS[$((i+1))]}"
        local_env_key="${SIDECAR_ENV_KEYS[$((i+1))]}"
        local_doc="${SIDECAR_DOC_URLS[$((i+1))]}"
        local_desc="${SIDECAR_ENV_DESC[$((i+1))]}"
        local_bin_path="$SIDECAR_BIN_DIR/$local_bin"

        if [[ ! -x "$local_bin_path" ]]; then
            echo -e "  ${DIM}[$((i+1))] $local_name (Puerto $local_port) — No disponible${NC}"
            continue
        fi

        if [[ "$SILENT_MODE" == true ]]; then
            activate="s"
        else
            read "activate?  [$((i+1))] Activar $local_name (Puerto $local_port)? (s/n): "
        fi

        if [[ "$activate" == "s" || "$activate" == "S" ]]; then
            # Validar credenciales (env file junto al binario o en config/)
            mkdir -p "$INSTALL_DIR/config"
            local_env_file="$INSTALL_DIR/config/${local_bin}.env"
            if [[ -n "$local_env_key" ]]; then
                local_current=""
                if [[ -f "$local_env_file" ]]; then
                    local_current=$(grep "^${local_env_key}=" "$local_env_file" 2>/dev/null | cut -d'=' -f2)
                fi

                if [[ -z "$local_current" || "$local_current" == *"tu_"* || "$local_current" == *"aqui"* ]]; then
                    if [[ "$SILENT_MODE" == false ]]; then
                        echo ""
                        echo -e "  ${YELLOW}Credencial requerida:${NC} $local_desc"
                        echo -e "  ${DIM}Documentacion: $local_doc${NC}"
                        echo -e "    ${BOLD}1)${NC} Configurar ahora"
                        echo -e "    ${BOLD}2)${NC} Configurar despues (no arranca)"
                        echo -e "    ${BOLD}3)${NC} Arrancar sin credencial"

                        read "opt?  Opcion [1-3]: "
                        case $opt in
                            1)
                                read "new_val?  Ingresa $local_env_key: "
                                [[ ! -f "$local_env_file" ]] && touch "$local_env_file"
                                if grep -q "^${local_env_key}=" "$local_env_file" 2>/dev/null; then
                                    sed -i '' "s|^${local_env_key}=.*|${local_env_key}=${new_val}|" "$local_env_file"
                                else
                                    echo "${local_env_key}=${new_val}" >> "$local_env_file"
                                fi
                                # Asegurar variables base
                                grep -q "^SIDECAR_PORT=" "$local_env_file" 2>/dev/null || echo "SIDECAR_PORT=$local_port" >> "$local_env_file"
                                grep -q "^GATEWAY_URL=" "$local_env_file" 2>/dev/null || echo "GATEWAY_URL=http://localhost:7071/gateway/v1/inbound" >> "$local_env_file"
                                log_success "$local_env_key configurado"
                                ;;
                            2)
                                log_info "$local_name saltado."
                                continue
                                ;;
                            3)
                                log_warning "$local_name arrancara sin credencial."
                                ;;
                            *)
                                continue
                                ;;
                        esac
                    else
                        log_warning "$local_name: falta $local_env_key"
                    fi
                else
                    log_success "$local_name: credenciales detectadas"
                fi
            fi

            # Asegurar env file con variables base
            if [[ ! -f "$local_env_file" ]]; then
                cat > "$local_env_file" << ENVEOF
NODE_ENV=production
SIDECAR_PORT=$local_port
GATEWAY_URL=http://localhost:7071/gateway/v1/inbound
ENVEOF
            fi

            # Registrar sidecar como servicio del sistema
            if [[ "$OS_TYPE" == "Darwin" ]]; then
                # macOS: LaunchAgent individual
                local_plist_path="$HOME/Library/LaunchAgents/com.fararoni.${local_bin}.plist"
                cat > "$local_plist_path" << SIDECAREOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.fararoni.${local_bin}</string>
    <key>ProgramArguments</key>
    <array>
        <string>${local_bin_path}</string>
    </array>
    <key>EnvironmentVariables</key>
    <dict>
        <key>NODE_ENV</key>
        <string>production</string>
        <key>SIDECAR_PORT</key>
        <string>${local_port}</string>
        <key>GATEWAY_URL</key>
        <string>http://localhost:7071/gateway/v1/inbound</string>
    </dict>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>WorkingDirectory</key>
    <string>${INSTALL_DIR}</string>
    <key>StandardOutPath</key>
    <string>${INSTALL_DIR}/logs/${local_bin}-out.log</string>
    <key>StandardErrorPath</key>
    <string>${INSTALL_DIR}/logs/${local_bin}-err.log</string>
    <key>ThrottleInterval</key>
    <integer>5</integer>
</dict>
</plist>
SIDECAREOF
                log_success "$local_name: LaunchAgent creado"

            elif [[ "$OS_TYPE" == "Linux" ]]; then
                # Linux: Systemd unit individual
                local_svc_path="/etc/systemd/system/${APP_NAME}-${local_bin}.service"
                cat << SVCEOF | sudo tee "$local_svc_path" > /dev/null
[Unit]
Description=Fararoni ${local_name} Sidecar (Native SEA)
After=network.target ${APP_NAME}-core.service

[Service]
User=${USER_NAME}
WorkingDirectory=${INSTALL_DIR}
ExecStart=${local_bin_path}
EnvironmentFile=${local_env_file}
Restart=always
RestartSec=5
StandardOutput=append:${INSTALL_DIR}/logs/${local_bin}-out.log
StandardError=append:${INSTALL_DIR}/logs/${local_bin}-err.log
MemoryMax=256M

[Install]
WantedBy=multi-user.target
SVCEOF
                sudo systemctl daemon-reload
                sudo systemctl enable "${APP_NAME}-${local_bin}"
                log_success "$local_name: Systemd service creado"
            fi

            ENABLED_SERVICES="${ENABLED_SERVICES}${ENABLED_SERVICES:+,}${local_bin}"
        fi
    done
fi

# =============================================================================
# PASO 5: RESUMEN
# =============================================================================
log_step 5 5 "Instalacion completada"

echo ""
echo -e "${GREEN}${BOLD}"
echo "  =================================================================="
echo "              FARARONI SUITE v${VERSION} — OPERATIVO"
echo "  =================================================================="
echo -e "${NC}"

echo -e "  ${CYAN}${BOLD}CORE (GraalVM Native):${NC}"
echo -e "    Binario:     $BINARY"
echo -e "    Comando:     ${BOLD}fararoni --server${NC}"
if [[ "$OS_TYPE" == "Darwin" ]]; then
    echo -e "    Servicio:    launchctl load ~/Library/LaunchAgents/com.fararoni.core.plist"
    echo -e "    Logs:        tail -f $INSTALL_DIR/logs/core.log"
elif [[ "$OS_TYPE" == "Linux" ]]; then
    echo -e "    Servicio:    sudo systemctl start ${APP_NAME}-core"
    echo -e "    Logs:        journalctl -u ${APP_NAME}-core -f"
fi

echo ""
echo -e "  ${CYAN}${BOLD}SIDECARS (Nativos SEA):${NC}"
if [[ -n "$ENABLED_SERVICES" ]]; then
    IFS=',' read -rA SLIST <<< "$ENABLED_SERVICES"
    for service in "${SLIST[@]}"; do
        friendly=$(echo "$service" | sed 's/sidecar-//')
        echo -e "    $friendly:  $INSTALL_DIR/logs/${service}-out.log"
    done
    echo ""
    if [[ "$OS_TYPE" == "Darwin" ]]; then
        echo -e "    Iniciar:     launchctl load ~/Library/LaunchAgents/com.fararoni.sidecar-*.plist"
        echo -e "    Detener:     launchctl unload ~/Library/LaunchAgents/com.fararoni.sidecar-*.plist"
    elif [[ "$OS_TYPE" == "Linux" ]]; then
        echo -e "    Iniciar:     sudo systemctl start ${APP_NAME}-sidecar-*"
        echo -e "    Estado:      sudo systemctl status ${APP_NAME}-sidecar-*"
    fi
else
    echo -e "    ${DIM}No se activaron sidecars.${NC}"
fi

echo ""
echo -e "  ${CYAN}${BOLD}COMANDOS RAPIDOS:${NC}"
echo -e "    fararoni --version        Verificar"
echo -e "    fararoni                  CLI interactivo"
echo -e "    fararoni --server         Servidor (REST + Gateway)"
echo -e "    ./fararoni-launcher.sh    Iniciar todo (core + sidecars)"

echo ""
echo -e "  ${GREEN}${BOLD}=================================================================="
echo -e "    Fararoni v${VERSION} instalado. Sistema operativo."
echo -e "  ==================================================================${NC}"
echo ""
