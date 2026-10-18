package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;

/**
 * The chat flood budget of one Habbo, used when {@code hotel.chat.flood.mode} is {@code budget}: as
 * in Habbo, a speaker holds at most {@link #BURST} lines and gains one back on every
 * {@link #REFILL_MS} step of the clock. A line over the budget is dropped without a mute or a
 * flood-control message; habbicons spend the same budget. Lines 600 ms apart all go out, lines
 * 150 ms apart keep the first three and then one per step.
 *
 * <p>The default mode, {@code mute}, keeps the hotel's classic counter that mutes a flooding speaker.
 */
public final class ChatFloodBudget {
    public static final String MODE_KEY = "hotel.chat.flood.mode";
    public static final String MODE_MUTE = "mute";
    public static final String MODE_BUDGET = "budget";

    /** Lines a speaker holds when idle. */
    static final int BURST = 2;
    /** One line comes back on every step of this many milliseconds. */
    static final long REFILL_MS = 600L;

    private int tokens = BURST;
    private long lastSlot = Long.MIN_VALUE;
    private long lastUseMs;

    /** Whether the hotel limits chat with the budget instead of the mute. */
    public static boolean isBudgetMode() {
        ConfigurationManager configuration = WiredPlatform.configuration();
        return configuration != null
                && MODE_BUDGET.equalsIgnoreCase(
                        configuration.getValue(MODE_KEY, MODE_MUTE).trim());
    }

    /** Whether a Habbo with room rights is held to the flood limit too ({@code flood.with.rights}). */
    static boolean floodWithRights() {
        ConfigurationManager configuration = WiredPlatform.configuration();
        return configuration != null && configuration.getBoolean("flood.with.rights", false);
    }

    /**
     * Spends one line of the budget at {@code nowMs}.
     *
     * @return false when the budget is empty and the line is to be dropped
     */
    public synchronized boolean tryTake(long nowMs) {
        long slot = Math.floorDiv(nowMs, REFILL_MS);
        if (this.lastSlot != Long.MIN_VALUE && slot > this.lastSlot) {
            this.tokens = (int) Math.min(BURST, this.tokens + (slot - this.lastSlot));
        }
        this.lastSlot = slot;
        this.lastUseMs = nowMs;

        if (this.tokens < 1) {
            return false;
        }
        this.tokens--;
        return true;
    }

    /** Whether the budget is full again at {@code nowMs}, so forgetting it changes nothing. */
    synchronized boolean isRefilled(long nowMs) {
        return nowMs - this.lastUseMs >= BURST * REFILL_MS + REFILL_MS;
    }
}
