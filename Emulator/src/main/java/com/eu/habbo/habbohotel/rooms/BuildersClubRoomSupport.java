package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubFurniCountComposer;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.generic.alerts.SimpleAlertComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BuildersClubRoomSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildersClubRoomSupport.class);

    public static final int DEFAULT_TRIAL_FURNI_LIMIT = 50;
    // Runtime-only owner marker used to display Builders Club furni as virtual/non-user-owned in-room.
    // The actual DB owner for persistence/FK purposes is tracked separately on the item instance.
    public static final int VIRTUAL_OWNER_ID = 1;
    public static final String DISPLAY_OWNER_NAME = "Builders Club";

    public enum SyncResult {
        UNCHANGED,
        LOCKED,
        UNLOCKED
    }

    private BuildersClubRoomSupport() {
    }

    public static int getFurniLimit(Habbo habbo) {
        if (habbo == null) {
            return DEFAULT_TRIAL_FURNI_LIMIT;
        }

        return DEFAULT_TRIAL_FURNI_LIMIT + Math.max(0, habbo.getHabboStats().getBuildersClubBonusFurni());
    }

    public static int getFurniLimit(int userId) {
        HabboInfo habboInfo = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(userId);

        if (habboInfo == null || habboInfo.getHabboStats() == null) {
            return DEFAULT_TRIAL_FURNI_LIMIT;
        }

        return DEFAULT_TRIAL_FURNI_LIMIT + Math.max(0, habboInfo.getHabboStats().getBuildersClubBonusFurni());
    }

    public static int getMembershipSecondsLeft(int userId) {
        HabboInfo habboInfo = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(userId);

        if (habboInfo == null || habboInfo.getHabboStats() == null) {
            return 0;
        }

        Subscription subscription = habboInfo.getHabboStats().getSubscription(Subscription.BUILDERS_CLUB);

        if (subscription == null) {
            return 0;
        }

        return Math.max(0, subscription.getRemaining());
    }

    public static boolean hasActiveMembership(int userId) {
        HabboInfo habboInfo = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(userId);

        return habboInfo != null
                && habboInfo.getHabboStats() != null
                && habboInfo.getHabboStats().hasSubscription(Subscription.BUILDERS_CLUB);
    }

    public static int getTrackedFurniCount(int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM builders_club_items WHERE user_id = ? AND room_id > 0")) {
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
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM builders_club_items bci INNER JOIN rooms r ON r.id = bci.room_id WHERE r.owner_id = ? AND bci.room_id > 0 LIMIT 1")) {
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
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM builders_club_items WHERE room_id = ? LIMIT 1")) {
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
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM builders_club_items WHERE item_id = ? LIMIT 1")) {
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
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT user_id FROM builders_club_items WHERE item_id = ? LIMIT 1")) {
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

    public static boolean hasPlacementVisitors(Room room, Habbo owner) {
        if (room == null || owner == null) {
            return false;
        }

        for (Habbo habbo : room.getHabbos()) {
            if (habbo == null || habbo.getHabboInfo() == null) {
                continue;
            }

            if (habbo.getHabboInfo().getId() == owner.getHabboInfo().getId()) {
                continue;
            }

            if (habbo.hasPermission(Permission.ACC_ENTERANYROOM) || habbo.hasPermission(Permission.ACC_ANYROOMOWNER)) {
                continue;
            }

            return true;
        }

        return false;
    }

    public static boolean isPlacementBlockedByVisitors(Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null) {
            return false;
        }

        if (hasActiveMembership(habbo.getHabboInfo().getId())) {
            return false;
        }

        Room currentRoom = habbo.getHabboInfo().getCurrentRoom();

        if (currentRoom == null || currentRoom.getOwnerId() != habbo.getHabboInfo().getId()) {
            return false;
        }

        return hasPlacementVisitors(currentRoom, habbo);
    }

    public static boolean canPlaceInCurrentRoom(Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null || habbo.getHabboInfo().getCurrentRoom() == null) {
            return false;
        }

        return canPlaceInRoom(habbo, habbo.getHabboInfo().getCurrentRoom());
    }

    public static boolean canPlaceInRoom(Habbo habbo, Room room) {
        if (habbo == null || habbo.getHabboInfo() == null || room == null) {
            return false;
        }

        if (room.getOwnerId() == habbo.getHabboInfo().getId()) {
            return true;
        }

        Room currentRoom = habbo.getHabboInfo().getCurrentRoom();

        if (currentRoom == null || currentRoom.getId() != room.getId()) {
            return false;
        }

        return canUseGuildPlacementPool(habbo, room);
    }

    public static int getPlacementPoolUserId(Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null) {
            return 0;
        }

        Room currentRoom = habbo.getHabboInfo().getCurrentRoom();

        if (currentRoom == null) {
            return habbo.getHabboInfo().getId();
        }

        if (currentRoom.getOwnerId() == habbo.getHabboInfo().getId()) {
            return habbo.getHabboInfo().getId();
        }

        if (canUseGuildPlacementPool(habbo, currentRoom)) {
            Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(currentRoom.getGuildId());

            if (guild != null && guild.getOwnerId() > 0) {
                return guild.getOwnerId();
            }
        }

        return habbo.getHabboInfo().getId();
    }

    public static int getPlacementPoolFurniCount(Habbo habbo) {
        int userId = getPlacementPoolUserId(habbo);

        if (userId <= 0) {
            return 0;
        }

        return getTrackedFurniCount(userId);
    }

    public static int getPlacementPoolFurniLimit(Habbo habbo) {
        int userId = getPlacementPoolUserId(habbo);

        if (userId <= 0) {
            return DEFAULT_TRIAL_FURNI_LIMIT;
        }

        return getFurniLimit(userId);
    }

    public static void sendPlacementStatus(Habbo habbo) {
        if (habbo == null || habbo.getClient() == null) {
            return;
        }

        habbo.getClient().sendResponse(new BuildersClubFurniCountComposer(getTrackedFurniCount(habbo.getHabboInfo().getId())));
        habbo.getClient().sendResponse(new BuildersClubSubscriptionStatusComposer(habbo));
    }

    public static void sendPlacementStatusForPool(Room room, int placementUserId) {
        if (placementUserId <= 0) {
            return;
        }

        Set<Integer> updatedUsers = new HashSet<>();

        if (room != null) {
            for (Habbo habbo : room.getHabbos()) {
                if (habbo == null || habbo.getHabboInfo() == null) {
                    continue;
                }

                if (getPlacementPoolUserId(habbo) != placementUserId) {
                    continue;
                }

                sendPlacementStatus(habbo);
                updatedUsers.add(habbo.getHabboInfo().getId());
            }
        }

        Habbo placementPoolHabbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(placementUserId);

        if (placementPoolHabbo != null && placementPoolHabbo.getHabboInfo() != null && !updatedUsers.contains(placementPoolHabbo.getHabboInfo().getId())) {
            sendPlacementStatus(placementPoolHabbo);
        }
    }

    public static void sendCurrentRoomPlacementStatus(Room room) {
        if (room == null) {
            return;
        }

        Habbo owner = room.getHabbo(room.getOwnerId());

        if (owner == null || owner.getClient() == null) {
            return;
        }

        owner.getClient().sendResponse(new com.eu.habbo.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer(owner));
    }

    private static boolean canUseGuildPlacementPool(Habbo habbo, Room room) {
        if (habbo == null || room == null) {
            return false;
        }

        Guild guild = resolvePlacementGuild(room);

        if (guild == null || guild.getOwnerId() <= 0) {
            return false;
        }

        boolean isGuildAdmin = room.getGuildRightLevel(habbo).isEqualOrGreaterThan(RoomRightLevels.GUILD_ADMIN);

        if (!isGuildAdmin) {
            GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild.getId(), habbo.getHabboInfo().getId());

            isGuildAdmin = member != null && (member.getRank() == GuildRank.ADMIN || member.getRank() == GuildRank.OWNER);
        }

        if (!isGuildAdmin) {
            return false;
        }

        return hasActiveMembership(habbo.getHabboInfo().getId()) && hasActiveMembership(guild.getOwnerId());
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

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT guild_id FROM rooms WHERE id = ? LIMIT 1")) {
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
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO builders_club_items (item_id, user_id, room_id) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), room_id = VALUES(room_id)")) {
            statement.setInt(1, itemId);
            statement.setInt(2, userId);
            statement.setInt(3, roomId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception tracking Builders Club item placement", e);
        }
    }

    public static void clearTrackedItemRoom(int itemId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE builders_club_items SET room_id = 0 WHERE item_id = ? LIMIT 1")) {
            statement.setInt(1, itemId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception clearing Builders Club room assignment", e);
        }
    }

    public static void deleteTrackedItem(int itemId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM builders_club_items WHERE item_id = ? LIMIT 1")) {
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

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
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
        sendBubbleNotification(userId, BubbleAlertKeys.BUILDERS_CLUB_MEMBERSHIP_MADE, null);
    }

    public static void sendMembershipExtendedBubble(int userId) {
        sendBubbleNotification(userId, BubbleAlertKeys.BUILDERS_CLUB_MEMBERSHIP_EXTENDED, null);
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
                        : "notification.builders_club.membership_expired.message_no_rooms"
        );
    }

    private static void sendBubbleNotification(int userId, BubbleAlertKeys key, Map<String, String> keys) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (habbo == null || habbo.getClient() == null) {
            return;
        }

        if (keys == null) {
            habbo.getClient().sendResponse(new BubbleAlertComposer(key.key));
            return;
        }

        habbo.getClient().sendResponse(new BubbleAlertComposer(key.key, keys));
    }

    private static void sendSimpleAlert(int userId, String messageKey) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (habbo == null || habbo.getClient() == null) {
            return;
        }

        habbo.getClient().sendResponse(new SimpleAlertComposer(messageKey));
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
}
