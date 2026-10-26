package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.wired.core.WiredRoomTime;

/**
 * The clock of the At Given Time triggers. As in Habbo, the pulses count from the first 500 ms room tick after the
 * reset, not from the reset itself: four pulses fire about 2.48 s after a reset that came 20 ms after a tick.
 * Behaviour observed in plus-emulator/PlusEMU d509f4ab, reimplemented.
 */
final class WiredRoomGridTimer {
    static final long ROOM_TICK_MS = 500L;

    private long resetAt;
    private long startTick = -1L;

    /** Starts counting again from the first room tick after {@code now}. */
    synchronized void reset(long now) {
        this.resetAt = now;
        this.startTick = -1L;
    }

    /** Whether {@code delayMs} has passed since the first room tick after the reset. */
    synchronized boolean isDue(long lastRoomTick, long now, long delayMs) {
        if (this.resetAt <= 0) {
            // Never reset yet (a timer that ticks before it was registered): count from now.
            this.resetAt = now;
        }
        if (this.startTick < 0) {
            this.startTick = firstTickAfter(lastRoomTick, this.resetAt);
        }
        return now >= this.startTick + delayMs;
    }

    /** The first 500 ms room tick strictly after {@code instant}. */
    static long firstTickAfter(long lastRoomTick, long instant) {
        return WiredRoomTime.firstTickAfter(lastRoomTick, instant);
    }
}
