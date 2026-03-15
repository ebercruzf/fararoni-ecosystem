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
package dev.fararoni.core.core.surgeon;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SurgeonManager {
    private static final Logger LOG = Logger.getLogger(SurgeonManager.class.getName());

    private final SmartPatcher patcher;
    private final SelfHealer healer;
    private final SelfHealer.SyntaxValidator validator;

    private OperationResult lastResult = null;

    public enum OperationResult {
        SUCCESS_DIRECT,
        SUCCESS_HEALED,
        PATCH_APPLIED_INVALID,
        PATCH_FAILED,
        ABORTED
    }

    public SurgeonManager(SelfHealer.SyntaxValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator no puede ser null");
        this.patcher = new SmartPatcher();
        this.healer = new SelfHealer(validator);
    }

    public SurgeonManager() {
        this(code -> 0);
    }

    public String operate(String originalCode, String searchBlock, String replaceBlock) {
        Objects.requireNonNull(originalCode, "originalCode no puede ser null");
        Objects.requireNonNull(searchBlock, "searchBlock no puede ser null");
        Objects.requireNonNull(replaceBlock, "replaceBlock no puede ser null");

        LOG.info("[SURGEON] Iniciando procedimiento quirurgico...");

        String newCode = patcher.applyPatch(originalCode, searchBlock, replaceBlock);

        if (newCode.equals(originalCode)) {
            LOG.warning("[SURGEON] Operacion abortada. No se pudo aplicar el parche.");
            lastResult = OperationResult.PATCH_FAILED;
            return originalCode;
        }

        LOG.info("[SURGEON] Parche aplicado (" + patcher.getLastResult() + ").");

        int errors = validator.countErrors(newCode);

        if (errors == 0) {
            LOG.info("[SURGEON] Codigo valido. Operacion exitosa.");
            lastResult = OperationResult.SUCCESS_DIRECT;
            return newCode;
        }

        LOG.info("[SURGEON] Codigo con " + errors + " errores. Iniciando auto-curacion...");

        String healedCode = healer.heal(newCode);

        if (healer.wasLastHealSuccessful()) {
            LOG.info("[SURGEON] El sistema se auto-reparo (Sintaxis corregida).");
            lastResult = OperationResult.SUCCESS_HEALED;
            return healedCode;
        }

        LOG.warning("[SURGEON] No se pudo auto-reparar el codigo.");
        lastResult = OperationResult.PATCH_APPLIED_INVALID;
        return newCode;
    }

    public String operateWithoutValidation(String originalCode, String searchBlock, String replaceBlock) {
        String newCode = patcher.applyPatch(originalCode, searchBlock, replaceBlock);

        if (newCode.equals(originalCode)) {
            lastResult = OperationResult.PATCH_FAILED;
        } else {
            lastResult = OperationResult.SUCCESS_DIRECT;
        }

        return newCode;
    }

    public OperationResult getLastResult() {
        return lastResult;
    }

    public boolean wasLastOperationSuccessful() {
        return lastResult == OperationResult.SUCCESS_DIRECT ||
               lastResult == OperationResult.SUCCESS_HEALED;
    }

    public SmartPatcher getPatcher() {
        return patcher;
    }

    public SelfHealer getHealer() {
        return healer;
    }

    public String getOperationReport() {
        if (lastResult == null) {
            return "SurgeonManager: No se ha realizado ninguna operacion.";
        }

        StringBuilder report = new StringBuilder();
        report.append("SurgeonManager Operation Report:\n");
        report.append("  - Resultado: ").append(lastResult).append("\n");
        report.append("  - Parche: ").append(patcher.getLastResult()).append("\n");

        if (lastResult == OperationResult.SUCCESS_HEALED ||
            lastResult == OperationResult.PATCH_APPLIED_INVALID) {
            report.append("  - Intentos de curacion: ").append(healer.getLastAttempts()).append("\n");
            report.append("  - Curacion exitosa: ").append(healer.wasLastHealSuccessful()).append("\n");
        }

        return report.toString();
    }
}
