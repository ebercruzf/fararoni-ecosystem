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
#  FARARONI — Build Sidecars as Node.js SEA (Single Executable Applications)
#
#  Compila los 4 sidecars Node.js como binarios nativos usando:
#    1. esbuild  → bundle de dependencias en un solo .js
#    2. node SEA → genera blob embebible
#    3. postject → inyecta blob en copia del binario de Node
#
#  Requisitos (one-time):
#    npm install -g esbuild
#    npm install -g postject
#
#  Uso:
#    ./build-sidecars.sh                # Compila los 4 sidecars
#    ./build-sidecars.sh telegram       # Compila solo telegram
#    ./build-sidecars.sh --clean        # Limpia target/sidecars
#
# =============================================================================

set -e

# =============================================================================
# CONFIGURACION
# =============================================================================
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/target/sidecars"
SEA_NODE_DIR="$SCRIPT_DIR/target/sea-node"
NODE_BIN=""  # Se resuelve en ensure_static_node

# Sidecars: nombre_dir:entry_point
declare -A SIDECARS=(
    [telegram]="fararoni-sidecar-telegram:index.js"
    [whatsapp]="fararoni-sidecar-whatsapp:index.js"
    [discord]="fararoni-sidecar-discord:index.js"
    [imessage]="fararoni-sidecar-imessage:index.js"
)

# esbuild externals por sidecar (native addons que no se pueden bundlear)
declare -A ESBUILD_EXTERNALS=(
    [telegram]=""
    [whatsapp]=""
    [discord]=""
    [imessage]=""
)

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

log_info()    { echo -e "${CYAN}[SEA]${NC} $1"; }
log_success() { echo -e "${GREEN}[OK]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

# =============================================================================
# ASEGURAR BINARIO ESTATICO DE NODE (requerido para SEA)
# =============================================================================
ensure_static_node() {
    local system_node="$(which node)"
    local node_size=$(wc -c < "$system_node" 2>/dev/null | tr -d ' ')

    # Si el binario del sistema es > 50MB, es estatico — usarlo directamente
    if (( node_size > 50000000 )); then
        NODE_BIN="$system_node"
        log_success "Node binario estatico detectado ($(ls -lh "$system_node" | awk '{print $5}'))"
        return
    fi

    # Homebrew/dynamically-linked node detectado — necesitamos el oficial
    log_warning "Node del sistema es dinamico ($(ls -lh "$system_node" | awk '{print $5}')). SEA requiere binario estatico."

    local node_version="$(node --version)"  # e.g. v25.3.0
    local arch="$(uname -m)"
    local platform="darwin"
    [[ "$(uname)" == "Linux" ]] && platform="linux"

    # Mapear arch
    local node_arch="arm64"
    [[ "$arch" == "x86_64" || "$arch" == "amd64" ]] && node_arch="x64"

    local tarball="node-${node_version}-${platform}-${node_arch}.tar.gz"
    local download_url="https://nodejs.org/dist/${node_version}/${tarball}"
    local cached_node="$SEA_NODE_DIR/node"

    if [[ -x "$cached_node" ]]; then
        local cached_size=$(wc -c < "$cached_node" | tr -d ' ')
        if (( cached_size > 50000000 )); then
            NODE_BIN="$cached_node"
            log_success "Node estatico en cache ($(ls -lh "$cached_node" | awk '{print $5}'))"
            return
        fi
    fi

    log_info "Descargando Node.js ${node_version} estatico para SEA..."
    mkdir -p "$SEA_NODE_DIR"

    curl -fSL --progress-bar "$download_url" -o "$SEA_NODE_DIR/$tarball" || {
        log_error "No se pudo descargar: $download_url"
        exit 1
    }

    # Extraer solo el binario node
    tar -xzf "$SEA_NODE_DIR/$tarball" -C "$SEA_NODE_DIR" --strip-components=2 "node-${node_version}-${platform}-${node_arch}/bin/node"
    rm -f "$SEA_NODE_DIR/$tarball"

    if [[ ! -x "$cached_node" ]]; then
        log_error "No se pudo extraer el binario de Node"
        exit 1
    fi

    NODE_BIN="$cached_node"
    log_success "Node estatico descargado ($(ls -lh "$cached_node" | awk '{print $5}'))"
}

# =============================================================================
# VERIFICAR REQUISITOS
# =============================================================================
check_requirements() {
    local missing=false

    if ! command -v node &> /dev/null; then
        log_error "node no encontrado. Instala Node.js >= 20"
        missing=true
    else
        local node_version=$(node --version | sed 's/v//' | cut -d. -f1)
        if (( node_version < 20 )); then
            log_error "Node.js >= 20 requerido (tienes v${node_version})"
            missing=true
        fi
    fi

    if ! command -v npx &> /dev/null; then
        log_error "npx no encontrado"
        missing=true
    fi

    # Verificar esbuild
    if ! npx esbuild --version &> /dev/null 2>&1; then
        log_error "esbuild no encontrado. Instala: npm install -g esbuild"
        missing=true
    fi

    # Verificar postject
    if ! npx postject --help &> /dev/null 2>&1; then
        log_warning "postject no encontrado globalmente, se usara npx postject"
    fi

    if [[ "$missing" == true ]]; then
        exit 1
    fi

    log_success "Requisitos OK: node $(node --version), esbuild $(npx esbuild --version 2>/dev/null)"
}

# =============================================================================
# COMPILAR UN SIDECAR
# =============================================================================
build_sidecar() {
    local name="$1"
    local config="${SIDECARS[$name]}"

    if [[ -z "$config" ]]; then
        log_error "Sidecar desconocido: $name"
        log_info "Disponibles: ${(k)SIDECARS}"
        return 1
    fi

    local dir_name="${config%%:*}"
    local entry="${config##*:}"
    local sidecar_dir="$PROJECT_ROOT/$dir_name"
    local dist_dir="$sidecar_dir/dist"
    local binary_name="sidecar-${name}"

    echo ""
    log_info "${BOLD}Compilando ${name}...${NC}"

    # Verificar que existe el directorio
    if [[ ! -d "$sidecar_dir" ]]; then
        log_error "Directorio no encontrado: $sidecar_dir"
        return 1
    fi

    # Verificar entry point
    if [[ ! -f "$sidecar_dir/$entry" ]]; then
        log_error "Entry point no encontrado: $sidecar_dir/$entry"
        return 1
    fi

    # Paso 1: npm install si falta node_modules
    if [[ ! -d "$sidecar_dir/node_modules" ]]; then
        log_info "  [1/7] npm install --production..."
        (cd "$sidecar_dir" && npm install --production --silent)
    else
        log_info "  [1/7] node_modules existente, saltando npm install"
    fi

    # Paso 2: esbuild bundle
    mkdir -p "$dist_dir"
    log_info "  [2/7] esbuild bundle..."

    local external_flags=""
    if [[ -n "${ESBUILD_EXTERNALS[$name]}" ]]; then
        for ext in ${(s:,:)ESBUILD_EXTERNALS[$name]}; do
            external_flags+=" --external:${ext}"
        done
    fi

    (cd "$sidecar_dir" && npx esbuild "$entry" \
        --bundle \
        --platform=node \
        --target=node20 \
        --format=cjs \
        --outfile=dist/bundle.js \
        --minify \
        --tree-shaking=true \
        ${=external_flags} \
        2>&1) || {
        log_error "esbuild fallo para $name"
        return 1
    }

    local bundle_size=$(ls -lh "$dist_dir/bundle.js" | awk '{print $5}')
    log_success "  Bundle: $bundle_size"

    # Paso 3: Generar SEA config
    log_info "  [3/7] Generando sea-config.json..."
    cat > "$dist_dir/sea-config.json" << EOF
{
    "main": "dist/bundle.js",
    "output": "dist/sea-prep.blob",
    "disableExperimentalSEAWarning": true
}
EOF

    # Paso 4: Generar blob SEA
    log_info "  [4/7] Generando SEA blob..."
    (cd "$sidecar_dir" && node --experimental-sea-config dist/sea-config.json 2>&1) || {
        log_error "node --experimental-sea-config fallo para $name"
        return 1
    }

    if [[ ! -f "$dist_dir/sea-prep.blob" ]]; then
        log_error "Blob no generado: $dist_dir/sea-prep.blob"
        return 1
    fi

    local blob_size=$(ls -lh "$dist_dir/sea-prep.blob" | awk '{print $5}')
    log_success "  Blob: $blob_size"

    # Paso 5: Copiar binario de Node
    log_info "  [5/7] Copiando binario de Node..."
    cp "$NODE_BIN" "$dist_dir/$binary_name"
    chmod u+w "$dist_dir/$binary_name"

    # Paso 6: Firmar (macOS)
    if [[ "$(uname)" == "Darwin" ]]; then
        log_info "  [6/7] Removiendo firma (macOS)..."
        codesign --remove-signature "$dist_dir/$binary_name" 2>/dev/null || true
    else
        log_info "  [6/7] Firma no aplica (Linux)"
    fi

    # Paso 7: Inyectar blob con postject
    log_info "  [7/7] Inyectando SEA blob con postject..."
    local postject_flags=""
    if [[ "$(uname)" == "Darwin" ]]; then
        postject_flags="--macho-segment-name NODE_SEA"
    fi

    npx postject "$dist_dir/$binary_name" \
        NODE_SEA_BLOB "$dist_dir/sea-prep.blob" \
        --sentinel-fuse NODE_SEA_FUSE_fce680ab2cc467b6e072b8b5df1996b2 \
        ${=postject_flags} \
        --overwrite 2>&1 || {
        log_error "postject fallo para $name"
        return 1
    }

    # Re-firmar ad-hoc (macOS)
    if [[ "$(uname)" == "Darwin" ]]; then
        codesign --sign - "$dist_dir/$binary_name" 2>/dev/null || true
    fi

    # Copiar a output
    mkdir -p "$OUTPUT_DIR"
    cp "$dist_dir/$binary_name" "$OUTPUT_DIR/$binary_name"
    chmod 755 "$OUTPUT_DIR/$binary_name"

    local final_size=$(ls -lh "$OUTPUT_DIR/$binary_name" | awk '{print $5}')
    log_success "  ${BOLD}$binary_name${NC} listo ($final_size) → $OUTPUT_DIR/$binary_name"

    # Limpiar intermedios
    rm -f "$dist_dir/sea-config.json" "$dist_dir/sea-prep.blob" "$dist_dir/bundle.js"
    rmdir "$dist_dir" 2>/dev/null || true
}

# =============================================================================
# CLEAN
# =============================================================================
clean() {
    log_info "Limpiando target/sidecars..."
    rm -rf "$OUTPUT_DIR"

    for name in "${(k)SIDECARS[@]}"; do
        local config="${SIDECARS[$name]}"
        local dir_name="${config%%:*}"
        rm -rf "$PROJECT_ROOT/$dir_name/dist"
    done

    log_success "Limpieza completada"
    exit 0
}

# =============================================================================
# MAIN
# =============================================================================
echo ""
echo -e "${CYAN}${BOLD}  ====================================================${NC}"
echo -e "${CYAN}${BOLD}    Fararoni — Node.js SEA Builder${NC}"
echo -e "${CYAN}${BOLD}  ====================================================${NC}"
echo ""

# Parsear argumentos
if [[ "$1" == "--clean" ]]; then
    clean
fi

check_requirements
ensure_static_node

start_time=$(date +%s)

if [[ -n "$1" && "$1" != "--"* ]]; then
    # Compilar sidecar especifico
    build_sidecar "$1"
else
    # Compilar todos
    for name in telegram whatsapp discord imessage; do
        build_sidecar "$name"
    done
fi

end_time=$(date +%s)
elapsed=$((end_time - start_time))

echo ""
echo -e "${GREEN}${BOLD}  ====================================================${NC}"
echo -e "${GREEN}  Build completado en ${elapsed}s${NC}"
echo ""

# Listar binarios generados
if [[ -d "$OUTPUT_DIR" ]]; then
    echo -e "  ${BOLD}Binarios generados:${NC}"
    for bin in "$OUTPUT_DIR"/sidecar-*; do
        if [[ -f "$bin" ]]; then
            local_size=$(ls -lh "$bin" | awk '{print $5}')
            echo -e "    $(basename "$bin")  ${local_size}"
        fi
    done
fi

echo ""
echo -e "${GREEN}${BOLD}  ====================================================${NC}"
echo ""
