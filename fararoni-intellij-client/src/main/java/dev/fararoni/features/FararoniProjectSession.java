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

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@Service(Service.Level.PROJECT)
public final class FararoniProjectSession {

    private static final Logger LOG = Logger.getLogger(FararoniProjectSession.class.getName());

    private final Project project;
    private String traceId;

    public FararoniProjectSession(Project project) {
        this.project = project;
        this.traceId = "tr-" + UUID.randomUUID().toString();
        LOG.info("[ProjectSession] Initialized for project: " + project.getName() +
                 " with traceId: " + traceId);
    }

    /**
     * Obtiene el Trace ID de este proyecto.
     *
     * @return Trace ID único para este proyecto
     */
    public String getTraceId() {
        if (traceId == null || traceId.isEmpty()) {
            traceId = "tr-" + UUID.randomUUID().toString();
        }
        return traceId;
    }

    /**
     * Rota la sesión generando un nuevo Trace ID.
     *
     * <p>El Core tratará la próxima conversación como nueva,
     * sin historial previo para ESTE proyecto.</p>
     *
     * @return el nuevo Trace ID generado
     */
    public String rotateSession() {
        this.traceId = "tr-" + UUID.randomUUID().toString();
        LOG.info("[ProjectSession] Session rotated for project: " + project.getName() +
                 " → new traceId: " + traceId);
        return this.traceId;
    }

    /**
     * Obtiene instancia del servicio para un proyecto.
     *
     * @param project el proyecto
     * @return instancia del FararoniProjectSession
     */
    public static FararoniProjectSession getInstance(Project project) {
        return project.getService(FararoniProjectSession.class);
    }
}
