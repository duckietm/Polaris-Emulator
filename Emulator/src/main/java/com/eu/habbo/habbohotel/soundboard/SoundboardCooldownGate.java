package com.eu.habbo.habbohotel.soundboard;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class SoundboardCooldownGate {
    private static final int PRUNE_THRESHOLD = 10_000;

    private final ConcurrentHashMap<Long, Long> expiresAtByKey = new ConcurrentHashMap<>();

    public Decision tryAcquire(long key, long nowMillis, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            this.expiresAtByKey.remove(key);
            return new Decision(true, 0);
        }

        AtomicReference<Decision> decision = new AtomicReference<>();
        long cooldownMillis = cooldownSeconds * 1_000L;

        this.expiresAtByKey.compute(key, (ignored, expiresAt) -> {
            if (expiresAt == null || expiresAt <= nowMillis) {
                decision.set(new Decision(true, 0));
                return nowMillis + cooldownMillis;
            }

            long remainingMillis = expiresAt - nowMillis;
            int remainingSeconds = (int) Math.ceil(remainingMillis / 1_000.0);
            decision.set(new Decision(false, remainingSeconds));
            return expiresAt;
        });

        if (this.expiresAtByKey.size() > PRUNE_THRESHOLD) {
            this.expiresAtByKey.entrySet().removeIf(entry -> entry.getValue() <= nowMillis);
        }

        return decision.get();
    }

    /** Reports what {@link #tryAcquire} would answer without starting the cooldown. */
    public Decision peek(long key, long nowMillis) {
        Long expiresAt = this.expiresAtByKey.get(key);
        if (expiresAt == null || expiresAt <= nowMillis) {
            return new Decision(true, 0);
        }

        return new Decision(false, (int) Math.ceil((expiresAt - nowMillis) / 1_000.0));
    }

    public record Decision(boolean allowed, int remainingSeconds) {}
}
