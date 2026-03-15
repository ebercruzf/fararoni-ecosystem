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
package dev.fararoni.core.core.context;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ExecutionContext {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private Consumer<String> onCancelAction;
    private String cancelReason;

    public void checkCancelled() throws InterruptedException {
        if (cancelled.get()) {
            throw new InterruptedException(
                cancelReason != null ? cancelReason : "Operacion cancelada por el usuario"
            );
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel(String reason) {
        if (cancelled.compareAndSet(false, true)) {
            this.cancelReason = reason;
            if (onCancelAction != null) {
                onCancelAction.accept(reason);
            }
        }
    }

    public void onCancel(Consumer<String> action) {
        this.onCancelAction = action;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public static ExecutionContext immortal() {
        return new ExecutionContext() {
            @Override
            public void cancel(String reason) {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void checkCancelled() {
            }
        };
    }
}
