package polaris.messages.outgoing.camera;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CameraPurchaseSuccesfullComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CameraPurchaseSuccesfullComposer);
        return this.response;
    }
}
