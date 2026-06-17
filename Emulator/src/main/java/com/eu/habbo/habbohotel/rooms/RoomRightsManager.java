package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.RoomAddRightsListComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomOwnerComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomRemoveRightsListComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomRightsComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomRightsListComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserUnbannedComposer;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.events.users.UserRightsTakenEvent;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages room rights, bans, and mutes.
 */
public class RoomRightsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomRightsManager.class);

    private final Room room;
    private final TIntArrayList rights;
    private final TIntObjectHashMap<RoomBan> bannedHabbos;
    private final TIntIntHashMap mutedHabbos;

    public RoomRightsManager(Room room) {
        this.room = room;
        this.rights = new TIntArrayList();
        this.bannedHabbos = new TIntObjectHashMap<>();
        this.mutedHabbos = new TIntIntHashMap();
    }

    /**
     * Loads rights from database.
     */
    public void loadRights(Connection connection) {
        this.rights.clear();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT user_id FROM room_rights WHERE room_id = ?")) {
            statement.setInt(1, this.room.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.rights.add(set.getInt("user_id"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    /**
     * Loads bans from database.
     */
    public void loadBans(Connection connection) {
        this.bannedHabbos.clear();

        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT users.username, users.id, room_bans.* FROM room_bans INNER JOIN users ON room_bans.user_id = users.id WHERE ends > ? AND room_bans.room_id = ?")) {
            statement.setInt(1, Emulator.getIntUnixTimestamp());
            statement.setInt(2, this.room.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (this.bannedHabbos.containsKey(set.getInt("user_id"))) {
                        continue;
                    }

                    this.bannedHabbos.put(set.getInt("user_id"), new RoomBan(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    /**
     * Gets the guild right level for a habbo.
     */
    public RoomRightLevels getGuildRightLevel(Habbo habbo) {
        int guildId = this.room.getGuildId();
        if (guildId > 0 && habbo != null && habbo.getHabboInfo() != null) {
            Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

            if (guild == null) {
                return RoomRightLevels.NONE;
            }

            GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild.getId(), habbo.getHabboInfo().getId());

            if ((member != null) && (member.getRank() == GuildRank.ADMIN || member.getRank() == GuildRank.OWNER)) {
                return RoomRightLevels.GUILD_ADMIN;
            }

            if (guild.getRights()) {
                return RoomRightLevels.GUILD_RIGHTS;
            }
        }

        return RoomRightLevels.NONE;
    }

    /**
     * @deprecated Use getGuildRightLevel instead.
     */
    @Deprecated
    public int guildRightLevel(Habbo habbo) {
        return this.getGuildRightLevel(habbo).level;
    }

    /**
     * Checks if a habbo is the room owner.
     */
    public boolean isOwner(Habbo habbo) {
        return habbo.getHabboInfo().getId() == this.room.getOwnerId() || habbo.hasPermission(
            Permission.ACC_ANYROOMOWNER);
    }

    /**
     * Checks if a habbo has rights in the room.
     */
    public boolean hasRights(Habbo habbo) {
        return this.isOwner(habbo) || this.rights.contains(habbo.getHabboInfo().getId()) || (
            habbo.getRoomUnit().getRightsLevel() != RoomRightLevels.NONE
                && this.room.getCurrentHabbos().containsKey(habbo.getHabboInfo().getId()));
    }

    /**
     * Gives rights to a habbo.
     */
    public void giveRights(Habbo habbo) {
        if (habbo != null) {
            this.giveRights(habbo.getHabboInfo().getId());
        }
    }

    /**
     * Gives rights to a user by ID.
     */
    public void giveRights(int userId) {
        if (this.rights.contains(userId)) {
            return;
        }

        if (this.rights.add(userId)) {
            try {
                SqlQueries.update(
                    "INSERT INTO room_rights (room_id, user_id) VALUES (?, ?)",
                    this.room.getId(), userId);
            } catch (SqlQueries.DataAccessException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        Habbo habbo = this.room.getHabbo(userId);

        if (habbo != null) {
            this.refreshRightsForHabbo(habbo);

            this.room.sendComposer(new RoomAddRightsListComposer(this.room, habbo.getHabboInfo().getId(),
                habbo.getHabboInfo().getUsername()).compose());
        } else {
            Habbo owner = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.room.getOwnerId());

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
        Habbo habbo = this.room.getHabbo(userId);

        if (Emulator.getPluginManager()
            .fireEvent(new UserRightsTakenEvent(this.room.getHabbo(this.room.getOwnerId()), userId, habbo))
            .isCancelled()) {
            return;
        }

        this.room.sendComposer(new RoomRemoveRightsListComposer(this.room, userId).compose());

        if (this.rights.remove(userId)) {
            try {
                SqlQueries.update(
                    "DELETE FROM room_rights WHERE room_id = ? AND user_id = ?",
                    this.room.getId(), userId);
            } catch (SqlQueries.DataAccessException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        if (habbo != null) {
            this.room.getItemManager().ejectUserFurni(habbo.getHabboInfo().getId());
            habbo.getRoomUnit().setRightsLevel(RoomRightLevels.NONE);
            habbo.getRoomUnit().removeStatus(RoomUnitStatus.FLAT_CONTROL);
            this.refreshRightsForHabbo(habbo);
        }
    }

    /**
     * Removes all rights from the room.
     */
    public void removeAllRights() {
        for (int userId : rights.toArray()) {
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
        for (Habbo habbo : this.room.getHabbos()) {
            if (habbo.getHabboInfo().getCurrentRoom() == room) {
                this.refreshRightsForHabbo(habbo);
            }
        }
    }

    /**
     * Refreshes rights for a specific habbo.
     */
    public void refreshRightsForHabbo(Habbo habbo) {
        HabboItem item;
        RoomRightLevels flatCtrl = RoomRightLevels.NONE;
        if (habbo.getHabboStats().isRentingSpace()) {
            item = this.room.getHabboItem(habbo.getHabboStats().getRentedItemId());

            if (item != null) {
                return;
            }
        }

        if (habbo.hasPermission(Permission.ACC_ANYROOMOWNER)) {
            habbo.getClient().sendResponse(new RoomOwnerComposer());
            flatCtrl = RoomRightLevels.MODERATOR;
        } else if (this.isOwner(habbo)) {
            habbo.getClient().sendResponse(new RoomOwnerComposer());
            flatCtrl = RoomRightLevels.MODERATOR;
        } else if (this.room.hasGuild()) {
            // Explicit room rights must still be honoured in guild rooms (the old
            // `&& !hasGuild()` guard stripped them for non-guild members) — take
            // whichever of the two is stronger.
            RoomRightLevels guildLevel = this.getGuildRightLevel(habbo);
            flatCtrl = (this.hasRights(habbo) && RoomRightLevels.RIGHTS.isEqualOrGreaterThan(guildLevel))
                    ? RoomRightLevels.RIGHTS
                    : guildLevel;
        } else if (this.hasRights(habbo)) {
            flatCtrl = RoomRightLevels.RIGHTS;
        }

        habbo.getClient().sendResponse(new RoomRightsComposer(flatCtrl));
        habbo.getRoomUnit().setStatus(RoomUnitStatus.FLAT_CONTROL, flatCtrl.level + "");
        habbo.getRoomUnit().setRightsLevel(flatCtrl);
        habbo.getRoomUnit().statusUpdate(true);

        if (flatCtrl.equals(RoomRightLevels.MODERATOR)) {
            habbo.getClient().sendResponse(new RoomRightsListComposer(this.room));
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
    public void unbanHabbo(int userId) {
        RoomBan ban = this.bannedHabbos.remove(userId);

        if (ban != null) {
            ban.delete();
        }

        this.room.sendComposer(new RoomUserUnbannedComposer(this.room, userId).compose());
    }

    /**
     * Checks if a habbo is banned from the room.
     */
    public boolean isBanned(Habbo habbo) {
        RoomBan ban = this.bannedHabbos.get(habbo.getHabboInfo().getId());

        boolean banned =
            ban != null && ban.endTimestamp > Emulator.getIntUnixTimestamp() && !habbo.hasPermission(
                Permission.ACC_ANYROOMOWNER) && !habbo.hasPermission("acc_enteranyroom");

        if (!banned && ban != null) {
            this.unbanHabbo(habbo.getHabboInfo().getId());
        }

        return banned;
    }

    /**
     * Gets all banned users.
     */
    public TIntObjectHashMap<RoomBan> getBannedHabbos() {
        return this.bannedHabbos;
    }

    /**
     * Adds a room ban.
     */
    public void addRoomBan(RoomBan roomBan) {
        this.bannedHabbos.put(roomBan.userId, roomBan);
    }

    /**
     * Mutes a habbo for a specified number of minutes.
     */
    public void muteHabbo(Habbo habbo, int minutes) {
        synchronized (this.mutedHabbos) {
            this.mutedHabbos.put(habbo.getHabboInfo().getId(),
                Emulator.getIntUnixTimestamp() + (minutes * 60));
        }
    }

    /**
     * Checks if a habbo is muted.
     */
    public boolean isMuted(Habbo habbo) {
        if (this.isOwner(habbo) || this.hasRights(habbo)) {
            return false;
        }

        if (this.mutedHabbos.containsKey(habbo.getHabboInfo().getId())) {
            boolean time =
                this.mutedHabbos.get(habbo.getHabboInfo().getId()) > Emulator.getIntUnixTimestamp();

            if (!time) {
                this.mutedHabbos.remove(habbo.getHabboInfo().getId());
            }

            return time;
        }

        return false;
    }

    /**
     * Gets the mute end time for a habbo.
     */
    public int getMuteEndTime(int habboId) {
        return this.mutedHabbos.get(habboId);
    }

    /**
     * Gets the rights list.
     */
    public TIntArrayList getRights() {
        return this.rights;
    }

    /**
     * Gets the muted habbos map.
     */
    public TIntIntHashMap getMutedHabbos() {
        return this.mutedHabbos;
    }

    /**
     * Clears all mutes.
     */
    public void clearMutes() {
        synchronized (this.mutedHabbos) {
            this.mutedHabbos.clear();
        }
    }

    /**
     * Refreshes guild rights for all users in the room.
     */
    public void refreshGuildRightsInRoom() {
        for (Habbo habbo : this.room.getHabbos()) {
            if (habbo.getHabboInfo().getCurrentRoom() == this.room) {
                if (habbo.getHabboInfo().getId() != this.room.getOwnerId()) {
                    if (!(habbo.hasPermission(Permission.ACC_ANYROOMOWNER) || habbo.hasPermission(
                        Permission.ACC_MOVEROTATE))) {
                        this.refreshRightsForHabbo(habbo);
                    }
                }
            }
        }
    }
}
