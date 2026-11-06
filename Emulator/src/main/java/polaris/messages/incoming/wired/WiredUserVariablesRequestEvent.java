package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;

public class WiredUserVariablesRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null) {
            return;
        }

        room.getUserVariableManager().sendSnapshot(this.client.getUser());
    }

    @Override
    public int getRatelimit() {
        return 50;
    }
}
