/*
 * Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author Eber Cruz
 * @version 1.0.0
 */

require('dotenv').config();
const express = require('express');
const axios = require('axios');

// =============================================================================
// =============================================================================

const CONFIG = {
    // Puerto del servidor HTTP para Ingress/Egress
    SIDECAR_PORT: process.env.SIDECAR_PORT || 3003,

    // URL del Gateway REST de Fararoni
    GATEWAY_URL: process.env.GATEWAY_URL || 'http://localhost:7071/gateway/v1/inbound',

    // Configuracion de BlueBubbles
    BLUEBUBBLES_URL: process.env.BLUEBUBBLES_URL || 'http://localhost:1234',
    BLUEBUBBLES_PASSWORD: process.env.BLUEBUBBLES_PASSWORD || '',

    // Identificador del canal
    CHANNEL_ID: 'imessage',

    // Timeout para requests al Gateway (ms)
    GATEWAY_TIMEOUT: 5000,

    // Lista de remitentes permitidos (opcional, vacio = todos)
    ALLOWED_SENDERS: process.env.ALLOWED_SENDERS ? process.env.ALLOWED_SENDERS.split(',') : []
};

// =============================================================================
// =============================================================================

if (!CONFIG.BLUEBUBBLES_PASSWORD) {
    console.error('='.repeat(60));
    console.error('[ERROR] BLUEBUBBLES_PASSWORD no configurado.');
    console.error('');
    console.error('Pasos para obtener el password:');
    console.error('1. Abre BlueBubbles Server en tu Mac');
    console.error('2. Ve a la pestana "API & Webhooks"');
    console.error('3. Copia el "Server Password"');
    console.error('4. Configuralo en el archivo .env:');
    console.error('');
    console.error('   BLUEBUBBLES_PASSWORD=tu_password_aqui');
    console.error('');
    console.error('Tambien configura el Webhook en BlueBubbles:');
    console.error('   URL: http://localhost:3003/webhook');
    console.error('   Eventos: New Messages');
    console.error('');
    console.error('='.repeat(60));
    process.exit(1);
}

// =============================================================================
// =============================================================================

const app = express();
app.use(express.json());

// Logger con timestamps
const logger = {
    info: (msg) => console.log(`[INFO]  ${new Date().toISOString().slice(11, 19)} ${msg}`),
    warn: (msg) => console.log(`[WARN]  ${new Date().toISOString().slice(11, 19)} ${msg}`),
    error: (msg) => console.error(`[ERROR] ${new Date().toISOString().slice(11, 19)} ${msg}`),
    debug: (msg) => {
        if (process.env.DEBUG === 'true') {
            console.log(`[DEBUG] ${new Date().toISOString().slice(11, 19)} ${msg}`);
        }
    }
};

// =============================================================================
// =============================================================================

app.post('/webhook', async (req, res) => {
    const { type, data } = req.body;

    // =========================================================================
    // =========================================================================

    // 1. Solo procesamos mensajes nuevos
    if (type !== 'new-message') {
        logger.debug(`[FILTRO] Evento ignorado: ${type}`);
        return res.status(200).send('Evento ignorado');
    }

    // 2. Validar que hay datos
    if (!data) {
        logger.debug('[FILTRO] Webhook sin datos');
        return res.status(200).send('Sin datos');
    }

    // 3. Ignorar mensajes propios (enviados por ti)
    if (data.isFromMe) {
        logger.debug('[FILTRO] Mensaje propio ignorado');
        return res.status(200).send('Mensaje propio');
    }

    // 4. Extraer texto
    const textContent = data.text;
    if (!textContent || textContent.trim().length === 0) {
        logger.debug('[FILTRO] Mensaje sin texto ignorado');
        return res.status(200).send('Sin texto');
    }

    // 5. Extraer remitente
    const senderId = data.handle ? data.handle.address : data.chatGuid;
    if (!senderId) {
        logger.debug('[FILTRO] Mensaje sin remitente ignorado');
        return res.status(200).send('Sin remitente');
    }

    // 6. Lista de remitentes permitidos (si esta configurada)
    if (CONFIG.ALLOWED_SENDERS.length > 0) {
        const senderNormalized = senderId.replace(/[^0-9+]/g, '');
        const isAllowed = CONFIG.ALLOWED_SENDERS.some(allowed =>
            senderNormalized.includes(allowed.replace(/[^0-9+]/g, ''))
        );
        if (!isAllowed) {
            logger.debug(`[FILTRO] Remitente ${senderId} no esta en ALLOWED_SENDERS`);
            return res.status(200).send('Remitente no autorizado');
        }
    }

    // =========================================================================
    // =========================================================================

    // Truncar texto para logs (max 50 chars)
    const textPreview = textContent.length > 50 ? textContent.substring(0, 50) + '...' : textContent;

    // Extraer nombre del contacto si esta disponible
    const profileName = data.handle?.displayName || data.handle?.address || 'iMessage User';

    const universalMessage = {
        messageId: `imsg-${data.guid || Date.now()}`,
        channelId: CONFIG.CHANNEL_ID,
        senderId: senderId,
        conversationId: data.chatGuid || senderId,
        type: 'TEXT',
        textContent: textContent,
        mediaContent: null,
        mimeType: null,
        metadata: {
            profileName: profileName,
            isGroup: data.isGroup || false,
            chatGuid: data.chatGuid,
            service: data.service || 'iMessage'  // 'iMessage' o 'SMS'
        },
        timestamp: new Date().toISOString()
    };

    // =========================================================================
    // =========================================================================
    try {
        const response = await axios.post(CONFIG.GATEWAY_URL, universalMessage, {
            timeout: CONFIG.GATEWAY_TIMEOUT,
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const service = data.service === 'SMS' ? 'SMS' : 'iMsg';
        logger.info(`[INGRESS] ${profileName} (${service}) -> "${textPreview}" (HTTP ${response.status})`);

    } catch (error) {
        if (error.code === 'ECONNREFUSED') {
            logger.error(`[INGRESS] Gateway no disponible en ${CONFIG.GATEWAY_URL}`);
        } else if (error.code === 'ETIMEDOUT') {
            logger.error(`[INGRESS] Timeout conectando al Gateway`);
        } else {
            logger.error(`[INGRESS] Error: ${error.message}`);
        }
    }

    res.status(200).send('OK');
});

// =============================================================================
// =============================================================================

app.post('/send', async (req, res) => {
    const { recipient, message, type } = req.body;

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    if (!recipient) {
        logger.warn('[EGRESS] Peticion sin recipient');
        return res.status(400).json({
            error: 'Campo requerido: recipient (chatGuid o numero de telefono)',
            received: req.body
        });
    }

    if (!message) {
        logger.warn('[EGRESS] Peticion sin message');
        return res.status(400).json({
            error: 'Campo requerido: message',
            received: req.body
        });
    }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    try {
        // Truncar para logs
        const msgPreview = message.length > 50 ? message.substring(0, 50) + '...' : message;

        // Llamar a la API de BlueBubbles
        const bbResponse = await axios.post(
            `${CONFIG.BLUEBUBBLES_URL}/api/v1/message/text`,
            {
                chatGuid: recipient,
                text: message,
                method: 'apple-script'  // Metodo nativo de macOS
            },
            {
                params: {
                    password: CONFIG.BLUEBUBBLES_PASSWORD
                },
                timeout: 10000,
                headers: {
                    'Content-Type': 'application/json'
                }
            }
        );

        logger.info(`[EGRESS] ${recipient} <- "${msgPreview}"`);

        res.status(200).json({
            status: 'sent',
            recipient: recipient,
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        logger.error(`[EGRESS] Error enviando a ${recipient}: ${error.message}`);

        // Determinar codigo de error apropiado
        let statusCode = 500;
        let errorType = 'INTERNAL_ERROR';

        if (error.code === 'ECONNREFUSED') {
            statusCode = 503;
            errorType = 'BLUEBUBBLES_UNAVAILABLE';
            logger.error('[EGRESS] BlueBubbles Server no esta corriendo');
        } else if (error.response?.status === 401) {
            statusCode = 401;
            errorType = 'INVALID_PASSWORD';
            logger.error('[EGRESS] Password de BlueBubbles incorrecto');
        } else if (error.response?.status === 400) {
            statusCode = 400;
            errorType = 'INVALID_RECIPIENT';
        }

        res.status(statusCode).json({
            error: errorType,
            message: error.message,
            recipient: recipient
        });
    }
});

// =============================================================================
// =============================================================================

app.get('/health', async (req, res) => {
    let blueBubblesStatus = 'unknown';

    // Verificar conexion con BlueBubbles
    try {
        const bbHealth = await axios.get(
            `${CONFIG.BLUEBUBBLES_URL}/api/v1/server/info`,
            {
                params: { password: CONFIG.BLUEBUBBLES_PASSWORD },
                timeout: 3000
            }
        );
        blueBubblesStatus = bbHealth.status === 200 ? 'healthy' : 'degraded';
    } catch (error) {
        blueBubblesStatus = 'unavailable';
    }

    const health = {
        status: blueBubblesStatus === 'healthy' ? 'healthy' : 'degraded',
        service: 'fararoni-sidecar-imessage',
        channel: CONFIG.CHANNEL_ID,
        port: CONFIG.SIDECAR_PORT,
        gatewayUrl: CONFIG.GATEWAY_URL,
        blueBubbles: {
            url: CONFIG.BLUEBUBBLES_URL,
            status: blueBubblesStatus
        },
        uptime: Math.floor(process.uptime()),
        timestamp: new Date().toISOString()
    };

    res.json(health);
});

// =============================================================================
// =============================================================================

app.get('/status', async (req, res) => {
    let bbInfo = null;

    try {
        const bbResponse = await axios.get(
            `${CONFIG.BLUEBUBBLES_URL}/api/v1/server/info`,
            {
                params: { password: CONFIG.BLUEBUBBLES_PASSWORD },
                timeout: 3000
            }
        );
        bbInfo = bbResponse.data;
    } catch (error) {
        bbInfo = { error: error.message };
    }

    res.json({
        config: {
            port: CONFIG.SIDECAR_PORT,
            gatewayUrl: CONFIG.GATEWAY_URL,
            channelId: CONFIG.CHANNEL_ID,
            blueBubblesUrl: CONFIG.BLUEBUBBLES_URL,
            passwordConfigured: !!CONFIG.BLUEBUBBLES_PASSWORD,
            allowedSendersCount: CONFIG.ALLOWED_SENDERS.length
        },
        blueBubbles: bbInfo,
        environment: process.env.NODE_ENV || 'development',
        nodeVersion: process.version
    });
});

// =============================================================================
// =============================================================================

async function start() {
    console.log('');
    console.log('='.repeat(60));
    console.log('  FARARONI SIDECAR - iMESSAGE');
    console.log('  via BlueBubbles Server');
    console.log('='.repeat(60));
    console.log('');

    try {
        // Verificar conexion con BlueBubbles
        logger.info('[STARTUP] Verificando conexion con BlueBubbles...');

        try {
            const bbCheck = await axios.get(
                `${CONFIG.BLUEBUBBLES_URL}/api/v1/server/info`,
                {
                    params: { password: CONFIG.BLUEBUBBLES_PASSWORD },
                    timeout: 5000
                }
            );
            logger.info(`[STARTUP] BlueBubbles Server OK: v${bbCheck.data?.data?.server_version || 'unknown'}`);
        } catch (error) {
            if (error.code === 'ECONNREFUSED') {
                logger.warn(`[STARTUP] BlueBubbles no disponible en ${CONFIG.BLUEBUBBLES_URL}`);
                logger.warn('[STARTUP] Asegurate de que BlueBubbles Server este corriendo');
            } else if (error.response?.status === 401) {
                logger.error('[STARTUP] Password de BlueBubbles incorrecto');
                process.exit(1);
            } else {
                logger.warn(`[STARTUP] No se pudo verificar BlueBubbles: ${error.message}`);
            }
        }

        // Iniciar servidor HTTP
        await new Promise((resolve) => {
            app.listen(CONFIG.SIDECAR_PORT, () => {
                logger.info(`[HTTP] Servidor en puerto ${CONFIG.SIDECAR_PORT}`);
                logger.info(`[GATEWAY] Enviando a: ${CONFIG.GATEWAY_URL}`);
                logger.info(`[BLUEBUBBLES] Conectado a: ${CONFIG.BLUEBUBBLES_URL}`);

                if (CONFIG.ALLOWED_SENDERS.length > 0) {
                    logger.info(`[SEGURIDAD] ${CONFIG.ALLOWED_SENDERS.length} remitentes en allowlist`);
                }

                console.log('');
                logger.info('[READY] Sidecar listo para recibir mensajes de iMessage');
                console.log('');
                console.log('-'.repeat(60));
                console.log('');
                console.log('  Asegurate de configurar el Webhook en BlueBubbles:');
                console.log(`    URL: http://localhost:${CONFIG.SIDECAR_PORT}/webhook`);
                console.log('    Eventos: New Messages');
                console.log('');
                console.log('-'.repeat(60));
                resolve();
            });
        });

    } catch (error) {
        logger.error(`[STARTUP] Error fatal: ${error.message}`);
        process.exit(1);
    }
}

// =============================================================================
// =============================================================================

function shutdown(signal) {
    console.log('');
    logger.info(`[SHUTDOWN] Recibido ${signal}, cerrando...`);
    process.exit(0);
}

process.once('SIGINT', () => shutdown('SIGINT'));
process.once('SIGTERM', () => shutdown('SIGTERM'));

// Manejar errores no capturados
process.on('uncaughtException', (error) => {
    logger.error(`[FATAL] Excepcion no capturada: ${error.message}`);
    console.error(error.stack);
    process.exit(1);
});

process.on('unhandledRejection', (reason, promise) => {
    logger.error(`[FATAL] Promesa rechazada: ${reason}`);
    process.exit(1);
});

// =============================================================================
// =============================================================================

start();
