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
package dev.fararoni.core.core.llm;

import java.time.Duration;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class InferenceParameters {
    private double temperature = 0.2;
    private String grammar;
    private int maxTokens = 4096;
    private double topP = 0.95;
    private double frequencyPenalty = 0.0;
    private double presencePenalty = 0.0;

    private double repeatPenalty = 1.1;
    private int numCtx = 8192;
    private String stopSequence = null;
    private Duration timeout = Duration.ofSeconds(60);
    private Long seed = null;
    private String modelName = null;

    public InferenceParameters setTemperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    public InferenceParameters setGrammar(String grammar) {
        this.grammar = grammar;
        return this;
    }

    public InferenceParameters setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    public InferenceParameters setTopP(double topP) {
        this.topP = topP;
        return this;
    }

    public InferenceParameters setFrequencyPenalty(double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
        return this;
    }

    public InferenceParameters setPresencePenalty(double presencePenalty) {
        this.presencePenalty = presencePenalty;
        return this;
    }

    public double getTemperature() {
        return temperature;
    }

    public String getGrammar() {
        return grammar;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTopP() {
        return topP;
    }

    public double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public double getPresencePenalty() {
        return presencePenalty;
    }

    public boolean hasGrammar() {
        return grammar != null && !grammar.isBlank();
    }

    public InferenceParameters setRepeatPenalty(double repeatPenalty) {
        this.repeatPenalty = repeatPenalty;
        return this;
    }

    public InferenceParameters setNumCtx(int numCtx) {
        this.numCtx = numCtx;
        return this;
    }

    public InferenceParameters setStopSequence(String stopSequence) {
        this.stopSequence = stopSequence;
        return this;
    }

    public InferenceParameters setTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public InferenceParameters setSeed(Long seed) {
        this.seed = seed;
        return this;
    }

    public InferenceParameters setModel(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public double getRepeatPenalty() {
        return repeatPenalty;
    }

    public int getNumCtx() {
        return numCtx;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Optional<String> getStopSequence() {
        return Optional.ofNullable(stopSequence);
    }

    public Optional<Long> getSeed() {
        return Optional.ofNullable(seed);
    }

    public Optional<String> getModel() {
        return Optional.ofNullable(modelName);
    }
}
