/**
 * FARARONI WhatsApp Sidecar (TypeScript)
 *
 * FASE 71 - Sidecar Node.js para comunicacion con WhatsApp via Baileys
 *
 * @author Eber Cruz
 * @since FASE 71
 */

import express, { Request, Response } from 'express';
import makeWASocket, {
  useMultiFileAuthState,
  DisconnectReason,
  downloadMediaMessage,
  WASocket,
  fetchLatestBaileysVersion,
  makeCacheableSignalKeyStore
} from '@whiskeysockets/baileys';
import axios from 'axios';
import pino from 'pino';
import qrcode from 'qrcode-terminal';

// =============================================================================
// CONFIGURACION
// =============================================================================

const CONFIG = {
  PORT: parseInt(process.env.PORT || '3000'),
  GATEWAY_URL: process.env.GATEWAY_URL || 'http://localhost:7071/gateway/v1/inbound',
  GATEWAY_TOKEN: process.env.FARARONI_GATEWAY_TOKEN || '',
  AUTH_DIR: process.env.AUTH_DIR || 'baileys_auth_info',
  CHANNEL_ID: 'whatsapp'
};

// Logger silencioso para Baileys
const logger = pino({ level: 'silent' });

// =============================================================================
// TIPOS
// =============================================================================

interface UniversalMessage {
  messageId: string;
  channelId: string;
  senderId: string;
  conversationId: string;
  type: 'TEXT' | 'AUDIO' | 'IMAGE' | 'FILE';
  textContent: string | null;
  mediaContent: string | null;
  mimeType: string | null;
  metadata: Record<string, string>;
  timestamp: string;
}

interface EgressPayload {
  recipient: string;
  message: string;
  conversationId?: string;
  type?: string;
}

// =============================================================================
// ESTADO
// =============================================================================

let sock: WASocket;
let connectionState: 'disconnected' | 'connecting' | 'connected' = 'disconnected';

// =============================================================================
// EXPRESS
// =============================================================================

const app = express();
app.use(express.json({ limit: '50mb' }));

app.post('/send', async (req: Request, res: Response) => {
  const { recipient, message }: EgressPayload = req.body;

  if (!recipient || !message) {
    return res.status(400).json({ error: 'Falta recipient o message' });
  }

  if (!sock || connectionState !== 'connected') {
    return res.status(503).json({ error: 'WhatsApp no conectado' });
  }

  try {
    const jid = recipient.includes('@') ? recipient : `${recipient}@s.whatsapp.net`;
    await sock.sendMessage(jid, { text: message });
    console.log(`[<<] Enviado a WhatsApp: ${jid.substring(0, 15)}...`);
    res.json({ status: 'sent', jid });
  } catch (error: any) {
    console.error('[EGRESS] Error:', error.message);
    res.status(500).json({ error: error.message });
  }
});

app.get('/health', (_req: Request, res: Response) => {
  res.json({
    status: connectionState === 'connected' ? 'healthy' : 'unhealthy',
    connection: connectionState,
    gatewayUrl: CONFIG.GATEWAY_URL
  });
});

// =============================================================================
// BAILEYS
// =============================================================================

async function connectToWhatsApp(): Promise<void> {
  const { state, saveCreds } = await useMultiFileAuthState(CONFIG.AUTH_DIR);
  const { version } = await fetchLatestBaileysVersion();

  console.log(`[BAILEYS] Conectando (version: ${version.join('.')})`);

  sock = makeWASocket({
    version,
    auth: {
      creds: state.creds,
      keys: makeCacheableSignalKeyStore(state.keys, logger)
    },
    logger,
    generateHighQualityLinkPreview: false,
    syncFullHistory: false
  });

  sock.ev.on('creds.update', saveCreds);

  // Manejar QR y conexion
  sock.ev.on('connection.update', (update) => {
    const { connection, lastDisconnect, qr } = update;

    // Mostrar QR en terminal cuando llegue
    if (qr) {
      console.log('\n[BAILEYS] Escanea este QR con WhatsApp:\n');
      qrcode.generate(qr, { small: true });
      console.log('\n[BAILEYS] Abre WhatsApp > Dispositivos Vinculados > Vincular Dispositivo\n');
    }

    if (connection === 'close') {
      connectionState = 'disconnected';
      const statusCode = (lastDisconnect?.error as any)?.output?.statusCode;
      const shouldReconnect = statusCode !== DisconnectReason.loggedOut;

      console.log(`[BAILEYS] Desconectado (codigo: ${statusCode})`);

      if (statusCode === DisconnectReason.loggedOut) {
        console.log('[BAILEYS] Sesion cerrada. Elimina baileys_auth_info y reinicia.');
      } else if (shouldReconnect) {
        console.log('[BAILEYS] Reconectando en 5s...');
        setTimeout(connectToWhatsApp, 5000);
      }
    } else if (connection === 'open') {
      connectionState = 'connected';
      console.log('[BAILEYS] WhatsApp conectado exitosamente!');
    }
  });

  // Mensajes entrantes
  sock.ev.on('messages.upsert', async (m) => {
    const msg = m.messages[0];

    if (!msg.message || msg.key.fromMe || msg.key.remoteJid === 'status@broadcast') {
      return;
    }

    const jid = msg.key.remoteJid!;
    const sender = jid.replace('@s.whatsapp.net', '');
    const pushName = msg.pushName || 'Usuario';
    const messageContent = msg.message;

    let textContent: string | null = null;
    let mediaContent: string | null = null;
    let mimeType: string | null = null;
    let messageType: UniversalMessage['type'] = 'TEXT';

    if (messageContent.conversation) {
      textContent = messageContent.conversation;
    } else if (messageContent.extendedTextMessage?.text) {
      textContent = messageContent.extendedTextMessage.text;
    } else if (messageContent.audioMessage) {
      messageType = 'AUDIO';
      mimeType = messageContent.audioMessage.mimetype || 'audio/ogg';

      try {
        const buffer = await downloadMediaMessage(msg, 'buffer', {}, {
          logger,
          reuploadRequest: sock.updateMediaMessage
        });
        mediaContent = (buffer as Buffer).toString('base64');
        console.log(`[>>] Audio recibido (${(buffer as Buffer).length} bytes)`);
      } catch (err: any) {
        console.error('[INGRESS] Error descargando audio:', err.message);
      }
    } else if (messageContent.imageMessage) {
      messageType = 'IMAGE';
      mimeType = messageContent.imageMessage.mimetype || 'image/jpeg';
      textContent = messageContent.imageMessage.caption || null;
    } else {
      return;
    }

    if (!textContent && !mediaContent) {
      return;
    }

    console.log(`[>>] De ${pushName} (${sender}): ${(textContent || '[AUDIO]').substring(0, 50)}`);

    const universalMessage: UniversalMessage = {
      messageId: msg.key.id!,
      channelId: CONFIG.CHANNEL_ID,
      senderId: sender,
      conversationId: jid,
      type: messageType,
      textContent,
      mediaContent,
      mimeType,
      metadata: {
        profileName: pushName,
        timestamp: msg.messageTimestamp?.toString() || ''
      },
      timestamp: new Date().toISOString()
    };

    await sendToGateway(universalMessage);
  });
}

async function sendToGateway(message: UniversalMessage): Promise<void> {
  try {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json'
    };

    if (CONFIG.GATEWAY_TOKEN) {
      headers['Authorization'] = `Bearer ${CONFIG.GATEWAY_TOKEN}`;
    }

    const response = await axios.post(CONFIG.GATEWAY_URL, message, {
      headers,
      timeout: 10000
    });

    console.log(`[>>] Enviado al Gateway: ${response.status}`);

  } catch (error: any) {
    if (error.code === 'ECONNREFUSED') {
      console.error('[INGRESS] Gateway no disponible (7071). Verifica que Java este corriendo.');
    } else {
      console.error('[INGRESS] Error:', error.message);
    }
  }
}

// =============================================================================
// MAIN
// =============================================================================

async function main(): Promise<void> {
  console.log('================================================');
  console.log('  FARARONI WhatsApp Sidecar - FASE 71');
  console.log('================================================');
  console.log(`Puerto Sidecar: ${CONFIG.PORT}`);
  console.log(`Gateway URL:    ${CONFIG.GATEWAY_URL}`);
  console.log('------------------------------------------------');

  app.listen(CONFIG.PORT, () => {
    console.log(`[HTTP] Servidor en puerto ${CONFIG.PORT}`);
  });

  await connectToWhatsApp();
}

main().catch((err) => {
  console.error('[FATAL]', err.message);
  process.exit(1);
});
