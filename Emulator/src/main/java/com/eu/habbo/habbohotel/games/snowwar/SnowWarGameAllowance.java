package com.eu.habbo.habbohotel.games.snowwar;

import java.time.LocalDate;

/**
 * Where a user's SnowWar games come from: a free daily allowance first, then
 * the games bought with tokens. A match charges one game when it starts.
 */
interface SnowWarGameAllowance {

    enum ConsumeResult {
        /** No limit configured; nothing was charged. */
        UNLIMITED,
        FREE,
        TOKEN,
        NO_GAMES,
        /** The database failed; nothing was charged. */
        ERROR;

        boolean allowed() {
            return this == UNLIMITED || this == FREE || this == TOKEN;
        }

        boolean charged() {
            return this == FREE || this == TOKEN;
        }
    }

    ConsumeResult tryConsumeGame(int userId, LocalDate today, int freePerDay);

    /** Gives one game back as a bought game (safe across a day change). */
    boolean refundGame(int userId);

    /** Free games left today plus bought games, or {@code -1} when unlimited. */
    int gamesLeft(int userId, LocalDate today, int freePerDay);
}
