package polaris.hotel.rooms;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.database.SqlQueries;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildMembershipStatus;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.BuildHeightAvailableComposer;
import polaris.messages.outgoing.rooms.RoomAddRightsListComposer;
import polaris.messages.outgoing.rooms.RoomOwnerComposer;
import polaris.messages.outgoing.rooms.RoomRemoveRightsListComposer;
import polaris.messages.outgoing.rooms.RoomRightsComposer;
import polaris.messages.outgoing.rooms.RoomRightsListComposer;
import polaris.messages.outgoing.rooms.users.RoomUserUnbannedComposer;
import polaris.plugin.events.users.UserRightsTakenEvent;

/**
 * Manages room rights, bans, and mutes.
 */
public class RoomRightsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomRightsManager.class);

    private final Room room;
    private final IntList legacyRights;
    private final IntSet rights;
    private final Int2ObjectMap<RoomBan> bannedUsers;
    private final Int2IntMap mutedUsers;

    public RoomRightsManager(Room room) {
        this(room, null, IntSets.synchronize(new IntOpenHashSet()), newBanMap(), newMuteMap());
    }

    /*
     * A room's bans, mutes and rights are changed from packet threads, the room cycle and the
     * loaders at once: each is a synchronized view, whose lock is the view itself, the one the
     * existing synchronized blocks on these collections already take.
     */
    static Int2ObjectMap<RoomBan> newBanMap() {
        return Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
    }

    static Int2IntMap newMuteMap() {
        return Int2IntMaps.synchronize(new Int2IntOpenHashMap());
    }

    static IntList newRightsList() {
        return IntLists.synchronize(new IntArrayList());
    }

    RoomRightsManager(Room room, IntList legacyRights, Int2ObjectMap<RoomBan> bannedUsers, Int2IntMap mutedUsers) {
        this(room, legacyRights, new RoomRightsSetView(legacyRights), bannedUsers, mutedUsers);
    }

    private RoomRightsManager(
            Room room, IntList legacyRights, IntSet rights, Int2ObjectMap<RoomBan> bannedUsers, Int2IntMap mutedUsers) {
        this.room = room;
        this.legacyRights = legacyRights;
        this.rights = rights;
        this.bannedUsers = bannedUsers;
        this.mutedUsers = mutedUsers;
    }

    /**
     * Loads rights from database. The entry checks and the full room load may both run this,
     * so the list is swapped under a lock instead of filled in place.
     */
    public void loadRights(Connection connection) {
        IntList loaded = new IntArrayList();
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT user_id FROM room_rights WHERE room_id = ?")) {
            statement.setInt(1, this.room.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    loaded.add(set.getInt("user_id"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        if (this.legacyRights != null) {
            synchronized (this.legacyRights) {
                this.legacyRights.clear();
                this.legacyRights.addAll(loaded);
            }
        } else {
            synchronized (this.rights) {
                this.rights.clear();
                this.rights.addAll(loaded);
            }
        }
    }

    /**
     * Loads bans from database.
     */
    public void loadBans(Connection connection) {
        RoomBanLoader.load(connection, this.room, this.bannedUsers);
    }

    /**
     * Gets the guild right level for a user.
     */
    public RoomRightLevels getGuildRightLevel(User user) {
        int guildId = this.room.getGuildId();
        if (guildId > 0 && user != null && user.getUserInfo() != null) {
            Guild guild = this.room.gameEnvironment().getGuildManager().getGuild(guildId);

            if (guild == null) {
                return RoomRightLevels.NONE;
            }

            GuildMember member = this.room
                    .gameEnvironment()
                    .getGuildManager()
                    .getGuildMember(guild.getId(), user.getUserInfo().getId());

            if ((member != null) && (member.getRank() == GuildRank.ADMIN || member.getRank() == GuildRank.OWNER)) {
                return RoomRightLevels.GUILD_ADMIN;
            }

            if ((member != null) && member.getMembershipStatus() == GuildMembershipStatus.MEMBER && guild.getRights()) {
                return RoomRightLevels.GUILD_RIGHTS;
            }
        }

        return RoomRightLevels.NONE;
    }

    /**
     * @deprecated Use getGuildRightLevel instead.
     */
    @Deprecated
    public int guildRightLevel(User user) {
        return this.getGuildRightLevel(user).level;
    }

    /**
     * Checks if a user is the room owner.
     */
    public boolean isOwner(User user) {
        return user.getUserInfo().getId() == this.room.getOwnerId() || user.hasPermission(Permission.ACC_ANYROOMOWNER);
    }

    /**
     * Checks if a user has rights in the room.
     */
    public boolean hasRights(User user) {
        int userId = user.getUserInfo().getId();
        return this.isOwner(user)
                || this.rights.contains(userId)
                || (user.getRoomUnit() != null
                        && user.getRoomUnit().getRightsLevel() != RoomRightLevels.NONE
                        && this.room.getCurrentUsers().containsKey(userId));
    }

    /** Room moderation settings, as the room settings window saves them. */
    static final int MODERATION_OWNER_ONLY = 0;

    static final int MODERATION_RIGHTS_HOLDERS = 1;
    static final int MODERATION_EVERYONE = 2;

    /** Whether the room's kick setting lets this user kick: the owner, rights holders, or everyone. */
    public boolean canKick(User user) {
        return this.allowedBy(this.room.getKickOption(), user, true);
    }

    /** Whether the room's mute setting lets this user mute: the owner, or rights holders too. */
    public boolean canMute(User user) {
        return this.allowedBy(this.room.getMuteOption(), user, false);
    }

    /** Whether the room's ban setting lets this user ban: the owner, or rights holders too. */
    public boolean canBan(User user) {
        return this.allowedBy(this.room.getBanOption(), user, false);
    }

    /**
     * The owner (and staff who own any room) always may. Holding rights used to be enough whatever the
     * setting, so "owner only" never applied. Mute and ban offer no "everyone" level: a stored 2 counts
     * as rights holders for them.
     */
    private boolean allowedBy(int option, User user, boolean everyoneAllowed) {
        if (user == null) {
            return false;
        }
        if (this.isOwner(user)) {
            return true;
        }
        if (option >= MODERATION_EVERYONE && everyoneAllowed) {
            return true;
        }
        return option >= MODERATION_RIGHTS_HOLDERS && this.hasRights(user);
    }

    /**
     * Checks whether a user (by id) owns the room or holds persistent rights,
     * without needing an online {@link User}. Does not consider the transient
     * in-room rights level (that requires the user to be present).
     */
    public boolean hasRights(int userId) {
        return userId == this.room.getOwnerId() || this.rights.contains(userId);
    }

    /**
     * Gives rights to a user.
     */
    public void giveRights(User user) {
        if (user != null) {
            this.giveRights(user.getUserInfo().getId());
        }
    }

    /**
     * Gives rights to a user by ID.
     */
    public void giveRights(int userId) {
        // add answers false when the user already has rights, atomically: a separate contains check
        // let two threads both add and both insert a room_rights row.
        if (this.rights.add(userId)) {
            try {
                SqlQueries.update(
                        "INSERT INTO room_rights (room_id, user_id) VALUES (?, ?)", this.room.getId(), userId);
            } catch (SqlQueries.DataAccessException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
        User user = this.room.getUser(userId);

        if (user != null) {
            this.refreshRightsForUser(user);

            this.room.sendComposer(new RoomAddRightsListComposer(
                            this.room,
                            user.getUserInfo().getId(),
                            user.getUserInfo().getUsername())
                    .compose());
        } else {
            User owner = this.room.gameEnvironment().getUserManager().getUser(this.room.getOwnerId());

            if (owner != null) {
                MessengerBuddy buddy = owner.getMessenger().getFriend(userId);

                if (buddy != null) {
                    this.room.sendComposer(
                            new RoomAddRightsListComposer(this.room, userId, buddy.getUsername()).compose());
                }
            }
        }
    }

    /**
     * Removes rights from a user.
     */
    public void removeRights(int userId) {
        User user = this.room.getUser(userId);

        if (this.room
                .pluginManager()
                .fireEvent(new UserRightsTakenEvent(this.room.getUser(this.room.getOwnerId()), userId, user))
                .isCancelled()) {
            return;
        }

        this.room.sendComposer(new RoomRemoveRightsListComposer(this.room, userId).compose());

        if (this.rights.remove(userId)) {
            try {
                SqlQueries.update(
                        "DELETE FROM room_rights WHERE room_id = ? AND user_id = ?", this.room.getId(), userId);
            } catch (SqlQueries.DataAccessException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
        if (user != null) {
            this.room.getItemManager().ejectUserFurni(user.getUserInfo().getId());
            user.getRoomUnit().setRightsLevel(RoomRightLevels.NONE);
            user.getRoomUnit().removeStatus(RoomUnitStatus.FLAT_CONTROL);
            this.refreshRightsForUser(user);
        }
    }

    /**
     * Removes all rights from the room.
     */
    public void removeAllRights() {
        for (int userId : this.rights.toIntArray()) {
            this.room.getItemManager().ejectUserFurni(userId);
        }

        this.rights.clear();

        try {
            SqlQueries.update("DELETE FROM room_rights WHERE room_id = ?", this.room.getId());
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.refreshRightsInRoom();
    }

    /**
     * Refreshes rights for all users in the room.
     */
    public void refreshRightsInRoom() {
        Room room = this.room;
        for (User user : this.room.getUsers()) {
            if (user.getUserInfo().getCurrentRoom() == room) {
                this.refreshRightsForUser(user);
            }
        }
    }

    /**
     * Refreshes rights for a specific user.
     */
    public void refreshRightsForUser(User user) {
        UserItem item;
        RoomRightLevels flatCtrl = RoomRightLevels.NONE;
        if (user.getUserStats().isRentingSpace()) {
            item = this.room.getUserItem(user.getUserStats().getRentedItemId());

            if (item != null) {
                return;
            }
        }

        if (user.hasPermission(Permission.ACC_ANYROOMOWNER)) {
            user.getClient().sendResponse(new RoomOwnerComposer());
            flatCtrl = RoomRightLevels.MODERATOR;
        } else if (this.isOwner(user)) {
            user.getClient().sendResponse(new RoomOwnerComposer());
            flatCtrl = RoomRightLevels.MODERATOR;
        } else if (this.room.hasGuild()) {
            // Explicit room rights must still be honoured in guild rooms (the old
            // `&& !hasGuild()` guard stripped them for non-guild members) — take
            // whichever of the two is stronger.
            RoomRightLevels guildLevel = this.getGuildRightLevel(user);
            flatCtrl = (this.hasRights(user) && RoomRightLevels.RIGHTS.isEqualOrGreaterThan(guildLevel))
                    ? RoomRightLevels.RIGHTS
                    : guildLevel;
        } else if (this.hasRights(user)) {
            flatCtrl = RoomRightLevels.RIGHTS;
        }

        // AIR playtest mode: the session reads NOT_CONTROLLER, so the user is sent level 0 while it is
        // on; the avatar's status and everything below keep the real level.
        user.getClient().sendResponse(new RoomRightsComposer(RoomPlayTestSupport.reportedLevel(user, flatCtrl)));
        // The build height widget is hidden until the hotel says it may be used here, so the answer
        // travels with the rights; losing rights also drops a height the user had picked.
        boolean mayBuild = !flatCtrl.equals(RoomRightLevels.NONE);
        // Playtest hides it too (the hotel's own rule beyond AIR).
        if (RoomPlayTestSupport.isBuildingRefused(user)) {
            mayBuild = false;
        }
        user.getClient().sendResponse(new BuildHeightAvailableComposer(mayBuild));
        if (!mayBuild) {
            user.getRoomUnit().setBuildHeight(false, 0.0D);
        }
        user.getRoomUnit().setStatus(RoomUnitStatus.FLAT_CONTROL, flatCtrl.level + "");
        user.getRoomUnit().setRightsLevel(flatCtrl);
        user.getRoomUnit().statusUpdate(true);

        if (flatCtrl.equals(RoomRightLevels.MODERATOR)) {
            user.getClient().sendResponse(new RoomRightsListComposer(this.room));
        }
    }

    /**
     * Gets all users with rights in the room.
     */
    public Map<Integer, String> getUsersWithRights() {
        if (this.rights.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            return SqlQueries.query(
                            "SELECT users.username AS username, users.id as user_id FROM room_rights INNER JOIN users ON room_rights.user_id = users.id WHERE room_id = ?",
                            rs -> Map.entry(rs.getInt("user_id"), rs.getString("username")),
                            this.room.getId())
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Unbans a user from the room.
     */
    public void unbanUser(int userId) {
        RoomBan ban = this.bannedUsers.remove(userId);

        if (ban != null) {
            ban.delete();
        }

        this.room.sendComposer(new RoomUserUnbannedComposer(this.room, userId).compose());
    }

    /**
     * Checks if a user is banned from the room.
     */
    public boolean isBanned(User user) {
        RoomBan ban = this.bannedUsers.get(user.getUserInfo().getId());

        boolean banned = ban != null
                && ban.endTimestamp > this.room.currentUnixTimestamp()
                && !user.hasPermission(Permission.ACC_ANYROOMOWNER)
                && !user.hasPermission("acc_enteranyroom");

        if (!banned && ban != null) {
            this.unbanUser(user.getUserInfo().getId());
        }

        return banned;
    }

    /**
     * Gets all banned users.
     */
    public Int2ObjectMap<RoomBan> getBannedUsers() {
        return this.bannedUsers;
    }

    /**
     * Adds a room ban.
     */
    public void addRoomBan(RoomBan roomBan) {
        this.bannedUsers.put(roomBan.userId, roomBan);
    }

    /**
     * Mutes a user for a specified number of minutes.
     */
    public void muteUser(User user, int minutes) {
        synchronized (this.mutedUsers) {
            this.mutedUsers.put(user.getUserInfo().getId(), this.room.currentUnixTimestamp() + (minutes * 60));
        }
    }

    /**
     * Checks if a user is muted.
     */
    public boolean isMuted(User user) {
        if (this.isOwner(user) || this.hasRights(user)) {
            return false;
        }

        if (this.mutedUsers.containsKey(user.getUserInfo().getId())) {
            boolean time = this.mutedUsers.get(user.getUserInfo().getId()) > this.room.currentUnixTimestamp();

            if (!time) {
                this.mutedUsers.remove(user.getUserInfo().getId());
            }

            return time;
        }

        return false;
    }

    /**
     * Gets the mute end time for a user.
     */
    public int getMuteEndTime(int userId) {
        return this.mutedUsers.get(userId);
    }

    /**
     * Gets the rights list.
     */
    public IntSet getRights() {
        return this.rights;
    }

    /**
     * Gets the muted users map.
     */
    public Int2IntMap getMutedUsers() {
        return this.mutedUsers;
    }

    /**
     * Clears all mutes.
     */
    public void clearMutes() {
        synchronized (this.mutedUsers) {
            this.mutedUsers.clear();
        }
    }

    /**
     * Refreshes guild rights for all users in the room.
     */
    public void refreshGuildRightsInRoom() {
        for (User user : this.room.getUsers()) {
            if (user.getUserInfo().getCurrentRoom() == this.room) {
                if (user.getUserInfo().getId() != this.room.getOwnerId()) {
                    if (!(user.hasPermission(Permission.ACC_ANYROOMOWNER)
                            || user.hasPermission(Permission.ACC_MOVEROTATE))) {
                        this.refreshRightsForUser(user);
                    }
                }
            }
        }
    }
}
