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
package dev.fararoni.core.core.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fararoni.core.FararoniCore;
import dev.fararoni.core.core.clients.OpenAICompatibleClient;
import dev.fararoni.core.core.managers.BiblioCognitiveTriadManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 2.0.0 (FASE 22 - Function Calling)
 */
public class ToolRegistry {
    private static final Logger LOG = Logger.getLogger(ToolRegistry.class.getName());

    private final ObjectMapper mapper;
    private final BiblioCognitiveTriadManager brain;

    private Set<String> activeRoles = Collections.emptySet();
    private Set<String> activeTemplates = Collections.emptySet();

    public ToolRegistry() {
        this.mapper = new ObjectMapper();
        this.brain = BiblioCognitiveTriadManager.getInstance();
    }

    public void injectDynamicContext(Set<String> roles, Set<String> templates) {
        this.activeRoles = roles != null ? Set.copyOf(roles) : Collections.emptySet();
        this.activeTemplates = templates != null ? Set.copyOf(templates) : Collections.emptySet();

        LOG.info("[ETAPA2] Contexto dinámico inyectado: " +
                 activeRoles.size() + " roles, " + activeTemplates.size() + " templates");
        System.out.println("[TOOL-REGISTRY] Contexto dinamico: " +
                          "roles=" + activeRoles + " templates=" + activeTemplates);
    }

    public List<ObjectNode> getAvailableTools() {
        return getAvailableTools(false);
    }

    public List<ObjectNode> getAvailableTools(boolean isExercismContext) {
        List<ObjectNode> tools = new ArrayList<>();

        tools.add(defineFsWriteTool());
        tools.add(defineFsPatchTool());
        tools.add(defineFsMkdirTool());
        tools.add(defineFsReadTool());

        tools.add(defineWebFetchTool());
        tools.add(defineWebSearchTool());

        tools.add(defineSwarmMissionTool());

        addQwenNativeTools(tools);

        addQwenExtendedTools(tools);

        if (isExercismContext) {
            tools.add(defineRestoreSolutionTool());
        }

        tools.add(defineEmailFetchTool());
        tools.add(defineEmailSendTool());
        tools.add(defineEmailReadTool());

        tools.add(defineConfigSetTool());

        return tools;
    }

    public List<ObjectNode> getAvailableTools(ModelFamily family) {
        return getAvailableTools(family, false);
    }

    public List<ObjectNode> getAvailableTools(ModelFamily family, boolean isExercismContext) {
        List<ObjectNode> tools = new ArrayList<>();

        tools.add(defineFsWriteTool());
        tools.add(defineFsPatchTool());
        tools.add(defineFsMkdirTool());
        tools.add(defineFsReadTool());
        tools.add(defineWebFetchTool());
        tools.add(defineWebSearchTool());
        tools.add(defineSwarmMissionTool());
        tools.add(defineShellCommandTool());

        tools.add(defineEmailFetchTool());
        tools.add(defineEmailSendTool());
        tools.add(defineEmailReadTool());

        tools.add(defineConfigSetTool());

        if (family == ModelFamily.QWEN_2_5 || family == ModelFamily.QWEN_3) {
            addQwenNativeTools(tools);
            addQwenExtendedTools(tools);
        }

        if (isExercismContext) {
            tools.add(defineRestoreSolutionTool());
        }

        return tools;
    }

    private ObjectNode defineFsWriteTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");

        ObjectNode function = tool.putObject("function");
        function.put("name", "fs_write");
        function.put("description",
            "Crea o sobrescribe un archivo en el proyecto. " +
            "USA ESTA HERRAMIENTA cuando el usuario pida crear clases, archivos, código, " +
            "configuraciones o cualquier contenido que deba guardarse en disco. " +
            "NO respondas con bloques de código markdown, USA esta herramienta. " +
            "IMPORTANTE: Siempre incluye el contenido COMPLETO del archivo. " +
            "NUNCA uses placeholders como '// ...' o '// Atributos existentes'.");

        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description",
            "Ruta relativa del archivo a crear (ej: src/main/java/com/example/Alumno.java, " +
            "config.json, README.md). Incluye la extensión correcta.");

        ObjectNode contentProp = properties.putObject("content");
        contentProp.put("type", "string");
        contentProp.put("description",
            "Contenido COMPLETO del archivo. Para código Java, incluye package, imports, " +
            "TODOS los atributos, TODOS los métodos y clase completa. " +
            "NUNCA uses placeholders ni comentarios tipo '// ...' o '// resto del código'.");

        ObjectNode forceProp = properties.putObject("force_destruction");
        forceProp.put("type", "string");
        forceProp.put("description",
            "Si el sistema bloquea tu escritura por 'contenido truncado' y la reducción " +
            "es intencional (refactor grande, eliminación de código muerto), " +
            "proporciona la razón aquí. Ej: 'Eliminando código legacy'. " +
            "Dejarlo vacío para operaciones normales.");

        parameters.putArray("required").add("path").add("content");
        parameters.put("additionalProperties", false);

        return tool;
    }

    private ObjectNode defineFsPatchTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");

        ObjectNode function = tool.putObject("function");
        function.put("name", "fs_patch");
        function.put("description",
            "Realiza una edicion quirurgica en un archivo existente reemplazando un bloque de texto por otro. " +
            "PREFIERE esta herramienta sobre fs_write cuando solo necesitas agregar un metodo, " +
            "modificar una funcion o hacer cambios pequenos en archivos grandes. " +
            "IMPORTANTE: El bloque 'search' debe coincidir EXACTAMENTE con el codigo actual del archivo, " +
            "incluyendo espacios e indentacion. Usa fs_read primero para ver el contenido exacto.");

        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description",
            "Ruta del archivo existente a modificar (ej: src/main/java/com/demo/Service.java)");

        ObjectNode searchProp = properties.putObject("search");
        searchProp.put("type", "string");
        searchProp.put("description",
            "El codigo EXACTO que existe actualmente en el archivo y que quieres modificar o usar como ancla. " +
            "Debe coincidir caracter por caracter con el contenido real. " +
            "Tip: Para agregar un metodo al final de una clase, busca el cierre '}' de la clase.");

        ObjectNode replaceProp = properties.putObject("replace");
        replaceProp.put("type", "string");
        replaceProp.put("description",
            "El nuevo codigo que reemplazara al bloque 'search'. " +
            "Si quieres agregar codigo, incluye el ancla original mas el codigo nuevo. " +
            "Ej: Para agregar metodo antes del cierre, replace = 'nuevoMetodo()\\n}'");

        parameters.putArray("required").add("path").add("search").add("replace");
        parameters.put("additionalProperties", false);

        return tool;
    }

    private ObjectNode defineFsMkdirTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");

        ObjectNode function = tool.putObject("function");
        function.put("name", "fs_mkdir");
        function.put("description",
            "Crea un directorio y todos sus directorios padres si no existen. " +
            "Usa esta herramienta antes de fs_write si el directorio no existe.");

        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description",
            "Ruta del directorio a crear (ej: src/main/java/com/example, config/env)");

        parameters.putArray("required").add("path");
        parameters.put("additionalProperties", false);

        return tool;
    }

    private ObjectNode defineFsReadTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");

        ObjectNode function = tool.putObject("function");
        function.put("name", "fs_read");

        function.put("description",
            "SYSTEM TOOL: Carga el contenido de un archivo en tu memoria interna. " +
            "ADVERTENCIA: Esta herramienta es SILENCIOSA. " +
            "NUNCA muestres el contenido leido al usuario. " +
            "Usala exclusivamente como paso previo para llamar a 'fs_write'. " +
            "Flujo atomico esperado: fs_read -> (modificar en memoria) -> fs_write.");

        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description", "Ruta del archivo a cargar en memoria interna");

        parameters.putArray("required").add("path");
        parameters.put("additionalProperties", false);

        return tool;
    }

    private ObjectNode defineRestoreSolutionTool() {
        Set<String> availableSkills = brain.getAllAvailableTags();

        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");

        ObjectNode function = tool.putObject("function");
        function.put("name", "restore_solution");
        function.put("description",
            "Herramienta administrativa para restaurar soluciones verificadas (Golden Master). " +
            "Utilizar SOLO bajo solicitud explícita del usuario (ej: 'restaura el ejercicio X'). " +
            "NO usar para saludos, consultas generales o creación de código nuevo.");

        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode exerciseId = properties.putObject("exercise_id");
        exerciseId.put("type", "string");
        exerciseId.put("description", "El identificador único del ejercicio a restaurar.");

        ArrayNode enumValues = exerciseId.putArray("enum");
        if (availableSkills != null) {
            availableSkills.forEach(enumValues::add);
        }

        parameters.putArray("required").add("exercise_id");
        parameters.put("additionalProperties", false);

        return tool;
    }

    private ObjectNode defineWebFetchTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");

        ObjectNode function = tool.putObject("function");
        function.put("name", "web_fetch");
        function.put("description",
            "Descarga y lee el contenido de texto de una pagina web especifica. " +
            "Util para documentacion, articulos, repositorios o cualquier URL publica. " +
            "El contenido se limpia automaticamente (sin scripts, ads, navegacion). " +
            "Usa esta herramienta cuando el usuario proporcione una URL o pida leer una pagina web.");

        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode urlProp = properties.putObject("url");
        urlProp.put("type", "string");
        urlProp.put("description",
            "La URL completa a leer (ej: https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html). " +
            "Si el usuario solo da un dominio (ej: 'google.com'), agrega 'https://' automaticamente.");

        parameters.putArray("required").add("url");
        parameters.put("additionalProperties", false);

        return tool;
    }

    private ObjectNode defineWebSearchTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");

        ObjectNode function = tool.putObject("function");
        function.put("name", "web_search");
        function.put("description",
            "Realiza una busqueda en internet para encontrar informacion actual. " +
            "Retorna una lista de resultados con titulo, URL y snippet. " +
            "Usa esta herramienta cuando el usuario pregunte sobre algo que requiera " +
            "informacion actualizada o cuando no tengas una URL especifica. " +
            "IMPORTANTE: Esta herramienta retorna RESULTADOS de busqueda, no contenido completo. " +
            "Si necesitas leer una pagina completa, usa web_fetch con la URL del resultado.");

        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode queryProp = properties.putObject("query");
        queryProp.put("type", "string");
        queryProp.put("description",
            "Terminos de busqueda (ej: 'Java 21 Virtual Threads tutorial', " +
            "'Spring Boot 3.2 new features', 'quien es Eber Cruz Fararoni').");

        parameters.putArray("required").add("query");
        parameters.put("additionalProperties", false);

        return tool;
    }

    private ObjectNode defineEmailFetchTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");

        ObjectNode function = tool.putObject("function");
        function.put("name", "email_fetch");
        function.put("description",
            "Lista correos electronicos de una carpeta del buzon. " +
            "Retorna remitente, asunto, fecha y un snippet de cada correo. " +
            "Usa esta herramienta cuando el usuario pida revisar, listar o contar sus correos.");

        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode folderProp = properties.putObject("folder");
        folderProp.put("type", "string");
        folderProp.put("description",
            "Carpeta IMAP a consultar (ej: 'INBOX', 'Sent', 'Drafts'). Por defecto: INBOX.");

        ObjectNode limitProp = properties.putObject("limit");
        limitProp.put("type", "integer");
        limitProp.put("description",
            "Numero maximo de correos a retornar. Por defecto: 10.");

        ObjectNode unreadProp = properties.putObject("unread_only");
        unreadProp.put("type", "boolean");
        unreadProp.put("description",
            "Si true, solo retorna correos no leidos. Por defecto: true.");

        parameters.putArray("required");
        parameters.put("additionalProperties", false);

        return tool;
    }

    private ObjectNode defineEmailSendTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");

        ObjectNode function = tool.putObject("function");
        function.put("name", "email_send");
        function.put("description",
            "Envia un correo electronico a traves del servidor SMTP configurado. " +
            "Usa esta herramienta cuando el usuario pida enviar, redactar o responder un email.");

        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode toProp = properties.putObject("to");
        toProp.put("type", "string");
        toProp.put("description", "Direccion de correo del destinatario.");

        ObjectNode subjectProp = properties.putObject("subject");
        subjectProp.put("type", "string");
        subjectProp.put("description", "Asunto del correo.");

        ObjectNode bodyProp = properties.putObject("body");
        bodyProp.put("type", "string");
        bodyProp.put("description", "Cuerpo del correo en texto plano.");

        parameters.putArray("required").add("to").add("subject").add("body");
        parameters.put("additionalProperties", false);

        return tool;
    }

    private ObjectNode defineEmailReadTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");

        ObjectNode function = tool.putObject("function");
        function.put("name", "email_read");
        function.put("description",
            "Lee el contenido completo de un correo especifico por su Message-ID. " +
            "Incluye headers completos, cuerpo y lista de adjuntos. " +
            "Usa email_fetch primero para obtener el Message-ID del correo que quieres leer.");

        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode msgIdProp = properties.putObject("message_id");
        msgIdProp.put("type", "string");
        msgIdProp.put("description",
            "Message-ID unico del correo a leer (obtenido de email_fetch).");

        parameters.putArray("required").add("message_id");
        parameters.put("additionalProperties", false);

        return tool;
    }

    private ObjectNode defineConfigSetTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");

        ObjectNode function = tool.putObject("function");
        function.put("name", "config_set");
        function.put("description",
            "Establece un valor de configuracion del sistema Fararoni. " +
            "Usa esta herramienta para configurar credenciales, servidores SMTP/IMAP, " +
            "API keys, y parametros del LLM. " +
            "Las passwords se encriptan automaticamente con AES-256-GCM. " +
            "Claves de email: mail-host, mail-port, mail-username, mail-password, " +
            "mail-imap-host, mail-imap-port, mail-sender, mail-sender-name. " +
            "Claves generales: api-key, server-url, model-name, llm-provider.");

        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode keyProp = properties.putObject("key");
        keyProp.put("type", "string");
        keyProp.put("description",
            "Clave de configuracion. Claves validas: " +
            "api-key, server-url, model-name, llm-provider, " +
            "mail-host, mail-port, mail-username, mail-password, " +
            "mail-imap-host, mail-imap-port, mail-sender, mail-sender-name, " +
            "max-tokens, temperature, context-window, streaming.");

        ObjectNode valueProp = properties.putObject("value");
        valueProp.put("type", "string");
        valueProp.put("description",
            "Valor a establecer. Para passwords y API keys, se encriptara automaticamente.");

        parameters.putArray("required").add("key").add("value");
        parameters.put("additionalProperties", false);

        return tool;
    }

    private ObjectNode defineSwarmMissionTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");

        String squadDescription;
        if (activeRoles != null && !activeRoles.isEmpty()) {
            squadDescription = String.join(", ", activeRoles);
        } else {
            squadDescription = "Commander, Intel, Blueprint, Builder, Sentinel, Operator";
            LOG.fine("[ETAPA2] Usando roles por defecto (contexto no inyectado)");
        }

        ObjectNode function = mapper.createObjectNode();
        function.put("name", "start_mission");
        function.put("description",
            "[ETAPA2] Inicia una misión compleja para el ENJAMBRE de agentes en SEGUNDO PLANO. " +
            "Los agentes disponibles son: [" + squadDescription + "]. Estos trabajan " +
            "en paralelo usando Virtual Threads mientras el usuario puede seguir interactuando. " +
            "La herramienta retorna un ID de seguimiento INMEDIATAMENTE (no espera el resultado). " +
            "USAR cuando: proyectos completos, análisis profundos, flujos de trabajo de múltiples pasos. " +
            "NO USAR cuando: cambios simples de un archivo (usar fs_write), " +
            "consultas informativas (responder directamente), ediciones menores (usar fs_patch). " +
            "Tras usar esta herramienta, despídete del usuario indicando que el equipo está trabajando.");

        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        ObjectNode missionGoal = mapper.createObjectNode();
        missionGoal.put("type", "string");
        missionGoal.put("description",
            "El objetivo de NEGOCIO de la misión a realizar. " +
            "Incluir: objetivo funcional, requisitos de negocio, restricciones del usuario. " +
            "NO incluir recomendaciones de tecnologías ni librerías (el sistema las elegirá). " +
            "NO mencionar Lombok, MapStruct, o frameworks específicos.");
        properties.set("mission_goal", missionGoal);

        ObjectNode templateId = mapper.createObjectNode();
        templateId.put("type", "string");
        templateId.put("description",
            "El ID de la plantilla de misión a usar. " +
            "Determina qué flujo de agentes se ejecutará.");

        if (activeTemplates != null && !activeTemplates.isEmpty()) {
            ArrayNode enumNode = mapper.createArrayNode();
            activeTemplates.forEach(enumNode::add);
            templateId.set("enum", enumNode);
            templateId.put("description",
                "El ID de la plantilla de misión. Opciones disponibles: " +
                String.join(", ", activeTemplates));
        }
        properties.set("template_id", templateId);

        ObjectNode defconLevel = mapper.createObjectNode();
        defconLevel.put("type", "integer");
        defconLevel.put("description",
            "Nivel de complejidad/urgencia (DEFCON militar). " +
            "5=Simple, 3=Medio, 1=Crítico. Default: 5");
        defconLevel.put("default", 5);
        properties.set("defcon_level", defconLevel);

        ObjectNode waitForCompletion = mapper.createObjectNode();
        waitForCompletion.put("type", "boolean");
        waitForCompletion.put("description",
            "Si true, espera a que la misión termine. Si false, retorna inmediatamente con ticket. " +
            "Default: false (async)");
        waitForCompletion.put("default", false);
        properties.set("wait_for_completion", waitForCompletion);

        parameters.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("mission_goal");
        parameters.set("required", required);

        function.set("parameters", parameters);
        tool.set("function", function);

        return tool;
    }

    private void addQwenNativeTools(List<ObjectNode> tools) {
        tools.add(defineTaskCreateTool());
        tools.add(defineTaskUpdateTool());
        tools.add(defineWriteFileAlias());
        tools.add(defineReadFileAlias());
    }

    private ObjectNode defineTaskCreateTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "TaskCreate");
        function.put("description", "Crea una nueva tarea o misión en el sistema. " +
            "Usa esto para inicializar tu estado cognitivo antes de trabajos complejos.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("subject")
            .put("type", "string")
            .put("description", "Título corto de la tarea (máx 70 chars)");
        props.putObject("description")
            .put("type", "string")
            .put("description", "Detalles completos de la misión");
        props.putObject("activeForm")
            .put("type", "string")
            .put("description", "Texto de spinner/contexto UI (ej: 'Analizando...')");

        params.putArray("required").add("subject").add("description");
        return tool;
    }

    private ObjectNode defineTaskUpdateTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "TaskUpdate");
        function.put("description", "Actualiza el estado de la tarea actual. " +
            "Úsalo para marcar progreso o finalización.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("taskId").put("type", "string");
        ObjectNode statusProp = props.putObject("status");
        statusProp.put("type", "string");
        ArrayNode enumValues = statusProp.putArray("enum");
        enumValues.add("in_progress").add("completed").add("failed").add("blocked");

        props.putObject("comment")
            .put("type", "string")
            .put("description", "Nota sobre la actualización");

        params.putArray("required").add("taskId").add("status");
        return tool;
    }

    private ObjectNode defineWriteFileAlias() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "WriteFile");
        function.put("description", "Crea o sobrescribe un archivo con el contenido dado. " +
            "Equivalente a fs_write pero con nombres de parámetros nativos de Qwen.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("filePath")
            .put("type", "string")
            .put("description", "Ruta absoluta o relativa del archivo");
        props.putObject("content")
            .put("type", "string")
            .put("description", "Contenido a escribir en el archivo");

        params.putArray("required").add("filePath").add("content");
        return tool;
    }

    private ObjectNode defineReadFileAlias() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "ReadFile");
        function.put("description", "Lee el contenido de un archivo existente. " +
            "Equivalente a fs_read pero con nombres de parámetros nativos de Qwen.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("filePath")
            .put("type", "string")
            .put("description", "Ruta del archivo a leer");

        params.putArray("required").add("filePath");
        return tool;
    }

    private void addQwenExtendedTools(List<ObjectNode> tools) {
        tools.add(defineListFilesTool());
        tools.add(defineFileSearchTool());
        tools.add(defineGlobGetTool());
        tools.add(defineDeepScanTool());

        tools.add(defineTaskGetTool());
        tools.add(defineTaskListTool());
        tools.add(defineTaskSearchTool());

        tools.add(defineTaskStartTool());
        tools.add(defineTaskStopTool());
        tools.add(defineTaskStopAllTool());
        tools.add(defineCommentCreateTool());

        tools.add(defineProjectListTool());
        tools.add(defineProjectGetTool());
        tools.add(defineProjectCreateTool());

        tools.add(defineCodeReviewRequestTool());
        tools.add(defineCodeReviewApproveTool());
        tools.add(defineCodeReviewRejectTool());

        tools.add(defineEnterPlanModeTool());
        tools.add(defineExitPlanModeTool());

        tools.add(defineShellCommandTool());
        tools.add(defineGitActionTool());
    }

    private ObjectNode defineListFilesTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "ListFiles");
        function.put("description",
            "Lista archivos y directorios del proyecto de forma estructurada. " +
            "Usa esta herramienta para explorar la estructura antes de leer o crear archivos. " +
            "Retorna un árbol visual limpio (sin .git, target, node_modules).");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("directoryPath")
            .put("type", "string")
            .put("description", "Ruta del directorio a listar (default: directorio actual)");

        params.putArray("required");
        return tool;
    }

    private ObjectNode defineFileSearchTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "FileSearch");
        function.put("description",
            "Busca archivos por contenido de texto. Equivalente a grep. " +
            "Útil para encontrar dónde se usa una clase, método o variable. " +
            "Retorna archivo:línea:contenido para cada coincidencia.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("query")
            .put("type", "string")
            .put("description", "Texto a buscar (ej: 'class User', 'import java.util')");
        props.putObject("fileType")
            .put("type", "string")
            .put("description", "Extensión de archivos a buscar (ej: 'java', 'xml', 'md'). Opcional.");

        params.putArray("required").add("query");
        return tool;
    }

    private ObjectNode defineGlobGetTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "GlobGet");
        function.put("description",
            "Busca archivos usando patrones glob. Equivalente a find. " +
            "Útil para encontrar archivos por nombre o extensión. " +
            "Ejemplos de patrones: '**/*.java', '**/Test*.java', 'src/**/*.xml'");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("pattern")
            .put("type", "string")
            .put("description", "Patrón glob (ej: '**/*.java', 'src/**/model/*.java')");

        params.putArray("required").add("pattern");
        return tool;
    }

    private ObjectNode defineDeepScanTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "DeepScan");
        function.put("description",
            "Genera un mapa estructural PROFUNDO del proyecto (hasta 10 niveles de profundidad). " +
            "Úsalo para entender arquitecturas complejas, ver la estructura completa de paquetes, " +
            "o cuando necesites visibilidad total del proyecto. " +
            "NOTA: Produce output extenso (~300 líneas). Usar con moderación.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("directory")
            .put("type", "string")
            .put("description", "Directorio específico a escanear (default: raíz del proyecto)");

        params.putArray("required");
        return tool;
    }

    private ObjectNode defineTaskGetTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "TaskGet");
        function.put("description",
            "Obtiene los detalles completos de una tarea por su ID. " +
            "Incluye: subject, description, status, owner, blocks, blockedBy.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("taskId")
            .put("type", "string")
            .put("description", "ID de la tarea a consultar (ej: 'task-12345')");

        params.putArray("required").add("taskId");
        return tool;
    }

    private ObjectNode defineTaskListTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "TaskList");
        function.put("description",
            "Lista todas las tareas existentes en el sistema. " +
            "Retorna un array con: id, subject, status, owner para cada tarea.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        params.putObject("properties");
        params.putArray("required");
        return tool;
    }

    private ObjectNode defineTaskSearchTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "TaskSearch");
        function.put("description",
            "Busca tareas existentes por palabra clave o estado. " +
            "Úsalo antes de crear una tarea nueva para evitar duplicados. " +
            "Retorna un array de tareas que coinciden con la búsqueda.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("query")
            .put("type", "string")
            .put("description", "Palabras clave para buscar (ej: 'login bug', 'refactor')");
        props.putObject("status")
            .put("type", "string")
            .put("description", "Filtrar por estado: pending, in_progress, completed, failed");

        params.putArray("required").add("query");
        return tool;
    }

    private ObjectNode defineTaskStartTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "TaskStart");
        function.put("description",
            "Marca una tarea como iniciada (status: in_progress). " +
            "Úsalo cuando comiences a trabajar en una tarea específica.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("taskId")
            .put("type", "string")
            .put("description", "ID de la tarea a iniciar");

        params.putArray("required").add("taskId");
        return tool;
    }

    private ObjectNode defineTaskStopTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "TaskStop");
        function.put("description",
            "Pausa una tarea específica (status: paused). " +
            "Úsalo cuando necesites interrumpir el trabajo en una tarea.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("taskId")
            .put("type", "string")
            .put("description", "ID de la tarea a pausar");

        params.putArray("required").add("taskId");
        return tool;
    }

    private ObjectNode defineTaskStopAllTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "TaskStopAll");
        function.put("description",
            "Pausa todas las tareas activas del sistema. " +
            "Úsalo para limpiar el estado antes de una nueva sesión.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        params.putObject("properties");
        params.putArray("required");
        return tool;
    }

    private ObjectNode defineCommentCreateTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "CommentCreate");
        function.put("description",
            "Agrega un comentario o nota a una tarea existente. " +
            "Útil para documentar progreso, problemas o decisiones.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("taskId")
            .put("type", "string")
            .put("description", "ID de la tarea");
        props.putObject("comment")
            .put("type", "string")
            .put("description", "Texto del comentario");

        params.putArray("required").add("taskId").add("comment");
        return tool;
    }

    private ObjectNode defineProjectListTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "ProjectList");
        function.put("description",
            "Lista todos los proyectos/módulos disponibles en el workspace. " +
            "Retorna ID, nombre y estado de cada proyecto.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        params.putObject("properties");
        params.putArray("required");
        return tool;
    }

    private ObjectNode defineProjectGetTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "ProjectGet");
        function.put("description",
            "Obtiene los detalles completos de un proyecto por su ID. " +
            "Incluye nombre, descripción, estado y configuración.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("projectId")
            .put("type", "string")
            .put("description", "ID del proyecto a consultar");

        params.putArray("required").add("projectId");
        return tool;
    }

    private ObjectNode defineProjectCreateTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "ProjectCreate");
        function.put("description",
            "Crea un nuevo proyecto o módulo en el workspace. " +
            "Configura la estructura básica de directorios.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("name")
            .put("type", "string")
            .put("description", "Nombre del proyecto");
        props.putObject("description")
            .put("type", "string")
            .put("description", "Descripción del proyecto (opcional)");

        params.putArray("required").add("name");
        return tool;
    }

    private ObjectNode defineCodeReviewRequestTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "CodeReviewRequest");
        function.put("description",
            "Solicita una revisión de código para una tarea. " +
            "Notifica al revisor (QA/Lead) que el código está listo.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("taskId")
            .put("type", "string")
            .put("description", "ID de la tarea a revisar");
        props.putObject("reviewer")
            .put("type", "string")
            .put("description", "ID del revisor (opcional, default: auto-assign)");

        params.putArray("required").add("taskId");
        return tool;
    }

    private ObjectNode defineCodeReviewApproveTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "CodeReviewApprove");
        function.put("description",
            "Aprueba una revisión de código pendiente. " +
            "Marca la tarea como lista para merge/deploy.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("taskId")
            .put("type", "string")
            .put("description", "ID de la tarea a aprobar");

        params.putArray("required").add("taskId");
        return tool;
    }

    private ObjectNode defineCodeReviewRejectTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "CodeReviewReject");
        function.put("description",
            "Rechaza una revisión de código con razones específicas. " +
            "La tarea vuelve a estado 'needs_work'.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("taskId")
            .put("type", "string")
            .put("description", "ID de la tarea a rechazar");
        props.putObject("reason")
            .put("type", "string")
            .put("description", "Razón del rechazo (obligatorio)");

        params.putArray("required").add("taskId").add("reason");
        return tool;
    }

    private ObjectNode defineEnterPlanModeTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "EnterPlanMode");
        function.put("description",
            "Activa el modo de planificación profunda. " +
            "En este modo, enfócate en pensar paso a paso antes de ejecutar. " +
            "Usa esto cuando la tarea sea compleja y necesites estructurar tu enfoque.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        params.putObject("properties");
        params.putArray("required");
        return tool;
    }

    private ObjectNode defineExitPlanModeTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "ExitPlanMode");
        function.put("description",
            "Desactiva el modo de planificación y entra en modo ejecución. " +
            "Usa esto cuando ya tienes un plan claro y estás listo para implementar.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        params.putObject("properties");
        params.putArray("required");
        return tool;
    }

    private ObjectNode defineShellCommandTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "ShellCommand");
        function.put("description",
            "Ejecuta comandos del sistema con whitelist de seguridad. " +
            "Nivel 0: comandos de lectura (cat, find, tree, wc, head, tail, ls, pwd). " +
            "Nivel 1: compilacion (mvn, gradle, npm, go). " +
            "Nivel 2: ejecucion restringida (java -jar, java -version). " +
            "IMPORTANTE: Para compilar proyectos Java/Maven, usa SIEMPRE 'mvn compile' o 'mvn clean compile'. " +
            "NO uses 'mvn install' ni 'mvn test' a menos que el usuario lo pida explicitamente. " +
            "Usa working_directory para especificar el directorio del proyecto.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("command")
            .put("type", "string")
            .put("description", "Comando a ejecutar. Solo comandos permitidos por whitelist.");

        props.putObject("working_directory")
            .put("type", "string")
            .put("description", "Directorio relativo donde ejecutar el comando. " +
                "Si no se especifica, usa el directorio de trabajo actual del proyecto.");

        props.putObject("timeout")
            .put("type", "integer")
            .put("description", "Timeout en segundos (default: 10 para Nivel 0, 120 para Nivel 1/2, max: 300)");

        params.putArray("required").add("command");
        return tool;
    }

    private ObjectNode defineGitActionTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "GitAction");
        function.put("description",
            "Ejecuta operaciones de Git con proteccion de ramas efimeras. " +
            "LECTURA (status, log, diff, show): se ejecutan directamente. " +
            "ESCRITURA (add, commit, checkout, branch, stash, init): " +
            "crean automaticamente una rama efimera para aislar cambios. " +
            "BLOQUEADO: push, pull, fetch, reset --hard, clean -f. " +
            "COMMITS: El mensaje DEBE seguir Conventional Commits (Angular). " +
            "Formato: <type>(<scope>): <descripcion>. " +
            "Tipos: feat, fix, docs, style, refactor, perf, test, chore. " +
            "Ejemplo: feat(auth): add JWT security filters. " +
            "MISION CRITICA: Al terminar los cambios, DEBES ejecutar action=finalize " +
            "para consolidar tu trabajo en la rama principal (squash merge). " +
            "Un commit local en rama wip NO completa la tarea. " +
            "discard: descarta cambios y restaura la rama original.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        ObjectNode actionProp = props.putObject("action");
        actionProp.put("type", "string");
        actionProp.put("description", "Accion Git a ejecutar");
        ArrayNode actionEnum = actionProp.putArray("enum");
        actionEnum.add("status").add("log").add("diff").add("show")
            .add("add").add("commit").add("checkout").add("branch")
            .add("stash").add("init").add("finalize").add("discard");

        props.putObject("params")
            .put("type", "string")
            .put("description",
                "Parametros: commit '-m <type>(<scope>): <descripcion>' " +
                "(SIEMPRE Conventional Commits), add '. o archivo.java', " +
                "log '--oneline -5', finalize 'descripcion del cambio'");

        params.putArray("required").add("action");
        return tool;
    }

    @SuppressWarnings("unused")
    private ObjectNode defineExecuteCodeTool() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "ExecuteCode");
        function.put("description",
            "[DESHABILITADO] Ejecuta código en memoria (Python, JavaScript, etc). " +
            "ESTA HERRAMIENTA ESTÁ DESHABILITADA POR SEGURIDAD. " +
            "PROTOCOLO: Usa WriteFile para guardar el código y luego /run para ejecutarlo.");

        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");

        props.putObject("language")
            .put("type", "string")
            .put("description", "Lenguaje: python, javascript, bash (DESHABILITADO)");
        props.putObject("code")
            .put("type", "string")
            .put("description", "Código a ejecutar (DESHABILITADO - usar WriteFile)");

        params.putArray("required").add("language").add("code");
        return tool;
    }
}
