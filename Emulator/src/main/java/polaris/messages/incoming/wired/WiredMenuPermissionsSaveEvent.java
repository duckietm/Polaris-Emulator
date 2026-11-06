package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredRoomSettingsDataComposer;

/**
 * Official AIR 13 {@code WiredMenuSettingsTab.onPermissionsChanged} (1936): the modify mask, the
 * read mask and the room's timezone in one packet.
 */
public class WiredMenuPermissionsSaveEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int modifyMask = this.packet.readInt();
        int inspectMask = this.packet.readInt();
        String timezone = this.packet.readString();
        Room room = currentRoom();

        if (room == null) {
            return;
        }

        if (room.canManageWiredSettings(this.client.getUser())) {
            room.saveWiredSettings(inspectMask, modifyMask, timezone);
        }

        this.client.sendResponse(new WiredRoomSettingsDataComposer(room, this.client.getUser()));
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
