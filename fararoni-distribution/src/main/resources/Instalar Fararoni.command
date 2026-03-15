#!/bin/zsh
# =============================================================================
# Fararoni Suite v1.0.0 - Lanzador macOS (doble-clic)
#
# Este archivo .command se ejecuta al hacer doble-clic en Finder.
# macOS abre Terminal automaticamente y ejecuta el installer.
#
# Si se ejecuta desde el DMG (read-only), copia automaticamente
# la suite a ~/Fararoni y ejecuta desde ahi.
# =============================================================================
cd "$(dirname "$0")"
LAUNCH_DIR="$(pwd)"

# ---- Localizar la carpeta del bundle ----
if [[ -f "./installer.sh" ]]; then
    SUITE_DIR="$LAUNCH_DIR"
elif [[ -f "./Fararoni Suite/installer.sh" ]]; then
    SUITE_DIR="$LAUNCH_DIR/Fararoni Suite"
else
    echo "ERROR: No se encontro installer.sh"
    echo "Asegurate de que la carpeta 'Fararoni Suite' este junto a este archivo."
    read -k 1 "?Presiona cualquier tecla para cerrar..."
    exit 1
fi

# ---- Detectar si estamos en un volumen read-only (DMG) ----
if ! touch "$SUITE_DIR/.write-test" 2>/dev/null; then
    echo ""
    echo "  Detectado: Ejecutando desde imagen de disco (DMG)."
    echo "  El instalador necesita un disco con escritura."
    echo ""

    DEST="$HOME/Fararoni"
    echo "  Copiando Fararoni Suite a: $DEST ..."
    rm -rf "$DEST"
    cp -R "$SUITE_DIR" "$DEST"
    chmod +x "$DEST/bin/fararoni-core" 2>/dev/null || true
    chmod +x "$DEST/bin/fararoni.sh" 2>/dev/null || true
    chmod +x "$DEST/bin"/sidecar-* 2>/dev/null || true
    chmod +x "$DEST/installer.sh" 2>/dev/null || true
    mkdir -p "$DEST/config"
    echo "  Copiado. Iniciando instalador..."
    echo ""

    cd "$DEST"
    exec ./installer.sh
else
    rm -f "$SUITE_DIR/.write-test"
    cd "$SUITE_DIR"
    exec ./installer.sh
fi
