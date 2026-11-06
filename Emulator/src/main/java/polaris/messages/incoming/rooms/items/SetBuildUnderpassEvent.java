package polaris.messages.incoming.rooms.items;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.messages.incoming.MessageHandler;

/**
 * Toggles the sender's build-underpass mode (packet 7022, sent by the build
 * height widget). While the mode is on, furniture the user places or moves is
 * marked walk-underneath ({@code items.allow_underpass}); avatars can then pass
 * below it when it is raised high enough, even when the room-wide underpass
 * setting is off. The mode lives on the {@link polaris.hotel.rooms.RoomUnit}
 * and resets on every room visit. The flag only takes effect through the normal
 * placement and movement paths, so all furniture rights checks still apply.
 */
public class SetBuildUnderpassEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        boolean enabled = this.packet.readBoolean();

        Room room = this.currentRoom();

        if (room == null) return;

        if (this.client.getUser().getRoomUnit() == null) return;

        // Playtest (the hotel's own rule beyond AIR): the build tools stay off.
        if (enabled && RoomPlayTestSupport.isBuildingRefused(this.client.getUser())) return;

        this.client.getUser().getRoomUnit().setBuildUnderpass(enabled);
    }
}
