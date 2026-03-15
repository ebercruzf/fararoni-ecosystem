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
package dev.fararoni.bus.agent.api;

import java.util.List;

/**
 * Exception thrown when parameter validation fails during tool invocation.
 *
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ParameterValidationException extends RuntimeException {

    private final String parameterName;
    private final String expectedType;
    private final Object actualValue;
    private final List<String> expectedParameters;

    /**
     * Creates a new exception for a missing required parameter.
     *
     * @param parameterName the missing parameter name
     */
    public ParameterValidationException(String parameterName) {
        super(String.format("Required parameter '%s' is missing", parameterName));
        this.parameterName = parameterName;
        this.expectedType = null;
        this.actualValue = null;
        this.expectedParameters = List.of();
    }

    /**
     * Creates a new exception for a type mismatch.
     *
     * @param parameterName the parameter name
     * @param expectedType  the expected type
     * @param actualValue   the actual value received
     */
    public ParameterValidationException(String parameterName, String expectedType, Object actualValue) {
        super(String.format("Parameter '%s' expected type '%s' but got '%s'",
            parameterName, expectedType, actualValue != null ? actualValue.getClass().getSimpleName() : "null"));
        this.parameterName = parameterName;
        this.expectedType = expectedType;
        this.actualValue = actualValue;
        this.expectedParameters = List.of();
    }

    /**
     * Creates a new exception with expected parameters list.
     *
     * @param message            the error message
     * @param expectedParameters list of expected parameter names
     */
    public ParameterValidationException(String message, List<String> expectedParameters) {
        super(message + ". Expected parameters: " + expectedParameters);
        this.parameterName = null;
        this.expectedType = null;
        this.actualValue = null;
        this.expectedParameters = List.copyOf(expectedParameters);
    }

    /**
     * Returns the parameter name.
     *
     * @return the parameter name
     */
    public String getParameterName() {
        return parameterName;
    }

    /**
     * Returns the expected type.
     *
     * @return the expected type
     */
    public String getExpectedType() {
        return expectedType;
    }

    /**
     * Returns the actual value received.
     *
     * @return the actual value
     */
    public Object getActualValue() {
        return actualValue;
    }

    /**
     * Returns the list of expected parameters.
     *
     * @return expected parameter names
     */
    public List<String> getExpectedParameters() {
        return expectedParameters;
    }
}
