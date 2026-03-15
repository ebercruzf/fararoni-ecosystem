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
package dev.fararoni.ui;

import javax.swing.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class SafeStreamingRenderer {

    private static final Logger LOG = Logger.getLogger(SafeStreamingRenderer.class.getName());


    /** Intervalo entre caracteres en milisegundos */
    public static final int DEFAULT_TYPING_INTERVAL_MS = 30;

    /** Intervalo rápido para chunks grandes */
    public static final int FAST_TYPING_INTERVAL_MS = 10;


    private final JTextArea outputArea;
    private final ConcurrentLinkedQueue<Character> buffer;
    private final Timer typingTimer;
    private int typingIntervalMs;

    // Estadísticas
    private long totalCharsRendered = 0;
    private long totalChunksReceived = 0;


    /**
     * Crea un nuevo renderer para el JTextArea dado.
     *
     * @param outputArea el área de texto donde mostrar el streaming
     */
    public SafeStreamingRenderer(JTextArea outputArea) {
        this(outputArea, DEFAULT_TYPING_INTERVAL_MS);
    }

    /**
     * Crea un nuevo renderer con intervalo personalizado.
     *
     * @param outputArea        el área de texto
     * @param typingIntervalMs  intervalo entre caracteres en ms
     */
    public SafeStreamingRenderer(JTextArea outputArea, int typingIntervalMs) {
        this.outputArea = outputArea;
        this.typingIntervalMs = typingIntervalMs;
        this.buffer = new ConcurrentLinkedQueue<>();

        // Timer que consume del buffer y escribe al JTextArea
        this.typingTimer = new Timer(typingIntervalMs, e -> renderNextCharacter());
        this.typingTimer.setRepeats(true);

        LOG.fine("[StreamingRenderer] Initialized with interval: " + typingIntervalMs + "ms");
    }


    /**
     * Agrega texto al buffer para renderizar.
     *
     * <p>Los caracteres se mostrarán uno por uno según el intervalo configurado.</p>
     *
     * @param text texto a encolar
     */
    public void enqueueText(String text) {
        if (text == null || text.isEmpty()) return;

        totalChunksReceived++;

        // Agregar cada caracter al buffer
        for (char c : text.toCharArray()) {
            buffer.add(c);
        }

        // Iniciar timer si no está corriendo
        if (!typingTimer.isRunning()) {
            typingTimer.start();
            LOG.fine("[StreamingRenderer] Timer started");
        }

        // Ajustar velocidad si hay mucho en el buffer
        adjustSpeed();
    }

    /**
     * Agrega texto inmediatamente sin efecto typing.
     *
     * <p>Útil para prefijos como "[Fararoni]: "</p>
     *
     * @param text texto a mostrar inmediatamente
     */
    public void appendImmediate(String text) {
        if (text == null) return;

        SwingUtilities.invokeLater(() -> {
            outputArea.append(text);
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    /**
     * Detiene el renderizado y limpia el buffer.
     */
    public void stop() {
        typingTimer.stop();
        buffer.clear();
        LOG.fine("[StreamingRenderer] Stopped");
    }

    /**
     * Pausa el renderizado sin limpiar el buffer.
     */
    public void pause() {
        typingTimer.stop();
    }

    /**
     * Reanuda el renderizado.
     */
    public void resume() {
        if (!buffer.isEmpty() && !typingTimer.isRunning()) {
            typingTimer.start();
        }
    }

    /**
     * Limpia el área de texto.
     */
    public void clear() {
        stop();
        SwingUtilities.invokeLater(() -> outputArea.setText(""));
    }


    /**
     * Renderiza el siguiente caracter del buffer.
     *
     * <p>Llamado por el SwingTimer cada intervalo.</p>
     */
    private void renderNextCharacter() {
        Character c = buffer.poll();

        if (c != null) {
            outputArea.append(String.valueOf(c));
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
            totalCharsRendered++;
        } else {
            // Buffer vacío, detener timer
            typingTimer.stop();
            LOG.fine("[StreamingRenderer] Buffer empty, timer stopped");
        }
    }

    /**
     * Ajusta la velocidad según el tamaño del buffer.
     *
     * <p>Si hay muchos caracteres pendientes, acelera para no quedarse atrás.</p>
     */
    private void adjustSpeed() {
        int bufferSize = buffer.size();

        if (bufferSize > 500 && typingIntervalMs > FAST_TYPING_INTERVAL_MS) {
            // Mucho en buffer, acelerar
            typingIntervalMs = FAST_TYPING_INTERVAL_MS;
            typingTimer.setDelay(typingIntervalMs);
            LOG.fine("[StreamingRenderer] Accelerated to " + typingIntervalMs + "ms");
        } else if (bufferSize < 50 && typingIntervalMs < DEFAULT_TYPING_INTERVAL_MS) {
            // Poco en buffer, volver a velocidad normal
            typingIntervalMs = DEFAULT_TYPING_INTERVAL_MS;
            typingTimer.setDelay(typingIntervalMs);
            LOG.fine("[StreamingRenderer] Normalized to " + typingIntervalMs + "ms");
        }
    }


    /**
     * Retorna el número de caracteres pendientes en el buffer.
     *
     * @return tamaño del buffer
     */
    public int getBufferSize() {
        return buffer.size();
    }

    /**
     * Retorna si el renderer está activo.
     *
     * @return true si el timer está corriendo
     */
    public boolean isRendering() {
        return typingTimer.isRunning();
    }

    /**
     * Retorna el total de caracteres renderizados.
     *
     * @return contador de caracteres
     */
    public long getTotalCharsRendered() {
        return totalCharsRendered;
    }

    /**
     * Retorna el total de chunks recibidos.
     *
     * @return contador de chunks
     */
    public long getTotalChunksReceived() {
        return totalChunksReceived;
    }

    /**
     * Retorna el intervalo actual de typing.
     *
     * @return intervalo en ms
     */
    public int getTypingIntervalMs() {
        return typingIntervalMs;
    }

    /**
     * Configura un nuevo intervalo de typing.
     *
     * @param intervalMs nuevo intervalo en ms
     */
    public void setTypingIntervalMs(int intervalMs) {
        this.typingIntervalMs = intervalMs;
        this.typingTimer.setDelay(intervalMs);
    }
}
