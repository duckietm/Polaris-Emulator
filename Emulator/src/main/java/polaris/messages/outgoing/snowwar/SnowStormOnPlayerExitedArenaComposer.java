package polaris.messages.outgoing.snowwar;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SnowStormOnPlayerExitedArenaComposer extends MessageComposer {

    private final int objectId;
    private final int userId;

    public SnowStormOnPlayerExitedArenaComposer(int objectId, int userId) {
        this.objectId = objectId;
        this.userId = userId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormOnPlayerExitedArenaComposer);
        this.response.appendInt(this.objectId);
        this.response.appendInt(this.userId);
        return this.response;
    }
}
