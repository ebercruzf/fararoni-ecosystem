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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class FararoniCommitMessageAction extends AnAction {

    private static final Logger LOG = Logger.getLogger(FararoniCommitMessageAction.class.getName());

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            LOG.warning("[FararoniCommitMessage] No hay proyecto activo");
            return;
        }

        // Obtener los cambios seleccionados para commit
        Change[] changes = e.getData(VcsDataKeys.CHANGES);
        if (changes == null || changes.length == 0) {
            Messages.showInfoMessage(
                project,
                "No hay cambios seleccionados para analizar.",
                "Fararoni Commit Helper"
            );
            return;
        }

        LOG.info("[FararoniCommitMessage] Analizando " + changes.length + " cambios...");

        // Construir resumen de los cambios
        StringBuilder diffSummary = new StringBuilder();
        diffSummary.append("Analiza estos cambios de Git y genera un mensaje de commit ");
        diffSummary.append("conciso y descriptivo en español. ");
        diffSummary.append("Usa el formato: tipo(scope): descripción\n\n");
        diffSummary.append("Cambios:\n");

        for (Change change : changes) {
            diffSummary.append("- ");
            if (change.getType() != null) {
                diffSummary.append("[").append(change.getType().name()).append("] ");
            }
            if (change.getVirtualFile() != null) {
                diffSummary.append(change.getVirtualFile().getName());
            } else if (change.getBeforeRevision() != null) {
                diffSummary.append(change.getBeforeRevision().getFile().getName());
            }
            diffSummary.append("\n");
        }

        // Agregar contexto de rama si está disponible
        String branch = getCurrentBranch(project);
        if (branch != null && !branch.equals("no-vcs")) {
            diffSummary.append("\nRama actual: ").append(branch);
        }

        // Enviar al Core para procesamiento
        FararoniBridge bridge = FararoniBridge.getInstance(project);
        if (bridge != null) {
            LOG.info("[FararoniCommitMessage] Enviando a Core para generar mensaje...");
            bridge.sendQuery(diffSummary.toString(), "COMMIT_MESSAGE_SUGGESTION");

            // Notificar al usuario que el mensaje está siendo generado
            Messages.showInfoMessage(
                project,
                "Fararoni está analizando los cambios.\n" +
                "El mensaje aparecerá en el chat cuando esté listo.",
                "Generando Mensaje de Commit"
            );
        } else {
            LOG.warning("[FararoniCommitMessage] FararoniBridge no disponible");
        }
    }

    /**
     * Obtiene la rama actual de Git.
     */
    private String getCurrentBranch(Project project) {
        try {
            FararoniBridge bridge = FararoniBridge.getInstance(project);
            // Usar reflection o método existente si está disponible
            // Por ahora retornamos un valor por defecto
            return "feature-branch"; // TODO: Implementar getCurrentGitBranch en Bridge
        } catch (Exception e) {
            return "no-vcs";
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Solo mostrar si hay un proyecto y cambios disponibles
        Project project = e.getProject();
        Change[] changes = e.getData(VcsDataKeys.CHANGES);

        e.getPresentation().setEnabledAndVisible(
            project != null && changes != null && changes.length > 0
        );
    }
}
