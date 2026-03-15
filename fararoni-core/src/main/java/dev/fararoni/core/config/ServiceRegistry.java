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
package dev.fararoni.core.config;

import dev.fararoni.core.context.ContextManager;
import dev.fararoni.core.context.BasicContextManager;
import dev.fararoni.core.core.audit.AuditLogger;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.lifecycle.GracefulShutdownService;
import dev.fararoni.core.core.llm.LocalLlmConfig;
import dev.fararoni.core.core.llm.LocalLlmService;
import dev.fararoni.core.core.routing.EnterpriseRouter;
import dev.fararoni.core.core.session.FileAction;
import dev.fararoni.core.core.session.SessionContextPersistence;
import dev.fararoni.core.core.utils.NativeSilencer;
import dev.fararoni.core.core.workspace.WorkspaceManager;
import dev.fararoni.core.router.BasicRouterService;
import dev.fararoni.core.router.LlmRouterService;
import dev.fararoni.core.router.RouterService;
import dev.fararoni.core.tokenizer.FallbackTokenizer;
import dev.fararoni.core.tokenizer.RemoteTokenizer;

/**
 * @author Eber Cruz
 * @version 1.3.0
 */
public class ServiceRegistry {
    private static ContextManager contextManager;

    public static synchronized ContextManager getContextManager() {
        if (contextManager == null) {
            contextManager = createDefaultContextManager();
        }
        return contextManager;
    }

    private static ContextManager createDefaultContextManager() {
        var remoteTokenizer = new RemoteTokenizer(
            AppDefaults.DEFAULT_SERVER_URL,
            null,
            AppDefaults.DEFAULT_CONNECT_TIMEOUT_MS,
            AppDefaults.DEFAULT_READ_TIMEOUT_MS,
            AppDefaults.DEFAULT_MAX_RETRIES
        );

        var tokenizer = new FallbackTokenizer(remoteTokenizer);
        var manager = new BasicContextManager(tokenizer, AppDefaults.DEFAULT_CONTEXT_WINDOW);

        System.out.println("[ServiceRegistry] Using ContextManager: " + manager.getStrategyName());

        return manager;
    }

    public static void setContextManager(ContextManager cm) {
        contextManager = cm;
    }

    public static void reset() {
        contextManager = null;
        if (routerService != null) {
            routerService.shutdown();
            routerService = null;
        }
        routerDisabled = false;
        if (enterpriseRouter != null) {
            enterpriseRouter.close();
            enterpriseRouter = null;
        }
        if (sharedLocalLlm != null) {
            sharedLocalLlm.close();
            sharedLocalLlm = null;
        }
    }

    public static WorkspaceManager initializeWorkspace(String[] args) {
        if (WorkspaceManager.isInitialized()) {
            return WorkspaceManager.getInstance();
        }
        return WorkspaceManager.initialize(args);
    }

    public static WorkspaceManager getWorkspaceManager() {
        return WorkspaceManager.getInstance();
    }

    public static void resetAll() {
        contextManager = null;
        if (routerService != null) {
            routerService.shutdown();
            routerService = null;
        }
        routerDisabled = false;
        if (enterpriseRouter != null) {
            enterpriseRouter.close();
            enterpriseRouter = null;
        }
        if (sharedLocalLlm != null) {
            sharedLocalLlm.close();
            sharedLocalLlm = null;
        }
        WorkspaceManager.reset();
    }

    public static GracefulShutdownService getShutdownService() {
        return GracefulShutdownService.getInstance();
    }

    public static void registerForShutdown(String name, AutoCloseable resource) {
        GracefulShutdownService.getInstance().register(name, resource);
    }

    public static GracefulShutdownService.ShutdownResult shutdown() {
        return GracefulShutdownService.getInstance().shutdown();
    }

    private static volatile AuditLogger auditLogger;

    public static AuditLogger getAuditLogger() {
        if (auditLogger == null) {
            synchronized (ServiceRegistry.class) {
                if (auditLogger == null) {
                    auditLogger = AuditLogger.getInstance();
                    registerForShutdown("AuditLogger", auditLogger);
                }
            }
        }
        return auditLogger;
    }

    public static void audit(AuditLogger.Category category, String message, String details) {
        getAuditLogger().log(AuditLogger.Level.INFO, category, message, details);
    }

    private static volatile SessionContextPersistence sessionContext;

    public static SessionContextPersistence getSessionContext() {
        if (sessionContext == null) {
            synchronized (ServiceRegistry.class) {
                if (sessionContext == null) {
                    sessionContext = SessionContextPersistence.getInstance();
                    registerForShutdown("SessionContext", sessionContext);
                }
            }
        }
        return sessionContext;
    }

    public static void trackFile(String filepath, FileAction action) {
        getSessionContext().trackFile(filepath, action);
    }

    public static void recordError(Throwable exception) {
        getSessionContext().recordException(exception);
    }

    public static void clearSessionError() {
        getSessionContext().clearError();
    }

    public static String getSessionContextForPrompt() {
        return getSessionContext().getContextForPrompt();
    }

    private static volatile RouterService routerService;
    private static volatile boolean routerDisabled = false;

    public static RouterService getRouterService() {
        if (routerDisabled) {
            return null;
        }

        if (routerService == null) {
            synchronized (ServiceRegistry.class) {
                if (routerService == null) {
                    routerService = createDefaultRouterService();
                }
            }
        }
        return routerService;
    }

    protected static RouterService createDefaultRouterService() {
        try {
            LocalLlmConfig llmConfig = LocalLlmConfig.fromEnvironment();
            if (llmConfig.isModelDownloaded() && llmConfig.hasEnoughRam()) {
                NativeSilencer.silencePermanently();

                java.io.PrintStream originalOut = System.out;
                System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
                    @Override public void write(int b) {}
                }));
                try {
                    var llmRouter = new LlmRouterService(llmConfig);
                    System.setOut(originalOut);

                    if (llmRouter.isAvailable()) {
                        System.out.println("[ServiceRegistry] Using RouterService: " + llmRouter.getName());
                        registerForShutdown("RouterService", llmRouter::shutdown);
                        return llmRouter;
                    } else {
                        llmRouter.shutdown();
                    }
                } catch (Exception e) {
                    System.setOut(originalOut);
                }
            }
        } catch (Exception e) {
        }

        var router = new BasicRouterService();
        System.out.println("[ServiceRegistry] Using RouterService: " + router.getName() + " (Cliente Ligero)");
        registerForShutdown("RouterService", router::shutdown);
        return router;
    }

    public static void setRouterService(RouterService router) {
        if (routerService != null && routerService != router) {
            routerService.shutdown();
        }
        routerService = router;
        if (router != null) {
            System.out.println("[ServiceRegistry] Using RouterService: " + router.getName());
            registerForShutdown("RouterService", router::shutdown);
        }
    }

    public static void disableRouter() {
        routerDisabled = true;
        if (routerService != null) {
            routerService.shutdown();
            routerService = null;
        }
    }

    public static void enableRouter() {
        routerDisabled = false;
    }

    public static boolean isRouterEnabled() {
        return !routerDisabled && getRouterService() != null;
    }

    private static volatile EnterpriseRouter enterpriseRouter;
    private static volatile LocalLlmService sharedLocalLlm;

    public static EnterpriseRouter getEnterpriseRouter() {
        if (enterpriseRouter == null) {
            synchronized (ServiceRegistry.class) {
                if (enterpriseRouter == null) {
                    enterpriseRouter = createEnterpriseRouter();
                }
            }
        }
        return enterpriseRouter;
    }

    private static EnterpriseRouter createEnterpriseRouter() {
        LocalLlmService llm = getSharedLocalLlm();
        EnterpriseRouter router = new EnterpriseRouter(llm);
        registerForShutdown("EnterpriseRouter", router);
        System.out.println("[ServiceRegistry] Using EnterpriseRouter: " +
            (router.isFullyOperational() ? "3 capas (LLM disponible)" : "2 capas (sin LLM)"));
        return router;
    }

    public static LocalLlmService getSharedLocalLlm() {
        if (sharedLocalLlm == null) {
            synchronized (ServiceRegistry.class) {
                if (sharedLocalLlm == null) {
                    sharedLocalLlm = createSharedLocalLlm();
                }
            }
        }
        return sharedLocalLlm;
    }

    private static LocalLlmService createSharedLocalLlm() {
        try {
            LocalLlmConfig config = LocalLlmConfig.fromEnvironment();

            if (!config.isModelDownloaded()) {
                return null;
            }

            if (!config.hasEnoughRam()) {
                System.out.println("[WARN] Motor local no disponible: RAM insuficiente (" +
                    config.getAvailableRamMb() + "MB disponible, " +
                    config.minFreeRamMb() + "MB requerido).");
                System.out.println("    -> Redirigiendo todo el trafico al modelo Remoto.");
                return null;
            }

            LocalLlmService llm = new LocalLlmService(config);

            if (!llm.isNativeAvailable()) {
                System.out.println("[WARN] Motor local no disponible: libreria nativa ausente.");
                System.out.println("    -> Activando modo 'Direct-to-Remote' (solo modelo grande).");
                llm.close();
                return null;
            }

            registerForShutdown("SharedLocalLlm", llm::close);
            return llm;
        } catch (Exception e) {
            System.out.println("[WARN] Error iniciando motor local: " + e.getMessage());
            System.out.println("    -> Continuando con modelo Remoto.");
            return null;
        }
    }

    public static void setEnterpriseRouter(EnterpriseRouter router) {
        if (enterpriseRouter != null && enterpriseRouter != router) {
            enterpriseRouter.close();
        }
        enterpriseRouter = router;
    }

    public static boolean isEnterpriseRouterFullyOperational() {
        EnterpriseRouter router = getEnterpriseRouter();
        return router != null && router.isFullyOperational();
    }

    public static synchronized boolean reloadLocalLlm() {
        try {
            if (sharedLocalLlm != null) {
                try {
                    sharedLocalLlm.close();
                } catch (Exception ignored) {}
                sharedLocalLlm = null;
            }

            if (enterpriseRouter != null) {
                try {
                    enterpriseRouter.close();
                } catch (Exception ignored) {}
                enterpriseRouter = null;
            }

            sharedLocalLlm = createSharedLocalLlm();

            if (sharedLocalLlm != null) {
                enterpriseRouter = createEnterpriseRouter();
                System.out.println("[ServiceRegistry] Hot-reload: LocalLlmService reinicializado exitosamente");
                return true;
            } else {
                System.err.println("[WARN] Hot-reload: No se pudo cargar el modelo local");
                return false;
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Hot-reload fallido: " + e.getMessage());
            return false;
        }
    }

    public static boolean isLocalLlmAvailable() {
        return sharedLocalLlm != null;
    }
}
