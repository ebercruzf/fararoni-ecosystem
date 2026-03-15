#!/bin/zsh
# =============================================================================
# Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
# Licensed under the Apache License, Version 2.0
# =============================================================================
#
# Fararoni Suite v1.0.0 - Generador de DMG (macOS)
# FASE 1008 - Empaquetado profesional "doble-clic"
#
# Se ejecuta automaticamente via Maven (exec-maven-plugin en fase install)
# o manualmente:
#   cd fararoni-distribution && ./create-dmg.sh
#
# Genera: target/Fararoni-Installer.dmg
#
# El DMG muestra solo 3 elementos al abrirse:
#   - "Fararoni Suite"              (carpeta con todo el bundle)
#   - "Instalar Fararoni.command"   (doble-clic para el wizard)
#   - "Applications"                (symlink para drag-and-drop)
#
# =============================================================================

set -e

VERSION="1.0.0"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_DIR="$SCRIPT_DIR/target/fararoni-v${VERSION}"
BUNDLE_ZIP="$SCRIPT_DIR/target/fararoni-v${VERSION}.zip"
DMG_STAGING="$SCRIPT_DIR/target/dmg-staging"
DMG_TEMP="$SCRIPT_DIR/target/fararoni-temp.dmg"
DMG_FINAL="$SCRIPT_DIR/target/Fararoni-Installer.dmg"
VOLUME_NAME="Fararoni Installer"
ICON_ICNS="$SCRIPT_DIR/src/main/resources/Fararoni.icns"

echo "=== Generador DMG - Fararoni v${VERSION} ==="

# -------------------------------------------------------------------------
# 1. Preparar la carpeta fuente
# -------------------------------------------------------------------------
if [[ -d "$SOURCE_DIR" ]]; then
    echo "[1/6] Carpeta fuente encontrada: $SOURCE_DIR"
elif [[ -f "$BUNDLE_ZIP" ]]; then
    echo "[1/6] Descomprimiendo bundle..."
    unzip -q "$BUNDLE_ZIP" -d "$SCRIPT_DIR/target"
else
    echo "ERROR: No se encontro ni carpeta ni ZIP del bundle."
    echo "Ejecuta primero: mvn package -DskipTests -pl fararoni-distribution"
    exit 1
fi

# Asegurar permisos ejecutables
chmod +x "$SOURCE_DIR/bin/fararoni-core" 2>/dev/null || true
chmod +x "$SOURCE_DIR/bin/fararoni.sh" 2>/dev/null || true
chmod +x "$SOURCE_DIR/installer.sh" 2>/dev/null || true
chmod +x "$SOURCE_DIR/Instalar Fararoni.command" 2>/dev/null || true

# -------------------------------------------------------------------------
# 2. Preparar staging limpio para el DMG
#    Solo 2 elementos visibles: carpeta "Fararoni Suite" y el .command
#    (Applications se agrega despues al montar)
# -------------------------------------------------------------------------
echo "[2/6] Preparando layout limpio del DMG..."
rm -rf "$DMG_STAGING"
mkdir -p "$DMG_STAGING"

# Copiar el .command a la raiz del DMG (siempre desde el source actualizado)
COMMAND_SRC="$SCRIPT_DIR/src/main/resources/Instalar Fararoni.command"
cp "$COMMAND_SRC" "$DMG_STAGING/Instalar Fararoni.command"
chmod +x "$DMG_STAGING/Instalar Fararoni.command"

# La carpeta del bundle completa como "Fararoni Suite"
cp -R "$SOURCE_DIR" "$DMG_STAGING/Fararoni Suite"

# -------------------------------------------------------------------------
# 3. Crear imagen DMG temporal (lectura/escritura)
# -------------------------------------------------------------------------
echo "[3/6] Creando imagen DMG..."
rm -f "$DMG_TEMP" "$DMG_FINAL"

hdiutil create \
    -srcfolder "$DMG_STAGING" \
    -volname "$VOLUME_NAME" \
    -fs HFS+ \
    -fsargs "-c c=64,a=16,e=16" \
    -format UDRW \
    "$DMG_TEMP"

# -------------------------------------------------------------------------
# 4. Montar y personalizar (symlink + icono)
# -------------------------------------------------------------------------
echo "[4/6] Personalizando DMG..."
# Desmontar si quedo montado de un intento previo
hdiutil detach "/Volumes/$VOLUME_NAME" 2>/dev/null || true
DEVICE=$(hdiutil attach -readwrite -noverify -nobrowse "$DMG_TEMP" | egrep '^/dev/' | sed 1q | awk '{print $1}')
sleep 2

# Symlink para "Arrastra a Aplicaciones"
ln -sf /Applications "/Volumes/$VOLUME_NAME/Applications" 2>/dev/null || true

# Icono personalizado del volumen DMG y carpeta Fararoni Suite
if [[ -f "$ICON_ICNS" ]]; then
    echo "    Aplicando icono al volumen..."
    cp "$ICON_ICNS" "/Volumes/$VOLUME_NAME/.VolumeIcon.icns"
    SetFile -a C "/Volumes/$VOLUME_NAME" 2>/dev/null || true

    # Icono de la carpeta "Fararoni Suite" via sips + osascript (DecomposedUnicodeResourceFork)
    if [[ -d "/Volumes/$VOLUME_NAME/Fararoni Suite" ]]; then
        echo "    Aplicando icono a carpeta Fararoni Suite..."
        # Convertir .icns a .png temporal para osascript
        TEMP_PNG="$SCRIPT_DIR/target/fararoni-icon-temp.png"
        sips -s format png "$ICON_ICNS" --out "$TEMP_PNG" --resampleWidth 512 >/dev/null 2>&1

        osascript <<APPLESCRIPT
use framework "AppKit"

set iconPath to POSIX file "$TEMP_PNG"
set folderPath to POSIX file "/Volumes/$VOLUME_NAME/Fararoni Suite"

set iconImage to (current application's NSImage's alloc()'s initWithContentsOfFile:(POSIX path of iconPath))
(current application's NSWorkspace's sharedWorkspace()'s setIcon:iconImage forFile:(POSIX path of folderPath) options:0)
APPLESCRIPT

        rm -f "$TEMP_PNG"
    fi
fi

# Desmontar
sync
hdiutil detach "$DEVICE"

# -------------------------------------------------------------------------
# 5. Comprimir DMG final (solo lectura, maximo nivel)
# -------------------------------------------------------------------------
echo "[5/6] Comprimiendo DMG final..."
hdiutil convert "$DMG_TEMP" \
    -format UDZO \
    -imagekey zlib-level=9 \
    -o "$DMG_FINAL"

rm -f "$DMG_TEMP"
rm -rf "$DMG_STAGING"

# -------------------------------------------------------------------------
# 6. Resultado
# -------------------------------------------------------------------------
DMG_SIZE=$(ls -lh "$DMG_FINAL" | awk '{print $5}')
echo "[6/6] Limpieza completada."
echo ""
echo "=== DMG GENERADO ==="
echo "  Archivo:  $DMG_FINAL"
echo "  Tamano:   $DMG_SIZE"
echo "  Volumen:  $VOLUME_NAME"
echo ""
echo "  Al abrir el DMG el usuario ve:"
echo "    - Fararoni Suite/             (carpeta con el bundle completo)"
echo "    - Instalar Fararoni.command   (doble-clic para el wizard)"
echo "    - Applications                (arrastra la carpeta aqui)"
echo ""
