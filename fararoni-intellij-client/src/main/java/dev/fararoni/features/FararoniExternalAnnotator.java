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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public final class FararoniExternalAnnotator
        extends ExternalAnnotator<FararoniExternalAnnotator.InitialInfo,
                                   FararoniExternalAnnotator.AnnotationResult> {

    private static final Logger LOG = Logger.getLogger(FararoniExternalAnnotator.class.getName());

    /** Prefijo para mensajes de anotación */
    private static final String ANNOTATION_PREFIX = "Fararoni: ";

    /** Mensaje por defecto para sugerencias de mejora */
    private static final String DEFAULT_IMPROVEMENT_MSG = "Mejora de diseño disponible";

    /** Mensaje por defecto para quick fixes */
    private static final String DEFAULT_QUICKFIX_MSG = "Corrección sugerida disponible";

    /**
     * Recolecta información inicial del archivo.
     *
     * <p>Se ejecuta en background thread. Lee del cache para ver si hay
     * sugerencias pendientes para este archivo.</p>
     *
     * @param file   el PsiFile siendo anotado
     * @param editor el editor activo
     * @param hasErrors true si el archivo tiene errores de compilación
     * @return información inicial o null si no hay sugerencias
     */
    @Override
    @Nullable
    public InitialInfo collectInformation(@NotNull PsiFile file,
                                          @NotNull Editor editor,
                                          boolean hasErrors) {
        // Obtener ruta del archivo
        String filePath = file.getVirtualFile() != null
            ? file.getVirtualFile().getPath()
            : null;

        if (filePath == null) {
            return null;
        }

        // Buscar en cache
        FararoniSuggestionCache cache = FararoniSuggestionCache.getInstance(file.getProject());
        if (cache == null) {
            return null;
        }

        FararoniSuggestionCache.CachedSuggestion suggestion = cache.get(filePath);
        if (suggestion == null) {
            return null;  // No hay sugerencia para este archivo
        }

        LOG.fine("[ExternalAnnotator] Found suggestion for: " + file.getName() +
                 " (age: " + suggestion.ageSeconds() + "s)");

        return new InitialInfo(
            filePath,
            suggestion.content(),
            suggestion.intent(),
            suggestion.traceId(),
            editor.getDocument().getText()
        );
    }

    /**
     * Sobrecarga sin editor (para compatibilidad).
     */
    @Override
    @Nullable
    public InitialInfo collectInformation(@NotNull PsiFile file) {
        return null;  // Requiere editor para acceder al documento
    }

    /**
     * Procesa la información y determina qué anotar.
     *
     * <p>Se ejecuta en background thread. Analiza la sugerencia y
     * determina el rango de texto a anotar.</p>
     *
     * @param collectedInfo información recolectada en fase 1
     * @return resultado de anotación o null
     */
    @Override
    @Nullable
    public AnnotationResult doAnnotate(InitialInfo collectedInfo) {
        if (collectedInfo == null) {
            return null;
        }

        // Determinar mensaje según intent
        String message = switch (collectedInfo.intent()) {
            case "QUICK_FIX" -> DEFAULT_QUICKFIX_MSG;
            case "SMART_SUGGESTION" -> DEFAULT_IMPROVEMENT_MSG;
            case "SURGICAL_FIX" -> "Refactoring listo para aplicar";
            default -> DEFAULT_IMPROVEMENT_MSG;
        };

        // Determinar severidad según intent
        HighlightSeverity severity = switch (collectedInfo.intent()) {
            case "QUICK_FIX" -> HighlightSeverity.WARNING;
            case "SMART_SUGGESTION" -> HighlightSeverity.WEAK_WARNING;
            case "SURGICAL_FIX" -> HighlightSeverity.WARNING;
            default -> HighlightSeverity.INFORMATION;
        };

        // Determinar rango de texto a anotar
        // Por ahora, anotamos la primera línea no vacía
        TextRange range = findFirstNonEmptyLine(collectedInfo.currentContent());

        LOG.fine("[ExternalAnnotator] Prepared annotation: " + message +
                 " (severity: " + severity.getName() + ")");

        return new AnnotationResult(
            collectedInfo.filePath(),
            range,
            ANNOTATION_PREFIX + message,
            severity,
            collectedInfo.suggestedContent(),
            collectedInfo.intent()
        );
    }

    /**
     * Encuentra el rango de la primera línea no vacía.
     */
    private TextRange findFirstNonEmptyLine(String content) {
        if (content == null || content.isEmpty()) {
            return new TextRange(0, 1);
        }

        String[] lines = content.split("\n");
        int offset = 0;

        for (String line : lines) {
            if (!line.isBlank()) {
                return new TextRange(offset, offset + line.length());
            }
            offset += line.length() + 1;  // +1 for newline
        }

        // Fallback: primera línea
        int firstNewline = content.indexOf('\n');
        return new TextRange(0, firstNewline > 0 ? firstNewline : Math.min(content.length(), 50));
    }

    /**
     * Aplica las anotaciones al editor.
     *
     * <p>Se ejecuta en EDT. Crea las anotaciones visuales en el editor.</p>
     *
     * @param file              el PsiFile siendo anotado
     * @param annotationResult  resultado del procesamiento
     * @param holder            contenedor de anotaciones
     */
    @Override
    public void apply(@NotNull PsiFile file,
                      AnnotationResult annotationResult,
                      @NotNull AnnotationHolder holder) {
        if (annotationResult == null) {
            return;
        }

        // Validar que el rango esté dentro del documento
        int docLength = file.getTextLength();
        TextRange range = annotationResult.range();

        if (range.getEndOffset() > docLength) {
            range = new TextRange(0, Math.min(50, docLength));
        }

        // Crear la anotación
        holder.newAnnotation(annotationResult.severity(), annotationResult.message())
              .range(range)
              .withFix(new ApplyFararoniFixAction(
                  annotationResult.filePath(),
                  annotationResult.suggestedContent(),
                  annotationResult.intent()
              ))
              .create();

        LOG.info("[ExternalAnnotator] Applied annotation to: " + file.getName());
    }

    /**
     * Información inicial recolectada del archivo.
     *
     * @param filePath         ruta completa del archivo
     * @param suggestedContent código sugerido por el LLM
     * @param intent           tipo de sugerencia
     * @param traceId          ID para tracking
     * @param currentContent   contenido actual del archivo
     */
    public record InitialInfo(
        String filePath,
        String suggestedContent,
        String intent,
        String traceId,
        String currentContent
    ) {}

    /**
     * Resultado del procesamiento de anotación.
     *
     * @param filePath         ruta del archivo
     * @param range            rango de texto a anotar
     * @param message          mensaje a mostrar
     * @param severity         severidad de la anotación
     * @param suggestedContent código sugerido para el fix
     * @param intent           tipo de sugerencia
     */
    public record AnnotationResult(
        String filePath,
        TextRange range,
        String message,
        HighlightSeverity severity,
        String suggestedContent,
        String intent
    ) {}
}
