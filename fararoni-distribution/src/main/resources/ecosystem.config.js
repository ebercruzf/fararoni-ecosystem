/*
 * Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * Fararoni Suite v1.0.0 - PM2 Ecosystem
 *
 * Orquesta los sidecars Node.js del bundle distribuible.
 * El Core (GraalVM Native) se gestiona como servicio del sistema.
 *
 * Uso:
 *   pm2 start ecosystem.config.js
 *   pm2 start ecosystem.config.js --only "fararoni-sidecar-telegram,fararoni-sidecar-discord"
 *
 * @version 1.0.0
 * @since 1.0.0
 */
module.exports = {
  apps: [
    {
      name: 'fararoni-sidecar-telegram',
      script: './modules/fararoni-sidecar-telegram/index.js',
      cwd: __dirname,
      instances: 1,
      autorestart: true,
      watch: false,
      max_memory_restart: '150M',
      env: {
        NODE_ENV: 'production',
        SIDECAR_PORT: 3001,
        GATEWAY_URL: 'http://localhost:7071/gateway/v1/inbound'
      },
      log_date_format: 'YYYY-MM-DD HH:mm:ss',
      error_file: './logs/telegram-error.log',
      out_file: './logs/telegram-out.log',
      merge_logs: true,
      min_uptime: '10s',
      max_restarts: 10
    },
    {
      name: 'fararoni-sidecar-whatsapp',
      script: './modules/fararoni-sidecar-whatsapp/index.js',
      cwd: __dirname,
      instances: 1,
      autorestart: true,
      watch: false,
      max_memory_restart: '250M',
      env: {
        NODE_ENV: 'production',
        SIDECAR_PORT: 3000,
        GATEWAY_URL: 'http://localhost:7071/gateway/v1/inbound'
      },
      log_date_format: 'YYYY-MM-DD HH:mm:ss',
      error_file: './logs/whatsapp-error.log',
      out_file: './logs/whatsapp-out.log',
      merge_logs: true,
      min_uptime: '10s',
      max_restarts: 10
    },
    {
      name: 'fararoni-sidecar-discord',
      script: './modules/fararoni-sidecar-discord/index.js',
      cwd: __dirname,
      instances: 1,
      autorestart: true,
      watch: false,
      max_memory_restart: '150M',
      env: {
        NODE_ENV: 'production',
        SIDECAR_PORT: 3002,
        GATEWAY_URL: 'http://localhost:7071/gateway/v1/inbound'
      },
      log_date_format: 'YYYY-MM-DD HH:mm:ss',
      error_file: './logs/discord-error.log',
      out_file: './logs/discord-out.log',
      merge_logs: true,
      min_uptime: '10s',
      max_restarts: 10
    },
    {
      name: 'fararoni-sidecar-imessage',
      script: './modules/fararoni-sidecar-imessage/index.js',
      cwd: __dirname,
      instances: 1,
      autorestart: true,
      watch: false,
      max_memory_restart: '150M',
      env: {
        NODE_ENV: 'production',
        SIDECAR_PORT: 3003,
        GATEWAY_URL: 'http://localhost:7071/gateway/v1/inbound'
      },
      log_date_format: 'YYYY-MM-DD HH:mm:ss',
      error_file: './logs/imessage-error.log',
      out_file: './logs/imessage-out.log',
      merge_logs: true,
      min_uptime: '10s',
      max_restarts: 10
    }
  ]
};
