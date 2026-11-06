package polaris.messages.outgoing.wheel;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

// The winning prize id. The client animates the wheel to that slice; the reward
// was already granted server-side.
public class WheelResultComposer extends MessageComposer {
    private final int prizeId;

    public WheelResultComposer(int prizeId) {
        this.prizeId = prizeId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WheelResultComposer);
        this.response.appendInt(this.prizeId);
        return this.response;
    }
}
