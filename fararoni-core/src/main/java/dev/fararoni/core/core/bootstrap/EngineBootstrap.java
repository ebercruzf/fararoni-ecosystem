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
package dev.fararoni.core.core.bootstrap;

import dev.fararoni.core.core.download.ModelDownloader;
import dev.fararoni.core.core.download.NativeEngineDownloader;
import dev.fararoni.core.core.download.DownloadProgress;
import dev.fararoni.core.core.download.DownloadState;
import dev.fararoni.core.core.llm.LocalLlmConfig;
import dev.fararoni.core.core.utils.NativeLoader;

import dev.fararoni.core.core.security.SecureConfigService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class EngineBootstrap {
    private static final Logger LOG = Logger.getLogger(EngineBootstrap.class.getName());

    private final NativeEngineDownloader engineDownloader;
    private final ModelDownloader modelDownloader;
    private final LocalLlmConfig localConfig;
    private final BufferedReader consoleReader;

    private boolean engineReady = false;
    private boolean modelReady = false;

    public EngineBootstrap() {
        this.engineDownloader = new NativeEngineDownloader();
        this.modelDownloader = new ModelDownloader();
        this.localConfig = LocalLlmConfig.fromEnvironment();
        this.consoleReader = new BufferedReader(new InputStreamReader(System.in));
    }

    public boolean ensureEngineReady(boolean isHeadless) {
        LOG.info("[BOOTSTRAP] Verificando componentes del motor neural...");

        String mode = SecureConfigService.getInstance().getProperty("mode");
        boolean isExternalOnly = "external".equals(mode);

        if (isExternalOnly) {
            LOG.info("[BOOTSTRAP] Modo external — componentes locales no requeridos");
            engineReady = NativeLoader.isNativeLibraryAvailable();
            modelReady = localConfig.isModelDownloaded();
            return true;
        }

        engineReady = NativeLoader.isNativeLibraryAvailable();
        modelReady = localConfig.isModelDownloaded();

        if (engineReady && modelReady) {
            LOG.info("[BOOTSTRAP] Sistema nominal - Motor y modelo disponibles");
            return true;
        }

        System.out.println("\n[BOOTSTRAP] Verificando componentes del motor neural...");

        if (!engineReady) {
            System.out.println("   [X] Motor nativo: NO INSTALADO");
        } else {
            System.out.println("   [OK] Motor nativo: OK");
        }

        if (!modelReady) {
            System.out.println("   [X] Modelo local (1.5B): NO DESCARGADO");
        } else {
            System.out.println("   [OK] Modelo local (1.5B): OK");
        }

        boolean proceedToDownload = false;

        if (isHeadless) {
            System.out.println("\n[WARN] [BOOTSTRAP] Componentes faltantes en modo Headless.");
            System.out.println("[AUTO-FIX] Iniciando descarga automatica de emergencia...");
            proceedToDownload = true;
        } else {
            proceedToDownload = askUserForDownload();
        }

        if (proceedToDownload) {
            return performDownload();
        } else {
            System.out.println("\n[STOP] Carga cancelada. Funcionara en modo 'Cliente Ligero' (Solo API Remota).");
            return false;
        }
    }

    public boolean isLocalReady() {
        return engineReady && modelReady;
    }

    public boolean checkStatus() {
        engineReady = NativeLoader.isNativeLibraryAvailable();
        modelReady = localConfig.isModelDownloaded();
        return engineReady && modelReady;
    }

    private boolean askUserForDownload() {
        try {
            System.out.println("\n[WARN] ATENCION: Faltan componentes para el modo local.");
            System.out.println("   Para funcionar localmente, se necesita descargar:");

            if (!engineReady) {
                System.out.println("   • Motor nativo (~5 MB)");
            }
            if (!modelReady) {
                System.out.println("   • Modelo Qwen 1.5B (~1.2 GB)");
            }

            System.out.print("\n   ¿Deseas iniciar la descarga ahora? [S/n]: ");
            System.out.flush();

            String input = consoleReader.readLine();
            if (input == null) {
                return false;
            }

            input = input.trim().toLowerCase();
            return input.isEmpty() || input.equals("s") || input.equals("si") ||
                   input.equals("y") || input.equals("yes");
        } catch (Exception e) {
            LOG.warning("[BOOTSTRAP] Error leyendo respuesta: " + e.getMessage());
            return false;
        }
    }

    private boolean performDownload() {
        boolean success = true;

        if (!engineReady) {
            success = downloadNativeEngine();
            if (!success) {
                return false;
            }
            engineReady = true;
        }

        if (!modelReady) {
            success = downloadLocalModel();
            if (!success) {
                return false;
            }
            modelReady = true;
        }

        System.out.println("\n[OK] [BOOTSTRAP] Motor instalado y verificado.");
        LOG.info("[BOOTSTRAP] Todos los componentes instalados correctamente");
        return true;
    }

    private boolean downloadNativeEngine() {
        System.out.println("\n[DOWNLOAD] Descargando motor nativo (~5 MB)...");

        try {
            boolean success = engineDownloader.download(progress -> {
                if (progress.state() == DownloadState.DOWNLOADING) {
                    System.out.printf("\r   Descargando: %.1f%% (%s / %s)",
                        progress.percentage(),
                        DownloadProgress.formatSize(progress.downloadedBytes()),
                        DownloadProgress.formatSize(progress.totalBytes()));
                    System.out.flush();
                } else if (progress.state() == DownloadState.COMPLETED) {
                    System.out.println("\r   [OK] Motor descargado                    ");
                }
            });

            if (success) {
                System.out.println("   [OK] Motor nativo instalado");
                return true;
            } else {
                System.out.println("   [ERROR] Error instalando motor");
                return false;
            }
        } catch (Exception e) {
            System.out.println("\n   [ERROR] Error: " + e.getMessage());
            LOG.severe("[BOOTSTRAP] Error descargando motor: " + e.getMessage());
            return false;
        }
    }

    private boolean downloadLocalModel() {
        System.out.println("\n[DOWNLOAD] Descargando modelo Qwen 1.5B (~1.2 GB)...");
        System.out.println("   (Esto puede tomar varios minutos)");

        try {
            boolean success = modelDownloader.download(progress -> {
                if (progress.state() == DownloadState.DOWNLOADING) {
                    System.out.printf("\r   Descargando: %.1f%% (%s / %s)",
                        progress.percentage(),
                        DownloadProgress.formatSize(progress.downloadedBytes()),
                        DownloadProgress.formatSize(progress.totalBytes()));
                    System.out.flush();
                } else if (progress.state() == DownloadState.COMPLETED) {
                    System.out.println("\r   [OK] Modelo descargado                    ");
                } else if (progress.state() == DownloadState.RETRYING) {
                    System.out.printf("\n   [RETRY] Reintentando (%d/%d): %s\n",
                        progress.attempt(), progress.maxAttempts(), progress.message());
                }
            });

            if (success) {
                System.out.println("   [OK] Modelo local instalado");
                return true;
            } else {
                System.out.println("   [ERROR] Error instalando modelo");
                return false;
            }
        } catch (Exception e) {
            System.out.println("\n   [ERROR] Error: " + e.getMessage());
            LOG.severe("[BOOTSTRAP] Error descargando modelo: " + e.getMessage());
            return false;
        }
    }
}
