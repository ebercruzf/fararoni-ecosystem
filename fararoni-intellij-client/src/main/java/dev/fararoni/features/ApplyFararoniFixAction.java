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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public final class ApplyFararoniFixAction implements IntentionAction, PriorityAction {

    private static final Logger LOG = Logger.getLogger(ApplyFararoniFixAction.class.getName());

    /** Nombre de la acción en el menú */
    private static final String ACTION_NAME = "Aplicar sugerencia de Fararoni";

    /** Familia de acciones (para agrupar en UI) */
    private static final String FAMILY_NAME = "Fararoni AI";

    /** Notification group (debe coincidir con plugin.xml) */
    private static final String NOTIFICATION_GROUP = "Fararoni Notifications";

    private final String filePath;
    private final String suggestedContent;
    private final String intent;

    /**
     * Crea una nueva acción de fix.
     *
     * @param filePath         ruta del archivo a modificar
     * @param suggestedContent código sugerido por el LLM
     * @param intent           tipo de sugerencia (QUICK_FIX, SURGICAL_FIX, etc.)
     */
    public ApplyFararoniFixAction(String filePath, String suggestedContent, String intent) {
        this.filePath = filePath;
        this.suggestedContent = suggestedContent;
        this.intent = intent;
    }

    /**
     * Texto mostrado en el menú Alt+Enter.
     */
    @Override
    @NotNull
    public String getText() {
        return switch (intent) {
            case "QUICK_FIX" -> "Fararoni: Aplicar corrección sugerida";
            case "SURGICAL_FIX" -> "Fararoni: Aplicar refactoring";
            case "SMART_SUGGESTION" -> "Fararoni: Aplicar mejora de diseño";
            default -> ACTION_NAME;
        };
    }

    /**
     * Familia de la acción (para agrupar).
     */
    @Override
    @NotNull
    public String getFamilyName() {
        return FAMILY_NAME;
    }

    /**
     * Determina si la acción está disponible.
     *
     * <p>Retorna true si hay contenido sugerido y el archivo es editable.</p>
     */
    @Override
    public boolean isAvailable(@NotNull Project project,
                                Editor editor,
                                PsiFile file) {
        if (suggestedContent == null || suggestedContent.isEmpty()) {
            return false;
        }

        if (file == null || file.getVirtualFile() == null) {
            return false;
        }

        return FararoniSurgicalPatcher.canPatch(filePath);
    }

    /**
     * Ejecuta la acción de aplicar el fix.
     *
     * <p>Llama a {@link FararoniSurgicalPatcher} para aplicar el cambio.</p>
     *
     * @param project el proyecto actual
     * @param editor  el editor activo
     * @param file    el archivo a modificar
     * @throws IncorrectOperationException si hay error al aplicar
     */
    @Override
    public void invoke(@NotNull Project project,
                       Editor editor,
                       PsiFile file) throws IncorrectOperationException {
        LOG.info("[ApplyFararoniFixAction] Applying fix to: " + filePath);

        // Aplicar el parche
        FararoniSurgicalPatcher.PatchResult result;

        if (isPartialPatch()) {
            // Para QUICK_FIX, intentar parche parcial
            result = applyPartialPatch(project);
        } else {
            // Para SURGICAL_FIX, reemplazo completo
            result = FararoniSurgicalPatcher.applyPatch(project, filePath, suggestedContent);
        }

        // Limpiar del cache
        FararoniSuggestionCache cache = FararoniSuggestionCache.getInstance(project);
        if (cache != null) {
            cache.remove(filePath);
        }

        // Mostrar notificación
        if (result.isSuccess()) {
            showNotification(project, "Sugerencia aplicada correctamente", NotificationType.INFORMATION);
            LOG.info("[ApplyFararoniFixAction] Fix applied successfully");
        } else {
            showNotification(project, "Error: " + result.errorMessage(), NotificationType.ERROR);
            LOG.warning("[ApplyFararoniFixAction] Fix failed: " + result.errorMessage());
        }
    }

    /**
     * Indica si la acción inicia en modo escritura.
     */
    @Override
    public boolean startInWriteAction() {
        // false porque WriteCommandAction se maneja en SurgicalPatcher
        return false;
    }

    /**
     * Prioridad de la acción (HIGH para aparecer arriba).
     */
    @Override
    @NotNull
    public Priority getPriority() {
        return Priority.HIGH;
    }

    /**
     * Determina si es un parche parcial.
     */
    private boolean isPartialPatch() {
        return "QUICK_FIX".equals(intent);
    }

    /**
     * Aplica un parche parcial extrayendo SEARCH/REPLACE del contenido.
     */
    private FararoniSurgicalPatcher.PatchResult applyPartialPatch(Project project) {
        // Si el contenido tiene formato SEARCH/REPLACE, usar parche parcial
        if (suggestedContent.contains("<<<SEARCH>>>") &&
            suggestedContent.contains("<<<REPLACE>>>")) {

            String[] parts = suggestedContent.split("<<<SEARCH>>>");
            if (parts.length > 1) {
                String rest = parts[1];
                String[] replaceParts = rest.split("<<<REPLACE>>>");
                if (replaceParts.length == 2) {
                    String searchText = replaceParts[0].trim();
                    String replaceText = replaceParts[1].trim();

                    return FararoniSurgicalPatcher.applyPartialPatch(
                        project, filePath, searchText, replaceText
                    );
                }
            }
        }

        // Fallback a reemplazo completo
        return FararoniSurgicalPatcher.applyPatch(project, filePath, suggestedContent);
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
            // Fallback si el notification group no existe
            LOG.warning("[ApplyFararoniFixAction] Could not show notification: " + e.getMessage());
        }
    }
}
