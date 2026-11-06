package polaris.hotel.rooms;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.outgoing.rooms.RoomEnterErrorComposer;

final class RoomModerationService {

    private final IntFunction<Room> rooms;
    private final IntFunction<User> onlineUsers;
    private final IntFunction<UserInfo> offlineUsers;
    private final IntSupplier unixTime;
    private final Consumer<RoomBan> banStore;

    RoomModerationService(
            IntFunction<Room> rooms,
            IntFunction<User> onlineUsers,
            IntFunction<UserInfo> offlineUsers,
            IntSupplier unixTime,
            Consumer<RoomBan> banStore) {
        this.rooms = rooms;
        this.onlineUsers = onlineUsers;
        this.offlineUsers = offlineUsers;
        this.unixTime = unixTime;
        this.banStore = banStore;
    }

    void banUser(User rights, int userId, int roomId, RoomManager.RoomBanTypes length) {
        Room room = this.rooms.apply(roomId);
        if (room == null) {
            return;
        }
        // The room's ban setting decides who may ban: holding rights was enough even with "owner only".
        if (rights != null && !room.getRightsManager().canBan(rights)) {
            return;
        }
        if (room.getOwnerId() == userId) {
            return;
        }

        User user = this.onlineUsers.apply(userId);
        String name = this.bannableUsername(userId, user);
        if (name.isEmpty()) {
            return;
        }

        RoomBan roomBan = new RoomBan(roomId, userId, name, this.unixTime.getAsInt() + length.duration);
        this.banStore.accept(roomBan);
        room.addRoomBan(roomBan);

        if (user != null && user.getUserInfo().getCurrentRoom() == room) {
            room.removeUser(user, true);
            user.getClient().sendResponse(new RoomEnterErrorComposer(RoomEnterErrorComposer.ROOM_ERROR_BANNED));
        }
    }

    private String bannableUsername(int userId, User user) {
        if (user != null) {
            return user.hasPermission(Permission.ACC_UNKICKABLE)
                    ? ""
                    : user.getUserInfo().getUsername();
        }

        UserInfo info = this.offlineUsers.apply(userId);
        if (info == null || info.getRank().hasPermission(Permission.ACC_UNKICKABLE, false)) {
            return "";
        }
        return info.getUsername();
    }
}
