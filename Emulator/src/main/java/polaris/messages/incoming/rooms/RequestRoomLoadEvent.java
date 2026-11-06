package polaris.messages.incoming.rooms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;
import polaris.messages.incoming.MessageHandler;

public class RequestRoomLoadEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestRoomLoadEvent.class);
    static final long STALE_LOAD_MILLIS = 5000;
    static final long ENTRY_THROTTLE_MILLIS = 1000;

    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();
        String password = this.packet.readString();

        // Clients may append spawn coordinates for a reconnect. They are read for wire compatibility
        // but never trusted: a re-entry of the room the user still stands in keeps the server's own
        // position below, anything else enters at the door.
        int spawnX = -1;
        int spawnY = -1;

        long now = System.currentTimeMillis();
        UserStats stats = this.client.getUser().getUserStats();
        UserInfo info = this.client.getUser().getUserInfo();

        // Reset stale loadingRoom if timestamp has expired (indicates failed/stuck load)
        if (info.getLoadingRoom() != 0 && isStaleLoad(stats.roomOpenedAtMillis, now)) {
            info.setLoadingRoom(0);
        }

        // Only a repeat of the room just opened is throttled (double click); another room always passes.
        if (isThrottledRepeat(roomId, stats.roomOpenedId, stats.roomOpenedAtMillis, now)) {
            return;
        }

        // Switching rooms while the previous one is still loading.
        if (info.getLoadingRoom() != 0 && info.getLoadingRoom() != roomId) {
            info.setLoadingRoom(0);
        }

        // The room contents are loaded by the entry itself, only once access is granted.
        Room room = info.getCurrentRoom();
        if (room != null) {
            // If re-entering the same room (session resume / reconnect), capture
            // the user's current position before removal so we can respawn there.
            if (room.getId() == roomId
                    && this.client.getUser().getRoomUnit() != null
                    && this.client.getUser().getRoomUnit().getCurrentLocation() != null) {
                RoomTile currentLoc = this.client.getUser().getRoomUnit().getCurrentLocation();
                spawnX = currentLoc.x;
                spawnY = currentLoc.y;
                LOGGER.info(
                        "[RequestRoomLoadEvent] Re-entering same room {} — preserving position ({}, {})",
                        roomId,
                        spawnX,
                        spawnY);
            }

            Emulator.getGameEnvironment().getRoomManager().logExit(this.client.getUser());

            room.removeUser(this.client.getUser(), true);

            info.setCurrentRoom(null);
        }

        if (this.client.getUser().getRoomUnit() != null && this.client.getUser().getRoomUnit().isTeleporting) {
            this.client.getUser().getRoomUnit().isTeleporting = false;
        }

        // The spawn tile is resolved against the layout after the room contents are loaded.
        LOGGER.debug("[RequestRoomLoadEvent] Entering room {} (spawn=({}, {}))", roomId, spawnX, spawnY);
        Emulator.getGameEnvironment()
                .getRoomManager()
                .enterRoomAt(this.client.getUser(), roomId, password, spawnX, spawnY);
    }

    // Both in millis: roomOpenedAtMillis is stamped by RoomManager.openRoom.
    static boolean isStaleLoad(long roomOpenedAtMillis, long nowMillis) {
        return roomOpenedAtMillis + STALE_LOAD_MILLIS < nowMillis;
    }

    static boolean isThrottledRepeat(int roomId, int roomOpenedId, long roomOpenedAtMillis, long nowMillis) {
        return roomId == roomOpenedId && roomOpenedAtMillis + ENTRY_THROTTLE_MILLIS >= nowMillis;
    }
}
