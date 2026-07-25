package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.wired.api.IWiredEffect;

/** Internal current-execution metadata without widening the plugin-facing API. */
final class WiredExecutionScope {
    private static final ThreadLocal<WiredContext> CURRENT = new ThreadLocal<>();

    private WiredExecutionScope() {}

    static void execute(IWiredEffect effect, WiredContext context) {
        WiredContext previous = CURRENT.get();
        CURRENT.set(context);
        try {
            effect.execute(context);
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    static void clearForCurrentThread() {
        CURRENT.remove();
    }
}
