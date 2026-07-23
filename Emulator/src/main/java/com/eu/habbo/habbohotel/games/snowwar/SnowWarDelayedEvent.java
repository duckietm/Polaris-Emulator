package com.eu.habbo.habbohotel.games.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarSnowballObject;

/**
 * A collision result that is detected during the subturns but only applied
 * AFTER the checksum has been calculated (README 12.3, 15.2).
 */
public class SnowWarDelayedEvent {

    private final SnowWarDelayedEventType type;
    private final SnowWarGamePlayer player;
    private final SnowWarSnowballObject ball;

    public SnowWarDelayedEvent(SnowWarDelayedEventType type, SnowWarGamePlayer player, SnowWarSnowballObject ball) {
        this.type = type;
        this.player = player;
        this.ball = ball;
    }

    public SnowWarDelayedEventType getType() {
        return this.type;
    }

    public SnowWarGamePlayer getPlayer() {
        return this.player;
    }

    public SnowWarSnowballObject getBall() {
        return this.ball;
    }
}
