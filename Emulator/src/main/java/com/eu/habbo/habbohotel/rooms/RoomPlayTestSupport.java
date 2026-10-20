package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.FurnitureUsagePolicy;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboStats;

/**
 * AIR playtest mode (the wired menu preference) on the server. While it is on, the room acts for
 * the user as for a visitor without rights wherever AIR's client refuses it: the controller level
 * reads {@code NOT_CONTROLLER}, moving, rotating, picking up and ejecting furni, pets and bots is
 * refused, and only furni that everybody may use ({@link FurnitureUsagePolicy#EVERYBODY}) can be
 * used. The room's free furni movements mode ({@code ConfigurationItemStates}) lifts the furni
 * checks, as in AIR.
 *
 * <p>Ownership, staff tools, room settings, kicking and banning and the wired menu (its own
 * permissions) stay as they are.
 */
public final class RoomPlayTestSupport {
    private RoomPlayTestSupport() {}

    /** The user's playtest switch, as the last saved wired menu preferences left it. */
    public static boolean isPlayTestMode(Habbo habbo) {
        if (habbo == null) {
            return false;
        }

        HabboStats stats = habbo.getHabboStats();
        return stats != null && stats.isPlayTestMode();
    }

    /**
     * The room's free furni movements mode for this viewer, which is what {@code
     * ConfigurationItemStates} sends: the {@code acc_moverotate} permission.
     */
    public static boolean hasFreeFurniMovementsMode(Habbo habbo) {
        return habbo != null && habbo.hasPermission(Permission.ACC_MOVEROTATE);
    }

    /** True when playtest refuses moving, rotating, picking up and rights-only use for this user. */
    public static boolean isRestricted(Habbo habbo) {
        return isPlayTestMode(habbo) && !hasFreeFurniMovementsMode(habbo);
    }

    /** AIR {@code useObject}: in playtest only furni with usage policy 2 (everybody) can be used. */
    public static boolean mayUse(Habbo habbo, HabboItem item) {
        return !isRestricted(habbo) || FurnitureUsagePolicy.of(item) >= FurnitureUsagePolicy.EVERYBODY;
    }

    /** The controller level sent to the user: none while playtest is on, as AIR's session reads it. */
    public static RoomRightLevels reportedLevel(Habbo habbo, RoomRightLevels level) {
        return isPlayTestMode(habbo) ? RoomRightLevels.NONE : level;
    }

    /**
     * The switch was flipped: the rights the user's room reports change with it, so they are sent
     * again straight away (level 0 when it is on, the real level when it is off).
     */
    public static void onPlayTestModeChanged(Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null || habbo.getRoomUnit() == null) {
            return;
        }

        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room != null) {
            room.refreshRightsForHabbo(habbo);
        }
    }
}
