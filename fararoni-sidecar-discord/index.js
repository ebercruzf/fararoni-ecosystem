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
const { Client, GatewayIntentBits, Partials } = require('discord.js');
const axios = require('axios');

// =============================================================================
// =============================================================================

const CONFIG = {
    // Puerto del servidor HTTP para Egress
    SIDECAR_PORT: process.env.SIDECAR_PORT || 3002,

    // URL del Gateway REST de Fararoni
    GATEWAY_URL: process.env.GATEWAY_URL || 'http://localhost:7071/gateway/v1/inbound',

    DISCORD_TOKEN: process.env.DISCORD_TOKEN || '',

    // Identificador del canal
    CHANNEL_ID: 'discord',

    // Timeout para requests al Gateway (ms)
    GATEWAY_TIMEOUT: 5000,

    // Permitir mensajes en servidores (requiere mencion)
    ALLOW_GUILDS: process.env.ALLOW_GUILDS === 'true',

    // Lista de usuarios permitidos (opcional, vacio = todos)
    ALLOWED_USERS: process.env.ALLOWED_USERS ? process.env.ALLOWED_USERS.split(',') : []
};

// =============================================================================
// =============================================================================

if (!CONFIG.DISCORD_TOKEN) {
    console.error('='.repeat(60));
    console.error('[ERROR] DISCORD_TOKEN no configurado.');
    console.error('');
    console.error('Pasos para obtener el token:');
    console.error('1. Ve a https://discord.com/developers/applications');
    console.error('2. Crea una "New Application"');
    console.error('3. Ve a la seccion "Bot" y crea un bot');
    console.error('4. IMPORTANTE: Activa "Message Content Intent"');
    console.error('5. Copia el token y ejecuta:');
    console.error('');
    console.error('   export DISCORD_TOKEN="tu_token_aqui"');
    console.error('');
    console.error('Para invitar el bot a tu servidor:');
    console.error('1. Ve a OAuth2 -> URL Generator');
    console.error('2. Selecciona scopes: bot');
    console.error('3. Selecciona permisos: Send Messages, Read Message History');
    console.error('4. Copia la URL generada y abrela en el navegador');
    console.error('');
    console.error('='.repeat(60));
    process.exit(1);
}

// =============================================================================
// =============================================================================

const app = express();
app.use(express.json());

// Cliente de Discord con los Intents necesarios
const client = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMessages,
        GatewayIntentBits.MessageContent,      // Requiere activarlo en el portal
        GatewayIntentBits.DirectMessages,
        GatewayIntentBits.DirectMessageTyping
    ],
    partials: [
        Partials.Channel,   // Necesario para DMs
        Partials.Message
    ]
});

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

client.on('messageCreate', async (msg) => {
    // =========================================================================
    // =========================================================================

    // 1. Ignorar mensajes de bots (incluyendo el propio)
    if (msg.author.bot) return;

    // 2. Determinar tipo de chat
    const isDM = !msg.guild;  // Si no hay guild, es DM
    const isGuild = !!msg.guild;

    // 3. En servidores, solo responder si mencionan al bot
    if (isGuild) {
        if (!CONFIG.ALLOW_GUILDS) {
            logger.debug(`[FILTRO] Mensaje de servidor ignorado (ALLOW_GUILDS=false)`);
            return;
        }

        // Verificar si el bot fue mencionado
        const botMentioned = msg.mentions.has(client.user.id);
        if (!botMentioned) {
            logger.debug(`[FILTRO] Mensaje de servidor sin mencion ignorado`);
            return;
        }
    }

    // 4. Lista de usuarios permitidos (si esta configurada)
    if (CONFIG.ALLOWED_USERS.length > 0) {
        if (!CONFIG.ALLOWED_USERS.includes(msg.author.id)) {
            logger.debug(`[FILTRO] Usuario ${msg.author.id} no esta en ALLOWED_USERS`);
            return;
        }
    }

    // =========================================================================
    // =========================================================================
    let textContent = msg.content;

    // Remover la mencion del bot si existe
    if (client.user) {
        textContent = textContent.replace(new RegExp(`<@!?${client.user.id}>`, 'g'), '').trim();
    }

    // Ignorar mensajes vacios
    if (!textContent || textContent.length === 0) {
        logger.debug(`[FILTRO] Mensaje vacio ignorado`);
        return;
    }

    // =========================================================================
    // =========================================================================

    // En Discord, usamos el channelId como "senderId" para responder al canal correcto
    const senderId = msg.channel.id;

    // Truncar texto para logs (max 50 chars)
    const textPreview = textContent.length > 50 ? textContent.substring(0, 50) + '...' : textContent;

    const universalMessage = {
        messageId: `dc-${msg.id}`,
        channelId: CONFIG.CHANNEL_ID,
        senderId: senderId,           // ID del canal para responder
        conversationId: msg.guild?.id || 'dm',
        type: 'TEXT',
        textContent: textContent,
        mediaContent: null,
        mimeType: null,
        metadata: {
            profileName: msg.author.username,
            globalName: msg.author.globalName || null,
            discriminator: msg.author.discriminator,
            userId: msg.author.id,    // ID del usuario real
            isDM: isDM,
            guildName: msg.guild?.name || null
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

        const source = isDM ? 'DM' : `#${msg.channel.name}`;
        logger.info(`[INGRESS] ${msg.author.username} (${source}) -> "${textPreview}" (HTTP ${response.status})`);

    } catch (error) {
        if (error.code === 'ECONNREFUSED') {
            logger.error(`[INGRESS] Gateway no disponible en ${CONFIG.GATEWAY_URL}`);
        } else if (error.code === 'ETIMEDOUT') {
            logger.error(`[INGRESS] Timeout conectando al Gateway`);
        } else {
            logger.error(`[INGRESS] Error: ${error.message}`);
        }
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

        // Obtener el canal por ID
        const channel = await client.channels.fetch(recipient);

        if (!channel) {
            logger.error(`[EGRESS] Canal ${recipient} no encontrado`);
            return res.status(404).json({
                error: 'CHANNEL_NOT_FOUND',
                recipient: recipient
            });
        }

        // Verificar que sea un canal de texto
        if (!channel.isTextBased()) {
            logger.error(`[EGRESS] Canal ${recipient} no es de texto`);
            return res.status(400).json({
                error: 'NOT_TEXT_CHANNEL',
                recipient: recipient
            });
        }

        // Enviar el mensaje
        await channel.send(message);

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

        if (error.code === 50001) {
            statusCode = 403;
            errorType = 'MISSING_ACCESS';
        } else if (error.code === 50013) {
            statusCode = 403;
            errorType = 'MISSING_PERMISSIONS';
        } else if (error.code === 10003) {
            statusCode = 404;
            errorType = 'UNKNOWN_CHANNEL';
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
        status: client.isReady() ? 'healthy' : 'degraded',
        service: 'fararoni-sidecar-discord',
        channel: CONFIG.CHANNEL_ID,
        port: CONFIG.SIDECAR_PORT,
        gatewayUrl: CONFIG.GATEWAY_URL,
        uptime: Math.floor(process.uptime()),
        timestamp: new Date().toISOString()
    };

    // Agregar info del bot si esta disponible
    if (client.user) {
        health.bot = {
            id: client.user.id,
            username: client.user.username,
            tag: client.user.tag
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
            tokenConfigured: !!CONFIG.DISCORD_TOKEN,
            allowGuilds: CONFIG.ALLOW_GUILDS,
            allowedUsersCount: CONFIG.ALLOWED_USERS.length
        },
        bot: client.user ? {
            id: client.user.id,
            tag: client.user.tag,
            ready: client.isReady()
        } : null,
        guilds: client.guilds?.cache.size || 0,
        environment: process.env.NODE_ENV || 'development',
        nodeVersion: process.version
    });
});

// =============================================================================
// =============================================================================

async function start() {
    console.log('');
    console.log('='.repeat(60));
    console.log('  FARARONI SIDECAR - DISCORD (FASE 71.6)');
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

        // 2. Iniciar cliente de Discord
        await client.login(CONFIG.DISCORD_TOKEN);

    } catch (error) {
        logger.error(`[STARTUP] Error fatal: ${error.message}`);

        if (error.message.includes('TOKEN_INVALID') || error.message.includes('invalid token')) {
            logger.error('[STARTUP] Token de Discord invalido.');
            logger.error('[STARTUP] Verifica en https://discord.com/developers/applications');
        }

        if (error.message.includes('Intents')) {
            logger.error('[STARTUP] Error de Intents. Asegurate de activar:');
            logger.error('[STARTUP] - "Message Content Intent" en el Developer Portal');
        }

        process.exit(1);
    }
}

// =============================================================================
// =============================================================================

client.once('ready', () => {
    logger.info(`[DISCORD] Bot conectado: ${client.user.tag}`);
    logger.info(`[DISCORD] Servidores: ${client.guilds.cache.size}`);
    logger.info(`[GATEWAY] Enviando a: ${CONFIG.GATEWAY_URL}`);

    if (CONFIG.ALLOW_GUILDS) {
        logger.info(`[SEGURIDAD] Servidores permitidos (requiere mencion)`);
    } else {
        logger.info(`[SEGURIDAD] Solo DMs (mensajes directos)`);
    }

    if (CONFIG.ALLOWED_USERS.length > 0) {
        logger.info(`[SEGURIDAD] ${CONFIG.ALLOWED_USERS.length} usuarios en allowlist`);
    }

    console.log('');
    logger.info('[READY] Sidecar listo para recibir mensajes');
    console.log('');
    console.log('-'.repeat(60));
});

// =============================================================================
// =============================================================================

function shutdown(signal) {
    console.log('');
    logger.info(`[SHUTDOWN] Recibido ${signal}, cerrando...`);
    client.destroy();
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
