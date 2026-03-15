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
class MessageTest {
    @Test
    @DisplayName("Should create message with role and content")
    void shouldCreateMessageWithRoleAndContent() {
        var message = new Message("user", "Hello, world!");

        assertThat(message.role()).isEqualTo("user");
        assertThat(message.content()).isEqualTo("Hello, world!");
    }

    @Test
    @DisplayName("Should support equality comparison")
    void shouldSupportEqualityComparison() {
        var message1 = new Message("user", "Hello");
        var message2 = new Message("user", "Hello");
        var message3 = new Message("assistant", "Hello");

        assertThat(message1).isEqualTo(message2);
        assertThat(message1).isNotEqualTo(message3);
    }

    @Test
    @DisplayName("Should handle empty content")
    void shouldHandleEmptyContent() {
        var message = new Message("system", "");

        assertThat(message.role()).isEqualTo("system");
        assertThat(message.content()).isEmpty();
    }
}
