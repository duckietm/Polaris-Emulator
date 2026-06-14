package com.eu.habbo.messages.rcon;

import java.util.function.IntPredicate;

public class SetRankRequestGuard {
    public static final int DEFAULT_MAX_RANK = 12;

    private SetRankRequestGuard() {
    }

    public static String validate(int userId, int rankId, int maxRank, IntPredicate rankExists) {
        if (userId <= 0) {
            return "invalid user";
        }

        if (rankId <= 0) {
            return "invalid rank";
        }

        if (maxRank > 0 && rankId > maxRank) {
            return "rank exceeds rcon ceiling";
        }

        if (!rankExists.test(rankId)) {
            return "invalid rank";
        }

        return null;
    }

    public static int parseMaxRank(String rawValue) {
        try {
            int value = Integer.parseInt(rawValue);
            return value > 0 ? value : DEFAULT_MAX_RANK;
        } catch (Exception e) {
            return DEFAULT_MAX_RANK;
        }
    }
}
