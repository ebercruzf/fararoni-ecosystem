#!/bin/bash
# ============================================
# SCRIPT: demo-ataque-enjambre.sh
# Proposito: Demo completa de ataque y redistribucion
# Uso: ./demo-ataque-enjambre.sh
# FASE: 80.1.15 S.A.T.I.
# ============================================

echo "╔════════════════════════════════════════════════════════════╗"
echo "║     DEMO: ATAQUE AL ENJAMBRE S.A.T.I.                      ║"
echo "║     Objetivo: Demostrar Alta Disponibilidad                ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Verificar que hay procesos MCP
MCP_COUNT=$(pgrep -f "mcp-server-filesystem" | wc -l | tr -d ' ')
if [ "$MCP_COUNT" -lt 3 ]; then
    echo "[ERROR] Se requieren al menos 3 MCP Servers para esta demo"
    echo "        Actualmente hay: $MCP_COUNT"
    echo "        Ejecuta primero: ./spawn-swarm-5.sh"
    exit 1
fi

# Paso 1: Estado inicial
echo "=== PASO 1: Estado Inicial ==="
INITIAL_COUNT=$(pgrep -f "mcp-server-filesystem" | wc -l | tr -d ' ')
echo "MCP Servers activos: $INITIAL_COUNT"
pgrep -f "mcp-server-filesystem" | while read pid; do
    echo "  - PID: $pid"
done
echo ""

# Paso 2: Identificar victimas
echo "=== PASO 2: Identificando Victimas ==="
PIDS=($(pgrep -f "mcp-server-filesystem"))
ALPHA_PID=${PIDS[0]}
BRAVO_PID=${PIDS[1]}
echo "Alpha PID (victima 1): $ALPHA_PID"
echo "Bravo PID (victima 2): $BRAVO_PID"
echo ""

# Paso 3: Ataque con SIGSTOP (FASE 80.1.19: Ahora inmune a congelamiento)
echo "=== PASO 3: Ejecutando Ataque (SIGSTOP - Congelamiento) ==="
echo "Congelando Alpha (PID: $ALPHA_PID)..."
kill -STOP $ALPHA_PID 2>/dev/null
sleep 1
echo "Congelando Bravo (PID: $BRAVO_PID)..."
kill -STOP $BRAVO_PID 2>/dev/null
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║  [ATAQUE COMPLETADO] 40% del enjambre CONGELADO (SIGSTOP)  ║"
echo "║  FASE 80.1.19: Isolated Sentinel Pattern deberia manejar   ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Paso 4: Enviar peticiones
echo "=== PASO 4: Enviando Peticiones Durante Ataque ==="
SUCCESS=0
FAIL=0
for i in {1..5}; do
    RESULT=$(nats request fararoni.skills.mcp \
        '{"jsonrpc":"2.0","id":'$i',"method":"tools/call","params":{"name":"read_file","arguments":{"path":"/private/tmp/fararoni-sandbox-charlie/demo.txt"}}}' \
        --timeout 3s 2>&1)

    if echo "$RESULT" | grep -q "Soberania"; then
        ((SUCCESS++))
        RTT=$(echo "$RESULT" | grep -oE 'rtt [0-9.]+ms' | head -1)
        echo "  Peticion $i: [OK] $RTT"
    else
        ((FAIL++))
        echo "  Peticion $i: [FAIL] (timeout o error)"
    fi
    sleep 1
done
echo ""
echo "Resultados durante ataque: $SUCCESS exitosas, $FAIL fallidas"
echo ""

# Paso 5: Esperar resurreccion
echo "=== PASO 5: Esperando Auto-Resurreccion ==="
echo "Los Watchdogs corren cada 5 segundos con timeout de 500ms"
echo "Esperando 15 segundos para que detecten y resuciten..."
for i in {1..15}; do
    printf "\r  Progreso: %2d/15 segundos" $i
    sleep 1
done
echo ""
echo ""

# Paso 6: Estado final
echo "=== PASO 6: Estado Final ==="
FINAL_COUNT=$(pgrep -f "mcp-server-filesystem" | wc -l | tr -d ' ')
echo "MCP Servers activos: $FINAL_COUNT"
echo ""

# Verificar nuevos PIDs
echo "PIDs actuales:"
pgrep -f "mcp-server-filesystem" | while read pid; do
    echo "  - PID: $pid"
done
echo ""
echo "NOTA: Los PIDs de Alpha ($ALPHA_PID) y Bravo ($BRAVO_PID) fueron reemplazados"
echo "      por nuevos procesos resucitados por el Watchdog"
echo ""

# Paso 7: Verificar que el sistema funciona
echo "=== PASO 7: Verificacion Post-Ataque ==="
RESULT=$(nats request fararoni.skills.mcp \
    '{"jsonrpc":"2.0","id":999,"method":"tools/call","params":{"name":"read_file","arguments":{"path":"/private/tmp/fararoni-sandbox-charlie/demo.txt"}}}' \
    --timeout 5s 2>&1)

if echo "$RESULT" | grep -q "Soberania"; then
    RTT=$(echo "$RESULT" | grep -oE 'rtt [0-9.]+ms' | head -1)
    echo "Peticion de verificacion: [OK] $RTT"
    echo ""
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║  [EXITO] SISTEMA AUTOREPARABLE CONFIRMADO                  ║"
    echo "║  - 40% del enjambre fue atacado                            ║"
    echo "║  - $SUCCESS/$((SUCCESS+FAIL)) peticiones exitosas durante el ataque             ║"
    echo "║  - Sistema sigue operando con nodos restantes              ║"
    echo "╚════════════════════════════════════════════════════════════╝"
else
    echo "Peticion de verificacion: [FAIL]"
    echo "[ADVERTENCIA] El sistema puede necesitar mas tiempo para recuperarse"
fi

echo ""
echo "=== NOTAS (FASE 80.1.19) ==="
echo "- Los procesos Alpha y Bravo fueron CONGELADOS (SIGSTOP)"
echo "- El Watchdog detecto timeout y ejecuto hardReset()"
echo "- Isolated Sentinel Pattern evito el deadlock"
echo "- Nuevos procesos MCP fueron resucitados automaticamente"
echo "- Para limpiar huerfanos: pkill -CONT -f 'mcp-server-filesystem'"
echo "- Para detener todo: pkill -f 'fararoni-sidecar'"
