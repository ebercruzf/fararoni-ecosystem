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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@Service(Service.Level.PROJECT)
public final class FararoniErrorSensor {

    private static final Logger LOG = Logger.getLogger(FararoniErrorSensor.class.getName());

    /** Tiempo de debounce en milisegundos */
    public static final int DEBOUNCE_MS = 500;

    /** Máximo de errores a procesar por archivo */
    public static final int MAX_ERRORS_PER_FILE = 3;

    /** Extensiones de archivo soportadas */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        "java", "kt", "kts", "py", "js", "ts", "tsx", "jsx"
    );

    private final Project project;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> pendingChecks;
    private final Map<String, Long> lastSentTimestamp;

    // Métricas
    private long totalErrorsDetected = 0;
    private long totalRequestsSent = 0;

    /**
     * Crea un nuevo ErrorSensor para el proyecto dado.
     *
     * @param project el proyecto de IntelliJ
     */
    public FararoniErrorSensor(Project project) {
        this.project = project;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FararoniErrorSensor");
            t.setDaemon(true);
            return t;
        });
        this.pendingChecks = new ConcurrentHashMap<>();
        this.lastSentTimestamp = new ConcurrentHashMap<>();

        LOG.info("[ErrorSensor] Initialized for project: " + project.getName());
    }

    /**
     * Programa una verificación de errores con debounce.
     *
     * <p>Si se llama múltiples veces en rápida sucesión,
     * solo la última llamada se ejecuta.</p>
     *
     * @param editor el editor activo
     */
    public void scheduleCheck(Editor editor) {
        if (editor == null) return;

        VirtualFile file = FileEditorManager.getInstance(project)
            .getSelectedFiles().length > 0
            ? FileEditorManager.getInstance(project).getSelectedFiles()[0]
            : null;

        if (file == null || !isSupported(file)) {
            return;
        }

        String filePath = file.getPath();

        // Cancelar check pendiente anterior
        ScheduledFuture<?> existing = pendingChecks.remove(filePath);
        if (existing != null) {
            existing.cancel(false);
        }

        // Programar nuevo check con debounce
        ScheduledFuture<?> future = scheduler.schedule(
            () -> checkForErrors(editor, file),
            DEBOUNCE_MS,
            TimeUnit.MILLISECONDS
        );

        pendingChecks.put(filePath, future);
        LOG.fine("[ErrorSensor] Scheduled check for: " + file.getName());
    }

    /**
     * Verifica inmediatamente si hay errores en el editor.
     *
     * @param editor el editor activo
     * @param file   el archivo virtual
     */
    public void checkForErrors(Editor editor, VirtualFile file) {
        if (editor == null || file == null) return;

        ApplicationManager.getApplication().runReadAction(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile == null) return;

            List<ErrorInfo> errors = collectErrors(psiFile, editor.getDocument());

            if (!errors.isEmpty()) {
                totalErrorsDetected += errors.size();
                sendToCore(errors, file.getPath());
            }
        });
    }

    /**
     * Habilita o deshabilita el sensor.
     *
     * @param enabled true para habilitar
     */
    public void setEnabled(boolean enabled) {
        if (!enabled) {
            // Cancelar todos los checks pendientes
            pendingChecks.values().forEach(f -> f.cancel(false));
            pendingChecks.clear();
            LOG.info("[ErrorSensor] Disabled");
        } else {
            LOG.info("[ErrorSensor] Enabled");
        }
    }

    /**
     * Recolecta errores del PsiFile.
     *
     * @param psiFile  el archivo PSI
     * @param document el documento asociado
     * @return lista de errores encontrados (máximo MAX_ERRORS_PER_FILE)
     */
    private List<ErrorInfo> collectErrors(PsiFile psiFile, Document document) {
        List<ErrorInfo> errors = new ArrayList<>();

        // Visitor que recorre el árbol PSI buscando errores
        psiFile.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (errors.size() >= MAX_ERRORS_PER_FILE) return;

                // Buscar elementos con errores sintácticos
                if (element instanceof PsiErrorElement errorElement) {
                    int offset = errorElement.getTextOffset();
                    int line = document.getLineNumber(offset) + 1;

                    errors.add(new ErrorInfo(
                        errorElement.getErrorDescription(),
                        line,
                        getContextAround(document, offset, 100),
                        "SYNTAX_ERROR"
                    ));
                }

                super.visitElement(element);
            }
        });

        // También buscar referencias no resueltas (errores semánticos)
        if (errors.size() < MAX_ERRORS_PER_FILE && psiFile instanceof PsiJavaFile javaFile) {
            collectUnresolvedReferences(javaFile, document, errors);
        }

        return errors;
    }

    /**
     * Busca referencias no resueltas en archivo Java.
     */
    private void collectUnresolvedReferences(PsiJavaFile javaFile, Document document,
                                              List<ErrorInfo> errors) {
        javaFile.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitReferenceExpression(PsiReferenceExpression expression) {
                if (errors.size() >= MAX_ERRORS_PER_FILE) return;

                if (expression.resolve() == null) {
                    String refName = expression.getReferenceName();
                    if (refName != null && !refName.isEmpty()) {
                        int offset = expression.getTextOffset();
                        int line = document.getLineNumber(offset) + 1;

                        errors.add(new ErrorInfo(
                            "Cannot resolve symbol '" + refName + "'",
                            line,
                            getContextAround(document, offset, 100),
                            "UNRESOLVED_REFERENCE"
                        ));
                    }
                }

                super.visitReferenceExpression(expression);
            }
        });
    }

    /**
     * Obtiene contexto de código alrededor de un offset.
     */
    private String getContextAround(Document document, int offset, int radius) {
        int start = Math.max(0, offset - radius);
        int end = Math.min(document.getTextLength(), offset + radius);
        return document.getText().substring(start, end);
    }

    /**
     * Envía los errores al Core via FararoniBridge.
     */
    private void sendToCore(List<ErrorInfo> errors, String filePath) {
        // Rate limiting: no enviar más de una vez por segundo para el mismo archivo
        long now = System.currentTimeMillis();
        Long lastSent = lastSentTimestamp.get(filePath);
        if (lastSent != null && (now - lastSent) < 1000) {
            LOG.fine("[ErrorSensor] Rate limited for: " + filePath);
            return;
        }
        lastSentTimestamp.put(filePath, now);

        // Construir descripción del error para el Core
        StringBuilder errorDesc = new StringBuilder();
        for (ErrorInfo error : errors) {
            errorDesc.append("Line ")
                     .append(error.line())
                     .append(": ")
                     .append(error.message())
                     .append("\n");
        }

        // Contexto del primer error (el más relevante)
        String context = errors.get(0).codeContext();

        // Enviar al Core
        FararoniBridge bridge = FararoniBridge.getInstance(project);
        if (bridge != null) {
            bridge.requestTacticalFix(errorDesc.toString(), context, filePath);
            totalRequestsSent++;
            LOG.info("[ErrorSensor] Sent " + errors.size() + " errors for " +
                     extractFileName(filePath));
        }
    }

    /**
     * Verifica si el archivo es de un tipo soportado.
     */
    private boolean isSupported(VirtualFile file) {
        String ext = file.getExtension();
        return ext != null && SUPPORTED_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * Extrae nombre del archivo de ruta completa.
     */
    private String extractFileName(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    public long getTotalErrorsDetected() { return totalErrorsDetected; }
    public long getTotalRequestsSent() { return totalRequestsSent; }

    /**
     * Obtiene instancia del servicio para un proyecto.
     *
     * @param project el proyecto
     * @return instancia del FararoniErrorSensor
     */
    public static FararoniErrorSensor getInstance(Project project) {
        return project.getService(FararoniErrorSensor.class);
    }

    /**
     * Información sobre un error detectado.
     *
     * @param message     descripción del error
     * @param line        número de línea
     * @param codeContext código alrededor del error
     * @param errorType   tipo de error (SYNTAX_ERROR, UNRESOLVED_REFERENCE, etc.)
     */
    public record ErrorInfo(
        String message,
        int line,
        String codeContext,
        String errorType
    ) {}
}
