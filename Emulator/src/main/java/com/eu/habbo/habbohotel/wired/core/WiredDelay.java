package com.eu.habbo.habbohotel.wired.core;

/**
 * An action's delay as Habbo encodes it. Standard: a positive number of 500 ms pulses. Precise: a
 * negative number of milliseconds, in steps of 50 ms ({@code -(steps * 50)}). Zero runs at once.
 */
public final class WiredDelay {
    public static final long PULSE_MS = 500L;
    public static final int PRECISE_STEP_MS = 50;
    public static final int MAX_PRECISE_STEPS = 1200;

    private WiredDelay() {}

    public static boolean isDelayed(int delay) {
        return delay != 0;
    }

    public static long toMillis(int delay) {
        if (delay > 0) {
            return delay * PULSE_MS;
        }

        return delay < 0 ? -(long) delay : 0L;
    }

    /**
     * Clamps a delay to at most {@code maxPulses} pulses of time. A precise delay keeps its 50 ms
     * steps (at most 1200 of them) and the same time limit.
     */
    public static int normalize(int delay, int maxPulses) {
        int pulses = Math.max(0, maxPulses);

        if (delay >= 0) {
            return Math.min(delay, pulses);
        }

        long maxSteps = Math.min(MAX_PRECISE_STEPS, (pulses * PULSE_MS) / PRECISE_STEP_MS);
        long steps = Math.min(maxSteps, Math.round(-(double) delay / PRECISE_STEP_MS));

        return (int) -(steps * PRECISE_STEP_MS);
    }

    /** Whether a saved delay is within {@code maxPulses} pulses of time. */
    public static boolean isValid(int delay, int maxPulses) {
        return normalize(delay, maxPulses) == delay;
    }
}
