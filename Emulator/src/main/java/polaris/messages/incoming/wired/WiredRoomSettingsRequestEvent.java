package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredRoomSettingsDataComposer;

public class WiredRoomSettingsRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null) {
            return;
        }

        this.client.sendResponse(new WiredRoomSettingsDataComposer(room, this.client.getUser()));
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
