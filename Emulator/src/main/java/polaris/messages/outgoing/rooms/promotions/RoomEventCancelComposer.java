package polaris.messages.outgoing.rooms.promotions;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomEventCancelComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomEventCancelComposer);
        // Empty Body
        return this.response;
    }
}
