package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.variables.PermanentUserVariables;
import polaris.hotel.rooms.variables.RoomWiredVariableCatalog;
import polaris.hotel.rooms.variables.RoomWiredVariableWrites;
import polaris.hotel.wired.WiredVariableChangeOrigin;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredPermanentVariableResultComposer;
import polaris.messages.outgoing.wired.WiredUserPermanentVariablesListComposer;

/**
 * Official holder detail change (627): sets the value of a permanent variable a holder has (action 0), gives the
 * holder the variable (1) or takes it away (2). Answered by the result and the holder's list as it then is. A holder
 * who is not in the room is only the variable owner's to change, as with the other creator-tool writes.
 */
public class WiredPermanentVariableSetEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // entity type: every holder of a user variable is listed as a Habbo
        int holderKey = this.packet.readInt();
        String variableId = this.packet.readString();
        int value = this.packet.readInt();
        int action = this.packet.readInt();

        Room room = currentRoom();
        if (room == null) {
            return;
        }

        boolean allowed = room.canModifyWired(this.client.getUser())
                && RoomWiredVariableWrites.mayManageUser(
                        room, this.client.getUser(), holderKey, RoomWiredVariableCatalog.definitionIdOf(variableId));
        boolean success = false;

        if (allowed) {
            int previousOrigin = WiredVariableChangeOrigin.enter(WiredVariableChangeOrigin.CREATOR_TOOLS);
            try {
                success = PermanentUserVariables.apply(room, holderKey, variableId, value, action);
            } finally {
                WiredVariableChangeOrigin.exit(previousOrigin);
            }
        }

        this.client.sendResponse(new WiredPermanentVariableResultComposer(success));

        if (room.canInspectWired(this.client.getUser())) {
            this.client.sendResponse(
                    new WiredUserPermanentVariablesListComposer(PermanentUserVariables.of(room, holderKey)));
        }
    }

    @Override
    public int getRatelimit() {
        return 150;
    }
}
