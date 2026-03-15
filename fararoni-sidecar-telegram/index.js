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
const { Telegraf } = require('telegraf');
const axios = require('axios');

// =============================================================================
// =============================================================================

const CONFIG = {
    // Puerto del servidor HTTP para Egress
    SIDECAR_PORT: process.env.SIDECAR_PORT || 3001,

    // URL del Gateway REST de Fararoni
    GATEWAY_URL: process.env.GATEWAY_URL || 'http://localhost:7071/gateway/v1/inbound',

    TELEGRAM_TOKEN: process.env.TELEGRAM_TOKEN || '',

    // Identificador del canal
    CHANNEL_ID: 'telegram',

    // Timeout para requests al Gateway (ms)
    GATEWAY_TIMEOUT: 5000
};

// =============================================================================
// =============================================================================

if (!CONFIG.TELEGRAM_TOKEN) {
    console.error('='.repeat(60));
    console.error('[ERROR] TELEGRAM_TOKEN no configurado.');
    console.error('');
    console.error('Pasos para obtener el token:');
    console.error('1. Abre Telegram y busca @BotFather');
    console.error('2. Envia el comando /newbot');
    console.error('3. Sigue las instrucciones para crear tu bot');
    console.error('4. Copia el token y ejecuta:');
    console.error('');
    console.error('   export TELEGRAM_TOKEN="tu_token_aqui"');
    console.error('');
    console.error('='.repeat(60));
    process.exit(1);
}

// =============================================================================
// =============================================================================

const app = express();
app.use(express.json());

const bot = new Telegraf(CONFIG.TELEGRAM_TOKEN);

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

bot.on('text', async (ctx) => {
    const msg = ctx.message;
    const chat = msg.chat;
    const from = msg.from;

    // =========================================================================
    // =========================================================================
    // =========================================================================
    if (chat.type !== 'private') {
        logger.debug(`[FILTRO] Ignorando mensaje de ${chat.type}: ${chat.id}`);
        return;
    }

    // =========================================================================
    // =========================================================================
    if (from.is_bot) {
        logger.debug(`[FILTRO] Ignorando mensaje de bot: ${from.id}`);
        return;
    }

    // =========================================================================
    // =========================================================================
    const senderId = from.id.toString();
    const text = msg.text;

    // Truncar texto para logs (max 50 chars)
    const textPreview = text.length > 50 ? text.substring(0, 50) + '...' : text;

    const universalMessage = {
        messageId: `tg-${msg.message_id}`,
        channelId: CONFIG.CHANNEL_ID,
        senderId: senderId,
        conversationId: chat.id.toString(),
        type: 'TEXT',
        textContent: text,
        mediaContent: null,
        mimeType: null,
        metadata: {
            profileName: from.first_name || 'Telegram User',
            lastName: from.last_name || null,
            username: from.username || null,
            languageCode: from.language_code || null
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

        logger.info(`[INGRESS] ${senderId} -> "${textPreview}" (HTTP ${response.status})`);

    } catch (error) {
        if (error.code === 'ECONNREFUSED') {
            logger.error(`[INGRESS] Gateway no disponible en ${CONFIG.GATEWAY_URL}`);
        } else if (error.code === 'ETIMEDOUT') {
            logger.error(`[INGRESS] Timeout conectando al Gateway`);
        } else {
            logger.error(`[INGRESS] Error: ${error.message}`);
        }

        // Opcional: Notificar al usuario si hay error grave
        // await ctx.reply('Lo siento, el servicio no esta disponible en este momento.');
    }
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
            error: 'Campo requerido: recipient',
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

        await bot.telegram.sendMessage(recipient, message, {
            parse_mode: 'Markdown'  // Soporte basico de formato
        });

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

        if (error.response) {
            // Error de la API de Telegram
            statusCode = error.response.status || 500;
            errorType = error.response.description || 'TELEGRAM_API_ERROR';

            if (error.response.error_code === 403) {
                errorType = 'BOT_BLOCKED_BY_USER';
            } else if (error.response.error_code === 400) {
                errorType = 'BAD_REQUEST';
            }
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

app.get('/health', (req, res) => {
    const health = {
        status: 'healthy',
        service: 'fararoni-sidecar-tg',
        channel: CONFIG.CHANNEL_ID,
        port: CONFIG.SIDECAR_PORT,
        gatewayUrl: CONFIG.GATEWAY_URL,
        uptime: Math.floor(process.uptime()),
        timestamp: new Date().toISOString()
    };

    // Agregar info del bot si esta disponible
    if (bot.botInfo) {
        health.bot = {
            id: bot.botInfo.id,
            username: bot.botInfo.username,
            firstName: bot.botInfo.first_name
        };
    }

    res.json(health);
});

// =============================================================================
// =============================================================================

app.get('/status', (req, res) => {
    res.json({
        config: {
            port: CONFIG.SIDECAR_PORT,
            gatewayUrl: CONFIG.GATEWAY_URL,
            channelId: CONFIG.CHANNEL_ID,
            tokenConfigured: !!CONFIG.TELEGRAM_TOKEN
        },
        bot: bot.botInfo || null,
        environment: process.env.NODE_ENV || 'development',
        nodeVersion: process.version
    });
});

// =============================================================================
// =============================================================================

async function start() {
    console.log('');
    console.log('='.repeat(60));
    console.log('  FARARONI SIDECAR - TELEGRAM (FASE 71.5)');
    console.log('='.repeat(60));
    console.log('');

    try {
        // 1. Iniciar servidor HTTP para Egress
        await new Promise((resolve) => {
            app.listen(CONFIG.SIDECAR_PORT, () => {
                logger.info(`[HTTP] Servidor Egress en puerto ${CONFIG.SIDECAR_PORT}`);
                resolve();
            });
        });

        // 2. Iniciar bot de Telegram
        await bot.launch();

        logger.info(`[TELEGRAM] Bot conectado: @${bot.botInfo.username}`);
        logger.info(`[GATEWAY] Enviando a: ${CONFIG.GATEWAY_URL}`);
        logger.info(`[SEGURIDAD] Solo chats privados (grupos bloqueados)`);
        console.log('');
        logger.info('[READY] Sidecar listo para recibir mensajes');
        console.log('');
        console.log('-'.repeat(60));

    } catch (error) {
        logger.error(`[STARTUP] Error fatal: ${error.message}`);

        if (error.message.includes('401')) {
            logger.error('[STARTUP] Token de Telegram invalido. Verifica con @BotFather.');
        }

        process.exit(1);
    }
}

// =============================================================================
// =============================================================================

function shutdown(signal) {
    console.log('');
    logger.info(`[SHUTDOWN] Recibido ${signal}, cerrando...`);
    bot.stop(signal);
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
