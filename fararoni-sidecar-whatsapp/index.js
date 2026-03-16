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

import makeWASocket, {
  DisconnectReason,
  useMultiFileAuthState,
  makeCacheableSignalKeyStore,
  fetchLatestBaileysVersion
} from '@whiskeysockets/baileys';
import express from 'express';
import axios from 'axios';
import pino from 'pino';
import { Boom } from '@hapi/boom';
import { writeFileSync, existsSync, mkdirSync, readFileSync } from 'fs';
import { join } from 'path';
import qrcode from 'qrcode-terminal';

// =============================================================================
// CONFIGURACION
// =============================================================================

const CONFIG = {
  // Puerto del Sidecar (recibe respuestas del Gateway)
  SIDECAR_PORT: parseInt(process.env.SIDECAR_PORT || '3000'),

  // URL del Gateway Java (OmniChannelGatewayModule)
  GATEWAY_URL: process.env.GATEWAY_URL || 'http://localhost:7071/gateway/v1/inbound',

  // Token de autenticacion (opcional, debe coincidir con modules.yml)
  GATEWAY_TOKEN: process.env.FARARONI_GATEWAY_TOKEN || '',

  // Directorio para almacenar sesion de WhatsApp
  AUTH_DIR: process.env.AUTH_DIR || './baileys_auth_info',

  // ID del canal
  CHANNEL_ID: 'whatsapp',

  // Whitelist de grupos permitidos (separados por coma)
  // Ejemplo: "123456789@g.us,987654321@g.us"
  // Dejar vacio para ignorar TODOS los grupos
  ALLOWED_GROUPS: process.env.ALLOWED_GROUPS || '',

  // Nivel de log
  LOG_LEVEL: process.env.LOG_LEVEL || 'info'
};

// Logger con formato limpio (compatible con SEA — sin pino-pretty transport)
const logger = pino({
  level: CONFIG.LOG_LEVEL,
  formatters: {
    level(label) { return { level: label }; }
  },
  timestamp: () => `,"time":"${new Date().toISOString().slice(11, 19)}"`,
  base: null  // Omite pid y hostname
});

// =============================================================================
// ESTADO GLOBAL
// =============================================================================

let sock = null;
let connectionState = 'disconnected';

// =============================================================================
// EXPRESS SERVER (Recibe respuestas del Gateway)
// =============================================================================

const app = express();
app.use(express.json({ limit: '50mb' }));

/**
 * POST /send - Recibe respuestas del Gateway y las envia a WhatsApp
 *
 * Body esperado:
 * {
 *   "recipient": "+522291234567@s.whatsapp.net",
 *   "message": "Texto de respuesta",
 *   "conversationId": "conv-123",
 *   "type": "TEXT"
 * }
 */
app.post('/send', async (req, res) => {
  const { recipient, message, type, conversationId } = req.body;

  logger.info(`[EGRESS] Recibido - recipient: ${recipient}, conversationId: ${conversationId}`);

  // Usar conversationId si es un grupo, sino usar recipient
  const targetJid = (conversationId && conversationId.endsWith('@g.us'))
    ? conversationId
    : recipient;

  if (!targetJid || !message) {
    logger.warn('[EGRESS] Missing recipient or message');
    return res.status(400).json({ error: 'Missing recipient or message' });
  }

  if (!sock || connectionState !== 'connected') {
    logger.warn('[EGRESS] WhatsApp not connected');
    return res.status(503).json({ error: 'WhatsApp not connected' });
  }

  try {
    // Normalizar JID (agregar @s.whatsapp.net si no tiene, excepto grupos @g.us)
    let jid;
    if (targetJid.endsWith('@g.us')) {
      jid = targetJid; // Grupo, usar tal cual
    } else if (targetJid.includes('@')) {
      jid = targetJid; // Ya tiene formato
    } else {
      jid = `${targetJid}@s.whatsapp.net`; // Chat privado
    }

    logger.info(`[EGRESS] Enviando a ${jid.substring(0, 25)}...`);

    // Enviar mensaje via Baileys
    await sock.sendMessage(jid, { text: message });

    logger.info(`[EGRESS] Mensaje enviado exitosamente`);
    res.json({ success: true, jid });

  } catch (error) {
    logger.error(`[EGRESS] Error enviando: ${error.message}`);
    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /health - Health check
 */
app.get('/health', (req, res) => {
  res.json({
    status: connectionState === 'connected' ? 'healthy' : 'unhealthy',
    connection: connectionState,
    channelId: CONFIG.CHANNEL_ID,
    gatewayUrl: CONFIG.GATEWAY_URL
  });
});

/**
 * GET /qr - Devuelve QR actual si esta en proceso de conexion
 */
app.get('/qr', (req, res) => {
  const qrPath = join(CONFIG.AUTH_DIR, 'qr.txt');
  if (existsSync(qrPath)) {
    const qr = readFileSync(qrPath, 'utf8');
    res.json({ qr, status: connectionState });
  } else {
    res.json({ qr: null, status: connectionState });
  }
});

// =============================================================================
// BAILEYS WHATSAPP CLIENT
// =============================================================================

/**
 * Inicia la conexion a WhatsApp via Baileys
 */
async function connectToWhatsApp() {
  // Crear directorio de autenticacion si no existe
  if (!existsSync(CONFIG.AUTH_DIR)) {
    mkdirSync(CONFIG.AUTH_DIR, { recursive: true });
  }

  // Cargar estado de autenticacion
  const { state, saveCreds } = await useMultiFileAuthState(CONFIG.AUTH_DIR);

  // Obtener version mas reciente de Baileys
  const { version } = await fetchLatestBaileysVersion();

  logger.info(`[BAILEYS] Iniciando conexion (version: ${version.join('.')})`);

  // Crear socket de WhatsApp
  sock = makeWASocket({
    version,
    auth: {
      creds: state.creds,
      keys: makeCacheableSignalKeyStore(state.keys, logger)
    },
    // printQRInTerminal removido (deprecado en Baileys) - usamos qrcode-terminal manualmente
    logger: logger.child({ level: 'warn' }), // Menos verbose
    generateHighQualityLinkPreview: false,
    syncFullHistory: false
  });

  // Guardar credenciales cuando cambien
  sock.ev.on('creds.update', saveCreds);

  // Manejar eventos de conexion
  sock.ev.on('connection.update', async (update) => {
    const { connection, lastDisconnect, qr } = update;

    // Mostrar QR en terminal (qrcode-terminal reemplaza printQRInTerminal deprecado)
    if (qr) {
      console.log('\n');
      qrcode.generate(qr, { small: true });
      console.log('\n👆 Escanea este QR con WhatsApp\n');
      writeFileSync(join(CONFIG.AUTH_DIR, 'qr.txt'), qr);
      logger.info('[BAILEYS] QR Code generado - Escanea con WhatsApp');
    }

    if (connection === 'close') {
      connectionState = 'disconnected';
      const reason = new Boom(lastDisconnect?.error)?.output?.statusCode;

      if (reason === DisconnectReason.loggedOut) {
        logger.warn('[BAILEYS] Sesion cerrada. Elimina baileys_auth_info y reinicia.');
      } else {
        logger.warn(`[BAILEYS] Desconectado (${reason}). Reconectando en 5s...`);
        setTimeout(connectToWhatsApp, 5000);
      }
    } else if (connection === 'open') {
      connectionState = 'connected';
      logger.info('[BAILEYS] WhatsApp conectado exitosamente');

      // Limpiar archivo QR
      const qrPath = join(CONFIG.AUTH_DIR, 'qr.txt');
      if (existsSync(qrPath)) {
        writeFileSync(qrPath, '');
      }
    }
  });

  // Manejar mensajes entrantes
  sock.ev.on('messages.upsert', async (m) => {
    const msg = m.messages[0];

    // Ignorar mensajes propios, de estado y de GRUPOS
    // @s.whatsapp.net = chat privado (1:1)
    // @g.us = grupo (ignorar para evitar que el bot responda en grupos)
    // status@broadcast = estados de WhatsApp
    if (!msg.message || msg.key.fromMe || msg.key.remoteJid === 'status@broadcast') {
      return;
    }

    // Filtro de grupos con whitelist
    if (msg.key.remoteJid.endsWith('@g.us')) {
      const groupJid = msg.key.remoteJid;
      const allowedGroups = CONFIG.ALLOWED_GROUPS.split(',').map(g => g.trim()).filter(g => g);

      // Si no hay whitelist, ignorar todos los grupos
      if (allowedGroups.length === 0) {
        logger.debug(`[INGRESS] Ignorando grupo (sin whitelist): ${groupJid}`);
        return;
      }

      // Si el grupo NO está en whitelist, ignorar
      if (!allowedGroups.includes(groupJid)) {
        logger.info(`[INGRESS] Grupo no permitido: ${groupJid} - Agrega a ALLOWED_GROUPS si quieres habilitarlo`);
        return;
      }

      logger.info(`[INGRESS] Mensaje de grupo PERMITIDO: ${groupJid}`);
    }

    // Extraer informacion del mensaje
    const jid = msg.key.remoteJid;
    const isGroup = jid.endsWith('@g.us');
    // En grupos, el sender real está en participant; en chats privados, es el jid
    const sender = isGroup
      ? (msg.key.participant || jid).replace('@s.whatsapp.net', '')
      : jid.replace('@s.whatsapp.net', '');
    const pushName = msg.pushName || 'Usuario';

    // Extraer contenido segun tipo
    let textContent = null;
    let mediaContent = null;
    let mimeType = null;
    let messageType = 'TEXT';

    const messageContent = msg.message;

    if (messageContent.conversation) {
      // Mensaje de texto simple
      textContent = messageContent.conversation;
    } else if (messageContent.extendedTextMessage?.text) {
      // Mensaje de texto con formato/enlaces
      textContent = messageContent.extendedTextMessage.text;
    } else if (messageContent.audioMessage) {
      // Nota de voz
      messageType = 'AUDIO';
      mimeType = messageContent.audioMessage.mimetype || 'audio/ogg';

      // Descargar audio y convertir a base64
      try {
        const stream = await downloadMediaMessage(msg, 'buffer', {}, {
          logger,
          reuploadRequest: sock.updateMediaMessage
        });
        mediaContent = stream.toString('base64');
        logger.info(`[INGRESS] Audio recibido (${stream.length} bytes)`);
      } catch (err) {
        logger.error(`[INGRESS] Error descargando audio: ${err.message}`);
      }
    } else if (messageContent.imageMessage) {
      // Imagen
      messageType = 'IMAGE';
      mimeType = messageContent.imageMessage.mimetype || 'image/jpeg';
      textContent = messageContent.imageMessage.caption || null;

      // Opcionalmente descargar imagen
      // (por ahora solo capturamos el caption)
    } else {
      // Otro tipo de mensaje no soportado
      logger.debug(`[INGRESS] Tipo de mensaje no soportado: ${Object.keys(messageContent).join(', ')}`);
      return;
    }

    // Solo procesar si hay contenido
    if (!textContent && !mediaContent) {
      logger.debug('[INGRESS] Mensaje sin contenido procesable');
      return;
    }

    logger.info(`[INGRESS] Mensaje de ${pushName} (${sender}): ${(textContent || '[AUDIO]').substring(0, 50)}`);

    // Construir UniversalMessage
    const universalMessage = {
      messageId: msg.key.id,
      channelId: CONFIG.CHANNEL_ID,
      senderId: sender,
      conversationId: jid,
      type: messageType,
      textContent: textContent,
      mediaContent: mediaContent,
      mimeType: mimeType,
      metadata: {
        profileName: pushName,
        timestamp: msg.messageTimestamp?.toString()
      },
      timestamp: new Date().toISOString()
    };

    // Enviar al Gateway Java
    await sendToGateway(universalMessage);
  });

  return sock;
}

// Importar funcion para descargar media
import { downloadMediaMessage } from '@whiskeysockets/baileys';

/**
 * Envia un UniversalMessage al Gateway Java
 */
async function sendToGateway(message) {
  try {
    const headers = {
      'Content-Type': 'application/json'
    };

    // Agregar token si esta configurado
    if (CONFIG.GATEWAY_TOKEN) {
      headers['Authorization'] = `Bearer ${CONFIG.GATEWAY_TOKEN}`;
    }

    const response = await axios.post(CONFIG.GATEWAY_URL, message, {
      headers,
      timeout: 10000
    });

    logger.info(`[INGRESS] Enviado al Gateway: ${response.status} - ${response.data?.requestId || 'OK'}`);

  } catch (error) {
    if (error.response) {
      logger.error(`[INGRESS] Gateway error: ${error.response.status} - ${error.response.data?.error || 'Unknown'}`);
    } else if (error.code === 'ECONNREFUSED') {
      logger.error('[INGRESS] Gateway no disponible (ECONNREFUSED). Verifica que Java este corriendo.');
    } else {
      logger.error(`[INGRESS] Error enviando al Gateway: ${error.message}`);
    }
  }
}

// =============================================================================
// MAIN
// =============================================================================

async function main() {
  logger.info('================================================');
  logger.info('  FARARONI WhatsApp Sidecar');
  logger.info('================================================');
  logger.info(`Puerto Sidecar: ${CONFIG.SIDECAR_PORT}`);
  logger.info(`Gateway URL:    ${CONFIG.GATEWAY_URL}`);
  logger.info(`Auth Dir:       ${CONFIG.AUTH_DIR}`);
  logger.info('------------------------------------------------');

  // Iniciar servidor Express (para recibir respuestas del Gateway)
  app.listen(CONFIG.SIDECAR_PORT, () => {
    logger.info(`[HTTP] Servidor escuchando en puerto ${CONFIG.SIDECAR_PORT}`);
    logger.info(`[HTTP] POST /send - Recibe respuestas del Gateway`);
    logger.info(`[HTTP] GET /health - Health check`);
  });

  // Conectar a WhatsApp
  await connectToWhatsApp();
}

// Manejar errores no capturados
process.on('uncaughtException', (err) => {
  logger.error(`[FATAL] Uncaught Exception: ${err.message}`);
  logger.error(err.stack);
});

process.on('unhandledRejection', (reason, promise) => {
  logger.error(`[FATAL] Unhandled Rejection: ${reason}`);
});

// Iniciar
main().catch((err) => {
  logger.error(`[FATAL] Error iniciando: ${err.message}`);
  process.exit(1);
});
