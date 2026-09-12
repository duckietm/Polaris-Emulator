package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.outgoing.rooms.RoomEnterErrorComposer;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

final class RoomModerationService {

    private final IntFunction<Room> rooms;
    private final IntFunction<Habbo> onlineUsers;
    private final IntFunction<HabboInfo> offlineUsers;
    private final IntSupplier unixTime;
    private final Consumer<RoomBan> banStore;

    RoomModerationService(
            IntFunction<Room> rooms,
            IntFunction<Habbo> onlineUsers,
            IntFunction<HabboInfo> offlineUsers,
            IntSupplier unixTime,
            Consumer<RoomBan> banStore) {
        this.rooms = rooms;
        this.onlineUsers = onlineUsers;
        this.offlineUsers = offlineUsers;
        this.unixTime = unixTime;
        this.banStore = banStore;
    }

    void banUser(Habbo rights, int userId, int roomId, RoomManager.RoomBanTypes length) {
        this.banUser(rights, userId, roomId, length.duration);
    }

    /**
     * Bans for an arbitrary number of seconds. Raid protection needs this: the client offers ten ban
     * lengths, and only two of them line up with {@link RoomManager.RoomBanTypes}.
     */
    void banUser(Habbo rights, int userId, int roomId, int durationSeconds) {
        Room room = this.rooms.apply(roomId);
        if (room == null) {
            return;
        }
        if (rights != null && !room.hasRights(rights)) {
            return;
        }
        if (room.getOwnerId() == userId) {
            return;
        }

        Habbo habbo = this.onlineUsers.apply(userId);
        String name = this.bannableUsername(userId, habbo);
        if (name.isEmpty()) {
            return;
        }

        RoomBan roomBan = new RoomBan(roomId, userId, name, this.unixTime.getAsInt() + durationSeconds);
        this.banStore.accept(roomBan);
        room.addRoomBan(roomBan);

        if (habbo != null && habbo.getHabboInfo().getCurrentRoom() == room) {
            room.removeHabbo(habbo, true);
            habbo.getClient().sendResponse(new RoomEnterErrorComposer(RoomEnterErrorComposer.ROOM_ERROR_BANNED));
        }
    }

    private String bannableUsername(int userId, Habbo habbo) {
        if (habbo != null) {
            return habbo.hasPermission(Permission.ACC_UNKICKABLE)
                    ? ""
                    : habbo.getHabboInfo().getUsername();
        }

        HabboInfo info = this.offlineUsers.apply(userId);
        if (info == null || info.getRank().hasPermission(Permission.ACC_UNKICKABLE, false)) {
            return "";
        }
        return info.getUsername();
    }
}
