package com.eu.habbo.habbohotel.rooms.raidprotection;

/**
 * Per-room raid protection configuration.
 *
 * <p>The client validates every field against a fixed set of values before sending them, and
 * refuses anything outside it. The server repeats the check in {@link #isValid()} because a
 * modified client would not.
 *
 * <p>{@code incidentActive} is deliberately absent from persistence: it answers "is a raid
 * happening right now", which is runtime state owned by {@link RaidProtectionMonitor}.
 */
public final class RaidProtectionSettings {
    public static final int SENSITIVITY_LOW = 0;

    public static final int SENSITIVITY_MEDIUM = 1;

    public static final int SENSITIVITY_HIGH = 2;

    public static final int ACTION_KICK = 0;

    public static final int ACTION_TEMPORARY_BAN = 1;

    /** The ban lengths the client offers, in seconds: 5 minutes through 7 days. */
    private static final int[] BAN_DURATIONS = {300, 900, 1800, 3600, 10800, 21600, 43200, 86400, 259200, 604800};

    /** The guard lengths the client offers, in seconds: 5 minutes through 3 hours. */
    private static final int[] GUARD_DURATIONS = {300, 900, 1800, 3600, 10800};

    private final int roomId;
    private final boolean enabled;
    private final int detectionSensitivity;
    private final int actionType;
    private final int banDurationSeconds;
    private final boolean guardEnabled;
    private final int guardDurationSeconds;
    private final int guardSensitivity;

    public RaidProtectionSettings(
            int roomId,
            boolean enabled,
            int detectionSensitivity,
            int actionType,
            int banDurationSeconds,
            boolean guardEnabled,
            int guardDurationSeconds,
            int guardSensitivity) {
        this.roomId = roomId;
        this.enabled = enabled;
        this.detectionSensitivity = detectionSensitivity;
        this.actionType = actionType;
        this.banDurationSeconds = banDurationSeconds;
        this.guardEnabled = guardEnabled;
        this.guardDurationSeconds = guardDurationSeconds;
        this.guardSensitivity = guardSensitivity;
    }

    /** The settings a room that has never been configured starts from: off, medium, kick. */
    public static RaidProtectionSettings defaults(int roomId) {
        return new RaidProtectionSettings(
                roomId, false, SENSITIVITY_MEDIUM, ACTION_KICK, 3600, false, 1800, SENSITIVITY_HIGH);
    }

    /** Whether every field is one the client could legitimately have produced. */
    public boolean isValid() {
        return isSensitivity(this.detectionSensitivity)
                && isSensitivity(this.guardSensitivity)
                && (this.actionType == ACTION_KICK || this.actionType == ACTION_TEMPORARY_BAN)
                && contains(BAN_DURATIONS, this.banDurationSeconds)
                && contains(GUARD_DURATIONS, this.guardDurationSeconds);
    }

    private static boolean isSensitivity(int value) {
        return value >= SENSITIVITY_LOW && value <= SENSITIVITY_HIGH;
    }

    private static boolean contains(int[] allowed, int value) {
        for (int candidate : allowed) {
            if (candidate == value) {
                return true;
            }
        }

        return false;
    }

    public int getRoomId() {
        return this.roomId;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public int getDetectionSensitivity() {
        return this.detectionSensitivity;
    }

    public int getActionType() {
        return this.actionType;
    }

    public int getBanDurationSeconds() {
        return this.banDurationSeconds;
    }

    public boolean isGuardEnabled() {
        return this.guardEnabled;
    }

    public int getGuardDurationSeconds() {
        return this.guardDurationSeconds;
    }

    public int getGuardSensitivity() {
        return this.guardSensitivity;
    }
}
