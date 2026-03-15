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

import dev.fararoni.bus.agent.api.saga.CompensationInstruction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FNLResult record.
 */
@DisplayName("FNLResult")
class FNLResultTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("success() creates successful result with data")
        void successCreatesResultWithData() {
            FNLResult<String> result = FNLResult.success("Hello");

            assertTrue(result.success());
            assertEquals("Hello", result.data());
            assertNull(result.error());
            assertNotNull(result.timestamp());
            assertNull(result.undoInstruction());
        }

        @Test
        @DisplayName("failure() creates failed result with error message")
        void failureCreatesResultWithError() {
            FNLResult<String> result = FNLResult.failure("Something went wrong");

            assertFalse(result.success());
            assertNull(result.data());
            assertEquals("Something went wrong", result.error());
            assertNotNull(result.timestamp());
        }

        @Test
        @DisplayName("failure(Throwable) creates result from exception")
        void failureFromExceptionCreatesResult() {
            FNLResult<String> result = FNLResult.failure(new RuntimeException("Test error"));

            assertFalse(result.success());
            assertNull(result.data());
            assertEquals("Test error", result.error());
        }

        @Test
        @DisplayName("failure(Throwable) handles exception with null message")
        void failureFromExceptionHandlesNullMessage() {
            FNLResult<String> result = FNLResult.failure(new RuntimeException((String) null));

            assertFalse(result.success());
            assertEquals("RuntimeException", result.error());
        }

        @Test
        @DisplayName("successWithSaga() creates result with compensation instruction")
        void successWithSagaCreatesResultWithCompensation() {
            CompensationInstruction instruction = CompensationInstruction.of(
                "FileSkill", "delete", Map.of("path", "/tmp/test.txt")
            );

            FNLResult<String> result = FNLResult.successWithSaga("Created", instruction);

            assertTrue(result.success());
            assertEquals("Created", result.data());
            assertNotNull(result.undoInstruction());
            assertEquals("FileSkill", result.undoInstruction().skillName());
            assertTrue(result.hasSagaCompensation());
        }

        @Test
        @DisplayName("successWithSaga() throws on null instruction")
        void successWithSagaThrowsOnNullInstruction() {
            assertThrows(NullPointerException.class, () ->
                FNLResult.successWithSaga("data", null)
            );
        }
    }

    @Nested
    @DisplayName("Functional Operations")
    class FunctionalOperations {

        @Test
        @DisplayName("map() transforms data on success")
        void mapTransformsDataOnSuccess() {
            FNLResult<Integer> result = FNLResult.success(5);
            FNLResult<Integer> mapped = result.map(n -> n * 2);

            assertTrue(mapped.success());
            assertEquals(10, mapped.data());
        }

        @Test
        @DisplayName("map() propagates failure")
        void mapPropagatesFailure() {
            FNLResult<Integer> result = FNLResult.failure("Error");
            FNLResult<Integer> mapped = result.map(n -> n * 2);

            assertFalse(mapped.success());
            assertEquals("Error", mapped.error());
        }

        @Test
        @DisplayName("flatMap() chains operations on success")
        void flatMapChainsOperationsOnSuccess() {
            FNLResult<Integer> result = FNLResult.success(5);
            FNLResult<String> flatMapped = result.flatMap(n ->
                FNLResult.success("Value: " + n)
            );

            assertTrue(flatMapped.success());
            assertEquals("Value: 5", flatMapped.data());
        }

        @Test
        @DisplayName("flatMap() propagates failure from first result")
        void flatMapPropagatesFirstFailure() {
            FNLResult<Integer> result = FNLResult.failure("First error");
            FNLResult<String> flatMapped = result.flatMap(n ->
                FNLResult.success("Value: " + n)
            );

            assertFalse(flatMapped.success());
            assertEquals("First error", flatMapped.error());
        }

        @Test
        @DisplayName("flatMap() propagates failure from second result")
        void flatMapPropagatesSecondFailure() {
            FNLResult<Integer> result = FNLResult.success(5);
            FNLResult<String> flatMapped = result.flatMap(n ->
                FNLResult.failure("Second error")
            );

            assertFalse(flatMapped.success());
            assertEquals("Second error", flatMapped.error());
        }

        @Test
        @DisplayName("orElse() returns data on success")
        void orElseReturnsDataOnSuccess() {
            FNLResult<String> result = FNLResult.success("Value");
            assertEquals("Value", result.orElse("Default"));
        }

        @Test
        @DisplayName("orElse() returns default on failure")
        void orElseReturnsDefaultOnFailure() {
            FNLResult<String> result = FNLResult.failure("Error");
            assertEquals("Default", result.orElse("Default"));
        }

        @Test
        @DisplayName("toOptional() returns Optional with data on success")
        void toOptionalReturnsOptionalWithDataOnSuccess() {
            FNLResult<String> result = FNLResult.success("Value");
            Optional<String> optional = result.toOptional();

            assertTrue(optional.isPresent());
            assertEquals("Value", optional.get());
        }

        @Test
        @DisplayName("toOptional() returns empty on failure")
        void toOptionalReturnsEmptyOnFailure() {
            FNLResult<String> result = FNLResult.failure("Error");
            Optional<String> optional = result.toOptional();

            assertTrue(optional.isEmpty());
        }
    }

    @Nested
    @DisplayName("Saga Compensation")
    class SagaCompensation {

        @Test
        @DisplayName("hasSagaCompensation() returns false when no instruction")
        void hasSagaCompensationReturnsFalseWhenNoInstruction() {
            FNLResult<String> result = FNLResult.success("data");
            assertFalse(result.hasSagaCompensation());
        }

        @Test
        @DisplayName("hasSagaCompensation() returns true when instruction present")
        void hasSagaCompensationReturnsTrueWhenInstructionPresent() {
            FNLResult<String> result = FNLResult.successWithSaga(
                "data",
                CompensationInstruction.of("Skill", "method", Map.of())
            );
            assertTrue(result.hasSagaCompensation());
        }

        @Test
        @DisplayName("getCompensation() returns Optional with instruction")
        void getCompensationReturnsOptionalWithInstruction() {
            CompensationInstruction instruction = CompensationInstruction.of(
                "Skill", "method", Map.of("key", "value")
            );
            FNLResult<String> result = FNLResult.successWithSaga("data", instruction);

            Optional<CompensationInstruction> compensation = result.getCompensation();

            assertTrue(compensation.isPresent());
            assertEquals("Skill", compensation.get().skillName());
        }

        @Test
        @DisplayName("getCompensation() returns empty Optional when no instruction")
        void getCompensationReturnsEmptyOptionalWhenNoInstruction() {
            FNLResult<String> result = FNLResult.success("data");

            Optional<CompensationInstruction> compensation = result.getCompensation();

            assertTrue(compensation.isEmpty());
        }
    }

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompatibility {

        @Test
        @DisplayName("toToolResponse() creates ToolResponse on success")
        void toToolResponseCreatesToolResponseOnSuccess() {
            FNLResult<String> result = FNLResult.success("Result data");
            ToolResponse response = result.toToolResponse();

            assertTrue(response.success());
            assertEquals("Result data", response.result());
        }

        @Test
        @DisplayName("toToolResponse() creates ToolResponse on failure")
        void toToolResponseCreatesToolResponseOnFailure() {
            FNLResult<String> result = FNLResult.failure("Error message");
            ToolResponse response = result.toToolResponse();

            assertFalse(response.success());
            assertEquals("Error message", response.errorMessage());
        }

        @Test
        @DisplayName("fromToolResponse() converts success response")
        void fromToolResponseConvertsSuccessResponse() {
            ToolResponse response = ToolResponse.success("Result");
            FNLResult<String> result = FNLResult.fromToolResponse(response);

            assertTrue(result.success());
            assertEquals("Result", result.data());
        }

        @Test
        @DisplayName("fromToolResponse() converts error response")
        void fromToolResponseConvertsErrorResponse() {
            ToolResponse response = ToolResponse.error("Error");
            FNLResult<String> result = FNLResult.fromToolResponse(response);

            assertFalse(result.success());
            assertEquals("Error", result.error());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("success with null data is allowed")
        void successWithNullDataIsAllowed() {
            FNLResult<String> result = FNLResult.success(null);

            assertTrue(result.success());
            assertNull(result.data());
        }

        @Test
        @DisplayName("map handles null transformation result")
        void mapHandlesNullTransformationResult() {
            FNLResult<String> result = FNLResult.success("value");
            FNLResult<String> mapped = result.map(s -> null);

            assertTrue(mapped.success());
            assertNull(mapped.data());
        }

        @Test
        @DisplayName("orElse returns null data when success with null")
        void orElseReturnsNullDataWhenSuccessWithNull() {
            FNLResult<String> result = FNLResult.success(null);
            assertNull(result.orElse("default"));
        }
    }
}
