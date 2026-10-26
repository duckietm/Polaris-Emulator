package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.games.Game;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * The official "times in game" of the give-score boxes: how often one box may score for the same
 * player (or team) in one round. A new round, the same game started again, starts the count over.
 */
final class WiredScorePerGame {
    /** The official slider's top value, shown as "unlimited" and sent as 0. */
    static final int UNLIMITED = 0;

    static final int MAX_TIMES = 10;

    private WeakReference<Game> game = new WeakReference<>(null);
    private int round = Integer.MIN_VALUE;
    private final Map<Object, Integer> counts = new HashMap<>();

    /** Counts one more score for {@code key} in {@code game}; false once {@code limit} is reached. */
    synchronized boolean tryCount(Game game, Object key, int limit) {
        if (limit <= UNLIMITED || game == null) return true;

        if (this.game.get() != game || this.round != game.getStartTime()) {
            this.game = new WeakReference<>(game);
            this.round = game.getStartTime();
            this.counts.clear();
        }

        int count = this.counts.getOrDefault(key, 0);
        if (count >= limit) return false;

        this.counts.put(key, count + 1);
        return true;
    }

    /** 0 or less is unlimited; more than the most AIR allows is stored as that most (10). */
    static int normalize(int times) {
        return (times <= 0) ? UNLIMITED : Math.min(times, MAX_TIMES);
    }
}
