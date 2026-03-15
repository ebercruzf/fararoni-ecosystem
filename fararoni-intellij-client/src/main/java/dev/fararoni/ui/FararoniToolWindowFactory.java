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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import dev.fararoni.features.FararoniBridge;
import dev.fararoni.features.FararoniCallbackServer;
import dev.fararoni.features.FararoniProjectSession;
import dev.fararoni.features.FararoniSettingsState;
import dev.fararoni.features.FararoniSuggestionCache;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class FararoniToolWindowFactory implements ToolWindowFactory {

    private static final Logger LOG = Logger.getLogger(FararoniToolWindowFactory.class.getName());

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        FararoniToolWindowPanel panel = new FararoniToolWindowPanel(project);
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "Chat", false);
        toolWindow.getContentManager().addContent(content);

        LOG.info("[ToolWindowFactory] Created ToolWindow for project: " + project.getName());
    }

    /**
     * Panel principal del Tool Window con soporte de streaming.
     */
    private static class FararoniToolWindowPanel extends JPanel {

        private static final Logger LOG = Logger.getLogger(FararoniToolWindowPanel.class.getName());


        private final Project project;
        private final JTextArea outputArea;
        private final JTextField inputField;
        private final JButton sendButton;


        private final SafeStreamingRenderer streamingRenderer;
        private FararoniBridge bridge;
        private FararoniCallbackServer callbackServer;


        public FararoniToolWindowPanel(Project project) {
            this.project = project;

            setLayout(new BorderLayout(8, 8));
            setBorder(new EmptyBorder(8, 8, 8, 8));

            // Header Panel con titulo y boton de limpiar sesion
            JPanel headerPanel = new JPanel(new BorderLayout());

            JLabel headerLabel = new JLabel("Fararoni Sentinel AI");
            headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
            headerPanel.add(headerLabel, BorderLayout.WEST);

            // Boton de Limpiar Sesion (Grado Militar)
            JButton clearSessionBtn = new JButton("Nueva Sesion");
            clearSessionBtn.setToolTipText("Genera un nuevo Trace ID para iniciar conversacion desde cero");
            clearSessionBtn.setFont(clearSessionBtn.getFont().deriveFont(10f));
            clearSessionBtn.addActionListener(e -> clearSession(clearSessionBtn));
            headerPanel.add(clearSessionBtn, BorderLayout.EAST);

            add(headerPanel, BorderLayout.NORTH);

            // Output Area (Respuestas)
            outputArea = new JTextArea();
            outputArea.setEditable(false);
            outputArea.setLineWrap(true);
            outputArea.setWrapStyleWord(true);
            outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            outputArea.setText("Fararoni listo. Escribe tu pregunta o pega código.\n\n");

            JBScrollPane scrollPane = new JBScrollPane(outputArea);
            scrollPane.setPreferredSize(new Dimension(300, 400));
            add(scrollPane, BorderLayout.CENTER);

            // Streaming Renderer (efecto typing)
            this.streamingRenderer = new SafeStreamingRenderer(outputArea);

            // Input Panel (Campo de texto + Botón)
            JPanel inputPanel = new JPanel(new BorderLayout(4, 0));

            inputField = new JTextField();
            inputField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            inputField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        sendMessage();
                    }
                }
            });

            sendButton = new JButton("Enviar");
            sendButton.addActionListener(e -> sendMessage());

            inputPanel.add(inputField, BorderLayout.CENTER);
            inputPanel.add(sendButton, BorderLayout.EAST);

            // Hint label
            JPanel bottomPanel = new JPanel(new BorderLayout());
            JLabel hintLabel = new JLabel("Presiona Enter o click en Enviar");
            hintLabel.setFont(hintLabel.getFont().deriveFont(Font.ITALIC, 10f));
            hintLabel.setForeground(Color.GRAY);
            bottomPanel.add(inputPanel, BorderLayout.CENTER);
            bottomPanel.add(hintLabel, BorderLayout.SOUTH);

            add(bottomPanel, BorderLayout.SOUTH);

            // Iniciar servicios Enterprise
            initializeServices();
        }


        /**
         * Inicializa los servicios enterprise: Bridge, CallbackServer, y StreamingCallback.
         */
        private void initializeServices() {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    // 1. Obtener Bridge
                    bridge = FararoniBridge.getInstance(project);

                    // 2. Obtener o iniciar CallbackServer
                    callbackServer = FararoniCallbackServer.getInstance(project);
                    if (callbackServer != null && !callbackServer.isRunning()) {
                        boolean started = callbackServer.start();
                        if (started) {
                            LOG.info("[ToolWindowPanel] CallbackServer started on port " +
                                     callbackServer.getPort());
                        }
                    }

                    // 3. Registrar StreamingCallback
                    if (callbackServer != null) {
                        callbackServer.setStreamingCallback(this::onStreamingChunk);
                        LOG.info("[ToolWindowPanel] StreamingCallback registered");
                    }

                    LOG.info("[ToolWindowPanel] Enterprise services initialized");

                } catch (Exception e) {
                    LOG.warning("[ToolWindowPanel] Error initializing services: " + e.getMessage());
                }
            });
        }


        /**
         * Callback invocado cuando llega un chunk de respuesta del Core.
         *
         * @param content  contenido del chunk
         * @param isFinal  true si es el último chunk
         */
        private void onStreamingChunk(String content, boolean isFinal) {
            LOG.info("[ToolWindowPanel] onStreamingChunk called: content=" +
                     (content != null ? content.length() + " chars" : "null") +
                     ", isFinal=" + isFinal);

            if (content == null || content.isEmpty()) {
                LOG.warning("[ToolWindowPanel] onStreamingChunk: Empty content, skipping");
                return;
            }

            // Si es el primer chunk de esta respuesta, agregar prefijo
            if (streamingRenderer.getBufferSize() == 0 && !streamingRenderer.isRendering()) {
                LOG.info("[ToolWindowPanel] First chunk, adding prefix");
                streamingRenderer.appendImmediate("\n[Fararoni]: ");
            }

            // Enviar al renderer para efecto typing
            LOG.info("[ToolWindowPanel] Enqueuing text: " + content.substring(0, Math.min(50, content.length())));
            streamingRenderer.enqueueText(content);

            // Si es el último chunk, agregar newline al final
            if (isFinal) {
                streamingRenderer.enqueueText("\n");

                // Re-habilitar input
                SwingUtilities.invokeLater(() -> {
                    inputField.setEnabled(true);
                    sendButton.setEnabled(true);
                    inputField.requestFocus();
                });
            }
        }


        /**
         * Envía el mensaje del usuario al Core via FararoniBridge.
         */
        private void sendMessage() {
            String input = inputField.getText().trim();
            if (input.isEmpty()) return;

            // Mostrar input del usuario inmediatamente
            appendOutput("\n[Tu]: " + input + "\n");
            inputField.setText("");
            inputField.setEnabled(false);
            sendButton.setEnabled(false);

            // Enviar al Gateway de forma asíncrona
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    if (bridge != null) {
                        // Mostrar indicador de espera
                        SwingUtilities.invokeLater(() -> {
                            streamingRenderer.appendImmediate("[Fararoni]: Pensando...");
                        });

                        // Envío Fire-and-Forget
                        bridge.sendQuery(input, "CHAT_RESPONSE");
                        LOG.info("[ToolWindowPanel] Query sent to Gateway");

                        // Limpiar "Pensando..." cuando llegue la primera respuesta
                        // (el callback onStreamingChunk lo manejará)
                        SwingUtilities.invokeLater(() -> {
                            String text = outputArea.getText();
                            if (text.contains("[Fararoni]: Pensando...")) {
                                text = text.replace("[Fararoni]: Pensando...", "");
                                outputArea.setText(text);
                            }
                        });

                    } else {
                        throw new RuntimeException("FararoniBridge no disponible");
                    }

                } catch (Exception e) {
                    LOG.warning("[ToolWindowPanel] Error sending query: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        String text = outputArea.getText();
                        text = text.replace("[Fararoni]: Pensando...", "");
                        outputArea.setText(text);

                        appendOutput("[Error]: " + e.getMessage() + "\n");
                        appendOutput("Verifica que el servidor esté corriendo: fararoni --server\n");

                        inputField.setEnabled(true);
                        sendButton.setEnabled(true);
                        inputField.requestFocus();
                    });
                }
            });
        }


        /**
         * Limpia la sesion actual y genera un nuevo Trace ID.
         *
         * <p>Esto hace que el Core trate la proxima conversacion como
         * completamente nueva, sin historial previo.</p>
         *
         * <p>Operacion atomica de grado militar que incluye:</p>
         * <ol>
         *   <li>Rotacion del Trace ID persistente</li>
         *   <li>Limpieza del cache de sugerencias</li>
         *   <li>Reinicio del DaemonCodeAnalyzer (warnings visuales)</li>
         *   <li>Notificacion Balloon al usuario</li>
         * </ol>
         *
         * @param sourceButton boton que disparo la accion (para posicionar el Balloon)
         */
        private void clearSession(JButton sourceButton) {
            LOG.info("[ToolWindowPanel] Usuario solicito limpiar sesion");

            // 1. Rotar el Trace ID a nivel de proyecto
            String newTraceId = FararoniProjectSession.getInstance(project).rotateSession();

            // 2. Limpiar el cache de sugerencias
            try {
                FararoniSuggestionCache cache = FararoniSuggestionCache.getInstance(project);
                if (cache != null) {
                    cache.clear();
                    LOG.info("[ToolWindowPanel] Suggestion cache cleared");
                }
            } catch (Exception e) {
                LOG.fine("[ToolWindowPanel] No suggestion cache to clear");
            }

            // 3. Forzar reinicio del DaemonCodeAnalyzer para limpiar warnings visuales
            try {
                DaemonCodeAnalyzer.getInstance(project).restart();
                LOG.info("[ToolWindowPanel] DaemonCodeAnalyzer restarted - warnings cleared");
            } catch (Exception e) {
                LOG.fine("[ToolWindowPanel] Could not restart DaemonCodeAnalyzer: " + e.getMessage());
            }

            // 4. Limpiar el area de chat
            outputArea.setText("");

            // 4.5 Rehabilitar input (en caso de que estuviera bloqueado)
            inputField.setEnabled(true);
            sendButton.setEnabled(true);
            inputField.requestFocus();

            // 5. Mostrar mensaje de confirmacion en el chat
            appendOutput("--- Sesion reiniciada ---\n");
            appendOutput("Nuevo Trace ID: " + newTraceId.substring(0, 12) + "...\n");
            appendOutput("El LLM no recordara conversaciones anteriores.\n\n");
            appendOutput("Fararoni listo. Escribe tu pregunta o pega codigo.\n\n");

            // 6. Mostrar notificacion Balloon sobre el boton
            try {
                // Calcular posicion central arriba del boton
                java.awt.Point buttonLocation = sourceButton.getLocationOnScreen();
                int centerX = buttonLocation.x + (sourceButton.getWidth() / 2);
                int topY = buttonLocation.y;
                RelativePoint position = new RelativePoint(new java.awt.Point(centerX, topY));

                JBPopupFactory.getInstance()
                    .createHtmlTextBalloonBuilder(
                        "Contexto reiniciado. El LLM iniciara desde cero.",
                        null,
                        new JBColor(new Color(40, 45, 50), new Color(40, 45, 50)),
                        null
                    )
                    .setFadeoutTime(3000)
                    .setBorderColor(JBColor.GRAY)
                    .createBalloon()
                    .show(position, Balloon.Position.above);
                LOG.info("[ToolWindowPanel] Balloon notification shown");
            } catch (Exception e) {
                LOG.fine("[ToolWindowPanel] Could not show balloon: " + e.getMessage());
            }

            LOG.info("[ToolWindowPanel] Session rotated to: " + newTraceId);
        }


        /**
         * Agrega texto al area de output inmediatamente.
         */
        private void appendOutput(String text) {
            SwingUtilities.invokeLater(() -> {
                outputArea.append(text);
                outputArea.setCaretPosition(outputArea.getDocument().getLength());
            });
        }
    }
}
