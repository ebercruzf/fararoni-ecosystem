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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
class JsonSchemaValidatorTest {
    private JsonSchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JsonSchemaValidator();
    }

    @Test
    void validate_validJson_returnsNoErrors() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"},
                "age": {"type": "integer"}
              },
              "required": ["name"]
            }
            """;
        String json = """
            {"name": "John", "age": 30}
            """;

        List<String> errors = validator.validate(json, schema);

        assertTrue(errors.isEmpty(), "JSON valido no deberia tener errores");
    }

    @Test
    void validate_missingRequiredField_returnsError() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"}
              },
              "required": ["name"]
            }
            """;
        String json = """
            {"age": 30}
            """;

        List<String> errors = validator.validate(json, schema);

        assertFalse(errors.isEmpty(), "Deberia tener errores por campo faltante");
        assertTrue(errors.stream().anyMatch(e -> e.contains("name") || e.contains("required")));
    }

    @Test
    void validate_wrongType_returnsError() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "age": {"type": "integer"}
              }
            }
            """;
        String json = """
            {"age": "not a number"}
            """;

        List<String> errors = validator.validate(json, schema);

        assertFalse(errors.isEmpty(), "Deberia tener errores por tipo incorrecto");
    }

    @Test
    void validate_numberOutOfRange_returnsError() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "score": {"type": "number", "minimum": 0, "maximum": 100}
              }
            }
            """;
        String json = """
            {"score": 150}
            """;

        List<String> errors = validator.validate(json, schema);

        assertFalse(errors.isEmpty(), "Deberia tener errores por valor fuera de rango");
    }

    @Test
    void validate_enumValue_valid() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "status": {"type": "string", "enum": ["APPROVE", "REJECT", "REVIEW"]}
              }
            }
            """;
        String json = """
            {"status": "APPROVE"}
            """;

        List<String> errors = validator.validate(json, schema);

        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_enumValue_invalid() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "status": {"type": "string", "enum": ["APPROVE", "REJECT", "REVIEW"]}
              }
            }
            """;
        String json = """
            {"status": "INVALID_STATUS"}
            """;

        List<String> errors = validator.validate(json, schema);

        assertFalse(errors.isEmpty());
    }

    @Test
    void validate_nullJson_returnsError() {
        String schema = """
            {"type": "object"}
            """;

        List<String> errors = validator.validate((String) null, schema);

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("null") || errors.get(0).contains("vacio"));
    }

    @Test
    void validate_emptyJson_returnsError() {
        String schema = """
            {"type": "object"}
            """;

        List<String> errors = validator.validate("", schema);

        assertFalse(errors.isEmpty());
    }

    @Test
    void validate_invalidJsonSyntax_returnsError() {
        String schema = """
            {"type": "object"}
            """;
        String invalidJson = "{not valid json}";

        List<String> errors = validator.validate(invalidJson, schema);

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("invalido") || errors.get(0).toLowerCase().contains("json"));
    }

    @Test
    void validate_nullSchema_returnsNoErrors() {
        String json = """
            {"anything": "goes"}
            """;

        List<String> errors = validator.validate(json, null);

        assertTrue(errors.isEmpty(), "Sin schema, cualquier JSON deberia ser valido");
    }

    @Test
    void validate_emptySchema_returnsNoErrors() {
        String json = """
            {"anything": "goes"}
            """;

        List<String> errors = validator.validate(json, "");

        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_nestedObject_valid() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "person": {
                  "type": "object",
                  "properties": {
                    "name": {"type": "string"},
                    "address": {
                      "type": "object",
                      "properties": {
                        "city": {"type": "string"}
                      },
                      "required": ["city"]
                    }
                  },
                  "required": ["name"]
                }
              }
            }
            """;
        String json = """
            {
              "person": {
                "name": "John",
                "address": {
                  "city": "NYC"
                }
              }
            }
            """;

        List<String> errors = validator.validate(json, schema);

        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_array_valid() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "tags": {
                  "type": "array",
                  "items": {"type": "string"}
                }
              }
            }
            """;
        String json = """
            {"tags": ["tag1", "tag2", "tag3"]}
            """;

        List<String> errors = validator.validate(json, schema);

        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_array_invalidItems() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "numbers": {
                  "type": "array",
                  "items": {"type": "integer"}
                }
              }
            }
            """;
        String json = """
            {"numbers": [1, "not a number", 3]}
            """;

        List<String> errors = validator.validate(json, schema);

        assertFalse(errors.isEmpty());
    }

    @Test
    void isValid_validJson_returnsTrue() {
        String schema = """
            {"type": "object"}
            """;
        String json = """
            {"valid": true}
            """;

        assertTrue(validator.isValid(json, schema));
    }

    @Test
    void isValid_invalidJson_returnsFalse() {
        String schema = """
            {
              "type": "object",
              "required": ["name"]
            }
            """;
        String json = """
            {}
            """;

        assertFalse(validator.isValid(json, schema));
    }

    @Test
    void validate_sameSchema_usesCachedVersion() {
        String schema = """
            {"type": "object"}
            """;
        String json1 = """
            {"a": 1}
            """;
        String json2 = """
            {"b": 2}
            """;

        validator.validate(json1, schema);
        int cacheSizeAfterFirst = validator.getCacheSize();

        validator.validate(json2, schema);
        int cacheSizeAfterSecond = validator.getCacheSize();

        assertEquals(cacheSizeAfterFirst, cacheSizeAfterSecond,
            "El cache no deberia crecer para el mismo schema");
    }

    @Test
    void clearCache_removesAllCachedSchemas() {
        String schema = """
            {"type": "object"}
            """;
        validator.validate("{}", schema);
        assertTrue(validator.getCacheSize() > 0);

        validator.clearCache();

        assertEquals(0, validator.getCacheSize());
    }

    @Test
    void getInstance_returnsSameInstance() {
        JsonSchemaValidator instance1 = JsonSchemaValidator.getInstance();
        JsonSchemaValidator instance2 = JsonSchemaValidator.getInstance();

        assertSame(instance1, instance2);
    }

    @Test
    void validate_riskAnalystSchema_fullExample() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "riskScore": {
                  "type": "number",
                  "minimum": 0,
                  "maximum": 100
                },
                "recommendation": {
                  "type": "string",
                  "enum": ["APPROVE", "APPROVE_WITH_CONDITIONS", "REVIEW", "REJECT"]
                },
                "riskFactors": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "factor": {"type": "string"},
                      "severity": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"]}
                    },
                    "required": ["factor", "severity"]
                  }
                },
                "justification": {"type": "string"}
              },
              "required": ["riskScore", "recommendation", "justification"]
            }
            """;

        String validJson = """
            {
              "riskScore": 35,
              "recommendation": "APPROVE_WITH_CONDITIONS",
              "riskFactors": [
                {"factor": "High debt ratio", "severity": "MEDIUM"},
                {"factor": "Short credit history", "severity": "LOW"}
              ],
              "conditions": ["Require co-signer", "Limit to 50% of requested amount"],
              "justification": "Applicant has good income but limited credit history and moderate debt."
            }
            """;

        List<String> errors = validator.validate(validJson, schema);

        assertTrue(errors.isEmpty(), "Respuesta de analista de riesgo deberia ser valida");
    }

    @Test
    void validate_riskAnalystSchema_missingRequired() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "riskScore": {"type": "number"},
                "recommendation": {"type": "string"},
                "justification": {"type": "string"}
              },
              "required": ["riskScore", "recommendation", "justification"]
            }
            """;

        String invalidJson = """
            {
              "riskScore": 50,
              "recommendation": "REVIEW"
            }
            """;

        List<String> errors = validator.validate(invalidJson, schema);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e ->
            e.contains("justification") || e.contains("required")));
    }
}
