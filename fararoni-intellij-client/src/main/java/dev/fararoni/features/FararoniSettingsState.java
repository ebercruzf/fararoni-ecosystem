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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@State(
    name = "dev.fararoni.settings.FararoniSettingsState",
    storages = @Storage("FararoniSentinel.xml")
)
public class FararoniSettingsState implements PersistentStateComponent<FararoniSettingsState> {

    /**
     * Puerto del CallbackServer para recibir respuestas proactivas.
     *
     * <p>Si este puerto esta ocupado, el usuario puede cambiarlo
     * desde Settings > Tools > Fararoni Sentinel.</p>
     */
    public int callbackPort = 9999;

    /**
     * URL del Gateway Fararoni.
     *
     * <p>Endpoint donde el plugin envia las solicitudes.</p>
     */
    public String gatewayUrl = "http://localhost:7071/gateway/v1/inbound";

    /**
     * Habilitar heartbeat para mantener conexion activa.
     *
     * <p>Si se desactiva, el plugin podria entrar en "hibernacion"
     * y responder con latencia.</p>
     */
    public boolean heartbeatEnabled = true;

    /**
     * Intervalo del heartbeat en segundos.
     */
    public int heartbeatIntervalSeconds = 30;

    /**
     * Mostrar notificaciones balloon para respuestas.
     */
    public boolean showNotifications = true;

    /**
     * Velocidad del efecto typing (ms por caracter).
     */
    public int typingSpeedMs = 30;

    // lastTraceId movido a FararoniProjectSession (project-level)
    // para evitar contaminación de contexto entre proyectos abiertos en paralelo.

    /**
     * Obtiene la instancia global del estado de configuracion.
     *
     * @return instancia singleton
     */
    public static FararoniSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(FararoniSettingsState.class);
    }

    /**
     * Retorna el estado actual para serializar.
     *
     * @return este objeto
     */
    @Nullable
    @Override
    public FararoniSettingsState getState() {
        return this;
    }

    /**
     * Carga el estado desde el archivo XML.
     *
     * @param state estado deserializado
     */
    @Override
    public void loadState(@NotNull FararoniSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    /**
     * Retorna la URL del callback completa.
     *
     * @return URL del callback (ej: http://localhost:9999/push)
     */
    public String getCallbackUrl() {
        return "http://localhost:" + callbackPort + "/push";
    }

    /**
     * Retorna la URL del health check del Gateway.
     *
     * @return URL del health (ej: http://localhost:7071/gateway/v1/health)
     */
    public String getGatewayHealthUrl() {
        return gatewayUrl.replace("/inbound", "/health");
    }

    /**
     * Resetea a valores por defecto.
     */
    public void resetToDefaults() {
        this.callbackPort = 9999;
        this.gatewayUrl = "http://localhost:7071/gateway/v1/inbound";
        this.heartbeatEnabled = true;
        this.heartbeatIntervalSeconds = 30;
        this.showNotifications = true;
        this.typingSpeedMs = 30;
        // lastTraceId movido a FararoniProjectSession
    }
}
