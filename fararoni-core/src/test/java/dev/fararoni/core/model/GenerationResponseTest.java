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
package dev.fararoni.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
class GenerationResponseTest {
    @Test
    @DisplayName("Should create successful response")
    void shouldCreateSuccessfulResponse() {
        var usage = GenerationResponse.Usage.of(50, 100);
        var response = GenerationResponse.success("Test response", usage, 1000L);

        assertThat(response.text()).isEqualTo("Test response");
        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.usage().totalTokens()).isEqualTo(150);
    }

    @Test
    @DisplayName("Should handle usage calculations")
    void shouldHandleUsageCalculations() {
        var usage = GenerationResponse.Usage.of(25, 75, 2000L);

        assertThat(usage.totalTokens()).isEqualTo(100);
        assertThat(usage.tokensPerSecond()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should validate response quality")
    void shouldValidateResponseQuality() {
        var fastUsage = GenerationResponse.Usage.of(10, 100, 1000L);
        var fastResponse = GenerationResponse.success("Fast", fastUsage, 1000L);

        assertThat(fastResponse.getQuality()).isIn(
            GenerationResponse.ResponseQuality.EXCELLENT,
            GenerationResponse.ResponseQuality.GOOD
        );
    }

    @Test
    @DisplayName("Should handle error responses")
    void shouldHandleErrorResponses() {
        var errorResponse = GenerationResponse.error("Error occurred");

        assertThat(errorResponse.isError()).isTrue();
        assertThat(errorResponse.isSuccessful()).isFalse();
        assertThat(errorResponse.getQuality()).isEqualTo(GenerationResponse.ResponseQuality.ERROR);
    }
}
