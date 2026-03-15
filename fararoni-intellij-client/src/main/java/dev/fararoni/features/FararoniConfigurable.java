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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class FararoniConfigurable implements Configurable {

    private JPanel mainPanel;
    private JBTextField callbackPortField;
    private JBTextField gatewayUrlField;
    private JBCheckBox heartbeatCheckbox;
    private JBTextField heartbeatIntervalField;
    private JBCheckBox notificationsCheckbox;
    private JBTextField typingSpeedField;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Fararoni Sentinel";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        FararoniSettingsState settings = FararoniSettingsState.getInstance();

        // Campos de texto
        callbackPortField = new JBTextField(String.valueOf(settings.callbackPort));
        gatewayUrlField = new JBTextField(settings.gatewayUrl);
        heartbeatIntervalField = new JBTextField(String.valueOf(settings.heartbeatIntervalSeconds));
        typingSpeedField = new JBTextField(String.valueOf(settings.typingSpeedMs));

        // Checkboxes
        heartbeatCheckbox = new JBCheckBox("Mantener conexion activa (Heartbeat)", settings.heartbeatEnabled);
        notificationsCheckbox = new JBCheckBox("Mostrar notificaciones balloon", settings.showNotifications);

        // Construir formulario
        mainPanel = FormBuilder.createFormBuilder()
            // Seccion: Conexion
            .addLabeledComponent(new JBLabel("<html><b>Conexion</b></html>"), new JPanel(), 1, false)
            .addLabeledComponent(
                new JBLabel("Puerto Callback:"),
                callbackPortField,
                1,
                false
            )
            .addTooltip("Puerto donde el plugin recibe respuestas del servidor (default: 9999)")
            .addLabeledComponent(
                new JBLabel("Gateway URL:"),
                gatewayUrlField,
                1,
                false
            )
            .addTooltip("URL del servidor Fararoni Gateway")
            .addSeparator()

            // Seccion: Heartbeat
            .addLabeledComponent(new JBLabel("<html><b>Heartbeat</b></html>"), new JPanel(), 1, false)
            .addComponent(heartbeatCheckbox)
            .addTooltip("Envia pings periodicos para mantener la conexion activa y evitar latencia")
            .addLabeledComponent(
                new JBLabel("Intervalo (segundos):"),
                heartbeatIntervalField,
                1,
                false
            )
            .addSeparator()

            // Seccion: UI
            .addLabeledComponent(new JBLabel("<html><b>Interfaz</b></html>"), new JPanel(), 1, false)
            .addComponent(notificationsCheckbox)
            .addLabeledComponent(
                new JBLabel("Velocidad typing (ms):"),
                typingSpeedField,
                1,
                false
            )
            .addTooltip("Milisegundos entre cada caracter en el efecto de escritura")

            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        FararoniSettingsState settings = FararoniSettingsState.getInstance();

        return !callbackPortField.getText().equals(String.valueOf(settings.callbackPort))
            || !gatewayUrlField.getText().equals(settings.gatewayUrl)
            || heartbeatCheckbox.isSelected() != settings.heartbeatEnabled
            || !heartbeatIntervalField.getText().equals(String.valueOf(settings.heartbeatIntervalSeconds))
            || notificationsCheckbox.isSelected() != settings.showNotifications
            || !typingSpeedField.getText().equals(String.valueOf(settings.typingSpeedMs));
    }

    @Override
    public void apply() throws ConfigurationException {
        FararoniSettingsState settings = FararoniSettingsState.getInstance();

        // Validar puerto
        int newPort;
        try {
            newPort = Integer.parseInt(callbackPortField.getText().trim());
            if (newPort < 1024 || newPort > 65535) {
                throw new ConfigurationException("El puerto debe estar entre 1024 y 65535");
            }
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Puerto invalido: debe ser un numero");
        }

        // Validar intervalo heartbeat
        int newInterval;
        try {
            newInterval = Integer.parseInt(heartbeatIntervalField.getText().trim());
            if (newInterval < 5 || newInterval > 300) {
                throw new ConfigurationException("El intervalo debe estar entre 5 y 300 segundos");
            }
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Intervalo invalido: debe ser un numero");
        }

        // Validar velocidad typing
        int newTypingSpeed;
        try {
            newTypingSpeed = Integer.parseInt(typingSpeedField.getText().trim());
            if (newTypingSpeed < 1 || newTypingSpeed > 200) {
                throw new ConfigurationException("Velocidad typing debe estar entre 1 y 200 ms");
            }
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Velocidad invalida: debe ser un numero");
        }

        // Detectar si cambio el puerto
        boolean portChanged = settings.callbackPort != newPort;

        // Aplicar cambios
        settings.callbackPort = newPort;
        settings.gatewayUrl = gatewayUrlField.getText().trim();
        settings.heartbeatEnabled = heartbeatCheckbox.isSelected();
        settings.heartbeatIntervalSeconds = newInterval;
        settings.showNotifications = notificationsCheckbox.isSelected();
        settings.typingSpeedMs = newTypingSpeed;

        // Reiniciar CallbackServer si cambio el puerto
        if (portChanged) {
            restartCallbackServers();
        }
    }

    @Override
    public void reset() {
        FararoniSettingsState settings = FararoniSettingsState.getInstance();

        callbackPortField.setText(String.valueOf(settings.callbackPort));
        gatewayUrlField.setText(settings.gatewayUrl);
        heartbeatCheckbox.setSelected(settings.heartbeatEnabled);
        heartbeatIntervalField.setText(String.valueOf(settings.heartbeatIntervalSeconds));
        notificationsCheckbox.setSelected(settings.showNotifications);
        typingSpeedField.setText(String.valueOf(settings.typingSpeedMs));
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        callbackPortField = null;
        gatewayUrlField = null;
        heartbeatCheckbox = null;
        heartbeatIntervalField = null;
        notificationsCheckbox = null;
        typingSpeedField = null;
    }

    /**
     * Reinicia el CallbackServer en todos los proyectos abiertos.
     */
    private void restartCallbackServers() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (!project.isDisposed()) {
                FararoniSentinelService sentinel = FararoniSentinelService.getInstance(project);
                if (sentinel != null) {
                    sentinel.restartCallbackServer();
                }
            }
        }
    }
}
