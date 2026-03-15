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
package dev.fararoni.enterprise.nats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class NatsSerializer {

    private final ObjectMapper mapper;

    /**
     * Creates a new serializer with default configuration.
     */
    public NatsSerializer() {
        this.mapper = new ObjectMapper();
        configureMapper();
    }

    /**
     * Creates a serializer with a custom ObjectMapper.
     *
     * @param mapper Custom ObjectMapper instance
     */
    public NatsSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
        configureMapper();
    }

    private void configureMapper() {
        // Java 8+ Date/Time support
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Lenient deserialization
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Pretty print disabled for performance
        mapper.disable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Serializes a SovereignEnvelope to bytes.
     *
     * @param envelope The envelope to serialize
     * @param <T> Payload type
     * @return UTF-8 encoded JSON bytes
     * @throws NatsSerializationException if serialization fails
     */
    public <T> byte[] serialize(SovereignEnvelope<T> envelope) {
        try {
            return mapper.writeValueAsBytes(envelope);
        } catch (JsonProcessingException e) {
            throw new NatsSerializationException("Failed to serialize envelope: " + envelope.id(), e);
        }
    }

    /**
     * Deserializes bytes to a SovereignEnvelope.
     *
     * @param data UTF-8 encoded JSON bytes
     * @param payloadType Expected payload type
     * @param <T> Payload type
     * @return Deserialized envelope
     * @throws NatsSerializationException if deserialization fails
     */
    @SuppressWarnings("unchecked")
    public <T> SovereignEnvelope<T> deserialize(byte[] data, Class<T> payloadType) {
        try {
            // Deserialize envelope - Jackson handles type conversion
            return (SovereignEnvelope<T>) mapper.readValue(data, SovereignEnvelope.class);
        } catch (IOException e) {
            String preview = new String(data, 0, Math.min(100, data.length), StandardCharsets.UTF_8);
            throw new NatsSerializationException("Failed to deserialize envelope: " + preview + "...", e);
        }
    }

    /**
     * Deserializes a response payload directly.
     *
     * @param data UTF-8 encoded JSON bytes
     * @param responseType Expected response type
     * @param <R> Response type
     * @return Deserialized response
     * @throws NatsSerializationException if deserialization fails
     */
    public <R> R deserializeResponse(byte[] data, Class<R> responseType) {
        try {
            return mapper.readValue(data, responseType);
        } catch (IOException e) {
            throw new NatsSerializationException("Failed to deserialize response", e);
        }
    }

    /**
     * Gets the underlying ObjectMapper for custom configuration.
     *
     * @return The ObjectMapper instance
     */
    public ObjectMapper getMapper() {
        return mapper;
    }

    /**
     * Exception thrown when serialization/deserialization fails.
     */
    public static class NatsSerializationException extends RuntimeException {
        public NatsSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
