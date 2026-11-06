package polaris.messages.outgoing.handshake;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PongComposer extends MessageComposer {
    private final int id;

    public PongComposer(int id) {
        this.id = id;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PongComposer);
        this.response.appendInt(this.id);
        return this.response;
    }

    public int getId() {
        return id;
    }
}
