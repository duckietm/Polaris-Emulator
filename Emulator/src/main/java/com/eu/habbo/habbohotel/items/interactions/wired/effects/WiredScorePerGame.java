package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.games.Game;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The official "times in game" of the give-score boxes: how often one box may score for the same
 * player (or team) in one round. A new round, the same game started again, starts the count over.
 */
final class WiredScorePerGame {
    /** The official slider's top value, shown as "unlimited" and sent as 0. */
    static final int UNLIMITED = 0;

    static final int MAX_TIMES = 10;

    /** The counts of the current round of one game. */
    private static final class Round {
        private final int startTime;
        private final Map<Object, Integer> counts = new HashMap<>();

        private Round(int startTime) {
            this.startTime = startTime;
        }
    }

    /**
     * One round per game, so two games running in one room keep their own counts: with a single slot, scoring
     * in one game and then the other started the count over each time and the limit never held. The keys are
     * weak, so a game that ended and was dropped leaves nothing behind.
     */
    private final Map<Game, Round> rounds = new WeakHashMap<>();

    /** Counts one more score for {@code key} in {@code game}; false once {@code limit} is reached. */
    synchronized boolean tryCount(Game game, Object key, int limit) {
        if (limit <= UNLIMITED || game == null) return true;

        Round round = this.rounds.get(game);
        if (round == null || round.startTime != game.getStartTime()) {
            round = new Round(game.getStartTime());
            this.rounds.put(game, round);
        }

        int count = round.counts.getOrDefault(key, 0);
        if (count >= limit) return false;

        round.counts.put(key, count + 1);
        return true;
    }

    /** 0 or less is unlimited; more than the most AIR allows is stored as that most (10). */
    static int normalize(int times) {
        return (times <= 0) ? UNLIMITED : Math.min(times, MAX_TIMES);
    }
}
