package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.variables.RoomWiredVariableCatalog;
import polaris.hotel.rooms.variables.RoomWiredVariableWrites;
import polaris.hotel.wired.WiredVariableChangeOrigin;
import polaris.hotel.wired.WiredVariableClearPolicy;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredMenuErrorComposer;

/**
 * Official "delete from all holders" of the variables overview (2480): takes a user or furni variable from every
 * holder. It reaches holders who are not in the room, so only the variable's owner may do it; anyone else gets the
 * menu error.
 */
public class WiredVariableDeleteAllHoldersEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String variableId = this.packet.readString();

        Room room = currentRoom();
        if (room == null || !room.canModifyWired(this.client.getUser())) {
            return;
        }

        int target = RoomWiredVariableCatalog.targetOf(variableId);
        int definitionId = RoomWiredVariableCatalog.definitionIdOf(variableId);

        if (definitionId <= 0
                || (target != RoomWiredVariableCatalog.TARGET_USER
                        && target != RoomWiredVariableCatalog.TARGET_FURNI)) {
            return;
        }

        if (!WiredVariableClearPolicy.canClear(room, this.client.getUser(), definitionId)) {
            this.client.sendResponse(new WiredMenuErrorComposer(WiredMenuErrorComposer.NOT_VARIABLE_OWNER));
            return;
        }

        int previousOrigin = WiredVariableChangeOrigin.enter(WiredVariableChangeOrigin.CREATOR_TOOLS);
        try {
            RoomWiredVariableWrites.clearAll(
                    room,
                    target == RoomWiredVariableCatalog.TARGET_FURNI
                            ? RoomWiredVariableWrites.TARGET_FURNI
                            : RoomWiredVariableWrites.TARGET_USER,
                    definitionId);
        } finally {
            WiredVariableChangeOrigin.exit(previousOrigin);
        }
    }

    @Override
    public int getRatelimit() {
        return 500;
    }
}
