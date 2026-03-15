#!/bin/bash
# ============================================
# SCRIPT: spawn-swarm-5.sh
# Proposito: Lanzar 5 Sidecars en paralelo
# Uso: ./spawn-swarm-5.sh
# FASE: 80.1.15 S.A.T.I.
# ============================================

JAR_PATH="target/fararoni-sidecar-mcp-1.0.0.jar"
NATS_URL="nats://localhost:4222"
LOG_DIR="/tmp/fararoni-swarm-logs"

# Verificar JAR existe
if [ ! -f "$JAR_PATH" ]; then
    echo "[ERROR] JAR no encontrado: $JAR_PATH"
    echo "Ejecuta primero: mvn clean package -DskipTests -q"
    exit 1
fi

# Verificar NATS corriendo
if ! pgrep -f nats-server > /dev/null; then
    echo "[ERROR] NATS Server no esta corriendo"
    echo "Ejecuta: nats-server &"
    exit 1
fi

mkdir -p $LOG_DIR

echo "╔════════════════════════════════════════════════════════════╗"
echo "║     DESPLEGANDO ENJAMBRE S.A.T.I. (5 NODOS)               ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Preparar sandboxes
echo "=== Preparando Sandboxes ==="
for i in alpha bravo charlie delta echo; do
    mkdir -p /tmp/fararoni-sandbox-$i
    echo "Sandbox $i - Soberania Fararoni" > /tmp/fararoni-sandbox-$i/demo.txt
    echo "  [OK] /tmp/fararoni-sandbox-$i"
done
echo ""

# Funcion para lanzar un Sidecar
launch_sidecar() {
    local ID=$1
    local SANDBOX="/tmp/fararoni-sandbox-$ID"
    local LOG="$LOG_DIR/sidecar-$ID.log"

    echo "Lanzando Sidecar [$ID]..."

    java --enable-preview \
        -Dfararoni.nats.url=$NATS_URL \
        -Dfararoni.sidecar.id=$ID \
        -Dmcp.exec.command="npx -y @modelcontextprotocol/server-filesystem $SANDBOX" \
        -jar $JAR_PATH \
        > $LOG 2>&1 &

    local PID=$!
    echo "  PID: $PID | Log: $LOG"
}

# Lanzar 5 Sidecars con delay
echo "=== Lanzando Sidecars ==="
launch_sidecar "alpha"
sleep 2
launch_sidecar "bravo"
sleep 2
launch_sidecar "charlie"
sleep 2
launch_sidecar "delta"
sleep 2
launch_sidecar "echo"

echo ""
echo "=== ENJAMBRE DESPLEGADO ==="
echo ""

# Esperar un momento para que se estabilicen
sleep 5

# Verificar
echo "=== VERIFICACION ==="
SIDECAR_COUNT=$(pgrep -f "fararoni-sidecar" | wc -l | tr -d ' ')
MCP_COUNT=$(pgrep -f "mcp-server-filesystem" | wc -l | tr -d ' ')

echo "Sidecars Java activos: $SIDECAR_COUNT"
echo "MCP Servers activos:   $MCP_COUNT"
echo ""
echo "Ver logs:     tail -f $LOG_DIR/*.log"
echo "Ver procesos: ps aux | grep fararoni-sidecar"
echo ""

if [ "$SIDECAR_COUNT" -eq 5 ] && [ "$MCP_COUNT" -eq 5 ]; then
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║  [EXITO] ENJAMBRE COMPLETAMENTE OPERACIONAL               ║"
    echo "╚════════════════════════════════════════════════════════════╝"
else
    echo "[ADVERTENCIA] Algunos nodos pueden no haber iniciado correctamente"
    echo "Verifica los logs en $LOG_DIR/"
fi
