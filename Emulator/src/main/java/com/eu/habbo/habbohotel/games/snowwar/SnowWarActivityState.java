package com.eu.habbo.habbohotel.games.snowwar;

/**
 * Avatar activity states (README 8.3). Timers are in subturns/frames.
 */
public enum SnowWarActivityState {
    NORMAL(0, 0),
    CREATING_SNOWBALL(1, SnowWarConstants.CREATING_TIMER),
    STUNNED(2, SnowWarConstants.STUNNED_TIMER),
    INVINCIBLE(3, SnowWarConstants.INVINCIBILITY_TIMER);

    private final int stateId;
    private final int timer;

    SnowWarActivityState(int stateId, int timer) {
        this.stateId = stateId;
        this.timer = timer;
    }

    public int getStateId() {
        return this.stateId;
    }

    public int getTimer() {
        return this.timer;
    }
}
