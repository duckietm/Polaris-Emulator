package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.wired.core.WiredRoomTime;

/**
 * When a repeater fires. Until the room's timers are reset it keeps the shared phase, so every repeater with the same
 * interval fires together. Reset Timers restarts it: it fires on the first 500 ms room tick after the reset and then
 * every interval from there (observed on AIR: a 200 ms repeater fired 361 and 372 ms after the reset, a 600 s one
 * 140 ms after it). A tick a whole period late skips the missed periods instead of firing them at once.
 */
final class WiredRepeaterPhase {
    private long resetAt = -1L;
    private long nextDue = -1L;

    synchronized void reset(long now) {
        this.resetAt = now;
        this.nextDue = -1L;
    }

    synchronized boolean shouldFire(boolean onSharedPhase, long lastRoomTick, long now, long intervalMs) {
        if (this.resetAt < 0) {
            return onSharedPhase;
        }
        if (this.nextDue < 0) {
            this.nextDue = WiredRoomTime.firstTickAfter(lastRoomTick, this.resetAt);
        }
        if (now < this.nextDue) {
            return false;
        }
        long interval = Math.max(1L, intervalMs);
        this.nextDue += ((now - this.nextDue) / interval + 1) * interval;
        return true;
    }
}
