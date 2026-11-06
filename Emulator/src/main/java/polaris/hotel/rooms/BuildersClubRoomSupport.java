package polaris.hotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.database.Database;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;
import polaris.hotel.users.subscriptions.Subscription;
import polaris.messages.outgoing.catalog.BuildersClubFurniCountComposer;
import polaris.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.generic.alerts.SimpleAlertComposer;

public class BuildersClubRoomSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildersClubRoomSupport.class);

    public static final int DEFAULT_TRIAL_FURNI_LIMIT = 50;
    public static final int DEFAULT_MEMBER_FURNI_LIMIT = 500;
    public static final int ONE_MONTH_PACKAGE_FURNI_LIMIT = 1000;
    public static final int THREE_MONTHS_PACKAGE_FURNI_LIMIT = 1500;
    private static final int ONE_MONTH_DAYS = 31;
    private static final int THREE_MONTHS_DAYS = 93;
    // Runtime-only owner marker used to display Builders Club furni as virtual/non-user-owned in-room.
    // The actual DB owner for persistence/FK purposes is tracked separately on the item instance.
    public static final int VIRTUAL_OWNER_ID = 1;
    public static final String DISPLAY_OWNER_NAME = "Builders Club";

    public enum SyncResult {
        UNCHANGED,
        LOCKED,
        UNLOCKED
    }

    private BuildersClubRoomSupport() {}

    public static int getFurniLimit(User user) {
        if (user == null || user.getUserInfo() == null) {
            return DEFAULT_TRIAL_FURNI_LIMIT;
        }

        return furniLimit(
                user.getUserStats(), hasActiveMembership(user.getUserInfo().getId()));
    }

    public static int getFurniLimit(int userId) {
        UserInfo userInfo = userInfo(userId);

        if (userInfo == null || userInfo.getUserStats() == null) {
            return DEFAULT_TRIAL_FURNI_LIMIT;
        }

        return furniLimit(userInfo.getUserStats(), userInfo.getUserStats().hasSubscription(Subscription.BUILDERS_CLUB));
    }

    private static int furniLimit(UserStats stats, boolean member) {
        if (stats == null) {
            return DEFAULT_TRIAL_FURNI_LIMIT;
        }

        return furniLimit(member, stats.getBuildersClubPackageFurniLimit(), stats.getBuildersClubBonusFurni());
    }

    /**
     * A member builds up to the highest limit a bought membership package gave, never below the member
     * default; a trial user gets the trial limit. Bought furni upgrades count on top of either.
     */
    static int furniLimit(boolean member, int packageFurniLimit, int bonusFurni) {
        int base = member ? Math.max(DEFAULT_MEMBER_FURNI_LIMIT, packageFurniLimit) : DEFAULT_TRIAL_FURNI_LIMIT;
        long limit = (long) base + Math.max(0, bonusFurni);

        return (int) Math.min(Integer.MAX_VALUE, limit);
    }

    /** The furni limit a membership package of this many days gives: 1000 for a month, 1500 for three months. */
    public static int packageFurniLimit(int days) {
        if (days >= THREE_MONTHS_DAYS) {
            return THREE_MONTHS_PACKAGE_FURNI_LIMIT;
        }

        return days >= ONE_MONTH_DAYS ? ONE_MONTH_PACKAGE_FURNI_LIMIT : 0;
    }

    /** Raises the user's package furni limit to the bought package's; a smaller package never lowers it. */
    public static void raisePackageFurniLimit(User user, int days) {
        int packageLimit = packageFurniLimit(days);

        if (user == null || user.getUserInfo() == null || user.getUserStats() == null || packageLimit <= 0) {
            return;
        }

        if (!user.getUserStats().raiseBuildersClubPackageFurniLimit(packageLimit)) {
            return;
        }

        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users_settings SET builders_club_furni_limit = GREATEST(builders_club_furni_limit, ?) WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, packageLimit);
            statement.setInt(2, user.getUserInfo().getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception saving the Builders Club package furni limit", e);
        }
    }

    public static int getMembershipSecondsLeft(int userId) {
        UserInfo userInfo = userInfo(userId);

        if (userInfo == null || userInfo.getUserStats() == null) {
            return 0;
        }

        Subscription subscription = userInfo.getUserStats().getSubscription(Subscription.BUILDERS_CLUB);

        if (subscription == null) {
            return 0;
        }

        return Math.max(0, subscription.getRemaining());
    }

    public static boolean hasActiveMembership(int userId) {
        UserInfo userInfo = userInfo(userId);

        return userInfo != null
                && userInfo.getUserStats() != null
                && userInfo.getUserStats().hasSubscription(Subscription.BUILDERS_CLUB);
    }

    public static int getTrackedFurniCount(int userId) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM builders_club_items WHERE user_id = ? AND room_id > 0")) {
            statement.setInt(1, userId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception counting Builders Club furni", e);
        }

        return 0;
    }

    public static boolean hasTrackedItemsInOwnedRooms(int ownerId) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM builders_club_items bci INNER JOIN rooms r ON r.id = bci.room_id WHERE r.owner_id = ? AND bci.room_id > 0 LIMIT 1")) {
            statement.setInt(1, ownerId);

            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception checking Builders Club room ownership", e);
        }

        return false;
    }

    public static boolean roomHasTrackedItems(int roomId) {
        try (Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT 1 FROM builders_club_items WHERE room_id = ? LIMIT 1")) {
            statement.setInt(1, roomId);

            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception checking Builders Club room items", e);
        }

        return false;
    }

    public static boolean isTrackedItem(int itemId) {
        try (Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT 1 FROM builders_club_items WHERE item_id = ? LIMIT 1")) {
            statement.setInt(1, itemId);

            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception checking Builders Club tracked item", e);
        }

        return false;
    }

    public static int getTrackedUserId(int itemId) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT user_id FROM builders_club_items WHERE item_id = ? LIMIT 1")) {
            statement.setInt(1, itemId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt("user_id");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception getting Builders Club tracked user", e);
        }

        return 0;
    }

    public static boolean hasPlacementVisitors(Room room, User owner) {
        if (room == null || owner == null) {
            return false;
        }

        for (User user : room.getUsers()) {
            if (user == null || user.getUserInfo() == null) {
                continue;
            }

            if (user.getUserInfo().getId() == owner.getUserInfo().getId()) {
                continue;
            }

            if (user.hasPermission(Permission.ACC_ENTERANYROOM) || user.hasPermission(Permission.ACC_ANYROOMOWNER)) {
                continue;
            }

            return true;
        }

        return false;
    }

    public static boolean isPlacementBlockedByVisitors(User user) {
        if (user == null || user.getUserInfo() == null) {
            return false;
        }

        if (hasActiveMembership(user.getUserInfo().getId())) {
            return false;
        }

        Room currentRoom = user.getUserInfo().getCurrentRoom();

        if (currentRoom == null
                || currentRoom.getOwnerId() != user.getUserInfo().getId()) {
            return false;
        }

        return hasPlacementVisitors(currentRoom, user);
    }

    public static boolean canPlaceInCurrentRoom(User user) {
        if (user == null || user.getUserInfo() == null || user.getUserInfo().getCurrentRoom() == null) {
            return false;
        }

        return canPlaceInRoom(user, user.getUserInfo().getCurrentRoom());
    }

    public static boolean canPlaceInRoom(User user, Room room) {
        if (user == null || user.getUserInfo() == null || room == null) {
            return false;
        }

        if (room.getOwnerId() == user.getUserInfo().getId()) {
            return true;
        }

        Room currentRoom = user.getUserInfo().getCurrentRoom();

        if (currentRoom == null || currentRoom.getId() != room.getId()) {
            return false;
        }

        return canUseGuildPlacementPool(user, room);
    }

    public static int getPlacementPoolUserId(User user) {
        if (user == null || user.getUserInfo() == null) {
            return 0;
        }

        Room currentRoom = user.getUserInfo().getCurrentRoom();

        if (currentRoom == null) {
            return user.getUserInfo().getId();
        }

        if (currentRoom.getOwnerId() == user.getUserInfo().getId()) {
            return user.getUserInfo().getId();
        }

        if (canUseGuildPlacementPool(user, currentRoom)) {
            Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(currentRoom.getGuildId());

            if (guild != null && guild.getOwnerId() > 0) {
                return guild.getOwnerId();
            }
        }

        return user.getUserInfo().getId();
    }

    public static int getPlacementPoolFurniCount(User user) {
        int userId = getPlacementPoolUserId(user);

        if (userId <= 0) {
            return 0;
        }

        return getTrackedFurniCount(userId);
    }

    public static int getPlacementPoolFurniLimit(User user) {
        int userId = getPlacementPoolUserId(user);

        if (userId <= 0) {
            return DEFAULT_TRIAL_FURNI_LIMIT;
        }

        return getFurniLimit(userId);
    }

    public static void sendPlacementStatus(User user) {
        if (user == null || user.getClient() == null) {
            return;
        }

        user.getClient()
                .sendResponse(new BuildersClubFurniCountComposer(
                        getTrackedFurniCount(user.getUserInfo().getId())));
        user.getClient().sendResponse(new BuildersClubSubscriptionStatusComposer(user));
    }

    public static void sendPlacementStatusForPool(Room room, int placementUserId) {
        if (placementUserId <= 0) {
            return;
        }

        Set<Integer> updatedUsers = new HashSet<>();

        if (room != null) {
            for (User user : room.getUsers()) {
                if (user == null || user.getUserInfo() == null) {
                    continue;
                }

                if (getPlacementPoolUserId(user) != placementUserId) {
                    continue;
                }

                sendPlacementStatus(user);
                updatedUsers.add(user.getUserInfo().getId());
            }
        }

        User placementPoolUser = onlineUser(placementUserId);

        if (placementPoolUser != null
                && placementPoolUser.getUserInfo() != null
                && !updatedUsers.contains(placementPoolUser.getUserInfo().getId())) {
            sendPlacementStatus(placementPoolUser);
        }
    }

    public static void sendCurrentRoomPlacementStatus(Room room) {
        if (room == null) {
            return;
        }

        User owner = room.getUser(room.getOwnerId());

        if (owner == null || owner.getClient() == null) {
            return;
        }

        owner.getClient()
                .sendResponse(new polaris.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer(owner));
    }

    private static boolean canUseGuildPlacementPool(User user, Room room) {
        if (user == null || room == null) {
            return false;
        }

        Guild guild = resolvePlacementGuild(room);

        if (guild == null || guild.getOwnerId() <= 0) {
            return false;
        }

        boolean isGuildAdmin = room.getGuildRightLevel(user).isEqualOrGreaterThan(RoomRightLevels.GUILD_ADMIN);

        if (!isGuildAdmin) {
            GuildMember member = Emulator.getGameEnvironment()
                    .getGuildManager()
                    .getGuildMember(guild.getId(), user.getUserInfo().getId());

            isGuildAdmin =
                    member != null && (member.getRank() == GuildRank.ADMIN || member.getRank() == GuildRank.OWNER);
        }

        if (!isGuildAdmin) {
            return false;
        }

        return hasActiveMembership(user.getUserInfo().getId()) && hasActiveMembership(guild.getOwnerId());
    }

    private static Guild resolvePlacementGuild(Room room) {
        int guildId = resolveRoomGuildId(room);

        if (guildId <= 0) {
            return null;
        }

        if (room.getGuildId() != guildId) {
            room.setGuild(guildId);
        }

        return Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);
    }

    private static int resolveRoomGuildId(Room room) {
        if (room == null) {
            return 0;
        }

        if (room.getGuildId() > 0) {
            return room.getGuildId();
        }

        try (Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT guild_id FROM rooms WHERE id = ? LIMIT 1")) {
            statement.setInt(1, room.getId());

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt("guild_id");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception resolving Builders Club room guild", e);
        }

        return 0;
    }

    public static void trackPlacedItem(int itemId, int userId, int roomId) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO builders_club_items (item_id, user_id, room_id) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), room_id = VALUES(room_id)")) {
            statement.setInt(1, itemId);
            statement.setInt(2, userId);
            statement.setInt(3, roomId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception tracking Builders Club item placement", e);
        }
    }

    public static void clearTrackedItemRoom(int itemId) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE builders_club_items SET room_id = 0 WHERE item_id = ? LIMIT 1")) {
            statement.setInt(1, itemId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception clearing Builders Club room assignment", e);
        }
    }

    public static void deleteTrackedItem(int itemId) {
        try (Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM builders_club_items WHERE item_id = ? LIMIT 1")) {
            statement.setInt(1, itemId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception deleting Builders Club tracked item", e);
        }
    }

    public static SyncResult syncRoom(Room room) {
        if (room == null) {
            return SyncResult.UNCHANGED;
        }

        boolean hasTrackedItems = roomHasTrackedItems(room.getId());
        boolean hasMembership = hasActiveMembership(room.getOwnerId());

        if (hasTrackedItems && !hasMembership) {
            return lockRoom(room) ? SyncResult.LOCKED : SyncResult.UNCHANGED;
        }

        if (room.isBuildersClubTrialLocked() && (!hasTrackedItems || hasMembership)) {
            return unlockRoom(room) ? SyncResult.UNLOCKED : SyncResult.UNCHANGED;
        }

        return SyncResult.UNCHANGED;
    }

    public static int syncOwnedRooms(int ownerId) {
        int changed = 0;

        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT id FROM rooms WHERE owner_id = ?")) {
            statement.setInt(1, ownerId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(set.getInt("id"), false);

                    if (syncRoom(room) != SyncResult.UNCHANGED) {
                        changed++;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception syncing Builders Club rooms", e);
        }

        return changed;
    }

    public static void sendRoomLockedBubble(int ownerId) {
        sendBubbleNotification(ownerId, BubbleAlertKeys.BUILDERS_CLUB_ROOM_LOCKED, null);
    }

    public static void sendRoomUnlockedBubble(int ownerId) {
        sendBubbleNotification(ownerId, BubbleAlertKeys.BUILDERS_CLUB_ROOM_UNLOCKED, null);
    }

    public static void sendMembershipMadeBubble(int userId) {
        sendBubbleNotification(userId, BubbleAlertKeys.BUILDERS_CLUB_MEMBERSHIP_MADE, furniLimitKeys(userId));
    }

    public static void sendMembershipExtendedBubble(int userId) {
        sendBubbleNotification(userId, BubbleAlertKeys.BUILDERS_CLUB_MEMBERSHIP_EXTENDED, furniLimitKeys(userId));
    }

    private static Map<String, String> furniLimitKeys(int userId) {
        Map<String, String> keys = new HashMap<>();
        keys.put("FURNI_LIMIT", String.valueOf(getFurniLimit(userId)));

        return keys;
    }

    public static void sendVisitDeniedOwnerBubble(int ownerId, String username) {
        Map<String, String> keys = new HashMap<>();
        keys.put("USERNAME", username);

        sendBubbleNotification(ownerId, BubbleAlertKeys.BUILDERS_CLUB_VISIT_DENIED_OWNER, keys);
    }

    public static void sendVisitDeniedVisitorAlert(int userId) {
        sendSimpleAlert(userId, "notification.builders_club.visit_denied_for_visitor.message");
    }

    public static void sendMembershipExpiringAlert(int userId) {
        sendSimpleAlert(userId, "expiring.bc.membership.description");
    }

    public static void sendMembershipExpiredAlert(int userId, boolean hasTrackedRooms) {
        sendSimpleAlert(
                userId,
                hasTrackedRooms
                        ? "notification.builders_club.membership_expired.message"
                        : "notification.builders_club.membership_expired.message_no_rooms");
    }

    private static void sendBubbleNotification(int userId, BubbleAlertKeys key, Map<String, String> keys) {
        User user = onlineUser(userId);

        if (user == null || user.getClient() == null) {
            return;
        }

        if (keys == null) {
            user.getClient().sendResponse(new BubbleAlertComposer(key.key));
            return;
        }

        user.getClient().sendResponse(new BubbleAlertComposer(key.key, keys));
    }

    private static void sendSimpleAlert(int userId, String messageKey) {
        User user = onlineUser(userId);

        if (user == null || user.getClient() == null) {
            return;
        }

        user.getClient().sendResponse(new SimpleAlertComposer(messageKey));
    }

    private static boolean lockRoom(Room room) {
        if (room.isBuildersClubTrialLocked()) {
            if (room.getState() != RoomState.INVISIBLE) {
                room.setState(RoomState.INVISIBLE);
                room.setNeedsUpdate(true);
                room.save();
            }

            return false;
        }

        room.setBuildersClubOriginalState(room.getState());
        room.setBuildersClubTrialLocked(true);
        room.setState(RoomState.INVISIBLE);
        room.setNeedsUpdate(true);
        room.save();

        return true;
    }

    private static boolean unlockRoom(Room room) {
        if (!room.isBuildersClubTrialLocked()) {
            return false;
        }

        RoomState originalState = room.getBuildersClubOriginalState();

        if (originalState == null) {
            originalState = RoomState.OPEN;
        }

        room.setState(originalState);
        room.setBuildersClubTrialLocked(false);
        room.setBuildersClubOriginalState(originalState);
        room.setNeedsUpdate(true);
        room.save();

        return true;
    }

    /**
     * Whether placing Builders Club furni here would take the room out of the navigator: a trial owner's
     * room that is not hidden already. Habbo asks before that placement and places only once confirmed.
     */
    public static boolean placementWouldHideRoom(Room room) {
        return room != null
                && !room.isBuildersClubTrialLocked()
                && room.getState() != RoomState.INVISIBLE
                && !hasActiveMembership(room.getOwnerId());
    }

    /** The ids of the Builders Club furni placed in this room, in one query. */
    public static Set<Integer> getTrackedItemIds(int roomId) {
        Set<Integer> ids = new HashSet<>();

        try (Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT item_id FROM builders_club_items WHERE room_id = ?")) {
            statement.setInt(1, roomId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    ids.add(set.getInt(1));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception reading Builders Club furni of room {}", roomId, e);
        }

        return ids;
    }

    private static Connection openConnection() throws SQLException {
        Database database = Emulator.getDatabase();
        // No database (a test, or a hotel still starting): every lookup answers "not tracked" instead of throwing.
        if (database == null) throw new SQLException("No database");
        return database.getDataSource().getConnection();
    }

    private static UserInfo userInfo(int userId) {
        return Emulator.getGameEnvironment().getUserManager().getUserInfo(userId);
    }

    private static User onlineUser(int userId) {
        return Emulator.getGameEnvironment().getUserManager().getUser(userId);
    }
}
