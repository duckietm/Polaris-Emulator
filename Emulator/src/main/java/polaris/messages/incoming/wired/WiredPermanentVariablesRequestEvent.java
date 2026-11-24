package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.variables.PermanentUserVariables;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredUserPermanentVariablesListComposer;

/**
 * Official holder detail request (2035): the permanent variables of one holder of the room, answered by
 * {@code WiredUserPermanentVariablesList}. The holder need not be in the room.
 */
public class WiredPermanentVariablesRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // entity type: every holder of a user variable is listed as a Habbo
        int holderKey = this.packet.readInt();

        Room room = currentRoom();
        if (room == null || !room.canInspectWired(this.client.getUser())) {
            return;
        }

        this.client.sendResponse(
                new WiredUserPermanentVariablesListComposer(PermanentUserVariables.of(room, holderKey)));
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
