package polaris.messages.outgoing.navigator;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class OpenRoomCreationWindowComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.OpenRoomCreationWindowComposer);
        return this.response;
    }
}
