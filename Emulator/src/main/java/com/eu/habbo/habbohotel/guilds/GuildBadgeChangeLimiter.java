package com.eu.habbo.habbohotel.guilds;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spaces out badge changes of one guild ({@code hotel.guilds.badge.change.cooldown.seconds}), so a script cannot
 * make the badge imager write a new image many times a second.
 */
public final class GuildBadgeChangeLimiter {
    private final Map<Integer, Long> lastChange = new ConcurrentHashMap<>();

    /** True (and the change is recorded) when the guild may change its badge now. */
    public boolean tryChange(int guildId, long cooldownMillis, long now) {
        if (cooldownMillis <= 0) {
            return true;
        }
        boolean[] allowed = {false};
        this.lastChange.compute(guildId, (id, last) -> {
            if (last == null || now - last >= cooldownMillis) {
                allowed[0] = true;
                return now;
            }
            return last;
        });
        return allowed[0];
    }
}
