package polaris.messages.outgoing.snowwar;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SnowStormUserRematchedComposer extends MessageComposer {

    private final int userId;

    public SnowStormUserRematchedComposer(int userId) {
        this.userId = userId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormUserRematchedComposer);
        this.response.appendInt(this.userId);
        return this.response;
    }
}
