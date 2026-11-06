package polaris.messages.incoming.rooms;

import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomPaintComposer;

public class RoomRemovePaintEvent extends MessageHandler {
    private static final String DEFAULT_PAINT = "0.0";

    // "0.0" is the stored sentinel (paint packets are skipped on room entry),
    // but a live broadcast must carry the texture ids the renderer defaults
    // to for a fresh room, or the paint renders as a bare colour instead.
    private static final String CLIENT_DEFAULT_FLOOR = "111";
    private static final String CLIENT_DEFAULT_WALL = "201";

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        String paintType = this.packet.readString();

        Room room = this.currentRoom();

        if (room == null) return;

        if (room.getOwnerId() != this.client.getUser().getUserInfo().getId()
                && !room.hasRights(this.client.getUser())
                && !this.client.getUser().hasPermission(Permission.ACC_PLACEFURNI)) return;

        String clientPaint;

        if ("floor".equals(paintType)) {
            if (DEFAULT_PAINT.equals(room.getFloorPaint())) return;

            room.setFloorPaint(DEFAULT_PAINT);
            clientPaint = CLIENT_DEFAULT_FLOOR;
        } else if ("wallpaper".equals(paintType)) {
            if (DEFAULT_PAINT.equals(room.getWallPaint())) return;

            room.setWallPaint(DEFAULT_PAINT);
            clientPaint = CLIENT_DEFAULT_WALL;
        } else {
            return;
        }

        room.setNeedsUpdate(true);
        room.sendComposer(new RoomPaintComposer(paintType, clientPaint).compose());
    }
}
