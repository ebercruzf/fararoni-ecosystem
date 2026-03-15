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
package dev.fararoni.core.core.domain;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public record DomainInferenceResult(
    String domain,
    String matchedWord,
    double confidence,
    List<String> parsedWords
) {

    public boolean hasDomain() {
        return domain != null && !domain.isBlank();
    }

    public static DomainInferenceResult unknown(List<String> words) {
        return new DomainInferenceResult(null, null, 0.0, words);
    }
}
