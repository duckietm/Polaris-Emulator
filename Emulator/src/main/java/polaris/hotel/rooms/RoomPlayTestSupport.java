package polaris.hotel.rooms;

import polaris.hotel.items.FurnitureUsagePolicy;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.UserStats;

/**
 * AIR playtest mode (the wired menu preference) on the server. While it is on, the room acts for
 * the user as for a visitor without rights wherever AIR's client refuses it: the controller level
 * reads {@code NOT_CONTROLLER}, moving, rotating, picking up and ejecting furni, pets and bots is
 * refused, and only furni that everybody may use ({@link FurnitureUsagePolicy#EVERYBODY}) can be
 * used. The room's free furni movements mode ({@code ConfigurationItemStates}) lifts the furni
 * checks, as in AIR.
 *
 * <p>Beyond AIR, this hotel also refuses the building rights AIR's playtest check leaves alone (see
 * {@link #isBuildingRefused}).
 *
 * <p>Ownership, staff tools, room settings, kicking and banning and the wired menu (its own
 * permissions) stay as they are.
 */
public final class RoomPlayTestSupport {
    private RoomPlayTestSupport() {}

    /** The user's playtest switch, as the last saved wired menu preferences left it. */
    public static boolean isPlayTestMode(User user) {
        if (user == null) {
            return false;
        }

        UserStats stats = user.getUserStats();
        return stats != null && stats.isPlayTestMode();
    }

    /**
     * The room's free furni movements mode for this viewer, which is what {@code
     * ConfigurationItemStates} sends: the {@code acc_moverotate} permission.
     */
    public static boolean hasFreeFurniMovementsMode(User user) {
        return user != null && user.hasPermission(Permission.ACC_MOVEROTATE);
    }

    /** True when playtest refuses moving, rotating, picking up and rights-only use for this user. */
    public static boolean isRestricted(User user) {
        return isPlayTestMode(user) && !hasFreeFurniMovementsMode(user);
    }

    /**
     * The hotel's own addition beyond AIR: in playtest the user cannot build either. Placing furni,
     * post-its, paint, pets and bots from the inventory (Builders Club too), the dimmer and the
     * background toner settings, the stack helper height and the build height are refused with the
     * room's usual no-rights answer. The free furni movements mode lifts it, as it lifts the furni
     * checks.
     */
    public static boolean isBuildingRefused(User user) {
        return isRestricted(user);
    }

    /** AIR {@code useObject}: in playtest only furni with usage policy 2 (everybody) can be used. */
    public static boolean mayUse(User user, UserItem item) {
        return !isRestricted(user) || FurnitureUsagePolicy.of(item) >= FurnitureUsagePolicy.EVERYBODY;
    }

    /** The controller level sent to the user: none while playtest is on, as AIR's session reads it. */
    public static RoomRightLevels reportedLevel(User user, RoomRightLevels level) {
        return isPlayTestMode(user) ? RoomRightLevels.NONE : level;
    }

    /**
     * The switch was flipped: the rights the user's room reports change with it, so they are sent
     * again straight away (level 0 when it is on, the real level when it is off).
     */
    public static void onPlayTestModeChanged(User user) {
        if (user == null || user.getUserInfo() == null || user.getRoomUnit() == null) {
            return;
        }

        Room room = user.getUserInfo().getCurrentRoom();

        if (room != null) {
            room.refreshRightsForUser(user);
        }
    }
}
