package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.variables.RoomWiredVariableCatalog;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredAllVariablesHashComposer;

/**
 * Official AIR 13 {@code WiredVariablesSynchronizer.getAllVariables} (1735): answers with the hash
 * of the room's whole wired variable set so the client can skip the diff when nothing moved.
 */
public class WiredAllVariablesRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null || !room.canInspectWired(this.client.getUser())) {
            return;
        }

        this.client.sendResponse(new WiredAllVariablesHashComposer(
                RoomWiredVariableCatalog.allVariablesHash(RoomWiredVariableCatalog.variables(room))));
    }

    @Override
    public int getRatelimit() {
        return 50;
    }
}
