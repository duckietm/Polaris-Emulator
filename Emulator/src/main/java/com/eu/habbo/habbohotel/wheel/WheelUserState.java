package com.eu.habbo.habbohotel.wheel;

// Per-user spin balance. freeSpins resets daily (lazy, on access); extraSpins persist.
public class WheelUserState {
    public int freeSpins;
    public int extraSpins;
    public int lastReset; // day index (unix / 86400) of the last daily reset

    public int totalSpins() {
        return this.freeSpins + this.extraSpins;
    }
}
