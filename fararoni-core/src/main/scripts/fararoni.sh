#!/bin/bash
# =============================================================================
#
#  FARARONI - Launcher Script (GraalVM Native Image Wrapper)
#  Version: 1.1.0
#  Fecha: 2026-01-14
#
#  Copyright 2026 Eber Cruz Fararoni. All Rights Reserved.
#
# =============================================================================
#
#  PROPOSITO:
#  Este script actua como "portero" antes de ejecutar el binario nativo.
#  Prepara un entorno seguro y resuelve problemas de compatibilidad con
#  GraalVM native-image y macOS.
#
#  PATRON DE DISENO: "Launcher Pattern"
#  Usado por herramientas como npm, gradle, mvn, gcloud para verificar
#  el entorno antes de ejecutar el programa principal.
#
#  PROBLEMAS QUE RESUELVE:
#
#  1. GraalVM user.dir:
#     JLine (libreria de terminal) llama a System.getProperties() en su
#     constructor. En GraalVM native-image, esto dispara una inicializacion
#     lazy que puede fallar si el sistema POSIX no puede determinar el CWD.
#     SOLUCION: Pasamos -Duser.dir="$PWD" al binario.
#
#  2. macOS Native Library Permissions (Sequoia/Sonoma):
#     macOS bloquea la ejecucion de codigo dinamico (JNI/DLLs) desde
#     carpetas temporales del sistema (/var/folders/...) por seguridad.
#     Esto causa "Operation not permitted" en Jansi y DJL.
#     SOLUCION: Redirigir java.io.tmpdir a ~/.llm-fararoni/native-tmp/
#     que es una carpeta de usuario donde macOS permite la ejecucion.
#
# =============================================================================

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# -----------------------------------------------------------------------------
# Detectar el directorio actual de forma segura
# -----------------------------------------------------------------------------
detect_current_directory() {
    # Intentar obtener el directorio actual usando pwd
    CURRENT_DIR=$(pwd 2>/dev/null)

    if [ -z "$CURRENT_DIR" ]; then
        return 1
    fi

    # Verificar que el directorio existe y es accesible
    if [ ! -d "$CURRENT_DIR" ]; then
        return 1
    fi

    echo "$CURRENT_DIR"
    return 0
}

# -----------------------------------------------------------------------------
# Mostrar mensaje de error amigable y ofrecer solucion
# -----------------------------------------------------------------------------
handle_directory_error() {
    echo ""
    echo -e "${RED}${BOLD}ALERTA DE SISTEMA${NC}"
    echo ""
    echo -e "${YELLOW}No se puede determinar el directorio actual.${NC}"
    echo ""
    echo "Posibles causas:"
    echo "  1. La carpeta desde donde ejecutas fue eliminada."
    echo "  2. La Terminal no tiene permisos para esta ubicacion."
    echo "  3. Hay un problema de permisos en macOS (TCC/Full Disk Access)."
    echo ""

    # Verificar si tenemos acceso a HOME
    if [ -n "$HOME" ] && [ -d "$HOME" ]; then
        echo -e "${CYAN}Solucion disponible:${NC}"
        echo ""
        read -p "  ¿Deseas iniciar FARARONI desde tu carpeta HOME (~/)? (s/n): " confirm
        echo ""

        if [[ "$confirm" =~ ^[Ss]$ ]]; then
            cd "$HOME" || exit 1
            CURRENT_DIR="$HOME"
            echo -e "${GREEN}Cambiando contexto a: $HOME${NC}"
            echo ""
            return 0
        else
            echo -e "${RED}Operacion cancelada por el usuario.${NC}"
            echo ""
            echo "Alternativas:"
            echo "  1. Navega a una carpeta valida: cd ~"
            echo "  2. Verifica permisos de Terminal en: Preferencias del Sistema > Privacidad"
            echo ""
            exit 1
        fi
    else
        echo -e "${RED}No se puede acceder al directorio HOME.${NC}"
        echo "Verifica la configuracion de tu sistema."
        exit 1
    fi
}

# -----------------------------------------------------------------------------
# Preparar directorio seguro para librerias nativas (macOS fix)
# -----------------------------------------------------------------------------
prepare_native_lib_directory() {
    # Directorio seguro dentro del HOME del usuario
    # macOS permite la ejecucion de codigo desde carpetas de usuario
    NATIVE_TMP_DIR="$HOME/.llm-fararoni/native-tmp"

    # Crear directorio si no existe
    if [ ! -d "$NATIVE_TMP_DIR" ]; then
        mkdir -p "$NATIVE_TMP_DIR" 2>/dev/null
        if [ $? -ne 0 ]; then
            echo -e "${YELLOW}Advertencia: No se pudo crear $NATIVE_TMP_DIR${NC}"
            echo "Algunas funciones de colores/tokenizacion pueden no funcionar."
            return 1
        fi
    fi

    # Limpiar archivos temporales viejos (mas de 7 dias) para mantener higiene
    # Solo si el comando find esta disponible y funciona
    if command -v find &> /dev/null; then
        find "$NATIVE_TMP_DIR" -type f -mtime +7 -delete 2>/dev/null || true
    fi

    echo "$NATIVE_TMP_DIR"
    return 0
}

# -----------------------------------------------------------------------------
# Localizar el binario real
# -----------------------------------------------------------------------------
find_real_binary() {
    # El binario puede estar en diferentes ubicaciones dependiendo de como se instalo
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)"

    # Detectar directorio raiz del proyecto (para desarrollo)
    PROJECT_ROOT="${SCRIPT_DIR}/../../.."

    # Lista de posibles ubicaciones del binario real
    BINARY_LOCATIONS=(
        "${SCRIPT_DIR}/fararoni-core"                    # Mismo directorio que el launcher
        "/usr/local/bin/fararoni-core"                   # Instalacion global
        "$HOME/bin/fararoni-core"                        # Instalacion local
        "$HOME/.llm-fararoni/bin/fararoni-core"          # Instalacion en home de la app
        "${PROJECT_ROOT}/fararoni-core/target/fararoni-core"  # Desarrollo (desde scripts/)
        "${SCRIPT_DIR}/../../../target/fararoni-core"    # Desarrollo (relativo)
    )

    for location in "${BINARY_LOCATIONS[@]}"; do
        if [ -x "$location" ]; then
            echo "$location"
            return 0
        fi
    done

    return 1
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------
main() {
    # 1. Detectar directorio actual de forma segura
    CURRENT_DIR=$(detect_current_directory)

    if [ $? -ne 0 ] || [ -z "$CURRENT_DIR" ]; then
        # El directorio actual no se puede determinar - ofrecer solucion
        handle_directory_error
    fi

    # 2. Preparar directorio seguro para librerias nativas (macOS Sequoia/Sonoma fix)
    NATIVE_TMP=$(prepare_native_lib_directory)

    # 3. Localizar el binario real
    REAL_BINARY=$(find_real_binary)

    if [ -z "$REAL_BINARY" ]; then
        echo -e "${RED}Error: No se encontro el binario de FARARONI.${NC}"
        echo ""
        echo "El binario 'fararoni-core' no esta en ninguna ubicacion conocida."
        echo ""
        echo "Reinstala FARARONI ejecutando:"
        echo "  ./install.sh"
        echo ""
        exit 1
    fi

    # 4. Ejecutar el binario real con entorno preparado
    #
    # Propiedades inyectadas:
    #   -Duser.dir              : Resuelve "Could not determine current working directory"
    #   -Djava.io.tmpdir        : Redirige temporales a carpeta segura (evita /var/folders)
    #   -Dlibrary.jansi.path    : Indica a Jansi donde extraer libjansi.jnilib
    #   -Dai.djl.repository.cache: Indica a DJL donde cachear modelos y librerias nativas
    #
    if [ -n "$NATIVE_TMP" ]; then
        exec "$REAL_BINARY" \
            -Duser.dir="$CURRENT_DIR" \
            -Djava.io.tmpdir="$NATIVE_TMP" \
            -Dlibrary.jansi.path="$NATIVE_TMP" \
            -Dai.djl.repository.cache="$NATIVE_TMP/djl-cache" \
            "$@"
    else
        # Fallback: solo user.dir si no se pudo crear el directorio de natives
        exec "$REAL_BINARY" -Duser.dir="$CURRENT_DIR" "$@"
    fi
}

# Ejecutar
main "$@"
