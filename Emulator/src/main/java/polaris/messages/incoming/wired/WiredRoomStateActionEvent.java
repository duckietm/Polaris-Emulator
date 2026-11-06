package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredEnvironmentComposer;
import polaris.messages.outgoing.wired.WiredRoomSettingsDataComposer;

/**
 * Official AIR 13 wired settings room-state buttons (3761).
 *
 * <p>{@code false} is "reload": the cached wired stacks are dropped so the room's boxes are wired up
 * again from the furniture as it stands now. {@code true} is "roll back": every box's configuration
 * is re-read from storage, discarding edits that were never saved, and then the stacks are rebuilt.
 */
public class WiredRoomStateActionEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        boolean rollback = this.packet.readBoolean();
        Room room = currentRoom();

        if (room == null || !room.canModifyWired(this.client.getUser())) {
            return;
        }

        if (rollback) {
            room.reloadWiredData();
        }

        if (WiredManager.getStackIndex() != null) {
            WiredManager.getStackIndex().invalidateAll(room);
        }

        this.client.sendResponse(new WiredEnvironmentComposer(room));
        this.client.sendResponse(new WiredRoomSettingsDataComposer(room, this.client.getUser()));
    }

    @Override
    public int getRatelimit() {
        return 5000;
    }
}
