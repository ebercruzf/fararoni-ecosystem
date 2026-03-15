-- ================================================================
-- FASE 43.3.5 - CARGA INICIAL DE CANALES (SEMILLA)
-- Estos registros activan los adaptadores automaticamente al iniciar
-- ================================================================
--
-- NOTA: Estos son datos de EJEMPLO para desarrollo.
-- En produccion, reemplazar con valores reales.
--
-- ================================================================

-- 1. CANAL WHATSAPP PRINCIPAL (Cobalt)
-- Configuracion para tu numero personal o dedicado
INSERT INTO agency_channels (id, type, name, config_json, status)
VALUES (
    'wa_main_01',
    'WHATSAPP_COBALT',
    'WhatsApp Principal (Ventas)',
    '{
        "phoneNumberId": "5215512345678",
        "sessionPath": "sessions/whatsapp_main",
        "historySync": true,
        "devMode": false
    }',
    'ACTIVE'
);

-- 2. CANAL TELEGRAM (Bot de Soporte)
-- Reemplaza '123456:ABC...' con tu token real de @BotFather
INSERT INTO agency_channels (id, type, name, config_json, status)
VALUES (
    'tg_support_01',
    'TELEGRAM',
    'Fararoni Support Bot',
    '{
        "token": "123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11",
        "botName": "fararoni_support_bot",
        "adminUserIds": [123456789]
    }',
    'ACTIVE'
);

-- 3. WEBHOOK GENERICO (Integracion con Slack/Discord/CRM)
-- Este adaptador escucha JSONs simples en /api/webhooks/generic_alerts
INSERT INTO agency_channels (id, type, name, config_json, status)
VALUES (
    'webhook_slack_01',
    'GENERIC_WEBHOOK',
    'Alertas de Sistema (Slack)',
    '{
        "mapping_text": "event.text",
        "mapping_sender": "event.user",
        "secret": "s3cr3t_v3r1f1c4t10n_t0k3n",
        "response_url": "https://hooks.slack.com/services/..."
    }',
    'ACTIVE'
);

-- 4. CANAL DE PRUEBAS (Desactivado por defecto)
-- Util para tener configuraciones listas pero apagadas ('PAUSED')
INSERT INTO agency_channels (id, type, name, config_json, status)
VALUES (
    'wa_debug_01',
    'WHATSAPP_COBALT',
    'WhatsApp Debugger',
    '{
        "phoneNumberId": "debug_session",
        "devMode": true
    }',
    'PAUSED'
);
