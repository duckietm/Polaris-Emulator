package com.eu.habbo.messages.incoming.housekeeping;

final class HousekeepingSanctionDuration {
    static final int SECONDS_IN_MINUTE = 60;
    static final int SECONDS_IN_HOUR = 3600;
    static final int MAX_SECONDS = Integer.MAX_VALUE;

    private HousekeepingSanctionDuration() {
    }

    static int secondsFromHours(int hours) {
        if (hours <= 0) {
            return 0;
        }

        long seconds = (long) hours * SECONDS_IN_HOUR;
        return seconds > MAX_SECONDS ? MAX_SECONDS : (int) seconds;
    }

    static int secondsFromMinutes(int minutes) {
        if (minutes <= 0) {
            return 0;
        }

        long seconds = (long) minutes * SECONDS_IN_MINUTE;
        return seconds > MAX_SECONDS ? MAX_SECONDS : (int) seconds;
    }

    static int unixUntil(int now, int durationSeconds) {
        if (durationSeconds <= 0) {
            return now;
        }

        long until = (long) now + durationSeconds;
        return until > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) until;
    }
}
