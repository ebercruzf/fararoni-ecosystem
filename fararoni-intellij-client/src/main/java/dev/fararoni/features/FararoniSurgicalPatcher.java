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

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public final class FararoniSurgicalPatcher {

    private static final Logger LOG = Logger.getLogger(FararoniSurgicalPatcher.class.getName());

    /** Nombre visible del comando en el historial de Undo */
    private static final String COMMAND_NAME = "Fararoni: Surgical Patch";

    /**
     * Aplica un parche completo (reemplaza todo el contenido).
     *
     * @param project    el proyecto de IntelliJ
     * @param filePath   ruta completa del archivo
     * @param newContent nuevo contenido completo
     * @return resultado de la operación
     */
    public static PatchResult applyPatch(Project project, String filePath, String newContent) {
        LOG.info("[SurgicalPatcher] Applying full patch to: " + filePath);

        // 1. Obtener VirtualFile
        VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(new File(filePath));
        if (vFile == null) {
            LOG.warning("[SurgicalPatcher] File not found: " + filePath);
            return PatchResult.error("Archivo no encontrado: " + filePath);
        }

        // 2. Verificar permisos
        if (!vFile.isWritable()) {
            LOG.warning("[SurgicalPatcher] File is read-only: " + filePath);
            return PatchResult.error("Archivo de solo lectura: " + filePath);
        }

        // 3. Obtener Document
        Document document = FileDocumentManager.getInstance().getDocument(vFile);
        if (document == null) {
            LOG.warning("[SurgicalPatcher] Could not get document: " + filePath);
            return PatchResult.error("No se pudo obtener el documento");
        }

        // 4. Aplicar cambio con WriteCommandAction
        try {
            WriteCommandAction.runWriteCommandAction(project, COMMAND_NAME, null, () -> {
                document.setText(newContent);
                FileDocumentManager.getInstance().saveDocument(document);
            });

            LOG.info("[SurgicalPatcher] Patch applied successfully");
            return PatchResult.success();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[SurgicalPatcher] Error applying patch", e);
            return PatchResult.error("Error aplicando parche: " + e.getMessage());
        }
    }

    /**
     * Aplica un parche parcial (SEARCH/REPLACE).
     *
     * @param project     el proyecto de IntelliJ
     * @param filePath    ruta completa del archivo
     * @param searchText  texto a buscar
     * @param replaceText texto de reemplazo
     * @return resultado de la operación
     */
    public static PatchResult applyPartialPatch(Project project, String filePath,
                                                 String searchText, String replaceText) {
        LOG.info("[SurgicalPatcher] Applying partial patch to: " + filePath);

        // 1. Obtener VirtualFile y Document
        VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(new File(filePath));
        if (vFile == null) {
            return PatchResult.error("Archivo no encontrado: " + filePath);
        }

        Document document = FileDocumentManager.getInstance().getDocument(vFile);
        if (document == null) {
            return PatchResult.error("No se pudo obtener el documento");
        }

        // 2. Buscar el texto
        String currentContent = document.getText();
        if (!currentContent.contains(searchText)) {
            LOG.warning("[SurgicalPatcher] Search text not found in file");
            return PatchResult.error("Texto de búsqueda no encontrado en el archivo");
        }

        // 3. Aplicar reemplazo
        String newContent = currentContent.replace(searchText, replaceText);

        try {
            WriteCommandAction.runWriteCommandAction(project, COMMAND_NAME + " (Partial)", null, () -> {
                document.setText(newContent);
                FileDocumentManager.getInstance().saveDocument(document);
            });

            LOG.info("[SurgicalPatcher] Partial patch applied successfully");
            return PatchResult.success();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[SurgicalPatcher] Error applying partial patch", e);
            return PatchResult.error("Error aplicando parche parcial: " + e.getMessage());
        }
    }

    /**
     * Agrega código al final del archivo.
     *
     * @param project   el proyecto de IntelliJ
     * @param filePath  ruta completa del archivo
     * @param codeToAdd código a agregar
     * @return resultado de la operación
     */
    public static PatchResult appendCode(Project project, String filePath, String codeToAdd) {
        LOG.info("[SurgicalPatcher] Appending code to: " + filePath);

        VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(new File(filePath));
        if (vFile == null) {
            return PatchResult.error("Archivo no encontrado: " + filePath);
        }

        Document document = FileDocumentManager.getInstance().getDocument(vFile);
        if (document == null) {
            return PatchResult.error("No se pudo obtener el documento");
        }

        try {
            WriteCommandAction.runWriteCommandAction(project, COMMAND_NAME + " (Append)", null, () -> {
                document.insertString(document.getTextLength(), "\n" + codeToAdd);
                FileDocumentManager.getInstance().saveDocument(document);
            });

            LOG.info("[SurgicalPatcher] Code appended successfully");
            return PatchResult.success();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[SurgicalPatcher] Error appending code", e);
            return PatchResult.error("Error agregando código: " + e.getMessage());
        }
    }

    /**
     * Verifica si un archivo puede ser modificado.
     *
     * @param filePath ruta del archivo
     * @return true si es modificable
     */
    public static boolean canPatch(String filePath) {
        VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(new File(filePath));
        return vFile != null && vFile.isWritable();
    }

    /**
     * Muestra una previsualización de los cambios antes de aplicar el parche.
     *
     * <p>Usa el comparador nativo de IntelliJ para mostrar un diff
     * lado a lado entre el código original y la sugerencia de Fararoni.</p>
     *
     * @param project    el proyecto de IntelliJ
     * @param filePath   ruta del archivo
     * @param newContent nuevo contenido sugerido
     */
    public static void showDiffPreview(Project project, String filePath, String newContent) {
        LOG.info("[SurgicalPatcher] Mostrando preview de diff para: " + filePath);

        VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(new File(filePath));
        if (vFile == null) {
            LOG.warning("[SurgicalPatcher] Archivo no encontrado para preview: " + filePath);
            return;
        }

        try {
            DiffContentFactory contentFactory = DiffContentFactory.getInstance();

            // Crear contenidos para comparación
            var originalContent = contentFactory.create(project, vFile);
            var suggestedContent = contentFactory.create(newContent, vFile.getFileType());

            // Crear request de diff
            SimpleDiffRequest request = new SimpleDiffRequest(
                "Fararoni: Previsualización de Mejora - " + vFile.getName(),
                originalContent,
                suggestedContent,
                "Código Original",
                "Sugerencia de Fararoni"
            );

            // Mostrar el diff viewer
            DiffManager.getInstance().showDiff(project, request);

            LOG.info("[SurgicalPatcher] Diff preview mostrado exitosamente");

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SurgicalPatcher] Error mostrando diff preview", e);
        }
    }

    /**
     * Muestra previsualización y retorna true si el usuario acepta aplicar.
     *
     * <p>Versión interactiva que muestra el diff y espera confirmación.</p>
     *
     * @param project    el proyecto de IntelliJ
     * @param vFile      archivo virtual
     * @param newContent nuevo contenido sugerido
     */
    public static void showDiffPreview(Project project, VirtualFile vFile, String newContent) {
        if (vFile == null) {
            LOG.warning("[SurgicalPatcher] VirtualFile es null para preview");
            return;
        }

        try {
            DiffContentFactory contentFactory = DiffContentFactory.getInstance();

            SimpleDiffRequest request = new SimpleDiffRequest(
                "Fararoni: Previsualización - " + vFile.getName(),
                contentFactory.create(project, vFile),
                contentFactory.create(newContent, vFile.getFileType()),
                "Código Original",
                "Sugerencia de Fararoni"
            );

            DiffManager.getInstance().showDiff(project, request);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SurgicalPatcher] Error mostrando diff preview", e);
        }
    }

    /**
     * Resultado de una operación de parcheo.
     *
     * @param ok           true si fue exitoso
     * @param errorMessage mensaje de error (null si exitoso)
     */
    public record PatchResult(boolean ok, String errorMessage) {

        public static PatchResult success() {
            return new PatchResult(true, null);
        }

        public static PatchResult error(String message) {
            return new PatchResult(false, message);
        }

        public boolean isSuccess() {
            return ok;
        }

        public boolean isError() {
            return !ok;
        }
    }
}
