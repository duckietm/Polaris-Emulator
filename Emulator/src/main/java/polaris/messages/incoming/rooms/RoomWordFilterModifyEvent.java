package polaris.messages.incoming.rooms;

import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;

public class RoomWordFilterModifyEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        final int roomId = this.packet.readInt();
        final boolean add = this.packet.readBoolean();
        String word = this.packet.readString().trim();

        if (word.length() > 25) {
            word = word.substring(0, 25);
        }

        if (word.isEmpty()) {
            return;
        }

        // Get current room of user.
        final Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null || room.getId() != roomId) {
            return;
        }

        // Check if owner.
        if (!room.isOwner(this.client.getUser())) {
            ScripterManager.scripterDetected(
                    this.client,
                    String.format(
                            "User (%s) tried to change wordfilter for a not owned room.",
                            this.client.getUser().getUserInfo().getUsername()));
            return;
        }

        // Modify word filter.
        if (add) {
            room.addToWordFilter(word);
        } else {
            room.removeFromWordFilter(word);
        }
    }
}
