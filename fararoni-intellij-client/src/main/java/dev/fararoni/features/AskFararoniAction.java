/*
 * Copyright 2026 Eber Cruz Fararoni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ---------------------------------------------------------------------------
 *
 * Copyright 2026 Eber Cruz Fararoni
 *
 * Licenciado bajo la Licencia Apache, Version 2.0 (la "Licencia");
 * no puede usar este archivo excepto en cumplimiento con la Licencia.
 * Puede obtener una copia de la Licencia en
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * A menos que lo exija la ley aplicable o se acuerde por escrito, el software
 * distribuido bajo la Licencia se distribuye "TAL CUAL", SIN GARANTIAS NI
 * CONDICIONES DE NINGUN TIPO, ya sean expresas o implicitas.
 * Consulte la Licencia para conocer el lenguaje especifico que rige los
 * permisos y las limitaciones de la misma.
 */
package dev.fararoni.features;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import dev.fararoni.features.MyIcons;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class AskFararoniAction extends AnAction {

    private static final Logger LOG = Logger.getLogger(AskFararoniAction.class.getName());

    private static final String NOTIFICATION_GROUP = "Fararoni Notifications";
    private static final int MAX_CODE_PREVIEW_LENGTH = 200;

    public AskFararoniAction() {
        super("Preguntar a Fararoni", "Enviar código seleccionado a Fararoni AI", MyIcons.FararoniLogo);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || editor == null) {
            Messages.showWarningDialog("No hay editor activo.", "Fararoni");
            return;
        }

        // Obtener texto seleccionado o línea actual
        String selection = editor.getSelectionModel().getSelectedText();
        final int cursorLine = editor.getCaretModel().getLogicalPosition().line + 1;
        final String filePath = psiFile != null ? psiFile.getVirtualFile().getPath() : "unknown";
        final String fileName = psiFile != null ? psiFile.getName() : "unknown";

        // Si no hay selección, tomar la línea actual
        if (selection == null || selection.isBlank()) {
            int lineStart = editor.getDocument().getLineStartOffset(cursorLine - 1);
            int lineEnd = editor.getDocument().getLineEndOffset(cursorLine - 1);
            selection = editor.getDocument().getText().substring(lineStart, lineEnd);
        }

        if (selection.isBlank()) {
            Messages.showWarningDialog("Selecciona código o posiciona el cursor en una línea.", "Fararoni");
            return;
        }

        final String selectedText = selection;

        // Mostrar diálogo de input para el prompt
        String prompt = Messages.showInputDialog(
            project,
            "Código seleccionado:\n" + truncate(selectedText, MAX_CODE_PREVIEW_LENGTH) + "\n\n¿Qué quieres que haga Fararoni?",
            "Preguntar a Fararoni",
            MyIcons.FararoniLogo,
            "Explica este código",
            null
        );

        if (prompt == null || prompt.isBlank()) return;

        // Construir contexto completo
        String fullContext = String.format(
            "Archivo: %s\nLínea: %d\nCódigo:\n```\n%s\n```\n\nPregunta: %s",
            filePath, cursorLine, selectedText, prompt
        );

        // Verificar que el CallbackServer está corriendo
        FararoniCallbackServer callbackServer = FararoniCallbackServer.getInstance(project);
        if (callbackServer == null || !callbackServer.isRunning()) {
            // Intentar iniciar el callback server
            if (callbackServer != null) {
                try {
                    callbackServer.start();
                } catch (Exception ex) {
                    LOG.warning("[AskFararoniAction] Could not start CallbackServer: " + ex.getMessage());
                }
            }
        }

        // Mostrar balloon de "procesando"
        showProcessingBalloon(editor, "Consultando a Fararoni...");

        // Enviar al Gateway de forma asíncrona
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                FararoniBridge bridge = FararoniBridge.getInstance(project);
                if (bridge != null) {
                    // Envío Fire-and-Forget
                    bridge.analyzeCode(fullContext, filePath, cursorLine, "CHAT_RESPONSE");
                    LOG.info("[AskFararoniAction] Query sent to Gateway");

                    // Abrir ToolWindow para mostrar respuesta cuando llegue
                    ApplicationManager.getApplication().invokeLater(() -> {
                        openToolWindow(project);
                        showNotification(project,
                            "Consulta enviada. La respuesta aparecerá en el panel Fararoni.",
                            NotificationType.INFORMATION);
                    });
                } else {
                    throw new RuntimeException("FararoniBridge no disponible");
                }

            } catch (Exception ex) {
                LOG.warning("[AskFararoniAction] Error sending query: " + ex.getMessage());
                ApplicationManager.getApplication().invokeLater(() -> {
                    Messages.showErrorDialog(
                        project,
                        "Error conectando con Fararoni:\n" + ex.getMessage() +
                        "\n\nVerifica que el servidor esté corriendo:\nfararoni --server",
                        "Error de Conexión"
                    );
                });
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Habilitar solo si hay un editor activo
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(editor != null);
    }

    /**
     * Muestra un balloon temporal de procesamiento.
     */
    private void showProcessingBalloon(Editor editor, String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                JBPopupFactory.getInstance()
                    .createHtmlTextBalloonBuilder(message, null, new Color(50, 50, 50), null)
                    .setFadeoutTime(3000)
                    .createBalloon()
                    .show(RelativePoint.getCenterOf(editor.getComponent()), Balloon.Position.above);
            } catch (Exception e) {
                // Ignorar si no se puede mostrar el balloon
            }
        });
    }

    /**
     * Abre el ToolWindow de Fararoni.
     */
    private void openToolWindow(Project project) {
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = manager.getToolWindow("Fararoni");
        if (toolWindow != null) {
            toolWindow.show();
        }
    }

    /**
     * Muestra una notificación al usuario.
     */
    private void showNotification(Project project, String content, NotificationType type) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("Fararoni", content, type)
                .notify(project);
        } catch (Exception e) {
            LOG.warning("[AskFararoniAction] Could not show notification: " + e.getMessage());
        }
    }

    /**
     * Trunca texto a una longitud máxima.
     */
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
