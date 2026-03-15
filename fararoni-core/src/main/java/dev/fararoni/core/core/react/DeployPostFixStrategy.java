/*
 * Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fararoni.core.core.react;

import java.util.regex.Pattern;

public class DeployPostFixStrategy implements IPostFixStrategy {
    private static final Pattern DEPLOY_INTENT = Pattern.compile(
        "(?i)(despliega|despliegue|deploy|levanta|arranca|ejecuta|prueba|probar|test|run|start|serve|lanza|sube|up)" +
        ".*(local|servidor|servicio|server|service|app|aplicacion|endpoint|puerto|port|backend|frontend)"
    );

    @Override
    public boolean supports(String userPrompt, String originalCommand) {
        return userPrompt != null && DEPLOY_INTENT.matcher(userPrompt).find();
    }

    @Override
    public String getContinuationDirective(String userPrompt, String originalCommand) {
        return "[TRANSICIÓN] La verificación de compilación fue EXITOSA — todos los errores de código están resueltos. "
            + "Sin embargo, la solicitud ORIGINAL del usuario era: \"" + userPrompt + "\". "
            + "La compilación solo verificó la sintaxis. Ahora debes completar la solicitud original "
            + "ejecutando el comando apropiado de inicio/despliegue del proyecto via ShellCommand. "
            + "Examina los archivos de configuración del proyecto (pom.xml, package.json, etc.) "
            + "para determinar el comando correcto de arranque.";
    }
}
