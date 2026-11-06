package polaris.messages.outgoing.gamecenter.basejump;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class BaseJumpJoinQueueComposer extends MessageComposer {
    private final int gameId;

    public BaseJumpJoinQueueComposer(int gameId) {
        this.gameId = gameId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BaseJumpJoinQueueComposer);
        this.response.appendInt(this.gameId);
        return this.response;
    }

    public int getGameId() {
        return gameId;
    }
}
