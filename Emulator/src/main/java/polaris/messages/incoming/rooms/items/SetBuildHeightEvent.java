package polaris.messages.incoming.rooms.items;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.messages.incoming.MessageHandler;

/**
 * Sets the sender's build height (packet 9351, sent by the build height widget). The client sends the
 * height in hundredths of a tile while the slider moves and a disabled packet when the widget closes.
 * The mode only changes where furniture the user places or moves lands; it is refused to anybody who
 * cannot build in the room, and the height is clamped to the hotel's furniture ceiling.
 */
public class SetBuildHeightEvent extends MessageHandler {

    static final double HEIGHT_SCALE = 100.0D;

    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        boolean enabled = this.packet.readBoolean();
        double height = this.packet.readInt() / HEIGHT_SCALE;

        Room room = this.currentRoom();

        if (room == null) return;

        if (this.client.getUser().getRoomUnit() == null) return;

        if (enabled && !room.hasRights(this.client.getUser())) return;

        // Playtest (the hotel's own rule beyond AIR): no build height, as for a visitor without rights.
        if (enabled && RoomPlayTestSupport.isBuildingRefused(this.client.getUser())) return;

        this.client
                .getUser()
                .getRoomUnit()
                .setBuildHeight(enabled, Math.max(0.0D, Math.min(height, Room.MAXIMUM_FURNI_HEIGHT)));
    }
}
