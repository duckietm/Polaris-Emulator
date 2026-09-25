package com.eu.habbo.messages.outgoing.users;

import java.util.Arrays;

/**
 * The account level the extended profile shows ("Level N"). Polaris has no account-wide level of
 * its own, so it is derived from the achievement score: level N is reached once the score is at
 * least the N-th threshold. The thresholds are a comma separated, ascending list of scores, read
 * from {@code hotel.profile.level.thresholds}; the first one is level 1.
 */
final class UserProfileLevel {
    static final String THRESHOLDS_KEY = "hotel.profile.level.thresholds";
    static final String DEFAULT_THRESHOLDS =
            "0,50,150,300,500,800,1200,1700,2300,3000,4000,5000,6500,8000,10000,12500,15000,20000,25000,30000";

    private final int level;
    private final int nextLevelStart;

    private UserProfileLevel(int level, int nextLevelStart) {
        this.level = level;
        this.nextLevelStart = nextLevelStart;
    }

    /** The level reached with {@code score} and the score the next one starts at; at the top level that is the score itself. */
    static UserProfileLevel of(int score, String thresholds) {
        int[] steps = parse(thresholds);

        if (steps.length == 0) steps = parse(DEFAULT_THRESHOLDS);

        int level = 0;

        while (level < steps.length && score >= steps[level]) level++;

        int nextLevelStart = level < steps.length ? steps[level] : score;

        return new UserProfileLevel(level, nextLevelStart);
    }

    int level() {
        return this.level;
    }

    int nextLevelStart() {
        return this.nextLevelStart;
    }

    private static int[] parse(String thresholds) {
        if (thresholds == null || thresholds.isBlank()) return new int[0];

        try {
            int[] steps = Arrays.stream(thresholds.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .mapToInt(Integer::parseInt)
                    .toArray();

            for (int i = 1; i < steps.length; i++) {
                if (steps[i] <= steps[i - 1]) return new int[0];
            }

            return steps;
        } catch (NumberFormatException e) {
            return new int[0];
        }
    }
}
