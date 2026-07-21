package com.eu.habbo.habbohotel.soundboard;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class SoundboardCooldownGate {
    private static final int PRUNE_THRESHOLD = 10_000;

    private final ConcurrentHashMap<Integer, Long> expiresAtByUser = new ConcurrentHashMap<>();

    public Decision tryAcquire(int userId, long nowMillis, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            this.expiresAtByUser.remove(userId);
            return new Decision(true, 0);
        }

        AtomicReference<Decision> decision = new AtomicReference<>();
        long cooldownMillis = cooldownSeconds * 1_000L;

        this.expiresAtByUser.compute(userId, (ignored, expiresAt) -> {
            if (expiresAt == null || expiresAt <= nowMillis) {
                decision.set(new Decision(true, 0));
                return nowMillis + cooldownMillis;
            }

            long remainingMillis = expiresAt - nowMillis;
            int remainingSeconds = (int) Math.ceil(remainingMillis / 1_000.0);
            decision.set(new Decision(false, remainingSeconds));
            return expiresAt;
        });

        if (this.expiresAtByUser.size() > PRUNE_THRESHOLD) {
            this.expiresAtByUser.entrySet().removeIf(entry -> entry.getValue() <= nowMillis);
        }

        return decision.get();
    }

    public record Decision(boolean allowed, int remainingSeconds) {
    }
}
