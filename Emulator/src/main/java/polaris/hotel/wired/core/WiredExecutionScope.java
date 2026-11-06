package polaris.hotel.wired.core;

import polaris.hotel.wired.api.IWiredEffect;

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

    /** Whether a wired effect is running on this thread, so an event raised now comes from wired. */
    static boolean isExecuting() {
        return CURRENT.get() != null;
    }

    static void clearForCurrentThread() {
        CURRENT.remove();
    }
}
