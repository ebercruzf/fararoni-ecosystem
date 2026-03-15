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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@Service(Service.Level.PROJECT)
public final class ProjectAwarenessManager {

    private static final Logger LOG = Logger.getLogger(ProjectAwarenessManager.class.getName());

    /** Máximo de archivos a incluir en el snapshot (evita payloads enormes) */
    private static final int MAX_FILES_IN_SNAPSHOT = 10;

    /** Máximo de caracteres de código fuente a capturar (evita saturar el LLM) */
    private static final int MAX_SOURCE_CODE_CHARS = 15000;

    private final Project project;

    /**
     * Crea un nuevo ProjectAwarenessManager para el proyecto dado.
     *
     * @param project el proyecto de IntelliJ
     */
    public ProjectAwarenessManager(Project project) {
        this.project = project;
        LOG.info("[ProjectAwareness] Initialized for project: " + project.getName());
    }

    /**
     * Genera un snapshot JSON del contexto actual del proyecto.
     *
     * <p>Incluye información de archivos abiertos sin contenido completo.</p>
     * <p>Usa ReadAction para acceso seguro al PSI.</p>
     *
     * @return JsonObject con el contexto del proyecto
     */
    public JsonObject getProjectContextSnapshot() {
        return ReadAction.compute(() -> {
            JsonObject snapshot = new JsonObject();
            snapshot.addProperty("projectName", project.getName());
            snapshot.addProperty("basePath", project.getBasePath());

            // Obtener archivos abiertos
            FileEditorManager editorManager = FileEditorManager.getInstance(project);
            VirtualFile[] openFiles = editorManager.getOpenFiles();
            VirtualFile[] selectedFiles = editorManager.getSelectedFiles();

            // Archivo activo (el que tiene foco)
            String activeFileName = selectedFiles.length > 0 ? selectedFiles[0].getName() : null;
            if (activeFileName != null) {
                snapshot.addProperty("activeFile", activeFileName);
            }

            // Lista de archivos abiertos
            JsonArray filesArray = new JsonArray();
            int count = 0;

            for (VirtualFile file : openFiles) {
                if (count >= MAX_FILES_IN_SNAPSHOT) break;

                JsonObject fileMeta = buildFileMetadata(file, activeFileName);
                if (fileMeta != null) {
                    filesArray.add(fileMeta);
                    count++;
                }
            }

            snapshot.add("openFiles", filesArray);
            snapshot.addProperty("totalOpenFiles", openFiles.length);
            snapshot.addProperty("timestamp", System.currentTimeMillis());

            LOG.fine("[ProjectAwareness] Snapshot generated: " + count + " files");
            return snapshot;
        });
    }

    /**
     * Obtiene el contexto del archivo actualmente en foco.
     *
     * <p>Incluye más detalles que el snapshot general:
     * imports, nombre de clase, métodos visibles.</p>
     * <p>Usa ReadAction para acceso seguro al PSI.</p>
     *
     * @return JsonObject con contexto detallado del archivo activo
     */
    public JsonObject getActiveFileContext() {
        return ReadAction.compute(() -> {
            JsonObject context = new JsonObject();

            FileEditorManager editorManager = FileEditorManager.getInstance(project);
            VirtualFile[] selectedFiles = editorManager.getSelectedFiles();

            if (selectedFiles.length == 0) {
                context.addProperty("error", "No active file");
                return context;
            }

            VirtualFile activeFile = selectedFiles[0];
            context.addProperty("name", activeFile.getName());
            context.addProperty("path", activeFile.getPath());
            context.addProperty("extension", activeFile.getExtension());

            // Obtener PSI para más detalles
            PsiFile psiFile = PsiManager.getInstance(project).findFile(activeFile);
            if (psiFile != null) {
                context.addProperty("language", psiFile.getLanguage().getID());

                // Si es Java, extraer estructura
                if (psiFile instanceof PsiJavaFile javaFile) {
                    context.add("structure", extractJavaStructure(javaFile));
                }
            }

            return context;
        });
    }

    /**
     * Obtiene solo los nombres de archivos abiertos (lightweight).
     *
     * @return array de nombres de archivos
     */
    public String[] getOpenFileNames() {
        VirtualFile[] openFiles = FileEditorManager.getInstance(project).getOpenFiles();
        String[] names = new String[openFiles.length];
        for (int i = 0; i < openFiles.length; i++) {
            names[i] = openFiles[i].getName();
        }
        return names;
    }

    /**
     * Verifica si un archivo específico está abierto.
     *
     * @param fileName nombre del archivo a buscar
     * @return true si está abierto
     */
    public boolean isFileOpen(String fileName) {
        VirtualFile[] openFiles = FileEditorManager.getInstance(project).getOpenFiles();
        for (VirtualFile file : openFiles) {
            if (file.getName().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Construye metadata JSON para un archivo de forma agnóstica.
     *
     * <p>GRADO MILITAR: Para el archivo activo, captura el código fuente completo
     * usando psiFile.getText(). Funciona para Java, TypeScript, JavaScript,
     * HTML, CSS, Kotlin, etc.</p>
     *
     * @param file           archivo virtual a procesar
     * @param activeFileName nombre del archivo activo (para comparación)
     * @return JsonObject con metadata y opcionalmente contenido
     */
    private JsonObject buildFileMetadata(VirtualFile file, String activeFileName) {
        JsonObject meta = new JsonObject();
        meta.addProperty("name", file.getName());
        meta.addProperty("path", file.getPath());
        meta.addProperty("extension", file.getExtension());

        boolean isActive = file.getName().equals(activeFileName);
        meta.addProperty("isActive", isActive);

        // El PsiManager es el motor universal de IntelliJ para entender cualquier archivo
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile != null) {
            // Capturamos el ID del lenguaje (ej: "TypeScript", "JAVA", "HTML", "kotlin")
            meta.addProperty("language", psiFile.getLanguage().getID());

            // --- CAPTURA DE CONTENIDO AGNÓSTICA (Grado Militar) ---
            // Si es el archivo que el usuario está viendo, enviamos el código completo.
            // psiFile.getText() es universal: devuelve el texto plano del buffer del editor.
            if (isActive) {
                String sourceCode = psiFile.getText();

                // Truncar si excede el límite para evitar saturar el LLM
                if (sourceCode.length() > MAX_SOURCE_CODE_CHARS) {
                    sourceCode = sourceCode.substring(0, MAX_SOURCE_CODE_CHARS) +
                                 "\n\n// ... [TRUNCADO: archivo muy largo, " +
                                 (psiFile.getText().length() - MAX_SOURCE_CODE_CHARS) +
                                 " caracteres omitidos] ...";
                    LOG.info("[ProjectAwareness] Contenido truncado de: " +
                             file.getName() + " (original: " + psiFile.getText().length() + " chars)");
                }

                meta.addProperty("content", sourceCode);

                // Log de control para verificar el tamaño del envío en idea.log
                LOG.info("[ProjectAwareness] Capturado contenido agnóstico de: " +
                         file.getName() + " (" + sourceCode.length() + " caracteres)");
            }
        }

        return meta;
    }

    /**
     * Extrae estructura básica de un archivo Java.
     *
     * <p>No envía código, solo firmas de clases y métodos.</p>
     */
    private JsonObject extractJavaStructure(PsiJavaFile javaFile) {
        JsonObject structure = new JsonObject();

        // Package
        PsiPackageStatement packageStatement = javaFile.getPackageStatement();
        if (packageStatement != null) {
            structure.addProperty("package", packageStatement.getPackageName());
        }

        // Imports (solo los nombres, no el código)
        JsonArray imports = new JsonArray();
        for (PsiImportStatement imp : javaFile.getImportList().getImportStatements()) {
            if (imp.getQualifiedName() != null) {
                imports.add(imp.getQualifiedName());
            }
        }
        structure.add("imports", imports);

        // Clases
        JsonArray classes = new JsonArray();
        for (PsiClass psiClass : javaFile.getClasses()) {
            JsonObject classMeta = new JsonObject();
            classMeta.addProperty("name", psiClass.getName());
            classMeta.addProperty("isInterface", psiClass.isInterface());
            classMeta.addProperty("isAbstract", psiClass.hasModifierProperty(PsiModifier.ABSTRACT));

            // Métodos (solo firmas)
            JsonArray methods = new JsonArray();
            for (PsiMethod method : psiClass.getMethods()) {
                methods.add(method.getName() + "()");
            }
            classMeta.add("methods", methods);

            // Campos
            JsonArray fields = new JsonArray();
            for (PsiField field : psiClass.getFields()) {
                fields.add(field.getName());
            }
            classMeta.add("fields", fields);

            classes.add(classMeta);
        }
        structure.add("classes", classes);

        return structure;
    }

    /**
     * Obtiene instancia del servicio para un proyecto.
     *
     * @param project el proyecto
     * @return instancia del ProjectAwarenessManager
     */
    public static ProjectAwarenessManager getInstance(Project project) {
        return project.getService(ProjectAwarenessManager.class);
    }
}
