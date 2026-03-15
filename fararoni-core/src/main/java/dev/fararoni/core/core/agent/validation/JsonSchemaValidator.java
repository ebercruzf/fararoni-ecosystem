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
package dev.fararoni.core.core.agent.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class JsonSchemaValidator {
    private static final Logger LOG = Logger.getLogger(JsonSchemaValidator.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(
        SpecVersion.VersionFlag.V202012
    );

    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    public List<String> validate(String json, String schema) {
        if (json == null || json.isBlank()) {
            return List.of("JSON es null o vacio");
        }
        if (schema == null || schema.isBlank()) {
            return List.of();
        }

        try {
            JsonNode jsonNode = MAPPER.readTree(json);

            JsonSchema jsonSchema = getOrCompileSchema(schema);

            Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);

            if (errors.isEmpty()) {
                return List.of();
            }

            return errors.stream()
                .map(ValidationMessage::getMessage)
                .toList();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return List.of("JSON invalido: " + e.getOriginalMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[JsonSchemaValidator] Error validando", e);
            return List.of("Error de validacion: " + e.getMessage());
        }
    }

    public List<String> validate(JsonNode jsonNode, String schema) {
        if (jsonNode == null) {
            return List.of("JsonNode es null");
        }
        if (schema == null || schema.isBlank()) {
            return List.of();
        }

        try {
            JsonSchema jsonSchema = getOrCompileSchema(schema);
            Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);

            if (errors.isEmpty()) {
                return List.of();
            }

            return errors.stream()
                .map(ValidationMessage::getMessage)
                .toList();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[JsonSchemaValidator] Error validando", e);
            return List.of("Error de validacion: " + e.getMessage());
        }
    }

    public boolean isValid(String json, String schema) {
        return validate(json, schema).isEmpty();
    }

    public boolean precompileSchema(String schemaId, String schema) {
        try {
            JsonNode schemaNode = MAPPER.readTree(schema);
            JsonSchema compiled = FACTORY.getSchema(schemaNode);
            schemaCache.put(schemaId, compiled);
            return true;
        } catch (Exception e) {
            LOG.warning("[JsonSchemaValidator] Error compilando schema " + schemaId + ": " + e.getMessage());
            return false;
        }
    }

    public List<String> validateWithCached(String json, String schemaId) {
        JsonSchema cached = schemaCache.get(schemaId);
        if (cached == null) {
            return List.of("Schema no encontrado en cache: " + schemaId);
        }

        try {
            JsonNode jsonNode = MAPPER.readTree(json);
            Set<ValidationMessage> errors = cached.validate(jsonNode);

            if (errors.isEmpty()) {
                return List.of();
            }

            return errors.stream()
                .map(ValidationMessage::getMessage)
                .toList();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return List.of("JSON invalido: " + e.getOriginalMessage());
        } catch (Exception e) {
            return List.of("Error de validacion: " + e.getMessage());
        }
    }

    public void clearCache() {
        schemaCache.clear();
        LOG.fine("[JsonSchemaValidator] Cache limpiado");
    }

    public int getCacheSize() {
        return schemaCache.size();
    }

    private JsonSchema getOrCompileSchema(String schema) throws Exception {
        String cacheKey = String.valueOf(schema.hashCode());

        return schemaCache.computeIfAbsent(cacheKey, k -> {
            try {
                JsonNode schemaNode = MAPPER.readTree(schema);
                return FACTORY.getSchema(schemaNode);
            } catch (Exception e) {
                throw new RuntimeException("Error compilando schema", e);
            }
        });
    }

    private static volatile JsonSchemaValidator INSTANCE;

    public static JsonSchemaValidator getInstance() {
        if (INSTANCE == null) {
            synchronized (JsonSchemaValidator.class) {
                if (INSTANCE == null) {
                    INSTANCE = new JsonSchemaValidator();
                }
            }
        }
        return INSTANCE;
    }
}
