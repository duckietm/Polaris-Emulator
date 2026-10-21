package com.eu.habbo.habbohotel.rooms.raidprotection;

import java.util.Arrays;

/**
 * The owner's raid protection settings of one room, the editable part of AIR's
 * RaidProtectionSettingsSnapshot (package_230): protection on/off, how sensitive detection is, what
 * happens to raiders (kick, or a temporary ban of {@link #banDurationSeconds}), and the guard that
 * keeps suspicious visitors out for {@link #guardDurationSeconds} during a raid.
 */
public record RaidProtectionSettings(
        int roomId,
        boolean enabled,
        int sensitivity,
        int actionType,
        int banDurationSeconds,
        boolean guardEnabled,
        int guardDurationSeconds,
        int guardSensitivity) {

    public static final int SENSITIVITY_LOW = 0;
    public static final int SENSITIVITY_MEDIUM = 1;
    public static final int SENSITIVITY_HIGH = 2;

    public static final int ACTION_KICK = 0;
    public static final int ACTION_TEMPORARY_BAN = 1;

    /** AIR RaidProtectionSettingsController's sensitivity list (detection and guard). */
    static final int[] SENSITIVITIES = {SENSITIVITY_LOW, SENSITIVITY_MEDIUM, SENSITIVITY_HIGH};
    /** AIR's action list. */
    static final int[] ACTIONS = {ACTION_KICK, ACTION_TEMPORARY_BAN};
    /** AIR's ban durations, 5 minutes to a week. */
    static final int[] BAN_DURATIONS = {300, 900, 1800, 3600, 10800, 21600, 43200, 86400, 259200, 604800};
    /** AIR's guard durations, 5 minutes to 3 hours. */
    static final int[] GUARD_DURATIONS = {300, 900, 1800, 3600, 10800};

    /** A room nobody configured: protection off, medium sensitivity, kick, guard off. */
    public static RaidProtectionSettings defaults(int roomId) {
        return new RaidProtectionSettings(
                roomId, false, SENSITIVITY_MEDIUM, ACTION_KICK, 3600, false, 900, SENSITIVITY_MEDIUM);
    }

    /** The same check as AIR's RaidProtectionSettingsController.valid: every value from its list. */
    public static boolean isValid(
            int sensitivity, int actionType, int banDurationSeconds, int guardDurationSeconds, int guardSensitivity) {
        return contains(SENSITIVITIES, sensitivity)
                && contains(ACTIONS, actionType)
                && contains(BAN_DURATIONS, banDurationSeconds)
                && contains(GUARD_DURATIONS, guardDurationSeconds)
                && contains(SENSITIVITIES, guardSensitivity);
    }

    public boolean isValid() {
        return isValid(
                this.sensitivity,
                this.actionType,
                this.banDurationSeconds,
                this.guardDurationSeconds,
                this.guardSensitivity);
    }

    private static boolean contains(int[] values, int value) {
        return Arrays.stream(values).anyMatch(candidate -> candidate == value);
    }
}
