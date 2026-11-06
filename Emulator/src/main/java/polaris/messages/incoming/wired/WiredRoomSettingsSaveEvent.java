package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredRoomSettingsDataComposer;

public class WiredRoomSettingsSaveEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null) {
            return;
        }

        if (this.packet.bytesAvailable() < 8) {
            this.client.sendResponse(new WiredRoomSettingsDataComposer(room, this.client.getUser()));
            return;
        }

        if (!room.canManageWiredSettings(this.client.getUser())) {
            this.client.sendResponse(new WiredRoomSettingsDataComposer(room, this.client.getUser()));
            return;
        }

        int inspectMask = this.packet.readInt();
        int modifyMask = this.packet.readInt();

        room.saveWiredSettings(inspectMask, modifyMask);

        this.client.sendResponse(new WiredRoomSettingsDataComposer(room, this.client.getUser()));
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
